# Evidence — Báo cáo và thống kê vận hành

## File chính

- Backend: `reporting/controller/OperationalReportController.java`, `reporting/service/OperationalReportService.java`, `reporting/dto/OperationalReportResponse.java`.
- Backend integration: `trip/repository/TripRepository.java`, `telemetry/repository/TelemetryRepository.java`, `reroute/repository/TripNotificationRepository.java`, `config/ReportingProperties.java`, `application.yaml`.
- Frontend: `src/pages/ReportsPage.tsx`, `src/pages/reports.css`, `src/services/reports.ts`, `src/types/reports.ts`, `src/App.tsx`, `src/app/routeConfig.ts`.
- Tests: `reporting/OperationalReportServiceTest.java`, `reporting/OperationalReportControllerTest.java`.

## Verification

- `cd vehicletracking-frontend && PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint && ./node_modules/.bin/tsc --noEmit && npm run build` — PASS; lint còn 6 warning không chặn.
- `cd vehicletracking-backend && env JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn PATH=... ./mvnw -Dtest='!**/*IntegrationTest' test` — PASS, 254 tests.
- Full backend test chạy được đến test phase; 6 lớp integration lỗi do Docker daemon không khả dụng, không có assertion failure.

## Mapping

- AC1/AC2: endpoint/controller + `ReportsPage` filters/KPI.
- AC3/AC4/AC5: `OperationalReportService` và unit test; off-route query giới hạn trực tiếp bởi `tripIds` trong kỳ; deadline dùng offset lịch gốc, không bị live ETA làm trễ chuẩn.
- AC6: `ReportsPage` state branches, `reports.css`, routeConfig.
- AC7: `OperationalReportService.validate` và controller contract test scope.

Không có migration mới và không đọc/in secret.
