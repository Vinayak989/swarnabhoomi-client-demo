package in.swarnabhoomi.farmlog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceEngagementController {
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?91[\\s-]?)?[6-9](?:[\\s-]?\\d){9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern URL = Pattern.compile("(?i)(https?://|www\\.)\\S+");
    private static final Pattern CONTACT_WORDS = Pattern.compile(
            "(?i)(whats ?app|व्हॉट्सअॅप|मोबाईल|मोबाइल|फोन नंबर|फोन क्रमांक|पत्ता पाठवा|पिनकोड)");

    private final MarketplacePurchaseRequestRepository orders;
    private final FarmProductListingRepository listings;
    private final MarketplaceOrderMessageRepository messages;
    private final MarketplaceConversationBlockRepository blocks;
    private final MarketplaceMessageReportRepository reports;
    private final MarketplaceOrderReviewRepository reviews;
    private final MarketplacePriceAlertRepository alerts;
    private final BrowserPushService pushService;

    public MarketplaceEngagementController(MarketplacePurchaseRequestRepository orders,
            FarmProductListingRepository listings, MarketplaceOrderMessageRepository messages,
            MarketplaceConversationBlockRepository blocks, MarketplaceMessageReportRepository reports,
            MarketplaceOrderReviewRepository reviews, MarketplacePriceAlertRepository alerts,
            BrowserPushService pushService) {
        this.orders = orders;
        this.listings = listings;
        this.messages = messages;
        this.blocks = blocks;
        this.reports = reports;
        this.reviews = reviews;
        this.alerts = alerts;
        this.pushService = pushService;
    }

    @GetMapping("/listings/{listingId}/reviews")
    public List<PublicSellerReview> listingReviews(@PathVariable Long listingId) {
        FarmProductListing listing = listings.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "विक्रीची नोंद सापडली नाही."));
        if (listing.getOwner() == null) return List.of();
        return reviews.findAllBySellerUserIdAndStatusOrderByCreatedAtDesc(listing.getOwner().getId(),
                        MarketplaceOrderReview.Status.APPROVED, PageRequest.of(0, 10)).stream()
                .map(review -> new PublicSellerReview(review.getRating(), review.getComment(), review.getCreatedAt()))
                .toList();
    }

    @GetMapping("/orders/{orderId}/messages")
    public ConversationResponse conversation(@PathVariable Long orderId,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        MarketplacePurchaseRequest order = requireParticipant(orderId, user);
        List<MarketplaceConversationBlock> orderBlocks = blocks.findAllByRequestId(orderId);
        boolean blockedByMe = orderBlocks.stream().anyMatch(block -> block.getUserId().equals(user.getId()));
        boolean blockedByOther = orderBlocks.stream().anyMatch(block -> !block.getUserId().equals(user.getId()));
        return new ConversationResponse(messages.findAllByRequestIdOrderByCreatedAtAsc(orderId).stream()
                .map(MessageResponse::from).toList(), blockedByMe, blockedByOther, !blockedByMe && !blockedByOther
                    && order.getStatus() != MarketplacePurchaseRequest.Status.DECLINED);
    }

    @PostMapping("/orders/{orderId}/messages")
    @Transactional
    public MessageResponse sendMessage(@PathVariable Long orderId, @RequestBody MessageCreate request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        MarketplacePurchaseRequest order = requireParticipant(orderId, user);
        if (blocks.existsByRequestIdAndUserId(orderId, user.getId())
                || blocks.findAllByRequestId(orderId).stream().anyMatch(block -> !block.getUserId().equals(user.getId()))) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "या ऑर्डरची चर्चा थांबवली आहे.");
        }
        if (order.getStatus() == MarketplacePurchaseRequest.Status.DECLINED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "नाकारलेल्या ऑर्डरवर संदेश पाठवता येत नाही.");
        }
        String body = request == null || request.body() == null ? "" : request.body().trim();
        if (body.isBlank() || body.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "संदेश १ ते १००० अक्षरांचा असावा.");
        }
        if (containsContactDetails(body)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "गोपनीयतेसाठी फोन, ईमेल, दुवा किंवा संपर्क तपशील संदेशात पाठवता येत नाहीत.");
        }
        MarketplaceOrderMessage message = new MarketplaceOrderMessage();
        message.setRequestId(orderId);
        message.setSenderUserId(user.getId());
        message.setSenderRole(user.getRole());
        message.setBody(body);
        MarketplaceOrderMessage saved = messages.save(message);
        Long recipientId = recipientUserId(order, user);
        try {
            pushService.notifyUser(recipientId, "ऑर्डरवर नवीन संदेश", "ऑर्डर #" + orderId + " साठी नवीन संदेश आला आहे.",
                    "/?tab=marketplace");
        } catch (RuntimeException ignored) {
            // Messaging remains available when a browser push subscription is unavailable.
        }
        return MessageResponse.from(saved);
    }

    @PostMapping("/orders/{orderId}/block")
    @Transactional
    public ConversationBlockResponse block(@PathVariable Long orderId,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireParticipant(orderId, user);
        if (!blocks.existsByRequestIdAndUserId(orderId, user.getId())) {
            MarketplaceConversationBlock block = new MarketplaceConversationBlock();
            block.setRequestId(orderId);
            block.setUserId(user.getId());
            blocks.save(block);
        }
        return blockState(orderId, user);
    }

    @DeleteMapping("/orders/{orderId}/block")
    @Transactional
    public ConversationBlockResponse unblock(@PathVariable Long orderId,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireParticipant(orderId, user);
        blocks.findByRequestIdAndUserId(orderId, user.getId()).ifPresent(blocks::delete);
        return blockState(orderId, user);
    }

    @PostMapping("/messages/{messageId}/report")
    @Transactional
    public MessageReportReceipt reportMessage(@PathVariable Long messageId, @RequestBody(required = false) ReportCreate request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        MarketplaceOrderMessage message = messages.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "संदेश सापडला नाही."));
        MarketplacePurchaseRequest order = requireParticipant(message.getRequestId(), user);
        if (message.getSenderUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "तुमचा स्वतःचा संदेश नोंदवता येत नाही.");
        }
        if (reports.existsByMessageIdAndReporterUserId(messageId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "हा संदेश आधीच नोंदवला आहे.");
        }
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank() || reason.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "कारण १ ते ३०० अक्षरांत लिहा.");
        }
        MarketplaceMessageReport report = new MarketplaceMessageReport();
        report.setMessageId(messageId);
        report.setRequestId(order.getId());
        report.setReporterUserId(user.getId());
        report.setReason(reason);
        reports.save(report);
        return new MessageReportReceipt("संदेश प्रशासकाकडे तपासणीसाठी पाठवला.");
    }

    @GetMapping("/price-alerts")
    public List<PriceAlertResponse> priceAlerts(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireMarketplaceAccount(user);
        return alerts.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(PriceAlertResponse::from).toList();
    }

    @PostMapping("/price-alerts")
    @Transactional
    public PriceAlertResponse createPriceAlert(@RequestBody PriceAlertCreate request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireMarketplaceAccount(user);
        if (request == null || blankOrLong(request.commodity(), 120) || tooLong(request.district(), 120)
                || request.threshold() == null || request.threshold().signum() <= 0
                || request.threshold().compareTo(new BigDecimal("9999999999.99")) > 0
                || request.threshold().stripTrailingZeros().scale() > 2
                || request.direction() == null || request.unit() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "भाव सूचना तयार करण्यासाठी पीक आणि मर्यादा तपासा.");
        }
        MarketplacePriceAlert alert = new MarketplacePriceAlert();
        alert.setUserId(user.getId());
        alert.setCommodity(request.commodity().trim());
        alert.setDistrict(trimToNull(request.district()));
        alert.setThreshold(request.threshold().setScale(2, RoundingMode.HALF_UP));
        alert.setDirection(request.direction());
        alert.setUnit(request.unit());
        return PriceAlertResponse.from(alerts.save(alert));
    }

    @DeleteMapping("/price-alerts/{alertId}")
    @Transactional
    public void deletePriceAlert(@PathVariable Long alertId,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireMarketplaceAccount(user);
        MarketplacePriceAlert alert = alerts.findByIdAndUserId(alertId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "भाव सूचना सापडली नाही."));
        alerts.delete(alert);
    }

    @PostMapping("/orders/{orderId}/review")
    @Transactional
    public ReviewReceipt reviewOrder(@PathVariable Long orderId, @RequestBody ReviewCreate request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        MarketplacePurchaseRequest order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ऑर्डर सापडला नाही."));
        requireParticipant(orderId, user);
        if (!user.getId().equals(order.getBuyerUserId()) || user.getId().equals(sellerUserId(order))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "फक्त ऑर्डर देणारा खरेदीदार अभिप्राय देऊ शकतो.");
        }
        if (order.getStatus() != MarketplacePurchaseRequest.Status.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "व्यवहार पूर्ण झाल्यावरच रेटिंग देता येते.");
        }
        if (reviews.existsByRequestIdAndBuyerUserId(orderId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "या ऑर्डरसाठी रेटिंग आधीच दिले आहे.");
        }
        if (request == null || request.rating() == null || request.rating() < 1 || request.rating() > 5
                || tooLong(request.comment(), 500) || request.comment() != null && containsContactDetails(request.comment())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "१ ते ५ तारे निवडा. अभिप्रायात संपर्क तपशील लिहू नका.");
        }
        MarketplaceOrderReview review = new MarketplaceOrderReview();
        review.setRequestId(orderId);
        review.setBuyerUserId(user.getId());
        review.setSellerUserId(sellerUserId(order));
        review.setListingId(order.getListingId());
        review.setRating(request.rating());
        review.setComment(trimToNull(request.comment()));
        reviews.save(review);
        return new ReviewReceipt("धन्यवाद! प्रशासकाच्या तपासणीनंतर तुमचे रेटिंग दिसेल.");
    }

    private MarketplacePurchaseRequest requireParticipant(Long orderId, AppUser user) {
        MarketplacePurchaseRequest order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ऑर्डर सापडला नाही."));
        Long sellerId = sellerUserId(order);
        if (order.getBuyerUserId() == null || sellerId == null
                || !user.getId().equals(order.getBuyerUserId()) && !user.getId().equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "या ऑर्डरची चर्चा पाहण्याची परवानगी नाही.");
        }
        return order;
    }

    private Long sellerUserId(MarketplacePurchaseRequest order) {
        return listings.findById(order.getListingId()).map(FarmProductListing::getOwner)
                .map(AppUser::getId).orElse(null);
    }

    private Long recipientUserId(MarketplacePurchaseRequest order, AppUser sender) {
        Long buyerId = order.getBuyerUserId();
        Long sellerId = sellerUserId(order);
        return sender.getId().equals(buyerId) ? sellerId : buyerId;
    }

    private ConversationBlockResponse blockState(Long orderId, AppUser user) {
        List<MarketplaceConversationBlock> orderBlocks = blocks.findAllByRequestId(orderId);
        return new ConversationBlockResponse(orderBlocks.stream().anyMatch(block -> block.getUserId().equals(user.getId())),
                orderBlocks.stream().anyMatch(block -> !block.getUserId().equals(user.getId())));
    }

    private static void requireMarketplaceAccount(AppUser user) {
        if (user == null || !List.of("farmer", "seller", "buyer").contains(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "या सुविधेसाठी खरेदीदार किंवा विक्रेता खाते आवश्यक आहे.");
        }
    }

    private static boolean containsContactDetails(String value) {
        return value != null && (containsPhoneNumber(value) || EMAIL.matcher(value).find() || URL.matcher(value).find()
                || CONTACT_WORDS.matcher(value).find());
    }

    private static boolean containsPhoneNumber(String value) {
        if (PHONE.matcher(value).find()) return true;
        String digits = value.replaceAll("\\D", "");
        for (int index = 0; index + 10 <= digits.length(); index++) {
            if (digits.charAt(index) >= '6' && digits.charAt(index) <= '9') return true;
        }
        return false;
    }

    private static boolean blankOrLong(String value, int limit) { return value == null || value.isBlank() || value.length() > limit; }
    private static boolean tooLong(String value, int limit) { return value != null && value.length() > limit; }
    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record MessageCreate(String body) {}
    public record MessageResponse(Long id, Long requestId, Long senderUserId, String senderRole, String body, Instant createdAt) {
        static MessageResponse from(MarketplaceOrderMessage message) {
            return new MessageResponse(message.getId(), message.getRequestId(), message.getSenderUserId(),
                    message.getSenderRole(), message.getBody(), message.getCreatedAt());
        }
    }
    public record ConversationResponse(List<MessageResponse> messages, boolean blockedByMe,
            boolean blockedByOther, boolean canMessage) {}
    public record ConversationBlockResponse(boolean blockedByMe, boolean blockedByOther) {}
    public record ReportCreate(String reason) {}
    public record MessageReportReceipt(String message) {}
    public record PriceAlertCreate(String commodity, String district, BigDecimal threshold,
            MarketplacePriceAlert.Direction direction, MarketplacePriceAlert.PriceUnit unit) {}
    public record PriceAlertResponse(Long id, String commodity, String district, BigDecimal threshold,
            MarketplacePriceAlert.Direction direction, MarketplacePriceAlert.PriceUnit unit,
            boolean currentlyMatched, Instant lastTriggeredAt, BigDecimal lastPrice) {
        static PriceAlertResponse from(MarketplacePriceAlert alert) {
            return new PriceAlertResponse(alert.getId(), alert.getCommodity(), alert.getDistrict(), alert.getThreshold(),
                    alert.getDirection(), alert.getUnit(), alert.isCurrentlyMatched(), alert.getLastTriggeredAt(), alert.getLastPrice());
        }
    }
    public record ReviewCreate(Integer rating, String comment) {}
    public record ReviewReceipt(String message) {}
    public record PublicSellerReview(Integer rating, String comment, Instant createdAt) {}
}
