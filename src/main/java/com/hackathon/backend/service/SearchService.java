package com.hackathon.backend.service;

import com.hackathon.backend.dto.PageResponse;
import com.hackathon.backend.dto.gift.GiftRecordResponse;
import com.hackathon.backend.dto.person.PersonResponse;
import com.hackathon.backend.dto.search.SearchResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상단바 통합 검색 — 사람과 마음 기록을 한 번에 찾는다. */
@Service
public class SearchService {

    private static final int DEFAULT_LIMIT = 10;

    private final PersonService personService;
    private final GiftRecordService giftRecordService;

    public SearchService(PersonService personService, GiftRecordService giftRecordService) {
        this.personService = personService;
        this.giftRecordService = giftRecordService;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String q, Integer limit) {
        int size = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, 50);

        if (q == null || q.isBlank()) {
            return new SearchResponse(q, List.of(), List.of());
        }

        List<PersonResponse> people = personService.list(q).stream().limit(size).toList();
        PageResponse<GiftRecordResponse> records = giftRecordService.search(
                null, null, null, null, null, null, null, q, "latest", 0, size);

        return new SearchResponse(q.trim(), people, records.content());
    }
}
