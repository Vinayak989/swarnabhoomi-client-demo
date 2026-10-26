package in.swarnabhoomi.farmlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "consultant_contact_payment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_consultant_contact_seller_advisor", columnNames = {"seller_id", "consultant_id"}),
        @UniqueConstraint(name = "uk_consultant_contact_order", columnNames = "razorpay_order_id"),
        @UniqueConstraint(name = "uk_consultant_contact_payment", columnNames = "razorpay_payment_id")
})
public class ConsultantContactPayment {
    public enum Status { PENDING, PAID, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private AppUser seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultant_id", nullable = false)
    private OrganicConsultant consultant;

    @Column(name = "razorpay_order_id", nullable = false, length = 80)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 80)
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant paidAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getSeller() { return seller; }
    public void setSeller(AppUser value) { seller = value; }
    public OrganicConsultant getConsultant() { return consultant; }
    public void setConsultant(OrganicConsultant value) { consultant = value; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String value) { razorpayOrderId = value; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void setRazorpayPaymentId(String value) { razorpayPaymentId = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant value) { paidAt = value; }
}
