# Requirement — Gộp màn hình tuyến và chuyến

Trạng thái: Verified (đã triển khai và kiểm chứng ngày 2026-09-24).

## Mục tiêu

Một không gian quản lý vận hành cho tuyến đường và chuyến đi, giữ đủ thông tin và thao tác hiện có; không gộp hai thực thể nghiệp vụ.

## Phạm vi

- Một mục điều hướng với hai tab Tuyến đường và Chuyến đi.
- Giữ danh sách, tìm/lọc, form, chi tiết, lỗi/loading/empty/confirm và hành động hiện có của hai trang.
- Chi tiết chuyến phải tận dụng bề ngang trang quản lý, phân cấp rõ tổng quan, thời gian, hành trình, tiến độ và ETA; responsive không làm mất dữ liệu hoặc thao tác.
- Từ chi tiết tuyến tạo chuyến với tuyến đã chọn; từ chi tiết chuyến mở đúng tuyến.
- Giữ URL `/routes`, `/trips`, query lọc xe và các liên kết cũ. Bản đồ giám sát `/operations` không đổi.
- Không đổi backend API/schema, không thêm dữ liệu giả, không gộp model tuyến với chuyến.

## Acceptance criteria

1. Sidebar chỉ có một mục chung; `/routes` và `/trips` mở cùng workspace ở đúng tab, có trạng thái active và dùng được trên màn hình nhỏ.
2. Mọi khả năng đang có của từng trang vẫn hoạt động, gồm tạo/xem/ngừng tuyến và tạo/xem/sửa lịch/gán tài xế/chuyển trạng thái/xóa chuyến theo quyền hiện tại; chi tiết chuyến hiển thị rõ ở desktop, tablet và mobile.
3. Từ chi tiết tuyến mở form tạo chuyến với tuyến đã chọn; từ chi tiết chuyến mở chi tiết đúng tuyến.
4. Deep link, Back/Forward, query `vehicleId`, loading/error/empty và xác nhận thao tác tiếp tục đúng.
5. Frontend lint, typecheck, unit, motion và build đạt; không có thay đổi backend hay secret.
