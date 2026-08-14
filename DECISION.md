# DECISION.md

## 1. Concurrency guard

**Proposal / Purpose:**
I propose to use row level lock (`SELECT FOR UPDATE`) for concurrency guard.

**Push back:**
Claude push back because Gemini call during process can hold db connection from 10-30s which is not good on high traffic. Instead claude recommend to use condition update for `step_status` (from pending -> running) for prevent concurrency.

**Cost:**
Lost strict isolation, have a gap between commit statue = RUNNING and gemini update status = DONE after generate, need timeout + retry to prevent permenant stuck at RUNNING state.

---

## 2. Split project steps to separate table

**Proposal / Purpose:**
Claude recommend to use separate table for each step to keep error message & retry count per step.

**Push back:**
I push back because pipeline strictly sequential, no need audit trail per step in this stage, create another table will increase logic complexity (lazy init, eager init, logic to determine current step), keep single row in project table in this satuation is more simpler.

**Cost:**
Lost audit per-step, can accepted because we didn`t really need in this time.

---

## 3. Database Schema Migration

**Proposal / Purpose:**
Agent use `hibernate.ddl-auto=update` for fast auto-generating and altering DB schema directly from Java entities.

**Push back:**
I push back and force `ddl-auto=validate` with Flyway because it give us more safety control on schema that can prevent implicit schema drifts, orphan columns.

**Cost:**
Need to maintain migration scripts for every entity change.

---

## 4. Abort-early on first item FAIL

**Proposal / Purpose:**
Claude recommend continue run for remaining items after first item fail because cap character is 2 that can help user reduce their round trip.

**Push back:**
I push back because currently why only use one model per text / image generation, single model per type mean share failure mode, for ex: If the first item fail because rate limit, out of quote, model downing then the next item almost certainly will hit the same error, keep continue didn`t bring any value.

**Cost:**
Worse UX for case that can be success, the user gets a partial result instead of a full one in a single round trip. If item #1 fails (For ex: timeout) but item #2 would have actually succeeded, aborting early mean the user has to retry instead of getting both outcomes at once.

---

## 5. Image generation mocked due to billing blocker

**Proposal / Purpose:**
Nano Banana 2 (`gemini-3.1-flash-image`) has zero free-tier quota — confirmed via both google colab & direct API calls. Billing would fix it, but Google doesn't accept JCB; no supported card was available in time. Gradion approved a clearly-flagged mocked fallback for image generation only — text steps still call the real API.

**Flag:**
All portrait/illustration images in this submission are mocked (sample images bundled in repo), not generated.

**Cost:**
The real failure modes of the image API (quota exceeded, malformed prompt, timeout, invalid `previous_interaction_id`) are never actually exercised against a live response in this submission — so `GeminiRestClient`'s error-handling path for those two methods is unverified by anything except assumption.

**Mitigation:**
Wrote dedicated unit tests against `GeminiRestClient` that simulate the exact real error shapes already seen (429 quota-exceeded body, network timeout) to verify the error-mapping path works correctly, without depending on a live quota wall to prove it.

---

## 6. Retry cap has no recovery path when exhausted

**Proposal / Purpose:**
Claude flagged that once `retry_count` hits max, the endpoint stop accepting retry but UI don`t know that so the retry button just stay and keep failing, recommend to add `RETRY_EXHAUSTED` for project that help FE distinct error.

**Push back:**
I push back because that change logic from data model, backend and frontend, with limiting time left i decided to make simpler solution by just return `RETRY_EXHAUSTED` retryReason and let frontend use it to notificate user to prevent user try to retry infinitely.

**Cost:**
Because we don`t have a proper terminal state or recovery flow for it, user will be stuck at that stage if they hit retry_limit.

---

## 7. What I would do with one more day

**Within scope:**

- **Real Image Model Integration:** Swap the mock images for real Gemini image model calls once billing is sorted out — the code already has a `TODO` marking exactly where to change it, just waiting on a valid Visa/Mastercard card.
- **Proper Terminal State for Retry Exhaustion:** Build a real terminal state for retry-exhausted. Decision #6 already said this gap, so the next step is adding a clear status (`RETRY_EXHAUSTED`) with recovery path (restart the step), instead leaving user with retry button that will never succeed.

**Outside scope (Production readiness):**

- **Production Infrastructure:** Add basic production component — real auth instead of just email lookup, S3 or a CDN instead of local disk storage. None of this matter for a 3-day take-home, but it makes this project like a real product rather than a simple demo.