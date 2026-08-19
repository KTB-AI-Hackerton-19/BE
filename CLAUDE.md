# 프로젝트 컨벤션 (해커톤 백엔드, 1인 담당)

## 기술 스택
- Spring Boot 4.0.6 (Spring Initializr에서 해당 패치가 더 이상 안 보이면 같은 4.0.x 라인의 최신 패치로 대체하고 사용자에게 한 줄로 알릴 것)
- Java 25
- Gradle
  - 로컬에 `gradle` 명령이 없으면 `brew install gradle`로 설치 후 `gradle wrapper --gradle-version <최신버전>`으로 wrapper 생성
  - Spring Initializr의 `/starter.zip`(`type=gradle-project`)이 500을 내는 경우가 있었음 (Maven 쪽은 정상이었음) — 이땐 기다리지 말고 `build.gradle`/`settings.gradle`/소스 트리를 직접 작성해서 진행
- DB: H2 (로컬 개발, 인메모리) → 인프라 담당자의 배포 환경 준비 완료 후 PostgreSQL/MySQL로 전환
- Lombok, Spring Data JPA, Validation, springdoc-openapi (Swagger), Spring Security + JJWT (JWT 인증용, 아래 인증 섹션 참고)
- 로컬 실행 방식: IntelliJ 직접 실행 (Docker Compose는 로컬 반복 개발엔 안 씀 — 코드 반영 속도 우선. 단, 인프라 담당자 전달/데모 직전 점검용 `docker-compose.yml`은 아래 Dockerfile 섹션 참고)
- 프로젝트는 서브폴더(`backend/` 등) 없이 저장소 루트에 직접 생성 (IntelliJ로 폴더 그대로 열 수 있도록)
- 외부 공유: ngrok 무료 고정 도메인으로 로컬 서버(8080)를 외부에 노출
  ```bash
  ngrok http 8080 --domain=customer-faster-stole.ngrok-free.dev
  ```
  이 도메인은 고정이라 서버 재시작해도 URL이 안 바뀜. 팀에 한 번만 공유하면 됨.

## 초기 세팅 순서 (당일 반드시 이 순서대로)
1. Spring Initializr로 프로젝트 생성 (안 되면 위 기술 스택 섹션의 폴백대로 직접 작성)
   - 의존성: web, data-jpa, lombok, h2, validation, springdoc-openapi, security(JWT 인증 포함 시)
2. `application.yml` — H2 인메모리 DB 연결 + `ddl-auto: update`, 포트 8080
3. `config/CorsConfig.java` — 모든 origin/method/header 허용 (`allowedOriginPatterns("*")`)
   - CORS는 엔드포인트 만들기 **전에** 먼저 설정할 것 (프론트가 URL 받자마자 바로 테스트 가능하게)
4. `controller/HealthController.java` — `GET /health` → `"OK"` 리턴, 서버 생존 확인용
5. 서버 실행 → `/health` 확인 → `ngrok http 8080` 실행 → URL 확보
6. 패키지 구조:
   ```
   controller / service / repository / domain / dto / config / security / exception
   ```
   (`security`: JWT 발급/검증/필터, `exception`: 커스텀 예외 + `@RestControllerAdvice` 글로벌 핸들러)

## API 공통 응답 포맷
필드명 자체보다 "성공 시엔 data만 채워지고, 실패 시엔 error만 채워지는 상호 배타적 구조"가 핵심 원칙.

성공:
```json
{ "success": true, "data": { ... } }
```

실패:
```json
{ "success": false, "error": { "code": "ERROR_CODE", "message": "사람이 읽을 수 있는 메시지" } }
```

```java
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorDetail error;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> res = new ApiResponse<>();
        res.success = true;
        res.data = data;
        return res;
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        ApiResponse<T> res = new ApiResponse<>();
        res.success = false;
        res.error = new ErrorDetail(code, message);
        return res;
    }
}
```
에러 코드는 하드코딩 문자열 대신 Enum으로 관리 권장 (예: `ErrorCode.USER_NOT_FOUND`).

## 네이밍 규칙 (REST 원칙)
- 엔드포인트: `/api/{리소스명 복수형}` — 목록 조회든 단건 조회든 동일 경로 패턴 유지
  - `GET /api/users` (목록), `GET /api/users/{id}` (단건)
- 리소스 계층: `/api/users/{userId}/posts`
- 동사 금지, HTTP 메서드로 행위 표현 (GET/POST/PUT·PATCH/DELETE)
- 예외: 로그인/로그아웃 등 순수 행위성 엔드포인트만 동사 허용 (`/api/auth/login`)
- DTO: `XxxRequest`, `XxxResponse`
- 엔티티: 단수형 (`User`, `Post`)

## 개발 순서 (당일 진행 방식)
1. 확정/예상 API 명세대로 **하드코딩 응답 주는 더미 컨트롤러 전체를 먼저 작성** — 프론트가 기다리지 않고 바로 개발 시작하는 게 최우선 목표
2. ngrok URL + Swagger 경로(`/swagger-ui.html`)를 팀에 공유
3. 더미 응답을 하나씩 실제 JPA 로직으로 교체
4. 인프라 담당자 배포 환경 준비되면 datasource만 실DB로 전환 (`application-local` / `application-prod` profile 분리 권장)

## 인증 판단 기준
- 유저별 데이터 저장/구분이 필요 → 최소한의 사용자 식별은 필요
- 데모 시나리오에 "로그인 화면"이 명시적으로 나오는 경우 → JWT 구현
- 로그인 화면 없이 그냥 유저 구분만 필요한 경우 → 이름/닉네임 기반 식별로 대체 가능 (인증 로직 생략) — 이건 사용자가 명시적으로 "로그인 없이 진행"이라고 할 때만 적용
- **기본값은 Access + Refresh Token 둘 다 구현** (Spring Security + JJWT, HS256). Access 12h / Refresh 7d 정도로 데모 기준 넉넉하게. Refresh Token은 `User` 엔티티에 저장해서 `/api/auth/refresh` 요청 시 DB 값과 대조
- 인증 안 된 요청은 기본 설정이면 403 + 빈 바디로 나감 — `AuthenticationEntryPoint`를 등록해서 401 + 공통 `ApiResponse.error(...)` 포맷으로 통일할 것

## Spring Boot 4 관련 주의사항 (세팅 중 실제로 겪은 이슈)
- `org.springframework.lang.NonNull`은 Spring Framework 7에서 deprecated → `org.jspecify.annotations.NonNull` 사용
- `data-jpa` + `h2`만으로는 H2 콘솔 자동설정이 안 붙음 (스타터가 세분화됨) → `runtimeOnly 'org.springframework.boot:spring-boot-h2console'` 명시적으로 추가해야 `/h2-console` 동작
- `com.fasterxml.jackson.databind.ObjectMapper` 빈이 자동으로 주입 안 될 수 있음 (Jackson 3 계열이 기본) → `AuthenticationEntryPoint`처럼 프레임워크 초기 단계 컴포넌트에서는 ObjectMapper 의존 대신 JSON 문자열 직접 조립 권장

## Dockerfile / 배포 관련 역할 분담
- 로컬 반복 개발 중엔 Docker 사용하지 않음 (IntelliJ 직접 실행 — 코드 반영 속도 우선). Docker/Compose는 인프라 담당자한테 전달할 때나 데모 직전 "배포됐을 때도 잘 도는지" 최종 점검용으로만 씀
- **Dockerfile 초안은 백엔드(본인)가 작성** — 앱을 어떻게 빌드/실행하는지는 백엔드가 제일 잘 알기 때문
- **멀티스테이지로 작성할 것** (컨테이너 안에서 소스부터 빌드) — 로컬에서 `./gradlew bootJar`를 미리 안 돌려도 `docker compose up -d --build` 한 줄이면 항상 최신 코드가 반영되게 하기 위함. 단일 스테이지로 미리 빌드된 jar만 복사하면, 로컬 jar를 안 새로 빌드했을 때 옛날 코드가 그대로 배포되는 걸 못 알아채는 사고가 남
  ```dockerfile
  # ---- build stage: 컨테이너 안에서 소스부터 빌드 ----
  FROM eclipse-temurin:25-jdk AS build
  WORKDIR /app
  COPY gradlew gradlew.bat build.gradle settings.gradle ./
  COPY gradle gradle
  RUN chmod +x gradlew && ./gradlew --no-daemon help > /dev/null
  COPY src src
  RUN ./gradlew --no-daemon bootJar

  # ---- run stage: 빌드 결과 jar만 담아서 가볍게 ----
  FROM eclipse-temurin:25-jdk
  WORKDIR /app
  COPY --from=build /app/build/libs/*.jar app.jar
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```
- `.dockerignore`도 같이 추가 (`build/`, `.gradle/`, `.idea/`, `*.iml`, `out/`, `.git/`)
- 서비스가 backend 하나뿐이어도 편의용 단일 서비스 `docker-compose.yml`은 백엔드가 같이 추가해도 됨 (그냥 `docker build`+`docker run`을 한 명령으로 감싸는 것뿐이라 인프라 영역 침범 아님):
  ```yaml
  services:
    backend:
      build: .
      ports:
        - "8080:8080"
  ```
  실행: `docker compose up -d --build` / 정리: `docker compose down`
- DB, Redis 등 여러 서비스를 묶는 **실제 배포용** Docker Compose 구성 및 파이프라인은 인프라 담당자 영역 (위 단일 서비스 compose는 그 위에 서비스만 추가하면 되는 출발점)
- 작성 시점: 핵심 기능이 어느 정도 안정된 후, 혹은 인프라 담당자가 요청하는 시점에 맞춰서 (Day 1부터 서두를 필요는 없음)

## 현재 진행 상황 (2026-08-18 기준)

주제는 **"마음장부"** — 받은 선물·부조금을 AI가 기록하고 답례 시점을 알려주는 서비스.
상세 설계/API 목록/결정 근거는 `BACKEND_PLAN.md` 참고. 프론트 디자인 원본은 `~/Documents/AI_Hackerton`.

**구현 완료**: 인증(signup/login/logout/refresh), 사람 CRUD, 마음 기록 CRUD + 필터·검색·정렬·페이징,
카테고리(DB 테이블 기반, 추가 시 재배포 불필요), 캘린더(월별 + 날짜별, RECEIVED/TO_GIVE 구분),
대시보드, 통합검색, 선물 추천, 답례 알림 + 10분 주기 스케줄러, presigned URL 발급, Swagger 한글 문서 28개.
프론트(`AI_Hackerton`)도 실제 API에 연동됨 — Vite dev 프록시로 `/api` → `localhost:8080`, 포트는 **5174**
(5173은 다른 프로젝트가 점유 중), 자동 로그인 계정 `demo` / `demo1234`.

**알림 발송 (SSE)** — 구현 완료. 스케줄러가 예정일 도래한 알림을 `SENT`로 바꾸면서 **접속 중인 사용자에게
SSE로 즉시 push**하고, 프론트가 토스트로 띄운다.

스케줄러 주기는 **오전 9시~밤 9시 매시 정각**(`0 0 9-21 * * *`). `reminderDate`에 시각이 없어서
발송 시각은 cron이 결정하는데, 자정에 돌리면 새벽에 알림이 가버리므로 아침 기준으로 잡았다.
`PENDING`인 것만 집어가서 **멱등**하므로 여러 번 돌아도 중복 발송이 없고, 9시에 서버가 꺼져 있었어도
다음 정각에 복구된다. 발송 시각을 매일 랜덤으로 바꾸는 건 하지 않는다 — 알림이 안 왔을 때
"시각이 안 된 건지 버그인지" 구분이 안 되고, 분산이 필요해지면 랜덤보다 `userId % N분` 같은
결정적 분산이 재현 가능해서 낫다.

관련 엔드포인트:
- `GET /api/reminders/stream` — SSE 구독. **`EventSource`가 Authorization 헤더를 못 붙여서
  `?token=<accessToken>` 쿼리로 전달**하며, `JwtAuthenticationFilter`가 `/stream` 경로에서만 이를 허용한다.
- `GET /api/reminders/undelivered`, `POST /api/reminders/{id}/delivered` — 표시 여부 관리.
  `ReminderTask.delivered` 플래그로 같은 알림이 두 번 뜨지 않게 하고, **접속하지 않은 동안 발송된 알림은
  다음 접속 시 SSE 연결 직후 흘려보낸다** (SSE만 쓰면 알림이 유실되는 문제를 이걸로 막음).
- `POST /api/reminders/dispatch` / `POST /api/reminders/{id}/dispatch` — **시연용 즉시 발송 트리거**.
  10분 주기 스케줄러를 기다리지 않고, 미래 날짜 알림도 바로 띄워볼 수 있다. 발표 중에 이걸 호출하면 된다.

세션 대신 JWT를 유지한 이유: 인증이 이미 완성·검증됐고, SSE 헤더 제약은 필터 5줄로 해결되는 반면
세션 전환은 인증 전체 재작성 + ngrok 크로스오리진 쿠키(SameSite) 문제를 새로 떠안는다.
기획서에도 "stateless라 수평 확장 가능"을 기술 차별점으로 적어둔 상태라 그것과도 일관된다.

**아직 더미인 것**: AI 추출 결과(`AI_SERVICE_URL` 미설정 시 하드코딩 폴백), 선물 추천, 데모 데이터 몇 건.
백엔드 로직 자체는 전부 실제 JPA 기반이라 더미가 아님.

## 다음 작업 (Day 2, 위에서부터 순서대로)

1. **`git init` + 원격 저장소 푸시** ← 제일 먼저. 지금 자바 파일 87개가 버전관리 없이 방치되어 있어
   실수로 날리면 복구 불가하고 팀 공유도 불가능. `.gitignore`는 이미 있음.
2. **JWT secret을 env var로 분리** — `application.yml`에 평문 하드코딩되어 있어 그대로 커밋하면 노출됨.
   `${JWT_SECRET:...}` 형태로 변경 (DB 접속정보는 이미 env var 처리됨).
3. **H2를 파일 기반으로 전환 검토** — 현재 인메모리라 서버 재시작하면 데이터가 전부 사라짐.
   데모 직전 재시작 사고 방지용으로 `jdbc:h2:file:./data/hackathon` 고려 (MySQL 전환 시 자동 해결).
4. **AI 서버 연동** — AI 담당자가 `POST {AI_SERVICE_URL}/extract` 만들면 env var만 채우면 됨.
   요청은 `{ "imageUrl": "<presigned GET URL>" }` 하나로 확정, 응답 스키마는 `BACKEND_PLAN.md`에 명시.
   ⚠️ **더미 폴백이 조용히 동작하므로**, 연동한 날엔 응답이 진짜 AI 값인지 반드시 눈으로 확인할 것
   (URL 오타나 서버 다운이어도 더미가 나와서 "잘 되는 줄" 착각하게 됨).
5. **S3 실제 버킷 연결** — 인프라 담당자에게 버킷 + IAM Role 요청. presigned URL 발급까지만 검증됐고
   실제 업로드는 미검증 상태.
6. **ngrok으로 외부 공유** — 아직 안 띄움. **프론트 포트(5174) 하나만 터널링하면 끝난다.**
   Vite dev 서버가 `/api`를 `localhost:8080`으로 프록시하므로 API 호출까지 같은 터널로 전달된다
   (백엔드용 터널을 따로 열 필요 없음 — 무료 플랜 동시 1개 제한에도 문제없음).
   ```bash
   ngrok http 5174 --domain=customer-faster-stole.ngrok-free.dev
   ```
   `vite.config.js`에 `host: true`(0.0.0.0 바인딩)와 `allowedHosts: true`(ngrok 도메인 차단 해제)를
   이미 넣어뒀고, 외부 IP로 접속 및 `/api` 프록시 동작까지 검증 완료.
   전제: **백엔드(8080)와 Vite(5174)가 둘 다 로컬에서 돌고 있어야** 한다.
7. **로그인 화면(프론트)** — 지금은 `demo`/`demo1234` 자동 로그인이라 로그인 UI가 없다.
   백엔드 인증은 10개 케이스(중복가입·비번오류·무인증·위조토큰·로그아웃 후 재사용 차단 등) 전부 검증 완료.
8. Google Calendar 연동 (또는 ICS 폴백), 이미지 PII 마스킹·삭제 API, 테스트 코드.

### 화면/동작 관련 합의사항 (변경 금지)
- **디자인은 원본(`AI_Hackerton`) 유지.** 화면을 새로 추가하지 않는다.
  기록 목록에서 항목을 누르면 **그 사람의 타임라인(사람 상세)으로 이동**하는 것이 원래 상세보기 동작이다.
  (AI 추출 직후 DRAFT 기록은 `personId`가 없어 이동할 곳이 없으므로 토스트로 안내한다)
- **사람은 "사람들" 화면에서 등록하고, 기록할 땐 등록된 사람을 선택**한다. 기록 모달의 보낸 사람은
  자유 입력이 아니라 드롭다운이며, 목록에 없으면 "+ 새로운 사람 등록하기"로 그 자리에서 만들 수 있다.
  AI가 추출한 이름은 참고용이고, 등록된 사람과 이름이 정확히 같을 때만 자동 선택된다.
- **답례 알림일은 기록할 때 사용자가 지정한 날짜를 그대로 쓴다.** 이벤트 유형별 오프셋 자동계산은 하지 않는다.

## 우선순위 원칙
- Kafka 등 무거운 인프라는 기본적으로 도입하지 않음
- Redis는 "AI 응답 캐싱" 등 명확한 용도가 있을 때만, 핵심 기능 완성 후 여유 있으면 추가
- 발표 몇 시간 전에는 새 기능 추가 금지, 안정화만 진행
- 처음부터 실DB로 시작하지 않음 (H2로 개발 속도 우선, 배포 직전에만 전환)
