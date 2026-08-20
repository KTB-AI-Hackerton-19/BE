package com.hackathon.backend.service;

import com.hackathon.backend.client.AiExtractionBatch;
import com.hackathon.backend.client.AiExtractionClient;
import com.hackathon.backend.client.AiExtractionResult;
import com.hackathon.backend.domain.Category;
import com.hackathon.backend.domain.EventCategory;
import com.hackathon.backend.domain.EventGroup;
import com.hackathon.backend.domain.Gender;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.GiftRecordStatus;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.RecordType;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.ErrorDetail;
import com.hackathon.backend.dto.PageResponse;
import com.hackathon.backend.dto.gift.EventCategoryResponse;
import com.hackathon.backend.dto.gift.GiftRecordCreateRequest;
import com.hackathon.backend.dto.gift.GiftRecordDeleteResponse;
import com.hackathon.backend.dto.gift.GiftRecordExtractRequest;
import com.hackathon.backend.dto.gift.GiftRecordExtractResponse;
import com.hackathon.backend.dto.gift.GiftRecordPersonLinkRequest;
import com.hackathon.backend.dto.gift.GiftRecordPersonLinkResponse;
import com.hackathon.backend.dto.gift.GiftRecordResponse;
import com.hackathon.backend.dto.gift.GiftRecordUpdateRequest;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.SecurityUtils;
import com.hackathon.backend.support.MoneyFormatter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GiftRecordService {

    private static final Logger log = LoggerFactory.getLogger(GiftRecordService.class);

    /**
     * DRAFT의 답례 알림일 기본값 오프셋(받은 날짜 + N일).
     * 사용자가 확인 폼에서 바꾸면 그 값이 그대로 쓰이며, 알림은 확정(PATCH) 시점에만 생성된다.
     */
    private static final int DEFAULT_REMINDER_OFFSET_DAYS = 30;

    /** 받은 날짜로 허용하는 미래 범위. 오늘 날짜를 잘못 입력하는 것과 실제 예약을 구분하려는 최소한의 방어. */
    private static final int MAX_FUTURE_DAYS = 1;

    private final GiftRecordRepository giftRecordRepository;
    private final ReminderTaskRepository reminderTaskRepository;
    private final RecommendationCache recommendationCache;
    private final GoogleCalendarService googleCalendarService;
    private final UserRepository userRepository;
    private final PersonService personService;
    private final CategoryService categoryService;
    private final RelationshipService relationshipService;
    private final S3PresignService s3PresignService;
    private final AiExtractionClient aiExtractionClient;

    /**
     * AI 호출 전에 기다리는 시간(ms). 프론트가 S3 PUT을 끝내자마자 extract를 부르는 구조라,
     * 업로드가 아직 안 끝났을 가능성을 없애려고 둔 여유 시간이다.
     * 0으로 두면 기다리지 않는다({@code AI_PRE_REQUEST_DELAY_MS=0}).
     */
    private final long preRequestDelayMs;

    public GiftRecordService(GiftRecordRepository giftRecordRepository, ReminderTaskRepository reminderTaskRepository,
                             UserRepository userRepository, PersonService personService,
                             CategoryService categoryService, RelationshipService relationshipService,
                             S3PresignService s3PresignService,
                             AiExtractionClient aiExtractionClient,
                             GoogleCalendarService googleCalendarService,
                             RecommendationCache recommendationCache,
                             @Value("${ai.service.pre-request-delay-ms:15000}") long preRequestDelayMs) {
        this.giftRecordRepository = giftRecordRepository;
        this.reminderTaskRepository = reminderTaskRepository;
        this.recommendationCache = recommendationCache;
        this.userRepository = userRepository;
        this.personService = personService;
        this.categoryService = categoryService;
        this.relationshipService = relationshipService;
        this.s3PresignService = s3PresignService;
        this.aiExtractionClient = aiExtractionClient;
        this.googleCalendarService = googleCalendarService;
        this.preRequestDelayMs = preRequestDelayMs;
    }

    /**
     * 기록 모달 저장 / 직접 등록. 보낸 사람은 personId로 고르거나 이름만 적어도 된다.
     * 이름만 적으면 <b>사람을 만들지 않고</b> 기록에만 남는다({@link #resolveSender} 참고).
     */
    @Transactional
    public GiftRecordResponse create(GiftRecordCreateRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = getUser(username);

        validateDates(request.date(), request.reminderDate());

        // 빠진 값은 여기서 하나씩 튕기지 않고 validateRequired가 한 번에 모아 알려준다.
        Sender sender = resolveSender(user, request.personId(), request.personName(), request.guestName(),
                request.registerPerson(), request.relation());

        RecordType recordType = RecordType.parseOrDefault(request.recordType());
        Category category = null;
        EventCategory eventCategory = null;
        if (recordType == RecordType.EVENT) {
            eventCategory = parseEventCategory(request.eventCategory());
        } else {
            category = categoryService.resolveOrFallback(request.categoryId(), request.category());
        }

        GiftRecord record = GiftRecord.createConfirmed(
                user, sender.person(), sender.guestName(),
                sender.person() == null ? relationshipService.normalize(request.relation()) : null,
                recordType, category, eventCategory, request.eventDate(),
                trimToNull(request.occasion()), trimToNull(request.gift()),
                MoneyFormatter.parse(request.price()), request.date(), request.reminderDate(),
                Boolean.TRUE.equals(request.thanked()));
        validateRequired(record);
        giftRecordRepository.save(record);

        syncReminder(user, record);
        // 새 마음이 들어오면 그 사람 추천의 근거가 달라진다. 낡은 추천이 그대로 나가지 않게 버린다.
        recommendationCache.evict(username, sender.person() == null ? null : sender.person().getId());
        return toResponse(record);
    }

    /**
     * 업로드된 이미지의 imageKey로 조회용 presigned GET URL을 만들어 AI 서비스에 넘기고, 결과를 DRAFT로 저장한다.
     *
     * <p>이미지는 한 번에 <b>한 장</b>이다. <b>그 한 장에 여러 명이 있으면</b>(봉투가 여러 개 찍힌 사진,
     * 방명록, 단체 메시지 캡처) AI가 사람 목록을
     * 돌려준다. 그 길이가 곧 사람 수이고, 사람 수만큼 DRAFT를 만들어 전부 응답에 실어 보낸다.</p>
     *
     * <p>대분류는 <b>사람 수</b>로 정한다 — <b>2명 이상이면 전원 EVENT</b>(봉투가 여러 개 찍힌 사진, 방명록처럼
     * 경조사 자리에서 받은 것), <b>1명이면 GIFT</b>다. EVENT일 때만 AI가 준 경조사 유형(고정 7종 중 하나)을
     * 붙이고, AI가 유형까지는 못 집어냈으면 비워둔 채 내려보내 사용자가 확인 폼에서 고른다.
     * 경조사는 더 이상 사용자별 카테고리 row가 아니라 코드에 고정된 값이라, 따로 만들거나 찾을 필요가 없다.</p>
     */
    @Transactional
    public GiftRecordExtractResponse extract(GiftRecordExtractRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = getUser(username);

        String imageUrl = s3PresignService.createGetUrl(request.imageKey());
        awaitUpload();
        AiExtractionBatch batch = aiExtractionClient.extract(imageUrl);

        // 사람 수가 대분류를 가른다 — 한 장에 여러 명이면 봉투/방명록처럼 경조사 자리에서 받은 것이고,
        // 한 명이면 선물로 본다. AI가 준 경조사 유형은 EVENT일 때만 쓰고, GIFT면 버린다
        // (GiftRecord가 어차피 한쪽을 비우므로, 여기서 미리 맞춰 응답까지 일관되게 만든다).
        RecordType recordType = batch.results().size() > 1 ? RecordType.EVENT : RecordType.GIFT;
        EventCategory eventCategory = recordType == RecordType.EVENT ? batch.eventCategory() : null;

        List<GiftRecordResponse> records = new ArrayList<>();
        for (AiExtractionResult result : batch.results()) {
            Category category = recordType == RecordType.GIFT
                    ? categoryService.resolveOrFallback(null, result.categoryName())
                    : null;

            // AI가 뽑은 이름이 등록된 사람과 정확히 일치할 때만 연결한다. 없으면 null로 두고 사용자가 폼에서 고른다.
            Person person = personService.findByExactName(user, result.senderName());

            LocalDate receivedDate = result.receivedDate() != null ? result.receivedDate() : LocalDate.now();
            String occasion = result.occasion() != null ? result.occasion() : batch.eventName();

            GiftRecord record = GiftRecord.createDraft(
                    user, person, request.imageKey(), result.senderName(),
                    relationshipService.normalize(result.relationship()), result.age(), Gender.from(result.gender()),
                    recordType, category, eventCategory, batch.eventDate(), occasion, result.giftName(),
                    result.amount(), receivedDate, receivedDate.plusDays(DEFAULT_REMINDER_OFFSET_DAYS));
            giftRecordRepository.save(record);

            // AI가 아니라 더미로 채워졌으면 응답에 그대로 실어 보낸다(로그만으로는 프론트가 알 수 없다).
            // imageUrl은 사람마다 같은 이미지라 위에서 만든 것을 그대로 쓴다(사람 수만큼 서명하지 않는다).
            records.add(GiftRecordResponse.from(record, imageUrl, batch.fallback(), batch.fallbackReason()));
        }

        log.info("AI 추출 완료 — {}명, 대분류: {}, 경조사: {}", records.size(), recordType,
                eventCategory != null ? eventCategory.getLabel() : "없음");

        return GiftRecordExtractResponse.of(
                records, eventCategory != null ? EventCategoryResponse.from(eventCategory) : null);
    }

    /**
     * AI를 부르기 전에 업로드가 끝날 시간을 준다.
     *
     * <p>인터럽트가 오면 삼키지 않고 플래그를 되돌려 놓는다. 안 그러면 서버를 내릴 때
     * 이 스레드만 인터럽트를 잃고 계속 살아 있게 된다.</p>
     */
    private void awaitUpload() {
        if (preRequestDelayMs <= 0) {
            return;
        }
        log.info("AI 호출 전 {}ms 대기 (S3 업로드 완료 여유)", preRequestDelayMs);
        try {
            Thread.sleep(preRequestDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 확인/수정 폼 저장(확정) 및 이후 수정. 보내지 않은 필드는 기존 값을 유지한다. */
    @Transactional
    public GiftRecordResponse update(Long id, GiftRecordUpdateRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = getUser(username);
        GiftRecord record = giftRecordRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.GIFT_RECORD_NOT_FOUND));

        validateDates(request.date(), request.reminderDate());

        // 사람이 바뀌는 수정이면 옮겨간 쪽과 떠나온 쪽 모두 근거가 달라진다. 바꾸기 전에 잡아둔다.
        Long previousPersonId = record.getPerson() == null ? null : record.getPerson().getId();

        Sender sender = resolveSender(user, request.personId(), request.personName(), request.guestName(),
                request.registerPerson(), request.relation());

        RecordType recordType = request.recordType() != null ? RecordType.parseOrDefault(request.recordType()) : null;
        Category category = categoryService.resolve(request.categoryId(), request.category());
        EventCategory eventCategory = EventCategory.from(request.eventCategory());

        RecordType effectiveType = recordType != null ? recordType : record.getRecordType();
        if (effectiveType == RecordType.EVENT
                && request.eventCategory() != null && !request.eventCategory().isBlank() && eventCategory == null) {
            // 유형을 보내긴 했는데 목록에 없는 값이다(오타). 미입력은 확정할 때 다른 항목과 함께 안내한다.
            throw CustomException.field("eventCategory", "경조사 유형을 정확히 선택해주세요.");
        }

        record.applyUpdate(sender.person(), sender.guestName(),
                sender.person() == null ? relationshipService.normalize(request.relation()) : null,
                recordType, category, eventCategory, request.eventDate(),
                trimToNull(request.occasion()), trimToNull(request.gift()),
                MoneyFormatter.parse(request.price()), request.date(), request.reminderDate(), request.thanked());

        if (request.confirm() == null || request.confirm()) {
            validateRequired(record);
            record.confirm();
        }

        syncReminder(user, record);
        recommendationCache.evict(username, java.util.Arrays.asList(
                previousPersonId, sender.person() == null ? null : sender.person().getId()));
        return toResponse(record);
    }

    /**
     * 확정된 기록이 갖춰야 할 값. 화면의 입력 폼과 1:1로 맞춘다.
     *
     * <ul>
     *   <li><b>경조사</b> — 유형 · 보낸 사람 · 금액</li>
     *   <li><b>선물</b> — 보낸 사람 · 선물명 · 금액</li>
     * </ul>
     *
     * <p>보낸 사람은 <b>이름만 있어도 된다.</b> 경조사 하객 수십 명을 전부 '사람들'에 올리지 않는 설계라,
     * personId를 강제하면 그 흐름이 막힌다.</p>
     *
     * <p>빠진 항목은 <b>한 번에 모아서</b> 알려준다. 하나씩 튕기면 사용자가 저장을 세 번 눌러야 한다.
     * AI가 만든 DRAFT는 값이 비어 있는 게 정상이라 여기를 통과하지 않는다 — 확정하는 순간에만 본다.</p>
     */
    private void validateRequired(GiftRecord record) {
        List<ErrorDetail.FieldError> missing = new ArrayList<>();
        if (isBlank(record.displayName())) {
            missing.add(new ErrorDetail.FieldError("personName", "보낸 사람을 입력해주세요."));
        }
        if (record.getRecordType() == RecordType.EVENT) {
            if (record.getEventCategory() == null) {
                missing.add(new ErrorDetail.FieldError("eventCategory", "경조사 유형을 선택해주세요."));
            }
        } else if (isBlank(record.getGiftName())) {
            missing.add(new ErrorDetail.FieldError("gift", "선물을 입력해주세요."));
        }
        if (record.getAmount() == null) {
            missing.add(new ErrorDetail.FieldError("price", "금액을 입력해주세요."));
        }
        if (missing.isEmpty()) {
            return;
        }
        // 토스트용 한 문장 + 인풋별 문구를 함께 보낸다. 프론트는 fields만 보고 각 칸에 표시하면 된다.
        String summary = "다음 항목을 입력해주세요: " + missing.stream()
                .map(field -> LABELS.getOrDefault(field.field(), field.field()))
                .collect(Collectors.joining(", "));
        throw new CustomException(ErrorCode.INVALID_INPUT, summary, missing);
    }

    /** 토스트 한 문장에 쓰는 사람이 읽는 이름. 인풋에 붙는 문구는 FieldError.message가 따로 갖는다. */
    private static final Map<String, String> LABELS = Map.of(
            "personName", "보낸 사람",
            "eventCategory", "경조사 유형",
            "gift", "선물명",
            "price", "금액");

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** {@link #resolveSender} 결과. 둘 중 하나만 채워지며, 둘 다 null이면 "보낸 사람 정보가 안 왔다"는 뜻이다. */
    private record Sender(Person person, String guestName) {
    }

    /**
     * 보낸 사람 결정. <b>이름만으로는 Person을 만들지 않는다.</b>
     *
     * <p>예전에는 이름이 오면 없는 사람을 그 자리에서 만들었는데, 사진 한 장에서 여러 명을 뽑는
     * 경조사에서는 그게 치명적이다. 축의금 50건을 확인 폼에서 저장하는 순간 "사람들" 목록에 50명이
     * 쌓여서 목록이 못 쓰게 된다. 경조사 하객은 대부분 다시 볼 일이 없어 사람으로 관리할 대상이 아니다.</p>
     *
     * <p>그래서 기본은 <b>이름만 기록에 남기고(guestName) 매핑하지 않는 것</b>이고, 사람으로 올리는 건
     * 사용자가 명시적으로 고를 때만이다 — 드롭다운에서 기존 사람을 고르거나(personId),
     * "사람으로 등록"을 누르거나(registerPerson=true), 나중에 {@link #linkPerson}으로 연결하거나.</p>
     */
    private Sender resolveSender(User user, Long personId, String personName, String guestName,
                                 Boolean registerPerson, String relation) {
        String name = trimToNull(guestName != null ? guestName : personName);
        if (personId != null) {
            return new Sender(personService.resolveOrCreateNullable(user, personId, null, relation), null);
        }
        if (Boolean.TRUE.equals(registerPerson) && name != null) {
            return new Sender(personService.resolveOrCreateNullable(user, null, name, relation), null);
        }
        return new Sender(null, name);
    }

    /**
     * 이름만 있던 기록을 사람(Person)에 연결한다 — 경조사 리스트의 "사람으로 등록" 버튼.
     *
     * <p>personId를 주면 이미 등록된 사람에 붙이고, 안 주면 기록에 적힌 이름으로 사람을 만든다.
     * 딸린 답례 알림의 대상도 같이 갈아끼워서, 연결 후 대시보드·추천에서 이 사람이 보이게 한다.</p>
     *
     * <p>{@code applySameName=true}면 같은 이름으로 남아 있는 다른 미등록 기록까지 함께 묶는다.
     * 한 사람이 결혼식과 돌잔치에 각각 잡힌 경우 하나씩 누르지 않아도 되게 하려는 것이다.</p>
     */
    @Transactional
    public GiftRecordPersonLinkResponse linkPerson(Long id, GiftRecordPersonLinkRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = getUser(username);
        GiftRecord record = giftRecordRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.GIFT_RECORD_NOT_FOUND));

        String name = record.displayName();
        boolean created;
        Person person;
        if (request.personId() != null) {
            person = personService.resolveOrCreateNullable(user, request.personId(), null, request.relation());
            created = false;
        } else {
            if (name == null || name.isBlank()) {
                throw new CustomException(ErrorCode.INVALID_INPUT,
                        "연결할 이름이 없습니다. 기록의 보낸 사람 이름을 먼저 채우거나 personId를 보내주세요.");
            }
            created = personService.findByExactName(user, name) == null;
            person = personService.resolveOrCreateNullable(user, null, name, request.relation());
        }

        List<GiftRecord> targets = new ArrayList<>();
        targets.add(record);
        if (Boolean.TRUE.equals(request.applySameName()) && name != null && !name.isBlank()) {
            giftRecordRepository.findByUser_UsernameAndPersonIsNull(username).stream()
                    .filter(other -> !other.getId().equals(record.getId()))
                    .filter(other -> name.equals(other.displayName()))
                    .forEach(targets::add);
        }

        for (GiftRecord target : targets) {
            target.linkPerson(person);
            // 알림은 reschedule이 아니라 assignPerson으로 건드린다 — 이미 발송된 알림이 되살아나면 안 된다.
            reminderTaskRepository.findByGiftRecord_Id(target.getId())
                    .ifPresent(task -> task.assignPerson(person));
        }

        log.info("사람 연결 — '{}' → personId {} ({}), 기록 {}건", name, person.getId(),
                created ? "신규" : "기존", targets.size());

        // 이름만 있던 기록이 사람에게 붙으면 그 사람 기준 추천의 근거가 생긴다.
        recommendationCache.evict(username, person.getId());

        return new GiftRecordPersonLinkResponse(person.getId(), person.getName(), created, targets.size(),
                targets.stream().map(t -> toResponse(t, false)).toList());
    }

    /** "감사 완료" / "확인 필요" 뱃지 토글. */
    @Transactional
    public GiftRecordResponse updateThanked(Long id, boolean thanked) {
        String username = SecurityUtils.getCurrentUsername();
        GiftRecord record = giftRecordRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.GIFT_RECORD_NOT_FOUND));
        record.updateThanked(thanked);
        return toResponse(record);
    }

    /** 기록 한 건 삭제. 딸린 답례 알림도 함께 사라진다. 없는 id면 404. */
    @Transactional
    public void delete(Long id) {
        String username = SecurityUtils.getCurrentUsername();
        GiftRecord record = giftRecordRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.GIFT_RECORD_NOT_FOUND));
        deleteRecords(username, List.of(record));
    }

    /**
     * 기록 여러 건 삭제(목록에서 체크해 한 번에 지우는 용도).
     *
     * <p>없는 id나 다른 사용자의 기록은 <b>조용히 건너뛴다.</b> 10건을 골랐는데 그중 하나가 이미 지워졌다고
     * 전체를 실패시키면 사용자가 다시 고르는 수밖에 없어서, 지울 수 있는 것만 지우고 실제 건수를 돌려준다.
     * 사람 다중 삭제({@code PersonService.deleteAll})와 같은 방침이다.</p>
     */
    @Transactional
    public GiftRecordDeleteResponse deleteAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "삭제할 기록의 id를 하나 이상 보내주세요.");
        }
        String username = SecurityUtils.getCurrentUsername();
        // 같은 id가 두 번 오면 삭제 건수가 부풀려지므로 먼저 중복을 제거한다.
        List<Long> unique = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (unique.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "삭제할 기록의 id를 하나 이상 보내주세요.");
        }
        return deleteRecords(username, giftRecordRepository.findByIdInAndUser_Username(unique, username));
    }

    /**
     * 내 마음 기록 <b>전부</b> 삭제. 딸린 답례 알림도 함께 사라진다.
     *
     * <p>사람·카테고리·계정은 남는다 — 기록만 비우고 다시 쌓고 싶은 경우(시연 초기화 등)를 위한 것이다.
     * 계정까지 통째로 지우려면 {@code DELETE /api/users}(회원탈퇴)를 쓴다.</p>
     */
    @Transactional
    public GiftRecordDeleteResponse deleteAllOfUser() {
        String username = SecurityUtils.getCurrentUsername();
        return deleteRecords(username, giftRecordRepository.findByUser_UsernameOrderByReceivedDateDescIdDesc(username));
    }

    /**
     * 삭제 순서가 중요하다. 기록을 참조하는 답례 알림을 먼저 비워야 FK 제약에 걸리지 않는다.
     * 사람(Person)은 건드리지 않는다 — 기록이 없어졌다고 상대방을 목록에서 지울 이유가 없다.
     */
    private GiftRecordDeleteResponse deleteRecords(String username, List<GiftRecord> records) {
        if (records.isEmpty()) {
            return GiftRecordDeleteResponse.empty();
        }
        // 지우기 전에 잡아둔다 — 삭제 후에는 어느 사람 추천을 버려야 할지 알 수 없다.
        List<Long> affectedPersonIds = records.stream()
                .map(record -> record.getPerson() == null ? null : record.getPerson().getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        long reminders = reminderTaskRepository.deleteByGiftRecord_IdIn(
                records.stream().map(GiftRecord::getId).toList());
        giftRecordRepository.deleteAll(records);
        recommendationCache.evict(username, affectedPersonIds);

        log.info("마음 기록 삭제 — 기록 {}건, 답례 알림 {}건", records.size(), reminders);
        return new GiftRecordDeleteResponse(records.size(), (int) reminders);
    }

    /** 마음 기록 목록 — 카테고리/사람/기간/검색어/감사여부 필터 + 정렬 + 페이징. */
    @Transactional(readOnly = true)
    public PageResponse<GiftRecordResponse> search(Long categoryId, String categoryName, Long personId,
                                                   Boolean thanked, GiftRecordStatus status, String kind,
                                                   LocalDate startDate, LocalDate endDate, String q, String personName,
                                                   String sort, int page, int size) {
        String username = SecurityUtils.getCurrentUsername();
        Long resolvedCategoryId = categoryId;
        if (resolvedCategoryId == null && categoryName != null && !categoryName.isBlank() && !"전체".equals(categoryName.trim())) {
            Category category = categoryService.resolve(null, categoryName);
            resolvedCategoryId = category != null ? category.getId() : -1L; // 존재하지 않는 카테고리 → 빈 결과
        }

        KindFilter kindFilter = KindFilter.parse(kind);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), toSort(sort));
        Page<GiftRecord> result = giftRecordRepository.search(
                username, status, resolvedCategoryId, personId, thanked,
                kindFilter.recordType(), kindFilter.allEventCategories(), kindFilter.eventCategories(),
                startDate, endDate, trimToNull(personName), trimToNull(q), pageable);

        List<GiftRecordResponse> content = result.getContent().stream().map(r -> toResponse(r, false)).toList();
        return PageResponse.of(result, content);
    }

    @Transactional(readOnly = true)
    public GiftRecordResponse get(Long id) {
        String username = SecurityUtils.getCurrentUsername();
        GiftRecord record = giftRecordRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.GIFT_RECORD_NOT_FOUND));
        return toResponse(record);
    }

    /** 사람 상세 타임라인 — 페이징. 한 사람에게 여러 번 받으면 목록이 길어진다. */
    @Transactional(readOnly = true)
    public PageResponse<GiftRecordResponse> listByPerson(Long personId, int page, int size) {
        String username = SecurityUtils.getCurrentUsername();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<GiftRecord> result = giftRecordRepository
                .findByUser_UsernameAndPerson_IdOrderByReceivedDateDescIdDesc(username, personId, pageable);
        return PageResponse.of(result, result.getContent().stream().map(r -> toResponse(r, false)).toList());
    }

    @Transactional(readOnly = true)
    public List<GiftRecordResponse> listByPerson(Long personId) {
        String username = SecurityUtils.getCurrentUsername();
        return giftRecordRepository.findByUser_UsernameAndPerson_IdOrderByReceivedDateDescIdDesc(username, personId)
                .stream()
                .map(r -> toResponse(r, false))
                .toList();
    }

    /** GET /api/gift-records/event-categories — 경조사 고정 7종 전체. */
    public List<EventCategoryResponse> listEventCategories() {
        return Arrays.stream(EventCategory.values()).map(EventCategoryResponse::from).toList();
    }

    public GiftRecordResponse toResponse(GiftRecord record) {
        return toResponse(record, true);
    }

    /**
     * 목록 응답에서는 imageUrl을 만들지 않는다. presigned URL은 15분 만료라 응답마다 새로 서명해야 하는데,
     * 목록 화면은 카테고리 이모지로 그리므로 100건이면 서명 100번과 URL 100개가 그대로 버려진다.
     * 사진이 실제로 필요한 단건 조회·DRAFT 응답에서만 발급한다.
     */
    private GiftRecordResponse toResponse(GiftRecord record, boolean includeImageUrl) {
        String imageUrl = includeImageUrl && record.getImageKey() != null
                ? s3PresignService.createGetUrl(record.getImageKey())
                : null;
        return GiftRecordResponse.from(record, imageUrl);
    }

    /**
     * reminderDate가 있으면 ReminderTask를 생성/갱신하고, 없어졌으면 제거한다 (기록 1건 : 알림 1건).
     * 구글 캘린더를 연동한 사용자면 같은 답례일자로 실제 일정까지 등록한다.
     */
    private void syncReminder(User user, GiftRecord record) {
        LocalDate reminderDate = record.getReminderDate();
        ReminderTask existing = reminderTaskRepository.findByGiftRecord_Id(record.getId()).orElse(null);

        if (reminderDate == null) {
            if (existing != null) {
                reminderTaskRepository.delete(existing);
            }
            return;
        }
        ReminderTask reminder;
        boolean dateChanged;
        if (existing == null) {
            reminder = reminderTaskRepository.save(new ReminderTask(user, record.getPerson(), record, reminderDate));
            dateChanged = true;
        } else {
            // reschedule이 scheduledAt을 덮어쓰므로 비교는 반드시 그 전에 해야 한다.
            dateChanged = !reminderDate.equals(existing.getScheduledAt());
            existing.reschedule(record.getPerson(), reminderDate);
            reminder = existing;
        }
        // 구글 일정은 아직 안 만들어졌거나 날짜가 실제로 바뀐 경우에만 건드린다.
        // 저장할 때마다 부르면 메모만 고쳐도 AI가 일정을 새로 만들어 캘린더에 중복이 쌓인다.
        if (dateChanged || reminder.getGoogleEventId() == null) {
            googleCalendarService.syncEvent(user, record, reminder);
        }
    }

    /** sort=latest(기본, 받은 날짜 최신순) / oldest / amount / created */
    private Sort toSort(String sort) {
        String key = sort == null ? "latest" : sort.trim().toLowerCase();
        return switch (key) {
            case "oldest" -> Sort.by(Sort.Order.asc("receivedDate"), Sort.Order.asc("id"));
            case "amount" -> Sort.by(Sort.Order.desc("amount"), Sort.Order.desc("id"));
            case "created" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("receivedDate"), Sort.Order.desc("id"));
        };
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /** 경조사는 고정 7종만 허용한다 — 매칭 안 되면 "기타"로 조용히 넘기지 않고 바로 실패시킨다. */
    /**
     * 경조사 유형 파싱.
     *
     * <p>값이 왔는데 목록에 없는 유형이면(오타) 그 자리에서 알려주고, <b>아예 안 온 경우는 null로 둔다</b> —
     * 미입력은 다른 빠진 항목들과 함께 {@link #validateRequired}가 한 번에 안내한다.</p>
     */
    private EventCategory parseEventCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        EventCategory eventCategory = EventCategory.from(raw);
        if (eventCategory == null) {
            throw CustomException.field("eventCategory", "경조사 유형을 정확히 선택해주세요.");
        }
        return eventCategory;
    }

    /**
     * 날짜 검증. 사용자가 연도를 잘못 치면(2099, 1900) 캘린더와 통계가 통째로 망가지므로 막는다.
     *
     * <p>답례 알림일이 과거면 스케줄러가 다음 정각에 바로 발송해버린다 —
     * 시연 중에 엉뚱한 알림이 뜨는 원인이라 저장 시점에 거른다.</p>
     */
    private void validateDates(LocalDate receivedDate, LocalDate reminderDate) {
        LocalDate today = LocalDate.now();
        if (receivedDate != null && receivedDate.isAfter(today.plusDays(MAX_FUTURE_DAYS))) {
            throw CustomException.field("date", "받은 날짜는 미래일 수 없습니다.");
        }
        if (reminderDate != null && reminderDate.isBefore(today)) {
            throw CustomException.field("reminderDate", "답례 알림일은 오늘 이후로 정해주세요.");
        }
        if (receivedDate != null && reminderDate != null && reminderDate.isBefore(receivedDate)) {
            throw CustomException.field("reminderDate", "답례 알림일은 받은 날짜보다 앞설 수 없습니다.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * {@code kind} 쿼리 파라미터 하나를 recordType + eventCategory 필터로 바꾼다.
     * EVENT/경조사 → 이벤트 전체, GIFT/선물 → 선물만, 경사/조사(그룹) → 그 그룹의 유형들,
     * 구체 유형("결혼"/"WEDDING") → 그 하나만. 모르는 값이면 필터를 걸지 않는다(전체).
     */
    private record KindFilter(RecordType recordType, boolean allEventCategories, List<EventCategory> eventCategories) {

        private static final KindFilter ALL = new KindFilter(null, true, List.of());

        static KindFilter parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return ALL;
            }
            String v = raw.trim();
            if (v.equalsIgnoreCase("EVENT") || v.equals("경조사")) {
                return new KindFilter(RecordType.EVENT, true, List.of());
            }
            if (v.equalsIgnoreCase("GIFT") || v.equals("선물")) {
                return new KindFilter(RecordType.GIFT, true, List.of());
            }
            EventGroup group = EventGroup.from(v);
            if (group != null) {
                List<EventCategory> categories = Arrays.stream(EventCategory.values())
                        .filter(c -> c.getGroup() == group)
                        .toList();
                return new KindFilter(RecordType.EVENT, false, categories);
            }
            EventCategory single = EventCategory.from(v);
            if (single != null) {
                return new KindFilter(RecordType.EVENT, false, List.of(single));
            }
            return ALL;
        }
    }
}
