# Test Plan — Đăng ký tài khoản admin

| AC | Mức test | Kịch bản | Kết quả mong đợi |
|---|---|---|---|
| 1 | Service + MockMvc | username mới; gọi endpoint có CSRF | account ADMIN, HTTP 201 |
| 2 | Service | username đã tồn tại/username có ký tự unsafe | 409 hoặc 400 |
| 3 | Service | kiểm tra entity sau save | password field là encoded hash |
| 4 | MockMvc | POST không có CSRF | 403 |
| 5 | Frontend tooling/manual | mở `/register`, mismatch password, API success | typecheck/build pass; hiển thị error/success/link login |
| Verify | Tooling | Maven auth tests, frontend lint/tsc/build, diff check | ghi exit code và giới hạn môi trường thật |
