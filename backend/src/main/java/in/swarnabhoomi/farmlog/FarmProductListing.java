package in.swarnabhoomi.farmlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "farm_product_listing")
public class FarmProductListing {
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

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(length = 1000)
    private String description;

    @Column(length = 100)
    private String photoUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

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
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String value) { photoUrl = value; }
    public Instant getCreatedAt() { return createdAt; }
}
