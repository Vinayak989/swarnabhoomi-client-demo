package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.security.Security;
import java.util.Base64;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BrowserPushService {
    private static final Logger log = LoggerFactory.getLogger(BrowserPushService.class);
    private static final int PUSH_TTL_SECONDS = 86_400;
    private static final String[] ALLOWED_ENDPOINT_HOSTS = {
            "fcm.googleapis.com", "push.services.mozilla.com", "web.push.apple.com"
    };
    private static final String[] ALLOWED_ENDPOINT_SUFFIXES = {
            ".push.services.mozilla.com", ".notify.windows.com"
    };

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final BrowserPushSubscriptionRepository repository;
    private final FarmReminderRepository reminderRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.web-push.public-key:}") private String publicKey;
    @Value("${app.web-push.private-key:}") private String privateKey;
    @Value("${app.web-push.subject:https://swarnabhoomi-demo.onrender.com}") private String subject;

    public BrowserPushService(BrowserPushSubscriptionRepository repository,
            FarmReminderRepository reminderRepository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.reminderRepository = reminderRepository;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }

    public PublicKeyResponse publicKey() {
        requireConfigured();
        return new PublicKeyResponse(publicKey);
    }

    @Transactional
    public SubscriptionResponse subscribe(SubscriptionRequest request, AppUser user) {
        requireConfigured();
        if (request == null || request.endpoint() == null || request.endpoint().length() > 2048
                || request.keys() == null || blank(request.keys().p256dh()) || blank(request.keys().auth())) {
            throw badRequest("वेब सूचना सदस्यता तपशील अवैध आहेत.");
        }
        validateEndpoint(request.endpoint());
        try {
            if (Base64.getUrlDecoder().decode(request.keys().p256dh()).length != 65
                    || Base64.getUrlDecoder().decode(request.keys().auth()).length != 16) {
                throw badRequest("वेब सूचना एन्क्रिप्शन की अवैध आहे.");
            }
        } catch (IllegalArgumentException exception) {
            throw badRequest("वेब सूचना एन्क्रिप्शन की अवैध आहे.");
        }

        BrowserPushSubscription subscription = repository.findByEndpoint(request.endpoint()).orElse(null);
        if (subscription != null && subscription.getOwner() != null
                && !subscription.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "हे डिव्हाइस दुसऱ्या खात्याशी जोडलेले आहे. आधी त्या खात्यातून सूचना बंद करा.");
        }
        if (subscription != null && subscription.getOwner() == null) {
            reminderRepository.detachLegacyReminders(subscription.getId());
        }
        if (subscription == null) subscription = new BrowserPushSubscription();
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dhKey(request.keys().p256dh());
        subscription.setAuthKey(request.keys().auth());
        subscription.setActive(true);
        subscription.setAndroidApp(subscription.isAndroidApp() || request.androidApp());
        subscription.setOwner(user);
        return new SubscriptionResponse(repository.save(subscription).getId());
    }

    @Transactional
    public void unsubscribe(Long id, AppUser user) {
        BrowserPushSubscription subscription = repository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        subscription.setActive(false);
        repository.save(subscription);
    }

    public boolean send(FarmReminder reminder) {
        if (reminder.getOwner() == null) return false;
        String body = (blank(reminder.getCropOrPlot()) ? "" : reminder.getCropOrPlot() + ": ") + reminder.getReason();
        return notifyUser(reminder.getOwner().getId(), new PushMessage("स्वर्णभूमी स्मरणपत्र", body,
                "/?tab=reminders", "reminder-" + reminder.getId(),
                reminder.getScheduledAt().atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()));
    }

    public boolean notifyUser(Long userId, String title, String body, String url) {
        return notifyUser(userId, new PushMessage(title, body, url, UUID.randomUUID().toString(), System.currentTimeMillis()));
    }

    public void sendTest(Long id, AppUser user) {
        requireConfigured();
        BrowserPushSubscription subscription = repository.findByIdAndOwnerIdAndActiveTrue(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "या डिव्हाइसवरील सूचना पुन्हा सुरू करा."));
        if (!deliver(subscription, forDevice(subscription, new PushMessage("स्वर्णभूमी चाचणी सूचना",
                "सूचना सुरू आहेत. जतन केलेल्या स्मरणपत्रांची आठवण याच ठिकाणी मिळेल.",
                "/?tab=reminders", "reminder-test", System.currentTimeMillis())))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "चाचणी सूचना पाठवता आली नाही. सूचना पुन्हा सुरू करा.");
        }
    }

    private boolean notifyUser(Long userId, PushMessage message) {
        if (userId == null || !isConfigured()) return false;
        boolean delivered = false;
        for (BrowserPushSubscription subscription : repository.findAllByOwnerIdAndActiveTrue(userId)) {
            try {
                delivered |= deliver(subscription, forDevice(subscription, message));
            } catch (RuntimeException exception) {
                log.warn("Web push notification could not be delivered to subscription {}", subscription.getId(), exception);
            }
        }
        return delivered;
    }

    private PushMessage forDevice(BrowserPushSubscription subscription, PushMessage message) {
        String url = subscription.isAndroidApp()
                ? org.springframework.web.util.UriComponentsBuilder.fromUriString(message.url())
                    .replaceQueryParam("androidApp", "1").build().toUriString()
                : message.url();
        return new PushMessage(message.title(), message.body(), url, message.tag(), message.timestamp());
    }

    boolean deliver(BrowserPushSubscription subscription, PushMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            PushService pushService = new PushService(publicKey, privateKey, subject);
            var pending = pushService.sendAsync(Notification.builder()
                    .endpoint(subscription.getEndpoint()).userPublicKey(subscription.getP256dhKey())
                    .userAuth(subscription.getAuthKey()).payload(payload).ttl(PUSH_TTL_SECONDS)
                    .urgency(Urgency.HIGH).build());
            HttpResponse response;
            try { response = pending.get(15, TimeUnit.SECONDS); }
            catch (TimeoutException exception) { pending.cancel(true); throw exception; }
            int status = response.getStatusLine().getStatusCode();
            EntityUtils.consumeQuietly(response.getEntity());
            if (status == 404 || status == 410) {
                deactivate(subscription);
                log.warn("Web push subscription {} expired with status {}", subscription.getId(), status);
                return false;
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Push provider returned HTTP " + status);
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Web push delivery was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Web push delivery failed", exception);
        }
    }

    private void deactivate(BrowserPushSubscription subscription) {
        subscription.setActive(false);
        repository.save(subscription);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "वेब सूचना सुरू करण्यासाठी सर्व्हरवर VAPID की कॉन्फिगर करणे आवश्यक आहे.");
        }
    }

    private void validateEndpoint(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            boolean allowedHost = host != null && (java.util.Arrays.stream(ALLOWED_ENDPOINT_HOSTS).anyMatch(host::equals)
                    || java.util.Arrays.stream(ALLOWED_ENDPOINT_SUFFIXES).anyMatch(host::endsWith));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowedHost || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw badRequest("वेब सूचना सेवा endpoint समर्थित नाही.");
            }
        } catch (IllegalArgumentException exception) {
            throw badRequest("वेब सूचना सेवा endpoint अवैध आहे.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record PushKeys(String p256dh, String auth) {}
    public record SubscriptionRequest(String endpoint, PushKeys keys, boolean androidApp) {}
    public record PublicKeyResponse(String publicKey) {}
    public record SubscriptionResponse(Long id) {}
    record PushMessage(String title, String body, String url, String tag, long timestamp) {}
}
