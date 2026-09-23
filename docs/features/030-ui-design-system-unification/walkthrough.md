# Walkthrough: Business UI refresh (Feature 030)

## 1. Quyết định thiết kế

Ứng dụng hiện có hai không gian rõ ràng:

- **Business console sáng:** dashboard, phương tiện, tài xế, chuyến đi, lịch chạy,
  cảnh báo, báo cáo, người dùng và driver portal. Nền sáng giúp đọc bảng, form và
  trạng thái trong các phiên làm việc dài.
- **Map-first tối:** operations, routes và stations. Workspace này đã được tối ưu
  riêng nên được giữ nguyên.

## 2. Thay đổi chính

`vehicletracking-frontend/src/ui-refresh.css` được nạp sau các stylesheet hiện hữu
trong `src/main.tsx`. Tất cả override cho shell đều có dạng
`.business-shell:not([data-map-focus="true"])`, vì vậy route bản đồ không nhận chúng.

### Shell và navigation

- Sidebar sáng với brand mark xanh, active navigation rõ ràng và nhóm menu dễ quét.
- Topbar trắng, tiêu đề trang, mô tả và account pill cùng một thang typography.
- Content canvas có max-width, khoảng cách ổn định và responsive padding.

### Dashboard và các trang nghiệp vụ

- Hero, KPI card, section heading và feedback state dùng chung border, radius,
  shadow và màu accent.
- Fleet workspace có toolbar, tabs, thẻ xe/tài xế/chuyến, editor và footer cùng một
  form language; không còn chữ sáng trên surface sáng hoặc input tối lạc tông.
- Lịch chạy, cảnh báo, báo cáo và người dùng dùng chung filter surface, list card,
  badge trạng thái và empty/error treatment.

### Responsive

- Desktop giữ content rộng vừa phải để tránh các card kéo quá dài.
- Tablet giảm padding và giữ sidebar drawer.
- Mobile thu gọn account label, hero padding và content gutter; fleet list chuyển
  sang flow một cột.

## 3. Bảo toàn chức năng

- Không đổi component, route, API contract, state hoặc event handler.
- Không thêm dependency CSS.
- Không chỉnh sửa MapComponent, workspace CSS hay các selector Leaflet.
- Auth page giữ branded dark surface hiện có; driver portal dùng business language
  sáng để phù hợp với các màn hình quản trị.

## 4. Kiểm tra

Đã chạy:

```bash
./node_modules/.bin/tsc --noEmit
npm run lint
npm run build
```

TypeScript và build đạt; lint có 6 warning React đã tồn tại. Chưa chạy browser/E2E
screenshot trong lượt này, nên visual sign-off trên viewport thật vẫn là bước tiếp
theo.
