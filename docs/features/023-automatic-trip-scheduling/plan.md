# Plan 023 — Lập lịch chuyến tự động

Trạng thái: **Approved by direct implementation request**.

1. **Schema/domain** — tạo V15 cho `trip_schedules`, thêm provenance/idempotency vào `trips`; tạo entity/enum/repository. Test migration/constraints.
2. **Backend contract** — tạo DTO/controller/service CRUD + enable/disable, validation timezone/recurrence/resource; refactor `TripService` để scheduler dùng chung. Test controller/service.
3. **Scheduler** — bật scheduling, tạo polling service 7-day horizon, resolve timezone, unique guard, last-run status. Test occurrence, retry, conflict và inactive resources.
4. **Frontend API/types** — thêm `schedules.ts`, schedule types và route metadata.
5. **Schedule page** — thay roadmap bằng list/KPI/filter/editor responsive, toggle confirmation và link trips; không mở rộng FleetWorkspace.
6. **Verify/docs** — chạy Maven tests, lint/tsc/build/diff check; cập nhật evidence/walkthrough/review sau kiểm tra.
