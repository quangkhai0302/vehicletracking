# Evidence — Đăng ký tài khoản admin

## Code

- Config: `auth/config/AuthProperties.java`, `application.yaml`, `.env.production.example`.
- Contract/service: `auth/dto/AdminRegistrationRequest.java`, `auth/service/UserAccountService.java` (`registerAdmin`).
- HTTP/security: `auth/controller/AuthController.java`, `auth/config/SecurityConfig.java`.
- Frontend: `src/pages/AdminRegistrationPage.tsx`, `src/pages/LoginPage.tsx`, `src/services/auth.ts`, `src/types/auth.ts`, `src/App.tsx`.
- Tests: `auth/UserAccountServiceTest.java`, `auth/controller/AuthSecurityControllerTest.java`.

## Verification

- `cd vehicletracking-backend && env JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn PATH=/home/khainq/.sdkman/candidates/java/26.0.1-amzn/bin:$PATH ./mvnw -Dtest=UserAccountServiceTest,AuthSecurityControllerTest,UserAccountBootstrapTest test` — PASS, 9 tests.
- `cd vehicletracking-backend && env JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn PATH=/home/khainq/.sdkman/candidates/java/26.0.1-amzn/bin:$PATH ./mvnw -Dtest='!**/*IntegrationTest' test` — PASS, 258 tests; integration suite không chạy trong lệnh này.
- `cd vehicletracking-frontend && PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH ./node_modules/.bin/tsc --noEmit` — PASS.
- `cd vehicletracking-frontend && env PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint` — PASS với 6 warning React lint có sẵn ngoài feature.
- `cd vehicletracking-frontend && env PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run build` — PASS.
- `git diff --check` — PASS.

Không có secret đăng ký mới; mật khẩu vẫn chỉ được gửi qua HTTPS/session flow và lưu dưới dạng hash.

## Cập nhật chính sách mật khẩu — 2026-09-28

- `AdminRegistrationRequest` và form `/register` dùng cùng giới hạn 8–100 ký tự.
- Test MockMvc xác minh mật khẩu đúng 8 ký tự được chấp nhận và 7 ký tự bị trả `400`.
- Tài khoản bootstrap từ cấu hình giữ riêng mức tối thiểu 12 ký tự; thay đổi này chỉ áp dụng cho luồng đăng ký/reset người dùng.

```text
Frontend: lint, typecheck, 110 unit tests, 5 motion tests và build — PASS
Backend Java 26: 311 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS
git diff --check — PASS
```
