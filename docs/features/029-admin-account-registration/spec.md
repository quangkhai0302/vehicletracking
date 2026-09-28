# Spec — Đăng ký tài khoản admin

## API contract

`POST /api/v1/auth/register-admin` (public route, CSRF required)

Request:

```json
{
  "username": "ops.admin",
  "password": "password"
}
```

- `username`: non-blank, tối đa 100 ký tự; normalize lowercase và khớp `[a-z0-9][a-z0-9._-]{2,99}`.
- `password`: non-blank, 8–100 ký tự; encode BCrypt trước khi lưu.

Response `201` là `UserAccountResponse` với role `ADMIN`, `driverId = null`; không chứa password/hash.

Errors: `400` validation/username, `403` CSRF, `409` username đã tồn tại.

## Security and data flow

Security matcher permit riêng `/api/v1/auth/register-admin`, nhưng CSRF filter vẫn áp dụng. Service kiểm tra username, encode password và insert account. Không nhận role từ request và không tự login.

## UI contract

`/login` hiển thị link “Đăng ký tài khoản quản trị”. `/register` có username, password và confirm password; hiển thị lỗi API, trạng thái đang gửi, success state và link về login.
