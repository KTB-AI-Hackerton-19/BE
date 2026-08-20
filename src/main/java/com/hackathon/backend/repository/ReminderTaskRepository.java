package com.hackathon.backend.repository;

import com.hackathon.backend.domain.ReminderStatus;
import com.hackathon.backend.domain.ReminderTask;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface ReminderTaskRepository extends JpaRepository<ReminderTask, Long> {

    /**
     * 그 회원의 모든 구글 일정 연결을 끊는다(행은 남기고 id만 비운다).
     *
     * <p>다른 구글 계정으로 다시 연동했을 때 필요하다. 기존 eventId는 <b>이전 계정의</b> 일정을 가리키므로
     * 그대로 두면 새 계정에 없는 일정을 수정하려 해서 동기화가 조용히 실패한다.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ReminderTask r set r.googleEventId = null, r.googleHtmlLink = null "
            + "where r.user.username = :username and r.googleEventId is not null")
    int clearGoogleEventsOf(@Param("username") String username);


    @EntityGraph(attributePaths = {"person", "giftRecord", "giftRecord.category"})
    List<ReminderTask> findByUser_UsernameOrderByScheduledAtAsc(String username);

    @EntityGraph(attributePaths = {"person", "giftRecord", "giftRecord.category"})
    List<ReminderTask> findByUser_UsernameAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(
            String username, LocalDate from);

    @EntityGraph(attributePaths = {"person", "giftRecord", "giftRecord.category"})
    List<ReminderTask> findByUser_UsernameAndScheduledAtBetweenOrderByScheduledAtAsc(
            String username, LocalDate start, LocalDate end);

    /** 답례 날짜가 가장 가까운 사람 순으로 훑을 때 사용 (선물 추천의 자동 대상 선정). */
    @EntityGraph(attributePaths = {"person"})
    List<ReminderTask> findByUser_UsernameAndStatusOrderByScheduledAtAsc(String username, ReminderStatus status);

    Optional<ReminderTask> findByGiftRecord_Id(Long giftRecordId);

    void deleteByGiftRecord_Id(Long giftRecordId);

    long deleteByGiftRecord_IdIn(List<Long> giftRecordIds);

    long deleteByPerson_IdIn(List<Long> personIds);

    long countByUser_UsernameAndScheduledAtGreaterThanEqual(String username, LocalDate from);

    Page<ReminderTask> findByStatusAndScheduledAtLessThanEqual(ReminderStatus status, LocalDate date, Pageable pageable);

    /** 발송은 됐지만 아직 화면에 띄우지 않은 알림 — 프론트가 토스트로 표시할 대상 */
    @EntityGraph(attributePaths = {"person", "giftRecord", "giftRecord.category"})
    List<ReminderTask> findByUser_UsernameAndStatusAndDeliveredFalseOrderByScheduledAtAsc(
            String username, ReminderStatus status);

    @EntityGraph(attributePaths = {"person", "giftRecord", "giftRecord.category"})
    Optional<ReminderTask> findByIdAndUser_Username(Long id, String username);

    @EntityGraph(attributePaths = {"person", "giftRecord", "giftRecord.category"})
    List<ReminderTask> findByUser_UsernameAndStatusAndScheduledAtLessThanEqual(
            String username, ReminderStatus status, LocalDate date);

    /** 회원탈퇴 시 그 사용자의 답례 알림을 전부 지운다. */
    void deleteByUser_Username(String username);
}
