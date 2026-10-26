package in.swarnabhoomi.farmlog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultantContactPaymentRepository extends JpaRepository<ConsultantContactPayment, Long> {
    Optional<ConsultantContactPayment> findByRazorpayOrderId(String razorpayOrderId);
    Optional<ConsultantContactPayment> findTopBySeller_IdAndConsultant_IdAndStatusOrderByCreatedAtDesc(
            Long sellerId, Long consultantId, ConsultantContactPayment.Status status);
    List<ConsultantContactPayment> findAllBySeller_IdAndStatus(Long sellerId,
            ConsultantContactPayment.Status status);
    boolean existsBySeller_IdAndConsultant_IdAndStatus(Long sellerId, Long consultantId,
            ConsultantContactPayment.Status status);
}
