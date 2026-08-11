1. Data model:
- User

step_state (null, style, character, portrait, chapter, illustrations)
step_status (PENDING, FAIL, DONE)
status (draft, in_progress, done)
- Project (id, book, style, status, step_state, step_status, retry_count, previous_interaction_id) ,
- Character, Project_Character ,
- Chapter, Chapter_Character,

3. Concurrency guard: DB row lock

4. API Design
POST /api/v1/init-project
    - name, textbook
    - DB write project, step_state = null, step_status = null, previous_interaction_id = null

GET /api/v1/{project_id}
    - return project_id, step_state, step_status with all related information of success step

POST /api/v1/{project_id}/retry
    - update step_status = PENDING
    - if retry_count > 3, return error
    - if retry_count <= 3, update retry_count = retry_count + 1

POST /api/v1/{project_id}/style
    - update step_state = style, step_status = SUCCESS, previous_interaction_id = interaction_id

POST /api/v1/{project_id}/character
    - update step_state = character, step_status = SUCCESS, previous_interaction_id = interaction_id

POST /api/v1/{project_id}/portraits
    - update step_state = portrait, step_status = SUCCESS, previous_interaction_id = interaction_id

POST /api/v1/{project_id}/chapters
    - update step_state = chapters, step_status = SUCCESS, previous_interaction_id = interaction_id

POST /api/v1/{project_id}/illustrations
    - update step_state = illustrations, step_status = SUCCESS, previous_interaction_id = interaction_id