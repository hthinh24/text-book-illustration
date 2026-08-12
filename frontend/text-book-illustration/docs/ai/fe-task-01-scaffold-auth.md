# FE Task 1 — Scaffold + Auth Screen

Project: Book Illustration Studio (Gradion take-home assessment)
Location: `frontend/text-book-illustration/` (sibling folder to `backend/text-book-illustration/` in the same repo)

## Context

We're building the frontend for a book-to-illustration pipeline app. The backend is
already largely built (Spring Boot, Postgres) and exposes the REST API described in
`reference/api_contract.md`. This task is the **first slice**: project scaffold + the
Identity screen only. Project list / new-project / project-detail are **stubs** in this
task — real logic for those comes in later tasks.

Reference files (read all of them before writing code):
- `reference/api_contract.md` — the authoritative API shape. Follow it exactly, including
  the confirmed real paths below (the contract's first draft had one path typo, already
  corrected):
  - `POST /api/v1/identity`
  - `POST /api/v1/projects/init-project`
  - `GET /api/v1/projects`
  - `GET /api/v1/projects/{projectId}`
  - `GET /api/v1/projects/{projectId}/files/book-text`
  - `POST /api/v1/projects/{id}/style|character|portraits|chapters|illustrations`
  - `POST /api/v1/projects/{id}/retry`
- `reference/app-demo.html` — a static mock shipped by the recruiter. Use it as the
  **visual/token reference and scope reference**, per the assessment spec: *"app-demo.html
  is the floor, not the ceiling... match or beat it visually, you do not have to copy its
  layout."*
- `reference/screenshots/` — 4 screenshots of the demo running with data, showing layout
  in context (auth form, project list, style step, completed project detail).

## Ground rules (read this twice)

1. **Copy design tokens exactly.** `app-demo.html`'s `<style>` block has a `:root { ... }`
   token set (colors, spacing, radius, shadow, font sizes, easing). Copy those values
   verbatim into `src/styles/tokens.css`. Do not invent new color/spacing values — every
   visual choice should trace back to a token.
2. **Do NOT port these three things from the demo** (the assessment spec calls this out
   explicitly — the demo is a mock and gets these wrong on purpose):
   - Its fake timing (`~1.6-2.5s` setTimeout delays, `8000ms` stale-step threshold). The
     real backend decides staleness itself (`retryReason: 'FAILED' | 'STUCK_TIMEOUT'` on
     the retry endpoint) — no client-side timers or `Date.now()` staleness checks, ever.
   - Its single-tab duplicate-click guard (a local JS variable). The real backend already
     handles duplicate-call prevention atomically. The frontend's only job is to disable
     the action button when the server-reported `stepStatus === 'RUNNING'`.
   - Its "fake database in localStorage" (the whole `db.users[email].projects` store).
     The real app must always fetch projects/steps/characters/chapters from the real API.
     **The only thing this app persists client-side is the logged-in identity**
     (`{userId, name, email}`), for session continuity across a refresh.
3. Add a Google Fonts `<link>` for "Noto Sans" to `index.html` — the demo's `@font-face`
   was commented out for their static mock, so without this the app will fall back to the
   system font and look off-brand.
4. Plain JavaScript (no TypeScript), Vite, npm.

## 1. Setup

```bash
npm create vite@latest . -- --template react
npm i react-router-dom
```

In `vite.config.js`, add a dev proxy so the frontend can call the backend without CORS
being configured yet:

```js
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': 'http://localhost:8080' }
  }
})
```

## 2. Folder structure

```
src/
  api/
    client.js       — shared fetch wrapper + postIdentity()
  context/
    AuthContext.jsx — user state + localStorage persistence
  pages/
    AuthPage.jsx
    ProjectListPage.jsx     (stub — render a placeholder heading only)
    NewProjectPage.jsx      (stub)
    ProjectDetailPage.jsx   (stub)
  components/
    Button.jsx
    Field.jsx
    Nav.jsx
    ProtectedRoute.jsx
  styles/
    tokens.css      — copied verbatim from app-demo.html's :root block
    global.css      — resets, body font, shared classes (.gd-btn, .gd-field, etc. —
                       reuse the demo's class names/approach where sensible)
```

## 3. `api/client.js`

A single `request(path, options)` helper:
- Prefixes `path` with `/api/v1`
- Sets `Content-Type: application/json` unless the caller passes `FormData`
- On non-2xx response, parses the body as `ApiErrorResponse` and throws an `Error` whose
  `.message` is the API's `message` field (fall back to statusText if parsing fails)
- On success, returns the parsed JSON body

Export `postIdentity({ email, name })` → `POST /identity`, returns
`{ userId, name }` (per contract — note this does **not** include `email`; the caller
must keep the email itself).

## 4. `AuthContext`

- State: `user` — `null` or `{ userId, name, email }`
- On mount, hydrate from `localStorage.getItem('gd_user')` (JSON), if present
- `login(name, email)`:
  1. Call `postIdentity({ email, name })`
  2. Build `{ userId: response.userId, name: response.name, email }` (email comes from
     the input, not the response)
  3. Save to state and to `localStorage`
  4. Let the caller (AuthPage) navigate on success
- `logout()`: clear state and `localStorage.removeItem('gd_user')`
- Provide via a `AuthProvider` wrapping the app, and a `useAuth()` hook

## 5. `AuthPage`

Layout inspired by the demo's auth card (see screenshot 1 and the `.auth-card` styles in
`app-demo.html`) — feel free to improve on it, per the "floor not ceiling" rule.

- Fields: Full name, Email — both required
- Client-side validation before calling the API: non-blank name, email contains `@`.
  Show inline field errors, don't call the API if invalid.
- On submit: call `useAuth().login(name, email)`
  - **Loading state**: disable the submit button, change its label (e.g. "Signing in…")
    while the request is in flight
  - **Error state**: if `login` throws, show the thrown error's `.message` in a visible
    inline error area (the demo has no error state here since it never fails — this one
    does, add it)
  - **Success**: navigate to `/projects`

## 6. Routing (`App.jsx`)

- `/` → `AuthPage`. If `useAuth().user` is already set, redirect straight to `/projects`.
- `/projects`, `/projects/new`, `/projects/:id` → stub pages, each wrapped in
  `ProtectedRoute` (redirects to `/` if `user` is null)
- `ProtectedRoute`: reads `useAuth().user`; render children if present, else
  `<Navigate to="/" replace />`

## 7. Tests

Use whatever test runner Vite scaffolds with (Vitest + React Testing Library is fine).
Cover only `AuthPage` in this task — a few cases that matter, not exhaustive coverage:

1. Renders empty → clicking submit with blank fields shows validation errors and does
   **not** call the API (assert the mocked `postIdentity`/`fetch` was never called).
2. Valid input, mocked API rejects with a 400-style error → the error message renders in
   the error area.
3. Valid input, mocked API resolves → `login` is called and navigation to `/projects`
   happens (or the router state changes accordingly).

Mock at the `api/client.js` boundary (mock the module, not `fetch` directly, unless your
test setup makes that awkward) — no real network calls in tests.

## 8. Acceptance checklist (for manual review before merging)

- [ ] Load `/` while logged out → auth form renders with correct brand tokens (orange,
      spacing, font) — compare visually against the screenshots
- [ ] Submit with blank fields → inline validation errors, no network request fires
      (check DevTools Network tab)
- [ ] Submit a brand-new email → creates the user via the real backend, navigates to
      `/projects` (stub page renders)
- [ ] Refresh the page → still logged in (identity persisted via localStorage)
- [ ] Submit the *same* email again from a fresh session → backend returns the same
      `userId` as before (idempotent lookup — confirms resume-by-email works)
- [ ] Click "Sign out" → returns to `/`, `localStorage` cleared
- [ ] Manually navigate to `/projects/anything` while logged out → redirected to `/`

## Out of scope for this task

Real project list, new-project form, project detail, pipeline steps, image rendering,
polling. Those are later tasks — leave the corresponding pages as simple stub components.
