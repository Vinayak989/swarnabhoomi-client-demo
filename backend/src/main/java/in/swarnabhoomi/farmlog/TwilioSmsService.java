package in.swarnabhoomi.farmlog;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TwilioSmsService {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${app.twilio.account-sid:}") private String accountSid;
    @Value("${app.twilio.auth-token:}") private String authToken;
    @Value("${app.twilio.from-number:}") private String fromNumber;
    @Value("${app.twilio.messaging-service-sid:}") private String messagingServiceSid;

    public boolean isConfigured() {
        return !accountSid.isBlank() && !authToken.isBlank()
                && (!fromNumber.isBlank() || !messagingServiceSid.isBlank());
    }

    public void sendOtp(String mobileNumber, String code) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OTP login सुरू करण्यासाठी सर्व्हरवर SMS सेवा कॉन्फिगर करणे आवश्यक आहे.");
        }
        StringBuilder body = new StringBuilder()
                .append("To=").append(encode(mobileNumber))
                .append("&Body=").append(encode("स्वर्णभूमी login OTP: " + code + ". हा कोड ५ मिनिटांत कालबाह्य होईल."));
        if (!messagingServiceSid.isBlank()) {
            body.append("&MessagingServiceSid=").append(encode(messagingServiceSid));
        } else {
            body.append("&From=").append(encode(fromNumber));
        }
        String credentials = Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "SMS सेवा OTP पाठवू शकली नाही. SMS कॉन्फिगरेशन तपासा.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SMS सेवा OTP पाठवू शकली नाही.");
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SMS सेवा सध्या उपलब्ध नाही.");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
