# Evidence — Implemented

Ngày kiểm tra: 2026-09-15.

## Source

- `TrafficEtaService` lấy toàn bộ Flow khớp geometry, chọn Flow ở gần xe khi simulator tick, và lấy mẫu từng đoạn thẳng geometry để ETA cộng tốc độ nhanh/chậm riêng.
- `TrafficRouteMatcher` trả khoảng cách hình học, dùng hướng cục bộ gần Flow để tránh ghép nhầm chiều ngược lại và cho phép chọn Flow gần xe.
- Không đổi endpoint, DTO, database hoặc frontend; frame telemetry hiện có đã nhận vận tốc mô phỏng sau khi áp rate.

## Kiểm tra

- `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn PATH=/home/khainq/.sdkman/candidates/java/26.0.1-amzn/bin:$PATH ./mvnw -Dtest=TrafficEtaServicePolicyTest,TrafficRouteMatcherTest,HereTrafficProviderTest test`: exit 0, 14 test đạt.
- `git diff --check`: đạt.
- Frontend với Node 24.16.0: TypeScript `--noEmit` và production build đạt; lint exit 0 với 2 warning cũ tại `useFleetWorkspace.ts`.
- Full `./mvnw test` đã được gọi nhưng không thể hoàn tất trong môi trường này: Mockito inline không tự attach được Java agent và Testcontainers không kết nối được Docker. Các lỗi này xuất hiện ở test cũ dùng mock/Spring context hoặc PostgreSQL container, không phải assertion failure của thay đổi này.

## Giới hạn

Chưa xác nhận browser/live HERE end-to-end. HERE cache hiện có thể giữ snapshot Flow tối đa TTL cấu hình trước khi simulator nhận dữ liệu mới.
