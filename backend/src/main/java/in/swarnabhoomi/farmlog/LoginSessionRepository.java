package in.swarnabhoomi.farmlog;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionRepository extends JpaRepository<LoginSession, String> {
    Optional<LoginSession> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
}
