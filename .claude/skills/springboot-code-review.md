---
name: springboot-code-review
description: SpringBoot/JPA 프로젝트의 코드 리뷰를 수행하는 스킬. 팀 코드 컨벤션 준수 여부, DDD 원칙, 레이어 아키텍처 위반, Entity 설계, Service 구조, Repository 패턴, DTO 설계, Controller 규칙 등을 검토한다. 사용자가 Java/Kotlin 코드를 붙여넣거나 파일을 업로드하고 '코드 리뷰', '리뷰해줘', 'review', '컨벤션 체크', '코드 검토', '이 코드 괜찮아?', 'PR 리뷰' 등을 요청할 때 트리거된다. SpringBoot, JPA, QueryDSL 관련 코드가 포함된 경우에도 트리거한다.
---

# SpringBoot Code Review Skill

사용자가 제출한 SpringBoot/JPA 코드를 팀 컨벤션과 소프트웨어 설계 원칙에 따라 체계적으로 리뷰한다.

## 리뷰 수행 절차

1. **코드 파악**: 제출된 코드의 역할(Entity, Service, Controller 등)을 파악한다
2. **컨벤션 파일 로딩**: `docs/convention.md`를 읽어 팀 컨벤션을 확인한다
3. **계층별 리뷰**: 해당 코드의 레이어에 맞는 리뷰 기준을 적용한다
4. **설계 원칙 검토**: DDD, SOLID 등 설계 원칙 위반 여부를 검토한다
5. **결과 보고**: 심각도별로 분류하여 리뷰 결과를 전달한다

## 리뷰 관점

### 1. 레이어 아키텍처 검증

이 프로젝트는 Controller → Service → Repository 계층 구조를 따른다. 각 레이어의 책임이 올바른지 확인한다.

**Controller 레이어**
- HTTP 요청/응답 처리만 담당하는가
- 비즈니스 로직이 Controller에 침투하지 않았는가
- `DefaultHttpResponse<T>`로 응답을 감싸고 있는가
- `@Valid`로 입력 검증을 위임하고 있는가
- URL 규칙을 따르는가 (인증 불필요: `/message/v2/allows/`, 인증 필요: `/message/v2/auth/`)

**Service 레이어**
- 메인 Service는 위임만 수행하는가 (비즈니스 로직 직접 구현 금지)
- 세부 Service(List, Detail, Create, Update, Delete)로 책임이 분리되어 있는가
- 조회 메서드에 `@Transactional(readOnly = true)`, 쓰기 메서드에 `@Transactional`이 선언되어 있는가
- 데이터 수정이 Entity의 비즈니스 메서드를 통해 이루어지는가 (Dirty Checking 활용)

**Repository 레이어**
- JPA Repository는 조회 전용으로 사용되고 있는가
- QueryDSL에서 `update()` 쿼리를 직접 사용하지 않는가
- `Projections.fields()`로 DTO를 직접 매핑하고 있는가
- Q클래스를 static import로 사용하고 있는가

### 2. Entity 설계 검증

Entity는 도메인의 핵심이므로 가장 엄격하게 검증한다.

**필수 요소 체크리스트:**
- `@Entity`, `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 선언 여부
- `@EntityListeners(AuditingEntityListener.class)` 추가 여부
- `@Table(name = "...")` 테이블명 명시 여부
- PK: `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`, 필드명은 `id`, 컬럼명은 `{table_name}_id`
- 모든 컬럼에 `columnDefinition` 작성 (타입 + comment)
- Audit 필드 4개: `createBy`, `modifyBy`, `createAt`, `updateAt`
- 생성자 제공 (Audit 필드 제외)
- `update()` 메서드 제공

**금지 항목:**
- `@Setter` 사용 금지 — Entity의 불변성을 훼손한다
- `@Data` 사용 금지 — 내부적으로 Setter를 생성한다
- Audit 필드를 생성자에 포함 금지

**Enum 필드:**
- `@Convert(converter = XxxConverter.class)` 사용
- Converter는 `EnumAttributeConverter<T>`를 상속

**Boolean 필드:**
- `boolean` primitive 타입 사용 (Wrapper 타입 금지)
- `@Convert` 불필요 — JPA 자동 변환
- 별도 Converter 클래스 작성 금지

### 3. DDD (Domain-Driven Design) 원칙

Entity가 단순한 데이터 홀더가 아닌 도메인 로직의 중심이 되어야 한다.

**Rich Domain Model:**
- 상태 변경 로직이 Entity 내부 메서드로 캡슐화되어 있는가
- Service에서 Entity의 필드를 직접 세팅하지 않고, Entity의 비즈니스 메서드를 호출하는가
- Entity가 자신의 불변식(invariant)을 스스로 보장하는가

**Aggregate 경계:**
- Entity 간 연관관계가 과도하지 않은가
- 트랜잭션 범위가 Aggregate 단위로 적절한가

**Anemic Domain Model 경고:**
- Entity에 getter만 있고 비즈니스 메서드가 없으면 경고한다
- 모든 로직이 Service에 몰려 있으면 개선을 제안한다

### 4. SOLID 원칙

**SRP (단일 책임 원칙):**
- 하나의 Service가 너무 많은 역할을 하고 있지 않은가
- 이 프로젝트는 Action별 Service 분리(List, Detail, Create, Update, Delete)를 통해 SRP를 실현한다

**OCP (개방-폐쇄 원칙):**
- Enum, 전략 패턴 등으로 확장에 열려 있는가
- 새로운 타입 추가 시 기존 코드 수정이 필요한 구조인가

**DIP (의존성 역전 원칙):**
- Service가 구체 클래스에 직접 의존하지 않는가
- 인터페이스를 통한 의존성 주입이 이루어지는가 (`@RequiredArgsConstructor` + `final` 필드)

### 5. DTO 설계 검증

**Request DTO:**
- `controller/request/` 패키지에 위치하는가
- `{Action}{Entity}` 네이밍을 따르는가
- Validation annotation(`@NotNull`, `@NotBlank` 등)이 적절한가
- Validation 메시지가 한글로 명확한가
- Setter 없이 생성자로 초기화하는가

**Response DTO:**
- `controller/response/` 패키지에 위치하는가
- `{Action}{Entity}Response` 네이밍을 따르는가
- Enum 필드에 대해 `{field}Value` getter로 한글 설명을 제공하는가
- 날짜 필드가 `LocalDateTime` / `LocalDate` 타입인가

### 6. 네이밍 컨벤션 검증

- **클래스**: PascalCase
- **메서드/변수**: camelCase
- **상수**: UPPER_SNAKE_CASE
- **패키지**: 소문자 + 언더스코어 (예: `enum_type`)
- **DB 테이블/컬럼**: snake_case
- **API URL**: 케밥 케이스
- **메서드명 패턴**: get/find(단건 조회), list(목록 조회), create/save(생성), update/modify(수정), delete/remove(삭제)

### 7. Swagger 문서화 검증

- Controller: `@Api(tags = {"한글 설명"})`
- 메서드: `@ApiOperation(value = "한글 설명")`
- DTO: `@ApiModel(description = "한글 설명")`
- 필드: `@ApiModelProperty(value = "한글 설명", required = true/false)`

## 리뷰 결과 보고 형식

리뷰 결과를 심각도별로 분류하여 보고한다. 과도한 형식을 피하고, 핵심 이슈를 명확하게 전달하는 것이 목표다.

### 심각도 분류

- **CRITICAL**: 런타임 오류, 데이터 정합성 문제, 보안 취약점 등 반드시 수정해야 하는 항목
- **WARNING**: 컨벤션 위반, 설계 원칙 위반 등 수정을 강력히 권고하는 항목
- **INFO**: 개선하면 좋지만 당장 문제가 되지는 않는 제안 사항

### 보고 구조

리뷰 결과는 다음 순서로 전달한다:

1. **요약** — 전체적인 코드 품질에 대한 간단한 평가 (2~3문장)
2. **이슈 목록** — 심각도별로 그룹핑하여 각 이슈를 설명한다. 각 이슈에는 해당 코드 위치, 문제 설명, 수정 제안을 포함한다.
3. **수정 코드 제안** — CRITICAL/WARNING 이슈에 대해 수정된 코드 예시를 제공한다

### 리뷰 톤

- 코드 작성자를 존중하는 톤을 유지한다
- "~하면 좋겠습니다", "~를 권장합니다" 형태로 제안한다
- 잘 작성된 부분이 있으면 먼저 언급한 뒤 개선점을 제안한다
- 왜 그렇게 해야 하는지 이유를 함께 설명한다

## 코드가 여러 파일인 경우

사용자가 여러 파일을 한 번에 제출하면, 파일 간의 관계도 함께 검토한다:

- Entity ↔ Repository 간 타입 일관성
- Service ↔ Repository 간 의존성 방향
- Controller ↔ Service 간 DTO 흐름
- 패키지 구조가 도메인별로 올바르게 나뉘어 있는가

## 주의사항

- 컨벤션 파일이 업데이트되었을 수 있으므로 항상 `docs/convention.md`를 새로 읽는다
- 리뷰 시 컨벤션에 명시되지 않은 항목은 일반적인 SpringBoot 모범 사례를 기준으로 판단하되, 컨벤션 규칙과 충돌하지 않도록 한다
- 사용자가 특정 부분만 리뷰를 요청한 경우, 해당 부분에 집중하되 심각한 문제가 보이면 함께 알려준다
