package in.swarnabhoomi.farmlog;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/marketplace")
public class FarmMarketplaceController {
    private static final Pattern INDIAN_PHONE = Pattern.compile("^(?:\\+91)?[6-9][0-9]{9}$");
    private static final Pattern PHOTO_URL = Pattern.compile("^/api/marketplace/photos/[a-fA-F0-9-]{36}$");
    private static final Set<String> UNITS = Set.of("kg", "quintal", "ton", "dozen", "crate", "piece");
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final FarmProductListingRepository repository;
    private final MarketplacePurchaseRequestRepository purchaseRequestRepository;
    private final FarmPhotoRepository photoRepository;
    private final PhotoObjectStorage photoStorage;
    private final BrowserPushService pushService;
    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private org.springframework.util.unit.DataSize maxPhotoSize;

    public FarmMarketplaceController(FarmProductListingRepository repository,
            MarketplacePurchaseRequestRepository purchaseRequestRepository,
            FarmPhotoRepository photoRepository, PhotoObjectStorage photoStorage,
            BrowserPushService pushService) {
        this.repository = repository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.photoRepository = photoRepository;
        this.photoStorage = photoStorage;
        this.pushService = pushService;
    }

    @GetMapping("/payment-options")
    public PaymentOptions paymentOptions() {
        return new PaymentOptions(true, false,
                "UPI साठी पेमेंट गेटवे आणि विक्रेता सेटलमेंट खाते सक्रिय करणे आवश्यक आहे.");
    }

    @GetMapping("/listings")
    public List<ListingResponse> listings() {
        return repository.findTop200ByOrderByCreatedAtDesc().stream().map(ListingResponse::from).toList();
    }

    @PostMapping("/photos")
    public Map<String, String> photo(@RequestParam("file") MultipartFile file,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        if (file.isEmpty() || file.getSize() > maxPhotoSize.toBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोटोचा आकार १० MB पेक्षा कमी असावा.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (!Set.of("image/jpeg", "image/png", "image/webp", "image/gif").contains(type.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JPEG, PNG, WebP किंवा GIF फोटो निवडा.");
        }
        String filename = Optional.ofNullable(file.getOriginalFilename())
                .filter(name -> !name.isBlank())
                .orElse("marketplace-photo");
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "_");
        if (filename.length() > 255) filename = filename.substring(filename.length() - 255);

        try {
            FarmPhoto photo = new FarmPhoto();
            photo.setId(photoStorage.newPhotoId());
            photo.setData(file.getBytes());
            photo.setContentType(type);
            photo.setOriginalFilename(filename);
            photo.setOwner(user);
            photo.setMarketplacePublic(true);
            photoStorage.store(photo);
            photoRepository.save(photo);
            return Map.of("url", "/api/marketplace/photos/" + photo.getId());
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोटो वाचता आला नाही.", exception);
        }
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> getPhoto(@PathVariable String id) {
        FarmPhoto photo = photoRepository.findByIdAndMarketplacePublicTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(photo.getContentType());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "फोटोचा प्रकार वाचता आला नाही.", exception);
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(photo.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(photoStorage.load(photo));
    }

    @PostMapping("/listings")
    public ResponseEntity<CreateListingResponse> create(@RequestBody ListingRequest request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        validate(request);
        FarmProductListing listing = new FarmProductListing();
        listing.setOwner(user);
        listing.setSellerName(request.sellerName().trim());
        listing.setPhone(request.phone().replaceAll("[\\s-]", ""));
        listing.setAddress(request.address().trim());
        listing.setLatitude(request.latitude());
        listing.setLongitude(request.longitude());
        listing.setProductName(request.productName().trim());
        listing.setCropType(request.cropType().trim());
        listing.setCategory(request.category().trim());
        listing.setQuantity(request.quantity());
        listing.setPricePerUnit(request.pricePerUnit().setScale(2, java.math.RoundingMode.HALF_UP));
        listing.setAvailable(true);
        listing.setUnit(request.unit().trim().toLowerCase(Locale.ROOT));
        listing.setDescription(trimToNull(request.description()));
        listing.setPhotoUrl(trimToNull(request.photoUrl()));
        String sellerToken = newSellerToken();
        listing.setSellerTokenHash(hashSellerToken(sellerToken));
        FarmProductListing saved = repository.save(listing);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateListingResponse(ListingResponse.from(saved), sellerToken));
    }

    @PostMapping("/listings/{id}/purchase-requests")
    @Transactional
    public ResponseEntity<PurchaseRequestReceipt> createPurchaseRequest(@PathVariable Long id,
            @RequestBody PurchaseRequestCreate request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser buyer) {
        FarmProductListing listing = findListingForUpdate(id);
        if (Boolean.FALSE.equals(listing.getAvailable()) || listing.getPricePerUnit() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "हा शेतमाल सध्या खरेदीसाठी उपलब्ध नाही.");
        }
        if (!validPurchaseRequest(request)
                || request.quantity().compareTo(BigDecimal.valueOf(listing.getQuantity())) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "खरेदीची माहिती तपासा. प्रमाण उपलब्ध साठ्यापेक्षा जास्त असू शकत नाही.");
        }

        MarketplacePurchaseRequest purchaseRequest = newPurchaseRequest(id, listing, buyer,
                request.buyerName(), request.quantity(), request.fulfillment(), request.note());
        reserveStock(listing, request.quantity());
        repository.save(listing);
        purchaseRequestRepository.save(purchaseRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PurchaseRequestReceipt("खरेदीची विनंती विक्रेत्याकडे पाठवली आहे."));
    }

    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<CheckoutResponse> checkout(@RequestBody CheckoutRequest request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser buyer) {
        if (!validCheckoutRequest(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "कार्टमधील खरेदीची माहिती तपासा.");
        }
        if (request.paymentMethod() == MarketplacePurchaseRequest.PaymentMethod.UPI) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "UPI पेमेंट सुरू करण्यासाठी पेमेंट गेटवे आणि विक्रेता सेटलमेंट खाते अद्याप सक्रिय नाही. कृपया रोख पर्याय निवडा.");
        }

        TreeMap<Long, BigDecimal> quantities = new TreeMap<>();
        for (CheckoutLine line : request.items()) {
            if (quantities.putIfAbsent(line.listingId(), line.quantity()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "एका शेतमालाची मात्रा कार्टमध्ये एकदाच द्या.");
            }
        }

        TreeMap<Long, FarmProductListing> lockedListings = new TreeMap<>();
        for (Long listingId : quantities.keySet()) {
            FarmProductListing listing = findListingForUpdate(listingId);
            BigDecimal requested = quantities.get(listingId);
            if (Boolean.FALSE.equals(listing.getAvailable()) || listing.getPricePerUnit() == null
                    || requested.compareTo(BigDecimal.valueOf(listing.getQuantity())) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "एक किंवा अधिक शेतमालाचा साठा बदलला आहे. कार्ट तपासून पुन्हा प्रयत्न करा.");
            }
            lockedListings.put(listingId, listing);
        }

        List<MarketplacePurchaseRequest> orders = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> item : quantities.entrySet()) {
            FarmProductListing listing = lockedListings.get(item.getKey());
            BigDecimal quantity = item.getValue();
            MarketplacePurchaseRequest order = newPurchaseRequest(item.getKey(), listing, buyer,
                    request.buyerName(), quantity, request.fulfillment(), request.note());
            order.setPaymentMethod(MarketplacePurchaseRequest.PaymentMethod.CASH);
            order.setPaymentStatus(MarketplacePurchaseRequest.PaymentStatus.PENDING);
            orders.add(order);
            totalAmount = totalAmount.add(listing.getPricePerUnit().multiply(quantity));
            reserveStock(listing, quantity);
        }

        repository.saveAll(lockedListings.values());
        List<PurchaseRequestResponse> savedOrders = purchaseRequestRepository.saveAll(orders).stream()
                .map(PurchaseRequestResponse::from).toList();
        for (MarketplacePurchaseRequest order : orders) {
            notifyPaymentParties(order, lockedListings.get(order.getListingId()),
                    "नवीन रोख ऑर्डर", "ऑर्डर #" + order.getId() + " विक्रेत्याकडे पाठवला आहे.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(new CheckoutResponse(
                "ऑर्डर विक्रेत्यांकडे पाठवले आहेत. विक्रेत्यांच्या प्रतिसादानंतर स्थिती येथे दिसेल.",
                savedOrders, totalAmount.setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    @GetMapping("/buyer/orders")
    public List<PurchaseRequestResponse> buyerOrders(
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser buyer) {
        return purchaseRequestRepository.findTop100ByBuyerUserIdOrderByCreatedAtDesc(buyer.getId()).stream()
                .map(PurchaseRequestResponse::from).toList();
    }

    @GetMapping("/seller/listings/{id}")
    public ListingResponse sellerListing(@PathVariable Long id,
            @RequestHeader(value = "X-Seller-Token", required = false) String sellerToken,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        FarmProductListing listing = findOwnedListing(id, user);
        requireSeller(listing, sellerToken);
        return ListingResponse.from(listing);
    }

    @PatchMapping("/seller/listings/{id}")
    @Transactional
    public ListingResponse updateSellerListing(@PathVariable Long id,
            @RequestHeader(value = "X-Seller-Token", required = false) String sellerToken,
            @RequestBody SellerListingUpdate update,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        FarmProductListing listing = findOwnedListingForUpdate(id, user);
        requireSeller(listing, sellerToken);
        if (update == null || !validPrice(update.pricePerUnit())
                || update.quantity() == null || !Double.isFinite(update.quantity())
                || update.quantity() < 0 || update.quantity() > 1_000_000_000
                || update.available() == null
                || Boolean.TRUE.equals(update.available()) && update.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "किंमत, प्रमाण आणि उपलब्धतेची माहिती तपासा.");
        }
        listing.setPricePerUnit(update.pricePerUnit().setScale(2, java.math.RoundingMode.HALF_UP));
        listing.setQuantity(update.quantity());
        listing.setAvailable(update.available());
        return ListingResponse.from(repository.save(listing));
    }

    @GetMapping("/seller/listings/{id}/purchase-requests")
    public List<PurchaseRequestResponse> sellerPurchaseRequests(@PathVariable Long id,
            @RequestHeader(value = "X-Seller-Token", required = false) String sellerToken,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireSeller(findOwnedListing(id, user), sellerToken);
        return purchaseRequestRepository.findTop100ByListingIdOrderByCreatedAtDesc(id).stream()
                .map(PurchaseRequestResponse::from).toList();
    }

    @PatchMapping("/seller/listings/{id}/purchase-requests/{requestId}")
    @Transactional
    public PurchaseRequestResponse updatePurchaseRequest(@PathVariable Long id, @PathVariable Long requestId,
            @RequestHeader(value = "X-Seller-Token", required = false) String sellerToken,
            @RequestBody PurchaseRequestStatusUpdate update,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        FarmProductListing listing = findOwnedListingForUpdate(id, user);
        requireSeller(listing, sellerToken);
        MarketplacePurchaseRequest request = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "खरेदीची विनंती सापडली नाही."));
        if (!id.equals(request.getListingId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "खरेदीची विनंती सापडली नाही.");
        }
        if (update == null || update.paymentStatus() != null && update.status() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "व्यवहाराची स्थिती तपासा.");
        }
        if (update.paymentStatus() != null) {
            MarketplacePurchaseRequest.PaymentStatus nextPaymentStatus;
            try {
                nextPaymentStatus = MarketplacePurchaseRequest.PaymentStatus.valueOf(
                        update.paymentStatus().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "पेमेंटची स्थिती तपासा.");
            }
            if (request.getPaymentMethod() != MarketplacePurchaseRequest.PaymentMethod.CASH
                    || request.getPaymentStatus() != MarketplacePurchaseRequest.PaymentStatus.PENDING
                    || request.getStatus() == MarketplacePurchaseRequest.Status.DECLINED
                    || nextPaymentStatus != MarketplacePurchaseRequest.PaymentStatus.PAID) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "या पेमेंटची स्थिती विक्रेत्याकडून बदलता येत नाही.");
            }
            request.setPaymentStatus(nextPaymentStatus);
            MarketplacePurchaseRequest saved = purchaseRequestRepository.save(request);
            notifyPaymentParties(saved, listing, "रोख पेमेंट मिळाले",
                    "ऑर्डर #" + saved.getId() + " चे रोख पेमेंट विक्रेत्याने मिळाल्याची नोंद केली.");
            return PurchaseRequestResponse.from(saved);
        }
        if (update.status() == null
                || !Set.of("ACCEPTED", "DECLINED", "COMPLETED").contains(update.status().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "विनंतीची स्थिती तपासा.");
        }
        MarketplacePurchaseRequest.Status nextStatus = MarketplacePurchaseRequest.Status.valueOf(
                update.status().toUpperCase(Locale.ROOT));
        boolean validTransition = request.getStatus() == MarketplacePurchaseRequest.Status.PENDING
                && (nextStatus == MarketplacePurchaseRequest.Status.ACCEPTED
                    || nextStatus == MarketplacePurchaseRequest.Status.DECLINED)
                || request.getStatus() == MarketplacePurchaseRequest.Status.ACCEPTED
                    && nextStatus == MarketplacePurchaseRequest.Status.COMPLETED;
        if (!validTransition) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "या ऑर्डरची स्थिती आधीच बदलली आहे.");
        }
        if (nextStatus == MarketplacePurchaseRequest.Status.COMPLETED
                && request.getPaymentMethod() == MarketplacePurchaseRequest.PaymentMethod.CASH
                && request.getPaymentStatus() != MarketplacePurchaseRequest.PaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "व्यवहार पूर्ण करण्यापूर्वी रोख पेमेंट मिळाल्याची नोंद करा.");
        }
        if (nextStatus == MarketplacePurchaseRequest.Status.DECLINED
                && request.getPaymentStatus() == MarketplacePurchaseRequest.PaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "पेमेंट झालेला ऑर्डर नाकारता येत नाही. परताव्यासाठी खरेदीदाराशी संपर्क साधा.");
        }
        if (nextStatus == MarketplacePurchaseRequest.Status.DECLINED) {
            BigDecimal restored = BigDecimal.valueOf(listing.getQuantity()).add(request.getQuantity());
            listing.setQuantity(restored.doubleValue());
            listing.setAvailable(restored.signum() > 0 && listing.getPricePerUnit() != null);
            repository.save(listing);
        }
        request.setStatus(nextStatus);
        MarketplacePurchaseRequest saved = purchaseRequestRepository.save(request);
        notifyPaymentParties(saved, listing, "ऑर्डर स्थिती अपडेट",
                "ऑर्डर #" + saved.getId() + " ची स्थिती " + saved.getStatus() + " झाली.");
        return PurchaseRequestResponse.from(saved);
    }

    private void notifyPaymentParties(MarketplacePurchaseRequest order, FarmProductListing listing,
            String title, String message) {
        try {
            pushService.notifyUser(order.getBuyerUserId(), title, message, "/?tab=marketplace");
            if (listing != null && listing.getOwner() != null) {
                pushService.notifyUser(listing.getOwner().getId(), title, message, "/?tab=marketplace");
            }
        } catch (RuntimeException exception) {
            // The payment/order record is authoritative; push delivery is best-effort.
        }
    }

    @GetMapping("/listings/{id}/contact")
    public Map<String, String> contact(@PathVariable Long id) {
        FarmProductListing listing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "विक्रीची नोंद सापडली नाही."));
        return Map.of("phone", listing.getPhone());
    }

    @PostMapping("/access")
    public ResponseEntity<Void> verifyAccess() {
        return ResponseEntity.noContent().build();
    }

    private static void validate(ListingRequest request) {
        if (request == null
                || blankOrLong(request.sellerName(), 100)
                || request.phone() == null
                || !INDIAN_PHONE.matcher(request.phone().replaceAll("[\\s-]", "")).matches()
                || blankOrLong(request.address(), 500)
                || request.latitude() == null || !Double.isFinite(request.latitude())
                || request.latitude() < -90 || request.latitude() > 90
                || request.longitude() == null || !Double.isFinite(request.longitude())
                || request.longitude() < -180 || request.longitude() > 180
                || blankOrLong(request.productName(), 120)
                || blankOrLong(request.cropType(), 80)
                || blankOrLong(request.category(), 40)
                || request.quantity() == null || !Double.isFinite(request.quantity())
                || request.quantity() <= 0 || request.quantity() > 1_000_000_000
                || !validPrice(request.pricePerUnit())
                || request.unit() == null || !UNITS.contains(request.unit().trim().toLowerCase(Locale.ROOT))
                || tooLong(request.description(), 1000)
                || tooLong(request.photoUrl(), 100)
                || request.photoUrl() != null && !request.photoUrl().isBlank()
                    && !PHOTO_URL.matcher(request.photoUrl()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "विक्रीची माहिती तपासा आणि आवश्यक तपशील भरा.");
        }
    }

    private FarmProductListing findListing(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "विक्रीची नोंद सापडली नाही."));
    }

    private FarmProductListing findListingForUpdate(Long id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "विक्रीची नोंद सापडली नाही."));
    }

    private static MarketplacePurchaseRequest newPurchaseRequest(Long listingId, FarmProductListing listing,
            AppUser buyer, String buyerName, BigDecimal quantity,
            MarketplacePurchaseRequest.Fulfillment fulfillment, String note) {
        MarketplacePurchaseRequest order = new MarketplacePurchaseRequest();
        order.setListingId(listingId);
        order.setBuyerUserId(buyer.getId());
        order.setBuyerName(buyerName.trim());
        order.setBuyerPhone(buyer.getMobileNumber());
        order.setProductName(listing.getProductName());
        order.setSellerName(listing.getSellerName());
        order.setSellerPhone(listing.getPhone());
        order.setPricePerUnit(listing.getPricePerUnit());
        order.setQuantity(quantity);
        order.setUnit(listing.getUnit());
        order.setFulfillment(fulfillment);
        order.setNote(trimToNull(note));
        order.setPaymentMethod(MarketplacePurchaseRequest.PaymentMethod.CASH);
        order.setPaymentStatus(MarketplacePurchaseRequest.PaymentStatus.PENDING);
        return order;
    }

    private static void reserveStock(FarmProductListing listing, BigDecimal quantity) {
        BigDecimal remaining = BigDecimal.valueOf(listing.getQuantity()).subtract(quantity);
        listing.setQuantity(remaining.max(BigDecimal.ZERO).doubleValue());
        if (remaining.signum() <= 0) listing.setAvailable(false);
    }

    private FarmProductListing findOwnedListing(Long id, AppUser user) {
        return repository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "या विक्रीची नोंद सापडली नाही."));
    }

    private FarmProductListing findOwnedListingForUpdate(Long id, AppUser user) {
        FarmProductListing listing = findListingForUpdate(id);
        if (listing.getOwner() == null || !user.getId().equals(listing.getOwner().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "या विक्रीची नोंद सापडली नाही.");
        }
        return listing;
    }

    private static boolean validPurchaseRequest(PurchaseRequestCreate request) {
        return request != null
                && !blankOrLong(request.buyerName(), 100)
                && request.buyerPhone() != null
                && INDIAN_PHONE.matcher(request.buyerPhone().replaceAll("[\\s-]", "")).matches()
                && request.quantity() != null && request.quantity().signum() > 0
                && request.quantity().compareTo(new BigDecimal("1000000000")) <= 0
                && request.quantity().stripTrailingZeros().scale() <= 3
                && request.fulfillment() != null
                && !tooLong(request.note(), 500);
    }

    private static boolean validCheckoutRequest(CheckoutRequest request) {
        return request != null && !blankOrLong(request.buyerName(), 100)
                && request.fulfillment() != null && !tooLong(request.note(), 500)
                && request.items() != null && !request.items().isEmpty() && request.items().size() <= 50
                && request.paymentMethod() != null
                && request.items().stream().allMatch(line -> line != null && line.listingId() != null
                    && line.listingId() > 0 && validOrderQuantity(line.quantity()));
    }

    private static boolean validOrderQuantity(BigDecimal quantity) {
        return quantity != null && quantity.signum() > 0
                && quantity.compareTo(new BigDecimal("1000000000")) <= 0
                && quantity.stripTrailingZeros().scale() <= 3;
    }

    private static boolean validPrice(BigDecimal price) {
        return price != null && price.signum() > 0 && price.compareTo(MAX_PRICE) <= 0
                && price.stripTrailingZeros().scale() <= 2;
    }

    private static String newSellerToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashSellerToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireSeller(FarmProductListing listing, String token) {
        if (token == null || token.isBlank() || listing.getSellerTokenHash() == null
                || !MessageDigest.isEqual(listing.getSellerTokenHash().getBytes(StandardCharsets.US_ASCII),
                        hashSellerToken(token).getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "या विक्रीची नोंद व्यवस्थापित करण्याची परवानगी नाही.");
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

    public record ListingRequest(
            String sellerName,
            String phone,
            String address,
            Double latitude,
            Double longitude,
            String productName,
            String cropType,
            String category,
            Double quantity,
            String unit,
            String description,
            String photoUrl,
            BigDecimal pricePerUnit) {}

    public record CreateListingResponse(ListingResponse listing, String sellerManagementToken) {}

    public record PurchaseRequestCreate(String buyerName, String buyerPhone, BigDecimal quantity,
            MarketplacePurchaseRequest.Fulfillment fulfillment, String note) {}

    public record CheckoutLine(Long listingId, BigDecimal quantity) {}

    public record CheckoutRequest(String buyerName, MarketplacePurchaseRequest.Fulfillment fulfillment,
            String note, List<CheckoutLine> items, MarketplacePurchaseRequest.PaymentMethod paymentMethod) {}

    public record CheckoutResponse(String message, List<PurchaseRequestResponse> orders, BigDecimal totalAmount) {}

    public record PaymentOptions(boolean cashEnabled, boolean upiEnabled, String upiMessage) {}

    public record PurchaseRequestReceipt(String message) {}

    public record SellerListingUpdate(BigDecimal pricePerUnit, Double quantity, Boolean available) {}

    public record PurchaseRequestStatusUpdate(String status, String paymentStatus) {}

    public record PurchaseRequestResponse(Long id, Long listingId, String buyerName, String buyerPhone,
            String productName, String sellerName, String sellerPhone, BigDecimal quantity, String unit, BigDecimal pricePerUnit,
            BigDecimal totalAmount, MarketplacePurchaseRequest.Fulfillment fulfillment, String note,
            MarketplacePurchaseRequest.Status status, MarketplacePurchaseRequest.PaymentMethod paymentMethod,
            MarketplacePurchaseRequest.PaymentStatus paymentStatus, java.time.Instant createdAt) {
        static PurchaseRequestResponse from(MarketplacePurchaseRequest request) {
            return new PurchaseRequestResponse(request.getId(), request.getListingId(), request.getBuyerName(),
                    request.getBuyerPhone(), request.getProductName(), request.getSellerName(), request.getSellerPhone(), request.getQuantity(),
                    request.getUnit(), request.getPricePerUnit(), request.getPricePerUnit() == null ? null
                        : request.getPricePerUnit().multiply(request.getQuantity()), request.getFulfillment(),
                    request.getNote(), request.getStatus(), request.getPaymentMethod(),
                    request.getPaymentStatus(), request.getCreatedAt());
        }
    }

    public record ListingResponse(
            Long id,
            String sellerName,
            String address,
            double latitude,
            double longitude,
            String productName,
            String cropType,
            String category,
            double quantity,
            BigDecimal pricePerUnit,
            boolean available,
            String unit,
            String description,
            String photoUrl) {
        static ListingResponse from(FarmProductListing listing) {
            return new ListingResponse(
                    listing.getId(),
                    listing.getSellerName(),
                    listing.getAddress(),
                    listing.getLatitude(),
                    listing.getLongitude(),
                    listing.getProductName(),
                    listing.getCropType(),
                    listing.getCategory(),
                    listing.getQuantity(),
                    listing.getPricePerUnit(),
                    !Boolean.FALSE.equals(listing.getAvailable()),
                    listing.getUnit(),
                    listing.getDescription(),
                    listing.getPhotoUrl());
        }
    }
}
