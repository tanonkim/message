# Message Server

알림톡 / SMS / Email 중앙 발송 플랫폼

---

## AS-IS: 현재 문제점

```
[Service A] ──→ SMS 벤더 직접 호출
[Service B] ──→ 알림톡 벤더 직접 호출
[Service C] ──→ SMS + 알림톡 각자 호출
```

| 문제 | 설명 |
|------|------|
| **발송 로직 중복** | 3개 이상의 서비스가 각자 SMS, 알림톡 연동 코드를 보유 |
| **장애 전파** | 외부 벤더 장애 시 서비스별 개별 대응 필요, fallback 없음 |
| **우선순위 없음** | 인증번호와 마케팅 메시지를 동일하게 처리 |
| **비용 관리 불가** | 채널별 비용/성공률 통합 집계 불가 |
| **운영 가시성 부재** | 발송 이력, 실패 원인, 재시도 현황 파악 어려움 |
| **확장성 한계** | 신규 서비스 추가 시마다 연동 코드 중복 작성 |

---

## TO-BE: 목표 아키텍처

```
[Service A] ─┐
[Service B] ─┼──→ Notification Gateway API ──→ Message Broker (ActiveMQ)
[Service C] ─┘         (단일 진입점)                     │
                                               ┌──────────┼──────────┐
                                               ▼          ▼          ▼
                                          SMS Worker  알림톡 Worker  Email Worker
                                               │          │          │
                                               ▼          ▼          ▼
                                          SMS 벤더   알림톡 벤더   Email 벤더
```

### 핵심 설계 원칙

**1. 단일 진입점**
모든 서비스는 Notification Gateway API 또는 경량 SDK를 통해 발송 요청. 100개 이상의 서비스가 연동되어도 발송 정책은 플랫폼에서 중앙 관리.

**2. 채널별 Worker 독립**
SMS / 알림톡 / Email Worker를 분리하여 특정 채널 장애가 다른 채널로 전파되지 않도록 격리. 채널별 독립 스케일링 가능.

**3. 우선순위 기반 큐 처리**

| 등급 | 대상 | 처리 정책 |
|------|------|----------|
| **Critical** | 인증번호, 결제완료, 보안 알림 | 즉시 처리, 실패 시 즉시 재시도 |
| **High** | 회원가입, 문의 답변, 주요 운영 알림 | 높은 우선순위 처리 |
| **Normal** | 일반 안내 메시지 | 기본 처리 |
| **Low** | 마케팅, 리마인드, 비필수 안내 | 배치 처리, 속도 제한 적용 |

**4. Fallback 정책**
- 알림톡 실패 → SMS fallback
- SMS 일시 실패 → 재시도
- 최종 실패 → DLQ 적재 후 운영 알림
- 특정 벤더 장애 시 대체 벤더 전환 구조

**5. 비용 최적화**
- 기본 발송 채널: 알림톡 (SMS 대비 저렴)
- 알림톡 실패 시에만 SMS fallback
- 비필수 메시지 배치 처리 및 속도 제한
- 중복 발송 방지
- 채널별 성공률 / 단가 / 대체발송률 분석

**6. 통합 로그 & 모니터링**

발송 건별로 아래 정보를 기록:

- 요청 서비스 식별자
- 메시지 유형 및 우선순위
- 발송 채널
- 성공 / 실패 여부
- 재시도 횟수
- Fallback 여부 및 경로
- 건별 비용
- 외부 벤더 응답 결과

---

## 기술 스택

| 분류 | 기술                   |
|------|----------------------|
| Language | Java 21              |
| Framework | Spring Boot 4        |
| Message Broker | ActiveMQ (JMS 표준 기반) |
| Build | Gradle               |

> ActiveMQ 채택 이유: JMS 표준 기반으로 Producer/Consumer 구조를 단순하게 유지할 수 있고, 비동기 처리 / 재처리 구조 / 서비스 간 느슨한 결합을 구성하기 용이

---

## 모듈 구조

```
module-{name}/           # 큰 주제 단위로 모듈 구성
  └─ src/main/java/com/message/
     └─ domain/
        └─ {domain_name}/
           ├─ {Entity}.java
           ├─ converter/
           ├─ enum_type/
           ├─ repository/
           ├─ controller/
           └─ service/
```

각 모듈은 독립 실행 단위. Entity부터 Controller까지 해당 도메인의 모든 레이어를 모듈 내에 포함.

---

## 관련 문서

- [코드 컨벤션](docs/convention.md)
