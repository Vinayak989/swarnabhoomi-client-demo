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
@Table(name = "support_thread", uniqueConstraints = @UniqueConstraint(
        name = "uk_support_thread_participant", columnNames = "participant_user_id"))
public class SupportThread {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long participantUserId;

    @Column(nullable = false, updatable = false, length = 16)
    private String participantRole;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected SupportThread() {}

    public SupportThread(Long participantUserId, String participantRole) {
        this.participantUserId = participantUserId;
        this.participantRole = participantRole;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    public void touch() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public Long getParticipantUserId() { return participantUserId; }
    public String getParticipantRole() { return participantRole; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
