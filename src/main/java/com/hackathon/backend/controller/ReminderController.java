package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.reminder.ReminderTaskResponse;
import com.hackathon.backend.service.ReminderTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "답례 알림",
        description = "기록의 답례 알림일(reminderDate)로부터 자동 생성되는 알림. 발송 처리는 서버 스케줄러가 10분 주기로 수행")
@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderTaskService reminderTaskService;

    public ReminderController(ReminderTaskService reminderTaskService) {
        this.reminderTaskService = reminderTaskService;
    }

    @Operation(
            summary = "답례 알림 목록 조회",
            description = "다가오는 답례 알림을 예정일이 가까운 순으로 조회한다. 상단 종 아이콘의 알림 목록이나 "
                    + "홈 화면 '다가오는 일정'에 사용. 각 항목에 daysLeft(남은 일수), 무엇에 대한 답례인지(gift), "
                    + "원본 기록의 감사 완료 여부(thanked)가 들어 있다."
    )
    @GetMapping
    public ApiResponse<List<ReminderTaskResponse>> list(
            @Parameter(description = "true면 이미 지난 알림까지 포함. 기본 false(오늘 이후만)", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean includePast,
            @Parameter(description = "최대 개수 (생략 시 전체)", example = "5") @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(reminderTaskService.list(includePast, limit));
    }

    @Operation(
            summary = "알림 실시간 구독 (SSE)",
            description = "서버가 답례 알림을 발송하는 순간 밀어준다. 프론트는 EventSource로 연결해 reminder 이벤트를 받아 "
                    + "토스트로 띄우면 된다. 연결 직후에는 아직 못 본 알림이 먼저 전달되므로, 접속하지 않은 동안 발송된 "
                    + "알림도 놓치지 않는다. EventSource는 헤더를 못 붙이므로 토큰은 ?token= 쿼리로 전달한다."
    )
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return reminderTaskService.subscribe();
    }

    @Operation(
            summary = "안 띄운 알림 조회 (토스트용)",
            description = "발송 처리는 됐지만 아직 화면에 표시하지 않은 알림을 반환한다. "
                    + "프론트가 주기적으로 호출해 토스트로 띄우고, 띄운 뒤에는 delivered API로 표시 완료를 알려야 "
                    + "같은 알림이 다시 뜨지 않는다."
    )
    @GetMapping("/undelivered")
    public ApiResponse<List<ReminderTaskResponse>> undelivered() {
        return ApiResponse.success(reminderTaskService.listUndelivered());
    }

    @Operation(
            summary = "알림 표시 완료 처리",
            description = "토스트로 띄운 알림을 표시 완료로 기록한다. 이후 안 띄운 알림 목록에서 제외된다."
    )
    @PostMapping("/{id}/delivered")
    public ApiResponse<Void> markDelivered(
            @Parameter(description = "표시 완료할 알림 ID") @PathVariable Long id) {
        reminderTaskService.markDelivered(id);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "기한 도래 알림 즉시 발송",
            description = "10분 주기 스케줄러를 기다리지 않고, 예정일이 지난 내 알림을 지금 바로 발송 처리한다. "
                    + "발송된 건수를 반환하며, 시연 중 알림이 도착하는 장면을 보여줄 때 사용한다."
    )
    @PostMapping("/dispatch")
    public ApiResponse<Integer> dispatchNow() {
        return ApiResponse.success(reminderTaskService.dispatchNow());
    }

    @Operation(
            summary = "특정 알림 즉시 발송 (시연용)",
            description = "예정일과 무관하게 지정한 알림 하나를 지금 발송 처리한다. "
                    + "미래 날짜로 잡힌 알림도 바로 띄워볼 수 있어 데모에 유용하다."
    )
    @PostMapping("/{id}/dispatch")
    public ApiResponse<ReminderTaskResponse> dispatchOne(
            @Parameter(description = "발송할 알림 ID") @PathVariable Long id) {
        return ApiResponse.success(reminderTaskService.dispatchOne(id));
    }
}
