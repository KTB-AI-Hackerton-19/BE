---
name: hackathon-backend-setup
description: Spring Boot 기반 해커톤 백엔드 프로젝트를 처음부터 빠르게 세팅할 때 사용. 프로젝트 초기화, CORS 설정, 헬스체크, 더미 응답 API 설계, 공통 응답 포맷 적용, ngrok 공유가 필요할 때 이 스킬을 사용. "해커톤 백엔드 세팅해줘", "프로젝트 뼈대 만들어줘", "더미 엔드포인트 만들어줘" 같은 요청에서 트리거.
---

# 해커톤 백엔드 세팅 (Spring Boot, 1인 백엔드 담당 기준)

해커톤 당일, 팀이 기획 회의를 하는 동안 혹은 그 직후 최대한 빠르게
"프론트가 바로 개발을 시작할 수 있는 상태"를 만드는 것이 목표.
백엔드 담당자가 병목이 되지 않는 것이 최우선 원칙.

## 기술 스택 (고정값)
- Spring Boot 4.0.6 — Initializr에 해당 패치가 없으면 같은 4.0.x 라인 최신 패치로 대체하고 한 줄로 알림
- Java 25
- Gradle
  - 로컬에 `gradle`이 없으면 `brew install gradle` → `gradle wrapper --gradle-version <최신버전>`으로 wrapper 생성
  - Spring Initializr `/starter.zip`(`type=gradle-project`)이 500 에러를 낸 적 있음 (Maven 타입은 정상이었음) — 이땐 기다리지 말고 `build.gradle`/`settings.gradle`/소스 트리를 직접 작성
- DB: H2 (인메모리, 로컬 개발용)
- Lombok, Spring Data JPA, Validation, springdoc-openapi (Swagger), Spring Security + JJWT (JWT 인증, 기본 포함 — 아래 인증 섹션 참고)
- 로컬 실행: IntelliJ 직접 실행 (Docker/Compose는 로컬 반복 개발엔 안 씀, 인프라 전달·데모 직전 점검용으로만 — 7단계 참고)
- 프로젝트는 서브폴더 없이 저장소 루트에 직접 생성 (IntelliJ로 폴더 그대로 열리게)

## 작업 순서

### 1단계 — 프로젝트 초기화
- Spring Initializr 방식으로 프로젝트 생성 (안 되면 위 기술 스택 섹션 폴백대로 직접 작성)
- 의존성: web, data-jpa, lombok, h2, validation, springdoc-openapi, security
- 패키지 구조:
  ```
  controller / service / repository / domain / dto / config / security / exception
  ```
  (`security`: JWT 발급/검증/필터, `exception`: 커스텀 예외 + `@RestControllerAdvice`)

### 2단계 — 공통 설정 파일 작성 (엔드포인트 만들기 전에 먼저)
1. `application.yml` — H2 인메모리 DB, `ddl-auto: update`, 포트 8080
2. `config/CorsConfig.java` — 모든 origin/method/header 허용
   - 반드시 엔드포인트 작성 전에 먼저 설정할 것. 나중에 넣으면 프론트가 먼저 테스트하다 에러 만나고 대기하는 상황이 생김.
3. `controller/HealthController.java` — `GET /health` → `"OK"`, 서버 생존 확인용. 제일 먼저 테스트.

### 3단계 — 공통 응답 포맷 적용
성공/실패를 명확히 분리하는 `ApiResponse<T>` 래퍼 클래스 작성.

성공: `{ "success": true, "data": { ... } }`
실패: `{ "success": false, "error": { "code": "ERROR_CODE", "message": "..." } }`

핵심 원칙: 필드명 자체보다 "성공 시엔 data만, 실패 시엔 error만 채워지는 상호 배타적 구조"가 중요.
에러 코드는 하드코딩 문자열 대신 Enum으로 관리.

### 4단계 — 인증(JWT) 기본 셋업
기본값은 **Access + Refresh Token 둘 다 구현** (아래 "인증 관련 판단 기준" 참고). Spring Security + JJWT(HS256).
- `security/JwtProvider` — access/refresh 토큰 발급·검증 (토큰에 `type` claim 넣어서 access/refresh 구분)
- `security/JwtAuthenticationFilter` — `Authorization: Bearer ...` 파싱해서 `SecurityContext`에 세팅
- `security/JwtAuthenticationEntryPoint` — 인증 안 된 요청을 **401 + 공통 `ApiResponse.error(...)` 포맷**으로 응답 (등록 안 하면 기본값인 403 + 빈 바디로 나가서 프론트가 에러 포맷을 못 씀). `ObjectMapper` 빈 주입에 의존하지 말고 JSON 문자열 직접 조립할 것 (Spring Boot 4는 Jackson 3 기본이라 classic `com.fasterxml.jackson.databind.ObjectMapper` 빈이 없을 수 있음)
- `config/SecurityConfig` — `/health`, `/api/auth/**`, swagger, `/h2-console/**` permitAll, 나머지 authenticated, stateless 세션
- `POST /api/auth/signup`, `/login`, `/refresh` — refresh token은 `User` 엔티티에 저장해서 요청 시 DB 값과 대조
- Access 12h / Refresh 7d 정도로 데모 기준 넉넉하게

### 5단계 — 더미(하드코딩) 엔드포인트 우선 작성
- 실제 DB 로직 없이, 확정되었거나 예상되는 API 명세대로 하드코딩된 값을 리턴하는 컨트롤러부터 전체 작성
- 목적: 프론트가 실제 응답 포맷을 보고 즉시 연동 코드를 짤 수 있게 하는 것
- 이후 더미 로직을 하나씩 실제 JPA 기반 로직으로 교체

### 6단계 — 네이밍 규칙 (REST 원칙)
- 엔드포인트: `/api/{리소스명 복수형}` — 목록/단건 조회 모두 동일 경로 패턴
- 리소스 계층: `/api/users/{userId}/posts`
- 동사 금지, HTTP 메서드로 행위 표현. 예외는 `/api/auth/login` 같은 순수 행위성 엔드포인트만
- DTO: `XxxRequest`, `XxxResponse` / 엔티티: 단수형

### 7단계 — 외부 공유 (ngrok, 고정 도메인 사용)
- 서버 실행 후 `/health` 응답 확인
- 무료 고정 도메인으로 실행 (재시작해도 URL 안 바뀜):
  ```bash
  ngrok http 8080 --domain=customer-faster-stole.ngrok-free.dev
  ```
- 해당 URL + `/swagger-ui.html` 경로를 팀(프론트, AI 담당자)에게 공유 (고정이므로 한 번만 공유하면 됨)

### 8단계 — Dockerfile 초안 작성 (핵심 기능 안정화 이후, 또는 인프라 담당자 요청 시 — 사용자가 먼저 요청하면 그때 진행해도 됨)
- Dockerfile 초안은 백엔드가 작성 (빌드/실행 방식을 제일 잘 아는 사람이 정의)
- **멀티스테이지로 작성**: 컨테이너 안에서 소스부터 `gradlew bootJar`로 빌드 → 로컬에서 jar를 미리 안 빌드해도 `docker compose up -d --build` 한 줄이면 항상 최신 코드로 뜸 (단일 스테이지로 미리 빌드된 jar만 복사하면 로컬 빌드를 깜빡했을 때 옛날 코드가 조용히 배포되는 사고가 남)
  ```dockerfile
  FROM eclipse-temurin:25-jdk AS build
  WORKDIR /app
  COPY gradlew gradlew.bat build.gradle settings.gradle ./
  COPY gradle gradle
  RUN chmod +x gradlew && ./gradlew --no-daemon help > /dev/null
  COPY src src
  RUN ./gradlew --no-daemon bootJar

  FROM eclipse-temurin:25-jdk
  WORKDIR /app
  COPY --from=build /app/build/libs/*.jar app.jar
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```
- `.dockerignore` 같이 추가: `build/`, `.gradle/`, `.idea/`, `*.iml`, `out/`, `.git/`
- 서비스가 backend 하나뿐이어도 편의용 단일 서비스 `docker-compose.yml`은 백엔드가 같이 추가 가능 (인프라 영역 침범 아님, `docker build`+`run`을 한 명령으로 감싸는 것뿐):
  ```yaml
  services:
    backend:
      build: .
      ports:
        - "8080:8080"
  ```
- DB/Redis 등 여러 서비스를 묶는 실제 배포용 Compose 구성 및 파이프라인은 인프라 담당자 영역 (위 단일 서비스 compose가 그 출발점)
- 로컬 반복 개발 단계에서는 Docker/Compose로 개발하지 않음 (IntelliJ 직접 실행이 빠름) — Docker/Compose는 인프라 전달·데모 직전 최종 점검용

## 인증 관련 판단 기준
- 유저별 데이터 저장/구분이 필요하면 최소한의 사용자 식별은 필요
- **기본값은 JWT(Access + Refresh Token 둘 다) 구현**. 데모 시나리오에 "로그인 화면"이 있으면 당연히 필요하고, 없어도 기본으로 구현해도 무방
- 사용자가 명시적으로 "로그인 없이 진행"이라고 한 경우에만 → 이름/닉네임 기반 식별로 대체 (인증 로직 생략)
- 인증 안 된 요청은 401 + 공통 에러 포맷으로 통일 (4단계 참고, 기본 설정이면 403+빈바디로 나가니 주의)

## Spring Boot 4 관련 주의사항 (세팅 중 실제로 겪은 이슈)
- `org.springframework.lang.NonNull`은 Spring Framework 7에서 deprecated → `org.jspecify.annotations.NonNull` 사용
- `data-jpa` + `h2`만으로는 H2 콘솔 자동설정이 안 붙음 → `runtimeOnly 'org.springframework.boot:spring-boot-h2console'` 명시적으로 추가해야 `/h2-console` 동작
- classic `com.fasterxml.jackson.databind.ObjectMapper` 빈이 자동 주입 안 될 수 있음 (Jackson 3 계열이 기본)

## 하지 말아야 할 것
- Kafka는 기본적으로 도입하지 않음 (명확한 실시간 스트리밍 스토리가 핵심일 때만 예외)
- Redis는 "이게 없으면 데모가 안 되는가?" 기준으로만, 핵심 기능 완성 후 여유 있을 때만 추가
- 로컬 반복 개발 단계에서 Docker/Compose로 백엔드를 띄우지 않음 (빌드-반영 루프가 느려짐, IntelliJ 직접 실행이 빠름). 단일 서비스 `docker-compose.yml` 자체는 8단계처럼 만들어둬도 되지만 평소 개발에 쓰지는 않음
- 발표 몇 시간 전에는 새 기능 추가 금지, 안정화만 진행
- 처음부터 실DB(PostgreSQL/MySQL)로 시작하지 않음 (인프라 담당자 배포 준비 완료 후 전환)
