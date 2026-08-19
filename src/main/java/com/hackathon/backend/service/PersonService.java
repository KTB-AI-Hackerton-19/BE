package com.hackathon.backend.service;

import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.person.PersonRequest;
import com.hackathon.backend.dto.person.PersonResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.PersonRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.SecurityUtils;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final GiftRecordRepository giftRecordRepository;

    public PersonService(PersonRepository personRepository, UserRepository userRepository,
                         GiftRecordRepository giftRecordRepository) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
        this.giftRecordRepository = giftRecordRepository;
    }

    @Transactional
    public PersonResponse create(PersonRequest request) {
        User user = getCurrentUser();
        Person person = personRepository.findByUser_UsernameAndName(user.getUsername(), request.name().trim())
                .orElseGet(() -> personRepository.save(
                        new Person(user, request.name().trim(), request.relation(), request.birthday(), request.memo())));
        person.update(null, request.relation(), request.birthday(), request.memo());
        return PersonResponse.of(person, 0L, null, null);
    }

    @Transactional
    public PersonResponse update(Long id, PersonRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        Person person = personRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.PERSON_NOT_FOUND));
        person.update(request.name(), request.relation(), request.birthday(), request.memo());
        return buildSummary(username, person);
    }

    /** 사람 목록 화면 — 이름 검색(q) 지원. 마음 개수/최근 선물/다가오는 알림일까지 채워서 내려준다. */
    @Transactional(readOnly = true)
    public List<PersonResponse> list(String q) {
        String username = SecurityUtils.getCurrentUsername();
        List<Person> people = (q == null || q.isBlank())
                ? personRepository.findByUser_UsernameOrderByNameAsc(username)
                : personRepository.findByUser_UsernameAndNameContainingIgnoreCaseOrderByNameAsc(username, q.trim());

        // 사람마다 쿼리를 던지지 않도록 개수/최근기록/알림일을 한 번에 모아서 메모리에서 매칭한다.
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

    @Transactional
    public void delete(Long id) {
        String username = SecurityUtils.getCurrentUsername();
        Person person = personRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.PERSON_NOT_FOUND));
        long records = giftRecordRepository.findByUser_UsernameAndPerson_IdOrderByReceivedDateDescIdDesc(username, id).size();
        if (records > 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT,
                    "이 사람에게 받은 마음 기록이 %d건 남아 있어 삭제할 수 없습니다. 기록을 먼저 정리해주세요.".formatted(records));
        }
        personRepository.delete(person);
    }

    /**
     * 기록 등록 시 보낸 사람 결정.
     * personId가 있으면 그걸 쓰고, 없으면 이름으로 찾고, 그래도 없으면 새로 만든다.
     * (디자인의 '보낸 사람'이 자유 입력 필드라 사람 선택 UI가 따로 없기 때문)
     */
    @Transactional
    public Person resolveOrCreate(User user, Long personId, String personName, String relation) {
        Person person = resolveOrCreateNullable(user, personId, personName, relation);
        if (person == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "보낸 사람(personId 또는 personName)을 입력해주세요.");
        }
        return person;
    }

    /** 위와 같지만 아무 정보도 안 왔으면 null을 돌려준다(PATCH 부분 수정용). */
    @Transactional
    public Person resolveOrCreateNullable(User user, Long personId, String personName, String relation) {
        if (personId != null) {
            Person person = personRepository.findByIdAndUser_Username(personId, user.getUsername())
                    .orElseThrow(() -> new CustomException(ErrorCode.PERSON_NOT_FOUND));
            if (relation != null && !relation.isBlank()) {
                person.update(null, relation, null, null);
            }
            return person;
        }
        if (personName == null || personName.isBlank()) {
            return null;
        }
        String name = personName.trim();
        Person person = personRepository.findByUser_UsernameAndName(user.getUsername(), name)
                .orElseGet(() -> personRepository.save(new Person(user, name, relation, null, null)));
        if (relation != null && !relation.isBlank()) {
            person.update(null, relation, null, null);
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
