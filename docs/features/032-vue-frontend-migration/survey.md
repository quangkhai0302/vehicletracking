# Survey — 032

Khảo sát source hiện tại ngày 2026-09-22; frontend_surveyor Terra/medium chỉ đọc. Main agent khảo sát entry, HTTP/security boundary, toolchain/deploy và tests; không dựa vào walkthrough cũ để kết luận chức năng tồn tại.

## Inventory

`rg --files vehicletracking-frontend/src` ghi nhận **124 file: 51 `.tsx`, 57 `.ts`, 16 `.css`**. Có 14 hook trong `src/hooks/`. Đây là số file, không phải số component hoặc số màn hình.

```text
vehicletracking-frontend/
  index.html, vite.config.js, tsconfig.json, .oxlintrc.json
  package.json, package-lock.json, Dockerfile, Caddyfile
  src/
    main.tsx, App.tsx
    app/              shell, routeConfig, style
    auth/             AuthContext, RouteGuards
    pages/            11 TSX, gồm RoadmapPage chưa gắn route
    components/
      business/       heading, auth layout, fleet table, dialog
      fleet/          xe/tài xế/chuyến/editor/history/revision
      operations/     simulator, live layers, alerts
      route/          route editor, stops, inspection
      traffic/        layer và inspection card
      MapComponent, panels/drawers/controls
    hooks/            14 React hooks
    services/         HTTP, auth, fleet, telemetry, route/traffic…
    types/, utils/    TypeScript dùng lại được
    index.css, workspace.css, ui-refresh.css
  public/huong-dan/   HTML/CSS tĩnh
  tests/vehicleMotion.test.ts
```

Evidence inventory: `src/App.tsx`, `src/hooks/`, các import `.tsx`; số lượng được đếm bằng `rg --files` theo extension. Inventory chi tiết từng file sẽ được đóng băng thành manifest trước implementation; cả component chưa được route/import tới cũng phải có disposition rõ, không xoá chỉ vì có vẻ không dùng.

## Route và URL contract

Nguồn: `vehicletracking-frontend/src/App.tsx:19` (`MapPage`) và `:40` (`App`). Có **15 URL màn hình được khai báo tường minh**, ngoài `/` redirect và catch-all:

| Nhóm | URL | State đặc biệt |
|---|---|---|
| Public | `/login`, `/register` | Người đã đăng nhập được redirect theo role ở auth pages |
| Admin không map | `/dashboard`, `/vehicles`, `/drivers`, `/trips`, `/schedules`, `/alerts`, `/reports`, `/users` | Fleet remount theo key của tab; `/trips?vehicleId=…` lọc xe |
| Admin map | `/operations`, `/routes`, `/stations` | Map keys khác nhau; `/operations?mode=simulation&tripId=…`; đổi query trong operations không remount map |
| Driver | `/driver/today`, `/driver/schedules` | Chung DriverPortalPage; chuyển view theo pathname |
| Điều hướng đặc biệt | `/`, path không tồn tại | `/` qua auth guard rồi dashboard; catch-all trong shell admin |

Các con số route bị nhầm trong báo cáo survey sơ bộ đã được đối chiếu lại trực tiếp với `App` trước viết Spec.

## Evidence quan trọng

Các đường dẫn `src/` trong bảng thuộc `vehicletracking-frontend/`.

| Nhận định thực tế | Evidence | Ý nghĩa migration |
|---|---|---|
| React root tải global CSS theo thứ tự | `src/main.tsx:1`, `src/index.css:1`, `src/ui-refresh.css:1` | Giữ `#root`, font Inter/Leaflet CSS và cascade; không reset stylesheet |
| Theme nghiệp vụ opt-in, maps không nhận theme | `src/app/ApplicationShell.tsx:43`, `src/app/routeConfig.ts` `fullBleed` | Giữ `.business-ui`, data attributes và wrapper của cả hai kiểu shell |
| Auth app-wide hiện là Context; guard giữ pathname vào history state `from` | `src/auth/AuthContext.tsx:14`, `src/auth/RouteGuards.tsx:5`, `src/pages/LoginPage.tsx` `submit` | Dịch sang Vue store/guard; không tự đổi redirect sang full URL hoặc JWT |
| Services/types/utils không import React; metadata nav có import lucide-react | `src/services/http.ts`, `src/services/*`, `src/types/*`, `src/utils/*`; `src/app/routeConfig.ts:1` | Giữ lớp nghiệp vụ TS; đổi type/component icon của metadata |
| HTTP đính cookie và CSRF cho unsafe methods | `src/services/http.ts` `ensureCsrfToken`, `appFetch` | Không viết lại wrapper theo framework mới |
| Live snapshot HTTP + SSE, bỏ snapshot cũ, đồng hồ server và cleanup | `src/hooks/useLiveOperations.ts:5`; `src/services/operations.ts` `subscribeOperations` | Vue composable phải giữ sequence/staleness/reconnect/teardown |
| Fleet sở hữu list/filter/selection/mutation/request lifecycle | `src/hooks/useFleetWorkspace.ts:20`; `src/components/fleet/FleetWorkspace.tsx` | Port composable trước forms/detail; `lockedTab` phân biệt bảng management và cards map |
| Route editor có token + AbortController chống stale response | `src/components/route/RouteWorkspace.tsx` `RouteWorkspace`, request effects | Không watch sâu toàn object rồi gọi lại API tùy ý |
| Station CRUD/coordinate picker có state riêng | `src/hooks/useStationWorkspace.ts:11`; `src/components/MapComponent.tsx` station/draft effects | Giữ source of truth cho chọn trạm, form và marker |
| Map orchestrator giữ instance và nhiều LayerGroup | `src/components/MapComponent.tsx:75`, `:358` init effect, `:822` render | Port ownership/lifecycle, không thay engine map hoặc tạo lại instance theo mỗi snapshot |
| Basemap hiện là Google raster qua Leaflet, có theme/toggle traffic/tile retry | `src/components/MapComponent.tsx:541` basemap effect | Không suy luận từ tài liệu HERE cũ rằng runtime đang là HERE WebGL; giữ provider hiện tại |
| HERE traffic/ETA đi qua backend | `src/services/hereTraffic.ts` request functions; `src/hooks/useTraffic.ts` | Bảo toàn debounce/cache/abort; không đưa provider secret ra Vue |
| Camera/marker chạy imperative, dùng ResizeObserver/RAF | `src/hooks/useMapCamera.ts:5`; `src/hooks/useVehicleMarkers.ts:35` | `shallowRef`/raw objects và cleanup explicit; không deep proxy class instances |
| Layer tự tạo listener/RAF/timer | `src/components/{traffic/TrafficLayer,route/RouteInspectionLayer,route/RouteShapeEditor,operations/SimulationFleetLayer,operations/SimulationRoutesLayer}.tsx` | Composable/onUnmounted cho từng owner, gồm popup DOM listeners |
| Map/layers/list simulator có lazy import | `src/App.tsx:17`; `src/components/MapComponent.tsx:33`; `src/components/operations/SimulatorPanel.tsx:11` | Giữ lazy chunks và fallback; kiểm tra CSS sau tải chunk bất đồng bộ |
| Dashboard/alerts refresh 15 giây; ETA có request/timer riêng | `src/pages/{DashboardPage,AlertsManagementPage}.tsx` load effect; `src/hooks/useTripEta.ts` | Không nhân đôi polling sau route switch; preserve cadence |
| Dialog cần theme inheritance và trả focus | `src/components/business/SidePanel.tsx`; `src/components/fleet/FleetConfirmDialog.tsx` | Không Teleport ra khỏi ancestor `.business-ui` làm đổi màu; giữ Escape/busy/opener |

## Backend/deployment boundary (chỉ đọc)

| Nhận định | Evidence | Hệ quả |
|---|---|---|
| Session cookie, CSRF, ADMIN/DRIVER enforced ở backend | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/auth/config/SecurityConfig.java:68`, `:89`; `auth/controller/AuthController.java` | Guard Vue không thay bảo mật server; giữ endpoint/status/CSRF |
| Telemetry snapshot và SSE có endpoint cố định | Backend `telemetry/controller/TelemetryController.java:11`, `:29` | Không đổi payload/SSE event `snapshot` |
| Simulator play/pause/speed/stop/reset/attempts có controller riêng | Backend `simulation/controller/SimulationController.java:8` | Chỉ port caller UI, không thay scheduler/DTO nghiệp vụ |
| Frontend build npm ci → Vite dist, runtime Caddy | `vehicletracking-frontend/Dockerfile:1`; `Caddyfile:1`; `compose.production.yaml:46` | Giữ context/path/dist/API base, SPA fallback và SSE proxy `flush_interval -1` |
| CI chạy Node24, lint, tsc, build rồi deploy | `.github/workflows/ci-cd.yml` job `frontend`, step `Type check` | Đổi tsc sang Vue-aware script khi implement; không sửa job deploy/trigger hoặc tự deploy |
| React-specific config còn ở compiler/plugin/lint | `vite.config.js:1`, `tsconfig.json` `jsx`, `.oxlintrc.json` `plugins` | Không thể chỉ đổi component rồi coi toolchain hoàn tất |

Không cần migration/database survey sâu: schema không đổi, không có persistence mới; mọi thay đổi backend ngoài scope.

## Test hiện tại và khoảng trống

- `vehicletracking-frontend/tests/vehicleMotion.test.ts` dùng `node:test`; package.json chưa có script test chuẩn.
- `docs/features/031-business-interface-redesign/verification/ui-smoke.mjs` có Playwright fixtures/screenshots/overflow/focus và map CSS isolation. Đọc script chứng minh coverage, không lấy kết quả cũ làm bằng chứng Vue pass.
- Script 031 chặn cả fonts/provider ngoài origin, map fixtures rỗng và phụ thuộc Playwright cài ngoài package. Chưa đủ để chứng minh pixel parity hoặc chức năng maps khi đổi framework. Phải thêm baseline đủ dữ liệu, test dependency lock và visual assertions.
- `public/huong-dan/index.html`, `guide.css` là nội dung tĩnh: giữ nguyên, smoke link/path sau build.

## Working tree / rủi ro

`git status --short` trước survey có nhiều thay đổi frontend/backend, cùng các thư mục untracked của features 021–031. Không reset/stash/checkout ghi đè; baseline phải gồm cả untracked files cần thiết, không chỉ HEAD. Lượt này chỉ thêm docs feature 032, không chạy build/test/cài dependency hoặc sửa source.
