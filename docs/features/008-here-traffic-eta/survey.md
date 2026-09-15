# Survey 008 — Trạng thái repository trước khi triển khai

Ngày khảo sát: **2026-09-14**. Kết luận dựa trên source/config/test hiện có, không dựa
vào tên fixture hoặc mô tả cũ. Working tree đang có nhiều thay đổi 004–007 chưa commit;
feature 008 chưa sửa source trong lượt lập kế hoạch này.

## 1. Cây file liên quan

```text
vehicletracking-backend/src/main/java/.../
├── config/
│   ├── HereTrafficProperties.java
│   └── HttpClientConfig.java
├── route/
│   ├── provider/HereRoutingProvider.java
│   ├── provider/HereRoutingProperties.java
│   ├── provider/HereRoutingHttpClientConfig.java
│   ├── dto/RouteDetailResponse.java
│   └── entity/RouteSectionEntity.java
├── telemetry/
│   ├── controller/TelemetryController.java
│   ├── dto/OperationsSnapshot.java
│   ├── service/OperationsSnapshotService.java
│   └── service/OperationsStreamService.java
└── simulation/
    ├── service/SimulationService.java
    ├── service/SimulationScheduler.java
    └── motion/RouteMotion.java

vehicletracking-frontend/src/
├── services/hereTraffic.ts
├── types/map.ts
├── types/operations.ts
├── hooks/useLiveOperations.ts
├── hooks/useSimulator.ts
├── components/MapComponent.tsx
├── components/MapControls.tsx
└── components/operations/SimulatorPanel.tsx

vehicletracking-backend/src/test/resources/fixtures/
├── here-flow.json
└── here-incidents.json
```

Không có package backend `traffic/` hoặc `notification/`, không có migration traffic và
không có test provider dùng hai fixture trên.

## 2. Luồng dữ liệu hiện tại

### Route và simulator

1. `RouteController` → `RouteService` → `HereRoutingProvider.calculate` gọi HERE Routing
   khi routing được bật và có key.
2. `HereRoutingProvider.buildUri` tạo `/v8/routes`, `transportMode=car`,
   `routingMode=fast`, origin/via/destination, `return=polyline,summary,travelSummary`;
   `RouteDetailResponse`/entity lưu section geometry, distance và duration.
3. `SimulationService`/`RouteMotion` chạy geometry và dwell của route snapshot; snapshot
   và SSE operations phát frame/countdown hiện tại.
4. ETA trên simulator là countdown theo simulated clock/route duration, không gọi Traffic
   API. Check-in 007 tiêu thụ telemetry accepted nhưng không phải traffic source.

### Traffic hiện tại

1. `application.yaml` có các biến `here.traffic` và import `.env` tùy vị trí chạy.
2. `HereTrafficProperties` bind enabled/base URL/key/timeout.
3. `HttpClientConfig` chỉ tạo một `RestClient` khi `here.traffic.enabled=true`.
4. Frontend `services/hereTraffic.ts` mới ghép URL nội bộ `/api/v1/traffic/flow` và
   `/api/v1/traffic/incidents`; không có hook/component gọi hai hàm này.
5. Backend không có mapping `/api/v1/traffic/**`; `MapControls` khóa checkbox traffic và
   hiển thị “Chưa kết nối nguồn traffic và sự cố”.

## 3. Evidence repository

| Nhận định | Evidence | Ý nghĩa cho 008 |
|---|---|---|
| Traffic flag mặc định tắt; chỉ một số HERE vars được bind | `vehicletracking-backend/src/main/resources/application.yaml:30-42` (`here.traffic.enabled`, `base-url`, `api-key`, timeout; routing tách riêng) | Cần giữ default an toàn, thêm validation/health rõ ràng; `ROUTING_PROVIDER`, `TRAFFIC_PROVIDER`, TTL/reroute hiện không tự bind. |
| Properties chỉ là cấu hình, chưa có business method | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/config/HereTrafficProperties.java:8-72` | Có thể tái sử dụng bean nhưng phải thêm provider/DTO/service; không coi class này là tích hợp đã xong. |
| RestClient traffic được tạo có điều kiện | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/config/HttpClientConfig.java:11-25` | `enabled=true` mới tạo client; hiện không có caller. Cần test startup và qualifier vì routing config cũng tạo `RestClient` ở `route/provider/HereRoutingHttpClientConfig.java:10-23`. |
| Routing đã gọi HERE nhưng chỉ cho route creation | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/provider/HereRoutingProvider.java:38-60,134-156` | Duration HERE lúc tính tuyến là baseline; không phải polling traffic live. |
| Route sections có geometry/distance/travel duration | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/entity/RouteSectionEntity.java:20-78`; `route/dto/RouteDetailResponse.java:55-125` | Tái sử dụng để tạo corridor, remaining sections và cộng dwell; không cần sửa route snapshot khi traffic đổi. |
| Snapshot/SSE hiện phục vụ positions/simulations/trips/check-ins | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/telemetry/dto/OperationsSnapshot.java:7-9`; `telemetry/controller/TelemetryController.java:9-20` | Có thể mở rộng additive traffic metadata hoặc thêm ETA endpoint; không tạo stream HERE riêng theo panel. |
| Không có traffic controller/service | Danh sách controller trong `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/**/controller`; không có package `traffic` | Frontend URL hiện tại sẽ không có API đích; cần bổ sung backend contract. |
| Frontend service chỉ gọi API dự kiến | `vehicletracking-frontend/src/services/hereTraffic.ts:29-66` | Giữ service làm điểm vào nhưng thêm type/envelope/error/abort và consumer thực. |
| Không có consumer/layer traffic; toggle đang disabled | `vehicletracking-frontend/src/components/MapControls.tsx:27-31`; `rg -n 'fetchHereTrafficFlow|fetchHereIncidents' vehicletracking-frontend/src` chỉ thấy file service | Cần state gần MapComponent, layer cleanup và trạng thái loading/empty/stale/unavailable. |
| Simulator ghi rõ chưa có traffic thật | `vehicletracking-frontend/src/components/operations/SimulatorPanel.tsx:47-76` | Copy/UI phải đổi additive, giữ nhãn route snapshot khi provider lỗi. |
| Operations frame dùng next-stop ETA dạng số và status cũ | `vehicletracking-frontend/src/types/operations.ts:10-22`; backend `simulation/dto/SimulationResponse.java:5-6` | Mở rộng nullable/additive traffic metadata hoặc DTO ETA, tránh phá client 006. |
| Fixture flow có speed/freeFlow/jamFactor/traversability/confidence | `vehicletracking-backend/src/test/resources/fixtures/here-flow.json:1-28` | Dùng contract/negative test, không coi `sourceUpdated` 2026-09-09 là dữ liệu live. |
| Fixture incident có id/type/criticality/start/end/shape | `vehicletracking-backend/src/test/resources/fixtures/here-incidents.json:1-30` | Dùng test mapping/expiry/route matching; cần thêm fixture ngoài route, closure và stale. |
| Test integration 006/007 chủ động tắt routing/traffic | `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/simulation/OperationsIntegrationTest.java:38`; `OperationsHttpIntegrationTest.java:33-35` | Regression deterministic không gọi HERE; thêm test mock traffic riêng, live test opt-in. |
| Env người dùng có provider/TTL/reroute nhưng source chưa tham chiếu | `PROJECT_HANDOFF.md:389-398`; `rg` trên backend/frontend với các tên biến | Không tuyên bố các biến này đang có tác dụng; đưa vào `HereTrafficProperties` hoặc loại khỏi contract trong implementation được duyệt. |

## 4. Ràng buộc và rủi ro

- `HereRoutingHttpClientConfig` luôn tạo `RestClient`, còn `HttpClientConfig` tạo thêm
  một bean khi traffic bật; constructor `HereRoutingProvider` hiện nhận `RestClient`
  không có `@Qualifier`. Implementation phải kiểm tra context với traffic bật và định danh
  bean rõ ràng, tránh lỗi ambiguous hoặc vô tình dùng traffic base URL cho routing.
- `HereTrafficProperties` chưa có `@AssertTrue` kiểm tra key khi enabled; provider tương
  lai phải trả lỗi cấu hình có kiểm soát trước request.
- Base URL trong source là host `https://data.traffic.hereapi.com`; HERE endpoint v7 có
  path `/v7`. Cần chuẩn hóa path một lần, xử lý cả giá trị host và giá trị đã có `/v7`,
  không nối lặp.
- Frontend không có test runner ứng dụng trong `package.json`; kế hoạch dùng lint/tsc/build,
  browser harness hiện có và API/integration test backend, không tự cài Vitest/Jest.
- `docs/` và working tree đang có thay đổi của người dùng/feature 007. Chỉ tạo hồ sơ
  008 trong lượt này, không reset, commit, push hoặc sửa `.env`.
- HERE coverage/quota/auth ở TP.HCM chưa có live evidence; fixture và mock không chứng
  minh quyền key hoặc dữ liệu thực.

## 5. Khoảng cách cần lấp

`HereTrafficProperties` → provider HTTP → DTO/error → cache → route matching/ETA →
simulator/snapshot → frontend service/state/layer/UI → tests/live spike. Không có bước nào
trong chuỗi này được source hiện tại thực hiện đầy đủ. Reroute, revision lịch và
notification được giữ ở feature 009.

