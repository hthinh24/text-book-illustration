package vn.hungthinh.text_book_illustration.dto.response;

import java.util.UUID;

import vn.hungthinh.text_book_illustration.entity.ItemStatus;

public record CharacterResponse(
        UUID id,
        String name,
        String imagePrompt,
        String portraitImagePath,
        ItemStatus status
) {}
