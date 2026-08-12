package vn.hungthinh.text_book_illustration.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.hungthinh.text_book_illustration.dto.response.ProjectDetailResponse;
import vn.hungthinh.text_book_illustration.dto.response.ProjectSummaryResponse;
import vn.hungthinh.text_book_illustration.service.ProjectService;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Slf4j
public class ProjectController {

    private final ProjectService projectService;

    /**
     * POST /api/v1/projects/init-project  (multipart/form-data)
     * Fields: userId (UUID), title (String), text (String, optional), file (.txt, optional)
     * Exactly one of text/file must be present — enforced in the service layer.
     */
    @PostMapping(value = "/init-project", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectDetailResponse> initProject(
            @RequestParam UUID userId,
            @RequestParam String title,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) MultipartFile file) {
        log.info("[Controller] initProject called: userId={}, title={}, hasText={}, hasFile={}",
                userId, title, text != null && !text.isBlank(), file != null && !file.isEmpty());
        return ResponseEntity.ok(projectService.initProject(userId, title, text, file));
    }

    /**
     * GET /api/v1/projects?userId={id}
     */
    @GetMapping("")
    public ResponseEntity<List<ProjectSummaryResponse>> listProjects(@RequestParam UUID userId) {
        log.info("[Controller] listProjects called: userId={}", userId);
        return ResponseEntity.ok(projectService.listProjects(userId));
    }

    /**
     * GET /api/v1/projects/{project_id}
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectDetailResponse> getProject(@PathVariable UUID projectId) {
        log.info("[Controller] getProject called: projectId={}", projectId);
        return ResponseEntity.ok(projectService.getProject(projectId));
    }

    /**
     * GET /api/v1/projects/{project_id}/files/book-text
     * Returns the raw UTF-8 content of the uploaded book text.
     */
    @GetMapping(value = "/{projectId}/files/book-text", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getBookText(@PathVariable UUID projectId) {
        log.info("[Controller] getBookText called: projectId={}", projectId);
        return ResponseEntity.ok(projectService.getBookText(projectId));
    }
}
