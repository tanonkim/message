# PDCA Plan: Message 서버 구현

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 프로젝트명 | Message Server (중앙 알림 발송 플랫폼) |
| 목적 | 분산된 SMS/알림톡/Email 발송 로직을 단일 플랫폼으로 통합 |
| Java | 21 |
| Framework | Spring Boot 4 |
| Message Broker | ActiveMQ |
| DB | MySQL (Master/Replica) |
| Build | Gradle (멀티모듈) |

---

## 2. AS-IS 문제점 (레퍼런스 기반 분석)

레퍼런스 시스템(`QueueReceiver`, `SolapiSender`, `EmailSender` 분석)을 통해 확인된 문제:

| 문제 | 코드 근거 |
|------|---------|
| **P1. 단일 장애점** | `QueueReceiver` 1개 클래스가 11개 큐 전담 → 서버 다운 시 전체 채널 중단 |
| **P2. 재시도 없음** | `SolapiSender.onFailure()` → `log.error()`만 출력하고 종료 |
| **P3. 우선순위 없음** | 인증번호·마케팅이 같은 `solapi-queue`로 유입 |
| **P4. 강결합** | 100개 서비스가 `module-core` JAR 직접 의존 → DTO 변경 시 전체 재빌드 |
| **P5. 비용 비효율** | SMS/알림톡 발송 로그 없음, 중복 발송 방지 없음, Fallback 없음 |
| **P6. 관측성 부재** | `email_log`만 존재, SMS/알림톡 발송 이력 DB 미저장 |
| **P7. 확장성 한계** | 채널 추가 시 `QueueReceiver` 수정 후 재배포 필요 |

---

## 3. TO-BE 목표 아키텍처

```
[Service A] ─┐
[Service B] ─┼──→ Notification Gateway API ──→ ActiveMQ (우선순위별 큐)
[Service C] ─┘      (REST API / SDK)                    │
                                          ┌──────────────┼─────────────┐
                                          ▼              ▼             ▼
                                     SMS/알림톡 Worker  Email Worker  Push Worker
                                     (채널별 독립)     (채널별 독립)  (채널별 독립)
                                          │              │             │
                                          ▼              ▼             ▼
                                     Solapi          AWS SES V2   Fingerpush/FCM
```

### 설계 원칙

| 원칙 | 내용 |
|------|------|
| 단일 진입점 | 모든 서비스는 REST API 또는 경량 SDK로 요청 |
| 채널 독립 | 채널별 Worker 분리, 장애 격리 |
| 우선순위 큐 | CRITICAL / HIGH / NORMAL / LOW 4단계 분리 |
| Fallback | 알림톡 실패 → SMS, 최종 실패 → DLQ |
| 통합 로그 | 모든 채널 발송 이력 `notification_log` 단일 테이블 |
| 비용 최적화 | 알림톡 우선 → SMS fallback, 중복 방지, 배치 처리 |

---

## 4. 구현 범위 (Scope)

### 포함 (In-Scope)
- Notification Gateway API (REST)
- ActiveMQ 우선순위 큐 설정 (4단계 × 채널)
- SMS Worker (Solapi - HMAC 인증)
- 알림톡 Worker (Solapi Nurigo SDK)
- Email Worker (AWS SES V2)
- Push Worker (Fingerpush)
- Fallback 정책 (알림톡 → SMS)
- 재시도 정책 (채널별 횟수 설정)
- DLQ (Dead Letter Queue)
- `notification_log` 통합 발송 이력
- 수신 차단 목록 (`blocklist`) 체크
- 스테이지 환경 발송 제한

### 제외 (Out-of-Scope)
- Slack / Teams / Channel.io (내부 알림 채널 - 별도 관리)
- 관리자 화면 (템플릿 관리 UI)
- 클라이언트 SDK 배포 (Notification SDK)
- 기존 서비스 마이그레이션

---

## 5. 모듈 구조

```
message/
├── module-service/    ← Notification Gateway API + Worker 실행 모듈
│   └── src/main/java/com/message/
│       └── domain/
│           ├── notification/    ← 발송 요청 수신, 큐 발행, 이력 저장
│           ├── sms/             ← SMS Worker (Solapi)
│           ├── alimtalk/        ← 알림톡 Worker (Nurigo SDK)
│           ├── email/           ← Email Worker (AWS SES V2)
│           ├── push/            ← Push Worker (Fingerpush)
│           └── blocklist/       ← 수신 차단 목록
└── (추가 모듈은 요구사항 확정 후 결정)
```

---

## 6. DB 설계

### 6.1 notification_log (통합 발송 이력)

```sql
CREATE TABLE notification_log (
  notification_log_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  idempotency_key       VARCHAR(64) UNIQUE COMMENT '중복 발송 방지 키',
  service_id            VARCHAR(50) NOT NULL COMMENT '요청 서비스 식별자',
  channel               VARCHAR(20) NOT NULL COMMENT 'SMS|ALIMTALK|EMAIL|PUSH',
  priority              VARCHAR(10) NOT NULL COMMENT 'CRITICAL|HIGH|NORMAL|LOW',
  recipient             VARCHAR(320) NOT NULL COMMENT '수신자 (암호화)',
  template_id           VARCHAR(100) COMMENT '템플릿 ID',
  variables             TEXT COMMENT '템플릿 변수 (JSON)',
  status                VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        COMMENT 'PENDING|SENT|DELIVERED|FAILED|FALLBACK',
  retry_count           INT DEFAULT 0 COMMENT '재시도 횟수',
  fallback_channel      VARCHAR(20) COMMENT '대체 발송 채널',
  provider_message_id   VARCHAR(100) COMMENT '외부 벤더 응답 ID',
  error_message         TEXT COMMENT '실패 사유',
  cost                  DECIMAL(10,4) DEFAULT 0 COMMENT '발송 단가 (원)',
  create_at             DATETIME NOT NULL COMMENT '요청 시각',
  sent_at               DATETIME COMMENT '발송 시각',

  INDEX idx_service_channel (service_id, channel),
  INDEX idx_status (status),
  INDEX idx_create_at (create_at)
);
```

### 6.2 blocklist (수신 차단 - 레퍼런스 유지)

```sql
CREATE TABLE blocklist (
  blocklist_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(320) COMMENT '이메일 (AES 암호화)',
  cell_phone    VARCHAR(1000) COMMENT '전화번호 (AES 암호화)',
  client_ip     VARCHAR(50),
  memo          TEXT COMMENT '차단 사유',
  create_at     DATETIME NOT NULL
);
```

### 6.3 notification_template (알림톡 템플릿 - Enum 대신 DB 관리)

```sql
CREATE TABLE notification_template (
  notification_template_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_code             VARCHAR(100) NOT NULL UNIQUE COMMENT '템플릿 코드',
  channel                   VARCHAR(20) NOT NULL COMMENT 'ALIMTALK|EMAIL|SMS',
  template_id               VARCHAR(100) COMMENT '외부 벤더 템플릿 ID',
  pf_id                     VARCHAR(100) COMMENT '카카오 pfId',
  title                     VARCHAR(200) COMMENT '제목',
  description               VARCHAR(500) COMMENT '설명',
  is_active                 boolean NOT NULL DEFAULT true COMMENT '활성 여부',
  create_at                 DATETIME NOT NULL,
  update_at                 DATETIME NOT NULL
);
```

---

## 7. API 설계

### 7.1 발송 요청

```
POST /api/v1/notifications
Authorization: Bearer {api-key}

Request:
{
  "serviceId":       "wello-api",           // 요청 서비스 식별자
  "channel":         "ALIMTALK",            // SMS | ALIMTALK | EMAIL | PUSH
  "priority":        "CRITICAL",            // CRITICAL | HIGH | NORMAL | LOW
  "recipient": {
    "phone":  "01012345678",                // SMS/알림톡
    "email":  "user@example.com"            // EMAIL
  },
  "template": {
    "id":        "캐시백신청독려",
    "variables": { "name": "홍길동", "code": "123456" }
  },
  "content":         "인증번호: 123456",     // 템플릿 미사용 시 직접 입력
  "fallback": {                              // 실패 시 대체 채널 (선택)
    "channel": "SMS",
    "content": "인증번호: 123456"
  },
  "idempotencyKey": "uuid-xxxx",            // 중복 발송 방지 키 (선택)
  "scheduledAt":     null                   // null=즉시, ISO8601=예약
}

Response:
{
  "notificationLogId": 12345,
  "status": "PENDING"
}
```

### 7.2 발송 상태 조회

```
GET /api/v1/notifications/{notificationLogId}
```

---

## 8. 우선순위 큐 전략 (ActiveMQ)

| 우선순위 | 큐 이름 | 용도 | 재시도 |
|---------|---------|------|--------|
| CRITICAL | `noti.critical.{channel}` | 인증번호, 결제완료, 비밀번호 재설정 | 5회 (즉시) |
| HIGH | `noti.high.{channel}` | 회원가입 완료, 문의 답변 | 3회 (1분 간격) |
| NORMAL | `noti.normal.{channel}` | 일반 안내 메시지 | 3회 (5분 간격) |
| LOW | `noti.low.{channel}` | 마케팅, 리마인드 | 1회 (실패 무시) |

- DLQ: `noti.dlq.{channel}` — 최종 실패 메시지 적재 후 운영 알림
- channel = `sms` | `alimtalk` | `email` | `push`

---

## 9. Fallback 정책

```
알림톡 요청
  → 알림톡 Worker 처리
    → 성공: notification_log.status = SENT
    → 실패 (재시도 소진):
        → fallback 설정 있음 → SMS Worker로 재발행 (fallback_channel = SMS)
        → fallback 설정 없음 → DLQ 적재 + 운영 Slack 알림
```

---

## 10. 채널별 발송 단가 (비용 추적용)

| 채널 | 단가 | 비고 |
|------|------|------|
| SMS | ~20원/건 | Solapi 기준 |
| LMS | ~50원/건 | Solapi 기준 |
| 알림톡 | ~8원/건 | 알림톡 기본 |
| Email | 무료~저렴 | AWS SES 기준 |
| Push | 무료 | FCM 기준 |

> `notification_log.cost` 컬럼에 발송 단가 기록 → 서비스별/채널별 월간 비용 분석

---

## 11. 환경변수 목록 (벤더별)

### 11.1 ActiveMQ

```yaml
spring:
  activemq:
    broker-url: ${MQ_URL}           # 예: tcp://localhost:61616
    user: ${MQ_USER}
    password: ${MQ_PASSWORD}
  jms:
    pub-sub-domain: false           # Queue 모드 (topic=false)
```

### 11.2 Solapi — SMS / LMS / 알림톡

```yaml
solapi:
  api-key: ${SOLAPI_API_KEY}         # Solapi 콘솔 발급
  api-secret: ${SOLAPI_API_SECRET}   # HMAC-SHA256 서명용
  base-url: https://api.solapi.com
  sender-phone: ${SOLAPI_SENDER_PHONE}  # 등록된 발신번호 (예: 0269523534)
  # 알림톡 추가 설정
  pf-id: ${SOLAPI_PF_ID}             # 카카오 비즈 채널 pfId
```

> 참고: 레퍼런스 시스템은 `solapi-config.ini` 파일에서 로드 → 본 프로젝트는 yaml + Parameter Store 사용

### 11.3 AWS SES V2 — 이메일

```yaml
aws:
  region:
    static: ap-northeast-2
  # IAM Role 사용 시 access-key 불필요 (권장)
  # 로컬 개발 시 아래 설정 사용
  credentials:
    access-key: ${AWS_ACCESS_KEY_ID}
    secret-key: ${AWS_SECRET_ACCESS_KEY}

ses:
  sender-email: ${SES_SENDER_EMAIL}        # 예: mailer@yourdomain.com
  sender-name: ${SES_SENDER_NAME}          # 예: 알림 서비스
  # Stage 환경 발송 허용 도메인
  stage-allowed-domains:
    - "@yourdomain.com"
```

> SES 발신 도메인은 AWS 콘솔에서 사전 인증(Domain Verification) 필요

### 11.4 Fingerpush — 앱 푸시

```yaml
fingerpush:
  app-key: ${FINGERPUSH_APP_KEY}
  app-secret: ${FINGERPUSH_APP_SECRET}
  customer-key: ${FINGERPUSH_CUSTOMER_KEY}
  api-url: https://api.fingerpush.com
```

### 11.5 MySQL (Master / Replica)

```yaml
spring:
  datasource:
    master:
      jdbc-url: ${DB_MASTER_URL}          # jdbc:mysql://host:3306/dbname
      username: ${DB_MASTER_USERNAME}
      password: ${DB_MASTER_PASSWORD}
      driver-class-name: com.mysql.cj.jdbc.Driver
      hikari:
        maximum-pool-size: 10
        minimum-idle: 5
    replica:
      jdbc-url: ${DB_REPLICA_URL}
      username: ${DB_REPLICA_USERNAME}
      password: ${DB_REPLICA_PASSWORD}
      driver-class-name: com.mysql.cj.jdbc.Driver
      hikari:
        maximum-pool-size: 10
        minimum-idle: 5
```

### 11.6 AES 암호화

```yaml
cipher:
  secret-hash-key: ${CIPHER_SECRET_HASH_KEY}   # Base64 인코딩된 AES 키
  algorithm: ${CIPHER_ALGORITHM}                # 예: AES/CBC/PKCS5Padding
  iv: ${CIPHER_IV}                              # 16자 IV 값
```

> 수신자(전화번호, 이메일) 및 `blocklist` 필드 암호화에 사용

### 11.7 서버 공통

```yaml
server:
  port: ${SERVER_PORT:8400}

spring:
  application:
    name: message
  jackson:
    property-naming-strategy: SNAKE_CASE
  lifecycle:
    timeout-per-shutdown-phase: 30s

notification:
  api-key-header: X-Api-Key           # 클라이언트 인증 헤더명
```

### 환경변수 요약표

| 변수명 | 설명 | 필수 여부 |
|--------|------|---------|
| `MQ_URL` | ActiveMQ Broker URL | 필수 |
| `MQ_USER` | ActiveMQ 사용자 | 필수 |
| `MQ_PASSWORD` | ActiveMQ 비밀번호 | 필수 |
| `SOLAPI_API_KEY` | Solapi API Key | SMS/알림톡 사용 시 |
| `SOLAPI_API_SECRET` | Solapi API Secret | SMS/알림톡 사용 시 |
| `SOLAPI_SENDER_PHONE` | SMS 발신번호 | SMS/알림톡 사용 시 |
| `SOLAPI_PF_ID` | 카카오 pfId | 알림톡 사용 시 |
| `AWS_ACCESS_KEY_ID` | AWS Access Key | 로컬 개발 시 |
| `AWS_SECRET_ACCESS_KEY` | AWS Secret Key | 로컬 개발 시 |
| `SES_SENDER_EMAIL` | SES 발신 이메일 | Email 사용 시 |
| `FINGERPUSH_APP_KEY` | Fingerpush App Key | Push 사용 시 |
| `FINGERPUSH_APP_SECRET` | Fingerpush App Secret | Push 사용 시 |
| `FINGERPUSH_CUSTOMER_KEY` | Fingerpush Customer Key | Push 사용 시 |
| `DB_MASTER_URL` | Master DB JDBC URL | 필수 |
| `DB_MASTER_USERNAME` | Master DB 계정 | 필수 |
| `DB_MASTER_PASSWORD` | Master DB 비밀번호 | 필수 |
| `DB_REPLICA_URL` | Replica DB JDBC URL | 필수 |
| `DB_REPLICA_USERNAME` | Replica DB 계정 | 필수 |
| `DB_REPLICA_PASSWORD` | Replica DB 비밀번호 | 필수 |
| `CIPHER_SECRET_HASH_KEY` | AES 암호화 키 | 필수 |
| `CIPHER_ALGORITHM` | AES 알고리즘 | 필수 |
| `CIPHER_IV` | AES IV | 필수 |

---

## 12. 단계별 구현 계획

### Phase 1: 프로젝트 기반 구축

- [ ] 멀티모듈 Gradle 프로젝트 셋업 (`module-service`)
- [ ] Spring Boot 4 + Java 21 기본 설정
- [ ] Master/Replica DataSource 설정
- [ ] ActiveMQ 연결 설정
- [ ] `notification_log`, `blocklist`, `notification_template` 테이블 생성
- [ ] 공통 예외 클래스 정의 (`ApiException`, `BadRequestException`)
- [ ] AES 암호화 설정 (`StringCryptoConverter`)
- [ ] 프로파일 설정 (local / dev / stage / production)

### Phase 2: Notification Gateway API

- [ ] `POST /api/v1/notifications` 엔드포인트
- [ ] API Key 인증 필터
- [ ] 요청 유효성 검증 (channel, priority, recipient)
- [ ] Idempotency Key 중복 발송 방지
- [ ] `notification_log` 저장 (PENDING 상태)
- [ ] 우선순위별 ActiveMQ 큐 발행
- [ ] `GET /api/v1/notifications/{id}` 상태 조회

### Phase 3: SMS / 알림톡 Worker (Solapi)

- [ ] `@JmsListener` 큐 수신 설정
- [ ] Solapi HMAC-SHA256 인증 헤더 생성
- [ ] SMS 발송 (`SolapiSender`)
- [ ] LMS 발송 (SMS 2,000자 초과 시 자동 전환)
- [ ] 알림톡 발송 (Nurigo SDK — `DefaultMessageService`)
- [ ] 재시도 정책 (CRITICAL 5회, HIGH/NORMAL 3회, LOW 1회)
- [ ] 발송 결과 `notification_log` 업데이트 (SENT / FAILED)
- [ ] `blocklist` 수신 차단 체크

### Phase 4: Email Worker (AWS SES V2)

- [ ] `@JmsListener` 큐 수신 설정
- [ ] AWS SES V2 Raw 메시지 발송
- [ ] Stage 환경 수신자 도메인 필터링
- [ ] 발송 결과 `notification_log` 업데이트
- [ ] 실패 시 재시도 정책

### Phase 5: Push Worker (Fingerpush)

- [ ] `@JmsListener` 큐 수신 설정
- [ ] SINGLE / TARGET / ENTIRE 발송 모드
- [ ] TARGET 모드 500건 분할 발송
- [ ] 발송 결과 `notification_log` 업데이트

### Phase 6: Fallback + DLQ

- [ ] 알림톡 실패 → SMS Fallback 자동 트리거
- [ ] DLQ 설정 (`noti.dlq.{channel}`)
- [ ] DLQ 적체 시 운영 Slack 알림
- [ ] `notification_log.fallback_channel` 기록

### Phase 7: 모니터링

- [ ] Actuator healthcheck / prometheus 엔드포인트
- [ ] 채널별 발송량 / 성공률 지표
- [ ] DLQ 적체 알림
- [ ] Graceful Shutdown (`ActiveMqShutdownManager`)

---

## 13. 위험 요소 및 대응

| 위험 | 영향 | 대응 |
|------|------|------|
| Solapi API 장애 | SMS/알림톡 전체 중단 | 재시도 + DLQ + 운영 알림 |
| AWS SES 발송 제한 초과 | 이메일 발송 실패 | Rate Limiting + 큐 속도 조절 |
| ActiveMQ 단일 장애 | 전체 큐 중단 | Graceful Shutdown + 재기동 절차 문서화 |
| 대량 발송으로 큐 폭주 | CRITICAL 메시지 지연 | 우선순위 큐 + LOW 채널 속도 제한 |
| DB 쓰기 병목 | `notification_log` 저장 지연 | 비동기 로그 저장 검토 |

---

## 14. 완료 기준 (Definition of Done)

- [ ] SMS / 알림톡 / Email / Push 4개 채널 발송 정상 동작
- [ ] 우선순위 4단계 큐 분리 확인
- [ ] 알림톡 실패 → SMS Fallback 정상 동작
- [ ] `notification_log`에 모든 채널 발송 이력 기록
- [ ] Idempotency Key 중복 발송 방지 확인
- [ ] `blocklist` 차단 번호 발송 차단 확인
- [ ] Stage 환경 발송 제한 동작 확인
- [ ] DLQ 적재 및 운영 알림 확인
