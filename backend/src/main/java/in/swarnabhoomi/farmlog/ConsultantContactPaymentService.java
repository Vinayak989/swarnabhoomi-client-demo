package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConsultantContactPaymentService {
    public static final int AMOUNT_PAISE = 9_900;
    private static final String CURRENCY = "INR";
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${app.payment.razorpay.key-id:}")
    private String keyId;

    @Value("${app.payment.razorpay.key-secret:}")
    private String keySecret;

    public ConsultantContactPaymentService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    public String keyId() {
        requireConfigured();
        return keyId;
    }

    public String createOrder(String receipt, Long sellerId, Long consultantId) {
        requireConfigured();
        JsonNode response = request("POST", "https://api.razorpay.com/v1/orders", Map.of(
                "amount", AMOUNT_PAISE,
                "currency", CURRENCY,
                "receipt", receipt,
                "notes", Map.of("seller_id", String.valueOf(sellerId),
                        "consultant_id", String.valueOf(consultantId), "purpose", "consultant_contact_unlock")));
        String orderId = text(response, "id");
        if (orderId == null || orderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "पेमेंट ऑर्डर तयार झाला नाही. कृपया पुन्हा प्रयत्न करा.");
        }
        return orderId;
    }

    public void verifyCapturedPayment(String orderId, String paymentId, String signature) {
        requireConfigured();
        if (orderId == null || paymentId == null || signature == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "पेमेंटची माहिती अपूर्ण आहे.");
        }
        String expected = signature(orderId + "|" + paymentId);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "पेमेंट स्वाक्षरी तपासता आली नाही.");
        }

        JsonNode payment = request("GET", "https://api.razorpay.com/v1/payments/" + paymentId, null);
        if (!orderId.equals(text(payment, "order_id"))
                || payment.path("amount").asInt(-1) != AMOUNT_PAISE
                || !CURRENCY.equals(text(payment, "currency"))
                || !"captured".equalsIgnoreCase(text(payment, "status"))) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "₹९९ पेमेंट पूर्ण झाल्याची खात्री झाली नाही. पेमेंट पूर्ण करून पुन्हा तपासा.");
        }
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "पेमेंट स्वाक्षरी तपासता आली नाही.");
        }
    }

    private JsonNode request(String method, String url, Object body) {
        try {
            String credentials = Base64.getEncoder().encodeToString(
                    (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + credentials)
                    .header("Accept", "application/json");
            if (body == null) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode result = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "पेमेंट सेवा सध्या उपलब्ध नाही. कृपया थोड्या वेळाने पुन्हा प्रयत्न करा.");
            }
            return result;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "पेमेंट सेवा सध्या उपलब्ध नाही. कृपया थोड्या वेळाने पुन्हा प्रयत्न करा.");
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "संपर्क पेमेंट सध्या सुरू नाही. व्यवस्थापकाने Razorpay पेमेंट तपशील सेट करणे आवश्यक आहे.");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
