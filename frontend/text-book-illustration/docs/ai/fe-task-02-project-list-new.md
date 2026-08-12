# FE Task 2 Implementation Plan — Project List + New Project Screen

Project: Book Illustration Studio (Gradion take-home assessment)
Location: `frontend/text-book-illustration/` — builds on Task 1 (scaffold + auth,
already merged: `AuthContext`, `tokens.css`, `api/client.js`, routing, `AppLayout`/`Nav`).

## Context

Task 1 gave us a working auth flow and route shell with 3 stub pages. This task makes
**Project List** and **New Project** real. `ProjectDetailPage` stays a stub — full pipeline
UI is Task 3.

Reference files (read before writing code):
- `reference/api_contract.md` — authoritative API shape
- `reference/app-demo.html` — visual/scope reference (see `renderList` and `renderNew`
  functions for the target interaction pattern — not the literal code, see ground rules)
- `reference/screenshots/` — project list + new-project-form screenshots

## Ground rules (same as Task 1 — repeating because they matter)

1. Copy visual values (spacing, color, radius) from the existing `tokens.css` — it's
   already in the repo from Task 1, don't recreate it, just reuse the classes/tokens.
2. **Do NOT port from the demo**: its fake setTimeout delays, its single-tab duplicate
   guard, its full localStorage-backed fake database. Projects always come from the real
   API — nothing about project data is cached or faked client-side.
3. No Gradion logo/wordmark — this app is "Book Illustration Studio" (already handled in
   `Nav`, nothing new needed here).
4. Plain JavaScript, no TypeScript.

## 1. `api/client.js` additions

Add two functions alongside the existing `postIdentity`:

- `getProjects(userId)` → `GET /api/v1/projects?userId={userId}` → returns
  `ProjectSummaryResponse[]`
- `initProject({ userId, title, text, file })` → `POST /api/v1/projects/init-project`
  as `multipart/form-data` via `FormData`. Append `userId`, `title`, and **exactly one**
  of `text` or `file` (the caller guarantees this — see NewProjectPage below). Do not set
  `Content-Type` manually on this call — let the browser set the multipart boundary.
  Returns `ProjectDetailResponse` on success.

Both go through the same error-handling path as `postIdentity` (throw an `Error` whose
`.message` comes from the parsed `ApiErrorResponse`).

## 2. `utils/pipelineSteps.js` (new, shared)

Both this task and Task 3 need to know the 5-step order and how to compute progress from
a project's `step` + `stepStatus`. Create one small shared module now so Task 3 doesn't
duplicate it:

```js
export const STEPS = ['STYLE', 'CHARACTER', 'PORTRAIT', 'CHAPTER', 'ILLUSTRATION'];

// Number of steps fully completed (0-5). The current `step` counts as completed
// only once its stepStatus is SUCCESS; PENDING/RUNNING/FAIL means everything
// before it is done but it itself isn't yet.
export function completedStepCount(step, stepStatus) {
  const idx = STEPS.indexOf(step);
  return idx + (stepStatus === 'SUCCESS' ? 1 : 0);
}
```

## 3. `ProjectListPage.jsx`

- On mount, call `getProjects(user.userId)` (get `user` from `useAuth()`)
- **Loading state**: show a simple loading indicator while the request is in flight
- **Error state**: if the request fails, show the error message with a "Retry" button
  that re-runs the fetch
- **Empty state**: if the array is empty, show "No projects yet" + a "+ New project"
  button (per spec §4.4) — same button also appears in the normal header when the list
  isn't empty
- **Project row**, per project:
  - Title, formatted created date (`new Date(p.createdAt).toLocaleDateString()`)
  - Status pill: `DRAFT` → "Draft" (muted/gray), `IN_PROGRESS` → "In progress" (orange,
    with the small pulsing dot style from the demo if you want to match it), `DONE` →
    "Done" (dark/ink) — reuse `.gd-pill` styles from tokens if you ported the demo's
    component classes, otherwise style consistently with the token set
  - Progress indicator: 5 small segments, filled count = `completedStepCount(p.step,
    p.stepStatus)` from the shared util
  - Clicking the row navigates to `/projects/${p.id}`
- Keyboard-usable: rows should be focusable and activatable with Enter (per spec's
  "keyboard-usable" requirement)

## 4. `NewProjectPage.jsx`

- Fields: **Project title** (required), and book text via **either** a `.txt` file
  upload **or** a pasted-text textarea — never both, per the contract's validation rule.
- UX for the either/or: when the user picks a file, clear/disable the textarea; when
  they type into the textarea, clear/disable the file picker. Track which input mode is
  "active" in state so only one can hold a value at submit time. (The demo's dropzone
  auto-fills the textarea with the file's content — don't copy that merge behavior; here
  file and text are genuinely two separate submission paths, since the multipart request
  sends either a `file` field or a `text` field, not both.)
- Client-side validation before calling the API: title non-blank, and exactly one of
  file/text present. Show inline errors, don't call the API if invalid.
- On submit: call `initProject({ userId, title, text, file })`
  - **Loading state**: disable the button, "Creating…" label
  - **Error state**: inline error banner on failure (e.g. wrong userId, both/neither
    provided — though client validation should prevent the latter)
  - **Success**: navigate to `/projects/${response.projectId}` (the stub detail page
    will just render for now — real detail view is Task 3)

## 5. Routing

No changes needed beyond what Task 1 already wired — `ProjectListPage` and
`NewProjectPage` already have routes; this task fills in their real implementation.

## 6. Tests

A couple of meaningful cases per page (per spec §5.4 — not exhaustive):

**`ProjectListPage.test.jsx`**
1. Mocked `getProjects` resolves with an empty array → empty state renders, no rows
2. Mocked `getProjects` resolves with sample projects → correct pill label and progress
   segment count render for a `DRAFT` and a `DONE` example (covers `completedStepCount`
   indirectly)
3. Mocked `getProjects` rejects → error state renders with a retry affordance

**`NewProjectPage.test.jsx`**
1. Submit with blank title and no text/file → validation errors, `initProject` not called
2. Fill title + paste text, then select a file → text input clears/disables (mutual
   exclusivity), or vice versa — pick whichever direction is easier to assert
3. Fill title + text only, mocked `initProject` resolves → navigates to
   `/projects/{returned id}`

Mock at the `api/client.js` boundary, same pattern as Task 1's `AuthPage.test.jsx`.

## 7. Acceptance checklist (manual)

- [ ] Log in, land on `/projects` → real projects from the backend render (create a
      couple via Postman/curl first if the list is empty, to verify non-empty rendering)
- [ ] Empty account → empty state with "+ New project" CTA
- [ ] Click "+ New project" → form renders
- [ ] Submit blank → inline validation errors, no network call
- [ ] Paste text, then click the file picker area → textarea clears (mutual exclusivity
      visible)
- [ ] Submit valid title + pasted text → creates project via real backend, navigates to
      `/projects/{id}` (stub page, just needs to not crash)
- [ ] Go back to `/projects` → the new project now appears in the list with `Draft`
      status and 0/5 progress
- [ ] Kill the backend, reload `/projects` → error state renders with a way to retry,
      not a blank page or unhandled crash

## Out of scope for this task

Project detail page content (stepper, style/character/chapter cards, pipeline actions,
polling, retry) — Task 3.
