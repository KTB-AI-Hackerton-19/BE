package com.hackathon.backend.service;

import com.hackathon.backend.domain.ReminderStatus;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.dto.reminder.ReminderTaskResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.security.SecurityUtils;
import com.hackathon.backend.sse.ReminderSseRegistry;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderTaskService {

    private final ReminderTaskRepository reminderTaskRepository;
    private final ReminderSseRegistry sseRegistry;

    public ReminderTaskService(ReminderTaskRepository reminderTaskRepository, ReminderSseRegistry sseRegistry) {
        this.reminderTaskRepository = reminderTaskRepository;
        this.sseRegistry = sseRegistry;
    }

    /**
     * 답례 알림 목록. 기본은 오늘 이후(다가오는) 것만, includePast=true면 지난 알림까지 전부.
     * limit을 주면 앞에서 그만큼만 잘라서 반환한다(홈 화면 위젯용).
     */
    @Transactional(readOnly = true)
    public List<ReminderTaskResponse> list(boolean includePast, Integer limit) {
        String username = SecurityUtils.getCurrentUsername();
        LocalDate today = LocalDate.now();

        List<ReminderTaskResponse> all = (includePast
                ? reminderTaskRepository.findByUser_UsernameOrderByScheduledAtAsc(username)
                : reminderTaskRepository.findByUser_UsernameAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(username, today))
                .stream()
                .map(task -> ReminderTaskResponse.of(task, today))
                .toList();

        if (limit == null || limit <= 0 || limit >= all.size()) {
            return all;
        }
        return all.subList(0, limit);
    }

    /**
     * 발송됐지만 아직 화면에 안 띄운 알림. 프론트가 주기적으로 호출해 토스트로 표시한다.
     * 표시한 뒤에는 markDelivered로 알려주어야 같은 알림이 다시 뜨지 않는다.
     */
    @Transactional(readOnly = true)
    public List<ReminderTaskResponse> listUndelivered() {
        String username = SecurityUtils.getCurrentUsername();
        LocalDate today = LocalDate.now();
        return reminderTaskRepository
                .findByUser_UsernameAndStatusAndDeliveredFalseOrderByScheduledAtAsc(username, ReminderStatus.SENT)
                .stream()
                .map(task -> ReminderTaskResponse.of(task, today))
                .toList();
    }

    /** 토스트로 띄운 알림을 표시 완료 처리한다. */
    @Transactional
    public void markDelivered(Long id) {
        String username = SecurityUtils.getCurrentUsername();
        ReminderTask task = reminderTaskRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.REMINDER_NOT_FOUND));
        task.markDelivered();
    }

    /**
     * 기한이 된 내 알림을 즉시 발송 처리하고 SSE로 밀어준다.
     * 스케줄러를 기다리지 않고 바로 확인할 때 사용한다. 발송된 건수를 반환한다.
     */
    @Transactional
    public int dispatchNow() {
        String username = SecurityUtils.getCurrentUsername();
        List<ReminderTask> due = reminderTaskRepository
                .findByUser_UsernameAndStatusAndScheduledAtLessThanEqual(
                        username, ReminderStatus.PENDING, LocalDate.now());
        List<ReminderTaskResponse> sent = markSentAndPush(username, due);
        return sent.size();
    }

    /** 특정 알림 하나를 예정일과 무관하게 즉시 발송한다 (시연용). */
    @Transactional
    public ReminderTaskResponse dispatchOne(Long id) {
        String username = SecurityUtils.getCurrentUsername();
        ReminderTask task = reminderTaskRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.REMINDER_NOT_FOUND));
        return markSentAndPush(username, List.of(task)).getFirst();
    }

    /** SENT로 바꾸고 접속 중인 클라이언트에 SSE로 전달한다. */
    public List<ReminderTaskResponse> markSentAndPush(String username, List<ReminderTask> tasks) {
        LocalDate today = LocalDate.now();
        tasks.forEach(ReminderTask::markSent);
        List<ReminderTaskResponse> responses = tasks.stream()
                .map(task -> ReminderTaskResponse.of(task, today))
                .toList();
        sseRegistry.push(username, responses);
        return responses;
    }

    /** SSE 연결. 접속하는 순간 아직 못 본 알림이 있으면 먼저 흘려보낸다. */
    @Transactional(readOnly = true)
    public SseEmitter subscribe() {
        String username = SecurityUtils.getCurrentUsername();
        SseEmitter emitter = sseRegistry.subscribe(username);
        sseRegistry.push(username, listUndelivered());
        return emitter;
    }
}
