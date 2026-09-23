# Review — Phân quyền người dùng và cổng tài xế

Review nội bộ sau implementation:

- Backend enforcement nằm trong `SecurityConfig`, không phụ thuộc việc ẩn menu; các API hiện có mặc định yêu cầu `ROLE_ADMIN`.
- Driver portal dùng finder có `driverId` cho cả list và detail; unit test kiểm tra không gọi finder unscoped.
- Migration ràng buộc username/driver unique và nhất quán role-driver; BCrypt không lưu plaintext.
- Hồ sơ driver inactive cũng làm principal không enabled; khóa account có hiệu lực ở lần xác thực tiếp theo.
- Frontend tách portal driver khỏi `ApplicationShell`/`FleetWorkspace`, có credentials cookie, guard role và các trạng thái UX cơ bản.
- Đã chạy non-Docker Maven suite (254 tests) trên JDK 26 và frontend build trên Node 24; full suite còn 6 integration test cần Docker daemon.

Trạng thái: **Implemented; verified with environment limitations; follow-up runtime verification required**.
