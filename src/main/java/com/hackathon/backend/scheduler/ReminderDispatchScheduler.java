package com.hackathon.backend.scheduler;

import com.hackathon.backend.domain.ReminderStatus;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.service.ReminderTaskService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReminderDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderDispatchScheduler.class);
    private static final int BATCH_SIZE = 200;

    private final ReminderTaskRepository reminderTaskRepository;
    private final ReminderTaskService reminderTaskService;

    public ReminderDispatchScheduler(ReminderTaskRepository reminderTaskRepository,
                                      ReminderTaskService reminderTaskService) {
        this.reminderTaskRepository = reminderTaskRepository;
        this.reminderTaskService = reminderTaskService;
    }

    /**
     * 예정일이 된 답례 알림을 발송 처리하고, 접속 중인 사용자에게는 SSE로 즉시 밀어준다.
     * 접속 중이 아니면 delivered=false로 남아 다음 접속 때 전달된다.
     *
     * 오전 9시~밤 9시 매시 정각에 돈다. reminderDate에 시각이 없어 발송 시각은 이 cron이 정하는데,
     * 자정에 돌리면 새벽에 알림이 가버리므로 아침 9시를 기준으로 잡았다.
     * PENDING인 것만 집어가므로 여러 번 돌아도 중복 발송되지 않고(멱등),
     * 9시에 서버가 꺼져 있었더라도 다음 정각에 자동으로 복구된다.
     */
    @Scheduled(cron = "0 0 9-21 * * *")
    @Transactional
    public void dispatchDueReminders() {
        Pageable page = PageRequest.of(0, BATCH_SIZE);
        List<ReminderTask> due = reminderTaskRepository
                .findByStatusAndScheduledAtLessThanEqual(ReminderStatus.PENDING, LocalDate.now(), page)
                .getContent();

        if (due.isEmpty()) {
            return;
        }

        // 사용자별로 묶어서 각자에게만 전달한다
        Map<String, List<ReminderTask>> byUser = due.stream()
                .collect(Collectors.groupingBy(task -> task.getUser().getUsername()));

        byUser.forEach(reminderTaskService::markSentAndPush);
        log.info("답례 알림 {}건 발송 ({}명)", due.size(), byUser.size());
    }
}
