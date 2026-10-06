# Implementation plan

1. Tạo Flyway V7 và JPA entity/repository cho revision, checkpoint, notification.
2. Bổ sung `baselineRemainingSeconds` vào ETA; xây `ReroutePolicy` và evaluator nối vào ETA/simulator/telemetry accepted để GPS thật cũng tự đánh giá.
3. Thêm DTO/controller cho revision và notification; đưa notifications vào OperationsSnapshot/SSE.
4. Thêm frontend types/service, AlertStream và style; giữ normalization cho snapshot cũ.
5. Chạy unit backend, compile, lint/type-check/build frontend; cập nhật evidence/walkthrough.

Trạng thái: Verified với compile/unit/frontend checks; full integration còn phụ thuộc Docker/PostgreSQL và môi trường Mockito.

## Bugfix copy — 06/10/2026

Approved theo yêu cầu trực tiếp; dùng quy trình rút gọn Survey → Plan → Implement → Verify.

1. Dùng `reroute/RerouteMessages.java` cho hai nội dung trung tính và mapping tương thích lịch sử; nối producer và hai DTO.
2. Regression trong `NotificationServiceTest`: notification/revision cũ trả copy mới mà entity lịch sử không đổi; lý do khác/null được giữ.
3. Chạy tests reroute/notification liên quan bằng Java 26 và PostgreSQL Testcontainers; ghi kết quả và giới hạn. Không thay frontend hoặc migration.
