package com.hackathon.backend.repository;

import com.hackathon.backend.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByDisplayOrderAscIdAsc();

    List<Category> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    Optional<Category> findByName(String name);

    boolean existsByName(String name);
}
