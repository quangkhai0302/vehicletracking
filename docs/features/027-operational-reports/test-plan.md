# Test Plan — Báo cáo và thống kê vận hành

| AC | Mức test | Kịch bản | Dữ liệu/fixture | Kết quả mong đợi |
|---|---|---|---|---|
| 1 | Controller + frontend manual | Gửi from/to và vehicleId/driverId; đổi bộ lọc UI | Query params hợp lệ | API nhận đủ filter; UI hiển thị lại aggregate |
| 2 | Service + controller | Aggregate trip, distance, runtime, on-time, late, off-route, overspeed | 3 trip, notification OFF_ROUTE, GPS samples | Các field response khớp contract |
| 3 | Service | Không có completed trip; completed đúng/trễ | Trip IN_PROGRESS/SCHEDULED | onTimeRatePercent = 0; late chỉ tính quy tắc đã nêu |
| 4 | Service | GPS 70, 90, 95, 70, 81 với ngưỡng 80 | Cùng và khác attempt | Hai episode quá tốc độ, không đếm từng sample |
| 5 | Repository/service | Notification khác type hoặc ngoài kỳ | REROUTE/OFF_ROUTE và boundary UTC | Chỉ OFF_ROUTE trong khoảng được đếm |
| 6 | Frontend lint/type/manual | API loading, lỗi, empty; mở `/reports` | Abort/error/empty response | Không crash; retry và empty state hiển thị; menu không Roadmap |
| 7 | Controller/service | from sau to, ID 0/âm | Query invalid | HTTP 400 Problem Detail |
| Verify | Tooling | Lint/type/backend test/build | Môi trường repository | Ghi exit code và giới hạn môi trường thực tế |
