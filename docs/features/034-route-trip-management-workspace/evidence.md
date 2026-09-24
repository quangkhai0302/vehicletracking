# Evidence — Không gian quản lý tuyến và chuyến

Trạng thái: Verified ngày 2026-09-24.

## Phạm vi đã triển khai

- `src/pages/PlanningManagementPage.vue` cung cấp một workspace với hai tab Tuyến đường/Chuyến đi và tái sử dụng nguyên trang quản lý hiện có.
- Tab cấp trang dùng class riêng `planning-workspace-tabs`, tránh bị các rule `.planning-tabs` của panel bản đồ ghi đè margin/display làm lệch container `/routes` và `/trips`.
- `src/app/router/index.ts`, `src/app/navigation.ts` và `src/app/layouts/ApplicationShell.vue` giữ `/routes`, `/trips`, đồng thời ánh xạ hai URL vào một mục điều hướng “Tuyến & chuyến”.
- `RouteManagementPage.vue` hỗ trợ deep link `routeId`, mở chi tiết tuyến và chuyển sang `/trips?routeId=<id>&create=1`.
- `FleetManagementPage.vue`, `FleetWorkspace.vue` và `TripEditor.vue` nhận tuyến ban đầu, mở form tạo chuyến nhưng không tự lưu. Tuyến được chọn sẵn là baseline nên đóng form ngay không hiện cảnh báo chưa lưu giả.
- `TripDetailPanel.vue` mở ngược lại đúng tuyến bằng `/routes?routeId=<id>`.
- `TripDetailPanel.vue` và `ui-refresh.css` tổ chức lại chi tiết chuyến thành tổng quan rộng, các mốc thời gian phù hợp với nguồn điều phối, thanh thao tác, timeline trạm và cột tiến độ/ETA; breakpoint 980/700/480 px giữ đủ dữ liệu và thao tác.
- `tests/visual/capture.mjs` chụp riêng chi tiết chuyến ở 1440, 768 và 390 px để kiểm tra hồi quy bố cục.
- Không thay đổi API, database, quyền hoặc contract backend. Các thay đổi bản đồ đã có trong working tree trước feature được bảo toàn và không thuộc phạm vi triển khai này.

## Mapping acceptance criteria

| AC | Bằng chứng code/test | Kết quả |
|---|---|---|
| 1 | `PlanningManagementPage.vue`; `router/index.ts`; `navigation.ts`; `router.test.ts` — test workspace chung và một mục navigation | `/routes` và `/trips` mở đúng tab, sidebar active cho cả hai URL, Back hoạt động. |
| 2 | Hai tab tiếp tục render `RouteManagementPage` và `FleetManagementPage`; toàn bộ 97 unit test frontend đạt; visual fixture kiểm tra chi tiết chuyến ở ba viewport | Danh sách, form, chi tiết và thao tác hiện hữu được giữ lại; bố cục responsive không mất dữ liệu. |
| 3 | `RouteManagementPage.vue:createTripFromRoute`; `TripDetailPanel.vue`; test cross-link trong `router.test.ts` | Tuyến mở form chuyến có preselect; chuyến mở đúng chi tiết tuyến; không POST tự động. |
| 4 | `FleetManagementPage.vue` validate query; `RouteManagementPage.vue` đồng bộ drawer với query; test query sai, `vehicleId` và Back/Forward | Deep link hợp lệ; query sai không làm crash hoặc tự mở form. |
| 5 | Các lệnh kiểm tra bên dưới | Lint, typecheck, unit, motion và build đều exit code 0. |

## Kiểm tra đã chạy

Môi trường: Node.js `v24.16.0`, đúng định hướng `.nvmrc` và yêu cầu `engines`.

| Lệnh | Kết quả |
|---|---|
| `npm run lint` | Exit 0. |
| `npm run typecheck` | Exit 0. |
| `npx vitest run tests/unit/router.test.ts tests/unit/fleet.test.ts` | 2 file, 20 test đạt. |
| `npm run test:unit` | 14 file, 97 test đạt. |
| `npm run test:motion` | 1 test đạt. |
| `npm run build` | Exit 0; Vite build production thành công, 2031 module transformed. |
| `UI_SCOPE=business UI_OUTPUT_DIR=/tmp/vehicletracking-trip-detail-review npm run test:visual` | Exit 0; 62 ảnh fixture, gồm `trip-detail-1440`, `trip-detail-768`, `trip-detail-390`; 0 page error. |
| `git diff --check` | Exit 0; không có whitespace error. |

## Giới hạn kiểm chứng

- Đã kiểm tra trình bày và runtime trong Chromium bằng HTTP fixture xác định; chưa walkthrough lại với dữ liệu backend thật trong lượt này.
- Không chạy backend test vì feature không thay đổi backend/API/schema.

## Sửa hồi quy căn chỉnh ngày 2026-09-24

- `PlanningManagementPage.vue` và `planning-workspace.css` được đổi sang selector riêng; mép tab dùng đúng chiều rộng của `.planning-workspace` và hai tab chia đều không gian.
- `router.test.ts`: 9/9 test đạt.
- `npm run lint`, `npm run typecheck`, `npm run build`: exit code 0.
- Không chụp lại ảnh bằng Playwright trong lượt này vì sandbox không cho Vite bind `127.0.0.1:5173` (`listen EPERM`).
