package in.swarnabhoomi.farmlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "marketplace_order_review")
public class MarketplaceOrderReview {
    public enum Status { PENDING, APPROVED, HIDDEN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true)
    private Long requestId;

    @Column(name = "buyer_user_id", nullable = false)
    private Long buyerUserId;

    @Column(name = "seller_user_id", nullable = false)
    private Long sellerUserId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 500)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = Status.PENDING;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long value) { requestId = value; }
    public Long getBuyerUserId() { return buyerUserId; }
    public void setBuyerUserId(Long value) { buyerUserId = value; }
    public Long getSellerUserId() { return sellerUserId; }
    public void setSellerUserId(Long value) { sellerUserId = value; }
    public Long getListingId() { return listingId; }
    public void setListingId(Long value) { listingId = value; }
    public Integer getRating() { return rating; }
    public void setRating(Integer value) { rating = value; }
    public String getComment() { return comment; }
    public void setComment(String value) { comment = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long value) { reviewedBy = value; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant value) { reviewedAt = value; }
    public Instant getCreatedAt() { return createdAt; }
}
