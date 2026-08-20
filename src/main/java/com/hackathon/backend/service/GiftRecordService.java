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
import com.hackathon.backend.domain.Relationship;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.PageResponse;
import com.hackathon.backend.dto.gift.EventCategoryResponse;
import com.hackathon.backend.dto.gift.GiftRecordCreateRequest;
import com.hackathon.backend.dto.gift.GiftRecordExtractRequest;
import com.hackathon.backend.dto.gift.GiftRecordExtractResponse;
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
    private final GoogleCalendarService googleCalendarService;
    private final UserRepository userRepository;
    private final PersonService personService;
    private final CategoryService categoryService;
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
                             CategoryService categoryService, S3PresignService s3PresignService,
                             AiExtractionClient aiExtractionClient,
                             GoogleCalendarService googleCalendarService,
                             @Value("${ai.service.pre-request-delay-ms:15000}") long preRequestDelayMs) {
        this.giftRecordRepository = giftRecordRepository;
        this.reminderTaskRepository = reminderTaskRepository;
        this.userRepository = userRepository;
        this.personService = personService;
        this.categoryService = categoryService;
        this.s3PresignService = s3PresignService;
        this.aiExtractionClient = aiExtractionClient;
        this.googleCalendarService = googleCalendarService;
        this.preRequestDelayMs = preRequestDelayMs;
    }

    /** 기록 모달 저장 / 직접 등록. 보낸 사람은 personId 또는 이름으로 지정하며, 없는 이름이면 새 Person을 만든다. */
    @Transactional
    public GiftRecordResponse create(GiftRecordCreateRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = getUser(username);

        validateDates(request.date(), request.reminderDate());

        Person person = personService.resolveOrCreate(user, request.personId(), request.personName(), request.relation());

        RecordType recordType = RecordType.parseOrDefault(request.recordType());
        Category category = null;
        EventCategory eventCategory = null;
        if (recordType == RecordType.EVENT) {
            eventCategory = requireEventCategory(request.eventCategory());
        } else {
            category = categoryService.resolveOrFallback(request.categoryId(), request.category());
        }

        GiftRecord record = GiftRecord.createConfirmed(
                user, person, recordType, category, eventCategory, request.eventDate(),
                trimToNull(request.occasion()), trimToNull(request.gift()),
                MoneyFormatter.parse(request.price()), request.date(), request.reminderDate(),
                Boolean.TRUE.equals(request.thanked()));
        giftRecordRepository.save(record);

        syncReminder(user, record);
        return toResponse(record);
    }

    /**
     * 업로드된 이미지의 imageKey로 조회용 presigned GET URL을 만들어 AI 서비스에 넘기고, 결과를 DRAFT로 저장한다.
     *
     * <p>이미지는 한 번에 <b>한 장</b>이다. <b>그 한 장에 여러 명이 있으면</b>(봉투가 여러 개 찍힌 사진,
     * 방명록, 단체 메시지 캡처) AI가 사람 목록을
     * 돌려준다. 그 길이가 곧 사람 수이고, 사람 수만큼 DRAFT를 만들어 전부 응답에 실어 보낸다.</p>
     *
     * <p>이때 AI 값이 <b>경조사</b>로 판정되면 그 사진의 사람 전원이 같은 경조사 유형(고정 7종 중 하나)으로
     * 묶인다. 경조사는 더 이상 사용자별 카테고리 row가 아니라 코드에 고정된 값이라, 따로 만들거나 찾을 필요가 없다.</p>
     */
    @Transactional
    public GiftRecordExtractResponse extract(GiftRecordExtractRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = getUser(username);

        String imageUrl = s3PresignService.createGetUrl(request.imageKey());
        awaitUpload();
        AiExtractionBatch batch = aiExtractionClient.extract(imageUrl);
        EventCategory eventCategory = batch.eventCategory();
        RecordType recordType = eventCategory != null ? RecordType.EVENT : RecordType.GIFT;

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
                    Relationship.from(result.relationship()), result.age(), Gender.from(result.gender()),
                    recordType, category, eventCategory, batch.eventDate(), occasion, result.giftName(),
                    result.amount(), receivedDate, receivedDate.plusDays(DEFAULT_REMINDER_OFFSET_DAYS));
            giftRecordRepository.save(record);

            // AI가 아니라 더미로 채워졌으면 응답에 그대로 실어 보낸다(로그만으로는 프론트가 알 수 없다).
            // imageUrl은 사람마다 같은 이미지라 위에서 만든 것을 그대로 쓴다(사람 수만큼 서명하지 않는다).
            records.add(GiftRecordResponse.from(record, imageUrl, batch.fallback(), batch.fallbackReason()));
        }

        log.info("AI 추출 완료 — {}명, 경조사: {}", records.size(), eventCategory != null ? eventCategory.getLabel() : "없음");

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

        Person person = personService.resolveOrCreateNullable(
                user, request.personId(), request.personName(), request.relation());

        RecordType recordType = request.recordType() != null ? RecordType.parseOrDefault(request.recordType()) : null;
        Category category = categoryService.resolve(request.categoryId(), request.category());
        EventCategory eventCategory = EventCategory.from(request.eventCategory());

        RecordType effectiveType = recordType != null ? recordType : record.getRecordType();
        if (effectiveType == RecordType.EVENT) {
            boolean requestedInvalidEventCategory = request.eventCategory() != null && eventCategory == null;
            EventCategory effectiveEventCategory = eventCategory != null ? eventCategory : record.getEventCategory();
            if (effectiveEventCategory == null || requestedInvalidEventCategory) {
                throw new CustomException(ErrorCode.INVALID_INPUT, "경조사 유형을 정확히 선택해주세요.");
            }
        }

        record.applyUpdate(person, recordType, category, eventCategory, request.eventDate(),
                trimToNull(request.occasion()), trimToNull(request.gift()),
                MoneyFormatter.parse(request.price()), request.date(), request.reminderDate(), request.thanked());

        if (request.confirm() == null || request.confirm()) {
            record.confirm();
        }

        syncReminder(user, record);
        return toResponse(record);
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

    @Transactional
    public void delete(Long id) {
        String username = SecurityUtils.getCurrentUsername();
        GiftRecord record = giftRecordRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.GIFT_RECORD_NOT_FOUND));
        reminderTaskRepository.deleteByGiftRecord_Id(record.getId());
        giftRecordRepository.delete(record);
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
    private EventCategory requireEventCategory(String raw) {
        EventCategory eventCategory = EventCategory.from(raw);
        if (eventCategory == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "경조사 유형을 정확히 선택해주세요.");
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
            throw new CustomException(ErrorCode.INVALID_INPUT, "받은 날짜는 미래일 수 없습니다.");
        }
        if (reminderDate != null && reminderDate.isBefore(today)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "답례 알림일은 오늘 이후로 정해주세요.");
        }
        if (receivedDate != null && reminderDate != null && reminderDate.isBefore(receivedDate)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "답례 알림일은 받은 날짜보다 앞설 수 없습니다.");
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
