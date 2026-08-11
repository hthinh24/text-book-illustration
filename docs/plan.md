1. Data model:
- User

step (style, character, portrait, chapter, illustrations)
step_status (PENDING, RUNNING, FAIL, SUCCESS)
status (DRAFT, IN_PROGESS, DONE)
- Project (id, book, style, status, step, step_status, retry_count, error_message, previous_interaction_id, created_at, started_at),
- Character, Project_Character ,
- Chapter, Chapter_Character,

3. Concurrency guard: condition update

4. API Design
POST /api/v1/init-project
    - name, textbook
    - DB write project, step = style, step_status = PENDING, previous_interaction_id = null

GET /api/v1/{project_id}
    - return project_id, step, step_status with all related information of successed step

POST /api/v1/{project_id}/retry
    UPDATE project SET step_status='PENDING', retry_count=retry_count+1
    WHERE id=? AND step=? AND step_status='FAIL' AND retry_count <= 3

POST /api/v1/{project_id}/style
    - update step = style, step_status = SUCCESS, previous_interaction_id = interaction_id

POST /api/v1/{project_id}/character
    - update step = character, step_status = SUCCESS, previous_interaction_id = interaction_id

POST /api/v1/{project_id}/portraits
    - update step = portrait, step_status = SUCCESS, previous_interaction_id = interaction_id

POST /api/v1/{project_id}/chapters
    - update step = chapters, step_status = SUCCESS, previous_interaction_id = interaction_id

POST /api/v1/{project_id}/illustrations
    - update step = illustrations, step_status = SUCCESS, previous_interaction_id = interaction_id

Key queries:
Condition update (claim resource):
    UPDATE project SET step='<step_name>', step_status='RUNNING', started_at=now()
    WHERE id=? AND (
    (step='<prev_step_name>' AND step_status='DONE')      -- case 1: advance from prev_step
    OR
    (step='<step_name>' AND step_status='PENDING') -- case 2: retry
    )

For any step:
Success:
    UPDATE project
    SET step_status = 'DONE', previous_interaction_id = ?
    WHERE id = ? AND step = ? AND step_status = 'RUNNING'

ERROR:
    UPDATE project
    SET step_status = 'FAIL', error_message = ?
    WHERE id = ? AND step = ? AND step_status = 'RUNNING'