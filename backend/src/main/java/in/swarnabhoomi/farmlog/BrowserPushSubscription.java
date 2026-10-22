package in.swarnabhoomi.farmlog;

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

    public Long getId() { return id; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String value) { endpoint = value; }
    public String getP256dhKey() { return p256dhKey; }
    public void setP256dhKey(String value) { p256dhKey = value; }
    public String getAuthKey() { return authKey; }
    public void setAuthKey(String value) { authKey = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
}
