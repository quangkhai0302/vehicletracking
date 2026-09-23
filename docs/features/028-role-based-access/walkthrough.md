# Walkthrough — Phân quyền người dùng và cổng tài xế

## Admin

1. Đặt `AUTH_BOOTSTRAP_ADMIN_PASSWORD` trong môi trường backend rồi khởi động để tạo admin lần đầu; không đặt password trong source.
2. Mở `/login`, đăng nhập admin; menu quản trị gồm phương tiện, tài xế, chuyến, tuyến, trạm, lịch chạy, cảnh báo, báo cáo và Người dùng.
3. Mở **Người dùng**, chọn hồ sơ driver active, nhập username/mật khẩu tạm thời và tạo tài khoản; có thể khóa/mở khóa account.

## Driver

1. Đăng nhập bằng tài khoản được admin cấp; hệ thống chuyển tới `/driver/today`, không tải shell/map toàn đội.
2. Xem các chuyến assigned, chọn một chuyến để xem xe, giờ khởi hành và trình tự stops.
3. Mở **Lịch chạy** để xem lịch route/vehicle/timezone/next run được gán; màn hình không có thao tác sửa, xóa, start hoặc điều khiển mô phỏng.

## API kiểm tra nhanh

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `GET /api/v1/driver/trips`
- `GET /api/v1/driver/schedules`
