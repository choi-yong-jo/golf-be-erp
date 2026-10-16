# GOLF ERP Backend

Vue.js frontend (`golf-fe-erp`)와 연동하는 Spring Boot REST API starter.

## 기술 스택
- Java 17, Spring Boot 3.5.x
- Spring Web, Spring Data JPA, MyBatis XML Mapper
- MySQL 8

## 시작하기
1. MySQL 8을 설치하고 `src/main/resources/schema.sql`을 실행합니다.
2. DB 계정을 환경변수로 지정합니다.
   ```bash
   export DB_URL='jdbc:mysql://localhost:3306/golf_erp?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul'
   export DB_USERNAME='golf_user'
   export DB_PASSWORD='실제비밀번호'
   ```
3. `mvn spring-boot:run`

`ddl-auto: validate`이므로 테이블을 자동 생성하지 않습니다. `schema.sql`은 개발 초기 스키마 예시이며 운영 반영 전 Flyway/Liquibase 마이그레이션으로 관리하세요.

## API
- `GET /api/dashboard/summary`
- `GET /api/dashboard/recent-reservations?limit=5`
- `GET /api/reservations?keyword=&status=&page=0&size=20`
- `GET /api/members`, `GET /api/members/{id}`, `POST /api/members`, `PUT /api/members/{id}`, `DELETE /api/members/{id}`
- `GET /api/courses`, `GET /api/courses/{id}`

상태 값 예시: 예약 `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`; 회원 `ACTIVE`, `INACTIVE`; 코스 `OPEN`, `CLOSED`.

## 참고
- Dashboard/예약 검색은 MyBatis, 회원/코스 조회와 회원 CRUD는 JPA로 구성했습니다.
- 예약 생성/변경, 결제/환불, 요금/정산은 실제 정책과 중복 예약 방지 규칙을 확정한 뒤 추가해야 합니다.
- 현재 CORS는 개발용 `http://localhost:5173`만 허용합니다.
- 인증/인가(JWT 또는 세션), 관리자 계정 초기화, 비밀정보 관리, 통합 테스트는 배포 전 필수 보완 항목입니다.
