# Survey 022 — Business Management Shell

## Working tree

Feature 021 và cấu hình `.codex/` đang có thay đổi chưa commit. Đây là baseline của người dùng, phải bảo toàn. Survey dùng ba subagent chỉ-đọc; không agent nào sửa file.

## Luồng hiện tại

`main.tsx` → `App.tsx` → `MapComponent`. `MapComponent` sở hữu Leaflet, realtime, simulator, traffic, station/route layers, Fleet Workspace và toàn bộ drawer/panel. Navigation chỉ là state `WorkspaceMode`, không có URL.

## Evidence

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| App render thẳng bản đồ | `vehicletracking-frontend/src/App.tsx:1-5` | Cần thêm composition root `AppShell`. |
| Không có router | `vehicletracking-frontend/package.json:15-28`; `src/types/workspace.ts:1` | Cần dependency/router và URL contract. |
| MapComponent là root lớn sở hữu nhiều domain | `src/components/MapComponent.tsx:68-140,752-875` | Giữ nguyên làm Operations page là hướng ít hồi quy. |
| Fleet gộp xe/tài xế/chuyến | `src/components/fleet/FleetWorkspace.tsx:15-121`; `src/hooks/useFleetWorkspace.ts:15-53` | Cần prop page mode để tái sử dụng ở route riêng. |
| Route editor cần mapRef/callback layer | `src/components/route/RouteWorkspace.tsx:10-20,308-313` | Route page hiện vẫn phải là map workflow. |
| Station CRUD gắn picking/focus map | `src/hooks/useStationWorkspace.ts:50-84`; `MapComponent.tsx:341-469` | Station page hiện vẫn phải là map workflow. |
| Backend đủ CRUD xe/tài xế/chuyến | `VehicleController`, `DriverController`, `TripController` | Shell không cần đổi backend. |
| List API chưa pagination | controller/repository vehicle, driver, route, station, trip | Chỉ dùng client filter quy mô hiện tại. |
| Không có auth/RBAC | `vehicletracking-backend/pom.xml:37-89`; không có SecurityFilterChain | Không giả permission bằng menu. |
| Không có scheduler/report/off-route model | không có migration/entity/service tương ứng | Dùng roadmap pages, không giả số liệu. |
| Caddy hỗ trợ deep link | `vehicletracking-frontend/Caddyfile:14-18` | BrowserRouter khả thi production. |
| API và SSE cùng origin production | `Caddyfile:4-9`; `compose.production.yaml:45-65` | Không đổi API base URL. |
| CI dùng Node 24 và build frontend | `.github/workflows/ci-cd.yml:28-47` | Verification giữ toolchain hiện tại. |

## Rủi ro hồi quy

- Mount Fleet hook trên nhiều page có thể fetch cả ba collection; chấp nhận trong bước shell, tối ưu query ownership sau.
- Map state mất khi rời Operations vì component unmount; đây là navigation page bình thường, nhưng form dirty cần confirmation nội bộ trước khi đi.
- CSS Fleet/Route/Station giả định drawer cố định; phải scope page layout và override chiều cao, không refactor diện rộng.
- Route/Station không thể đưa sang page không map mà không tách layer/picker lớn; feature giữ map workflow.
- Dashboard không được suy diễn delayed/off-route/speed metrics từ dữ liệu chưa có.
