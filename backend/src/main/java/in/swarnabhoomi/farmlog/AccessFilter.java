package in.swarnabhoomi.farmlog;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AccessFilter extends OncePerRequestFilter {
    private static final Pattern MARKETPLACE_PHOTO = Pattern.compile("^/api/marketplace/photos/[a-fA-F0-9-]{36}$");
    private static final Pattern SELLER_MANAGEMENT = Pattern.compile(
            "^/api/marketplace/seller/listings/[0-9]+(?:/purchase-requests(?:/[0-9]+)?)?$");
    private static final Pattern PURCHASE_REQUEST = Pattern.compile("^/api/marketplace/listings/[0-9]+/purchase-requests$");
    private static final String MARKETPLACE_ORDERS = "/api/marketplace/orders";
    private static final String MARKETPLACE_BUYER_ORDERS = "/api/marketplace/buyer/orders";
    private static final Pattern MARKETPLACE_USER_FEATURE = Pattern.compile(
            "^/api/marketplace/(?:unread-messages|orders/[0-9]+/(?:messages|block|review)|messages/[0-9]+/report|price-alerts(?:/[0-9]+)?)$");
    private static final Pattern MARKETPLACE_PUBLIC_REVIEWS = Pattern.compile("^/api/marketplace/listings/[0-9]+/reviews$");
    @Value("${app.access.enabled:false}") private boolean enabled;
    @Value("${app.access.username:demo}") private String expectedUsername;
    @Value("${app.access.password:}") private String expectedPassword;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/") || path.equals("/api/health")) {
            chain.doFilter(request, response);
            return;
        }
        if (isPublicMarketplaceRequest(request, path)) {
            chain.doFilter(request, response);
            return;
        }
        if (path.equals("/api/marketplace/access")
                || path.matches("^/api/marketplace/listings/[0-9]+/contact$")) {
            chain.doFilter(request, response);
            return;
        }
        boolean privateMarketplaceRequest = path.startsWith("/api/marketplace/");
        if (!privateMarketplaceRequest) {
            chain.doFilter(request, response);
            return;
        }
        if (privateMarketplaceRequest && expectedPassword.isBlank()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"विक्रेता नोंदणी व संपर्कासाठी अॅप प्रवेश तपशील सर्व्हरवर कॉन्फिगर करणे आवश्यक आहे.\"}");
            return;
        }
        if (authorized(request.getHeader("Authorization"))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Basic realm=\"Swarnabhoomi\", charset=\"UTF-8\"");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"प्रवेशासाठी लॉगिन करा\"}");
    }

    private boolean isPublicMarketplaceRequest(HttpServletRequest request, String path) {
        String method = request.getMethod();
        return (path.equals("/api/consultants")
                    && ("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)))
                || (path.startsWith("/api/marketplace/")
                    && ("GET".equalsIgnoreCase(method) && path.equals("/api/marketplace/listings")
                        || "GET".equalsIgnoreCase(method) && path.equals("/api/marketplace/payment-options")
                        || "GET".equalsIgnoreCase(method) && MARKETPLACE_PUBLIC_REVIEWS.matcher(path).matches()
                        || "POST".equalsIgnoreCase(method) && path.equals("/api/marketplace/listings")
                        || "POST".equalsIgnoreCase(method) && path.equals("/api/marketplace/photos")
                        || "GET".equalsIgnoreCase(method) && MARKETPLACE_PHOTO.matcher(path).matches()
                        || "POST".equalsIgnoreCase(method) && PURCHASE_REQUEST.matcher(path).matches()
                        || "POST".equalsIgnoreCase(method) && MARKETPLACE_ORDERS.equals(path)
                        || "GET".equalsIgnoreCase(method) && MARKETPLACE_BUYER_ORDERS.equals(path)
                        || MARKETPLACE_USER_FEATURE.matcher(path).matches()
                        || ("GET".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))
                            && SELLER_MANAGEMENT.matcher(path).matches()));
    }

    private boolean authorized(String header) {
        if (header == null || !header.startsWith("Basic ") || expectedPassword.isBlank()) return false;
        try {
            String credentials = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
            int separator = credentials.indexOf(':');
            if (separator < 0) return false;
            byte[] username = credentials.substring(0, separator).getBytes(StandardCharsets.UTF_8);
            byte[] password = credentials.substring(separator + 1).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(username, expectedUsername.getBytes(StandardCharsets.UTF_8))
                    && MessageDigest.isEqual(password, expectedPassword.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
