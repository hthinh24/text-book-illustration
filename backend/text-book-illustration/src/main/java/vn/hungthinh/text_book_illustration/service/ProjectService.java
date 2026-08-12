package vn.hungthinh.text_book_illustration.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.hungthinh.text_book_illustration.config.AppProperties;
import vn.hungthinh.text_book_illustration.dto.response.CharacterResponse;
import vn.hungthinh.text_book_illustration.dto.response.ChapterResponse;
import vn.hungthinh.text_book_illustration.dto.response.ProjectDetailResponse;
import vn.hungthinh.text_book_illustration.dto.response.ProjectSummaryResponse;
import vn.hungthinh.text_book_illustration.entity.Project;
import vn.hungthinh.text_book_illustration.entity.User;
import vn.hungthinh.text_book_illustration.repository.CharacterRepository;
import vn.hungthinh.text_book_illustration.repository.ChapterRepository;
import vn.hungthinh.text_book_illustration.repository.ProjectRepository;
import vn.hungthinh.text_book_illustration.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final CharacterRepository characterRepository;
    private final ChapterRepository chapterRepository;
    private final AppProperties appProperties;
    private final TransactionTemplate transactionTemplate;

    // ------------------------------------------------------------------ //
    //  POST /api/v1/init-project                                           //
    // ------------------------------------------------------------------ //
    public ProjectDetailResponse initProject(UUID userId,
                                             String title,
                                             String text,
                                             MultipartFile file) {
        log.info("[Service] Starting initProject: userId={}, title={}", userId, title);

        boolean hasText = text != null && !text.isBlank();
        boolean hasFile = file != null && !file.isEmpty();

        if (hasText == hasFile) {
            log.warn("[Service] initProject failed validation: userId={}, title={}, hasText={}, hasFile={}",
                    userId, title, hasText, hasFile);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Exactly one of 'text' or 'file' must be provided, not both and not neither.");
        }

        UUID projectId = UUID.randomUUID();
        String bookText = hasText ? text : readMultipart(file);
        Path bookTextPath = writeBookText(projectId, bookText);

        try {
            ProjectDetailResponse response = transactionTemplate.execute(status -> {
                User user = userRepository.getReferenceById(userId);
                Project project = new Project();
                project.setId(projectId);
                project.setUser(user);
                project.setTitle(title);
                project.setBookTextPath(bookTextPath.toAbsolutePath().toString());
                projectRepository.save(project);
                return toDetail(project);
            });
            log.info("[Service] Finished initProject: projectId={}, userId={}", projectId, userId);
            return response;
        } catch (Exception e) {
            log.error("[Service] initProject failed: projectId={}, userId={}, error={}", projectId, userId, e.getMessage(), e);
            // DB transaction failed — delete the orphan file so disk and DB stay in sync.
            deleteQuietly(bookTextPath);
            throw e;
        }
    }


    // ------------------------------------------------------------------ //
    //  GET /api/v1/projects?userId={id}                                   //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> listProjects(UUID userId) {
        log.info("[Service] Starting listProjects: userId={}", userId);
        User user = findUserOrThrow(userId);
        List<ProjectSummaryResponse> result = projectRepository.findByUser(user).stream()
                .map(p -> new ProjectSummaryResponse(
                        p.getId(),
                        p.getTitle(),
                        p.getStatus(),
                        p.getStep(),
                        p.getStepStatus(),
                        p.getCreatedAt()))
                .toList();
        log.info("[Service] Finished listProjects: userId={}, count={}", userId, result.size());
        return result;
    }

    // ------------------------------------------------------------------ //
    //  GET /api/v1/{project_id}                                           //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProject(UUID projectId) {
        log.info("[Service] Starting getProject: projectId={}", projectId);
        Project project = findProjectOrThrow(projectId);
        ProjectDetailResponse result = toDetail(project);
        log.info("[Service] Finished getProject: projectId={}, step={}, stepStatus={}",
                projectId, result.step(), result.stepStatus());
        return result;
    }

    // ------------------------------------------------------------------ //
    //  GET /api/v1/files/{project_id}/book-text                           //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public String getBookText(UUID projectId) {
        log.info("[Service] Starting getBookText: projectId={}", projectId);
        Project project = findProjectOrThrow(projectId);
        try {
            String content = Files.readString(Path.of(project.getBookTextPath()), StandardCharsets.UTF_8);
            log.info("[Service] Finished getBookText: projectId={}, length={}", projectId, content.length());
            return content;
        } catch (IOException e) {
            log.error("[Service] getBookText failed: projectId={}, error={}", projectId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read book text file: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("[Service] User not found: userId={}", userId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
                });
    }

    private Project findProjectOrThrow(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> {
                    log.warn("[Service] Project not found: projectId={}", projectId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId);
                });
    }

    private String readMultipart(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Service] Failed to read multipart file: fileName={}, error={}", file.getOriginalFilename(), e.getMessage(), e);
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }

    /**
     * Write book text to {@code {fileStorageRoot}/{projectId}/book.txt}.
     *
     * @return the {@link Path} to the written file
     */
    private Path writeBookText(UUID projectId, String bookText) {
        try {
            Path dir = Path.of(appProperties.getFileStorageRoot(), projectId.toString());
            Files.createDirectories(dir);
            Path file = dir.resolve("book.txt");
            Files.writeString(file, bookText, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            log.error("[Service] Failed to write book text: projectId={}, error={}", projectId, e.getMessage(), e);
            throw new UncheckedIOException("Failed to write book text to disk", e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("[Service] Failed to delete file quietly: path={}, error={}", path, e.getMessage());
        }
    }

    private ProjectDetailResponse toDetail(Project project) {
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
