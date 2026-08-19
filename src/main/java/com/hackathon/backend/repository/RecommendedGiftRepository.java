package com.hackathon.backend.repository;

import com.hackathon.backend.domain.RecommendedGift;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendedGiftRepository extends JpaRepository<RecommendedGift, Long> {

    @EntityGraph(attributePaths = {"person"})
    List<RecommendedGift> findByUser_UsernameAndPerson_IdOrderByDisplayOrderAsc(String username, Long personId);

    @EntityGraph(attributePaths = {"person"})
    List<RecommendedGift> findByUser_UsernameAndPersonIsNullOrderByDisplayOrderAsc(String username);

    void deleteByUser_UsernameAndPerson_Id(String username, Long personId);

    void deleteByUser_UsernameAndPerson_IdIn(String username, List<Long> personIds);

    void deleteByUser_UsernameAndPersonIsNull(String username);
}
