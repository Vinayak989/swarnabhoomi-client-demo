package in.swarnabhoomi.farmlog;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {
    Optional<OtpChallenge> findTopByMobileNumberAndRoleAndConsumedFalseOrderByCreatedAtDesc(
            String mobileNumber, String role);
    void deleteAllByMobileNumberAndRole(String mobileNumber, String role);
    boolean existsByMobileNumberAndRoleAndCreatedAtAfter(String mobileNumber, String role, Instant after);
    boolean existsByMobileNumberAndCreatedAtAfter(String mobileNumber, Instant after);
}
