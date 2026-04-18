# Message Service v0.2.0 릴리즈 노트

> **알림 게이트웨이 메세지서버**  
> 배포일: 2026-04-18  
> 버전: v0.2.0

---

## 개요

Message Service는 **알림 발송 게이트웨이**입니다.  
각 서비스에서 직접 SMS·카카오 알림톡·이메일·앱 푸시 벤더를 연동하지 않아도,  
이 서비스에 발송 요청을 보내면 채널별 발송, 우선순위 큐잉, 재시도, 폴백을 모두 처리합니다.

**v0.2.0 변경 사항**
- 수신 차단 목록(Blocklist) CQRS 리팩토링으로 조회 성능 개선
- ActiveMQ Connection Factory 설정 안정화
- Circuit Breaker 운영 환경 적용 (Solapi, Nurigo, SES, Fingerpush)
- 채널 Sender의 내부 메시지 의존성 분리 (인터페이스 안정화)

---

## 지원 채널

| 채널 코드 | 설명 | 벤더 |
|-----------|------|------|
| `SMS` | SMS 문자 | Solapi |
| `ALIMTALK` | 카카오 알림톡 | Nurigo |
| `EMAIL` | 이메일 | AWS SES v2 |
| `PUSH` | 앱 푸시 | Fingerpush |

---

## API

**Base URL**: `https://{host}/api/v1/notifications`

### 인증

모든 요청에 API Key 헤더가 필요합니다.

```
X-API-Key: {발급받은 서비스 API Key}
```

API Key는 각 벤더사마다 발급 필요합니다.

---

### 1. 알림 발송 요청

```
POST /api/v1/notifications
```

#### Request Body

```json
{
  "service_id": "order-service",
  "channel": "SMS",
  "priority": "NORMAL",
  "recipient": {
    "phone": "01012345678",
    "email": "user@example.com"
  },
  "template": {
    "id": "tmpl-001",
    "variables": {
      "orderNo": "ORD-20260418-001",
      "amount": "30,000"
    }
  },
  "content": "주문이 완료되었습니다.",
  "fallback": {
    "channel": "SMS",
    "content": "주문이 완료되었습니다."
  },
  "idempotency_key": "order-12345-sms-20260418",
  "scheduled_at": "2026-04-18T15:00:00"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `service_id` | String | Y | 요청 서비스 식별자 (예: `order-service`) |
| `channel` | String | Y | 발송 채널 (`SMS` / `ALIMTALK` / `EMAIL` / `PUSH`) |
| `priority` | String | Y | 발송 우선순위 (아래 우선순위 정책 참고) |
| `recipient.phone` | String | 조건부 | SMS·ALIMTALK·PUSH 채널 시 필수 |
| `recipient.email` | String | 조건부 | EMAIL 채널 시 필수 |
| `template` | Object | 조건부 | `template` 또는 `content` 중 하나 필수 |
| `template.id` | String | - | 사전 등록된 템플릿 ID |
| `template.variables` | Map | - | 템플릿 변수 치환 값 |
| `content` | String | 조건부 | 자유 발송 내용 (`template` 없을 경우 필수) |
| `fallback` | Object | N | 발송 실패 시 대체 채널 설정 |
| `fallback.channel` | String | - | 폴백 채널 코드 |
| `fallback.content` | String | - | 폴백 발송 내용 |
| `idempotency_key` | String | N | 중복 발송 방지 키. 미입력 시 자동 생성 |
| `scheduled_at` | String | N | 예약 발송 시각 (ISO 8601). 미입력 시 즉시 발송 |

#### Response (202 Accepted)

```json
{
  "notification_log_id": 1024,
  "channel": null,
  "status": "PENDING",
  "sent_at": null,
  "provider_message_id": null,
  "fallback_channel": null,
  "cost": null
}
```

발송 요청은 **비동기**로 처리됩니다. 응답 시점에는 `PENDING` 상태이며,  
실제 발송 결과는 상태 조회 API로 확인하거나 웹훅으로 수신할 수 있습니다.

---

### 2. 발송 상태 조회

```
GET /api/v1/notifications/{notification_log_id}
```

#### Response (200 OK)

```json
{
  "notification_log_id": 1024,
  "channel": "SMS",
  "status": "SENT",
  "sent_at": "2026-04-18T15:00:05",
  "provider_message_id": "MSG-20260418-001",
  "fallback_channel": null,
  "cost": "10.0000"
}
```

| 필드 | 설명 |
|------|------|
| `notification_log_id` | 알림 로그 ID |
| `channel` | 실제 발송된 채널 |
| `status` | 발송 상태 (아래 상태 코드 참고) |
| `sent_at` | 발송 완료 시각 |
| `provider_message_id` | 벤더 발급 메시지 ID |
| `fallback_channel` | 폴백 발송된 경우 폴백 채널 |
| `cost` | 발송 비용 (원) |

---

## 우선순위 정책

우선순위는 큐 처리 순서와 재시도 횟수를 결정합니다.

| 우선순위 | 용도 | 최대 재시도 | 재시도 간격 |
|----------|------|-------------|-------------|
| `CRITICAL` | 장애 알림, 보안 인증 등 즉시 처리 필요 | 5회 | 즉시 |
| `HIGH` | 주문 완료, 결제 확인 등 중요 알림 | 3회 | 60초 |
| `NORMAL` | 일반 마케팅, 안내 알림 | 3회 | 5분 |
| `LOW` | 배치성 발송, 뉴스레터 | 1회 | 즉시 |

> 재시도 소진 후 폴백 채널이 설정된 경우 폴백 채널로 전환되며,  
> 폴백도 없으면 DLQ로 이동하고 Slack 알림이 발송됩니다.

---

## 발송 상태 코드

| 상태 | 설명 |
|------|------|
| `PENDING` | 발송 대기 중 (큐에 적재됨) |
| `SENT` | 벤더 발송 완료 |
| `DELIVERED` | 수신자 전달 확인 (벤더 콜백 기반) |
| `FAILED` | 발송 실패 (재시도 소진) |
| `FALLBACK` | 폴백 채널로 전환됨 |

---

## 에러 코드

| HTTP 상태 | 에러 코드 | 설명 |
|-----------|-----------|------|
| 400 | `INVALID_CHANNEL` | 지원하지 않는 채널 |
| 400 | `INVALID_PRIORITY` | 지원하지 않는 우선순위 |
| 400 | `MISSING_RECIPIENT` | 수신자 정보 누락 |
| 400 | `INVALID_REQUEST` | 요청 파라미터 오류 |
| 401 | `UNAUTHORIZED` | API Key 인증 실패 |
| 404 | `NOTIFICATION_NOT_FOUND` | 발송 이력 없음 |
| 409 | `DUPLICATE_REQUEST` | 동일 `idempotency_key`로 중복 요청 |
| 500 | `SEND_FAILED` | 서버 내부 오류 |

#### 에러 응답 형식

```json
{
  "code": "INVALID_CHANNEL",
  "message": "지원하지 않는 채널입니다: KAKAO",
  "timestamp": "2026-04-18T15:00:00"
}
```

---

## 멱등성 (중복 발송 방지)

동일 요청이 여러 번 들어오는 상황을 방지하기 위해 `idempotency_key`를 지원합니다.

- 클라이언트가 직접 키를 지정하는 경우: 동일 키로 재요청 시 `409 Conflict` 반환
- 키를 지정하지 않는 경우: 서버가 UUID를 자동 생성하며 중복 방지 보장 없음

**권장 키 생성 패턴**: `{service_id}-{비즈니스_식별자}-{채널}-{날짜}`  
예시: `order-service-ORD-20260418-001-SMS-20260418`

---

## 수신 차단 (Blocklist)

관리자가 수신 차단 처리한 전화번호 또는 이메일로 발송 요청 시,  
큐에 적재하지 않고 즉시 `FAILED` 상태로 기록 후 응답합니다.  
별도 에러가 반환되지 않으며 `status: FAILED`, `error_message: 수신 차단된 대상입니다`로 저장됩니다.

---

## 통합 예시 (order-service)

```java
// 주문 완료 SMS 발송
NotificationRequest request = NotificationRequest.builder()
    .serviceId("order-service")
    .channel("SMS")
    .priority("HIGH")
    .recipient(new RecipientDto("01012345678", null))
    .content("주문이 완료되었습니다. 주문번호: " + orderId)
    .fallback(new FallbackDto("ALIMTALK", "주문이 완료되었습니다."))
    .idempotencyKey("order-service-" + orderId + "-SMS")
    .build();

NotificationResponse response = notificationClient.send(request);
// response.notificationLogId() 로 이후 상태 추적 가능
```

---

## 운영 정보

| 항목 | 내용 |
|------|------|
| 모니터링 | `/actuator/prometheus` (Micrometer, 채널별 sent/failed/dlq 카운터) |
| 헬스 체크 | `/actuator/health` |
| 메시지 브로커 | Apache ActiveMQ (우선순위 큐: `noti.{priority}.{channel}`) |
| DLQ | `noti.dlq.{channel}` — Slack 알림 연동 |
| 수신자 데이터 | AES 암호화 저장 (컬럼 레벨 암호화) |

---