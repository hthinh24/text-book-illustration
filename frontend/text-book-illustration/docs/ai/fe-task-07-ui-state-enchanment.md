# Task 07 — Retry Messaging, Multi-Tab Sync, Generating Spinner

Three independent fixes found during manual testing after mục 6. No dependency between them — implement and test each separately.

---

## Fix 1 — Distinguish "retry exhausted" from other retry failures (priority: high)

**Problem:** `POST /projects/{id}/retry` currently returns the same generic failure response whether the reject reason is "wrong state to retry" or "retry_count already exceeds max". The FE has no way to tell these apart, so it keeps showing an active Retry button even when retrying is permanently impossible — the user clicks forever with no feedback.

**Decision (locked, don't relitigate):** the retry cap itself stays — it protects against burning Gemini quota on a permanently-broken request. We are **not** building a new terminal status or a step-restart recovery flow (out of scope). This fix is message-differentiation only, reusing the claim-failure-branching pattern already built in mục 5 (where claim failures branch into distinct 200/409/409 responses based on current DB state) — same idea, applied to the retry endpoint's failure path.

**What to do:**
1. In the retry service method, when the retry UPDATE affects 0 rows, re-query the project's current `step`, `step_status`, and `retry_count`.
2. Branch into two distinct cases instead of one generic failure:
   - `retry_count >= maxRetryCount` (from `AppProperties`) → this is "exhausted". Return a response with a distinct reason, e.g. `retryReason: "RETRY_EXHAUSTED"` (extending the existing `retryReason` field that already carries `"FAILED" | "STUCK_TIMEOUT"` on success — add this as a third possible value on the failure path) and a clear message like `"Retry limit reached for this step."`
   - Anything else (step not in FAIL/stuck-RUNNING state at all) → keep the existing generic "not eligible to retry" message.
3. FE: when a poll or retry-attempt response comes back with `retryReason: "RETRY_EXHAUSTED"`, hide the Retry button and show a static message instead (e.g. "This step has failed too many times and can't be retried automatically.") — no new button, no recovery action, just an honest terminal message.

**HTTP status:** keep whatever status the generic retry-failure case already uses (likely 409) — this fix only differentiates the message/reason field, not the status code.

---

## Fix 2 — Multi-tab polling doesn't stay in sync (priority: medium)

**Problem:** with two browser tabs open on the same project, the tab that triggered a step (clicked the button) polls and updates correctly. A second tab just viewing the same project does not pick up the state change — it stays frozen on the old state until manually refreshed.

**Root cause (likely):** polling is probably wired to start only in response to the local button-click action (e.g. a polling loop kicked off inside the click handler), rather than being tied to "this project is currently open and in a non-terminal state." A tab that never clicked anything never starts a polling loop.

**Fix direction:** polling should be driven by **the project's current state**, not by "did this tab trigger the action." Any tab with the project detail view open should poll `GET /projects/{id}` on an interval whenever the displayed `stepStatus` is `RUNNING` (regardless of which tab caused it to become RUNNING), and stop polling once it reaches a terminal status (`SUCCESS`, `FAIL`, or all steps done). Concretely: move the polling trigger to a `useEffect` (or equivalent) keyed on the project's current `stepStatus` from state/props, not on the button's click handler — the click handler triggers the initial mutation request, but polling start/stop should be a separate effect reacting to whatever `stepStatus` currently is, so it works the same whether this tab caused the RUNNING state or just observed it.

**Test:** open the same project in two tabs, trigger a step from tab A, confirm tab B's UI updates to the new state without a manual refresh, purely from polling.

---

## Fix 3 — "Generating..." button has no loading indicator (priority: low, cosmetic)

**Problem:** while a step is RUNNING, the button shows static text "Generating..." with no visual motion — looks indistinguishable from a frozen/broken UI.

**Fix:** add a simple spinner (CSS animation or an existing icon library already in use in the project) next to or inside the button while `stepStatus === 'RUNNING'`. Keep it simple — a spinning icon or animated dots is enough, no need for a custom animation system. Disable the button while spinning (if not already disabled) so it can't be double-clicked.

---

## Out of scope for this task

- Any new terminal status (`RETRY_EXHAUSTED` as a `step_status` enum value) — Fix 1 only adds a `retryReason` value, not a new pipeline state.
- A "restart step" or admin-reset recovery flow.
- WebSocket/SSE push instead of polling — polling-based sync fix in #2 is sufficient for this scope.
