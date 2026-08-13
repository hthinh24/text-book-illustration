package vn.hungthinh.text_book_illustration.service;

import java.util.List;
import java.util.UUID;

/**
 * Plug point for Gemini AI calls.
 * Task 05 uses GeminiStubClient. Task 06 replaces it with GeminiRestClient
 * by removing @Primary from the stub and making GeminiRestClient the sole
 * production bean.
 */
public interface GeminiClient {

    /**
     * Generate a style description for the book.
     *
     * @param bookText              raw book content
     * @param previousInteractionId last interaction ID (null on first call)
     * @return generated style string, and the new interaction ID packed together
     */
    Result<String> generateStyle(String bookText, String previousInteractionId);

    /**
     * Extract characters from the book text. The service layer caps the result at 2.
     */
    Result<List<CharacterData>> generateCharacters(String previousInteractionId);

    /**
     * Generate a portrait image path for a single character.
     *
     * @param characterId used for collision-free filename (Character.name is nullable)
     */
    Result<String> generatePortrait(UUID characterId, String characterName, String imagePrompt, String previousInteractionId);

    /**
     * Extract chapters from the book text. The service layer caps the result at 1.
     */
    Result<List<ChapterData>> generateChapters(String style, String previousInteractionId);

    /**
     * Generate an illustration image path for a single chapter.
     *
     * @param chapterId used for collision-free filename
     */
    Result<String> generateIllustration(UUID chapterId, String illustrationPrompt, String previousInteractionId);

    // ------------------------------------------------------------------ //
    //  Value types                                                         //
    // ------------------------------------------------------------------ //

    record CharacterData(String name, String imagePrompt) {}

    record ChapterData(String illustrationPrompt) {}

    /**
     * Wraps a Gemini response value together with the interaction ID returned
     * by the API so the service can persist it as previousInteractionId.
     */
    record Result<T>(T value, String interactionId) {}
}
