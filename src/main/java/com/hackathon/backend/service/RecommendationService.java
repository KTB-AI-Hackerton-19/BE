package com.hackathon.backend.service;

import com.hackathon.backend.client.AiRecommendRequest;
import com.hackathon.backend.client.AiRecommendResponse;
import com.hackathon.backend.client.AiRecommendationClient;
import com.hackathon.backend.domain.Category;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.RecommendationTag;
import com.hackathon.backend.domain.RecommendedGift;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.recommendation.RecommendationResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.PersonRepository;
import com.hackathon.backend.repository.RecommendedGiftRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.SecurityUtils;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선물 추천. AI 서비스가 없으면 {@link AiRecommendationClient}가 더미로 폴백하므로 프론트는 지금 바로 붙일 수 있다.
 * 생성된 추천은 {@code recommended_gifts}에 저장해두고 같은 대상에 대해 재사용하며, refresh=true면 새로 생성한다.
 */
@Service
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 3;
    private static final int RECENT_GIFT_SAMPLE = 5;

    private final RecommendedGiftRepository recommendedGiftRepository;
    private final PersonRepository personRepository;
    private final GiftRecordRepository giftRecordRepository;
    private final UserRepository userRepository;
    private final AiRecommendationClient aiRecommendationClient;

    public RecommendationService(RecommendedGiftRepository recommendedGiftRepository, PersonRepository personRepository,
                                 GiftRecordRepository giftRecordRepository, UserRepository userRepository,
                                 AiRecommendationClient aiRecommendationClient) {
        this.recommendedGiftRepository = recommendedGiftRepository;
        this.personRepository = personRepository;
        this.giftRecordRepository = giftRecordRepository;
        this.userRepository = userRepository;
        this.aiRecommendationClient = aiRecommendationClient;
    }

    @Transactional
    public List<RecommendationResponse> list(Long personId, Integer limit, boolean refresh) {
        String username = SecurityUtils.getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        final Person person = personId == null ? null
                : personRepository.findByIdAndUser_Username(personId, username)
                        .orElseThrow(() -> new CustomException(ErrorCode.PERSON_NOT_FOUND));

        List<RecommendedGift> cached = findCached(username, person);
        if (!refresh && !cached.isEmpty()) {
            return cached.stream().map(RecommendationResponse::from).toList();
        }

        recommendedGiftRepository.deleteAll(cached);
        recommendedGiftRepository.flush();

        int size = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, 10);
        List<AiRecommendResponse.Item> items = aiRecommendationClient.recommend(buildRequest(username, person, size));

        List<RecommendedGift> saved = recommendedGiftRepository.saveAll(
                java.util.stream.IntStream.range(0, Math.min(items.size(), size))
                        .mapToObj(i -> toEntity(user, person, items.get(i), i))
                        .toList());

        return saved.stream().map(RecommendationResponse::from).toList();
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
