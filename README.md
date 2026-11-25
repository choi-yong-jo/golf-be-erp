# GOLF ERP Backend

Vue.js frontend(`golf-fe-erp`)와 연동하는 골프장 관리자(ERP) REST API.
고객용 예약 앱(`golf-reservation-api`)과 같은 PostgreSQL `golf` 스키마를 함께 쓴다.

## 기술 스택
- Java 17, Spring Boot 3.5.x
- Spring Web, Spring Data JPA(단건 조회·저장), MyBatis XML Mapper(목록·집계)
- PostgreSQL (`golf` 스키마, `src/main/resources/schema.sql`)

## 시작하기
1. PostgreSQL에 `golf` DB를 만들고 스키마를 적용합니다. (dev 프로필은 기동 시 자동 실행)
   ```bash
   psql -1 -d golf -f src/main/resources/schema.sql
   ```
2. 접속 정보를 환경변수로 지정합니다. (기본값: `jdbc:postgresql://localhost:5432/golf`, `postgres`)
   ```bash
   export DB_URL='jdbc:postgresql://localhost:5432/golf'
   export DB_USERNAME='postgres'
   export DB_PASSWORD='실제비밀번호'
   ```
3. `mvn spring-boot:run` (기본 프로필 `local`, `-Dspring-boot.run.profiles=dev` 로 dev)

| 프로필 | DB | schema.sql 실행 |
|---|---|---|
| local | 로컬 PostgreSQL | 안 함 (미리 적용) |
| dev | `DB_URL` | 기동할 때마다 (IF NOT EXISTS 라 반복 실행 안전) |

`ddl-auto: validate` 이므로 엔티티와 테이블이 다르면 기동이 실패합니다.

## API
모든 목록 API는 `{items, page, size, total}` 형식, 오류는 `{timestamp, status, message}` 형식입니다.
날짜는 `YYYY-MM-DD`, 시각은 `HH:mm[:ss]`, 일시는 `YYYY-MM-DDTHH:mm:ss` (Asia/Seoul).

| 메뉴 | API |
|---|---|
| 대시보드 | `GET /api/dashboard/summary`, `GET /api/dashboard/recent-reservations?limit=5` |
| 회원 | `GET /api/members?keyword=&status=&page=&size=`, `GET/PUT /api/members/{id}`, `POST /api/members`, `PATCH /api/members/{id}/status` |
| 예약 제한 | `GET/POST /api/members/{id}/restrictions`, `POST /api/members/{id}/restrictions/{rid}/release` |
| 코스 | `GET/POST /api/courses`, `GET/PUT /api/courses/{id}`, `PATCH /api/courses/{id}/status` |
| 휴장 | `GET/POST /api/courses/{id}/closures`, `DELETE /api/courses/{id}/closures/{closureId}` (해제) |
| 요금 | `GET/POST /api/fee-policies`, `GET/PUT/DELETE /api/fee-policies/{id}` (DELETE=비활성), `GET /api/fee-policies/quote?courseId=&date=&time=` |
| 예약 현황(티타임) | `GET /api/tee-times?courseId=&from=&to=&status=`, `POST /api/tee-times`, `POST /api/tee-times/generate`, `GET/PATCH/DELETE /api/tee-times/{id}` |
| 예약 | `GET /api/reservations?keyword=&status=&courseId=&memberId=&from=&to=`, `POST /api/reservations`, `GET /api/reservations/{id}` |
| 예약 처리 | `POST /api/reservations/{id}/confirm`, `/payments`, `/cancel`, `/complete`, `/no-show`, `PATCH /api/reservations/{id}/memo` |
| 결제 내역 | `GET /api/payments?status=&from=&to=` |
| 정산 | `GET /api/settlements?from=&to=`, `GET /api/settlements/{date}`, `POST /api/settlements/{date}/calculate`, `/close`, `/reopen` |
| 감사 로그 | `GET /api/audit-logs?entityType=&entityId=` (읽기 전용) |

### 상태 값
- 회원: `ACTIVE`, `SUSPENDED`, `WITHDRAWN` (삭제 대신 상태 변경)
- 코스: `OPEN`, `CLOSED` + `active`
- 티타임: `OPEN`, `BOOKED`(예약으로만 변경), `BLOCKED`, `CLOSED`
- 예약: `PENDING`, `PENDING_PAYMENT`, `PAYING`, `CONFIRMED`, `REFUND_PENDING`, `CANCELLED`, `COMPLETED`, `NO_SHOW`, `FAILED`
- 결제: `READY`, `PENDING`, `SUCCEEDED`, `PAID`, `FAILED`, `CANCELLED`, `REFUNDED`

## 업무 규칙

### 예약 (ERP 현장/전화 예약)
```
생성(PENDING) ─ 확정 ──────────────→ CONFIRMED ─ 완료 → COMPLETED
     │        └ 현장 결제(PAID) ──→      │        └ 노쇼 → NO_SHOW
     └──────────── 취소 ←───────────────┘  (ERP 결제는 REFUNDED 로 환불 처리)
```
- 생성 조건: 회원 `ACTIVE`, 진행 중인 예약 제한 없음, 티타임 `OPEN`·미래·정원 이내, 코스 운영 중, 휴장 아님.
- 동시 예약: 회원·티타임 행을 `SELECT ... FOR UPDATE` 로 잠그고, DB 부분 유니크 인덱스(`ux_reservation_active_tee_time`)가 최종적으로 막는다.
- 금액: 1인 그린피는 `tee_time.green_fee`, 없으면 요금 정책. `total = (그린피 - 1인 할인) x 인원`.
- 예약자는 1번 플레이어로 자동 등록되고, 동반자는 2번부터. 모든 변경은 `reservation_history` 에 남는다.
- 완료/노쇼는 플레이 시작 시각 이후에만 가능.

### 예약 앱과의 호환
- ERP 예약은 `PENDING` 으로 시작한다. (`PENDING_PAYMENT` 는 예약 앱이 10분 뒤 자동 취소)
- 예약번호는 `ERP-R{id}` (예약 앱은 `GOLF-R{id}`).
- 예약 시 티타임 `BOOKED`, 취소 시 `OPEN`. `version` 은 양쪽 낙관적 락으로 공유한다.
- ERP 현장 결제는 `pg_provider=ERP`, `PAID`. 환불은 같은 행을 `REFUNDED` 로 바꾼다(별도 `REFUND` 행을 만들지 않음).
- 예약 앱이 결제/환불 중인 예약(`PAYING`, `REFUND_PENDING`)과 온라인 결제(PG) 건의 취소는 ERP에서 막는다 → 예약 앱에서 처리.
- 예약 앱은 Redis에 티타임 목록을 30초 캐시하므로 ERP 변경이 고객 화면에 최대 30초 늦게 보일 수 있다.

### 요금 정책
- 1인 기준 `base_fee` / `discount_amount`. 코스 지정이 없으면 전체 코스 공통.
- 여러 정책이 맞으면: 특정 코스 > 공통, 요일 지정 > 미지정, 평일/주말 지정 > 미지정, 시간대 지정 > 미지정, 최근 시작일 > 최근 등록 순.
- `HOLIDAY` 는 공휴일 달력이 없어 자동 적용되지 않는다.
- 티타임 생성 시 그린피를 생략하면 정책의 `기본 요금 - 할인` 으로 채운다. 정책도 없으면 비워 두며, 이 티타임은 예약 앱에 노출되지 않는다.

### 정산
- 해당 일자 `completed_at` 기준 결제 합계(이후 환불 건 포함) - `refunded_at` 기준 환불 합계.
- 지난 날짜만 마감(`CLOSED`)할 수 있고, 마감된 날짜는 해제 전까지 재계산하지 않는다.

## 참고
- `schema.sql` 2부의 호환 뷰(`members`, `reservations` 등)는 이제 코드에서 쓰지 않는다. 다른 시스템이 쓰지 않으면 지워도 된다.
- 관리자 계정(`admin_user`)·인증/인가는 아직 없다. 감사 로그(`audit_log`)는 조회만 하며 ERP 변경은 `reservation_history` 에 남긴다.
- CORS는 개발용 `http://localhost:5173` 만 허용한다.
