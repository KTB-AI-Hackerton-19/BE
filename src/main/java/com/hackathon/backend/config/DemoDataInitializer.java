package com.hackathon.backend.config;

import com.hackathon.backend.domain.Category;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.repository.CategoryRepository;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.PersonRepository;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.repository.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 데모용 계정 + 사람 + 기록 + 답례 알림 시드.
 *
 * <p>H2가 인메모리라 서버를 재시작할 때마다 데이터가 전부 사라진다. 그때마다 손으로 다시 만들지 않아도
 * 로그인부터 화면 전체가 채워진 상태로 시작하도록 기동 시 한 번 넣어준다.</p>
 *
 * <p><b>계정이 이미 있으면 아무것도 하지 않는다</b> — 파일 기반 DB나 실DB로 바꿔서 데이터가 살아남는
 * 환경이 되면 자동으로 비활성화되는 셈이라, 재기동해도 중복이 쌓이지 않는다.</p>
 *
 * <p>날짜는 전부 <b>오늘 기준 상대값</b>으로 만든다. 고정 날짜로 박아두면 며칠만 지나도 "다가오는 일정"이
 * 전부 과거가 되어 홈 화면 에이전트 카드와 캘린더가 비어버린다.</p>
 *
 * <p>카테고리 마스터가 먼저 들어와야 하므로 {@link CategoryInitializer} 다음에 실행한다.</p>
 */
@Configuration
public class DemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    /** 프론트가 자동 로그인에 쓰는 계정과 수동 테스트용 계정. 둘 다 같은 목데이터를 갖는다. */
    private static final Map<String, String> DEMO_USERS = Map.of("demo", "데모유저", "test", "테스트유저");
    private static final String DEMO_PASSWORD_SUFFIX = "1234";

    /** 사람: 이름, 관계, 생일(오늘로부터 +N일, 없으면 null), 메모 */
    private record PersonSeed(String name, String relation, Integer birthdayInDays, String memo) {
    }

    /**
     * 기록: 보낸 사람, 받은 날짜(오늘로부터 -N일), 받은 이유, 선물명, 카테고리, 금액,
     * 답례 알림일(오늘로부터 +N일, 없으면 null), 감사 완료 여부
     */
    private record RecordSeed(String personName, int receivedDaysAgo, String occasion, String gift,
                              String category, int amount, Integer reminderInDays, boolean thanked) {
    }

    private static final List<PersonSeed> PEOPLE = List.of(
            // 김민수는 AI 더미 응답이 뱉는 이름이라 반드시 있어야 이미지 업로드 흐름에서 자동 매칭이 보인다.
            new PersonSeed("김민수", "친한 친구", 26, "커피 좋아함. 단 거는 별로"),
            new PersonSeed("박지영", "회사 동료", 9, "고양이 두 마리 키움"),
            new PersonSeed("이서준", "대학 선배", 73, null),
            new PersonSeed("최유나", "사촌 동생", 41, "향수 취향 확실함"),
            new PersonSeed("정하늘", "동호회 친구", null, "러닝 크루에서 만남"),
            new PersonSeed("윤도현", "회사 팀장", 130, null),
            new PersonSeed("한소희", "고등학교 친구", 55, "디저트 카페 자주 감")
    );

    private static final List<RecordSeed> RECORDS = List.of(
            new RecordSeed("김민수", 1, "내 생일", "스타벅스 케이크", "디저트", 35000, 29, false),
            new RecordSeed("박지영", 3, "승진 축하", "디퓨저 세트", "생활용품", 42000, 4, false),
            new RecordSeed("이서준", 6, "집들이 답례", "무민 머그컵 세트", "생활용품", 28000, 11, false),
            new RecordSeed("최유나", 12, "내 생일", "조말론 향수", "패션·잡화", 95000, 2, false),
            new RecordSeed("정하늘", 18, "완주 축하", "러닝 양말 세트", "패션·잡화", 24000, 18, true),
            new RecordSeed("한소희", 24, "그냥", "마카롱 한 박스", "디저트", 21000, 40, true),
            new RecordSeed("윤도현", 31, "결혼 축의금", "축의금", "부조금", 200000, 7, false),
            new RecordSeed("박지영", 38, "명절 인사", "한우 선물세트", "기타", 150000, 62, true),
            new RecordSeed("김민수", 47, "취업 축하", "교보문고 상품권", "상품권", 50000, null, true),
            new RecordSeed("이서준", 55, "생일", "튤립 한 다발", "꽃·식물", 38000, null, true),
            new RecordSeed("최유나", 68, "수능 응원", "핸드크림 세트", "생활용품", 19000, null, true),
            new RecordSeed("정하늘", 82, "이사 축하", "몬스테라 화분", "꽃·식물", 45000, null, true)
    );

    @Bean
    @Order(2) // CategoryInitializer(@Order(1)) 이후에 실행되어야 카테고리를 찾을 수 있다.
    public ApplicationRunner demoDataSeedRunner(UserRepository userRepository,
                                                PersonRepository personRepository,
                                                GiftRecordRepository giftRecordRepository,
                                                ReminderTaskRepository reminderTaskRepository,
                                                CategoryRepository categoryRepository,
                                                PasswordEncoder passwordEncoder) {
        return args -> {
            Map<String, Category> categories = new LinkedHashMap<>();
            categoryRepository.findAll().forEach(c -> categories.put(c.getName(), c));
            if (categories.isEmpty()) {
                log.warn("카테고리가 없어 데모 데이터를 건너뜁니다.");
                return;
            }

            DEMO_USERS.forEach((username, name) -> {
                if (userRepository.existsByUsername(username)) {
                    return; // 이미 있으면 손대지 않는다(실DB 전환 시 자동 비활성화).
                }
                seedFor(username, name, userRepository, personRepository, giftRecordRepository,
                        reminderTaskRepository, categories, passwordEncoder);
            });
        };
    }

    private void seedFor(String username, String name, UserRepository userRepository, PersonRepository personRepository,
                         GiftRecordRepository giftRecordRepository, ReminderTaskRepository reminderTaskRepository,
                         Map<String, Category> categories, PasswordEncoder passwordEncoder) {
        LocalDate today = LocalDate.now();

        User user = userRepository.save(
                new User(username, passwordEncoder.encode(username + DEMO_PASSWORD_SUFFIX), name));

        Map<String, Person> people = new LinkedHashMap<>();
        for (PersonSeed seed : PEOPLE) {
            LocalDate birthday = seed.birthdayInDays() == null ? null : today.plusDays(seed.birthdayInDays());
            people.put(seed.name(),
                    personRepository.save(new Person(user, seed.name(), seed.relation(), birthday, seed.memo())));
        }

        List<ReminderTask> reminders = new ArrayList<>();
        for (RecordSeed seed : RECORDS) {
            Person person = people.get(seed.personName());
            Category category = categories.getOrDefault(seed.category(), categories.get("기타"));
            LocalDate reminderDate = seed.reminderInDays() == null ? null : today.plusDays(seed.reminderInDays());

            GiftRecord record = giftRecordRepository.save(GiftRecord.createConfirmed(
                    user, person, category, seed.occasion(), seed.gift(), seed.amount(),
                    today.minusDays(seed.receivedDaysAgo()), reminderDate, seed.thanked()));

            // 답례가 끝난 기록에는 알림을 만들지 않는다(이미 챙긴 건 다시 알릴 이유가 없다).
            if (reminderDate != null && !seed.thanked()) {
                reminders.add(new ReminderTask(user, person, record, reminderDate));
            }
        }
        reminderTaskRepository.saveAll(reminders);

        log.info("데모 데이터 생성 — 계정 '{}' / 비밀번호 '{}{}' / 이름 '{}' : 사람 {}명, 기록 {}건, 답례 알림 {}건",
                username, username, DEMO_PASSWORD_SUFFIX, name, PEOPLE.size(), RECORDS.size(), reminders.size());
    }
}
