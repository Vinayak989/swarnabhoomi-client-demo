package in.swarnabhoomi.farmlog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api/assistant")
public class FarmerAssistantController {
    private static final int MAX_MESSAGES = 12;
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_CONVERSATION_LENGTH = 8000;
    private static final int MAX_FARM_CONTEXT_LENGTH = 6000;
    private static final int MAX_IMAGE_BYTES = 4_000_000;
    private static final int MAX_IMAGE_DATA_URL_LENGTH = 5_400_000;

    private final FarmerAssistantWorkflow assistantWorkflow;

    public FarmerAssistantController(FarmerAssistantWorkflow assistantWorkflow) {
        this.assistantWorkflow = assistantWorkflow;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        validate(request);
        validateImagePlacement(request.messages());
        return new ChatResponse(assistantWorkflow.answer(request.messages(), request.farmContext()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ChatErrorResponse> handleAssistantError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ChatErrorResponse(exception.getReason()));
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
                || request.farmContext() != null && request.farmContext().length() > MAX_FARM_CONTEXT_LENGTH
                || !"user".equals(request.messages().get(request.messages().size() - 1).role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "संभाषण खूप मोठे आहे किंवा शेवटचा संदेश प्रश्न नाही.");
        }
    }

    private void validateImagePlacement(List<ChatMessage> messages) {
        for (int index = 0; index < messages.size(); index++) {
            String imageDataUrl = messages.get(index).imageDataUrl();
            if (imageDataUrl == null || imageDataUrl.isBlank()) {
                continue;
            }
            if (index != messages.size() - 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "फोटो फक्त नवीनतम प्रश्नासोबत पाठवा.");
            }
            validateImage(imageDataUrl);
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

    public record ChatRequest(List<ChatMessage> messages, String farmContext) {
    }

    public record ChatMessage(String role, String content, String imageDataUrl) {
    }

    public record ChatResponse(String answer) {
    }

    public record ChatErrorResponse(String detail) {
    }
}
