# **Message 서버 코드 컨벤**

## **1. 모듈 및 패키지 구조**

### **1.1 모듈 구조**

각 모듈은 독립적으로 실행 가능한 단위이며, Entity부터 Controller까지 해당 도메인의 모든 레이어를 포함합니다.

```
module-{name}/           # 큰 주제 단위로 모듈 구성 (예: service, batch, admin 등)
  └─ src/main/java/com/message/

```

### **1.2 도메인별 패키지 구조**

모듈 내부에서 도메인별로 모든 레이어(Entity, Repository, Service, Controller, DTO)를 함께 위치시킵니다.

```
{module}/
  └─ src/main/java/com/message/
     └─ domain/
        └─ {domain_name}/
           ├─ {Entity}.java                        # Entity
           ├─ converter/
           │  └─ {Enum}Converter.java              # JPA Converter
           ├─ enum_type/
           │  └─ {Enum}.java                       # Enum 타입
           ├─ repository/
           │  ├─ {Entity}Repository.java           # JPA Repository
           │  └─ {Action}{Entity}Repository.java   # QueryDSL Repository
           ├─ controller/
           │  ├─ {Domain}Controller.java           # REST Controller
           │  ├─ request/
           │  │  └─ {Action}{Entity}.java          # Request DTO
           │  └─ response/
           │     └─ {Action}{Entity}Response.java  # Response DTO
           └─ service/
              ├─ {Domain}Service.java              # 메인 Service (위임)
              ├─ List{Entity}Service.java          # 목록 조회 Service
              ├─ Detail{Entity}Service.java        # 상세 조회 Service
              ├─ Create{Entity}Service.java        # 생성 Service
              ├─ Update{Entity}Service.java        # 수정 Service
              └─ Delete{Entity}Service.java        # 삭제 Service

```

## **2. Entity 작성 규칙**

### **2.1 기본 구조**

```java
@Entity
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name="table_name")
public class EntityName {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name="entity_name_id", columnDefinition="bigint comment 'PK 설명'")
    private Long id;

    // 필드들...

    @CreatedBy
    @Column(name="create_id", updatable=false, columnDefinition="bigint comment '작성자 ID'")
    private Long createBy;

    @LastModifiedBy
    @Column(name="update_id", columnDefinition="bigint comment '수정자 ID'")
    private Long modifyBy;

    @CreatedDate
    @Column(name="create_at", nullable=false, updatable=false, columnDefinition="datetime comment '등록일'")
    private LocalDateTime createAt;

    @LastModifiedDate
    @Column(name="update_at", nullable=false, columnDefinition="datetime comment '수정일'")
    private LocalDateTime updateAt;

    // 생성자
    public EntityName(...) {
        // 필드 초기화
    }

    // 수정 메서드
    public void update(...) {
        // 필드 업데이트
    }
}

```

### **2.2 필수 Annotation**

- `@Entity`: JPA Entity 선언
- `@Getter`: Lombok getter (Setter는 사용하지 않음)
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`: 기본 생성자는 protected
- `@EntityListeners(AuditingEntityListener.class)`: Audit 지원
- `@Table(name = "...")`: 테이블명 명시

### **2.3 컬럼 정의 규칙**

```java
@Column(
    name="column_name",             // 스네이크 케이스
    columnDefinition="타입 comment '설명'",  // 컬럼 타입과 주석
    nullable=false                  // null 허용 여부
)
```

**타입 예시:**

- `bigint` - Long 타입
- `varchar(50)` - String 타입
- `boolean` - boolean 타입 (true/false)
- `datetime` - LocalDateTime
- `date` - LocalDate

### **2.4 Enum 필드 처리**

```java
@Convert(converter= MessageTypeConverter.class)
@Column(name="message_type", columnDefinition="varchar(50) comment '메시지 타입'", nullable=false)
private MessageType messageType;
```

### **2.5 Boolean 필드 처리**

```java
@Column(name="is_displayed", columnDefinition="boolean default false comment '전시여부'", nullable=false)
private boolean displayed;

```

- `@Convert` 불필요, JPA가 `boolean` ↔ `tinyint(1)` 자동 변환
- 별도 Converter 클래스 작성하지 않음

### **2.6 Audit 필드 (필수)**

모든 Entity는 다음 4개 필드를 포함해야 함:

- `createBy` (작성자 ID)
- `modifyBy` (수정자 ID)
- `createAt` (등록일)
- `updateAt` (수정일)

### **2.7 생성자 및 수정 메서드**

- **생성자**: 모든 비즈니스 필드를 받는 생성자 제공 (Audit 필드 제외)
- **update() 메서드**: Entity 수정 시 사용, 변경 가능한 필드만 파라미터로 받음
- **Setter 자제**: Entity는 불변성 유지를 위해 Setter 제공하지 않음

## **3. Enum 작성 규칙**

### **3.1 Enum 기본 구조**

```java
@Getter
@AllArgsConstructor
public enum MessageType {

  PUSH("푸시 알림"),       // 푸시 알림
  SMS("문자 메시지"),      // 문자 메시지
  EMAIL("이메일")          // 이메일
;

  private final String desc;
}

```

### **3.2 Enum 규칙**

- **패키지 위치**: `{domain}/enum_type/`
- **네이밍**: Pascal Case, 명확한 의미의 이름
- **필드**: `desc` 필드로 한글 설명 포함
- **주석**: 각 상수 옆에 한글 주석 추가
- **세미콜론**: 마지막 상수 뒤 세미콜론(`;`) 필수

### **3.3 Enum Converter**

```java
@Converter
public class MessageTypeConverter extends EnumAttributeConverter<MessageType> {

    public MessageTypeConverter() {
        super(MessageType.class);
    }
}

```

**Converter 규칙:**

- **패키지 위치**: `{domain}/converter/`
- **네이밍**: `{EnumName}Converter`
- **상속**: `EnumAttributeConverter<T>` 상속
- **생성자**: Enum 클래스를 super에 전달

## **4. Repository 작성 규칙**

## Repository 데이터 수정 규칙

### 단일 QueryDSL Update 쿼리 사용 자제

```java
❌ 잘못된 방식 (QueryDSL bulk update 직접 사용)

@Repository
@RequiredArgsConstructor
public class UpdateMessageRepository {

  private final JPAQueryFactory queryFactory;

  public void updateStatus(Long messageId, String status) {
      queryFactory
          .update(message)
          .set(message.status, status)
          .where(message.id.eq(messageId))
          .execute();
  }
}

✅ 올바른 방식 (엔티티 조회 후 엔티티 메서드 호출)

    // 1. 엔티티 조회
    Message message = messageRepository.findById(messageId)
        .orElseThrow(() -> new BadRequestException(BaseCode.ERR_ARG_IS_WRONG, "메시지가 존재하지 않습니다"));

    // 2. 엔티티 메서드 호출로 업데이트
    message.updateStatus(status);
    // JPA Dirty Checking으로 자동 업데이트

규칙 요약

1. QueryDSL의 update() 쿼리 사용 금지
  - Bulk update는 영속성 컨텍스트를 우회하여 데이터 정합성 문제 발생 가능
2. 엔티티 조회 후 엔티티 메서드 호출
  - Repository 또는 Service에서 엔티티를 조회
  - 엔티티의 비즈니스 메서드 호출
  - JPA의 변경 감지(Dirty Checking)를 활용한 자동 업데이트
3. @Transactional 필수
  - 수정 메서드는 반드시 @Transactional 선언
  - 트랜잭션 종료 시 변경 감지로 자동 업데이트
4. 위치
  - 데이터 수정 로직은 Service 계층에서 처리
  - Repository는 조회 전용으로 사용
```

### **4.1 기본 Repository (module-core)**

```java
public interface MessageRepository extends JpaRepository<Message, Long> {
    // Spring Data JPA 메서드만 정의
    // 복잡한 쿼리는 module-service의 Repository에서 처리
}

```

### **4.2 QueryDSL Repository (module-service)**

```java
@Repository
@RequiredArgsConstructor
public class DetailMessageRepository {

    private final JPAQueryFactory queryFactory;

    public DetailMessageResponse getMessage(SearchMessage search) {
        return queryFactory.select(Projections.fields(DetailMessageResponse.class,
                message.messageType.as("messageType"),
                message.title,
                message.content,
                message.displayed,
                message.createAt.as("createdAt")
        ))
        .from(message)
        .where(
                message.id.eq(search.getMessageId()),
                message.displayed.isTrue()
        )
        .orderBy(message.id.desc())
        .fetchFirst();
    }
}

```

**QueryDSL Repository 규칙:**

- **패키지 위치**: `{module}/domain/{domain}/repository/`
- **네이밍**: `{Action}{Entity}Repository`
- **Static Import**: Q클래스는 static import 사용
- **Projections**: DTO 직접 매핑 시 `Projections.fields()` 사용
- **조건절**: where 조건은 가독성을 위해 여러 줄로 작성
- **정렬**: orderBy로 정렬 조건 명시

## **5. Service 작성 규칙**

### **5.1 Service 계층 구조**

```
MessageService (메인 Service - 위임)
  └─ DetailMessageService (실제 비즈니스 로직)
       └─ DetailMessageRepository (데이터 조회)

```

### **5.2 메인 Service**

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class MessageService {

    private final DetailMessageService detailMessageService;

    public DetailMessageResponse getMessage(SearchMessage search) {
        return detailMessageService.getMessage(search);
    }
}

```

**메인 Service 규칙:**

- **역할**: 하위 Service에 위임만 수행
- **Transactional**: 클래스 레벨에 `@Transactional(readOnly = true)` 선언
- **네이밍**: `{Domain}Service`

### **5.3 세부 Service**

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class DetailMessageService {

    private final DetailMessageRepository detailMessageRepository;

    public DetailMessageResponse getMessage(SearchMessage search) {
        return detailMessageRepository.getMessage(search);
    }
}

```

**세부 Service 규칙:**

- **네이밍**:
    - 목록 조회: `List{Entity}Service`
    - 상세 조회: `Detail{Entity}Service`
    - 생성: `Create{Entity}Service`
    - 수정: `Update{Entity}Service`
    - 삭제: `Delete{Entity}Service`
- **Transactional**:
    - 조회: `@Transactional(readOnly = true)`
    - 쓰기: `@Transactional` (readOnly 생략)
- **의존성**: Repository 의존성 주입

## **6. Controller 작성 규칙**

### **6.1 Controller 구조**

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/message/v2/allows/messages")
@Api(value="MessageController", tags={"메시지 관리"})
public class MessageController {

    private final MessageService messageService;

    @ApiOperation(value="메시지 조회")
    @GetMapping
    public DefaultHttpResponse<DetailMessageResponse> getMessage(
            @Valid @ModelAttribute SearchMessage search
    ) {
        return new DefaultHttpResponse<>(BaseCode.SUCCESS, messageService.getMessage(search));
    }
}

```

### **6.2 Controller 규칙**

- **패키지 위치**: `{module}/domain/{domain}/controller/`
- **Annotation**:
    - `@RestController`: REST API Controller
    - `@RequiredArgsConstructor`: 의존성 주입
    - `@RequestMapping`: Base URL 정의
    - `@Api`: Swagger 문서화 (태그는 한글로)
- **URL 규칙**:
    - 인증 불필요: `/message/v2/allows/{resource}`
    - 인증 필요: `/message/v2/auth/{resource}`
- **응답 타입**: `DefaultHttpResponse<T>` 사용
- **메서드 시그니처**:
    - GET (Query): `@ModelAttribute` + `@Valid`
    - POST/PUT (Body): `@RequestBody` + `@Valid`
    - Path Variable: `@PathVariable`

## **7. DTO 작성 규칙**

### **7.1 Request DTO**

```java
@Getter
@ApiModel(value="SearchMessage", description="메시지 조회 요청 Dto")
public class SearchMessage {

    @NotNull(message="메시지 ID는 필수입니다.")
    @ApiModelProperty(value="메시지 ID", required=true)
    private Long messageId;

    @NotNull(message="메시지 타입은 필수입니다.")
    @ApiModelProperty(value="메시지 타입", required=true)
    private MessageType messageType;

    public SearchMessage(Long messageId, MessageType messageType) {
        this.messageId = messageId;
        this.messageType = messageType;
    }
}

```

**Request DTO 규칙:**

- **패키지 위치**: `controller/request/`
- **네이밍**: `{Action}{Entity}` (예: SearchMessage, CreateMessage)
- **Annotation**:
    - `@Getter`: Lombok getter
    - `@ApiModel`: Swagger 문서화
    - Validation: `@NotNull`, `@NotBlank`, `@Size` 등
    - `@ApiModelProperty`: 필드 설명
- **Setter 금지**: Setter 대신 생성자 사용
- **Validation 메시지**: 한글로 명확하게

### **7.2 Response DTO**

```java
@Getter
@ApiModel(value="DetailMessageResponse", description="메시지 상세 응답 Dto")
public class DetailMessageResponse {

    @ApiModelProperty(value="메시지 타입", required=true)
    private MessageType messageType;

    @ApiModelProperty(value="메시지 타입 설명", required=true)
    private String messageTypeValue;

    @ApiModelProperty(value="제목", required=true)
    private String title;

    @ApiModelProperty(value="내용", required=true)
    private String content;

    @ApiModelProperty(value="전시여부", required=true)
    private boolean displayed;

    @ApiModelProperty(value="등록일", required=true)
    private LocalDateTime createdAt;

    // Enum 값의 한글 설명을 반환하는 메서드
    public String getMessageTypeValue() {
        return !ObjectUtils.isEmpty(messageType)
            ? messageType.getDesc()
            : null;
    }
}

```

**Response DTO 규칙:**

- **패키지 위치**: `controller/response/`
- **네이밍**: `{Action}{Entity}Response`
- **Annotation**:
    - `@Getter`: Lombok getter
    - `@ApiModel`: Swagger 문서화
    - `@ApiModelProperty`: 필드 설명 (required 명시)
- **Enum 처리**: Enum 필드와 함께 `{field}Value` 필드로 한글 설명 제공
- **날짜 필드**: `LocalDateTime`, `LocalDate` 타입 사용

## **8. 네이밍 컨벤션**

### **8.1 Java 네이밍**

- **클래스/인터페이스**: PascalCase (예: `MessageDetail`)
- **메서드/변수**: camelCase (예: `getMessage`)
- **상수**: UPPER_SNAKE_CASE (예: `MAX_SIZE`)
- **패키지**: 소문자, 언더스코어 (예: `message_detail`)

### **8.2 데이터베이스 네이밍**

- **테이블명**: 소문자, 언더스코어 (예: `message_detail`)
- **컬럼명**: 소문자, 언더스코어 (예: `message_detail_id`)
- **PK 컬럼**: `{table_name}_id`
- **Boolean 컬럼**: `is_{name}` 또는 `{name}` (예: `is_displayed`, `displayed`)
- **날짜 컬럼**:
    - 생성일: `create_at` / `created_at`
    - 수정일: `update_at` / `updated_at`
    - 시작일: `{name}_begin_at`
    - 종료일: `{name}_end_at`

### **8.3 API 네이밍**

- **URL**: 케밥 케이스 (예: `/message-detail`)
- **JSON 필드**: 스네이크 케이스 (예: `message_type`)
    - `application.yml`에 `spring.jackson.property-naming-strategy: SNAKE_CASE` 설정됨

### **8.4 메서드 네이밍**

- **조회 (단건)**: `get{Entity}` 또는 `find{Entity}`
- **조회 (목록)**: `list{Entity}` 또는 `get{Entity}List`
- **생성**: `create{Entity}` 또는 `save{Entity}`
- **수정**: `update{Entity}` 또는 `modify{Entity}`
- **삭제**: `delete{Entity}` 또는 `remove{Entity}`
- **존재 확인**: `exists{Entity}`
- **개수**: `count{Entity}`

## **9. Annotation 사용 규칙**

### **9.1 Lombok**

- **권장**: `@Getter`, `@RequiredArgsConstructor`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@Builder`
- **금지**: `@Setter`, `@Data` (불변성 유지를 위해)

### **9.2 Transaction**

- **조회 메서드**: `@Transactional(readOnly = true)`
- **쓰기 메서드**: `@Transactional`
- **클래스 레벨**: 기본 전략 설정, 메서드 레벨에서 오버라이드

### **9.3 Validation**

- **필수 값**: `@NotNull`, `@NotBlank`, `@NotEmpty`
- **크기 제한**: `@Size(min=?, max=?)`
- **패턴**: `@Pattern(regexp="...")`
- **범위**: `@Min`, `@Max`

### **9.4 Swagger**

- **Controller**: `@Api(tags = {"한글 설명"})`
- **메서드**: `@ApiOperation(value = "한글 설명")`
- **DTO**: `@ApiModel(description = "한글 설명")`
- **필드**: `@ApiModelProperty(value = "한글 설명", required = true/false)`

## **10. 코딩 스타일**

### **10.1 Import 순서**

1. Java 표준 라이브러리 (`java.*`, `javax.*`)
2. 외부 라이브러리
3. Spring Framework
4. 내부 패키지 (`com.message.*`)

### **10.2 들여쓰기 및 포맷팅**

- **들여쓰기**: 스페이스 2칸
- **중괄호**: K&R 스타일 (같은 줄에 열기)
- **줄 길이**: 최대 120자 권장

### **10.3 주석**

- **한글 주석 사용**: 비즈니스 로직, 복잡한 알고리즘 설명
- **JavaDoc**: Public API에 필수
- **컬럼 주석**: `columnDefinition`에 comment 포함
- **Enum 주석**: 각 상수 옆에 의미 설명

### **10.4 필드 순서 (Entity)**

1. `@Id` 필드
2. 비즈니스 필드 (Enum, 일반 필드, 연관 관계 순)
3. Audit 필드 (`createBy`, `modifyBy`, `createAt`, `updateAt`)

## **11. Git 커밋 컨벤션**

### **11.1 커밋 메시지 형식**

```
[이슈번호] [모듈] 작업 내용

- 상세 내용 1
- 상세 내용 2

```

**예시:**

```
[MSG-100] 메시지 발송 기능 추가

- 엔티티 추가

```

```
[MSG-100] 메시지 발송 기능 추가

- 조회 API 추가

```

### **11.2 브랜치 전략**

- **main 브랜치**: `production`
- **개발 브랜치**: `stage`
- **기능 브랜치**: `feature/MSG-{번호}`
- **핫픽스 브랜치**: `hotfix/v{버전}`
- **릴리즈 브랜치**: `release/v{버전}`

### **11.3 커밋 단위**

- **Entity 추가**: 별도 커밋
- **API 추가**: 별도 커밋 (Controller, Service, Repository, DTO 포함)
- **수정/개선**: 작업 단위별 커밋
- **원자적 커밋**: 하나의 논리적 변경사항만 포함

## **12. 특이사항**

### **12.1 한글 사용**

- **테스트 메서드명**: 한글 사용 (예: `메시지_조회_테스트`)
- **커밋 메시지**: 한글 사용
- **주석**: 한글 사용
- **Validation 메시지**: 한글 사용
- **Swagger 문서**: 한글 사용

### **12.2 Boolean 타입**

- **Java 필드**: `boolean` 타입 (primitive)
- **DB 컬럼**: `boolean` (tinyint(1), true/false)
- **Converter 불필요**: JPA 기본 변환 사용
- **네이밍**: `is_{name}` 또는 `{name}` (예: `displayed`, `isActive`)

### **12.3 날짜/시간 타입**

- **일시**: `LocalDateTime` (컬럼 타입: `datetime`)
- **날짜**: `LocalDate` (컬럼 타입: `date`)
- **시각**: `LocalTime` (컬럼 타입: `time`)

### **12.4 Primary Key**

- **타입**: `Long`
- **생성 전략**: `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- **컬럼명**: `{table_name}_id`
- **필드명**: `id`

## **13. 체크리스트**

### **CRUD 기능 개발 시 체크리스트**

### **Entity 생성**

- [ ]  `@Entity`, `@Getter`, `@NoArgsConstructor(protected)` 선언
- [ ]  `@EntityListeners(AuditingEntityListener.class)` 추가
- [ ]  `@Table(name = "...")` 테이블명 지정
- [ ]  PK 필드 정의 (`@Id`, `@GeneratedValue`)
- [ ]  모든 컬럼에 `columnDefinition` 작성 (타입 + comment)
- [ ]  Enum 필드는 `@Convert` 사용
- [ ]  Boolean 필드는 `boolean` 타입 그대로 사용 (Converter 불필요)
- [ ]  Audit 필드 4개 추가 (createBy, modifyBy, createAt, updateAt)
- [ ]  생성자 작성 (Audit 필드 제외)
- [ ]  `update()` 메서드 작성

### **Enum 및 Converter 생성**

- [ ]  Enum에 `@Getter`, `@AllArgsConstructor` 추가
- [ ]  `desc` 필드로 한글 설명 포함
- [ ]  각 상수에 한글 주석 추가
- [ ]  Converter 클래스 생성 (`EnumAttributeConverter` 상속)

### **Repository 생성**

- [ ]  `JpaRepository` 인터페이스 생성
- [ ]  QueryDSL Repository 생성 (필요 시)
- [ ]  Q클래스 static import
- [ ]  `Projections.fields()`로 DTO 매핑

### **Service 생성**

- [ ]  메인 Service 생성 (위임 역할)
- [ ]  세부 Service 생성 (List, Detail, Create, Update, Delete)
- [ ]  `@Service`, `@RequiredArgsConstructor` 추가
- [ ]  조회: `@Transactional(readOnly = true)`
- [ ]  쓰기: `@Transactional`

### **DTO 생성**

- [ ]  Request DTO: `controller/request/` 패키지
- [ ]  Response DTO: `controller/response/` 패키지
- [ ]  `@Getter`, `@ApiModel`, `@ApiModelProperty` 추가
- [ ]  Validation annotation 추가 (Request)
- [ ]  Enum 필드의 `{field}Value` getter 추가 (Response)

### **Controller 생성**

- [ ]  `@RestController`, `@RequiredArgsConstructor` 추가
- [ ]  `@RequestMapping` URL 정의 (allows/auth 구분)
- [ ]  `@Api` Swagger 태그 추가 (한글)
- [ ]  `@ApiOperation` 메서드 설명 추가
- [ ]  `DefaultHttpResponse<T>` 반환

### **커밋**

- [ ]  Entity 추가 커밋 (별도)
- [ ]  API 추가 커밋 (별도)
- [ ]  커밋 메시지 형식: `[이슈번호] [모듈] 작업내용`

---
