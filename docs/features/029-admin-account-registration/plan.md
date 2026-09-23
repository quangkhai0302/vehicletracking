# Plan — Đăng ký tài khoản admin

Feature được triển khai trực tiếp theo yêu cầu người dùng.

1. Bổ sung DTO đăng ký; service kiểm tra username và encode password.
2. Mở route controller có CSRF, permit matcher riêng và cấu hình env production mẫu.
3. Thêm service/controller test cho duplicate, CSRF và response 201.
4. Thêm trang React `/register`, liên kết từ login, type/service và trạng thái UX.
5. Cập nhật evidence/walkthrough, chạy kiểm tra backend/frontend.

Điều kiện xong: chỉ tạo ADMIN, không lộ password, contract frontend/backend đồng nhất.
