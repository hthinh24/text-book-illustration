package vn.hungthinh.text_book_illustration.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import vn.hungthinh.text_book_illustration.config.AppProperties;
import vn.hungthinh.text_book_illustration.entity.User;
import vn.hungthinh.text_book_illustration.repository.CharacterRepository;
import vn.hungthinh.text_book_illustration.repository.ChapterRepository;
import vn.hungthinh.text_book_illustration.repository.ProjectRepository;
import vn.hungthinh.text_book_illustration.repository.UserRepository;

/**
 * Pure unit test — no Spring context, no MockMvc.
 * Tests the "exactly one of text/file" business rule in ProjectService directly.
 * The validation fires before the TransactionTemplate is ever touched, so a bare
 * mock() with no stubbing is sufficient.
 */
class ProjectServiceTest {

    private UserRepository userRepository;
    private ProjectService projectService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        CharacterRepository characterRepository = mock(CharacterRepository.class);
        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

        AppProperties appProperties = new AppProperties();
        appProperties.setFileStorageRoot(System.getProperty("java.io.tmpdir"));

        projectService = new ProjectService(
                userRepository, projectRepository, characterRepository, chapterRepository,
                appProperties, transactionTemplate);

        userId = UUID.randomUUID();
        User user = new User();
        user.setEmail("alice@example.com");
        user.setName("Alice");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void initProject_bothTextAndFile_throwsBadRequest() {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);

        assertThatThrownBy(() -> projectService.initProject(userId, "My Book", "some text", file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void initProject_neitherTextNorFile_throwsBadRequest() {
        assertThatThrownBy(() -> projectService.initProject(userId, "My Book", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
