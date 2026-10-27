package in.swarnabhoomi.farmlog;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push/subscriptions")
public class BrowserPushController {
    private final BrowserPushService service;

    public BrowserPushController(BrowserPushService service) {
        this.service = service;
    }

    @GetMapping("/public-key")
    public BrowserPushService.PublicKeyResponse publicKey() {
        return service.publicKey();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BrowserPushService.SubscriptionResponse subscribe(
            @RequestBody(required = false) BrowserPushService.SubscriptionRequest request,
            @org.springframework.web.bind.annotation.RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        if (!"farmer".equals(user.getRole())) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.FORBIDDEN, "हे वैशिष्ट्य शेतकरी खात्यासाठी आहे.");
        return service.subscribe(request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        if (!"farmer".equals(user.getRole())) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.FORBIDDEN, "हे वैशिष्ट्य शेतकरी खात्यासाठी आहे.");
        service.unsubscribe(id, user);
    }

    @PostMapping("/{id}/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void test(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        if (!"farmer".equals(user.getRole())) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.FORBIDDEN, "हे वैशिष्ट्य शेतकरी खात्यासाठी आहे.");
        service.sendTest(id, user);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> pushError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "message", exception.getReason() == null ? "डिव्हाइस सूचना सेट करता आल्या नाहीत. पुन्हा प्रयत्न करा." : exception.getReason(),
                "code", exception.getStatusCode().value() == 400 ? "PUSH_SUBSCRIPTION_INVALID" : "PUSH_REQUEST_FAILED"));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableSubscription() {
        return ResponseEntity.badRequest().body(Map.of("message", "फोनवरील सूचना नोंदणीचे तपशील मिळाले नाहीत. अॅप पुन्हा उघडून सूचना सुरू करा.",
                "code", "PUSH_REQUEST_INVALID"));
    }
}
