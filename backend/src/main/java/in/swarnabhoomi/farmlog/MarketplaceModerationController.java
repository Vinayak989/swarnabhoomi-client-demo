package in.swarnabhoomi.farmlog;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/marketplace")
@Transactional(readOnly = true)
public class MarketplaceModerationController {
    private static final int MAX_PAGE_SIZE = 100;

    private final MarketplaceMessageReportRepository reports;
    private final MarketplaceOrderMessageRepository messages;
    private final MarketplaceOrderReviewRepository reviews;

    public MarketplaceModerationController(MarketplaceMessageReportRepository reports,
            MarketplaceOrderMessageRepository messages, MarketplaceOrderReviewRepository reviews) {
        this.reports = reports;
        this.messages = messages;
        this.reviews = reviews;
    }

    @GetMapping("/message-reports")
    public Page<MessageReportRecord> messageReports(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        return reports.findAllByStatusOrderByCreatedAtDesc(MarketplaceMessageReport.Status.PENDING,
                pageRequest(page, size)).map(report -> {
                    MarketplaceOrderMessage message = messages.findById(report.getMessageId()).orElse(null);
                    return new MessageReportRecord(report.getId(), report.getRequestId(), report.getMessageId(),
                            message == null ? "संदेश उपलब्ध नाही" : message.getBody(),
                            message == null ? null : message.getSenderRole(), report.getReporterUserId(),
                            report.getReason(), report.getCreatedAt());
                });
    }

    @PatchMapping("/message-reports/{id}")
    @Transactional
    public ModerationReceipt updateMessageReport(@PathVariable Long id, @RequestBody ModerationUpdate update,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        MarketplaceMessageReport report = reports.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "तक्रार सापडली नाही."));
        MarketplaceMessageReport.Status status;
        try {
            status = MarketplaceMessageReport.Status.valueOf(update == null || update.status() == null
                    ? "" : update.status().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "तक्रारीचा निकाल निवडा.");
        }
        if (status == MarketplaceMessageReport.Status.PENDING || update.note() != null && update.note().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "तक्रारीचा निकाल आणि नोंद तपासा.");
        }
        report.setStatus(status);
        report.setReviewNote(update.note() == null || update.note().isBlank() ? null : update.note().trim());
        report.setReviewedBy(user.getId());
        report.setReviewedAt(Instant.now());
        reports.save(report);
        return new ModerationReceipt("संदेश तक्रारीचा निकाल जतन केला.");
    }

    @GetMapping("/reviews")
    public Page<ReviewRecord> reviews(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        return reviews.findAllByStatusOrderByCreatedAtDesc(MarketplaceOrderReview.Status.PENDING,
                pageRequest(page, size)).map(review -> new ReviewRecord(review.getId(), review.getRequestId(),
                        review.getListingId(), review.getBuyerUserId(), review.getSellerUserId(), review.getRating(),
                        review.getComment(), review.getCreatedAt()));
    }

    @PatchMapping("/reviews/{id}")
    @Transactional
    public ModerationReceipt updateReview(@PathVariable Long id, @RequestBody ModerationUpdate update,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        MarketplaceOrderReview review = reviews.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "रेटिंग सापडले नाही."));
        MarketplaceOrderReview.Status status;
        try {
            status = MarketplaceOrderReview.Status.valueOf(update == null || update.status() == null
                    ? "" : update.status().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "रेटिंगचा निकाल निवडा.");
        }
        if (status == MarketplaceOrderReview.Status.PENDING || update.note() != null && update.note().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "रेटिंगचा निकाल आणि नोंद तपासा.");
        }
        review.setStatus(status);
        review.setReviewedBy(user.getId());
        review.setReviewedAt(Instant.now());
        reviews.save(review);
        return new ModerationReceipt("रेटिंगचा निकाल जतन केला.");
    }

    private static Pageable pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
    }

    private static void requireAdmin(AppUser user) {
        if (user == null || !"admin".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "या माहितीसाठी प्रशासक प्रवेश आवश्यक आहे.");
        }
    }

    public record MessageReportRecord(Long id, Long orderId, Long messageId, String message,
            String senderRole, Long reporterUserId, String reason, Instant createdAt) {}
    public record ReviewRecord(Long id, Long orderId, Long listingId, Long buyerUserId, Long sellerUserId,
            Integer rating, String comment, Instant createdAt) {}
    public record ModerationUpdate(String status, String note) {}
    public record ModerationReceipt(String message) {}
}
