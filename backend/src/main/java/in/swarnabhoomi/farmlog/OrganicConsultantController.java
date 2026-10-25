package in.swarnabhoomi.farmlog;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    public OrganicConsultantController(OrganicConsultantRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ConsultantResponse> consultants() {
        return repository.findTop200ByOrderByCreatedAtDesc().stream()
                .map(ConsultantResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<ConsultantResponse> register(@RequestBody ConsultantRequest request,
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        if (!"admin".equals(user.getRole()) && !"consultant".equals(user.getRole())) {
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
                .body(ConsultantResponse.from(repository.save(consultant)));
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
            String introduction) {
        static ConsultantResponse from(OrganicConsultant consultant) {
            return new ConsultantResponse(
                    consultant.getId(),
                    consultant.getName(),
                    consultant.getPhone(),
                    consultant.getServiceArea(),
                    consultant.getExpertise(),
                    consultant.getExperienceYears(),
                    consultant.getIntroduction());
        }
    }
}
