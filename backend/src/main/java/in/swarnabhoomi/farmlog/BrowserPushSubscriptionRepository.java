package in.swarnabhoomi.farmlog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrowserPushSubscriptionRepository extends JpaRepository<BrowserPushSubscription, Long> {
    Optional<BrowserPushSubscription> findByEndpoint(String endpoint);
}
