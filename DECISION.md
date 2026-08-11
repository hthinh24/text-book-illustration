1. Concurrency guard:
I propose to use row level lock (SELECT FOR UPDATE) for concurrency guard, claude push back because Gemini call during process
can hold db connection from 10-30s which is not good on high traffic. Instead claude recommend to use condition update for step_status (from pending -> running) for prevent concurrency.
Cost: Lost strict isolation, have a gap between commit statue = RUNNING and gemini update status = DONE after generate, need timeout + retry to prevent permenant stuck at RUNNING state.