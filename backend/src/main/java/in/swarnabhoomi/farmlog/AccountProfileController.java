package in.swarnabhoomi.farmlog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile")
@Transactional
public class AccountProfileController {
    private static final Set<String> PROFILE_ROLES = Set.of("buyer", "seller", "farmer");
    private static final Set<String> ORGANIC_SYSTEMS = Set.of("PGS_INDIA", "NPOP_APEDA");

    private final AccountProfileRepository profiles;
    private final ProfilePhotoService photos;

    public AccountProfileController(AccountProfileRepository profiles, ProfilePhotoService photos) {
        this.profiles = profiles;
        this.photos = photos;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ProfileResponse profile(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireProfileRole(user);
        return ProfileResponse.from(user, profiles.findByUserId(user.getId()).orElse(null), photos.exists(user.getId()));
    }

    @PutMapping
    public ProfileResponse save(@RequestBody(required = false) ProfileUpdate request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireProfileRole(user);
        if (request == null) throw badRequest("प्रोफाइलची माहिती भरा.");

        String fullName = required(request.fullName(), 120, "पूर्ण नाव लिहा.");
        String village = required(request.village(), 120, "गाव लिहा.");
        String district = required(request.district(), 120, "जिल्हा लिहा.");
        String state = required(request.state(), 120, "राज्य लिहा.");
        String pincode = optional(request.pincode(), 6, "पिनकोड");
        if (pincode != null && !pincode.matches("[0-9]{6}")) {
            throw badRequest("पिनकोड ६ अंकांचा असावा.");
        }

        String system = request.organicCertificationSystem() == null
                ? "NONE" : request.organicCertificationSystem().trim().toUpperCase(Locale.ROOT);
        if (!"NONE".equals(system) && !ORGANIC_SYSTEMS.contains(system)) {
            throw badRequest("PGS-India किंवा NPOP / APEDA प्रमाणपत्र प्रणाली निवडा.");
        }
        boolean farmerOrSeller = "farmer".equals(user.getRole()) || "seller".equals(user.getRole());
        if (!farmerOrSeller && !"NONE".equals(system)) {
            throw badRequest("सेंद्रिय प्रमाणपत्राची माहिती विक्रेता किंवा शेतकरी खात्यासाठीच आहे.");
        }
        String certificationNumber = "NONE".equals(system) ? null
                : required(request.organicCertificationNumber(), 120, "प्रमाणपत्राचा क्रमांक लिहा.");
        LocalDate validUntil = "NONE".equals(system) ? null : request.organicCertificateValidUntil();
        if (!"NONE".equals(system) && validUntil == null) {
            throw badRequest("प्रमाणपत्राची वैधता दिनांक लिहा.");
        }

        AccountProfile profile = profiles.findByUserId(user.getId()).orElseGet(() -> new AccountProfile(user.getId()));
        boolean sameCertificate = Objects.equals(profile.getFullName(), fullName)
                && Objects.equals(profile.getVillage(), village)
                && Objects.equals(profile.getTaluka(), optional(request.taluka(), 120, "तालुका"))
                && Objects.equals(profile.getDistrict(), district)
                && Objects.equals(profile.getState(), state)
                && Objects.equals(profile.getPincode(), pincode)
                && Objects.equals(profile.getOrganicCertificationSystem(), "NONE".equals(system) ? null : system)
                && Objects.equals(profile.getOrganicCertificationNumber(), certificationNumber)
                && Objects.equals(profile.getOrganicCertificateValidUntil(), validUntil);

        profile.setFullName(fullName);
        profile.setBusinessName(optional(request.businessName(), 160, "व्यवसाय / शेताचे नाव"));
        profile.setVillage(village);
        profile.setTaluka(optional(request.taluka(), 120, "तालुका"));
        profile.setDistrict(district);
        profile.setState(state);
        profile.setPincode(pincode);
        profile.setAddress(optional(request.address(), 500, "पत्ता"));
        profile.setAbout(optional(request.about(), 800, "परिचय"));
        profile.setOrganicCertificationSystem("NONE".equals(system) ? null : system);
        profile.setOrganicCertificationNumber(certificationNumber);
        profile.setOrganicCertificateValidUntil(validUntil);
        if ("NONE".equals(system)) {
            profile.setOrganicVerificationStatus("NOT_CLAIMED");
            clearVerification(profile);
        } else if (!sameCertificate) {
            profile.setOrganicVerificationStatus("PENDING");
            clearVerification(profile);
        }

        return ProfileResponse.from(user, profiles.save(profile), photos.exists(user.getId()));
    }

    private static void requireProfileRole(AppUser user) {
        if (user == null || !PROFILE_ROLES.contains(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "या खाते प्रकारासाठी प्रोफाइल उपलब्ध नाही.");
        }
    }

    private static String required(String value, int maxLength, String message) {
        String cleaned = optional(value, maxLength, "माहिती");
        if (cleaned == null) throw badRequest(message);
        return cleaned;
    }

    private static String optional(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim();
        if (cleaned.length() > maxLength) throw badRequest(fieldName + " खूप मोठे आहे.");
        return cleaned;
    }

    private static void clearVerification(AccountProfile profile) {
        profile.setOrganicVerificationNote(null);
        profile.setOrganicVerificationCheckedAt(null);
        profile.setOrganicVerificationCheckedBy(null);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ProfileUpdate(String fullName, String businessName, String village, String taluka,
            String district, String state, String pincode, String address, String about,
            String organicCertificationSystem, String organicCertificationNumber,
            LocalDate organicCertificateValidUntil) {}

    public record ProfileResponse(Long profileId, Long userId, String role, String mobileNumber,
            String fullName, String businessName, String village, String taluka, String district,
            String state, String pincode, String address, String about,
            String organicCertificationSystem, String organicCertificationNumber,
            LocalDate organicCertificateValidUntil, String organicVerificationStatus,
            String organicVerificationNote, Instant organicVerificationCheckedAt,
            boolean complete, Instant updatedAt, String photoUrl) {
        static ProfileResponse from(AppUser user, AccountProfile profile, boolean hasPhoto) {
            String role = user.getRole();
            boolean complete = profile != null && profile.getFullName() != null
                    && profile.getVillage() != null && profile.getDistrict() != null && profile.getState() != null;
            return new ProfileResponse(profile == null ? null : profile.getId(), user.getId(), role,
                    user.getMobileNumber(), profile == null ? null : profile.getFullName(),
                    profile == null ? null : profile.getBusinessName(), profile == null ? null : profile.getVillage(),
                    profile == null ? null : profile.getTaluka(), profile == null ? null : profile.getDistrict(),
                    profile == null ? null : profile.getState(), profile == null ? null : profile.getPincode(),
                    profile == null ? null : profile.getAddress(), profile == null ? null : profile.getAbout(),
                    profile == null ? null : profile.getOrganicCertificationSystem(),
                    profile == null ? null : profile.getOrganicCertificationNumber(),
                    profile == null ? null : profile.getOrganicCertificateValidUntil(),
                    profile == null ? "NOT_CLAIMED" : verificationStatus(profile),
                    profile == null ? null : profile.getOrganicVerificationNote(),
                    profile == null ? null : profile.getOrganicVerificationCheckedAt(), complete,
                    profile == null ? null : profile.getUpdatedAt(), hasPhoto ? "/api/profile/photo" : null);
        }

        private static String verificationStatus(AccountProfile profile) {
            if ("VERIFIED".equals(profile.getOrganicVerificationStatus())
                    && profile.getOrganicCertificateValidUntil() != null
                    && profile.getOrganicCertificateValidUntil().isBefore(LocalDate.now())) return "EXPIRED";
            return profile.getOrganicVerificationStatus();
        }
    }
}
