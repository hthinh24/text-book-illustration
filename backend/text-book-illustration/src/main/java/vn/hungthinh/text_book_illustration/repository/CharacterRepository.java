package vn.hungthinh.text_book_illustration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import vn.hungthinh.text_book_illustration.entity.Character;
import vn.hungthinh.text_book_illustration.entity.ItemStatus;
import vn.hungthinh.text_book_illustration.entity.Project;

public interface CharacterRepository extends JpaRepository<Character, UUID> {

    List<Character> findByProject(Project project);

    /** Stable ordering for multi-item loop — ensures consistent processing order on retry. */
    List<Character> findByProjectOrderById(Project project);

    /**
     * Update a character's status (PENDING → RUNNING → DONE/FAIL).
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Character c SET c.status = :status WHERE c.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") ItemStatus status);

    /**
     * Mark a character DONE and persist the generated portrait image path.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Character c SET c.status = :done, c.portraitImagePath = :path WHERE c.id = :id")
    int updatePortraitDone(
            @Param("id") UUID id,
            @Param("path") String path,
            @Param("done") ItemStatus done
    );
}
