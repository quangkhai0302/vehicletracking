# Spec 022 — Business Management Shell

Trạng thái: **Reviewed**.

## Information architecture và URL

| Path | Module | Trạng thái |
|---|---|---|
| `/dashboard` | Tổng quan | Dùng API hiện có |
| `/operations` | Giám sát vận hành | Map/realtime hiện có |
| `/vehicles` | Phương tiện | CRUD + driver assignment hiện có |
| `/drivers` | Tài xế | CRUD hiện có |
| `/trips` | Chuyến đi | Create/detail/lifecycle/driver assignment hiện có |
| `/routes` | Tuyến đường | Map route editor hiện có |
| `/stations` | Trạm dừng | Map station picker/editor hiện có |
| `/schedules` | Lịch chạy | Roadmap page, chưa có scheduler |
| `/alerts` | Cảnh báo | Roadmap page, nêu rõ notification hiện chưa đủ alert management |
| `/reports` | Báo cáo | Roadmap page, chưa có aggregate API |
| `/users` | Người dùng & phân quyền | Roadmap page, chưa có authentication/RBAC |

`/` redirect `/dashboard`; unknown path render not-found trong shell.

## Shell contract

- Desktop business pages: sidebar cố định 264 px, topbar, scrollable main content.
- Desktop map pages (`/operations`, `/routes`, `/stations`): map focus mode ẩn business topbar, sidebar mặc định thu thành rail 72 px và có nút mở lại 264 px. Link ở rail giữ accessible label/title.
- Mobile/tablet: sidebar là overlay drawer; map pages giữ topbar rút gọn để còn nút mở menu; trigger có `aria-expanded`, close button, click overlay/Escape đóng và focus trả trigger.
- Navigation là `<nav aria-label="Điều hướng chính">` với `NavLink`; active route có `aria-current="page"` do router.
- Topbar hiển thị eyebrow, title và description của route; không hiển thị user/role giả.
- Content max-width cho business pages; map routes dùng edge-to-edge content và không mount ở route khác.

## Dashboard contract

- Fetch vehicles, drivers, trips song song qua services hiện có; có abort cleanup, loading, error và retry.
- Metric: xe active/tổng, tài xế active/tổng, trip `IN_PROGRESS`, `SCHEDULED`, `COMPLETED`, số trip `CANCELLED`.
- Quick actions tới Giám sát, Xe, Tài xế, Chuyến.
- “Cảnh báo cần xử lý”, “Xe lệch tuyến”, “Tỷ lệ đúng giờ” hiển thị là **Chưa có nguồn dữ liệu tổng hợp**, không hiển thị 0 giả.

## Management pages

- `FleetWorkspace` nhận `initialTab` và `lockedTab`. Khi locked, ẩn tab nội bộ và giữ route domain tương ứng.
- Business page đặt workspace trong surface full-width; list/form/detail có chiều cao hợp lý và scroll nội dung.
- Từ Vehicle/Trip có hành động cần map thì điều hướng `/operations`, không yêu cầu map ref trong page quản trị.
- Route/Station paths mount `MapComponent` với initial workspace tương ứng.

## Operations contract

- `MapComponent` nhận initial workspace và presentation embedded.
- Ở presentation embedded trên desktop, mode bar bám sát mép trên thay vì nổi cách map; drawer/dock bắt đầu dưới command bar.
- Alert stream không chiếm chỗ thường trực trên map: command bar hiển thị nút Cảnh báo và số chưa đọc; người dùng mở alert drawer khi cần. Drawer có Escape/focus return và mobile chuyển thành sheet.
- Giữ Leaflet init/cleanup, SSE cleanup, layers, traffic, simulator, alert stream và route/station workflows.
- Root map dùng chiều cao container trong shell, không ép thêm một viewport ngoài shell.

## API/data/security

- Không đổi backend API, migration hay DTO.
- Không implement quyền bằng frontend. Roadmap RBAC phải nêu backend Spring Security là dependency.
- Không thêm secret; tiếp tục dùng `VITE_API_BASE_URL` như public build-time config.

## Error/responsive

- Dashboard fetch fail hiển thị error và retry.
- Fleet pages giữ error/empty/confirm hiện có.
- Sidebar ở <= 960 px chuyển drawer; main content giữ min-width 0; management cards/table không bị cắt.
- Map compact behavior hiện tại vẫn áp dụng bên trong vùng map.

## Mapping AC

Contract này bao phủ AC-01 đến AC-09 trong `requirement.md`.
