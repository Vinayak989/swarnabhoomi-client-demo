package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "organic_consultant")
public class OrganicConsultant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 120)
    private String serviceArea;

    @Column(nullable = false, length = 300)
    private String expertise;

    @Column(nullable = false)
    private int experienceYears;

    @Column(length = 800)
    private String introduction;

    @JsonIgnore
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "owner_id")
    private AppUser owner;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getPhone() { return phone; }
    public void setPhone(String value) { phone = value; }
    public String getServiceArea() { return serviceArea; }
    public void setServiceArea(String value) { serviceArea = value; }
    public String getExpertise() { return expertise; }
    public void setExpertise(String value) { expertise = value; }
    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int value) { experienceYears = value; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String value) { introduction = value; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser value) { owner = value; }
    public Instant getCreatedAt() { return createdAt; }
}
