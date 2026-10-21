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
    private final String model;

    public FarmerAssistantController(RestClient.Builder restClientBuilder,
            @Value("${app.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${app.ollama.model:qwen2.5vl:3b}") String model) {
        this.restClient = restClientBuilder.baseUrl(ollamaBaseUrl).build();
        this.model = model;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        validate(request);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (int index = 0; index < request.messages().size(); index++) {
            ChatMessage message = request.messages().get(index);
            String imageDataUrl = message.imageDataUrl();
            Object content = message.content().trim();
            if (imageDataUrl != null && !imageDataUrl.isBlank()) {
                if (index != request.messages().size() - 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोटो फक्त नवीनतम प्रश्नासोबत पाठवा.");
                }
                validateImage(imageDataUrl);
                content = message.content().trim();
                String base64Image = imageDataUrl.substring(imageDataUrl.indexOf(',') + 1);
                messages.add(Map.of(
                        "role", message.role(),
                        "content", content,
                        "images", List.of(base64Image)));
                continue;
            }
            messages.add(Map.of("role", message.role(), "content", content));
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "messages", messages, "stream", false))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Ollama मध्ये " + model + " मॉडेल सापडले नाही. `ollama pull " + model + "` चालवा.");
            }
            if (exception.getStatusCode().value() == 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Ollama ने विनंती नाकारली. फोटोसाठी vision समर्थित मॉडेल वापरा.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Ollama HTTP " + exception.getStatusCode().value() + " प्रतिसाद देत आहे.");
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ollama शी संपर्क होऊ शकला नाही. Ollama सुरू आहे का ते तपासा (`ollama serve`).", exception);
        }

        String answer = response == null
                ? ""
                : response.path("message").path("content").asText("").trim();
        if (answer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ollama कडून वाचता येणारे उत्तर मिळाले नाही.");
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
