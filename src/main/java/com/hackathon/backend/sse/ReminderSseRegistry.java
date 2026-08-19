package com.hackathon.backend.sse;

import com.hackathon.backend.dto.reminder.ReminderTaskResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 사용자별 SSE 연결을 들고 있다가 답례 알림이 발송되면 그 사용자에게 밀어준다.
 * 인스턴스 1대 기준의 인메모리 구현 — 여러 대로 늘리면 공유 브로커가 필요하다.
 */
@Component
public class ReminderSseRegistry {

    private static final Logger log = LoggerFactory.getLogger(ReminderSseRegistry.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;   // 30분

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String username) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(username, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(username, emitter));
        emitter.onTimeout(() -> remove(username, emitter));
        emitter.onError(e -> remove(username, emitter));

        // 연결 직후 한 번 보내주지 않으면 프록시가 연결을 끊을 수 있다.
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(username, emitter);
        }
        return emitter;
    }

    /** 특정 사용자에게 답례 알림들을 push한다. */
    public void push(String username, List<ReminderTaskResponse> reminders) {
        if (reminders.isEmpty()) {
            return;
        }
        List<SseEmitter> targets = emitters.get(username);
        if (targets == null || targets.isEmpty()) {
            return;   // 접속 중이 아니면 보관만 되고, 다음 접속 때 밀린 알림으로 전달된다
        }

        for (SseEmitter emitter : targets) {
            for (ReminderTaskResponse reminder : reminders) {
                try {
                    emitter.send(SseEmitter.event().name("reminder").data(reminder));
                } catch (IOException e) {
                    remove(username, emitter);
                    break;
                }
            }
        }
        log.info("[SSE] {} 에게 알림 {}건 전송", username, reminders.size());
    }

    private void remove(String username, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(username);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(username);
            }
        }
    }
}
