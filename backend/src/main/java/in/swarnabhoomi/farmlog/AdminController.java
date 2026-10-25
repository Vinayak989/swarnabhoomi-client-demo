package in.swarnabhoomi.farmlog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@Transactional(readOnly = true)
public class AdminController {
    private static final Set<String> VIEWABLE_ACCOUNT_ROLES = Set.of("buyer", "seller");
    private static final int MAX_PAGE_SIZE = 100;

    private final AppUserRepository users;
    private final FarmEntryRepository entries;
    private final FarmProductListingRepository listings;
    private final MarketplacePurchaseRequestRepository purchaseRequests;

    public AdminController(AppUserRepository users, FarmEntryRepository entries,
            FarmProductListingRepository listings, MarketplacePurchaseRequestRepository purchaseRequests) {
        this.users = users;
        this.entries = entries;
        this.listings = listings;
        this.purchaseRequests = purchaseRequests;
    }

    @GetMapping("/overview")
    public Overview overview(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        return new Overview(
                users.countByRole("buyer"),
                users.countByRole("seller"),
                users.countByRole("farmer"),
                users.countByRole("consultant"),
                listings.count(),
                listings.countByAvailableTrue(),
                purchaseRequests.count(),
                purchaseRequests.countByStatus(MarketplacePurchaseRequest.Status.PENDING),
                entries.countAllByOwnerRole("farmer"),
                entries.countAllByOwnerRoleAndEntryDate("farmer", java.time.ZonedDateTime.now(
                        java.time.ZoneId.of("Asia/Kolkata")).toLocalDate()));
    }

    @GetMapping("/users")
    public Page<UserRecord> users(@RequestParam String role, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        if (!VIEWABLE_ACCOUNT_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "खरेदीदार किंवा विक्रेता खाते निवडा.");
        }
        return users.findAllByRoleOrderByCreatedAtDesc(role, pageRequest(page, size)).map(AdminController::userRecord);
    }

    @GetMapping("/daily-entries")
    public Page<DailyEntryRecord> dailyEntries(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        return entries.findAllByOwnerRole("farmer", pageRequest(page, size)).map(AdminController::dailyRecord);
    }

    @GetMapping("/listings")
    public Page<ListingRecord> listings(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        return listings.findAllByOrderByCreatedAtDesc(pageRequest(page, size)).map(AdminController::listingRecord);
    }

    @GetMapping("/purchase-requests")
    public Page<PurchaseRequestRecord> purchaseRequests(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        Page<MarketplacePurchaseRequest> requests = purchaseRequests.findAllByOrderByCreatedAtDesc(pageRequest(page, size));
        Set<Long> listingIds = requests.getContent().stream().map(MarketplacePurchaseRequest::getListingId)
                .collect(Collectors.toSet());
        Map<Long, FarmProductListing> listingById = listings.findAllById(listingIds).stream()
                .collect(Collectors.toMap(FarmProductListing::getId, Function.identity()));
        return requests.map(request -> purchaseRecord(request, listingById.get(request.getListingId())));
    }

    private static Pageable pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
    }

    private static void requireAdmin(AppUser user) {
        if (user == null || !"admin".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "या माहितीसाठी प्रशासक प्रवेश आवश्यक आहे.");
        }
    }

    private static UserRecord userRecord(AppUser user) {
        return new UserRecord(user.getId(), user.getMobileNumber(), user.getRole(), user.getCreatedAt());
    }

    private static DailyEntryRecord dailyRecord(FarmEntry entry) {
        AppUser farmer = entry.getOwner();
        return new DailyEntryRecord(entry.getId(), farmer == null ? null : farmer.getId(),
                farmer == null ? null : farmer.getMobileNumber(), entry.getEntryDate(), entry.getWeather(),
                entry.getPincode(), entry.getFarmSection(), entry.getWorkDone(), entry.getMaterials(),
                entry.getCropCondition(), entry.getSoilNotes(), entry.getTomorrowPlan(), entry.getSpecialNote(),
                entry.getSeasonName(), entry.getApplicationKind(), entry.getApplicationName(),
                entry.getApplicationUnit(), entry.getPestDisease(), entry.getIssueSeverity(), entry.getIntervention(),
                entry.getWorkerCount(), entry.getLaborCost(), entry.getMaterialsCost(), entry.getOtherCost(),
                entry.getProductionKg(), entry.getSoldKg(), entry.getSaleIncome(), entry.getPlotAreaAcres(),
                entry.getWaterLitres(), entry.getApplicationAmount(), entry.getFollowUpDate(), entry.getPhotoUrl(),
                entry.getWeatherDetails(), entry.getReceiptPhotoUrls());
    }

    private static ListingRecord listingRecord(FarmProductListing listing) {
        AppUser owner = listing.getOwner();
        return new ListingRecord(listing.getId(), listing.getSellerName(), listing.getPhone(),
                owner == null ? null : owner.getMobileNumber(), listing.getProductName(), listing.getCropType(),
                listing.getCategory(), listing.getQuantity(), listing.getUnit(), listing.getPricePerUnit(),
                listing.getAvailable(), listing.getAddress(), listing.getCreatedAt());
    }

    private static PurchaseRequestRecord purchaseRecord(MarketplacePurchaseRequest request,
            FarmProductListing listing) {
        return new PurchaseRequestRecord(request.getId(), request.getListingId(),
                listing == null ? null : listing.getProductName(), listing == null ? null : listing.getSellerName(),
                request.getBuyerName(), request.getBuyerPhone(), request.getQuantity(), request.getUnit(),
                request.getFulfillment(), request.getNote(), request.getStatus(), request.getCreatedAt());
    }

    public record Overview(long buyers, long sellers, long farmers, long consultants,
            long listings, long availableListings, long purchaseRequests, long pendingRequests,
            long dailyEntries, long todayEntries) {}

    public record UserRecord(Long id, String mobileNumber, String role, Instant createdAt) {}

    public record ListingRecord(Long id, String sellerName, String sellerPhone, String accountMobileNumber,
            String productName, String cropType, String category, double quantity, String unit,
            BigDecimal pricePerUnit, Boolean available, String address, Instant createdAt) {}

    public record PurchaseRequestRecord(Long id, Long listingId, String productName, String sellerName,
            String buyerName, String buyerPhone, BigDecimal quantity, String unit,
            MarketplacePurchaseRequest.Fulfillment fulfillment, String note,
            MarketplacePurchaseRequest.Status status, Instant createdAt) {}

    public record DailyEntryRecord(Long id, Long farmerId, String farmerMobileNumber, LocalDate entryDate,
            String weather, String pincode, String farmSection, String workDone, String materials,
            String cropCondition, String soilNotes, String tomorrowPlan, String specialNote,
            String seasonName, String applicationKind, String applicationName, String applicationUnit,
            String pestDisease, String issueSeverity, String intervention, Integer workerCount,
            Double laborCost, Double materialsCost, Double otherCost, Double productionKg, Double soldKg,
            Double saleIncome, Double plotAreaAcres, Double waterLitres, Double applicationAmount,
            LocalDate followUpDate, String photoUrl, String weatherDetails, String receiptPhotoUrls) {}
}
