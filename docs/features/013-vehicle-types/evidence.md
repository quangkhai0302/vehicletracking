# Evidence — Implemented, PostgreSQL verification pending

Ngày kiểm tra: 2026-09-15. Working tree chưa commit; giữ nguyên các thay đổi có sẵn, không đọc/in secret.

## Source

- AC1/AC4: `VehicleType`, `VehicleEntity.vehicleType`, `VehicleUpsertRequest`, `VehicleService` và migration `V10__add_vehicle_type.sql`.
- AC2: `VehicleResponse.vehicleType`, `TripSummaryResponse.vehicleType`; constructor/request thiếu loại mặc định `CAR`.
- AC3: `VehicleEditor`, `FleetWorkspace`, `TripEditor`, `useVehicleMarkers`, `SimulationFleetLayer`, `SimulationFleetList`, `vehiclePresentation.ts`.
- AC5: không sửa route provider/travel mode; ghi rõ giới hạn trong spec và comment migration.

## Kiểm tra

- Backend compile gồm test source: exit 0.
- Ba suite mục tiêu đạt 31/31: `VehicleServiceTest` 9, `FleetControllerTest` 8 và `TripServiceTest` 14; gồm mặc định CAR, tạo/sửa MOTORCYCLE, trip summary đúng loại và HTTP 400 với enum sai.
- Full Maven chạy 168 test: 162 đạt, 0 assertion failure; 6 lớp Testcontainers không khởi động vì sandbox không truy cập Docker. Vì vậy `FleetRepositoryIntegrationTest.persistsMotorcycleTypeAndExposesItThroughTripSummary` và Flyway V1–V10 chưa chạy trong lượt này.
- Frontend Node 24.16.0: lint exit 0 với 2 warning cũ trong `useFleetWorkspace`; TypeScript `--noEmit` exit 0; Vite production build exit 0.
- `git diff --check`: đạt.

## Giới hạn

Chưa chạy browser smoke cho hai glyph và chưa áp migration lên database phát triển. Sau khi Docker/PostgreSQL khả dụng, chạy full Maven để chuyển trạng thái sang Verified. Khi backend dev khởi động, Flyway sẽ áp dụng V10; nên sao lưu database theo quy trình vận hành hiện có trước khi nâng schema.
