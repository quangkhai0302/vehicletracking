# Sửa lỗi polling ETA và fingerprint đổi tuyến

## Phạm vi và kế hoạch rút gọn

- `useTripEta`: đợi request kết thúc rồi mới hẹn lần tiếp theo sau 10 giây; hủy khi đổi chuyến/unmount/retry.
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
