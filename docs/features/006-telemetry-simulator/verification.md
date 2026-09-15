# 006 — Triển khai và kết quả kiểm tra

> Cập nhật contract 2026-09-15: phần mô tả reset tạo trip thay thế bên dưới là evidence lịch sử của 006 và đã được feature 012 thay thế. Contract hiện tại giữ nguyên trip/route/run, tăng `attemptNumber` và tách lịch sử theo lần chạy; xem `docs/features/012-simulation-replay/`.

Cập nhật 2026-09-14. **Source backend/frontend đã được nối; chưa đủ điều kiện nghiệm thu toàn bộ** vì còn test HTTP/SSE với Spring, browser nối PostgreSQL thật và full Maven suite chưa chạy được. Bộ duyệt quyền tự động từ chối Maven/Docker bổ sung do hạn mức tài khoản; không dùng lệnh gián tiếp để vượt từ chối.

## Implementation và evidence

Đường dẫn Java dưới đây tính từ `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/`; frontend từ `vehicletracking-frontend/src/`.

| Phạm vi | Evidence |
| --- | --- |
| HTTP GPS + ingestion chung | `telemetry/controller/TelemetryController.ingest`, `telemetry/service/TelemetryService.ingestGps/ingestSimulator`: validation, eventId idempotency, timestamp cũ/bằng bị 409, GPS không được trộn với simulator; recordedAt và receivedAt riêng. |
| Persistence | `telemetry/entity/TelemetrySampleEntity`, `VehiclePositionEntity`; `telemetry/repository/TelemetryRepository`, `VehiclePositionRepository`. V5 `V5__create_telemetry_and_simulation.sql`: history/latest/run, unique, FK và CHECK. V1–V4 không sửa. |
| Geometry/clock | `simulation/motion/FlexiblePolyline.decode`, `RouteMotion.at`: khoảng cách segment trên từng section, dwell theo stop occurrence, next stop và progress theo tuyến vòng; từ chối geometry không đủ hoặc thời lượng sai. `simulation/service/SimulationService`: clock wall time × multiplier, simulatedAt riêng, tốc độ vật lý không nhân 5/10. |
| Lifecycle | `SimulationService.play/pause/speed/stop/reset/tick/recover`: khóa trip → vehicle theo thứ tự 005; tick/play cùng trip được tuần tự hóa. Evidence reset tạo replacement tại mốc 006 đã được feature 012 thay bằng reset cùng trip và attempt idempotent. `SimulationScheduler.start/tick/close`: khởi động lại chuyển running thành paused, scheduler riêng backend, lỗi có trạng thái FAILED. |
| SSE | `telemetry/service/OperationsSnapshotService.snapshot` dùng transaction read-only REPEATABLE_READ để đọc snapshot nhất quán đã commit. `OperationsStreamService.subscribe/broadcast/stop`: snapshot lúc mở và mỗi giây, timeout 10 phút, cleanup, một write đang chờ/client, không tích hàng đợi vô hạn. Scheduler phát stream tách khỏi simulator. |
| UI | `hooks/useLiveOperations`, `services/operations`, `types/operations`: snapshot ban đầu, EventSource resync, reconnect, timer freshness. `hooks/useSimulator`: selection, abort detail, busyRef, command response + stream state. `components/operations/SimulatorPanel` và `simulator.css`: điều khiển, confirmation, telemetry và đồng hồ. |
| Map/Fleet | `hooks/useVehicleMarkers`: giữ marker DOM, cập nhật vị trí/heading/tooltip, nguồn GPS/GIẢ LẬP, stale/offline; follow chỉ dịch camera khi tọa độ đổi. `MapComponent` dùng nguồn chung và route simulator riêng khi ở mode simulation. `FleetWorkspace/useFleetWorkspace` hợp nhất status chuyến từ snapshot, bảo toàn draft và baseline; nút mở simulator từ chi tiết chuyến. |

Điểm quan trọng về dữ liệu: tọa độ simulator được phát qua cùng ingestion và lưu như GPS nhưng có source riêng; không lấy tọa độ station làm fallback cho geometry hỏng. `recordedAt` luôn wall clock; `simulatedAt` là giờ xuất phát kế hoạch cộng thời gian mô phỏng. Các dữ liệu cũ/terminal vẫn có thể xem nhưng không được gọi là xe đang chạy. Pause không ghi tick di chuyển; frontend hiển thị trạng thái paused thay vì coi vị trí đứng yên là check-in.

## API và sử dụng

| HTTP | Endpoint | Body/kết quả |
| --- | --- | --- |
| POST | `/api/v1/telemetry` | `{eventId,vehicleId,tripId,recordedAt,latitude,longitude,speedKmh,heading,accuracyMeters,source:"GPS"}` → TelemetryResponse |
| GET | `/api/v1/telemetry/snapshot` | `{serverTime,positions,simulations,trips}` |
| GET | `/api/v1/telemetry/stream` | `text/event-stream`, event `snapshot`, ID serverTime, retry 1000 ms; mỗi reconnect luôn nhận full snapshot |
| POST | `/api/v1/trips/{tripId}/simulation/play` | Bắt đầu trip hoặc tiếp tục run paused; SimulationResponse |
| POST | `/api/v1/trips/{tripId}/simulation/pause` | Chốt tiến độ tới thời điểm pause, tốc độ 0 |
| POST | `/api/v1/trips/{tripId}/simulation/speed` | `{multiplier:1\|5\|10}` |
| POST | `/api/v1/trips/{tripId}/simulation/stop` | Dừng run và hủy trip còn mở, giữ history |
| POST | `/api/v1/trips/{tripId}/simulation/reset` | Feature 012: giữ cùng trip/run, tăng attempt và đưa run về paused/0/1×; lịch sử cũ được giữ theo attempt |

HTTP client gửi source SIMULATOR bị 400. Tọa độ, speed 0–500 km/h, heading [0,360), accuracy 0–10000 m, timestamp từ năm 2000 và không vượt server time +30 giây được validate; active trip/vehicle/source conflicts trả 409. Timestamp normalized microsecond để khớp PostgreSQL; trùng eventId cùng nội dung trả mẫu cũ, không ghi lặp. Mẫu GPS bằng/cũ hơn latest bị từ chối và không thay latest.

UI vào **Mô phỏng → Chọn chuyến → Phát** hoặc từ **Chuyến đi → Mở mô phỏng chuyến này**. Dùng 1×/5×/10×, pause, stop hoặc chạy lại có xác nhận. Mở hai tab, chọn cùng trip để quan sát trạng thái chung. Click marker rồi **Theo xe**; kéo bản đồ hủy follow. Mất stream khóa điều khiển simulator; giữ tọa độ cuối. GPS quá 15 giây hiển thị vị trí cũ, quá 60 giây hiển thị mất tín hiệu. ETA của simulator là số giây giả lập còn lại theo route snapshot; chưa áp dụng HERE Traffic hoặc check-in.

Không cần biến môi trường mới để chạy mặc định. `app.simulation.scheduling-enabled=false` là property phục vụ test clock; mặc định true. Khởi động backend theo cấu hình hiện có sẽ chạy Flyway V5; lượt này chỉ áp dụng V5 trong Testcontainers, không đụng database phát triển của người dùng.

## Đã chạy thành công

Backend (Java 26.0.1, PostgreSQL 17 Testcontainers) từ `vehicletracking-backend`:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd '-Dtest=RouteMotionTest,OperationsIntegrationTest' test
```

- Compile exit 0; log `backend-compile.log`.
- Test chọn lọc exit 0, **24 tests / 0 failures / 0 errors / 0 skipped**, hoàn tất 00:11:55 +07 ngày 2026-09-14. `RouteMotionTest`: 14; `OperationsIntegrationTest`: 10. Log UTF-16 `backend-focused.log`, reports trong backend `target/surefire-reports/`.
- Đã chạy Flyway V1–V5 + Hibernate validate, kiểm tra GPS dedupe/collision/old/future/source/wrong vehicle, source exclusion, clock/pause/5×/dwell/completion, reset giữ lịch sử/idempotent, recover giữ tiến độ, manual cancel, concurrent play và geometry sai không khởi hành.
- Các test trên gọi service qua transaction và database thật trong container tạm; **chưa chứng minh HTTP/SSE hoặc browser nối Spring**. Production Java code chưa sửa sau lần focused pass; HTTP test được thêm sau và chưa chạy.

Frontend từ `vehicletracking-frontend`:

```powershell
npm.cmd run lint
.\node_modules\.bin\tsc.cmd --noEmit
npm.cmd run build
```

Cả ba exit 0, không có lint warning; build 1887 modules. `git diff --check` không báo whitespace error; LF/CRLF warnings là cấu hình line ending hiện có. Không thêm dependency ứng dụng hoặc đọc `.env`.

Browser (Edge headless, dev server 127.0.0.1:5173):

```powershell
node docs/features/006-telemetry-simulator/verification/live-browser.mjs
node docs/features/006-telemetry-simulator/verification/regression-004.mjs
node docs/features/006-telemetry-simulator/verification/regression-005.mjs
```

- `live-browser.mjs` khi không có VERIFICATION_API khởi động **Node API fixture có SSE socket thật**, không chạy Spring hoặc truy cập database. Đạt **9 nhóm**, `pageErrors: []`: hai tab cùng tiến độ/tạm dừng, 5×/10×, reload/reconnect resync, marker/follow, reset giữ history, complete, conflict/recovery, stale/offline và khóa điều khiển khi ngắt stream, 390/320 px. Kết quả: `artifacts/fixture/results.json`; ảnh desktop/mobile cùng thư mục.
- Regression 004: **19 nhóm**, `pageErrors: []`, giữ CRUD/drag/keyboard/camera/map picking, route errors và mobile 320–1440 px + landscape. `artifacts/regression-004/results.json`.
- Regression 005: **11 nhóm**, `pageErrors: []`, giữ xe/trip/form/dirty confirmation/lifecycle/409/stale request và mobile. `artifacts/regression-005/results.json`.
- Tổng **39 nhóm browser fixture**. Scripts 004/005 được copy và bổ sung endpoint telemetry fixture dưới hồ sơ 006, không sửa bằng chứng lịch sử. Playwright dependency dùng lại thư mục verification 004, không nằm trong package frontend.
- Lỗi tìm thấy qua browser đã sửa: dựng lại icon làm phần tử marker bị detach liên tục khi hover, và follow dịch camera dù tọa độ không đổi. `useVehicleMarkers` hiện cập nhật DOM tại chỗ, lưu tọa độ đã follow; test stale/hover/reconnect đã chạy lại đạt.

## Chưa xác minh và bước cần chạy tiếp

1. **OperationsHttpIntegrationTest** (2 test mới) chưa được Maven compile/run: HTTP validation/lifecycle, hai SseEmitter subscriber, CORS, Last-Event-ID resync, disconnect cleanup. Import LocalServerPort đã đối chiếu với jar Spring Boot hiện tại; đọc code không thay cho chạy test.
2. Extension browser của HTTP test chưa chạy với Spring + PostgreSQL; chưa đo latency command-to-SSE trên backend thật. Script đã chuẩn bị nhưng không tuyên bố AC hai tab end-to-end hoặc mục tiêu <2 giây đã đạt.
3. Full Maven suite sau thêm 006 chưa chạy. Kết quả 118 pass của feature 005 là lịch sử, **không cộng với 24 để tuyên bố full suite 006 đã pass**.

Lệnh để tiếp tục khi quyền/hạn mức cho phép (từ backend):

```powershell
.\mvnw.cmd '-Dtest=OperationsHttpIntegrationTest' test
.\mvnw.cmd '-Dtest=OperationsHttpIntegrationTest' '-Dverification.browser006=true' test
.\mvnw.cmd test
```

Browser extension cần frontend dev server ở 127.0.0.1:5173. Test tạo dữ liệu station/route/vehicle/trip trong PostgreSQL container riêng, provider HERE tắt. ProcessBuilder truyền port backend tạm qua VERIFICATION_API; browser rewrite URL API để gọi đúng instance test. Nếu assertion HTTP/browser thất bại cần sửa rồi chạy lại trước khi đánh dấu 006 hoàn thành.

**Blocker hiện tại:** auto-review từ chối lệnh `mvnw.cmd -Dtest=OperationsHttpIntegrationTest test` với lý do `Automatic approval review failed: You've hit your usage limit`, thông báo thử lại 04:30. Đây là lỗi quyền/hạn mức thực thi, không phải test đã chạy và thất bại. Không thử lệnh Maven/Docker gián tiếp để vượt từ chối. Các kiểm tra frontend độc lập đã được hoàn thành.

## Giới hạn triển khai

Một backend instance quản lý scheduler; chưa có distributed leader/scheduler, authentication thiết bị, retention/pagination, GPS thiết bị thật, HERE Traffic, check-in, reroute hoặc thông báo lưu bền. Full snapshot mỗi giây phù hợp bản phát triển hiện tại; không cam kết hiệu năng với đội xe lớn. SSE dùng resync mới nhất, không replay mọi vị trí bỏ lỡ; lịch sử vẫn ở telemetry_samples cho feature sau. Các tác vụ UI/module cũ có sẵn được giữ; chưa commit/push. Cuối lượt thấy `.gitignore` được chỉnh đồng thời ngoài phần agent sửa để bỏ rule `docs/`; giữ nguyên thay đổi đó, tài liệu hiện local/untracked.
