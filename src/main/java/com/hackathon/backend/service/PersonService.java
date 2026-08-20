package com.hackathon.backend.service;

import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.Relationship;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.PageResponse;
import com.hackathon.backend.dto.person.PersonDeleteResponse;
import com.hackathon.backend.dto.person.PersonRequest;
import com.hackathon.backend.dto.person.PersonResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.PersonRepository;
import com.hackathon.backend.repository.RecommendedGiftRepository;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.SecurityUtils;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final GiftRecordRepository giftRecordRepository;
    private final ReminderTaskRepository reminderTaskRepository;
    private final RecommendedGiftRepository recommendedGiftRepository;

    public PersonService(PersonRepository personRepository, UserRepository userRepository,
                         GiftRecordRepository giftRecordRepository,
                         ReminderTaskRepository reminderTaskRepository,
                         RecommendedGiftRepository recommendedGiftRepository) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
        this.giftRecordRepository = giftRecordRepository;
        this.reminderTaskRepository = reminderTaskRepository;
        this.recommendedGiftRepository = recommendedGiftRepository;
    }

    @Transactional
    public PersonResponse create(PersonRequest request) {
        User user = getCurrentUser();
        String createName = request.name().trim();
        Person existing = firstByName(user.getUsername(), createName);
        Person person = existing != null
                ? existing
                : personRepository.save(new Person(user, createName, request.relation(), request.gender(),
                        request.birthday(), request.memo()));
        person.update(null, request.relation(), request.gender(), request.birthday(), request.memo());
        return PersonResponse.of(person, 0L, null, null);
    }

    @Transactional
    public PersonResponse update(Long id, PersonRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        Person person = personRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.PERSON_NOT_FOUND));

        person.update(request.name(), request.relation(), request.gender(), request.birthday(), request.memo());
        return buildSummary(username, person);
    }

    /** 페이징 없이 전체가 필요할 때(모달의 사람 선택 드롭다운 등). */
    @Transactional(readOnly = true)
    public List<PersonResponse> list(String q) {
        String username = SecurityUtils.getCurrentUsername();
        List<Person> people = (q == null || q.isBlank())
                ? personRepository.findByUser_UsernameOrderByNameAsc(username)
                : personRepository.findByUser_UsernameAndNameContainingIgnoreCaseOrderByNameAsc(username, q.trim());
        return decorate(username, people);
    }

    /**
     * 사람 목록 화면 — 이름 검색(q) + 페이징.
     * 사람이 수십 명을 넘어가면 한 번에 다 내려주는 게 부담이라 페이지로 끊는다.
     */
    @Transactional(readOnly = true)
    public PageResponse<PersonResponse> search(String q, int page, int size) {
        String username = SecurityUtils.getCurrentUsername();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Person> result = (q == null || q.isBlank())
                ? personRepository.findByUser_UsernameOrderByNameAsc(username, pageable)
                : personRepository.findByUser_UsernameAndNameContainingIgnoreCaseOrderByNameAsc(username, q.trim(), pageable);
        return PageResponse.of(result, decorate(username, result.getContent()));
    }

    /** 목록 항목에 마음 개수/최근 선물/다가오는 알림일을 채운다. 사람마다 쿼리를 던지지 않는다. */
    private List<PersonResponse> decorate(String username, List<Person> people) {
        Map<Long, Long> counts = new HashMap<>();
        giftRecordRepository.countGroupedByPerson(username)
                .forEach(row -> counts.put((Long) row[0], (Long) row[1]));

        Map<Long, GiftRecord> latestByPerson = new HashMap<>();
        Map<Long, LocalDate> upcomingByPerson = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (GiftRecord record : giftRecordRepository.findByUser_UsernameOrderByReceivedDateDescIdDesc(username)) {
            if (record.getPerson() == null) {
                continue;
            }
            Long personId = record.getPerson().getId();
            latestByPerson.putIfAbsent(personId, record);
            LocalDate reminderDate = record.getReminderDate();
            if (reminderDate != null && !reminderDate.isBefore(today)) {
                upcomingByPerson.merge(personId, reminderDate, (a, b) -> a.isBefore(b) ? a : b);
            }
        }

        return people.stream()
                .map(person -> PersonResponse.of(
                        person,
                        counts.getOrDefault(person.getId(), 0L),
                        latestByPerson.get(person.getId()),
                        upcomingByPerson.get(person.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonResponse get(Long id) {
        String username = SecurityUtils.getCurrentUsername();
        Person person = personRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.PERSON_NOT_FOUND));
        return buildSummary(username, person);
    }

    /** 사람 한 명 삭제. 그 사람의 기록·알림·추천이 함께 사라진다. 없는 id면 404. */
    @Transactional
    public PersonDeleteResponse delete(Long id) {
        String username = SecurityUtils.getCurrentUsername();
        Person person = personRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.PERSON_NOT_FOUND));
        return deletePeople(username, List.of(person));
    }

    /**
     * 사람 여러 명 삭제(목록에서 체크해 한 번에 지우는 용도).
     *
     * <p>없는 id나 다른 사용자의 사람은 <b>조용히 건너뛴다.</b> 10명을 골랐는데 그중 하나가 이미 지워졌다고
     * 전체를 실패시키면 사용자가 다시 고르는 수밖에 없어서, 지울 수 있는 것만 지우고 실제 건수를 돌려준다.</p>
     */
    @Transactional
    public PersonDeleteResponse deleteAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "삭제할 사람의 id를 하나 이상 보내주세요.");
        }
        String username = SecurityUtils.getCurrentUsername();
        return deletePeople(username, personRepository.findByIdInAndUser_Username(ids, username));
    }

    /**
     * 삭제 순서가 중요하다. 사람을 참조하는 쪽(추천·알림·기록)을 먼저 비워야 FK 제약에 걸리지 않는다.
     * 기록을 남겨두면 "보낸 사람이 사라진 기록"이 되어 목록·타임라인·통계가 전부 깨지므로 함께 지운다.
     */
    private PersonDeleteResponse deletePeople(String username, List<Person> people) {
        if (people.isEmpty()) {
            return PersonDeleteResponse.empty();
        }
        List<Long> personIds = people.stream().map(Person::getId).toList();
        List<GiftRecord> records = giftRecordRepository.findByUser_UsernameAndPerson_IdIn(username, personIds);

        // 1) 선물 추천 (person_id 참조)
        recommendedGiftRepository.deleteByUser_UsernameAndPerson_IdIn(username, personIds);

        // 2) 답례 알림 — 기록에 딸린 것을 먼저 지우고, 사람만 참조하는 잔여분을 정리한다.
        long reminders = 0;
        if (!records.isEmpty()) {
            reminders += reminderTaskRepository.deleteByGiftRecord_IdIn(records.stream().map(GiftRecord::getId).toList());
        }
        reminders += reminderTaskRepository.deleteByPerson_IdIn(personIds);

        // 3) 마음 기록
        giftRecordRepository.deleteAll(records);

        // 4) 사람
        personRepository.deleteAll(people);

        return new PersonDeleteResponse(people.size(), records.size(), (int) reminders);
    }

    /**
     * 기록 등록 시 보낸 사람 결정.
     * personId가 있으면 그걸 쓰고, 없으면 이름으로 찾고, 그래도 없으면 새로 만든다.
     * (디자인의 '보낸 사람'이 자유 입력 필드라 사람 선택 UI가 따로 없기 때문)
     */
    @Transactional
    public Person resolveOrCreate(User user, Long personId, String personName, Relationship relation) {
        Person person = resolveOrCreateNullable(user, personId, personName, relation);
        if (person == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "보낸 사람(personId 또는 personName)을 입력해주세요.");
        }
        return person;
    }

    /**
     * 이름이 정확히 일치하는 <b>이미 등록된</b> 사람만 찾는다. 없으면 null이며 새로 만들지 않는다.
     * AI가 추출한 이름은 오탈자가 있을 수 있어 참고용이므로, 확실할 때만 연결하려고 별도로 둔다.
     */
    @Transactional(readOnly = true)
    public Person findByExactName(User user, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return firstByName(user.getUsername(), name.trim());
    }

    /**
     * 이름으로 한 명을 고른다. 중복이 이미 있으면 <b>가장 먼저 등록된 사람</b>을 쓴다.
     *
     * <p>동명이인을 허용하기 때문에 Optional로 받으면 안 된다. 중복이 있는 순간 그 이름을 쓰는 요청이
     * 전부 NonUniqueResultException으로 500이 나기 때문이다.</p>
     */
    private Person firstByName(String username, String name) {
        List<Person> found = personRepository.findByUser_UsernameAndNameOrderByIdAsc(username, name);
        return found.isEmpty() ? null : found.get(0);
    }

    /** 위와 같지만 아무 정보도 안 왔으면 null을 돌려준다(PATCH 부분 수정용). */
    @Transactional
    public Person resolveOrCreateNullable(User user, Long personId, String personName, Relationship relation) {
        if (personId != null) {
            Person person = personRepository.findByIdAndUser_Username(personId, user.getUsername())
                    .orElseThrow(() -> new CustomException(ErrorCode.PERSON_NOT_FOUND));
            if (relation != null) {
                person.update(null, relation, null, null, null);
            }
            return person;
        }
        if (personName == null || personName.isBlank()) {
            return null;
        }
        String name = personName.trim();
        Person existing = firstByName(user.getUsername(), name);
        Person person = existing != null
                ? existing
                : personRepository.save(new Person(user, name, relation, null, null, null));
        if (relation != null) {
            person.update(null, relation, null, null, null);
        }
        return person;
    }

    private PersonResponse buildSummary(String username, Person person) {
        List<GiftRecord> records =
                giftRecordRepository.findByUser_UsernameAndPerson_IdOrderByReceivedDateDescIdDesc(username, person.getId());
        LocalDate today = LocalDate.now();
        LocalDate upcoming = records.stream()
                .map(GiftRecord::getReminderDate)
                .filter(date -> date != null && !date.isBefore(today))
                .min(Comparator.naturalOrder())
                .orElse(null);
        GiftRecord latest = records.isEmpty() ? null : records.getFirst();
        return PersonResponse.of(person, records.size(), latest, upcoming);
    }

    private User getCurrentUser() {
        String username = SecurityUtils.getCurrentUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
