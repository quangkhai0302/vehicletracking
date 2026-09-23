# Evidence 021 — Quản lý tài xế

Trạng thái kiểm chứng: **Verified** trên working tree chưa commit; kiểm tra lại ngày 2026-09-21.

## Phạm vi thay đổi chính

- Schema: `vehicletracking-backend/src/main/resources/db/migration/V14__create_drivers_and_assignments.sql`.
- Backend driver CRUD: package `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/driver/`.
- Assignment xe/chuyến: `VehicleController`, `VehicleService`, `TripController`, `TripService` và DTO/entity/repository liên quan.
- Frontend: `types/fleet.ts`, `services/fleet.ts`, `useFleetWorkspace.ts`, `DriverEditor.tsx` và các component Fleet Workspace.
- Test: driver controller/service, vehicle/trip service và PostgreSQL integration trong `FleetRepositoryIntegrationTest`.

Các thay đổi `AGENTS.md` và `.codex/` đã có từ công việc cấu hình subagent trước feature; chúng được giữ nguyên, không được tính là source của feature 021.

## Mapping acceptance criteria

| AC | Evidence code/test | Kết quả |
|---|---|---|
| AC-01 | `driver/controller/DriverController.java:17`, `driver/service/DriverService.java:29`, `DriverControllerTest`, `DriverServiceTest` | CRUD, validation, trim/normalize GPLX và conflict trùng đã được test. |
| AC-02 | `DriverService.java:57`, `DriverServiceTest.deactivate_requiresNoActiveAssignment`, `deactivate_isSoftAndIdempotent` | Soft-delete idempotent và blocker assignment hoạt động. |
| AC-03 | `VehicleService.java:59`, V14 dòng 19, `VehicleServiceTest:103`, `FleetRepositoryIntegrationTest:146` | Gán/bỏ gán xe được bảo vệ bằng service lock và unique partial index. |
| AC-04 | `TripEditor.tsx:12-55`, `TripService.java:45-73`, `TripServiceTest.create_snapshotsSelectedDriverDetails` | UI preselect từ xe; backend lưu đúng driver được gửi hoặc null. |
| AC-05 | `TripService.java:94-105`, `TripServiceTest.assignDriver_onlyAllowsScheduledTrip` | Chỉ trip `SCHEDULED` được đổi/bỏ driver. |
| AC-06 | `TripEntity.java:75`, V14 dòng 24-38, `FleetRepositoryIntegrationTest:134` | Snapshot tên/điện thoại/GPLX được persist và không đổi khi hồ sơ đổi. |
| AC-07 | `TripService.java:121-132`, V14 dòng 42, `TripServiceTest.start_rejectsDriverRunningAnotherTrip`, `FleetRepositoryIntegrationTest:155` | Driver inactive/đang chạy bị chặn; DB chặn race hai trip running. |
| AC-08 | `FleetWorkspace.tsx:35-117`, `DriverEditor.tsx`, `VehicleEditor.tsx:36`, `TripDetailPanel.tsx:61` | Có tab/list/search/filter/form/confirm và thao tác assignment. Build/typecheck chứng minh contract; chưa chạy browser manual. |
| AC-09 | Các lệnh bên dưới | Backend non-Docker, migration contract, lint, typecheck và build đều exit 0; integration PostgreSQL cần Docker. |

## Lệnh đã chạy

Backend dùng Java 26 tại `/home/khainq/.sdkman/candidates/java/26.0.1-amzn`:

```bash
cd vehicletracking-backend
JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn ./mvnw -q test
```

- Exit code `0` khi chạy `-Dtest='!**/*IntegrationTest'`.
- Surefire: **254 tests, 0 failures, 0 errors, 0 skipped**; full suite còn 6 lớp integration bị chặn vì Docker daemon không khả dụng.
- Hibernate compile/validation contract và các unit/controller test xác nhận snapshot, unique assignment xe và partial unique running-trip; Flyway/Testcontainers cần chạy lại trên môi trường có Docker.

Frontend dùng Node `v24.16.0`:

```bash
cd vehicletracking-frontend
PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint
PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH ./node_modules/.bin/tsc --noEmit
PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run build
```

- Cả ba lệnh exit `0`.
- Lint còn 6 warning không chặn (`react(set-state-in-effect)` và `react(only-export-components)`); không có lint error.
- Vite build thành công, còn warning chunk chính lớn hơn 500 kB vốn không chặn build.

Kiểm tra bổ sung:

```bash
git diff --check
rg -n "(?i)(api[_-]?key|secret|password|token)\\s*[:=]" <các file feature>
```

- `git diff --check`: exit `0`.
- Secret scan trên code/migration/tài liệu feature: không có kết quả.

## Giới hạn và sai lệch

- Chưa chạy kiểm tra browser thủ công cho responsive, focus/keyboard và toàn bộ chuỗi thao tác UI; repository chưa có React component/E2E test runner.
- Reviewer subagent `gpt-5.6-sol/high` đã được gọi nhưng dịch vụ từ chối do usage limit. Main agent đã thực hiện fallback review; chi tiết ở `review.md`.
- Backend focused/non-Docker test chạy trên JDK 26; full integration bị giới hạn bởi Docker daemon của môi trường.
