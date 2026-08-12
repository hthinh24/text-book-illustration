1. Concurrency guard:
I propose to use row level lock (SELECT FOR UPDATE) for concurrency guard, claude push back because Gemini call during process
can hold db connection from 10-30s which is not good on high traffic. Instead claude recommend to use condition update for step_status (from pending -> running) for prevent concurrency.
Cost: Lost strict isolation, have a gap between commit statue = RUNNING and gemini update status = DONE after generate, need timeout + retry to prevent permenant stuck at RUNNING state.

2. Split project steps to separate table:
Claude recommend to use separate table for each step to keep error message & retry count per step.
I push back because pipeline strictly sequential, no need audit trail per step in this stage, create another table will increase logic complexity (lazy init, eager init, logic to determine current step), keep single row in project table in this satuation is more simpler.
Cost: Lost audit per-step, can accepted because we didn`t really need in this time.

3. Database Schema Migration
Agent use hibernate.ddl-auto=update for fast auto-generating and altering DB schema directly from Java entities. 
I push back and force ddl-auto=validate with Flyway because it give us more safety control on schema that can prevent implicit schema drifts, orphan columns.
Cost: Need to maintain migration scripts for every entity change.

4. Abort-early on first item FAIL
Claude recommend continue run for remaining items after first item fail because cap character is 2 that can help user reduce their round trip.
I push back because currently why only use one model per text / image generation, single model per type mean share failure mode, for ex: If the first item fail because rate limit, out of quote, model downing then the next item almost certainly will hit the same error, keep continue didn`t bring any value.
Cost: worse UX for case that can be success, the user gets a partial result instead of a full one in a single round trip. If item #1 fails (For ex: timeout) but item #2 would have actually succeeded, aborting early mean the user has to retry instead of getting both outcomes at once.
