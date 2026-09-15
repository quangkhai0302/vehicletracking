# 011 — Mô phỏng nhiều xe, chọn trực tiếp trên bản đồ

Người dùng yêu cầu mở mô phỏng thấy các xe đứng ở trạm đầu, bấm từng xe để chạy/cấu hình, nhiều xe chạy cùng lúc. Dùng xe/chuyến đã tạo; không tự tạo xe, chuyến, telemetry hoặc check-in khi mở chế độ. `docs/workflow.md` không có sau tìm kiếm cả hidden; dùng hồ sơ rút gọn theo yêu cầu triển khai trực tiếp trong cuộc trao đổi.

## Source evidence trước triển khai

- Backend `simulation/service/SimulationScheduler#tick` lặp `SimulationService#activeTripIds`; `play/pause/speed/stop/reset` nhận tripId riêng. `OperationsSnapshotService#snapshot` chứa danh sách toàn bộ runs/positions/trips, không giới hạn một xe.
- `V4__create_vehicles_and_trips.sql#uq_trips_running_vehicle` và `TripService#start` chỉ cấm hai chuyến IN_PROGRESS của cùng vehicle. `V5__create_telemetry_and_simulation.sql` có run unique theo tripId, không singleton.
- Frontend `useSimulator` chỉ giữ selection một trip, lệnh theo tripId; đổi selection không gửi pause. `useVehicleMarkers` chỉ vẽ vị trí có telemetry nên xe SCHEDULED chưa chạy chưa hiện.
- `services/fleet.ts#fetchTrip`, `TripDetail.stops` có tọa độ và tên trạm đầu. Dùng vị trí chờ mô phỏng từ snapshot trạm, gắn nhãn rõ, không giả làm GPS.

## Nghiệm thu

1. Mở chế độ Mô phỏng hiển thị xe có chuyến chờ tại trạm đầu trước khi play, tốc độ chờ 0, không POST khi chỉ mở/chọn.
2. Nhiều xe cùng trạm chọn được từng xe qua danh sách trong popup, không dịch tọa độ giả để tránh trùng. Danh sách đội mô phỏng có trạng thái/chọn xe và nút vừa khung tất cả xe.
3. Bấm xe chờ hoặc xe đang mô phỏng mở đúng chuyến/điều khiển và tuyến đã gán. Có play/pause/continue/1×/5×/10×/stop/reset riêng; cấu hình xe/tuyến/giờ xuất phát vẫn dùng quản lý chuyến đã có. Không thêm vận tốc km/h tùy chỉnh.
4. Xe A và B (khác vehicleId) chạy đồng thời; pause/đổi multiplier A không làm đổi B; đổi selection không gửi lệnh tới xe cũ. Status cập nhật qua SSE/reload. Một xe có nhiều chuyến chờ chỉ vẽ chuyến sớm nhất; xe có chuyến IN_PROGRESS không vẽ bản chờ khác. GPS hiện tại không bị chuyển thành xe giả lập.
5. Khi xe bắt đầu, bỏ preview và dùng telemetry/frame hiện hành; không còn marker lịch sử và preview trùng cho cùng xe. Mở preview không ghi nhận check-in. Rời mode gỡ preview/listeners nhưng không dừng backend simulations.
6. Loading/lỗi tải tọa độ, retry, empty hướng dẫn tạo xe/chuyến; desktop/mobile/keyboard có tương tác. Giữ hành vi tracking/tooltip 010, backend/schema/.env không đổi nếu API hiện có đủ.
7. Bổ sung sửa lỗi ngày 2026-09-15: trong Mô phỏng phải hiển thị đồng thời hình học các tuyến của đội xe, kể cả chưa chọn xe hoặc có hai xe đang chạy trên hai tuyến khác nhau. Tuyến xe chọn màu cyan, các tuyến còn lại màu nhận diện theo vehicle; đổi selection không xóa tuyến khác. Click tuyến chọn xe, đoạn đi chung mở danh sách xe. Enter/Space trên tuyến hoặc danh sách cũng chọn được. Xem tất cả/vừa khung bao toàn tuyến và vị trí xe; tắt lớp tuyến/rời mode gỡ layer. Lỗi GET/polyline một chuyến phải báo lỗi, cho retry và giữ các tuyến còn lại. Chỉ hiển thị trạm đánh số của chuyến đang chọn; trong mode mô phỏng tuyến là đối tượng chọn xe, thẻ vận tốc/ETA vẫn theo xe, tooltip phân tích giao thông tuyến 010 giữ ở tracking/editor.

Giới hạn: scheduler hiện cập nhật các chuyến tuần tự; chưa chứng minh capacity tải lớn. Preview là điểm xuất phát theo lịch, không phải GPS đo. Nghiệm thu nhiều xe bằng API/SSE fixture tách biệt; không tự tạo dữ liệu trong database người dùng để test.
