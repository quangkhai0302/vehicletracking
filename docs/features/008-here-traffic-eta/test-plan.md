# Test Plan 008 — HERE Traffic, ETA và simulator

Trạng thái: **Implementing** · Test deterministic và upstream live spike đã chạy; browser/full-suite evidence còn thiếu. Bảng dưới là kế hoạch kiểm tra
sau khi được duyệt triển khai. Fixture và live evidence phải tách nhãn.

## 1. Ma trận acceptance criteria

| AC | Mức test | Kịch bản | Dữ liệu/fixture | Kết quả mong đợi |
|---|---|---|---|---|
| AC-01 | Config + provider unit/context | enabled false; enabled true với key; enabled true thiếu key; base URL khác nhau | HereTrafficProperties, application.yaml, isolated env; không dùng key thật | Không gọi HERE khi tắt; validation/lỗi rõ khi thiếu key; traffic bean có qualifier; routing bean không bị nhập nhằng |
| AC-02 | Provider unit + API integration | bbox hợp lệ, empty result, malformed speed/shape, min/max boundary | here-flow.json, flow empty/invalid fixtures, MockRestServiceServer | Upstream request có /v7/flow, in=bbox, locationReferencing=shape; map m/s→km/h; envelope observed/fetched; 200/400/502/503/504 đúng contract |
| AC-03 | Provider unit + API integration | incident active, expired, no result, unknown criticality/type | here-incidents.json, active/expired/closure/outside-route fixtures | id/type/severity/geometry/time map đúng; empty hợp lệ; expired không ảnh hưởng ETA |
| AC-04 | Cache/concurrency unit | hit trong 60s; refresh sau TTL; upstream lỗi trong stale window; stale hết hạn; 20 request đồng thời cùng key | Clock fake, cache key variants, MockRestServiceServer counter | Fresh HERE_LIVE; stale HERE_LAST_KNOWN có tuổi; hết hạn unavailable; một upstream call cho một key; lỗi không cache vô hạn |
| AC-05 | Geometry/matching unit | flow cắt route, song song trong corridor, ngoài bbox route, hướng ngược, geometry thiếu hướng, incident trên section đã đi | Route sections/Polyline fixture, flow/incidents augmented fixtures | Chỉ item ảnh hưởng remaining geometry được match; ngoài route/đã đi không đổi ETA; reverse không match khi đủ hướng; fallback spatial có nhãn |
| AC-06 | ETA service unit + integration/API | free-flow, jam, thiếu flow, dwell chưa xong, stop đã check-in, vị trí giữa section, không có position | Route snapshot 005, check-in 007, flow fixtures jam 0/4.5/10 | ETA từng stop đúng tổng section+dwell; actual giữ nguyên; jam tăng ETA; fallback route snapshot rõ; không Euclid/GPS speed |
| AC-07 | ETA + simulator integration | jam update giữa hai tick; closure trên section còn lại; closure ngoài route; reversible not routable | RouteMotion curved route, flow closed, incident road closure | Effective speed/ETA thay đổi ở tick kế; closure blocked và không chạy baseline; ngoài route không ảnh hưởng; không tự tạo reroute |
| AC-08 | Controller + browser/manual | toggle on/off, raster tile load/error, incident loading/empty/stale/unavailable/retry, layer unmount, 320/390/768/1440 | Browser fixture server có tile PNG/incident mock và status; live backend nếu có | Tile/legend/marker đúng, cleanup không duplicate; badge source/timestamp; không network HERE/key từ browser; responsive không overflow |
| AC-09 | Snapshot/SSE + simulator integration | 1×/5×/10×; traffic refresh; SSE reconnect; provider lỗi; check-in/reset | Operations integration fixture, two subscribers | Metadata source/status đồng bộ; clock/vị trí/check-in không lùi; multiplier không tăng upstream vượt TTL; fallback warning không mất state |
| AC-10 | API negative + logging test | 400/401/403/429/414/5xx, timeout, invalid JSON, oversized bbox | Mock HTTP status/body; log appender kiểm tra chuỗi cấm | Problem Details + code ổn định; timeout/credential/rate-limit map đúng; log không có API key/full URI/raw response |
| AC-11 | Regression/API/frontend | traffic tắt rồi chạy routes, trips, telemetry, check-in, simulator; client bỏ qua field mới | Existing 005–007 integration tests, JSON snapshot contract | API cũ vẫn pass; route baseline/check-in không đổi; field additive/nullable; frontend type-check/build đạt |
| AC-12 | Live spike + full suite | flow/incidents bbox/corridor và raster tile TP.HCM; key invalid/quota; coverage empty; latency | Key ephemeral đã rotate; không lưu trong fixture/log; request IDs | Ghi status/observedAt/coverage/PNG signature/latency và giới hạn quota; live pass chỉ khi response thật parse đúng; full deterministic suite không phụ thuộc live |

## 2. Test lớp backend

### Unit/provider

- HereTrafficProviderTest: URI builder, encoding bbox/corridor, /v7 normalization, API key chỉ xuất hiện trong outgoing request được mock, parse speed/shape/incident, null/unknown field và error mapping.
- TrafficCacheTest: fake Clock, fresh/stale/expired, key canonicalization, single-flight, upstream failure và eviction. Assertion không phụ thuộc sleep thực.
- TrafficRouteMatcherTest: tuyến cong, section gap, hướng xuôi/ngược, corridor radius, expired incident và flow ngoài route; expected geometry được tạo độc lập với matcher.
- EtaServiceTest: cộng duration/dwell, check-in actual, jam speed, closure/blocked, fallback baseline, precision/rounding và timestamp UTC.

### API/integration

- TrafficHttpIntegrationTest dùng MockRestServiceServer hoặc HTTP stub local, kiểm tra request/response/status/Problem Details/CORS và không gọi Internet.
- TrafficEtaIntegrationTest dùng PostgreSQL Testcontainers và route/trip/check-in fixtures hiện có; kiểm tra 404/409, snapshot bất biến, cache hit và provider failure.
- OperationsIntegrationTest/OperationsHttpIntegrationTest mở rộng additive traffic metadata với property traffic disabled để hồi quy; test enabled dùng stub, không key thật.
- Nếu thêm bean qualifier/config validation, có context test enabled/disabled và kiểm tra cả hai RestClient không nhầm base URL.

## 3. Test frontend và browser

- npm run lint, ./node_modules/.bin/tsc --noEmit, npm run build là bắt buộc; repository hiện không có Vitest/Jest trong app.
- Reuse browser harness dưới docs/features/006-telemetry-simulator/verification/ hoặc tạo extension 008 riêng, với fixture server có nhãn TRAFFIC_FIXTURE và backend live có nhãn SPRING_TRAFFIC. Không chuyển fallback fixture thành pass khi backend/HERE lỗi.
- Kiểm tra hai tab chọn cùng trip, SSE/GET race (revision/traffic fetchedAt cũ trả sau), viewport đổi nhanh, retry/abort/unmount, marker tooltip, legend, stale/unavailable, closure banner, 320/390/768/1440 px và giữ draft route/station.
- Network assertion: trình duyệt chỉ gọi VITE_API_BASE_URL/api/v1/...; không có data.traffic.hereapi.com, apiKey, Authorization của HERE trong request/bundle.

## 4. Live spike có kiểm soát

Live spike không chạy trong full suite và không ghi key vào repository:

1. Chạy backend với key ephemeral đã rotate, HERE_TRAFFIC_ENABLED=true, base URL đúng /v7 và tile base URL đúng host, timeout 2s/5s; giới hạn một bbox nhỏ tại TP.HCM.
2. Gọi flow và incidents một lần cho bbox, một lần corridor ngắn, và một tile raster; lưu status code, sourceUpdated, số results, loại location referencing, content type/PNG signature, latency và response schema đã chuẩn hóa, không lưu raw payload/secret.
3. Lặp sau cache TTL để xác nhận hit/stale; cố ý dùng key sai hoặc stub timeout để kiểm tra error contract, sau đó xóa/rotate credential test.
4. Ghi rõ coverage empty có nghĩa là HERE không trả item, không tự kết luận lỗi code.

## 5. Hiệu năng và độ tin cậy

- Tải dev: 10 trip × 50 stop, 1 snapshot/giây, 2 SSE tabs trong 60 giây; traffic provider stub có latency cố định. Đo số upstream calls, cache hit ratio, query count ETA, payload, p95 endpoint và commit→UI; mục tiêu p95 cache hit <200 ms, upstream <6 s, không tạo request theo stop/frame.
- Chạy timeout/cancel/reconnect và provider 429/5xx nhiều lần; bảo đảm scheduler không tạo storm, stale window không kéo dài vô hạn và simulator không teleport/lùi clock.
- Nếu Docker/PostgreSQL/browser/API key không sẵn sàng, ghi unrun và lý do trong evidence; không nâng timeout hay bỏ assertion để ghi pass.

## 6. Lệnh dự kiến sau implementation

Từ vehicletracking-backend:

    ./mvnw -Dtest=HereTrafficProviderTest,TrafficCacheTest,TrafficRouteMatcherTest,EtaServiceTest test
    ./mvnw -Dtest=TrafficHttpIntegrationTest,TrafficEtaIntegrationTest test
    ./mvnw test

Từ vehicletracking-frontend:

    npm run lint
    ./node_modules/.bin/tsc --noEmit
    npm run build

Kết quả thực tế, version runtime, live spike và giới hạn phải được ghi vào evidence.md
sau implementation; source MVP đã triển khai, còn P6 browser/full-suite/live end-to-end cần hoàn tất trước khi chuyển Verified.
