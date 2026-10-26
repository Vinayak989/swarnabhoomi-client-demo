package in.swarnabhoomi.farmlog;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplacePurchaseRequestRepository extends JpaRepository<MarketplacePurchaseRequest, Long> {
    List<MarketplacePurchaseRequest> findTop100ByListingIdOrderByCreatedAtDesc(Long listingId);
    List<MarketplacePurchaseRequest> findTop100ByBuyerUserIdOrderByCreatedAtDesc(Long buyerUserId);
    List<MarketplacePurchaseRequest> findAllByListingIdInOrderByCreatedAtDesc(List<Long> listingIds);
    @Modifying
    @Query("update MarketplacePurchaseRequest request set request.buyerLastReadMessageId = :messageId "
            + "where request.id = :requestId and (request.buyerLastReadMessageId is null "
            + "or request.buyerLastReadMessageId < :messageId)")
    int markBuyerMessagesRead(@Param("requestId") Long requestId, @Param("messageId") Long messageId);
    @Modifying
    @Query("update MarketplacePurchaseRequest request set request.sellerLastReadMessageId = :messageId "
            + "where request.id = :requestId and (request.sellerLastReadMessageId is null "
            + "or request.sellerLastReadMessageId < :messageId)")
    int markSellerMessagesRead(@Param("requestId") Long requestId, @Param("messageId") Long messageId);
    long countByStatus(MarketplacePurchaseRequest.Status status);
    Page<MarketplacePurchaseRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
