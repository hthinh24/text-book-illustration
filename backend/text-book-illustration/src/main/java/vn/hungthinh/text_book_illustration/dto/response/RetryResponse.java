package vn.hungthinh.text_book_illustration.dto.response;

public record RetryResponse(
        ProjectDetailResponse project,
        String retryReason   // "FAILED" | "STUCK_TIMEOUT"
) {}
