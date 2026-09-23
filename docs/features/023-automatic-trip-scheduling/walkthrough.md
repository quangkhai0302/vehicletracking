# Walkthrough 023 — Lập lịch chuyến tự động

## Luồng người dùng

1. Mở **Lịch chạy tự động** từ sidebar.
2. Chọn **Tạo lịch chạy**, chọn tuyến, xe, tài xế, múi giờ, giờ khởi hành và ngày hiệu lực.
3. Chọn **Một lần** hoặc các thứ trong tuần. Form kiểm tra các trường bắt buộc trước khi gửi.
4. Card lịch hiển thị trạng thái, quy tắc lặp, phân công, lần chạy kế tiếp và kết quả scheduler gần nhất.
5. Dùng nút sửa hoặc bật/tắt; thao tác bật/tắt có dialog xác nhận. Trip đã sinh từ lịch được giữ lại và không thể hard-delete.
6. Mở **Xem chuyến đi** để xem các trip cụ thể được tạo.

## Luồng dữ liệu

`ScheduleManagementPage` gọi `services/schedules.ts` và các catalog route/vehicle/driver. Backend `TripScheduleController` chuyển request vào `TripScheduleService`, lưu `trip_schedules` bằng Flyway V15. `TripSchedulePollingScheduler` chạy mỗi 60 giây, resolve local date/time bằng timezone IANA, rồi gọi `TripService.createFromSchedule` để tạo snapshot trip nội bộ trong cửa sổ 7 ngày.

`trips.schedule_id + schedule_occurrence_at` cùng unique index là provenance/idempotency guard. Xe, tài xế và tuyến được kiểm tra active; khoảng thời gian dự kiến chồng lấn trên cùng xe/tài xế được ghi nhận là conflict. `lastRunAt` là thời điểm scheduler xử lý occurrence gần nhất.

## Cách kiểm tra

- Dùng JDK 26 và PostgreSQL/Testcontainers để chạy `cd vehicletracking-backend && ./mvnw test`.
- Dùng Node >=22.12 để chạy `cd vehicletracking-frontend && npm run lint && ./node_modules/.bin/tsc --noEmit && npm run build`.
- Trên UI, kiểm tra tạo ONCE/WEEKLY, lọc trạng thái/tuyến, sửa, tạm dừng/bật lại, lỗi API và responsive drawer.

## Hạn chế

Feature hiện chưa có holiday/exception, monthly recurrence, auto-assign pool, auth/RBAC hoặc báo cáo. Overlap theo thời lượng tuyến đã được chặn ở service; race hai transaction và station deactivation vẫn cần PostgreSQL integration test.
