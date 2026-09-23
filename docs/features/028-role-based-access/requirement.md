# Requirement — Phân quyền người dùng và cổng tài xế

## Mục tiêu

Đưa quyền truy cập từ mức menu lên enforcement ở backend, đồng thời cung cấp một cổng đọc-only cho tài xế. Admin quản lý toàn bộ vận hành; tài xế chỉ xem chuyến và lịch chạy được gán cho hồ sơ của mình.

## Phạm vi

### In scope

- Đăng nhập, đăng xuất, khôi phục phiên và xem người dùng hiện tại.
- Hai role `ADMIN` và `DRIVER`, tài khoản tài xế liên kết một-một với hồ sơ tài xế.
- Admin cấp/khóa/mở khóa tài khoản tài xế và tiếp tục dùng các API quản trị hiện có.
- Driver API chỉ trả chuyến/lịch có `driver_id` của phiên; không có mutation cho tài xế.
- UI `/login`, `/users` cho admin và `/driver/today`, `/driver/schedules` cho tài xế.

### Out of scope

- Phân quyền chi tiết theo từng nút hoặc nhiều tenant.
- Tự đăng ký driver, reset mật khẩu qua email, SSO/MFA. Đăng ký admin được mô tả riêng ở Feature 029.
- Cho tài xế sửa chuyến, nhận chuyến, điều khiển mô phỏng hoặc xem bản đồ toàn đội xe.

## Acceptance criteria

1. API vận hành yêu cầu phiên admin; request không xác thực bị từ chối.
2. Tài khoản driver chỉ vào được `/api/v1/driver/**`; các API quản trị trả 403.
3. Mỗi tài khoản driver gắn tối đa một hồ sơ active; username duy nhất; mật khẩu không lưu plaintext.
4. Driver không thể đọc chuyến/lịch của driver khác kể cả khi biết ID.
5. Khóa tài khoản hoặc deactive hồ sơ driver ngăn đăng nhập mới.
6. Admin có UI quản lý tài khoản và vẫn thấy các trang quản lý tài xế, xe, tuyến, trạm, chuyến, lịch và báo cáo.
7. Driver UI hiển thị tên, chuyến được giao, lịch chạy, xe/tuyến/trạm cần thiết; có loading/error/empty/logout và không hiển thị thao tác ghi.

## Non-functional requirements

- Không lưu password/token plaintext trong database, log hoặc biến `VITE_*`.
- Mọi quyết định role phải được kiểm tra ở backend; guard/menu frontend chỉ là lớp UX.
- Các truy vấn driver phải có điều kiện `driverId` ở repository, tránh tải toàn bộ rồi lọc ở trình duyệt.
