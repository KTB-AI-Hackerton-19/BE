package com.hackathon.backend.service;

import com.hackathon.backend.domain.Category;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.GiftRecordStatus;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.dto.calendar.CalendarDayResponse;
import com.hackathon.backend.dto.calendar.CalendarEventResponse;
import com.hackathon.backend.dto.calendar.CalendarResponse;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.security.SecurityUtils;
import com.hackathon.backend.support.MoneyFormatter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

    private static final String TYPE_RECEIVED = "RECEIVED";
    private static final String TYPE_TO_GIVE = "TO_GIVE";
    private static final String REMINDER_EMOJI = "🔔";
    private static final String REMINDER_COLOR = "coral";
    private static final String DEFAULT_EMOJI = "🎁";
    private static final String DEFAULT_COLOR = "blue";

    private final GiftRecordRepository giftRecordRepository;
    private final ReminderTaskRepository reminderTaskRepository;

    public CalendarService(GiftRecordRepository giftRecordRepository, ReminderTaskRepository reminderTaskRepository) {
        this.giftRecordRepository = giftRecordRepository;
        this.reminderTaskRepository = reminderTaskRepository;
    }

    /** 월 단위 조회. year/month 생략 시 오늘 기준. 받은 마음(RECEIVED)과 답례 알림(TO_GIVE)을 날짜별로 묶는다. */
    @Transactional(readOnly = true)
    public CalendarResponse getMonth(Integer year, Integer month) {
        LocalDate today = LocalDate.now();
        int y = year != null ? year : today.getYear();
        int m = month != null ? month : today.getMonthValue();

        YearMonth yearMonth = YearMonth.of(y, m);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        Map<LocalDate, List<CalendarEventResponse>> byDate = collectEvents(start, end);

        List<CalendarDayResponse> days = byDate.entrySet().stream()
                .map(entry -> toDay(entry.getKey(), entry.getValue()))
                .toList();

        return new CalendarResponse(y, m, days);
    }

    /** 특정 날짜 클릭 시의 "이날의 마음" 상세. 월 조회 응답만으로도 그릴 수 있지만 단독 호출용으로 제공한다. */
    @Transactional(readOnly = true)
    public CalendarDayResponse getDay(LocalDate date) {
        List<CalendarEventResponse> events = collectEvents(date, date).getOrDefault(date, List.of());
        return toDay(date, events);
    }

    private Map<LocalDate, List<CalendarEventResponse>> collectEvents(LocalDate start, LocalDate end) {
        String username = SecurityUtils.getCurrentUsername();
        Map<LocalDate, List<CalendarEventResponse>> byDate = new TreeMap<>();

        giftRecordRepository.findByUser_UsernameAndStatusAndReceivedDateBetweenOrderByReceivedDateAsc(
                        username, GiftRecordStatus.CONFIRMED, start, end)
                .forEach(record -> byDate
                        .computeIfAbsent(record.getReceivedDate(), d -> new ArrayList<>())
                        .add(toReceivedEvent(record)));

        reminderTaskRepository.findByUser_UsernameAndScheduledAtBetweenOrderByScheduledAtAsc(username, start, end)
                .forEach(task -> byDate
                        .computeIfAbsent(task.getScheduledAt(), d -> new ArrayList<>())
                        .add(toGiveEvent(task)));

        return byDate;
    }

    private CalendarDayResponse toDay(LocalDate date, List<CalendarEventResponse> events) {
        List<CalendarEventResponse> ordered = events.stream()
                .sorted((a, b) -> Boolean.compare(TYPE_TO_GIVE.equals(a.type()), TYPE_TO_GIVE.equals(b.type())))
                .toList();
        int received = (int) ordered.stream().filter(e -> TYPE_RECEIVED.equals(e.type())).count();
        return new CalendarDayResponse(date, received, ordered.size() - received, ordered);
    }

    private CalendarEventResponse toReceivedEvent(GiftRecord record) {
        Person person = record.getPerson();
        Category category = record.getCategory();
        return new CalendarEventResponse(
                record.getId(), TYPE_RECEIVED, record.getReceivedDate(), record.getId(),
                person != null ? person.getId() : null,
                person != null ? person.getName() : record.getExtractedSenderName(),
                record.getGiftName(), record.getOccasion(),
                category != null ? category.getName() : null,
                category != null ? category.getEmoji() : DEFAULT_EMOJI,
                category != null ? category.getColor() : DEFAULT_COLOR,
                record.getAmount(), MoneyFormatter.format(record.getAmount()), record.isThanked());
    }

    private CalendarEventResponse toGiveEvent(ReminderTask task) {
        Person person = task.getPerson();
        GiftRecord record = task.getGiftRecord();
        Category category = record != null ? record.getCategory() : null;
        return new CalendarEventResponse(
                task.getId(), TYPE_TO_GIVE, task.getScheduledAt(),
                record != null ? record.getId() : null,
                person != null ? person.getId() : null,
                person != null ? person.getName() : null,
                record != null ? record.getGiftName() : null,
                record != null ? record.getOccasion() : null,
                category != null ? category.getName() : null,
                REMINDER_EMOJI, REMINDER_COLOR,
                record != null ? record.getAmount() : null,
                record != null ? MoneyFormatter.format(record.getAmount()) : null,
                record != null && record.isThanked());
    }
}
