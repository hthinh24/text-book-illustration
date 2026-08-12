# Task: Refactor logging for items #4–#5 + update agent.md

## Context
The functions implemented in item #4 (Identity + Project CRUD) and item #5 (pipeline state
machine) currently don't have adequate logging. Successful requests log nothing, making it
hard to trace the app's real behavior during review or debugging. We need to refactor
logging according to the standard below, apply it to the existing code, and write this
standard into agent.md so the agent applies it automatically to code written from item #6
onward, without needing to be reminded each time.

## Logging standard to apply

### 1. Controller — one log line when the request comes in
- Log right at the start of the method, before calling down into the service.
- Content: action name + key params (id, a short summary of the request body if needed).
- Do NOT log at the end of the controller method (the HTTP response status is already the
  result, so avoid duplicating the final log on the service side).

```java
log.info("[Controller] activateStep called: projectId={}, step={}", projectId, step);
```

### 2. Service — log at the start and end of the method
- Start of method: log that processing has begun + the input.
- End of method (before every successful return): log the processing result.
- If a method has multiple return branches (e.g. a failed claim → early return with 200/409),
  each branch needs its own log — not just a log at the very end.

```java
log.info("[Service] Starting activateStep: projectId={}, step={}", projectId, step);
...
log.info("[Service] Finished activateStep: projectId={}, step={}, result={}", projectId, step, result);
```

### 3. Every important status change — log the transition clearly
Applies to all the state transitions designed in item #5 (claim → RUNNING,
RUNNING → SUCCESS, RUNNING → FAIL, and the 3 branch cases when a claim fails: already
SUCCESS / currently RUNNING due to another request / called out of order).

- Log format: state the fromStatus → toStatus explicitly, along with projectId/step (and
  item index if it's a multi-item step like PORTRAIT/ILLUSTRATION).
- The failed-claim case is the most important — must clearly log which of the 3 cases it
  fell into, since this is the core branching logic of the pipeline.

```java
log.warn("[Service] Claim failed: projectId={}, step={}, currentStatus={} (reason: already SUCCESS / currently RUNNING / wrong order)",
        projectId, step, current.getStepStatus());

log.info("[Service] status RUNNING → SUCCESS: projectId={}, step={}", projectId, step);

log.error("[Service] status RUNNING → FAIL: projectId={}, step={}, error={}", projectId, step, e.getMessage(), e);
```

### 4. Exception catch blocks — log full context, not just e.getMessage()
- Every catch(Exception) that currently persists e.getMessage() into error_message must
  also include a log.error line right at the catch site, BEFORE or AT THE SAME TIME as the
  persist operation.
- Pass the exception object at the end of the log call (not just the String message) so the
  logging framework prints the stack trace — this is mandatory, since this is the spot where
  debugging really needs to know exactly which line the error blew up on.
- Include enough business context in the log (projectId, step, item index if applicable) —
  never log a bare exception with no context.

```java
} catch (Exception e) {
    log.error("[Service] activateStep failed: projectId={}, step={}, error={}", projectId, step, e.getMessage(), e);
    ...
}
```

## What needs to be done

1. Review all the code in item #4 (Identity + Project CRUD) and item #5 (pipeline state
   machine): controllers, services, and every existing catch(Exception).
2. Add logging according to the 4 rules above — do not change any logic, only add logging.
3. Use consistent tag prefixes: `[Controller]`, `[Service]` (no `[Async]` needed if there's
   no asynchronous processing yet in these two items — only add it when refactoring reaches
   the async part in item #6).
4. Do not log large full objects (e.g. long response bodies, base64 data) — only log the
   fields needed for tracing (id, status, error message).
5. After finishing the refactor, update `agent.md` (or the equivalent guideline file the
   agent reads) by adding a "Logging convention" section summarizing exactly the 4 rules
   above, so that from item #6 onward the agent applies them automatically to new code
   without needing to be reminded in each task brief.

## Out of scope

- Do not use AOP/interceptors to auto-log — the team has decided to log explicitly by hand,
  since the project is small in scope and needs log messages with clear business meaning
  rather than generic logs.
- Do not change existing behavior/logic — only add logging.
