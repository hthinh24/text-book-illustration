package vn.hungthinh.text_book_illustration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hungthinh.text_book_illustration.entity.Project;
import vn.hungthinh.text_book_illustration.entity.User;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByUser(User user);
}
