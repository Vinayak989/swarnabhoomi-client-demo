package in.swarnabhoomi.farmlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "marketplace_conversation_block",
        uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "user_id"}))
public class MarketplaceConversationBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long value) { requestId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public Instant getCreatedAt() { return createdAt; }
}
