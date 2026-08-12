-- Book-to-Illustration Pipeline — Postgres schema

CREATE TYPE step AS ENUM ('STYLE', 'CHARACTER', 'PORTRAIT', 'CHAPTER', 'ILLUSTRATION');
CREATE TYPE step_status AS ENUM ('PENDING', 'RUNNING', 'FAIL', 'SUCCESS');
CREATE TYPE project_status AS ENUM ('DRAFT', 'IN_PROGRESS', 'DONE');
CREATE TYPE item_status AS ENUM ('PENDING', 'RUNNING', 'TEXT_GENERATED', 'DONE', 'FAIL');

CREATE TABLE "user" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE project (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id),
    title TEXT NOT NULL,
    book_text_path TEXT NOT NULL,
    style TEXT,
    status project_status NOT NULL DEFAULT 'DRAFT',
    step step NOT NULL DEFAULT 'STYLE',
    step_status step_status NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    previous_interaction_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ
);

CREATE TABLE character (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id),
    name TEXT,                                        -- nullable: populated at CHARACTER step
    image_prompt TEXT,
    portrait_image_path TEXT,
    status item_status NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE chapter (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES project(id),
    illustration_prompt TEXT,
    illustration_image_path TEXT,
    status item_status NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE character_chapter (
    character_id UUID NOT NULL REFERENCES character(id),
    chapter_id UUID NOT NULL REFERENCES chapter(id),
    PRIMARY KEY (character_id, chapter_id)
);

