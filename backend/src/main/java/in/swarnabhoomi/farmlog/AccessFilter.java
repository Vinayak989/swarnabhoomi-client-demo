package in.swarnabhoomi.farmlog;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class AccessFilter extends OncePerRequestFilter {
    @Value("${app.access.enabled:false}") private boolean enabled;
    @Value("${app.access.username:demo}") private String expectedUsername;
    @Value("${app.access.password:}") private String expectedPassword;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || !enabled || !path.startsWith("/api/") || path.equals("/api/health")) {
            chain.doFilter(request, response);
            return;
        }
        if ("GET".equalsIgnoreCase(request.getMethod())
                && path.matches("/api/reminders/call-audio/[0-9a-fA-F-]{36}")) {
            chain.doFilter(request, response);
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
