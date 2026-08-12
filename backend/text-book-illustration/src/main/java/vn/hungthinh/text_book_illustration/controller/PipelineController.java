package vn.hungthinh.text_book_illustration.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.hungthinh.text_book_illustration.dto.request.StyleRequest;
import vn.hungthinh.text_book_illustration.dto.response.ProjectDetailResponse;
import vn.hungthinh.text_book_illustration.dto.response.RetryResponse;
import vn.hungthinh.text_book_illustration.entity.StepStatus;
import vn.hungthinh.text_book_illustration.service.PipelineService;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Slf4j
public class PipelineController {

    private final PipelineService pipelineService;

    /**
     * POST /api/v1/projects/{id}/style
     * Body (optional JSON): { "style": "watercolor" }
     * Returns 202 if step was claimed, 200 if already SUCCESS (idempotent), 409 on conflict.
     */
    @PostMapping("/{id}/style")
    public ResponseEntity<ProjectDetailResponse> triggerStyle(
            @PathVariable UUID id,
            @RequestBody(required = false) StyleRequest request) {

        log.info("[Controller] triggerStyle called: projectId={}, hasUserStyle={}",
                id, request != null && request.hasUserStyle());
        ProjectDetailResponse response = pipelineService.triggerStyle(id, request);
        return toStepResponse(response);
    }

    /**
     * POST /api/v1/projects/{id}/character
     */
    @PostMapping("/{id}/character")
    public ResponseEntity<ProjectDetailResponse> triggerCharacter(@PathVariable UUID id) {
        log.info("[Controller] triggerCharacter called: projectId={}", id);
        return toStepResponse(pipelineService.triggerCharacter(id));
    }

    /**
     * POST /api/v1/projects/{id}/portraits
     */
    @PostMapping("/{id}/portraits")
    public ResponseEntity<ProjectDetailResponse> triggerPortraits(@PathVariable UUID id) {
        log.info("[Controller] triggerPortraits called: projectId={}", id);
        return toStepResponse(pipelineService.triggerPortraits(id));
    }

    /**
     * POST /api/v1/projects/{id}/chapters
     */
    @PostMapping("/{id}/chapters")
    public ResponseEntity<ProjectDetailResponse> triggerChapters(@PathVariable UUID id) {
        log.info("[Controller] triggerChapters called: projectId={}", id);
        return toStepResponse(pipelineService.triggerChapters(id));
    }

    /**
     * POST /api/v1/projects/{id}/illustrations
     */
    @PostMapping("/{id}/illustrations")
    public ResponseEntity<ProjectDetailResponse> triggerIllustrations(@PathVariable UUID id) {
        log.info("[Controller] triggerIllustrations called: projectId={}", id);
        return toStepResponse(pipelineService.triggerIllustrations(id));
    }

    /**
     * POST /api/v1/projects/{id}/retry
     * No request body — DB is the source of truth for which step to retry.
     * Returns 200 with retryReason, or 409 if not in a retryable state.
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<RetryResponse> retry(@PathVariable UUID id) {
        log.info("[Controller] retry called: projectId={}", id);
        return ResponseEntity.ok(pipelineService.retry(id));
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Returns 202 if the step was just claimed (RUNNING), or 200 if it was
     * already SUCCESS (idempotent re-call). PipelineService throws 409 for
     * genuine conflicts so this method only needs to distinguish 202 vs 200.
     */
    private ResponseEntity<ProjectDetailResponse> toStepResponse(ProjectDetailResponse response) {
        if (response.stepStatus() == StepStatus.RUNNING) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        // SUCCESS (idempotent) — return 200
        return ResponseEntity.ok(response);
    }
}
