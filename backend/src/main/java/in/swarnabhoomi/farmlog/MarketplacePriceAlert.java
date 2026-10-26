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
@Table(name = "marketplace_price_alert")
public class MarketplacePriceAlert {
    public enum Direction { BELOW, ABOVE }
    public enum PriceUnit { KG, QUINTAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String commodity;

    @Column(length = 120)
    private String district;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PriceUnit unit = PriceUnit.QUINTAL;

    @Column(nullable = false)
    private boolean currentlyMatched;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "last_price", precision = 12, scale = 2)
    private BigDecimal lastPrice;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public String getCommodity() { return commodity; }
    public void setCommodity(String value) { commodity = value; }
    public String getDistrict() { return district; }
    public void setDistrict(String value) { district = value; }
    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal value) { threshold = value; }
    public Direction getDirection() { return direction; }
    public void setDirection(Direction value) { direction = value; }
    public PriceUnit getUnit() { return unit; }
    public void setUnit(PriceUnit value) { unit = value; }
    public boolean isCurrentlyMatched() { return currentlyMatched; }
    public void setCurrentlyMatched(boolean value) { currentlyMatched = value; }
    public Instant getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(Instant value) { lastTriggeredAt = value; }
    public BigDecimal getLastPrice() { return lastPrice; }
    public void setLastPrice(BigDecimal value) { lastPrice = value; }
    public Instant getCreatedAt() { return createdAt; }
}
