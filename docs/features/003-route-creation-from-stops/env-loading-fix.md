# Sửa lỗi Routing không nạp .env — 2026-09-14

## Survey và kế hoạch rút gọn

Người dùng yêu cầu sửa HTTP 503 `Routing service is currently disabled or unconfigured`. `HereRoutingProvider.calculate` trả lỗi này trước khi gọi HERE nếu disabled/key trống. `application.yaml` dùng HERE_ROUTING_ENABLED nhưng trước sửa chưa import `.env`. Kiểm tra riêng biến không bí mật xác nhận `.env` root đã bật HERE_ROUTING_ENABLED; không cần đọc/chép giá trị key.

Kế hoạch: dùng Spring Config Data sẵn có để nạp file local, giữ disabled mặc định và validation key; cách ly file local khỏi test; kiểm tra precedence và routing regression. Không thêm feature nghiệp vụ, dependency, schema hoặc sửa frontend/dist. Không sửa `.gitignore` đang modified của người dùng.

## Thay đổi

- `vehicletracking-backend/src/main/resources/application.yaml#spring.config.import`: optional imports theo thứ tự `../.env`, `./.env`, `./vehicletracking-backend/.env`. Các đường dẫn tính từ working directory tiến trình; hỗ trợ chạy từ root repository hoặc backend. File backend ghi đè biến trùng của file root. Không tìm kiếm thư mục đệ quy, không nạp frontend/.env.
- Import dạng `[.properties]`: mỗi dòng `KEY=value`, không bọc giá trị bằng dấu nháy shell, không dùng `export`. Environment tiến trình có độ ưu tiên cao hơn file. Đây là hỗ trợ Java Properties, không phải dotenv parser đầy đủ.
- `APP_ENV_IMPORT` cho phép override toàn bộ danh sách ở IDE/deployment nếu working directory khác hoặc không muốn auto-load local files. Để không đọc local env: `APP_ENV_IMPORT=optional:classpath:/no-local-env.properties`. Chạy từ root có thể đọc `../.env` nếu file đó tồn tại; deployment nên chỉ định danh sách file tuyệt đối chủ định hoặc tắt auto-load.
- `vehicletracking-backend/pom.xml` Surefire đặt APP_ENV_IMPORT về classpath optional không tồn tại, tránh nạp credential local khi chạy Maven tests. Chạy test bằng IDE ngoài Maven cũng cần đặt biến override này. Cờ HERE_ROUTING_ENABLED=false vẫn tắt Routing dù có key; không tự bật theo sự tồn tại của key.
- `route/config/RouteEnvironmentImportTest`: 6 test dùng thư mục tạm và key giả, ConfigData thật, không DB/HERE. Đọc template import từ YAML rồi remap vào temporary directory; không dùng đường dẫn .env thật trong lần chạy cuối.

Nguồn: [Spring Boot Externalized Configuration — import extension hints, ordering](https://docs.spring.io/spring-boot/reference/features/external-config.html), truy cập 2026-09-14. Các import sau ưu tiên hơn import trước; dùng extension hint cho file không có phần mở rộng thích hợp.

## Verification

JDK mặc định shell là 17; dùng JDK 26 có sẵn tại `/home/khainq/.sdkman/candidates/java/26.0.1-amzn` cho Maven, không đổi cấu hình JDK toàn máy.

Lệnh từ vehicletracking-backend:

```bash
JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn bash ./mvnw -Dtest=RouteEnvironmentImportTest,RouteConfigurationTest,HereRoutingProviderTest test
```

Kết quả cuối: exit 0, **36 tests, 0 failures/errors/skipped** (6 env + 3 config + 27 provider), BUILD SUCCESS. Các lần test đầu phát hiện assertion đường dẫn đòi file tồn tại và override spring.config.import không loại bỏ imports khai báo trong YAML; đã sửa assertion lexical và dùng placeholder APP_ENV_IMPORT. Không tuyên bố các lần trước đạt.

Các test xác nhận chạy từ root/backend, precedence backend/root/process environment, không nạp frontend env, thiếu file giữ disabled và bật nhưng thiếu key fail validation. Provider test dùng MockRestServiceServer; không chứng minh key thật/quota/quyền HERE.

Full Maven suite lần đầu trong sandbox: exit 1, báo 126 tests / 76 errors vì Mockito không khởi tạo agent và Testcontainers không tìm được Docker. Sau khi được cấp quyền chạy ngoài sandbox, chạy:

```bash
JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn bash ./mvnw test -Ddebug=false -Dlogging.level.root=WARN
```

Kết quả: exit 0, **150 tests, 0 failures/errors/skipped**, BUILD SUCCESS lúc 08:31:55 +07 ngày 2026-09-14. Bao gồm 2 OperationsHttpIntegrationTest, 10 OperationsIntegrationTest và các tests routing/station/fleet; PostgreSQL Testcontainers chạy thành công. HTTP/SSE test báo command-to-two-subscribers latency 673 ms. Có warnings connection pool/scheduler sau khi container test cũ đóng, không gây test failure; không refactor phần simulator ngoài phạm vi bugfix này. Không chạy extension browser006 nên không coi feature 006 đã nghiệm thu đầy đủ.

Frontend không đổi nên không chạy lại lint/build. `git diff --check` đạt tại lần kiểm tra sau sửa. Surefire reports dưới target là evidence của lần chạy cuối, không commit vì có metadata môi trường test.

## Sử dụng sau sửa

Khởi động lại backend bằng JDK 26 với working directory root repository hoặc vehicletracking-backend. Giữ HERE_ROUTING_ENABLED=true, HERE_API_KEY và HERE_ROUTING_BASE_URL=https://router.hereapi.com trong file root hoặc backend. Không cần sửa/copy key vào frontend. Nếu có biến trùng trong backend/.env hoặc environment của IDE, chúng có thể ghi đè file root.

Không tự restart server, gọi HERE bằng key thật, chạy migration trên database phát triển hoặc sửa secret trong lượt này. Nếu sau restart lỗi chuyển thành unauthorized/timeout thì cần kiểm tra quyền key/network riêng; sửa import không chứng minh provider live đã thành công. Tài liệu này nằm trong docs đang bị ignore; không force-add/commit.
