# Walkthrough — FE Task 1: Scaffold + Auth Screen

Completed the project scaffolding, design token implementation, authentication/identity flow, state management, routing protection, and automated testing for **Book Illustration Studio**.

## Changes Summary

### 1. Configuration & Dependencies
- Added `react-router-dom`, `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, and `jsdom`.
- Added `"test": "vitest run"` script to [package.json](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/package.json).
- Configured dev server proxy (`/api` $\rightarrow$ `http://localhost:8080`) and Vitest test environment in [vite.config.js](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/vite.config.js).
- Added Google Font **Noto Sans** to [index.html](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/index.html).

### 2. Styling System
- Defined exact design tokens from `app-demo.html` in [tokens.css](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/styles/tokens.css).
- Created global resets, typography, and reusable CSS classes (`.gd-btn`, `.gd-field`, `.gd-card`, `.auth-card`, error banners) in [global.css](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/styles/global.css) imported by [index.css](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/index.css).

### 3. API Layer & Context State
- Built [client.js](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/api/client.js) with standard `request()` wrapper and `postIdentity({ email, name })`.
- Created [AuthContext.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/context/AuthContext.jsx) with **synchronous state initialization** from `localStorage` (`gd_user`), preventing F5 refresh flash/redirect glitches.

### 4. Layout & UI Components
- [Nav.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/Nav.jsx): Rendered **only** on logged-in routes with **Book Illustration Studio** title, user avatar initial badge, user name, and Sign out button.
- [AppLayout.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/AppLayout.jsx) & [ProtectedRoute.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/ProtectedRoute.jsx): Protected route wrapper enforcing authentication and supplying the top `Nav` layout to authenticated routes.
- Form inputs [Field.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/Field.jsx) & [Button.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/Button.jsx).

### 5. Auth Screen & Stubs
- [AuthPage.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/pages/AuthPage.jsx): Card layout without `Nav`, client-side field validation, submit loading state ("Signing in..."), inline error banner handling API failures, and navigation to `/projects`.
- Stub pages: [ProjectListPage.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/pages/ProjectListPage.jsx), [NewProjectPage.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/pages/NewProjectPage.jsx), [ProjectDetailPage.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/pages/ProjectDetailPage.jsx).
- Routing configured in [App.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/App.jsx).

---

## Verification Results

### Automated Unit Tests
Executed `npm test` with Vitest:

```bash
 RUN  v4.1.10 D:/collection/applied/Gradion/code/frontend/text-book-illustration

 ✓ src/pages/__tests__/AuthPage.test.jsx (3 tests) 296ms

 Test Files  1 passed (1)
      Tests  3 passed (3)
   Duration  2.99s
```

All 3 unit test cases passed:
1. `shows validation errors on submit with blank fields and does not call API`
2. `renders inline error message when API rejects with an error`
3. `calls login and navigates to /projects when submission is valid`

### Production Build Verification
Executed `npm run build`:
```bash
built in 339ms (dist/assets/index.js & dist/assets/index.css generated cleanly)
```
