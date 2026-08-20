package com.hackathon.backend.service;

import com.hackathon.backend.client.AiConfirmDtos;
import com.hackathon.backend.client.AiGiftDataClient;
import com.hackathon.backend.client.AiRecommendResponse;
import com.hackathon.backend.client.ProductImageResolver;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.RecommendationTag;
import com.hackathon.backend.domain.Relationship;
import com.hackathon.backend.dto.gift.GiftRecordPrepareRequest;
import com.hackathon.backend.dto.gift.GiftRecordPrepareResponse;
import com.hackathon.backend.dto.recommendation.RecommendationResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.PersonRepository;
import com.hackathon.backend.security.SecurityUtils;
import com.hackathon.backend.support.MoneyFormatter;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사진 없이 직접 입력한 값으로 AI 준비 작업({@code /from-gift-data})을 돌린다.
 *
 * <p><b>아무것도 저장하지 않는다.</b> 마음 기록 저장은 {@link GiftRecordService#create}가 그대로 하고,
 * 여기서는 그 등록 화면 옆에 붙일 수 있는 <b>추천·답례 메시지·초안</b>만 받아온다.
 * 저장하지 않으므로 {@code GET /api/recommendations}의 캐시(NEXT/CURRENT 슬롯)도 건드리지 않는다 —
 * 등록 도중 한 번 눌러본 것 때문에 홈 화면 추천이 바뀌면 안 되기 때문이다.</p>
 */
@Service
public class GiftDataAgentService {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 10;

    private final AiGiftDataClient aiGiftDataClient;
    private final PersonRepository personRepository;
    private final ProductImageResolver productImageResolver;
    private final CategoryEmojiResolver categoryEmojiResolver;

    public GiftDataAgentService(AiGiftDataClient aiGiftDataClient, PersonRepository personRepository,
                                ProductImageResolver productImageResolver,
                                CategoryEmojiResolver categoryEmojiResolver) {
        this.aiGiftDataClient = aiGiftDataClient;
        this.personRepository = personRepository;
        this.productImageResolver = productImageResolver;
        this.categoryEmojiResolver = categoryEmojiResolver;
    }

    @Transactional(readOnly = true)
    public GiftRecordPrepareResponse prepare(GiftRecordPrepareRequest request) {
        String username = SecurityUtils.getCurrentUsername();

        Person person = request.personId() == null ? null
                : personRepository.findByIdAndUser_Username(request.personId(), username)
                        .orElseThrow(() -> new CustomException(ErrorCode.PERSON_NOT_FOUND));

        // AI 명세상 gift_price는 0보다 커야 한다(exclusiveMinimum: 0). 그냥 보내면 422가 나므로 여기서 막는다.
        Integer price = MoneyFormatter.parse(request.price());
        if (price == null || price <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "금액은 0보다 큰 값이어야 합니다.");
        }

        int limit = request.limit() == null || request.limit() <= 0
                ? DEFAULT_LIMIT : Math.min(request.limit(), MAX_LIMIT);

        AiConfirmDtos.GiftData giftData = new AiConfirmDtos.GiftData(
                request.gift(),
                price,
                ageOf(person),
                genderOf(person),
                person != null ? person.getName() : trimToNull(request.personName()),
                relationshipOf(person, request.relation()),
                request.date() != null ? request.date() : LocalDate.now(),
                request.reminderDate(),
                trimToNull(request.occasion()));

        AiGiftDataClient.Preparation preparation = aiGiftDataClient.prepare(giftData, limit);

        // 추천 상품 링크에서 카드 썸네일을 뽑는다. 저장 경로와 같은 규칙이라 화면도 같은 모양이 된다.
        Map<String, String> images = productImageResolver.resolveAll(
                preparation.recommendations().stream().map(AiRecommendResponse.Item::productUrl).toList());

        List<RecommendationResponse> cards = preparation.recommendations().stream()
                .map(item -> toCard(username, person, item, images))
                .toList();

        return new GiftRecordPrepareResponse(
                cards,
                preparation.thankYouMessage(),
                preparation.calendarDraft(),
                preparation.notiDraft(),
                preparation.workflowId(),
                preparation.requiresConfirmation(),
                preparation.error());
    }

    /**
     * 저장된 추천과 <b>같은 응답 모양</b>으로 맞춘다 — 프론트가 카드 렌더링 코드를 두 벌 갖지 않아도 되게.
     * 저장하지 않으므로 id만 null이다.
     */
    private RecommendationResponse toCard(String username, Person person, AiRecommendResponse.Item item,
                                          Map<String, String> images) {
        String emoji = (item.emoji() == null || item.emoji().isBlank())
                ? categoryEmojiResolver.resolve(username, item.aiCategory())
                : item.emoji();
        return new RecommendationResponse(
                null,
                person != null ? person.getId() : null,
                person != null ? person.getName() : null,
                emoji,
                item.name(),
                item.amount(),
                MoneyFormatter.format(item.amount()),
                RecommendationTag.from(item.tag()).getLabel(),
                item.reason(),
                item.productUrl(),
                item.productUrl() == null ? null : images.get(item.productUrl()),
                item.thankYouMessage());
    }

    /** 등록된 사람이 있으면 그 관계가 우선한다 — 드롭다운 값보다 저장된 값이 정확하다. */
    private String relationshipOf(Person person, String relation) {
        if (person != null && person.getRelationship() != null) {
            return Relationship.displayLabel(person.getRelationship());
        }
        return trimToNull(relation);
    }

    /** 생일이 있어야 나이를 계산한다. 없으면 보내지 않는다(추측하지 않는다). */
    private Integer ageOf(Person person) {
        if (person == null || person.getBirthday() == null || person.getBirthday().isAfter(LocalDate.now())) {
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

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
