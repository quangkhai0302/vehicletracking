# Spec — Phân quyền người dùng và cổng tài xế

## Security contract

Session HTTP được tạo bởi `POST /api/v1/auth/login` và gửi lại bằng cookie HttpOnly/SameSite=Lax; session identifier được rotate sau khi xác thực. `GET /api/v1/auth/me` trả role hiện tại; `POST /api/v1/auth/logout` hủy security context. CORS chỉ cho phép origin cấu hình cụ thể.

| Phạm vi | Quyền |
|---|---|
| `/api/v1/auth/login`, `/api/v1/auth/register-admin` | public route (CSRF vẫn bắt buộc với POST) |
| `/api/v1/auth/csrf` | public |
| `/api/v1/auth/**` | authenticated |
| `/api/v1/driver/**` | `ROLE_DRIVER` |
| `/api/v1/users/**` và các API `/api/v1/**` khác | `ROLE_ADMIN` |

Tài khoản được lưu ở `vehicle_tracking.user_accounts`. `role=ADMIN` bắt buộc `driver_id IS NULL`; `role=DRIVER` bắt buộc có `driver_id`, unique. Password lưu BCrypt hash.

Migration V18 chỉ tạo bảng/index/foreign key mới, không sửa migration V14–V17. Driver không bị xóa vật lý trong lifecycle hiện tại; FK `ON DELETE RESTRICT` bảo toàn liên kết lịch sử.

401/403 dùng Problem Detail của Spring MVC khi có body lỗi; các controller không trả JPA entity trực tiếp. Username được normalize lowercase và validate `[a-z0-9][a-z0-9._-]{2,99}`; mật khẩu driver dài 8–100 ký tự.

## API contract

### Auth

- `POST /api/v1/auth/login` body `{ "username": "...", "password": "..." }` → `AuthUserResponse`.
- `POST /api/v1/auth/register-admin` tạo admin public với contract chi tiết ở Feature 029.
- `GET /api/v1/auth/me` → account id, username, role, active, driver id/name.
- `POST /api/v1/auth/logout` → 204.

### Admin account management

- `GET /api/v1/users` → danh sách tài khoản.
- `POST /api/v1/users/driver` body username/password/driverId → 201.
- `POST /api/v1/users/{id}/enable`, `/disable` → account đã cập nhật.

`active` trong account response là trạng thái hiệu lực: account phải active và, với DRIVER, hồ sơ driver cũng phải active.

### Driver portal

- `GET /api/v1/driver/trips?status=&from=&to=` → chỉ trip có `driver_id` của principal.
- `GET /api/v1/driver/trips/{id}` → 404 nếu ID không thuộc principal.
- `GET /api/v1/driver/schedules` → chỉ schedule có driver của principal; `nextRunAt` nullable nếu không tính được.

## UI contract

- `/login`: form username/password, lỗi xác thực, redirect theo role.
- Admin shell: menu Người dùng, account badge và logout; `/users` cấp tài khoản driver, khóa/mở khóa.
- Driver portal: header tài khoản, tab Chuyến của tôi/Lịch chạy, KPI hôm nay, danh sách read-only, panel chi tiết chuyến theo stops và nút logout.
- Guard chuyển anonymous về `/login`, driver khỏi route admin về `/driver/today`, admin khỏi route driver về `/dashboard`.
