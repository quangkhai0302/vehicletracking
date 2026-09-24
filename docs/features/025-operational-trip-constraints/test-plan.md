# Test Plan 025 — Ràng buộc vận hành chuyến

| AC | Mức test | Kịch bản | Kết quả mong đợi |
|---|---|---|---|
| AC-01 | Unit/service/controller | Start thiếu driver, driver inactive, xe inactive | `409`, không đổi status |
| AC-02 (điều chỉnh 2026-09-24) | Unit/service | Giờ dự kiến trước và sau thời điểm hiện tại | Đều chuyển `IN_PROGRESS` khi các điều kiện khác hợp lệ |
| AC-03 | Unit/repository/integration | Hai chuyến cùng xe overlap; một tài xế được gán hai chuyến overlap trên hai xe | Xe overlap bị chặn; cả hai phân công tài xế được lưu |
| AC-03a | Unit/integration qua simulator | Bắt đầu mô phỏng chuyến thứ nhất rồi bắt đầu chuyến thứ hai của cùng tài xế | Chuyến đầu `IN_PROGRESS`; chuyến thứ hai trả `409`, vẫn `SCHEDULED` và không tạo simulation run |
| AC-04 | Unit/service | Complete thiếu final visit, đủ final visit, sai attempt | Chỉ đủ final visit/attempt mới complete |
| AC-05 | Controller/service | Cancel thiếu reason, reason hợp lệ, cancel lặp | `400`/`409` đúng; reason lưu và không bị ghi đè |
| AC-06 | Frontend type/manual | Form driver, readiness, conflict, cancel textarea, cancelled detail | UI phản ánh đúng lỗi và trạng thái |
| AC-07 | Migration/quality | Flyway/JPA, Maven test, lint, tsc, build | Đạt hoặc ghi rõ giới hạn môi trường |
