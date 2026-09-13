# Plan 005 — được yêu cầu triển khai trực tiếp

1. Thêm migration V4; packages vehicle/trip, JPA entities/repositories, DTO/controller/service theo pattern station. Vehicle lock tuần tự hóa create/start/deactivate; DB index bảo vệ invariant.
2. Test validation/normalization/lifecycle/snapshot và request contracts. Integration dùng Testcontainers nếu Docker khả dụng; không đổi schema database người dùng để thay test.
3. Tạo services/types và FleetWorkspace riêng; VehicleEditor/TripEditor/TripDetail trong drawer Map-First. Chỉ nối route của trip vào map ở mode theo dõi; route editor 004 giữ state riêng.
4. Chạy Maven, frontend lint/tsc/build, browser fixture; sửa các lỗi trong phạm vi, ghi kết quả thực tế và cập nhật progress/handoff.

Trạng thái 2026-09-13: cả bốn bước đã hoàn thành trong working tree. Xem [verification.md](verification.md) cho source evidence, kết quả 118 backend tests, lint/tsc/build và browser fixtures; 006 chưa triển khai.
