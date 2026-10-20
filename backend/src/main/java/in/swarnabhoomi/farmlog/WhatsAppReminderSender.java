package in.swarnabhoomi.farmlog;

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
            throw new IllegalStateException("WhatsApp Cloud API is not configured");
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
        } catch (Exception exception) {
            // Avoid logging the request, phone number, token, or Meta's potentially sensitive response body.
            log.warn("WhatsApp reminder delivery failed ({})", exception.getClass().getSimpleName());
            throw new IllegalStateException("WhatsApp message delivery failed");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
