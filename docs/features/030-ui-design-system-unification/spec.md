# Spec: 030-ui-design-system-unification

> **Implementation note:** contract cuối cùng áp dụng light business console cho các
> route không có `data-map-focus="true"`; dark map-first styles vẫn là contract riêng
> của operations/routes/stations. `src/ui-refresh.css` là lớp override có scope để
> bảo toàn hai không gian này.

## 1. Hệ thống Tokens Thiết kế Hợp nhất (Unified Design Tokens)
Định nghĩa tại `:root` trong `src/index.css` và kế thừa xuyên suốt toàn bộ các file CSS:

```css
:root {
  /* Tông màu nền chính (Dark Canvas) */
  --app-bg: #070b14;
  --app-canvas: #090e1c;
  
  /* Bề mặt kính & Panel (Glassmorphism & Luminous Surfaces) */
  --surface-base: rgba(13, 21, 38, 0.72);
  --surface-elevated: rgba(18, 30, 54, 0.85);
  --surface-overlay: rgba(22, 38, 68, 0.95);
  --surface-glass: rgba(15, 25, 46, 0.65);
  --backdrop-blur: blur(16px) saturate(180%);

  /* Hệ thống đường viền (Borders) */
  --border-subtle: rgba(255, 255, 255, 0.07);
  --border-default: rgba(255, 255, 255, 0.12);
  --border-focus: rgba(56, 189, 248, 0.5);
  --border-highlight: rgba(56, 189, 248, 0.25);

  /* Chữ & Độ tương phản (Typography) */
  --text-primary: #f8fafc;
  --text-secondary: #cbd5e1;
  --text-muted: #94a3b8;
  --text-dim: #64748b;

  /* Điểm nhấn & Trạng thái ngữ nghĩa (Accents & Semantic Colors) */
  --accent-cyan: #06b6d4;
  --accent-sky: #0ea5e9;
  --accent-primary: #0284c7;
  --accent-primary-gradient: linear-gradient(135deg, #0284c7 0%, #0ea5e9 100%);
  --accent-primary-hover: linear-gradient(135deg, #0369a1 0%, #0284c7 100%);

  --semantic-success: #10b981;
  --semantic-success-bg: rgba(16, 185, 129, 0.14);
  --semantic-success-border: rgba(16, 185, 129, 0.28);

  --semantic-warning: #f59e0b;
  --semantic-warning-bg: rgba(245, 158, 11, 0.14);
  --semantic-warning-border: rgba(245, 158, 11, 0.28);

  --semantic-danger: #f43f5e;
  --semantic-danger-bg: rgba(244, 63, 94, 0.14);
  --semantic-danger-border: rgba(244, 63, 94, 0.28);

  --semantic-info: #6366f1;
  --semantic-info-bg: rgba(99, 102, 241, 0.14);
  --semantic-info-border: rgba(99, 102, 241, 0.28);

  /* Đổ bóng (Shadows) */
  --shadow-card: 0 10px 30px -5px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.06);
  --shadow-card-hover: 0 16px 40px -5px rgba(0, 0, 0, 0.55), 0 0 0 1px rgba(56, 189, 248, 0.35);
  --shadow-dropdown: 0 20px 48px rgba(0, 0, 0, 0.6);
  --shadow-glow-cyan: 0 0 24px rgba(6, 182, 212, 0.25);

  /* Bo góc (Border Radii) */
  --radius-sm: 6px;
  --radius-md: 9px;
  --radius-lg: 14px;
  --radius-xl: 18px;
  --radius-full: 9999px;
}
```

## 2. Quy chuẩn thiết kế từng thành phần (UI Component Contracts)

### 2.1 Application Shell (`src/app/application-shell.css`)
- **Shell Container**: `.business-shell` dùng background `--app-bg` (`#070b14`), color `--text-primary`.
- **Sidebar**: Nền `rgba(11, 18, 33, 0.9)`, border phải `1px solid var(--border-subtle)`.
  - Brand mark: Icon la bàn gradient cyan phát sáng nhẹ (`--shadow-glow-cyan`).
  - Menu NavLink: Trạng thái bình thường có text `--text-muted`, hover chuyển sang `--text-primary` với background `rgba(255, 255, 255, 0.05)`.
  - Active item: Background `linear-gradient(90deg, rgba(2, 132, 199, 0.3), rgba(14, 165, 233, 0.08))`, viền mỏng cyan, text sáng rực `#e0f2fe`, vạch chỉ báo trái (accent border indicator) phát sáng.
- **Topbar**: Nền `rgba(11, 18, 33, 0.85)` kèm `backdrop-filter`, viền dưới `1px solid var(--border-subtle)`.
  - Tiêu đề H1 màu `--text-primary`, badge thể loại xanh neon nổi bật.
  - User account pill: Nền `rgba(255, 255, 255, 0.06)`, text `--text-secondary`, nút đăng xuất hover đổi sang màu đỏ tinh tế.
- **Content Area**: Nền trong suốt trên nền canvas sâu thẳm, thanh cuộn sleek mỏng.

### 2.2 Trang Đăng nhập & Đăng ký (`src/pages/auth-pages.css`)
- `.auth-page`: Nền không gian điều hành Cyber Dark với ambient radial gradient:
  `radial-gradient(circle at 50% 20%, rgba(2, 132, 199, 0.15) 0%, transparent 60%), #070b14`.
- `.auth-card`: Bề mặt kính nổi bật `background: rgba(13, 22, 40, 0.85)`, `backdrop-filter: blur(24px)`, viền `1px solid rgba(255, 255, 255, 0.12)`, shadow đa tầng sâu lắng.
- `.auth-input`: Nền `rgba(7, 12, 22, 0.7)`, viền `1px solid rgba(255, 255, 255, 0.12)`, focus glow viền cyan rực rỡ.
- Submit button: Nút bấm gradient `--accent-primary-gradient` nổi bật, hiệu ứng hover nâng nhẹ.

### 2.3 Trang Tổng quan (Dashboard) (`src/pages/business-pages.css`)
- **Welcome Banner**: Gradient xanh thẫm uy nghi (`linear-gradient(135deg, #0c2b4d 0%, #071c35 100%)`), viền `1px solid rgba(56, 189, 248, 0.2)`, nút dẫn nhanh kính trắng mờ.
- **KPI Metrics**: Thẻ kính mờ `.dashboard-metric` bo góc 14px, icon đặt trong hộp kính có phát quang tinh tế, con số giá trị lớn 28px in đậm rõ nét font `tabular-nums`.
- **Bản đồ mini & Khối danh sách**: Bề mặt `.business-surface` đồng bộ kính mờ đen sâu, hàng chuyến đi `.dashboard-trip-row` có hover êm dịu, badge trạng thái chuyến đi đúng màu ngữ nghĩa (In Progress: Sky, Completed: Emerald, Cancelled: Rose).

### 2.4 Quản lý Đội xe (`FleetManagementPage` & `src/components/fleet/fleet.css`)
- Gỡ bỏ nền xám trắng `.business-management-surface` (`background: #f8fafc`). Thay bằng bề mặt kính mờ thống nhất.
- Hộp tìm kiếm và dropdown bộ lọc: Đồng bộ style input kính mờ sang trọng.
- Thẻ phương tiện & tài xế: Thẻ card bo góc 12px, badge biển số nổi bật, các nút thao tác icon (Sửa, Xóa, Chi tiết) mượt mà, phản hồi màu tương ứng khi hover.

### 2.5 Lịch chạy, Cảnh báo, Báo cáo, Người dùng
- **Hero Banner**: Chuẩn hóa cùng kiểu thiết kế với Dashboard (Header tinh gọn, icon đại diện phát sáng, typography rõ ràng).
- **Bộ lọc (Filter Toolbar)**: Nền kính mờ `rgba(10, 16, 30, 0.6)`, các thẻ chọn và ngày tháng được cách điệu hiện đại.
- **Lưới chỉ số (Metrics Grid)**: Đồng bộ kiểu dáng thẻ, icon và typography với Dashboard.
- **Card cảnh báo (`/alerts`)**: Thẻ cảnh báo phát sáng viền trái theo cấp độ (Critical = đỏ thắm, Major = cam hổ phách, Info = lam biếc), thời gian tương đối hiển thị thanh nhã.
- **Báo cáo (`/reports`)**: 8 thẻ KPI được tổ chức thành lưới 4 cột trên desktop, 2 cột trên tablet và 1 cột trên mobile, dễ quan sát đối soát.
- **Người dùng (`/users`)**: Bảng tài khoản dạng danh sách phân hàng kính mờ, form tạo tài khoản chuẩn form hệ thống.

## 3. Quy tắc Bảo toàn Chức năng (Functional Invariants)
- Toàn bộ DOM ID, `aria-*`, `role`, class chức năng, test attributes và event handler giữ nguyên vẹn 100%.
- Không thay đổi text nội dung hiển thị tiếng Việt đã có.
- Không gây ảnh hưởng tới hoạt động của bản đồ Leaflet, WebGL canvas, HERE API và Simulator.
