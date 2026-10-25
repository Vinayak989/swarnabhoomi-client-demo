package in.swarnabhoomi.farmlog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OrganicGovernmentSchemeController {
    private final OrganicGovernmentSchemeService schemes;

    public OrganicGovernmentSchemeController(OrganicGovernmentSchemeService schemes) {
        this.schemes = schemes;
    }

    @GetMapping("/api/schemes/organic")
    public OrganicGovernmentSchemeService.Feed organicSchemes(
            @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        if (!"farmer".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Farmer account required.");
        }
        return schemes.currentFeed();
    }
}
