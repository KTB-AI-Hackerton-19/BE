package com.hackathon.backend.dto.integration;

import com.hackathon.backend.domain.GoogleCredential;
import java.time.LocalDateTime;

/**
 * 마이페이지의 "구글 캘린더 연동" 상태.
 *
 * @param connected      연동됨 여부. google_credentials 행이 있고 폐기되지 않았으면 true.
 * @param googleEmail    연동된 구글 계정(어느 계정에 붙었는지 보여주기 위함). 조회 실패 시 null.
 * @param connectedAt    연동 시각.
 * @param reauthRequired 사용자가 구글 쪽에서 권한을 철회해 재연동이 필요한 상태.
 * @param available      서버에 구글 OAuth 설정이 있는지. false면 화면에서 버튼을 숨긴다.
 */
public record GoogleCalendarStatusResponse(
        boolean connected,
        String googleEmail,
        LocalDateTime connectedAt,
        boolean reauthRequired,
        boolean available) {

    public static GoogleCalendarStatusResponse disconnected(boolean available) {
        return new GoogleCalendarStatusResponse(false, null, null, false, available);
    }

    public static GoogleCalendarStatusResponse of(GoogleCredential credential, boolean available) {
        return new GoogleCalendarStatusResponse(
                !credential.isRevoked(),
                credential.getGoogleEmail(),
                credential.getConnectedAt(),
                credential.isRevoked(),
                available);
    }
}
