# Requirement — Đăng ký tài khoản admin

## Mục tiêu

Cho phép người vận hành tạo tài khoản `ADMIN` trực tiếp từ trang đăng nhập với luồng đơn giản, dễ dùng.

## Phạm vi

### In scope

- Public endpoint và trang `/register` để tạo tài khoản admin.
- Chỉ tạo role `ADMIN`; client không được chọn role hoặc gán tài xế.
- Chuẩn hóa username, kiểm tra trùng và mật khẩu tối thiểu 12 ký tự.
- CSRF, validation, trạng thái thành công/lỗi và liên kết về trang đăng nhập.

### Out of scope

- Đăng ký tài khoản driver công khai.
- Email verification, reset mật khẩu, SSO/MFA, rate limiting theo IP.

## Acceptance criteria

1. `POST /api/v1/auth/register-admin` tạo đúng một account `ADMIN`, trả `201`; không có trường role trong request.
2. Username trùng trả conflict; username được normalize giống luồng cấp account hiện tại.
3. Mật khẩu được encode trước khi lưu và không trả hash trong response.
4. Endpoint public vẫn bắt buộc CSRF; request không có CSRF bị `403`.
5. UI có loading, lỗi, xác nhận mật khẩu, success state và link đăng nhập.

## Non-functional requirements

- Không tự động đăng nhập sau đăng ký để tránh mở rộng session flow ngoài yêu cầu.