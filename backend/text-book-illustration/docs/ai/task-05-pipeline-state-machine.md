# Task 05 — Pipeline State Machine

## Scope

Implement the async claim/finalize state machine for the book-to-illustration pipeline, covering 5 endpoints:

- `POST /projects/{id}/style`
- `POST /projects/{id}/character`
- `POST /projects/{id}/portraits`
- `POST /projects/{id}/chapters`
- `POST /projects/{id}/illustrations`

Each triggers one pipeline step. Endpoints return **202 Accepted** immediately after a successful claim; the actual Gemini call + finalize run in a background thread. Do NOT implement the Gemini REST client itself in this task — mock/stub the Gemini call (e.g. a method that sleeps then returns a canned response) so the state machine can be tested end-to-end without live API calls. The real client is a separate task (mục 6).

## Out of scope (do not implement)

- Gemini REST client (stub it)
- Frontend
- Per-step audit history table (already decided: single row on `Project`, not `ProjectStep`)

## Core mechanism — claim / async execute / finalize

Every endpoint follows this exact 3-phase pattern:

### 1. Claim (synchronous, in the HTTP request thread)

Run the claim UPDATE for that step. This must commit and release the DB connection before any Gemini call starts — do not hold a transaction open across the async boundary.

```sql
UPDATE project
SET step = :this_step, step_status = 'RUNNING', started_at = now()
WHERE id = :id AND (
  (step = :prev_step AND step_status = 'SUCCESS')
  OR
  (step = :this_step AND step_status = 'PENDING')
)
```

- STYLE step has no prev_step — claim condition is just `step = 'STYLE' AND step_status = 'PENDING'`.
- Affected rows = 1 → claim succeeded, proceed to phase 2.
- Affected rows = 0 → claim failed, see "Claim failure responses" below.

### 2. Dispatch to background thread

After a successful claim, dispatch the actual work (Gemini call + finalize) to a background executor (`@Async` or explicit thread pool — your call on mechanism, but it must not block the HTTP response). Immediately return `202 Accepted` with the current project state (same envelope shape as `GET /projects/{id}`).

### 3. Finalize (in the background thread, after Gemini call completes)

Success:
```sql
UPDATE project SET step_status = 'SUCCESS', previous_interaction_id = :id
WHERE id = :id AND step = :this_step AND step_status = 'RUNNING'
```

Error:
```sql
UPDATE project SET step_status = 'FAIL', error_message = :msg
WHERE id = :id AND step = :this_step AND step_status = 'RUNNING'
```

Note: `previous_interaction_id` is only touched on the success path. This is intentional — it means a retry after FAIL automatically resumes from the last clean interaction. Do not add extra logic to "clean up" or reset it on failure.

## Claim failure responses (affected_rows = 0)

Do not return a generic error. Re-query the current project state and branch into exactly one of these:

| Condition | HTTP Status | Notes |
|---|---|---|
| Step already `SUCCESS` | `200` | Idempotent no-op — treat as success, return current state |
| Step is `RUNNING` (another request in flight) | `409 Conflict` | Transient — client should poll, not retry the POST |
| Any other mismatch (wrong order, e.g. calling `/portraits` before `/character` succeeded) | `409 Conflict` | Client-side bug — different message than the RUNNING case |

All three cases return the same response shape: current project state (same envelope as success), with the HTTP status code as the signal. FE should branch on `stepStatus` in the body, not on parsing message strings — but the `errorMessage`/message field should still differ between the RUNNING and wrong-order cases for debugging.

## Retry endpoint

Single endpoint (design your own route, e.g. `POST /projects/{id}/retry`) covering both FAIL and stuck-RUNNING:

```sql
UPDATE project
SET step_status = 'PENDING', retry_count = retry_count + 1
WHERE id = :id AND step = :this_step AND (
  (step_status = 'FAIL' AND retry_count <= 3)
  OR (step_status = 'RUNNING' AND started_at < now() - interval '3 minutes')
)
```

Response should indicate which case applied (normal retry vs. stuck-recovery) so FE can show different copy — include a field like `retryReason: "FAILED" | "STUCK_TIMEOUT"` in the response, don't make FE infer it from status codes.

After a successful retry (status back to PENDING), the client is expected to call the original step endpoint again to re-claim and re-run.

## Multi-item steps (PORTRAIT, ILLUSTRATION)

These loop over Character/Chapter rows, one Gemini call per item, sequentially — not parallel (so items "land" one at a time for FE polling).

**Skip already-done items on retry/re-run:** before processing an item, check its own `status` field (not `portraitImagePath`/`illustrationImagePath` nullness — status is the source of truth). Only process items where `status != 'DONE'`. This applies whether the step-level call is a fresh run or a retry — always skip DONE items, never reprocess them.

**Per-item status updates:** update each Character/Chapter row's `status` individually as its own Gemini call completes (PENDING → RUNNING → DONE/FAIL), not batched at the end of the loop. This is what makes FE polling show incremental progress.

**Abort-early on first item FAIL (locked decision):** if an item's Gemini call fails, stop the loop immediately — do not attempt remaining items in that run. Reasoning: this is a single shared Gemini model, so a failure is likely systemic (rate limit, quota, model down) and will very likely fail the next item too; continuing wastes calls for no new information. Items not yet reached by the time of abort stay `status = 'PENDING'` (meaning "not yet attempted" — distinct from `RUNNING` or `FAIL`). Step-level status: if all items end DONE → step_status SUCCESS. If the loop aborted on a FAIL → step_status FAIL, with error_message identifying which item failed. On retry, PENDING items get their first attempt (not a "retry" semantically, just proceeding), while the FAIL item gets reattempted — the skip-DONE logic above handles both uniformly since only `status='DONE'` is skipped.

Revisit this only if the project later moves to multi-model or parallel image generation — continue-and-collect would make more sense there, but not for this single-model sequential setup.

## CHARACTER / CHAPTER list generation

CHARACTER step: Gemini returns a list of characters (name + imagePrompt) which get inserted as Character rows, capped at 2. CHAPTER step similarly capped at 1.

- Primary enforcement: prompt engineering (instruct Gemini to return exactly the cap).
- Safety net: in the service layer, if Gemini returns more than the cap, truncate to the cap (take the first N) before insert. Do not error out — truncate silently and proceed.

## STYLE step special case

- No prev_step to check — claim condition is `step = 'STYLE' AND step_status = 'PENDING'`.
- Project already defaults to `step=STYLE, step_status=PENDING` on `init-project` (confirmed working from Task 04 — no change needed there).
- If the request includes a user-supplied `style` value, skip the Gemini call entirely but still go through the full claim → RUNNING → finalize-SUCCESS sequence synchronously (in the request thread is fine here, no need to dispatch async since there's no external call to wait on) — for consistency of the audit trail (`previous_interaction_id` handling, etc.), don't special-case this into a different code path.

## Caps enforcement

Max 2 Characters, max 1 Chapter — enforce server-side in the service layer before insert (see CHARACTER/CHAPTER section above for the truncate approach).

## Acceptance checklist

- [ ] All 5 step endpoints implement claim → 202 → async execute → finalize
- [ ] Claim failure returns correct status (200/409/409) per the table above, with distinguishable messages for the two 409 cases
- [ ] Retry endpoint handles both FAIL and stuck-RUNNING, returns `retryReason`
- [ ] PORTRAIT and ILLUSTRATION skip items already `status=DONE` on any re-run
- [ ] PORTRAIT and ILLUSTRATION abort the loop immediately on the first item FAIL — remaining unprocessed items stay `status=PENDING`, not touched
- [ ] Per-item status updates are individual, not batched — verify via DB polling during a stubbed multi-item run that rows update one at a time, not all at once at the end
- [ ] CHARACTER/CHAPTER truncate to cap if Gemini stub returns more than the cap
- [ ] STYLE step: both Gemini-generated and user-supplied paths tested, both end in SUCCESS with previous_interaction_id handled consistently
- [ ] previous_interaction_id only changes on finalize-success, confirmed unchanged after a stubbed-FAIL finalize
- [ ] Gemini call is stubbed (not real) — plug point should be a single method/interface swappable later for the real client (mục 6)
