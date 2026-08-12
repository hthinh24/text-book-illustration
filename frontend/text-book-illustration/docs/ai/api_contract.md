# API Contract & TypeScript Specifications for Text-Book-Illustration Frontend

This document serves as the authoritative API contract for the React frontend agent and developers.

- **Base URL**: `/api/v1` (e.g. `http://localhost:8080/api/v1`)
- **Data Exchange**: JSON (`application/json`) except `POST /api/v1/init-project` (`multipart/form-data`) and `GET /api/v1/files/{projectId}/book-text` (`text/plain`).
- **Auth**: No auth header/JWT required. User session is tracked via `userId` (UUID).

---

## 1. TypeScript Enums & Data Models

```typescript
// Enums
export type ProjectStatus = 'DRAFT' | 'IN_PROGRESS' | 'DONE';

export type Step = 'STYLE' | 'CHARACTER' | 'PORTRAIT' | 'CHAPTER' | 'ILLUSTRATION';

export type StepStatus = 'PENDING' | 'RUNNING' | 'FAIL' | 'SUCCESS';

export type ItemStatus = 'PENDING' | 'RUNNING' | 'TEXT_GENERATED' | 'DONE' | 'FAIL';

export type RetryReason = 'FAILED' | 'STUCK_TIMEOUT';

// Identity DTOs
export interface IdentityRequest {
  email: string; // Valid email, non-blank
  name: string;  // Non-blank
}

export interface IdentityResponse {
  userId: string; // UUID
  name: string;
}

// Project & Pipeline DTOs
export interface StyleRequest {
  style?: string | null;
}

export interface CharacterResponse {
  id: string; // UUID
  name: string | null;
  imagePrompt: string | null;
  portraitImagePath: string | null;
  status: ItemStatus;
}

export interface ChapterResponse {
  id: string; // UUID
  illustrationPrompt: string | null;
  illustrationImagePath: string | null;
  status: ItemStatus;
}

export interface ProjectSummaryResponse {
  id: string; // UUID
  title: string;
  status: ProjectStatus;
  step: Step;
  stepStatus: StepStatus;
  createdAt: string; // ISO-8601 UTC timestamp string
}

export interface ProjectDetailResponse {
  projectId: string; // UUID
  title: string;
  createdAt: string; // ISO-8601 UTC timestamp string
  status: ProjectStatus;
  step: Step;
  stepStatus: StepStatus;
  errorMessage: string | null;
  style: string | null;
  characters: CharacterResponse[];
  chapters: ChapterResponse[];
}

export interface RetryResponse {
  project: ProjectDetailResponse;
  retryReason: RetryReason | string;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
```

---

## 2. API Endpoints

### 2.1 Identity API

#### `POST /api/v1/identity`
Lookup or create a user by email. Idempotent: returns existing user if email exists (ignores `name` in request).

- **Content-Type**: `application/json`
- **Request Body**: `IdentityRequest`
  ```json
  {
    "email": "user@example.com",
    "name": "John Doe"
  }
  ```
- **Response**: `200 OK` -> `IdentityResponse`
  ```json
  {
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "John Doe"
  }
  ```
- **Errors**: `400 Bad Request` if `email` is invalid/blank or `name` is blank.

---

### 2.2 Project Management API

#### `POST /api/v1/projects/init-project`
Initialize a new book illustration project by pasting raw text OR uploading a `.txt` file.

- **Content-Type**: `multipart/form-data`
- **Form Data Fields**:
  - `userId` (string, UUID, required): User ID.
  - `title` (string, required): Project title.
  - `text` (string, optional): Raw book text content.
  - `file` (File, `.txt`, optional): File upload object.
  > **Validation Rule**: Exactly one of `text` or `file` must be provided. Providing both or neither returns `400 Bad Request`.
- **Response**: `200 OK` -> `ProjectDetailResponse`
- **Errors**:
  - `400 Bad Request`: Both `text` and `file` provided, or neither provided.
  - `404 Not Found`: `userId` does not exist.

#### `GET /api/v1/projects`
List all projects belonging to a specific user.

- **Query Parameters**:
  - `userId` (UUID, required): e.g. `?userId=3fa85f64-5717-4562-b3fc-2c963f66afa6`
- **Response**: `200 OK` -> `ProjectSummaryResponse[]`
  ```json
  [
    {
      "id": "8c024f08-6a69-4dfc-91b9-1aa36286a003",
      "title": "The Little Prince",
      "status": "DRAFT",
      "step": "STYLE",
      "stepStatus": "PENDING",
      "createdAt": "2026-08-12T14:20:00Z"
    }
  ]
  ```

#### `GET /api/v1/projects/{projectId}`
Get full detail envelope of a project (includes state machine status, style, characters, and chapters).

- **Path Parameter**: `projectId` (UUID)
- **Response**: `200 OK` -> `ProjectDetailResponse`
  ```json
  {
    "projectId": "8c024f08-6a69-4dfc-91b9-1aa36286a003",
    "title": "The Little Prince",
    "createdAt": "2026-08-12T14:20:00Z",
    "status": "IN_PROGRESS",
    "step": "CHARACTER",
    "stepStatus": "SUCCESS",
    "errorMessage": null,
    "style": "watercolor painting style with soft pastel colors",
    "characters": [
      {
        "id": "11111111-2222-3333-4444-555555555555",
        "name": "The Little Prince",
        "imagePrompt": "A young boy with golden hair...",
        "portraitImagePath": null,
        "status": "DONE"
      }
    ],
    "chapters": []
  }
  ```
- **Errors**: `404 Not Found` if project does not exist.

#### `GET /api/v1/projects/{projectId}/files/book-text`
Fetch raw book text content for preview/display.

- **Path Parameter**: `projectId` (UUID)
- **Response**: `200 OK` -> Content-Type: `text/plain;charset=UTF-8` (raw string text)

---

### 2.3 Pipeline State Machine Execution API

All pipeline step trigger endpoints return `202 Accepted` when the step is successfully claimed and transitioned to `stepStatus = RUNNING`. If the step was already completed (`stepStatus = SUCCESS`), re-triggering is idempotent and returns `200 OK`.

#### `POST /api/v1/projects/{id}/style`
Trigger Step 1: Style extraction / confirmation.
- **Request Body** (JSON, optional): `StyleRequest` e.g. `{ "style": "anime studio ghibli style" }`
- **Response**: `202 Accepted` (if claimed) or `200 OK` (if already SUCCESS) -> `ProjectDetailResponse`
- **Errors**: `409 Conflict` if project is not in valid step state.

#### `POST /api/v1/projects/{id}/character`
Trigger Step 2: Character extraction from book text.
- **Request Body**: None
- **Response**: `202 Accepted` | `200 OK` -> `ProjectDetailResponse`

#### `POST /api/v1/projects/{id}/portraits`
Trigger Step 3: Character portrait image generation.
- **Request Body**: None
- **Response**: `202 Accepted` | `200 OK` -> `ProjectDetailResponse`

#### `POST /api/v1/projects/{id}/chapters`
Trigger Step 4: Chapter segmentation & scene illustration prompt generation.
- **Request Body**: None
- **Response**: `202 Accepted` | `200 OK` -> `ProjectDetailResponse`

#### `POST /api/v1/projects/{id}/illustrations`
Trigger Step 5: Chapter illustration image generation.
- **Request Body**: None
- **Response**: `202 Accepted` | `200 OK` -> `ProjectDetailResponse`

#### `POST /api/v1/projects/{id}/retry`
Reset a failed or stuck step to `PENDING` so it can be re-triggered.
- **Request Body**: None
- **Response**: `200 OK` -> `RetryResponse`
  ```json
  {
    "project": { ... },
    "retryReason": "FAILED"
  }
  ```
- **Errors**: `409 Conflict` if the project is currently `RUNNING` or `stepStatus` is not in a retryable failure state.

---

## 3. Frontend Integration Guidance for React Agent

1. **Async Step Execution & Polling Strategy**:
   - When calling any `POST /api/v1/projects/{id}/{step}` endpoint, check response HTTP status code:
     - `202 Accepted`: Backend background worker is currently executing the Gemini API calls (`stepStatus === 'RUNNING'`).
     - Frontend should immediately start polling `GET /api/v1/{projectId}` every **2 to 3 seconds**.
     - Stop polling when `stepStatus` becomes `'SUCCESS'` or `'FAIL'`.
2. **Multipart Form Upload**:
   - For `POST /api/v1/init-project`, use `FormData`:
     ```typescript
     const formData = new FormData();
     formData.append('userId', userId);
     formData.append('title', title);
     if (file) formData.append('file', file);
     else formData.append('text', text);
     ```
3. **Error Handling**:
   - Handle `409 Conflict`: Indicates user tried to trigger a step out of order or while another step is running. Show user error message or prompt to retry.
