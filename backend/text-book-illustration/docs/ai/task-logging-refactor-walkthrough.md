# Logging Refactor — Implementation Walkthrough

## What Was Built

Refactored logging across all Controller and Service classes for **Task 04** (Identity + Project CRUD) and **Task 05** (Pipeline State Machine), and updated [AGENT.md](file:///d:/collection/applied/Gradion/code/AGENT.md) with logging rules for Task 06+.

---

## Files Modified

### Controllers
| File | Changes |
|---|---|
| [IdentityController.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/controller/IdentityController.java) | Added `@Slf4j` and `[Controller] identity called: email={}` entry log |
| [ProjectController.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/controller/ProjectController.java) | Added `@Slf4j` and `[Controller]` entry logs to `initProject`, `listProjects`, `getProject`, and `getBookText` |
| [PipelineController.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/controller/PipelineController.java) | Added `@Slf4j` and `[Controller]` entry logs to `triggerStyle`, `triggerCharacter`, `triggerPortraits`, `triggerChapters`, `triggerIllustrations`, and `retry` |

### Services
| File | Changes |
|---|---|
| [IdentityService.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/IdentityService.java) | Added `@Slf4j`, method entry log `Starting getOrCreate identity`, and branch exit logs for both existing user found and new user created |
| [ProjectService.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/ProjectService.java) | Added `@Slf4j`, method entry/exit logs for `initProject`, `listProjects`, `getProject`, `getBookText`, validation warning logs, and stack trace error logs for file/DB exceptions |
| [PipelineService.java](file:///d:/collection/applied/Gradion/code/backend/text-book-illustration/src/main/java/vn/hungthinh/text_book_illustration/service/PipelineService.java) | Added structured entry/exit logs, explicit state transition logs (`status <from> → <to>`), explicit branching logs for claim failures (`already SUCCESS`, `currently RUNNING`, `wrong order`), per-item status logs for PORTRAIT and ILLUSTRATION multi-item steps, and full stack trace exception logs |

### Documentation
| File | Changes |
|---|---|
| [AGENT.md](file:///d:/collection/applied/Gradion/code/AGENT.md) | Added a new **Logging conventions** section documenting the 4 strict manual logging rules across Controller and Service layers for all future tasks (Task 06+) |

---

## Applied Logging Rules Summary

1. **Controller Layer (`[Controller]`):**
   - Single `log.info` line right at entry with action name and key parameters.
   - No log at exit (avoids duplicating service-layer results/HTTP status).

2. **Service Layer (`[Service]`):**
   - `log.info` at start (`Starting <action>`) and before every return/exit branch.

3. **State Transitions & Claim Branching:**
   - State transition logs format: `status <from> → <to>: projectId={}, step={}`
   - Claim failure logic logs clearly which branch was taken (`already SUCCESS`, `currently RUNNING`, `wrong order`).
   - Multi-item loops log status per character/chapter item (`status item PENDING → RUNNING`, `status item RUNNING → DONE`).

4. **Exception Handling:**
   - Every `catch (Exception e)` block includes business context (`projectId`, `step`, item details) and passes `e` as the final parameter so the logging framework prints the full stack trace.
