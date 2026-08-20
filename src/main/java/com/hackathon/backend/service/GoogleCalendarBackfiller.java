package com.hackathon.backend.service;

import com.hackathon.backend.config.AsyncConfig;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 구글 연동 직후, 이미 잡혀 있던 답례일들을 캘린더에 올린다.
 *
 * <p>한 번에 몰아서 부르지 않고 <b>일정 10개마다 1분씩 쉬어가며</b> 등록한다. 같은 행사의 하객은
 * 일정 하나로 묶이므로 개수에 포함되지 않는다(구글을 부르는 횟수만 센다).</p>
 *
 * <p>{@link GoogleCalendarService}와 분리한 이유는 {@code @Async}가 프록시로 동작하기 때문이다 —
 * 같은 빈 안에서 부르면 그냥 동기 실행이 되어, 연동 콜백이 등록이 다 끝날 때까지 화면을 붙잡는다
 * ({@link RecommendationPrefetcher}와 같은 이유). 배치마다 트랜잭션을 새로 여는 것도 의도한 것으로,
 * 1분씩 쉬는 동안 트랜잭션을 붙잡고 있으면 그 사이 다른 요청이 같은 행을 못 건드린다.</p>
 */
@Component
public class GoogleCalendarBackfiller {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarBackfiller.class);

    /** 순환 참조(Service → Backfiller → Service)를 생성 시점에 만들지 않으려고 지연 조회한다. */
    private final ObjectProvider<GoogleCalendarService> googleCalendarService;
    private final int batchSize;
    private final long intervalMs;

    public GoogleCalendarBackfiller(ObjectProvider<GoogleCalendarService> googleCalendarService,
                                    @Value("${google.calendar.backfill.batch-size:10}") int batchSize,
                                    @Value("${google.calendar.backfill.interval-ms:60000}") long intervalMs) {
        this.googleCalendarService = googleCalendarService;
        this.batchSize = batchSize;
        this.intervalMs = intervalMs;
    }

    /** 실패해도 연동 자체는 이미 끝난 상태다. 여기서 삼키고, 이후 등록/수정 건은 평소대로 올라간다. */
    @Async(AsyncConfig.CALENDAR_BACKFILL_EXECUTOR)
    public void backfill(String username) {
        try {
            List<Long> remaining = googleCalendarService.getObject().upcomingBackfillTargets(username);
            log.info("구글 캘린더 일괄 등록 시작 — 대상 {}건, {}개마다 {}ms 대기. username={}",
                    remaining.size(), batchSize, intervalMs, username);

            while (!remaining.isEmpty()) {
                int handled = googleCalendarService.getObject().backfillBatch(username, remaining, batchSize);
                if (handled <= 0) {
                    break;   // 연동이 풀렸거나 더 처리할 게 없다
                }
                remaining = remaining.subList(handled, remaining.size());
                if (remaining.isEmpty()) {
                    break;
                }
                Thread.sleep(intervalMs);
            }
        } catch (InterruptedException e) {
            // 서버를 내리는 중이다. 남은 건 다음 연동 때 다시 대상이 된다(일정이 붙은 건 대상에서 빠진다).
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("구글 캘린더 일괄 등록 실패 (연동 상태에는 영향 없음). username={}", username, e);
        }
    }
}
