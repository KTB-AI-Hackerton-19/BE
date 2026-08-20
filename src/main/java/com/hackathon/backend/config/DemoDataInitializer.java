package com.hackathon.backend.config;

import com.hackathon.backend.domain.Category;
import com.hackathon.backend.domain.EventCategory;
import com.hackathon.backend.domain.Gender;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.RecordType;
import com.hackathon.backend.domain.Relationship;
import com.hackathon.backend.domain.ReminderTask;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.repository.CategoryRepository;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.PersonRepository;
import com.hackathon.backend.repository.ReminderTaskRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.service.CategoryService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Random;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * <p>카테고리는 사용자별이라, 계정을 만든 직후 기본 6종을 그 사용자 것으로 깔고 나서 기록을 만든다.
 * 경조사(결혼식·장례식)는 더 이상 카테고리 row가 아니라 {@link GiftRecord}가 {@link EventCategory}를 직접 갖는다.</p>
 */
@Configuration
public class DemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    /**
     * 데모 계정. <b>비밀번호는 전부 "아이디 + 1234"</b> (예: demo → demo1234).
     * 계정마다 사람·기록·알림·카테고리를 각자 갖기 때문에 여러 명이 동시에 시연해도 서로 간섭하지 않는다.
     * 프론트 자동 로그인은 demo를 쓰므로 그 계정은 지우지 말 것.
     */
    private record DemoUser(String username, String name) {
    }

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("demo", "데모유저"),      // 프론트 자동 로그인 계정
            new DemoUser("test", "테스트유저"),    // 막 테스트해도 되는 계정
            new DemoUser("hana", "김하늘"),
            new DemoUser("junho", "이준호"),
            new DemoUser("seoyeon", "박서연"),
            new DemoUser("minjae", "최민재"),
            new DemoUser("yeeun", "정예은"),
            new DemoUser("doyun", "한도윤"),
            new DemoUser("sujin", "오수진"),
            new DemoUser("taeho", "강태호")
    );

    private static final String DEMO_PASSWORD_SUFFIX = "1234";

    /** 사람: 이름, 관계, 성별(없으면 null), 생일(오늘로부터 +N일, 없으면 null), 메모 */
    private record PersonSeed(String name, String relation, Gender gender, Integer birthdayInDays, String memo) {
    }

    /**
     * 기록: 보낸 사람, 받은 날짜(오늘로부터 -N일), 받은 이유, 선물명, 카테고리 이름, 금액,
     * 답례 알림일(오늘로부터 +N일, 없으면 null), 감사 완료 여부.
     *
     * <p>{@code eventCategory}가 있으면 경조사 기록이다 — 이때 {@code category}는 무시되고 {@code occasion}은
     * 저장 시 GiftRecord가 알아서 비운다. 행사일은 {@link #EVENT_DATES}에서 유형별로 하나만 관리한다
     * (같은 결혼식에서 받은 축의금이면 행사일도 같아야 하므로).</p>
     */
    private record RecordSeed(String personName, int receivedDaysAgo, String occasion, String gift,
                              String category, EventCategory eventCategory, int amount, Integer reminderInDays,
                              boolean thanked) {
        private static RecordSeed gift(String personName, int receivedDaysAgo, String occasion, String gift,
                                       String category, int amount, Integer reminderInDays, boolean thanked) {
            return new RecordSeed(personName, receivedDaysAgo, occasion, gift, category, null, amount,
                    reminderInDays, thanked);
        }

        private static RecordSeed event(String personName, int receivedDaysAgo, String occasion, String gift,
                                        EventCategory eventCategory, int amount, Integer reminderInDays,
                                        boolean thanked) {
            return new RecordSeed(personName, receivedDaysAgo, occasion, gift, null, eventCategory, amount,
                    reminderInDays, thanked);
        }
    }

    /** 경조사 유형별 행사일(오늘로부터 -N일). 같은 결혼식/장례식이면 행사일도 하나로 고정돼야 한다. */
    private static final Map<EventCategory, Integer> EVENT_DAYS_AGO = Map.of(
            EventCategory.WEDDING, 31,
            EventCategory.FUNERAL, 15
    );

    private static final List<PersonSeed> PEOPLE = List.of(
            // 김민수는 AI 더미 응답이 뱉는 이름이라 반드시 있어야 이미지 업로드 흐름에서 자동 매칭이 보인다.
            new PersonSeed("김민수", "친한 친구", Gender.MALE, 26, "커피 좋아함. 단 거는 별로"),
            new PersonSeed("박지영", "회사 동료", Gender.FEMALE, 9, "고양이 두 마리 키움"),
            new PersonSeed("이서준", "대학 선배", Gender.MALE, 73, null),
            new PersonSeed("최유나", "사촌 동생", Gender.FEMALE, 41, "향수 취향 확실함"),
            new PersonSeed("정하늘", "동호회 친구", null, null, "러닝 크루에서 만남"),
            new PersonSeed("윤도현", "회사 팀장", Gender.MALE, 130, null),
            new PersonSeed("한소희", "고등학교 친구", Gender.FEMALE, 55, "디저트 카페 자주 감")
    );

    private static final List<RecordSeed> RECORDS = List.of(
            RecordSeed.gift("김민수", 1, "내 생일", "스타벅스 케이크", "디저트", 35000, 29, false),
            RecordSeed.gift("박지영", 3, "승진 축하", "디퓨저 세트", "생활용품", 42000, 4, false),
            RecordSeed.gift("이서준", 6, "집들이 답례", "무민 머그컵 세트", "생활용품", 28000, 11, false),
            RecordSeed.gift("최유나", 12, "내 생일", "조말론 향수", "패션·잡화", 95000, 2, false),
            RecordSeed.gift("정하늘", 18, "완주 축하", "러닝 양말 세트", "패션·잡화", 24000, 18, true),
            RecordSeed.gift("한소희", 24, "그냥", "마카롱 한 박스", "디저트", 21000, 40, true),
            RecordSeed.event("윤도현", 31, "결혼 축의금", "축의금", EventCategory.WEDDING, 200000, 7, false),
            RecordSeed.gift("박지영", 38, "명절 인사", "한우 선물세트", "기타", 150000, 62, true),
            RecordSeed.gift("김민수", 47, "취업 축하", "교보문고 상품권", "상품권", 50000, null, true),
            RecordSeed.gift("이서준", 55, "생일", "튤립 한 다발", "꽃·식물", 38000, null, true),
            RecordSeed.gift("최유나", 68, "수능 응원", "핸드크림 세트", "생활용품", 19000, null, true),
            RecordSeed.gift("정하늘", 82, "이사 축하", "몬스테라 화분", "꽃·식물", 45000, null, true),
            RecordSeed.event("김민수", 31, "결혼 축의금", "축의금", EventCategory.WEDDING, 100000, 7, false),
            RecordSeed.event("박지영", 31, "결혼 축의금", "축의금", EventCategory.WEDDING, 50000, 7, true),
            RecordSeed.event("최유나", 31, "결혼 축의금", "축의금", EventCategory.WEDDING, 100000, 7, false),
            RecordSeed.event("정하늘", 15, "부친상 조의금", "조의금", EventCategory.FUNERAL, 100000, 9, false),
            RecordSeed.event("한소희", 15, "부친상 조의금", "조의금", EventCategory.FUNERAL, 50000, null, true),
            RecordSeed.event("이서준", 15, "부친상 조의금", "조의금", EventCategory.FUNERAL, 50000, 9, false)
    );

    @Bean
    public ApplicationRunner demoDataSeedRunner(UserRepository userRepository,
                                                PersonRepository personRepository,
                                                GiftRecordRepository giftRecordRepository,
                                                ReminderTaskRepository reminderTaskRepository,
                                                CategoryRepository categoryRepository,
                                                CategoryService categoryService,
                                                PasswordEncoder passwordEncoder) {
        return args -> DEMO_USERS.forEach(demo -> {
            if (userRepository.existsByUsername(demo.username())) {
                return; // 이미 있으면 손대지 않는다(실DB 전환 시 자동 비활성화).
            }
            seedFor(demo.username(), demo.name(), userRepository, personRepository, giftRecordRepository,
                    reminderTaskRepository, categoryRepository, categoryService, passwordEncoder);
        });
    }

    /** 대량 데이터를 넣을 계정. 프론트 자동 로그인 계정이라 화면에서 바로 보인다. */
    private static final String BULK_ACCOUNT = "demo";

    private static final List<String> SURNAMES =
            List.of("김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신", "권", "황");
    private static final List<String> GIVEN_NAMES =
            List.of("민수", "지영", "서준", "유나", "하늘", "도현", "소희", "예린", "태호", "수진", "지훈", "다은",
                    "현우", "채원", "준영", "서윤", "건우", "하린", "시우", "지안");
    private static final List<Relationship> RELATIONS =
            List.of(Relationship.WORK, Relationship.SCHOOL, Relationship.FRIEND, Relationship.RELATIVE,
                    Relationship.NEIGHBOR, Relationship.BUSINESS, Relationship.FAMILY);
    private static final List<String> GIFT_NAMES =
            List.of("핸드크림 세트", "커피 원두", "머그컵", "무릎담요", "디퓨저", "티 세트", "양말 세트", "떡 세트",
                    "과일 바구니", "케이크", "화분", "향초", "책", "문구 세트", "에코백");

    /**
     * 큰 이벤트 하나에 수십 명이 몰리는 실제 모양을 만든다.
     * 결혼식 축의금 45건 + 장례식 조의금 25건 + 일반 선물 30건 ≈ 100건.
     */
    private int seedBulk(User user, Map<String, Category> giftCategoriesByName, PersonRepository personRepository,
                         GiftRecordRepository giftRecordRepository, ReminderTaskRepository reminderTaskRepository,
                         LocalDate today) {
        Random rnd = new Random(42);   // 고정 시드 — 재기동해도 같은 데이터가 나온다
        List<Person> pool = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            String pname = SURNAMES.get(rnd.nextInt(SURNAMES.size()))
                    + GIVEN_NAMES.get(rnd.nextInt(GIVEN_NAMES.size())) + (i + 1);
            pool.add(personRepository.save(new Person(user, pname,
                    RELATIONS.get(rnd.nextInt(RELATIONS.size())),
                    rnd.nextBoolean() ? Gender.MALE : Gender.FEMALE,
                    today.plusDays(rnd.nextInt(365)), null)));
        }

        List<GiftRecord> records = new ArrayList<>();
        List<ReminderTask> reminders = new ArrayList<>();
        int[] congratulation = {50000, 50000, 100000, 100000, 100000, 200000, 300000};

        // 결혼식 — 같은 날 45명
        LocalDate weddingDay = today.minusDays(EVENT_DAYS_AGO.get(EventCategory.WEDDING));
        for (int i = 0; i < 45; i++) {
            Person p = pool.get(i);
            boolean thanked = rnd.nextInt(10) < 4;
            LocalDate remind = thanked ? null : today.plusDays(7 + rnd.nextInt(21));
            GiftRecord r = giftRecordRepository.save(GiftRecord.createConfirmed(
                    user, p, RecordType.EVENT, null, EventCategory.WEDDING, weddingDay, "결혼 축의금", "축의금",
                    congratulation[rnd.nextInt(congratulation.length)], weddingDay, remind, thanked));
            records.add(r);
            if (remind != null) {
                reminders.add(new ReminderTask(user, p, r, remind));
            }
        }

        // 장례식 — 같은 날 25명
        LocalDate funeralDay = today.minusDays(EVENT_DAYS_AGO.get(EventCategory.FUNERAL));
        for (int i = 45; i < 70; i++) {
            Person p = pool.get(i);
            boolean thanked = rnd.nextInt(10) < 5;
            GiftRecord r = giftRecordRepository.save(GiftRecord.createConfirmed(
                    user, p, RecordType.EVENT, null, EventCategory.FUNERAL, funeralDay, "부친상 조의금", "조의금",
                    congratulation[rnd.nextInt(4)], funeralDay, thanked ? null : today.plusDays(9), thanked));
            records.add(r);
        }

        // 일반 선물 30건 — 카테고리와 날짜를 흩뿌린다
        List<Category> giftCategories = List.copyOf(giftCategoriesByName.values());
        for (int i = 0; i < 30; i++) {
            Person p = pool.get(rnd.nextInt(pool.size()));
            records.add(giftRecordRepository.save(GiftRecord.createConfirmed(
                    user, p, RecordType.GIFT, giftCategories.get(rnd.nextInt(giftCategories.size())), null, null,
                    "생일 선물", GIFT_NAMES.get(rnd.nextInt(GIFT_NAMES.size())),
                    15000 + rnd.nextInt(9) * 10000, today.minusDays(rnd.nextInt(300)), null, rnd.nextBoolean())));
        }

        reminderTaskRepository.saveAll(reminders);
        return records.size();
    }

    private void seedFor(String username, String name, UserRepository userRepository, PersonRepository personRepository,
                         GiftRecordRepository giftRecordRepository, ReminderTaskRepository reminderTaskRepository,
                         CategoryRepository categoryRepository, CategoryService categoryService,
                         PasswordEncoder passwordEncoder) {
        LocalDate today = LocalDate.now();

        User user = userRepository.save(
                new User(username, passwordEncoder.encode(username + DEMO_PASSWORD_SUFFIX), name));

        // 카테고리는 사용자별이므로 이 계정 것으로 먼저 깔아둔다(선물 6종). 경조사는 고정 7종 enum이라 여기 관여하지 않는다.
        categoryService.provisionDefaults(user);
        Map<String, Category> categories = new LinkedHashMap<>();
        categoryRepository.findByUser_UsernameOrderByDisplayOrderAscIdAsc(username)
                .forEach(c -> categories.put(c.getName(), c));

        Map<String, Person> people = new LinkedHashMap<>();
        for (PersonSeed seed : PEOPLE) {
            LocalDate birthday = seed.birthdayInDays() == null ? null : today.plusDays(seed.birthdayInDays());
            people.put(seed.name(),
                    personRepository.save(new Person(user, seed.name(), Relationship.from(seed.relation()), seed.gender(),
                            birthday, seed.memo())));
        }

        List<ReminderTask> reminders = new ArrayList<>();
        for (RecordSeed seed : RECORDS) {
            Person person = people.get(seed.personName());
            LocalDate reminderDate = seed.reminderInDays() == null ? null : today.plusDays(seed.reminderInDays());

            GiftRecord record;
            if (seed.eventCategory() != null) {
                LocalDate eventDate = today.minusDays(EVENT_DAYS_AGO.get(seed.eventCategory()));
                record = giftRecordRepository.save(GiftRecord.createConfirmed(
                        user, person, RecordType.EVENT, null, seed.eventCategory(), eventDate, seed.occasion(),
                        seed.gift(), seed.amount(), today.minusDays(seed.receivedDaysAgo()), reminderDate,
                        seed.thanked()));
            } else {
                Category category = categories.getOrDefault(seed.category(), categories.get("기타"));
                record = giftRecordRepository.save(GiftRecord.createConfirmed(
                        user, person, RecordType.GIFT, category, null, null, seed.occasion(), seed.gift(),
                        seed.amount(), today.minusDays(seed.receivedDaysAgo()), reminderDate, seed.thanked()));
            }

            // 답례가 끝난 기록에는 알림을 만들지 않는다(이미 챙긴 건 다시 알릴 이유가 없다).
            if (reminderDate != null && !seed.thanked()) {
                reminders.add(new ReminderTask(user, person, record, reminderDate));
            }
        }
        reminderTaskRepository.saveAll(reminders);

        // demo 계정만 대량 데이터를 추가한다. 큰 이벤트(축의금 수십 건)에서 목록·페이징·집계가
        // 어떻게 보이는지 확인하려면 실제 규모가 필요한데, 모든 계정에 넣으면 기동이 느려진다.
        int bulk = BULK_ACCOUNT.equals(username)
                ? seedBulk(user, categories, personRepository, giftRecordRepository, reminderTaskRepository, today)
                : 0;

        log.info("데모 계정 '{}' (비밀번호 '{}{}', 이름 '{}') — 사람 {} / 기록 {} / 알림 {}{}",
                username, username, DEMO_PASSWORD_SUFFIX, name, PEOPLE.size(), RECORDS.size(), reminders.size(),
                bulk > 0 ? " (+ 대량 " + bulk + "건)" : "");
    }
}
