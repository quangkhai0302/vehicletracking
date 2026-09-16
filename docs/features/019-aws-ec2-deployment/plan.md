# Plan — AWS EC2 production deployment

**Status:** Implementing (đã được người dùng yêu cầu triển khai trực tiếp)

1. Tạo multi-stage Dockerfile cho backend và frontend.
2. Dùng Caddy phục vụ SPA, proxy `/api/*` và stream SSE.
3. Tạo `compose.production.yaml` với mạng nội bộ, healthcheck và named volumes.
4. Tạo mẫu biến môi trường không chứa secret; thêm `.env.production` vào `.gitignore`.
5. Xác minh compose, build frontend/backend và kiểm tra không lộ secret.
6. Khởi chạy trên EC2 ARM64, kiểm tra HTTP/API/SSE và ghi evidence runtime.
