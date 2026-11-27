# GOLF ERP Backend

골프장 관리자(ERP) REST API. Vue.js 프론트엔드(`golf-fe-erp`)와 연동한다.
고객용 예약 앱(`golf-reservation-api`)과 같은 PostgreSQL `golf` 스키마를 함께 쓴다.

스키마: [`src/main/resources/db/golf-erp-db.sql`](src/main/resources/db/golf-erp-db.sql) (테이블 71개)
API 목록: [docs/API-ENDPOINTS.md](docs/API-ENDPOINTS.md) (341개) · 상세 스키마는 Swagger UI
개발 규칙: [docs/CONVENTIONS.md](docs/CONVENTIONS.md)

## 기술 스택
- Java 17, Spring Boot 3.5.5
- Spring Web, Spring Data JPA (+ JdbcTemplate: 모듈 간 조회·집계)
- Spring Security + OAuth2 Resource Server (JWT HS256)
- PostgreSQL, springdoc-openapi

## 시작하기

1. PostgreSQL 에 `golf` DB 를 만든다. dev 프로필은 기동할 때마다 스키마 스크립트를 실행한다(모두 `IF NOT EXISTS` 라 반복 실행해도 데이터 유지).
   다른 프로필은 미리 적용한다.
   ```bash
   psql -1 -h localhost -U postgres -d golf -f src/main/resources/db/golf-erp-db.sql
   ```
2. 실행
   ```bash
   mvn spring-boot:run                                   # dev 프로필(기본)
   mvn spring-boot:run -Dspring-boot.run.profiles=local  # 스키마 스크립트 실행 안 함
   ```
3. http://localhost:8081/swagger-ui.html 에서 `POST /api/auth/login` → 응답의 `accessToken` 을 Authorize 에 넣는다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/golf` / `postgres` / (dev 만 기본값) | DB 접속 |
| `JWT_SECRET` | dev·local 은 개발용 키, 그 외 **필수** | HS256 서명 키, 32바이트 이상 |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | `PT2H` / `P14D` | 토큰 유효시간 (ISO-8601) |
| `ERP_ADMIN_USERNAME` / `ERP_ADMIN_PASSWORD` | `admin` / dev 는 `admin1234!`, 그 외는 임의 생성 후 로그 출력 | `admin_user` 가 비어 있을 때만 만드는 초기 관리자 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | 쉼표로 여러 개 |
| `FILE_STORAGE_DIR` | `./uploads` | 업로드 파일 저장 디렉터리(파일당 최대 20MB) |

`ddl-auto: validate` 이므로 엔티티와 테이블이 어긋나면 기동이 실패한다.

## 인증

| API | 설명 |
|---|---|
| `POST /api/auth/login` `{username, password}` | `accessToken`(2시간), `refreshToken`(14일), 내 정보·권한 |
| `POST /api/auth/refresh` `{refreshToken}` | 새 토큰 쌍 |
| `GET /api/auth/me` | 내 정보와 권한 코드 목록 (화면 메뉴·버튼 제어용) |
| `POST /api/auth/password` | 내 비밀번호 변경 |

- 이후 모든 요청: `Authorization: Bearer {accessToken}`. 세션·쿠키 없음.
- 토큰에는 계정 id 만 들어 있고, 계정 상태와 권한은 **요청마다 DB 에서 다시 읽는다**. 그래서 계정을 비활성화하거나 역할을 바꾸면 바로 반영된다.
- 로그아웃은 클라이언트가 토큰을 버리는 방식이다(서버 측 토큰 폐기 목록 없음).

## 권한

URL 접두어마다 모듈이 정해져 있고, 모듈마다 `{MODULE}_READ` / `{MODULE}_WRITE` 권한이 있다([`ErpModule`](src/main/java/com/golf/erp/security/ErpModule.java)).
- `GET` 요청: `_READ` 또는 `_WRITE` 권한
- 그 밖의 요청: `_WRITE` 권한
- `admin_user.role = ADMIN`: 모든 API
- 권한 코드는 기동 시 `golf.permission` 에 자동 등록된다. 역할(`/api/roles`)에 권한을 묶고, 직원 계정(`/api/admin-users/{id}/roles`)에 역할을 지정한다.

| 모듈 | 경로 | 주요 기능 |
|---|---|---|
| `MEMBER` | `/members` | 회원(오프라인 회원 등록), 상태 변경, 예약 제한, 회원별 예약 |
| `COURSE` | `/golf-courses` `/courses` `/holes` | 골프장, 휴장, 9홀 코스, 홀(스코어카드) |
| `FEE` | `/fee-policies` `/service-fees` | 그린피 정책·요금 조회(quote), 캐디피·카트비 등 부대 요금표 |
| `TEE_TIME` | `/tee-times` | 예약 현황판, 일괄 생성(휴장 제외), 상태 변경 |
| `RESERVATION` | `/reservations` | 예약 생성·확정·현장 결제·취소(환불)·완료·노쇼 |
| `PAYMENT` | `/payments` | 예약 결제 내역 |
| `SETTLEMENT` | `/settlements` `/daily-settlements` | 예약 결제 일별 정산 / 골프장별 일 마감(라운드 요금·수납) |
| `ROUND` | `/rounds` | 체크인(예약·워크인), 출발·코스 이동·종료, 캐디·카트 배정, 스코어, 요금·수납, 이용 내역 |
| `RESOURCE` | `/caddies` `/carts` | 캐디·카트, 배정 가능 목록 |
| `STAFF` | `/staff` `/staff-shifts` | 직원, 근무 일정·출퇴근 |
| `PAYROLL` | `/payrolls` | 급여(작성→승인→지급), 월 일괄 생성 |
| `EQUIPMENT` | `/equipment` | 장비, 정비 이력 |
| `FACILITY` | `/facilities` `/facility-room-types` `/facility-units` `/facility-bookings` | 회의실·연회장, 공간 종류(시간당 요금), 공간, 시간 단위 예약(15분 단위·중복 방지), 예약 가능 조회, 캘린더 |
| `LODGING` | `/lodging-room-types` `/lodging-rooms` `/lodging-bookings` | 객실 타입(대실·1박 요금), 객실·청소 상태, 대실(기본 3시간)·1박(16:00~익일 11:00) 예약, 연박 일괄 예약, 캘린더 |
| `TENANT` | `/store-brands` `/tenant-stores` | 입점 브랜드, 외부 운영 식당·카페(임대 기간·월 임대료·만료 예정), 매장 메뉴 분류·메뉴 |
| `STORE_ORDER` | `/store-orders` | 매장 주문(접수→조리→완료, 결제·환불), 매장별 매출 요약 |
| `INVENTORY` | `/inventory-items` `/inventory-transactions` `/purchase-orders` | 재고 품목·입출고, 재주문 필요 품목, 발주·입고 |
| `CRM` | `/notifications` `/promotions` | 알림 발송 대기열, 프로모션·사용 |
| `BOARD` | `/boards` | 공지·이벤트·FAQ·고객 문의 통합 게시판, 문의 답변, 첨부 파일 |
| (로그인만) | `/files` | 파일 업로드·다운로드(로컬 스토리지 `FILE_STORAGE_DIR`) |
| `ACCOUNTING` | `/accounts` `/journal-entries` `/operating-expenses` | 계정과목, 전표(차대 균형 검사·전기·무효), 시산표, 운영비 결재 |
| `RENTAL` | `/rental-equipment` `/rentals` | 렌탈 품목·골프장별 재고, 대여(예약→출고→반납·파손·분실), 보증금·요금 |
| `SYSTEM` | `/admin-users` `/roles` `/permissions` | 직원 계정, 역할, 권한 |
| `AUDIT` | `/audit-logs` | 감사 로그(삭제·상태 변경·승인·마감 등) |
| (로그인만) | `/dashboard` | 일일 요약(예약·티타임 점유율·라운드·매출) |

## 응답 형식
- 목록: `{items, page, size, total}` — `page` 0부터, `size` 기본 20·최대 100. 건수가 적은 마스터(골프장, 코스, 역할 등)는 배열.
- 오류: `{timestamp, status, message}` — `message` 는 화면에 그대로 보여줄 수 있는 한국어 문장.

  | status | 의미 |
  |---|---|
  | 400 | 입력값 오류 |
  | 401 | 토큰 없음·만료·비활성 계정, 로그인 실패 |
  | 403 | 권한 없음 |
  | 404 | 대상 없음 |
  | 409 | 상태 충돌(허용되지 않는 상태 변경, 중복, 마감된 날짜, 재고 부족, 동시 수정) |
- 날짜 `2026-11-26`, 시각 `14:30:00`, 일시 `2026-11-26T14:30:00` (시간대 없음 = Asia/Seoul). 금액은 숫자(원).

## 주요 업무 흐름

**예약 → 라운드 → 마감**
1. 티타임 일괄 생성(`POST /tee-times/generate`) — 휴장 시간대는 건너뛰고, 그린피는 정책에서 채운다.
2. 예약(`POST /reservations`) — 티타임 행 잠금, 회원 상태·예약 제한·휴장 검사, 티타임 `BOOKED`.
3. 현장 결제(`POST /reservations/{id}/payments`) → `CONFIRMED`.
4. 체크인(`POST /rounds/check-in {reservationId}`) — 동반자를 참가자로 옮기고, 1인 그린피 청구, 예약 결제액은 `PREPAID` 수납으로 옮긴다.
5. 캐디·카트 배정 → 출발(`/start`) → 코스 이동(`/change-course`) → 스코어(`PUT /scores`) → 부대 요금(`/charges`) → 수납(`/payments`).
6. 종료(`/finish`) — 배정 해제, 참가자 체크아웃, 예약 `COMPLETED`.
7. 일 마감(`POST /daily-settlements/calculate` → `/{id}/close`) — 마감된 날의 요금·수납 변경은 409, `reopen` 으로 다시 연다.

**파일 첨부**: `POST /api/files`(multipart `files`)로 먼저 올리고, 받은 id 를 게시글 등록·첨부(`PUT /api/boards/{id}/files`)에 넣는다.
다운로드(`GET /api/files/{id}/download`)도 Bearer 토큰이 필요하므로 화면에서는 `<img src>` 대신 fetch → blob URL 로 표시한다.

**동시성**: 티타임·예약·라운드·재고·렌탈 재고처럼 동시에 바뀌면 안 되는 행은 행 잠금(`SELECT … FOR UPDATE`)과 `@Version` 으로 보호하고, DDL 의 부분 유니크 인덱스(티타임당 활성 예약 1건, 캐디·카트 동시 배정 1건 등)가 최종 방어선이다.

## 구조

```
com.golf.erp
├─ common     예외, 목록·검색 헬퍼, 업무 번호, 참조 검증(RefChecker), 감사 로그
│  └─ port    모듈 간 인터페이스: GreenFeeQuoter(fee 구현), ReservationLifecycle(reservation 구현)
├─ config     설정값, Clock(Asia/Seoul), OpenAPI
├─ security   JWT, URL→권한 매핑(ErpModule), CurrentUser
├─ system     인증·직원 계정·역할·권한, 초기 데이터
└─ member, course, fee, teetime, reservation, payment, settlement, round, billing, resource,
   hr, equipment, facility, store, inventory, crm, board, file, accounting, rental, audit, dashboard
```

모듈끼리는 엔티티를 직접 참조하지 않고 id 로만 연결한다. 다른 모듈 테이블은 JdbcTemplate SQL 로 읽고, 상태 변경이 필요하면 `common.port` 인터페이스를 쓴다.

## DDL 관련 확인 필요 사항

코드는 아래처럼 처리했지만, DDL 이나 예약 앱과 맞춰 정리하는 것이 좋다.

| 항목 | 현재 처리 |
|---|---|
| `reservation.discount_amount` 의 의미: DDL 주석은 "그린피 × 인원 − 할인"(예약 전체 할인), 이전 ERP 코드는 1인 할인 | 1인 그린피를 `total_amount ÷ player_count` 로 계산. **예약 앱과 의미를 맞춰야 한다** |
| 같은 뜻의 중복 컬럼: `caddie.hired_on`/`hire_date`, `caddie_status`/`employment_status`, `cart.cart_no`/`cart_code` | 앞의 것을 쓰고 뒤의 것도 같은 값으로 저장 |
| `ALTER … ADD COLUMN IF NOT EXISTS` 가 이미 있는 컬럼을 다른 길이로 선언: `course.course_code`(20 vs 30), `admin_user.department`(50 vs 100) | 실제 길이(20, 50) 기준 |
| CHECK 없는 상태 컬럼: `golf_course.course_status`, `settlement.settlement_status` | enum(OPEN/CLOSED)으로 매핑. 다른 앱이 다른 값을 쓰면 조회 시 오류 |
| `inventory_transaction` 에 수량 부호가 없음(quantity > 0) | 재고 조정 증가 = `ADJUSTMENT`, 감소 = `ISSUE` + 메모 `[조정]` |
| `equipment_rental_item` 에 파손·분실 수량 컬럼이 없음 | `returned_quantity` = 정상+파손+분실 처리 수량, 파손·분실 수량은 이력에서 합산 |
| `equipment_rental_history.action_type`, `round_event.event_type` 에 연체·결제·참가자 체크아웃 등 값이 없음 | 해당 처리는 감사 로그에만 남김 |
| `round_player` 에 제외 표시가 없음 | 참가자 제외 = 행 삭제(스코어·결제가 있으면 거부, 요금은 무효 처리 후 팀 공통으로 이동) |
| `reservation_history` 에 직원 행위자 컬럼이 없음(`actor_member_id` 는 회원 FK) | `reason` 앞에 `[직원아이디]` 를 붙임 |
| `promotion_redemption` 에 (promotion_id, reservation_id) 유니크 없음 | 서비스에서 행 잠금 후 검사. 부분 유니크 인덱스 추가 권장 |
| 인덱스 없음: `payment(completed_at)`, `payment(refunded_at)`, `audit_log(created_at)` | 정산·대시보드·감사 로그 조회용으로 추가 권장 |
| 생성·수정 시각이 없는 테이블: `equipment`, `account`, `facility`, `product`, `customer_notification`, `journal_entry`(무효 시각·사유), `operating_expense`(반려 사유·지급 시각) | 감사 로그로 보완 |
| `facility_booking`, `lodging_booking` 에 같은 공간·객실의 기간 겹침을 막는 DB 제약이 없음 | 공간·객실 행 잠금 후 서비스에서 검사. `btree_gist` + `EXCLUDE USING gist (facility_unit_id WITH =, tsrange(booking_start_at, booking_end_at) WITH &&) WHERE (booking_status IN ('PENDING','CONFIRMED','CHECKED_IN'))` (lodging 도 같은 방식) 추가 권장 |
| `facility_booking` CHECK: `booking_hours = 실제 시간`, `base_amount = hourly_rate × booking_hours` 가 소수점까지 정확히 같아야 함 | 예약은 15분 단위, 시간당 요금은 원 단위 정수만 허용(그래야 NUMERIC(6,2)·(14,2) 로 정확히 맞음) |
| `lodging_booking` CHECK: OVERNIGHT 는 한 행에 정확히 1박(16:00 → 익일 11:00) | 연박은 1박씩 여러 행으로 저장(예약 요청 한 번에 연속 생성) |
| `store_order_item` CHECK: `line_amount = quantity × unit_price − discount` 가 정확히 같아야 함 | 수량은 정수, 메뉴 가격은 원 단위 정수만 허용 |
| `inventory_transaction.facility_id` 가 여전히 `golf.facility`(이제 회의실·연회장)를 참조 | 그대로 둠(선택 값). 재고 위치 개념이 필요하면 컬럼 정리 필요 |
| `board` 문의(INQUIRY)의 작성자(회원 또는 비회원 정보) 필수 여부가 DB 에 없음, `answered_by` 는 답변 CHECK 에 포함되지 않음 | 서비스에서 검사 |
| 인덱스 없음: `common_file(uploaded_by_admin_user_id)`, `common_file(upload_status)`, `tenant_store(lease_end_date)` | 목록·임대 만료 조회용으로 추가 권장 |
| 스크립트 머리 주석은 "BEGIN/COMMIT 을 쓰지 않는다"인데 실제로는 있음 | Spring `sql.init` 과 psql 모두에서 정상 실행 확인 |

## 아직 없는 것
- 자동 테스트(`src/test`) — 업무 흐름 스모크 테스트(170건)는 개발 중 수동으로 실행했다.
- 로그인 시도 횟수 제한, refresh 토큰 폐기(로그아웃 서버 처리)
- 모듈 간 자동 연동: 급여·운영비 지급 → 회계 전표, 고객 알림 실제 발송기(대기열 API만 있음)
- 파일 스토리지는 로컬 디스크(`LOCAL`)만 지원. 삭제(`DELETED`)된 파일의 실제 바이너리 정리 작업 없음
