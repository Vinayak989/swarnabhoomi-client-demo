package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class VoiceReminderSender {
    private static final Logger log = LoggerFactory.getLogger(VoiceReminderSender.class);
    private static final Duration AUDIO_TTL = Duration.ofMinutes(15);
    private static final int MAX_AUDIO_CLIPS = 100;
    private static final DateTimeFormatter MARATHI_DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("mr-IN"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String CLOUD_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final ConcurrentHashMap<UUID, AudioClip> AUDIO_CLIPS = new ConcurrentHashMap<>();

    @Value("${app.twilio.account-sid:}") private String accountSid;
    @Value("${app.twilio.auth-token:}") private String authToken;
    @Value("${app.twilio.from-number:}") private String fromNumber;
    @Value("${app.google.tts.credentials-json:}") private String googleCredentialsJson;
    @Value("${app.public-url:}") private String publicUrl;
    @Value("${RENDER_EXTERNAL_URL:}") private String renderExternalUrl;

    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VoiceReminderSender() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !blank(accountSid) && !blank(authToken) && !blank(fromNumber)
                && !blank(googleCredentialsJson) && !blank(publicBaseUrl());
    }

    public void send(FarmReminder reminder) {
        if (!isConfigured()) {
            throw new DeliveryException("फोन कॉलसाठी Twilio Voice नंबर, Google Cloud TTS credentials आणि सार्वजनिक HTTPS URL कॉन्फिगर करणे आवश्यक आहे.");
        }

        UUID audioId = UUID.randomUUID();
        byte[] audio = synthesize(reminder);
        storeAudio(audioId, audio);
        String audioUrl = publicBaseUrl() + "/api/reminders/call-audio/" + audioId;
        String twiml = "<Response><Play>" + audioUrl + "</Play></Response>";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", "+" + reminder.getMobileNumber());
        form.add("From", fromNumber);
        form.add("Twiml", twiml);

        try {
            client.post()
                    .uri("https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Calls.json", accountSid)
                    .headers(headers -> headers.setBasicAuth(accountSid, authToken, StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            AUDIO_CLIPS.remove(audioId);
            int status = exception.getStatusCode().value();
            log.warn("Voice reminder call failed (HTTP {})", status);
            String explanation = status == 401 || status == 403
                    ? "Twilio ने कॉलसाठी प्रवेश नाकारला. Account SID, auth token आणि Voice परवानग्या तपासा."
                    : status == 429
                    ? "Twilio ची कॉल मर्यादा पूर्ण झाली. थोड्या वेळाने पुन्हा प्रयत्न करा."
                    : "Twilio ने कॉल नाकारला. नंबर, Voice-सक्षम sender आणि भौगोलिक परवानग्या तपासा.";
            String code = readTwilioErrorCode(exception.getResponseBodyAsString());
            throw new DeliveryException(explanation + " (HTTP " + status
                    + (code.isBlank() ? "" : ", Twilio code " + code) + ").");
        } catch (ResourceAccessException exception) {
            AUDIO_CLIPS.remove(audioId);
            log.warn("Voice reminder could not reach Twilio ({})", exception.getClass().getSimpleName());
            throw new DeliveryException("सर्व्हरचा Twilio Voice शी संपर्क झाला नाही. नेटवर्क जोडणी तपासा.");
        } catch (RestClientException exception) {
            AUDIO_CLIPS.remove(audioId);
            log.warn("Voice reminder call failed ({})", exception.getClass().getSimpleName());
            throw new DeliveryException("स्मरणपत्र कॉल सुरू करताना अनपेक्षित तांत्रिक अडचण आली.");
        } catch (IllegalArgumentException exception) {
            AUDIO_CLIPS.remove(audioId);
            log.warn("Voice reminder call request was invalid ({})", exception.getClass().getSimpleName());
            throw new DeliveryException("स्मरणपत्र कॉलची सेटिंग तपासा.");
        }
    }

    public static byte[] getAudio(UUID audioId) {
        pruneExpiredAudio();
        AudioClip clip = AUDIO_CLIPS.get(audioId);
        if (clip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return clip.bytes();
    }

    private byte[] synthesize(FarmReminder reminder) {
        String text = "स्वर्णभूमी स्मरणपत्र. " + reminder.getReason()
                + ". तारीख " + reminder.getScheduledAt().format(MARATHI_DATE)
                + ", वेळ " + reminder.getScheduledAt().format(TIME) + ".";
        Map<String, Object> request = Map.of(
                "input", Map.of("text", text),
                "voice", Map.of("languageCode", "mr-IN"),
                "audioConfig", Map.of("audioEncoding", "MP3", "speakingRate", 0.95));
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(googleCredentialsJson.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(CLOUD_SCOPE);
            credentials.refreshIfExpired();
            String accessToken = credentials.getAccessToken().getTokenValue();
            JsonNode response = client.post()
                    .uri("https://texttospeech.googleapis.com/v1/text:synthesize")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            String encodedAudio = response == null ? "" : response.path("audioContent").asText("");
            if (encodedAudio.isBlank()) {
                throw new DeliveryException("Google Cloud TTS ने कॉलसाठी audio तयार केला नाही.");
            }
            return Base64.getDecoder().decode(encodedAudio);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("Google Cloud TTS failed (HTTP {})", status);
            throw new DeliveryException("Google Cloud TTS ने मराठी audio तयार केला नाही. Credentials, API आणि mr-IN voice तपासा (HTTP "
                    + status + ").");
        } catch (ResourceAccessException exception) {
            log.warn("Google Cloud TTS could not be reached ({})", exception.getClass().getSimpleName());
            throw new DeliveryException("सर्व्हरचा Google Cloud TTS शी संपर्क झाला नाही.");
        } catch (DeliveryException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Google Cloud TTS request failed ({})", exception.getClass().getSimpleName());
            throw new DeliveryException("Google Cloud TTS शी संपर्क होऊ शकला नाही.");
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("Google Cloud TTS failed ({})", exception.getClass().getSimpleName());
            throw new DeliveryException("Google Cloud TTS credentials तपासा; कॉलसाठी audio तयार करता आला नाही.");
        }
    }

    private String publicBaseUrl() {
        String base = blank(publicUrl) ? renderExternalUrl : publicUrl;
        if (blank(base)) return "";
        String normalized = base.trim().replaceAll("/+$", "");
        try {
            java.net.URI uri = java.net.URI.create(normalized);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null ? normalized : "";
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static synchronized void storeAudio(UUID audioId, byte[] bytes) {
        pruneExpiredAudio();
        while (AUDIO_CLIPS.size() >= MAX_AUDIO_CLIPS) {
            UUID oldest = AUDIO_CLIPS.entrySet().stream()
                    .min((left, right) -> left.getValue().expiresAt().compareTo(right.getValue().expiresAt()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldest == null) break;
            AUDIO_CLIPS.remove(oldest);
        }
        AUDIO_CLIPS.put(audioId, new AudioClip(bytes, Instant.now().plus(AUDIO_TTL)));
    }

    private static void pruneExpiredAudio() {
        Instant now = Instant.now();
        AUDIO_CLIPS.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String readTwilioErrorCode(String responseBody) {
        try {
            return objectMapper.readTree(responseBody).path("code").asText("");
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return "";
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record AudioClip(byte[] bytes, Instant expiresAt) {}

    public static class DeliveryException extends RuntimeException {
        public DeliveryException(String message) { super(message); }
    }
}
