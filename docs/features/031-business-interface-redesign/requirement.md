# 031 — Thiết kế lại giao diện quản trị

Người dùng yêu cầu làm lại toàn bộ giao diện trừ phần maps đã tối ưu. Actor: quản trị viên và tài xế. Mục tiêu: dễ đọc, đồng bộ và thao tác quản lý rõ ràng.

In scope: shell quản trị, dashboard, phương tiện/tài xế/chuyến, lịch chạy, cảnh báo, báo cáo, tài khoản, login/register và cổng tài xế. Out of scope: bản đồ (bao gồm shell map), API, schema, nghiệp vụ và thư viện UI mới.

- AC1: Các trang nghiệp vụ dùng cùng typography, màu, button, input, trạng thái và header; không còn chữ dark-theme trên nền sáng.
- AC2: Danh sách phương tiện/tài xế/chuyến hỗ trợ đọc theo hàng/cột trên desktop, card theo hàng trên mobile, giữ tìm kiếm/lọc và thao tác hiện có.
- AC3: Auth và cổng tài xế cùng ngôn ngữ thiết kế; nội dung dài cuộn được.
- AC4: Desktop 1440px, tablet 768px, mobile 390px không tràn viewport; drawer/modal và điều hướng dùng được với bàn phím.
- AC5: Maps và logic nghiệp vụ hiện có được bảo toàn.
- AC6: Type check, lint, build và kiểm tra trình duyệt có evidence; fixture chỉ dùng trong kiểm tra, không đưa vào app.

Người dùng đã yêu cầu trực tiếp triển khai. Không có yêu cầu backend/migration.
