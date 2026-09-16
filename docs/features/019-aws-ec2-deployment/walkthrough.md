# Walkthrough — Deploy lên AWS EC2

## 1. Chuẩn bị máy chủ

Sau khi SSH vào Ubuntu 24.04 ARM64 và cài Docker, tạo 2 GB swap:

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

## 2. Lấy source

```bash
git clone https://github.com/quangkhai0302/vehicletracking.git
cd vehicletracking
```

Production files phải có trong revision được clone:

```bash
ls compose.production.yaml vehicletracking-backend/Dockerfile vehicletracking-frontend/Dockerfile
```

## 3. Tạo cấu hình bí mật

```bash
cp .env.production.example .env.production
openssl rand -hex 32
nano .env.production
```

Thay `POSTGRES_PASSWORD`, `HERE_API_KEY`. Khi dùng IP giữ `SITE_ADDRESS=:80`; khi có domain đặt domain vào `SITE_ADDRESS` và đặt origin HTTPS tương ứng vào `APP_CORS_ALLOWED_ORIGINS`.

Không in nội dung `.env.production` ra log và không commit file này.

## 4. Build và khởi động

```bash
docker compose --env-file .env.production -f compose.production.yaml build
docker compose --env-file .env.production -f compose.production.yaml up -d
docker compose --env-file .env.production -f compose.production.yaml ps
```

## 5. Kiểm tra

Trên EC2:

```bash
curl -I http://localhost
curl http://localhost/api/v1/telemetry/snapshot
docker compose --env-file .env.production -f compose.production.yaml logs --tail=100 backend
```

Từ máy cá nhân mở `http://PUBLIC_IP`.

## 6. Cập nhật phiên bản

```bash
git pull --ff-only
docker compose --env-file .env.production -f compose.production.yaml build
docker compose --env-file .env.production -f compose.production.yaml up -d
docker image prune -f
```

## 7. Sao lưu PostgreSQL

```bash
mkdir -p backups
docker compose --env-file .env.production -f compose.production.yaml exec -T postgres \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "backups/vehicletracking-$(date +%F-%H%M).dump"
```

Không dùng `docker compose down -v` trên production vì tùy chọn `-v` xóa volume dữ liệu.
