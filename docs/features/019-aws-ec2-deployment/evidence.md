# Evidence — AWS EC2 production deployment

## Trạng thái hiện tại

- EC2 Ubuntu 24.04 `aarch64` tại Singapore đã chạy.
- Docker Engine và Docker Compose đã được người dùng kiểm tra trên EC2.
- Cấu hình source đang ở giai đoạn kiểm tra local; runtime stack trên EC2 chưa được khởi động.

## Kiểm tra đã chạy

| Lệnh | Kết quả |
|---|---|
| `docker compose --env-file .env.production -f compose.production.yaml config --quiet` với file mẫu tạm thời | Exit code 0 |
| `npm.cmd run lint` | Exit code 0; còn 2 warning có sẵn tại `useFleetWorkspace.ts:93,101` |
| `tsc --noEmit` | Exit code 0 |
| `VITE_API_BASE_URL=/ npm run build` | Exit code 0; Vite build thành công, có cảnh báo chunk 513.99 kB |
| `mvn.cmd -DskipTests package` | Exit code 0; Spring Boot jar được tạo |
| `docker compose ... build` | Exit code 0; build thành công cả backend và frontend |
| `caddy validate --config /etc/caddy/Caddyfile` trong image frontend | `Valid configuration` |
| Tìm `localhost:8080` trong frontend production bundle | Không có kết quả |

Image local được build trên `linux/amd64`. Các base image đều cung cấp ARM64; bước build native và smoke test trên EC2 `aarch64` vẫn đang chờ thực hiện.

## File triển khai

- `compose.production.yaml`
- `.env.production.example`
- `vehicletracking-backend/Dockerfile`
- `vehicletracking-backend/.dockerignore`
- `vehicletracking-frontend/Dockerfile`
- `vehicletracking-frontend/.dockerignore`
- `vehicletracking-frontend/Caddyfile`

Evidence runtime sẽ được bổ sung sau khi build và smoke test trên EC2.
