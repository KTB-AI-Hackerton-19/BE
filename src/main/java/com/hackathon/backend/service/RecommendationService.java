package com.hackathon.backend.service;

import com.hackathon.backend.client.AiRecommendRequest;
import com.hackathon.backend.client.AiRecommendResponse;
import com.hackathon.backend.client.AiRecommendationClient;
import com.hackathon.backend.client.ProductImageResolver;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.Relationship;
import com.hackathon.backend.domain.RecommendationSlot;
import com.hackathon.backend.domain.RecommendationTag;
import com.hackathon.backend.domain.RecommendedGift;
import com.hackathon.backend.domain.ReminderStatus;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.recommendation.PersonRecommendationResponse;
import com.hackathon.backend.dto.recommendation.RecommendationResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.PersonRepository;
import com.hackathon.backend.repository.RecommendedGiftRepository;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.SecurityUtils;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 선물 추천. AI 서비스가 없으면 {@link AiRecommendationClient}가 더미로 폴백하므로 프론트는 지금 바로 붙일 수 있다.
 * 대상 인물은 사용자가 지정하지 않고, 답례 알림(reminderDate)이 가장 가까운 날짜에 있는 사람들을 자동 선정한다.
 *
 * <p><b>원칙: 화면 요청은 AI를 기다리지 않는다.</b> AI 추천은 실측 8~9초라, 한 번이라도 요청 스레드에서 부르면
 * 그 화면은 멈춘 것처럼 보인다. 그래서 생성된 추천은 {@code recommended_gifts}에 저장해두고
 * 실제 생성은 전부 {@link RecommendationPrefetcher}(백그라운드)로 민다. 요청 스레드가 AI를 부르는 건
 * 캐시가 정말 하나도 없을 때(가입 직후)뿐이다.</p>
 *
 * <p>자리는 둘이다. {@link RecommendationSlot#CURRENT}는 지금 화면에 보이는 세트,
 * {@link RecommendationSlot#NEXT}는 '다시 추천받기'용으로 미리 만들어 둔 세트다. refresh 때는 NEXT를 승격시켜
 * 즉시 응답하고, 응답을 내려보낸 뒤 그다음 세트를 또 만들어 둔다.</p>
 *
 * <p>기록이 바뀌어 추천 근거가 달라지면 CURRENT를 <b>지우지 않고 낡음 표시만</b> 한다
 * ({@link RecommendationCache}). 화면은 직전 세트를 즉시 받고, 새 세트는 백그라운드에서 만들어져 다음 진입에 나간다.
 */
@Service
public class RecommendationService {

    public static final int DEFAULT_LIMIT = 3;

    /**
     * 캐시를 버린 직후 <b>백그라운드로</b> 다시 채워둘 사람 수 상한.
     *
     * <p>같은 날짜에 답례할 사람이 여럿이면 그 수만큼 AI를 부르게 되므로 상한을 둔다.
     * 홈은 한 명만 보여주고({@code MAX_DASHBOARD_RECOMMENDATION_GROUPS}) 추천 목록 화면이 나머지를 보는데,
     * 앞쪽 몇 명만 데워둬도 대부분의 진입이 캐시에 맞는다.</p>
     */
    private static final int WARM_GROUP_LIMIT = 3;
    /** 그룹 수를 제한하지 않는다는 표시. */
    public static final int NO_GROUP_LIMIT = 0;

    /** 이 기간 안에 있는 생일만 '지금 챙길 일'로 본다. */
    private static final int BIRTHDAY_WINDOW_DAYS = 90;

    private static final double BUDGET_MIN_RATIO = 0.8;
    private static final double BUDGET_MAX_RATIO = 1.2;
    private static final int MAX_INTERESTS = 5;

    private final RecommendedGiftRepository recommendedGiftRepository;
    private final PersonRepository personRepository;
    private final GiftRecordRepository giftRecordRepository;
    private final UserRepository userRepository;
    private final ReminderTaskRepository reminderTaskRepository;
    private final CategoryEmojiResolver categoryEmojiResolver;
    private final AiRecommendationClient aiRecommendationClient;
    private final ProductImageResolver productImageResolver;
    private final RecommendationPrefetcher prefetcher;

    public RecommendationService(RecommendedGiftRepository recommendedGiftRepository, PersonRepository personRepository,
                                 GiftRecordRepository giftRecordRepository, UserRepository userRepository,
                                 ReminderTaskRepository reminderTaskRepository,
                                 CategoryEmojiResolver categoryEmojiResolver,
                                 AiRecommendationClient aiRecommendationClient,
                                 ProductImageResolver productImageResolver,
                                 RecommendationPrefetcher prefetcher) {
        this.recommendedGiftRepository = recommendedGiftRepository;
        this.personRepository = personRepository;
        this.giftRecordRepository = giftRecordRepository;
        this.userRepository = userRepository;
        this.reminderTaskRepository = reminderTaskRepository;
        this.categoryEmojiResolver = categoryEmojiResolver;
        this.aiRecommendationClient = aiRecommendationClient;
        this.productImageResolver = productImageResolver;
        this.prefetcher = prefetcher;
    }

    /**
     * 답례 알림(PENDING) 중 예정일이 가장 가까운 "날짜"를 찾고, 그 날짜에 답례할 사람 전원에 대해
     * 각각 추천 선물 목록(최대 {@code limit}건)을 묶어 돌려준다.
     * 답례 예정인 사람이 한 명도 없으면(전부 답례 완료했거나 기록이 아직 없으면) 특정 대상 없는 일반 추천 그룹 하나로 대체한다.
     */
    @Transactional
    public List<PersonRecommendationResponse> list(Integer limit, boolean refresh) {
        return list(limit, refresh, NO_GROUP_LIMIT);
    }

    /**
     * @param maxGroups 사람 그룹 수 상한. 같은 날짜에 여러 명이 몰리면 그 수만큼 AI를 호출하므로
     *                  홈 화면처럼 빨라야 하는 곳에서는 상한을 준다. {@link #NO_GROUP_LIMIT}이면 전부.
     */
    @Transactional
    public List<PersonRecommendationResponse> list(Integer limit, boolean refresh, int maxGroups) {
        String username = SecurityUtils.getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        int size = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, 10);
        LocalDate today = LocalDate.now();

        List<Occasion> occasions = upcomingOccasions(username, today);
        if (occasions.isEmpty()) {
            // 챙길 일이 하나도 없으면 전체 기록 기준으로 한 묶음만 준다.
            List<RecommendationResponse> general = recommendFor(user, username, null, null, size, refresh).stream()
                    .map(RecommendationResponse::from)
                    .toList();
            schedulePrefetch(username, List.of(new WarmTarget(null, null)), size);
            return List.of(new PersonRecommendationResponse(null, null, null, null, null, general));
        }

        LocalDate nearestDate = occasions.getFirst().date();
        List<Occasion> targets = occasions.stream()
                .filter(occasion -> occasion.date().equals(nearestDate))
                .limit(maxGroups == NO_GROUP_LIMIT ? Long.MAX_VALUE : Math.max(maxGroups, 1))
                .toList();

        List<PersonRecommendationResponse> groups = targets.stream()
                .map(occasion -> new PersonRecommendationResponse(
                        occasion.person().getId(),
                        occasion.person().getName(),
                        occasion.type(),
                        occasion.date(),
                        (int) ChronoUnit.DAYS.between(today, occasion.date()),
                        recommendFor(user, username, occasion.person(), occasion.event(), size, refresh).stream()
                                .map(RecommendationResponse::from)
                                .toList()))
                .toList();

        schedulePrefetch(username,
                targets.stream().map(o -> new WarmTarget(o.person().getId(), o.event())).toList(),
                size);
        return groups;
    }

    /** 미리받기 대상 하나. 스레드를 넘어가므로 엔티티가 아니라 식별자만 들고 간다. */
    public record WarmTarget(Long personId, String event) {
    }

    /**
     * 응답을 내려보낸 뒤 다음 세트를 백그라운드에서 만들어 둔다.
     *
     * <p>커밋 이후에 띄우는 이유는, refresh로 NEXT를 CURRENT로 승격시킨 변경이 아직 커밋되지 않은 상태에서
     * 미리받기가 돌면 "NEXT가 아직 차 있다"고 보고 그냥 돌아가 버리기 때문이다. 그러면 다음 버튼이 다시 느려진다.</p>
     */
    private void schedulePrefetch(String username, List<WarmTarget> targets, int size) {
        Runnable task = () -> targets.forEach(
                target -> prefetcher.prefetch(username, target.personId(), target.event(), size));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * 한 대상의 추천 캐시를 백그라운드에서 채워둔다. 화면이 AI를 <b>기다리지 않게</b> 만드는 지점이다.
     * 백그라운드 스레드에서 호출되므로 SecurityContext에 기대지 않고 username을 직접 받는다.
     *
     * <p>채우는 순서가 중요하다. 예전에는 {@link RecommendationSlot#NEXT}(=다시 추천받기용)만 채웠는데,
     * 정작 화면이 기다리는 자리는 {@link RecommendationSlot#CURRENT}다. CURRENT가 비는 경우
     * — 서버 재시작(인메모리 H2), 기록 등록/수정으로 캐시를 버린 직후, 답례 대상이 다른 사람으로 바뀐 직후 —
     * 홈 진입이 그대로 AI 응답 시간만큼 멈췄다. 그래서 <b>CURRENT부터</b> 채우고, 그게 준비된 뒤에 NEXT를 채운다.</p>
     *
     * <p>다시 만들어야 하는 세트는 둘이다. 하나는 <b>낡음 표시된</b> 세트(기록이 바뀌어 근거가 달라진 것),
     * 다른 하나는 <b>더미 폴백</b>으로 채워진 세트다. 후자를 굳이 다시 만드는 이유는, 그러지 않으면
     * AI가 잠깐 죽었을 때 만들어진 더미가 "캐시가 차 있다"는 이유로 눌러앉아 계속 화면에 나가기 때문이다.</p>
     */
    @Transactional
    public void warm(String username, Long personId, String event, int size) {
        // 'size보다 적은지'가 아니라 '비었는지'로 판단한다. AI가 요청보다 적은 수를 주는 일이 흔한데
        // 개수로 재면 그 세트는 영원히 미달로 보여서 화면을 열 때마다 AI를 다시 부르게 된다.
        List<RecommendedGift> current = findCached(username, personId, RecommendationSlot.CURRENT);
        if (current.isEmpty() || needsRefresh(current)) {
            regenerate(username, personId, event, size, RecommendationSlot.CURRENT, current);
            return;   // CURRENT를 채우는 데 이미 AI를 한 번 불렀다. NEXT는 다음 진입 때 채운다.
        }

        List<RecommendedGift> waiting = findCached(username, personId, RecommendationSlot.NEXT);
        if (!waiting.isEmpty() && !needsRefresh(waiting)) {
            return;   // 이미 대기 중인 세트가 있으면 AI를 또 부르지 않는다
        }
        regenerate(username, personId, event, size, RecommendationSlot.NEXT, waiting);
    }

    /** 쓸모없어진 세트를 지우고 그 자리에 새로 만든다. */
    private void regenerate(String username, Long personId, String event, int size,
                            RecommendationSlot slot, List<RecommendedGift> obsolete) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Person person = personId == null ? null
                : personRepository.findByIdAndUser_Username(personId, username).orElse(null);
        if (personId != null && person == null) {
            return;   // 미리받기를 준비하는 사이에 사람이 지워졌다
        }

        recommendedGiftRepository.deleteAll(obsolete);
        recommendedGiftRepository.flush();
        generate(user, username, person, event, size, slot);
    }

    /**
     * 그대로 두면 안 되는 세트인지 — 근거가 바뀌어 낡음 표시가 붙었거나, AI가 아니라 더미로 채워진 경우.
     * 비어 있으면 false다(비었다는 판단은 호출부가 따로 한다).
     */
    private boolean needsRefresh(List<RecommendedGift> gifts) {
        return !gifts.isEmpty()
                && (gifts.stream().anyMatch(RecommendedGift::isStale)
                        || gifts.stream().allMatch(RecommendedGift::isFallback));
    }

    /**
     * 홈이 다음에 요구할 대상들. 캐시를 버린 직후 무엇을 데워야 하는지 판단하는 데 쓴다.
     *
     * <p>버려진 사람이 아니라 <b>화면이 다음에 볼 사람</b>을 데운다는 게 핵심이다. 기록을 하나 등록하면
     * 답례 알림이 새로 생겨 가장 가까운 대상 자체가 바뀔 수 있어서, 방금 지운 사람만 데우면 헛수고가 된다.</p>
     */
    @Transactional(readOnly = true)
    public List<WarmTarget> upcomingWarmTargets(String username) {
        List<Occasion> occasions = upcomingOccasions(username, LocalDate.now());
        if (occasions.isEmpty()) {
            return List.of(new WarmTarget(null, null));   // 대상 없는 일반 추천
        }
        LocalDate nearestDate = occasions.getFirst().date();
        return occasions.stream()
                .filter(occasion -> occasion.date().equals(nearestDate))
                .limit(WARM_GROUP_LIMIT)
                .map(occasion -> new WarmTarget(occasion.person().getId(), occasion.event()))
                .toList();
    }

    /**
     * "이 사람에게 줄 차례"인 일정. 답례 알림과 생일을 같은 후보로 놓고 가까운 순으로 정렬한다.
     *
     * <p>둘을 나누지 않는 이유는, 이 서비스에서 사람이 목록에 있는 이유가 "그 사람이 나에게 무언가 줬기 때문"이라
     * 생일이든 답례일이든 <b>줄 차례이고 근거도 같기</b> 때문이다. 예산도 받은 금액을 기준으로 잡으면 된다.</p>
     *
     * <p>생일은 {@link #BIRTHDAY_WINDOW_DAYS}일 안쪽만 본다. 열 달 뒤 생일이 "지금 챙길 일"로
     * 올라오면 화면이 엉뚱해진다.</p>
     */
    private List<Occasion> upcomingOccasions(String username, LocalDate today) {
        List<Occasion> occasions = new java.util.ArrayList<>();

        reminderTaskRepository
                .findByUser_UsernameAndStatusOrderByScheduledAtAsc(username, ReminderStatus.PENDING)
                .stream()
                .filter(task -> task.getPerson() != null && !task.getScheduledAt().isBefore(today))
                .forEach(task -> occasions.add(
                        new Occasion("REMINDER", task.getPerson(), task.getScheduledAt(), null)));

        LocalDate limit = today.plusDays(BIRTHDAY_WINDOW_DAYS);
        personRepository.findByUser_UsernameOrderByNameAsc(username).stream()
                .filter(person -> person.getBirthday() != null)
                .forEach(person -> {
                    LocalDate next = nextOccurrence(person.getBirthday(), today);
                    if (!next.isAfter(limit)) {
                        occasions.add(new Occasion("BIRTHDAY", person, next, "생일"));
                    }
                });

        // 같은 사람이 답례와 생일 모두 걸리면 가까운 쪽 하나만 남긴다.
        return occasions.stream()
                .sorted(Comparator.comparing(Occasion::date))
                .filter(distinctByPerson())
                .toList();
    }

    private java.util.function.Predicate<Occasion> distinctByPerson() {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        return occasion -> seen.add(occasion.person().getId());
    }

    /** 올해 생일이 지났으면 내년 생일. 윤달(2/29)은 그 해에 없으면 말일로 당겨진다. */
    private LocalDate nextOccurrence(LocalDate birthday, LocalDate today) {
        LocalDate thisYear = birthday.withYear(today.getYear());
        return thisYear.isBefore(today) ? birthday.withYear(today.getYear() + 1) : thisYear;
    }

    /** 챙길 일 하나. type은 REMINDER(답례) 또는 BIRTHDAY(생일). */
    private record Occasion(String type, Person person, LocalDate date, String event) {
    }

    /** 특정 사람(또는 전체 기록) 기준 추천. 사람 상세 화면에서 쓴다. */
    @Transactional
    public List<RecommendationResponse> listForPerson(Long personId, Integer limit, boolean refresh) {
        String username = SecurityUtils.getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Person person = personId == null ? null
                : personRepository.findByIdAndUser_Username(personId, username)
                        .orElseThrow(() -> new CustomException(ErrorCode.PERSON_NOT_FOUND));

        int size = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, 10);
        List<RecommendationResponse> gifts = recommendFor(user, username, person, null, size, refresh).stream()
                .map(RecommendationResponse::from)
                .toList();
        schedulePrefetch(username, List.of(new WarmTarget(personId, null)), size);
        return gifts;
    }

    /**
     * 대상(person, null이면 전체 기록 기준)의 추천을 캐시에서 재사용하거나, 없으면/refresh면 새로 생성해 최대 size건 반환한다.
     *
     * @param event 이번에 챙기는 이유("생일" 등). null이면 가장 최근 기록의 받은 이유를 쓴다.
     */
    private List<RecommendedGift> recommendFor(User user, String username, Person person, String event,
                                               int size, boolean refresh) {
        Long personId = person == null ? null : person.getId();
        List<RecommendedGift> current = findCached(username, personId, RecommendationSlot.CURRENT);

        if (!refresh && !current.isEmpty()) {
            return trim(current, size);
        }

        if (refresh) {
            // 미리 받아둔 세트가 있으면 AI를 부르지 않고 그대로 올린다 — 버튼이 즉시 반응하는 지점이다.
            List<RecommendedGift> waiting = findCached(username, personId, RecommendationSlot.NEXT);
            if (!waiting.isEmpty()) {
                recommendedGiftRepository.deleteAll(current);
                recommendedGiftRepository.flush();
                waiting.forEach(RecommendedGift::promote);
                return trim(waiting, size);
            }
        }

        recommendedGiftRepository.deleteAll(current);
        recommendedGiftRepository.flush();
        return generate(user, username, person, event, size, RecommendationSlot.CURRENT);
    }

    /**
     * AI를 실제로 불러 세트를 만들어 저장한다.
     *
     * <p>AI는 상품 링크만 주고 이미지 주소는 주지 않으므로, 저장 직전에 링크에서 대표 이미지를 한 번 뽑아
     * 같이 넣어둔다. 여기서 뽑는 이유는 이 지점이 <b>추천이 새로 만들어지는 유일한 곳</b>이라서다 —
     * 조회 때 뽑으면 캐시된 추천을 볼 때마다 쇼핑몰을 다시 두드리게 된다.</p>
     */
    private List<RecommendedGift> generate(User user, String username, Person person, String event,
                                           int size, RecommendationSlot slot) {
        String batchId = java.util.UUID.randomUUID().toString();
        AiRecommendResponse.Result result =
                aiRecommendationClient.recommend(buildRequest(username, person, event), size);
        List<AiRecommendResponse.Item> items = result.items();
        List<AiRecommendResponse.Item> selected =
                items.size() > size ? items.subList(0, size) : items;

        Map<String, String> images = productImageResolver.resolveAll(
                selected.stream().map(AiRecommendResponse.Item::productUrl).toList());

        return recommendedGiftRepository.saveAll(
                java.util.stream.IntStream.range(0, selected.size())
                        .mapToObj(i -> toEntity(user, person, selected.get(i), images, i, slot,
                                result.fallback(), batchId))
                        .toList());
    }

    private List<RecommendedGift> trim(List<RecommendedGift> gifts, int size) {
        return gifts.size() > size ? gifts.subList(0, size) : gifts;
    }

    /**
     * 한 슬롯의 캐시를 읽는다. 세트가 두 벌 들어 있으면 <b>가장 최근 것만</b> 쓰고 나머지는 버린다.
     *
     * <p>두 벌이 생기는 건 미리받기와 화면 요청이 "캐시가 비었다"를 동시에 보고 둘 다 AI를 부른 경우다.
     * 서로의 저장을 못 보기 때문에(각자 트랜잭션) 막을 수는 없고, 읽을 때 정리한다.
     * 그냥 두면 {@code displayOrder} 정렬만으로는 서로 다른 세트의 카드가 섞여 나간다.</p>
     */
    private List<RecommendedGift> findCached(String username, Long personId, RecommendationSlot slot) {
        List<RecommendedGift> rows = personId == null
                ? recommendedGiftRepository.findByUser_UsernameAndPersonIsNullAndSlotOrderByDisplayOrderAsc(username, slot)
                : recommendedGiftRepository.findByUser_UsernameAndPerson_IdAndSlotOrderByDisplayOrderAsc(
                        username, personId, slot);
        if (rows.size() <= 1) {
            return rows;
        }

        Map<String, List<RecommendedGift>> batches = rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        gift -> gift.getBatchId() == null ? "" : gift.getBatchId()));
        if (batches.size() <= 1) {
            return rows;
        }

        List<RecommendedGift> newest = batches.values().stream()
                .max(Comparator.comparing(batch -> batch.getFirst().getCreatedAt()))
                .orElse(rows);
        List<RecommendedGift> losers = rows.stream().filter(gift -> !newest.contains(gift)).toList();
        recommendedGiftRepository.deleteAll(losers);
        recommendedGiftRepository.flush();
        return newest;
    }

    /**
     * AI 추천 요청 조립.
     *
     * <p>AI 명세({@code RecommendRequest})는 모든 필드가 선택이므로 <b>모르는 값은 보내지 않는다.</b>
     * 나이는 생일이 있어야 계산되고, 성별은 미입력이면 생략(서버 기본값 unknown)한다.</p>
     *
     * <p>기준이 되는 선물은 <b>가장 최근에 받은 것</b> 하나다. AI가 여러 건을 받지 않기 때문이고,
     * 답례는 보통 마지막으로 받은 마음에 대해 하므로 최근 것이 맞다.</p>
     *
     * <p><b>{@code categories}는 보내지 않는다.</b> 이 필드는 "지정하면 그 안에서만 추천"이라
     * 받은 선물의 카테고리 하나를 넣으면 AI가 그 카테고리 하나짜리 응답만 주고, 거기 딸린
     * 예시 개수가 그대로 카드 수 상한이 된다(실제로 향수 → '패션·잡화' 하나 → 카드 2장으로 잘렸다).
     * 받은 게 향수라고 답례까지 향수 계열로 묶을 이유도 없고, "받은 것과 비슷한 부담"은
     * {@code gift_name}·{@code gift_price}·예산으로 이미 전달된다.</p>
     */
    private AiRecommendRequest buildRequest(String username, Person person, String event) {
        List<GiftRecord> records = person == null
                ? giftRecordRepository.findByUser_UsernameOrderByReceivedDateDescIdDesc(username)
                : giftRecordRepository.findByUser_UsernameAndPerson_IdOrderByReceivedDateDescIdDesc(username, person.getId());

        GiftRecord latest = records.isEmpty() ? null : records.getFirst();
        Integer amount = latest != null ? latest.getAmount() : null;

        return new AiRecommendRequest(
                ageOf(person),
                genderOf(person),
                budgetOf(amount, BUDGET_MIN_RATIO),
                budgetOf(amount, BUDGET_MAX_RATIO),
                null,   // categories — 위 주석 참고. 후보를 좁히면 추천 카드가 모자란다.
                latest != null ? latest.getGiftName() : null,
                amount,
                person != null ? person.getName() : null,
                person != null ? Relationship.displayLabel(person.getRelationship()) : null,
                // 생일이면 그 사실을 AI에 알린다. 아니면 받은 기록의 이유를 그대로 쓴다.
                event != null ? event : (latest != null ? latest.getOccasion() : null),
                interestsOf(person));
    }

    /** 생일이 있어야 나이를 계산한다. 없으면 보내지 않는다(추측하지 않는다). */
    private Integer ageOf(Person person) {
        if (person == null || person.getBirthday() == null) {
            return null;
        }
        // 생일이 아직 안 지난 연도(=미래 날짜)면 Period가 0년으로 나와 "0살"이 나간다. 모르면 안 보낸다.
        if (person.getBirthday().isAfter(LocalDate.now())) {
            return null;
        }
        int age = Period.between(person.getBirthday(), LocalDate.now()).getYears();
        return age >= 0 && age <= 120 ? age : null;
    }

    /** AI는 male/female/unknown만 받는다. 미입력이면 생략해 서버 기본값(unknown)을 쓰게 한다. */
    private String genderOf(Person person) {
        if (person == null || person.getGender() == null) {
            return null;
        }
        return switch (person.getGender()) {
            case MALE -> "male";
            case FEMALE -> "female";
            case OTHER -> null;
        };
    }

    /** 받은 금액의 80~120%를 답례 예산으로 제안한다. 금액을 모르면 보내지 않는다. */
    private Integer budgetOf(Integer amount, double ratio) {
        if (amount == null || amount <= 0) {
            return null;
        }
        return (int) Math.round(amount * ratio);
    }

    /** 취향 메모. 최대 5개까지 허용되므로 쉼표로 끊어 넘긴다. */
    private List<String> interestsOf(Person person) {
        if (person == null || person.getMemo() == null || person.getMemo().isBlank()) {
            return null;
        }
        List<String> interests = Arrays.stream(person.getMemo().split("[,·/]"))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .limit(MAX_INTERESTS)
                .toList();
        return interests.isEmpty() ? null : interests;
    }

    private RecommendedGift toEntity(User user, Person person, AiRecommendResponse.Item item,
                                     Map<String, String> images, int order, RecommendationSlot slot,
                                     boolean fallback, String batchId) {
        String emoji = (item.emoji() == null || item.emoji().isBlank())
                ? categoryEmojiResolver.resolve(user.getUsername(), item.aiCategory())
                : item.emoji();
        return new RecommendedGift(
                user, person,
                emoji,
                item.name(),
                item.amount(),
                RecommendationTag.from(item.tag()),
                item.reason(),
                item.productUrl(),
                item.productUrl() == null ? null : images.get(item.productUrl()),
                item.thankYouMessage(),
                order,
                slot,
                fallback,
                batchId);
    }

}
