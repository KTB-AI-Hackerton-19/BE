package com.hackathon.backend.repository;

import com.hackathon.backend.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUser_UsernameOrderByDisplayOrderAscIdAsc(String username);

    List<Category> findByUser_UsernameAndActiveTrueOrderByDisplayOrderAscIdAsc(String username);

    Optional<Category> findByUser_UsernameAndName(String username, String name);

    Optional<Category> findByIdAndUser_Username(Long id, String username);

    boolean existsByUser_UsernameAndName(String username, String name);

    void deleteByUser_Username(String username);
}
