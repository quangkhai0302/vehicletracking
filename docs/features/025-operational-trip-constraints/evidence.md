# Evidence 025 — Ràng buộc vận hành chuyến

Ngày kiểm tra ban đầu: **2026-09-21**. Cập nhật nghiệp vụ **2026-09-24**: bỏ giới hạn giờ khởi hành cho chuyến và simulator; cho phép tài xế được phân công nhiều chuyến và chỉ chặn xung đột khi bắt đầu chạy.

## Source evidence

| Acceptance criterion | Evidence sau implementation |
|---|---|
| AC-01 | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/trip/service/TripService.java` — `transition()` nhánh `IN_PROGRESS` bắt buộc driver, khóa và kiểm tra driver/xe active; `TripServiceTest` có case thiếu và inactive driver. |
| AC-02 (điều chỉnh 2026-09-24) | `TripService.transition()` không còn gọi `ensureStartWindow()`; xóa `TripLifecycleProperties` và các biến cấu hình. `TripServiceTest` kiểm tra chuyến được khởi hành trước/sau giờ dự kiến. `SimulationService.play()` gọi `TripService.start()` nên hưởng cùng quy tắc. |
| AC-03 | `TripRepository.findAllByVehicleIdAndStatusIn()` và `TripService.ensureNoVehicleScheduleConflict()` giữ conflict interval cho xe. `TripService.assignDriver()` không kiểm tra lịch dự kiến của tài xế; unit và repository integration test chứng minh một tài xế được gán hai chuyến overlap trên hai xe. |
| AC-03a | `TripService.transition()` nhánh `IN_PROGRESS` gọi `existsByDriverIdAndStatusAndIdNot(..., IN_PROGRESS, ...)`; migration `V14__create_drivers_and_assignments.sql` có unique partial index `uq_trips_running_driver`. `OperationsIntegrationTest.driverMayOwnOverlappingTripsButOnlyOneSimulationCanRun()` đi qua `SimulationService.play()` và kiểm tra chuyến thứ hai bị `409` mà không tạo run. |
| AC-04 | `transition()` chỉ complete khi `TripStopVisitRepository.existsByTripIdAndStopSequence()` trả true cho stop sequence cuối của attempt hiện tại; frontend disable nút Hoàn thành khi thiếu check-in. |
| AC-05 | `CancelTripRequest`, `TripController` và `TripService.cancel(long, String)` yêu cầu reason 3–500 ký tự; V17 lưu `cancellation_reason` và cấm reason trên status khác CANCELLED; entity giữ reason khi cancel idempotent. |
| AC-06 | `TripDetailPanel.vue` hiển thị điều kiện tài xế/final stop và lỗi thao tác; `FleetConfirmDialog.vue` nhận lý do hủy; `fleet.ts` gửi body cancel và `types/fleet.ts` nhận cancellation reason. |
| AC-07 | Migration `V17__add_trip_lifecycle_constraints.sql` giữ nguyên; DTO/API/frontend type nhất quán. Kiểm tra ngày 2026-09-24: 302 backend tests, 96 frontend unit tests, 5 motion tests, lint/typecheck/build đạt. |

## Verification 2026-09-24

- `git diff --check` — đạt.
- `cd vehicletracking-backend && ./mvnw test` với JDK 26 — exit 0, 302 tests passed, gồm PostgreSQL/Testcontainers.
- `cd vehicletracking-backend && ./mvnw -q -Dtest='OperationsIntegrationTest#driverMayOwnOverlappingTripsButOnlyOneSimulationCanRun,FleetRepositoryIntegrationTest#driverMayOwnOverlappingTripsButCannotStartBoth' test` — exit 0; cho phép hai phân công trùng lịch, chỉ chặn chuyến thứ hai khi tài xế đã chạy chuyến đầu.
- `cd vehicletracking-backend && ./mvnw -q -Dtest=OperationsIntegrationTest#simulatorStartsTripLongAfterPlannedDeparture test` — exit 0; simulator tạo vị trí cho chuyến dự kiến ba ngày trước.
- `cd vehicletracking-frontend && npm run lint && npm run typecheck && npm run test:unit && npm run test:motion && npm run build` với Node 24 — tất cả exit 0; 96 unit tests và 5 motion tests.
- Không thay đổi Flyway migration hay database schema.
