# Walkthrough — FE Task 2: Project List + New Project Screen

Successfully implemented the **Project List** and **New Project** screens for **Book Illustration Studio**, connecting to the Spring Boot REST backend endpoints.

## Changes Summary

### 1. API & Utility Layer
- Created [pipelineSteps.js](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/utils/pipelineSteps.js): defines 5 pipeline steps `['STYLE', 'CHARACTER', 'PORTRAIT', 'CHAPTER', 'ILLUSTRATION']` and `completedStepCount(step, stepStatus)` calculation helper (0 to 5 completed segments).
- Extended [client.js](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/api/client.js):
  - `getProjects(userId)`: calls `GET /api/v1/projects?userId={userId}`.
  - `initProject({ userId, title, text, file })`: constructs `FormData` sending `userId`, `title`, and either `text` or `file` via `POST /api/v1/projects/init-project`.

### 2. UI Components & Styling
- [ProjectCard.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/ProjectCard.jsx): Reusable project list item featuring project title, creation date, status pills (`Draft`, `In progress` with animated pulsing dot, `Done`), 5-segment progress bar, and keyboard navigation.
- [global.css](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/styles/global.css): Added project row cards, progress bar segments, tab button switcher, and file dropzone styles.

### 3. Page Implementations
- [ProjectListPage.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/pages/ProjectListPage.jsx): Fetches project summaries on mount, supports loading state, error banner with a "Retry" button, empty list state ("No projects yet"), and populated list rendering.
- [NewProjectPage.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/pages/NewProjectPage.jsx): Title input, tabbed selector (`Paste text` vs `Upload .txt file`) enforcing mutual exclusivity, client-side validation, loading indicator ("Creating…"), error handling, and redirection to `/projects/${response.projectId}`.

---

## Verification Results

### Automated Unit Tests
Executed `npm test` via Vitest:

```bash
 RUN  v4.1.10 D:/collection/applied/Gradion/code/frontend/text-book-illustration

 ✓ src/pages/__tests__/AuthPage.test.jsx (3 tests)
 ✓ src/pages/__tests__/NewProjectPage.test.jsx (3 tests)
 ✓ src/pages/__tests__/ProjectListPage.test.jsx (3 tests)

 Test Files  3 passed (3)
      Tests  9 passed (9)
```

### Production Build
Executed `npm run build`:
```bash
built in 357ms (dist/assets/index.js & dist/assets/index.css generated cleanly)
```
