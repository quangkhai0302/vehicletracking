# Walkthrough — Đăng ký tài khoản admin

1. Mở `/register` từ liên kết ở `/login`, nhập username, mật khẩu tối thiểu 8 ký tự và xác nhận mật khẩu.
2. Backend kiểm tra CSRF, normalize username, tạo account `ADMIN` với password hash và trả `201`; người dùng bấm link để đăng nhập.
3. Username trùng hoặc CSRF thiếu bị từ chối.

Không có migration mới vì bảng `user_accounts` từ V18 đã hỗ trợ admin không gắn driver.
