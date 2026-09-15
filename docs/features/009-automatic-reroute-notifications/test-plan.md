# Test plan

| AC | Mức test | Kịch bản | Kết quả mong đợi |
|---|---|---|---|
| 1–2 | Unit | `ReroutePolicyTest` với fetch trùng/khác timestamp và breach delay | Chỉ fetch mới được đếm; lần thứ hai mới trigger |
| 3 | Service/integration | HERE trả tuyến thay thế, trả lỗi hoặc ETA mới không tốt hơn | Revision hoặc `REROUTE_UNAVAILABLE` đúng loại |
| 4 | Repository/integration | unique dedupe, active revision và cooldown | Không tạo bản ghi lặp |
| 5 | Controller | GET revisions/notifications, POST read | DTO và trạng thái đọc đúng |
| 6 | Frontend | lint, type-check, build; mở bảng cảnh báo/đánh dấu đọc | UI hiển thị và cập nhật không lỗi |
