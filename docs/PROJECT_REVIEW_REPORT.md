# Báo cáo review toàn bộ website Vehicle Tracking

**Ngày review:** 2026-09-16 (Asia/Saigon)

**Commit nền:** `1af346a` (`master`, đồng bộ với `origin/master` tại thời điểm review)

**Working tree:** có thay đổi chưa commit ở `MapComponent.tsx`, `MapControls.tsx`, `TrafficLayer.tsx` và `workspace.css`

**Phạm vi:** backend, frontend, Flyway migration, test, cấu hình công khai, `PROJECT_HANDOFF.md` và tài liệu feature. Không đọc hoặc ghi lại giá trị secret từ các file `.env`.

## 1. Kết luận điều hành

Dự án **chưa đáp ứng đầy đủ để nghiệm thu toàn bộ bảy yêu cầu**, dù phần lớn lõi nghiệp vụ đã có code end-to-end. Trạng thái phù hợp nhất là **prototype tích hợp nâng cao, chưa production-ready**.

- Quản lý trạm đã có đủ CRUD từ UI đến PostgreSQL.
- Tạo tuyến, quản lý xe/chuyến, telemetry realtime, check-in, ETA theo từng trạm, simulator, reroute và thông báo đều có backend/frontend tương ứng.
- Hệ thống hiện dùng **HERE-only** cho routing và traffic. Google Routes/Map Tiles đã bị gỡ ở commit `e4cb0a7`; migration V13 đưa schema quay lại chỉ nhận `routing_provider = 'HERE'`.
- Simulator hỗ trợ nhiều xe ở lớp dữ liệu và marker, nhưng bản đồ hiện chỉ truyền tuyến của chuyến đang chọn vào `SimulationRoutesLayer`. Vì vậy hai xe có thể chạy đồng thời nhưng chỉ một tuyến được vẽ.
- Lỗi chèn trùng trong `MapComponent`, `MapControls` và `TrafficLayer` đã được sửa trên working tree; lint, TypeScript và production build hiện chạy được.
- Backend chưa có authentication/authorization hoặc cơ chế xác thực thiết bị GPS. Không nên công khai API khi chưa bổ sung trust boundary.
- Chưa có bằng chứng live đầy đủ với Docker/PostgreSQL, HERE thật và trình duyệt trên đúng phiên bản hiện tại.

Đánh giá định lượng theo bảy yêu cầu:

| Mức | Số yêu cầu | Diễn giải |
|---|---:|---|
| Đã triển khai đầy đủ trong phạm vi source | 1 | CRUD trạm |
| Đã có lõi nhưng còn điều kiện kiểm chứng/vận hành | 5 | Realtime, route, check-in, ETA, reroute/thông báo |
| Chưa hoàn chỉnh | 1 | Simulator nhiều xe/nhiều tuyến trên bản đồ |

## 2. Lưu ý về tài liệu handoff

`PROJECT_HANDOFF.md` là tài liệu lịch sử bắt đầu từ snapshot ngày 2026-09-13 và có nhiều đoạn mô tả giai đoạn cũ, ví dụ telemetry/simulator/trip từng chưa tồn tại. Các nhận định đó không còn phản ánh source hiện tại. Phần đầu file đã trỏ sang `docs/SESSION_HANDOFF.md`, nhưng cả hai vẫn chỉ nên dùng để tìm ngữ cảnh.

Nguồn sự thật khi đánh giá là:

1. Source code hiện tại.
2. Flyway migration V1–V13.
3. Test thực sự được chạy trong lần review này.
4. Tài liệu feature chỉ dùng để đối chiếu ý định, không thay thế evidence từ code.

## 3. Ma trận đối chiếu yêu cầu

### 3.1 Theo dõi vị trí xe realtime trên bản đồ

**Trạng thái: Đã triển khai có điều kiện.**

Đã có:

- `TelemetryController` tại `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/telemetry/controller/TelemetryController.java` cung cấp GPS ingest, history, snapshot và SSE `/api/v1/telemetry/stream`.
- `TelemetryService` xử lý telemetry từ GPS và simulator.
- `OperationsStreamService` phát snapshot hoạt động định kỳ.
- Frontend dùng `useLiveOperations`, `useVehicleMarkers`, `SimulationFleetLayer` và `MapComponent` để cập nhật marker, trạng thái kết nối, tốc độ và vị trí trên map.
- Có fallback snapshot/reconnect ở luồng frontend thay vì phụ thuộc hoàn toàn vào một kết nối SSE duy nhất.

Còn thiếu hoặc chưa chứng minh:

- Chưa có xác thực thiết bị, token riêng cho GPS, chống giả mạo hoặc replay request.
- Chưa có quy trình đăng ký thiết bị thật với vehicle.
- Chưa có load test cho nhiều xe và nhiều client SSE.
- Chưa nghiệm thu xuyên suốt thiết bị GPS → Spring → PostgreSQL → SSE → browser trên bản hiện tại.

Kết luận: phù hợp cho simulator/local demo; chưa đủ cho đội xe thật ngoài môi trường tin cậy.

### 3.2 Thêm, xóa, sửa trạm đầu/cuối/trạm dừng

**Trạng thái: Đã triển khai.**

Evidence:

- `StationController` có GET list/detail, POST, PUT và DELETE tại `station/controller/StationController.java:21-58`.
- `StationService` đảm nhiệm validation nghiệp vụ, chuẩn hóa dữ liệu và soft-delete.
- Migration `V2__create_stations_table.sql` tạo bảng, constraint và index.
- Frontend có `StationDrawer`, `StationPanel`, `useStationWorkspace`, chọn vị trí trên map và kéo marker.
- UI có loading, error, empty state, edit và xác nhận xóa.

Giải thích mô hình: START/STOP/END là vai trò của một station trong **một route cụ thể**, được suy ra từ thứ tự stop. Station không mang loại cố định, nên cùng một station có thể là điểm đầu ở tuyến A và điểm dừng ở tuyến B. Mô hình này phù hợp hơn việc khóa loại station toàn cục.

### 3.3 Tạo tuyến từ danh sách điểm dừng và dự kiến thời gian hoàn thành

**Trạng thái: Đã triển khai có điều kiện.**

Đã có:

- `RouteController` có create/list/detail/update/deactivate tại `route/controller/RouteController.java:22-55`.
- `RouteService` validate 2–50 điểm, station active, thứ tự stop, dwell time và lưu snapshot.
- `RouteShapeController` hỗ trợ preview, cập nhật shape và copy route.
- `HereRoutingProvider` gọi HERE Routing qua backend; API key không đưa xuống frontend.
- `RouteDetailResponse` trả distance, travel duration, base duration, dwell, total duration, stops và sections.
- `TripStartScheduleService` làm mới lịch dự kiến từ traffic sau khi chuyến được start nếu có dữ liệu HERE phù hợp.
- Frontend có `RouteWorkspace`, `RouteDrawer`, danh sách stop có reorder và hiển thị route/ETA snapshot.

Giới hạn hiện tại:

- Chỉ dùng HERE. `RoutingProviderName` hiện chỉ có `HERE`; migration `V13__remove_google_routes_feature.sql` loại Google khỏi schema.
- `RouteService` lưu `RouteTransportMode.CAR`. `vehicleType=MOTORCYCLE` hiện là loại hiển thị/quản lý xe, không tạo route bằng routing mode xe máy.
- `HERE_ROUTING_ENABLED` mặc định false; phải có cấu hình hợp lệ mới gọi provider được.
- Chưa chạy live verification với HERE trong lần review này.

Kết luận: yêu cầu route cho ô tô đã có trong source. Không thể coi Google hoặc routing xe máy là chức năng hiện hành.

### 3.4 Tự động check-in khi xe đi ngang trạm

**Trạng thái: Đã triển khai có điều kiện.**

Evidence:

- `CheckInService` tại `checkin/service/CheckInService.java` xử lý ba loại evidence `POINT`, `SEGMENT`, `ROUTE_TRACE`.
- Thuật toán xét giao cắt đoạn di chuyển với geofence, thứ tự stop, hysteresis, độ chính xác GPS và attempt/replay.
- Migration V6 tạo state/checkpoint/visit; V9 bổ sung attempt phục vụ replay simulator.
- `CheckInController` trả lịch sử check-in theo trip và attempt.
- Frontend hiển thị tiến độ và lịch sử check-in trong chi tiết chuyến.

Còn thiếu:

- Chưa có ma trận load/edge hoàn chỉnh cho GPS thưa, nhiều geofence chồng nhau, mất gói và vị trí nhiễu.
- Chưa nghiệm thu bằng GPS thật.
- Chưa có nghiệp vụ xác nhận đón/trả hành khách; hiện chỉ ghi nhận xe đi vào vùng trạm.

### 3.5 Tính thời gian xe đến từng trạm

**Trạng thái: Đã triển khai có điều kiện.**

Evidence:

- `TrafficEtaController` cung cấp `GET /api/v1/trips/{tripId}/eta`.
- `TrafficEtaService` tính next stop, tổng thời gian còn lại, ETA cho từng stop, affected segments, traffic source và status.
- `TripEtaResponse` chứa danh sách `EtaStop` thay vì chỉ một ETA tổng.
- `TripDetailPanel` và `TripTrafficSummary` hiển thị ETA, nguồn HERE live/last known/snapshot, trạng thái stale và blocked.
- Simulator dùng thời lượng section/traffic để suy ra tốc độ và thời gian còn lại.

Giới hạn:

- Khi HERE không khả dụng, hệ thống fallback về snapshot của route; giá trị đó không còn là traffic realtime.
- Khi tuyến bị blocked có thể không trả ETA thông thường.
- Cần kiểm chứng map matching ở đường song song, cầu vượt và đường nhiều nhánh bằng dữ liệu HCM thật.

### 3.6 Simulator nhiều xe, vị trí, tốc độ và ETA theo traffic

**Trạng thái: Chưa hoàn chỉnh.**

Phần đã có:

- `SimulationController` có play, pause, speed, stop, reset và history attempts tại `simulation/controller/SimulationController.java:8-16`.
- `SimulationService` quản lý run riêng theo trip; nhiều trip có thể chạy đồng thời.
- `RouteMotion` nội suy vị trí trên geometry và section timing.
- Có các mức playback 1x, 5x và 10x; đây là hệ số chạy thời gian mô phỏng.
- `SimulationFleetLayer` vẽ nhiều vehicle marker; `SimulationFleetList` cho chọn từng xe.
- Telemetry card hiển thị tốc độ hiện tại, trạm tiếp theo và ETA.
- Vận tốc vật lý được suy ra từ route/traffic; hiện chưa có cấu hình km/h cơ sở/tối đa theo xe.

Lỗi chặn nghiệm thu multi-route:

- `MapComponent.tsx:203` tạo `selectedSimulationRoutes` bằng cách lọc `simulationFleet.routes` theo `selectedTripId`.
- `MapComponent.tsx:758` truyền chính mảng đã lọc này vào `SimulationRoutesLayer`.
- `SimulationRoutesLayer.tsx:21-22` lại yêu cầu `selectedTripId` và lọc thêm lần nữa.

Hệ quả: nhiều xe/marker chạy cùng lúc, nhưng chỉ tuyến của xe đang chọn xuất hiện. Việc đổi xe làm tuyến cũ biến mất. Đây là lỗi đúng với hiện tượng đã quan sát trên giao diện.

Thiết kế đúng cần truyền toàn bộ `simulationFleet.routes`; `selectedTripId` chỉ quyết định màu, độ dày, z-order và thao tác focus. Route lỗi của một xe không được làm mất route các xe còn lại.

### 3.7 Tự động thông báo khi lịch trình thay đổi do sự cố/kẹt xe

**Trạng thái: Đã triển khai có điều kiện.**

Evidence:

- `ReroutePolicy` định nghĩa ngưỡng đánh giá.
- `RerouteEvaluationService` đọc ETA/traffic, yêu cầu đủ số lần vi phạm liên tiếp, áp cooldown/dedup và tạo route revision khi tuyến mới tốt hơn hoặc có closure.
- Migration V7 tạo route revision, traffic alert state và notifications.
- `NotificationController` hỗ trợ đọc danh sách, đánh dấu đã đọc và xóa.
- `RouteRevisionController` và `TripRouteGeometryController` cung cấp lịch sử/geometry revision.
- `AlertStream` hiển thị cảnh báo trong UI; snapshot/SSE cập nhật dữ liệu mới.

Giới hạn:

- Thông báo hiện là in-app; chưa có email, SMS hoặc push notification.
- Chỉ tự reroute khi nguồn traffic là HERE live/last-known đạt điều kiện policy.
- Chưa có live acceptance test với incident/closure thật và chưa kiểm thử tranh chấp khi nhiều instance scheduler chạy cùng lúc.

## 4. Đánh giá giao diện và layout

### 4.1 Phần đã đúng hướng

Layout Map-First đã được triển khai, không còn là dashboard dạng card chiếm phần lớn màn hình:

- Bản đồ phủ toàn viewport trong `MapComponent`.
- `ModeBar` chuyển Theo dõi, Tuyến & trạm và Mô phỏng.
- Panel/drawer bên trái chứa đội xe, chi tiết chuyến, trạm và route editor.
- `SimulatorPanel` và `AlertStream` nổi bên phải.
- Mobile dùng bottom sheet/panel thông qua các breakpoint trong `workspace.css`.
- Màu route/traffic/check-in có độ tương phản và trạng thái selected rõ.

### 4.2 Vấn đề hiện tại

1. **Multi-route không đúng:** panel hỗ trợ nhiều xe nhưng route layer chỉ nhận một tuyến.
2. **Nguồn bản đồ/traffic cần làm rõ:** working tree dùng traffic trong tile nền và chỉ vẽ incident HERE ở `TrafficLayer`; ETA/reroute vẫn dùng HERE qua backend. Cần bảo đảm UI ghi đúng nguồn của từng dữ liệu.
3. **MapComponent quá lớn:** file hơn 800 dòng sở hữu nhiều state/effect/layer. Việc sửa traffic dễ ảnh hưởng route, station và simulator.
4. **Thông tin provider chưa rõ:** UI nên cho biết HERE live, HERE last-known, route snapshot hoặc unavailable tại đúng nơi người vận hành ra quyết định.
5. **Hiệu năng:** bundle chính hiện khoảng 514 kB minified và vượt cảnh báo 500 kB.
6. **Lint debt:** `useFleetWorkspace.ts` còn hai warning `react(set-state-in-effect)`.

### 4.3 Hướng cải thiện layout phù hợp

Không cần thiết kế lại toàn bộ từ đầu. Nên giữ Map-First và sửa theo thứ tự:

1. Khôi phục traffic layer compile được và chốt một nguồn HERE qua backend.
2. Vẽ toàn bộ route mô phỏng cùng lúc; route đang chọn sáng/đậm hơn, route khác vẫn nhìn thấy.
3. Tách `MapComponent` thành controller + các hook/layer theo nghiệp vụ: base map, traffic, station editing, route planning, fleet simulation và viewport fitting.
4. Giữ simulator panel theo mô hình master-detail: danh sách xe gọn, telemetry chi tiết của xe đang chọn.
5. Chuẩn hóa các state loading/stale/error/provider-disabled ngay trên traffic pill và ETA card.
6. Lazy-load các workspace/panel lớn, đo lại bundle và tránh mount layer không dùng.

## 5. Provider và luồng dữ liệu hiện tại

### HERE Routing

- Backend: `HereRoutingProvider`, `HereRoutingHttpClientConfig`, `HereRoutingProperties`.
- Nhiệm vụ: tính geometry, distance, base duration, traffic-aware duration và section/waypoint cho route.
- Secret: `HERE_API_KEY` chỉ nằm ở backend.

### HERE Traffic

- Backend: `TrafficController`, HERE traffic client/service/cache.
- Nhiệm vụ: flow, incidents, tile/vector resource proxy, dữ liệu cho ETA và reroute.
- Frontend gọi API nội bộ qua `services/hereTraffic.ts`.

### Google

- Không còn là provider được hỗ trợ trong source hiện hành.
- Commit `e4cb0a7` đã gỡ Google routing/map tile provider.
- Migration V13 đưa database về HERE-only sau migration tương thích Google V12.
- URL Google raster đang xuất hiện trong `MapComponent` là base map phía client; working tree còn thử ghép traffic layer trực tiếp. Nó không phải Google Routes integration và không nên được xem là một chức năng backend đã hỗ trợ.

## 6. Backend, database và API

### Điểm tốt

- Tổ chức package theo feature.
- Controller nhận/trả DTO, service giữ nghiệp vụ.
- JPA entity không được trả trực tiếp qua API.
- Flyway V1–V13 là nguồn schema; `ddl-auto=validate` theo quy ước dự án.
- Có transaction, validation, soft-delete và snapshot để bảo toàn lịch sử.
- Route/trip/check-in/simulation/reroute có test unit/controller/integration tương ứng.

### Khoảng trống sản xuất

- Không tìm thấy Spring Security, `SecurityFilterChain`, authentication hoặc authorization.
- Không có user/role/tenant/fleet ownership.
- Không có device credential hoặc chữ ký request telemetry.
- Chưa có rate limit, audit log, idempotency boundary rõ cho GPS ingest.
- Scheduler và operations stream đang theo mô hình một instance; scale ngang có nguy cơ tick/đánh giá trùng.
- Chưa thấy retention policy cho telemetry/history/notifications, backup/restore drill hoặc observability đầy đủ.
- `compose.yaml` chỉ phục vụ PostgreSQL/pgAdmin, chưa phải deployment production của toàn hệ thống.

### Vấn đề quy trình tài liệu

`docs/features` có hai ID `010` và hai ID `011`, trái quy tắc ID duy nhất trong `docs/workflow.md`. Feature 018 mô tả Google nhưng code hiện tại đã gỡ Google; cần đánh dấu feature này superseded/removed để session sau không hiểu nhầm.

## 7. Bảo mật và cấu hình

- Frontend chỉ nên có `VITE_API_BASE_URL`; secret provider không được đưa vào `VITE_*`.
- HERE được gọi qua backend, phù hợp với trust boundary mong muốn.
- `.env.example` hiện chứa chuỗi có hình thức giống credential thật và lặp lại ở nhiều dòng. Nếu đó từng là key thật, cần revoke/rotate ngay, thay bằng placeholder vô hại và kiểm tra lịch sử Git/secret scanner. Báo cáo này không sao chép giá trị đó.
- CORS không thay thế authentication. Cấu hình CORS hiện có chỉ giới hạn origin/browser, không bảo vệ API trước client khác.

## 8. Kết quả kiểm tra thực tế

### 8.1 Frontend trên working tree hiện tại

Đã chạy:

```powershell
cd vehicletracking-frontend
npm.cmd run lint
node_modules\.bin\tsc.cmd --noEmit
npm.cmd run build
```

Kết quả:

| Kiểm tra | Kết quả | Chi tiết |
|---|---|---|
| Lint | Exit 0 | 2 warning tại `useFleetWorkspace.ts:93,101` |
| TypeScript | Exit 0 | `tsc --noEmit` hoàn tất không có lỗi |
| Vite build | Exit 0 | 1.916 modules; JS chính khoảng 514,15 kB; cảnh báo chunk >500 kB |

Vite build không tự chạy TypeScript, nên hai lệnh vẫn được kiểm tra riêng. Working tree hiện qua cả typecheck và build; hai warning lint vẫn còn.

### 8.2 Backend trên commit hiện tại

Đã chạy:

```powershell
cd vehicletracking-backend
mvn test
```

Kết quả: **196 test được phát hiện; 0 failure; 12 error; BUILD FAILURE**.

- 6 integration test class không khởi tạo được vì Testcontainers không truy cập được Docker named pipe: reroute, route repository, operations HTTP, operations service, station repository và fleet repository.
- 6 case của `RouteEnvironmentImportTest` chạy logic nhưng lỗi khi JUnit cleanup thư mục tạm do `AccessDeniedException`.
- 184 test còn lại hoàn tất không có assertion failure.
- Không được diễn giải kết quả này thành full pass. Cần chạy lại trên môi trường có Docker/PostgreSQL 17 và quyền filesystem bình thường.

### 8.3 Chưa kiểm tra trong lần review này

- HERE Routing/Traffic live với key/quota thật.
- Browser smoke end-to-end trên backend/database thật.
- GPS hardware/device thật.
- Nhiều route simulator sau khi sửa lỗi selected-only.
- Load, failover, backup/restore và multi-instance scheduler.

## 9. Ma trận rủi ro ưu tiên

| Mức | Vấn đề | Tác động | Hành động |
|---|---|---|---|
| P0 | Chỉ vẽ route đang chọn | Không đáp ứng simulator nhiều xe/nhiều tuyến | Truyền toàn bộ routes, selected chỉ đổi style/focus |
| P0 | Chưa có auth/device trust | API vận hành có thể bị đọc/ghi trái phép | Thiết kế user/role/device token/ownership trước khi public |
| P0 | Credential-like value trong `.env.example` | Nguy cơ lộ key và phát sinh chi phí | Rotate/revoke, thay placeholder, quét lịch sử |
| P1 | Full integration không chạy | Chưa chứng minh migration V13, transaction, SSE và reroute trên PostgreSQL | Chạy Docker/Testcontainers ở môi trường phù hợp |
| P1 | Traffic source bị trộn HERE/Google trong working tree | Dữ liệu/attribution không nhất quán, khó debug ETA | Chốt HERE-only và gọi qua backend |
| P1 | Scheduler/stream một instance | Có thể tick/notify trùng khi scale ngang | Distributed lock/leader hoặc worker riêng |
| P1 | Thiếu retention/observability/backup | Khó điều tra, kiểm soát dung lượng và phục hồi | Metrics, structured logs, retention, backup/restore |
| P2 | Không cấu hình speed profile km/h | Khó mô phỏng các loại xe/kịch bản tốc độ | Thêm base/max speed riêng, tách khỏi playback multiplier |
| P2 | Bundle lớn và lint warning | Tăng tải ban đầu, tăng debt frontend | Tách chunk và sửa state effect |

## 10. Kế hoạch chức năng tiếp theo

### Giai đoạn 1 — ổn định bản hiện tại

1. Đã sửa các đoạn code bị chèn trùng; lint, typecheck và build đều có exit code 0.
2. Làm rõ trên UI rằng traffic nền và incident/ETA/reroute có thể đến từ các luồng dữ liệu khác nhau.
3. Sửa multi-route: truyền toàn bộ `simulationFleet.routes`, vẽ route khác màu theo vehicle, route selected nổi bật.
4. Regression thủ công với ít nhất hai trip có geometry khác nhau, hai xe chạy đồng thời, chuyển xe, pause/reset và toggle route/traffic.

Điều kiện hoàn thành: không còn lỗi TypeScript; hai xe hiển thị đủ hai marker và hai route; đổi xe không làm mất route còn lại.

### Giai đoạn 2 — xác minh tích hợp

1. Bật Docker/PostgreSQL 17 và chạy toàn bộ Flyway V1–V13.
2. Chạy `mvn test` full; phân biệt lỗi source với lỗi môi trường.
3. Chạy browser end-to-end qua Spring/PostgreSQL: CRUD trạm, route HERE, vehicle/trip, start, simulation, check-in, ETA, reroute, notification.
4. Kiểm tra SSE hai client, reconnect và không nhân đôi subscriber/tick.
5. Chạy HERE live tại HCM cho flow, incidents, stale fallback, closure và provider timeout/quota.

### Giai đoạn 3 — hoàn thiện simulator

1. Thêm speed profile nghiệp vụ nếu cần: tốc độ cơ sở/tối đa theo vehicle/trip; giữ 1x/5x/10x là playback multiplier.
2. Hiển thị source/age của traffic cạnh speed và ETA.
3. Bổ sung test cho nhiều route, route trùng đoạn, route lỗi, replay attempt và revision giữa chuyến.
4. Xác định retention/export cho simulation attempts và telemetry.

### Giai đoạn 4 — sẵn sàng vận hành

1. Authentication, RBAC, tenant/fleet ownership và device credential.
2. Rate limit, audit, idempotency, provider health/quota alert.
3. Distributed scheduler/lock, HTTPS, secret manager và deployment manifest.
4. Metrics/log/tracing, retention, backup/restore và runbook.
5. Load test theo mục tiêu thực tế về số xe, số client SSE và tần suất GPS.

## 11. Checklist nghiệm thu cuối

- [ ] Frontend lint, TypeScript và production build cùng pass.
- [ ] Backend full Maven test pass trên Docker/PostgreSQL.
- [ ] HERE Routing tạo route CAR từ 2–50 stop và lưu snapshot hoàn chỉnh.
- [ ] Hai xe chạy đồng thời hiển thị đủ hai marker và hai route.
- [ ] Speed/ETA thay đổi theo section traffic; UI nêu rõ live, last-known hoặc snapshot.
- [ ] GPS/simulator check-in đúng thứ tự, chống trùng và tách attempt.
- [ ] ETA từng stop cập nhật sau check-in và khi traffic thay đổi.
- [ ] Closure/jam đủ ngưỡng tạo đúng một revision/notification, áp cooldown/dedup.
- [ ] SSE reconnect không nhân đôi listener/tick.
- [ ] Có auth/role/device trust trước khi mở API ra mạng công cộng.
- [ ] Không còn secret thật trong file mẫu/lịch sử đang phân phối.
- [ ] Có monitoring, retention và backup/restore trước khi production.

## 12. Kết luận cuối

Nếu mục tiêu là demo/local development, dự án đã có nền tảng khá đầy đủ. Lỗi TypeScript của traffic layer đã được sửa; việc cần ưu tiên tiếp theo là simulator chỉ vẽ route đang chọn.

Nếu mục tiêu là khẳng định “đã đủ toàn bộ yêu cầu” hoặc triển khai vận hành thật, câu trả lời hiện tại là **chưa**. Sau khi sửa hai P0 về frontend, dự án vẫn cần full integration/live-provider evidence và lớp bảo mật/vận hành trước khi nghiệm thu.
