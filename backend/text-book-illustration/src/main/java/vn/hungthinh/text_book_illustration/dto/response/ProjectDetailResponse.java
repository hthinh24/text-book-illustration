package vn.hungthinh.text_book_illustration.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import vn.hungthinh.text_book_illustration.entity.ProjectStatus;
import vn.hungthinh.text_book_illustration.entity.Step;
import vn.hungthinh.text_book_illustration.entity.StepStatus;

/**
 * Full project detail envelope — pinned shape per task-04.
 * Later tasks extend the characters/chapters lists with more data, but this record
 * stays as the authoritative response for GET /api/v1/{project_id}.
 */
public record ProjectDetailResponse(
        UUID projectId,
        String title,
        Instant createdAt,
        ProjectStatus status,
        Step step,
        StepStatus stepStatus,
        String errorMessage,
        String style,
        List<CharacterResponse> characters,
        List<ChapterResponse> chapters
) {}
