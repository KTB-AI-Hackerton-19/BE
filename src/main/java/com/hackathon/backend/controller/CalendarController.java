package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.calendar.CalendarDayResponse;
import com.hackathon.backend.dto.calendar.CalendarResponse;
import com.hackathon.backend.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캘린더", description = "마음 캘린더 화면. 받은 마음(RECEIVED)과 답례 알림(TO_GIVE)을 날짜별로 묶어서 조회")
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Operation(
            summary = "월별 캘린더 조회",
            description = "캘린더 그리드를 그리기 위한 월 단위 조회. year/month를 생략하면 오늘 기준 년월을 쓴다. "
                    + "이전/다음 달 이동은 year/month를 바꿔 다시 호출한다(월 단위 네비게이션). "
                    + "각 날짜의 receivedCount로 이모지 표시, toGiveCount > 0이면 답례 알림 dot을 찍으면 되고, "
                    + "events에 선물명·금액·이유까지 들어 있어 날짜 상세 패널('이날의 마음')도 추가 호출 없이 그릴 수 있다. "
                    + "이벤트가 없는 날짜는 days에 아예 포함되지 않는다."
    )
    @GetMapping
    public ApiResponse<CalendarResponse> getMonth(
            @Parameter(description = "조회 연도 (생략 시 올해)", example = "2026") @RequestParam(required = false) Integer year,
            @Parameter(description = "조회 월 1~12 (생략 시 이번 달)", example = "8") @RequestParam(required = false) Integer month) {
        return ApiResponse.success(calendarService.getMonth(year, month));
    }

    @Operation(
            summary = "특정 날짜 조회",
            description = "캘린더에서 날짜를 클릭했을 때의 '이날의 마음' 상세. 월별 조회 응답만으로도 그릴 수 있지만, "
                    + "날짜 하나만 다시 불러오고 싶을 때 사용한다. events의 type이 RECEIVED면 받은 선물, TO_GIVE면 답례 알림이다."
    )
    @GetMapping("/days/{date}")
    public ApiResponse<CalendarDayResponse> getDay(
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)", example = "2026-08-18")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(calendarService.getDay(date));
    }
}
