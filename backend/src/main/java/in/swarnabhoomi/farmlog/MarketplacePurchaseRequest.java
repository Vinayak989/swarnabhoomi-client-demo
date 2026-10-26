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
    public enum FulfillmentStage { REQUESTED, ACCEPTED, PREPARING, READY_FOR_PICKUP, OUT_FOR_DELIVERY, COMPLETED, DECLINED }
    public enum PaymentMethod { CASH, UPI }
    public enum PaymentStatus { PENDING, PAID, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long listingId;

    @Column(name = "buyer_user_id")
    private Long buyerUserId;

    @Column(name = "buyer_last_read_message_id")
    private Long buyerLastReadMessageId = 0L;

    @Column(name = "seller_last_read_message_id")
    private Long sellerLastReadMessageId = 0L;

    @Column(nullable = false, length = 100)
    private String buyerName;

    @Column(name = "product_name", length = 120)
    private String productName;

    @Column(name = "seller_name", length = 100)
    private String sellerName;

    @Column(name = "seller_phone", length = 20)
    private String sellerPhone;

    @Column(nullable = false, length = 20)
    private String buyerPhone;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    @Column(nullable = false, precision = 13, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(name = "price_per_unit", precision = 12, scale = 2)
    private BigDecimal pricePerUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Fulfillment fulfillment;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_stage", length = 24)
    private FulfillmentStage fulfillmentStage = FulfillmentStage.REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 12)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 12)
    private PaymentStatus paymentStatus;

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
    public Long getBuyerUserId() { return buyerUserId; }
    public void setBuyerUserId(Long value) { buyerUserId = value; }
    public Long getBuyerLastReadMessageId() { return buyerLastReadMessageId == null ? 0L : buyerLastReadMessageId; }
    public void setBuyerLastReadMessageId(Long value) { buyerLastReadMessageId = value; }
    public Long getSellerLastReadMessageId() { return sellerLastReadMessageId == null ? 0L : sellerLastReadMessageId; }
    public void setSellerLastReadMessageId(Long value) { sellerLastReadMessageId = value; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String value) { buyerName = value; }
    public String getProductName() { return productName; }
    public void setProductName(String value) { productName = value; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String value) { sellerName = value; }
    public String getSellerPhone() { return sellerPhone; }
    public void setSellerPhone(String value) { sellerPhone = value; }
    public String getBuyerPhone() { return buyerPhone; }
    public void setBuyerPhone(String value) { buyerPhone = value; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String value) { deliveryAddress = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal value) { pricePerUnit = value; }
    public Fulfillment getFulfillment() { return fulfillment; }
    public void setFulfillment(Fulfillment value) { fulfillment = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public FulfillmentStage getFulfillmentStage() { return fulfillmentStage; }
    public void setFulfillmentStage(FulfillmentStage value) { fulfillmentStage = value; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod value) { paymentMethod = value; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus value) { paymentStatus = value; }
    public Instant getCreatedAt() { return createdAt; }
}
