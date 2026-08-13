# Task 06 Walkthrough — Gemini REST Integration & Image Mocking

## Overview

Task 06 replaces the stub Gemini client (`GeminiStubClient`) with a production-ready HTTP REST client (`GeminiRestClient`) targeting Google's Gemini Interactions API for text generation steps, while using a local file-copy fallback for image generation steps due to Google Cloud billing constraints.

---

## Technical Changes

### 1. Gemini REST Client (`GeminiRestClient`)
- **Primary Production Bean:** Annotated `@Primary`, making it the default injected `GeminiClient`.
- **Text Generation Methods (`generateStyle`, `generateCharacters`, `generateChapters`):**
  - Make real REST calls to `POST {base-url}/interactions`.
  - Pass required headers: `x-goog-api-key` and `Api-Revision: 2026-05-20` (ensuring the `steps[]` response shape).
  - Use structured JSON outputs (`response_format` with schema) for `generateCharacters` and `generateChapters`.
  - Pass `previousInteractionId` continuously across sequential steps (STYLE → CHARACTER → CHAPTER).
- **Image Generation Methods (`generatePortrait`, `generateIllustration`):**
  - **Mocked:** Copies sample PNG images from `src/main/resources/mock-images/` to `{fileStorageRoot}/portraits/{characterId}.png` and `{fileStorageRoot}/illustrations/{chapterId}.png`.
  - Uses `characterId` / `chapterId` (UUIDs) as filenames to avoid name collision risks (since character names are nullable).
  - Includes code comment `// TODO: swap to a real API call once billing is resolved — see DECISIONS.md`.
  - If classpath resources are missing, falls back to writing a valid 1×1 transparent PNG placeholder.

### 2. Configuration Updates (`AppProperties` & `application.yaml`)
- Configured under `app.gemini.*` in `AppProperties`:
  - `apiKey`: Loaded via environment variable `GEMINI_API_KEY`.
  - `baseUrl`: Defaults to `https://generativelanguage.googleapis.com/v1beta`.
  - `apiRevision`: Defaults to `2026-05-20`.
  - `textModel`: Configurable via `${APP_GEMINI_TEXT_MODEL}` (defaults to `gemini-3.6-flash`).
- Updated `application.yaml` and `.env.example` accordingly.

### 3. HTTP Layer & Spring Configuration (`GeminiClientConfig`)
- Exposes `RestTemplate` and `RestClient` beans (`geminiRestTemplate` & `geminiHttpClient`).
- Enables `MockRestServiceServer` binding in unit tests without requiring a full Spring context application context.

### 4. Logging & Error Handling
- Adheres to standard logging conventions (`[Service]` tags at method entry, successful exit, and error trace).
- **429 Quota Exceeded:** Parses Google error body `{"error": {"message": "..."}}` and throws `RuntimeException` with the exact API error message.
- **Network / Timeout Errors:** Catches Spring `ResourceAccessException` (wrapping `IOException`) and surfaces a clear message.
- **Malformed / Generic HTTP Errors:** Gracefully falls back to `"Gemini API call failed: HTTP <statusCode>"`.

---

## Verification & Unit Testing

A comprehensive unit test suite was implemented in `GeminiRestClientTest`:

1. **Successful REST Interactions:**
   - `generateStyle_success`: Validates header presence, request URL, and parsing of `model_output` steps.
   - `generateCharacters_structuredOutput_success`: Validates JSON schema parsing into `CharacterData` list.
   - `generateChapters_structuredOutput_success`: Validates JSON schema parsing into `ChapterData` list.

2. **Error Handling & Failure Modes:**
   - `generateStyle_429QuotaExceeded_surfacesErrorMessage`: Verifies HTTP 429 quota body mapping.
   - `generateStyle_networkTimeout_wrapsResourceAccessException`: Verifies simulated connection timeout results in `ResourceAccessException` wrapping.
   - `generateStyle_malformedErrorBody_surfacesGenericHttpError`: Verifies fallback error message on HTTP 500 HTML body.

3. **Mocked Image File Operations:**
   - `generatePortrait_createsFileInPortraitsFolderWithEntityId`: Verifies mock file creation named `{characterId}.png`.
   - `generateIllustration_createsFileInIllustrationsFolderWithEntityId`: Verifies mock file creation named `{chapterId}.png`.

---

## File Summary

| File Path | Description |
| --- | --- |
| [GeminiClient.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/GeminiClient.java) | Added `UUID characterId` / `UUID chapterId` to image method signatures. |
| [GeminiStubClient.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/GeminiStubClient.java) | Removed `@Primary`, updated method signatures for test stubbing. |
| [GeminiRestClient.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/GeminiRestClient.java) | **New.** Primary REST implementation for Gemini API text calls and mocked image copying. |
| [GeminiClientConfig.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/config/GeminiClientConfig.java) | **New.** Spring configuration providing `RestTemplate` and `RestClient` beans. |
| [AppProperties.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/config/AppProperties.java) | Added nested `Gemini` static class under `app.gemini.*`. |
| [PipelineService.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/PipelineService.java) | Updated call sites to pass `character.getId()` and `chapter.getId()`. |
| [GeminiRestClientTest.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/test/java/vn/hungthinh/text_book_illustration/service/GeminiRestClientTest.java) | **New.** Full unit test suite using `MockRestServiceServer`. |
| [application.yaml](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/resources/application.yaml) | Updated `app.gemini` properties. |
| [.env.example](file:///d:/collection/applied/Gradion/code/.env.example) | Updated environment variable key names. |
| [mock-images/](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/resources/mock-images/) | Sample portrait and illustration PNG files. |
