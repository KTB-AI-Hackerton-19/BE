package com.hackathon.backend.service;

import com.hackathon.backend.client.AiExtractionClient;
import com.hackathon.backend.client.AiExtractionResult;
import com.hackathon.backend.domain.Category;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.GiftRecordStatus;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.PageResponse;
import com.hackathon.backend.dto.gift.GiftRecordCreateRequest;
import com.hackathon.backend.dto.gift.GiftRecordExtractRequest;
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
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GiftRecordService {

    private final GiftRecordRepository giftRecordRepository;
    private final ReminderTaskRepository reminderTaskRepository;
    private final UserRepository userRepository;
    private final PersonService personService;
    private final CategoryService categoryService;
    private final S3PresignService s3PresignService;
    private final AiExtractionClient aiExtractionClient;

    public GiftRecordService(GiftRecordRepository giftRecordRepository, ReminderTaskRepository reminderTaskRepository,
                             UserRepository userRepository, PersonService personService,
                             CategoryService categoryService, S3PresignService s3PresignService,
                             AiExtractionClient aiExtractionClient) {
        this.giftRecordRepository = giftRecordRepository;
        this.reminderTaskRepository = reminderTaskRepository;
        this.userRepository = userRepository;
        this.personService = personService;
        this.categoryService = categoryService;
        this.s3PresignService = s3PresignService;
        this.aiExtractionClient = aiExtractionClient;
    }

    /** 기록 모달 저장 / 직접 등록. 보낸 사람은 personId 또는 이름으로 지정하며, 없는 이름이면 새 Person을 만든다. */
    @Transactional
    public GiftRecordResponse create(GiftRecordCreateRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = getUser(username);

        Person person = personService.resolveOrCreate(user, request.personId(), request.personName(), request.relation());
        Category category = categoryService.resolveOrFallback(request.categoryId(), request.category());

        GiftRecord record = GiftRecord.createConfirmed(
                user, person, category, trimToNull(request.occasion()), trimToNull(request.gift()),
                MoneyFormatter.parse(request.price()), request.date(), request.reminderDate(),
                Boolean.TRUE.equals(request.thanked()));
        giftRecordRepository.save(record);

        syncReminder(user, record);
        return toResponse(record);
    }

    /**
     * 업로드된 이미지의 imageKey로 조회용 presigned GET URL을 만들어 AI 서비스에 넘기고, 결과를 DRAFT로 저장한다.
     * 프론트는 이 응답을 확인/수정 폼의 초기값으로 그대로 쓰면 된다.
     */
    @Transactional
    public GiftRecordResponse extract(GiftRecordExtractRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = getUser(username);

        String imageUrl = s3PresignService.createGetUrl(request.imageKey());
        AiExtractionResult result = aiExtractionClient.extract(imageUrl);
        Category category = categoryService.resolveOrFallback(null, result.categoryName());

        GiftRecord record = GiftRecord.createDraft(
                user, request.imageKey(), result.senderName(), result.relationship(), category,
                result.occasion(), result.giftName(), result.amount(),
                result.receivedDate() != null ? result.receivedDate() : LocalDate.now(), null);
        giftRecordRepository.save(record);
        return toResponse(record);
    }

    /** 확인/수정 폼 저장(확정) 및 이후 수정. 보내지 않은 필드는 기존 값을 유지한다. */
    @Transactional
    public GiftRecordResponse update(Long id, GiftRecordUpdateRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = getUser(username);
        GiftRecord record = giftRecordRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.GIFT_RECORD_NOT_FOUND));

        Person person = personService.resolveOrCreateNullable(
                user, request.personId(), request.personName(), request.relation());
        Category category = categoryService.resolve(request.categoryId(), request.category());

        record.applyUpdate(person, category, trimToNull(request.occasion()), trimToNull(request.gift()),
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
                                                   Boolean thanked, GiftRecordStatus status,
                                                   LocalDate startDate, LocalDate endDate, String q,
                                                   String sort, int page, int size) {
        String username = SecurityUtils.getCurrentUsername();
        Long resolvedCategoryId = categoryId;
        if (resolvedCategoryId == null && categoryName != null && !categoryName.isBlank() && !"전체".equals(categoryName.trim())) {
            Category category = categoryService.resolve(null, categoryName);
            resolvedCategoryId = category != null ? category.getId() : -1L; // 존재하지 않는 카테고리 → 빈 결과
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), toSort(sort));
        Page<GiftRecord> result = giftRecordRepository.search(
                username, status, resolvedCategoryId, personId, thanked,
                startDate, endDate, trimToNull(q), pageable);

        List<GiftRecordResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return PageResponse.of(result, content);
    }

    @Transactional(readOnly = true)
    public GiftRecordResponse get(Long id) {
        String username = SecurityUtils.getCurrentUsername();
        GiftRecord record = giftRecordRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.GIFT_RECORD_NOT_FOUND));
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public List<GiftRecordResponse> listByPerson(Long personId) {
        String username = SecurityUtils.getCurrentUsername();
        return giftRecordRepository.findByUser_UsernameAndPerson_IdOrderByReceivedDateDescIdDesc(username, personId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public GiftRecordResponse toResponse(GiftRecord record) {
        String imageUrl = record.getImageKey() != null ? s3PresignService.createGetUrl(record.getImageKey()) : null;
        return GiftRecordResponse.from(record, imageUrl);
    }

    /** reminderDate가 있으면 ReminderTask를 생성/갱신하고, 없어졌으면 제거한다 (기록 1건 : 알림 1건). */
    private void syncReminder(User user, GiftRecord record) {
        LocalDate reminderDate = record.getReminderDate();
        ReminderTask existing = reminderTaskRepository.findByGiftRecord_Id(record.getId()).orElse(null);

        if (reminderDate == null) {
            if (existing != null) {
                reminderTaskRepository.delete(existing);
            }
            return;
        }
        if (existing == null) {
            reminderTaskRepository.save(new ReminderTask(user, record.getPerson(), record, reminderDate));
            return;
        }
        existing.reschedule(record.getPerson(), reminderDate);
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
