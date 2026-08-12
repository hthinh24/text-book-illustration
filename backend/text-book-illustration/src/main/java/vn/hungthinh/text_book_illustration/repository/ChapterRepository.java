package vn.hungthinh.text_book_illustration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import vn.hungthinh.text_book_illustration.entity.Chapter;
import vn.hungthinh.text_book_illustration.entity.ItemStatus;
import vn.hungthinh.text_book_illustration.entity.Project;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    List<Chapter> findByProject(Project project);

    /** Stable ordering for multi-item loop — ensures consistent processing order on retry. */
    List<Chapter> findByProjectOrderById(Project project);

    /**
     * Update a chapter's status (PENDING → RUNNING → DONE/FAIL).
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Chapter c SET c.status = :status WHERE c.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") ItemStatus status);

    /**
     * Mark a chapter DONE and persist the generated illustration image path.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Chapter c SET c.status = :done, c.illustrationImagePath = :path WHERE c.id = :id")
    int updateIllustrationDone(
            @Param("id") UUID id,
            @Param("path") String path,
            @Param("done") ItemStatus done
    );
}
