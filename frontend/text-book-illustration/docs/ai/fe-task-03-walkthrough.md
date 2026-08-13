# Walkthrough — FE Task 3: Project Detail (Pipeline Stepper)

Successfully implemented the full **Project Detail** page featuring the 5-step pipeline stepper, action panels, auto-polling mechanism, side panel with raw book text modal, step retry handler, and character/chapter entity card sections.

## Changes Summary

### 1. API Extensions & Utilities
- Updated [pipelineSteps.js](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/utils/pipelineSteps.js) with `getActionableStep(step, stepStatus)` and `isProjectComplete(step, stepStatus)`.
- Extended [client.js](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/api/client.js) with `getProjectDetail`, `getBookText` (reading raw string via `response.text()`), trigger endpoints (`triggerStyle`, `triggerCharacter`, `triggerPortraits`, `triggerChapters`, `triggerIllustrations`), and `retryStep`.
- Built [usePolling.js](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/hooks/usePolling.js) hook for auto-polling `getProjectDetail` every 2.5s while `stepStatus === 'RUNNING'`.

### 2. UI Components & Layout
- [Stepper.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/Stepper.jsx): 5-step horizontal indicator showing checkmark circles for completed steps, orange highlight for active step, and numbered gray circles for upcoming steps.
- [StepPanel.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/StepPanel.jsx): Action panel handling style text input, trigger action buttons, running spinner state, step failure & retry banner, and complete status.
- [BookTextModal.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/BookTextModal.jsx): On-demand modal dialog fetching and displaying raw book text.
- [CharacterCard.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/CharacterCard.jsx) & [ChapterCard.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/ChapterCard.jsx): Cards displaying generated portraits, illustrations, prompts, and status placeholders.

### 3. Page Implementation
- [ProjectDetailPage.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/pages/ProjectDetailPage.jsx): Main page assembling header, stepper, two-column detail grid, side panel, modal dialog, and entity sections.
- [global.css](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/styles/global.css): Added styling for stepper horizontal line & circles, step panel, modal backdrop & dialog, running spinner, and entity card grids.

---

## Verification Results

### Automated Unit Tests
Executed `npm test` via Vitest:

```bash
 RUN  v4.1.10 D:/collection/applied/Gradion/code/frontend/text-book-illustration

 ✓ src/pages/__tests__/ProjectListPage.test.jsx (3 tests)
 ✓ src/pages/__tests__/ProjectDetailPage.test.jsx (4 tests)
 ✓ src/pages/__tests__/NewProjectPage.test.jsx (3 tests)
 ✓ src/pages/__tests__/AuthPage.test.jsx (3 tests)

 Test Files  4 passed (4)
      Tests  13 passed (13)
```

### Production Build
Executed `npm run build`:
```bash
built in 392ms (dist/assets/index.js & dist/assets/index.css generated cleanly)
```
