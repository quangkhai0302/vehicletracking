# Feature 009 — Tự động đổi tuyến và thông báo giao thông

## Bối cảnh

Chuyến đang chạy đã có ETA từ HERE Traffic (Feature 008) nhưng chưa có cơ chế đánh giá trễ/đường đóng, lưu tuyến thay thế hoặc báo cho điều hành viên.

## Phạm vi

In scope: đánh giá chuyến `IN_PROGRESS`; yêu cầu hai lần dữ liệu HERE khác thời điểm; tạo revision tuyến thay thế khi đường đóng hoặc tuyến mới cải thiện ETA; lưu thông báo idempotent; API/SSE và bảng cảnh báo UI.

Out of scope: thay thế route snapshot gốc, điều phối tài xế thật, push notification ngoài trình duyệt, tự cập nhật bản đồ dẫn đường của xe.

## Acceptance criteria

1. Chỉ HERE live/last-known có `trafficFetchedAt` mới được đánh giá; cache hit cùng timestamp không tạo trigger.
2. Vi phạm là `BLOCKED` hoặc trễ ít nhất giá trị `REROUTE_DELAY_SECONDS` (mặc định 600) và `REROUTE_DELAY_PERCENT` (mặc định 30%); phải xuất hiện trong số lần fetch cấu hình (mặc định 2).
3. Đường đóng tạo revision nếu HERE trả tuyến; trễ chỉ tạo revision khi ETA mới tốt hơn ETA động. Nếu không, tạo `REROUTE_UNAVAILABLE`.
4. Revision và notification có dedupe/cooldown 300 giây, giữ nguyên route/trip snapshot ban đầu.
5. Có API đọc revision, đọc thông báo và đánh dấu đã đọc; snapshot SSE chứa thông báo gần đây.
6. UI hiển thị cảnh báo chưa đọc và cho phép đánh dấu đã đọc.

## Phụ thuộc

HERE Routing/Traffic được cấu hình ở backend; migration Flyway V7 phải được áp dụng. Không đưa API key vào frontend.
