# Test Plan 025 — Ràng buộc vận hành chuyến

| AC | Mức test | Kịch bản | Kết quả mong đợi |
|---|---|---|---|
| AC-01 | Unit/service/controller | Start thiếu driver, driver inactive, xe inactive | `409`, không đổi status |
| AC-02 | Unit/service | Trước cửa sổ, trong cửa sổ, sau cửa sổ | Chỉ trong cửa sổ chuyển `IN_PROGRESS` |
| AC-03 | Unit/repository/integration | Xe/tài xế overlap, cùng endpoint, trip completed/cancelled | Overlap bị chặn; endpoint không overlap; terminal trip không khóa |
| AC-04 | Unit/service | Complete thiếu final visit, đủ final visit, sai attempt | Chỉ đủ final visit/attempt mới complete |
| AC-05 | Controller/service | Cancel thiếu reason, reason hợp lệ, cancel lặp | `400`/`409` đúng; reason lưu và không bị ghi đè |
| AC-06 | Frontend type/manual | Form driver, readiness, conflict, cancel textarea, cancelled detail | UI phản ánh đúng lỗi và trạng thái |
| AC-07 | Migration/quality | Flyway/JPA, Maven test, lint, tsc, build | Đạt hoặc ghi rõ giới hạn môi trường |
