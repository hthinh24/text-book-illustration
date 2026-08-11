package vn.hungthinh.text_book_illustration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hungthinh.text_book_illustration.entity.Character;
import vn.hungthinh.text_book_illustration.entity.Project;

public interface CharacterRepository extends JpaRepository<Character, UUID> {

    List<Character> findByProject(Project project);
}
