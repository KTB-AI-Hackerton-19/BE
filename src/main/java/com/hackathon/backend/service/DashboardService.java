package com.hackathon.backend.service;

import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.dto.PageResponse;
import com.hackathon.backend.dto.dashboard.AgentInsightResponse;
import com.hackathon.backend.dto.dashboard.DashboardResponse;
import com.hackathon.backend.dto.dashboard.DashboardStatsResponse;
import com.hackathon.backend.dto.gift.GiftRecordResponse;
import com.hackathon.backend.dto.recommendation.PersonRecommendationResponse;
import com.hackathon.backend.domain.GiftRecordStatus;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.PersonRepository;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.security.SecurityUtils;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈 화면 전체(통계 / 에이전트 카드 / 최근 마음 / 추천)를 한 번에 만들어주는 서비스. */
@Service
public class DashboardService {

    private static final int DEFAULT_RECENT_LIMIT = 4;
    private static final int DEFAULT_RECOMMENDATION_LIMIT = 3;

    /**
     * 홈에서 내려줄 추천 그룹(사람) 수 상한 — <b>한 명</b>.
     *
     * <p>그룹마다 AI를 한 번씩 부르므로 상한이 없으면 같은 날짜에 여러 명이 몰렸을 때 홈 진입이 그만큼 느려진다.
     * 화면도 "가장 가까운 한 명에게 무엇을 줄까"를 보여주는 카드 3장이라 한 명이면 충분하다.
     * 같은 날짜의 나머지 사람까지 필요하면 화면에서 {@code GET /api/recommendations}를 따로 부르면 된다.</p>
     */
    private static final int MAX_DASHBOARD_RECOMMENDATION_GROUPS = 1;
    private static final String[] MONTH_LABELS =
            {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};

    private final GiftRecordRepository giftRecordRepository;
    private final PersonRepository personRepository;
    private final ReminderTaskRepository reminderTaskRepository;
    private final GiftRecordService giftRecordService;
    private final RecommendationService recommendationService;

    public DashboardService(GiftRecordRepository giftRecordRepository, PersonRepository personRepository,
                            ReminderTaskRepository reminderTaskRepository, GiftRecordService giftRecordService,
                            RecommendationService recommendationService) {
        this.giftRecordRepository = giftRecordRepository;
        this.personRepository = personRepository;
        this.reminderTaskRepository = reminderTaskRepository;
        this.giftRecordService = giftRecordService;
        this.recommendationService = recommendationService;
    }

    @Transactional
    public DashboardResponse getDashboard(Integer recentLimit, Integer recommendationLimit) {
        String username = SecurityUtils.getCurrentUsername();
        LocalDate today = LocalDate.now();

        DashboardStatsResponse stats = buildStats(username, today);
        AgentInsightResponse insight = buildInsight(username, today);

        int recent = (recentLimit == null || recentLimit <= 0) ? DEFAULT_RECENT_LIMIT : Math.min(recentLimit, 20);
        PageResponse<GiftRecordResponse> recentRecords =
                giftRecordService.search(null, null, null, null, null, null, null, null, null, null, "latest", 0, recent);

        int recommendCount = (recommendationLimit == null || recommendationLimit <= 0)
                ? DEFAULT_RECOMMENDATION_LIMIT : Math.min(recommendationLimit, RecommendationService.MAX_LIMIT);
        // 추천 대상 선정은 RecommendationService 한 곳에서만 판단한다.
        // 대시보드가 따로 고르면 홈과 추천 목록이 서로 다른 사람을 추천하게 된다.
        // 다만 그룹마다 AI를 부르므로 홈에서는 수를 제한해 첫 진입이 느려지지 않게 한다.
        List<PersonRecommendationResponse> recommendations =
                recommendationService.list(recommendCount, false, MAX_DASHBOARD_RECOMMENDATION_GROUPS);

        return new DashboardResponse(today, stats, insight, recentRecords.content(), recommendations);
    }

    private DashboardStatsResponse buildStats(String username, LocalDate today) {
        long totalRecords = giftRecordRepository.countByUser_UsernameAndStatus(username, GiftRecordStatus.CONFIRMED);
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        long recordsThisMonth = giftRecordRepository.countByUser_UsernameAndStatusAndCreatedAtGreaterThanEqual(
                username, GiftRecordStatus.CONFIRMED, monthStart);
        long totalPeople = personRepository.countByUser_Username(username);
        long upcoming = reminderTaskRepository.countByUser_UsernameAndScheduledAtGreaterThanEqual(username, today);

        Integer daysToNearest = reminderTaskRepository
                .findByUser_UsernameAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(username, today)
                .stream()
                .findFirst()
                .map(task -> (int) ChronoUnit.DAYS.between(today, task.getScheduledAt()))
                .orElse(null);

        return new DashboardStatsResponse(
                totalRecords, totalRecords + "개",
                recordsThisMonth, "이번 달 +" + recordsThisMonth,
                totalPeople, totalPeople + "명",
                upcoming, upcoming + "개",
                daysToNearest,
                daysToNearest == null ? "예정된 일정이 없어요"
                        : daysToNearest == 0 ? "오늘이 바로 그날이에요" : "가장 가까운 일정 " + daysToNearest + "일 후");
    }

    /**
     * "마음 에이전트가 발견했어요" 카드.
     * 다가오는 생일과 다가오는 답례일 중 더 가까운 쪽을 골라 문구까지 만들어 내려준다. 둘 다 없으면 null.
     */
    private AgentInsightResponse buildInsight(String username, LocalDate today) {
        // personId는 사람으로 등록되지 않은 기록이면 null이다(경조사 하객 등). 이름만 있어도 카드는 띄운다.
        record Candidate(String type, Long personId, String name, LocalDate date) {
        }

        Candidate birthday = personRepository.findByUser_UsernameOrderByNameAsc(username).stream()
                .filter(p -> p.getBirthday() != null)
                .map(p -> new Candidate("BIRTHDAY", p.getId(), p.getName(), nextOccurrence(p.getBirthday(), today)))
                .min(Comparator.comparing(Candidate::date))
                .orElse(null);

        ReminderTask nextReminder = reminderTaskRepository
                .findByUser_UsernameAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(username, today)
                .stream()
                .filter(task -> reminderName(task) != null)
                .findFirst()
                .orElse(null);
        Candidate reminder = nextReminder == null ? null
                : new Candidate("REMINDER",
                        nextReminder.getPerson() != null ? nextReminder.getPerson().getId() : null,
                        reminderName(nextReminder), nextReminder.getScheduledAt());

        Candidate picked;
        if (birthday == null) {
            picked = reminder;
        } else if (reminder == null) {
            picked = birthday;
        } else {
            picked = birthday.date().isAfter(reminder.date()) ? reminder : birthday;
        }
        if (picked == null) {
            return null;
        }

        int daysLeft = (int) ChronoUnit.DAYS.between(today, picked.date());
        String shortName = shortName(picked.name());
        boolean isBirthday = "BIRTHDAY".equals(picked.type());

        String title = isBirthday
                ? "%s님의 생일이 %s 남았어요".formatted(shortName, describe(daysLeft))
                : "%s님에게 답례할 날이 %s 남았어요".formatted(shortName, describe(daysLeft));
        String message = isBirthday
                ? "지난번에 받은 마음을 참고해, 부담 없이 마음을 전할 선물을 준비해볼까요?"
                : "받았던 마음을 잊지 않도록 지금 답례 선물을 골라볼까요?";
        String caption = isBirthday ? shortName + " 생일" : shortName + " 답례";

        return new AgentInsightResponse(
                picked.type(), picked.personId(), picked.name(), picked.date(), daysLeft,
                title, message, MONTH_LABELS[picked.date().getMonthValue() - 1], picked.date().getDayOfMonth(), caption);
    }

    /**
     * 알림 카드에 쓸 이름. 사람으로 등록된 경우가 우선이고, 아니면 기록에 적힌 이름을 쓴다.
     *
     * <p>예전에는 {@code getPerson() != null}로 걸러서, 경조사처럼 사람 미등록 기록에 딸린 답례일은
     * 아무리 코앞이어도 대시보드에 아예 안 떴다.</p>
     */
    private String reminderName(ReminderTask task) {
        if (task.getPerson() != null) {
            return task.getPerson().getName();
        }
        return task.getGiftRecord() != null ? task.getGiftRecord().displayName() : null;
    }

    /** "김민수" → "민수" (디자인의 "민수님의 생일이…" 문구용). 두 글자 이하 이름은 그대로 둔다. */
    private String shortName(String name) {
        if (name == null || name.isBlank()) {
            return "이분";
        }
        return name.length() >= 3 ? name.substring(1) : name;
    }

    private String describe(int daysLeft) {
        if (daysLeft == 0) {
            return "오늘";
        }
        if (daysLeft >= 25 && daysLeft <= 35) {
            return "한 달";
        }
        if (daysLeft >= 6 && daysLeft <= 8) {
            return "일주일";
        }
        return daysLeft + "일";
    }

    /** 생일처럼 매년 돌아오는 날짜의 "다음 도래일" 계산 (2/29는 평년에 2/28로 처리). */
    private LocalDate nextOccurrence(LocalDate anchor, LocalDate today) {
        LocalDate thisYear;
        try {
            thisYear = anchor.withYear(today.getYear());
        } catch (DateTimeException e) {
            thisYear = LocalDate.of(today.getYear(), 2, 28);
        }
        return thisYear.isBefore(today) ? thisYear.plusYears(1) : thisYear;
    }
}
