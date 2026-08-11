1. Concurrency guard:
I propose to use row level lock (SELECT FOR UPDATE) for concurrency guard, claude push back because Gemini call during process
can hold db connection from 10-30s which is not good on high traffic. Instead claude recommend to use condition update for step_status (from pending -> running) for prevent concurrency.
Cost: Lost strict isolation, have a gap between commit statue = RUNNING and gemini update status = DONE after generate, need timeout + retry to prevent permenant stuck at RUNNING state.

2. Split project steps to separate table:
Claude recommend to use separate table for each step to keep error message & retry count per step.
I push back because pipeline strictly sequential, no need audit trail per step in this stage, create another table will increase logic complexity (lazy init, eager init, logic to determine current step), keep single row in project table in this satuation is more simpler.
Cost: Lost audit per-step, can accepted because we didn`t really need in this time.