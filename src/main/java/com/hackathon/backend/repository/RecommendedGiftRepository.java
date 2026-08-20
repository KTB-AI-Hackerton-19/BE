package com.hackathon.backend.repository;

import com.hackathon.backend.domain.RecommendationSlot;
import com.hackathon.backend.domain.RecommendedGift;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendedGiftRepository extends JpaRepository<RecommendedGift, Long> {

    @EntityGraph(attributePaths = {"person"})
    List<RecommendedGift> findByUser_UsernameAndPerson_IdAndSlotOrderByDisplayOrderAsc(
            String username, Long personId, RecommendationSlot slot);

    @EntityGraph(attributePaths = {"person"})
    List<RecommendedGift> findByUser_UsernameAndPersonIsNullAndSlotOrderByDisplayOrderAsc(
            String username, RecommendationSlot slot);

    void deleteByUser_UsernameAndPerson_Id(String username, Long personId);

    void deleteByUser_UsernameAndPerson_IdInAndSlot(String username, List<Long> personIds, RecommendationSlot slot);

    void deleteByUser_UsernameAndPersonIsNullAndSlot(String username, RecommendationSlot slot);

    /**
     * 추천을 '다시 만들어야 함'으로만 표시한다. 지우지 않는 이유는 {@code RecommendedGift#stale} 참고 —
     * 지우면 다음 화면 진입이 AI를 기다리게 된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RecommendedGift g set g.stale = true "
            + "where g.user.username = :username and g.slot = :slot and g.person.id in :personIds")
    int markStale(@Param("username") String username, @Param("personIds") List<Long> personIds,
                  @Param("slot") RecommendationSlot slot);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RecommendedGift g set g.stale = true "
            + "where g.user.username = :username and g.slot = :slot and g.person is null")
    int markStaleForGeneral(@Param("username") String username, @Param("slot") RecommendationSlot slot);

    void deleteByUser_UsernameAndPerson_IdIn(String username, List<Long> personIds);

    void deleteByUser_UsernameAndPersonIsNull(String username);

    /** 회원탈퇴 시 그 사용자의 추천을 전부 지운다. */
    void deleteByUser_Username(String username);
}
