package com.hackathon.backend.service;

import com.hackathon.backend.domain.CustomRelationship;
import com.hackathon.backend.domain.Relationship;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.relationship.RelationshipRequest;
import com.hackathon.backend.dto.relationship.RelationshipResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.CustomRelationshipRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.SecurityUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관계 드롭다운의 원본. <b>기본 9종({@link Relationship}) + 그 사용자가 추가한 것</b>을 합쳐서 다룬다.
 *
 * <p>기본 9종은 모두에게 같으므로 가입 시 복제하지 않는다(카테고리와 다른 점). 커스텀만 테이블에 쌓고
 * 목록을 만들 때 앞에 기본 9종을 붙인다 — 기본 항목의 순서·라벨을 바꿔도 사용자 데이터 마이그레이션이 없다.</p>
 */
@Service
public class RelationshipService {

    private static final Logger log = LoggerFactory.getLogger(RelationshipService.class);

    private final CustomRelationshipRepository customRelationshipRepository;
    private final UserRepository userRepository;

    public RelationshipService(CustomRelationshipRepository customRelationshipRepository,
                               UserRepository userRepository) {
        this.customRelationshipRepository = customRelationshipRepository;
        this.userRepository = userRepository;
    }

    /** 기본 9종이 먼저, 내가 추가한 것이 뒤. 드롭다운은 이 순서 그대로 그리면 된다. */
    @Transactional(readOnly = true)
    public List<RelationshipResponse> list() {
        String username = SecurityUtils.getCurrentUsername();
        List<RelationshipResponse> options = new ArrayList<>(
                Arrays.stream(Relationship.values()).map(RelationshipResponse::of).toList());
        customRelationshipRepository.findByUser_UsernameOrderByIdAsc(username).stream()
                .map(RelationshipResponse::of)
                .forEach(options::add);
        return options;
    }

    /**
     * 관계 추가. 기본 9종과 겹치는 이름은 409로 막는다 — 통과시키면 드롭다운에 "친구"가 두 줄로 뜨고,
     * 둘 중 어느 쪽을 골라도 저장되는 값이 같아 구분되지 않는다. enum 이름("FRIEND")으로 보내도 같게 본다.
     *
     * <p>그 외에는 이름을 손대지 않는다. "동호회"든 "러닝 크루"든 사용자가 적은 그대로 항목이 되고,
     * 그 값이 그대로 사람·기록에 저장된다.</p>
     */
    @Transactional
    public RelationshipResponse create(RelationshipRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String name = request.name().trim();
        if (Relationship.exactMatch(name) != null) {
            throw new CustomException(ErrorCode.DUPLICATE_RELATIONSHIP,
                    "'%s'는 기본으로 제공되는 관계라 추가할 수 없습니다.".formatted(Relationship.exactMatch(name).getLabel()));
        }
        if (customRelationshipRepository.existsByUser_UsernameAndName(username, name)) {
            throw new CustomException(ErrorCode.DUPLICATE_RELATIONSHIP);
        }

        CustomRelationship created = customRelationshipRepository.save(new CustomRelationship(user, name));
        return RelationshipResponse.of(created);
    }

    /**
     * 들어온 관계 값을 <b>저장할 문자열</b>로 맞춘다. 사람·기록에 관계를 넣기 전에 반드시 통과시킨다.
     *
     * <p>하는 일은 "이 값이 그 사용자의 드롭다운에 실제로 있는가" 확인이 전부다 —
     * 기본 9종이거나(라벨/enum 이름 모두 허용), 그 사용자가 추가해둔 관계이거나.
     * 비슷한 값으로 맞춰주지 않는다. 목록에 없으면 {@code POST /api/relationships}로 추가한 뒤 고르는 것이
     * 이 구조의 흐름이고, 서버가 짐작해서 다른 값으로 바꾸면 사용자가 굳이 따로 만든 항목이 뭉개진다.</p>
     *
     * <p>목록에 없는 값은 null(미지정)이다. 화면이 드롭다운으로만 보내므로 정상 흐름에서는 나오지 않고,
     * 나온다면 프론트가 등록되지 않은 값을 보낸 것이라 로그를 남긴다. 저장 자체를 400으로 막지는 않는다 —
     * 관계는 선택 항목이라, 이것 때문에 기록 저장 전체가 실패하면 시연 중에 더 곤란해진다.</p>
     */
    @Transactional(readOnly = true)
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        Relationship exact = Relationship.exactMatch(trimmed);
        if (exact != null) {
            return exact.getLabel();
        }
        if (customRelationshipRepository.existsByUser_UsernameAndName(SecurityUtils.getCurrentUsername(), trimmed)) {
            return trimmed;
        }
        log.warn("등록되지 않은 관계 '{}' — 미지정으로 저장한다. 먼저 POST /api/relationships로 추가해야 한다.", trimmed);
        return null;
    }
}
