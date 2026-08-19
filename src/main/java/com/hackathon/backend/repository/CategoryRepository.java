package com.hackathon.backend.repository;

import com.hackathon.backend.domain.Category;
import com.hackathon.backend.domain.GiftKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUser_UsernameOrderByDisplayOrderAscIdAsc(String username);

    List<Category> findByUser_UsernameAndActiveTrueOrderByDisplayOrderAscIdAsc(String username);

    /** 탭(선물/경조사)별 조회. kinds에 경사+조사를 넣으면 "경조사 탭" 전체가 나온다. */
    List<Category> findByUser_UsernameAndKindInOrderByDisplayOrderAscIdAsc(String username, List<GiftKind> kinds);

    List<Category> findByUser_UsernameAndKindInAndActiveTrueOrderByDisplayOrderAscIdAsc(
            String username, List<GiftKind> kinds);

    Optional<Category> findByUser_UsernameAndName(String username, String name);

    Optional<Category> findByIdAndUser_Username(Long id, String username);

    boolean existsByUser_UsernameAndName(String username, String name);

    void deleteByUser_Username(String username);
}
