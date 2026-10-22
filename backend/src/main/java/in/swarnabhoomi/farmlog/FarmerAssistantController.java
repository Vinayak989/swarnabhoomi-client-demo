package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class FarmerAssistantController {
    private static final int MAX_MESSAGES = 12;
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_CONVERSATION_LENGTH = 8000;
    private static final int MAX_IMAGE_BYTES = 4_000_000;
    private static final int MAX_IMAGE_DATA_URL_LENGTH = 5_400_000;
    private static final String SYSTEM_PROMPT = """
            You are a helpful agricultural assistant for farmers in India. Answer in the same language as the
            farmer's latest question. Be clear, practical, and concise. Ask a brief clarifying question when
            important details such as crop, location, or growth stage are missing. Do not invent local weather,
            prices, or official recommendations. When a photo is attached, describe only visible details and explain
            uncertainty; do not claim a certain diagnosis from an image alone. For pesticides, fertilizers, or other
            chemicals, never guess a product dose or mixing instruction; advise following the product label and
            consulting a local agricultural officer. For urgent crop disease concerns, recommend local expert
            inspection. Do not claim to be a government authority.
            """;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public FarmerAssistantController(RestClient.Builder restClientBuilder,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-2.5-flash}") String model) {
        this.restClient = restClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        validate(request);
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google Gemini API सुरू करण्यासाठी GEMINI_API_KEY कॉन्फिगर करा.");
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        for (int index = 0; index < request.messages().size(); index++) {
            ChatMessage message = request.messages().get(index);
            String imageDataUrl = message.imageDataUrl();
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("text", message.content().trim()));
            if (imageDataUrl != null && !imageDataUrl.isBlank()) {
                if (index != request.messages().size() - 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोटो फक्त नवीनतम प्रश्नासोबत पाठवा.");
                }
                validateImage(imageDataUrl);
                String mediaType = imageDataUrl.substring(5, imageDataUrl.indexOf(';'));
                String base64Image = imageDataUrl.substring(imageDataUrl.indexOf(',') + 1);
                parts.add(Map.of("inlineData", Map.of("mimeType", mediaType, "data", base64Image)));
            }
            contents.add(Map.of("role", "assistant".equals(message.role()) ? "model" : "user", "parts", parts));
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                            "contents", contents))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 400) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Google Gemini ने विनंती नाकारली. प्रश्न किंवा फोटो तपासून पुन्हा प्रयत्न करा.");
            }
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Google Gemini API key अमान्य आहे किंवा API ला प्रवेश नाही. GEMINI_API_KEY तपासा.");
            }
            if (status == 404) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Google Gemini मॉडेल उपलब्ध नाही. GEMINI_MODEL तपासा.");
            }
            if (status == 429) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Google Gemini ची विनंती मर्यादा पूर्ण झाली. थोड्या वेळाने पुन्हा प्रयत्न करा.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google Gemini HTTP " + status + " प्रतिसाद देत आहे.");
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google Gemini शी संपर्क होऊ शकला नाही. इंटरनेट कनेक्शन तपासून पुन्हा प्रयत्न करा.", exception);
        }

        StringBuilder answerText = new StringBuilder();
        JsonNode parts = response == null ? null : response.path("candidates").path(0).path("content").path("parts");
        if (parts != null && parts.isArray()) {
            for (JsonNode part : parts) {
                String text = part.path("text").asText("").trim();
                if (!text.isEmpty()) {
                    if (!answerText.isEmpty()) answerText.append('\n');
                    answerText.append(text);
                }
            }
        }
        String answer = answerText.toString().trim();
        if (answer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Gemini कडून वाचता येणारे उत्तर मिळाले नाही.");
        }
        return new ChatResponse(answer);
    }

    private void validate(ChatRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()
                || request.messages().size() > MAX_MESSAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "संभाषणातील संदेशांची संख्या योग्य नाही.");
        }
        int totalLength = 0;
        for (ChatMessage message : request.messages()) {
            if (message == null || (!"user".equals(message.role()) && !"assistant".equals(message.role()))
                    || message.content() == null || message.content().isBlank()
                    || message.content().length() > MAX_MESSAGE_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "संदेश रिकामा किंवा खूप मोठा आहे.");
            }
            totalLength += message.content().length();
        }
        if (totalLength > MAX_CONVERSATION_LENGTH
                || !"user".equals(request.messages().get(request.messages().size() - 1).role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "संभाषण खूप मोठे आहे किंवा शेवटचा संदेश प्रश्न नाही.");
        }
    }

    private void validateImage(String imageDataUrl) {
        if (imageDataUrl.length() > MAX_IMAGE_DATA_URL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोटोचा आकार ४ MB पेक्षा कमी ठेवा.");
        }
        int separator = imageDataUrl.indexOf(',');
        if (separator < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोटोचा प्रकार समर्थित नाही.");
        }
        String mediaType = imageDataUrl.substring(0, separator);
        if (!List.of("data:image/jpeg;base64", "data:image/png;base64", "data:image/webp;base64").contains(mediaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फक्त JPG, PNG किंवा WEBP फोटो वापरा.");
        }
        try {
            byte[] imageBytes = Base64.getDecoder().decode(imageDataUrl.substring(separator + 1));
            if (imageBytes.length == 0 || imageBytes.length > MAX_IMAGE_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोटोचा आकार ४ MB पेक्षा कमी ठेवा.");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोटोचा डेटा वाचता आला नाही.");
        }
    }

    public record ChatRequest(List<ChatMessage> messages) {
    }

    public record ChatMessage(String role, String content, String imageDataUrl) {
    }

    public record ChatResponse(String answer) {
    }
}
