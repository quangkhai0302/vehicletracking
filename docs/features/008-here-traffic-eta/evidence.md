# Evidence 008 — HERE Traffic, ETA động và simulator

Ngày kiểm tra: 2026-09-14. Trạng thái: **Source implemented / raster tile live spike đã pass; browser và full-suite còn pending**.

## Source evidence

- Backend config `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/config/HereTrafficProperties.java` bind enabled, host, timeout, fresh/stale TTL, corridor radius và giới hạn bbox; API key được kiểm tra khi enabled và che trong `toString()`.
- `traffic/HereTrafficProvider.java` gọi HERE Traffic API v7 server-side cho `/flow` và `/incidents`, gửi `in=bbox`, `locationReferencing=shape`, chuyển m/s → km/h, chuẩn hóa geometry/incident và phân loại lỗi timeout/unauthorized/invalid response.
- `traffic/service/TrafficQueryService.java` canonical cache key, single-flight theo bbox, envelope `HERE_LIVE`/`HERE_LAST_KNOWN`/`UNAVAILABLE`, fresh 60 giây và stale tối đa 300 giây.
- `traffic/controller/TrafficController.java` expose `/api/v1/traffic/flow`, `/api/v1/traffic/incidents` và `/api/v1/traffic/tiles/{z}/{x}/{y}.png`; tile trả `image/png` với cache-control 60 giây. `traffic/eta/TrafficEtaController.java` expose `/api/v1/trips/{tripId}/eta`.
- `traffic/matching/TrafficRouteMatcher.java` match geometry + hướng; `traffic/matching/RoutePositionMatcher.java` chiếu telemetry lên section còn lại để tính phần đường chưa đi; `traffic/eta/TrafficEtaService.java` tính ETA từng stop còn lại, dwell, check-in, dynamic speed, fallback route snapshot và `TRAFFIC_BLOCKED` cho closure.
- `simulation/dto/SimulationTrafficMetadata.java` và `SimulationResponse.java` mở rộng additive source/status/ETA/freshness/blocked; `SimulationService` dùng traffic rate để giảm/tăng tiến độ, giữ speed frame đồng bộ và dừng elapsed khi blocked, không để lỗi traffic làm hỏng lifecycle/vị trí simulator.
- Frontend `hooks/useTraffic.ts`, `components/traffic/TrafficLayer.tsx`, `MapControls.tsx` dùng raster tile HERE qua backend proxy cho viewport (Google chỉ còn là lớp nền); Incidents JSON chỉ phục vụ marker/popup, kèm freshness/error/retry và cleanup listener/layer/fetch. `useTripEta.ts`, `SimulatorPanel.tsx`, `TripDetailPanel.tsx` hiển thị ETA/source/fallback/blocked.

## Deterministic checks

Đã chạy:

```text
JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn bash ./mvnw -q -DskipTests compile
JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn JAVA_TOOL_OPTIONS=-javaagent:/home/khainq/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar bash ./mvnw -q -Dtest=HereTrafficProviderTest,TrafficQueryServiceTest,TrafficControllerTest,TrafficRouteMatcherTest,RoutePositionMatcherTest,TrafficEtaServicePolicyTest -DfailIfNoTests=false test
PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint
PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH ./node_modules/.bin/tsc --noEmit
PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run build
```

Kết quả: bộ test tập trung Traffic/ETA gồm **20 test pass** (provider, cache/single-flight, controller, matching, position, policy và tile); frontend lint/tsc/build pass, không còn warning lint. Build hiện tại: **1,899 modules**, JS 487.81 kB (gzip 144.96 kB), CSS 119.05 kB (gzip 26.30 kB).

## Live spike upstream

Đã gọi trực tiếp HERE Traffic API bằng credential đã có trong môi trường vào lúc
`2026-09-14T03:14:15Z`, với bbox nhỏ tại TP.HCM. Chỉ ghi lại metadata kiểm tra, không in
hoặc lưu API key và raw response:

- `GET /v7/flow`: HTTP **200**, JSON hợp lệ, `sourceUpdated=2026-09-14T03:13:03Z`,
  `results=128`.
- `GET /v7/incidents`: HTTP **200**, JSON hợp lệ, `results=0` và upstream không gửi
  `sourceUpdated` cho response rỗng.
- Kiểm tra tên field của một flow live khớp DTO hiện tại: top-level `results/sourceUpdated`,
  `location/shape/links/points` và `currentFlow` gồm `confidence/freeFlow/jamFactor/speed/
  traversability` (các field mở rộng như `speedUncapped` được bỏ qua an toàn).
- `GET /v3/flow/mc/12/3261/1916/png8`: HTTP **200**, `image/png`, 3,199 bytes và PNG
  signature hợp lệ; tile được phục vụ qua backend proxy, không đưa key vào browser.

Kết quả này xác nhận credential, quyền truy cập và schema upstream tại thời điểm spike.
Chưa chạy qua HTTP server Spring trong phiên này vì môi trường thiếu PostgreSQL/Docker;
do đó chưa tuyên bố browser end-to-end hoặc API nội bộ đã lấy live.

## Chưa thể xác nhận trong môi trường này

- Đã gọi live HERE ở mức upstream như mục trên; chưa xác minh browser → backend → HERE
  end-to-end và không lưu key/raw response vào artifact.
- `./mvnw test` đã được thử nhưng JDK 26 hiện tại không attach được Byte Buddy Mockito cho các test context hiện có; các test Testcontainers cũng cần Docker PostgreSQL. Đây là giới hạn môi trường, không phải kết luận live traffic đã hoạt động.
- Chưa có browser/E2E evidence cho two tabs, 320/390/768/1440 và lớp HERE thật. Reroute/notification vẫn là feature 009.
