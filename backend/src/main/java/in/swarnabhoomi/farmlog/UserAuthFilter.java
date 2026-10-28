package in.swarnabhoomi.farmlog;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UserAuthFilter extends OncePerRequestFilter {
    public static final String USER_ATTRIBUTE = "swarnabhoomi.authenticatedUser";
    private static final Pattern MARKETPLACE_CONTACT = Pattern.compile("^/api/marketplace/listings/[0-9]+/contact$");
    private static final Pattern MARKETPLACE_PURCHASE = Pattern.compile("^/api/marketplace/listings/[0-9]+/purchase-requests$");
    private static final String MARKETPLACE_ORDERS = "/api/marketplace/orders";
    private static final String MARKETPLACE_BUYER_ORDERS = "/api/marketplace/buyer/orders";
    private static final Pattern MARKETPLACE_USER_FEATURE = Pattern.compile(
            "^/api/marketplace/(?:unread-messages|orders/[0-9]+/(?:messages|block|review)|messages/[0-9]+/report|price-alerts(?:/[0-9]+)?)$");
    private static final Pattern MARKETPLACE_SELLER = Pattern.compile(
            "^/api/marketplace/seller/listings/[0-9]+(?:/purchase-requests(?:/[0-9]+)?)?$");
    private final AuthService authService;
    private final DatabaseExportAccess databaseExportAccess;

    public UserAuthFilter(AuthService authService, DatabaseExportAccess databaseExportAccess) {
        this.authService = authService;
        this.databaseExportAccess = databaseExportAccess;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean userMarketplaceRoute = path.equals("/api/marketplace/access")
                || MARKETPLACE_CONTACT.matcher(path).matches()
                || MARKETPLACE_USER_FEATURE.matcher(path).matches()
                || MARKETPLACE_SELLER.matcher(path).matches() && ("GET".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))
                || MARKETPLACE_ORDERS.equals(path) && "POST".equalsIgnoreCase(method)
                || MARKETPLACE_BUYER_ORDERS.equals(path) && "GET".equalsIgnoreCase(method)
                || "POST".equalsIgnoreCase(method) && (path.equals("/api/marketplace/listings")
                    || path.equals("/api/marketplace/photos") || MARKETPLACE_PURCHASE.matcher(path).matches());
        return "OPTIONS".equalsIgnoreCase(method)
                || databaseExportAccess.isAuthorized(request)
                || !path.startsWith("/api/")
                || path.equals("/api/health")
                || path.startsWith("/api/auth/") && !path.equals("/api/auth/me") && !path.equals("/api/auth/logout")
                || path.startsWith("/api/weather/")
                || path.startsWith("/api/marketplace/") && !userMarketplaceRoute
                || path.equals("/api/push/subscriptions/public-key");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        AppUser user = header != null && header.startsWith("Bearer ")
                ? authService.userForToken(header.substring(7)) : null;
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"प्रवेश संपला आहे. कृपया पुन्हा लॉगिन करा.\"}");
            return;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ((path.equals("/api/marketplace/listings") || path.equals("/api/marketplace/photos"))
                && "POST".equalsIgnoreCase(method)
                && !("seller".equals(user.getRole()) || "farmer".equals(user.getRole()))
                || MARKETPLACE_PURCHASE.matcher(path).matches() && "POST".equalsIgnoreCase(method)
                    && !"buyer".equals(user.getRole())
                || MARKETPLACE_ORDERS.equals(path) && !"buyer".equals(user.getRole())
                || MARKETPLACE_BUYER_ORDERS.equals(path) && !"buyer".equals(user.getRole())
                || MARKETPLACE_CONTACT.matcher(path).matches() && !"admin".equals(user.getRole())
                || MARKETPLACE_USER_FEATURE.matcher(path).matches()
                    && !("farmer".equals(user.getRole()) || "seller".equals(user.getRole())
                        || "buyer".equals(user.getRole()))
                || MARKETPLACE_SELLER.matcher(path).matches()
                    && !("GET".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))
                || MARKETPLACE_SELLER.matcher(path).matches()
                    && !("seller".equals(user.getRole()) || "farmer".equals(user.getRole()))
                || path.equals("/api/consultants") && "POST".equalsIgnoreCase(method)
                    && !"admin".equals(user.getRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"या कृतीसाठी निवडलेला खाते प्रकार योग्य नाही.\"}");
            return;
        }
        request.setAttribute(USER_ATTRIBUTE, user);
        chain.doFilter(request, response);
    }
}
