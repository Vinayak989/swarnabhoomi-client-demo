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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "marketplace_purchase_request")
public class MarketplacePurchaseRequest {
    public enum Status { PENDING, ACCEPTED, DECLINED, COMPLETED }
    public enum Fulfillment { PICKUP, DELIVERY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long listingId;

    @Column(nullable = false, length = 100)
    private String buyerName;

    @Column(nullable = false, length = 20)
    private String buyerPhone;

    @Column(nullable = false, precision = 13, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, length = 16)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Fulfillment fulfillment;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = Status.PENDING;
    }

    public Long getId() { return id; }
    public Long getListingId() { return listingId; }
    public void setListingId(Long value) { listingId = value; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String value) { buyerName = value; }
    public String getBuyerPhone() { return buyerPhone; }
    public void setBuyerPhone(String value) { buyerPhone = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public Fulfillment getFulfillment() { return fulfillment; }
    public void setFulfillment(Fulfillment value) { fulfillment = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public Instant getCreatedAt() { return createdAt; }
}
