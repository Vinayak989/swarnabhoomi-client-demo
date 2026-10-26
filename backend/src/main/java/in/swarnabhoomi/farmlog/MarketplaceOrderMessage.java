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
@Table(name = "marketplace_order_message")
public class MarketplaceOrderMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private Long senderUserId;

    @Column(nullable = false, length = 16)
    private String senderRole;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long value) { requestId = value; }
    public Long getSenderUserId() { return senderUserId; }
    public void setSenderUserId(Long value) { senderUserId = value; }
    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String value) { senderRole = value; }
    public String getBody() { return body; }
    public void setBody(String value) { body = value; }
    public Instant getCreatedAt() { return createdAt; }
}
