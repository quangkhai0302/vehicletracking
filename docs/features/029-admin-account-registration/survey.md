# Survey — Đăng ký tài khoản admin

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Auth controller đã có endpoint public login/csrf và các endpoint auth còn lại yêu cầu phiên | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/auth/config/SecurityConfig.java` — `securityFilterChain`, các matcher `/api/v1/auth/**` | Registration phải được permit riêng trước matcher authenticated |
| Account service đã chuẩn hóa username và encode password khi tạo driver account | `auth/service/UserAccountService.java` — `normalizeUsername`, `createDriverAccount` | Tái sử dụng quy tắc và encoder, không tạo persistence flow thứ hai |
| Frontend có `appFetch` tự bootstrap CSRF cho mọi mutation và `LoginPage` public | `vehicletracking-frontend/src/services/http.ts`, `src/pages/LoginPage.tsx` | Trang đăng ký dùng service chung và liên kết từ login |
| Schema account yêu cầu role/driver nhất quán | `vehicletracking-backend/src/main/resources/db/migration/V18__create_user_accounts.sql` | Admin registration phải lưu `driver_id = NULL`, role cố định `ADMIN` |

Working tree đã có thay đổi của các feature trước; feature này chỉ bổ sung auth DTO/config/service/controller, route UI, test và tài liệu liên quan.
