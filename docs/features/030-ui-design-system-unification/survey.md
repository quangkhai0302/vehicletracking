# Survey: 030-ui-design-system-unification

## 1. Cây file liên quan trong Frontend
```text
vehicletracking-frontend/src/
├── index.css                             # Global tokens & root rules
├── workspace.css                         # Map workspace & dark canvas styles
├── app/
│   ├── ApplicationShell.tsx              # Shell component (Sidebar, Topbar, Main)
│   ├── application-shell.css             # Shell styling (đang hardcode Light theme)
│   └── routeConfig.ts                    # Cấu hình navigation items & nhóm menu
├── components/
│   ├── MapComponent.tsx                  # Khối bản đồ Leaflet trung tâm
│   └── fleet/
│       ├── FleetWorkspace.tsx            # Không gian quản lý xe/tài xế/chuyến
│       └── fleet.css                     # Style quản lý đội xe
└── pages/
    ├── LoginPage.tsx                     # Form đăng nhập
    ├── AdminRegistrationPage.tsx         # Form đăng ký quản trị
    ├── auth-pages.css                    # Style trang đăng nhập / đăng ký
    ├── DashboardPage.tsx                 # Trang tổng quan KPI & mini-map
    ├── FleetManagementPage.tsx           # Wrapper cho Quản lý xe/tài xế/chuyến
    ├── business-pages.css                # Style Dashboard & Roadmap
    ├── ScheduleManagementPage.tsx        # Quản lý lịch chạy tự động
    ├── schedule-management.css           # Style lịch chạy tự động
    ├── AlertsManagementPage.tsx          # Trung tâm cảnh báo
    ├── alerts-management.css             # Style trung tâm cảnh báo
    ├── ReportsPage.tsx                   # Báo cáo hiệu suất vận hành
    ├── reports.css                       # Style báo cáo
    ├── UserManagementPage.tsx            # Quản lý tài khoản người dùng
    └── user-management.css               # Style người dùng
```

## 2. Khảo sát hiện trạng thực tế và Các điểm bất cập

### A. Vỏ ứng dụng (Application Shell)
- `src/app/application-shell.css:1-6`: Lớp `.business-shell` định nghĩa:
  ```css
  color: #172033;
  background: #f3f6fa;
  --business-border: #dce3ec;
  --business-muted: #64748b;
  --business-primary: #0369a1;
  ```
  Nền sáng nhờ nhờ đối nghịch hoàn toàn với tông nền tối của Map (`#060910` trong `src/workspace.css:4`).
- `src/app/application-shell.css:7-10`: Sidebar dùng nền tối (`linear-gradient(180deg,#0b1729 0%,#0b1220 100%)`).
- `src/app/application-shell.css:31`: Topbar lại dùng nền trắng toát (`background: #fff; border-bottom: 1px solid var(--business-border)`).
- **Hậu quả**: Khi mở ứng dụng, người dùng thấy 3 mảng màu xung đột gay gắt: Sidebar đen xì bên trái, Topbar trắng toát ở trên và Main content nền xám đục ở giữa.

### B. Trang Đăng nhập & Đăng ký
- `src/pages/auth-pages.css:1`: `.auth-page` dùng nền gradient màu xanh lơ nhạt `linear-gradient(145deg,#e0f2fe,#f8fafc 48%,#dbeafe)`, thẻ card trắng toát thô cứng, form input viền xám nhạt, nút đăng nhập xanh đơn điệu. Chưa thể hiện được sự chuyên nghiệp, đẳng cấp của một hệ thống quản lý xe thông minh.

### C. Trang Tổng quan (Dashboard)
- `src/pages/business-pages.css:1-24`:
  - Thẻ chào mừng `.dashboard-welcome` dùng gradient xanh nước biển sặc sỡ (`#0c4a6e` - `#0369a1`) không đồng bộ với các trang khác.
  - Các khối KPI `.dashboard-metric` dùng card trắng phẳng viền `#dce3ec`, icon nhét trong các ô màu pastel nhạt (`#e0f2fe`, `#cffafe`, `#fef3c7`, `#ede9fe`, `#d1fae5`, `#ffe4e6`).
  - Bản đồ nhét trong khung thẻ nền trắng tạo ra sự chắp vá thị giác (bản đồ xám/tối nằm lọt thỏm trong thẻ trắng viền xám).

### D. Trang Quản lý Đội xe (`/vehicles`, `/drivers`, `/trips`)
- `src/app/application-shell.css:52`: `.business-management-surface` áp đặt nền trắng `#f8fafc` và viền `#dce3ec`.
- Trong khi đó, `src/components/fleet/fleet.css` lại được thiết kế với chữ trắng `#f8fafc` (dòng 3), nền input đen `rgba(8, 14, 24, 0.85)` (dòng 30, 43).
- **Hậu quả**: Các component trong FleetWorkspace bị hiển thị cọc cạch, ô input tối nằm trên nền sáng, thẻ danh sách xe bị lẫn lộn màu sắc và tương phản kém.

### E. Trang Lịch chạy, Cảnh báo, Báo cáo, Người dùng
- Mỗi trang sở hữu một bộ CSS riêng với kiểu card, padding và màu hero banner tự do:
  - `ScheduleManagementPage` (`schedule-management.css:2`): Hero `#0c4a6e` -> `#0369a1`.
  - `AlertsManagementPage` (`alerts-management.css:2`): Hero `#164e63` -> `#0e7490`.
  - `ReportsPage` (`reports.css:2`): Hero `#172554` -> `#2563eb`.
  - `UserManagementPage` (`user-management.css:1`): Hero `#312e81` -> `#4f46e5`.
- Nút bấm, bộ lọc (Filter bars), thẻ danh sách, bảng biểu và dialog xác nhận trên mỗi trang đều có class và style riêng biệt, gây phân mảnh và khó bảo trì.

## 3. Bảng Evidence từ Codebase

| Nhận định | Evidence | Ý nghĩa thực tế |
|---|---|---|
| Cấu hình Shell nền sáng đối nghịch với design guideline | `src/app/application-shell.css:1-6` | Cần chuyển sang Dark Slate Surface |
| Sidebar tối nhưng Topbar trắng toát | `src/app/application-shell.css:8`, `src/app/application-shell.css:31` | Cần đồng bộ Topbar sang cùng phong cách Dark với Sidebar |
| Xung đột nền sáng với component quản lý đội xe | `src/app/application-shell.css:52` và `src/components/fleet/fleet.css:3,30,43` | Cần loại bỏ nền `#f8fafc` để FleetWorkspace hiển thị chuẩn |
| Phân mảnh màu sắc Hero Banner giữa các trang | `business-pages.css:1`, `schedule-management.css:2`, `alerts-management.css:2`, `reports.css:2`, `user-management.css:1` | Cần chuẩn hóa sang một kiểu Hero Banner hiện đại chung |
| Thiếu hệ thống token bề mặt (Surfaces, Card, Form) | `src/index.css:4-44` | Đã có một số biến màu nhưng thiếu token chi tiết cho Shell, Cards, Tables, Forms |
| Auth page phong cách văn phòng nhạt nhòa | `src/pages/auth-pages.css:1` | Cần nâng cấp sang phong cách Cyber Control Glassmorphism |
