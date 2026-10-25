package in.swarnabhoomi.farmlog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByMobileNumberAndRole(String mobileNumber, String role);
    boolean existsByMobileNumberAndRole(String mobileNumber, String role);
    long countByRole(String role);
    Page<AppUser> findAllByRoleOrderByCreatedAtDesc(String role, Pageable pageable);
}
