package in.swarnabhoomi.farmlog;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplacePurchaseRequestRepository extends JpaRepository<MarketplacePurchaseRequest, Long> {
    List<MarketplacePurchaseRequest> findTop100ByListingIdOrderByCreatedAtDesc(Long listingId);
    List<MarketplacePurchaseRequest> findTop100ByBuyerUserIdOrderByCreatedAtDesc(Long buyerUserId);
    long countByStatus(MarketplacePurchaseRequest.Status status);
    Page<MarketplacePurchaseRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
