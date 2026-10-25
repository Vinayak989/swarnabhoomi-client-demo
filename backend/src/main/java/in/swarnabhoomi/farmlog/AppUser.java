package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(
        name = "uk_app_user_mobile_role", columnNames = {"mobile_number", "account_role"}))
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mobile_number", nullable = false, length = 16)
    private String mobileNumber;

    @Column(name = "account_role", nullable = false, length = 20)
    private String role;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {}

    public AppUser(String mobileNumber, String role, String passwordHash) {
        this.mobileNumber = mobileNumber;
        this.role = role;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getMobileNumber() { return mobileNumber; }
    public String getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
}
