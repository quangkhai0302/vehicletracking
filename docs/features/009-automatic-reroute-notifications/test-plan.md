# Test plan

| AC | Mức test | Kịch bản | Kết quả mong đợi |
|---|---|---|---|
| 1–2 | Unit | `ReroutePolicyTest` với fetch trùng/khác timestamp và breach delay | Chỉ fetch mới được đếm; lần thứ hai mới trigger |
| 3 | Service/integration | HERE trả tuyến thay thế, trả lỗi hoặc ETA mới không tốt hơn | Revision hoặc `REROUTE_UNAVAILABLE` đúng loại |
| 4 | Repository/integration | unique dedupe, active revision và cooldown | Không tạo bản ghi lặp |
| 5 | Controller | GET revisions/notifications, POST read | DTO và trạng thái đọc đúng |
| 6 | Frontend | lint, type-check, build; mở bảng cảnh báo/đánh dấu đọc | UI hiển thị và cập nhật không lỗi |

## UI copy (AC7)

- Unit response mapping: legacy unavailable/closure ở notification, closure ở revision, giữ giá trị entity; pass-through lý do khác và null revision.
- Regression sẵn có: NotificationService delete giữ lịch sử; ReroutePolicy/Fingerprint và RerouteSimulationIntegrationTest để xác minh trigger/mô phỏng không bị đổi.
- Lệnh targeted: `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn bash ./mvnw -Dtest=NotificationServiceTest,RerouteFingerprintTest,ReroutePolicyTest,RerouteSimulationIntegrationTest test`.
