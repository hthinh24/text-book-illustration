package vn.hungthinh.text_book_illustration.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.hungthinh.text_book_illustration.config.AppProperties;
import vn.hungthinh.text_book_illustration.dto.request.StyleRequest;
import vn.hungthinh.text_book_illustration.dto.response.CharacterResponse;
import vn.hungthinh.text_book_illustration.dto.response.ChapterResponse;
import vn.hungthinh.text_book_illustration.dto.response.ProjectDetailResponse;
import vn.hungthinh.text_book_illustration.dto.response.RetryResponse;
import vn.hungthinh.text_book_illustration.entity.*;
import vn.hungthinh.text_book_illustration.entity.Character;
import vn.hungthinh.text_book_illustration.repository.ChapterRepository;
import vn.hungthinh.text_book_illustration.repository.CharacterRepository;
import vn.hungthinh.text_book_illustration.repository.ProjectRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineService {

    private final ProjectRepository projectRepository;
    private final CharacterRepository characterRepository;
    private final ChapterRepository chapterRepository;
    private final GeminiClient geminiClient;
    private final AppProperties appProperties;

    // ------------------------------------------------------------------ //
    //  POST /projects/{id}/style                                           //
    // ------------------------------------------------------------------ //

    public ProjectDetailResponse triggerStyle(UUID projectId, StyleRequest request) {
        boolean hasUserStyle = request != null && request.hasUserStyle();
        log.info("[Service] Starting triggerStyle: projectId={}, hasUserStyle={}", projectId, hasUserStyle);

        int rows = projectRepository.claimStyleStep(
                projectId, Instant.now(),
                StepStatus.RUNNING, StepStatus.PENDING, Step.STYLE);

        if (rows == 0) {
            return handleClaimFailure(projectId, Step.STYLE);
        }

        log.info("[Service] status PENDING → RUNNING: projectId={}, step=STYLE", projectId);

        if (hasUserStyle) {
            // No external call — finalize inline; still returns 202 per spec
            finalizeStyleSuccess(projectId, request.style(), null);
        } else {
            executeStyleAsync(projectId);
        }

        ProjectDetailResponse result = toDetail(findProjectOrThrow(projectId));
        log.info("[Service] Finished triggerStyle claim: projectId={}, stepStatus={}", projectId, result.stepStatus());
        return result;
    }

    @Async
    public void executeStyleAsync(UUID projectId) {
        log.info("[Service] Starting executeStyleAsync: projectId={}", projectId);
        Project project = findProjectOrThrow(projectId);
        try {
            String bookText = readBookText(project);
            GeminiClient.Result<String> result = geminiClient.generateStyle(bookText, project.getPreviousInteractionId());
            finalizeStyleSuccess(projectId, result.value(), result.interactionId());
        } catch (Exception e) {
            log.error("[Service] STYLE step failed: projectId={}, step=STYLE, error={}", projectId, e.getMessage(), e);
            projectRepository.finalizeStepFail(
                    projectId, Step.STYLE, e.getMessage(),
                    StepStatus.FAIL, StepStatus.RUNNING);
            log.error("[Service] status RUNNING → FAIL: projectId={}, step=STYLE, error={}", projectId, e.getMessage());
        }
    }

    @Transactional
    public void finalizeStyleSuccess(UUID projectId, String style, String interactionId) {
        projectRepository.finalizeStepSuccess(
                projectId, Step.STYLE, interactionId,
                StepStatus.SUCCESS, StepStatus.RUNNING);
        // Also persist the style value on the project row
        Project project = findProjectOrThrow(projectId);
        project.setStyle(style);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        projectRepository.save(project);
        log.info("[Service] status RUNNING → SUCCESS: projectId={}, step=STYLE, interactionId={}", projectId, interactionId);
    }

    // ------------------------------------------------------------------ //
    //  POST /projects/{id}/character                                       //
    // ------------------------------------------------------------------ //

    public ProjectDetailResponse triggerCharacter(UUID projectId) {
        log.info("[Service] Starting triggerCharacter: projectId={}", projectId);

        int rows = projectRepository.claimStep(
                projectId, Step.STYLE, Step.CHARACTER, Instant.now(),
                StepStatus.RUNNING, StepStatus.SUCCESS, StepStatus.PENDING);

        if (rows == 0) {
            return handleClaimFailure(projectId, Step.CHARACTER);
        }

        log.info("[Service] status PENDING/SUCCESS → RUNNING: projectId={}, step=CHARACTER", projectId);
        executeCharacterAsync(projectId);
        ProjectDetailResponse result = toDetail(findProjectOrThrow(projectId));
        log.info("[Service] Finished triggerCharacter claim: projectId={}, stepStatus={}", projectId, result.stepStatus());
        return result;
    }

    @Async
    public void executeCharacterAsync(UUID projectId) {
        log.info("[Service] Starting executeCharacterAsync: projectId={}", projectId);
        Project project = findProjectOrThrow(projectId);
        try {
            GeminiClient.Result<List<GeminiClient.CharacterData>> result =
                    geminiClient.generateCharacters(project.getPreviousInteractionId());

            // Cap at 2 — truncate silently if Gemini returns more
            List<GeminiClient.CharacterData> chars = result.value().stream().limit(2).toList();

            for (GeminiClient.CharacterData data : chars) {
                Character character = new Character();
                character.setProject(project);
                character.setName(data.name());
                character.setImagePrompt(data.imagePrompt());
                character.setStatus(ItemStatus.TEXT_GENERATED);
                characterRepository.save(character);
                log.info("[Service] Created character: projectId={}, characterName={}", projectId, data.name());
            }

            projectRepository.finalizeStepSuccess(
                    projectId, Step.CHARACTER, result.interactionId(),
                    StepStatus.SUCCESS, StepStatus.RUNNING);
            log.info("[Service] status RUNNING → SUCCESS: projectId={}, step=CHARACTER, characterCount={}", projectId, chars.size());
        } catch (Exception e) {
            log.error("[Service] CHARACTER step failed: projectId={}, step=CHARACTER, error={}", projectId, e.getMessage(), e);
            projectRepository.finalizeStepFail(
                    projectId, Step.CHARACTER, e.getMessage(),
                    StepStatus.FAIL, StepStatus.RUNNING);
            log.error("[Service] status RUNNING → FAIL: projectId={}, step=CHARACTER, error={}", projectId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  POST /projects/{id}/portraits                                       //
    // ------------------------------------------------------------------ //

    public ProjectDetailResponse triggerPortraits(UUID projectId) {
        log.info("[Service] Starting triggerPortraits: projectId={}", projectId);

        int rows = projectRepository.claimStep(
                projectId, Step.CHARACTER, Step.PORTRAIT, Instant.now(),
                StepStatus.RUNNING, StepStatus.SUCCESS, StepStatus.PENDING);

        if (rows == 0) {
            return handleClaimFailure(projectId, Step.PORTRAIT);
        }

        log.info("[Service] status PENDING/SUCCESS → RUNNING: projectId={}, step=PORTRAIT", projectId);
        executePortraitsAsync(projectId);
        ProjectDetailResponse result = toDetail(findProjectOrThrow(projectId));
        log.info("[Service] Finished triggerPortraits claim: projectId={}, stepStatus={}", projectId, result.stepStatus());
        return result;
    }

    @Async
    public void executePortraitsAsync(UUID projectId) {
        log.info("[Service] Starting executePortraitsAsync: projectId={}", projectId);
        Project project = findProjectOrThrow(projectId);
        List<Character> characters = characterRepository.findByProjectOrderById(project);
        String lastInteractionId = project.getPreviousInteractionId();
        String failMessage = null;

        for (Character character : characters) {
            if (character.getStatus() == ItemStatus.DONE) {
                log.info("[Service] Skipping character portrait (already DONE): projectId={}, characterId={}, characterName={}",
                        projectId, character.getId(), character.getName());
                continue; // Skip already-done items on retry
            }

            characterRepository.updateStatus(character.getId(), ItemStatus.RUNNING);
            log.info("[Service] status item PENDING → RUNNING: projectId={}, step=PORTRAIT, characterId={}, characterName={}",
                    projectId, character.getId(), character.getName());

            try {
                GeminiClient.Result<String> result = geminiClient.generatePortrait(
                        character.getId(),
                        character.getName(),
                        character.getImagePrompt(),
                        lastInteractionId);

                characterRepository.updatePortraitDone(character.getId(), result.value(), ItemStatus.DONE);
                lastInteractionId = result.interactionId();
                log.info("[Service] status item RUNNING → DONE: projectId={}, step=PORTRAIT, characterId={}, characterName={}",
                        projectId, character.getId(), character.getName());
            } catch (Exception e) {
                log.error("[Service] status item RUNNING → FAIL: projectId={}, step=PORTRAIT, characterId={}, characterName={}, error={}",
                        projectId, character.getId(), character.getName(), e.getMessage(), e);
                characterRepository.updateStatus(character.getId(), ItemStatus.FAIL);
                failMessage = "Portrait generation failed for character '"
                        + character.getName() + "': " + e.getMessage();
                break; // Abort-early on first FAIL — remaining items stay PENDING
            }
        }

        if (failMessage != null) {
            projectRepository.finalizeStepFail(
                    projectId, Step.PORTRAIT, failMessage,
                    StepStatus.FAIL, StepStatus.RUNNING);
            log.error("[Service] status RUNNING → FAIL: projectId={}, step=PORTRAIT, error={}", projectId, failMessage);
        } else {
            projectRepository.finalizeStepSuccess(
                    projectId, Step.PORTRAIT, lastInteractionId,
                    StepStatus.SUCCESS, StepStatus.RUNNING);
            log.info("[Service] status RUNNING → SUCCESS: projectId={}, step=PORTRAIT", projectId);
        }
    }

    // ------------------------------------------------------------------ //
    //  POST /projects/{id}/chapters                                        //
    // ------------------------------------------------------------------ //

    public ProjectDetailResponse triggerChapters(UUID projectId) {
        log.info("[Service] Starting triggerChapters: projectId={}", projectId);

        int rows = projectRepository.claimStep(
                projectId, Step.PORTRAIT, Step.CHAPTER, Instant.now(),
                StepStatus.RUNNING, StepStatus.SUCCESS, StepStatus.PENDING);

        if (rows == 0) {
            return handleClaimFailure(projectId, Step.CHAPTER);
        }

        log.info("[Service] status PENDING/SUCCESS → RUNNING: projectId={}, step=CHAPTER", projectId);
        executeChaptersAsync(projectId);
        ProjectDetailResponse result = toDetail(findProjectOrThrow(projectId));
        log.info("[Service] Finished triggerChapters claim: projectId={}, stepStatus={}", projectId, result.stepStatus());
        return result;
    }

    @Async
    public void executeChaptersAsync(UUID projectId) {
        log.info("[Service] Starting executeChaptersAsync: projectId={}", projectId);
        Project project = findProjectOrThrow(projectId);
        try {
            GeminiClient.Result<List<GeminiClient.ChapterData>> result =
                    geminiClient.generateChapters(project.getStyle(), project.getPreviousInteractionId());

            // Cap at 1 — truncate silently if Gemini returns more
            List<GeminiClient.ChapterData> chaps = result.value().stream().limit(1).toList();

            for (GeminiClient.ChapterData data : chaps) {
                Chapter chapter = new Chapter();
                chapter.setProject(project);
                chapter.setIllustrationPrompt(data.illustrationPrompt());
                chapter.setStatus(ItemStatus.TEXT_GENERATED);
                chapterRepository.save(chapter);
                log.info("[Service] Created chapter: projectId={}, chapterId={}", projectId, chapter.getId());
            }

            projectRepository.finalizeStepSuccess(
                    projectId, Step.CHAPTER, result.interactionId(),
                    StepStatus.SUCCESS, StepStatus.RUNNING);
            log.info("[Service] status RUNNING → SUCCESS: projectId={}, step=CHAPTER, chapterCount={}", projectId, chaps.size());
        } catch (Exception e) {
            log.error("[Service] CHAPTER step failed: projectId={}, step=CHAPTER, error={}", projectId, e.getMessage(), e);
            projectRepository.finalizeStepFail(
                    projectId, Step.CHAPTER, e.getMessage(),
                    StepStatus.FAIL, StepStatus.RUNNING);
            log.error("[Service] status RUNNING → FAIL: projectId={}, step=CHAPTER, error={}", projectId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  POST /projects/{id}/illustrations                                   //
    // ------------------------------------------------------------------ //

    public ProjectDetailResponse triggerIllustrations(UUID projectId) {
        log.info("[Service] Starting triggerIllustrations: projectId={}", projectId);

        int rows = projectRepository.claimStep(
                projectId, Step.CHAPTER, Step.ILLUSTRATION, Instant.now(),
                StepStatus.RUNNING, StepStatus.SUCCESS, StepStatus.PENDING);

        if (rows == 0) {
            return handleClaimFailure(projectId, Step.ILLUSTRATION);
        }

        log.info("[Service] status PENDING/SUCCESS → RUNNING: projectId={}, step=ILLUSTRATION", projectId);
        executeIllustrationsAsync(projectId);
        ProjectDetailResponse result = toDetail(findProjectOrThrow(projectId));
        log.info("[Service] Finished triggerIllustrations claim: projectId={}, stepStatus={}", projectId, result.stepStatus());
        return result;
    }

    @Async
    public void executeIllustrationsAsync(UUID projectId) {
        log.info("[Service] Starting executeIllustrationsAsync: projectId={}", projectId);
        Project project = findProjectOrThrow(projectId);
        List<Chapter> chapters = chapterRepository.findByProjectOrderById(project);
        String lastInteractionId = project.getPreviousInteractionId();
        String failMessage = null;

        for (Chapter chapter : chapters) {
            if (chapter.getStatus() == ItemStatus.DONE) {
                log.info("[Service] Skipping chapter illustration (already DONE): projectId={}, chapterId={}",
                        projectId, chapter.getId());
                continue; // Skip already-done items on retry
            }

            chapterRepository.updateStatus(chapter.getId(), ItemStatus.RUNNING);
            log.info("[Service] status item PENDING → RUNNING: projectId={}, step=ILLUSTRATION, chapterId={}",
                    projectId, chapter.getId());

            try {
                GeminiClient.Result<String> result = geminiClient.generateIllustration(
                        chapter.getId(),
                        chapter.getIllustrationPrompt(),
                        lastInteractionId);

                chapterRepository.updateIllustrationDone(chapter.getId(), result.value(), ItemStatus.DONE);
                lastInteractionId = result.interactionId();
                log.info("[Service] status item RUNNING → DONE: projectId={}, step=ILLUSTRATION, chapterId={}",
                        projectId, chapter.getId());
            } catch (Exception e) {
                log.error("[Service] status item RUNNING → FAIL: projectId={}, step=ILLUSTRATION, chapterId={}, error={}",
                        projectId, chapter.getId(), e.getMessage(), e);
                chapterRepository.updateStatus(chapter.getId(), ItemStatus.FAIL);
                failMessage = "Illustration generation failed for chapter "
                        + chapter.getId() + ": " + e.getMessage();
                break; // Abort-early on first FAIL
            }
        }

        if (failMessage != null) {
            projectRepository.finalizeStepFail(
                    projectId, Step.ILLUSTRATION, failMessage,
                    StepStatus.FAIL, StepStatus.RUNNING);
            log.error("[Service] status RUNNING → FAIL: projectId={}, step=ILLUSTRATION, error={}", projectId, failMessage);
        } else {
            projectRepository.finalizeStepSuccess(
                    projectId, Step.ILLUSTRATION, lastInteractionId,
                    StepStatus.SUCCESS, StepStatus.RUNNING);
            projectRepository.completeProject(projectId, ProjectStatus.DONE);
            log.info("[Service] status RUNNING → SUCCESS: projectId={}, step=ILLUSTRATION, projectStatus=DONE", projectId);
        }
    }

    // ------------------------------------------------------------------ //
    //  POST /projects/{id}/retry                                           //
    // ------------------------------------------------------------------ //

    public RetryResponse retry(UUID projectId) {
        log.info("[Service] Starting retry: projectId={}", projectId);
        // DB is the source of truth for which step to retry — don't trust FE
        Project project = findProjectOrThrow(projectId);
        Step currentStep = project.getStep();
        int maxRetryCount = appProperties.getMaxRetryCount();
        int timeoutSeconds = appProperties.getStepTimeoutSeconds();

        // Try FAIL path first
        int failRows = projectRepository.retryFailedStep(
                projectId, currentStep, maxRetryCount,
                StepStatus.PENDING, StepStatus.FAIL);
        if (failRows == 1) {
            log.info("[Service] status FAIL → PENDING (retry claimed): projectId={}, step={}, reason=FAILED",
                    projectId, currentStep);
            RetryResponse response = new RetryResponse(toDetail(findProjectOrThrow(projectId)), "FAILED");
            log.info("[Service] Finished retry successfully: projectId={}, reason=FAILED", projectId);
            return response;
        }

        // Try stuck-RUNNING path
        Instant timeoutBefore = Instant.now().minus(timeoutSeconds, ChronoUnit.SECONDS);
        int stuckRows = projectRepository.recoverStuckStep(
                projectId, currentStep, timeoutBefore,
                StepStatus.PENDING, StepStatus.RUNNING);
        if (stuckRows == 1) {
            log.info("[Service] status RUNNING → PENDING (stuck timeout recovered): projectId={}, step={}, reason=STUCK_TIMEOUT",
                    projectId, currentStep);
            RetryResponse response = new RetryResponse(toDetail(findProjectOrThrow(projectId)), "STUCK_TIMEOUT");
            log.info("[Service] Finished retry successfully: projectId={}, reason=STUCK_TIMEOUT", projectId);
            return response;
        }

        // Neither path matched — re-query project state to differentiate RETRY_EXHAUSTED from other conflict reasons
        Project fresh = findProjectOrThrow(projectId);
        if (fresh.getStepStatus() == StepStatus.FAIL && fresh.getRetryCount() >= maxRetryCount) {
            log.warn("[Service] Retry exhausted: projectId={}, step={}, retryCount={}, maxRetryCount={}",
                    projectId, currentStep, fresh.getRetryCount(), maxRetryCount);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "RETRY_EXHAUSTED: Retry limit reached for step '" + currentStep + "' (" + fresh.getRetryCount() + "/" + maxRetryCount + ").");
        }

        log.warn("[Service] Retry rejected (not in retryable state): projectId={}, step={}, stepStatus={}, retryCount={}",
                projectId, currentStep, fresh.getStepStatus(), fresh.getRetryCount());
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Project is not in a retryable state. Step '" + currentStep +
                "' must be FAIL (with retryCount <= " + maxRetryCount +
                ") or RUNNING and stuck for >" + timeoutSeconds + "s.");
    }

    // ------------------------------------------------------------------ //
    //  Claim failure branching (shared logic)                              //
    // ------------------------------------------------------------------ //

    /**
     * Called when a claim UPDATE returns 0 affected rows.
     * Re-queries the project and branches into the correct 200/409 response.
     */
    private ProjectDetailResponse handleClaimFailure(UUID projectId, Step thisStep) {
        Project project = findProjectOrThrow(projectId);

        if (project.getStep() == thisStep && project.getStepStatus() == StepStatus.SUCCESS) {
            // Idempotent — step already done, return 200 with current state
            log.info("[Service] Claim no-op (already SUCCESS): projectId={}, step={}", projectId, thisStep);
            return toDetail(project);
        }

        if (project.getStep() == thisStep && project.getStepStatus() == StepStatus.RUNNING) {
            log.warn("[Service] Claim conflict (already RUNNING): projectId={}, step={}", projectId, thisStep);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Step '" + thisStep + "' is already running. Poll GET /api/v1/{id} for current status.");
        }

        // Wrong order or any other mismatch — distinct message from the RUNNING case
        log.warn("[Service] Claim conflict (wrong order / not ready): projectId={}, step={}, currentStep={}, currentStepStatus={}",
                projectId, thisStep, project.getStep(), project.getStepStatus());
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Step '" + thisStep + "' is not ready. Current state: step=" + project.getStep() +
                ", stepStatus=" + project.getStepStatus() + ". Complete prior steps first.");
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    private Project findProjectOrThrow(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> {
                    log.warn("[Service] Project not found: projectId={}", projectId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId);
                });
    }

    private String readBookText(Project project) {
        try {
            return Files.readString(Path.of(project.getBookTextPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[Service] Failed to read book text: projectId={}, path={}, error={}",
                    project.getId(), project.getBookTextPath(), e.getMessage(), e);
            throw new RuntimeException("Failed to read book text for project " + project.getId(), e);
        }
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse toDetail(Project project) {
        List<CharacterResponse> characters = characterRepository.findByProject(project).stream()
                .map(c -> new CharacterResponse(
                        c.getId(),
                        c.getName(),
                        c.getImagePrompt(),
                        c.getPortraitImagePath(),
                        c.getStatus()))
                .toList();

        List<ChapterResponse> chapters = chapterRepository.findByProject(project).stream()
                .map(ch -> new ChapterResponse(
                        ch.getId(),
                        ch.getIllustrationPrompt(),
                        ch.getIllustrationImagePath(),
                        ch.getStatus()))
                .toList();

        return new ProjectDetailResponse(
                project.getId(),
                project.getTitle(),
                project.getCreatedAt(),
                project.getStatus(),
                project.getStep(),
                project.getStepStatus(),
                project.getErrorMessage(),
                project.getStyle(),
                characters,
                chapters);
    }
}
