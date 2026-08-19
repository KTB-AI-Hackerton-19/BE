package com.hackathon.backend.service;

import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.dto.PageResponse;
import com.hackathon.backend.dto.dashboard.AgentInsightResponse;
import com.hackathon.backend.dto.dashboard.DashboardResponse;
import com.hackathon.backend.dto.dashboard.DashboardStatsResponse;
import com.hackathon.backend.dto.gift.GiftRecordResponse;
import com.hackathon.backend.dto.recommendation.RecommendationResponse;
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
                ? DEFAULT_RECOMMENDATION_LIMIT : Math.min(recommendationLimit, 10);
        List<RecommendationResponse> recommendations = recommendationService.listForPerson(
                insight != null ? insight.personId() : null, recommendCount, false);

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
        record Candidate(String type, Person person, LocalDate date) {
        }

        Candidate birthday = personRepository.findByUser_UsernameOrderByNameAsc(username).stream()
                .filter(p -> p.getBirthday() != null)
                .map(p -> new Candidate("BIRTHDAY", p, nextOccurrence(p.getBirthday(), today)))
                .min(Comparator.comparing(Candidate::date))
                .orElse(null);

        ReminderTask nextReminder = reminderTaskRepository
                .findByUser_UsernameAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(username, today)
                .stream()
                .filter(task -> task.getPerson() != null)
                .findFirst()
                .orElse(null);
        Candidate reminder = nextReminder == null ? null
                : new Candidate("REMINDER", nextReminder.getPerson(), nextReminder.getScheduledAt());

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
        String shortName = shortName(picked.person().getName());
        boolean isBirthday = "BIRTHDAY".equals(picked.type());

        String title = isBirthday
                ? "%s님의 생일이 %s 남았어요".formatted(shortName, describe(daysLeft))
                : "%s님에게 답례할 날이 %s 남았어요".formatted(shortName, describe(daysLeft));
        String message = isBirthday
                ? "지난번에 받은 마음을 참고해, 부담 없이 마음을 전할 선물을 준비해볼까요?"
                : "받았던 마음을 잊지 않도록 지금 답례 선물을 골라볼까요?";
        String caption = isBirthday ? shortName + " 생일" : shortName + " 답례";

        return new AgentInsightResponse(
                picked.type(), picked.person().getId(), picked.person().getName(), picked.date(), daysLeft,
                title, message, MONTH_LABELS[picked.date().getMonthValue() - 1], picked.date().getDayOfMonth(), caption);
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
