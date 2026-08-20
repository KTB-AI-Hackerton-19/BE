package com.hackathon.backend.service;

import com.hackathon.backend.client.AiRecommendRequest;
import com.hackathon.backend.client.AiRecommendResponse;
import com.hackathon.backend.client.AiRecommendationClient;
import com.hackathon.backend.domain.Category;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.RecommendationTag;
import com.hackathon.backend.domain.RecommendedGift;
import com.hackathon.backend.domain.ReminderStatus;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.recommendation.PersonRecommendationResponse;
import com.hackathon.backend.dto.recommendation.RecommendationResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.CategoryRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선물 추천. AI 서비스가 없으면 {@link AiRecommendationClient}가 더미로 폴백하므로 프론트는 지금 바로 붙일 수 있다.
 * 생성된 추천은 {@code recommended_gifts}에 저장해두고 같은 대상에 대해 재사용하며, refresh=true면 새로 생성한다.
 * 대상 인물은 사용자가 지정하지 않고, 답례 알림(reminderDate)이 가장 가까운 날짜에 있는 사람들을 자동 선정한다.
 */
@Service
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 3;
    /** 그룹 수를 제한하지 않는다는 표시. */
    public static final int NO_GROUP_LIMIT = 0;

    /** 이 기간 안에 있는 생일만 '지금 챙길 일'로 본다. */
    private static final int BIRTHDAY_WINDOW_DAYS = 90;

    private static final double BUDGET_MIN_RATIO = 0.8;
    private static final double BUDGET_MAX_RATIO = 1.2;
    private static final int MAX_INTERESTS = 5;
    private static final String DEFAULT_EMOJI = "🎁";

    private final RecommendedGiftRepository recommendedGiftRepository;
    private final PersonRepository personRepository;
    private final GiftRecordRepository giftRecordRepository;
    private final UserRepository userRepository;
    private final ReminderTaskRepository reminderTaskRepository;
    private final CategoryRepository categoryRepository;
    private final AiRecommendationClient aiRecommendationClient;

    public RecommendationService(RecommendedGiftRepository recommendedGiftRepository, PersonRepository personRepository,
                                 GiftRecordRepository giftRecordRepository, UserRepository userRepository,
                                 ReminderTaskRepository reminderTaskRepository,
                                 CategoryRepository categoryRepository,
                                 AiRecommendationClient aiRecommendationClient) {
        this.recommendedGiftRepository = recommendedGiftRepository;
        this.personRepository = personRepository;
        this.giftRecordRepository = giftRecordRepository;
        this.userRepository = userRepository;
        this.reminderTaskRepository = reminderTaskRepository;
        this.categoryRepository = categoryRepository;
        this.aiRecommendationClient = aiRecommendationClient;
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
            return List.of(new PersonRecommendationResponse(null, null, null, null, null, general));
        }

        LocalDate nearestDate = occasions.getFirst().date();
        return occasions.stream()
                .filter(occasion -> occasion.date().equals(nearestDate))
                .limit(maxGroups == NO_GROUP_LIMIT ? Long.MAX_VALUE : Math.max(maxGroups, 1))
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
        return recommendFor(user, username, person, null, size, refresh).stream()
                .map(RecommendationResponse::from)
                .toList();
    }

    /**
     * 대상(person, null이면 전체 기록 기준)의 추천을 캐시에서 재사용하거나, 없으면/refresh면 새로 생성해 최대 size건 반환한다.
     *
     * @param event 이번에 챙기는 이유("생일" 등). null이면 가장 최근 기록의 받은 이유를 쓴다.
     */
    private List<RecommendedGift> recommendFor(User user, String username, Person person, String event,
                                               int size, boolean refresh) {
        List<RecommendedGift> cached = findCached(username, person);
        if (!refresh && !cached.isEmpty()) {
            return cached.size() > size ? cached.subList(0, size) : cached;
        }

        recommendedGiftRepository.deleteAll(cached);
        recommendedGiftRepository.flush();

        List<AiRecommendResponse.Item> items =
                aiRecommendationClient.recommend(buildRequest(username, person, event), size);

        return recommendedGiftRepository.saveAll(
                java.util.stream.IntStream.range(0, Math.min(items.size(), size))
                        .mapToObj(i -> toEntity(user, person, items.get(i), i))
                        .toList());
    }

    private List<RecommendedGift> findCached(String username, Person person) {
        return person == null
                ? recommendedGiftRepository.findByUser_UsernameAndPersonIsNullOrderByDisplayOrderAsc(username)
                : recommendedGiftRepository.findByUser_UsernameAndPerson_IdOrderByDisplayOrderAsc(username, person.getId());
    }

    /**
     * AI 추천 요청 조립.
     *
     * <p>AI 명세({@code RecommendRequest})는 모든 필드가 선택이므로 <b>모르는 값은 보내지 않는다.</b>
     * 나이는 생일이 있어야 계산되고, 성별은 미입력이면 생략(서버 기본값 unknown)한다.</p>
     *
     * <p>기준이 되는 선물은 <b>가장 최근에 받은 것</b> 하나다. AI가 여러 건을 받지 않기 때문이고,
     * 답례는 보통 마지막으로 받은 마음에 대해 하므로 최근 것이 맞다.</p>
     */
    private AiRecommendRequest buildRequest(String username, Person person, String event) {
        List<GiftRecord> records = person == null
                ? giftRecordRepository.findByUser_UsernameOrderByReceivedDateDescIdDesc(username)
                : giftRecordRepository.findByUser_UsernameAndPerson_IdOrderByReceivedDateDescIdDesc(username, person.getId());

        GiftRecord latest = records.isEmpty() ? null : records.getFirst();
        Category category = latest != null ? latest.getCategory() : null;
        Integer amount = latest != null ? latest.getAmount() : null;

        return new AiRecommendRequest(
                ageOf(person),
                genderOf(person),
                budgetOf(amount, BUDGET_MIN_RATIO),
                budgetOf(amount, BUDGET_MAX_RATIO),
                category != null ? List.of(category.getName()) : null,
                latest != null ? latest.getGiftName() : null,
                amount,
                person != null ? person.getName() : null,
                person != null ? person.getRelationship() : null,
                // 생일이면 그 사실을 AI에 알린다. 아니면 받은 기록의 이유를 그대로 쓴다.
                event != null ? event : (latest != null ? latest.getOccasion() : null),
                interestsOf(person));
    }

    /** 생일이 있어야 나이를 계산한다. 없으면 보내지 않는다(추측하지 않는다). */
    private Integer ageOf(Person person) {
        if (person == null || person.getBirthday() == null) {
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

    private RecommendedGift toEntity(User user, Person person, AiRecommendResponse.Item item, int order) {
        String emoji = (item.emoji() == null || item.emoji().isBlank())
                ? emojiFor(user.getUsername(), item.aiCategory())
                : item.emoji();
        return new RecommendedGift(
                user, person,
                emoji,
                item.name(),
                item.amount(),
                RecommendationTag.from(item.tag()),
                item.reason(),
                item.productUrl(),
                item.thankYouMessage(),
                order);
    }

    /**
     * AI가 준 카테고리 이름으로 우리 카테고리의 이모지를 찾는다.
     *
     * <p>AI는 이모지를 주지 않고, 카테고리 이름도 자기 체계를 쓴다("식품·디저트" vs 우리 "디저트").
     * 그래서 이름이 정확히 같지 않아도 찾을 수 있게 세 단계로 본다.</p>
     *
     * <ol>
     *   <li>정확히 같은 이름</li>
     *   <li>한쪽이 다른 쪽을 포함 ("식품·디저트" ⊃ "디저트")</li>
     *   <li>구분자(·, /, 쉼표)로 쪼갠 조각끼리 비교 ("식품·디저트" → "식품", "디저트")</li>
     * </ol>
     *
     * <p>그래도 못 찾으면 기본 이모지를 쓴다. 매핑표를 하드코딩하지 않은 이유는,
     * 사용자가 카테고리를 자유롭게 추가할 수 있어 표가 금방 낡기 때문이다.</p>
     */
    private String emojiFor(String username, String aiCategory) {
        if (aiCategory == null || aiCategory.isBlank()) {
            return DEFAULT_EMOJI;
        }
        String target = aiCategory.trim();
        List<Category> categories = categoryRepository.findByUser_UsernameOrderByDisplayOrderAscIdAsc(username);

        for (Category category : categories) {
            if (category.getName().equalsIgnoreCase(target)) {
                return orDefault(category.getEmoji());
            }
        }
        for (Category category : categories) {
            String name = category.getName();
            if (target.contains(name) || name.contains(target)) {
                return orDefault(category.getEmoji());
            }
        }
        for (String token : target.split("[·/,]")) {
            String piece = token.trim();
            if (piece.isEmpty()) {
                continue;
            }
            for (Category category : categories) {
                if (category.getName().contains(piece) || piece.contains(category.getName())) {
                    return orDefault(category.getEmoji());
                }
            }
        }
        return DEFAULT_EMOJI;
    }

    private String orDefault(String emoji) {
        return (emoji == null || emoji.isBlank()) ? DEFAULT_EMOJI : emoji;
    }
}
