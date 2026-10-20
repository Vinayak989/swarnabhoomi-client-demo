package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

@Component
public class WhatsAppReminderSender {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppReminderSender.class);
    private static final DateTimeFormatter MARATHI_DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("mr-IN"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @Value("${app.whatsapp.access-token:}") private String accessToken;
    @Value("${app.whatsapp.phone-number-id:}") private String phoneNumberId;
    @Value("${app.whatsapp.api-version:v23.0}") private String apiVersion;
    @Value("${app.whatsapp.reminder-template:}") private String templateName;
    @Value("${app.whatsapp.template-language:mr}") private String templateLanguage;

    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhatsAppReminderSender() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !blank(accessToken) && !blank(phoneNumberId) && !blank(templateName);
    }

    public void send(FarmReminder reminder) {
        if (!isConfigured()) {
            throw new DeliveryException("सर्व्हरवर WhatsApp Cloud API टोकन, फोन नंबर ID किंवा मंजूर संदेश नमुना जोडलेला नाही.");
        }

        List<Map<String, Object>> parameters = List.of(
                Map.of("type", "text", "text", reminder.getReason()),
                Map.of("type", "text", "text", reminder.getScheduledAt().format(MARATHI_DATE)),
                Map.of("type", "text", "text", reminder.getScheduledAt().format(TIME)));
        Map<String, Object> template = Map.of(
                "name", templateName,
                "language", Map.of("code", templateLanguage),
                "components", List.of(Map.of("type", "body", "parameters", parameters)));
        Map<String, Object> message = Map.of(
                "messaging_product", "whatsapp",
                "to", reminder.getMobileNumber(),
                "type", "template",
                "template", template);

        try {
            client.post()
                    .uri("https://graph.facebook.com/{version}/{phoneNumberId}/messages", apiVersion, phoneNumberId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("WhatsApp reminder delivery failed (HTTP {})", status);
            throw new DeliveryException(describeApiFailure(exception, reminder));
        } catch (ResourceAccessException exception) {
            log.warn("WhatsApp reminder could not reach Meta ({})", exception.getClass().getSimpleName());
            throw new DeliveryException("सर्व्हरचा WhatsApp API शी संपर्क झाला नाही. इंटरनेट किंवा नेटवर्क जोडणी तपासा.");
        } catch (Exception exception) {
            // Avoid logging the request, phone number, token, or Meta's potentially sensitive response body.
            log.warn("WhatsApp reminder delivery failed ({})", exception.getClass().getSimpleName());
            throw new DeliveryException("WhatsApp संदेश पाठवताना अनपेक्षित तांत्रिक अडचण आली.");
        }
    }

    private String describeApiFailure(RestClientResponseException exception, FarmReminder reminder) {
        int status = exception.getStatusCode().value();
        JsonNode error = readMetaError(exception.getResponseBodyAsString());
        String code = error.path("code").asText("");
        String detail = error.path("message").asText("");
        String explanation;
        if (status == 401 || status == 403) {
            explanation = "WhatsApp API ने प्रवेश नाकारला. टोकन आणि आवश्यक परवानग्या तपासा.";
        } else if (status == 429) {
            explanation = "WhatsApp API ची संदेश मर्यादा पूर्ण झाली. थोड्या वेळाने पुन्हा प्रयत्न करा.";
        } else if (status >= 500) {
            explanation = "Meta WhatsApp सेवा सध्या उपलब्ध नाही. थोड्या वेळाने पुन्हा प्रयत्न करा.";
        } else {
            explanation = "Meta WhatsApp API ने संदेश नाकारला.";
        }

        StringBuilder note = new StringBuilder(explanation).append(" (HTTP ").append(status);
        if (!code.isBlank()) note.append(", कोड ").append(code);
        note.append(").");
        if (!detail.isBlank()) {
            String safeDetail = detail;
            if (!blank(accessToken)) safeDetail = safeDetail.replace(accessToken, "[लपवले]");
            safeDetail = safeDetail.replace(reminder.getMobileNumber(), "निवडलेला नंबर")
                    .replaceAll("[\\r\\n\\t]+", " ").trim();
            if (!safeDetail.isBlank()) note.append(" Meta कारण: ").append(safeDetail);
        }
        return note.length() > 290 ? note.substring(0, 287) + "..." : note.toString();
    }

    private JsonNode readMetaError(String responseBody) {
        try {
            return objectMapper.readTree(responseBody).path("error");
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public static class DeliveryException extends RuntimeException {
        public DeliveryException(String message) { super(message); }
    }
}
