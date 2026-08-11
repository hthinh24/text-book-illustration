# Task 04 — Identity + Project CRUD

**Package root:** `vn.hungthinh.text_book_illustration`
**Layers:** `controller` / `service` / `repository` / `entity` (existing structure — follow it, don't introduce new layers like `dto` sub-packages unless needed for request/response records)
**Persistence:** Spring Data JPA (entities as `@Entity`, repositories as `JpaRepository<T, UUID>`)

## Scope

This task covers **Identity + Project CRUD only**:
- Create/lookup user by email
- Create project (text paste or `.txt` upload)
- List projects for a user
- Read one project's full detail
- Serve book text back to the frontend

**Do NOT implement**: step claiming, conditional-update/RUNNING transitions, retry logic, or any Gemini calls. Those belong to Task 05 (pipeline state machine) and Task 06 (Gemini integration). New records default to `step = STYLE`, `step_status = PENDING` and are never changed by this task's endpoints.

## Entities

**User**
```
id: UUID (PK)
email: String, unique, not null
name: String, not null
createdAt: Instant, not null
```

**Project**
```
id: UUID (PK)
user: User (ManyToOne, not null)
title: String, not null
bookTextPath: String, not null
style: String, nullable
status: enum DRAFT, IN_PROGRESS, DONE — default DRAFT
step: enum STYLE, CHARACTER, PORTRAIT, CHAPTER, ILLUSTRATION — default STYLE
stepStatus: enum PENDING, RUNNING, FAIL, SUCCESS — default PENDING
retryCount: int, default 0
errorMessage: String, nullable
previousInteractionId: String, nullable
createdAt: Instant, not null
startedAt: Instant, nullable
```

**Character**
```
id: UUID (PK)
project: Project (ManyToOne, not null)
name: String, nullable (populated at CHARACTER step)
imagePrompt: String, nullable
portraitImagePath: String, nullable
status: enum PENDING, RUNNING, DONE, FAIL — default PENDING  (per-item progress, new field)
```

**Chapter**
```
id: UUID (PK)
project: Project (ManyToOne, not null)
illustrationPrompt: String, nullable
illustrationImagePath: String, nullable
status: enum PENDING, RUNNING, DONE, FAIL — default PENDING  (per-item progress, new field)
```

**CharacterChapter** (join entity or `@ManyToMany` — agent's choice, keep it simple)
```
character: Character
chapter: Chapter
```

Do not create a `ProjectCharacter` join table — `Character` links to `Project` directly.

## Endpoints

### `POST /api/v1/identity`
Request: `{ email, name }`
Logic: lookup `User` by email. If found, return it (ignore `name` from request — don't overwrite). If not found, create it.
Response: `{ userId, name }`
Validation: `email` valid format + not blank, `name` not blank. Use `spring-boot-starter-validation` (`@Email`, `@NotBlank`).

### `POST /api/v1/init-project`
Content-Type: `multipart/form-data`
Fields: `userId` (UUID), `title` (String), `text` (String, optional), `file` (`.txt`, optional)
Validation: exactly one of `text` / `file` must be present — both present or both absent is a 400 error.
Logic: write book text to `{FILE_STORAGE_ROOT}/{project_id}/book.txt`, create `Project` row with defaults (`status=DRAFT`, `step=STYLE`, `stepStatus=PENDING`).
Response: same envelope as `GET /{project_id}` below.

### `GET /api/v1/projects?userId={id}`
Response: array of
```json
{ "id": "...", "title": "...", "status": "DRAFT", "step": "STYLE", "stepStatus": "PENDING", "createdAt": "..." }
```

### `GET /api/v1/{project_id}`
Response envelope (pin this shape — later tasks extend it, don't redesign it):
```json
{
  "projectId": "...",
  "title": "...",
  "createdAt": "...",
  "status": "DRAFT",
  "step": "STYLE",
  "stepStatus": "PENDING",
  "errorMessage": null,
  "style": null,
  "characters": [
    { "id": "...", "name": null, "imagePrompt": null, "portraitImagePath": null, "status": "PENDING" }
  ],
  "chapters": [
    { "id": "...", "illustrationPrompt": null, "illustrationImagePath": null, "status": "PENDING" }
  ]
}
```

### `GET /api/v1/files/{project_id}/book-text`
Returns raw text content (`text/plain`) read from `bookTextPath`. No image-serving endpoints yet — those come in Task 06 once portraits/illustrations exist.

## Constraints for the agent

- No new dependencies beyond what's already in `pom.xml` plus `spring-boot-starter-validation`. No file-upload helper libraries — `MultipartFile` is enough.
- No security/auth framework — identity is just an email lookup, not a real login.
- Write a minimal test per endpoint (happy path + one validation-failure case). Full coverage (loading/error/empty states etc.) is Task 08, not this one.
- Keep controllers thin — validation via annotations + `@Valid`, business logic in `service`, persistence in `repository`. No business logic in controllers or entities.
