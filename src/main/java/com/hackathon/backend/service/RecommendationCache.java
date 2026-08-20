package com.hackathon.backend.service;

import com.hackathon.backend.repository.RecommendedGiftRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 추천을 버린다. 추천의 <b>근거가 바뀌면</b> 캐시도 같이 버려야 하기 때문이다.
 *
 * <p>추천은 "그 사람에게 마지막으로 받은 것"(선물명·금액·이유)과 사람 정보(나이·성별·취향)로 만들어진다.
 * 그래서 기록을 새로 등록하거나 고치면 근거가 달라지는데, 캐시를 그대로 두면
 * <b>새로 받은 마음이 반영되지 않은 추천</b>이 계속 나간다. 화면에서는 이게 "AI가 이상한 걸 추천한다"로 보이지,
 * "캐시가 낡았다"로는 절대 안 보인다 — 그래서 조용히 틀리는 쪽을 막는다.</p>
 *
 * <p>두 슬롯을 <b>모두</b> 지운다. 미리 받아둔 NEXT도 결국 옛 근거로 만든 것이라,
 * 남겨두면 '다시 추천받기'를 눌렀을 때 낡은 세트가 올라온다.</p>
 *
 * <p>대상 없는 일반 추천(person이 null)도 함께 지운다. 그건 사용자의 <b>전체</b> 기록으로 만들어져서
 * 어느 기록이 바뀌든 근거가 달라진다.</p>
 */
@Component
public class RecommendationCache {

    private static final Logger log = LoggerFactory.getLogger(RecommendationCache.class);

    private final RecommendedGiftRepository recommendedGiftRepository;

    public RecommendationCache(RecommendedGiftRepository recommendedGiftRepository) {
        this.recommendedGiftRepository = recommendedGiftRepository;
    }

    /** 사람 한 명 기준 추천을 버린다. personId가 null이면 일반 추천만 버린다. */
    public void evict(String username, Long personId) {
        evict(username, personId == null ? List.of() : List.of(personId));
    }

    @Transactional
    public void evict(String username, Collection<Long> personIds) {
        List<Long> targets = personIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!targets.isEmpty()) {
            recommendedGiftRepository.deleteByUser_UsernameAndPerson_IdIn(username, targets);
        }
        recommendedGiftRepository.deleteByUser_UsernameAndPersonIsNull(username);
        log.debug("추천 캐시 무효화 — username={}, personIds={}", username, targets);
    }
}
