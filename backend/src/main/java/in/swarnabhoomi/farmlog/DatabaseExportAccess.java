package in.swarnabhoomi.farmlog;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DatabaseExportAccess {
    public static final String PATH = "/api/internal/database-export";
    private final String token;
    private final Instant expiresAt;

    public DatabaseExportAccess(@Value("${DATABASE_EXPORT_TOKEN:}") String token,
            @Value("${DATABASE_EXPORT_EXPIRES_AT:}") String expiresAt) {
        this.token = token;
        Instant parsed;
        try { parsed = Instant.parse(expiresAt); } catch (RuntimeException exception) { parsed = Instant.EPOCH; }
        this.expiresAt = parsed;
    }

    public boolean isAuthorized(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !PATH.equals(request.getRequestURI())
                || token.length() < 40 || !expiresAt.isAfter(Instant.now())) return false;
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Bearer ") && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), authorization.substring(7).getBytes(StandardCharsets.UTF_8));
    }

    public void requireAuthorized(HttpServletRequest request) {
        if (!isAuthorized(request)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Database export authorization is missing or expired.");
    }
}
