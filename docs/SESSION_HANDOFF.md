# Báo cáo bàn giao session — Vehicle Tracking

**Mốc chốt: 2026-09-14, múi giờ Asia/Saigon. Workspace hiện tại:** `/home/khainq/Code/vehicletracking`**, bash. Working tree có source 004–008 chưa commit.**

Đây là báo cáo tổng hợp để tiếp tục ở session mới mà không cần đọc lại hội thoại. Nội dung được đối chiếu với source, cấu hình và log đang có trên máy. Các kết quả test bên dưới ghi rõ lệnh và phạm vi; full suite/browser end-to-end vẫn chưa chạy trong môi trường hiện tại.

**Trạng thái hiện tại: Feature 009 và 010 đã triển khai source; việc tiếp theo là migrate V7/V8 và verification integration/browser.** Backend 008 đã có provider/cache/API/matching/ETA theo vị trí/raster tile proxy, simulator rate/blocked metadata; Feature 009 thêm evaluator hai fetch, route revision, notification persistence/API và SSE; Feature 010 thêm management endpoints. Test policy/compile/frontend pass; full Maven suite ở môi trường hiện tại bị giới hạn JDK 26 Byte Buddy và Docker PostgreSQL. 007–010 vẫn còn edge/load/live evidence.

## 1. Mục tiêu sản phẩm và quyết định đã được người dùng chấp thuận

Người dùng muốn website theo dõi xe realtime trên bản đồ; CRUD trạm; tạo tuyến từ điểm dừng và dự kiến thời gian chuyến; tự check-in khi xe đi ngang trạm; ETA từng trạm; simulator phản ứng với giao thông thật; tự thông báo khi sự cố/kẹt xe nghiêm trọng làm đổi lịch/lộ trình.

Người dùng không hài lòng dashboard truyền thống và đã chọn **Map-First Canvas dark mode**: bản đồ toàn màn hình, navigation gọn nổi phía trên, drawer trái cho dữ liệu/tuyến/trạm, simulator trên phải và luồng cảnh báo dưới phải, mobile dùng panel thu/mở. Không quay về dashboard hai cột nền sáng theo tài liệu cũ.

004 và 005 đã triển khai theo yêu cầu trực tiếp. Người dùng cũng đã yêu cầu trực tiếp triển khai **006 — telemetry và simulator cơ bản**, rồi nhiều lần yêu cầu tiếp tục. Phần công việc còn lại của 006 đã được cho phép về phạm vi; không cần xin lại quyết định có làm feature hay không. Quyền thực thi công cụ/hạn mức là vấn đề riêng.

007 đã được triển khai theo yêu cầu trực tiếp. 008 và 009 đã được triển khai source trong working tree; 009 vẫn cần integration/live verification. Không đưa driver CRUD, MQTT/broker, email/SMS hoặc production infrastructure thành điều kiện của các mốc hiện tại.

## 2. Trạng thái thực tế theo chức năng

| Chức năng | Đã có | Còn thiếu / giới hạn bằng chứng |
| --- | --- | --- |
| Quản lý trạm | CRUD, xóa mềm, tọa độ, bán kính, map picking và kéo marker, tìm tên/địa chỉ | Check-in do feature 007 xử lý theo TripStop snapshot; tìm kiếm tọa độ chưa có |
| Lập tuyến | 2–50 điểm, reorder, dwell, gọi HERE server-side, lưu snapshot và vẽ tuyến | Chưa edit/recalculate/deactivate/versioning tuyến đã lưu; chưa xác minh HERE live trong session này |
| Layout 004 | Map-First dark, drawer/panel, mobile, camera tránh vùng bị che, drag/keyboard reorder | Prototype/ảnh dashboard cũ chỉ là lịch sử |
| Xe và chuyến 005 | CRUD/xóa mềm xe; gán tuyến + giờ xuất phát; snapshot lịch; start/complete/cancel; khóa xe và DB unique bảo vệ race | Chưa có driver, edit/delete trip; hủy bằng lifecycle |
| Telemetry 006 | HTTP GPS contract, lịch sử/latest JPA, chống trùng và bản tin cũ, source GPS/SIMULATOR | Chưa kết nối thiết bị GPS thật |
| Simulator 006 | Nội suy geometry, clock ở backend, dwell, play/pause/1×/5×/10×/stop/reset, persistence, recovery paused, UI | Browser Spring/PostgreSQL đã chạy trong extension 007; chưa có traffic live |
| Realtime UI 006 | SSE snapshot, marker/follow, nguồn/thời điểm, stale/offline, đồng bộ panel/status | Chưa có load benchmark production |
| Check-in 007 | GPS POINT/SEGMENT, simulator ROUTE_TRACE, ordered visits, revision, GET/API snapshot/SSE, timeline UI; browser core đã xác nhận 3 visits | Còn load/edge evidence; feature đang `Implementing` |
| Traffic/ETA 008 | Provider v7 flow/incidents server-side, raster tile proxy, cache fresh/stale, API bbox, ETA theo vị trí, simulator traffic rate/blocked, map tile/ETA UI; live spike upstream flow/incidents/tile HTTP 200 | Chưa xác minh browser → Spring → HERE; chưa full-suite/load evidence |
| Reroute/cảnh báo 009 | Revision + checkpoint + notification persistence, evaluator HERE, API/SSE và AlertStream đã có source | Chưa chạy full integration PostgreSQL/HERE live; chưa điều khiển dẫn đường thiết bị |

Evidence chính: các symbol và file ở mục 5–9. Không quy đổi thành phần trăm; “có source”, “đã test” và “đã nghiệm thu end-to-end” là các trạng thái khác nhau.

## 3. Working tree và nguyên tắc bảo toàn

- Có nhiều thay đổi **chưa commit/chưa stage**, gồm source 004, 005, 006 và tài liệu. Clone lại HEAD hiện tại sẽ không có toàn bộ công việc này. Tiếp tục trên workspace này hoặc chuyển đầy đủ file modified/untracked nếu đổi máy.
- Trước lượt viết báo cáo, `git status --short` có modified `.gitignore`, `PROJECT_HANDOFF.md`, backend `pom.xml`, các file shell/map/route/station frontend; untracked các package vehicle/trip/telemetry/simulation, V4/V5, tests và các hook/component/service/type mới. `git diff` đơn thuần không liệt kê nội dung file untracked.
- `.gitignore` đã được chỉnh đồng thời bởi phía ngoài phần agent sửa để bỏ rule `docs/`. Thay đổi này được giữ nguyên. `docs/` **hiện untracked, không còn bị ignore cả thư mục**; `node_modules/`, `target/`, `dist/`, `.env` vẫn bị ignore. `.env.example` cũng đang có rule ignore; chưa sửa chuyện này.
- Các mô tả “docs vẫn ignored” trong tài liệu 004/005 cũ phản ánh mốc trước đó. Không dùng chúng thay cho trạng thái Git hiện tại.
- Chưa commit/push; người dùng chưa yêu cầu. Không reset/clean/checkout lại để làm sạch cây làm việc và không xóa dữ liệu/volume.
- Không đọc/in secret từ `.env`. Lượt này chỉ đọc cấu hình source và file mẫu/public khi cần, không đọc giá trị secret.

Quy trình repository nằm trong [AGENTS.md](../AGENTS.md). Nó yêu cầu `docs/workflow.md`, nhưng file này **không có** sau khi tìm cả hidden. Hồ sơ 004–006 đã dùng AGENTS.md và yêu cầu implement trực tiếp; không tái dựng workflow hoặc tài liệu 001–003 từ trí nhớ. Inventory hiện có hồ sơ 004, 005, 006; tên feature 001–003 trong handoff cũ là lịch sử.

Backend dùng feature packages, JPA entity + DTO, controller mỏng, transaction ở service, Flyway là nguồn schema, giữ `ddl-auto=validate`. Frontend để HTTP trong services, types rõ, state form gần luồng sở hữu; cleanup listener/timer/layer. Không thêm abstraction hoặc module ngoài acceptance criteria. Trao đổi tiếng Việt; không sử dụng sub-agent nếu không được yêu cầu rõ trong session/instructions hiện hành.

## 4. Môi trường và cấu hình

| Thành phần | Trạng thái đã đối chiếu |
| --- | --- |
| Backend | Spring Boot **4.1.1**, Java target **26**, Maven wrapper; Spring MVC, JPA, Validation, Lombok, Flyway, PostgreSQL |
| Frontend | React 19, TypeScript 7, Vite 8, Leaflet 1.9, lucide-react; lệnh lint dùng **oxlint** |
| Runtime đã dùng khi test | Java **26.0.1**, Node **22.20.0**, Docker Engine **29.6.1**; cần kiểm tra lại availability ở session mới |
| Node yêu cầu | `package.json` yêu cầu &gt;=22.12.0; `.nvmrc` định hướng **24** |
| Database | PostgreSQL **17**; application schema `vehicle_tracking`; Testcontainers **1.20.6** theo pom |
| Browser verification | Headless Microsoft Edge (`channel: 'msedge'`), Playwright **1.63.0** trong thư mục verification 004 |
| Vite config | File là `vehicletracking-frontend/vite.config.js`, không phải `.ts`; chỉ cấu hình React plugin, không có proxy API |
| Compose | Chỉ PostgreSQL và pgAdmin, không chạy backend/frontend; Postgres port mặc định 5432, pgAdmin 5050 |

Evidence: [pom.xml](../vehicletracking-backend/pom.xml), [package.json](../vehicletracking-frontend/package.json), [vite.config.js](../vehicletracking-frontend/vite.config.js), [compose.yaml](../compose.yaml), [application.yaml](../vehicletracking-backend/src/main/resources/application.yaml).

Tên biến cần biết, **không cần đọc giá trị trong .env để hiểu thiết kế**:

- Backend DB: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Compose dùng nhóm `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`; phải cấu hình hai phía tương ứng khi chạy dev.
- Routing: `HERE_ROUTING_ENABLED`, `HERE_API_KEY`, `HERE_ROUTING_BASE_URL`, các timeout routing. Mặc định disabled. Khi cần tạo tuyến mới bằng HERE phải cấu hình key backend hợp lệ; simulator chạy tuyến đã lưu không cần gọi lại HERE.
- Traffic: `HERE_TRAFFIC_ENABLED`, base URL/key/timeouts/TTL đã có cấu hình; source provider/cache/API/ETA nằm trong `vehicletracking-backend/.../traffic`, không đồng nghĩa đã xác minh HERE live.
- CORS: `APP_CORS_ALLOWED_ORIGINS` hoặc fallback `CORS_ALLOWED_ORIGINS`; default có localhost:5173 và 127.0.0.1:5173. Config `WebConfig` cho `/api/**`.
- Frontend: `VITE_API_BASE_URL` là origin/base backend, ví dụ `http://localhost:8080`; services tự nối `/api/v1`. Không đặt key HERE hoặc secret trong `VITE_*`. `vite-env.d.ts` chỉ khai báo type public này.
- 006 không yêu cầu env mới. `app.simulation.scheduling-enabled=false` là Spring property phục vụ test clock; mặc định true khi chạy ứng dụng.

**Cập nhật đối chiếu cấu hình tại HEAD** `d990d29`**:** `application.yaml#spring.config.import` đã nạp `.env` bằng Spring Config Data, không cần thư viện dotenv. Khi chạy từ root repository hoặc thư mục backend, `.env` của backend được ưu tiên hơn `.env` ở root; biến môi trường của tiến trình/IDE có ưu tiên cao hơn các file. `APP_ENV_IMPORT` có thể thay danh sách import. File được đọc theo Java properties (`KEY=value` không bọc dấu nháy, không dùng cú pháp `export`), không nạp `.env` của frontend. Evidence: [application.yaml](../vehicletracking-backend/src/main/resources/application.yaml), [RouteEnvironmentImportTest](../vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/route/config/RouteEnvironmentImportTest.java), các test `backendEnvironment_overridesRootForBothLaunchDirectories` và `processEnvironment_overridesFiles`. Đây là đối chiếu source, không phải xác nhận cấu hình của tiến trình đang chạy.

Thông báo **“Routing service is currently disabled or unconfigured”** là lỗi cấu hình HTTP 503 / `ROUTING_UNAVAILABLE` từ [HereRoutingProvider#calculate](../vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/provider/HereRoutingProvider.java), trước khi gọi HERE: `here.routing.enabled=false` hoặc key null/blank. Chức năng tạo tuyến đã có; để gọi provider cần `HERE_ROUTING_ENABLED=true` và `HERE_API_KEY` hợp lệ ở backend, sau đó khởi động lại backend để nạp cấu hình. Không suy ra key sai hay thiếu quyền HERE từ riêng thông báo này; lỗi credential do HERE trả có thông báo khác. Không đọc/in secret để debug và không đặt key trong `VITE_*`.

## 5. Cấu trúc và luồng dữ liệu

Trong các mục dưới đây:

- **B/** = `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/`.
- **T/** = `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/`.
- **F/** = `vehicletracking-frontend/src/`.
- Tên sau `#` là symbol cần đọc, không phải tên file khác.

```text
Trạm active → RouteService → HERE server-side → Route snapshot
                                              ↓
Xe active + route + giờ xuất phát → TripService → Trip + TripStop snapshots
                                              ↓
HTTP GPS → TelemetryService ← SimulationService ← Scheduler clock
                 ↓                  ↓
        history + latest        SimulationRun checkpoint
                 └──────────────┬──────────────┘
                    DB transaction đã commit
                                ↓
                 OperationsSnapshotService → SSE full snapshot
                                ↓
            useLiveOperations → map + simulator + trạng thái chuyến
```

Các package nghiệp vụ backend: `station`, `route`, `vehicle`, `trip`, `telemetry`, `simulation`; `config` chứa cấu hình chung. Không tạo toàn bộ controller/entity/service ở cấp global.

### 5.1. Trạm và tuyến đã có trước mốc 004

`B/station/service/StationService#create/update/delete/findAll/findById` dùng JPA, xóa mềm bằng active=false; list/detail chỉ active. Tên tối đa 150, địa chỉ 255, latitude \[-90,90\], longitude \[-180,180\], radius 10–1000 m. DTO: `StationUpsertRequest`, `StationResponse`. UI: `F/services/stations.ts`, `useStationWorkspace`, `StationPanel`, `StationDrawer`. Tìm kiếm hiện theo tên/địa chỉ.

START/STOP/END là vai trò của **lần xuất hiện trạm trong tuyến**, suy ra từ thứ tự; không phải thuộc tính cố định của station. Tuyến A→B→A hợp lệ, hai điểm liên tiếp cùng station bị từ chối.

`B/route/service/RouteService#create` validate 2–50 điểm active, dwell 0–3600 giây, đầu/cuối dwell=0; tạo RoutingWaypoint theo thứ tự, gọi provider **ngoài transaction database**, cộng metrics rồi `RoutePersistenceService#persistRoute` ghi transaction. `HereRoutingProvider#calculate/buildUri` dùng RestClient/key backend, profile hiện CAR, lưu Flexible Polyline và section metrics; có phân loại lỗi provider và validation response.

`RouteDetailResponse#from` nhóm sections theo destinationStopSequence; tính arrival/departure offsets cộng travel và dwell; một leg có thể gồm nhiều section. Route snapshot gồm tên/tọa độ stop và geometry/metrics; **không snapshot radius ở route**, radius được snapshot khi tạo trip. Route đã lưu hiện chỉ create/list/detail, được coi là immutable trong 005–006.

`RoutingWaypoint` vẫn có file tại `B/route/provider/RoutingWaypoint.java` và đúng package; `RouteServiceTest` và `HereRoutingProvider` import nó. Chưa có báo lỗi cụ thể chứng minh import này hỏng. Nếu IDE báo unresolved ở session mới, đối chiếu JDK 26/Maven project trước khi sửa import hoặc nhân bản class. Lần compile 006 từng có cảnh báo deprecated API ở RouteService/Lombok nhưng **BUILD SUCCESS**, không phải lỗi compile.

### 5.2. Feature 005 — xe và lịch chuyến

`B/vehicle/service/VehicleService`:

- Biển số tối đa 20 ký tự đầu vào, chữ/số/khoảng trắng/dấu chấm/gạch nối; chuẩn hóa hoa và bỏ dấu phân cách. Unique cả xe inactive, DB cũng có constraint.
- Tên tối đa 100; mô tả tùy chọn 255. Update giữ ID, xe inactive không sửa. DELETE là deactivate, retry deactivate idempotent.
- Ngừng dùng xe bị chặn nếu còn SCHEDULED/IN_PROGRESS. `VehicleRepository#findLockedById` pessimistic write dùng chung với các luồng trip/simulator.

`B/trip/service/TripService#create`:

- Xe active + route tồn tại có đủ stop và các station còn active + scheduledDepartureAt. Giới hạn năm 2000–2100, cho phép lịch quá khứ; không có scheduler tự khởi hành theo ngày giờ.
- Không gọi HERE. Copy stop sequence/name/coords/radius/dwell/offset và plannedArrivalAt/plannedDepartureAt; snapshot biển số. Geometry tham chiếu route immutable qua FK.
- Schedule baseline = scheduled departure + offsets. Sửa station/xe sau đó hoặc start trễ không đổi snapshot/lịch đã hứa.

Lifecycle `SCHEDULED → IN_PROGRESS → COMPLETED`, hoặc SCHEDULED/IN_PROGRESS → CANCELLED. `TripService#transition` khóa **trip → vehicle**, kiểm tra trạng thái, retry cùng trạng thái đích không đổi timestamp. Vehicle lock cùng **partial unique index một IN_PROGRESS/xe** bảo vệ đồng thời; không bỏ một trong hai lớp bảo vệ khi sửa.

`TripDetailResponse = {trip, stops, route}`; summary chứa planned/actual timestamps, stops giữ lịch kế hoạch, route là snapshot hình học. Instants của schedule và lifecycle được truncate microsecond để khớp PostgreSQL. UI nhập/hiển thị ngày giờ địa phương, gửi ISO Instant về backend. Chưa có edit/delete trip, tài xế hoặc lịch tuần lặp.

## 6. Feature 006 — cách implement hiện tại

### 6.1. Telemetry ingestion và persistence

`B/telemetry/service/TelemetryService` là nơi nhận mẫu cho GPS/simulator:

1. HTTP chỉ nhận source GPS; SIMULATOR do `SimulationService` gọi `ingestSimulator` nội bộ, có simulatedAt riêng.
2. Validate eventId UUID, ID dương, số hữu hạn, tọa độ, speed 0–500 km/h, heading \[0,360), accuracy 0–10000 m, recordedAt &gt;= năm 2000 và &lt;= clock server +30 giây.
3. Chuẩn hóa recordedAt microsecond. EventId cùng nội dung trả record cũ; khác nội dung bị 409. Kiểm tra lại duplicate sau lock để xử lý request đồng thời.
4. Khóa trip → vehicle; yêu cầu đúng xe active, trip IN_PROGRESS. GPS không được vào trip đã có simulation run; simulator không bắt đầu trên trip đã nhận GPS.
5. RecordedAt bằng/cũ hơn latest bị 409; không ghi history hoặc làm latest lùi. Mẫu hợp lệ lưu telemetry_samples và cập nhật vehicle_positions cùng transaction.

Ba đồng hồ phải giữ riêng: **recordedAt** là lúc đo theo wall clock, **receivedAt** là lúc server nhận, **simulatedAt** là thời điểm trong lịch giả lập tăng tốc. Không gửi đồng hồ giả lập tương lai vào recordedAt.

`TelemetrySampleEntity` lưu lịch sử; `VehiclePositionEntity` chỉ trỏ sample mới nhất của mỗi xe. Hiện chưa có API phân trang/history playback; bảng lịch sử là nền cho feature sau. Type frontend dùng `TelemetryPosition`, không dùng type `Vehicle` cũ chứa driver/traffic mock fields.

### 6.2. Geometry và simulator clock

`B/simulation/motion/FlexiblePolyline#decode` giải mã 2D, đọc/bỏ thành phần thứ ba nếu có, validate format/coords/truncation/overflow. `RouteMotion` chuẩn bị cumulative distances theo Haversine trên từng section, giữ start/end time và destination stop. `at(elapsed)` nội suy vị trí **theo khoảng cách**, tính heading và tốc độ section, progress theo quãng đường, nextStop/countdown và dwell.

Không dùng chỉ số điểm làm tỷ lệ quãng đường. Không chia khoảng cách thẳng tới trạm cho tốc độ GPS. Geometry hỏng/thiếu hoặc thời lượng không hợp lệ trả lỗi, không thay bằng đường thẳng. Code hiện chặn khoảng hở giữa hai section &gt;100 m và vận tốc geometry/time &gt;500 km/h; tổng thời lượng phải khớp route. Đây là policy code hiện tại cần cân nhắc khi test HERE route thật, không phải kết luận coverage provider đã kiểm chứng.

`SimulationRunEntity` lưu tripId unique, status, multiplier, elapsedSeconds, lastTickAt, updatedAt, createdAt, errorMessage, replacementTripId. Status: RUNNING, PAUSED, COMPLETED, STOPPED, FAILED.

`SimulationService` dùng `Clock operationsClock` từ `SimulationConfig` và route geometry cache tối đa 100 tuyến immutable:

- **play**: validate route trước khi đổi lifecycle, start trip nếu cần, tạo run hoặc resume paused, phát mẫu đầu. Retry running trả cùng run.
- **tick**: cộng delta wall time × multiplier, emit qua TelemetryService; cuối route clamp tiến độ, speed=0 và complete trip.
- **pause**: chốt thời gian đến lúc nhận lệnh, đổi PAUSED, phát speed=0. Tick paused không phát vị trí tiến tiếp. Resume không cộng thời gian nghỉ.
- **speed**: chỉ 1/5/10; chốt elapsed với multiplier cũ rồi đổi multiplier. SpeedKmh vẫn là tốc độ vật lý; đồng hồ chạy nhanh hơn.
- **stop**: hủy chuyến còn mở, giữ run/history và vị trí cuối.
- **reset**: kết thúc run/trip cũ nếu còn mở, tạo trip mới cùng xe/route, scheduledDepartureAt mới, run PAUSED. Lưu replacementTripId để retry trả cùng chuyến mới. Không xóa history hoặc kéo baseline cũ về đầu.
- **recover**: sau backend restart, RUNNING chuyển PAUSED và không cộng thời gian downtime. `recover` đã test bằng service/clock; toàn quá trình restart HTTP server chưa test.
- **fail**: run FAILED có thông báo; không tiếp tục tick vô hạn. Xe/trip còn mở cần xử lý dừng/hủy theo lifecycle.

`SimulationScheduler` khởi động sau ApplicationReadyEvent, một scheduler backend tick mỗi giây, shutdownNow khi đóng. Nó vẫn duyệt paused runs để nhận biết trip đã complete/cancel thủ công. Hiện giả định **một backend instance**; chưa có distributed leader/scheduler.

Fixture cần lưu ý: `T/trip/TripFixtures` dùng polyline một điểm cho test snapshot 005, **không đủ để chạy simulator**. Dùng `T/simulation/SimulationFixtures` cho tuyến vòng A→B→A có đường đi thật trong fixture, 20 + 4 dwell + 20 = 44 giây. Không sửa fixture 005 thành dữ liệu vận hành để che validation geometry.

### 6.3. Snapshot và SSE

`OperationsSnapshotService#snapshot` chạy read-only **REPEATABLE_READ**, đọc trips, latest positions, simulations thành DTO trong transaction; phản hồi chỉ gồm dữ liệu đã commit. Format: `{serverTime, positions, simulations, trips}`.

`OperationsStreamService`:

- Spring MVC `SseEmitter`, event name **snapshot**, ID serverTime ISO, retry 1000 ms.
- Gửi full snapshot khi subscribe và mỗi giây; heartbeat cũng chính là snapshot. Mỗi reconnect resync mới nhất, **không replay toàn bộ event bị lỡ** và không dùng Last-Event-ID để replay history.
- Timeout 600000 ms; completion/error/timeout bỏ client khỏi collection. Có `subscriberCount` phục vụ test cleanup.
- Scheduler đọc snapshot tách khỏi scheduler simulator; virtual-thread writers, mỗi client chỉ một write chờ, tránh hàng đợi tăng không giới hạn.
- `TelemetryController#stream` trả text/event-stream, no-store và `X-Accel-Buffering: no`.

Đây là thiết kế có source và compile được; **độ trễ, cleanup với servlet/network thật và hành vi disconnect cần HTTP test mới xác nhận**.

## 7. Schema và API để nối chức năng

### 7.1. Flyway migrations

Nguồn: `vehicletracking-backend/src/main/resources/db/migration/`.

| Version | File | Dữ liệu |
| --- | --- | --- |
| V1 | `V1__create_application_schema.sql` | Schema ứng dụng |
| V2 | `V2__create_stations_table.sql` | Stations, validation tọa độ/radius, active |
| V3 | `V3__create_routes_tables.sql` | Routes, route_stops, route_sections, snapshots |
| V4 | `V4__create_vehicles_and_trips.sql` | Vehicles, trips, trip_stops; unique biển số, partial unique running vehicle, state/time CHECK |
| V5 | `V5__create_telemetry_and_simulation.sql` | Telemetry history, latest positions, simulation runs; source/time data, FK, unique/check |
| V6 | `V6__create_trip_stop_checkins.sql` | Durable detector checkpoint, immutable ordered stop visits, provenance composite FK/checks |

Flyway + `ddl-auto=validate`, `open-in-view=false`. Không sửa migration đã chia sẻ/áp dụng. V6 đã chạy trong PostgreSQL Testcontainers; database phát triển người dùng sẽ áp dụng khi backend khởi động.

### 7.2. HTTP contract

Tất cả prefix `/api/v1`; controller code là nguồn chính xác. Không trả JPA entity trực tiếp.

| Method/path | Request / kết quả |
| --- | --- |
| GET `/stations`, GET `/stations/{id}` | Active StationResponse list/detail |
| POST `/stations`, PUT `/stations/{id}` | `{name,address?,latitude,longitude,checkinRadiusMeters}` |
| DELETE `/stations/{id}` | Xóa mềm trạm |
| GET `/routes`, GET `/routes/{id}` | Route summary list/detail snapshot |
| POST `/routes` | `{name,stops:[{stationId,dwellDurationSeconds}]}`; tính HERE + lưu route |
| GET `/vehicles`, GET `/vehicles/{id}` | VehicleResponse; list gồm cả inactive |
| POST `/vehicles`, PUT `/vehicles/{id}` | `{plateNumber,name,description?}` |
| DELETE `/vehicles/{id}` | Deactivate, 204 hoặc 409 nếu còn chuyến mở |
| GET `/trips?vehicleId=...` | TripSummaryResponse\[\], filter optional |
| GET `/trips/{id}` | `{trip,stops,route}` |
| POST `/trips` | `{vehicleId,routeId,scheduledDepartureAt}`; 201 + Location |
| POST `/trips/{id}/start`, `/complete`, `/cancel` | TripDetailResponse, lifecycle 005 |
| POST `/telemetry` | GPS payload bên dưới → TelemetryResponse |
| GET `/telemetry/snapshot` | OperationsSnapshot |
| GET `/telemetry/stream` | SSE event snapshot |
| POST `/trips/{tripId}/simulation/play`, `/pause`, `/stop`, `/reset` | SimulationResponse; reset trả tripId mới |
| POST `/trips/{tripId}/simulation/speed` | `{multiplier:1}` hoặc 5 hoặc 10 → SimulationResponse |
| GET `/trips/{tripId}/check-ins` | Ordered `TripCheckInsResponse` gồm revision, target, awaitingExit và visits |
| GET `/traffic/flow?west=...&south=...&east=...&north=...` | HERE Traffic flow envelope theo viewport |
| GET `/traffic/incidents?west=...&south=...&east=...&north=...` | HERE incidents envelope theo viewport |
| GET `/trips/{tripId}/eta` | ETA từng stop còn lại, source/status/freshness/affected segments |

GPS payload có `eventId` UUID, `vehicleId`, `tripId`, `recordedAt` ISO Instant, `latitude`, `longitude`, `speedKmh`, `heading`, `accuracyMeters`, `source:"GPS"`. Client gửi SIMULATOR bị 400. Validation 400, missing 404, business conflict/old packet/duplicate collision 409; route provider còn có 422/502/503/504 theo `RouteExceptionHandler/RouteErrorCode`.

`SimulationResponse` gồm id, tripId, status, multiplier, elapsedSeconds, durationSeconds, simulatedAt, updatedAt, errorMessage, replacementTripId, frame và field additive `traffic` (source/status/nextStopEtaSeconds/observedAt/fetchedAt/blocked/warning). Frame gồm tọa độ, heading, speedKmh, progressPercent, nextStopSequence, nextStopEtaSeconds, dwelling, finished; có thể null khi geometry/run lỗi. Không diễn giải nextStop/progress là actual check-in.

## 8. Frontend Map-First và quyền sở hữu state

| Thành phần | Trách nhiệm / file |
| --- | --- |
| Shell | `F/App.tsx` mount `MapComponent`; `F/workspace.css` layout dark floating/full viewport |
| Navigation/panels | `components/operations/ModeBar`, `SimulatorPanel`, `AlertStream`; compact layout dùng một panel/sheet |
| Station | `hooks/useStationWorkspace`; `StationPanel`, `StationDrawer`, `ConfirmStationDelete` |
| Route | `RouteWorkspace`, `RoutePanel`, `RouteDrawer`, `SortableStopList`; HTTP `services/routes.ts` |
| Fleet | `FleetWorkspace`, `VehicleEditor`, `TripEditor`, `TripDetailPanel`, `FleetConfirmDialog`, `hooks/useFleetWorkspace` |
| Realtime | `hooks/useLiveOperations` + `services/operations.ts` + `types/operations.ts` |
| Simulator | `hooks/useSimulator` giữ trip selection/detail/local command response; `SimulatorPanel` trình bày/tương tác |
| Map camera/markers | `useMapCamera`, `useCompactLayout`, `useVehicleMarkers`; services không nằm trong marker callbacks |

Những ràng buộc cần giữ khi thay UI:

- Bản đồ Leaflet được giữ mounted; drawer/workspaces dùng hidden để giữ draft khi chuyển mode/thu panel. Đừng biến MapComponent thành chỗ gọi mọi API/form logic.
- Route reorder hỗ trợ kéo handle, phím Space/Enter + ↑/↓/Esc và nút. Occurrence ID ổn định bằng UUID, không dùng stationId làm key vì tuyến vòng có trạm lặp. Draft chỉ có marker, submit mới tính/lưu HERE.
- `useMapCamera` đo các panel qua data-map-edge, ResizeObserver/RAF, fit/focus tránh vùng che. Trạm chọn vị trí trên map phải quay về form/sheet đúng trạng thái. Giữ cleanup.
- Route hiển thị có `editorRoute`, `tripRoute` và route từ simulator detail. Ở mode simulation, nếu đã chọn trip thì chờ đúng detail rồi vẽ route đó; không lôi route cũ vào lúc đang load. Route editor giữ state độc lập.
- `useFleetWorkspace` dùng abort/token cho detail, busyRef chống double submit; 006 hợp nhất trip summaries từ stream, tránh trạng thái cũ làm lùi terminal/IN_PROGRESS và không làm mất draft. Vehicles catalog chưa trở thành kênh CRUD đồng bộ realtime giữa các tab.
- `useLiveOperations` GET snapshot + EventSource, bỏ snapshot cũ hơn serverTime đã nhận; kết nối lại giữ dữ liệu cuối. Sau 5 giây không nhận message chuyển reconnecting; timer client chỉnh theo offset serverTime cho freshness.
- `useSimulator` abort detail khi selection đổi, giữ local command response mới hơn stream theo updatedAt, khóa selection/mutation khi busy. Reset chọn tripId mới từ response. Reload trang phải chọn lại trip trong dropdown; run vẫn ở backend.
- `useVehicleMarkers` dùng TelemetryPosition, DOM tooltip textContent, giữ marker/icon DOM thay vì dựng lại mỗi tick. Cập nhật tọa độ/heading/state tại chỗ; follow chỉ pan khi tọa độ thay đổi. Kéo map tắt follow; remove marker/listener khi không dùng.
- GPS quá 15 giây = vị trí cũ, quá 60 giây = mất tín hiệu. Paused/terminal có nhãn riêng; marker có source GIẢ LẬP/GPS, giờ ghi nhận và tốc độ. Chỉ có danh mục xe không có nghĩa đã có tọa độ.
- Stop/reset dùng native dialog có confirmation/Escape và hiển thị lỗi; control simulator bị khóa khi stream chưa live. Traffic injector vẫn disabled kèm lý do chưa áp dụng giao thông thực tế.

Component cũ `TrackingPanel`, `VehicleDrawer`, `SimulatorControls`, type `Vehicle` trong `types/vehicle.ts` vẫn còn vì không xóa ngoài phạm vi, nhưng không đại diện luồng realtime hiện tại. `services/hereTraffic.ts` chưa có consumer live/backend endpoint tương ứng. Không nối mock cũ vào map để làm demo rồi gọi là dữ liệu thật.

## 9. Bằng chứng kiểm thử — đọc đúng mốc

| Mốc | Lệnh / phạm vi | Kết quả đã có |
| --- | --- | --- |
| 004 layout | Lint/tsc/build + browser Map-First | Đã kiểm tra; revision cũ có bộ ảnh riêng. Source nghiệp vụ backend không thay trong layout |
| 005 full backend | `mvnw.cmd test` | **118 tests, 0 failures/errors/skipped**, 2026-09-13 23:42:59 +07 |
| 006 production compile | `mvnw.cmd -DskipTests compile` | **BUILD SUCCESS**, 2026-09-14 00:08:29 +07; đây không compile test HTTP mới thêm sau |
| 006 focused backend | `mvnw.cmd -Dtest=RouteMotionTest,OperationsIntegrationTest test` | **24 tests, 0 failures/errors/skipped**, 00:11:55 +07 |
| 006 frontend + 007 UI | lint, tsc --noEmit, build (Node v24.16.0) | Exit 0, không lint warning, **1889 modules**; build JS 459.19 kB/gzip 137.37 kB, CSS 105.25 kB/gzip 23.64 kB |
| 006 browser simulator | `live-browser.mjs` không có VERIFICATION_API | **10 nhóm**, pageErrors=\[\], Node API fixture có SSE socket thật; explicit empty check-in state; kết quả 2026-09-14 |
| 006 browser regression 004 | `regression-004.mjs` | **19 nhóm lịch sử**, pageErrors=\[\], fixture; lần rerun hiện tại dừng ở locator form trạm, không dùng làm pass evidence |
| 006 browser regression 005 | `regression-005.mjs` | **11 nhóm lịch sử**, pageErrors=\[\], fixture; lần rerun hiện tại thiếu summary trip, không dùng làm pass evidence |
| 007 backend/full + HTTP/SSE | `bash ./mvnw -q test` | **178 tests, 0 failures/errors/skipped**, PostgreSQL Testcontainers V1→V8; check-in slice 12 integration + 2 HTTP/SSE |
| 007 browser fixture/Spring | `docs/features/007-automatic-station-check-in/verification/live-browser.mjs`; `-Dverification.browser006=true` | Fixture **10 checks**, Spring/PostgreSQL **9 checks** (ordered visits), `pageErrors=[]` |
| 008 provider/cache/controller/matching/tile/position/config-error | `HereTrafficProviderTest,TrafficQueryServiceTest,TrafficControllerTest,TrafficRouteMatcherTest,RoutePositionMatcherTest,TrafficEtaServicePolicyTest` | **20 tests pass**; backend production compile pass; live upstream spike flow/incidents/tile HTTP 200; Google traffic overlay đã thay bằng HERE tile proxy; chưa chạy browser/Spring end-to-end |
| 008 frontend | Node v24.16.0: lint, tsc, build | Exit 0; không có lint warning; build pass |

**Mốc 008:** các test deterministic ở trên pass. Lần thử `./mvnw test` hiện không thể lặp lại mốc 155 vì JDK 26 không attach được Byte Buddy Mockito và Docker PostgreSQL không khả dụng; không dùng kết quả đó để kết luận regression. Frontend lint/tsc/build hiện pass bằng Node v24.16.0.

Evidence:

- 005 backend log, 005 verification.
- 006 compile log, 006 focused log, 006 verification.
- Simulator fixture results, Regression 004 results, Regression 005 results.
- Ảnh desktop simulator, Panel 320 px. Ảnh và failure.png từ lần lỗi trước không thay cho results cuối.

178 test backend trong lần chạy gần nhất gồm toàn bộ hồi quy hiện tại, `RouteMotionTest` 14, `GeofenceCrossingTest` 3 và `OperationsIntegrationTest` 12 (thêm GPS POINT→SEGMENT và simulator ROUTE_TRACE ordered visits), cùng HTTP/SSE check-in. Testcontainers đã chạy migrations V1–V8 và Hibernate validate.

Bộ browser fixture lịch sử gồm 39 nhóm (hai tab, pause clock, tốc độ 5×/10×, reset/complete, follow, 409 recovery, GPS stale/offline, disconnect khóa lệnh/reconnect, mobile 320/390 px); hồi quy còn 1024/768/landscape và trạm/tuyến/xe/chuyến. Cử chỉ cảm ứng vật lý, HERE live, thiết bị GPS thật và production load chưa được kiểm chứng.

## 10. Việc còn lại sau feature 007

### 10.1. Điểm cần hoàn tất

Core 007 đã chạy được bằng Maven/Docker. Browser verification riêng cho 007 đã có wrapper và xác nhận visits; còn thiếu load benchmark 10×50 stop/2 tab và các edge/fault-injection case trong test-plan. Đây là phần cần làm để chuyển trạng thái `Implementing` → `Verified`, không phải lỗi runtime đã xác định.

Khi đổi máy, cài Node ≥22.12 rồi chạy lại frontend build và browser verification riêng cho 007. Không cần HERE key để kiểm tra check-in.

### 10.2. Test HTTP đã chạy

File: [OperationsHttpIntegrationTest.java](../vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/simulation/OperationsHttpIntegrationTest.java).

- `@SpringBootTest(webEnvironment=RANDOM_PORT)` + PostgreSQL 17 Testcontainers, HERE routing/traffic disabled, CORS cho 127.0.0.1:5173; scheduler thật bật mặc định.
- `httpValidationSourceAndLifecycleContracts`: validation payload, cấm source giả, play/pause/speed/stop/reset và conflict.
- `twoStreamsReceiveCommittedStateAndReconnectResyncs`: hai HTTP SSE subscribers nhận committed positions, reconnect với Last-Event-ID cũ, PAUSED snapshot, đóng stream và subscriberCount về 0; in `latency_ms` đo từ lệnh play tới hai snapshot.
- `verification.browser006=true` mở phần extension: tạo fixture riêng, ProcessBuilder gọi Node browser script, truyền `VERIFICATION_API` (bao gồm /api/v1) và `VERIFICATION_TRIP`, timeout 180 giây.
- Test đã compile/run trong full suite; endpoint check-ins trả revision/visit và snapshot/SSE chứa mảng `checkIns`.

### 10.3. Thứ tự tiếp tục ở session mới

Đầu tiên đọc AGENTS, báo cáo này và evidence 007, rồi kiểm tra working tree/runtime; không làm sạch cây file.

```powershell
Set-Location D:\vehicletracking
git status --short
java -version
node --version
docker version
```

Nếu Maven/Docker được phép, chạy HTTP trước, lưu log mới để giữ evidence cũ:

```powershell
Set-Location D:\vehicletracking\vehicletracking-backend
.\mvnw.cmd '-Dtest=OperationsHttpIntegrationTest' test *> ..\docs\features\006-telemetry-simulator\backend-http-resume.log
$LASTEXITCODE
```

Nếu lỗi, sửa trong phạm vi 006 rồi chạy lại test liên quan. Không che assertion hoặc nới timeout đơn thuần để tuyên bố đạt. Test mới có thể lộ vấn đề HTTP/CORS/serialization, executor/stream cleanup hoặc giả định fixture; chưa có lỗi runtime cụ thể được xác nhận cho các phần này.

Chuẩn bị dev server frontend cho browser, kiểm tra port trước vì server có thể vẫn chạy từ session cũ:

```powershell
Set-Location D:\vehicletracking\vehicletracking-frontend
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Không mặc định process/session ID công cụ cũ còn dùng được. Nếu port đang phục vụ đúng app, tái sử dụng; không tự kill server không rõ chủ sở hữu. Câu lệnh dev chạy dài, dùng terminal riêng hoặc background helper phù hợp; trên Windows helper nền phải ẩn cửa sổ theo quy tắc môi trường.

Browser script import Playwright từ `docs/features/004-operations-layout/verification/node_modules/playwright/index.mjs`. **Hiện có dependency 1.63.0 nhưng không có package.json riêng ở thư mục verification 004.** Khi đổi máy mà thiếu node_modules, cài lại đúng vị trí, không thêm test runner vào package frontend chỉ để chạy evidence:

```powershell
Set-Location D:\vehicletracking
npm.cmd install --prefix docs/features/004-operations-layout/verification --no-save playwright@1.63.0
```

Lệnh cài trên là hướng dẫn phục hồi, chưa chạy lại trong lượt bàn giao. Browser script dùng Microsoft Edge đã cài. Sau đó chạy extension qua Java test để nó tự tạo backend/DB tạm đúng port:

```powershell
Set-Location D:\vehicletracking\vehicletracking-backend
.\mvnw.cmd '-Dtest=OperationsHttpIntegrationTest' '-Dverification.browser006=true' test *> ..\docs\features\006-telemetry-simulator\backend-live-browser-resume.log
$LASTEXITCODE
```

Script live-browser.mjs rewrite API URL từ app sang instance test; không cần sửa `.env`. Với env do Java truyền, nó gọi Spring/PostgreSQL thật trong Testcontainers và ghi `artifacts/live-test-database/`. **Chạy trực tiếp không env chỉ khởi động fixture-server.mjs** và ghi `artifacts/fixture/`; tên “live-browser” không phải bằng chứng chế độ live đã chạy.

Khi HTTP/browser đạt, chạy full suite hiện tại:

```powershell
Set-Location D:\vehicletracking\vehicletracking-backend
.\mvnw.cmd test *> ..\docs\features\006-telemetry-simulator\backend-full-resume.log
$LASTEXITCODE
```

Nếu có thay đổi frontend khi sửa bug hoặc cần xác nhận cuối:

```powershell
Set-Location D:\vehicletracking\vehicletracking-frontend
npm.cmd run lint
.\node_modules\.bin\tsc.cmd --noEmit
npm.cmd run build
```

Các script hồi quy tương thích 006, chạy từ root khi dev server đang mở:

```powershell
node docs/features/006-telemetry-simulator/verification/live-browser.mjs
node docs/features/006-telemetry-simulator/verification/regression-004.mjs
node docs/features/006-telemetry-simulator/verification/regression-005.mjs
```

Sau pass: ghi số test thực tế/lệnh/exit code, latency đo được và giới hạn vào verification 006; cập nhật spec/plan, PROJECT_PROGRESS, PROJECT_HANDOFF và báo cáo này. Chỉ đánh dấu 006 hoàn thành khi AC đã có bằng chứng. Nếu còn môi trường chặn, ghi rõ test chưa chạy; không biến giới hạn môi trường thành success hoặc assertion failure.

### 10.4. Kinh nghiệm chạy và debug đã có

- PowerShell `Get-Content -Encoding UTF8` đọc source/docs; log Maven tạo bằng `*>` ở máy này là UTF-16, đọc `Get-Content -Encoding Unicode ... -Tail 30`.
- Maven cache `.m2` và Docker ở ngoài workspace sandbox; các lần trước cần quyền thực thi mở rộng. `javap` đọc jar từng trả AccessDenied kèm một phần output; đó không phải compile/test success.
- PostgreSQL test từng từ chối alias timezone **Asia/Saigon** do JVM Windows. `pom.xml` Surefire đặt `user.timezone=UTC` riêng JVM test; không đổi timezone runtime/database người dùng. `src/test/resources/docker-java.properties` hiện có `api.version=1.44`.
- Java Instant nanosecond so với PostgreSQL microsecond từng làm retry timestamp lệch; TripService và telemetry đã normalize precision. Không bỏ fix vì thấy test time quá chặt.
- Tests 005 từng sửa giả định “ngày cố định luôn ở tương lai”; clock tests cần thời gian kiểm soát, tránh phụ thuộc ngày máy.
- Playwright dùng `goto/reload({waitUntil:'domcontentloaded'})`; chờ load/networkidle có thể vướng Google tiles/fonts bên ngoài. Screenshot thiếu tiles không tự chứng minh API hỏng.
- Nhiều panel hidden vẫn nằm trong DOM; locator lỗi/confirmation cần đúng vùng/visible. Không đổi source để thỏa selector trùng ở panel ẩn.
- Multi-section/loop geometry, pause/resume, reset retry, hai client gửi command đồng thời và stream reconnect là các điểm cần giữ test khi sửa simulator.
- Một số test HTTP dùng virtual-thread reader có thể chờ I/O. Nếu có hang, cần kiểm tra cleanup/đóng InputStream/timeout/process browser; đây là điểm cần review khi chạy, không phải lỗi đã xác nhận.
- Luồng stream hiện full snapshot mọi trip/run mỗi giây; chưa có load test, không cam kết scale production. Tách GPS source khỏi simulator không đồng nghĩa có authentication thiết bị.

## 11. Cách dùng ứng dụng sau khi backend được kiểm tra

Đây là thao tác dev thực tế, khác bộ test fixture. Backend khi start sẽ chạy Flyway vào DB đã cấu hình, nên dùng đúng database chủ định; không dùng DB người dùng để thay Testcontainers evidence.

1. Mở PostgreSQL dev qua compose/setup hiện có; kiểm tra env của tiến trình backend đã được cấu hình đúng.
2. Chạy backend bằng IDE hoặc `mvnw.cmd spring-boot:run`, frontend bằng `npm.cmd run dev`. Nếu chưa có tuyến, bật HERE routing/key ở backend theo setup để tạo tuyến thật.
3. **Tuyến & trạm**: tạo station, chọn các stop và dwell, tính/lưu tuyến. Chỉ route đã có geometry hợp lệ mới chạy mô phỏng.
4. **Theo dõi → Đội xe**: thêm xe; **Chuyến đi**: chọn xe/tuyến/giờ xuất phát.
5. **Mở mô phỏng chuyến này** hoặc mode **Mô phỏng**, chọn trip và phát. Nút **Khởi hành** trong lifecycle 005 chỉ đổi status; nút **Phát mô phỏng** của 006 mới điều khiển engine.
6. Chọn cùng trip ở tab thứ hai. Xem position/speed/clock, pause/resume, 1/5/10, follow. Pause không phải hoàn thành chuyến; stop hủy chuyến; chạy lại tạo chuyến mới.
7. Bật `Lớp bản đồ → Giao thông trực tiếp` để xem flow/incidents theo viewport. Chọn trip để xem ETA từng stop, source/freshness và trạng thái fallback/blocked; AlertStream hiển thị notification 009 khi evaluator tạo cảnh báo. Check-in vẫn do backend xử lý qua telemetry accepted.

## 12. Các feature tiếp theo

### 007 — Automatic check-in — đang triển khai, core đã có

Source nằm tại `vehicletracking-backend/.../checkin`, migration V6 và frontend `types/checkin.ts`/`services/checkins.ts`/`hooks/useTripCheckIns.ts`. Dùng telemetry accepted làm đầu vào chung GPS/SIMULATOR, xử lý trong backend; tránh mỗi tab tự check-in. Evidence: 007 evidence, walkthrough: 007 walkthrough.

- Visit key `(trip_id, stop_sequence)` vì A→B→A là ba occurrence; unique DB bảo vệ retry/concurrency. Dùng radius snapshot của TripStop, không radius station hiện tại.
- Check stop tiếp theo theo thứ tự; xe đi ngang là đủ, không bắt buộc tốc độ=0/dừng. Cần tách suy luận đi qua với phục vụ hành khách nếu sau này có yêu cầu.
- Xét đoạn nối hai GPS samples để không bỏ sót vùng nhỏ khi xe nhanh, nhưng phải giới hạn gap/accuracy/tốc độ hợp lý; không nội suy thẳng qua trạm sau mất GPS dài.
- Ghi actualArrivalAt, source và evidence sample; timestamp nội suy phải đánh dấu suy ra. Điểm đầu check-in đúng policy, không tự hoàn thành điểm cuối trùng tọa độ đầu trên tuyến vòng. Hysteresis nếu thêm check-out.
- Đã test GPS POINT→SEGMENT, simulator ROUTE_TRACE A→B→A, ordered visits, GET/API snapshot/SSE, retry và browser wrapper 007 với 3 visits. Còn load/edge/fault-injection evidence trước khi chuyển `Verified`.

### 008 — HERE Traffic, ETA và simulator theo traffic — source đã triển khai, verification pending

Đã có provider HERE v7 `/flow` và `/incidents` server-side, raster tile proxy v3, envelope freshness/cache, endpoint bbox, matcher geometry/hướng, ETA theo vị trí telemetry/stop/check-in/dwell/fallback/blocked, simulator traffic rate/blocked và metadata additive, frontend layer/ETA UI. Xem 008 evidence và 008 walkthrough. Không đưa key lên Vite.

- Live spike HERE tại khu vực TP.HCM đã xác minh quyền/coverage ở mức upstream cho flow, incidents và raster tile; browser → Spring → HERE vẫn chưa xác minh. Tài liệu provider là tham khảo, không thay live evidence.
- DTO nội bộ đã có geometry/hướng, speed/đơn vị, jamFactor, confidence, observedAt/fetchedAt; incidents có ID/type/severity/location/hiệu lực.
- Match phần đường còn lại theo geometry/hướng; cache/quota/TTL, timeout/rate-limit, last-known có tuổi dữ liệu; provider lỗi không được hiển thị thông thoáng.
- ETA qua remaining sections + dwell đã hoàn thành, stop qua rồi hiển thị actual; không dùng khoảng cách thẳng/tốc độ GPS tức thời. Baseline 005 giữ nguyên, ETA động là giá trị khác.
- Simulator/operations có metadata traffic additive nguồn HERE_LIVE/LAST_KNOWN/ROUTE_SNAPSHOT; đồng hồ mô phỏng tách wall clock traffic, tốc độ tiến độ được điều chỉnh theo ETA rate và closure giữ elapsed/speed ở 0. Evaluator 009 có thể tạo revision từ vị trí telemetry và các stop còn lại.
- Test fixture provider/cache/controller/matching/tile pass; live upstream đã xác minh flow/incidents/tile HTTP 200 nhưng browser/Spring/load chưa xác minh. Google traffic overlay đã được thay bằng HERE raster tile proxy; nút kẹt xe/tai nạn/công trường vẫn là injector disabled, không trà trộn incidents thật.

### 009 — Đổi lộ trình/lịch trình và thông báo (source đã triển khai)

- Trigger hiện tại: closure còn hiệu lực hoặc trễ ≥600 giây và ≥30% trên phần đường chưa đi; yêu cầu hai `trafficFetchedAt` khác nhau. Cooldown/fingerprint 300 giây. Mỗi telemetry accepted (GPS hoặc simulator) tự kích hoạt evaluator; cache timestamp không bị tính lại.
- Tính từ vị trí xe qua tất cả stop bắt buộc còn lại, tạo trip route revision mới; không sửa route snapshot gốc hoặc bỏ stop để rút ngắn đường.
- Lưu baseline/revised schedule riêng. Không có đường tốt/khả dụng hơn thì giữ phương án hiện hành và thông báo bị chặn; ETA đổi có thể cần thông báo dù geometry không đổi.
- Notification trong ứng dụng: trip, lý do/incident, stop bị ảnh hưởng, ETA cũ→mới, createdAt/revision/severity/readAt. Revision + notification được ghi trong transaction và xuất qua snapshot SSE.
- Idempotency theo fingerprint/dedupe key, cooldown 5 phút và checkpoint bền; reload đọc lại lịch sử. Chưa thêm SMS/email.
- Nghiệm thu end-to-end simulator → traffic fixture → ETA đổi → revision → alert; hai client cùng revision, không spam, stop đã xong không tính lại, timeout không mất route.

Route versioning/edit lifecycle, auth/quyền điều hành, device onboarding, audit, retention/pagination, HTTPS/deploy/backup và scale nhiều instance chỉ đưa vào kế hoạch khi cần hoặc được yêu cầu; hiện chưa triển khai. Những mục này không thay mục tiêu sản phẩm 007–009.

## 13. Bản đồ tài liệu và file cần đọc

| Tài liệu | Vai trò |
| --- | --- |
| [SESSION_HANDOFF.md](SESSION_HANDOFF.md) | Điểm bắt đầu session mới, trạng thái tổng hợp hiện tại |
| [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) | Đối chiếu bảy yêu cầu và lộ trình 007–009 |
| [PROJECT_HANDOFF.md](../PROJECT_HANDOFF.md) | Lịch sử bàn giao; mục 1–16 cũ, 17 layout 004, 18 feature 005, 19 feature 006; nhiều câu cũ đã lỗi thời |
| 004 design | Thiết kế Map-First được chọn |
| 004 implementation | Cách triển khai và evidence layout tại thời điểm 004 |
| 005 spec, 005 verification | Invariants xe/trip/snapshot và full tests mốc 005 |
| 006 spec, plan, test plan, verification | Phạm vi 006, source/test evidence và phần còn chờ |

`docs/features/004-operations-layout/artifacts/map-first/` hoặc prototype/dashboard results cũ không phải kết quả simulator 006. Ưu tiên `docs/features/006-telemetry-simulator/artifacts/` khi đánh giá UI hiện tại, giữ folder cũ làm lịch sử. Logs/images dưới docs hiện là file untracked; chuyển máy cần copy nếu chưa commit theo quyết định người dùng. Không chuyển secret trong báo cáo. Không chạy git push/force-add theo suy đoán.

## 14. Đoạn yêu cầu có thể dùng cho session mới

> Đọc AGENTS.md và docs/SESSION_HANDOFF.md trong D:\\vehicletracking, tiếp tục migrate V7 và verification integration/browser cho feature 009/008. Giữ nguyên working tree và không đọc/in secret, không commit/push. Chạy policy/compile, frontend lint/tsc/build và full Maven khi JDK agent/Docker PostgreSQL sẵn sàng; live HERE chỉ dùng key được cấp phép và không lưu raw key/response. Ghi số test/giới hạn thực tế vào evidence tương ứng.

## 15. Phạm vi lượt cập nhật báo cáo này

Lượt này đã triển khai source 008 và cập nhật evidence/walkthrough bằng fixture; không sửa migration hay gọi HERE live, không commit/push và không ghi secret. Full suite cần được xác nhận lại khi môi trường có Byte Buddy agent và Docker PostgreSQL.

## 16. Cập nhật 010 — Tooltip tuyến, ngày 2026-09-14

Người dùng đã yêu cầu triển khai thông tin khi rê chuột trên tuyến. Đã hoàn tất frontend cho tuyến đã lưu đang hiển thị: tên tuyến/chặng, chiều dài và thời gian snapshot, tốc độ dòng xe/free flow/trạng thái, chiều dài đoạn traffic tương ứng, ước tính đi qua/chậm thêm, sự cố gần vị trí và nguồn/tuổi dữ liệu. Hover mở, click/tap/Enter ghim, ×/Escape đóng; tắt lớp hoặc pan/zoom đóng nội dung cũ. Chưa mở rộng sang mọi đường nền hoặc thêm vận tốc tùy chỉnh simulator.

Source: `components/route/RouteInspectionLayer.tsx` + `route-inspection.css`, `hooks/useRouteTraffic.ts`, `utils/routeInspection.ts`, `types/map.ts#TrafficFlowSegment.lengthMeters`, tích hợp lazy trong `MapComponent`. Dùng endpoint traffic có sẵn; không sửa backend/schema/.env. Cache theo ô quanh con trỏ, chống response cũ, matching geometry/hướng bảo thủ; trường hợp không khớp phải hiện chưa có dữ liệu. Không dùng một vận tốc cho toàn chặng. `workspace.css` sửa specificity mobile để control lớp bản đồ không có vùng bấm vô hình che tuyến.

Kiểm tra đã chạy: 10 unit test hình học/dữ liệu, 12 nhóm browser fixture (desktop/320 px, pin/keyboard/touch, stale/zero/closure/no-match/error/retry/race/cleanup), lint, TypeScript và build đều đạt. Browser cuối 22:32:06 +07, `pageErrors: []`. Đây là fixture, chưa xác minh tooltip qua Spring/HERE live. Chi tiết và lệnh tái chạy tại [verification 010](features/010-route-traffic-tooltip/verification.md).

Giữ các thay đổi có sẵn trong handoff và source. Hồ sơ 010 đang local/ignored theo rule `docs/` hiện tại; không force-add, commit hoặc push. Những nội dung trước mục này giữ các mốc lịch sử, không phải toàn bộ đều đã được chạy lại trong lượt 010.

### Điều chỉnh cuối 010 — theo dõi xe, thay thế phạm vi hover mọi đường

Người dùng đã rút yêu cầu xem thông số mọi đường nền: chỉ cần vị trí xe, vận tốc, ETA tới trạm dựa trên giao thông. Đã gỡ `components/traffic/RoadInspectionLayer.tsx` và flow theo viewport trong `hooks/useTraffic.ts`; map vẫn có marker xe/sự cố, tuyến đang chọn và tooltip tuyến xanh. Không khôi phục layer road chỉ vì thấy artifact/test cũ.

`components/operations/TripTrafficSummary.tsx` hiện dùng chung trong `SimulatorPanel` và thẻ `.live-follow` của xe được chọn trong `MapComponent`. Vận tốc lấy từ simulator frame hoặc GPS telemetry đúng ngữ cảnh, không lấy free-flow speed; ETA chỉ query chuyến được chọn qua `useTripEta` mỗi 10 giây. Thẻ hiển thị trạm kế tiếp, nguồn, tuổi cập nhật, thời gian đến, chậm thêm và các ảnh hưởng từ `TripEta.affectedSegments`. Tọa độ/time chi tiết thu trong disclosure. Nút “Xem vị trí xe và tuyến đang chạy” định vị và bật theo xe. 1×/5×/10× vẫn là tốc độ phát.

`utils/tripTraffic.ts#tripTrafficView` giữ ETA/trạm/nguồn cùng response, chọn metadata simulator mới hơn khi còn khả dụng, không fallback countdown hữu hạn khi bị chặn; phục hồi dùng response mới. Metadata UNAVAILABLE không được đè ETA blocked có sẵn. Khi chỉ có frame, nguồn phải là ROUTE_SNAPSHOT; trường hợp trạm đã kết thúc không lấy countdown cũ. Không sửa backend/migration/.env trong lượt này.

Evidence mới: 9 unit `verification/trip-traffic.test.mjs`; 12 nhóm `tracking-browser.mjs` với SSE fixture, ETA congestion/accident/construction/blocked/stale/error/retry, GPS riêng, follow, mobile 320 px, đổi chuyến bỏ response cũ và bỏ chọn dừng polling; 10 nhóm lifecycle 006 hai tab (pause/continue/5×/10×/reset/reconnect); 12 nhóm route regression; lint/tsc/build đạt. `tracking-live-readonly.mjs` trên Spring/Vite thật: 1 marker, speed/ETA visible, 0 flow query theo viewport, ETA HTTP 200 ROUTE_SNAPSHOT/nextStop null vì chuyến hiện có đã kết thúc. Đây không phải nghiệm thu chuyến chạy qua tai nạn/HERE thực tế. Chi tiết tại [verification 010](features/010-route-traffic-tooltip/verification.md).

Camera: giữ wrapper `.live-follow` mounted (ẩn khi không chọn xe), `data-map-edge="top"` cho `useMapCamera` quan sát kích thước; mobile cao tối đa 45dvh. Không bỏ thuộc tính/wrapper này vì marker có thể nằm dưới thẻ khi follow hoặc đổi viewport. Browser đã kiểm tra hit target marker desktop/mobile và khi mở disclosure. Script 006 sửa một selector cũ “Bắt đầu” → “Tiếp tục” sau pause; artifacts fixture 006 là kết quả hồi quy mới.

Giới hạn backend đọc thấy, chưa sửa trong lượt chỉnh UI: `TrafficEtaService#calculate` chọn flow matching đầu tiên cho từng section và nhận incident theo geometry; chưa chứng minh tích phân speed từng road-link. `SimulationService#trafficMetadata` dùng `map(EtaStop::etaSeconds).findFirst()` có thể rơi vào catch khi ETA null (blocked), trả UNAVAILABLE; frontend hiện không để metadata này che kết quả blocked từ endpoint ETA. Nếu mở feature engine tiếp theo, cần kiểm thử/chỉnh policy này; không coi UI fixture là bằng chứng engine thực tế đã xử lý đầy đủ mọi sự cố.

## 17. Feature 011 — nhiều xe ở trạm đầu, điều khiển từng xe

Theo yêu cầu mới nhất, mở Mô phỏng sẽ hiển thị xe có chuyến chờ tại trạm bắt đầu, chưa cần bấm play. Các xe khác nhau có thể chạy đồng thời; chỉ panel điều khiển chọn một xe tại một thời điểm. Backend vốn có danh sách runs và scheduler lặp nhiều trip (`SimulationScheduler#tick`, `SimulationService#activeTripIds`); unique index V4 chỉ giới hạn một IN_PROGRESS trên cùng vehicle, không giới hạn toàn hệ thống.

Source mới: `src/utils/simulationFleet.ts` chọn một trip hiển thị mỗi vehicle, active ưu tiên rồi scheduled sớm nhất; không lấy completed/cancelled, không chiếm xe có chuyến GPS active. `src/hooks/useSimulationFleet.ts` tải trạm đầu từ TripDetail, tối đa 4 GET song song, cache/abort/error/retry. `src/components/operations/SimulationFleetLayer.tsx` nhóm preview cùng tọa độ, popup chọn từng xe, cleanup; `SimulationFleetList` tổng số xe/đang chạy, chọn xe, Xem tất cả, đi quản lý chuyến; danh sách thu lại khi chọn để ưu tiên điều khiển.

`MapComponent` tích hợp đội mô phỏng và lazy layer, tự fit đội khi vào mode; click xe chờ/danh sách nối `useSimulator.select` và tuyến/panel. `useVehicleMarkers` click xe SIMULATOR khi ở mode mô phỏng mở đúng trip; các marker chạy gần nhau có popup chọn xe, được cleanup khi đổi mode. `mapSnapshot` chỉ lọc marker lịch sử của xe có preview, không giả dữ liệu GPS. Bắt đầu xong tự bỏ preview, dùng vị trí telemetry. Khi rời mode gỡ preview nhưng không gửi pause/stop. `SimulatorPanel` trạng thái SCHEDULED chưa run hiển thị 0 km/h/trạm đầu/nhãn GIẢ LẬP, chỉ hiện ETA 010 sau khi chạy.

Cách dùng: tạo xe và chuyến chờ cho từng xe → Mô phỏng → click xe/trạm có badge → chọn xe → Bắt đầu → chọn xe khác để chạy tiếp. Play/pause/continue/1×/5×/10×/stop/reset là riêng trip được chọn. Xe có nhiều chuyến chờ lấy chuyến sớm nhất để đặt preview; những chuyến khác vẫn xem trong dropdown/quản lý chuyến. Không tự tạo trip hoặc teleport xe vận hành về trạm đầu. Chuyến cũ kết thúc phải Chạy lại có xác nhận hoặc tạo chuyến mới. Chưa thêm vận tốc km/h tùy ý.

Verification 011: 7 unit policy, 9 nhóm browser fixture (3 xe/2 trạm, 2 xe chạy đồng thời, overlap picker, pause/multiplier riêng, reload, lỗi/retry trạm đầu, mobile 320 px) pass; 12 nhóm hồi quy traffic 010 và 10 nhóm lifecycle hai tab 006 pass; lint/tsc/build pass, main 499.85 kB và fleet list/layer lazy riêng. Không đổi backend/migration/.env, không chạy Maven trong lượt frontend. Browser fixture không phải bằng chứng load lớn hoặc HERE nhiều xe live. GET-only app thật hiện có 0 fleet rows, 0 preview, 1 marker lịch sử; không ghi dữ liệu người dùng. [Hồ sơ 011](features/011-multi-vehicle-simulator/verification.md) và artifacts nằm local theo rule ignore docs; chưa commit/push.

### Sửa lỗi 011 ngày 2026-09-15 — nhiều xe nhưng chỉ một tuyến

Mục 17 phía trên là mốc triển khai ban đầu. Người dùng xác nhận hai xe đang chạy nhưng bản đồ chỉ có một tuyến. Nguyên nhân source: `MapComponent#plannedRoute` chỉ theo `simulator.detail.route`; bộ kiểm thử ban đầu chưa kiểm tra nhiều hình học khác nhau. Snapshot thật đầu lượt có #2/#3 RUNNING.

Đã bổ sung `components/operations/SimulationRoutesLayer.tsx`: tất cả tuyến đội mô phỏng hiện đồng thời bằng SVG, màu nhận diện theo xe; xe chọn cyan/dày. Click tuyến chọn xe, vùng đoạn chung chọn qua popup; keyboard/list cũng chọn được. `useSimulationFleet` tải TripDetail/route cho toàn bộ đội thay vì chỉ trạm đầu xe chờ, cache tripId:routeId/4 GET/abort, decode validate trọn tuyến, lỗi một tuyến có retry riêng và không mất tuyến khác. `SimulationFleetList` có chú giải màu/loading/lỗi. `MapComponent` fit toàn bộ geometry và xe, chỉ giữ stop đánh số của chuyến chọn; không auto-fit một tuyến khi đổi selection. Trong Mô phỏng route click mở điều khiển thay tooltip HERE của 010; tracking/editor vẫn giữ tooltip. Vận tốc/ETA xe và backend commands không đổi.

`mapSnapshot` bỏ SIMULATOR positions không thuộc đội hiện hành: chuyến hoàn thành/hủy không để lại marker không có tuyến trong Mô phỏng. Theo dõi vẫn có lịch sử gốc, GPS vẫn dùng telemetry riêng. Static geometry không dựng lại hay gọi GET mỗi SSE tick. Các sections vẽ riêng, không nối tắt qua đoạn thiếu; đoạn chung không dịch tọa độ.

Kiểm tra lượt sửa: `011/verification/routes-browser.mjs` 10 nhóm pass (hai xe đồng thời trên hai tuyến rẽ khác hướng, shared segment picker, selection/fit/keyboard/cache/reload/toggle/errors/retry/mobile/complete cleanup); browser011 cũ 9 nhóm + policy7 + traffic010 12 nhóm pass. Lint/tsc/build exit0; main501.00 kB có warning vượt500, lazy routes2.80 kB. Xem [evidence](features/011-multi-vehicle-simulator/verification.md), [ảnh hai tuyến](features/011-multi-vehicle-simulator/artifacts/routes/two-running-routes.png). Không chạy Maven tests; không sửa backend/schema/.env/commit/push.

Vite5173/backend8080 cũ dừng giữa lượt; đã khởi động lại. Wrapper Maven lỗi NullArray, Maven cài sẵn dùng được; JVM cần `-Duser.timezone=UTC` để tránh PostgreSQL từ chối Asia/Saigon. GET-only cuối lượt: #3 COMPLETED, #2 IN_PROGRESS/run PAUSED, một route và một marker, không có lỗi JS/POST từ smoke. Không tự play/reset hay tạo chuyến trong DB để kiểm chứng hai xe; muốn thấy hai chuyến hoạt động lại cần Tiếp tục #2 và dùng Chạy lại/tạo chuyến mới cho xe #3. Artifact live-readonly đã cập nhật theo dữ liệu mới, không còn là bằng chứng của con số 0 xe ở mốc ban đầu. Tile nền ngoài chưa được xác minh trong sandbox.
