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

@Slf4j
@Service
@RequiredArgsConstructor
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
        int rows = projectRepository.claimStyleStep(
                projectId, Instant.now(),
                StepStatus.RUNNING, StepStatus.PENDING, Step.STYLE);

        if (rows == 0) {
            return handleClaimFailure(projectId, Step.STYLE);
        }

        if (request != null && request.hasUserStyle()) {
            // No external call — finalize inline; still returns 202 per spec
            finalizeStyleSuccess(projectId, request.style(), "user-supplied");
        } else {
            executeStyleAsync(projectId);
        }

        return toDetail(findProjectOrThrow(projectId));
    }

    @Async
    public void executeStyleAsync(UUID projectId) {
        Project project = findProjectOrThrow(projectId);
        try {
            String bookText = readBookText(project);
            GeminiClient.Result<String> result = geminiClient.generateStyle(bookText, project.getPreviousInteractionId());
            finalizeStyleSuccess(projectId, result.value(), result.interactionId());
        } catch (Exception e) {
            log.error("STYLE step failed for project {}: {}", projectId, e.getMessage(), e);
            projectRepository.finalizeStepFail(
                    projectId, Step.STYLE, e.getMessage(),
                    StepStatus.FAIL, StepStatus.RUNNING);
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
    }

    // ------------------------------------------------------------------ //
    //  POST /projects/{id}/character                                       //
    // ------------------------------------------------------------------ //

    public ProjectDetailResponse triggerCharacter(UUID projectId) {
        int rows = projectRepository.claimStep(
                projectId, Step.STYLE, Step.CHARACTER, Instant.now(),
                StepStatus.RUNNING, StepStatus.SUCCESS, StepStatus.PENDING);

        if (rows == 0) {
            return handleClaimFailure(projectId, Step.CHARACTER);
        }
        executeCharacterAsync(projectId);
        return toDetail(findProjectOrThrow(projectId));
    }

    @Async
    public void executeCharacterAsync(UUID projectId) {
        Project project = findProjectOrThrow(projectId);
        try {
            String bookText = readBookText(project);
            GeminiClient.Result<List<GeminiClient.CharacterData>> result =
                    geminiClient.generateCharacters(bookText, project.getPreviousInteractionId());

            // Cap at 2 — truncate silently if Gemini returns more
            List<GeminiClient.CharacterData> chars = result.value().stream().limit(2).toList();

            for (GeminiClient.CharacterData data : chars) {
                Character character = new Character();
                character.setProject(project);
                character.setName(data.name());
                character.setImagePrompt(data.imagePrompt());
                character.setStatus(ItemStatus.TEXT_GENERATED);
                characterRepository.save(character);
            }

            projectRepository.finalizeStepSuccess(
                    projectId, Step.CHARACTER, result.interactionId(),
                    StepStatus.SUCCESS, StepStatus.RUNNING);
        } catch (Exception e) {
            log.error("CHARACTER step failed for project {}: {}", projectId, e.getMessage(), e);
            projectRepository.finalizeStepFail(
                    projectId, Step.CHARACTER, e.getMessage(),
                    StepStatus.FAIL, StepStatus.RUNNING);
        }
    }

    // ------------------------------------------------------------------ //
    //  POST /projects/{id}/portraits                                       //
    // ------------------------------------------------------------------ //

    public ProjectDetailResponse triggerPortraits(UUID projectId) {
        int rows = projectRepository.claimStep(
                projectId, Step.CHARACTER, Step.PORTRAIT, Instant.now(),
                StepStatus.RUNNING, StepStatus.SUCCESS, StepStatus.PENDING);

        if (rows == 0) {
            return handleClaimFailure(projectId, Step.PORTRAIT);
        }
        executePortraitsAsync(projectId);
        return toDetail(findProjectOrThrow(projectId));
    }

    @Async
    public void executePortraitsAsync(UUID projectId) {
        Project project = findProjectOrThrow(projectId);
        List<Character> characters = characterRepository.findByProjectOrderById(project);
        String lastInteractionId = project.getPreviousInteractionId();
        String failMessage = null;

        for (Character character : characters) {
            if (character.getStatus() == ItemStatus.DONE) {
                continue; // Skip already-done items on retry
            }

            characterRepository.updateStatus(character.getId(), ItemStatus.RUNNING);
            try {
                GeminiClient.Result<String> result = geminiClient.generatePortrait(
                        character.getName(),
                        character.getImagePrompt(),
                        lastInteractionId);

                characterRepository.updatePortraitDone(character.getId(), result.value(), ItemStatus.DONE);
                lastInteractionId = result.interactionId();
            } catch (Exception e) {
                log.error("PORTRAIT failed for character {} in project {}: {}",
                        character.getId(), projectId, e.getMessage(), e);
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
        } else {
            projectRepository.finalizeStepSuccess(
                    projectId, Step.PORTRAIT, lastInteractionId,
                    StepStatus.SUCCESS, StepStatus.RUNNING);
        }
    }

    // ------------------------------------------------------------------ //
    //  POST /projects/{id}/chapters                                        //
    // ------------------------------------------------------------------ //

    public ProjectDetailResponse triggerChapters(UUID projectId) {
        int rows = projectRepository.claimStep(
                projectId, Step.PORTRAIT, Step.CHAPTER, Instant.now(),
                StepStatus.RUNNING, StepStatus.SUCCESS, StepStatus.PENDING);

        if (rows == 0) {
            return handleClaimFailure(projectId, Step.CHAPTER);
        }
        executeChaptersAsync(projectId);
        return toDetail(findProjectOrThrow(projectId));
    }

    @Async
    public void executeChaptersAsync(UUID projectId) {
        Project project = findProjectOrThrow(projectId);
        try {
            String bookText = readBookText(project);
            GeminiClient.Result<List<GeminiClient.ChapterData>> result =
                    geminiClient.generateChapters(bookText, project.getStyle(), project.getPreviousInteractionId());

            // Cap at 1 — truncate silently if Gemini returns more
            List<GeminiClient.ChapterData> chaps = result.value().stream().limit(1).toList();

            for (GeminiClient.ChapterData data : chaps) {
                Chapter chapter = new Chapter();
                chapter.setProject(project);
                chapter.setIllustrationPrompt(data.illustrationPrompt());
                chapter.setStatus(ItemStatus.TEXT_GENERATED);
                chapterRepository.save(chapter);
            }

            projectRepository.finalizeStepSuccess(
                    projectId, Step.CHAPTER, result.interactionId(),
                    StepStatus.SUCCESS, StepStatus.RUNNING);
        } catch (Exception e) {
            log.error("CHAPTER step failed for project {}: {}", projectId, e.getMessage(), e);
            projectRepository.finalizeStepFail(
                    projectId, Step.CHAPTER, e.getMessage(),
                    StepStatus.FAIL, StepStatus.RUNNING);
        }
    }

    // ------------------------------------------------------------------ //
    //  POST /projects/{id}/illustrations                                   //
    // ------------------------------------------------------------------ //

    public ProjectDetailResponse triggerIllustrations(UUID projectId) {
        int rows = projectRepository.claimStep(
                projectId, Step.CHAPTER, Step.ILLUSTRATION, Instant.now(),
                StepStatus.RUNNING, StepStatus.SUCCESS, StepStatus.PENDING);

        if (rows == 0) {
            return handleClaimFailure(projectId, Step.ILLUSTRATION);
        }
        executeIllustrationsAsync(projectId);
        return toDetail(findProjectOrThrow(projectId));
    }

    @Async
    public void executeIllustrationsAsync(UUID projectId) {
        Project project = findProjectOrThrow(projectId);
        List<Chapter> chapters = chapterRepository.findByProjectOrderById(project);
        String lastInteractionId = project.getPreviousInteractionId();
        String failMessage = null;

        for (Chapter chapter : chapters) {
            if (chapter.getStatus() == ItemStatus.DONE) {
                continue; // Skip already-done items on retry
            }

            chapterRepository.updateStatus(chapter.getId(), ItemStatus.RUNNING);
            try {
                GeminiClient.Result<String> result = geminiClient.generateIllustration(
                        chapter.getIllustrationPrompt(),
                        lastInteractionId);

                chapterRepository.updateIllustrationDone(chapter.getId(), result.value(), ItemStatus.DONE);
                lastInteractionId = result.interactionId();
            } catch (Exception e) {
                log.error("ILLUSTRATION failed for chapter {} in project {}: {}",
                        chapter.getId(), projectId, e.getMessage(), e);
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
        } else {
            projectRepository.finalizeStepSuccess(
                    projectId, Step.ILLUSTRATION, lastInteractionId,
                    StepStatus.SUCCESS, StepStatus.RUNNING);
            projectRepository.completeProject(projectId, ProjectStatus.DONE);
        }
    }

    // ------------------------------------------------------------------ //
    //  POST /projects/{id}/retry                                           //
    // ------------------------------------------------------------------ //

    public RetryResponse retry(UUID projectId) {
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
            return new RetryResponse(toDetail(findProjectOrThrow(projectId)), "FAILED");
        }

        // Try stuck-RUNNING path
        Instant timeoutBefore = Instant.now().minus(timeoutSeconds, ChronoUnit.SECONDS);
        int stuckRows = projectRepository.recoverStuckStep(
                projectId, currentStep, timeoutBefore,
                StepStatus.PENDING, StepStatus.RUNNING);
        if (stuckRows == 1) {
            return new RetryResponse(toDetail(findProjectOrThrow(projectId)), "STUCK_TIMEOUT");
        }

        // Neither path matched — project is not in a retryable state
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
            return toDetail(project);
        }

        if (project.getStep() == thisStep && project.getStepStatus() == StepStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Step '" + thisStep + "' is already running. Poll GET /api/v1/{id} for current status.");
        }

        // Wrong order or any other mismatch — distinct message from the RUNNING case
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Step '" + thisStep + "' is not ready. Current state: step=" + project.getStep() +
                ", stepStatus=" + project.getStepStatus() + ". Complete prior steps first.");
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    private Project findProjectOrThrow(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Project not found: " + projectId));
    }

    private String readBookText(Project project) {
        try {
            return Files.readString(Path.of(project.getBookTextPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
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
