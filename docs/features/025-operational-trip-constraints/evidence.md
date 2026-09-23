# Evidence 025 — Ràng buộc vận hành chuyến

Ngày kiểm tra: **2026-09-21**.

## Source evidence

| Acceptance criterion | Evidence sau implementation |
|---|---|
| AC-01 | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/trip/service/TripService.java` — `transition()` nhánh `IN_PROGRESS` bắt buộc driver, khóa và kiểm tra driver/xe active; `TripServiceTest` có case thiếu và inactive driver. |
| AC-02 | `TripLifecycleProperties`, `application.yaml` và `.env.production.example` cấu hình cửa sổ sớm 1.800 giây/trễ 7.200 giây; `ensureStartWindow()` chặn ngoài khoảng; unit test kiểm tra ngoài/trong cửa sổ. |
| AC-03 | `TripRepository.findAllByVehicleIdAndStatusIn()` và `findAllByDriverIdAndStatusIn()`; `ensureNoResourceConflict()` dùng interval half-open theo estimated duration, được gọi khi tạo, sửa giờ, gán driver và start; service tests bao phủ xe/tài xế overlap. |
| AC-04 | `transition()` chỉ complete khi `TripStopVisitRepository.existsByTripIdAndStopSequence()` trả true cho stop sequence cuối của attempt hiện tại; frontend disable nút Hoàn thành khi thiếu check-in. |
| AC-05 | `CancelTripRequest`, `TripController` và `TripService.cancel(long, String)` yêu cầu reason 3–500 ký tự; V17 lưu `cancellation_reason` và cấm reason trên status khác CANCELLED; entity giữ reason khi cancel idempotent. |
| AC-06 | `TripDetailPanel.tsx` tính readiness từ driver active/final stop, hiển thị prerequisite; `FleetConfirmDialog.tsx` nhận textarea reason và disable confirm khi chưa đủ; `fleet.ts` gửi body cancel và `types/fleet.ts` nhận cancellation reason. |
| AC-07 | Migration `V17__add_trip_lifecycle_constraints.sql`, DTO/API/frontend type và test/controller/service đã đồng bộ; lint, typecheck, build và 254 non-Docker backend tests đạt. |

## Verification commands

- `git diff --check` — đạt.
- `cd vehicletracking-frontend && PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint && ./node_modules/.bin/tsc --noEmit && npm run build` — exit 0; lint còn 6 warning không chặn.
- `cd vehicletracking-backend && env JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn PATH=... ./mvnw -Dtest='!**/*IntegrationTest' test` — exit 0, 254 tests passed.
- `cd vehicletracking-backend && ./mvnw test` — 6 integration tests lỗi vì Docker daemon không khả dụng; không có failure assertion.

Chưa tuyên bố Maven/Flyway integration test thành công vì Docker daemon không khả dụng trong môi trường kiểm tra.
