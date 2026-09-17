# 005 — Kết quả triển khai và xác minh

## Đồng bộ route khi sửa station

`StationService.update` gọi `RouteService.refreshRoutesUsingStation` khi tên hoặc tọa độ station thay đổi. Route active chưa từng được dùng bởi trip được cập nhật mọi stop snapshot; nếu tọa độ đổi, HERE được gọi lại để tính distance/duration/polyline và các shaping point được giữ nguyên. Route đã gắn trip được bỏ qua để không làm thay đổi lịch sử snapshot. `RouteRepository.findAllActiveByStationId` giới hạn phạm vi truy vấn vào các route có station liên quan. Địa chỉ và bán kính check-in không ảnh hưởng geometry nên không gọi lại HERE.

Ngày kiểm tra: 2026-09-13. Trạng thái: **đã triển khai và kiểm tra AC 1–8 trong working tree**, chưa commit/push. Các thay đổi Map-First 004 có sẵn được giữ lại. `docs/workflow.md` không tồn tại khi khảo sát; hồ sơ dùng AGENTS.md và yêu cầu triển khai trực tiếp của người dùng. Không mở rộng sang engine 006.

## Kiến trúc và evidence implementation

Đường dẫn backend tính từ `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/`; đường dẫn frontend tính từ `vehicletracking-frontend/src/`.

| AC | Implementation / symbol có thể đối chiếu |
| --- | --- |
| 1 — Xe CRUD/xóa mềm | `vehicle/controller/VehicleController`, `vehicle/service/VehicleService.create/update/deactivate`, `vehicle/dto/VehicleUpsertRequest`, `vehicle/entity/VehicleEntity`. Chuẩn hóa biển số, unique kể cả xe inactive; không cho ngừng xe có chuyến chờ/đang thực hiện. |
| 2 — Tạo/list/detail trip | `trip/controller/TripController`, `trip/service/TripService.create/findAll/findById`. Kiểm tra xe/trạm active, dùng route đã lưu; không gọi provider. |
| 3 — Lifecycle | `TripService.transition`, `trip/entity/TripStatus`, `TripEntity.start/complete/cancel`: chuyển trạng thái hợp lệ, lặp lại trạng thái đích không thay timestamp. |
| 4 — Race và DB | `vehicle/repository/VehicleRepository.findLockedById`, `trip/repository/TripRepository.findLockedById`; `src/main/resources/db/migration/V4__create_vehicles_and_trips.sql`: `uq_trips_running_vehicle`, `chk_trips_state`, `chk_trips_time_order`, FK RESTRICT. |
| 5 — Lịch cố định | `trip/entity/TripStopEntity`, `trip/dto/TripDetailResponse.from`, `TripSummaryResponse.from`: snapshot tên/tọa độ/radius/dwell/offset/planned times, biển số snapshot, geometry tham chiếu route immutable. `TripService.create/transition` chuẩn hóa timestamp về microsecond trước khi trả DTO để khớp PostgreSQL. |
| 6 — Map-First fleet | `components/fleet/FleetWorkspace`, `VehicleEditor`, `TripEditor`, `TripDetailPanel`, `fleet.css`: hai tab xe/chuyến, tìm/lọc, form, timeline, lifecycle. `components/MapComponent` giữ `editorRoute` riêng với `tripRoute`, vẽ tuyến chuyến theo selection bằng camera 004. |
| 7 — Async và UX | `hooks/useFleetWorkspace`: AbortController, token detail, busyRef; `services/fleet.ts`: HTTP/ProblemDetail; `FleetConfirmDialog`: native dialog/confirm/Escape. `utils/tripTime.ts`: ngày/giờ địa phương. Không dùng danh mục xe để tạo vị trí/ETA giả. |
| 8 — Verification | Các lệnh và kết quả bên dưới, browser fixture scripts và JUnit tests. |

Luồng dữ liệu UI: `FleetWorkspace → useFleetWorkspace → services/fleet → API`. Component con giữ draft; hook giữ dữ liệu và selection. FleetWorkspace luôn mounted trong shell khi đổi mode, nên draft không mất khi sang tạo trạm/tuyến. Khi mở chi tiết chuyến mới, tuyến cũ được bỏ khỏi map cho tới khi chi tiết tương ứng tải xong. Danh mục `FleetVehicle` tách khỏi type `Vehicle` dành cho telemetry sau này.

## API contract

| HTTP | Đường dẫn | Kết quả |
| --- | --- | --- |
| GET | `/api/v1/vehicles`, `/api/v1/vehicles/{id}` | Danh sách (gồm inactive) / VehicleResponse |
| POST | `/api/v1/vehicles` | Body `{plateNumber, name, description?}`; 201 + Location |
| PUT | `/api/v1/vehicles/{id}` | Body như create; 200, giữ ID |
| DELETE | `/api/v1/vehicles/{id}` | Ngừng sử dụng, 204; không xóa lịch sử |
| GET | `/api/v1/trips?vehicleId=...` | TripSummaryResponse[]; filter tùy chọn |
| GET | `/api/v1/trips/{id}` | TripDetailResponse |
| POST | `/api/v1/trips` | Body `{vehicleId, routeId, scheduledDepartureAt}`; 201 + Location |
| POST | `/api/v1/trips/{id}/start`, `/complete`, `/cancel` | TripDetailResponse sau hành động |

`TripDetailResponse = {trip, stops, route}`. `trip` chứa scheduledDepartureAt/plannedEndAt, startedAt/endedAt và status; `stops` có thứ tự, snapshot tọa độ/radius, offset và plannedArrivalAt/plannedDepartureAt. Thời gian API là ISO-8601 Instant, UI nhập/hiển thị giờ địa phương. Tạo chuyến cho phép lịch quá khứ trong năm 2000–2100; không tự khởi hành theo đồng hồ. Validation lỗi trả 400, không tìm thấy 404, trùng biển số/trạng thái không hợp lệ/xe có chuyến khác trả 409 ProblemDetail. Không có edit/delete trip trong phạm vi 005; hủy bằng lifecycle.

## Backend — đạt

Chạy từ `vehicletracking-backend` trên Java 26.0.1, Docker Engine 29.6.1; PostgreSQL 17 trong Testcontainers:

```powershell
.\mvnw.cmd test *> ..\docs\features\005-vehicles-trips\backend-test.log
```

Exit **0**, `BUILD SUCCESS`, **118 tests / 0 failures / 0 errors / 0 skipped**, hoàn tất 23:42:59 +07. Tất cả station/route integration tests trước đó cũng chạy. Evidence: `backend-test.log` (PowerShell UTF-16), `vehicletracking-backend/target/surefire-reports/`.

33 tests mới:

- `vehicle/service/VehicleServiceTest`: 8 — chuẩn hóa, unique, conflict, deactivate và update.
- `trip/service/TripServiceTest`: 13 — tuyến vòng A→B→A, qua ngày, snapshot, inactive/missing/bad date, lifecycle và giữ baseline.
- `trip/controller/FleetControllerTest`: 7 — request validation, timezone offset, response/Location, ProblemDetail và delegation.
- `trip/repository/FleetRepositoryIntegrationTest`: 5 — Flyway V1–V4 + Hibernate validate; giữ snapshot sau sửa trạm/xe, CHECK/unique, partial index, hai transaction start đồng thời chỉ một thành công, deactivate sau cancel.

Các lỗi tìm thấy và đã sửa trước lần chạy cuối: JVM Windows gửi timezone alias `Asia/Saigon` làm PostgreSQL test từ chối; Surefire đặt `user.timezone=UTC` cho JVM test. Timestamps Java nanosecond khác độ chính xác PostgreSQL microsecond làm response retry lệch; create/start/complete/cancel chuẩn hóa precision. Assertion thời gian không dựa vào ngày cố định tương lai. Không đổi timezone runtime ứng dụng hoặc database người dùng. Docker/cache Maven cần chạy ngoài sandbox hạn chế; quyền thực thi đã được cho phép và bộ test cuối không còn bị chặn.

## Frontend — đạt

Node 22.20.0 đáp ứng engine hiện tại (repo định hướng Node 24). Chạy từ `vehicletracking-frontend`:

```powershell
npm.cmd run lint
.\node_modules\.bin\tsc.cmd --noEmit
npm.cmd run build
```

Cả ba exit **0**, lint không có warning. Build **1881 modules**, JS khoảng 443.33 kB / gzip 132.03 kB. Không thêm dependency vào package ứng dụng. `git diff --check` không báo lỗi whitespace; các thông báo LF/CRLF là quy đổi line ending của cấu hình Git hiện có.

## Browser fixtures — đạt

Dev server đã chạy ở `http://127.0.0.1:5173`, headless Microsoft Edge. Script dùng Playwright đã cài riêng ở `docs/features/004-operations-layout/verification/node_modules`; không cài test runner vào frontend. Nếu chuyển máy, cần phục hồi/cài dependency verification riêng và mở dev server trước.

```powershell
node docs/features/005-vehicles-trips/verification/fleet-smoke.mjs
node docs/features/005-vehicles-trips/verification/regression-004.mjs
```

- Fleet: **11 nhóm kiểm tra**, `pageErrors: []`; `artifacts/results.json` lúc 23:48:18 +07. Gồm create/update/soft deactivate, duplicate/409, giữ nháp qua mode, submit chậm không gửi lặp, trip gán xe/tuyến/ngày giờ, UTC và lịch qua ngày, start/complete/cancel và confirmation, stale GET, inactive/history, API error/retry. Kiểm tra detail/editor ở 390 và 320 px, desktop 1440 px; không tràn ngang, bản đồ toàn viewport.
- Regression 004: **19 nhóm kiểm tra**, `pageErrors: []`; `artifacts/regression-004/results.json` lúc 23:47:03 +07. CRUD trạm, drag/keyboard reorder, giữ route/draft/camera, mobile map picking, errors và stale responses; 1440/1024/768/390/320 và landscape 844×390. Script là bản điều chỉnh fixture xe/chuyến từ smoke 004 để giữ test lịch sử nguyên vẹn.
- Screenshot thành công: `artifacts/fleet-desktop-fixture.png`, `trip-editor-desktop-fixture.png`, `trip-detail-desktop-fixture.png`, `trip-detail-390-fixture.png`, `trip-detail-320-fixture.png` và editor tương ứng. `failure-fixture.png` là ảnh từ lần thử trước, không phải kết quả cuối.

Tất cả `/api/` trong browser được intercept bằng fixture; không ghi dữ liệu vận hành. Tiles bên ngoài có thể tải chậm/thiếu trong ảnh, script chờ DOM và UI của ứng dụng. Không dùng ảnh fixture để kết luận HERE live, GPS, hoặc UI nối database thật đã hoạt động. Integration backend chứng minh persistence trong database tạm, không thay cho live end-to-end.

## Sử dụng và phần còn lại

Khởi động lại backend theo cấu hình hiện có để Flyway áp dụng V4 vào database phát triển; không cần biến môi trường mới. Trong **Theo dõi → Đội xe**, thêm xe; sang **Chuyến đi**, chọn xe/tuyến đã lưu/giờ xuất phát, lưu rồi xem timeline hoặc khởi hành/hoàn thành/hủy. “Đang thực hiện” chỉ là trạng thái nghiệp vụ, chưa xác nhận xe đang di chuyển.

Chưa có ingestion/GPS/SSE, engine simulator, check-in, ETA động, traffic/reroute hay notification. Bước kế tiếp là 006 theo `docs/PROJECT_PROGRESS.md`. Không đọc/đổi `.env`, không áp dụng V4 vào database vận hành trong lượt kiểm tra, không đổi `.gitignore`; tài liệu dưới `docs/` vẫn local/ignored.
