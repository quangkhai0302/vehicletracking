# Test plan — AWS EC2 production deployment

| AC | Kiểm tra | Kết quả mong đợi |
|---|---|---|
| 1 | `docker compose --env-file .env.production -f compose.production.yaml config --quiet` | Exit code 0 |
| 2 | Build frontend với `VITE_API_BASE_URL=/`; kiểm tra asset | URL API là `/api/v1`, không chứa `localhost:8080` |
| 3 | Đọc config compose đã render | Chỉ service frontend có `ports` |
| 4 | `git status` và `git check-ignore .env.production` | File secret không được track |
| 5 | `docker compose ps`; restart stack | Database healthy, dữ liệu còn trong volume |
| 6 | Build/start trên EC2 `aarch64`; `curl` frontend và snapshot API | Image chạy được trên ARM64; HTTP 200 |
| SSE | Mở UI hoặc `curl -N /api/v1/telemetry/stream` | Event tiếp tục được đẩy qua Caddy |

Kiểm tra runtime và SSE chỉ hoàn tất sau khi stack được chạy trên EC2.
