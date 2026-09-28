# Evidence — 2026-09-15

Trạng thái: Implementing — source đã triển khai, chưa xác nhận đầy đủ trên browser và PostgreSQL với bản cuối. Không commit/push; giữ các thay đổi có sẵn trong working tree.

## Code và test

| Phạm vi | Evidence |
|---|---|
| Vận tốc theo flow tại xe, bỏ trần 1,5 lần | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/traffic/eta/TrafficEtaService.java::currentSectionRate`; `simulation/service/SimulationService.java::emit/describe`; `traffic/eta/TrafficSimulationSpeedTest.java` kiểm tra matcher thật với response fixture 37 km/h trên nền khoảng 10 km/h. |
| Chuyển động có buffer 1,5 giây | `vehicletracking-frontend/src/hooks/useVehicleMarkers.ts::scheduleAnimations`; `utils/vehicleMotion.ts::sampleMotion`; `tests/vehicleMotion.test.ts`. |
| Preview/save/copy tuyến kéo chỉnh | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/service/RouteShapeService.java`; `route/controller/RouteShapeController.java`; `vehicletracking-frontend/src/components/route/RouteShapeEditor.tsx`. |
| Đổi tuyến đang mô phỏng, khôi phục theo attempt | `reroute/service/TripRouteGeometryService.java::resolve/applyActive`; `simulation/motion/RouteMotion.java::revise`; `TripRouteGeometryServiceTest`, `RouteMotionTest`. |
| Không xóa bộ đếm breach khi đọc cache | `reroute/service/RerouteEvaluationService.java::evaluate`; `RerouteSimulationIntegrationTest::cachedTrafficDoesNotEraseConsecutiveBreachBeforeNextRefresh`. |
| Persistence mới | `vehicletracking-backend/src/main/resources/db/migration/V11__route_shaping_and_simulation_revision.sql`; `RouteRepositoryIntegrationTest::shapingPointsPersistAndCanBeReplacedWithoutSequenceConflict`. |

Đường dẫn class test backend nằm dưới `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/` theo package tương ứng. Đường dẫn class main viết rút gọn ở bảng nằm dưới `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/`.

## Kiểm tra

- Java 26, Node 24. Lệnh Maven cuối dùng `JAVA_TOOL_OPTIONS=-javaagent:<local Maven repository>/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar` để Mockito hoạt động trong sandbox mà không cần dynamic attach. Không đổi cấu hình sản phẩm.
- Bộ test mục tiêu cuối: `./mvnw -q clean -Dtest=TrafficSimulationSpeedTest,RouteMotionTest,TrafficEtaServicePolicyTest,RouteShapeServiceTest,SimulationReplayTest,CheckInReplayTest,TripRouteGeometryServiceTest,ReroutePolicyTest,TrafficRouteMatcherTest,HereTrafficProviderTest test`: exit 0, 58 test, 0 failure/error/skipped. Test matcher thật xác nhận 37 km/h thay vì trần 15.
- Trước lần clean: test mới từng lỗi vì fixture đảo lat/lon (đã sửa); một lượt khác đọc class chứa `Unresolved compilation problems` — dấu hiệu output bị IDE compiler ghi đè. Lần clean Maven cuối đã biên dịch toàn bộ và qua test. Clean chỉ tạo lại output `target`, không xóa source/migration/dữ liệu ứng dụng.
- `node tests/vehicleMotion.test.ts`: 4 test pass. Kiểm tra nội suy qua biên snapshot, mất mẫu, góc cua, quay hướng ngắn nhất.
- `./node_modules/.bin/tsc --noEmit`: exit 0.
- `npm run lint`: exit 0; hai warning có sẵn ở `useFleetWorkspace.ts:93,101`.
- `npm run build`: exit 0; cảnh báo chunk chính lớn hơn 500 kB.
- `git diff --check`: exit 0.

## Giới hạn

- Lệnh `./mvnw -q -Dtest=RerouteSimulationIntegrationTest,RouteRepositoryIntegrationTest test` trên bản cuối trả exit 1 do Testcontainers không được truy cập `/var/run/docker.sock` (`Operation not permitted`). Hai class chưa chạy được, không phải kết quả assertion nghiệp vụ.
- Lượt chạy toàn bộ Maven trước đó từng thành công trên bản trung gian; không dùng kết quả đó để khẳng định bản cuối đã qua toàn bộ test. Xin chạy lại đầy đủ ngoài sandbox có Docker.
- Chưa kiểm tra thủ công browser/FPS, API HERE live, quota, hoặc việc lưu điểm và khôi phục revision trên PostgreSQL ở bản cuối. Không gọi HERE thật trong unit test.
- Không đọc/in `.env`, không thêm key vào frontend. Tài liệu `docs/` đang bị ignore theo cấu hình repository có sẵn; không sửa `.gitignore`.

## Sửa hồi quy frontend — 2026-09-28

Phạm vi rút gọn: bản đồ nhấp nháy khi chọn xe mô phỏng và danh sách trạm bị mất trong panel. Kết quả này không thay thế các giới hạn backend/HERE ở mốc trên.

- Nguyên nhân nháy toàn map: watcher basemap trong `vehicletracking-frontend/src/features/map/composables/useMapLayers.ts` dùng trực tiếp `mapInstanceRef` và `basemapRetry` là `shallowRef`. Getter `options()` ở `MapComponent.vue::useMapLayers` cũng đọc trạng thái chuyến/check-in realtime. Vue force-trigger callback vì có shallow-ref source, tháo và tạo lại tile layer dù theme/showTraffic không đổi.
- Sửa: đọc `.value` bằng getter source; chỉ thay lớp nền khi map, theme, showTraffic hoặc retry thực sự đổi. Cleanup khi đổi lớp nền/unmount được giữ nguyên.
- Test hồi quy `vehicletracking-frontend/tests/unit/map-integration.test.ts::selecting a simulator vehicle and receiving realtime updates keep the loaded basemap` đã FAIL trước sửa (`map.hasLayer(basemap)` trả false sau click), PASS sau sửa. Kiểm tra click xe, ba SSE update ở 10×, giữ tile container và không gọi lại factory tile layer. Test đổi theme và retry vẫn PASS.
- Nguyên nhân mất list: `.simulation-stops-card` có `overflow: hidden` nên bị flex shrink xuống 2px. `vehicletracking-frontend/src/features/simulation/styles/simulator.css` thêm `flex-shrink: 0`; giữ scroll danh sách và vùng điều khiển sticky. Compact view đặt traffic pill dưới drawer simulator để không che nút hủy/chạy lại.
- `npm run lint`: exit 0.
- `npm run typecheck`: exit 0.
- `npm run test:unit`: exit 0, 117 test trong 16 file PASS.
- `npm run test:motion`: exit 0, 5 test PASS.
- `npm run build`: exit 0; `git diff --check`: exit 0.
- Chromium/Playwright với fixture API/SSE (`tests/fixtures/api.mjs`), không backend/database thật: 1920×1000, 1440×800 và 390×844 đều giữ cùng tile-layer DOM qua chọn xe và 10 SSE update; 0 lần thay lớp nền, 0 page error. List có đủ 3 trạm và cao 220,5px; mọi nút điều khiển không bị che. Cuộn/chọn trạm cuối mở được popup thông tin.
- Ảnh kiểm tra nằm tạm ở `/tmp/vehicletracking-simulator-1920-check.png`, `/tmp/vehicletracking-simulator-1440-check.png`, `/tmp/vehicletracking-simulator-390-check.png`. Tile provider được mock; không tuyên bố đã kiểm tra độ ổn định mạng Google/HERE thật hay đo FPS.
