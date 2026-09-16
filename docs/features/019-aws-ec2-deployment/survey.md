# Survey — AWS EC2 production deployment

## Trạng thái trước thay đổi

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Compose hiện tại chỉ chạy PostgreSQL và pgAdmin cho local | `compose.yaml`, services `postgres`, `pgadmin` | Cần compose production riêng để không ảnh hưởng local |
| Backend dùng Java 26, Maven và tạo executable Spring Boot jar | `vehicletracking-backend/pom.xml`, `java.version`, `spring-boot-maven-plugin` | Builder/runtime image phải hỗ trợ Java 26 ARM64 |
| Backend đọc database từ biến môi trường | `vehicletracking-backend/src/main/resources/application.yaml`, `spring.datasource` | Container có thể kết nối qua hostname `postgres` |
| Frontend lấy API origin từ `VITE_API_BASE_URL` | `vehicletracking-frontend/src/services/operations.ts` và các service cùng mẫu | Build production có thể dùng `/` để gọi cùng origin |
| Realtime dùng SSE tại `/api/v1/telemetry/stream` | `TelemetryController.stream`, `subscribeOperations` | Reverse proxy phải tránh buffer stream |
| CORS không chấp nhận wildcard | `CorsProperties.isValidOrigins` | Cấu hình production phải cung cấp URL hợp lệ |
| Repository chưa có Dockerfile hoặc reverse proxy | Khảo sát `rg --files` trước implementation | Cần bổ sung image và Caddyfile |

## Rủi ro

- 2 GB RAM có thể thiếu trong lần build đầu; EC2 cần swap.
- Ứng dụng hiện chưa có authentication, vì vậy public deployment chưa phù hợp dữ liệu thật.
- Public IPv4 có thể đổi sau thao tác stop/start nếu chưa dùng Elastic IP hoặc domain cập nhật theo IP mới.
