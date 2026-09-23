# Evidence 023 — Lập lịch chuyến tự động

Trạng thái: **Implemented — static verification và non-Docker runtime verification đạt**.

## Mapping acceptance criteria

| AC | Evidence hiện tại | Trạng thái |
|---|---|---|
| AC-01 | `schedule/controller/TripScheduleController.java:14-27`, `schedule/service/TripScheduleService.java` validation ONCE/WEEKLY, timezone, effective range và active resource | Đã implement; chưa chạy API integration |
| AC-02 | `ScheduleResponse`, `TripScheduleService.findAll()`, `ScheduleManagementPage.tsx:106-128` có KPI/list/filter/next/last run | Đã implement |
| AC-03 | enable/disable API, `TripSchedulePollingScheduler`, recheck `enabled`/`version` trong `TripService.createFromSchedule` | Đã implement; race runtime chưa có integration test |
| AC-04 | `TripService.createFromSchedule/createInternal`, `TripEntity.schedule/scheduleOccurrenceAt`, snapshot route/stop/driver | Đã implement |
| AC-05 | V15 unique index `uq_trips_schedule_occurrence`, provenance pairing check, pre-check repository, không hard-delete trip có schedule | Đã implement; chưa chạy PostgreSQL concurrency test |
| AC-06 | Active checks trong schedule service/trip service, interval overlap theo estimated duration cho xe/tài xế và `last_run_*` | Đã implement; race/station deactivation cần PostgreSQL integration test |
| AC-07 | `ScheduleManagementPage.tsx` và `schedule-management.css`: loading, empty, error/retry, validation, confirm, responsive drawer | Đã implement; chưa có browser test tự động |
| AC-08 | V15, backend/frontend contract đồng bộ; lint/tsc/build/diff check và backend unit/web/service test đạt | PostgreSQL/Testcontainers chưa chạy vì Docker daemon không khả dụng |

## Files chính

- `vehicletracking-backend/src/main/resources/db/migration/V15__create_trip_schedules.sql`
- `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/schedule/`
- `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/trip/service/TripService.java`
- `vehicletracking-frontend/src/pages/ScheduleManagementPage.tsx`
- `vehicletracking-frontend/src/services/schedules.ts`
- `vehicletracking-frontend/src/types/schedule.ts`

## Commands đã chạy

- `PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint` — exit 0; còn 6 warning lint không chặn.
- `./node_modules/.bin/tsc --noEmit` — exit 0.
- `git diff --check` — exit 0.
- `PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run build` — exit 0.
- `env JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn PATH=... ./mvnw -Dtest='!**/*IntegrationTest' test` — exit 0, 254 tests passed.
- `./mvnw test` trên JDK 26 — 260 tests, 0 failures; 6 integration tests lỗi vì không tìm thấy Docker daemon.

## Giới hạn cần xác minh tiếp

- Chạy lại PostgreSQL/Testcontainers trên CI hoặc máy có Docker để kiểm tra Flyway V15, JPA validate và hai transaction cạnh tranh.
- Bổ sung browser/API integration test cho toggle, disabled race, overlap route/station và conflict theo khoảng thời gian nếu nghiệp vụ yêu cầu.

Không có secret mới; không commit hoặc push trong lượt này.
