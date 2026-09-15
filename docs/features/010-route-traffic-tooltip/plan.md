# Kế hoạch 010

1. Thêm model frontend thuần cho projection/matching, freshness và tính metrics; bổ sung lengthMeters optional khớp DTO backend.
2. Hook tải traffic theo ô quanh hover, request hủy được, cập nhật định kỳ; dùng services hiện có.
3. Component riêng tạo lớp tương tác trên tuyến và card dark responsive, có keyboard/touch; tích hợp vào MapComponent, giữ nguyên tuyến/marker/camera hiện tại.
4. Kiểm tra hình học, stale/zero/closure và incident hiệu lực bằng test thuần; browser fixture kiểm tra hover/pin/keyboard/mobile/race/error/cleanup. Chạy lint, tsc --noEmit và build.
5. Ghi kết quả thực tế vào verification và liên kết trong progress/handoff. Không commit/push.

Đã hoàn thành các bước trên. Xem [verification](verification.md): 10 test hình học, 12 nhóm browser fixture và frontend lint/tsc/build đạt; chưa chạy HERE live. Browser phát hiện và đã sửa vùng control vô hình che tuyến trên mobile trong `workspace.css`.

Phần mở rộng flow theo viewport/`RoadInspectionLayer` đã được thử và sau đó **người dùng rút yêu cầu**. Không tiếp tục triển khai hover mọi đường nền.

Điều chỉnh cuối đã hoàn thành: gỡ layer và request flow theo viewport, thêm `TripTrafficSummary` dùng chung cho xe được chọn và simulator, ưu tiên vận tốc/trạm/ETA trước điều khiển, phân biệt nguồn và sự cố từ ETA backend. `tripTrafficView` chọn response mới có dữ liệu, không để metadata unavailable hoặc ETA cũ che trạng thái blocked. Wrapper thẻ xe được camera tính là vùng che phía trên, có giới hạn chiều cao mobile để giữ marker trong vùng nhìn. Xác minh 9 unit, 12 browser tracking (SSE/GPS/mobile/đổi chuyến/hủy polling), 10 browser lifecycle simulator hai tab, 12 browser route regression, lint/tsc/build; GET-only trên ứng dụng thật ghi nhận chuyến đã kết thúc. Xem verification cho giới hạn bằng chứng; không thay engine backend trong lượt này.
