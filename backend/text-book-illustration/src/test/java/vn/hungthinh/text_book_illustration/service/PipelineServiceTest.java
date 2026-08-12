package vn.hungthinh.text_book_illustration.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import vn.hungthinh.text_book_illustration.config.AppProperties;
import vn.hungthinh.text_book_illustration.dto.response.ProjectDetailResponse;
import vn.hungthinh.text_book_illustration.dto.response.RetryResponse;
import vn.hungthinh.text_book_illustration.entity.Project;
import vn.hungthinh.text_book_illustration.entity.Step;
import vn.hungthinh.text_book_illustration.entity.StepStatus;
import vn.hungthinh.text_book_illustration.repository.ChapterRepository;
import vn.hungthinh.text_book_illustration.repository.CharacterRepository;
import vn.hungthinh.text_book_illustration.repository.ProjectRepository;

/**
 * Pure unit test — no Spring context.
 * Tests the claim-failure branching logic and retry branching in PipelineService.
 */
class PipelineServiceTest {

    private ProjectRepository projectRepository;
    private CharacterRepository characterRepository;
    private ChapterRepository chapterRepository;
    private GeminiClient geminiClient;
    private PipelineService pipelineService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        characterRepository = mock(CharacterRepository.class);
        chapterRepository = mock(ChapterRepository.class);
        geminiClient = mock(GeminiClient.class);

        AppProperties appProperties = new AppProperties();
        appProperties.setFileStorageRoot(System.getProperty("java.io.tmpdir"));
        appProperties.setStepTimeoutSeconds(180);
        appProperties.setMaxRetryCount(3);

        pipelineService = new PipelineService(
                projectRepository, characterRepository, chapterRepository,
                geminiClient, appProperties);
    }

    // ------------------------------------------------------------------ //
    //  triggerStyle — claim failure branching                              //
    // ------------------------------------------------------------------ //

    @Test
    void triggerStyle_claimFails_projectAlreadySuccess_returns200State() {
        UUID id = UUID.randomUUID();
        // Claim returns 0 rows — match any additional enum args with any()
        when(projectRepository.claimStyleStep(eq(id), any(), any(), any(), any())).thenReturn(0);
        // Re-query shows SUCCESS
        Project project = buildProject(id, Step.STYLE, StepStatus.SUCCESS);
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));
        when(characterRepository.findByProject(project)).thenReturn(java.util.List.of());
        when(chapterRepository.findByProject(project)).thenReturn(java.util.List.of());

        ProjectDetailResponse response = pipelineService.triggerStyle(id, null);

        assertThat(response.stepStatus()).isEqualTo(StepStatus.SUCCESS);
    }

    @Test
    void triggerStyle_claimFails_projectRunning_throws409() {
        UUID id = UUID.randomUUID();
        when(projectRepository.claimStyleStep(eq(id), any(), any(), any(), any())).thenReturn(0);
        Project project = buildProject(id, Step.STYLE, StepStatus.RUNNING);
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> pipelineService.triggerStyle(id, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void triggerCharacter_claimFails_wrongOrder_throws409WithDistinctMessage() {
        UUID id = UUID.randomUUID();
        // Claim CHARACTER returns 0 — STYLE not yet done
        when(projectRepository.claimStep(eq(id), eq(Step.STYLE), eq(Step.CHARACTER), any(), any(), any(), any()))
                .thenReturn(0);
        // Re-query: still on STYLE/PENDING (wrong order)
        Project project = buildProject(id, Step.STYLE, StepStatus.PENDING);
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> pipelineService.triggerCharacter(id))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    // Message must be distinct from the RUNNING-conflict message
                    assertThat(rse.getReason()).contains("not ready").doesNotContain("already running");
                });
    }

    // ------------------------------------------------------------------ //
    //  retry — branching                                                   //
    // ------------------------------------------------------------------ //

    @Test
    void retry_failedStep_returnsFailedReason() {
        UUID id = UUID.randomUUID();
        Project project = buildProject(id, Step.PORTRAIT, StepStatus.FAIL);
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));
        // FAIL retry succeeds — match enum params with any()
        when(projectRepository.retryFailedStep(eq(id), eq(Step.PORTRAIT), eq(3), any(), any())).thenReturn(1);
        // Re-query after retry
        Project retried = buildProject(id, Step.PORTRAIT, StepStatus.PENDING);
        when(projectRepository.findById(id))
                .thenReturn(Optional.of(project))   // first call (get current step)
                .thenReturn(Optional.of(retried));   // second call (build response)
        when(characterRepository.findByProject(any())).thenReturn(java.util.List.of());
        when(chapterRepository.findByProject(any())).thenReturn(java.util.List.of());

        RetryResponse response = pipelineService.retry(id);

        assertThat(response.retryReason()).isEqualTo("FAILED");
        assertThat(response.project().stepStatus()).isEqualTo(StepStatus.PENDING);
    }

    @Test
    void retry_stuckRunning_returnsStuckTimeoutReason() {
        UUID id = UUID.randomUUID();
        Project project = buildProject(id, Step.CHARACTER, StepStatus.RUNNING);
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));
        // FAIL retry returns 0 (not FAIL state)
        when(projectRepository.retryFailedStep(eq(id), eq(Step.CHARACTER), eq(3), any(), any())).thenReturn(0);
        // Stuck recovery succeeds
        when(projectRepository.recoverStuckStep(eq(id), eq(Step.CHARACTER), any(), any(), any())).thenReturn(1);
        // Re-query
        Project recovered = buildProject(id, Step.CHARACTER, StepStatus.PENDING);
        when(projectRepository.findById(id))
                .thenReturn(Optional.of(project))
                .thenReturn(Optional.of(recovered));
        when(characterRepository.findByProject(any())).thenReturn(java.util.List.of());
        when(chapterRepository.findByProject(any())).thenReturn(java.util.List.of());

        RetryResponse response = pipelineService.retry(id);

        assertThat(response.retryReason()).isEqualTo("STUCK_TIMEOUT");
    }

    @Test
    void retry_notEligible_throws409() {
        UUID id = UUID.randomUUID();
        Project project = buildProject(id, Step.STYLE, StepStatus.PENDING);
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));
        when(projectRepository.retryFailedStep(eq(id), eq(Step.STYLE), eq(3), any(), any())).thenReturn(0);
        when(projectRepository.recoverStuckStep(eq(id), eq(Step.STYLE), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> pipelineService.retry(id))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private Project buildProject(UUID id, Step step, StepStatus stepStatus) {
        Project p = new Project();
        p.setStep(step);
        p.setStepStatus(stepStatus);
        return p;
    }
}
