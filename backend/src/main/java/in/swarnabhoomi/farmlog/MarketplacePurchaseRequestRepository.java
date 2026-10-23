package in.swarnabhoomi.farmlog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplacePurchaseRequestRepository extends JpaRepository<MarketplacePurchaseRequest, Long> {
    List<MarketplacePurchaseRequest> findTop100ByListingIdOrderByCreatedAtDesc(Long listingId);
}
