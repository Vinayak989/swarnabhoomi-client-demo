package in.swarnabhoomi.farmlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "farm_product_listing")
public class FarmProductListing {
    private Instant offerStartsAt;
    private Instant offerEndsAt;
    private java.time.LocalDate harvestDate;
    @Column(length=80) private String batchNumber;
    @Column(length=1000) private String traceabilityNotes;
    @Column(nullable=false) private boolean traceabilityPublic;
    @Column(length=36) private String traceabilityToken;
    public Instant getOfferStartsAt(){return offerStartsAt;} public void setOfferStartsAt(Instant v){offerStartsAt=v;}
    public Instant getOfferEndsAt(){return offerEndsAt;} public void setOfferEndsAt(Instant v){offerEndsAt=v;}
    public java.time.LocalDate getHarvestDate(){return harvestDate;} public void setHarvestDate(java.time.LocalDate v){harvestDate=v;}
    public String getBatchNumber(){return batchNumber;} public void setBatchNumber(String v){batchNumber=v;}
    public String getTraceabilityNotes(){return traceabilityNotes;} public void setTraceabilityNotes(String v){traceabilityNotes=v;}
    public boolean isTraceabilityPublic(){return traceabilityPublic;} public void setTraceabilityPublic(boolean v){traceabilityPublic=v;}
    public String getTraceabilityToken(){return traceabilityToken;} public void setTraceabilityToken(String v){traceabilityToken=v;}
    public BigDecimal effectiveDiscount(){Instant now=Instant.now();return (offerStartsAt!=null&&now.isBefore(offerStartsAt))||(offerEndsAt!=null&&!now.isBefore(offerEndsAt))?BigDecimal.ZERO:discountPercent;}
    public BigDecimal effectivePrice(){return FarmMarketplaceController.discountedPrice(pricePerUnit,effectiveDiscount());}
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String sellerName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, length = 80)
    private String cropType;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false)
    private double quantity;

    @Column(precision = 12, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column
    private Boolean available = true;

    @Column(length = 64)
    private String sellerTokenHash;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(length = 1000)
    private String description;

    @Column(length = 100)
    private String photoUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "owner_id")
    private AppUser owner;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String value) { sellerName = value; }
    public String getPhone() { return phone; }
    public void setPhone(String value) { phone = value; }
    public String getAddress() { return address; }
    public void setAddress(String value) { address = value; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double value) { latitude = value; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double value) { longitude = value; }
    public String getProductName() { return productName; }
    public void setProductName(String value) { productName = value; }
    public String getCropType() { return cropType; }
    public void setCropType(String value) { cropType = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double value) { quantity = value; }
    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal value) { pricePerUnit = value; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal value) { discountPercent = value; }
    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean value) { available = value; }
    public String getSellerTokenHash() { return sellerTokenHash; }
    public void setSellerTokenHash(String value) { sellerTokenHash = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String value) { photoUrl = value; }
    public Instant getCreatedAt() { return createdAt; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser value) { owner = value; }
}
