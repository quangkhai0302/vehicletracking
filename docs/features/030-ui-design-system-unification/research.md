# Research: 030-ui-design-system-unification

## 1. Mục tiêu nghiên cứu
Khảo sát các nguyên lý thiết kế và tiêu chuẩn giao diện trung tâm điều hành vận tải hiện đại (Fleet Operations & Transportation Control Center) để định hình một hệ thống thiết kế (Design System) chuẩn mực, áp dụng đồng bộ cho toàn bộ ứng dụng web `vehicletracking-frontend`.

## 2. Tiêu chuẩn và Ràng buộc kỹ thuật của Repository
- **Ngôn ngữ & Công nghệ**: HTML5, Vanilla CSS3, React 19, TypeScript, Vite.
- **Ràng buộc quy tắc**: Quy ước dự án nghiêm cấm việc tự ý cài đặt thư viện CSS thứ ba như TailwindCSS, Ant Design hay MUI khi chưa có yêu cầu. Mọi cải tiến phải sử dụng CSS thuần (Vanilla CSS) có cấu trúc module hoặc semantic tokens.
- **Tài liệu nền tảng**: File `docs/design.md` (Source of truth của dự án) quy định:
  - Phong cách: **Modern Fleet Operations / Transportation Control Dashboard**.
  - Ứng dụng là một công cụ điều hành xe thời gian thực (Realtime Fleet Operations), không phải web CRUD văn phòng thông thường.
  - Vỏ ứng dụng (Shell): Dark Theme đồng nhất.
  - Điểm nhấn chính: Cyan/Blue (`#0284c7`, `#38bdf8`).
  - Màu trạng thái ngữ nghĩa: Green (`#10b981`), Amber (`#f59e0b`), Red (`#ef4444`).
  - Font chữ: Inter (`Inter, ui-sans-serif, system-ui, -apple-system, sans-serif`).
  - Spacing scale: 4, 8, 12, 16, 20, 24, 32.

## 3. Các thực tiễn tốt nhất từ các hệ thống điều hành xe hàng đầu
Tham chiếu từ các bảng điều khiển vận hành hàng đầu (như Tesla Fleet Telemetry, Uber Freight Management, Palantir Foundry Operations):
1. **Luminous Dark Surfaces (Bề mặt tối có chiều sâu)**:
   - Thay vì dùng nền trắng chói hoặc nền đen kịt (`#000000`), sử dụng các lớp màu Deep Slate/Navy xếp tầng:
     - Nền sâu nhất (Canvas background): `#070b14` hoặc `#080d1a`.
     - Lớp vỏ ứng dụng (Sidebar, Topbar): `rgba(11, 19, 36, 0.85)` đi kèm `backdrop-filter: blur(20px)`.
     - Lớp card / panel nghiệp vụ: `rgba(15, 25, 45, 0.7)` kết hợp viền mỏng tinh tế `1px solid rgba(255, 255, 255, 0.08)`.
     - Lớp card nổi bật / active: `rgba(20, 35, 62, 0.85)` với viền `rgba(56, 189, 248, 0.3)`.
2. **Visual Hierarchy & Typography**:
   - Sử dụng font chữ hiện đại Inter đã được tích hợp sẵn.
   - Giảm thiểu số lượng font size: Page title (20-22px), Section title (15-16px), Card title (13-14px), Body text (12-13px), Label & Badge (9-11px).
   - Sử dụng `tabular-nums` cho các số liệu KPI, thời gian và chỉ số vận hành để không bị nhảy số khi cập nhật realtime.
3. **Micro-interactions & Focus States**:
   - Hover cards nâng nhẹ (`transform: translateY(-2px)`) với độ mượt transition 200ms ease.
   - Form inputs và buttons có outline/box-shadow glow cyan khi focus: `box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.25)`.
   - Nút bấm Primary sử dụng gradient xanh rực rỡ có độ bão hòa cao (`linear-gradient(135deg, #0284c7 0%, #0ea5e9 100%)`).

## 4. Kết luận áp dụng cho Spec
- Khởi tạo bảng biến CSS tokens chuẩn hóa tại `:root` trong `src/index.css`.
- Viết lại toàn bộ hệ thống lớp vỏ trong `src/app/application-shell.css` để chuyển từ Light theme (`#f3f6fa`) sang Dark theme thống nhất.
- Đồng bộ lại style của từng trang (`business-pages.css`, `schedule-management.css`, `alerts-management.css`, `reports.css`, `user-management.css`, `auth-pages.css`, `fleet.css`) dựa trên bộ token chung này.
