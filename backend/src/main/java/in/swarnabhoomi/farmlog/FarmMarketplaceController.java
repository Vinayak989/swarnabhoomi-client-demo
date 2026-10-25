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
import java.util.HexFormat;
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
    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private org.springframework.util.unit.DataSize maxPhotoSize;

    public FarmMarketplaceController(FarmProductListingRepository repository,
            MarketplacePurchaseRequestRepository purchaseRequestRepository,
            FarmPhotoRepository photoRepository, PhotoObjectStorage photoStorage) {
        this.repository = repository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.photoRepository = photoRepository;
        this.photoStorage = photoStorage;
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
    public ResponseEntity<PurchaseRequestReceipt> createPurchaseRequest(@PathVariable Long id,
            @RequestBody PurchaseRequestCreate request) {
        FarmProductListing listing = findListing(id);
        if (Boolean.FALSE.equals(listing.getAvailable()) || listing.getPricePerUnit() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "हा शेतमाल सध्या खरेदीसाठी उपलब्ध नाही.");
        }
        if (!validPurchaseRequest(request)
                || request.quantity().compareTo(BigDecimal.valueOf(listing.getQuantity())) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "खरेदीची माहिती तपासा. प्रमाण उपलब्ध साठ्यापेक्षा जास्त असू शकत नाही.");
        }

        MarketplacePurchaseRequest purchaseRequest = new MarketplacePurchaseRequest();
        purchaseRequest.setListingId(id);
        purchaseRequest.setBuyerName(request.buyerName().trim());
        purchaseRequest.setBuyerPhone(request.buyerPhone().replaceAll("[\\s-]", ""));
        purchaseRequest.setQuantity(request.quantity());
        purchaseRequest.setUnit(listing.getUnit());
        purchaseRequest.setFulfillment(request.fulfillment());
        purchaseRequest.setNote(trimToNull(request.note()));
        purchaseRequestRepository.save(purchaseRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PurchaseRequestReceipt("खरेदीची विनंती विक्रेत्याकडे पाठवली आहे."));
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
    public ListingResponse updateSellerListing(@PathVariable Long id,
            @RequestHeader(value = "X-Seller-Token", required = false) String sellerToken,
            @RequestBody SellerListingUpdate update,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        FarmProductListing listing = findOwnedListing(id, user);
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
    public PurchaseRequestResponse updatePurchaseRequest(@PathVariable Long id, @PathVariable Long requestId,
            @RequestHeader(value = "X-Seller-Token", required = false) String sellerToken,
            @RequestBody PurchaseRequestStatusUpdate update,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireSeller(findOwnedListing(id, user), sellerToken);
        MarketplacePurchaseRequest request = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "खरेदीची विनंती सापडली नाही."));
        if (!id.equals(request.getListingId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "खरेदीची विनंती सापडली नाही.");
        }
        if (update == null || update.status() == null
                || !Set.of("ACCEPTED", "DECLINED", "COMPLETED").contains(update.status().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "विनंतीची स्थिती तपासा.");
        }
        request.setStatus(MarketplacePurchaseRequest.Status.valueOf(update.status().toUpperCase(Locale.ROOT)));
        return PurchaseRequestResponse.from(purchaseRequestRepository.save(request));
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

    private FarmProductListing findOwnedListing(Long id, AppUser user) {
        return repository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "या विक्रीची नोंद सापडली नाही."));
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

    public record PurchaseRequestReceipt(String message) {}

    public record SellerListingUpdate(BigDecimal pricePerUnit, Double quantity, Boolean available) {}

    public record PurchaseRequestStatusUpdate(String status) {}

    public record PurchaseRequestResponse(Long id, Long listingId, String buyerName, String buyerPhone,
            BigDecimal quantity, String unit, MarketplacePurchaseRequest.Fulfillment fulfillment, String note,
            MarketplacePurchaseRequest.Status status, java.time.Instant createdAt) {
        static PurchaseRequestResponse from(MarketplacePurchaseRequest request) {
            return new PurchaseRequestResponse(request.getId(), request.getListingId(), request.getBuyerName(),
                    request.getBuyerPhone(), request.getQuantity(), request.getUnit(), request.getFulfillment(),
                    request.getNote(), request.getStatus(), request.getCreatedAt());
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
