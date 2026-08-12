# Task 05 — Implementation Walkthrough

## What Was Built

Full async pipeline state machine for the book-to-illustration pipeline — covering 5 step endpoints and 1 retry endpoint on top of the Spring Boot 4.1.0 backend from Task 04.

---

## Files Created

### Config
| File | Purpose |
|---|---|
| [AsyncConfig.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/config/AsyncConfig.java) | `@EnableAsync` only — delegates executor management to Spring Boot's auto-config (`spring.task.execution.*`) |

### Gemini Plug Point
| File | Purpose |
|---|---|
| [GeminiClient.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/GeminiClient.java) | Interface — single swap point for Task 06. Defines 5 methods (one per step) plus inner records `CharacterData`, `ChapterData`, and a generic `Result<T>` wrapper that carries both the response value and the new `interactionId` |
| [GeminiStubClient.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/GeminiStubClient.java) | `@Primary @Service` stub — sleeps 200ms then returns hardcoded canned data. Task 06 removes `@Primary` and provides `GeminiRestClient` |

### DTOs
| File | Purpose |
|---|---|
| [StyleRequest.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/dto/request/StyleRequest.java) | Optional request body for `POST /style`. `hasUserStyle()` helper so service doesn't repeat the null/blank check |
| [RetryResponse.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/dto/response/RetryResponse.java) | Wraps `ProjectDetailResponse` + adds `retryReason: "FAILED" \| "STUCK_TIMEOUT"`. Wrapped (not flattened) to keep the pinned envelope shape intact |

### Service
| File | Purpose |
|---|---|
| [PipelineService.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/PipelineService.java) | Full state machine — one public method per step + retry. Each step: (1) claim via conditional UPDATE, (2) return state, (3) `@Async` background execute, (4) finalize SUCCESS or FAIL |

### Controller
| File | Purpose |
|---|---|
| [PipelineController.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/controller/PipelineController.java) | 6 endpoints under `/api/v1/projects/{id}/...`. Thin controller — all logic in `PipelineService`. `toStepResponse()` helper maps RUNNING → 202, SUCCESS → 200 |

### Test Resources
| File | Purpose |
|---|---|
| [test/resources/application.yaml](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/test/resources/application.yaml) | Provides all required `app.*` property values so `@WebMvcTest` context boots without env vars. Previously no test resource file existed |

---

## Files Modified

| File | Change |
|---|---|
| [AppProperties.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/config/AppProperties.java) | Added `stepTimeoutSeconds` and `maxRetryCount` (bound from `STEP_TIMEOUT_SECONDS` / `MAX_RETRY_COUNT` env vars already in `application.yaml`) |
| [ProjectRepository.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/repository/ProjectRepository.java) | Added 6 `@Modifying @Transactional` JPQL queries: `claimStyleStep`, `claimStep`, `finalizeStepSuccess`, `finalizeStepFail`, `retryFailedStep`, `recoverStuckStep` — all return `int` (affected rows) |
| [CharacterRepository.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/repository/CharacterRepository.java) | Added `findByProjectOrderById` (stable loop ordering), `updateStatus` (PENDING→RUNNING / RUNNING→FAIL), `updatePortraitDone` (DONE + path in one UPDATE) |
| [ChapterRepository.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/repository/ChapterRepository.java) | Same pattern as CharacterRepository — `findByProjectOrderById`, `updateStatus`, `updateIllustrationDone` |

---

## Test Results

```
Tests run: 12, Failures: 0 — PipelineControllerTest  ✅
Tests run:  6, Failures: 0 — PipelineServiceTest     ✅
Tests run:  3, Failures: 0 — IdentityControllerTest  ✅  (pre-existing, still passing)
Tests run:  2, Failures: 0 — ProjectControllerTest   ✅  (pre-existing, still passing)
Tests run:  2, Failures: 0 — ProjectServiceTest      ✅  (pre-existing, still passing)
```

> `TextBookIllustrationApplicationTests` (pre-existing context-loads test) still fails — needs a live PostgreSQL connection via Docker Compose. Not part of this task's scope.

---

## Notable Design Decisions

### Claim / async-execute / finalize — the core pattern

Every step endpoint follows this 3-phase sequence:

1. **Claim** — conditional `UPDATE ... WHERE step=X AND step_status=Y` commits and releases the DB connection *before* any Gemini call starts. Returns affected rows (0 or 1).
2. **Dispatch** — on `rows=1`, `@Async` fires the Gemini call in a background thread. HTTP response (202) is returned immediately.
3. **Finalize** — on Gemini success: `UPDATE ... SET step_status='SUCCESS', previous_interaction_id=...`. On failure: `UPDATE ... SET step_status='FAIL', error_message=...`.

This avoids holding a DB transaction open across a 10–30s external call. `previous_interaction_id` is only touched on the success path — intentional, so a retry after FAIL automatically resumes from the last clean interaction.

### Claim failure — three distinct branches

When `claimStep` returns 0, the service re-queries and branches into exactly one of:

| Current state | HTTP | Reason |
|---|---|---|
| Same step, `SUCCESS` | `200` | Idempotent — already done |
| Same step, `RUNNING` | `409` | In flight — client should poll |
| Anything else | `409` | Wrong order / client-side bug — distinct message |

FE should branch on `stepStatus` in the body, not parse message strings. Both 409 cases return the same status code but different `message` text for debugging.

### STYLE step — user-supplied style

If the request body contains a non-blank `style` value, the Gemini call is skipped entirely. The step still goes through the full claim → RUNNING → finalizeSuccess sequence (synchronously in the request thread, since there is no external call to wait on). This keeps `previousInteractionId` handling identical between both paths.

### PORTRAIT / ILLUSTRATION — abort-early on first item FAIL

The multi-item loop aborts immediately when a single item's Gemini call fails. Remaining unprocessed items stay `status=PENDING` (distinct from RUNNING or FAIL — meaning "not yet attempted"). On retry, the skip-DONE logic handles both fresh items and re-attempted items uniformly: only `status=DONE` items are skipped.

Reasoning: a single shared model means failures are likely systemic (rate limit, quota, model down) — continuing after a failure wastes calls for no new information.

### Retry endpoint — DB is source of truth

`POST /projects/{id}/retry` takes no request body. The service re-queries the current `step` from the DB first, then tries:
1. FAIL path — `retryFailedStep` (enforces `retry_count <= maxRetryCount`)
2. Stuck-RUNNING path — `recoverStuckStep` (enforces `started_at < now() - stepTimeoutSeconds`)

Response includes `retryReason: "FAILED" | "STUCK_TIMEOUT"` so the FE can show different copy.

### CHARACTER / CHAPTER truncation

If the Gemini stub (or later, the real client) returns more rows than the cap (2 characters, 1 chapter), the service silently truncates to the cap via `.stream().limit(N)` before insert. No error is thrown — prompt engineering is the primary enforcement.

### JPQL over native SQL

All `@Modifying` queries use JPQL with fully-qualified enum literals (e.g. `vn.hungthinh...StepStatus.RUNNING`) rather than native SQL strings. This keeps Hibernate as the single translation layer and avoids DB-dialect-specific casting for the Postgres enum types.

### No schema migration needed

All required columns and Postgres enum types were already present in `V1__init_schema.sql` from Task 04. Task 05 adds no new Flyway migration.
