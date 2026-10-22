package in.swarnabhoomi.farmlog;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/marketplace")
public class FarmMarketplaceController {
    private static final Pattern INDIAN_PHONE = Pattern.compile("^(?:\\+91)?[6-9][0-9]{9}$");
    private static final Pattern PHOTO_URL = Pattern.compile("^/api/photos/[a-fA-F0-9-]{36}$");
    private static final Set<String> UNITS = Set.of("kg", "quintal", "ton", "dozen", "crate", "piece");
    private final FarmProductListingRepository repository;
    private final FarmPhotoRepository photoRepository;
    private final PhotoObjectStorage photoStorage;
    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private org.springframework.util.unit.DataSize maxPhotoSize;

    public FarmMarketplaceController(FarmProductListingRepository repository, FarmPhotoRepository photoRepository,
            PhotoObjectStorage photoStorage) {
        this.repository = repository;
        this.photoRepository = photoRepository;
        this.photoStorage = photoStorage;
    }

    @GetMapping("/listings")
    public List<ListingResponse> listings() {
        return repository.findTop200ByOrderByCreatedAtDesc().stream().map(ListingResponse::from).toList();
    }

    @PostMapping("/photos")
    public Map<String, String> photo(@RequestParam("file") MultipartFile file) {
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
            photoStorage.store(photo);
            photoRepository.save(photo);
            return Map.of("url", "/api/marketplace/photos/" + photo.getId());
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोटो वाचता आला नाही.", exception);
        }
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> getPhoto(@PathVariable String id) {
        FarmPhoto photo = photoRepository.findById(id)
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
    public ResponseEntity<ListingResponse> create(@RequestBody ListingRequest request) {
        validate(request);
        FarmProductListing listing = new FarmProductListing();
        listing.setSellerName(request.sellerName().trim());
        listing.setPhone(request.phone().replaceAll("[\\s-]", ""));
        listing.setAddress(request.address().trim());
        listing.setLatitude(request.latitude());
        listing.setLongitude(request.longitude());
        listing.setProductName(request.productName().trim());
        listing.setCropType(request.cropType().trim());
        listing.setCategory(request.category().trim());
        listing.setQuantity(request.quantity());
        listing.setUnit(request.unit().trim().toLowerCase(Locale.ROOT));
        listing.setDescription(trimToNull(request.description()));
        listing.setPhotoUrl(trimToNull(request.photoUrl()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ListingResponse.from(repository.save(listing)));
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
                || request.unit() == null || !UNITS.contains(request.unit().trim().toLowerCase(Locale.ROOT))
                || tooLong(request.description(), 1000)
                || tooLong(request.photoUrl(), 100)
                || request.photoUrl() != null && !request.photoUrl().isBlank()
                    && !PHOTO_URL.matcher(request.photoUrl()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "विक्रीची माहिती तपासा आणि आवश्यक तपशील भरा.");
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
            String photoUrl) {}

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
                    listing.getUnit(),
                    listing.getDescription(),
                    listing.getPhotoUrl());
        }
    }
}
