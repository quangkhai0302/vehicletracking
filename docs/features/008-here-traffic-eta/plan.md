# Kế hoạch 008 — HERE Traffic, ETA động và simulator theo giao thông

Trạng thái: **Implementing** · Ngày cập nhật: 2026-09-14 · Đã triển khai source MVP; P6 browser/full-suite hardening còn thiếu. Live spike upstream đã pass. Kế hoạch chỉ bắt đầu
sau khi người dùng duyệt; mỗi bước phải giữ regression 005–007 và secret boundary.

## Quyết định và điều kiện bắt đầu

- Feature 008 lấy HERE Traffic API v7 làm provider JSON cho MVP; backend gọi /v7/flow và
  /v7/incidents, đồng thời proxy HERE Traffic Raster Tile API v3 cho lớp bản đồ. Frontend
  chỉ gọi API nội bộ.
- Hai hình thức truy vấn: bbox cho overlay bản đồ, corridor Flexible Polyline cho ETA
  remaining route. Không coi mọi incident trong bbox là ảnh hưởng trip.
- Cache một backend instance: fresh 60 giây, last-known tối đa 300 giây; request cùng key
  được dedupe. Không thêm Redis hoặc scheduler phân tán.
- Route snapshot 005, telemetry/simulator 006 và check-in 007 là baseline bất biến; 008
  không tạo route revision/reroute/notification.
- Closure tạo metadata BLOCKED/ETA không khả dụng và không chạy theo baseline như đường
  mở. Cách hiển thị/pause run phải được kiểm tra cùng API hiện có, không thêm status tùy tiện.
- Trước implementation phải có quyết định bán kính corridor (mặc định đề xuất 100 m), giới
  hạn bbox, jam thresholds, policy fallback và kiểm tra context khi cả routing/traffic
  RestClient cùng tồn tại.
- Chỉ live spike dùng key ephemeral đã rotate; deterministic tests dùng stub/fixture. Không
  đọc/in giá trị .env, không commit/push.

## P0 — Chốt contract và live spike

- **Mục tiêu:** xác nhận quyền, coverage và schema HERE tại TP.HCM; khóa API/DTO/status trước
  khi viết provider.
- **File dự kiến:** cập nhật docs/features/008-here-traffic-eta/{requirement,research,survey,spec,test-plan,plan}.md
  nếu quyết định thay đổi; tạo ghi chú kết quả trong evidence.md chỉ sau khi chạy thật.
- **Thay đổi contract:** chọn bbox limit, corridor radius, HERE_TRAFFIC_BASE_URL có/không
  /v7, sourceUpdated nullability, status BLOCKED/UNAVAILABLE, error codes.
- **Test/kiểm tra:** request nhỏ flow/incidents với locationReferencing=shape, key invalid,
  timeout/quota stub; lưu status/count/latency/schema chuẩn hóa, không raw payload/key.
- **Phụ thuộc/rủi ro:** cần key đã rotate và network; coverage empty không được diễn giải
  thành code failure.
- **Hoàn tất:** có quyết định được ghi, hoặc đánh dấu blocker cụ thể; không tạo source nếu
  API permission/shape không đáp ứng.

## P1 — Config, HTTP client và DTO provider

- **Mục tiêu:** có provider backend thuần HTTP, parse và map dữ liệu HERE an toàn.
- **File tạo/sửa dự kiến:**
  - vehicletracking-backend/src/main/java/.../traffic/config/TrafficProperties.java
    (hoặc mở rộng config/HereTrafficProperties.java nếu không tạo package thừa).
  - traffic/provider/HereTrafficProvider.java, HereTrafficDtos.java.
  - traffic/error/TrafficErrorCode.java, mapper dùng Problem Details hiện có.
  - config/HttpClientConfig.java và route/provider/HereRoutingHttpClientConfig.java để
    định danh hai RestClient.
  - src/main/resources/application.yaml, src/test/resources/application-test.yaml và
    .env.example chỉ khi contract đổi; không sửa .env thật.
- **Thay đổi contract:** bind enabled/base/key/timeouts/TTL; chuẩn hóa host + /v7; flow/
  incident DTO/envelope theo spec; upstream params in, locationReferencing=shape.
- **Test:** HereTrafficProviderTest, config context enabled/disabled, MockRestServiceServer
  kiểm tra URI/headers/parse/null/unknown fields/units và 401/403/429/5xx/timeout/invalid JSON.
- **Phụ thuộc/rủi ro:** P0; không log URL có apiKey; tránh RestClient injection nhầm
  routing. Không thêm SDK HERE nếu REST đủ.
- **Hoàn tất:** provider chạy với stub, key chỉ xuất hiện ở request server-side; chưa có
  controller/cache/ETA.

## P2 — Cache, freshness và API traffic

- **Mục tiêu:** bảo vệ quota, có last-known rõ ràng và expose flow/incidents cho frontend.
- **File tạo/sửa dự kiến:**
  - traffic/cache/TrafficCache.java và clock/single-flight helper phục vụ trực tiếp.
  - traffic/service/TrafficQueryService.java.
  - traffic/controller/TrafficController.java.
  - DTO/error/status tests.
- **Thay đổi contract:** GET /api/v1/traffic/flow và /incidents với bbox; 200 envelope
  fresh/stale/empty; 400 bounds; 502 invalid; 503 disabled/provider; 504 timeout.
- **Test:** TrafficCacheTest, TrafficHttpIntegrationTest với counter concurrent, fake
  Clock, stale expiry, CORS/Problem Details; request không gọi Internet.
- **Phụ thuộc/rủi ro:** P1; canonical cache key, memory growth/eviction, map viewport thay
  đổi nhanh. Không cache lỗi vô hạn và không trả stale như live.
- **Hoàn tất:** frontend có endpoint thật với stub; metrics/log an toàn; route/trip API cũ
  không đổi.

## P3 — Matching geometry và ETA service

- **Mục tiêu:** ghép traffic vào sections còn lại và tính ETA từng stop.
- **File tạo/sửa dự kiến:**
  - traffic/matching/TrafficRouteMatcher.java và records hình học nhỏ; tái sử dụng
    route/dto/RouteDetailResponse, RouteSectionEntity, Flexible Polyline decoder.
  - traffic/eta/EtaService.java, EtaResponse/EtaStop.
  - Có thể bổ sung helper position-to-section từ RouteMotion nhưng không đưa provider
    vào geometry thuần.
- **Thay đổi contract:** corridor query theo remaining geometry; spatial + hướng match;
  GET /api/v1/trips/{tripId}/eta; source/status/affectedSegments/warning; dwell/check-in
  policy ở spec.
- **Test:** TrafficRouteMatcherTest, EtaServiceTest, route cong/outside/expired/reverse/
  closure/missing speed, free-flow/jam/baseline/dwell/actual/check-in/position absent.
- **Phụ thuộc/rủi ro:** P2 + route/trip/check-in; đơn vị m/s-km/h, clamp speed, section gap,
  không N+1 query hoặc dùng Euclid/GPS speed; không sửa route snapshot.
- **Hoàn tất:** ETA deterministic đúng acceptance; provider lỗi fallback có source/warning;
  closure blocked không normal speed.

## P4 — Tích hợp simulator, snapshot và SSE

- **Mục tiêu:** simulator và operations đọc cùng traffic profile mà không gọi HERE theo frame.
- **File sửa dự kiến:**
  - simulation/service/SimulationService.java, SimulationScheduler.java,
    simulation/dto/SimulationResponse.java, simulation/motion/RouteMotion.java chỉ
    khi cần truyền profile; giữ geometry/clock API cũ.
  - telemetry/dto/OperationsSnapshot.java, telemetry/service/OperationsSnapshotService.java
    và stream serialization additive.
  - types/operations.ts counterpart sau khi backend contract ổn định.
- **Thay đổi contract:** metadata traffic nullable (source, status, observed/fetched,
  ETA, blocked, warning); jam làm chậm tick kế, closure giữ section blocked; clock/check-in/
  route revision không lùi; 5×/10× không tăng upstream vượt TTL.
- **Test:** mở rộng OperationsIntegrationTest/OperationsHttpIntegrationTest với stub
  enabled/disabled, jam giữa tick, closure, provider timeout, SSE reconnect/two subscribers,
  reset/complete/check-in regression.
- **Phụ thuộc/rủi ro:** P3; circular dependency Simulation↔ETA, race cache refresh,
  primitive ETA cũ không nhận null, status RUNNING/PAUSED semantics. Nếu cần pause reason
  mới phải chốt additive contract trước, không lén đổi lifecycle.
- **Hoàn tất:** simulator hiển thị/giữ blocked/fallback đúng và traffic failure không mất
  vị trí/check-in; full backend regression vẫn pass.

## P5 — Frontend service, state, map layer và ETA UI

- **Mục tiêu:** bật lớp traffic thực bằng raster tile HERE, hiển thị freshness/ETA mà không lộ secret hay phá layout.
- **File tạo/sửa dự kiến:**
  - vehicletracking-frontend/src/types/traffic.ts (hoặc mở rộng types/map.ts có kiểm
    soát), services/hereTraffic.ts và services/eta.ts.
  - hooks/useTraffic.ts/useTripEta.ts với AbortController, retry và polling ≥ TTL.
  - components/traffic/TrafficLayer.tsx dùng `L.tileLayer` proxy HERE Raster Tile; Incidents JSON chỉ dùng marker/popup;
    sửa MapComponent.tsx, MapControls.tsx.
  - components/operations/SimulatorPanel.tsx, fleet/TripDetailPanel.tsx và CSS.
- **Thay đổi contract:** traffic tile overlay theo viewport, incident overlay theo bbox, selected trip ETA,
  source badge, timestamp/age, clear/slow/congested/blocked legend, active/empty/stale/unavailable/retry;
  browser chỉ gọi /api/v1.
- **Test:** lint/tsc/build; browser fixture + Spring HTTP harness (nếu có) kiểm tra two tabs,
  layer cleanup, GET/SSE race, selection/draft, network không có HERE host/key, 320/390/768/
  1440 px.
- **Phụ thuộc/rủi ro:** P4; Leaflet listener/timer cleanup, map viewport debounce, state
  cũ trả sau, không làm request theo mỗi render, không hiển thị fake incidents.
- **Hoàn tất:** toggle không còn disabled khi config/API available; fallback/blocked copy rõ;
  UI giữ baseline route/check-in và không overflow.

## P6 — Hardening, hiệu năng và live verification

- **Mục tiêu:** chứng minh AC bằng test thực và giới hạn vận hành.
- **File tạo/sửa dự kiến:** docs/features/008-here-traffic-eta/evidence.md,
  walkthrough.md; browser scripts/artifacts chỉ tạo trong thư mục feature; cập nhật
  docs/PROJECT_PROGRESS.md và docs/SESSION_HANDOFF.md sau khi có kết quả thật.
- **Kiểm tra:** provider/cache/matching/ETA unit; API/integration PostgreSQL; ./mvnw test;
  frontend lint/tsc/build; browser fixture và Spring/PostgreSQL; load 10×50 stop, 2 tabs,
  60 giây; live spike P0 với key ephemeral.
- **Phụ thuộc/rủi ro:** Docker/browser/network/quota; không bỏ assertion hoặc nâng timeout
  để ghi đạt; raw key/response không được lưu vào artifacts.
- **Hoàn tất:** AC mapping, số test/latency/cache hit/upstream calls, status/coverage live
  và giới hạn môi trường được ghi; nếu thiếu live thì trạng thái vẫn chưa Verified.

## P7 — Review và bàn giao

- **Mục tiêu:** hồ sơ phản ánh đúng source sau triển khai.
- **File:** cập nhật status trong sáu tài liệu, evidence/walkthrough; review.md chỉ sau
  review độc lập; không tạo migration nếu P1–P6 không yêu cầu.
- **Kiểm tra:** git status --short, diff chỉ trong phạm vi, secret scan theo tên/không in
  giá trị, API/type/UI nhất quán, route/trip/check-in regression.
- **Hoàn tất:** chỉ chuyển Implementing khi người dùng duyệt Plan; chỉ chuyển Verified
  khi mọi AC/evidence thật đạt; reroute/notification vẫn ghi là 009 chưa làm.

## Gate phê duyệt

Sau khi người dùng duyệt, bắt đầu P0/P1 theo thứ tự. Nếu P0 phát hiện key không có quyền
Traffic hoặc shape coverage không đủ, dừng ở Blocked/mở lại requirement; không thay bằng
dữ liệu giả hoặc gọi trực tiếp từ browser.
