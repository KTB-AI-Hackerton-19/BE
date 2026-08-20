package com.hackathon.backend.service;

import com.hackathon.backend.domain.RecommendationSlot;
import com.hackathon.backend.repository.RecommendedGiftRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 추천의 <b>근거가 바뀌었을 때</b> 저장된 추천을 갈아치운다.
 *
 * <p>추천은 "그 사람에게 마지막으로 받은 것"(선물명·금액·이유)과 사람 정보(나이·성별·취향)로 만들어진다.
 * 그래서 기록을 새로 등록하거나 고치면 근거가 달라지는데, 캐시를 그대로 두면
 * <b>새로 받은 마음이 반영되지 않은 추천</b>이 계속 나간다. 화면에서는 이게 "AI가 이상한 걸 추천한다"로 보이지,
 * "캐시가 낡았다"로는 절대 안 보인다 — 그래서 조용히 틀리는 쪽을 막는다.</p>
 *
 * <p><b>다만 지우지는 않는다.</b> 지웠더니 다음 홈 진입이 그 자리에서 AI를 부르게 되어 8~9초 멈췄다(실측).
 * 그리고 캐시를 버리는 사건은 대부분 "기록을 등록하고 홈으로 돌아가는" 흐름이라, 사용자가 겪는 느린 새로고침이
 * 정확히 거기서 나왔다. 그래서 지금 보여주는 세트({@link RecommendationSlot#CURRENT})는 <b>낡음 표시만</b> 하고
 * 남겨둔 뒤, 커밋 이후 백그라운드에서 새 세트를 만들어 덮는다. 화면은 즉시 뜨고, 몇 초 뒤 진입부터 새 추천이 나간다.</p>
 *
 * <p>미리 받아둔 {@link RecommendationSlot#NEXT}는 반대로 <b>지운다.</b> 그건 '다시 추천받기'를 누르는 순간
 * 그대로 화면에 올라가는 세트라, 낡은 걸 남겨두면 버튼을 누른 결과가 옛 근거로 만든 추천이 된다.
 * 지워도 손해가 없다 — 예열이 CURRENT를 채운 다음 알아서 다시 만들어 둔다.</p>
 *
 * <p>대상 없는 일반 추천(person이 null)도 함께 다룬다. 그건 사용자의 <b>전체</b> 기록으로 만들어져서
 * 어느 기록이 바뀌든 근거가 달라진다.</p>
 */
@Component
public class RecommendationCache {

    private static final Logger log = LoggerFactory.getLogger(RecommendationCache.class);

    private final RecommendedGiftRepository recommendedGiftRepository;

    /** 순환 참조(Cache → Prefetcher → Service)를 생성 시점에 만들지 않으려고 지연 조회한다. */
    private final ObjectProvider<RecommendationPrefetcher> prefetcher;

    public RecommendationCache(RecommendedGiftRepository recommendedGiftRepository,
                               ObjectProvider<RecommendationPrefetcher> prefetcher) {
        this.recommendedGiftRepository = recommendedGiftRepository;
        this.prefetcher = prefetcher;
    }

    /** 사람 한 명 기준 추천을 무효화한다. personId가 null이면 일반 추천만 무효화한다. */
    public void evict(String username, Long personId) {
        evict(username, personId == null ? List.of() : List.of(personId));
    }

    @Transactional
    public void evict(String username, Collection<Long> personIds) {
        List<Long> targets = personIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!targets.isEmpty()) {
            recommendedGiftRepository.markStale(username, targets, RecommendationSlot.CURRENT);
            recommendedGiftRepository.deleteByUser_UsernameAndPerson_IdInAndSlot(
                    username, targets, RecommendationSlot.NEXT);
        }
        recommendedGiftRepository.markStaleForGeneral(username, RecommendationSlot.CURRENT);
        recommendedGiftRepository.deleteByUser_UsernameAndPersonIsNullAndSlot(username, RecommendationSlot.NEXT);

        log.debug("추천 캐시 무효화 — username={}, personIds={}", username, targets);
        warmAfterCommit(username);
    }

    /**
     * 낡음 표시된 캐시를 백그라운드에서 새로 만들어 덮는다.
     *
     * <p>커밋 이후여야 한다. 지금 트랜잭션 안에서 예열을 띄우면 다른 스레드는 아직 <b>표시되기 전</b> 데이터를
     * 보게 되어 "멀쩡한 캐시네" 하고 그냥 돌아간다. 그러면 낡은 추천이 다음 무효화까지 그대로 남는다.
     * 호출한 트랜잭션이 롤백되면 예열도 안 도는 게 맞다 — 무효화 자체가 없던 일이 되기 때문이다.</p>
     */
    private void warmAfterCommit(String username) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            prefetcher.getObject().warmUpcoming(username, RecommendationService.DEFAULT_LIMIT);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                prefetcher.getObject().warmUpcoming(username, RecommendationService.DEFAULT_LIMIT);
            }
        });
    }
}
