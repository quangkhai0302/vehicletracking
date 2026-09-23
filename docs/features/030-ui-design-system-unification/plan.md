# Plan: 030-ui-design-system-unification

## Trạng thái: Verified

> Quyết định triển khai: giữ nguyên map-first workspace; thay vì sửa trực tiếp từng
> stylesheet trang, nạp `src/ui-refresh.css` sau cùng và scope toàn bộ selector quản trị
> bằng `.business-shell:not([data-map-focus="true"])`. Cách này giảm rủi ro hồi quy cho
> Leaflet và cho phép đồng bộ giao diện nghiệp vụ bằng một điểm kiểm soát.

## Các bước triển khai chi tiết

### Bước 1: Khởi tạo Bộ Tokens Thiết kế Hợp nhất (Unified Design Tokens)
- **Mục tiêu**: Bổ sung bộ biến CSS hoàn chỉnh tại `:root` trong `src/index.css` cho canvas background, surface layers, borders, typography colors, semantic accents, shadows và radii theo đúng `spec.md`.
- **File sửa**:
  - `vehicletracking-frontend/src/index.css`
- **Điều kiện hoàn thành**: Biến CSS có sẵn cho toàn bộ ứng dụng; không làm ảnh hưởng style bản đồ cũ.

### Bước 2: Tái thiết kế Vỏ ứng dụng (Application Shell)
- **Mục tiêu**: Chuyển toàn bộ khung vỏ từ Light theme sang Dark Slate Glassmorphic Theme.
- **File sửa**:
  - `vehicletracking-frontend/src/app/application-shell.css`
- **Nội dung thực hiện**:
  - Đổi `.business-shell` sang background `--app-bg` và text `--text-primary`.
  - Cập nhật Sidebar: hiệu ứng mờ kính, active menu link phát sáng viền cyan, hover êm ái.
  - Cập nhật Topbar: nền kính mờ dark, đồng bộ với Sidebar, chữ tiêu đề trắng sáng, badge breadcrumb cyan neon, user pill thanh nhã.
  - Cập nhật `.business-surface`: chuyển thành bề mặt kính mờ dark `rgba(13, 22, 40, 0.75)` viền `rgba(255, 255, 255, 0.08)`.
- **Điều kiện hoàn thành**: Khung điều hướng chuyển sang nền Dark sang trọng, đồng nhất giữa Sidebar, Topbar và Content area.

### Bước 3: Nâng cấp Trang Xác thực (Login & Register)
- **Mục tiêu**: Thay thế giao diện đăng nhập cũ bằng thẻ đăng nhập Cyber Operations kính mờ sang trọng trên nền ambient radial glow.
- **File sửa**:
  - `vehicletracking-frontend/src/pages/auth-pages.css`
- **Nội dung thực hiện**:
  - Nền không gian điều hành Cyber Dark với ánh sáng cyan khuếch tán.
  - Card đăng nhập kính mờ (`rgba(13, 22, 40, 0.85)` + blur 24px) viền mỏng tinh tế.
  - Input fields nền tối trong suốt, viền mỏng, focus glow cyan.
  - Button Submit gradient cyan/sky rực rỡ.
- **Điều kiện hoàn thành**: Trang `/login` và `/register` hiển thị đẳng cấp, chuyên nghiệp.

### Bước 4: Đồng bộ Trang Tổng quan (Dashboard)
- **Mục tiêu**: Nâng cấp toàn diện `DashboardPage` để làm nổi bật thông tin vận hành.
- **File sửa**:
  - `vehicletracking-frontend/src/pages/business-pages.css`
- **Nội dung thực hiện**:
  - Welcome Banner: Gradient Deep Blue sang trọng, nút hành động kính mờ.
  - 5 Thẻ KPI Metrics: Bề mặt kính mờ, icon phát quang tinh tế, con số `tabular-nums` lớn 28px.
  - Bảng chuyến đi và danh sách cảnh báo: Hàng phân cách tinh tế, hiệu ứng hover nhẹ, badge trạng thái đúng chuẩn ngữ nghĩa.
- **Điều kiện hoàn thành**: Dashboard trở thành trung tâm chỉ huy vận hành ấn tượng, sắc nét.

### Bước 5: Chuẩn hóa Quản lý Đội xe (Vehicles, Drivers, Trips)
- **Mục tiêu**: Loại bỏ triệt để xung đột màu nền cũ, đưa `FleetWorkspace` hòa nhập hoàn toàn vào theme chung.
- **File sửa**:
  - `vehicletracking-frontend/src/components/fleet/fleet.css`
  - `vehicletracking-frontend/src/app/application-shell.css` (loại bỏ `.business-management-surface` nền trắng)
- **Nội dung thực hiện**:
  - Thẻ phương tiện, thẻ tài xế và thẻ chuyến đi dùng chung chuẩn card kính mờ.
  - Toolbar tìm kiếm, dropdown lọc và nút bấm được chuẩn hóa kích thước, viền và focus state.
  - Dialog xác nhận (FleetConfirmDialog) và Drawer form nhập liệu (VehicleEditor, DriverEditor, TripEditor) hiển thị đồng bộ.
- **Điều kiện hoàn thành**: Các trang `/vehicles`, `/drivers`, `/trips` hiển thị liền mạch, không còn hiện tượng chữ đen trên nền tối hay input tối trên nền sáng.

### Bước 6: Đồng bộ Trang Lịch chạy tự động (Schedules)
- **Mục tiêu**: Thiết kế lại toàn bộ thẻ lịch chạy, bộ lọc và modal tạo lịch.
- **File sửa**:
  - `vehicletracking-frontend/src/pages/schedule-management.css`
- **Nội dung thực hiện**:
  - Bỏ màu xanh lá lạc điệu, chuyển Hero Banner sang chuẩn chung.
  - Cập nhật 3 thẻ KPI lịch chạy, thanh filter kính mờ.
  - Danh sách thẻ lịch chạy: Badge trạng thái (Active: Emerald, Paused: Muted Slate), thông tin trạm và tần suất rõ ràng.
  - Drawer chỉnh sửa lịch chạy: Form kính mờ, chọn thứ trong tuần dạng pill hiện đại.
- **Điều kiện hoàn thành**: Trang `/schedules` hiện đại, thanh thoát, đồng bộ với toàn hệ thống.

### Bước 7: Đồng bộ Trang Cảnh báo (Alerts)
- **Mục tiêu**: Nâng cấp trung tâm cảnh báo để làm nổi bật các sự cố theo mức độ nghiêm trọng.
- **File sửa**:
  - `vehicletracking-frontend/src/pages/alerts-management.css`
- **Nội dung thực hiện**:
  - Thẻ cảnh báo có đường viền chỉ báo (Accent Left Border): Đỏ cho Critical (lệch tuyến), Cam cho Major (kẹt xe), Xanh cho Info.
  - Cảnh báo chưa đọc (Unread) có hiệu ứng viền phát sáng nhẹ.
  - Nút Đọc tất cả và bộ lọc đồng bộ.
- **Điều kiện hoàn thành**: Trang `/alerts` hiển thị trực quan, cảnh báo khẩn cấp nổi bật rõ ràng.

### Bước 8: Đồng bộ Trang Báo cáo (Reports)
- **Mục tiêu**: Chuẩn hóa bộ lọc thời gian và lưới 8 chỉ số KPI hiệu suất.
- **File sửa**:
  - `vehicletracking-frontend/src/pages/reports.css`
- **Nội dung thực hiện**:
  - Khung lọc thời gian (Từ ngày - Đến ngày) và dropdown xe/tài xế tinh tế.
  - Lưới 8 thẻ KPI (Tổng số chuyến, Quãng đường, Thời gian chạy, Tỷ lệ đúng giờ, Chuyến trễ, Lệch tuyến, Vượt tốc độ) sắc nét.
  - Khối định nghĩa chỉ số ở chân trang gọn gàng, dễ tra cứu.
- **Điều kiện hoàn thành**: Trang `/reports` chuyên nghiệp, thông số trực quan, dễ đối soát.

### Bước 9: Đồng bộ Trang Người dùng (Users)
- **Mục tiêu**: Chuẩn hóa danh sách tài khoản và form cấp tài khoản tài xế.
- **File sửa**:
  - `vehicletracking-frontend/src/pages/user-management.css`
- **Nội dung thực hiện**:
  - Bảng danh sách tài khoản dạng hàng kính mờ, badge vai trò ADMIN/DRIVER sắc nét.
  - Form cấp tài khoản bên phải đồng bộ với hệ thống form controls chuẩn.
- **Điều kiện hoàn thành**: Trang `/users` đồng bộ, thẩm mỹ và phân cấp quyền rõ ràng.

### Bước 10: Kiểm tra Chất lượng & Nghiệm thu trực quan
- **Kiểm tra tự động**:
  ```bash
  cd vehicletracking-frontend
  npm run lint
  ./node_modules/.bin/tsc --noEmit
  npm run build
  ```
- **Kiểm tra trực quan với Browser Subagent**:
  - Sử dụng browser subagent đăng nhập tài khoản `admin` / `admin12345678`.
  - Duyệt qua từng trang (Dashboard, Operations, Vehicles, Drivers, Trips, Routes, Stations, Schedules, Alerts, Reports, Users).
  - Chụp màn hình và quay video kiểm chứng sự đồng bộ tuyệt đối về màu sắc, layout, card và typography.
- **Tài liệu hoàn tất**: Lập `evidence.md` và `walkthrough.md`.
