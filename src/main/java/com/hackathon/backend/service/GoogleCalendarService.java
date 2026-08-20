package com.hackathon.backend.service;

import com.hackathon.backend.client.AiCalendarClient;
import com.hackathon.backend.client.AiCalendarClient.CalendarRegistration;
import com.hackathon.backend.client.AiConfirmDtos.CalendarDraft;
import com.hackathon.backend.client.AiConfirmDtos.ConfirmRequest;
import com.hackathon.backend.client.AiConfirmDtos.GiftData;
import com.hackathon.backend.client.GoogleOAuthClient;
import com.hackathon.backend.client.GoogleOAuthClient.TokenResponse;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.GoogleCredential;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.Relationship;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.integration.GoogleAuthorizeUrlResponse;
import com.hackathon.backend.dto.integration.GoogleCalendarStatusResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.GoogleCredentialRepository;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.JwtProvider;
import com.hackathon.backend.security.SecurityUtils;
import com.hackathon.backend.support.MoneyFormatter;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구글 캘린더 연동. 크게 두 가지 일을 한다.
 *
 * <ol>
 *   <li><b>연동</b> — 동의 URL 발급 / 콜백 처리 / 상태 조회 / 해제</li>
 *   <li><b>일정 동기화</b> — 답례일자(reminderDate)가 생기거나 바뀌면 구글 캘린더에 실제 일정을 만든다</li>
 * </ol>
 *
 * <p>실제 구글 Calendar API 호출은 AI 서비스가 대신한다(명세의 {@code /api/v1/agent/confirm}에
 * {@code google_access_token}을 넘기는 구조). 우리는 access token만 만들어 주면 된다.</p>
 */
@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

    /** 일정 시작 시각. 답례 준비는 하루 일과 중에 떠올라야 의미가 있어서 아침으로 고정한다. */
    private static final String EVENT_START_TIME = "10:00";
    private static final int EVENT_DURATION_MINUTES = 30;
    private static final String TIMEZONE = "Asia/Seoul";
    /** 당일 알림 + 하루 전 알림. 당일에만 울리면 준비할 시간이 없다. */
    private static final List<Integer> REMINDERS_MINUTES = List.of(0, 1440);
    private static final String DEFAULT_CALENDAR_ID = "primary";
    /** AI 명세상 gift_price는 0보다 커야 한다(exclusiveMinimum). 금액 없는 기록도 일정은 잡아야 하므로 최소값을 쓴다. */
    private static final int MIN_GIFT_PRICE = 1;

    private final GoogleCredentialRepository googleCredentialRepository;
    private final ReminderTaskRepository reminderTaskRepository;
    private final UserRepository userRepository;
    private final GoogleOAuthClient googleOAuthClient;
    private final AiCalendarClient aiCalendarClient;
    private final JwtProvider jwtProvider;
    private final String frontendRedirectUri;

    public GoogleCalendarService(GoogleCredentialRepository googleCredentialRepository,
                                 ReminderTaskRepository reminderTaskRepository,
                                 UserRepository userRepository,
                                 GoogleOAuthClient googleOAuthClient,
                                 AiCalendarClient aiCalendarClient,
                                 JwtProvider jwtProvider,
                                 @Value("${google.oauth.frontend-redirect-uri}") String frontendRedirectUri) {
        this.googleCredentialRepository = googleCredentialRepository;
        this.reminderTaskRepository = reminderTaskRepository;
        this.userRepository = userRepository;
        this.googleOAuthClient = googleOAuthClient;
        this.aiCalendarClient = aiCalendarClient;
        this.jwtProvider = jwtProvider;
        this.frontendRedirectUri = frontendRedirectUri;
    }

    // ---------------------------------------------------------------- 연동

    public GoogleAuthorizeUrlResponse authorizeUrl() {
        String username = SecurityUtils.getCurrentUsername();
        String state = jwtProvider.createOAuthStateToken(username);
        return new GoogleAuthorizeUrlResponse(googleOAuthClient.authorizationUrl(state));
    }

    /**
     * 구글 콜백 처리. 이 요청에는 우리 JWT가 없고 state만 있다.
     *
     * @return 프론트로 되돌려보낼 URL (성공/실패를 쿼리로 알려준다)
     */
    @Transactional
    public String handleCallback(String code, String state, String error) {
        if (error != null && !error.isBlank()) {
            // 사용자가 동의 화면에서 "취소"를 누른 경우가 대부분이라 에러로 취급하지 않는다.
            log.info("구글 연동이 취소되었습니다: {}", error);
            return frontendRedirectUri + "?google=denied";
        }
        String username = jwtProvider.usernameFromOAuthState(state);
        if (username == null) {
            log.warn("구글 콜백의 state가 유효하지 않습니다.");
            return frontendRedirectUri + "?google=invalid_state";
        }
        if (code == null || code.isBlank()) {
            return frontendRedirectUri + "?google=no_code";
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        try {
            TokenResponse token = googleOAuthClient.exchangeCode(code);
            if (token.refreshToken() == null) {
                // prompt=consent를 붙였는데도 안 왔다면 이미 승인된 앱이라는 뜻. 재연동을 유도한다.
                log.warn("구글이 refresh token을 주지 않았습니다. username={}", username);
                return frontendRedirectUri + "?google=no_refresh_token";
            }
            String email = googleOAuthClient.fetchEmail(token.accessToken());
            String scope = token.scope() == null ? googleOAuthClient.scope() : token.scope();

            GoogleCredential existing = googleCredentialRepository.findByUser_Id(user.getId()).orElse(null);
            if (existing == null) {
                googleCredentialRepository.save(new GoogleCredential(user, token.refreshToken(), email, scope));
            } else {
                // 다른 구글 계정으로 갈아탄 경우, 저장된 eventId는 전 계정의 일정을 가리킨다.
                // 끊어주지 않으면 새 계정에 없는 일정을 수정하려 해서 이후 동기화가 전부 조용히 실패한다.
                String previousEmail = existing.getGoogleEmail();
                boolean accountChanged = previousEmail != null && email != null
                        && !previousEmail.equalsIgnoreCase(email);
                existing.reconnect(token.refreshToken(), email, scope);
                if (accountChanged) {
                    int cleared = reminderTaskRepository.clearGoogleEventsOf(username);
                    log.info("구글 계정 변경({} -> {}) — 기존 일정 연결 {}건 해제. username={}",
                            previousEmail, email, cleared, username);
                }
            }
            log.info("구글 캘린더 연동 완료. username={} googleEmail={}", username, email);
            return frontendRedirectUri + "?google=connected";
        } catch (CustomException e) {
            log.warn("구글 연동 실패. username={} reason={}", username, e.getMessage());
            return frontendRedirectUri + "?google=failed";
        }
    }

    @Transactional(readOnly = true)
    public GoogleCalendarStatusResponse status() {
        boolean available = googleOAuthClient.isConfigured();
        return googleCredentialRepository.findByUser_Username(SecurityUtils.getCurrentUsername())
                .map(credential -> GoogleCalendarStatusResponse.of(credential, available))
                .orElseGet(() -> GoogleCalendarStatusResponse.disconnected(available));
    }

    /**
     * 연동 해제. 이미 구글 캘린더에 만들어진 일정은 <b>지우지 않는다</b>(권한을 버린 뒤라 지울 수 없다).
     * 대신 eventId 연결을 끊어서, 나중에 다시 연동하면 새 일정으로 다시 만들어지게 한다.
     */
    @Transactional
    public void disconnect() {
        String username = SecurityUtils.getCurrentUsername();
        reminderTaskRepository.clearGoogleEventsOf(username);
        googleCredentialRepository.deleteByUser_Username(username);
    }

    /** 회원 탈퇴 시 함께 정리한다. */
    @Transactional
    public void deleteAllOf(String username) {
        googleCredentialRepository.deleteByUser_Username(username);
    }

    // ------------------------------------------------------------ 일정 동기화

    /**
     * 답례일자를 구글 캘린더에 등록/갱신한다.
     *
     * <p><b>절대 예외를 던지지 않는다.</b> 마음 기록 저장은 성공했는데 캘린더 등록이 실패했다고
     * 전체가 롤백되면 사용자는 기록 자체를 잃는다. 연동이 없거나 AI가 죽어 있으면 조용히 건너뛴다.</p>
     *
     * <p>{@code REQUIRES_NEW}인 이유도 같다. 이 안에서 난 예외가 바깥 트랜잭션을
     * rollback-only로 오염시키지 않게 분리한다.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncEvent(User user, GiftRecord record, ReminderTask reminder) {
        String accessToken = accessTokenOrNull(user);
        if (accessToken == null) {
            return;
        }
        CalendarRegistration result = aiCalendarClient.confirm(
                buildConfirmRequest(record, reminder, accessToken));

        if (!result.registered()) {
            log.warn("구글 캘린더 등록 실패. recordId={} reason={}", record.getId(), result.error());
            return;
        }
        reminder.linkGoogleEvent(result.eventId(), result.htmlLink());
        log.info("구글 캘린더 등록 완료. recordId={} eventId={}", record.getId(), result.eventId());
    }

    /**
     * 연동된 사용자면 access token을, 아니면 null을 돌려준다.
     * refresh token이 폐기됐으면 그 사실을 기록해 화면이 재연동을 안내할 수 있게 한다.
     */
    private String accessTokenOrNull(User user) {
        if (!googleOAuthClient.isConfigured()) {
            return null;
        }
        GoogleCredential credential = googleCredentialRepository.findByUser_Id(user.getId()).orElse(null);
        if (credential == null || credential.isRevoked()) {
            return null;
        }
        try {
            return googleOAuthClient.refreshAccessToken(credential.getRefreshToken()).accessToken();
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.GOOGLE_REAUTH_REQUIRED) {
                credential.markRevoked(e.getMessage());
            }
            log.warn("구글 access token 갱신 실패. username={} reason={}", user.getUsername(), e.getMessage());
            return null;
        }
    }

    /**
     * 우리 답례일자를 그대로 일정 초안으로 만든다.
     *
     * <p>명세상 {@code calendar}를 생략하면 AI가 gift_data로 날짜를 <b>다시 계산</b>한다.
     * 그러면 사용자가 직접 고른 답례일자가 무시되므로 반드시 채워 보낸다.</p>
     */
    private ConfirmRequest buildConfirmRequest(GiftRecord record, ReminderTask reminder, String accessToken) {
        Person person = record.getPerson();
        // 사람으로 등록하지 않은 경조사 기록도 그대로 캘린더에 올라간다 — 이름은 기록에 적힌 것을 쓴다.
        String personName = record.displayName();
        String relationship = Relationship.displayLabel(record.displayRelationship());
        LocalDate reminderDate = reminder.getScheduledAt();

        GiftData giftData = new GiftData(
                blankToDefault(record.getGiftName(), "받은 선물"),
                record.getAmount() != null && record.getAmount() > 0 ? record.getAmount() : MIN_GIFT_PRICE,
                ageOf(person, record),
                genderOf(person, record),
                personName,
                relationship,
                record.getReceivedDate(),
                reminderDate,
                record.getOccasion());

        CalendarDraft calendar = new CalendarDraft(
                buildTitle(record, personName),
                buildDescription(record, personName),
                reminderDate,
                EVENT_START_TIME,
                EVENT_DURATION_MINUTES,
                TIMEZONE,
                REMINDERS_MINUTES,
                DEFAULT_CALENDAR_ID,
                reminderDate,
                // ⚠️ eventId를 실어 보내지만 AI 서비스는 지금 이걸 무시하고 항상 새 일정을 만든다
                // (AI-Service app/services/tasks/calendar.py가 create_event만 호출한다).
                // AI가 update_event를 쓰도록 고치기 전까지, 중복 방지는 호출부에서
                // "날짜가 바뀐 경우에만 부른다"로 막고 있다 — GiftRecordService.syncReminder 참고.
                reminder.getGoogleEventId());

        return new ConfirmRequest(
                // AI 서비스는 상태를 저장하지 않으므로 workflow_id는 매 호출 새로 만들어도 무방하다.
                UUID.randomUUID().toString(),
                giftData,
                calendar,
                true,
                true,
                accessToken,
                DEFAULT_CALENDAR_ID);
    }

    private String buildTitle(GiftRecord record, String personName) {
        if (personName != null && !personName.isBlank()) {
            return personName + "님 답례 준비";
        }
        // 이름 없는 기록에 "답례 준비"만 올리면 캘린더에서 무슨 일정인지 알 수 없다. 계기라도 붙여준다.
        String occasion = record.getOccasion();
        return occasion == null || occasion.isBlank() ? "답례 준비" : occasion + " 답례 준비";
    }

    private String buildDescription(GiftRecord record, String personName) {
        StringBuilder sb = new StringBuilder();
        String who = personName == null || personName.isBlank() ? "누군가" : personName + "님";
        sb.append(who).append("에게 받은 ");
        sb.append(blankToDefault(record.getGiftName(), "선물"));
        if (record.getAmount() != null && record.getAmount() > 0) {
            sb.append(" (").append(MoneyFormatter.format(record.getAmount())).append(")");
        }
        sb.append("에 대한 답례를 준비할 시간입니다.");
        if (record.getReceivedDate() != null) {
            sb.append("\n받은 날: ").append(record.getReceivedDate());
        }
        if (record.getOccasion() != null && !record.getOccasion().isBlank()) {
            sb.append("\n계기: ").append(record.getOccasion());
        }
        sb.append("\n\n마음장부에서 기록을 확인하세요.");
        return sb.toString();
    }

    /**
     * 나이. 등록된 사람의 생일이 우선이고, 없으면 AI가 사진에서 추정한 나이를 쓴다.
     *
     * <p>사람 미등록 기록은 생일이 있을 수 없어서 person만 보면 항상 비어 나간다.
     * AI 서비스는 이 값으로 답례 선물을 고르므로, 추정치라도 넘기는 편이 낫다.</p>
     */
    private Integer ageOf(Person person, GiftRecord record) {
        if (person != null && person.getBirthday() != null) {
            int age = Period.between(person.getBirthday(), LocalDate.now()).getYears();
            if (age >= 0 && age <= 120) {
                return age;
            }
        }
        Integer extracted = record.getExtractedAge();
        return extracted != null && extracted >= 0 && extracted <= 120 ? extracted : null;
    }

    /** 성별도 같은 이유로 AI 추정치까지 폴백한다(그동안 extractedGender를 뽑아만 두고 안 쓰고 있었다). */
    private String genderOf(Person person, GiftRecord record) {
        if (person != null && person.getGender() != null) {
            return person.getGender().name().toLowerCase();
        }
        return record.getExtractedGender() != null ? record.getExtractedGender().name().toLowerCase() : null;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
