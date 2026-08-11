package vn.hungthinh.text_book_illustration.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import vn.hungthinh.text_book_illustration.dto.response.ProjectDetailResponse;
import vn.hungthinh.text_book_illustration.entity.ProjectStatus;
import vn.hungthinh.text_book_illustration.entity.Step;
import vn.hungthinh.text_book_illustration.entity.StepStatus;
import vn.hungthinh.text_book_illustration.service.ProjectService;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @Test
    void getProject_happyPath_returns200WithEnvelope() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectDetailResponse response = new ProjectDetailResponse(
                projectId,
                "My Book",
                Instant.parse("2026-01-01T00:00:00Z"),
                ProjectStatus.DRAFT,
                Step.STYLE,
                StepStatus.PENDING,
                null,
                null,
                List.of(),
                List.of());

        when(projectService.getProject(projectId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.title").value("My Book"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.step").value("STYLE"))
                .andExpect(jsonPath("$.stepStatus").value("PENDING"))
                .andExpect(jsonPath("$.characters").isArray())
                .andExpect(jsonPath("$.chapters").isArray());
    }

    @Test
    void getProject_unknownId_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(projectService.getProject(unknownId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        mockMvc.perform(get("/api/v1/{projectId}", unknownId))
                .andExpect(status().isNotFound());
    }
}
