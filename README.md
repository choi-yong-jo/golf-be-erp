# GOLF ERP Backend

Vue.js frontend(`golf-fe-erp`)와 연동하는 골프장 관리자(ERP) REST API.
고객용 예약 앱(`golf-reservation-api`)과 같은 PostgreSQL `golf` 스키마를 함께 쓴다.

## 기술 스택
- Java 17, Spring Boot 3.5.x
- Spring Web, Spring Data JPA(단건 조회·저장), MyBatis XML Mapper(목록·집계)
- PostgreSQL (`golf` 스키마, `src/main/resources/db/schema.sql`)

## 시작하기
1. PostgreSQL에 `golf` DB를 만들고 스키마를 적용합니다. (dev 프로필은 기동 시 자동 실행)
   ```bash
   psql -1 -d golf -f src/main/resources/db/schema.sql
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
| 예약 현황(티타임) | `GET /api/tee-times?courseId=&from=&to=&status=`, `GET /api/tee-times/stream?courseId=` (SSE), `POST /api/tee-times`, `POST /api/tee-times/generate`, `GET/PATCH/DELETE /api/tee-times/{id}` |
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

### Redis (예약 앱 연동 · 실시간 알림)
Redis 는 부가 기능이다. **Redis 가 없거나 죽어도 ERP 기동과 모든 업무는 그대로 동작**한다.
접속: `REDIS_HOST`(기본 localhost), `REDIS_PORT`(기본 6380), `REDIS_PASSWORD`. 끄려면 `REDIS_ENABLED=false`.

| 기능 | 언제 | Redis 작업 |
|---|---|---|
| 예약 앱 티타임 캐시 무효화 | 티타임 생성/일괄 생성/수정/삭제, 예약 생성/취소 | `{prefix}tee-times:{날짜}:all`, `{prefix}tee-times:{날짜}:{코스ID}` 삭제 |
| 예약 앱 회원 상태 즉시 반영 | 회원 상태 변경 | `{prefix}auth:member-status:{회원ID}` 삭제. 정지/탈퇴면 `{prefix}auth:revoked-before:{회원ID}` 기록(예약 앱 즉시 로그아웃) |
| 예약 현황 실시간 알림 | 위 변경 + 예약 확정/결제/완료/노쇼 | `golf:tee-time-events` 채널에 발행 → 구독 → `GET /api/tee-times/stream`(SSE)으로 화면에 전달 |

- `{prefix}` 는 예약 앱의 `app.redis.key-prefix`(기본 `golf-api:`)와 같아야 한다 → `RESERVATION_APP_REDIS_KEY_PREFIX`.
- 예약 앱이 **같은 Redis** 를 써야 1·2번이 효과가 있다. (예약 앱 컨테이너: `REDIS_HOST=host.docker.internal`, `REDIS_PORT=6380`)
- 장애 대비
  - Redis 작업은 커밋 후 별도 스레드에서 실행하고, 실패하면 로그만 남긴다. 예약 앱 캐시는 30초 뒤 저절로 만료된다.
  - 연결이 끊긴 동안의 명령은 쌓지 않고 바로 실패시킨다(timeout 1초).
  - 기동 시 Redis 가 없어도 앱은 뜨고, 30초마다 채널 구독을 다시 시도한다.
  - 발행에 실패하거나 구독이 끊긴 동안에는 알림을 이 서버의 화면에 직접 전달한다.
  - `/actuator/health` 는 Redis 상태와 무관하다.

#### 실시간 알림 형식 (`golf:tee-time-events`, SSE 이벤트 이름 `tee-time`)
```json
{"source":"ERP","type":"RESERVATION_CREATED","golfCourseId":1,"playDate":"2026-11-26",
 "teeTimeId":1,"reservationId":1,"status":"PENDING","occurredAt":"2026-11-25T07:42:17Z"}
```
- `type`: `TEE_TIME_CREATED`, `TEE_TIME_UPDATED`, `TEE_TIME_DELETED`, `RESERVATION_CREATED`, `RESERVATION_UPDATED`, `RESERVATION_CANCELLED`
- 화면은 보고 있는 코스·날짜에 해당하면 `GET /api/tee-times` 를 다시 조회한다. `stream?courseId=1` 처럼 코스를 정하면 그 코스 이벤트만 받는다.
- 예약 앱이 고객 예약/취소 때 같은 형식(`"source":"APP"`)으로 이 채널에 발행하면 ERP 화면에도 바로 보인다. **예약 앱은 아직 발행하지 않는다.**

### 정산
- 해당 일자 `completed_at` 기준 결제 합계(이후 환불 건 포함) - `refunded_at` 기준 환불 합계.
- 지난 날짜만 마감(`CLOSED`)할 수 있고, 마감된 날짜는 해제 전까지 재계산하지 않는다.

## 참고
- 예전 schema.sql 이 만들던 호환 뷰(`golf.members`, `golf.reservations` 등)는 새 `db/schema.sql` 에서 빠졌다. 코드는 뷰를 쓰지 않으며, 이미 만들어진 DB 에는 뷰가 남아 있을 수 있다(스크립트가 지우지는 않음).
- 관리자 계정(`admin_user`)·인증/인가는 아직 없다. 감사 로그(`audit_log`)는 조회만 하며 ERP 변경은 `reservation_history` 에 남긴다.
- CORS는 개발용 `http://localhost:5173` 만 허용한다.
