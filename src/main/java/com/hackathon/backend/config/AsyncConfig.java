package com.hackathon.backend.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 백그라운드 선물 추천 미리받기용 스레드 풀.
 *
 * <p>요청 스레드에서 AI를 부르면 응답이 그만큼 늦어지므로, "다음 추천"은 여기서 따로 돌린다.
 * 큐를 굳이 크게 잡지 않는 이유는, 밀린 미리받기는 어차피 화면 진입 때 다시 요청되기 때문이다.
 * 넘치면 버려도(AbortPolicy 대신 CallerRuns가 아니라) 화면 동작에는 영향이 없어야 한다 —
 * 미리받기가 실패하면 버튼을 눌렀을 때 그 자리에서 AI를 부르는 기존 동작으로 자연스럽게 돌아간다.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String RECOMMENDATION_EXECUTOR = "recommendationExecutor";
    public static final String CALENDAR_BACKFILL_EXECUTOR = "calendarBackfillExecutor";

    /**
     * 구글 연동 직후의 일정 일괄 등록용. 배치 사이에 스레드가 그대로 잠들기 때문에
     * 추천 미리받기 풀과 섞으면 그동안 추천이 밀린다. 한 번에 하나만 돌면 되므로 스레드도 하나다.
     */
    @Bean(CALENDAR_BACKFILL_EXECUTOR)
    public Executor calendarBackfillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("calendar-backfill-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(RECOMMENDATION_EXECUTOR)
    public Executor recommendationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("recommend-prefetch-");
        // 큐까지 가득 차면 조용히 버린다. 미리받기는 없어도 되는 최적화라 요청 스레드를 막으면 안 된다.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
