# Evidence — Phân quyền người dùng và cổng tài xế

## File chính

- Schema: `vehicletracking-backend/src/main/resources/db/migration/V18__create_user_accounts.sql`.
- Auth: `auth/config/SecurityConfig.java`, `auth/config/PasswordEncoderConfig.java`, `auth/controller/AuthController.java`, `auth/controller/UserAccountController.java`, `auth/service/UserAccountService.java`, `auth/service/UserAccountBootstrap.java`.
- Test/legacy opt-out: `auth/config/SecurityDisabledConfig.java`, `src/test/resources/application.properties` explicitly disables the chain only for existing isolated tests; production default remains enabled.
- Driver scope: `driverportal/controller/DriverPortalController.java`, `driverportal/service/DriverPortalService.java`, `trip/repository/TripRepository.java`, `schedule/repository/TripScheduleRepository.java`.
- Frontend auth/role: `src/auth/AuthContext.tsx`, `src/auth/RouteGuards.tsx`, `src/services/auth.ts`, `src/App.tsx`.
- Frontend UI: `src/pages/LoginPage.tsx`, `src/pages/UserManagementPage.tsx`, `src/pages/DriverPortalPage.tsx`, tương ứng các file CSS và services/types.
- Test: `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/driverportal/DriverPortalServiceTest.java`.

## Verification

- `cd vehicletracking-frontend && PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint && ./node_modules/.bin/tsc --noEmit && npm run build` — PASS; lint còn 6 warning không chặn.
- `cd vehicletracking-backend && env JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn PATH=... ./mvnw -Dtest='!**/*IntegrationTest' test` — PASS, 254 tests; auth/CSRF/session tests đều xanh.
- Full backend test còn 6 integration test lỗi do Docker daemon không khả dụng; không có assertion failure.

Không đọc/in giá trị secret. Bootstrap admin chỉ đọc password từ `AUTH_BOOTSTRAP_ADMIN_PASSWORD` lúc runtime.
