package vn.hungthinh.text_book_illustration.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import vn.hungthinh.text_book_illustration.config.AppProperties;

class GeminiRestClientTest {

    private MockRestServiceServer mockServer;
    private GeminiRestClient geminiRestClient;
    private AppProperties appProperties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        appProperties = new AppProperties();
        appProperties.setFileStorageRoot("data");
        appProperties.getGemini().setApiKey("test-key");
        appProperties.getGemini().setBaseUrl("https://generativelanguage.googleapis.com/v1beta");
        appProperties.getGemini().setApiRevision("2026-05-20");
        appProperties.getGemini().setTextModel("gemini-3.6-flash");

        geminiRestClient = new GeminiRestClient(restClient, appProperties);
    }

    // ------------------------------------------------------------------ //
    //  Successful Text Generation Calls                                   //
    // ------------------------------------------------------------------ //

    @Test
    void generateStyle_success() {
        String mockResponseBody = """
            {
              "id": "interaction-123",
              "status": "completed",
              "model": "gemini-3.6-flash",
              "steps": [
                { "type": "thought", "signature": "abc" },
                { "type": "model_output", "content": [ { "type": "text", "text": "Vibrant watercolor with warm tones" } ] }
              ]
            }
            """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(header("Api-Revision", "2026-05-20"))
                .andRespond(withSuccess(mockResponseBody, MediaType.APPLICATION_JSON));

        GeminiClient.Result<String> result = geminiRestClient.generateStyle("Once upon a time...", null);

        mockServer.verify();
        assertThat(result.value()).isEqualTo("Vibrant watercolor with warm tones");
        assertThat(result.interactionId()).isEqualTo("interaction-123");
    }

    @Test
    void generateCharacters_structuredOutput_success() {
        String mockResponseBody = """
            {
              "id": "interaction-456",
              "status": "completed",
              "model": "gemini-3.6-flash",
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "{\\"characters\\": [{\\"name\\": \\"Alice\\", \\"imagePrompt\\": \\"Girl in blue dress\\"}, {\\"name\\": \\"Bob\\", \\"imagePrompt\\": \\"Old wizard\\"}]}"
                    }
                  ]
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseBody, MediaType.APPLICATION_JSON));

        GeminiClient.Result<List<GeminiClient.CharacterData>> result =
                geminiRestClient.generateCharacters("interaction-123");

        mockServer.verify();
        assertThat(result.interactionId()).isEqualTo("interaction-456");
        assertThat(result.value()).hasSize(2);
        assertThat(result.value().get(0).name()).isEqualTo("Alice");
        assertThat(result.value().get(0).imagePrompt()).isEqualTo("Girl in blue dress");
    }

    @Test
    void generateChapters_structuredOutput_success() {
        String mockResponseBody = """
            {
              "id": "interaction-789",
              "status": "completed",
              "model": "gemini-3.6-flash",
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "{\\"chapters\\": [{\\"illustrationPrompt\\": \\"A serene village at dawn\\"}]}"
                    }
                  ]
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseBody, MediaType.APPLICATION_JSON));

        GeminiClient.Result<List<GeminiClient.ChapterData>> result =
                geminiRestClient.generateChapters("Watercolor style", "interaction-456");

        mockServer.verify();
        assertThat(result.interactionId()).isEqualTo("interaction-789");
        assertThat(result.value()).hasSize(1);
        assertThat(result.value().get(0).illustrationPrompt()).isEqualTo("A serene village at dawn");
    }

    // ------------------------------------------------------------------ //
    //  Error Scenarios (Quota, Timeout, Malformed Error Body)             //
    // ------------------------------------------------------------------ //

    @Test
    void generateStyle_429QuotaExceeded_surfacesErrorMessage() {
        String errorResponseBody = """
            {
              "error": {
                "code": "RESOURCE_EXHAUSTED",
                "message": "Quota exceeded for quota metric 'Generate Content API requests' and limit 'Requests per minute'",
                "status": "RESOURCE_EXHAUSTED"
              }
            }
            """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body(errorResponseBody)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiRestClient.generateStyle("Book text", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Quota exceeded");
    }

    @Test
    void generateStyle_networkTimeout_wrapsResourceAccessException() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("Connect timed out")));

        assertThatThrownBy(() -> geminiRestClient.generateStyle("Book text", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Gemini API network error")
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    @Test
    void generateStyle_malformedErrorBody_surfacesGenericHttpError() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Server Error HTML Page")
                        .contentType(MediaType.TEXT_HTML));

        assertThatThrownBy(() -> geminiRestClient.generateStyle("Book text", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Gemini API call failed: HTTP 500");
    }

    // ------------------------------------------------------------------ //
    //  Mocked Image Generation                                            //
    // ------------------------------------------------------------------ //

    @Test
    void generatePortrait_createsFileInPortraitsFolderWithEntityId() throws Exception {
        UUID characterId = UUID.randomUUID();

        GeminiClient.Result<String> result = geminiRestClient.generatePortrait(
                characterId, "Alice", "Portrait of Alice", "prev-123");

        assertThat(result.interactionId()).isEqualTo("prev-123");
        assertThat(result.value()).isEqualTo("/data/portraits/" + characterId + ".png");
    }

    @Test
    void generateIllustration_createsFileInIllustrationsFolderWithEntityId() throws Exception {
        UUID chapterId = UUID.randomUUID();

        GeminiClient.Result<String> result = geminiRestClient.generateIllustration(
                chapterId, "Illustration of village", "prev-456");

        assertThat(result.interactionId()).isEqualTo("prev-456");
        assertThat(result.value()).isEqualTo("/data/illustrations/" + chapterId + ".png");
    }
}
