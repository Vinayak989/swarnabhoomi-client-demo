package in.swarnabhoomi.farmlog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrowserPushSubscriptionRepository extends JpaRepository<BrowserPushSubscription, Long> {
    Optional<BrowserPushSubscription> findByEndpoint(String endpoint);
    Optional<BrowserPushSubscription> findByIdAndOwnerIdAndActiveTrue(Long id, Long ownerId);
    Optional<BrowserPushSubscription> findByIdAndOwnerId(Long id, Long ownerId);

}
