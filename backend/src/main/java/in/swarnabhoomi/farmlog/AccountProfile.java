package in.swarnabhoomi.farmlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "account_profile", uniqueConstraints = @UniqueConstraint(
        name = "uk_account_profile_user", columnNames = "user_id"))
public class AccountProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(length = 120)
    private String fullName;

    @Column(length = 160)
    private String businessName;

    @Column(length = 120)
    private String village;

    @Column(length = 120)
    private String taluka;

    @Column(length = 120)
    private String district;

    @Column(length = 120)
    private String state;

    @Column(length = 6)
    private String pincode;

    @Column(length = 500)
    private String address;

    @Column(length = 800)
    private String about;

    @Column(length = 24)
    private String organicCertificationSystem;

    @Column(length = 120)
    private String organicCertificationNumber;

    private LocalDate organicCertificateValidUntil;

    @Column(nullable = false, length = 24)
    private String organicVerificationStatus = "NOT_CLAIMED";

    @Column(length = 500)
    private String organicVerificationNote;

    private Instant organicVerificationCheckedAt;
    private Long organicVerificationCheckedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AccountProfile() {}

    public AccountProfile(Long userId) {
        this.userId = userId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public void setFullName(String value) { fullName = value; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String value) { businessName = value; }
    public String getVillage() { return village; }
    public void setVillage(String value) { village = value; }
    public String getTaluka() { return taluka; }
    public void setTaluka(String value) { taluka = value; }
    public String getDistrict() { return district; }
    public void setDistrict(String value) { district = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getPincode() { return pincode; }
    public void setPincode(String value) { pincode = value; }
    public String getAddress() { return address; }
    public void setAddress(String value) { address = value; }
    public String getAbout() { return about; }
    public void setAbout(String value) { about = value; }
    public String getOrganicCertificationSystem() { return organicCertificationSystem; }
    public void setOrganicCertificationSystem(String value) { organicCertificationSystem = value; }
    public String getOrganicCertificationNumber() { return organicCertificationNumber; }
    public void setOrganicCertificationNumber(String value) { organicCertificationNumber = value; }
    public LocalDate getOrganicCertificateValidUntil() { return organicCertificateValidUntil; }
    public void setOrganicCertificateValidUntil(LocalDate value) { organicCertificateValidUntil = value; }
    public String getOrganicVerificationStatus() { return organicVerificationStatus; }
    public void setOrganicVerificationStatus(String value) { organicVerificationStatus = value; }
    public String getOrganicVerificationNote() { return organicVerificationNote; }
    public void setOrganicVerificationNote(String value) { organicVerificationNote = value; }
    public Instant getOrganicVerificationCheckedAt() { return organicVerificationCheckedAt; }
    public void setOrganicVerificationCheckedAt(Instant value) { organicVerificationCheckedAt = value; }
    public Long getOrganicVerificationCheckedBy() { return organicVerificationCheckedBy; }
    public void setOrganicVerificationCheckedBy(Long value) { organicVerificationCheckedBy = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
