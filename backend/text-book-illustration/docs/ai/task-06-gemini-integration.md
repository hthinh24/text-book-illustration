# Task 06 — Gemini Integration (Real Text Calls + Mocked Image Generation)

## Scope

Implement `GeminiRestClient implements GeminiClient`, replacing `GeminiStubClient` as the production bean (`@Primary` moves to `GeminiRestClient`; `GeminiStubClient` stays in the codebase for tests only — do not delete it).

**Within `GeminiRestClient`, methods split into two real behaviors:**
- `generateStyle`, `generateCharacters`, `generateChapters` — **real REST calls** to the Gemini Interactions API.
- `generatePortrait`, `generateIllustration` — **mocked**: copy a bundled sample image, no network call. This is intentional (see "Why image generation is mocked" below), not a placeholder to fill in later — don't attempt a real call for these two methods.

## Why image generation is mocked

Verified via direct API testing (two separate accounts) that Gemini's image-generation models (`gemini-3.1-flash-image`, aka Nano Banana 2) return zero free-tier quota — a hard limit, not a transient rate limit. Billing would resolve it, but Google Cloud Billing doesn't accept the payment method available to us. Gradion's recruiter (Gam Ho) explicitly approved a clearly-flagged mocked fallback for image generation. This is a documented, deliberate decision — write it up in `DECISIONS.md` (see template at the end of this brief).

Text generation is unaffected — free tier has quota for text models, so those three methods should make real calls.

## Config additions (`AppProperties` + `application.yaml`)

```yaml
app:
  gemini:
    api-key: ${GEMINI_API_KEY}
    base-url: https://generativelanguage.googleapis.com/v1beta
    api-revision: "2026-05-20"
    text-model: gemini-3.6-flash
```

- `api-revision` **must** be sent as the `Api-Revision` header on every request — without it, the API returns a legacy response shape (`outputs[]`) instead of `steps[]`, and parsing will silently break.
- `api-key` sent as header `x-goog-api-key`.
- Only one model needed in config — image steps don't call the API, so no image-model setting required.

## Real call shape (style / characters / chapters)

**Endpoint:** `POST {base-url}/interactions`

**Request body (record `InteractionRequest`):**
```json
{
  "model": "gemini-3.6-flash",
  "input": "<prompt text>",
  "previous_interaction_id": "<nullable>",
  "response_format": { "...": "optional, see structured output below" }
}
```

**Response body (record `InteractionResponse`):**
```json
{
  "id": "...",
  "status": "completed",
  "model": "...",
  "steps": [
    { "type": "thought", "signature": "..." },
    { "type": "model_output", "content": [ { "type": "text", "text": "..." } ] }
  ]
}
```

Parsing: find the step where `type == "model_output"`, take its `content[]`. For `generateStyle`, extract `content[0].text` directly as the style string. For `generateCharacters`/`generateChapters`, the text content should be a JSON string (see structured output below) — parse it into the record types.

**previous_interaction_id:** all three text methods use the same model (`gemini-3.6-flash`), and same-model continuation is confirmed working (tested directly). Forward `previousInteractionId` normally between STYLE → CHARACTER → CHAPTER — no special-case handling needed, no retry-without-it fallback required (that concern only applied to the text→image case, which is now moot since image steps don't call the API at all).

## Structured output (generateCharacters / generateChapters)

Use `response_format` to force valid JSON matching a schema, rather than parsing free text:

```json
"response_format": {
  "type": "text",
  "mime_type": "application/json",
  "schema": {
    "type": "object",
    "properties": {
      "characters": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "name": { "type": "string" },
            "imagePrompt": { "type": "string" }
          },
          "required": ["name", "imagePrompt"]
        }
      }
    },
    "required": ["characters"]
  }
}
```

Same pattern for chapters (`chapters` array, each item `{ "illustrationPrompt": string }`). Wrap the array in a named object property (not a bare top-level array) — this matches the schema shape Google's structured output expects.

**Note:** this is the first real integration test of structured output against this API — if the actual response shape differs from what's documented here (e.g. the JSON lands somewhere other than `content[0].text`), log the raw response body during development to confirm the real shape, then adjust the parsing accordingly. Don't guess silently if the first real call doesn't parse as expected.

## Mocked image methods

```java
@Override
public Result<String> generatePortrait(String characterName, String imagePrompt, String previousInteractionId) {
    String path = copyMockImage("mock-images/portrait-sample.png", characterName);
    return new Result<>(path, previousInteractionId); // pass through unchanged, no real interaction created
}

@Override
public Result<String> generateIllustration(String illustrationPrompt, String previousInteractionId) {
    String path = copyMockImage("mock-images/illustration-sample.png", "chapter");
    return new Result<>(path, previousInteractionId);
}
```

- Bundle 2-3 sample portrait images and 1-2 sample illustration images under `src/main/resources/mock-images/`.
- `copyMockImage(...)` copies the bundled sample to the same storage location/naming convention already used for real generated images (reuse whatever file-writing pattern exists from Task 04/05 — check how `bookTextPath` is written and follow the same approach for consistency).
- No `isMocked` flag on the Character/Chapter entities — since 100% of images in this submission are mocked (not a per-item mix), a single global notice is enough. Don't add a DB column for this.
- Add a code comment at the top of both methods: `// TODO: swap to a real API call once billing is resolved — see DECISIONS.md`.

## Frontend note (not part of this backend task, but flag it)

The FE should show a persistent, clearly visible notice that image generation is simulated (e.g. a banner: "Image generation is simulated due to a billing limitation — see DECISIONS.md"). Not implementing this here, just flagging it so it doesn't get missed — mention it back to the user rather than silently skipping.

## Error handling

Wrap the real REST calls (style/characters/chapters) in try/catch:
- Non-2xx response → throw `RuntimeException` with the message from the API's `error.message` field (don't let it be null — if the error body is unparseable, use a generic but non-null message like `"Gemini API call failed: HTTP " + statusCode`).
- Network/timeout errors → wrap in `RuntimeException` with a clear message too.
- No special handling needed beyond this — `PipelineService`'s existing generic `catch (Exception e)` at each step already persists `e.getMessage()` into `error_message`.

**Dedicated error-path tests (required, not optional):** since the mocked image methods mean the real API's failure modes (quota-exceeded, timeout, malformed response) never actually get exercised in this submission, write unit tests for `GeminiRestClient` that simulate those exact error shapes against a mocked HTTP layer:
- A 429 response body matching the real quota-exceeded shape already seen in testing (`{"error": {"message": "...quota exceeded...", "code": "too_many_requests"}}`) — assert it surfaces as a `RuntimeException` with a non-null, meaningful message.
- A simulated network timeout — same assertion.
- An unparseable/malformed error body — assert the fallback generic message path (not a `NullPointerException` or silent swallow).

This is the test-side mitigation referenced in the DECISIONS.md entry for this change — don't skip it even though it feels redundant with the mocked image methods.

## Note on DECISIONS.md

The billing/mock decision is documented separately (already written up outside this brief) — no need to draft that entry as part of this task.

## Out of scope

- FE banner implementation (flagged above, not built here)
- Swapping mock images back to real calls (leave the TODO comments)
- Any change to `PipelineService`'s claim/finalize/retry logic — that's done, don't touch it
