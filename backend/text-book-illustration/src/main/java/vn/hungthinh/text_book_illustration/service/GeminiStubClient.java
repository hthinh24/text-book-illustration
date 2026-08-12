package vn.hungthinh.text_book_illustration.service;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Stub Gemini client for Task 05 — returns canned data after a short delay.
 * Marked @Primary so it wins over any future real implementation until Task 06
 * swaps it by removing @Primary and providing GeminiRestClient.
 */
@Primary
@Service
public class GeminiStubClient implements GeminiClient {

    private static final int STUB_DELAY_MS = 200;

    @Override
    public Result<String> generateStyle(String bookText, String previousInteractionId) {
        sleep();
        return new Result<>("Vibrant watercolor with warm earthy tones", fakeInteractionId());
    }

    @Override
    public Result<List<CharacterData>> generateCharacters(String bookText, String previousInteractionId) {
        sleep();
        return new Result<>(
                List.of(
                        new CharacterData("Alice", "A young girl with a blue dress and curious eyes"),
                        new CharacterData("Bob", "An old wizard with a long silver beard")),
                fakeInteractionId());
    }

    @Override
    public Result<String> generatePortrait(String characterName, String imagePrompt, String previousInteractionId) {
        sleep();
        // Stub returns a placeholder path — real client would save the base64 image to disk
        return new Result<>("portraits/stub-" + characterName.toLowerCase().replace(' ', '_') + ".png",
                fakeInteractionId());
    }

    @Override
    public Result<List<ChapterData>> generateChapters(String bookText, String style, String previousInteractionId) {
        sleep();
        return new Result<>(
                List.of(new ChapterData("A serene village bathed in golden morning light, as the story begins")),
                fakeInteractionId());
    }

    @Override
    public Result<String> generateIllustration(String illustrationPrompt, String previousInteractionId) {
        sleep();
        return new Result<>("illustrations/stub-chapter.png", fakeInteractionId());
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    private static void sleep() {
        try {
            Thread.sleep(STUB_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String fakeInteractionId() {
        return "stub-" + UUID.randomUUID();
    }
}
