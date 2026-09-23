# Requirement: 030-ui-design-system-unification

> **Quyết định UX sau review:** map workspace đã tối ưu được giữ nguyên dark/map-first.
> Các màn hình quản trị dùng business console sáng, tương phản cao và nhất quán để ưu
> tiên đọc bảng, form và thao tác quản lý. Phần quyết định này supersede mô tả dark
> glass cho management shell ở bản nháp ban đầu.

## 1. Bối cảnh và Vấn đề
Hiện tại, hệ thống `vehicletracking-frontend` đang bị phân mảnh thiết kế giao diện nghiêm trọng giữa hai nửa ứng dụng:
1. **Không gian Bản đồ & Giám sát vận hành** (`/operations`, `/routes`, `/stations`): Sử dụng phong cách Dark Mode chuyên sâu (Deep Slate Navy `#060910` - `#0c1420`, điểm nhấn Cyan/Sky `#0284c7`/`#38bdf8`, glassmorphism theo quy chuẩn của `docs/design.md`).
2. **Không gian Quản lý & Shell** (`ApplicationShell`, `/dashboard`, `/vehicles`, `/drivers`, `/trips`, `/schedules`, `/alerts`, `/reports`, `/users`, `/login`): Được thêm vào qua các feature 021 - 029 với phong cách Light theme tạm bợ (`#f3f6fa`, khung card trắng toát `#ffffff`, viền xám `#dce3ec`), kết hợp sidebar tối màu (`#0b1729`) và topbar sáng màu.
3. **Mỗi trang nghiệp vụ tự sinh phong cách riêng**:
   - `ScheduleManagementPage` dùng hero xanh lá (`#0c4a6e` / `#075985`) cùng button xanh dương và icon xanh lục bảo.
   - `AlertsManagementPage` dùng hero xanh lơ đậm (`#164e63` / `#0e7490`).
   - `ReportsPage` dùng hero xanh navy (`#172554` / `#2563eb`).
   - `UserManagementPage` dùng hero tím (`#312e81` / `#4338ca`).
   - `FleetManagementPage` (`/vehicles`, `/drivers`, `/trips`) ép component `FleetWorkspace` (vốn có style dark) vào container nền sáng `.business-management-surface`, dẫn đến việc vỡ bố cục, chữ khó đọc, nút bấm và form inputs thô cứng.
   - Trang đăng nhập (`/login`) và đăng ký (`/register`) có nền gradient xanh xỉn, card trắng đơn điệu, thiếu tính hiện đại của một hệ thống điều hành đội xe thời gian thực.

## 2. Mục tiêu người dùng và nghiệp vụ
- Xây dựng **một Hệ thống Ngôn ngữ Thiết kế (Unified Design System)** đồng bộ 100% xuyên suốt toàn bộ ứng dụng từ Đăng nhập, Dashboard, Giám sát bản đồ cho tới toàn bộ các trang Quản lý (Xe, Tài xế, Chuyến, Tuyến, Trạm, Lịch chạy, Cảnh báo, Báo cáo, Người dùng).
- Chuyển đổi toàn bộ giao diện quản trị sang phong cách **Modern Fleet Operations Control Center** cao cấp:
  - Tông nền Deep Slate Navy (`#070b14` - `#0b1325`) với độ tương phản chuẩn WCAG AA.
  - Hiệu ứng kính mờ tinh tế (Glassmorphism & Luminous borders), card có chiều sâu (shadow và highlight gradient).
  - Bảng màu ngữ nghĩa (Semantic Palette) chuẩn hóa: Primary Sky/Cyan, Success Emerald, Warning Amber, Danger Rose/Crimson, Info Indigo.
  - Chuẩn hóa toàn bộ hệ thống Card, Table, Form Inputs, Buttons, Badges, Tabs, Filter bars, Modals và Empty states.
- Giữ nguyên 100% logic nghiệp vụ, API contract, React state, router flow và các tính năng tương tác.

## 3. Phạm vi (Scope)
### In Scope
- **Design Tokens chung**: Khởi tạo hệ thống CSS variables toàn diện trong `index.css` cho colors, typography, surfaces, borders, shadows, spacing, transition.
- **Application Shell & Navigation**: Đồng bộ `application-shell.css` sang chuẩn dark slate hiện đại, đồng nhất Sidebar, Topbar và Content wrapper.
- **Trang Xác thực**: Cải tiến giao diện `/login` và `/register` trong `auth-pages.css` theo phong cách cyber operations glassmorphic.
- **Trang Dashboard**: Tái thiết kế `DashboardPage` và `business-pages.css` với Welcome Hero, KPI Metric Cards, mini-map container, danh sách chuyến đi và cảnh báo đồng bộ.
- **Trang Quản lý Đội xe (Vehicles, Drivers, Trips)**: Cập nhật `fleet.css` và `business-pages.css` để hòa nhập mượt mà vào theme chuẩn, tối ưu card xe, card tài xế, form editor và modal.
- **Trang Lịch chạy tự động (`/schedules`)**: Thiết kế lại `schedule-management.css` theo bảng token thống nhất.
- **Trang Cảnh báo (`/alerts`)**: Thiết kế lại `alerts-management.css` với card cảnh báo phát sáng viền theo mức độ (Critical/Major/Info).
- **Trang Báo cáo (`/reports`)**: Chuẩn hóa `reports.css` với bộ lọc thời gian tinh tế, lưới 8 chỉ số KPI thống kê và giải thích thuật ngữ.
- **Trang Người dùng (`/users`)**: Chuẩn hóa `user-management.css` với bảng danh sách tài khoản, badge phân quyền và form cấp tài khoản.
- **Tương thích Responsive**: Hoạt động hoàn hảo trên desktop (>= 1200px), laptop/tablet (768px - 1199px) và mobile (< 768px).

### Out of Scope
- Thay đổi cấu trúc backend API hoặc database schema (không thêm migration, không sửa Spring Boot).
- Thêm dependency hoặc framework CSS mới (không dùng TailwindCSS, tiếp tục sử dụng Vanilla CSS theo quy ước).
- Đổi tên route hoặc sửa logic xác thực người dùng.

## 4. Acceptance Criteria (AC)
- **AC-1 (Theme & Shell Cohesion)**: Toàn bộ Application Shell (Sidebar, Topbar, Main content) chuyển sang tông Dark Slate đồng nhất, không còn hiện tượng chói sáng hoặc xung đột Light/Dark khi chuyển đổi giữa các trang bản đồ và trang quản lý.
- **AC-2 (Standardized Surfaces & Cards)**: Tất cả card thống kê (KPI), card danh sách (phương tiện, tài xế, chuyến đi, lịch chạy, cảnh báo) sử dụng chung chuẩn surface (border mỏng bán trong suốt, background kính mờ có chiều sâu, bo góc 12-14px, hover elevation tinh tế).
- **AC-3 (Unified Form Controls & Buttons)**: Tất cả inputs, selects, textareas và buttons xuyên suốt các trang có cùng chiều cao, font size, viền, trạng thái focus glow cyan và hiệu ứng hover đồng bộ.
- **AC-4 (Semantic Badges & Color Palette)**: Trạng thái xe, chuyến đi, lịch chạy, phân quyền và mức độ cảnh báo sử dụng đúng bảng màu ngữ nghĩa đồng nhất (Active = Emerald, In Progress = Sky/Cyan, Warning/Pending = Amber, Critical/Failed = Rose/Crimson).
- **AC-5 (Premium Auth Experience)**: Trang Đăng nhập `/login` và Đăng ký `/register` có giao diện đăng nhập trung tâm điều hành chuyên nghiệp, sang trọng.
- **AC-6 (No Regression)**: Tất cả các luồng tương tác (thêm/sửa/xóa phương tiện, tạo chuyến, cấu hình lịch, đọc cảnh báo, lọc báo cáo, cấp tài khoản, chuyển workspace bản đồ) hoạt động bình thường, không có lỗi runtime và build production thành công.
