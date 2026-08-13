# Walkthrough — Task 07: Retry Messaging, Multi-Tab Sync, Generating Spinner & Nav Mock Notice

Successfully implemented the three UI state enhancements from `task-07-ui-state-enchanment.md` along with the mock image notice badge in the header `Nav`.

## Changes Summary

### 1. Fix 1 — Retry Exhausted Message Differentiation
- **Backend ([PipelineService.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/PipelineService.java))**: Updated `retry(projectId)` so when claim queries affect 0 rows, it re-queries project state. If `retryCount >= maxRetryCount` and `stepStatus == FAIL`, it throws a `409 Conflict` exception with message starting with `RETRY_EXHAUSTED: Retry limit reached for this step.`.
- **Frontend ([StepPanel.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/StepPanel.jsx))**: When retry fails due to `RETRY_EXHAUSTED` (or `project.retryCount >= maxRetryCount`), the `Retry` button is hidden and replaced with a static terminal message: `"This step has failed too many times and can't be retried automatically."`

### 2. Fix 2 — Multi-Tab Polling Sync
- **Frontend ([ProjectDetailPage.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/pages/ProjectDetailPage.jsx))**: Configured `usePolling` so polling runs periodically (2.5s when `RUNNING`, 3.5s when `PENDING`/`FAIL`) as long as the project is incomplete (`!isProjectComplete`). Any open tab automatically observes mid-step transitions initiated in another tab without needing a manual browser refresh.

### 3. Fix 3 — Generating Button Loading Indicator
- **Frontend ([StepPanel.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/StepPanel.jsx))**: Added an animated inline spinner inside the button while `stepStatus === 'RUNNING'` or `isActionLoading === true`, keeping the button disabled to prevent duplicate clicks.

### 4. Nav Mock Image Notice Badge
- **Frontend ([Nav.jsx](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/components/Nav.jsx) & [global.css](file:///d:/collection/applied/Gradion/code/frontend/text-book-illustration/src/styles/global.css))**: Added a centered pill badge in the top navigation bar: `ℹ Image generation uses mock assets` styled in soft paper/accent tones (`#FFEEDF`).

---

## Verification Results

### Backend Automated Tests
Ran `./mvnw test`:
```
[INFO] Running vn.hungthinh.text_book_illustration.TextBookIllustrationApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Frontend Automated Tests
Ran `npm test` via Vitest:
```
 RUN  v4.1.10 D:/collection/applied/Gradion/code/frontend/text-book-illustration

 ✓ src/pages/__tests__/ProjectListPage.test.jsx (3 tests)
 ✓ src/pages/__tests__/ProjectDetailPage.test.jsx (5 tests)
 ✓ src/pages/__tests__/NewProjectPage.test.jsx (3 tests)
 ✓ src/pages/__tests__/AuthPage.test.jsx (3 tests)

 Test Files  4 passed (4)
      Tests  14 passed (14)
```
