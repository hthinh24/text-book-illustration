# TESTING.md

## What we tested

### Backend
- Step ordering — claim only succeeds when the previous step is SUCCESS or
  the current step is PENDING; wrong-order calls are rejected (409).
- Retry — FAIL path (retry_count-bounded) and stuck-RUNNING recovery
  (timeout-based) both covered.
- Claim-failure branching — already-SUCCESS (200), in-flight RUNNING (409),
  wrong-order (409) each verified to return correct status and message.
- Per-item progress — PORTRAIT/ILLUSTRATION update each Character/Chapter's
  status individually as it completes, not batched.
- Abort-early — a multi-item step stops on the first item FAIL; remaining
  items stay PENDING rather than being attempted.
- GeminiRestClient error handling — quota-exceeded (429), network timeout
  (ResourceAccessException), and malformed error bodies all verified to
  surface as a non-null RuntimeException message, using MockRestServiceServer
  against the exact error shapes seen from the real API during development.
- Mocked image generation — generatePortrait/generateIllustration write to
  the correct folder (portraits/illustrations) with entity-ID-based
  filenames, avoiding collisions since Character.name is nullable.

### Frontend
- AuthPage — validates required email/password input fields before submission.
- NewProjectPage — validates required project title and book text/file inputs before triggering project creation.
- ProjectListPage — handles project list retrieval and displays an error banner with a retry option on failure.
- ProjectDetailPage — verifies style generation form rendering, polling state transitions (PENDING → RUNNING → SUCCESS), error banner displaying retry button, and rendering character/chapter cards in DONE/PENDING states.

## What we deliberately did not test, and why

- E2E — not expected per the brief; covered by unit + integration tests instead.
- Real Gemini image-generation calls — the free tier has zero quota for
  image models (verified directly, not assumed); text generation (style,
  characters, chapters) *was* verified against the real API, but image
  generation is mocked per an approach agreed with Gradion's recruiter —
  see DECISIONS.md.
- Visual/styling regressions — low value for a 3-day take-home.

## Why these choices

We prioritized the pipeline state machine and the Gemini integration's
error paths because they're the highest-risk, highest-value parts of the
assessment — a wrong claim/retry transition or an unhandled API error
silently corrupts pipeline state. UI states were chosen for the components
most likely to confuse a user if they got the state wrong (stuck spinners,
silent failures), not for full coverage.

---

## Test report (real run output)

### Backend — `./mvnw test`

```text
[INFO] Running vn.hungthinh.text_book_illustration.TextBookIllustrationApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running vn.hungthinh.text_book_illustration.controller.IdentityControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running vn.hungthinh.text_book_illustration.controller.PipelineControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running vn.hungthinh.text_book_illustration.controller.ProjectControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running vn.hungthinh.text_book_illustration.service.PipelineServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running vn.hungthinh.text_book_illustration.service.ProjectServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running vn.hungthinh.text_book_illustration.service.GeminiRestClientTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  33.565 s
```

### Frontend — `npm test` (`vitest run`)

```text
 RUN  v4.1.10 D:/collection/applied/Gradion/code/frontend/text-book-illustration

 ✓ src/pages/__tests__/AuthPage.test.jsx (3 tests)
 ✓ src/pages/__tests__/NewProjectPage.test.jsx (3 tests)
 ✓ src/pages/__tests__/ProjectDetailPage.test.jsx (4 tests)
 ✓ src/pages/__tests__/ProjectListPage.test.jsx (3 tests)

 Test Files  4 passed (4)
      Tests  13 passed (13)
   Duration  16.31s
```