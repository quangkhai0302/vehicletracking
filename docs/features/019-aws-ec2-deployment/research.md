# Research — AWS EC2 production deployment

**Ngày khảo sát:** 2026-09-17

- Docker Engine hỗ trợ Ubuntu 24.04 và Docker Compose plugin.
- EC2 `t4g.small` dùng ARM64, vì vậy mọi base image phải có manifest `linux/arm64`.
- Caddy có thể phục vụ file tĩnh, reverse proxy và tự cấp HTTPS khi `SITE_ADDRESS` là domain trỏ đúng về EC2.
- SSE cần proxy chuyển dữ liệu ngay; cấu hình dùng `flush_interval -1`.

Nguồn tham khảo:

- https://docs.docker.com/engine/install/ubuntu/
- https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EC2_GetStarted.html
- https://caddyserver.com/docs/automatic-https
- https://caddyserver.com/docs/caddyfile/directives/reverse_proxy
