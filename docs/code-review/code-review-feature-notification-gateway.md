# 코드 리뷰 — feature/notification-gateway

- **리뷰 일자**: 2026-03-29
- **브랜치**: feature/notification-gateway
- **리뷰 대상 커밋**:
  - feat : 모니터링 gracefulShutdown
  - feat : DLQ Handler + Slack 알림
  - feat : AppPush Vendor
  - feat : AWS SES Vendor
  - feat : SOLAPI Vendor

---

## 요약

전반적으로 메시지 게이트웨이의 핵심 아키텍처(AbstractWorker, 우선순위별 큐, Fallback/DLQ 흐름)는 잘 설계되어 있습니다. 다만 팀 컨벤션 위반(Entity Audit 필드, Enum 처리 방식, 트랜잭션 선언)이 여러 곳에 걸쳐 발견되고, 이메일 Subject/Body 매핑 역전 버그처럼 런타임에서 잘못된 동작을 유발하는 CRITICAL 이슈도 포함되어 있습니다.

---

## CRITICAL

### 1. `SesV2Sender` — Subject/Body 필드 역전 버그

**위치**: `SesV2Sender.java:71-77`

`resolveSubject`가 `content`를 반환하고 `resolveBody`가 `templateCode`를 반환하는데, 실제 사용에서:

```java
.subject(Content.builder().data(resolveSubject(message))...) // content → 제목으로 들어감
.text(Content.builder().data(resolveBody(message))...)       // templateCode → 본문으로 들어감
```

이메일 **제목(subject)** 에 `content` 필드가, **본문(body)** 에 `"[알림] templateCode"` 형식이 들어가는 게 의도된 설계인지 확인이 필요합니다. 일반적으로 역전된 것처럼 보입니다.

---

### 2. `NotificationLog` — 컨벤션 위반 (Entity 필수 요소 다수 누락)

**위치**: `NotificationLog.java`

| 항목 | 현재 | 컨벤션 |
|------|------|--------|
| PK 필드명 | `notificationLogId` | `id` |
| Audit 리스너 | 없음 | `@EntityListeners(AuditingEntityListener.class)` 필수 |
| Audit 필드 | `createAt`만 존재 | `createBy`, `modifyBy`, `createAt`, `updateAt` 4개 필수 |
| Enum 처리 | `@Enumerated(EnumType.STRING)` | `@Convert(converter = XxxConverter.class)` |
| columnDefinition | 없음 | 모든 컬럼에 타입+comment 필수 |

`createAt`도 `LocalDateTime.now()`로 수동 설정 중인데, Audit을 적용하면 `@CreatedDate`로 처리해야 합니다.

---

### 3. `SaveNotificationService` — `@Transactional` 누락 + 이중 조회 비효율

**위치**: `SaveNotificationService.java:34-44`

```java
// 문제 1: 클래스/메서드 레벨 @Transactional 없음 (컨벤션 5.2 위반)
public NotificationResponse request(NotificationRequest request) {

// 문제 2: exist → find 이중 쿼리 (불필요한 DB 조회, 조회 결과 미사용)
if (detailNotificaionLogRepository.existsByIdempotencyKey(idempotencyKey)) {
    detailNotificaionLogRepository.findByIdempotencyKey(idempotencyKey)  // 결과를 아무도 사용하지 않음
        .orElseThrow(() -> new ApiException(ErrorCode.DUPLICATE_REQUEST));
    throw new ApiException(ErrorCode.DUPLICATE_REQUEST);  // 어차피 여기서 던짐
}
```

또한 `saveLog()` 내부에서 `parseChannel()`을 한 번 더 호출하고 있어서, `request()` 메서드에서 이미 파싱한 값을 다시 파싱합니다.

**수정 제안:**

```java
// TO-BE
if (detailNotificaionLogRepository.existsByIdempotencyKey(idempotencyKey)) {
    log.warn("Duplicate request detected: idempotencyKey={}", idempotencyKey);
    throw new ApiException(ErrorCode.DUPLICATE_REQUEST);
}
```

---

### 4. 테스트 파일 — `SmsWorkerTest`, `StageEmailFilterTest` 미구현

**위치**: `SmsWorkerTest.java`, `StageEmailFilterTest.java`

두 파일 모두 클래스만 선언되고 테스트 케이스가 없습니다. `AlimtalkWorkerTest`에는 테스트가 있는 것으로 보아 의도적으로 스켈레톤만 만든 것으로 보이나, 빈 테스트 파일은 커밋 전 구현이 필요합니다.

---

## WARNING

### 5. `AbstractWorker` — fallback 큐 이름 생성 로직 중복

**위치**: `AbstractWorker.java:69`

```java
// AbstractWorker에서 직접 큐 이름을 문자열 조합
String fallbackQueue = "noti." + message.priority().toLowerCase() + "." + message.fallbackChannel().toLowerCase();
```

이 로직은 `NotificationMessage.queueName()`과 동일한 패턴입니다. `NotificationMessage`에 `fallbackQueueName()` 메서드를 추가하거나, fallback용 `NotificationMessage` 생성 후 `queueName()`을 호출하는 방식이 더 일관성 있습니다.

---

### 6. `SolapiSender`, `FingerpushSender` — `RestClient` 인스턴스 필드 선언

**위치**: `SolapiSender.java:31`, `FingerpushSender.java:28`

```java
private final RestClient restClient = RestClient.create();  // @RequiredArgsConstructor와 함께 선언
```

Spring Bean에 주입받지 않고 직접 생성하면 **테스트에서 Mock이 불가능**합니다. `@Bean`으로 등록하거나 생성자 주입으로 변경을 권장합니다.

---

### 7. `FingerpushSender` — 배치 발송 중 부분 실패 처리 없음

**위치**: `FingerpushSender.java:61-74`

500건씩 배치로 나눠 발송하지만, 중간 배치에서 예외가 발생해도 이미 발송된 배치는 롤백할 수 없고, 전체를 실패로 처리합니다. 또한 `lastId`가 빈 배치일 경우 null이 됩니다:

```java
String lastId = null;
for (List<String> batch : batches) {
    lastId = postToFingerpush("/push/target", body);  // batches 비어있으면 null 유지
}
return SendResult.success("target-" + lastId, 0);  // "target-null"
```

또한 `recipient.split(",")` 시 공백을 제거하지 않아 `" 01087654321"` 같은 값이 토큰으로 들어갈 수 있습니다.

**수정 제안:**

```java
// TO-BE — split 공백 처리
return sendTarget(Arrays.stream(recipient.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList(), content);
```

---

### 8. `SesV2Sender` — `@Value` 다중 프로파일 대응 불가

**위치**: `SesV2Sender.java:20`

```java
@Value("${spring.profiles.active:local}")
private String activeProfile;
```

`spring.profiles.active=stage,feature` 처럼 여러 프로파일이 활성화된 경우 `"stage".equals(activeProfile)` 비교가 실패합니다.

**수정 제안:**

```java
// TO-BE
private final Environment environment;
// ...
if (environment.acceptsProfiles(Profiles.of("stage")) && !stageEmailFilter.isAllowed(toEmail)) {
```

---

### 9. `NotificationMetrics` — `recordDlq` 캐싱 방식 불일치

**위치**: `NotificationMetrics.java:39-45`

`recordSent`, `recordFailed`는 `ConcurrentHashMap`으로 Counter를 캐싱하는데, `recordDlq`는 매번 `Counter.builder().register()`를 호출합니다. `MeterRegistry`가 중복 등록된 Counter를 반환하므로 기능적으로는 문제없지만, 세 메서드의 구현 방식이 불일관합니다.

---

### 10. `ActiveMqShutdownManager` — 대기 시간 하드코딩

**위치**: `ActiveMqShutdownManager.java:24`

```java
Thread.sleep(3000); // 처리 중인 메시지 완료 대기
```

3초가 항상 충분하다는 보장이 없습니다. 설정 값으로 분리하거나, `jmsListenerEndpointRegistry.stop()`이 `SmartLifecycle`의 `stop(Runnable callback)` 형태를 지원하므로 이를 활용하는 방식을 권장합니다.

---

## INFO

### 11. `AlimtalkWorkerTest` — 테스트 메서드명 영어 (컨벤션 위반)

**위치**: `AlimtalkWorkerTest.java:43, 69`

컨벤션 12.1에 따르면 테스트 메서드명은 한글을 사용해야 합니다.

```java
// AS-IS
void processHigh_fallback_to_sms()
void processHigh_no_fallback_to_dlq()

// TO-BE
void 알림톡_HIGH_재시도소진_Fallback_SMS큐로_재발행()
void 알림톡_HIGH_재시도소진_Fallback없으면_DLQ_적재()
```

---

### 12. Enum 패키지 위치 불일치

**위치**: `domain/notification/enums/`

컨벤션 3.2에 따르면 Enum 패키지는 `enum_type/`이어야 하는데, 현재 `enums/`를 사용 중입니다.

---

### 13. `SolapiSender` — 발송 비용 하드코딩

**위치**: `SolapiSender.java:52`

```java
return SendResult.success(extractMessageId(response), 8.0); // 8원 하드코딩
```

비용을 `SolapiProperties`에서 가져오거나, 실제 API 응답에서 파싱하는 방식이 더 확장성 있습니다.

---

## 이슈 요약

| 심각도 | 건수 | 주요 항목 |
|--------|------|-----------|
| CRITICAL | 4 | SES Subject/Body 역전, NotificationLog 컨벤션 위반, Transactional 누락, 빈 테스트 파일 |
| WARNING | 6 | RestClient Mock 불가, FingerpushSender 부분 실패, SES 프로파일 체크, Metrics 불일관, Shutdown 하드코딩, fallback 큐 이름 중복 |
| INFO | 3 | 테스트 메서드명 한글화, Enum 패키지 위치, 비용 하드코딩 |
