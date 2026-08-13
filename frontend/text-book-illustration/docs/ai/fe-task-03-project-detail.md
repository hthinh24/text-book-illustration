# FE Task 3 Implementation Plan — Project Detail (Pipeline Stepper)

Project: Book Illustration Studio (Gradion take-home assessment)
Location: `frontend/text-book-illustration/` — builds on Task 1 (auth/scaffold) and
Task 2 (project list/create, `pipelineSteps.js`, `client.js` base). This is the biggest
remaining slice: the actual pipeline UI.

## Context

`ProjectDetailPage` is currently a stub. This task makes it real: a 5-step stepper,
one action panel per step (style input / trigger button / running spinner / error +
retry), and entity cards for generated characters and chapters, all driven by polling
the real backend.

Reference files:
- `reference/api_contract.md` — authoritative API shape, read the pipeline section again
- `reference/app-demo.html` — visual/scope reference for the stepper, step panel, side
  panel, and entity card layout (see the `renderDetail` / entity card functions for the
  *pattern*, not for logic — see ground rules)
- `reference/screenshots/` — style-step screenshot and completed-project screenshot

## Ground rules (repeating, still applies)

1. Reuse existing tokens/classes from `tokens.css` / `global.css` — don't invent new ones.
2. **Do NOT port**: the demo's fake step delays, its single-tab duplicate guard, or any
   client-side staleness timer (`Date.now()` / `setTimeout`-based "stuck" detection). The
   real backend tells you when a step is stuck via `retryReason: 'STUCK_TIMEOUT'` on the
   retry response — the frontend never computes staleness itself.
3. No Gradion branding.
4. Plain JavaScript.

## 1. `api/client.js` additions

```
getProjectDetail(projectId)        → GET /api/v1/projects/{projectId}
getBookText(projectId)             → GET /api/v1/projects/{projectId}/files/book-text
                                      (returns raw text, NOT json — don't run this
                                      through the JSON-parsing `request()` helper;
                                      write a small separate fetch that does
                                      `response.text()`)
triggerStyle(projectId, style)     → POST /api/v1/projects/{id}/style  body: { style } or {} if blank
triggerCharacter(projectId)        → POST /api/v1/projects/{id}/character   (no body)
triggerPortraits(projectId)        → POST /api/v1/projects/{id}/portraits  (no body)
triggerChapters(projectId)         → POST /api/v1/projects/{id}/chapters   (no body)
triggerIllustrations(projectId)    → POST /api/v1/projects/{id}/illustrations (no body)
retryStep(projectId)               → POST /api/v1/projects/{id}/retry      (no body)
```

All the trigger/retry calls return a `ProjectDetailResponse` (retry wraps it in
`{ project, retryReason }`) directly — no separate re-fetch is needed right after
calling one, the response body already has the fresh state.

## 2. `utils/pipelineSteps.js` — add one helper

We already have `STEPS` and `completedStepCount`. Add a helper for "which step is
actionable right now" — this handles the case where the backend may or may not have
already advanced `project.step` to the next stage immediately after a SUCCESS (this
project's semantics aren't 100% pinned down yet from the frontend's point of view, so
make the UI derive it rather than assume):

```js
// Returns { stepName, stepStatus } for whichever step the UI should currently act on.
// If the current `step` already succeeded and it isn't the last step, the actionable
// step is the next one (rendered as PENDING, ready to trigger) — covers both possible
// backend behaviors (auto-advance-on-success vs. staying pinned at SUCCESS).
export function getActionableStep(step, stepStatus) {
  const idx = STEPS.indexOf(step);
  if (idx === -1) return { stepName: STEPS[0], stepStatus: 'PENDING' };
  if (stepStatus === 'SUCCESS' && idx < STEPS.length - 1) {
    return { stepName: STEPS[idx + 1], stepStatus: 'PENDING' };
  }
  return { stepName: step, stepStatus };
}

export function isProjectComplete(step, stepStatus) {
  return step === STEPS[STEPS.length - 1] && stepStatus === 'SUCCESS';
}
```

**Flag for manual verification, not a blocker**: once real pipeline calls are wired up
end to end, do one full run and confirm whether `project.step` actually advances on its
own after a SUCCESS, or stays pinned. `getActionableStep` is written to give the correct
actionable step either way, so this shouldn't require a code change regardless of which
it turns out to be — just worth knowing for `DECISIONS.md`.

## 3. `hooks/usePolling.js` (new, shared)

```js
import { useEffect, useRef } from 'react';

// Calls `fn` immediately is NOT done here — caller decides when to start.
// While `enabled` is true, calls `fn` every `intervalMs`. Cleans up on unmount
// or when `enabled` flips to false.
export function usePolling(fn, { intervalMs = 2500, enabled }) {
  const fnRef = useRef(fn);
  fnRef.current = fn;

  useEffect(() => {
    if (!enabled) return;
    const id = setInterval(() => fnRef.current(), intervalMs);
    return () => clearInterval(id);
  }, [enabled, intervalMs]);
}
```

Usage pattern in `ProjectDetailPage`: after any trigger call resolves, `setProject(response)`.
If the resulting `stepStatus === 'RUNNING'`, that's what turns polling `enabled` on. The
poll callback calls `getProjectDetail(projectId)` and `setProject(fresh)`; once
`stepStatus` becomes `'SUCCESS'` or `'FAIL'`, `enabled` naturally becomes false and the
interval stops. This also covers **reopening the page mid-step**: on mount, fetch detail
once — if it comes back `RUNNING`, polling starts immediately, no new trigger is fired.

## 4. `ProjectDetailPage.jsx`

Structure (two-column, matching the demo's layout intent — feel free to improve):

- **Header**: title, created date, back link to `/projects`
- **Stepper**: 5 circles using `STEPS`. Circle state per step index vs. the actionable
  index: done (checkmark) if before the actionable step, current (highlighted, pulsing
  if RUNNING) if it IS the actionable step, upcoming (plain number) otherwise
- **Main panel** (`StepPanel`), based on the actionable `{stepName, stepStatus}`:
  - `stepStatus === 'PENDING'` and `stepName === 'STYLE'`: optional text input ("leave
    blank to let Gemini choose") + "Generate Style" button → `triggerStyle`
  - `stepStatus === 'PENDING'`, any other step: a single "Generate {Characters
    /Portraits/Chapters/Illustrations}" button → matching trigger function, no input
  - `stepStatus === 'RUNNING'`: disabled state, spinner, short status text (no countdown,
    no fake progress bar — see ground rule 2)
  - `stepStatus === 'FAIL'`: error banner showing `project.errorMessage`, "Retry" button
    → `retryStep`. On the retry response, read `retryReason` and show a short toast/line
    distinguishing "that failed — retrying" vs "that got stuck — retrying" using the
    reason, then update state to the reset (`PENDING`) step so the normal trigger button
    appears next
  - `isProjectComplete(...)` true: "All 5 steps complete" done state, no action needed
- **Side panel**: shows `project.style` once set; a "Book text" section with a short
  snippet + a "Read full text" action that fetches `getBookText` and shows it in a modal
  (fetch on demand when opened, don't preload)
- **Entity sections** below the main panel, shown once they have content:
  - **Characters** (`project.characters`, once non-empty): grid of cards — name,
    portrait image if `status === 'DONE'` (see image URL note below), a subtle
    placeholder + spinner if `RUNNING`, a plain placeholder if `PENDING` or
    `TEXT_GENERATED` (text extracted, image not generated yet), an error badge if `FAIL`
  - **Chapters** (`project.chapters`, once non-empty): same pattern with
    `illustrationImagePath`

**Image URLs**: `portraitImagePath` / `illustrationImagePath` are relative paths served
as static resources by the backend (confirmed approach — see `reference/decisions.md` if
present, otherwise just: prefix the path with the backend origin the same way `/api` is
proxied). **Important**: the Vite dev proxy currently only forwards `/api/**` — add
whatever path prefix the backend actually serves images under (e.g. `/images/**`) to the
same `server.proxy` block in `vite.config.js`, or these `<img>` tags will 404 in dev even
though the paths are correct. Confirm the exact prefix with the backend before wiring
this up if it isn't already obvious from a real API response.

## 5. Tests

Pick meaningful cases, not exhaustive coverage. Use `vi.useFakeTimers()` for the polling
test so it doesn't actually wait 2.5s in real time.

**`ProjectDetailPage.test.jsx`**
1. Detail loads with `stepStatus: 'PENDING'`, `step: 'STYLE'` → style form + "Generate
   Style" button render
2. Click generate → mocked trigger resolves with `stepStatus: 'RUNNING'` → button
   disables, spinner shows, and (with fake timers advanced) the polling function
   (mocked `getProjectDetail`) gets called; feed it a `SUCCESS` response on the next tick
   and assert the UI updates out of the running state
3. Detail loads with `stepStatus: 'FAIL'` and an `errorMessage` → error banner with that
   message + Retry button render; clicking Retry calls `retryStep`
4. Detail loads with a non-empty `characters` array mixing `DONE` and `PENDING` items →
   both card variants render (image for `DONE`, placeholder for `PENDING`)

## 6. Acceptance checklist (manual)

- [ ] Open a fresh project (from Task 2) → stepper shows step 1 current, style form
      renders
- [ ] Leave style blank, submit → spinner/running state, then (once the backend
      finishes) style text appears in the side panel and the stepper advances
- [ ] Refresh the page while a step is running → still shows the running state (no
      duplicate trigger fired) and eventually resolves via polling
- [ ] Force a failure (e.g. temporarily break `GEMINI_API_KEY` on the backend, or ask
      the backend to inject a failure for this one manual check) → error banner +
      Retry render; clicking Retry resets the step; the normal trigger button reappears
- [ ] Run through to Characters/Chapters generation → cards appear per item, in the
      right partial states (not just once everything is fully done)
- [ ] A fully completed project (`DONE`) shows the "all complete" state with all
      character portraits / chapter illustrations visible

## Out of scope

Real image assets requiring live Gemini calls — mục 6 backend integration may still be
gated on billing (see project notes). It's fine if the manual checklist above is run
mostly against mocked/stubbed backend responses for now; the UI code itself should not
assume or special-case that.
