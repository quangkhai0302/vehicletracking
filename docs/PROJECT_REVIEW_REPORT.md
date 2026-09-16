# Báo cáo review toàn bộ website Vehicle Tracking

**Ngày review:** 2026-09-16 (Asia/Saigon)

**Commit được review:** `c83f96f`

**Phạm vi:** source backend, source frontend, Flyway migration, cấu hình công khai, test và bằng chứng trong `docs/features/`. Không đọc hoặc ghi giá trị secret trong các file `.env`.

## Kết luận điều hành

Website hiện đã vượt qua giai đoạn demo giao diện: các luồng trạm, tuyến, xe, chuyến đi, telemetry, check-in, traffic/ETA, mô phỏng, reroute và thông báo đều có source backend/frontend tương ứng. Kiến trúc Map-First cũng đã được triển khai với bản đồ chiếm toàn màn hình, thanh chuyển chế độ, drawer trạm/tuyến ở bên trái và panel mô phỏng/cảnh báo ở bên phải.

Tuy nhiên, sản phẩm **chưa nên coi là hoàn thiện hoặc sẵn sàng production**. Có bốn nhóm việc còn ảnh hưởng trực tiếp đến yêu cầu:

1. Bản đồ mô phỏng hiện vẫn chỉ truyền tuyến của xe đang chọn vào `SimulationRoutesLayer`; vì vậy nhiều xe có thể chạy nhưng không phải lúc nào tất cả tuyến đều được vẽ đồng thời. Đây là hồi quy cần sửa trước khi nghiệm thu multi-vehicle.
2. Google Routes và Google Map Tiles đã có adapter/config nhưng đang tắt mặc định và chưa được xác minh bằng billing, API live, PostgreSQL V12 và browser production. HERE vẫn là đường mặc định.
3. Kiểm tra backend trên máy review không đạt toàn bộ vì Docker/Testcontainers bị chặn; 197/209 test chạy qua, 12 test lỗi môi trường hoặc integration chưa khởi tạo được. Frontend lint/typecheck/build đạt nhưng còn 2 warning lint và cảnh báo bundle chính vượt 500 kB.
4. Chưa có authentication/authorization, onboarding thiết bị GPS, multi-tenant, audit, rate limit, observability, backup/retention và cơ chế scheduler nhiều instance. Đây là các khoảng trống vận hành/bảo mật nếu đưa ra ngoài môi trường local.

Đánh giá tổng thể: **feature core đã triển khai, bản prototype tích hợp tốt; chưa đủ bằng chứng và hardening để gọi là production-ready**.

## 1. Tiêu chí đánh giá

- **Đã triển khai:** có source nghiệp vụ, API/UI liên quan và có test hoặc bằng chứng phù hợp.
- **Đã triển khai có điều kiện:** source đã có nhưng phụ thuộc key/provider, migration, môi trường hoặc luồng chưa được nghiệm thu đầy đủ.
- **Chưa hoàn thiện:** thiếu source, hoặc source hiện tại chưa đáp ứng đầy đủ hành vi yêu cầu.
- **Chưa xác minh:** có thể đã có code/test fixture nhưng chưa có bằng chứng chạy xuyên suốt với backend, database, browser hoặc provider thật.

Tên chức năng trong báo cáo được đối chiếu từ source hiện tại. Các tài liệu handoff cũ chỉ được dùng để tìm điểm cần kiểm tra, không dùng thay cho code hiện tại.

## 2. Đối chiếu bảy yêu cầu nghiệp vụ

| Yêu cầu | Trạng thái hiện tại | Bằng chứng source | Khoảng trống còn lại |
| --- | --- | --- | --- |
| Theo dõi vị trí xe realtime trên bản đồ | **Đã triển khai có điều kiện** | `telemetry/controller/TelemetryController.java` có GPS ingest, snapshot, history và SSE; `telemetry/service/OperationsStreamService.java` phát snapshot mỗi giây; frontend `useLiveOperations.ts`, `useVehicleMarkers.ts`, `MapComponent.tsx` hiển thị marker, heading, speed, freshness và follow | Chưa có driver/device authentication, provisioning GPS thật, chống giả mạo thiết bị, load test và nghiệm thu browser → Spring → PostgreSQL với dữ liệu vận hành thật |
| Thêm, sửa, xóa trạm đầu/cuối/trạm dừng | **Đã triển khai** | `station/controller/StationController.java`, `StationService.java`, migration V2; `StationDrawer`, `StationPanel`, `useStationWorkspace`; có xóa mềm, tọa độ, bán kính check-in, map picker và kéo marker | START/STOP/END là vai trò theo thứ tự trong tuyến, chưa phải thuộc tính cố định của trạm. Tìm kiếm hiện chủ yếu theo tên/địa chỉ; chưa có tìm tọa độ nâng cao |
| Tạo tuyến từ danh sách điểm dừng và tính thời gian chuyến | **Đã triển khai có điều kiện** | `RouteService.create/update`, `RouteController`, `RouteShapeService`; validate 2–50 điểm, thứ tự, dwell, lưu polyline/section/summary; `RouteDrawer` và `SortableStopList`; Google/HERE provider | HERE hiện chỉ nhận `CAR`; `MOTORCYCLE` cần Google Routes. Google đang tắt và chưa live verify. Nội dung provider có expiry nhưng chưa có quy trình tự refresh trước khi dùng ngoài local |
| Xe tự check-in khi đi ngang trạm | **Đã triển khai có điều kiện** | `CheckInService` xử lý POINT, SEGMENT và ROUTE_TRACE, xét thứ tự, hysteresis, độ chính xác, khoảng cách và revision; V6 tạo checkpoint/visit; `CheckInController`, `useTripCheckIns`, timeline UI | Chưa có load/edge matrix đầy đủ (nhiều xe, mẫu thưa, overlap, fault injection). Check-in là ghi nhận đi qua vùng; chưa có nghiệp vụ đón/trả khách |
| Tính thời gian xe đến từng trạm | **Đã triển khai có điều kiện** | `TrafficEtaService.calculate` trả `TripEtaResponse.stops`, next stop, ETA từng stop, baseline, affected segments, source/status; UI `TripDetailPanel` và `TripTrafficSummary` hiển thị ETA/nguồn/cảnh báo | Khi thiếu vị trí/provider sẽ fallback snapshot; khi BLOCKED không có ETA. Cần nghiệm thu live provider, các tuyến song song/cầu vượt và đảm bảo cập nhật đúng theo từng road-link trong dữ liệu thực |
| Simulator chạy tuyến, hiển thị vị trí, vận tốc, ETA theo traffic | **Đã triển khai nhưng còn lỗi UI multi-route** | `SimulationService`, `RouteMotion`, scheduler, simulation attempts V9; các nút play/pause/stop/reset và multiplier 1×/5×/10×; `useSimulator`, `useSimulationFleet`, `SimulationFleetLayer`, `SimulationRoutesLayer`; HERE flow/incidents và Google traffic intervals được nối vào ETA/motion | Vận tốc vật lý được suy ra từ route/traffic; chưa có trường cấu hình km/h cơ sở hoặc speed profile riêng cho từng xe. Source hiện truyền `selectedSimulationRoutes` từ `MapComponent` và layer tiếp tục lọc theo `selectedTripId`, nên chưa bảo đảm tất cả tuyến mô phỏng cùng hiển thị |
| Tự thông báo đổi lịch khi kẹt xe/sự cố nghiêm trọng | **Đã triển khai source, chưa production verify** | V7 tạo revisions/checkpoint/notifications; `RerouteEvaluationService` dùng snapshot → provider → compare-and-write; `ReroutePolicy` có ngưỡng trễ, closure, consecutive fetch và cooldown; `NotificationController`, SSE snapshot, `AlertStream` | Chỉ có thông báo trong ứng dụng; chưa có email/SMS/push. Chưa có integration cạnh tranh PostgreSQL/live HERE cuối cùng; chưa điều khiển dẫn đường của thiết bị thật |

### Nhận xét về mức hoàn thành

Các yêu cầu chính đã có đường đi xuyên lớp dữ liệu, API và UI. Phần còn thiếu không nằm ở một nút giao diện đơn lẻ mà chủ yếu ở ba lớp: provider thật, nghiệm thu tích hợp và khả năng vận hành an toàn. Do đó không nên công bố phần trăm hoàn thành duy nhất cho toàn dự án; mỗi yêu cầu có mức sẵn sàng khác nhau như bảng trên.

## 3. Review giao diện Map-First

### Những phần phù hợp với thiết kế đã chốt

- `MapComponent` là canvas Leaflet toàn viewport; `ModeBar` chuyển giữa Theo dõi, Tuyến & trạm và Mô phỏng.
- Drawer trái giữ context của trạm, tuyến, xe và chuyến; form có loading/error/empty state, giữ draft và xác nhận thao tác phá hủy.
- Panel mô phỏng và cảnh báo nổi bên phải; mobile chuyển thành sheet/panel thu gọn qua `useCompactLayout` và `useMapCamera`.
- Marker được giữ lại để tránh nhấp nháy khi SSE cập nhật; follow chỉ dịch camera khi tọa độ mới.
- Route inspection trên tuyến đã chọn hiển thị section, distance, duration snapshot, flow/incidents gần đó và tuổi dữ liệu. Các đường nền không còn bị tải thông tin flow hàng loạt theo hover, phù hợp yêu cầu đã chốt sau feature 010.
- Traffic layer có HERE raster flow/incidents; route Google có interval màu riêng và HERE area flow không bị chồng màu lên đường Google.
- Có keyboard semantics cho danh sách và route picker, cleanup layer/listener/timer ở các hook/component chính.

### Vấn đề cần sửa trước khi nghiệm thu UI

#### P0 — nhiều xe chạy nhưng bản đồ không hiển thị đủ tuyến

Trong `MapComponent.tsx`, `selectedSimulationRoutes` được tạo bằng cách lọc `simulationFleet.routes` theo `selectedTripId`, rồi prop này được truyền vào `SimulationRoutesLayer`. Bên trong `SimulationRoutesLayer.tsx`, danh sách lại tiếp tục lọc theo `selectedTripId`. Vì vậy layer hiện chỉ nhận/vẽ tuyến được chọn, dù `useSimulationFleet` đã tải route cho nhiều trip.

Hệ quả: UI có thể hiển thị nhiều marker/xe và cho phép đổi xe, nhưng không đáp ứng đầy đủ yêu cầu “nhiều xe một lượt, tất cả tuyến cùng hiển thị”. Cần truyền toàn bộ `simulationFleet.routes` cho lớp geometry; chỉ dùng `selectedTripId` để đổi màu, độ dày, popup và route inspection. Sau đó phải thêm regression browser với ít nhất hai geometry khác nhau, hai tuyến chồng đoạn và một route lỗi riêng lẻ.

#### P1 — trạng thái provider chưa đủ rõ khi thiếu key

`application.yaml` mặc định `HERE_ROUTING_ENABLED=false`, `HERE_TRAFFIC_ENABLED=false`, `GOOGLE_ROUTES_ENABLED=false`, `GOOGLE_MAP_TILES_ENABLED=false`. UI đã có thông báo lỗi/fallback, nhưng màn hình khởi động chưa có health/config summary cho operator biết provider nào đang sẵn sàng trước khi tạo tuyến.

#### P1 — nền Google đang có đường fallback trực tiếp

`MapComponent.tsx` dùng endpoint backend khi `VITE_GOOGLE_MAP_TILES_ENABLED=true`, nhưng khi false lại tạo URL tile `google.com/vt/...` trực tiếp. Cách này không đưa secret vào frontend nhưng phụ thuộc endpoint không chính thức/điều khoản và có thể thất bại khác nhau giữa môi trường. Production nên chọn một nguồn nền được cấp phép và xác minh attribution, quota, cache và điều khoản; không coi fallback tile hiện tại là cam kết SLA.

#### P2 — bundle chính và cảnh báo lint

Build hiện tạo chunk chính khoảng **516.79 kB sau minify**. Lint đạt exit code 0 nhưng còn hai warning `react(set-state-in-effect)` tại `src/hooks/useFleetWorkspace.ts` dòng 93 và 101. Không phải lỗi chức năng ngay lập tức, nhưng nên tách thêm lazy boundary hoặc sửa state flow trước khi mở rộng dashboard.

## 4. Review backend, dữ liệu và API

### Những phần đã làm tốt

- Tổ chức theo feature (`station`, `route`, `vehicle`, `trip`, `telemetry`, `simulation`, `traffic`, `checkin`, `reroute`) đúng với cấu trúc repository.
- Controller nhận DTO và chuyển tiếp service; JPA entity không được dùng trực tiếp làm response.
- Flyway là nguồn sự thật schema, `ddl-auto=validate`, migration V1–V12 không sửa ngược migration cũ.
- Telemetry có event idempotency, validation tọa độ/tốc độ/thời gian, latest position và history có phân trang/attempt.
- Simulator có clock backend, dwell, route geometry, pause/recovery, reset theo attempt; không dùng tốc độ playback làm tốc độ km/h thực.
- Traffic cache có fresh/stale/unavailable; provider lỗi không làm rollback bản ghi telemetry nhờ trigger reroute sau commit.
- Reroute tách HTTP provider ra khỏi transaction ghi, đối chiếu attempt/route/revision/sample trước khi persist; notification dedupe bằng database key.
- Google request có field mask, giới hạn concurrent/minute/day, single-flight và giới hạn waypoint; key chỉ nằm ở backend properties.

### Các rủi ro/thiếu hụt cần đưa vào backlog

1. **Không có authentication/authorization.** Các endpoint CRUD, GPS ingest, simulator command, notification read/delete và reroute revision hiện không có lớp xác thực người dùng hoặc thiết bị. CORS chỉ giới hạn origin, không thay thế authentication.
2. **Không có tenant/ownership.** Dữ liệu không gắn organization/user; mọi client hợp lệ ở origin có thể nhìn cùng fleet nếu truy cập được API.
3. **Scheduler chỉ phù hợp một backend instance.** `SimulationScheduler` và `OperationsStreamService` dùng executor trong process; chạy hai instance sẽ tạo tick/stream trùng nếu không có leader/lock phân tán.
4. **Chưa có rate limit và quota nội bộ.** GPS ingest, SSE subscriber, traffic bbox, map tile và simulator command cần giới hạn theo user/device/IP trước khi mở public.
5. **Chưa có observability đầy đủ.** Cần health check provider/database, metrics latency/cache hit/provider error, structured audit và correlation id cho các lệnh vận hành.
6. **Retention/backup chưa hoàn thiện.** Telemetry, notifications, route geometry và simulation attempts có thể tăng không giới hạn; cần retention, archive, backup/restore và kiểm thử khôi phục.
7. **Provider content và billing chưa được vận hành.** Google route/tiles phụ thuộc billing/quota; HERE traffic/routing phụ thuộc key/quota. Cần health/config screen, cảnh báo quota và runbook thay key.
8. **Bảo mật cấu hình:** file `.env.example` trong workspace đang chứa giá trị có hình dạng credential HERE thay vì placeholder vô hại. Không đưa giá trị đó vào báo cáo; cần thu hồi/rotate nếu đó là key thật, thay bằng placeholder và bảo đảm file không được commit.

## 5. Review theo nhà cung cấp bản đồ/định tuyến

| Nguồn | Vai trò hiện tại | Trạng thái |
| --- | --- | --- |
| HERE Routing | Tính tuyến mặc định, polyline Flexible, duration/baseDuration, notices; hiện chỉ hỗ trợ `CAR` trong `HereRoutingProvider` | Có source và fixture/unit test; live route phụ thuộc `HERE_API_KEY` và `HERE_ROUTING_ENABLED` |
| HERE Traffic | Flow, jam factor, speed/free-flow, incidents, closure, traffic tiles và stale cache | Có backend proxy/cache/ETA/reroute/UI; chưa nghiệm thu toàn luồng live ở máy review |
| Google Routes | Tính route explicit theo route/provider, `DRIVE`/`TWO_WHEELER`, traffic-aware polyline, static/travel duration, speed intervals | Có adapter và guard; mặc định tắt, chưa billing/live/PostgreSQL/browser verify |
| Google Map Tiles | Backend session/tile proxy cho ROADMAP/SATELLITE/DARK | Có source/test; cần billing/key/quota/attribution, chưa live verify |
| Google trực tiếp trong frontend | Fallback tile URL khi official proxy tắt | Chỉ nên coi là fallback phát triển; cần quyết định nguồn nền hợp lệ cho production |

Điểm cần nhớ: Google Routes và HERE Traffic đang được dùng theo vai trò khác nhau. Chọn `ROUTING_PROVIDER=GOOGLE` không tự làm HERE Traffic biến mất; ETA Google dùng duration Google cho tuyến Google, còn HERE vẫn được dùng để phân tích flow/incidents/closure theo chính sách hiện tại. Ngược lại, nếu Google billing không hoạt động, route HERE CAR vẫn là đường khả dụng khi HERE key/config hợp lệ.

## 6. Kiểm tra thực tế trong lần review này

### Backend

Đã chạy:

```powershell
cd vehicletracking-backend
mvn test
```

Kết quả: **209 test được khám phá; 0 assertion failure; 12 error; build failure**.

- Các test logic/unit/controller không dùng Docker chạy qua.
- Các lỗi integration (`RerouteSimulationIntegrationTest`, `RouteRepositoryIntegrationTest`, `OperationsIntegrationTest`, `OperationsHttpIntegrationTest`, `StationRepositoryIntegrationTest`, `FleetRepositoryIntegrationTest`) không khởi tạo được Testcontainers vì Docker named pipe bị từ chối quyền.
- Sáu test `RouteEnvironmentImportTest` còn lỗi khi extension dọn thư mục tạm do `AccessDeniedException` trong môi trường review; chưa đủ cơ sở coi đây là lỗi nghiệp vụ của application.
- Vì vậy không ghi nhận “full backend pass”. Cần chạy lại trên máy có Docker/Testcontainers và quyền filesystem bình thường.

Lệnh wrapper `mvnw.cmd test` cũng không chạy được ở PowerShell do lỗi `Cannot index into a null array` của wrapper; lần kiểm tra đã dùng Maven cài sẵn (`Apache Maven 3.9.15`) để tránh nhầm wrapper failure với source failure.

### Frontend

Đã chạy:

```powershell
cd vehicletracking-frontend
npm.cmd run lint
node_modules\\.bin\\tsc.cmd --noEmit
npm.cmd run build
```

- Lint: exit 0, **2 warning** tại `useFleetWorkspace.ts:93,101`.
- TypeScript: exit 0.
- Vite build: exit 0, 1,917 modules; chunk chính khoảng 516.79 kB sau minify, có cảnh báo vượt ngưỡng 500 kB.

### Browser/live/provider

Trong lần review này chưa chạy lại browser smoke nối server thật, HERE live, Google live hoặc GPS thật. Repository có fixture/browser artifacts của các feature trước; chúng hữu ích cho hồi quy nhưng không thay thế nghiệm thu production. Cần chạy lại sau khi sửa multi-route và khi có Docker/key hợp lệ.

## 7. Ma trận rủi ro ưu tiên

| Mức | Vấn đề | Tác động | Việc cần làm |
| --- | --- | --- | --- |
| P0 | `SimulationRoutesLayer` chỉ nhận route đang chọn | Sai yêu cầu nhiều xe/nhiều tuyến hiển thị đồng thời | Truyền toàn bộ fleet routes; thêm browser regression hai route khác geometry, overlap, route lỗi và mobile |
| P0 | Không có auth/device trust | Bất kỳ client truy cập API có thể đọc/ghi dữ liệu vận hành | Chốt mô hình user/device/role, token, quyền theo fleet và audit trước khi public |
| P0 | Credential-like value trong `.env.example` | Có nguy cơ lộ/tiếp tục sử dụng key thật | Rotate/revoke, thay placeholder, kiểm tra lịch sử Git và secret scanner |
| P1 | Google chưa billing/live verify | Không tạo được route Google/map tiles trong môi trường cần Google | Giữ HERE làm mặc định; kiểm tra billing/key/quota/attribution trên project riêng trước khi bật |
| P1 | Full integration không chạy do Docker | Chưa chứng minh migration V12, SSE/transaction/reroute cạnh tranh | Chạy trên máy có Docker/PostgreSQL 17; lưu log và artifact mới |
| P1 | Scheduler/stream một instance | Trùng tick, trùng telemetry hoặc SSE khi scale ngang | Distributed lock/leader hoặc chuyển scheduler/stream sang hạ tầng phù hợp |
| P1 | Không có retention/backup/observability | Tăng chi phí, khó điều tra và khôi phục sự cố | Metrics, log structured, alert, retention và restore drill |
| P2 | Không có speed profile km/h cấu hình được | Không mô phỏng được kịch bản vận tốc cố định hoặc đội xe khác nhau | Thêm profile cơ sở/max speed theo vehicle/trip, tách rõ với playback multiplier |
| P2 | Bundle lớn và lint warnings | Tăng thời gian tải, khó bảo trì | Lazy-load panel/layer, xử lý hai warning, đo bundle budget |

## 8. Kế hoạch thực hiện tiếp theo

### Giai đoạn 1 — sửa lỗi chức năng đang nhìn thấy

1. Sửa `SimulationRoutesLayer`/`MapComponent` để vẽ toàn bộ `simulationFleet.routes`; route chọn chỉ đổi style và focus.
2. Thêm test unit cho dữ liệu fleet và browser test: hai route khác nhau, hai route dùng chung đoạn, một route lỗi không làm mất route còn lại, toggle layer, reload và mobile.
3. Chạy lint/typecheck/build và kiểm thử thủ công với ít nhất hai chuyến `IN_PROGRESS` thật.

### Giai đoạn 2 — xác minh tích hợp

1. Bật Docker/PostgreSQL 17, chạy Flyway V1–V12 và `mvn test` đầy đủ.
2. Chạy HTTP/SSE browser qua Spring, kiểm tra hai subscriber, reconnect, check-in, ETA, reroute và reset attempt.
3. Với HERE: xác minh route, flow, incidents, stale fallback và closure tại khu vực HCM.
4. Với Google: dùng project billing riêng, giới hạn quota thấp, kiểm tra Google Routes CAR/MOTORCYCLE, Map Tiles session, attribution và expiry; không bật mặc định trước khi pass.

### Giai đoạn 3 — hoàn thiện simulator theo nhu cầu vận hành

1. Thêm cấu hình speed profile: tốc độ cơ sở, tốc độ tối đa, dwell và policy traffic theo từng vehicle/trip.
2. Hiển thị đồng thời route, marker, tốc độ, ETA và trạng thái check-in của nhiều xe; tránh để panel chọn một xe làm mất dữ liệu các xe khác.
3. Thêm bảng lịch sử attempt/telemetry với filter và export nếu cần; xác định retention.

### Giai đoạn 4 — production hardening

1. Authentication, role/permission, device token và ownership theo tenant/fleet.
2. Rate limit, idempotency boundary, audit log, provider health/quota alerts.
3. Distributed scheduler/lock, deployment manifest, HTTPS, secret manager, backup/restore và runbook.
4. Performance test cho 10–50 xe, 2–10 client SSE, 50 stop/route và provider timeout/rate-limit.

## 9. Checklist nghiệm thu cuối

- [ ] Hai xe đang chạy hiển thị đủ hai tuyến cùng lúc trên map.
- [ ] Đổi xe chỉ đổi panel/focus, không làm mất route/marker của xe khác.
- [ ] Tạo route HERE CAR thành công khi key hợp lệ; lỗi provider hiển thị rõ và không lưu route dở dang.
- [ ] Tạo route Google CAR/MOTORCYCLE thành công trên project billing test; geometry/ETA/interval decode đúng.
- [ ] Sửa station/route không làm thay đổi snapshot của trip đã chạy.
- [ ] GPS và simulator check-in đúng thứ tự, chống trùng và tách attempt.
- [ ] ETA hiển thị source, tuổi dữ liệu, blocked/stale/fallback và từng stop.
- [ ] Reroute chỉ tạo sau đủ breach/fetch, không tạo trùng, revision giữ stop còn lại.
- [ ] Hai client SSE nhận cùng snapshot; reconnect không nhân đôi subscriber/tick.
- [ ] Full Maven pass trên Docker/PostgreSQL; frontend lint/typecheck/build pass với bundle budget đã chấp thuận.
- [ ] Có auth/role, rate limit, audit, health/metrics, retention và backup trước khi public.

## 10. Kết luận cuối

Nếu mục tiêu là tiếp tục phát triển bản local/demo, dự án đã có nền tảng tốt và có thể dùng HERE làm provider mặc định. Việc cần ưu tiên ngay là sửa hồi quy hiển thị nhiều tuyến, sau đó chạy lại integration trên Docker.

Nếu mục tiêu là vận hành thật, chưa nên bật Google hoặc mở API công khai chỉ dựa trên các fixture hiện có. Cần hoàn thành xác minh provider, xử lý credential trong `.env.example`, bổ sung auth/device trust và có bằng chứng load/backup/observability trước khi tuyên bố sản phẩm đáp ứng đầy đủ yêu cầu vận hành.
