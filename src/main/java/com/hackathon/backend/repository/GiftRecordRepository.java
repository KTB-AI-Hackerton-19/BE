package com.hackathon.backend.repository;

import com.hackathon.backend.domain.EventCategory;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.RecordType;
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
     *
     * <p><b>person/category는 반드시 명시적 left join으로 걸어야 한다.</b> where에서 {@code r.person.id}처럼
     * 경로로 바로 쓰면 JPQL이 암시적 INNER JOIN을 만들어, <b>보낸 사람이 아직 없는 기록(AI 추출 직후 DRAFT)이
     * 필터와 무관하게 목록에서 통째로 빠진다.</b> 사진 한 장에서 여러 명을 뽑으면 대부분 이 상태라,
     * "카테고리 카드는 6건인데 목록엔 2건만 보인다" 같은 불일치로 나타난다.</p>
     */
    /** 아직 사람에 연결되지 않은 기록들. 같은 이름을 한 번에 묶어줄 때 후보로 쓴다. */
    @EntityGraph(attributePaths = {"person", "category"})
    List<GiftRecord> findByUser_UsernameAndPersonIsNull(String username);

    @EntityGraph(attributePaths = {"person", "category"})
    @Query("""
            select r from GiftRecord r
            left join r.person p
            left join r.category c
            where r.user.username = :username
              and (:status is null or r.status = :status)
              and (:categoryId is null or c.id = :categoryId)
              and (:personId is null or p.id = :personId)
              and (:thanked is null or r.thanked = :thanked)
              and (:recordType is null or r.recordType = :recordType)
              and (:allEventCategories = true or r.eventCategory in :eventCategories)
              and (:startDate is null or r.receivedDate >= :startDate)
              and (:endDate is null or r.receivedDate <= :endDate)
              and (:personName is null
                   or lower(p.name) like lower(concat('%', :personName, '%'))
                   or lower(r.guestName) like lower(concat('%', :personName, '%'))
                   or lower(r.extractedSenderName) like lower(concat('%', :personName, '%')))
              and (:keyword is null
                   or lower(r.giftName) like lower(concat('%', :keyword, '%'))
                   or lower(r.occasion) like lower(concat('%', :keyword, '%'))
                   or lower(p.name) like lower(concat('%', :keyword, '%'))
                   or lower(r.guestName) like lower(concat('%', :keyword, '%'))
                   or lower(r.extractedSenderName) like lower(concat('%', :keyword, '%')))
            """)
    Page<GiftRecord> search(@Param("username") String username,
                            @Param("status") GiftRecordStatus status,
                            @Param("categoryId") Long categoryId,
                            @Param("personId") Long personId,
                            @Param("thanked") Boolean thanked,
                            @Param("recordType") RecordType recordType,
                            @Param("allEventCategories") boolean allEventCategories,
                            @Param("eventCategories") List<EventCategory> eventCategories,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate,
                            @Param("personName") String personName,
                            @Param("keyword") String keyword,
                            Pageable pageable);

    @EntityGraph(attributePaths = {"person", "category"})
    Optional<GiftRecord> findByIdAndUser_Username(Long id, String username);

    List<GiftRecord> findByUser_UsernameAndPerson_IdIn(String username, List<Long> personIds);

    /** 다중 삭제용. 다른 사용자의 id가 섞여 오면 조회 단계에서 걸러진다. */
    List<GiftRecord> findByIdInAndUser_Username(List<Long> ids, String username);

    @EntityGraph(attributePaths = {"person", "category"})
    List<GiftRecord> findByUser_UsernameAndStatusAndReceivedDateBetweenOrderByReceivedDateAsc(
            String username, GiftRecordStatus status, LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = {"person", "category"})
    List<GiftRecord> findByUser_UsernameAndPerson_IdOrderByReceivedDateDescIdDesc(String username, Long personId);

    /** 사람 상세의 타임라인. 한 사람에게 여러 번 받을 수 있어 페이지로 끊는다. */
    @EntityGraph(attributePaths = {"person", "category"})
    Page<GiftRecord> findByUser_UsernameAndPerson_IdOrderByReceivedDateDescIdDesc(
            String username, Long personId, Pageable pageable);

    @EntityGraph(attributePaths = {"person", "category"})
    List<GiftRecord> findByUser_UsernameOrderByReceivedDateDescIdDesc(String username);

    long countByUser_Username(String username);

    /** 대시보드 통계용. 확정된 기록만 센다(사진만 올리고 취소한 DRAFT가 숫자에 끼면 안 된다). */
    long countByUser_UsernameAndStatus(String username, GiftRecordStatus status);

    long countByUser_UsernameAndStatusAndCreatedAtGreaterThanEqual(
            String username, GiftRecordStatus status, LocalDateTime from);

    long countByUser_UsernameAndCreatedAtGreaterThanEqual(String username, LocalDateTime from);

    long countByUser_UsernameAndCategory_Id(String username, Long categoryId);

    /** 카테고리를 삭제할 때 그 카테고리로 저장된 기록을 "기타"로 옮기기 위해 조회한다. */
    List<GiftRecord> findByUser_UsernameAndCategory_Id(String username, Long categoryId);

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

    /**
     * 카테고리별 집계 — [categoryId, 건수, 금액합, 가장 최근 받은 날짜].
     *
     * <p>경조사 탭에서 "내 결혼식 · 32명 · 1,240,000원" 카드를 그리는 데 쓴다.
     * 최근 날짜는 이벤트 카드 정렬(최신 이벤트가 위)에도 쓰인다.</p>
     */
    @Query("""
            select r.category.id, count(r), coalesce(sum(r.amount), 0), max(r.receivedDate)
            from GiftRecord r
            where r.user.username = :username and r.category is not null
            group by r.category.id
            """)
    List<Object[]> aggregateByCategory(@Param("username") String username);

    /** 회원탈퇴 시 그 사용자의 기록을 전부 지운다. */
    void deleteByUser_Username(String username);
}
