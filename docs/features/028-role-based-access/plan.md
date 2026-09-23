# Plan — Phân quyền người dùng và cổng tài xế

Feature được triển khai trực tiếp theo yêu cầu người dùng.

1. **Identity và enforcement** — tạo `V18__create_user_accounts.sql`, auth entity/repository/service/config/controller, BCrypt và bootstrap admin qua env; test compile/security contract. Phụ thuộc: bảng `drivers` từ V14; rủi ro là cookie/CORS phải khớp origin frontend.
2. **Driver scope** — thêm finder `find...ByDriverId` trong trip/schedule repository, `DriverPortalService`/controller và unit test; không tái sử dụng endpoint admin cho dữ liệu tài xế. Điều kiện xong: detail ngoài scope trả 404.
3. **Frontend role shell** — thêm `AuthProvider`, guards, credentials cho các service, `LoginPage`, `UserManagementPage`, driver portal và route CSS. Điều kiện xong: admin/driver được redirect đúng role; portal không có mutation/map toàn đội.
4. **Verification/evidence** — chạy lint/tsc, Maven test/package trong môi trường khả dụng, cập nhật evidence/walkthrough/review và reviewer độc lập. Nếu JDK/Node thiếu, ghi exit code và không tuyên bố pass.

Điều kiện hoàn thành: backend matcher, migration, DTO/service, route guard và UI cùng contract; không có secret hoặc thay đổi ngoài phạm vi.
