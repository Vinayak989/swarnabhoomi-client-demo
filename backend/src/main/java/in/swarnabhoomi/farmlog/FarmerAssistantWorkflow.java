package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/** Runs text chat and crop-photo questions through a LangGraph4j workflow. */
@Service
public class FarmerAssistantWorkflow {
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

    private static final String PHOTO_PROMPT = SYSTEM_PROMPT + """

            For this photo question, use this response structure in the farmer's language:
            1. What is visibly present in the image.
            2. Up to three possible causes, clearly marked as possibilities rather than a diagnosis.
            3. One or two safe checks the farmer can make next.
            Ask for the crop, village or district, and crop stage if they are needed to narrow the possibilities.
            Do not infer symptoms that are not visible in the image.
            """;

    private static final String MESSAGES_JSON = "messagesJson";
    private static final String PHOTO_ATTACHED = "photoAttached";
    private static final String ROUTE = "route";
    private static final String ANSWER = "answer";
    private static final String ERROR_STATUS = "errorStatus";
    private static final String ERROR_MESSAGE = "errorMessage";

    private static final Map<String, Channel<?>> STATE_SCHEMA = Map.of(
            MESSAGES_JSON, Channels.base(() -> ""),
            PHOTO_ATTACHED, Channels.base(() -> false),
            ROUTE, Channels.base(() -> ""),
            ANSWER, Channels.base(() -> ""),
            ERROR_STATUS, Channels.base(() -> 0),
            ERROR_MESSAGE, Channels.base(() -> ""));

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final CompiledGraph<AssistantState> assistantGraph;

    public FarmerAssistantWorkflow(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-2.5-flash}") String model) {
        this.restClient = restClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.assistantGraph = createGraph();
    }

    public String answer(List<FarmerAssistantController.ChatMessage> messages) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google Gemini API सुरू करण्यासाठी GEMINI_API_KEY कॉन्फिगर करा.");
        }

        String messagesJson;
        try {
            messagesJson = objectMapper.writeValueAsString(messages);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "संभाषण वाचता आले नाही.");
        }

        boolean photoAttached = messages.stream()
                .anyMatch(message -> message.imageDataUrl() != null && !message.imageDataUrl().isBlank());
        Map<String, Object> input = Map.of(MESSAGES_JSON, messagesJson, PHOTO_ATTACHED, photoAttached);
        AssistantState result = assistantGraph.invoke(
                GraphInput.args(input), RunnableConfig.builder().disableCloneState().build())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "AI सहाय्यकाकडून उत्तर मिळाले नाही."));

        if (result.errorStatus() > 0) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(result.errorStatus()), result.errorMessage());
        }
        if (result.answer().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google Gemini कडून वाचता येणारे उत्तर मिळाले नाही.");
        }
        return result.answer();
    }

    private CompiledGraph<AssistantState> createGraph() {
        try {
            return new StateGraph<>(STATE_SCHEMA, AssistantState::new)
                    .addNode("route_question", node_async(state -> Map.of(
                            ROUTE, state.photoAttached() ? "photo" : "text")))
                    .addNode("text_advice", node_async(state -> generateAnswer(state, SYSTEM_PROMPT)))
                    .addNode("photo_advice", node_async(state -> generateAnswer(state, PHOTO_PROMPT)))
                    .addEdge(START, "route_question")
                    .addConditionalEdges("route_question", edge_async(AssistantState::route),
                            Map.of("text", "text_advice", "photo", "photo_advice"))
                    .addEdge("text_advice", END)
                    .addEdge("photo_advice", END)
                    .compile();
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Could not compile the farmer assistant graph", exception);
        }
    }

    private Map<String, Object> generateAnswer(AssistantState state, String systemPrompt) {
        try {
            List<FarmerAssistantController.ChatMessage> messages = objectMapper.readValue(
                    state.messagesJson(), new TypeReference<>() { });
            JsonNode response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                            "contents", toGeminiContents(messages)))
                    .retrieve()
                    .body(JsonNode.class);

            String answer = extractAnswer(response);
            if (answer.isBlank()) {
                return failure(HttpStatus.BAD_GATEWAY,
                        "Google Gemini कडून वाचता येणारे उत्तर मिळाले नाही.");
            }
            return Map.of(ANSWER, answer, ERROR_STATUS, 0, ERROR_MESSAGE, "");
        } catch (RestClientResponseException exception) {
            return geminiHttpFailure(exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google Gemini शी संपर्क होऊ शकला नाही. इंटरनेट कनेक्शन तपासून पुन्हा प्रयत्न करा.");
        } catch (JsonProcessingException exception) {
            return failure(HttpStatus.BAD_REQUEST, "संभाषण वाचता आले नाही.");
        }
    }

    private List<Map<String, Object>> toGeminiContents(List<FarmerAssistantController.ChatMessage> messages) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (FarmerAssistantController.ChatMessage message : messages) {
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("text", message.content().trim()));

            String imageDataUrl = message.imageDataUrl();
            if (imageDataUrl != null && !imageDataUrl.isBlank()) {
                int separator = imageDataUrl.indexOf(',');
                String mediaType = imageDataUrl.substring(5, imageDataUrl.indexOf(';'));
                String base64Image = imageDataUrl.substring(separator + 1);
                parts.add(Map.of("inlineData", Map.of("mimeType", mediaType, "data", base64Image)));
            }
            contents.add(Map.of("role", "assistant".equals(message.role()) ? "model" : "user", "parts", parts));
        }
        return contents;
    }

    private String extractAnswer(JsonNode response) {
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
        return answerText.toString().trim();
    }

    private Map<String, Object> geminiHttpFailure(int status) {
        if (status == 400) {
            return failure(HttpStatus.BAD_REQUEST,
                    "Google Gemini ने विनंती नाकारली. प्रश्न किंवा फोटो तपासून पुन्हा प्रयत्न करा.");
        }
        if (status == 401 || status == 403) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google Gemini API key अमान्य आहे किंवा API ला प्रवेश नाही. GEMINI_API_KEY तपासा.");
        }
        if (status == 404) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google Gemini मॉडेल उपलब्ध नाही. GEMINI_MODEL तपासा.");
        }
        if (status == 429) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google Gemini ची विनंती मर्यादा पूर्ण झाली. थोड्या वेळाने पुन्हा प्रयत्न करा.");
        }
        return failure(HttpStatus.BAD_GATEWAY, "Google Gemini HTTP " + status + " प्रतिसाद देत आहे.");
    }

    private Map<String, Object> failure(HttpStatusCode status, String message) {
        return Map.of(ANSWER, "", ERROR_STATUS, status.value(), ERROR_MESSAGE, message);
    }

    private static final class AssistantState extends AgentState {
        private AssistantState(Map<String, Object> initData) {
            super(initData);
        }

        private boolean photoAttached() {
            return this.<Boolean>value(PHOTO_ATTACHED).orElse(false);
        }

        private String route() {
            return this.<String>value(ROUTE).orElse("text");
        }

        private String messagesJson() {
            return this.<String>value(MESSAGES_JSON).orElse("");
        }

        private String answer() {
            return this.<String>value(ANSWER).orElse("");
        }

        private int errorStatus() {
            return this.<Integer>value(ERROR_STATUS).orElse(0);
        }

        private String errorMessage() {
            return this.<String>value(ERROR_MESSAGE).orElse("");
        }
    }
}
