# Requirement — AWS EC2 production deployment

**Status:** Approved

## Mục tiêu

Đóng gói và chạy Vehicle Tracking trên một EC2 ARM64 bằng Docker Compose, phục vụ frontend và API qua cùng một public origin, giữ PostgreSQL và cổng backend trong mạng nội bộ.

## Phạm vi

- Image production cho Spring Boot Java 26 và React/Vite.
- PostgreSQL 17 có volume bền vững.
- Caddy phục vụ SPA, reverse proxy API và không buffer SSE.
- Cấu hình runtime qua `.env.production` không được commit.
- Hướng dẫn cài đặt, cập nhật, log và sao lưu trên Ubuntu 24.04 ARM64.

## Acceptance criteria

1. `docker compose config` đọc được production compose khi có đủ biến bắt buộc.
2. Frontend gọi `/api/v1` trên cùng origin; trình duyệt không gọi thẳng cổng `8080`.
3. Chỉ frontend publish cổng 80/443; PostgreSQL và backend không publish cổng host.
4. HERE API key và mật khẩu database chỉ nằm trong file runtime bị Git bỏ qua.
5. PostgreSQL dùng named volume và backend chỉ khởi động sau khi database healthy.
6. Cấu hình chạy được trên EC2 `linux/arm64`.

## Ngoài phạm vi

- CI/CD tự động.
- RDS, load balancer và multi-instance.
- Quản lý domain hoặc tài khoản DNS của người dùng.
- Bổ sung authentication cho ứng dụng.
