# Requirement: Nâng cấp Web Theo dõi Đội xe Realtime & Cải thiện UX Form Quản lý Trạm

## 1. Bối cảnh và Vấn đề
- Giao diện hiện tại của `vehicletracking-frontend` ở màn hình "Theo dõi xe" (`workspace === 'tracking'`) chưa đáp ứng yêu cầu một hệ thống giám sát điều hành vận tải: chỉ hiển thị bản đồ trống với một panel tĩnh báo "0 xe", không có phương tiện di chuyển trên bản đồ, không có lộ trình tuyến đường, không có bảng điều khiển mô phỏng telemetry hay chi tiết xe.
- Trải nghiệm thêm/sửa/xoá trạm (`workspace === 'stations'`) còn nhiều bất cập:
  - Việc chọn vị trí trạm trên bản đồ thiếu hướng dẫn trực quan và thiếu nút tiện ích (ví dụ: lấy tâm bản đồ hiện tại).
  - Biểu mẫu (form) thêm/sửa trạm thiếu validation thời gian thực, thiếu bộ chọn bán kính trực quan (slider + preset khoảng cách) khiến người dùng khó ước lượng phạm vi check-in.
  - Phân loại trạm trên danh sách (Đầu/Cuối/Dừng) đang dùng logic suy đoán chuỗi tên trạm không chính xác.
  - Thiếu cảnh báo bảo vệ dữ liệu khi đóng biểu mẫu đang nhập dở (dirty form).
  - Hộp thoại xác nhận xoá/ngừng sử dụng trạm còn đơn điệu, chưa cung cấp đủ ngữ cảnh.

## 2. Mục tiêu Nghiệp vụ & Người dùng
1. **Theo dõi xe realtime**: Cung cấp giao diện trung tâm điều hành đội xe (Fleet Operations Control Center) chuẩn mực theo `docs/design.md`: danh sách xe động, bộ lọc trạng thái, tìm kiếm xe, vehicle marker xoay theo hướng di chuyển (`heading`), polyline tuyến đường, follow vehicle mode và drawer xem thông tin chi tiết xe.
2. **Bộ mô phỏng Telemetry (Simulator)**: Xây dựng simulator giả lập luồng telemetry của các xe chạy trên tuyến thực tế tại TP.HCM, có điều khiển Play/Pause/Reset và hệ số tốc độ (1x, 2x, 5x, 10x). Tách bạch rõ là nguồn mô phỏng phục vụ vận hành/demo.
3. **Trải nghiệm Quản lý trạm hoàn thiện**:
  - Cơ chế chọn tọa độ trên bản đồ mượt mà, hỗ trợ lấy tọa độ tâm bản đồ và kéo thả marker với geofence preview cập nhật tức thì.
  - Form thêm/sửa trạm có validation trực quan, hỗ trợ chọn bán kính nhanh qua preset (30m, 50m, 80m, 100m, 200m) và slider trực quan.
  - Bảo vệ dữ liệu đang nhập dở (dirty form confirmation).
  - Danh sách trạm có bộ lọc trạng thái thực tế, sắp xếp và tìm kiếm thông minh.
  - Hộp thoại xác nhận thao tác phá hủy (ngừng sử dụng trạm) rõ ràng, an toàn.

## 3. Phạm vi (Scope)
- **In Scope (Frontend)**:
  - Cải tiến màn hình "Theo dõi xe": `TrackingPanel`, `VehicleDrawer`, `VehicleMarker`, `RoutePolyline`, bộ điều khiển `SimulatorControls`, dịch vụ `telemetrySimulator.ts`.
  - Cải tiến màn hình "Quản lý trạm": Tối ưu `StationPanel`, `StationDrawer`, `MapComponent` (picking location, geofence circle preview, dirty state check).
  - Tối ưu `App.tsx` (topbar status, telemetry indicator) và `index.css` (bổ sung styling vehicle tracking, slider, presets).
- **Out of Scope**:
  - Không thay đổi backend Spring Boot hoặc database schema của PostgreSQL/Flyway (giữ nguyên API station hiện tại).
  - Không tích hợp MQTT/WebSocket backend thật trong lượt này (dùng mock simulator có dán nhãn rõ ràng).

## 4. Functional Requirements
- **FR-01 (Fleet List & Filter)**: Hiển thị danh sách xe với các thông số: biển số, model, tài xế, tốc độ hiện tại, trạm tiếp theo, ETA. Hỗ trợ lọc theo trạng thái (Tất cả, Đang chạy, Trễ giờ, Dừng đỗ) và tìm kiếm theo biển số/tuyến.
- **FR-02 (Vehicle Map Rendering)**: Hiển thị marker xe trên bản đồ với góc xoay theo hướng di chuyển, màu sắc theo trạng thái vận hành; vẽ tuyến đường polyline của xe được chọn.
- **FR-03 (Vehicle Detail View)**: Khi nhấp chọn xe, hiển thị drawer bên phải với đầy đủ thuộc tính: biển số, tài xế, vận tốc, giới hạn tốc độ, ETA trạm kế tiếp, tiến độ chuyến đi và timeline lộ trình. Hỗ trợ bật/tắt chế độ "Bám theo xe" (Follow mode).
- **FR-04 (Telemetry Simulator Engine)**: Cho phép điều khiển mô phỏng đội xe (Chạy/Tạm dừng/Khởi động lại/Tốc độ 1x-10x) với dữ liệu xe di chuyển dọc tuyến đường TP.HCM đã định nghĩa.
- **FR-05 (Location Picking & Map Interaction)**: Hỗ trợ nhấp bản đồ để chọn tọa độ trạm, kéo thả marker tinh chỉnh vị trí, nút "Lấy tọa độ trung tâm bản đồ", vòng tròn geofence co giãn trực quan theo giá trị bán kính.
- **FR-06 (Station Form Validation & Presets)**: Biểu mẫu tạo/sửa trạm có validation (bắt buộc tên trạm, tọa độ hợp lệ, bán kính từ 10m–1000m), thanh trượt chọn bán kính và các nút chọn nhanh (30m, 50m, 80m, 100m, 200m).
- **FR-07 (Dirty Form Protection)**: Cảnh báo khi người dùng hủy bỏ biểu mẫu trạm nếu đã có dữ liệu chỉnh sửa dở dang.
- **FR-08 (Station List & Actions)**: Bỏ cơ chế tab đoán mò trạm đầu/cuối từ chuỗi ký tự; bổ sung sắp xếp theo tên/bán kính, tìm kiếm đa trường; nút Sửa/Xóa trạm rõ ràng, dễ bấm trên mọi thiết bị.

## 5. Non-functional Requirements
- Tuân thủ nghiêm ngặt `docs/design.md`: bảng màu Dark operations shell, font chữ Inter, màu nhấn Cyan (`#0284c7`, `#38bdf8`), thẻ số `tabular-nums`.
- Hiệu năng Leaflet: Cập nhật marker xe mượt mà mà không re-create toàn bộ map instance hoặc tile layers; dọn dẹp timers, listeners khi unmount.
- Trợ năng & Responsive: Layout hoạt động tốt trên desktop, laptop và tablet màn hình cảm ứng; bổ sung đầy đủ thuộc tính `aria-*`.

## 6. Acceptance Criteria (AC)
- **AC-01**: Khi ở màn hình "Theo dõi xe", hiển thị danh sách phương tiện với trạng thái và số liệu tổng quan (tổng xe, đang chạy, trễ giờ).
- **AC-02**: Bản đồ hiển thị các xe đang di chuyển mượt mà dọc theo lộ trình mô phỏng; marker xe xoay đúng hướng di chuyển.
- **AC-03**: Bộ điều khiển simulator cho phép Start/Pause/Reset và thay đổi tốc độ mô phỏng (1x, 2x, 5x, 10x).
- **AC-04**: Nhấp vào xe trên danh sách hoặc bản đồ sẽ mở panel chi tiết xe, vẽ polyline tuyến đường và hỗ trợ chế độ bám theo xe (Follow Vehicle).
- **AC-05**: Khi tạo/sửa trạm, người dùng có thể nhấp bản đồ, kéo thả marker hoặc bấm nút "Lấy tâm bản đồ" để gán tọa độ.
- **AC-06**: Form trạm có slider bán kính và các nút preset (30m, 50m, 80m, 100m, 200m); vòng tròn geofence trên bản đồ phản hồi tức thì với sự thay đổi bán kính.
- **AC-07**: Khi đóng hoặc bấm Hủy form đang nhập dở, hệ thống hỏi xác nhận nếu có thay đổi để tránh mất dữ liệu.
- **AC-08**: Hộp thoại xác nhận ngừng sử dụng trạm hiển thị chi tiết tên trạm, tọa độ và xử lý an toàn.
- **AC-09**: Mã nguồn vượt qua kiểm tra kiểu TypeScript (`tsc --noEmit`), linter (`npm run lint`), và build thành công (`npm run build`).
