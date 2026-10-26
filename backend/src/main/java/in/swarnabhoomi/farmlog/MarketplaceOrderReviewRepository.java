package in.swarnabhoomi.farmlog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceOrderReviewRepository extends JpaRepository<MarketplaceOrderReview, Long> {
    Optional<MarketplaceOrderReview> findByRequestIdAndBuyerUserId(Long requestId, Long buyerUserId);
    boolean existsByRequestIdAndBuyerUserId(Long requestId, Long buyerUserId);
    Page<MarketplaceOrderReview> findAllByStatusOrderByCreatedAtDesc(MarketplaceOrderReview.Status status, Pageable pageable);

    @Query("select review.sellerUserId, avg(review.rating), count(review.id) from MarketplaceOrderReview review "
            + "where review.sellerUserId in :sellerIds and review.status = :status group by review.sellerUserId")
    List<Object[]> findRatingStats(@Param("sellerIds") Collection<Long> sellerIds,
            @Param("status") MarketplaceOrderReview.Status status);
    List<MarketplaceOrderReview> findAllBySellerUserIdAndStatusOrderByCreatedAtDesc(Long sellerUserId,
            MarketplaceOrderReview.Status status, Pageable pageable);
}
