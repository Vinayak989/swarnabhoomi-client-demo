package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "browser_push_subscription")
public class BrowserPushSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 2048)
    private String endpoint;

    @Column(nullable = false, length = 256)
    private String p256dhKey;

    @Column(nullable = false, length = 128)
    private String authKey;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean androidApp;

    @JsonIgnore
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.JoinColumn(name = "owner_id")
    private AppUser owner;

    public Long getId() { return id; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String value) { endpoint = value; }
    public String getP256dhKey() { return p256dhKey; }
    public void setP256dhKey(String value) { p256dhKey = value; }
    public String getAuthKey() { return authKey; }
    public void setAuthKey(String value) { authKey = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public boolean isAndroidApp() { return androidApp; }
    public void setAndroidApp(boolean value) { androidApp = value; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser value) { owner = value; }
}
