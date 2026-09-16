# Spec — AWS EC2 production deployment

## Kiến trúc runtime

```text
Internet :80/:443
       |
       v
Caddy + React SPA ---- /api/* ----> Spring Boot :8080 ----> PostgreSQL :5432
  (published)                        (internal)              (internal)
```

- `frontend` publish 80/TCP, 443/TCP và 443/UDP.
- `backend` và `postgres` chỉ tham gia network `application`.
- Vite được build với `VITE_API_BASE_URL=/`.
- Caddy fallback mọi route SPA về `index.html` và reverse proxy `/api/*` đến backend.
- PostgreSQL lưu dữ liệu trong `postgres_data`.
- Caddy lưu certificate/config trong `caddy_data` và `caddy_config`.
- `.env.production` chứa secret thật, bị Git bỏ qua; `.env.production.example` chỉ chứa placeholder.

## Chế độ địa chỉ

- Kiểm thử bằng IP: `SITE_ADDRESS=:80`.
- Có domain: `SITE_ADDRESS=tracking.example.com`; Caddy tự quản lý HTTPS sau khi DNS và security group cho phép 80/443.

## Tài nguyên

- JVM mặc định: `-Xms128m -Xmx768m`.
- EC2 cần tối thiểu 2 GB swap trong lúc build.
