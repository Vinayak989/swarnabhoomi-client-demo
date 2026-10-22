package in.swarnabhoomi.farmlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
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

    private Boolean smsConsent = false;

    private Boolean callConsent = false;

    @Column(length = 300)
    private String deliveryNote;

    private LocalDateTime sentAt;

    @ManyToOne
    private BrowserPushSubscription browserSubscription;

    private Boolean whatsappConsent = true;

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
    public boolean isSmsConsent() { return Boolean.TRUE.equals(smsConsent); }
    public void setSmsConsent(boolean value) { smsConsent = value; }
    public boolean isCallConsent() { return Boolean.TRUE.equals(callConsent); }
    public void setCallConsent(boolean value) { callConsent = value; }
    public String getDeliveryNote() { return deliveryNote; }
    public void setDeliveryNote(String value) { deliveryNote = value; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime value) { sentAt = value; }
    public BrowserPushSubscription getBrowserSubscription() { return browserSubscription; }
    public void setBrowserSubscription(BrowserPushSubscription value) { browserSubscription = value; }
    public boolean isWhatsappConsent() { return whatsappConsent == null || whatsappConsent; }
    public void setWhatsappConsent(boolean value) { whatsappConsent = value; }
}
