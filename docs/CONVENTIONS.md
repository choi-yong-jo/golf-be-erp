# golf-be-erp 개발 규칙

DDL: `src/main/resources/db/golf-erp-db.sql` (PostgreSQL, 스키마 `golf`). 이 DDL 이 진실의 원천이다.
엔티티는 DDL 에 맞춰 쓰고, 기동 시 `ddl-auto: validate` 가 어긋남을 잡아낸다.

참조 구현: `com.golf.erp.resource` (캐디·카트), `com.golf.erp.system` (계정·역할).

## 패키지

```
com.golf.erp
├─ common        예외, PageResult, Pages, Specs, DocumentNo, Ref/RefChecker, AuditLogger
│  └─ port       모듈 간 인터페이스 (GreenFeeQuoter, ReservationLifecycle)
├─ config        ErpProperties, TimeConfig(Clock), OpenApiConfig
├─ security      JWT, ErpModule(URL→권한), CurrentUser
└─ <module>      업무 모듈 하나 = 패키지 하나 (평평하게, 하위 패키지 없음)
```

모듈 패키지 안의 파일:

| 파일 | 내용 |
|---|---|
| `Xxx.java` | JPA 엔티티 (테이블 하나에 하나) |
| `XxxStatus.java` 등 | CHECK 제약이 있는 상태·유형 컬럼의 enum |
| `XxxRepository.java` | `JpaRepository<Xxx, Long>, JpaSpecificationExecutor<Xxx>` |
| `XxxDtos.java` | 모듈의 요청/응답 record 모음 (final class 안의 public record) |
| `XxxService.java` | `@Service @Transactional`, 조회 메서드는 `@Transactional(readOnly = true)` |
| `XxxController.java` | `@RestController`, `@Tag(name = "...")`, 얇게 유지 |

## 모듈 경계

- **다른 모듈의 엔티티·리포지토리·서비스를 import 하지 않는다.** 다른 테이블은 id(Long) 로만 참조한다.
- 존재 확인은 `RefChecker.require(Ref.X, id)` / `requireIfPresent(...)` (없으면 404).
- 다른 테이블을 읽어야 하면 자기 모듈 안에서 `JdbcTemplate` 로 필요한 컬럼만 SQL 조회한다(테이블명은 `golf.` 접두어).
- 다른 모듈의 상태를 바꿔야 하면 `common.port` 의 인터페이스를 쓴다(구현은 소유 모듈).
- 새 Ref 가 필요하면 `common/Ref.java` 에 추가하지 말고 통합 담당에게 요청한다(공용 파일은 수정 금지).

## 엔티티

- `@Entity @Table(name = "테이블")`, 모든 컬럼에 `@Column(name = "...")` 명시, `length`/`precision` 은 DDL 과 맞춘다.
- PK: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "xxx_id") private Long id;`
- **타입 매핑 (validate 가 검사하므로 반드시 지킨다)**

  | DDL | Java |
  |---|---|
  | BIGINT | `Long` |
  | INTEGER | `Integer` / `int` |
  | **SMALLINT** | **`Short` / `short`** (Integer 쓰면 기동 실패) |
  | NUMERIC(p,s) | `BigDecimal` + `@Column(precision = p, scale = s)` |
  | BOOLEAN | `boolean` / `Boolean` |
  | DATE / TIME / TIMESTAMP(6) | `LocalDate` / `LocalTime` / `LocalDateTime` |
  | VARCHAR(n) | `String` + `length = n` |
  | TEXT | `String` + `@Column(columnDefinition = "text")` |
  | JSONB | `String` + `@JdbcTypeCode(SqlTypes.JSON)` |

- 상태·유형 컬럼(CHECK 있는 것)은 `@Enumerated(EnumType.STRING)` + enum. enum 상수는 CHECK 값과 정확히 같게.
- DDL 에 `version` 컬럼이 있으면 `@Version private Long version;`
- `protected` 기본 생성자 + 의미 있는 생성자. **setter 금지**, 상태 변경은 이름 있는 메서드(`confirm(now)`, `cancel(reason, now)`)로.
  상태 전이 규칙(허용되지 않는 전이 → `ConflictException`)은 엔티티 메서드 안에서 검사한다.
- getter 는 필요한 것만. 연관관계 매핑(`@ManyToOne` 등)은 같은 모듈 안 부모-자식(예: 주문-주문항목)에만 쓰고, 그 외엔 id 컬럼만 둔다.
- `created_at`/`updated_at` 은 서비스가 `LocalDateTime.now(clock)` 으로 넣는다(DB DEFAULT 에 기대지 않는다 — insert 시 null 이 들어가면 NOT NULL 위반).
  **NOT NULL + DEFAULT 컬럼도 엔티티에서 반드시 값을 채운다** (Hibernate 는 null 을 그대로 insert 한다).
- DDL 의 중복 컬럼(같은 뜻의 컬럼 두 개)은 하나를 기준으로 쓰고 다른 하나도 같은 값으로 저장한다(예: `Caddie`).

## API

- 경로: `/api/<복수형-kebab>` . 반드시 `security/ErpModule` 에 등록된 경로 아래에 둔다(등록 안 된 경로는 권한 검사가 빠짐).
- 메서드:
  - `GET /xxx` 목록(페이지) — `page`(0부터, 기본 0), `size`(기본 20, 최대 100), 필터는 선택 `@RequestParam(required = false)`
  - `GET /xxx/{id}` 상세
  - `POST /xxx` 등록 → `201 Created` (`@ResponseStatus(HttpStatus.CREATED)`), 생성된 리소스 반환
  - `PUT /xxx/{id}` 수정(전체 필드)
  - `PATCH /xxx/{id}/status` 등 상태 변경 / `POST /xxx/{id}/<동사>` 업무 처리(confirm, cancel, close, approve ...)
  - `DELETE /xxx/{id}` → `204`. 이력이 남아야 하는 데이터는 물리 삭제 대신 비활성(is_active=false)·취소 상태로.
- 목록 응답: `PageResult<T>` (`PageResult.of(page, mapper)`), 페이지네이션: `Pages.of(page, size, Sort)`.
  코드표처럼 건수가 적은 마스터(코스, 홀, 역할 등)는 `List<T>` 로 돌려줘도 된다.
- 목록 필터: `Specification.allOf(Specs.eq(...), Specs.like(keyword, "a", "b"), Specs.gte(...), Specs.lte(...))` — null 인 조건은 무시된다.
  날짜 범위 `from`/`to`(LocalDate) 를 TIMESTAMP 컬럼에 적용할 때는 `gte(attr, from.atStartOfDay())`, `lt(attr, to.plusDays(1).atStartOfDay())`.
- 요청 DTO: record + Bean Validation(`@NotNull`, `@NotBlank`, `@Size(max = DDL 길이)`, `@Positive`, `@PositiveOrZero`, `@Min/@Max`).
  컨트롤러에서 `@Valid @RequestBody`. 응답 DTO 는 `static Xxx of(Entity e)` 팩토리를 둔다. **엔티티를 그대로 응답하지 않는다.**
- 날짜/시각 JSON: `2026-11-26`, `14:30:00`, `2026-11-26T14:30:00` (ISO, 시간대 없음 = Asia/Seoul).
- 금액은 `BigDecimal`. 계산은 `setScale(2, RoundingMode.HALF_UP)`.

## 오류

| 상황 | 던질 것 | HTTP |
|---|---|---|
| 입력값 오류 | `IllegalArgumentException("한국어 메시지")` | 400 |
| 대상 없음 | `NotFoundException.of("라운드", id)` | 404 |
| 상태 충돌, 중복 | `ConflictException("한국어 메시지")` | 409 |
| 낙관적 락 충돌, DB 제약 위반 | (자동 처리) | 409 |

응답 본문: `{"timestamp", "status", "message"}`. 메시지는 화면에 그대로 보여줄 수 있는 한국어 문장.

## 공통 도구

- 현재 시각: `Clock` 주입 → `LocalDateTime.now(clock)`, `LocalDate.now(clock)`. `LocalDateTime.now()` 직접 호출 금지.
- 현재 직원: `CurrentUser.idOrNull()` → `created_by`, `assigned_by`, `received_by`, `actor_admin_user_id` 등에 저장.
- 감사 로그: `AuditLogger.log(action, "테이블명", id, Map.of(...))` — 삭제, 상태 변경(취소·마감·승인·환불 등) 같은 주요 처리에서 호출.
- 업무 번호: `DocumentNo.of("RD", date, id)` → `RD-20261126-000123`. NOT NULL 번호 컬럼은 `DocumentNo.temporary()` 로 저장 → `saveAndFlush` → `of(...)` 로 교체.
- 파일 첨부: 업로드는 `file` 모듈(`/api/files`)이 맡는다. 다른 모듈은 `common_file_id` 만 저장하고, 존재·상태 확인은 `RefChecker(Ref.COMMON_FILE)` 또는 `golf.common_file.upload_status = 'ACTIVE'` SQL 로 한다.
- 문자열 입력 정리: 공백만 있는 선택 값은 null 로(`blankToNull`), 필수 값은 `trim()`.

## 동시성

- 수량·잔액을 바꾸는 행(재고, 렌탈 재고, 티타임 예약 등)은 `@Lock(PESSIMISTIC_WRITE)` 조회(`findByIdForUpdate`) 후 변경하거나 `@Version` 을 쓴다.
- "동시에 하나만" 규칙은 DDL 의 부분 유니크 인덱스가 최종 방어선이다(위반 시 409). 서비스에서도 먼저 검사해 친절한 메시지를 준다.

## 권한

`security/ErpModule` — URL 접두어별 모듈. GET 은 `{MODULE}_READ` 또는 `_WRITE`, 나머지는 `{MODULE}_WRITE`. `admin_user.role = ADMIN` 은 전부 허용.
권한 코드는 기동 시 `golf.permission` 에 자동 등록되고, 역할(`/api/roles`)에 묶어 직원에게 부여한다.
