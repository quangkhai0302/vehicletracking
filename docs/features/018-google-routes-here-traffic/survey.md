# Survey repository

Khảo sát 2026-09-15, HEAD `662134d`, working tree sạch trước lập hồ sơ. ID lớn nhất đang có: 017. Đã đọc `docs/workflow.md`; không có `docs/templates/`. Báo cáo cũ có mốc lịch sử, không dùng thay source.

Quy ước đường dẫn: **B** = `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/`; **T** = `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/`; **F** = `vehicletracking-frontend/src/`; **M** = `vehicletracking-backend/src/main/resources/db/migration/`.

## Evidence

| ID | Nhận định từ code | Evidence (path + symbol) | Tác động |
|---|---|---|---|
| E01 | Provider interface đã được inject; chưa có Google implementation | B/route/provider/RoutingProvider.java#calculate; B/route/provider/HereRoutingProvider.java#calculate | Không mô tả RouteService gọi trực tiếp HERE client; cần registry và contract giàu hơn. |
| E02 | RouteService gán cứng CAR/HERE | B/route/service/RouteService.java#buildRoute; B/route/entity/RouteTransportMode.java; B/route/entity/RoutingProviderName.java | Provider response phải quyết định metadata, thay cả enums/UI/DB. |
| E03 | Request cho phép 2–50 stops, dwell 0–3600; đầu/cuối dwell 0 | B/route/dto/RouteCreateRequest.java; B/route/service/RouteService.java#validateStops | Không âm thầm hạ giới hạn theo Google. |
| E04 | Kéo tuyến có preview/save/copy, shape point gắn destinationStopSequence | B/route/service/RouteShapeService.java#calculate; B/route/controller/RouteShapeController.java; F/components/route/RouteShapeEditor.tsx | Cần bảo toàn occurrence mapping khi Google via không sinh leg. |
| E05 | Polyline không ghi encoding; provider output chỉ có sections/timestamps | B/route/provider/CalculatedRoute.java; CalculatedSection.java; F/services/polyline.ts#decodeFlexiblePolyline; B/simulation/motion/FlexiblePolyline.java | Mọi decoder/matcher/renderer phải dispatch theo encoding hoặc geometry chuẩn hóa. |
| E06 | DB chỉ chấp nhận HERE/CAR và lưu geometry/metrics bắt buộc | M/V3__create_routes_tables.sql#chk_routes_routing_provider, #chk_routes_transport_mode, #route_sections | Thêm migration mới; retention Google không tương thích lưu snapshot vĩnh viễn mặc định. |
| E07 | Revision có geometry, schedule; V11 có thời điểm áp dụng theo attempt | B/reroute/entity/TripRouteRevisionEntity.java; M/V7__create_route_revisions_and_notifications.sql; M/V11__route_shaping_and_simulation_revision.sql | Metadata provider/encoding/version cần xuyên revision và route gốc. |
| E08 | ETA lấy HERE Flow/Incidents theo geometry; rate simulator dựa flow gần xe | B/traffic/eta/TrafficEtaService.java#calculate, #currentSectionRate, #trafficDuration | Phải tách nguồn ETA Google và nguồn phân tích HERE. |
| E09 | Reroute trigger sau commit telemetry; evaluator gọi routing trong transaction mới | B/telemetry/service/TelemetryService.java#evaluateRerouteSafely; B/reroute/service/RerouteEvaluationService.java#evaluateCurrent, #buildRevision | Đưa mạng khỏi khóa DB và chặn ghi kết quả cũ. |
| E10 | Gate chỉ chấp nhận HERE sources, 600s VÀ 30%, 2 refresh, cooldown 300s | B/reroute/service/ReroutePolicy.java#observe; application.yaml#reroute | Không đổi nguồn ETA mà quên điều kiện trigger/freshness. |
| E11 | Simulator áp revision, check-in/ETA chia sẻ geometry | B/simulation/service/SimulationService.java#advance; B/reroute/service/TripRouteGeometryService.java#resolve, #applyActive; B/checkin/service/CheckInService.java | Google ETA refresh không được tự thay geometry đang chạy. |
| E12 | Frontend có buffer 1500ms, selected route và marker dùng đường đã decode | F/utils/vehicleMotion.ts#sampleMotion; F/hooks/useVehicleMarkers.ts; F/components/MapComponent.tsx#selectedSimulationRoutes | Giữ chuyển động mượt và bỏ buffer cũ khi revision đổi. |
| E13 | Nền Google qua URL vt có traffic tích hợp; HERE layer hiện xử lý incidents | F/components/MapComponent.tsx effect comment “Google base map”; F/components/traffic/TrafficLayer.tsx | Đổi nền API chính thức phải xác định lại thanh traffic; không chồng Google+HERE flow. |
| E14 | Hover tải HERE; ETA poll frontend mỗi 10s | F/hooks/useRouteTraffic.ts; F/hooks/useTripEta.ts#REFRESH_MS; F/components/route/RouteInspectionLayer.tsx | Không nối mỗi polling vào một request Google tính phí. |
| E15 | DTO ETA/source chưa có Google; route TS chỉ HERE/CAR | B/traffic/eta/TripEtaResponse.java; F/types/route.ts; F/types/eta.ts; F/types/operations.ts | Thay contract đồng bộ, không gắn jamFactor giả vào Google intervals. |
| E16 | Trip plannedEndAt suy từ thời lượng route đã lưu | B/trip/dto/TripSummaryResponse.java#from; B/trip/entity/TripEntity.java | ETA động không được ghi đè lịch kế hoạch; retention metrics ảnh hưởng DTO. |
| E17 | Có test 37 km/h HERE, shape, reroute/replay và native Node motion | T/traffic/eta/TrafficSimulationSpeedTest.java#realMatcherAndRateFollow37KmhFlowRatherThan15KmhCap; T/route/service/RouteShapeServiceTest.java; T/reroute/RerouteSimulationIntegrationTest.java#simulatorAdoptsPersistedDetourAndReplayReturnsToOriginalRoute; vehicletracking-frontend/tests/vehicleMotion.test.ts | Giữ regression HERE, thêm fixture Google; không tuyên bố đã chạy lại test lượt lập kế hoạch. |
| E18 | Config có HERE flags/key và RestClient riêng | vehicletracking-backend/src/main/resources/application.yaml#here; B/config/HttpClientConfig.java | Cần Google config riêng, không dùng HERE_ENABLED để bật Google. |

## Luồng hiện có

`RouteWorkspace → routes service → RouteService/RouteShapeService → RoutingProvider(HERE) → routes/stops/sections → trip → telemetry/simulation → TrafficEtaService(HERE) → reroute → revision → shared geometry → SSE/UI` (E01–E18).

## Tái sử dụng và rủi ro

- Giữ CRUD, snapshot trạm do người dùng nhập, UI chọn xe, form kéo tuyến, persistence attempt, notification và motion buffer (E03, E04, E07, E11, E12).
- Phạm vi sửa chính: route/provider; traffic/eta; reroute; simulation/motion; DTO/entity/migration; frontend types/decoders/route inspector/map layer (E01–E18).
- Nguy cơ lớn: decoder sai, sai leg index, ETA của đường khác, cộng traffic hai lần, mạng giữ khóa DB, quota tăng theo tick, mất lịch sử do retention không được thiết kế (E05–E16).
- Không có xác minh tài khoản/provider live hay test/build mới trong lượt survey; chỉ đọc source, cấu hình không chứa secret, migrations và tests.
