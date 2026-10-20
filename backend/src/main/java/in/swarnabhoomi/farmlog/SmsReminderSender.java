package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SmsReminderSender {
    private static final Logger log = LoggerFactory.getLogger(SmsReminderSender.class);
    private static final DateTimeFormatter MARATHI_DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("mr-IN"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @Value("${app.twilio.account-sid:}") private String accountSid;
    @Value("${app.twilio.auth-token:}") private String authToken;
    @Value("${app.twilio.from-number:}") private String fromNumber;
    @Value("${app.twilio.messaging-service-sid:}") private String messagingServiceSid;

    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SmsReminderSender() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !blank(accountSid) && !blank(authToken)
                && (!blank(fromNumber) || !blank(messagingServiceSid));
    }

    public void send(FarmReminder reminder) {
        if (!isConfigured()) {
            throw new DeliveryException("Twilio account SID, auth token आणि sender number किंवा Messaging Service SID कॉन्फिगर केलेले नाही.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", "+" + reminder.getMobileNumber());
        if (!blank(messagingServiceSid)) {
            form.add("MessagingServiceSid", messagingServiceSid);
        } else {
            form.add("From", fromNumber);
        }
        form.add("Body", "स्वर्णभूमी स्मरणपत्र: " + reminder.getReason()
                + ". तारीख: " + reminder.getScheduledAt().format(MARATHI_DATE)
                + ", वेळ: " + reminder.getScheduledAt().format(TIME) + ".");

        try {
            client.post()
                    .uri("https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Messages.json", accountSid)
                    .headers(headers -> headers.setBasicAuth(accountSid, authToken, StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("SMS reminder delivery failed (HTTP {})", status);
            String explanation = status == 401 || status == 403
                    ? "Twilio ने प्रवेश नाकारला. Account SID आणि auth token तपासा."
                    : status == 429
                    ? "Twilio ची संदेश मर्यादा पूर्ण झाली. थोड्या वेळाने पुन्हा प्रयत्न करा."
                    : "Twilio ने SMS नाकारला. नंबर, sender आणि खाते सेटिंग्ज तपासा.";
            String code = readTwilioErrorCode(exception.getResponseBodyAsString());
            throw new DeliveryException(explanation + " (HTTP " + status
                    + (code.isBlank() ? "" : ", Twilio code " + code) + ").");
        } catch (ResourceAccessException exception) {
            log.warn("SMS reminder could not reach Twilio ({})", exception.getClass().getSimpleName());
            throw new DeliveryException("सर्व्हरचा Twilio शी संपर्क झाला नाही. नेटवर्क जोडणी तपासा.");
        } catch (Exception exception) {
            log.warn("SMS reminder delivery failed ({})", exception.getClass().getSimpleName());
            throw new DeliveryException("SMS पाठवताना अनपेक्षित तांत्रिक अडचण आली.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String readTwilioErrorCode(String responseBody) {
        try {
            return objectMapper.readTree(responseBody).path("code").asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    public static class DeliveryException extends RuntimeException {
        public DeliveryException(String message) { super(message); }
    }
}
