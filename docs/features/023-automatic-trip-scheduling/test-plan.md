# Test Plan 023 — Lập lịch chuyến tự động

| AC | Mức test | Kịch bản | Kết quả mong đợi |
|---|---|---|---|
| AC-01 | Controller/service | Tạo ONCE/WEEKLY hợp lệ và các payload thiếu date/mask/timezone | 201 với cấu hình chuẩn hóa; lỗi 400 rõ ràng |
| AC-02 | API/frontend | Load danh sách, next run, last run, filter và empty state | UI phản ánh đúng response, không số liệu giả |
| AC-03 | Service/controller | Update, enable, disable lịch | Cấu hình đổi; disabled không sinh mới |
| AC-04 | Integration | Scheduler sinh trip từ lịch với route/xe/tài xế active | Trip `SCHEDULED`, provenance và snapshot đúng |
| AC-05 | PostgreSQL integration | Hai lần xử lý cùng occurrence, xóa trip tự sinh và hai transaction cạnh tranh | Tối đa một trip nhờ unique index + provenance không cho hard-delete |
| AC-06 | Service/integration | Inactive resource, trùng giờ xe/tài xế, conflict, lỗi timezone | Lưu last-run failure; scheduler tiếp tục lịch khác |
| AC-07 | Frontend/manual | Loading/error/retry, form validation, confirm toggle, mobile | Trạng thái đầy đủ và không overflow |
| AC-08 | CI/local | Maven test, lint, tsc, build, diff check, Flyway validate | Lệnh phù hợp đạt hoặc giới hạn môi trường được ghi rõ |
