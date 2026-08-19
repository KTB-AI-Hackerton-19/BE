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
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.PersonRepository;
import com.hackathon.backend.repository.RecommendedGiftRepository;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.SecurityUtils;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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
    private static final int RECENT_GIFT_SAMPLE = 5;

    private final RecommendedGiftRepository recommendedGiftRepository;
    private final PersonRepository personRepository;
    private final GiftRecordRepository giftRecordRepository;
    private final UserRepository userRepository;
    private final ReminderTaskRepository reminderTaskRepository;
    private final AiRecommendationClient aiRecommendationClient;

    public RecommendationService(RecommendedGiftRepository recommendedGiftRepository, PersonRepository personRepository,
                                 GiftRecordRepository giftRecordRepository, UserRepository userRepository,
                                 ReminderTaskRepository reminderTaskRepository,
                                 AiRecommendationClient aiRecommendationClient) {
        this.recommendedGiftRepository = recommendedGiftRepository;
        this.personRepository = personRepository;
        this.giftRecordRepository = giftRecordRepository;
        this.userRepository = userRepository;
        this.reminderTaskRepository = reminderTaskRepository;
        this.aiRecommendationClient = aiRecommendationClient;
    }

    /**
     * 답례 알림(PENDING) 중 예정일이 가장 가까운 "날짜"를 찾고, 그 날짜에 답례할 사람 전원에 대해
     * 각각 추천 선물 목록(최대 {@code limit}건)을 묶어 돌려준다.
     * 답례 예정인 사람이 한 명도 없으면(전부 답례 완료했거나 기록이 아직 없으면) 특정 대상 없는 일반 추천 그룹 하나로 대체한다.
     */
    @Transactional
    public List<PersonRecommendationResponse> list(Integer limit, boolean refresh) {
        String username = SecurityUtils.getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        int size = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, 10);

        List<ReminderTask> pending = reminderTaskRepository
                .findByUser_UsernameAndStatusOrderByScheduledAtAsc(username, ReminderStatus.PENDING)
                .stream()
                .filter(task -> task.getPerson() != null)
                .toList();

        LocalDate nearestDate = pending.stream()
                .map(ReminderTask::getScheduledAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

        if (nearestDate == null) {
            List<RecommendationResponse> general = recommendFor(user, username, null, size, refresh).stream()
                    .map(RecommendationResponse::from)
                    .toList();
            return List.of(new PersonRecommendationResponse(null, null, null, null, general));
        }

        LocalDate today = LocalDate.now();
        List<Person> peopleOnNearestDate = pending.stream()
                .filter(task -> task.getScheduledAt().equals(nearestDate))
                .map(ReminderTask::getPerson)
                .distinct()
                .toList();

        return peopleOnNearestDate.stream()
                .map(person -> new PersonRecommendationResponse(
                        person.getId(),
                        person.getName(),
                        nearestDate,
                        (int) ChronoUnit.DAYS.between(today, nearestDate),
                        recommendFor(user, username, person, size, refresh).stream()
                                .map(RecommendationResponse::from)
                                .toList()))
                .toList();
    }

    /** 대시보드의 "마음 에이전트" 카드처럼, 답례일 자동 선정과 무관하게 특정 한 명을 지정해 추천을 받을 때 쓴다. */
    @Transactional
    public List<RecommendationResponse> listForPerson(Long personId, Integer limit, boolean refresh) {
        String username = SecurityUtils.getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Person person = personId == null ? null
                : personRepository.findByIdAndUser_Username(personId, username)
                        .orElseThrow(() -> new CustomException(ErrorCode.PERSON_NOT_FOUND));

        int size = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, 10);
        return recommendFor(user, username, person, size, refresh).stream()
                .map(RecommendationResponse::from)
                .toList();
    }

    /** 대상(person, null이면 전체 기록 기준)의 추천을 캐시에서 재사용하거나, 없으면/refresh면 새로 생성해 최대 size건 반환한다. */
    private List<RecommendedGift> recommendFor(User user, String username, Person person, int size, boolean refresh) {
        List<RecommendedGift> cached = findCached(username, person);
        if (!refresh && !cached.isEmpty()) {
            return cached.size() > size ? cached.subList(0, size) : cached;
        }

        recommendedGiftRepository.deleteAll(cached);
        recommendedGiftRepository.flush();

        List<AiRecommendResponse.Item> items = aiRecommendationClient.recommend(buildRequest(username, person, size));

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

    private AiRecommendRequest buildRequest(String username, Person person, int limit) {
        List<GiftRecord> records = person == null
                ? giftRecordRepository.findByUser_UsernameOrderByReceivedDateDescIdDesc(username)
                : giftRecordRepository.findByUser_UsernameAndPerson_IdOrderByReceivedDateDescIdDesc(username, person.getId());

        List<AiRecommendRequest.ReceivedGift> recentGifts = records.stream()
                .limit(RECENT_GIFT_SAMPLE)
                .map(record -> {
                    Category category = record.getCategory();
                    return new AiRecommendRequest.ReceivedGift(
                            record.getGiftName(),
                            category != null ? category.getName() : null,
                            record.getAmount(),
                            record.getOccasion(),
                            record.getReceivedDate());
                })
                .toList();

        return new AiRecommendRequest(
                person != null ? person.getName() : null,
                person != null ? person.getRelationship() : null,
                person != null ? person.getMemo() : null,
                limit,
                recentGifts);
    }

    private RecommendedGift toEntity(User user, Person person, AiRecommendResponse.Item item, int order) {
        return new RecommendedGift(
                user, person,
                (item.emoji() == null || item.emoji().isBlank()) ? "🎁" : item.emoji(),
                item.name(),
                item.amount(),
                RecommendationTag.from(item.tag()),
                item.reason(),
                order);
    }
}
