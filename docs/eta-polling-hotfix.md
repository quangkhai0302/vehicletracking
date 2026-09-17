# Sửa lỗi polling ETA và fingerprint đổi tuyến

## Bổ sung theo thread dump EC2 (2026-09-17)

Survey: cả operations-stream (OperationsSnapshotService -> SimulationService.describe -> trafficMetadata)
và simulation-clock (TelemetryService.afterCommit -> RerouteEvaluationService.evaluateCurrent) đang chạy
TrafficEtaService.matchingFlows/TrafficRouteMatcher trong hai dump. Sửa theo quy trình bugfix rút gọn:

- OperationsSnapshotService dùng describeSnapshot: không tính ETA hay simulationRate trên luồng SSE.
  Tốc độ lấy từ telemetry đúng trip/attempt. Metadata ETA đọc từ cache tối đa 10 giây; khi thiếu/hết hạn
  trả UNAVAILABLE rõ ràng, không bịa thông tin traffic. Cache giới hạn 128 chuyến, chỉ publish sau commit.
- Cache match hình học LRU 16 mục, khóa gồm polyline, nội dung flow (cả tốc độ) và bán kính;
  không phụ thuộc ageSeconds của envelope. Các lần miss được đồng bộ để không tính trùng.
  Không cache toàn response ETA cho HTTP: vị trí, check-in vẫn tính hiện tại.
- Lọc khoảng latitude bảo thủ trước phép đo chi tiết, không dùng longitude tránh lỗi tại đường đổi ngày.
- evaluateCurrent giới hạn 10 giây/chuyến/attempt; tối đa 128 checkpoint. Replay bỏ qua checkpoint cũ.
  Đây là độ trễ có chủ ý tối đa khoảng 10 giây trước lần đánh giá từ telemetry tiếp theo;
  endpoint ETA vẫn đánh giá đổi tuyến như trước. Không bỏ gọi provider vĩnh viễn.

Kiểm tra: Java 26, `./mvnw -Dtest='Traffic*Test,SimulationReplayTest,ReroutePolicyTest,RerouteFingerprintTest' test`
đạt 38 tests. Test bổ sung xác nhận snapshot không calculate/simulationRate; cache reuse khi age đổi
và miss khi tốc độ flow đổi. Full `./mvnw test` ở bước trước test cache mới: 199 tests,
0 failures, 6 errors do Docker không khả dụng. `git diff --check` sạch.

Giới hạn: chưa benchmark EC2 hoặc kiểm thử SSE end-to-end. Cache match dùng monitor chung, có thể
serialize các cache miss khác tuyến; HTTP và simulation vẫn có thể tính phần duration song song.
Chưa tuyên bố xử lý hoàn toàn OOM. Cần đo lại CPU, ETA latency và realtime sau deploy backend.

## Phạm vi và kế hoạch rút gọn

- `useTripEta`: đợi request kết thúc rồi mới hẹn lần tiếp theo sau 10 giây; hủy khi đổi chuyến/unmount/retry.
- `useVehicleMarkers`: khi snapshot đến muộn hơn bộ đệm 1,5 giây, tạo một mẫu nối tại vị trí
  đang hiển thị rồi nội suy đến tọa độ mới theo độ trễ (tối đa 10 giây). Vì vậy bản tin SSE bị trễ
  không làm marker nhảy vọt; khi không còn bản tin, animation vẫn dừng ở mẫu cuối.
- `TrafficEtaService.matchingFlows`: giải mã polyline một lần mỗi nhóm ứng viên thay vì một lần mỗi ứng viên; giữ nguyên thuật toán khoảng cách và hướng.
- `bestMatchingFlowAtPosition`: vòng lặp tìm min thay stream/record ở mỗi cạnh tuyến để giảm cấp phát tạm.
- `RerouteEvaluationService.fingerprint`: chuỗi ghép ID có thể vượt `VARCHAR(255)` trong V7 (`breach_fingerprint`, `last_triggered_fingerprint`, `dedupe_key`). Giữ khóa ngắn <=200 ký tự; khóa dài chuyển thành SHA-256 (71 ký tự kể cả prefix). Không cắt ID và không sửa migration đã áp dụng.

## Kiểm chứng (2026-09-17)

- Node 24: `npm run lint`, `./node_modules/.bin/tsc --noEmit`, `npm run build`: thành công. Còn hai warning cũ ở useFleetWorkspace và warning kích thước bundle.
- Lần chạy Maven với Java mặc định 17 thất bại do yêu cầu release 26. Đã chuyển JAVA_HOME sang Java 26.
- Java 26 `./mvnw test`: 198 tests, 0 failures, 6 errors do không có Docker khả dụng cho integration tests; không coi full suite là đạt.
- Java 26 `./mvnw -Dtest='RerouteFingerprintTest,TrafficRouteMatcherTest,TrafficEtaServicePolicyTest,TrafficEtaControllerTest,ReroutePolicyTest' test`: 16 tests, 0 failures/errors, BUILD SUCCESS.
- Test fingerprint bao phủ khóa dài, tính ổn định, phân biệt suffix, ranh giới 200/201 và kích thước khóa notification. Test matcher đối chiếu đường đi decoded với đường đi encoded.

## Giới hạn / kiểm tra triển khai

- Chưa deploy EC2, chưa benchmark CPU/heap production, chưa chứng minh hết nguyên nhân OOM.
- Log production không chỉ tên cột lỗi; sửa đường ghi fingerprint có thể vượt giới hạn đã xác định trong source, không khẳng định mọi lỗi varchar(255) đều đến từ đó.
- Chưa có kiểm thử trình duyệt tự động cho hook: cần xác nhận request >10 giây không bị polling hủy; sau thành công/thất bại đợi 10 giây mới gửi tiếp; đổi chuyến không hiển thị kết quả chuyến cũ.
- Không thêm cache giữ geometry lâu dài; dữ liệu traffic vẫn lấy theo cơ chế hiện có. Các tab khác nhau vẫn có thể gửi yêu cầu đồng thời.
- Fingerprint dài cũ nếu tồn tại sẽ có một lần chuyển định dạng; khóa ngắn giữ nguyên. API và schema không thay đổi.
