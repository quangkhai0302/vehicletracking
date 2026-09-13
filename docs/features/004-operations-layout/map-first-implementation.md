# Triển khai Map-First — revision 004

Được người dùng chấp thuận triển khai sau `map-first-design.md`. `docs/workflow.md` vẫn không có trong checkout (đã kiểm tra inventory); áp dụng `AGENTS.md` và hồ sơ 004 đã có. Không đổi ID nghiệp vụ 005–009.

## Phạm vi nghiệm thu lượt này

1. Thay shell dashboard bằng bản đồ toàn viewport, dark mode, ModeBar, ContextDrawer, SimulatorPanel/AlertStream và map toolbar; panel thu/mở, camera tính vùng bị che.
2. Giữ một Leaflet instance qua đổi mode. Danh sách, station draft, route draft và route selection giữ qua đổi tab/collapse. Station CRUD tiếp tục gọi services hiện có.
3. Kéo thả stop bằng handle; hỗ trợ Space/Enter để nhấc/thả, mũi tên đổi thứ tự, Escape hủy; nút Lên/Xuống vẫn tồn tại. Occurrence ID ổn định, role/dwell đầu/cuối đúng, marker nháp đồng bộ.
4. Tạo tuyến vẫn theo API POST hiện có; chỉ tính/lưu khi người dùng submit. Snapshot ghi rõ thời điểm tính. Không thêm engine/traffic/ETA/check-in/reroute/notification backend trong lượt UI.
5. Các panel thiếu nguồn dữ liệu có empty/unavailable và control bị vô hiệu hóa cùng lý do. Không seed hoặc nối fixture vào runtime ứng dụng.
6. Tablet/mobile chỉ mở một panel, map luôn còn phía dưới; chọn tọa độ thu panel và quay lại form. Focus/tab order không đi vào panel ẩn, có xử lý draft khi đóng.
7. Chạy lint/tsc/build, browser fixture cho CRUD/route/reorder/race/viewport/camera/state retention. Báo cáo và handoff cập nhật sau kiểm chứng.

## Cơ sở source và thứ tự làm

- `App.tsx`/`workspace.css`: thay shell, không thêm stylesheet override thứ ba.
- `MapComponent.tsx`: tách workflow station và camera/occlusion, giữ lifecycle map/layer; `vehicles=[]` vẫn chưa có source.
- `StationDrawer`/`StationPanel`/`services/stations.ts`: giữ hợp đồng CRUD.
- `RouteWorkspace`/`RouteDrawer`: giữ request token + AbortController; bổ sung draft preview và reorder.
- Backend `RouteController` chỉ GET/POST; `V3__create_routes_tables.sql` lưu snapshot bất biến; `RouteServiceTest.create_normalizesNameAndPreservesStopOrderAndCalculatesMetrics` kiểm tra offset/travel/dwell. Các tên backend thuộc package `com.quangkhai.vehicletracking_backend.route`.

Thứ tự: tách controller UI/camera → shell/panel → reorder/draft preview → dark styles/responsive → kiểm chứng và cập nhật kết quả. Mọi thay đổi có trước lượt được giữ, không commit/push.

## Kết quả triển khai

**Hoàn thành phần UI được duyệt.** Dashboard hai cột đã được thay trong ứng dụng React thật; prototype SVG vẫn là tài liệu minh họa riêng. Không thay backend/API/migration hoặc package/lockfile ứng dụng.

| Thành phần hiện có | Trách nhiệm và evidence source |
| --- | --- |
| `App` → `MapComponent` | Shell map toàn viewport; một Leaflet instance, lifecycle tile/layer/listener; overlay độc lập map. `src/components/MapComponent.tsx#MapComponent`, `src/workspace.css#.map-first` |
| `ModeBar` | Ba chế độ; nút có trạng thái chọn, thông tin nguồn dữ liệu. `src/components/operations/ModeBar.tsx#ModeBar` |
| Context drawer | Tracking hoặc tab station/route; giữ form/list/detail được mount bằng `hidden` khi chuyển mode. `MapComponent` / `.context-content`, `RouteWorkspace` / `.panel-list-slot` |
| `useStationWorkspace` | Tải/CRUD/selection/form trạm; API ở services. `src/hooks/useStationWorkspace.ts#handleSaveStation`, `handleDeactivate` |
| `useMapCamera` | Đo overlay thật, fit/pan theo phần nhìn được, release focus khi kéo map, cleanup observer/RAF. `src/hooks/useMapCamera.ts#useMapCamera` |
| `SortableStopList` | Native drag handle; Space/Enter + ↑/↓/Esc; nút lên/xuống/xóa; live announcement; occurrence UUID. `src/components/route/SortableStopList.tsx#SortableStopList`, `src/types/route.ts#RouteDraftStop` |
| Route editor/detail | Normalize role/dwell đầu/cuối; cấm trùng liên tiếp/trạm inactive; marker nháp; POST tính/lưu; timeline snapshot và nút định vị từng trạm. `RouteDrawer.tsx#normalizeStops`, `RouteCreateContent`, `RouteViewContent`; `RouteWorkspace.tsx#handleSaveRoute` |
| `SimulatorPanel`, `AlertStream` | Hai panel nổi thật, trạng thái chưa kết nối; telemetry —, control disabled và lý do hiển thị. `src/components/operations/SimulatorPanel.tsx#SimulatorPanel`, `AlertStream.tsx#AlertStream` |
| `MapControls` | Zoom, fit, 3 basemap; bật/tắt trạm/tuyến; traffic disabled. `src/components/MapControls.tsx#MapControls` |
| `ConfirmStationDelete` | Native dialog giữ focus, Escape/hủy, khóa khi đang ghi. `src/components/operations/ConfirmStationDelete.tsx#ConfirmStationDelete` |

Các đường dẫn `src/` trong bảng thuộc `vehicletracking-frontend/`. Các layer traffic, incidents, nguồn xe và stream cảnh báo trong kiến trúc đích chưa được dựng thành abstraction khi chưa có nghiệp vụ.

## Luồng và điều chỉnh theo API hiện có

1. **Trạm:** mở tab Trạm dừng → thêm/sửa → chọn vị trí trên map (panel thu) → quay lại form → lưu qua API. Có geofence nháp, dirty confirmation và xác nhận ngừng sử dụng.
2. **Tuyến:** tạo bản nháp → chọn/sắp thứ tự stops → marker nháp đồng bộ → “Tính & lưu tuyến mới” → HERE qua backend → snapshot/detail/polyline. Không tự gửi POST mỗi lần kéo; không suy ra ETA từ đường thẳng giữa trạm.
3. **Đổi ngữ cảnh:** đổi mode/tab hoặc thu panel giữ bản nháp; chỉ hỏi bỏ thay đổi khi đóng/hủy form. Mở lại có thể tiếp tục nhập, không tạo thêm route ngoài ý muốn.
4. **Bản đồ:** fit tuyến/các trạm tính vùng panel che; click tên stop trong timeline hoặc nút định vị stop nháp để focus. Vai trò đầu/cuối dùng cyan/indigo, không giả badge đã check-in.
5. **Thiết bị nhỏ:** 900–1279 px một cột nổi; dưới 900 px hoặc chiều cao ≤650 px một sheet. Quick view khoảng 43%; mở rộng tối đa 75% nhưng chừa ít nhất 272 px cho map/thanh công cụ/nút mở panel. Landscape ≤480 px cao dùng panel bên trái để giữ diện tích bản đồ. Map picking dùng banner gọn, đặt xong khôi phục form.

## Kiểm chứng sau cùng — 2026-09-13

| Working directory | Lệnh | Kết quả |
| --- | --- | --- |
| `vehicletracking-frontend` | `npm.cmd run lint` | Exit 0, không cảnh báo |
| `vehicletracking-frontend` | `.\node_modules\.bin\tsc.cmd --noEmit` | Exit 0 |
| `vehicletracking-frontend` | `npm.cmd run build` | Exit 0, Vite 8.2.2, 1872 modules; JS 424.44 kB / gzip 127.14 kB |
| root | `node docs/features/004-operations-layout/verification/map-first-app-smoke.mjs` | Exit 0, 19 nhóm kiểm tra, 0 page errors; Edge headless |

Browser script: [map-first-app-smoke.mjs](verification/map-first-app-smoke.mjs). Kết quả có timestamp: [results.json](artifacts/map-first-app/results.json).

Kiểm tra bao gồm station CRUD, tìm kiếm/nháp giữ qua chuyển mode, xác nhận bỏ form và Escape dialog; cùng Leaflet instance/camera/tuyến sau đổi mode; native kéo thả, keyboard/drop/Escape, buttons, endpoint dwell, duplicate validation, marker nháp; POST thất bại → giữ form → retry, đổi mode khi POST chậm; GET chi tiết cũ không đè lựa chọn mới; polyline lỗi bị loại toàn bộ. Fit được đo với bounding box marker và panel/control, gồm cả mobile mở rộng.

Viewport đã kiểm tra: 1440×900, 1024×900, 768×900, 390×844, 320×720, 844×390; native drag qua bốn stops dùng 1440×1200 để source/target cùng nằm trong vùng cuộn hiển thị. Mobile kiểm tra thao tác nút/keyboard bằng browser; chưa kiểm chứng cử chỉ trên thiết bị cảm ứng vật lý.

Đã sửa các vấn đề tìm qua browser: toolbar che launcher cảnh báo ở tablet; nav tràn 320 px; banner chọn tọa độ bị kéo cao bởi rule CSS cũ; marker bị toolbar che khi sheet mở rộng. Các case này đã được chạy lại trong script trên.

Ảnh ứng dụng (dữ liệu API là fixture):

- [Desktop tuyến đã tính](artifacts/map-first-app/desktop-route-fixture.png)
- [Desktop editor](artifacts/map-first-app/desktop-route-editor-fixture.png)
- [Mobile editor](artifacts/map-first-app/mobile-route-editor-fixture.png)
- [Mobile mô phỏng chưa kết nối](artifacts/map-first-app/simulator-320-fixture.png)

## Giới hạn và bàn giao

- Mọi `/api/` trong browser verification bị intercept; không ghi dữ liệu vào database thật. Map tiles sử dụng nền sẵn có của ứng dụng. Kết quả không chứng minh HERE live, GPS thật hoặc backend integration.
- Backend không đổi, không chạy lại Maven trong revision UI. Kết quả Docker/Testcontainers của lượt khảo sát trước nằm ở [verification.md](verification.md); không coi các test repository đó đã pass.
- `MapComponent` vẫn khởi tạo `vehicles=[]`. Simulator engine, traffic, ETA động, check-in, reroute và sự kiện realtime thuộc kế hoạch 005–009 trong [PROJECT_PROGRESS](../../PROJECT_PROGRESS.md). Các nút disabled có thể nối vào các feature đó sau này.
- Tài liệu/ảnh/script trong `docs/` vẫn bị Git ignore theo cấu hình có sẵn. Source mới trong `src/hooks`, `src/components/operations`, `SortableStopList.tsx` và `workspace.css` hiện chưa được Git stage. Không commit/push hoặc đọc/đổi secret.
