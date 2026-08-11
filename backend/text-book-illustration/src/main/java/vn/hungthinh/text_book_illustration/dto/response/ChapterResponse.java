package vn.hungthinh.text_book_illustration.dto.response;

import java.util.UUID;

import vn.hungthinh.text_book_illustration.entity.ItemStatus;

public record ChapterResponse(
        UUID id,
        String illustrationPrompt,
        String illustrationImagePath,
        ItemStatus status
) {}
