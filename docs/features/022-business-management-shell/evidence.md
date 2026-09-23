# Evidence 022 — Business Management Shell

Ngày kiểm chứng: **2026-09-18**  
Trạng thái working tree: chưa commit; đang chứa thay đổi Feature 021 đã có trước khi Feature 022 bắt đầu và thay đổi Feature 022.

## Phạm vi source đã triển khai

- Router và route contract: `vehicletracking-frontend/src/App.tsx:17-48`.
- Metadata điều hướng: `vehicletracking-frontend/src/app/routeConfig.ts`.
- Sidebar/topbar/mobile drawer: `vehicletracking-frontend/src/app/ApplicationShell.tsx:7-60` và `application-shell.css`.
- Map focus mode: `ApplicationShell.tsx` dùng `data-map-focus`/`data-map-navigation-expanded`; `application-shell.css` thu desktop sidebar thành rail 72 px và ẩn topbar chỉ trên full-bleed route; `workspace.css` dock embedded mode bar vào mép trên.
- Dashboard dùng API thật: `vehicletracking-frontend/src/pages/DashboardPage.tsx:16-91`.
- Wrapper trang xe/tài xế/chuyến: `vehicletracking-frontend/src/pages/FleetManagementPage.tsx:6-31`.
- Business presentation cho xe/tài xế/chuyến: `vehicletracking-frontend/src/components/fleet/FleetWorkspace.tsx:40-125` và các rule scoped `.business-management-surface` trong `vehicletracking-frontend/src/components/fleet/fleet.css`.
- Page roadmap trung thực: `vehicletracking-frontend/src/pages/RoadmapPage.tsx` và route props tại `App.tsx:29-44`.
- Fleet page mode: `vehicletracking-frontend/src/components/fleet/FleetWorkspace.tsx:15-24,41-108`.
- Map embedded/initial workspace: `vehicletracking-frontend/src/components/MapComponent.tsx:68-75,757-772`.
- Alert center on demand: `vehicletracking-frontend/src/components/operations/ModeBar.tsx:1-30`, `MapComponent.tsx:128-165,843-883`, `workspace.css` rules `.mode-alert-trigger`/`.alert-drawer`; alert stream empty state ghi rõ nguồn hiện tại chỉ là thông báo đổi tuyến.
- Dependency: `react-router-dom` trong `vehicletracking-frontend/package.json` và lockfile.

## Mapping acceptance criteria

| AC | Evidence | Kết quả |
|---|---|---|
| AC-01 | `DashboardPage.tsx:22-36,42-88` | Fetch song song xe/tài xế/chuyến; có abort, loading, error, retry; KPI thiếu nguồn được ghi rõ |
| AC-02 | `ApplicationShell.tsx:32-44`; `routeConfig.ts` | Semantic nav + `NavLink`; title/description theo route |
| AC-03 | `App.tsx`; `vehicletracking-frontend/Caddyfile:16`; preview smoke | `/`, toàn bộ module route, query route và unknown URL đều trả HTTP 200 từ SPA fallback |
| AC-04 | `FleetManagementPage.tsx:16-30`; `FleetWorkspace.tsx:15-24,40-125` | Reuse đúng CRUD/gán tài xế/chuyến của Feature 021, khóa domain tab theo URL; thêm header/KPI/CTA/filter/list business theo từng module |
| AC-05 | `App.tsx`; `ApplicationShell.tsx`; `MapComponent.tsx`; `ModeBar.tsx`; `workspace.css` | Map được lazy-load, chiếm trọn chiều cao desktop; sidebar rail mở rộng được, command bar bám mép trên và cảnh báo chỉ mở thành drawer khi cần |
| AC-06 | `App.tsx:27-28`; `MapComponent.tsx:73-75` | Route/station truyền initial workspace tương ứng, không tạo form giả ngoài map |
| AC-07 | `App.tsx:29-44`; `RoadmapPage.tsx` | Bốn module tương lai nêu scope/dependency, không gọi API hay hiển thị số liệu giả |
| AC-08 | `ApplicationShell.tsx`; `application-shell.css` media <= 960px | Escape/overlay/close/NavLink trả focus; drawer focus trap, trigger có `aria-expanded`; desktop sidebar không overlay content |
| AC-09 | Các lệnh kiểm chứng bên dưới | Lint/typecheck/build/diff check exit 0; Feature 022 không sửa backend/schema |

## Lệnh đã chạy

| Lệnh | Exit | Kết quả |
|---|---:|---|
| `npm install react-router-dom` | 0 | Cài `react-router-dom@7.18.4`, audit 0 vulnerability |
| `npm run lint` | 0 | Không có error; còn 2 warning `react(set-state-in-effect)` ở `useFleetWorkspace.ts`, thuộc luồng đồng bộ Feature 021 có trước phần shell |
| `./node_modules/.bin/tsc --noEmit` | 0 | TypeScript không có lỗi |
| `npm run build` | 0 (Node 24) | Build xác nhận bằng Node v24.16.0; lint còn warning không chặn |
| `git diff --check` | 0 | Không có whitespace error trong tracked diff |
| Chrome headless `1440x1000` tại `/vehicles`, `/drivers`, `/trips` | 0 | Kiểm tra trực quan: module header, KPI, CTA, filter toolbar, error state và card/list không overflow |
| Chrome headless `390x844` tại `/drivers` | 0 | Kiểm tra responsive: menu topbar, header module, CTA, KPI, search/select và error state vừa viewport |

Reviewer vòng cuối đã kiểm tra và đóng các điểm: CTA tạo chuyến khi thiếu xe active, diễn giải vị trí realtime ở trang quản lý, đồng bộ xóa `vehicleId` filter với URL, không điều hướng nhầm khi xóa filter ở Xe/Tài xế, và tương phản của trip detail/form preview trên surface sáng.
| `npm run preview -- --host 127.0.0.1` + `curl` | 0 | `/`, dashboard, drivers, filtered trips, operations/simulation, routes, stations, 4 roadmap route và unknown page đều trả HTTP 200 cùng SPA document |
| Chrome headless `1914x1018` tại `/operations` | 0 | Kiểm tra trực quan: không còn business topbar desktop; rail 72 px; mode bar bám mép; map lấy toàn bộ chiều cao và tăng rõ vùng trung tâm |

Lần kiểm chứng alert center bổ sung: `npm run lint` exit 0 (chỉ còn 2 warning baseline ở `useFleetWorkspace.ts`), `tsc --noEmit` exit 0 và `git diff --check` exit 0. Chưa thể chạy lại build bằng Node 24 trong môi trường hiện tại; không có thay đổi backend/schema.

## Bảo mật và giới hạn

- Không thêm secret hoặc biến `VITE_*`; dashboard dùng service API nội bộ hiện có.
- Không giả lập authorization ở frontend; `/users` nêu rõ Spring Security/backend authorization là dependency.
- Không đổi backend API, DTO, migration trong phạm vi Feature 022. Các thay đổi backend đang có trong working tree thuộc Feature 021 và được bảo toàn.
- Chưa có frontend E2E runner để tự động hóa toggle rail, viewport mobile và Back/Forward. Đã smoke desktop bằng Chrome headless; lần chụp mobile cuối bị môi trường từ chối do giới hạn công cụ. Tương tác Leaflet/SSE đầy đủ vẫn cần backend đang chạy và kiểm tra thủ công trên trình duyệt.
