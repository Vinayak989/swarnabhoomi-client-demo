package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "farm_reminder")
public class FarmReminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(nullable = false, length = 16)
    private String mobileNumber;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(length = 300)
    private String deliveryNote;

    private LocalDateTime sentAt;

    private Integer deliveryAttempts;
    private LocalDateTime nextDeliveryAt;

    @ManyToOne
    private BrowserPushSubscription browserSubscription;

    @JsonIgnore
    @ManyToOne(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.JoinColumn(name = "owner_id")
    private AppUser owner;

    @Column(length = 120)
    private String cropOrPlot;

    @Column(length = 10)
    private String repeatFrequency = "NONE";

    private LocalDate repeatUntil;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String value) { mobileNumber = value; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime value) { scheduledAt = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getDeliveryNote() { return deliveryNote; }
    public void setDeliveryNote(String value) { deliveryNote = value; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime value) { sentAt = value; }
    public int getDeliveryAttempts() { return deliveryAttempts == null ? 0 : deliveryAttempts; }
    public void setDeliveryAttempts(int value) { deliveryAttempts = value; }
    public LocalDateTime getNextDeliveryAt() { return nextDeliveryAt; }
    public void setNextDeliveryAt(LocalDateTime value) { nextDeliveryAt = value; }
    public BrowserPushSubscription getBrowserSubscription() { return browserSubscription; }
    public void setBrowserSubscription(BrowserPushSubscription value) { browserSubscription = value; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser value) { owner = value; }
    public String getCropOrPlot() { return cropOrPlot; }
    public void setCropOrPlot(String value) { cropOrPlot = value; }
    public String getRepeatFrequency() { return repeatFrequency; }
    public void setRepeatFrequency(String value) { repeatFrequency = value; }
    public LocalDate getRepeatUntil() { return repeatUntil; }
    public void setRepeatUntil(LocalDate value) { repeatUntil = value; }
}
