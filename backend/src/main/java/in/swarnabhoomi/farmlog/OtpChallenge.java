package in.swarnabhoomi.farmlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "otp_challenge")
public class OtpChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String mobileNumber;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 100)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean consumed;

    protected OtpChallenge() {}

    public OtpChallenge(String mobileNumber, String role, String codeHash, Instant expiresAt) {
        this.mobileNumber = mobileNumber;
        this.role = role;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getMobileNumber() { return mobileNumber; }
    public String getRole() { return role; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAttempts() { return attempts; }
    public void incrementAttempts() { attempts++; }
    public boolean isConsumed() { return consumed; }
    public void setConsumed(boolean value) { consumed = value; }
}
