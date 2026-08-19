package com.hackathon.backend.service;

import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.user.UserResponse;
import com.hackathon.backend.dto.user.UserUpdateRequest;
import com.hackathon.backend.dto.user.WithdrawResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.CategoryRepository;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.PersonRepository;
import com.hackathon.backend.repository.RecommendedGiftRepository;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 프로필 조회·수정과 회원탈퇴. */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final GiftRecordRepository giftRecordRepository;
    private final ReminderTaskRepository reminderTaskRepository;
    private final RecommendedGiftRepository recommendedGiftRepository;
    private final CategoryRepository categoryRepository;
    private final S3PresignService s3PresignService;

    public UserService(UserRepository userRepository, PersonRepository personRepository,
                       GiftRecordRepository giftRecordRepository, ReminderTaskRepository reminderTaskRepository,
                       RecommendedGiftRepository recommendedGiftRepository, CategoryRepository categoryRepository,
                       S3PresignService s3PresignService) {
        this.userRepository = userRepository;
        this.personRepository = personRepository;
        this.giftRecordRepository = giftRecordRepository;
        this.reminderTaskRepository = reminderTaskRepository;
        this.recommendedGiftRepository = recommendedGiftRepository;
        this.categoryRepository = categoryRepository;
        this.s3PresignService = s3PresignService;
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return toResponse(currentUser());
    }

    /**
     * 프로필 수정. 이름과 프로필 이미지만 바꾼다.
     *
     * <p>이미지는 선물 사진과 같은 방식이다 — presigned URL로 S3에 직접 올린 뒤 받은 key를
     * {@code profileImageKey}로 넘긴다. 이미지 바이트가 백엔드를 지나가지 않는다.</p>
     */
    @Transactional
    public UserResponse update(UserUpdateRequest request) {
        User user = currentUser();

        if (Boolean.TRUE.equals(request.removeProfileImage())) {
            user.clearProfileImage();
        }
        if (request.profileImageKey() != null && !request.profileImageKey().isBlank()) {
            requireOwnKey(user.getUsername(), request.profileImageKey());
        }
        user.updateProfile(request.name(), request.profileImageKey());
        return toResponse(user);
    }

    /**
     * 회원탈퇴. 이 사용자의 데이터를 전부 지운다 — 되돌릴 수 없다.
     *
     * <p>삭제 순서가 중요하다. 다른 행을 참조하는 쪽부터 비워야 FK 제약에 걸리지 않는다:
     * 답례 알림(사람·기록 참조) → 기록(사람·카테고리 참조) → 추천(사람 참조) → 사람 → 카테고리 → 계정.</p>
     *
     * <p>S3 이미지는 마지막에 지우고, 실패해도 탈퇴는 진행한다.</p>
     */
    @Transactional
    public WithdrawResponse withdraw() {
        User user = currentUser();
        String username = user.getUsername();

        int records = (int) giftRecordRepository.countByUser_Username(username);
        int people = (int) personRepository.countByUser_Username(username);
        int reminders = reminderTaskRepository.findByUser_UsernameOrderByScheduledAtAsc(username).size();
        int categories = categoryRepository.findByUser_UsernameOrderByDisplayOrderAscIdAsc(username).size();

        reminderTaskRepository.deleteByUser_Username(username);
        giftRecordRepository.deleteByUser_Username(username);
        recommendedGiftRepository.deleteByUser_Username(username);
        personRepository.deleteByUser_Username(username);
        categoryRepository.deleteByUser_Username(username);
        userRepository.delete(user);

        int images = s3PresignService.deleteAllOf(username);

        log.info("회원탈퇴 '{}' — 기록 {} / 사람 {} / 알림 {} / 카테고리 {} / 이미지 {}",
                username, records, people, reminders, categories, images);
        return new WithdrawResponse(records, people, reminders, categories, images);
    }

    /** 남의 key를 넘겨 다른 사람 사진을 자기 프로필로 걸지 못하게 막는다. */
    private void requireOwnKey(String username, String key) {
        String expected = "%s/%s/".formatted(S3PresignService.PROFILE_PREFIX, username);
        if (!key.startsWith(expected)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "내 프로필 이미지 key가 아닙니다.");
        }
    }

    private User currentUser() {
        return userRepository.findByUsername(SecurityUtils.getCurrentUsername())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private UserResponse toResponse(User user) {
        String url = user.getProfileImageKey() == null
                ? null
                : s3PresignService.createGetUrl(user.getProfileImageKey());
        return new UserResponse(user.getUsername(), user.getName(), url);
    }
}
