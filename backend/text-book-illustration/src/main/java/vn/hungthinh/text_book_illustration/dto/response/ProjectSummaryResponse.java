package vn.hungthinh.text_book_illustration.dto.response;

import java.time.Instant;
import java.util.UUID;

import vn.hungthinh.text_book_illustration.entity.ProjectStatus;
import vn.hungthinh.text_book_illustration.entity.Step;
import vn.hungthinh.text_book_illustration.entity.StepStatus;

public record ProjectSummaryResponse(
        UUID id,
        String title,
        ProjectStatus status,
        Step step,
        StepStatus stepStatus,
        Instant createdAt
) {}
