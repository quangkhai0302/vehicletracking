# Evidence — Feature 018 — 2026-09-15

Trạng thái: **Implementing / fixture verified**. Google vẫn mặc định tắt; chưa gọi API tính phí và chưa đưa key vào source/frontend/tài liệu.

## Code đã triển khai

| Phạm vi | Evidence source |
|---|---|
| Provider pin theo tuyến, mode CAR/MOTORCYCLE | `route/provider/RoutingProviderRegistry.java`, `RoutingRequest.java`, `route/service/RouteService.java`; update giữ provider/mode hiện tại khi client cũ không gửi mode. |
| Google Routes adapter | `route/provider/GoogleRoutingProvider.java`; POST computeRoutes, field mask cụ thể, DRIVE/TWO_WHEELER, traffic-aware polyline, batching tối đa 27 waypoint/request và chuẩn hóa leg. |
| Codec/traffic metadata | `route/entity/PolylineEncoding.java`, `RouteTrafficInterval.java`, `simulation/motion/GooglePolyline.java`, `RoutePolylineCodec.java`; frontend `services/polyline.ts`. |
| Persistence tương thích | `db/migration/V12__google_routes_provider_compatibility.sql`; route/revision section lưu encoding và intervals, route có geometryVersion/expiry; V1–V11 không bị sửa. |
| Tạo/kéo/copy tuyến | `RouteCreateRequest.java`, `RouteShapeRequest.java`, `RouteShapeService.java`; frontend `RouteDrawer.tsx`, `RouteShapeEditor.tsx`. Copy có thể chọn Google/HERE, save không đổi provider ngầm. |
| ETA Google + HERE analysis | `traffic/eta/TrafficEtaService.java`, `GoogleEtaCoordinator.java`, `RouteGeometryCompatibility.java`. Google duration điều khiển ETA tuyến Google; HERE Flow/Incidents vẫn phân tích/closure; đường refresh khác hành lang không được gắn ETA lên line cũ. |
| Giới hạn request | `GoogleRoutingRequestGuard.java`, `GoogleRoutingProperties.java`: single-flight theo trip/attempt/geometry/revision/stop, TTL và giới hạn đồng thời/phút/ngày. |
| Simulator | `simulation/motion/RouteMotion.java`: duration Google được phân bổ theo trọng số NORMAL=1, SLOW=2, TRAFFIC_JAM=4 nhưng tổng duration không đổi; playback 1×/5×/10× không nhân km/h. |
| Reroute | `RerouteEvaluationService.java`, `ReroutePolicy.java`, `TripRouteGeometryService.java`: snapshot DB → gọi provider ngoài transaction → khóa và đối chiếu attempt/revision/telemetry trước khi ghi; giữ trạm và shaping point còn phía trước. `RerouteCandidateValidator.java` loại ứng viên vẫn cắt qua geometry closure HERE. |
| Map/UI | Backend `maps/GoogleMapTiles*`; frontend `services/mapTiles.ts`, `MapComponent.tsx`, `TrafficLayer.tsx`, `TripTrafficSummary.tsx`. Key/session Google ở backend; tuyến Google tô interval, HERE Flow khu vực ẩn để không chồng màu; sự cố HERE vẫn hiển thị. |
| Chẩn đoán Map Tiles | `maps/GoogleMapTilesService.java`, `GoogleMapTilesProviderException.java`, `GoogleMapTilesExceptionHandler.java`: giữ status/code/reason/message lỗi upstream ở dạng đã lọc, trả `traceId` trong ProblemDetail và ghi cùng `traceId` ở backend; API key/session bị che trước khi log hoặc trả client. |
| Hướng dẫn | `vehicletracking-frontend/public/huong-dan/index.html`: loại phương tiện và vai trò Google/HERE. |

Đường dẫn backend trong bảng tương đối với `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/`; frontend tương đối với `vehicletracking-frontend/src/` nếu không ghi đầy đủ.

## Kiểm tra đã chạy

- Backend Java 26: `./mvnw -Dtest='!**/*IntegrationTest' test` — **203 test, 0 failure, 0 error**.
- Bộ mới/trọng tâm riêng lẻ: Google provider, request guard, registry, polyline, traffic RouteMotion, ETA single-flight, geometry compatibility và Map Tiles proxy đều pass.
- Bổ sung chẩn đoán Map Tiles: `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn ./mvnw -q -Dtest=GoogleMapTilesServiceTest clean test` — exit 0; kiểm tra response upstream 404 được ánh xạ sang ProblemDetail và key giả bị thay bằng `[REDACTED]`.
- Full `./mvnw test`: 203 test được khám phá; logic unit/controller đạt sau khi sửa regression mode. Sáu integration class không khởi động vì Testcontainers không tìm thấy Docker, nên không dùng lượt này làm kết quả pass toàn bộ.
- Frontend: `./node_modules/.bin/tsc --noEmit` — exit 0. `npm run lint` — exit 0, còn hai warning có sẵn tại `useFleetWorkspace.ts:93,101`.
- `npm run build` chưa chạy được bằng môi trường hiện tại: Node 18.19.1 thiếu `node:util.styleText`; project yêu cầu Node >=22.12 và định hướng Node 24.
- `git diff --check` — exit 0.

## Chưa xác minh

- V12/Flyway/Hibernate validate trên PostgreSQL thật vì Docker không khả dụng.
- Google Routes/Map Tiles live chưa xác minh thành công. Map Tiles `createSession` trên cấu hình cục bộ đã trả upstream `404 NOT_FOUND`; phần chẩn đoán mới cho phép xem lỗi đã lọc ở DevTools sau khi khởi động lại backend.
- Browser drag/touch, tile session và màu Google/HERE trên bundle Node 24.
- Transaction ba pha và hậu kiểm closure đã có fixture/unit evidence; kiểm thử cạnh tranh thật trên PostgreSQL, cầu vượt/đường song song vẫn chờ môi trường Docker/browser; xem [review](review.md).
