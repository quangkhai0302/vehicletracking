# Requirement — Điều phối chuyến tức thời

Trạng thái: **Approved**

## Bối cảnh

Màn tạo chuyến thủ công hiện bắt nhập giờ xuất phát và toàn bộ giao diện gọi đó là giờ kế hoạch. Điều này làm lẫn hai nghiệp vụ: tuyến đường chỉ mô tả lộ trình, còn ngày/giờ chạy cố định thuộc lịch chạy tự động.

## Mục tiêu

- Chuyến tạo thủ công là chuyến điều phối tức thời, không yêu cầu giờ xuất phát.
- Chỉ chuyến được sinh từ lịch chạy tự động có giờ xuất phát và hoàn thành theo kế hoạch.
- Người dùng chỉnh giờ tại cấu hình lịch chạy tự động, không chỉnh trực tiếp trên chuyến.
- Không làm thay đổi lộ trình, điểm dừng, mô phỏng và dữ liệu lịch sử hiện có.

## Trong phạm vi

- Contract tạo chuyến thủ công, quy tắc tạo chuyến và kiểm tra xung đột.
- Dấu hiệu nguồn tạo chuyến trong response API.
- Form tạo chuyến, danh sách, chi tiết, dashboard, giám sát và cổng tài xế.
- Test backend/frontend liên quan.

## Ngoài phạm vi

- Thay đổi cơ chế lặp của lịch tự động.
- Thay đổi thuật toán tuyến đường, ETA hoặc bản đồ giám sát.
- Chuyển đổi/xóa dữ liệu chuyến cũ.

## Quy tắc chức năng

1. API tạo chuyến thủ công chỉ nhận xe, tuyến và tài xế tùy chọn.
2. Backend lấy thời điểm tạo làm mốc kỹ thuật cho chuyến tức thời; người dùng không nhập hoặc sửa mốc này.
3. Chuyến sinh từ lịch tự động giữ occurrence làm giờ kế hoạch.
4. API trả nguồn điều phối `ON_DEMAND` hoặc `FIXED_SCHEDULE`.
5. UI chỉ dùng cụm từ “kế hoạch/theo lịch” cho `FIXED_SCHEDULE`.
6. Chuyến tức thời chỉ bị chặn khởi hành khi xe hoặc tài xế đang thực hiện chuyến khác, ngoài các ràng buộc trạng thái/hoạt động đã có.
7. Xung đột thời gian dự kiến chỉ áp dụng giữa các occurrence của lịch cố định.

## Acceptance criteria

- **AC-01:** Form tạo chuyến không còn trường giờ xuất phát và request không gửi `scheduledDepartureAt`.
- **AC-02:** Chuyến thủ công được tạo với `dispatchMode=ON_DEMAND`; chuyến do scheduler tạo có `dispatchMode=FIXED_SCHEDULE` và thông tin lịch nguồn.
- **AC-03:** UI của chuyến tức thời không hiển thị giờ xuất phát/hoàn thành “theo lịch”.
- **AC-04:** UI của chuyến lịch cố định vẫn hiển thị đúng giờ kế hoạch.
- **AC-05:** Không còn thao tác sửa giờ xuất phát tại chi tiết chuyến.
- **AC-06:** Chuyến tức thời không bị từ chối chỉ vì mốc kế hoạch của một chuyến chờ khác; khởi hành vẫn chặn xe/tài xế đang chạy chuyến khác.
- **AC-07:** Lint, typecheck, unit test, frontend build và backend test liên quan vượt qua.

## Giả định

Các bản ghi có `schedule_id` là chuyến lịch cố định; bản ghi không có `schedule_id` là chuyến điều phối tức thời. Cột `scheduled_departure_at` hiện hữu tiếp tục được dùng làm mốc kỹ thuật để tương thích với mô phỏng và dữ liệu cũ.
