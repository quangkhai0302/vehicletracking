# Sửa hiển thị tuyến theo xe được chọn — 2026-09-15

## Khảo sát và kế hoạch rút gọn

- Working tree sạch trước khi sửa. Không thay đổi backend, API, database hay giao thông nền.
- `SimulationRoutesLayer` trước đây duyệt tất cả route, chỉ đổi màu route đang chọn.
- `MapComponent` trước đây lấy tuyến theo panel chi tiết chuyến, tuyến editor hoặc simulator, độc lập với marker xe được chọn.
- Yêu cầu: chỉ hiển thị tuyến xe được chọn trong theo dõi/mô phỏng, giữ marker các xe khác và xem trước tuyến trong quản lý.
- Kế hoạch: ràng buộc tuyến vào trip của xe chọn; hủy request cũ khi đổi/bỏ chọn; lọc lớp tuyến mô phỏng; kiểm tra cleanup và quản lý tuyến.

## Evidence source sau sửa

| Hành vi | Symbol/file |
|---|---|
| Chọn trip từ xe có telemetry hoặc xe chờ; editor chỉ hiện trong workspace routes | `MapComponent`: `selectedTripId`, `plannedRoute`, `selectedSimulationRoutes` |
| Chỉ tải chi tiết chuyến được chọn; abort request cũ, kiểm tra trip ID trước khi trả tuyến | `hooks/useSelectedVehicleRoute.ts` |
| Chặn vẽ các trip không được chọn, cleanup layer khi thay đổi selection | `SimulationRoutesLayer` effect |
| Mở panel chi tiết chuyến không tự thay đổi tuyến đang hiển thị | `FleetWorkspace` bỏ callback `onTripRoute` |
| Xe chờ có nút bỏ chọn trước khi phát sinh telemetry | `MapComponent`: `selectedWaitingVehicle` |

## Kiểm tra

Node 24.16.0, tại `vehicletracking-frontend`:

- `npm run lint`: exit 0.
- `./node_modules/.bin/tsc --noEmit`: exit 0.
- `npm run build`: exit 0; cảnh báo bundle chính lớn hơn 500 kB, không chặn build.
- `git diff --check`: exit 0.

Chrome headless, Vite localhost:5178, dữ liệu fixture hoàn toàn tách biệt:

```bash
node docs/features/006-telemetry-simulator/verification/selected-vehicle-route.mjs
```

Exit 0, không có pageerror. Đã kiểm tra:

1. Theo dõi: chưa chọn không có tuyến; chọn xe có tuyến và điểm dừng.
2. Đổi xe khi API trễ: tuyến và hit-layer cũ bị xóa ngay.
3. Bỏ chọn: response đến muộn không làm tuyến xuất hiện lại.
4. Mô phỏng: hai xe có telemetry và một xe chờ; không tự hiện toàn bộ tuyến.
5. Chuyển A → B chỉ còn tuyến B; bỏ chọn xóa đường và điểm dừng; marker cả hai xe vẫn còn.
6. Tắt/bật lớp tuyến chỉ khôi phục tuyến xe đang chọn.
7. Chọn/bỏ chọn xe chờ trước khi có telemetry.
8. Xem trước tuyến trong quản lý vẫn hoạt động; quay về theo dõi không làm rò tuyến editor.

Giới hạn: không gọi HERE hoặc backend thật; API phụ được giả lập unavailable; không kiểm thử tính đúng dữ liệu tuyến backend. Thư mục `docs/` hiện bị ignore bởi `.gitignore`; script và evidence này tồn tại cục bộ, chưa được stage/commit. Không thay đổi quy tắc ignore.

## Bổ sung: đồng bộ bảng chuyến đi và mô phỏng khi chọn xe

- Yêu cầu tiếp theo: chọn xe thì cả bảng chi tiết chuyến và mô phỏng chuyển tới chuyến của xe đó, không chỉ đổi đường trên bản đồ.
- Khảo sát: `useVehicleMarkers.onSelect` trước đây chỉ gọi `simulator.select` trong workspace simulation cho nguồn SIMULATOR. `FleetWorkspace` giữ lựa chọn riêng, không nhận sự kiện chọn xe.
- Kế hoạch rút gọn: một handler chọn xe chung, gửi yêu cầu mở chuyến cho fleet hook; dùng lại abort/token của `selectTrip`, mở hai bảng trên desktop; giữ cách chuyển bảng trên màn hình hẹp.
- `MapComponent.selectVehicleTrip`: cập nhật xe, yêu cầu mở chi tiết, simulator và trạng thái mở bảng; marker GPS cũng đi qua handler này. Chọn xe ở quản lý tuyến/trạm chuyển về theo dõi để hiện chi tiết.
- `useFleetWorkspace(tripSelection)`: nhận sự kiện chọn, tải đúng trip; cho phép quay về danh sách, rồi click cùng xe để mở lại. Nếu thao tác ghi đang chạy, chỉ xử lý lựa chọn mới nhất sau khi ghi xong.
- `clearVehicleSelection`: bỏ chọn cả tuyến lẫn hai bảng. Không dừng/chạy mô phỏng, không mutate backend.
- `SimulatorPanel` được key theo trip ID để hộp xác nhận cũ không áp dụng cho chuyến vừa chọn.
- Cùng script browser phía trên đã chạy lại, exit 0, không pageerror: cả hai bảng cùng ID với xe đang chọn (xe đang chạy, xe chờ, GPS), đổi xe khi tải chậm, bỏ chọn khi request chưa xong, chọn lại sau khi đóng chi tiết. Fixture theo dõi request xác nhận không phát sinh POST/PUT/DELETE.
- `npm run lint`: exit 0, một cảnh báo `react/set-state-in-effect` tại nhánh đồng bộ yêu cầu bỏ chọn vào màn hình CRUD độc lập trong `useFleetWorkspace`.
- `./node_modules/.bin/tsc --noEmit`: exit 0.
- `npm run build`: exit 0, vẫn cảnh báo bundle chính >500 kB.
- Không chạy backend test vì không đổi backend. Các thay đổi hiển thị tuyến của lượt trước được giữ nguyên.

## Bổ sung: bỏ lịch sử vị trí trong chi tiết chuyến

- Yêu cầu: không hiển thị lịch sử vị trí; giữ ghi nhận check-in qua trạm.
- Khảo sát/kế hoạch rút gọn: `TripDetailPanel` mount `TelemetryHistoryPanel` độc lập với `useTripCheckIns` và `VisitStatus`. Bỏ import và mount lịch sử, không thay đổi cơ chế check-in, ETA, lịch trình, database hay API telemetry.
- Source: `vehicletracking-frontend/src/components/fleet/TripDetailPanel.tsx`; giữ `trip-checkin-summary` và `VisitStatus` hiển thị giờ ghi nhận tại từng trạm.
- Browser fixture phía trên: exit 0. Đã phát một snapshot realtime mới với check-in, xác nhận số điểm dừng tăng 0 → 1/3 và thời điểm check-in xuất hiện trong timeline. Không còn vùng UI `Lịch sử vị trí` và không request `/telemetry/history`. Các kiểm tra chọn xe/đồng bộ hai bảng vẫn qua.
- `npm run lint`: exit 0, vẫn một cảnh báo state-in-effect từ lượt trước, không có cảnh báo mới.
- `./node_modules/.bin/tsc --noEmit`, `npm run build`, `git diff --check`: exit 0. Bundle chính còn 498.20 kB, lần build này không có cảnh báo >500 kB.
- Giới hạn: kiểm thử bằng dữ liệu và SSE giả lập, không khẳng định đã kiểm tra backend phát hiện check-in thật. Dữ liệu lịch sử backend và file component lịch sử vẫn được giữ, chỉ bỏ khỏi UI chi tiết chuyến.
