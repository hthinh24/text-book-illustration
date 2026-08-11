package vn.hungthinh.text_book_illustration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hungthinh.text_book_illustration.entity.Chapter;
import vn.hungthinh.text_book_illustration.entity.Project;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    List<Chapter> findByProject(Project project);
}
