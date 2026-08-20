package com.hackathon.backend.service;

import com.hackathon.backend.config.AsyncConfig;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 추천 캐시를 화면 뒤에서 미리 채워 둔다 — 홈 진입("지금 보여줄 세트")과 "다시 추천받기"("다음 세트") 둘 다.
 *
 * <p>{@link RecommendationService}와 분리한 이유는 두 가지다. 하나는 {@code @Async}가 프록시로 동작해서
 * 같은 빈 안에서 자기 메서드를 부르면 그냥 동기 실행이 되어 버리기 때문이고, 다른 하나는 미리받기 실패가
 * 화면 요청에 절대 번지면 안 되기 때문이다 — 여기서 예외를 전부 삼킨다. 미리받기가 없으면
 * 버튼을 눌렀을 때 그 자리에서 AI를 부르는 원래 동작으로 돌아갈 뿐이다.</p>
 *
 * <p>같은 대상에 대한 미리받기가 겹치면(홈을 연달아 새로고침하면) AI를 그 횟수만큼 부르게 되므로
 * 진행 중인 키를 들고 있다가 중복 요청은 버린다.</p>
 */
@Component
public class RecommendationPrefetcher {

    private static final Logger log = LoggerFactory.getLogger(RecommendationPrefetcher.class);

    /** 순환 참조(Service → Prefetcher → Service)를 생성 시점에 만들지 않으려고 지연 조회한다. */
    private final ObjectProvider<RecommendationService> recommendationService;

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public RecommendationPrefetcher(ObjectProvider<RecommendationService> recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Async(AsyncConfig.RECOMMENDATION_EXECUTOR)
    public void prefetch(String username, Long personId, String event, int size) {
        String key = username + ":" + personId;
        if (!inFlight.add(key)) {
            return;   // 이미 같은 대상을 만들고 있다
        }
        try {
            recommendationService.getObject().warm(username, personId, event, size);
        } catch (Exception e) {
            log.warn("추천 미리받기 실패 (화면에는 영향 없음). username={}, personId={}", username, personId, e);
        } finally {
            inFlight.remove(key);
        }
    }

    /**
     * <b>홈이 다음에 요구할 대상</b>을 찾아서 미리 채워 둔다. 캐시를 버린 직후와 로그인 직후에 부른다.
     *
     * <p>대상을 인자로 받지 않고 여기서 다시 계산하는 이유는, 캐시를 버리게 만든 사건(기록 등록 등)이
     * <b>추천 대상 자체를 바꾸기</b> 때문이다. 방금 지운 사람만 데워두면 홈은 정작 다른 사람을 물어보고
     * 또 AI를 기다리게 된다.</p>
     */
    @Async(AsyncConfig.RECOMMENDATION_EXECUTOR)
    public void warmUpcoming(String username, int size) {
        String key = username + ":upcoming";
        if (!inFlight.add(key)) {
            return;
        }
        try {
            // prefetch를 그대로 재사용한다. 같은 빈 안에서 부르니 @Async 프록시를 타지 않아 이 스레드에서 그냥 실행되는데,
            // 여기가 이미 백그라운드라 그게 맞다. 중복 방지(inFlight)는 프록시와 무관한 평범한 코드라 그대로 걸린다 —
            // 덕분에 화면 응답 뒤에 도는 미리받기와 키를 공유해서 같은 대상에 AI를 두 번 부르지 않는다.
            recommendationService.getObject().upcomingWarmTargets(username)
                    .forEach(target -> prefetch(username, target.personId(), target.event(), size));
        } catch (Exception e) {
            log.warn("추천 캐시 예열 실패 (화면에는 영향 없음). username={}", username, e);
        } finally {
            inFlight.remove(key);
        }
    }
}
