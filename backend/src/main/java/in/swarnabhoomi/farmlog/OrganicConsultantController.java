package in.swarnabhoomi.farmlog;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/consultants")
public class OrganicConsultantController {
    private static final Pattern INDIAN_PHONE = Pattern.compile("^(?:\\+91)?[6-9][0-9]{9}$");
    private final OrganicConsultantRepository repository;
    private final ConsultantContactPaymentRepository payments;
    private final ConsultantContactPaymentService paymentService;

    public OrganicConsultantController(OrganicConsultantRepository repository,
            ConsultantContactPaymentRepository payments, ConsultantContactPaymentService paymentService) {
        this.repository = repository;
        this.payments = payments;
        this.paymentService = paymentService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ConsultantResponse> consultants(
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireDirectoryAccount(user);
        boolean seller = "seller".equals(user.getRole());
        Set<Long> unlockedConsultantIds = seller
                ? payments.findAllBySeller_IdAndStatus(user.getId(), ConsultantContactPayment.Status.PAID).stream()
                        .map(payment -> payment.getConsultant().getId()).collect(java.util.stream.Collectors.toSet())
                : Set.of();
        return repository.findTop200ByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(consultant -> {
                    boolean unlocked = unlockedConsultantIds.contains(consultant.getId());
                    boolean canSeeContact = !seller || unlocked;
                    return ConsultantResponse.from(consultant, canSeeContact, unlocked,
                            seller && paymentService.isConfigured());
                })
                .toList();
    }

    @PostMapping("/{consultantId}/contact-payment/order")
    @Transactional
    public ContactPaymentOrder createContactPaymentOrder(@PathVariable Long consultantId,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireSeller(user);
        OrganicConsultant consultant = repository.findActiveByIdForUpdate(consultantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "सल्लागार सापडला नाही."));
        if (payments.existsBySeller_IdAndConsultant_IdAndStatus(user.getId(), consultantId,
                ConsultantContactPayment.Status.PAID)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "या सल्लागाराचा संपर्क आधीच खुला आहे.");
        }

        ConsultantContactPayment payment = payments
                .findTopBySeller_IdAndConsultant_IdAndStatusOrderByCreatedAtDesc(user.getId(), consultantId,
                        ConsultantContactPayment.Status.PENDING)
                .orElse(null);
        if (payment == null) {
            String receipt = "consult-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            String orderId = paymentService.createOrder(receipt, user.getId(), consultantId);
            payment = new ConsultantContactPayment();
            payment.setSeller(user);
            payment.setConsultant(consultant);
            payment.setRazorpayOrderId(orderId);
            payment.setStatus(ConsultantContactPayment.Status.PENDING);
            payment = payments.save(payment);
        }
        return new ContactPaymentOrder(paymentService.keyId(), payment.getRazorpayOrderId(),
                ConsultantContactPaymentService.AMOUNT_PAISE, "INR");
    }

    @PostMapping("/{consultantId}/contact-payment/verify")
    @Transactional
    public ContactPaymentVerification verifyContactPayment(@PathVariable Long consultantId,
            @RequestBody PaymentVerification request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireSeller(user);
        if (request == null || request.orderId() == null || request.paymentId() == null
                || request.signature() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "पेमेंटची माहिती अपूर्ण आहे.");
        }
        ConsultantContactPayment payment = payments.findByRazorpayOrderId(request.orderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "पेमेंट ऑर्डर सापडली नाही."));
        if (!user.getId().equals(payment.getSeller().getId())
                || !consultantId.equals(payment.getConsultant().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "या पेमेंटची परवानगी नाही.");
        }
        if (payment.getStatus() == ConsultantContactPayment.Status.PAID) {
            return paymentVerification(payment, "सल्लागाराचा संपर्क खुला झाला आहे.");
        }
        paymentService.verifyCapturedPayment(payment.getRazorpayOrderId(), request.paymentId(), request.signature());
        payment.setRazorpayPaymentId(request.paymentId());
        payment.setStatus(ConsultantContactPayment.Status.PAID);
        payment.setPaidAt(java.time.Instant.now());
        payments.save(payment);
        return paymentVerification(payment, "₹९९ पेमेंट यशस्वी. सल्लागाराचा संपर्क खुला झाला आहे.");
    }

    private static ContactPaymentVerification paymentVerification(ConsultantContactPayment payment, String message) {
        if (payment.getConsultant().getDeletedAt() != null) {
            return new ContactPaymentVerification(false,
                    "पेमेंटची नोंद जतन झाली आहे. हा सल्लागार यादीत उपलब्ध नाही. मदतीतून प्रशासकाशी संपर्क करा.");
        }
        return new ContactPaymentVerification(true, message);
    }

    @PostMapping
    public ResponseEntity<ConsultantResponse> register(@RequestBody ConsultantRequest request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        if (!"admin".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "सल्लागार नोंदणीसाठी प्रशासक प्रवेश आवश्यक आहे.");
        }
        validate(request);
        OrganicConsultant consultant = new OrganicConsultant();
        consultant.setOwner(user);
        consultant.setName(request.name().trim());
        consultant.setPhone(request.phone().replaceAll("[\\s-]", ""));
        consultant.setServiceArea(request.serviceArea().trim());
        consultant.setExpertise(request.expertise().trim());
        consultant.setExperienceYears(request.experienceYears());
        consultant.setIntroduction(trimToNull(request.introduction()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConsultantResponse.from(repository.save(consultant), true, false, false));
    }

    private static void validate(ConsultantRequest request) {
        if (request == null
                || blankOrLong(request.name(), 100)
                || request.phone() == null
                || !INDIAN_PHONE.matcher(request.phone().replaceAll("[\\s-]", "")).matches()
                || blankOrLong(request.serviceArea(), 120)
                || blankOrLong(request.expertise(), 300)
                || request.experienceYears() == null
                || request.experienceYears() < 0 || request.experienceYears() > 80
                || tooLong(request.introduction(), 800)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "सल्लागाराची माहिती तपासा आणि आवश्यक तपशील भरा.");
        }
    }

    private static boolean blankOrLong(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    private static boolean tooLong(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireDirectoryAccount(AppUser user) {
        if (user == null || !Set.of("farmer", "seller", "admin", "consultant").contains(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "सल्लागार पाहण्यासाठी योग्य खाते आवश्यक आहे.");
        }
    }

    private static void requireSeller(AppUser user) {
        if (user == null || !"seller".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "सल्लागाराशी संपर्कासाठी विक्रेता खाते आवश्यक आहे.");
        }
    }

    public record ConsultantRequest(
            String name,
            String phone,
            String serviceArea,
            String expertise,
            Integer experienceYears,
            String introduction) {}

    public record ConsultantResponse(
            Long id,
            String name,
            String phone,
            String serviceArea,
            String expertise,
            int experienceYears,
            String introduction,
            boolean contactUnlocked,
            boolean contactPaymentEnabled) {
        static ConsultantResponse from(OrganicConsultant consultant, boolean includeContact,
                boolean contactUnlocked, boolean paymentEnabled) {
            return new ConsultantResponse(
                    consultant.getId(),
                    consultant.getName(),
                    includeContact ? consultant.getPhone() : null,
                    consultant.getServiceArea(),
                    consultant.getExpertise(),
                    consultant.getExperienceYears(),
                    consultant.getIntroduction(),
                    contactUnlocked,
                    paymentEnabled);
        }
    }

    public record ContactPaymentOrder(String keyId, String orderId, int amount, String currency) {}
    public record PaymentVerification(String orderId, String paymentId, String signature) {}
    public record ContactPaymentVerification(boolean unlocked, String message) {}
}
