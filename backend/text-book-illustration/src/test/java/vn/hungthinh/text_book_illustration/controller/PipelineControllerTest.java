package vn.hungthinh.text_book_illustration.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import vn.hungthinh.text_book_illustration.dto.request.StyleRequest;
import vn.hungthinh.text_book_illustration.dto.response.ProjectDetailResponse;
import vn.hungthinh.text_book_illustration.dto.response.RetryResponse;
import vn.hungthinh.text_book_illustration.entity.ProjectStatus;
import vn.hungthinh.text_book_illustration.entity.Step;
import vn.hungthinh.text_book_illustration.entity.StepStatus;
import vn.hungthinh.text_book_illustration.service.PipelineService;

@WebMvcTest(PipelineController.class)
class PipelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PipelineService pipelineService;

    // ------------------------------------------------------------------ //
    //  /style                                                              //
    // ------------------------------------------------------------------ //

    @Test
    void triggerStyle_claimed_returns202() throws Exception {
        UUID id = UUID.randomUUID();
        ProjectDetailResponse running = buildResponse(id, Step.STYLE, StepStatus.RUNNING);
        when(pipelineService.triggerStyle(any(UUID.class), isNull())).thenReturn(running);

        mockMvc.perform(post("/api/v1/projects/{id}/style", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.step").value("STYLE"))
                .andExpect(jsonPath("$.stepStatus").value("RUNNING"));
    }

    @Test
    void triggerStyle_alreadySuccess_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        ProjectDetailResponse success = buildResponse(id, Step.STYLE, StepStatus.SUCCESS);
        when(pipelineService.triggerStyle(any(UUID.class), isNull())).thenReturn(success);

        mockMvc.perform(post("/api/v1/projects/{id}/style", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepStatus").value("SUCCESS"));
    }

    @Test
    void triggerStyle_withUserStyle_returns202() throws Exception {
        UUID id = UUID.randomUUID();
        ProjectDetailResponse running = buildResponse(id, Step.STYLE, StepStatus.RUNNING);
        when(pipelineService.triggerStyle(any(UUID.class), any(StyleRequest.class))).thenReturn(running);

        mockMvc.perform(post("/api/v1/projects/{id}/style", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"style\":\"watercolor\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void triggerStyle_alreadyRunning_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(pipelineService.triggerStyle(any(UUID.class), isNull()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "already running"));

        mockMvc.perform(post("/api/v1/projects/{id}/style", id))
                .andExpect(status().isConflict());
    }

    @Test
    void triggerStyle_wrongOrder_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(pipelineService.triggerStyle(any(UUID.class), isNull()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "wrong order"));

        mockMvc.perform(post("/api/v1/projects/{id}/style", id))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ //
    //  /character                                                          //
    // ------------------------------------------------------------------ //

    @Test
    void triggerCharacter_claimed_returns202() throws Exception {
        UUID id = UUID.randomUUID();
        when(pipelineService.triggerCharacter(any(UUID.class)))
                .thenReturn(buildResponse(id, Step.CHARACTER, StepStatus.RUNNING));

        mockMvc.perform(post("/api/v1/projects/{id}/character", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.step").value("CHARACTER"));
    }

    // ------------------------------------------------------------------ //
    //  /portraits                                                          //
    // ------------------------------------------------------------------ //

    @Test
    void triggerPortraits_claimed_returns202() throws Exception {
        UUID id = UUID.randomUUID();
        when(pipelineService.triggerPortraits(any(UUID.class)))
                .thenReturn(buildResponse(id, Step.PORTRAIT, StepStatus.RUNNING));

        mockMvc.perform(post("/api/v1/projects/{id}/portraits", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.step").value("PORTRAIT"));
    }

    // ------------------------------------------------------------------ //
    //  /chapters                                                           //
    // ------------------------------------------------------------------ //

    @Test
    void triggerChapters_claimed_returns202() throws Exception {
        UUID id = UUID.randomUUID();
        when(pipelineService.triggerChapters(any(UUID.class)))
                .thenReturn(buildResponse(id, Step.CHAPTER, StepStatus.RUNNING));

        mockMvc.perform(post("/api/v1/projects/{id}/chapters", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.step").value("CHAPTER"));
    }

    // ------------------------------------------------------------------ //
    //  /illustrations                                                      //
    // ------------------------------------------------------------------ //

    @Test
    void triggerIllustrations_claimed_returns202() throws Exception {
        UUID id = UUID.randomUUID();
        when(pipelineService.triggerIllustrations(any(UUID.class)))
                .thenReturn(buildResponse(id, Step.ILLUSTRATION, StepStatus.RUNNING));

        mockMvc.perform(post("/api/v1/projects/{id}/illustrations", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.step").value("ILLUSTRATION"));
    }

    // ------------------------------------------------------------------ //
    //  /retry                                                              //
    // ------------------------------------------------------------------ //

    @Test
    void retry_failedStep_returns200WithFailedReason() throws Exception {
        UUID id = UUID.randomUUID();
        ProjectDetailResponse project = buildResponse(id, Step.PORTRAIT, StepStatus.PENDING);
        when(pipelineService.retry(any(UUID.class)))
                .thenReturn(new RetryResponse(project, "FAILED"));

        mockMvc.perform(post("/api/v1/projects/{id}/retry", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retryReason").value("FAILED"))
                .andExpect(jsonPath("$.project.step").value("PORTRAIT"));
    }

    @Test
    void retry_stuckRunning_returns200WithStuckTimeoutReason() throws Exception {
        UUID id = UUID.randomUUID();
        ProjectDetailResponse project = buildResponse(id, Step.CHARACTER, StepStatus.PENDING);
        when(pipelineService.retry(any(UUID.class)))
                .thenReturn(new RetryResponse(project, "STUCK_TIMEOUT"));

        mockMvc.perform(post("/api/v1/projects/{id}/retry", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retryReason").value("STUCK_TIMEOUT"));
    }

    @Test
    void retry_notEligible_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(pipelineService.retry(any(UUID.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "not retryable"));

        mockMvc.perform(post("/api/v1/projects/{id}/retry", id))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private ProjectDetailResponse buildResponse(UUID id, Step step, StepStatus stepStatus) {
        return new ProjectDetailResponse(
                id,
                "Test Book",
                Instant.parse("2026-01-01T00:00:00Z"),
                ProjectStatus.DRAFT,
                step,
                stepStatus,
                null,
                null,
                List.of(),
                List.of());
    }
}
