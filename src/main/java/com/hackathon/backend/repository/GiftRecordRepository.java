package com.hackathon.backend.repository;

import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.GiftRecordStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GiftRecordRepository extends JpaRepository<GiftRecord, Long> {

    /**
     * 목록 화면용 통합 검색. 모든 조건은 null이면 무시된다(동적 필터).
     * person/category를 fetch join해 목록 렌더링 시 N+1을 막는다.
     */
    @EntityGraph(attributePaths = {"person", "category"})
    @Query("""
            select r from GiftRecord r
            where r.user.username = :username
              and (:status is null or r.status = :status)
              and (:categoryId is null or r.category.id = :categoryId)
              and (:personId is null or r.person.id = :personId)
              and (:thanked is null or r.thanked = :thanked)
              and (:startDate is null or r.receivedDate >= :startDate)
              and (:endDate is null or r.receivedDate <= :endDate)
              and (:keyword is null
                   or lower(r.giftName) like lower(concat('%', :keyword, '%'))
                   or lower(r.occasion) like lower(concat('%', :keyword, '%'))
                   or lower(r.person.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<GiftRecord> search(@Param("username") String username,
                            @Param("status") GiftRecordStatus status,
                            @Param("categoryId") Long categoryId,
                            @Param("personId") Long personId,
                            @Param("thanked") Boolean thanked,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate,
                            @Param("keyword") String keyword,
                            Pageable pageable);

    @EntityGraph(attributePaths = {"person", "category"})
    Optional<GiftRecord> findByIdAndUser_Username(Long id, String username);

    @EntityGraph(attributePaths = {"person", "category"})
    List<GiftRecord> findByUser_UsernameAndStatusAndReceivedDateBetweenOrderByReceivedDateAsc(
            String username, GiftRecordStatus status, LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = {"person", "category"})
    List<GiftRecord> findByUser_UsernameAndPerson_IdOrderByReceivedDateDescIdDesc(String username, Long personId);

    @EntityGraph(attributePaths = {"person", "category"})
    List<GiftRecord> findByUser_UsernameOrderByReceivedDateDescIdDesc(String username);

    long countByUser_Username(String username);

    long countByUser_UsernameAndCreatedAtGreaterThanEqual(String username, LocalDateTime from);

    long countByUser_UsernameAndCategory_Id(String username, Long categoryId);

    /** 사람별 기록 개수 — [personId, count] 형태로 한 번에 집계 (사람 목록 N+1 방지) */
    @Query("""
            select r.person.id, count(r)
            from GiftRecord r
            where r.user.username = :username and r.person is not null
            group by r.person.id
            """)
    List<Object[]> countGroupedByPerson(@Param("username") String username);

    /** 카테고리별 기록 개수 — 필터 칩에 붙일 count를 한 방에 집계 */
    @Query("""
            select r.category.id, count(r)
            from GiftRecord r
            where r.user.username = :username and r.category is not null
            group by r.category.id
            """)
    List<Object[]> countGroupedByCategory(@Param("username") String username);
}
