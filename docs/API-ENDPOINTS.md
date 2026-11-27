# API 엔드포인트 목록

전체 341개. 요청/응답 스키마는 서버 실행 후 `/swagger-ui.html` 에서 확인한다.
경로의 `/api` 접두어는 생략했다. 권한은 [README](../README.md#권한) 참고.

## 감사 로그 (2)

```
GET /audit-logs
GET /audit-logs/{id}
```

## 게시판 (11)

```
GET /boards
POST /boards
GET /boards/{id}
PUT /boards/{id}
DELETE /boards/{id}
POST /boards/{id}/answer
DELETE /boards/{id}/answer
PUT /boards/{id}/files
PATCH /boards/{id}/inquiry-status
PATCH /boards/{id}/status
GET /boards/inquiries/summary
```

## 고객 알림 (8)

```
GET /notifications
POST /notifications
GET /notifications/{id}
POST /notifications/{id}/cancel
POST /notifications/{id}/mark-failed
POST /notifications/{id}/mark-sent
POST /notifications/{id}/retry
GET /notifications/queue
```

## 골프장·휴장 (8)

```
GET /golf-courses
POST /golf-courses
GET /golf-courses/{id}
PUT /golf-courses/{id}
GET /golf-courses/{id}/closures
POST /golf-courses/{id}/closures
DELETE /golf-courses/{id}/closures/{closureId}
PATCH /golf-courses/{id}/status
```

## 공통 파일 (5)

```
GET /files
POST /files
GET /files/{id}
DELETE /files/{id}
GET /files/{id}/download
```

## 그린피 정책 (6)

```
GET /fee-policies
POST /fee-policies
GET /fee-policies/{id}
PUT /fee-policies/{id}
DELETE /fee-policies/{id}
GET /fee-policies/quote
```

## 대시보드 (2)

```
GET /dashboard/recent-reservations
GET /dashboard/summary
```

## 라운드 요금·수납 (7)

```
GET /rounds/{roundId}/bill
GET /rounds/{roundId}/charges
POST /rounds/{roundId}/charges
POST /rounds/{roundId}/charges/{chargeId}/void
GET /rounds/{roundId}/payments
POST /rounds/{roundId}/payments
POST /rounds/{roundId}/payments/{paymentId}/cancel
```

## 라운드 (18)

```
GET /rounds
GET /rounds/{id}
POST /rounds/{id}/assignments
POST /rounds/{id}/assignments/{assignmentId}/release
POST /rounds/{id}/assignments/{assignmentId}/swap
POST /rounds/{id}/cancel
POST /rounds/{id}/change-course
GET /rounds/{id}/events
POST /rounds/{id}/finish
POST /rounds/{id}/players
PUT /rounds/{id}/players/{playerId}
DELETE /rounds/{id}/players/{playerId}
POST /rounds/{id}/players/{playerId}/check-out
GET /rounds/{id}/scorecard
PUT /rounds/{id}/scores
POST /rounds/{id}/start
GET /rounds/board
POST /rounds/check-in
```

## 매장 메뉴 (10)

```
GET /tenant-stores/{storeId}/menu
GET /tenant-stores/{storeId}/menu-categories
POST /tenant-stores/{storeId}/menu-categories
PUT /tenant-stores/{storeId}/menu-categories/{id}
PATCH /tenant-stores/{storeId}/menu-categories/{id}/active
GET /tenant-stores/{storeId}/menu-items
POST /tenant-stores/{storeId}/menu-items
GET /tenant-stores/{storeId}/menu-items/{id}
PUT /tenant-stores/{storeId}/menu-items/{id}
PATCH /tenant-stores/{storeId}/menu-items/{id}/availability
```

## 매장 주문 (11)

```
GET /store-orders
POST /store-orders
GET /store-orders/{id}
POST /store-orders/{id}/cancel
POST /store-orders/{id}/items
POST /store-orders/{id}/items/{itemId}/cancel
PATCH /store-orders/{id}/items/{itemId}/status
POST /store-orders/{id}/pay
POST /store-orders/{id}/refund
PATCH /store-orders/{id}/status
GET /store-orders/summary
```

## 발주 (7)

```
GET /purchase-orders
POST /purchase-orders
GET /purchase-orders/{id}
PUT /purchase-orders/{id}
POST /purchase-orders/{id}/cancel
POST /purchase-orders/{id}/order
POST /purchase-orders/{id}/receive
```

## 부대 요금표 (5)

```
GET /service-fees
POST /service-fees
GET /service-fees/{id}
PUT /service-fees/{id}
DELETE /service-fees/{id}
```

## 부대시설 공간 종류 (6)

```
GET /facilities/{id}/room-types
GET /facility-room-types
POST /facility-room-types
GET /facility-room-types/{id}
PUT /facility-room-types/{id}
PATCH /facility-room-types/{id}/active
```

## 부대시설 예약 (11)

```
GET /facility-bookings
POST /facility-bookings
GET /facility-bookings/{id}
PUT /facility-bookings/{id}
POST /facility-bookings/{id}/cancel
POST /facility-bookings/{id}/check-in
POST /facility-bookings/{id}/check-out
POST /facility-bookings/{id}/confirm
POST /facility-bookings/{id}/no-show
PATCH /facility-bookings/{id}/payment-status
GET /facility-bookings/calendar
```

## 부대시설·공간 (13)

```
GET /facilities
POST /facilities
GET /facilities/{id}
PUT /facilities/{id}
POST /facilities/{id}/activate
POST /facilities/{id}/deactivate
GET /facilities/{id}/units
GET /facility-units
POST /facility-units
GET /facility-units/{id}
PUT /facility-units/{id}
PATCH /facility-units/{id}/status
GET /facility-units/availability
```

## 숙박 객실 타입 (6)

```
GET /lodging-room-types
POST /lodging-room-types
GET /lodging-room-types/{id}
PUT /lodging-room-types/{id}
POST /lodging-room-types/{id}/activate
POST /lodging-room-types/{id}/deactivate
```

## 숙박 객실 (7)

```
GET /lodging-rooms
POST /lodging-rooms
GET /lodging-rooms/{id}
PUT /lodging-rooms/{id}
POST /lodging-rooms/{id}/cleaned
PATCH /lodging-rooms/{id}/status
GET /lodging-rooms/availability
```

## 숙박 예약 (11)

```
GET /lodging-bookings
POST /lodging-bookings
GET /lodging-bookings/{id}
PUT /lodging-bookings/{id}
POST /lodging-bookings/{id}/cancel
POST /lodging-bookings/{id}/check-in
POST /lodging-bookings/{id}/check-out
POST /lodging-bookings/{id}/confirm
POST /lodging-bookings/{id}/no-show
PATCH /lodging-bookings/{id}/payment-status
GET /lodging-bookings/calendar
```

## 시스템 - 역할·권한 (7)

```
GET /permissions
GET /roles
POST /roles
GET /roles/{id}
PUT /roles/{id}
DELETE /roles/{id}
PUT /roles/{id}/permissions
```

## 시스템 - 직원 계정 (7)

```
GET /admin-users
POST /admin-users
GET /admin-users/{id}
PUT /admin-users/{id}
PATCH /admin-users/{id}/active
POST /admin-users/{id}/reset-password
PUT /admin-users/{id}/roles
```

## 예약 결제 (2)

```
GET /payments
GET /payments/{id}
```

## 예약 (9)

```
GET /reservations
POST /reservations
GET /reservations/{id}
POST /reservations/{id}/cancel
POST /reservations/{id}/complete
POST /reservations/{id}/confirm
PATCH /reservations/{id}/memo
POST /reservations/{id}/no-show
POST /reservations/{id}/payments
```

## 인사·급여 (22)

```
GET /payrolls
POST /payrolls
GET /payrolls/{id}
PUT /payrolls/{id}
DELETE /payrolls/{id}
POST /payrolls/{id}/approve
POST /payrolls/{id}/pay
POST /payrolls/generate
GET /staff
POST /staff
GET /staff-shifts
POST /staff-shifts
GET /staff-shifts/{id}
PUT /staff-shifts/{id}
POST /staff-shifts/{id}/absent
POST /staff-shifts/{id}/cancel
POST /staff-shifts/{id}/clock-in
POST /staff-shifts/{id}/clock-out
GET /staff/{id}
PUT /staff/{id}
DELETE /staff/{id}
PATCH /staff/{id}/status
```

## 인증 (4)

```
POST /auth/login
GET /auth/me
POST /auth/password
POST /auth/refresh
```

## 일 마감 정산 (6)

```
GET /daily-settlements
GET /daily-settlements/{id}
POST /daily-settlements/{id}/close
POST /daily-settlements/{id}/reopen
POST /daily-settlements/calculate
GET /daily-settlements/preview
```

## 입점 매장 (12)

```
GET /store-brands
POST /store-brands
GET /store-brands/{id}
PUT /store-brands/{id}
DELETE /store-brands/{id}
PATCH /store-brands/{id}/active
GET /tenant-stores
POST /tenant-stores
GET /tenant-stores/{id}
PUT /tenant-stores/{id}
PATCH /tenant-stores/{id}/status
GET /tenant-stores/lease-expiring
```

## 장비 렌탈 - 품목·재고 (10)

```
GET /rental-equipment
POST /rental-equipment
GET /rental-equipment/{id}
PUT /rental-equipment/{id}
DELETE /rental-equipment/{id}
PATCH /rental-equipment/{id}/status
GET /rental-equipment/{id}/stocks
POST /rental-equipment/{id}/stocks/{golfCourseId}/transactions
GET /rental-equipment/stock-transactions
GET /rental-equipment/stocks
```

## 장비 렌탈 (11)

```
GET /rentals
POST /rentals
GET /rentals/{id}
POST /rentals/{id}/cancel
POST /rentals/{id}/extend
POST /rentals/{id}/issue
PATCH /rentals/{id}/items/{itemId}/fees
POST /rentals/{id}/payments
POST /rentals/{id}/payments/{paymentId}/cancel
POST /rentals/{id}/return
POST /rentals/mark-overdue
```

## 장비 (14)

```
GET /equipment
POST /equipment
GET /equipment/{id}
PUT /equipment/{id}
DELETE /equipment/{id}
GET /equipment/{id}/maintenances
POST /equipment/{id}/maintenances
PATCH /equipment/{id}/status
GET /equipment/maintenances
GET /equipment/maintenances/{maintenanceId}
PUT /equipment/maintenances/{maintenanceId}
POST /equipment/maintenances/{maintenanceId}/cancel
POST /equipment/maintenances/{maintenanceId}/complete
POST /equipment/maintenances/{maintenanceId}/start
```

## 재고 (10)

```
GET /inventory-items
POST /inventory-items
GET /inventory-items/{id}
PUT /inventory-items/{id}
DELETE /inventory-items/{id}
PATCH /inventory-items/{id}/active
GET /inventory-items/low-stock
GET /inventory-transactions
POST /inventory-transactions
GET /inventory-transactions/{id}
```

## 정산 (5)

```
GET /settlements
GET /settlements/{date}
POST /settlements/{date}/calculate
POST /settlements/{date}/close
POST /settlements/{date}/reopen
```

## 캐디·카트 (10)

```
GET /caddies
POST /caddies
GET /caddies/{id}
PUT /caddies/{id}
PATCH /caddies/{id}/status
GET /carts
POST /carts
GET /carts/{id}
PUT /carts/{id}
PATCH /carts/{id}/status
```

## 코스·홀 (9)

```
GET /courses
POST /courses
GET /courses/{id}
PUT /courses/{id}
DELETE /courses/{id}
GET /courses/{id}/holes
PUT /courses/{id}/holes
PATCH /courses/{id}/status
PUT /holes/{id}
```

## 티타임 (7)

```
GET /tee-times
POST /tee-times
GET /tee-times/{id}
PUT /tee-times/{id}
DELETE /tee-times/{id}
PATCH /tee-times/{id}/status
POST /tee-times/generate
```

## 프로모션 (9)

```
GET /promotions
POST /promotions
GET /promotions/{id}
PUT /promotions/{id}
DELETE /promotions/{id}
PATCH /promotions/{id}/active
GET /promotions/{id}/redemptions
POST /promotions/{id}/redemptions
GET /promotions/validate
```

## 회계 (24)

```
GET /accounts
POST /accounts
GET /accounts/{id}
PUT /accounts/{id}
DELETE /accounts/{id}
POST /accounts/{id}/activate
POST /accounts/{id}/deactivate
GET /accounts/tree
GET /journal-entries
POST /journal-entries
GET /journal-entries/{id}
PUT /journal-entries/{id}
DELETE /journal-entries/{id}
POST /journal-entries/{id}/post
POST /journal-entries/{id}/void
GET /journal-entries/trial-balance
GET /operating-expenses
POST /operating-expenses
GET /operating-expenses/{id}
PUT /operating-expenses/{id}
DELETE /operating-expenses/{id}
POST /operating-expenses/{id}/approve
POST /operating-expenses/{id}/pay
POST /operating-expenses/{id}/reject
```

## 회원 (9)

```
GET /members
POST /members
GET /members/{id}
PUT /members/{id}
GET /members/{id}/reservations
GET /members/{id}/restrictions
POST /members/{id}/restrictions
POST /members/{id}/restrictions/{restrictionId}/end
PATCH /members/{id}/status
```
