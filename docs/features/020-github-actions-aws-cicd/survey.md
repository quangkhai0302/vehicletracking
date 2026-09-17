# Survey — GitHub Actions AWS CI/CD

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Repository chưa có GitHub Actions workflow | Không có `.github/workflows` trước Feature 020 | Cần tạo pipeline mới |
| Frontend yêu cầu Node >=22.12 và có lockfile | `vehicletracking-frontend/package.json`, `package-lock.json` | CI dùng Node 24 và `npm ci` |
| Backend dùng Java 26/Maven, integration test dùng Testcontainers PostgreSQL 17 | `vehicletracking-backend/pom.xml`; các test repository/simulation | GitHub Ubuntu runner cần Docker và Java 26 |
| Production dùng Docker Compose và `.env.production` trên EC2 | `compose.production.yaml`, Feature 019 | CD chỉ cập nhật source/build; không chuyển secret |
| Public API có snapshot để smoke test | `TelemetryController.snapshot` | Script deploy có thể xác minh qua Caddy |
| EC2 source hiện tại ở `/home/ubuntu/vehicletracking` | Luồng deploy Feature 019 | SSM command dùng đúng checkout hiện có |

Ba file UI đang có thay đổi cục bộ ngoài phạm vi Feature 020 và không được đưa vào commit CI/CD.
