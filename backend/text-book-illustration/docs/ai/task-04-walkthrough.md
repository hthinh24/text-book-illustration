# Task 04 — Implementation Walkthrough

## What Was Built

Full Identity + Project CRUD layer for the text-book-illustration Spring Boot 4.1.0 backend.

---

## Files Created

### Config
| File | Purpose |
|---|---|
| [AppProperties.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/config/AppProperties.java) | `@ConfigurationProperties(prefix = "app")` — binds `app.file-storage-root` |

### Enums
| File | Values |
|---|---|
| [ProjectStatus.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/entity/ProjectStatus.java) | `DRAFT, IN_PROGRESS, DONE` |
| [Step.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/entity/Step.java) | `STYLE, CHARACTER, PORTRAIT, CHAPTER, ILLUSTRATION` |
| [StepStatus.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/entity/StepStatus.java) | `PENDING, RUNNING, FAIL, SUCCESS` |
| [ItemStatus.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/entity/ItemStatus.java) | `PENDING, RUNNING, DONE, FAIL` (per character/chapter) |

### Entities
| File | Notes |
|---|---|
| [User.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/entity/User.java) | `@Table(name = "\"user\"")` — quoted because `user` is a PostgreSQL reserved word |
| [Project.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/entity/Project.java) | All pipeline state fields, defaults set in field declarations |
| [Character.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/entity/Character.java) | `name` is nullable, owns `@ManyToMany` with `@JoinTable(character_chapter)` |
| [Chapter.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/entity/Chapter.java) | Inverse side of the `@ManyToMany` (no list declared, clean) |

### Repositories
All `JpaRepository<T, UUID>`. [UserRepository](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/repository/UserRepository.java), [ProjectRepository](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/repository/ProjectRepository.java), [CharacterRepository](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/repository/CharacterRepository.java), [ChapterRepository](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/repository/ChapterRepository.java).

### DTOs
- Request: [IdentityRequest](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/dto/request/IdentityRequest.java) (Java record with `@Email @NotBlank`)
- Response: [IdentityResponse](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/dto/response/IdentityResponse.java), [ProjectSummaryResponse](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/dto/response/ProjectSummaryResponse.java), [CharacterResponse](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/dto/response/CharacterResponse.java), [ChapterResponse](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/dto/response/ChapterResponse.java), [ProjectDetailResponse](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/dto/response/ProjectDetailResponse.java)

### Services
- [IdentityService](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/IdentityService.java) — email lookup, create-if-absent, never overwrites existing name
- [ProjectService](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/ProjectService.java) — all 4 CRUD methods + file I/O

### Controllers
- [IdentityController](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/controller/IdentityController.java) — `POST /api/v1/identity`
- [ProjectController](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/controller/ProjectController.java) — `POST /init-project`, `GET /projects`, `GET /{id}`, `GET /files/{id}/book-text`

---

## Files Modified

| File | Change |
|---|---|
| [application.yaml](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/resources/application.yaml) | Added `spring.jpa.hibernate.ddl-auto: update` and `open-in-view: false` |
| [TextBookIllustrationApplication.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/TextBookIllustrationApplication.java) | Added `@EnableConfigurationProperties(AppProperties.class)` |
| [docs/schema.sql](file:///d:/collection/applied/Gradion/code/docs/schema.sql) | Added `item_status` enum, made `character.name` nullable, added `status` columns to `character` and `chapter` |

---

## Test Results

```
Tests run: 3, Failures: 0 — IdentityControllerTest   ✅
Tests run: 2, Failures: 0 — ProjectControllerTest    ✅
Tests run: 2, Failures: 0 — ProjectServiceTest       ✅
```

> `TextBookIllustrationApplicationTests` (the pre-existing context-loads test) fails because it needs a live PostgreSQL connection — expected behavior without Docker Compose running. Not part of this task's scope.

---

## Notable Design Decisions

### Spring Boot 4.x `@WebMvcTest` package change
In Spring Boot 4.1, `@WebMvcTest` moved from:
- **Old:** `org.springframework.boot.test.autoconfigure.web.servlet`
- **New:** `org.springframework.boot.webmvc.test.autoconfigure`

This was discovered by inspecting the `spring-boot-webmvc-test-4.1.0.jar`. Tests updated accordingly.

### Pre-generated project UUID
In `ProjectService.initProject()`, the project UUID is generated *before* the `save()` call so we can compute the storage path (`{fileStorageRoot}/{projectId}/book.txt`) before writing to disk, then pass the same UUID to JPA via `@GeneratedValue(strategy = UUID)` override.

### `character.name` constraint vs schema.sql
Task-04 spec takes precedence: `name` is `nullable` in the entity. Hibernate's `ddl-auto: update` will **not** automatically drop the existing `NOT NULL` constraint if the DB already has data. For a fresh DB this is handled automatically; an existing DB needs:
```sql
ALTER TABLE character ALTER COLUMN name DROP NOT NULL;
```
