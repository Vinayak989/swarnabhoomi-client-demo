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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@Transactional(readOnly = true)
public class AdminController {
    private static final Set<String> VIEWABLE_ACCOUNT_ROLES = Set.of("buyer", "seller", "farmer");
    private static final Set<String> FARM_ACCOUNT_ROLES = Set.of("farmer", "seller");
    private static final int MAX_PAGE_SIZE = 100;

    private final AppUserRepository users;
    private final AccountProfileRepository profiles;
    private final FarmEntryRepository entries;
    private final FarmProductListingRepository listings;
    private final MarketplacePurchaseRequestRepository purchaseRequests;
    private final OrganicConsultantRepository consultantProfiles;
    private final ProfilePhotoService profilePhotos;

    public AdminController(AppUserRepository users, AccountProfileRepository profiles, FarmEntryRepository entries,
            FarmProductListingRepository listings, MarketplacePurchaseRequestRepository purchaseRequests,
            OrganicConsultantRepository consultantProfiles, ProfilePhotoService profilePhotos) {
        this.users = users;
        this.profiles = profiles;
        this.entries = entries;
        this.listings = listings;
        this.purchaseRequests = purchaseRequests;
        this.consultantProfiles = consultantProfiles;
        this.profilePhotos = profilePhotos;
    }

    @GetMapping("/overview")
    public Overview overview(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        return new Overview(
                users.countByRole("buyer"),
                users.countByRole("seller"),
                users.countByRole("farmer"),
                users.countByRole("consultant"),
                consultantProfiles.count(),
                listings.count(),
                listings.countByAvailableTrue(),
                purchaseRequests.count(),
                purchaseRequests.countByStatus(MarketplacePurchaseRequest.Status.PENDING),
                entries.countAllByOwnerRoles(FARM_ACCOUNT_ROLES),
                entries.countAllByOwnerRolesAndEntryDate(FARM_ACCOUNT_ROLES, java.time.ZonedDateTime.now(
                        java.time.ZoneId.of("Asia/Kolkata")).toLocalDate()));
    }

    @GetMapping("/users")
    public Page<UserRecord> users(@RequestParam String role, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        if (!VIEWABLE_ACCOUNT_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "खरेदीदार, विक्रेता किंवा शेतकरी खाते निवडा.");
        }
        Page<AppUser> accountPage = users.findAllByRoleOrderByCreatedAtDesc(role, pageRequest(page, size));
        Set<Long> userIds = accountPage.getContent().stream().map(AppUser::getId).collect(Collectors.toSet());
        Map<Long, AccountProfile> profileByUserId = userIds.isEmpty() ? Map.of()
                : profiles.findAllByUserIds(userIds).stream()
                        .collect(Collectors.toMap(AccountProfile::getUserId, Function.identity()));
        Set<String> photoIds = profilePhotos.existingIds(userIds);
        return accountPage.map(account -> userRecord(account, profileByUserId.get(account.getId()),
                photoIds.contains(ProfilePhotoService.photoId(account.getId())) ? "/api/profile/photo/" + account.getId() : null));
    }

    @PatchMapping("/users/{id}/organic-verification")
    @Transactional
    public VerificationRecord reviewOrganicVerification(@PathVariable Long id,
            @RequestBody(required = false) VerificationReviewRequest request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser admin) {
        requireAdmin(admin);
        AppUser account = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "खाते सापडले नाही."));
        if (!("seller".equals(account.getRole()) || "farmer".equals(account.getRole()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "सेंद्रिय पडताळणी फक्त विक्रेता किंवा शेतकरी खात्यासाठी आहे.");
        }
        if (request == null || !("VERIFIED".equals(request.status()) || "NOT_VERIFIED".equals(request.status()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "पडताळणीचा निकाल निवडा.");
        }
        AccountProfile profile = profiles.findByUserId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "प्रोफाइल सापडले नाही."));
        if (profile.getOrganicCertificationNumber() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "प्रोफाइलमध्ये प्रमाणपत्र क्रमांक नाही.");
        }
        String note = request.note() == null ? null : request.note().trim();
        if ("VERIFIED".equals(request.status())
                && (profile.getOrganicCertificateValidUntil() == null
                    || profile.getOrganicCertificateValidUntil().isBefore(LocalDate.now()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "वैधता दिनांक नसलेले किंवा कालबाह्य प्रमाणपत्र पडताळलेले म्हणून जतन करता येणार नाही.");
        }
        if ("VERIFIED".equals(request.status()) && (note == null || note.isBlank()
                || !note.toLowerCase(java.util.Locale.ROOT).matches(".*(pgs|apeda|jaivik|fssai).*"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "पडताळलेल्या सरकारी पोर्टलचे नाव नोंदीत लिहा.");
        }
        if (note != null && note.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "पडताळणीची नोंद ५०० अक्षरांपेक्षा मोठी आहे.");
        }
        profile.setOrganicVerificationStatus(request.status());
        profile.setOrganicVerificationNote(note == null || note.isBlank() ? null : note);
        profile.setOrganicVerificationCheckedAt(Instant.now());
        profile.setOrganicVerificationCheckedBy(admin.getId());
        profiles.save(profile);
        return new VerificationRecord(id, profile.getOrganicVerificationStatus(),
                profile.getOrganicVerificationNote(), profile.getOrganicVerificationCheckedAt());
    }

    @GetMapping("/consultants")
    public Page<ConsultantRecord> consultants(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        return consultantProfiles.findAllByOrderByCreatedAtDesc(pageRequest(page, size))
                .map(AdminController::consultantRecord);
    }

    @GetMapping("/daily-entries")
    public Page<DailyEntryRecord> dailyEntries(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        return entries.findAllByOwnerRoles(FARM_ACCOUNT_ROLES, pageRequest(page, size)).map(AdminController::dailyRecord);
    }

    @GetMapping("/listings")
    public Page<ListingRecord> listings(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        return listings.findAllByOrderByCreatedAtDesc(pageRequest(page, size)).map(AdminController::listingRecord);
    }

    @DeleteMapping("/listings/{id}")
    @Transactional
    public void deleteListing(@PathVariable Long id,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireAdmin(user);
        FarmProductListing listing = listings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "विक्रीची नोंद सापडली नाही."));
        listings.delete(listing);
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

    private static UserRecord userRecord(AppUser user, AccountProfile profile, String photoUrl) {
        return new UserRecord(user.getId(), user.getMobileNumber(), user.getRole(), user.getCreatedAt(),
                profile == null ? null : profile.getFullName(), profile == null ? null : profile.getBusinessName(),
                profile == null ? null : profile.getVillage(), profile == null ? null : profile.getTaluka(),
                profile == null ? null : profile.getDistrict(), profile == null ? null : profile.getState(),
                profile == null ? null : profile.getPincode(), profile == null ? null : profile.getAddress(),
                profile == null ? null : profile.getAbout(),
                profile == null ? null : profile.getOrganicCertificationSystem(),
                profile == null ? null : profile.getOrganicCertificationNumber(),
                profile == null ? null : profile.getOrganicCertificateValidUntil(),
                profile == null ? "NOT_CLAIMED" : organicVerificationStatus(profile),
                profile == null ? null : profile.getOrganicVerificationNote(),
                profile == null ? null : profile.getOrganicVerificationCheckedAt(),
                profile != null && profile.getOrganicVerificationCheckedBy() != null,
                profile != null && profile.getFullName() != null && profile.getVillage() != null
                        && profile.getDistrict() != null && profile.getState() != null, photoUrl);
    }

    private static String organicVerificationStatus(AccountProfile profile) {
        if ("VERIFIED".equals(profile.getOrganicVerificationStatus())
                && profile.getOrganicCertificateValidUntil() != null
                && profile.getOrganicCertificateValidUntil().isBefore(LocalDate.now())) return "EXPIRED";
        return profile.getOrganicVerificationStatus();
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
                request.getFulfillment(), request.getNote(), request.getStatus(), request.getPaymentMethod(),
                request.getPaymentStatus(), request.getCreatedAt());
    }

    public record Overview(long buyers, long sellers, long farmers, long consultants, long consultantProfiles,
            long listings, long availableListings, long purchaseRequests, long pendingRequests,
            long dailyEntries, long todayEntries) {}

    public record UserRecord(Long id, String mobileNumber, String role, Instant createdAt,
            String fullName, String businessName, String village, String taluka, String district, String state,
            String pincode, String address, String about, String organicCertificationSystem,
            String organicCertificationNumber, LocalDate organicCertificateValidUntil,
            String organicVerificationStatus, String organicVerificationNote,
            Instant organicVerificationCheckedAt, boolean organicVerificationReviewed, boolean profileComplete, String photoUrl) {}

    public record VerificationReviewRequest(String status, String note) {}
    public record VerificationRecord(Long userId, String status, String note, Instant checkedAt) {}

    public record ConsultantRecord(Long id, String name, String phone, String serviceArea, String expertise,
            int experienceYears, String introduction, Instant createdAt) {}

    private static ConsultantRecord consultantRecord(OrganicConsultant consultant) {
        return new ConsultantRecord(consultant.getId(), consultant.getName(), consultant.getPhone(),
                consultant.getServiceArea(), consultant.getExpertise(), consultant.getExperienceYears(),
                consultant.getIntroduction(), consultant.getCreatedAt());
    }

    public record ListingRecord(Long id, String sellerName, String sellerPhone, String accountMobileNumber,
            String productName, String cropType, String category, double quantity, String unit,
            BigDecimal pricePerUnit, Boolean available, String address, Instant createdAt) {}

    public record PurchaseRequestRecord(Long id, Long listingId, String productName, String sellerName,
            String buyerName, String buyerPhone, BigDecimal quantity, String unit,
            MarketplacePurchaseRequest.Fulfillment fulfillment, String note,
            MarketplacePurchaseRequest.Status status, MarketplacePurchaseRequest.PaymentMethod paymentMethod,
            MarketplacePurchaseRequest.PaymentStatus paymentStatus, Instant createdAt) {}

    public record DailyEntryRecord(Long id, Long farmerId, String farmerMobileNumber, LocalDate entryDate,
            String weather, String pincode, String farmSection, String workDone, String materials,
            String cropCondition, String soilNotes, String tomorrowPlan, String specialNote,
            String seasonName, String applicationKind, String applicationName, String applicationUnit,
            String pestDisease, String issueSeverity, String intervention, Integer workerCount,
            Double laborCost, Double materialsCost, Double otherCost, Double productionKg, Double soldKg,
            Double saleIncome, Double plotAreaAcres, Double waterLitres, Double applicationAmount,
            LocalDate followUpDate, String photoUrl, String weatherDetails, String receiptPhotoUrls) {}
}
