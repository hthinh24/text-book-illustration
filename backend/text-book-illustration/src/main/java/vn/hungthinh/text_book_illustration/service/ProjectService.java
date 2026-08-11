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
        boolean hasText = text != null && !text.isBlank();
        boolean hasFile = file != null && !file.isEmpty();

        if (hasText == hasFile) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Exactly one of 'text' or 'file' must be provided, not both and not neither.");
        }

        UUID projectId = UUID.randomUUID();
        String bookText = hasText ? text : readMultipart(file);
        Path bookTextPath = writeBookText(projectId, bookText);

        try {
            return transactionTemplate.execute(status -> {
                User user = userRepository.getReferenceById(userId);
                Project project = new Project();
                project.setId(projectId);
                project.setUser(user);
                project.setTitle(title);
                project.setBookTextPath(bookTextPath.toAbsolutePath().toString());
                projectRepository.save(project);
                return toDetail(project);
            });
        } catch (Exception e) {
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
        User user = findUserOrThrow(userId);
        return projectRepository.findByUser(user).stream()
                .map(p -> new ProjectSummaryResponse(
                        p.getId(),
                        p.getTitle(),
                        p.getStatus(),
                        p.getStep(),
                        p.getStepStatus(),
                        p.getCreatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ //
    //  GET /api/v1/{project_id}                                           //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProject(UUID projectId) {
        Project project = findProjectOrThrow(projectId);
        return toDetail(project);
    }

    // ------------------------------------------------------------------ //
    //  GET /api/v1/files/{project_id}/book-text                           //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public String getBookText(UUID projectId) {
        Project project = findProjectOrThrow(projectId);
        try {
            return Files.readString(Path.of(project.getBookTextPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read book text file: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found: " + userId));
    }

    private Project findProjectOrThrow(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Project not found: " + projectId));
    }

    private String readMultipart(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
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
            throw new UncheckedIOException("Failed to write book text to disk", e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort: log this in a real system but don't mask the original exception
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
