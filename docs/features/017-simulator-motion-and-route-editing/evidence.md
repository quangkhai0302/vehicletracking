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
