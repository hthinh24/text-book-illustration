# AGENT.md

Persistent context for AI agents working in this repo. Read this before starting any task. Task-specific instructions live in separate task briefs (`task-0X-*.md`) — this file covers what stays true across tasks.

## Project

Book-to-illustration pipeline app — take-home assessment for Gradion (formerly NFQ Vietnam). Uploads a book text, runs it through a multi-step Gemini-powered pipeline (style → characters → portraits → chapters → illustrations) to produce illustrated output.

## Stack

- Backend: Spring Boot 4.1.0, Java, Maven
- Database: Postgres 17 (via docker-compose)
- Frontend: React/Vite/Tailwind (not started yet — backend-first)
- AI: Gemini Interactions API — REST only, **no official Java SDK**. Must call REST directly. Response shape uses `steps[]` timeline, not the older `candidates[0].content.parts` shape. Images come back as base64 inside `model_output.content[]` items with `type: image`.

## Repo layout

```
/                              repo root — .env, .env.example, start.sh, test.sh, docker-compose.yml
/backend/text-book-illustration/   Maven project root
/docs/schema.sql               DB schema
/docs/data-model.md            data model reference
/DECISIONS.md                  running log of real decisions, written close to when they happen — not backfilled
/task-0X-*.md                  per-task briefs handed to agents, one per work session
```

## Backend package structure

Root package: `vn.hungthinh.text_book_illustration`

Strict 3-layer convention, followed exactly — don't introduce a 4th layer or skip one:
```
controller/   HTTP endpoints, 
dto/          request/response DTOs
service/      business logic, orchestration
repository/   Spring Data JPA repositories
entity/       JPA entities
```

## Data model (current)

```
User: id, email (unique), name, createdAt
Project: id, user(FK), title, bookTextPath, style, status(DRAFT/IN_PROGRESS/DONE),
         step(STYLE/CHARACTER/PORTRAIT/CHAPTER/ILLUSTRATION), stepStatus(PENDING/RUNNING/FAIL/SUCCESS),
         retryCount, errorMessage, previousInteractionId, createdAt, startedAt
Character: id, project(FK), name(nullable), imagePrompt, portraitImagePath, status(PENDING/RUNNING/DONE/FAIL)
Chapter: id, project(FK), illustrationPrompt, illustrationImagePath, status(PENDING/RUNNING/DONE/FAIL)
CharacterChapter: many-to-many join
```

`Project` carries step/retry/error state directly (single row) rather than a separate `ProjectStep` audit table — the pipeline is strictly sequential and the spec has no per-step history requirement. This means `retryCount`/`errorMessage`/`startedAt` all reset on each step transition; there's no audit trail after a step advances. Don't reintroduce a step-history table without a real reason to revisit this.

## API response envelope (pinned shape — extend, don't redesign)

```json
{
  "projectId": "...", "title": "...", "createdAt": "...",
  "status": "DRAFT", "step": "STYLE", "stepStatus": "PENDING", "errorMessage": null,
  "style": null,
  "characters": [{ "id": "...", "name": null, "imagePrompt": null, "portraitImagePath": null, "status": "PENDING" }],
  "chapters": [{ "id": "...", "illustrationPrompt": null, "illustrationImagePath": null, "status": "PENDING" }]
}
```

Any new endpoint that returns project state should return this same shape, not a bespoke one.

## Known gotchas (already hit these — don't repeat)

- **JPA detached-entity error:** don't pre-generate a UUID and call `setId()` before `save()` — causes "detached entity passed to persist". Instead: `save()` first to get the Hibernate-generated ID, do any file-writing that needs the ID, then `save()` again to persist the resulting path/field. Two saves in one transaction, not a `Persistable<UUID>` workaround.
- **Pipeline concurrency:** don't use `SELECT ... FOR UPDATE` for step transitions — it holds a DB connection open across a 10-30s+ Gemini call. Use conditional `UPDATE ... WHERE step_status IN (...)` claim/finalize instead (see task-05 brief for the exact pattern). This is now the standard mechanism for every pipeline step endpoint.

## Commands

- `start.sh` — brings up the stack (docker-compose: Postgres + app)
- `test.sh` — runs tests
- Both run clean as of Task 04.

## Workflow (how we work with agents on this project)

1. Design/edge-cases get discussed and locked in chat first — not decided by the agent mid-implementation.
2. A focused task brief (`task-0X-*.md`) is written with explicit scope boundaries (what NOT to implement is as important as what to).
3. Agent implements against the brief, one task per conversation.
4. Human reviews: read the tests, don't just accept green — manually verify via Postman/DB where it matters.
5. Real decisions get written into `DECISIONS.md` close to when they're made, not backfilled at the end.
6. If a task brief hits a genuinely undecided edge case, the agent should ask rather than guess — briefs will flag known open questions explicitly.

## Status

- Task 04 (Identity + Project CRUD) — done, tested via Postman, all passing.
- Task 05 (pipeline state machine) — next up, see `task-05-pipeline-state-machine.md`.
- Task 06 (real Gemini REST client) — not started; Task 05 stubs the Gemini call so this can slot in later without touching the state machine.

*This file is expected to evolve as the project progresses — update it when a new convention, gotcha, or structural decision gets locked in, not just at the start.*
