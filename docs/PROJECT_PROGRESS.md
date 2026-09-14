# Tiến độ và kế hoạch Vehicle Tracking

> **Đổi session:** đọc [báo cáo bàn giao tổng hợp](SESSION_HANDOFF.md) và [evidence feature 007](features/007-automatic-station-check-in/evidence.md) để nắm kiến trúc, phần đã/chưa làm và giới hạn kiểm thử.

Khảo sát ngày **2026-09-13**, cập nhật **2026-09-14** sau triển khai source 007 trong working tree cùng layout 004 và xe/chuyến đi 005. Nguồn đánh giá là source, cấu hình, migration và test đã đọc; `PROJECT_HANDOFF.md` là điểm xuất phát, không thay cho bằng chứng chạy thực tế.

> **Cập nhật 008:** đã có source HERE Traffic v7 (flow/incidents), raster tile proxy, cache fresh/stale, API nội bộ, matching geometry, ETA theo vị trí/section, simulator traffic rate/blocked metadata additive và lớp traffic/ETA trên frontend. Bộ test tập trung Traffic/ETA **20 pass**; backend compile và frontend lint/tsc/build pass. Google chỉ còn là lớp nền, traffic map tải HERE raster tile qua backend proxy. Live spike upstream `flow`, `incidents` và raster tile đã trả HTTP 200; chưa có browser → Spring → HERE end-to-end, còn giới hạn full suite do JDK 26 Byte Buddy/Docker; xem [evidence 008](features/008-here-traffic-eta/evidence.md). Reroute/thông báo vẫn thuộc 009.

005 đã có CRUD xe, tạo chuyến từ tuyến/giờ xuất phát, lịch kế hoạch cố định và lifecycle trong drawer Map-First. Kết quả full suite 118 pass tại mốc 005 được giữ ở [verification 005](features/005-vehicles-trips/verification.md); không dùng kết quả cũ thay cho full suite sau 006.

Map-First Canvas dark mode đã có bản đồ toàn màn hình, drawer trái, simulator/alerts nổi, panel mobile và kéo thả điểm dừng. Xem [hồ sơ layout](features/004-operations-layout/map-first-implementation.md). [Prototype](features/004-operations-layout/map-first-prototype.html) là bản minh họa riêng.

## 1. Kết quả theo bảy yêu cầu

| Yêu cầu | Đã có trong code | Còn thiếu | Evidence |
| --- | --- | --- | --- |
| Theo dõi vị trí xe realtime trên bản đồ | Danh mục xe/chuyến, GPS ingestion, history/latest, SSE snapshot, marker/follow và stale/offline; HTTP/SSE và browser Spring đã có bằng chứng | Chưa nối thiết bị GPS thật | E01, E02, E13, E16–E18 |
| Thêm/xóa/sửa trạm đầu, cuối, dừng | CRUD trạm xuyên UI–API–JPA; xóa mềm; tọa độ và bán kính được validate | Không thiếu CRUD trong phạm vi đã yêu cầu. Vai trò đầu/dừng/cuối gắn với thứ tự trong tuyến, không phải loại cố định của trạm | E03, E04 |
| Tạo tuyến từ điểm dừng, dự kiến thời gian hoàn thành chuyến | Chọn 2–50 điểm, sắp thứ tự, dwell, HERE routing, lưu snapshot; trip gắn xe/giờ xuất phát, lịch từng trạm và dự kiến kết thúc | ETA động theo vị trí đã có trong 008; chưa quản lý phiên bản/chỉnh sửa tuyến | E04, E05, E14, evidence 008 |
| Xe tự check-in khi đi ngang trạm | GPS POINT/SEGMENT, simulator ROUTE_TRACE theo stop sequence, visit history, revision và timeline UI; browser Spring xác nhận 3 visit | Chưa đủ ma trận load/edge; chưa xác nhận dừng đón/trả khách (ngoài phạm vi) | E01, E03, E06, feature 007 evidence |
| Thời gian xe đến từng trạm trong lịch trình | Lịch trip cố định theo ngày/giờ; simulator có thời gian giả lập còn lại tới stop tiếp theo | Chưa có ETA mọi trạm theo GPS/traffic live | E04, E07, E14, E17 |
| Simulator chạy tuyến, tốc độ và ETA theo giao thông thật | Engine theo geometry/dwell, ETA theo vị trí, tốc độ tiến độ được điều chỉnh theo traffic rate, metadata traffic nullable, lớp traffic và badge source | Chưa browser/full-suite xác minh; chưa reroute | E05, E17, E18, evidence 008 |
| Tự thông báo đổi lịch khi tuyến có sự cố/kẹt xe nghiêm trọng | Feature 009 đã có evaluator HERE, revision tuyến/lịch, dedupe/cooldown, API/SSE và AlertStream | Chưa xác minh full integration trên PostgreSQL/HERE live; chưa điều khiển dẫn đường thiết bị | Feature 009 evidence |

**Kết luận:** trạm/tuyến/xe/chuyến, telemetry/check-in, Traffic/ETA và source reroute/notification đã có. 007 còn thiếu load/edge evidence; 008/009 còn giới hạn browser/full integration. Không quy đổi thành phần trăm vì các yêu cầu khác nhau đáng kể về khối lượng.

Phân biệt thời gian: HERE có thể tính `duration` dựa trên dữ liệu giao thông khi tạo tuyến, nhưng việc lưu kết quả đó không tạo thành cơ chế cập nhật ETA của xe đang chạy. `HereRoutingProvider.buildUri` chưa truyền `departureTime`; HERE mặc định thời điểm gọi nếu không chỉ định. [HERE: traffic in routing](https://docs.here.com/routing/docs/routing-v8-traffic-in-routing), [duration/baseDuration](https://docs.here.com/routing/docs/routing-v8-duration). Evidence code: E05, E07.

## 2. Bằng chứng repository

Đường dẫn dưới đây tính từ root. Với kết luận “chưa có”, phạm vi là `src/main` backend và `src` frontend hiện được khảo sát, không suy ra từ tên file tài liệu.

- **E01** — Frontend `MapComponent` mount `useLiveOperations`, `useSimulator`, `useVehicleMarkers` với cùng snapshot và FleetWorkspace. Đã thay mảng `vehicles=[]` bằng dữ liệu realtime; `VehicleDrawer` cũ vẫn không mount. `useVehicleMarkers` giữ phần tử marker, tooltip nguồn/time và follow theo tọa độ mới.
- **E02** — Backend `src/main/java/com/quangkhai/vehicletracking_backend/` có StationController/RouteController/VehicleController/TripController, telemetry/simulation/traffic controllers và Feature 009 `reroute/controller/NotificationController`, `RouteRevisionController`.
- **E03** — `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/station/service/StationService.java`, `findAll`, `create`, `update`, `delete`; `station/dto/StationUpsertRequest.java` và `src/main/resources/db/migration/V2__create_stations_table.sql`, `stations`/`chk_stations_*`: ghi transaction, đọc active, validation và xóa mềm. Frontend `src/services/stations.ts`; `StationPanel`, `StationDrawer` và hook `src/hooks/useStationWorkspace.ts` (`handleSaveStation`, `handleDeactivate`) nối CRUD.
- **E04** — `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/service/RouteService.java`, `create`, `validateStops`: chỉ active station, 2–50 stops, cấm trùng liên tiếp, dwell đầu/cuối bằng 0; tổng duration = travel + dwell. `route/dto/RouteDetailResponse.java`, `from`: suy ra START/STOP/END theo vị trí.
- **E05** — `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/provider/HereRoutingProvider.java`, `calculate`, `buildUri`: gọi HERE server-side, `car`/`fast`, trả polyline/summary/travelSummary, phân loại lỗi provider. `route/service/RoutePersistenceService.java`, `persistRoute` ghi sau khi tính tuyến. `vehicletracking-frontend/src/components/route/RouteWorkspace.tsx`, `handleSaveRoute`, `handleSelectRoute`; `RouteDrawer`/`RoutePanel`; `src/services/polyline.ts`, `decodeFlexiblePolyline`.
- **E06** — Backend `src/main/resources/db/migration/` V1–V4 tạo stations/routes và vehicles/trips; V5 thêm telemetry_samples/positions/runs; V6 thêm trip check-in state/visits. V4 snapshot radius theo trip. Notification vẫn chưa có.
- **E07** — `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/dto/RouteDetailResponse.java`, `from`, `cumulativeDepartureOffset`, `arrivalOffsetSeconds` và `departureOffsetSeconds`; `vehicletracking-frontend/src/components/route/RouteDrawer.tsx`, `RouteViewContent`: timeline offset từ snapshot, không tiêu thụ vị trí xe.
- **E08** — Mốc khảo sát trước 008: `services/hereTraffic.ts` chưa có consumer và backend chưa có traffic endpoint. Source hiện tại của 008 và kiểm tra mới được ghi tại [evidence 008](features/008-here-traffic-eta/evidence.md); không dùng E08 cũ để kết luận trạng thái hiện tại.
- **E09** — `vehicletracking-backend/src/main/resources/application.yaml`, `spring.jpa`, `here`, `app.cors`: `ddl-auto=validate`, `open-in-view=false`, HERE key lấy từ biến backend. `compose.yaml`, `services`: PostgreSQL/pgAdmin, không chạy app. Không đọc `.env` trong lần khảo sát này.
- **E10** — `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/route/service/RouteServiceTest.java`, `create_normalizesNameAndPreservesStopOrderAndCalculatesMetrics`, `create_acceptsLoopRoute_whenStartAndEndShareSameStation`; `route/provider/HereRoutingProviderTest.java`, `setUp` dùng `MockRestServiceServer`; `station/repository/StationRepositoryIntegrationTest.java`, `createUpdateAndDeactivate_persistsExpectedStationState`; `route/repository/RouteRepositoryIntegrationTest.java`: database bằng Testcontainers. Kết quả chạy mới có cả E15 nằm ở `target/surefire-reports/`, tổng hợp tại verification 005.
- **E11** — `vehicletracking-frontend/package.json`, `scripts`, `engines`: lint/tsc/build, không có component/E2E runner trong dependency ứng dụng; `.nvmrc` định hướng Node 24. Browser scripts nằm riêng dưới `docs/features/004-operations-layout/verification/` và `005-vehicles-trips/verification/`.
- **E12** — Lúc bắt đầu, `.gitignore` có rule `docs/`. Kiểm tra cuối 006 thấy một thay đổi đồng thời ngoài phần agent sửa đã bỏ rule này; `git status` hiện báo `?? docs/` và `.gitignore` modified. Giữ nguyên thay đổi đó; tài liệu hiện untracked, chưa commit. `docs/workflow.md` vẫn không có khi khảo sát.

- **E13** — Backend `vehicle/service/VehicleService.create/update/deactivate`, `vehicle/repository/VehicleRepository.findLockedById`; frontend `components/fleet/FleetWorkspace`, `VehicleEditor`, `hooks/useFleetWorkspace`, `services/fleet.ts`: danh mục xe, form, xóa mềm, giữ draft và danh sách từ API.
- **E14** — Backend `trip/service/TripService.create/transition`, `trip/entity/TripStopEntity`, `trip/dto/TripDetailResponse.from`; V4 `uq_trips_running_vehicle` và `chk_trips_state`; frontend `TripEditor`, `TripDetailPanel`, `MapComponent` state `editorRoute`/`tripRoute`: snapshot lịch, lifecycle, chặn hai chuyến đang thực hiện và route theo selection.
- **E15** — `vehicle/service/VehicleServiceTest`, `trip/service/TripServiceTest`, `trip/controller/FleetControllerTest`, `trip/repository/FleetRepositoryIntegrationTest.concurrentStartsAllowExactlyOneWinner` và `persistsImmutableStopScheduleRadiusAndPlateAfterStationAndVehicleUpdates`: 33 test mới. Full suite 118/0/0/0; `pom.xml` Surefire `user.timezone=UTC` cho JVM test.

- **E16** — Backend `telemetry/service/TelemetryService.ingestGps/ingestSimulator`, repositories/entities: unique eventId, giữ latest, GPS validation và source guard. `OperationsSnapshotService.snapshot` transaction REPEATABLE_READ; `OperationsStreamService` full resync/SSE/cleanup.
- **E17** — Backend `simulation/motion/RouteMotion.at`, `FlexiblePolyline.decode`, `SimulationService.play/pause/speed/stop/reset/tick/recover`, `SimulationScheduler.start/tick`: geometry/dwell, clock, lifecycle và restart paused. `RouteMotionTest` 14 và `OperationsIntegrationTest` 12 (kèm GPS/simulator check-in) đã chạy pass với PostgreSQL 17.
- **E18** — Frontend `services/operations`, `hooks/useLiveOperations`, `useSimulator`, `useVehicleMarkers`, `components/operations/SimulatorPanel`, `types/operations.positionFreshness`: EventSource, điều khiển, source và freshness. Browser 006 fixture server có SSE socket thật nhưng không phải Spring; results dưới `docs/features/006-telemetry-simulator/artifacts/`.

Điều chỉnh handoff: `StationPanel.filteredStations` tìm tên/địa chỉ, chưa tìm tọa độ. Full suite hiện tại 178 pass với V1–V8; browser 007 đã có bằng chứng ba visit, load/edge matrix vẫn chưa đầy đủ.

## 3. Layout 004 hiện tại — Map-First

- Bản đồ Leaflet toàn viewport, mặc định nền tối; ModeBar nổi với ba chế độ Theo dõi / Tuyến & trạm / Mô phỏng. Evidence: `src/App.tsx#App`, `src/components/MapComponent.tsx#MapComponent`, `src/components/operations/ModeBar.tsx#ModeBar`, `src/workspace.css#.map-first` (đường dẫn trong frontend).
- Drawer trái chứa danh sách, chi tiết hoặc form; tab station/route giữ mounted bằng `hidden`, giữ nháp/tìm kiếm/tuyến khi chuyển mode và thu panel. CRUD station tách ở `src/hooks/useStationWorkspace.ts#useStationWorkspace`.
- Reorder bằng kéo handle, bàn phím Space/Enter + ↑/↓/Esc và nút; occurrence ID ổn định, đầu/cuối dwell = 0, marker nháp đồng bộ. Chỉ gọi tính/lưu HERE khi submit. Evidence: `src/components/route/SortableStopList.tsx#SortableStopList`, `RouteDrawer.tsx#normalizeStops` / `RouteCreateContent`, `RouteWorkspace.tsx#handleSaveRoute`.
- Simulator/AlertStream ở góc phải; revision 006 đã nối simulator control/telemetry với backend (E17–E18). 008 đã nối traffic layer/ETA UI; 009 đã nối AlertStream với notification API/SSE. Evidence: `src/components/operations/SimulatorPanel.tsx#SimulatorPanel`, `AlertStream.tsx#AlertStream`, `MapControls.tsx#MapControls`, [evidence 008](features/008-here-traffic-eta/evidence.md), [evidence 009](features/009-automatic-reroute-notifications/evidence.md).
- Tablet một cột nổi; mobile một sheet, thu/mở chiều cao, map picking trả về form. Camera đo phần panel che, fit/focus giữ mục tiêu trong vùng nhìn được; có cleanup observer/RAF. Evidence: `src/hooks/useMapCamera.ts#useMapCamera`, `useCompactLayout.ts#useCompactLayout`, `workspace.css` media queries.
- Hộp thoại ngừng dùng station dùng native `dialog` để giữ focus và hỗ trợ Escape. Evidence: `src/components/operations/ConfirmStationDelete.tsx#ConfirmStationDelete`.
- Lint/tsc/build và kiểm tra trình duyệt ghi ở [verification Map-First](features/004-operations-layout/map-first-implementation.md). Browser sử dụng API fixture; không xác nhận backend/HERE/GPS thật.

Bản dashboard hai cột/nền sáng đã được thay bằng revision này. Tài liệu kiểm tra dashboard cũ giữ lại làm lịch sử trong [verification.md](features/004-operations-layout/verification.md).

## 4. Các mốc chức năng — 005/006 đã có, 007–010 đã triển khai source (integration verification pending)

005–009 đã có source theo yêu cầu trực tiếp. 007–009 còn thiếu một phần load/live/browser evidence để chuyển `Verified` đầy đủ. Không coi lộ trình là source đã tồn tại.

| Thứ tự | Feature / trạng thái | Kết quả | Phụ thuộc |
| --- | --- | --- | --- |
| 005 | Xe và chuyến đi — đã triển khai | CRUD/xóa mềm xe, gán tuyến snapshot + giờ xuất phát; bắt đầu/kết thúc/hủy; timeline kế hoạch và chặn start đồng thời | Station/route hiện có |
| 006 | Telemetry và simulator — đã triển khai | Geometry/clock/persistence/SSE/UI; full backend hồi quy và HTTP/SSE đạt, browser Spring riêng còn tùy môi trường | 005 |
| 007 | Check-in tự động — đang triển khai | GPS POINT/SEGMENT, simulator ROUTE_TRACE, visit history, GET/API snapshot/SSE và timeline UI; browser core đạt, còn load/edge evidence | 005–006 |
| 008 | Traffic thực tế, ETA và simulator theo traffic — source đã triển khai | Provider flow/incidents, raster tile proxy, cache, API bbox, ETA remaining stops, simulator metadata, map tile/incident layer, source/fallback/blocked UI | 005–007 |
| 009 | Đổi lộ trình/lịch trình và thông báo — đã triển khai source | Hai fetch breach HERE, tự kích hoạt sau telemetry, revision alternative, unavailable notification, dedupe/cooldown, API/SSE/AlertStream | 008 |
| 010 | Management endpoints — đã triển khai source | Route update/deactivate, trip reschedule/delete, telemetry history filter/page, notification read-all/delete, revision supersede | 005–009 |

### 005 — Xe và chuyến đi — hoàn thành

**Đã có:** feature packages `vehicle`, `trip`; migration V4. `vehicles` gồm biển số unique chuẩn hóa, tên/mô tả, active; `trips` gắn vehicle/route immutable, lịch xuất phát và timestamps lifecycle; `trip_stops` snapshot thứ tự, tên/tọa độ/radius/dwell/offset và giờ kế hoạch. Lưu timestamps UTC, UI hiển thị ngày giờ địa phương (E13–E14).

**API đã có:** `/api/v1/vehicles`, `/api/v1/trips`, detail và start/complete/cancel. Service có transaction, vehicle lock và partial unique index bảo vệ race; retry cùng trạng thái đích không thay timestamp. Ngừng xe bị chặn khi còn chuyến chờ/đang thực hiện. Không thêm driver module (E13–E15).

**Đã kiểm tra:** lịch qua ngày/tuyến vòng, snapshot sau sửa station/xe, lifecycle và hai start đồng thời; UI form/confirmation/loading/error/retry, giữ draft/selection/map, responsive và regression trạm/tuyến. Chi tiết contract và bằng chứng: [spec](features/005-vehicles-trips/spec.md), [verification](features/005-vehicles-trips/verification.md). Để dùng database phát triển, khởi động lại backend cho Flyway áp dụng V4; lượt kiểm tra chỉ dùng database tạm. Nút khởi hành chưa chạy simulator hoặc cập nhật vị trí.

### 006 — Telemetry và simulator cơ bản — đã triển khai

**Đã có trong source:** HTTP GPS ingestion, history/latest và SSE snapshot từ Spring MVC. Payload có eventId, vehicleId/tripId, recordedAt, coords/speed/heading/accuracy, receivedAt server và source. SIMULATOR chỉ được phát từ backend qua cùng ingestion; GPS không trộn vào run simulator, bản tin cũ/collision bị từ chối. Không thêm MQTT/broker (E16).

Simulator nội suy theo khoảng cách segment trong section, play/pause/stop/reset, 1×/5×/10×, dwell theo occurrence. Lưu clock checkpoint ở backend, restart đưa running về paused; hết tuyến hoàn thành trip, reset tạo trip mới giữ history. UI ghi rõ GIẢ LẬP và thời lượng snapshot, không giả traffic live. Marker/follow, nguồn/freshness, status trip và controls cùng nhận snapshot (E17–E18).

**Kết quả:** full backend hiện tại đạt 178 test, trong đó HTTP/SSE Testcontainers đạt; lint/tsc/build frontend đạt bằng Node v24.16.0. Browser fixture và browser nối Spring/PostgreSQL đã chạy, browser 007 xác nhận visits theo thứ tự; traffic/HERE live không thuộc 006/007. Chi tiết lịch sử ở [verification 006](features/006-telemetry-simulator/verification.md) và [evidence 007](features/007-automatic-station-check-in/evidence.md).

### 007 — Check-in tự động

**Đã triển khai:** xét stop tiếp theo trong trip, không quét và check-in mọi trạm gần xe. Visit key là `(trip_id, stop_sequence)`, không chỉ stationId để tuyến vòng ghé lại cùng trạm vẫn hợp lệ. Theo yêu cầu “đi ngang”, check-in phát sinh khi quỹ đạo đi vào/cắt vùng bán kính; không bắt buộc dừng xe. Tách “đi qua” khỏi “thực sự dừng” nếu cần trạng thái phục vụ hành khách sau này.

Xét cả đoạn nối hai mẫu GPS để không bỏ sót xe đi nhanh qua vòng nhỏ; chỉ suy ra giao cắt nếu khoảng cách thời gian/tốc độ/accuracy hợp lý, tránh kéo đoạn thẳng qua trạm khi mất GPS lâu. Dùng hysteresis cho check-out để chống nhiễu ở mép; thời gian nội suy phải được đánh dấu suy ra. DB unique đảm bảo retry/concurrency không nhân event. Theo quy tắc nghiệp vụ, điểm đầu check-in khi bắt đầu trip trong vùng; không tự check-in điểm cuối chỉ vì cùng tọa độ đầu trong tuyến vòng.

**Đã kiểm tra:** GPS POINT→SEGMENT, simulator ROUTE_TRACE qua A→B→A, thứ tự occurrence, revision, endpoint GET, snapshot/SSE, retry dedupe và browser 007 ba visit. Còn cần bổ sung tải/overlap/rollback fault-injection trước khi đánh dấu Verified. Xem [evidence 007](features/007-automatic-station-check-in/evidence.md).

### 008 — Traffic, ETA và nâng cấp simulator — source đã triển khai, verification pending

**Đã triển khai:** provider HERE v7 server-side cho flow/incidents, raster tile proxy v3, DTO chuẩn hóa geometry/speed/jam/incident, cache fresh 60 giây + stale 300 giây, endpoint bbox, matching section theo geometry/hướng, ETA từng stop bắt đầu từ vị trí telemetry, dwell/check-in/fallback/blocked, simulator rate theo ETA và metadata additive trong SimulationResponse, traffic tile/incident layer và retry/freshness UI. Chi tiết source/test ở [evidence 008](features/008-here-traffic-eta/evidence.md), hướng dẫn chạy ở [walkthrough 008](features/008-here-traffic-eta/walkthrough.md). HERE Traffic API dùng flow/incidents cho ETA; tile dùng để render mượt, với đường đóng không suy diễn từ jamFactor đơn thuần. [HERE Traffic Flow](https://docs.here.com/traffic-api/reference/traffic-api-v7-getflow), [HERE Traffic Incidents](https://docs.here.com/traffic-api/reference/traffic-api-v7-getincidents).

Thu thập theo hành lang phần tuyến còn lại, ghép đúng đoạn/hướng di chuyển, không dùng mọi incident trong bounding box để báo trễ. Cache request chung và giới hạn chu kỳ, quota; TTL thử nghiệm 60 giây là đề xuất cần đo. Validate key khi traffic bật, timeout/rate-limit có lỗi kiểm soát, giữ last-known kèm tuổi dữ liệu, không đánh dấu “thông thoáng” khi provider lỗi. Live spike HERE tại TP.HCM đã xác nhận flow, incidents và raster tile HTTP 200; browser → Spring → HERE, load và full-suite vẫn cần chạy trong môi trường phù hợp.

**ETA:** từ vị trí xe mới nhất được chiếu lên section còn lại, sau đó qua các stop chưa check-in và cộng thời gian dừng chưa hoàn thành; nếu thiếu hoặc lệch route thì fallback route snapshot có warning. Stop đã check-in hiển thị actual riêng. Dùng route/section duration có xét giao thông; không lấy khoảng cách thẳng chia tốc độ GPS tức thời. `baseDuration` là thời lượng không xét thời gian, không phải baseline lịch trình đã hứa và không bảo đảm luôn nhỏ hơn `duration`. [HERE duration](https://docs.here.com/routing/docs/routing-v8-duration).

Simulator điều chỉnh tốc độ tiến độ theo tỷ lệ ETA/route snapshot trên đoạn đang chạy, dùng cùng ETA service như GPS; closure giữ frame tốc độ 0 và không tăng elapsed. Gắn nguồn `HERE_LIVE`, `LAST_KNOWN` hoặc `ROUTE_SNAPSHOT` vào kết quả. Thời gian giả lập tăng tốc phải tách khỏi wall clock của traffic: chế độ live ưu tiên 1×; chạy nhanh vẫn phải hiển thị thời điểm traffic thực được lấy, không giả định provider biết tương lai. Tai nạn/công trường dùng fixtures cho test lặp lại; live mode chỉ hiển thị sự cố thật provider trả, có thể không có sự cố ở khu vực đang chọn.

**Nghiệm thu:** flow chậm làm tốc độ simulator giảm và ETA tăng; closure không bị diễn giải thành tốc độ thông thường; xe đứng yên không chia cho 0; mất provider có nhãn stale; incident ngoài đường/hết hạn không làm đổi ETA; test fixture và live evidence được báo riêng.

### 009 — Tính lại lộ trình và thông báo đổi lịch

**Trigger đã triển khai:** đường đóng còn hiệu lực trên phần đường chưa đi, hoặc trễ vượt ngưỡng cấu hình (`REROUTE_DELAY_SECONDS`, mặc định 600 giây; `REROUTE_DELAY_PERCENT`, mặc định 30%); yêu cầu hai lần `trafficFetchedAt` khác nhau. Mỗi telemetry accepted tự gọi evaluator, nhưng timestamp cache không được tính là fetch mới. Segment ID dùng để tạo fingerprint chống lặp.

Tính từ vị trí hiện tại qua toàn bộ stop bắt buộc còn lại; lưu phương án thực thi thành trip route revision mới, không sửa snapshot tuyến gốc. Lưu baseline và revised schedule riêng. Chỉ áp dụng phương án khả dụng và có lợi theo ngưỡng; nếu không có đường thay thế thì giữ phương án hiện hành, đánh dấu bị chặn và thông báo để người điều hành xử lý. ETA có thể thay đổi mà geometry không đổi; vẫn cần thông báo nếu vượt ngưỡng đổi lịch.

Thông báo trong ứng dụng: trip, nguyên nhân/sự cố, các trạm ảnh hưởng, ETA cũ→mới, createdAt, revision, mức độ, readAt. Ghi revision và notification trong cùng transaction; snapshot SSE mang danh sách gần nhất. DB dedupe key và checkpoint cooldown 5 phút chống lặp. Chưa thêm email/SMS vì chưa được yêu cầu.

**Nghiệm thu:** một sự cố không sinh lặp thông báo qua nhiều tick; reload vẫn đọc được thông báo; hai client nhận cùng revision; stop đã hoàn thành không bị tính lại; không bỏ stop khi reroute; hết sự cố cập nhật có kiểm soát; timeout/không có tuyến thay thế không làm mất lộ trình đang chạy. Test end-to-end: simulator → traffic fixture → ETA thay đổi → revision → thông báo.

## 5. Cách nghiệm thu từng mốc

Luồng demo mục tiêu: tạo 3 trạm → tạo tuyến → tạo xe/trip → chạy simulator → marker và tốc độ đổi → check-in → traffic làm chậm → ETA từng stop đổi → reroute/lịch revision → thông báo. Tại mỗi mốc chạy backend tests, frontend lint/tsc/build và browser regression cho trạm/tuyến hiện có.

Những việc bổ trợ chỉ đưa vào kế hoạch khi cần: route rename/recalculate/deactivate phục vụ vận hành nhiều phiên bản; thiết bị GPS thật và định dạng giao thức sau khi có thiết bị cụ thể. Xác thực nguồn telemetry, quyền điều hành và chính sách giữ dữ liệu cần được chốt trước khi mở hệ thống ra ngoài môi trường phát triển; không tự triển khai các module này trong lượt layout.

## 6. Giới hạn xác minh của lượt hiện tại

- Frontend lint/tsc/build exit 0 bằng Node v24.16.0 (build: 1.899 modules). Browser fixture đạt 10 nhóm; browser wrapper 007 qua Spring/PostgreSQL đạt 9 nhóm, không lỗi JavaScript và xác nhận ordered visits. Artifacts ở [evidence 007](features/007-automatic-station-check-in/evidence.md).
- Full backend đạt 178 tests (0 failures/errors/skipped), gồm Flyway V1–V8, GPS/simulator check-in, geometry, GET check-ins và HTTP/SSE. Chưa đo production load và chưa chạy đủ edge/fault-injection matrix 007.
- 008 đã test live HERE ở mức upstream spike; chưa xác minh browser → Spring → HERE, backend/database thật qua UI hay nguồn GPS. Ảnh browser dùng fixtures rõ tên, không chứng minh dữ liệu vận hành thật.
- Tài liệu dưới `docs/` hiện untracked sau thay đổi `.gitignore` đồng thời được giữ nguyên (E12). `PROJECT_HANDOFF.md` lưu tóm tắt tiến độ/lộ trình; chưa commit/push.
