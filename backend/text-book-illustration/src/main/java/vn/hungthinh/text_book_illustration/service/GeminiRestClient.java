package vn.hungthinh.text_book_illustration.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import vn.hungthinh.text_book_illustration.config.AppProperties;

/**
 * Production implementation of GeminiClient.
 * <p>
 * Real REST calls: generateStyle, generateCharacters, generateChapters
 * Mocked (billing blocker): generatePortrait, generateIllustration
 * <p>
 * See DECISIONS.md for the billing/mock decision rationale.
 */
@Slf4j
@Primary
@Service
public class GeminiRestClient implements GeminiClient {

    private final RestClient restClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public GeminiRestClient(
            @Qualifier("geminiHttpClient") RestClient restClient,
            AppProperties appProperties) {
        this.restClient = restClient;
        this.appProperties = appProperties;
    }

    // ------------------------------------------------------------------ //
    //  Text generation — real REST calls                                  //
    // ------------------------------------------------------------------ //

    @Override
    public Result<String> generateStyle(String bookText, String previousInteractionId) {
        log.info("[Service] generateStyle: calling Gemini API");
        String prompt = "Analyze this book text and describe the overall visual art style that would best suit "
                + "illustrating this story. Be concise — 1-2 sentences, focusing on color palette, "
                + "mood, and artistic technique.\n\nBook text:\n" + bookText;

        InteractionResult result = callGemini(prompt, previousInteractionId, null);
        String style = result.textContent().strip();
        log.info("[Service] generateStyle: received style, interactionId={}", result.interactionId());
        return new Result<>(style, result.interactionId());
    }

    @Override
    public Result<List<CharacterData>> generateCharacters(String previousInteractionId) {
        log.info("[Service] generateCharacters: calling Gemini API");
        String prompt = "Extract up to 2 main characters from this book text. "
                + "For each character provide their name and a detailed image prompt suitable "
                + "for portrait illustration. Return as JSON.";

        InteractionResult result = callGemini(prompt, previousInteractionId, characterResponseFormat());
        try {
            CharacterList parsed = objectMapper.readValue(result.textContent(), CharacterList.class);
            List<CharacterData> characters = parsed.characters().stream()
                    .map(c -> new CharacterData(c.name(), c.imagePrompt()))
                    .toList();
            log.info("[Service] generateCharacters: parsed {} characters, interactionId={}", characters.size(), result.interactionId());
            return new Result<>(characters, result.interactionId());
        } catch (Exception e) {
            log.error("[Service] generateCharacters: failed to parse Gemini response body={}, error={}", result.textContent(), e.getMessage(), e);
            throw new RuntimeException("Failed to parse Gemini character response: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<List<ChapterData>> generateChapters(String style, String previousInteractionId) {
        log.info("[Service] generateChapters: calling Gemini API");
        String prompt = "Identify 1 key dramatic scene from this book text that would make a compelling illustration. "
                + "Write a detailed illustration prompt in the visual style: '" + style + "'."
                + " Return as JSON.";

        InteractionResult result = callGemini(prompt, previousInteractionId, chapterResponseFormat());
        try {
            ChapterList parsed = objectMapper.readValue(result.textContent(), ChapterList.class);
            List<ChapterData> chapters = parsed.chapters().stream()
                    .map(c -> new ChapterData(c.illustrationPrompt()))
                    .toList();
            log.info("[Service] generateChapters: parsed {} chapters, interactionId={}", chapters.size(), result.interactionId());
            return new Result<>(chapters, result.interactionId());
        } catch (Exception e) {
            log.error("[Service] generateChapters: failed to parse Gemini response body={}, error={}", result.textContent(), e.getMessage(), e);
            throw new RuntimeException("Failed to parse Gemini chapter response: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Image generation — mocked (billing blocker, see DECISIONS.md)     //
    // ------------------------------------------------------------------ //

    @Override
    public Result<String> generatePortrait(UUID characterId, String characterName, String imagePrompt, String previousInteractionId) {
        // TODO: swap to a real API call once billing is resolved — see DECISIONS.md
        log.info("[Service] generatePortrait: using mock image for characterId={}", characterId);
        String path = copyMockImage("mock-images/portrait-sample.png", "portraits", characterId);
        return new Result<>(path, previousInteractionId);
    }

    @Override
    public Result<String> generateIllustration(UUID chapterId, String illustrationPrompt, String previousInteractionId) {
        // TODO: swap to a real API call once billing is resolved — see DECISIONS.md
        log.info("[Service] generateIllustration: using mock image for chapterId={}", chapterId);
        String path = copyMockImage("mock-images/illustration-sample.png", "illustrations", chapterId);
        return new Result<>(path, previousInteractionId);
    }

    // ------------------------------------------------------------------ //
    //  Core REST call helper                                              //
    // ------------------------------------------------------------------ //

    private InteractionResult callGemini(String prompt, String previousInteractionId, ResponseFormat responseFormat) {
        AppProperties.Gemini geminiProps = appProperties.getGemini();

        InteractionRequest requestBody = new InteractionRequest(
                geminiProps.getTextModel(),
                prompt,
                previousInteractionId,
                responseFormat
        );

        try {
            InteractionResponse response = restClient.post()
                    .uri(geminiProps.getBaseUrl() + "/interactions")
                    .header("x-goog-api-key", geminiProps.getApiKey())
                    .header("Api-Revision", geminiProps.getApiRevision())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(InteractionResponse.class);

            if (response == null) {
                throw new RuntimeException("Gemini API returned an empty response body");
            }

            String text = response.steps().stream()
                    .filter(step -> "model_output".equals(step.type()))
                    .findFirst()
                    .map(step -> step.content().get(0).text())
                    .orElseThrow(() -> new RuntimeException("No model_output step found in Gemini response"));

            return new InteractionResult(text, response.id());

        } catch (RestClientResponseException e) {
            String errorMsg = parseErrorMessage(e.getResponseBodyAsString(), e.getStatusCode().value());
            log.error("[Service] Gemini API error: status={}, message={}", e.getStatusCode().value(), errorMsg);
            throw new RuntimeException(errorMsg, e);

        } catch (ResourceAccessException e) {
            log.error("[Service] Gemini API network error: {}", e.getMessage(), e);
            throw new RuntimeException("Gemini API network error: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("[Service] Gemini API response error: {}", e.getMessage(), e);
            if (e.getMessage() != null && (e.getMessage().contains("extracting response") || e.getMessage().contains("content type"))) {
                throw new RuntimeException("Gemini API returned an invalid response or connection timed out.", e);
            }
            throw new RuntimeException("Gemini API request failed: " + e.getMessage(), e);
        }
    }

    private String parseErrorMessage(String responseBody, int statusCode) {
        try {
            ErrorResponse error = objectMapper.readValue(responseBody, ErrorResponse.class);
            if (error.error() != null && error.error().message() != null) {
                return error.error().message();
            }
        } catch (Exception ignored) {
            // Malformed error body — fall through to generic message
        }
        return "Gemini API call failed: HTTP " + statusCode;
    }

    // ------------------------------------------------------------------ //
    //  Mock image helper                                                  //
    // ------------------------------------------------------------------ //

    private String copyMockImage(String resourcePath, String subfolder, UUID entityId) {
        try {
            Path outputDir = Path.of(appProperties.getFileStorageRoot(), subfolder);
            Files.createDirectories(outputDir);
            Path outputFile = outputDir.resolve(entityId + ".png");

            if (Files.notExists(outputFile)) {
                InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
                if (is != null) {
                    try (is) {
                        Files.copy(is, outputFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    log.warn("[Service] Mock image resource '{}' not found on classpath — writing placeholder PNG", resourcePath);
                    writePlaceholderPng(outputFile);
                }
            }

            String root = appProperties.getFileStorageRoot().replace("./", "").replace(".\\", "");
            if (root.startsWith("/")) root = root.substring(1);

            return "/" + root + "/" + subfolder + "/" + entityId + ".png";
        } catch (IOException e) {
            log.error("[Service] Failed to copy mock image: entityId={}, subfolder={}, error={}", entityId, subfolder, e.getMessage(), e);
            throw new RuntimeException("Failed to copy mock image: " + e.getMessage(), e);
        }
    }

    /**
     * Writes a minimal 1×1 transparent PNG as a fallback when classpath resources are absent.
     * This keeps the app runnable even without bundled sample images in the resources directory.
     */
    private void writePlaceholderPng(Path path) throws IOException {
        // Minimal valid 1x1 transparent PNG (67 bytes, Base64-encoded)
        byte[] minimalPng = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        Files.write(path, minimalPng);
    }

    // ------------------------------------------------------------------ //
    //  Response-format schema builders                                   //
    // ------------------------------------------------------------------ //

    private ResponseFormat characterResponseFormat() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "characters", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "name", Map.of("type", "string"),
                                                "imagePrompt", Map.of("type", "string")),
                                        "required", List.of("name", "imagePrompt")))),
                "required", List.of("characters"));
        return new ResponseFormat("text", "application/json", schema);
    }

    private ResponseFormat chapterResponseFormat() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "chapters", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "illustrationPrompt", Map.of("type", "string")),
                                        "required", List.of("illustrationPrompt")))),
                "required", List.of("chapters"));
        return new ResponseFormat("text", "application/json", schema);
    }

    // ------------------------------------------------------------------ //
    //  Request / response DTOs (internal to this class)                  //
    // ------------------------------------------------------------------ //

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record InteractionRequest(
            String model,
            String input,
            @JsonProperty("previous_interaction_id") String previousInteractionId,
            @JsonProperty("response_format") ResponseFormat responseFormat) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ResponseFormat(
            String type,
            @JsonProperty("mime_type") String mimeType,
            Object schema) {}

    record InteractionResponse(
            String id,
            String status,
            List<InteractionStep> steps) {}

    record InteractionStep(
            String type,
            List<StepContent> content) {}

    record StepContent(
            String type,
            String text) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(ErrorBody error) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorBody(String message, Object code) {}

    // Structured-output wrappers for JSON parsing
    record CharacterList(List<CharacterWrapper> characters) {}
    record CharacterWrapper(String name, String imagePrompt) {}
    record ChapterList(List<ChapterWrapper> chapters) {}
    record ChapterWrapper(String illustrationPrompt) {}

    // Internal carrier (text + interaction ID from one Gemini call)
    record InteractionResult(String textContent, String interactionId) {}
}
