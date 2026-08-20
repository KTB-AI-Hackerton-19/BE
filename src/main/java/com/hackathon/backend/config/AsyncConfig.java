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
