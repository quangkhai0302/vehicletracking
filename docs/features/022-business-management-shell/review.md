# Review 022 — Business Management Shell

Ngày review: **2026-09-18**\
Reviewer: subagent `reviewer`, model Sol / high\
Kết luận: **Reviewed — không còn finding blocker/high/medium**.

## Findings và cách xử lý

| Finding vòng đầu | Cách xử lý | Trạng thái |
| --- | --- | --- |
| `MapComponent` dùng `<main>` bên trong `<main>` của shell | Đổi root map thành `<section aria-label="Không gian bản đồ vận hành">` | Đã đóng |
| Sidebar mobile ẩn bằng transform nhưng link còn trong tab order | Thêm `visibility`, focus vào close button, focus trap, Escape/overlay và focus return | Đã đóng |
| React có thể giữ state khi chuyển sibling route Xe/Tài xế/Chuyến | Thêm key theo domain route | Đã đóng |
| “Xem chuyến đi” làm mất filter xe | Mang `vehicleId` qua query và khởi tạo `vehicleFilter` ở trang Chuyến | Đã đóng |
| Workspace map có thể lệch URL/topbar | Thêm callback route-aware cho tracking/simulation/routes/stations | Đã đóng |
| NavLink đóng mobile drawer nhưng focus nằm trên link bị ẩn | Dùng `closeNavigation` trả focus về menu trigger | Đã đóng |
| Warning hook dependency mới trong shell | Memoize `closeNavigation` bằng `useCallback` và khai báo dependency | Đã đóng |

## Xác nhận acceptance criteria

- AC-01 đến AC-09 được reviewer xác nhận nhất quán với Requirement/Spec.
- Không phát hiện dữ liệu dashboard giả, RBAC frontend giả hoặc thay đổi backend/schema ngoài phạm vi.
- Feature 021 được giữ: route page tái sử dụng FleetWorkspace, CRUD/assignment và filter chuyến theo xe.

## Kiểm tra reviewer

- `npm run lint`: exit 0; còn 2 warning baseline trong `useFleetWorkspace.ts`.
- `tsc --noEmit`: exit 0.
- `npm run build`: exit 0.
- `git diff --check`: exit 0.

## Residual risk

- Chưa có frontend browser/E2E runner để tự động hóa viewport 390 px, browser Back/Forward và tương tác Leaflet/SSE.
- Deep-link đã smoke bằng Vite preview và source Caddy có `try_files {path} /index.html`; chưa dựng container production Caddy trong vòng review này.

## Review bổ sung — Map focus mode

Theo feedback trực quan sau lần bàn giao đầu, reviewer đã rà riêng refinement cho `/operations`, `/routes`, `/stations`:

- Desktop full-bleed ẩn business topbar, thu sidebar thành rail 72 px và cho mở lại 264 px.
- Embedded mode bar bám sát mép trên; drawer/dock được dời lên dưới command bar.
- Dashboard và các business pages vẫn dùng layout 264 px + topbar.
- Mobile/tablet vẫn giữ topbar rút gọn, menu drawer, focus trap, Escape/overlay/focus-return.
- Screenshot Chrome headless kích thước 1914 × 1018 xác nhận map lấy trọn chiều cao và vùng trung tâm tăng rõ.

Kết quả review bổ sung: không còn finding blocker/high/medium trong code. Finding tài liệu không khớp implementation đã được đóng bằng cập nhật Spec/Evidence/Review này.

Residual risk bổ sung: chưa có E2E tự động cho thao tác mở/thu rail và viewport &lt;= 960 px; mobile screenshot cuối không chạy được do giới hạn công cụ của môi trường.

## UI refinement — Alert center

Theo feedback về việc panel cảnh báo cố định làm giảm diện tích bản đồ, alert stream đã được chuyển thành drawer mở theo nhu cầu:

- Command bar giữ nút Cảnh báo và badge số notification chưa đọc.
- Drawer desktop nằm bên phải; mobile dùng sheet cùng breakpoint hiện có.
- Escape đóng drawer và trả focus về trigger; simulator dock được ẩn trong lúc drawer mở.
- Empty state phân biệt notification đổi tuyến hiện có với cảnh báo lệch tuyến chưa có backend.

Kiểm tra source và lint/typecheck đã xác nhận không có finding blocker/high/medium cho refinement này. Browser smoke tương tác drawer vẫn là residual risk vì môi trường chưa có E2E runner.

Các finding medium/low của vòng follow-up đã được xử lý trong source:

- URL `/operations` và query `?mode=simulation` được đồng bộ hai chiều; `MapComponent` đồng bộ workspace từ query khi vẫn mounted để Back/Forward không giữ mode cũ và không mất chuyến mô phỏng đang chọn.
- Khi mở drawer, focus chuyển vào nút đóng; khi đóng, focus trả về đúng trigger đã mở (command bar hoặc launcher mobile). Escape không đóng drawer khi focus đang ở confirmation dialog.
- Command bar mobile cho phép nav co theo min-content, text ellipsis và giữ target Cảnh báo tối thiểu 42 px ở breakpoint hẹp.
- Badge unread nhận cập nhật optimistic từ `AlertStream` trong cùng snapshot và tự quay về snapshot mới khi SSE phát server time mới; legend chỉ còn loại notification đổi tuyến hiện có.

Reviewer đã xác nhận các điểm alert drawer, focus, responsive và badge ở vòng follow-up đầu. Vòng xác nhận cuối cho bản vá giữ component map mounted bị gián đoạn bởi giới hạn lượt của agent; main agent đã kiểm tra lại bằng lint, typecheck và diff check.

## UI refinement — Management pages

Sau feedback về `/vehicles`, `/drivers`, `/trips`, phần presentation được rà và triển khai trong cùng Feature 022:

- Module header theo domain có KPI dẫn xuất từ dữ liệu hiện có, CTA chính và refresh.
- Surface sáng đồng nhất với dashboard; filter/search và kết quả rõ hơn.
- Xe, tài xế, chuyến vẫn reuse mutation/CRUD/assignment/lifecycle; không đổi API.
- Desktop list có density phù hợp, mobile chuyển một cột; card và action có focus-visible.

Smoke review bằng Chrome headless ở 1440 × 1000 cho cả ba route và 390 × 844 cho `/drivers` không phát hiện overflow layout. API không chạy trong môi trường nên ảnh hiển thị error state là hành vi dự kiến.

Vòng reviewer cuối đã đóng các finding medium: tương phản trip detail/form preview, điều kiện CTA tạo chuyến khi thiếu xe active, diễn giải trạng thái vị trí khi không có snapshot realtime, đồng bộ URL khi xóa filter xe và không điều hướng nhầm từ Xe/Tài xế. Không còn finding blocker/high/medium.