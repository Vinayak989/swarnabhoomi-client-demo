package in.swarnabhoomi.farmlog;

import org.springframework.http.HttpStatus;
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
            @RequestBody(required = false) BrowserPushService.SubscriptionRequest request) {
        return service.subscribe(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable Long id) {
        service.unsubscribe(id);
    }
}
