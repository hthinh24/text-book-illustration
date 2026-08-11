package vn.hungthinh.text_book_illustration.dto.response;

import java.util.UUID;

public record IdentityResponse(
        UUID userId,
        String name
) {}
