# Tiến độ và kế hoạch Vehicle Tracking

> **Đổi session:** đọc [báo cáo bàn giao tổng hợp](SESSION_HANDOFF.md) để nắm kiến trúc, phần đã/chưa làm, giới hạn kiểm thử và các bước hoàn tất feature 006. Mục 14 có đoạn yêu cầu dùng cho session mới.

Khảo sát ngày **2026-09-13**, tại HEAD `8d398b0`, cập nhật **2026-09-14** sau triển khai source 006 trong working tree cùng layout 004 và xe/chuyến đi 005. Nguồn đánh giá là source, cấu hình, migration và test đã đọc; `PROJECT_HANDOFF.md` là điểm xuất phát, không thay cho bằng chứng chạy thực tế.

> **Cập nhật 006:** đã có source GPS ingestion/history/latest, simulator clock backend, SSE và UI marker/control. **24 test backend chọn lọc**, lint/tsc/build và **39 nhóm browser fixture** đạt. Chưa nghiệm thu toàn bộ: HTTP/SSE và browser nối Spring/PostgreSQL, full Maven suite đang chờ vì auto-review từ chối lệnh thực thi do hết hạn mức. Xem [verification 006](features/006-telemetry-simulator/verification.md). **007–009 chưa triển khai**.

005 đã có CRUD xe, tạo chuyến từ tuyến/giờ xuất phát, lịch kế hoạch cố định và lifecycle trong drawer Map-First. Kết quả full suite 118 pass tại mốc 005 được giữ ở [verification 005](features/005-vehicles-trips/verification.md); không dùng kết quả cũ thay cho full suite sau 006.

Map-First Canvas dark mode đã có bản đồ toàn màn hình, drawer trái, simulator/alerts nổi, panel mobile và kéo thả điểm dừng. Xem [hồ sơ layout](features/004-operations-layout/map-first-implementation.md). [Prototype](features/004-operations-layout/map-first-prototype.html) là bản minh họa riêng.

## 1. Kết quả theo bảy yêu cầu

| Yêu cầu | Đã có trong code | Còn thiếu | Evidence |
| --- | --- | --- | --- |
| Theo dõi vị trí xe realtime trên bản đồ | Danh mục xe/chuyến, GPS ingestion, history/latest, SSE snapshot, marker/follow và stale/offline | Chờ nghiệm thu HTTP/SSE + browser nối Spring; chưa nối thiết bị GPS thật | E01, E02, E13, E16–E18 |
| Thêm/xóa/sửa trạm đầu, cuối, dừng | CRUD trạm xuyên UI–API–JPA; xóa mềm; tọa độ và bán kính được validate | Không thiếu CRUD trong phạm vi đã yêu cầu. Vai trò đầu/dừng/cuối gắn với thứ tự trong tuyến, không phải loại cố định của trạm | E03, E04 |
| Tạo tuyến từ điểm dừng, dự kiến thời gian hoàn thành chuyến | Chọn 2–50 điểm, sắp thứ tự, dwell, HERE routing, lưu snapshot; trip gắn xe/giờ xuất phát, lịch từng trạm và dự kiến kết thúc | Chưa có ETA động theo vị trí; chưa quản lý phiên bản/chỉnh sửa tuyến | E04, E05, E14 |
| Xe tự check-in khi đi ngang trạm | Bán kính trạm, vòng geofence trên bản đồ | Chưa có xử lý GPS qua vùng trạm, event check-in và lịch sử | E01, E03, E06 |
| Thời gian xe đến từng trạm trong lịch trình | Lịch trip cố định theo ngày/giờ; simulator có thời gian giả lập còn lại tới stop tiếp theo | Chưa có ETA mọi trạm theo GPS/traffic live | E04, E07, E14, E17 |
| Simulator chạy tuyến, tốc độ và ETA theo giao thông thật | Engine theo geometry và dwell; backend clock lưu DB; play/pause/1×/5×/10×/stop/reset; source SIMULATOR và UI telemetry | Chờ HTTP/SSE end-to-end; chưa nối HERE Traffic hoặc ETA theo traffic | E05, E08, E17, E18 |
| Tự thông báo đổi lịch khi tuyến có sự cố/kẹt xe nghiêm trọng | Chưa có luồng nghiệp vụ | Phát hiện ảnh hưởng đến phần đường còn lại, tính phương án thay thế, revision lịch trình và thông báo lưu bền | E02, E06, E08 |

**Kết luận:** trạm/tuyến/xe/chuyến đã có; source simulator và truyền vị trí 006 đã được nối nhưng chưa đủ bằng chứng nghiệm thu end-to-end. Check-in, traffic/ETA và cảnh báo còn ở 007–009. Không quy đổi thành phần trăm vì các yêu cầu khác nhau đáng kể về khối lượng.

Phân biệt thời gian: HERE có thể tính `duration` dựa trên dữ liệu giao thông khi tạo tuyến, nhưng việc lưu kết quả đó không tạo thành cơ chế cập nhật ETA của xe đang chạy. `HereRoutingProvider.buildUri` chưa truyền `departureTime`; HERE mặc định thời điểm gọi nếu không chỉ định. [HERE: traffic in routing](https://docs.here.com/routing/docs/routing-v8-traffic-in-routing), [duration/baseDuration](https://docs.here.com/routing/docs/routing-v8-duration). Evidence code: E05, E07.

## 2. Bằng chứng repository

Đường dẫn dưới đây tính từ root. Với kết luận “chưa có”, phạm vi là `src/main` backend và `src` frontend hiện được khảo sát, không suy ra từ tên file tài liệu.

- **E01** — Frontend `MapComponent` mount `useLiveOperations`, `useSimulator`, `useVehicleMarkers` với cùng snapshot và FleetWorkspace. Đã thay mảng `vehicles=[]` bằng dữ liệu realtime; `VehicleDrawer` cũ vẫn không mount. `useVehicleMarkers` giữ phần tử marker, tooltip nguồn/time và follow theo tọa độ mới.
- **E02** — Backend `src/main/java/com/quangkhai/vehicletracking_backend/` có StationController/RouteController/VehicleController/TripController và mới `telemetry/controller/TelemetryController`, `simulation/controller/SimulationController`; endpoint `/api/v1/telemetry[/snapshot|/stream]`, `/api/v1/trips/{tripId}/simulation/{action}`. Chưa có traffic/notification controller.
- **E03** — `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/station/service/StationService.java`, `findAll`, `create`, `update`, `delete`; `station/dto/StationUpsertRequest.java` và `src/main/resources/db/migration/V2__create_stations_table.sql`, `stations`/`chk_stations_*`: ghi transaction, đọc active, validation và xóa mềm. Frontend `src/services/stations.ts`; `StationPanel`, `StationDrawer` và hook `src/hooks/useStationWorkspace.ts` (`handleSaveStation`, `handleDeactivate`) nối CRUD.
- **E04** — `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/service/RouteService.java`, `create`, `validateStops`: chỉ active station, 2–50 stops, cấm trùng liên tiếp, dwell đầu/cuối bằng 0; tổng duration = travel + dwell. `route/dto/RouteDetailResponse.java`, `from`: suy ra START/STOP/END theo vị trí.
- **E05** — `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/provider/HereRoutingProvider.java`, `calculate`, `buildUri`: gọi HERE server-side, `car`/`fast`, trả polyline/summary/travelSummary, phân loại lỗi provider. `route/service/RoutePersistenceService.java`, `persistRoute` ghi sau khi tính tuyến. `vehicletracking-frontend/src/components/route/RouteWorkspace.tsx`, `handleSaveRoute`, `handleSelectRoute`; `RouteDrawer`/`RoutePanel`; `src/services/polyline.ts`, `decodeFlexiblePolyline`.
- **E06** — Backend `src/main/resources/db/migration/` V1–V4 tạo stations/routes và vehicles/trips; V5 `V5__create_telemetry_and_simulation.sql` thêm telemetry_samples, vehicle_positions, simulation_runs. V4 snapshot radius theo trip. Chưa có stop visit/notification.
- **E07** — `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/dto/RouteDetailResponse.java`, `from`, `cumulativeDepartureOffset`, `arrivalOffsetSeconds` và `departureOffsetSeconds`; `vehicletracking-frontend/src/components/route/RouteDrawer.tsx`, `RouteViewContent`: timeline offset từ snapshot, không tiêu thụ vị trí xe.
- **E08** — `vehicletracking-frontend/src/components/SimulatorControls.tsx`, `SimulatorControls` không được import trong `App`/`MapComponent`; `src/services/hereTraffic.ts`, `fetchHereTrafficFlow`/`fetchHereIncidents` chỉ là hàm gọi API dự kiến và không có consumer hiện tại (`rg -n 'fetchHereTrafficFlow|fetchHereIncidents|SimulatorControls' vehicletracking-frontend/src`). Backend `config/HereTrafficProperties.java`, `config/HttpClientConfig.java` chỉ có cấu hình/RestClient; không có traffic endpoint trong E02.
- **E09** — `vehicletracking-backend/src/main/resources/application.yaml`, `spring.jpa`, `here`, `app.cors`: `ddl-auto=validate`, `open-in-view=false`, HERE key lấy từ biến backend. `compose.yaml`, `services`: PostgreSQL/pgAdmin, không chạy app. Không đọc `.env` trong lần khảo sát này.
- **E10** — `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/route/service/RouteServiceTest.java`, `create_normalizesNameAndPreservesStopOrderAndCalculatesMetrics`, `create_acceptsLoopRoute_whenStartAndEndShareSameStation`; `route/provider/HereRoutingProviderTest.java`, `setUp` dùng `MockRestServiceServer`; `station/repository/StationRepositoryIntegrationTest.java`, `createUpdateAndDeactivate_persistsExpectedStationState`; `route/repository/RouteRepositoryIntegrationTest.java`: database bằng Testcontainers. Kết quả chạy mới có cả E15 nằm ở `target/surefire-reports/`, tổng hợp tại verification 005.
- **E11** — `vehicletracking-frontend/package.json`, `scripts`, `engines`: lint/tsc/build, không có component/E2E runner trong dependency ứng dụng; `.nvmrc` định hướng Node 24. Browser scripts nằm riêng dưới `docs/features/004-operations-layout/verification/` và `005-vehicles-trips/verification/`.
- **E12** — Lúc bắt đầu, `.gitignore` có rule `docs/`. Kiểm tra cuối 006 thấy một thay đổi đồng thời ngoài phần agent sửa đã bỏ rule này; `git status` hiện báo `?? docs/` và `.gitignore` modified. Giữ nguyên thay đổi đó; tài liệu hiện untracked, chưa commit. `docs/workflow.md` vẫn không có khi khảo sát.

- **E13** — Backend `vehicle/service/VehicleService.create/update/deactivate`, `vehicle/repository/VehicleRepository.findLockedById`; frontend `components/fleet/FleetWorkspace`, `VehicleEditor`, `hooks/useFleetWorkspace`, `services/fleet.ts`: danh mục xe, form, xóa mềm, giữ draft và danh sách từ API.
- **E14** — Backend `trip/service/TripService.create/transition`, `trip/entity/TripStopEntity`, `trip/dto/TripDetailResponse.from`; V4 `uq_trips_running_vehicle` và `chk_trips_state`; frontend `TripEditor`, `TripDetailPanel`, `MapComponent` state `editorRoute`/`tripRoute`: snapshot lịch, lifecycle, chặn hai chuyến đang thực hiện và route theo selection.
- **E15** — `vehicle/service/VehicleServiceTest`, `trip/service/TripServiceTest`, `trip/controller/FleetControllerTest`, `trip/repository/FleetRepositoryIntegrationTest.concurrentStartsAllowExactlyOneWinner` và `persistsImmutableStopScheduleRadiusAndPlateAfterStationAndVehicleUpdates`: 33 test mới. Full suite 118/0/0/0; `pom.xml` Surefire `user.timezone=UTC` cho JVM test.

- **E16** — Backend `telemetry/service/TelemetryService.ingestGps/ingestSimulator`, repositories/entities: unique eventId, giữ latest, GPS validation và source guard. `OperationsSnapshotService.snapshot` transaction REPEATABLE_READ; `OperationsStreamService` full resync/SSE/cleanup.
- **E17** — Backend `simulation/motion/RouteMotion.at`, `FlexiblePolyline.decode`, `SimulationService.play/pause/speed/stop/reset/tick/recover`, `SimulationScheduler.start/tick`: geometry/dwell, clock, lifecycle và restart paused. `RouteMotionTest` 14 và `OperationsIntegrationTest` 10 đã chạy pass với PostgreSQL 17.
- **E18** — Frontend `services/operations`, `hooks/useLiveOperations`, `useSimulator`, `useVehicleMarkers`, `components/operations/SimulatorPanel`, `types/operations.positionFreshness`: EventSource, điều khiển, source và freshness. Browser 006 fixture server có SSE socket thật nhưng không phải Spring; results dưới `docs/features/006-telemetry-simulator/artifacts/`.

Điều chỉnh handoff: `StationPanel.filteredStations` tìm tên/địa chỉ, chưa tìm tọa độ. Full suite 118 pass thuộc mốc 005; 006 mới chạy chọn lọc 24 test. Lỗi thiếu Docker mốc 004 đã hết, nhưng auto-review hiện chặn Maven bổ sung do hạn mức.

## 3. Layout 004 hiện tại — Map-First

- Bản đồ Leaflet toàn viewport, mặc định nền tối; ModeBar nổi với ba chế độ Theo dõi / Tuyến & trạm / Mô phỏng. Evidence: `src/App.tsx#App`, `src/components/MapComponent.tsx#MapComponent`, `src/components/operations/ModeBar.tsx#ModeBar`, `src/workspace.css#.map-first` (đường dẫn trong frontend).
- Drawer trái chứa danh sách, chi tiết hoặc form; tab station/route giữ mounted bằng `hidden`, giữ nháp/tìm kiếm/tuyến khi chuyển mode và thu panel. CRUD station tách ở `src/hooks/useStationWorkspace.ts#useStationWorkspace`.
- Reorder bằng kéo handle, bàn phím Space/Enter + ↑/↓/Esc và nút; occurrence ID ổn định, đầu/cuối dwell = 0, marker nháp đồng bộ. Chỉ gọi tính/lưu HERE khi submit. Evidence: `src/components/route/SortableStopList.tsx#SortableStopList`, `RouteDrawer.tsx#normalizeStops` / `RouteCreateContent`, `RouteWorkspace.tsx#handleSaveRoute`.
- Simulator/AlertStream ở góc phải; revision 006 đã nối simulator control/telemetry với backend (E17–E18). AlertStream và traffic layer vẫn chưa có nguồn sự kiện. Evidence: `src/components/operations/SimulatorPanel.tsx#SimulatorPanel`, `AlertStream.tsx#AlertStream`, `MapControls.tsx#MapControls`.
- Tablet một cột nổi; mobile một sheet, thu/mở chiều cao, map picking trả về form. Camera đo phần panel che, fit/focus giữ mục tiêu trong vùng nhìn được; có cleanup observer/RAF. Evidence: `src/hooks/useMapCamera.ts#useMapCamera`, `useCompactLayout.ts#useCompactLayout`, `workspace.css` media queries.
- Hộp thoại ngừng dùng station dùng native `dialog` để giữ focus và hỗ trợ Escape. Evidence: `src/components/operations/ConfirmStationDelete.tsx#ConfirmStationDelete`.
- Lint/tsc/build và kiểm tra trình duyệt ghi ở [verification Map-First](features/004-operations-layout/map-first-implementation.md). Browser sử dụng API fixture; không xác nhận backend/HERE/GPS thật.

Bản dashboard hai cột/nền sáng đã được thay bằng revision này. Tài liệu kiểm tra dashboard cũ giữ lại làm lịch sử trong [verification.md](features/004-operations-layout/verification.md).

## 4. Các mốc chức năng — 005 hoàn thành, 006 chờ kiểm tra cuối, 007–009 đề xuất

005 và source 006 đã triển khai theo yêu cầu trực tiếp; cần chạy nốt verification 006 trước khi chuyển mốc tiếp theo. Các ID 007–009 là dự kiến, xác nhận ID khi bắt đầu. Không coi lộ trình là source đã tồn tại.

| Thứ tự | Feature / trạng thái | Kết quả | Phụ thuộc |
| --- | --- | --- | --- |
| 005 | Xe và chuyến đi — đã triển khai | CRUD/xóa mềm xe, gán tuyến snapshot + giờ xuất phát; bắt đầu/kết thúc/hủy; timeline kế hoạch và chặn start đồng thời | Station/route hiện có |
| 006 | Telemetry và simulator — source đã có, chờ nghiệm thu | Geometry/clock/persistence/SSE/UI; JPA và browser fixture đạt, HTTP/SSE/full suite còn chờ hạn mức | 005 |
| 007 | Check-in tự động | Ghi duy nhất một lần cho mỗi lần ghé của một stop trong trip; xe đi ngang vùng trạm cũng được nhận diện | 005–006 |
| 008 | Traffic thực tế, ETA và simulator theo traffic | ETA mọi trạm còn lại cập nhật theo HERE; tốc độ giả lập phản ứng theo đoạn đường; có freshness và trạng thái thiếu dữ liệu | 005–007 |
| 009 | Đổi lộ trình/lịch trình và thông báo | Sự cố ảnh hưởng đường còn lại sinh revision và thông báo chứa lý do, trạm bị ảnh hưởng, giờ cũ/mới; không spam | 008 |

### 005 — Xe và chuyến đi — hoàn thành

**Đã có:** feature packages `vehicle`, `trip`; migration V4. `vehicles` gồm biển số unique chuẩn hóa, tên/mô tả, active; `trips` gắn vehicle/route immutable, lịch xuất phát và timestamps lifecycle; `trip_stops` snapshot thứ tự, tên/tọa độ/radius/dwell/offset và giờ kế hoạch. Lưu timestamps UTC, UI hiển thị ngày giờ địa phương (E13–E14).

**API đã có:** `/api/v1/vehicles`, `/api/v1/trips`, detail và start/complete/cancel. Service có transaction, vehicle lock và partial unique index bảo vệ race; retry cùng trạng thái đích không thay timestamp. Ngừng xe bị chặn khi còn chuyến chờ/đang thực hiện. Không thêm driver module (E13–E15).

**Đã kiểm tra:** lịch qua ngày/tuyến vòng, snapshot sau sửa station/xe, lifecycle và hai start đồng thời; UI form/confirmation/loading/error/retry, giữ draft/selection/map, responsive và regression trạm/tuyến. Chi tiết contract và bằng chứng: [spec](features/005-vehicles-trips/spec.md), [verification](features/005-vehicles-trips/verification.md). Để dùng database phát triển, khởi động lại backend cho Flyway áp dụng V4; lượt kiểm tra chỉ dùng database tạm. Nút khởi hành chưa chạy simulator hoặc cập nhật vị trí.

### 006 — Telemetry và simulator cơ bản — đang hoàn tất kiểm tra

**Đã có trong source:** HTTP GPS ingestion, history/latest và SSE snapshot từ Spring MVC. Payload có eventId, vehicleId/tripId, recordedAt, coords/speed/heading/accuracy, receivedAt server và source. SIMULATOR chỉ được phát từ backend qua cùng ingestion; GPS không trộn vào run simulator, bản tin cũ/collision bị từ chối. Không thêm MQTT/broker (E16).

Simulator nội suy theo khoảng cách segment trong section, play/pause/stop/reset, 1×/5×/10×, dwell theo occurrence. Lưu clock checkpoint ở backend, restart đưa running về paused; hết tuyến hoàn thành trip, reset tạo trip mới giữ history. UI ghi rõ GIẢ LẬP và thời lượng snapshot, không giả traffic live. Marker/follow, nguồn/freshness, status trip và controls cùng nhận snapshot (E17–E18).

**Kết quả/chờ kiểm tra:** compile Java và 24 test chọn lọc PostgreSQL đạt; lint/tsc/build và 39 nhóm browser fixture đạt. Chưa chạy HTTP/SSE test mới, browser nối Spring/PostgreSQL và full suite vì auto-review từ chối Maven/Docker do usage limit. Chưa đo mục tiêu <2 giây, chưa chứng minh end-to-end bằng database thật. Lệnh tiếp tục và evidence: [verification 006](features/006-telemetry-simulator/verification.md). Chưa đánh dấu hoàn thành.

### 007 — Check-in tự động

**Đề xuất:** xét stop tiếp theo trong trip, không quét và check-in mọi trạm gần xe. Visit key là `(trip_id, stop_sequence)`, không chỉ stationId để tuyến vòng ghé lại cùng trạm vẫn hợp lệ. Theo yêu cầu “đi ngang”, check-in phát sinh khi quỹ đạo đi vào/cắt vùng bán kính; không bắt buộc dừng xe. Tách “đi qua” khỏi “thực sự dừng” nếu cần trạng thái phục vụ hành khách sau này.

Xét cả đoạn nối hai mẫu GPS để không bỏ sót xe đi nhanh qua vòng nhỏ; chỉ suy ra giao cắt nếu khoảng cách thời gian/tốc độ/accuracy hợp lý, tránh kéo đoạn thẳng qua trạm khi mất GPS lâu. Dùng hysteresis cho check-out để chống nhiễu ở mép; thời gian nội suy phải được đánh dấu suy ra. DB unique đảm bảo retry/concurrency không nhân event. Theo quy tắc nghiệp vụ, điểm đầu check-in khi bắt đầu trip trong vùng; không tự check-in điểm cuối chỉ vì cùng tọa độ đầu trong tuyến vòng.

**Nghiệm thu:** trong vòng, ngoài vòng, sát ranh, đi ngang giữa hai mẫu, đứng yên nhiều mẫu, GPS trùng/ngược thứ tự, khoảng trống GPS dài, hai vùng chồng nhau, tuyến A→B→A, restart service rồi nhận lại event. Ghi actualArrivalAt, source và evidence vị trí; chỉ cập nhật stop occurrence hợp lệ. Chưa kiểm chứng bằng vòng tròn UI hiện có (E06).

### 008 — Traffic, ETA và nâng cấp simulator

**Đề xuất tích hợp:** xây traffic controller/service/provider server-side từ cấu hình sẵn có (E08). DTO nội bộ có geometry, speed với đơn vị rõ, jamFactor, traversability, confidence, observedAt/fetchedAt; incidents có ID, loại, mức độ, vị trí, thời gian hiệu lực. HERE Traffic cung cấp flow/speed và incident data; với đường đóng nên dùng incidents thay vì chỉ dựa jamFactor. [HERE Traffic introduction](https://docs.here.com/traffic-api/docs/introduction-to-here-traffic-api-v7), [Flow](https://docs.here.com/traffic-api/docs/flow).

Thu thập theo hành lang phần tuyến còn lại, ghép đúng đoạn/hướng di chuyển, không dùng mọi incident trong bounding box để báo trễ. Cache request chung và giới hạn chu kỳ, quota; TTL thử nghiệm 60 giây là đề xuất cần đo. Validate key khi traffic bật, timeout/rate-limit có lỗi kiểm soát, giữ last-known kèm tuổi dữ liệu, không đánh dấu “thông thoáng” khi provider lỗi. Cần chạy spike HERE thật trên các đoạn tại TP.HCM để xác nhận coverage/quyền truy cập trước khi chốt AC live.

**ETA:** từ vị trí xe và thời điểm hiện tại qua các stop còn lại, cộng thời gian dừng chưa hoàn thành; stop đã check-in hiển thị actual riêng. Dùng route/section duration có xét giao thông; không lấy khoảng cách thẳng chia tốc độ GPS tức thời. `baseDuration` là thời lượng không xét thời gian, không phải baseline lịch trình đã hứa và không bảo đảm luôn nhỏ hơn `duration`. [HERE duration](https://docs.here.com/routing/docs/routing-v8-duration).

Simulator cập nhật vận tốc theo flow trên đoạn đang chạy, dùng cùng ETA service như GPS. Gắn nguồn `HERE_LIVE`, `LAST_KNOWN` hoặc `ROUTE_SNAPSHOT` vào kết quả. Thời gian giả lập tăng tốc phải tách khỏi wall clock của traffic: chế độ live ưu tiên 1×; chạy nhanh vẫn phải hiển thị thời điểm traffic thực được lấy, không giả định provider biết tương lai. Tai nạn/công trường dùng fixtures cho test lặp lại; live mode chỉ hiển thị sự cố thật provider trả, có thể không có sự cố ở khu vực đang chọn.

**Nghiệm thu:** flow chậm làm tốc độ simulator giảm và ETA tăng; closure không bị diễn giải thành tốc độ thông thường; xe đứng yên không chia cho 0; mất provider có nhãn stale; incident ngoài đường/hết hạn không làm đổi ETA; test fixture và live evidence được báo riêng.

### 009 — Tính lại lộ trình và thông báo đổi lịch

**Đề xuất trigger:** đường đóng còn hiệu lực trên phần đường chưa đi, hoặc mức trễ vượt ngưỡng cấu hình ổn định qua hai lần cập nhật. Giá trị thử nghiệm: trễ ≥5 phút đồng thời ≥30% so với lịch baseline còn lại; đây là chính sách đề xuất, không phải ngưỡng chuẩn HERE. Jam factor cao chỉ là tín hiệu hỗ trợ, chưa đủ để tự đổi tuyến.

Tính từ vị trí hiện tại qua toàn bộ stop bắt buộc còn lại; lưu phương án thực thi thành trip route revision mới, không sửa snapshot tuyến gốc. Lưu baseline và revised schedule riêng. Chỉ áp dụng phương án khả dụng và có lợi theo ngưỡng; nếu không có đường thay thế thì giữ phương án hiện hành, đánh dấu bị chặn và thông báo để người điều hành xử lý. ETA có thể thay đổi mà geometry không đổi; vẫn cần thông báo nếu vượt ngưỡng đổi lịch.

Thông báo trong ứng dụng trước: trip/vehicle, nguyên nhân/sự cố, các trạm ảnh hưởng, ETA cũ→mới, generatedAt, revision, mức độ, readAt. Ghi revision và notification trong cùng transaction; SSE chỉ vận chuyển sự kiện sau commit. DB idempotency theo trip/revision/type, debounce/cooldown thử nghiệm 5 phút và nhận biết phục hồi để tránh nhảy qua lại. Chưa thêm email/SMS vì chưa được yêu cầu.

**Nghiệm thu:** một sự cố không sinh lặp thông báo qua nhiều tick; reload vẫn đọc được thông báo; hai client nhận cùng revision; stop đã hoàn thành không bị tính lại; không bỏ stop khi reroute; hết sự cố cập nhật có kiểm soát; timeout/không có tuyến thay thế không làm mất lộ trình đang chạy. Test end-to-end: simulator → traffic fixture → ETA thay đổi → revision → thông báo.

## 5. Cách nghiệm thu từng mốc

Luồng demo mục tiêu: tạo 3 trạm → tạo tuyến → tạo xe/trip → chạy simulator → marker và tốc độ đổi → check-in → traffic làm chậm → ETA từng stop đổi → reroute/lịch revision → thông báo. Tại mỗi mốc chạy backend tests, frontend lint/tsc/build và browser regression cho trạm/tuyến hiện có.

Những việc bổ trợ chỉ đưa vào kế hoạch khi cần: route rename/recalculate/deactivate phục vụ vận hành nhiều phiên bản; thiết bị GPS thật và định dạng giao thức sau khi có thiết bị cụ thể. Xác thực nguồn telemetry, quyền điều hành và chính sách giữ dữ liệu cần được chốt trước khi mở hệ thống ra ngoài môi trường phát triển; không tự triển khai các module này trong lượt layout.

## 6. Giới hạn xác minh của lượt hiện tại

- Frontend lint/tsc/build exit 0 (1887 modules). Browser 006/005/004 đạt tổng 39 nhóm bằng fixture, không lỗi JavaScript. [Verification 006](features/006-telemetry-simulator/verification.md).
- Compile backend và 24 test chọn lọc 006 đạt (0 failures/errors/skipped). HTTP/SSE/full suite và browser nối Spring/PostgreSQL đang chờ hạn mức auto-review. 118 pass mốc 005 không phải full suite của source 006.
- Chưa test HERE live, backend/database thật qua UI hay nguồn GPS; không đọc/đổi `.env`. Ảnh browser dùng fixtures rõ tên, không chứng minh dữ liệu vận hành thật.
- Tài liệu dưới `docs/` hiện untracked sau thay đổi `.gitignore` đồng thời được giữ nguyên (E12). `PROJECT_HANDOFF.md` lưu tóm tắt tiến độ/lộ trình; chưa commit/push.
