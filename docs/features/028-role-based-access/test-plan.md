# Test Plan — Phân quyền người dùng và cổng tài xế

| AC | Mức test | Kịch bản | Dữ liệu/fixture | Kết quả mong đợi |
|---|---|---|---|---|
| 1–2 | Security/MockMvc | anonymous gọi API admin/driver; admin và driver gọi chéo phạm vi | session không có; principal ADMIN/DRIVER | 401/403 đúng role |
| 3 | Service + migration | tạo trùng username/driver; password lưu hash; kiểm tra constraint role-driver | hai account cùng username/driver, role null-driver | 409 hoặc DB reject; không plaintext |
| 4 | Service | driver A yêu cầu trip ID của driver B | principal driver 42, trip 99 của driver khác | 404; repository finder luôn nhận driver 42 |
| 5 | Authentication | khóa account hoặc deactive hồ sơ rồi login | active=false ở account/driver | login bị từ chối |
| 6 | Frontend manual + type | admin mở `/users`, menu quản trị, logout | bootstrap admin và driver catalog | thao tác account và guard hoạt động |
| 7 | Frontend manual | driver mở `/driver/today`, `/driver/schedules`, loading/error/empty/detail | assigned trip/schedule và response rỗng/lỗi | chỉ dữ liệu assigned, không có nút ghi/map toàn đội |
| Verify | Tooling | lint, tsc, Maven test/package | môi trường Node/JDK/repository hiện tại | ghi lệnh, kết quả và blocker môi trường thực tế |
