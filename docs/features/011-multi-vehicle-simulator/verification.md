# Verification 011 — Đội xe mô phỏng

## Sửa lỗi chỉ hiển thị một tuyến — 2026-09-15

**Đã sửa:** trong Mô phỏng, mọi chuyến trong đội đều có polyline riêng trước/sau khi chọn xe. Trước đó `MapComponent#plannedRoute` và effect Render Planned Route chỉ lấy tuyến từ `simulator.detail`, còn `useSimulationFleet` chỉ tải tọa độ xe chờ. Kiểm thử cũ xác nhận hai run/marker nhưng chưa xác nhận hai hình học khác nhau; do đó kết quả cũ chưa bao phủ lỗi người dùng gặp.

- `src/hooks/useSimulationFleet.ts#useSimulationFleet/prepareDetail` nay tải TripDetail cho tất cả chuyến trong đội (key tripId:routeId, tối đa 4 GET đồng thời), cache, abort, retry. Decode đầy đủ từng tuyến; lỗi HTTP/hình học của một chuyến không xóa tuyến khác hoặc điểm chờ hợp lệ. Không gọi HERE routing để tính lại tuyến.
- `src/components/operations/SimulationRoutesLayer.tsx#SimulationRoutesLayer` vẽ từng tuyến bằng SVG trong pane riêng; đường cyan/dày là xe đang chọn, màu khác ổn định theo vehicleId. Tooltip chỉ nhận diện xe/chuyến/tên tuyến. Click tuyến chọn xe; đoạn đi chung mở popup chọn từng xe; Enter/Space và danh sách cũng chọn được. Không thay tọa độ để tách đoạn trùng. Signature không phụ thuộc tick telemetry nên không tạo lại path/request mỗi SSE; cleanup popup/listeners/renderer khi tắt lớp/rời mode.
- `MapComponent#fleetPoints/fitSimulationFleet/handleFit` tính bounds từ tất cả sections và vị trí xe. Layer cũ chỉ vẽ trạm đánh số của chuyến đang chọn trong Mô phỏng; không còn xóa tuyến khác hay tự fit lại một tuyến khi đổi selection. `RouteInspectionLayer` giữ cho tracking/editor; trong Mô phỏng click tuyến mở điều khiển xe và thẻ vận tốc/ETA. `mapSnapshot` chỉ giữ SIMULATOR positions thuộc đội hiện hành, tránh marker chuyến đã kết thúc không có tuyến; dữ liệu GPS và lịch sử trong Theo dõi vẫn giữ.
- `SimulationFleetList` bổ sung chú giải, swatch theo xe, loading và lỗi/retry tuyến ngay cả khi danh sách xe thu gọn. Các nút play/pause/speed vẫn theo tripId như trước.

### Kiểm tra đã chạy trong lượt sửa

| Lệnh | Kết quả |
| --- | --- |
| `node docs/features/011-multi-vehicle-simulator/verification/routes-browser.mjs` | **10 nhóm pass**, pageErrors []; ba tuyến, hai xe chạy đồng thời trên hai tuyến rẽ khác hướng sau đoạn chung; fit/click/keyboard/overlap, đổi selection, SSE không rebuild/request lại, reload, toggle/cleanup, lỗi HTTP/polyline/retry, mobile, hoàn thành xóa đúng tuyến/marker và giữ lịch sử ở Theo dõi |
| `node docs/features/011-multi-vehicle-simulator/verification/browser.mjs` | **9 nhóm pass** hồi quy điều khiển nhiều xe, pause/speed riêng, xe chờ cùng trạm và mobile |
| `node --experimental-strip-types --test docs/features/011-multi-vehicle-simulator/verification/fleet.test.mjs` | **7 pass**, 0 fail |
| `node docs/features/010-route-traffic-tooltip/verification/tracking-browser.mjs` | **12 nhóm pass**, pageErrors []; hồi quy speed/ETA/giao thông/blocked/GPS/follow/đổi chuyến |
| `npm.cmd run lint`, `.\node_modules\.bin\tsc.cmd --noEmit`, `npm.cmd run build` (trong frontend) | Exit 0 cả ba; build 1913 modules; main **501.00 kB**, có cảnh báo vượt ngưỡng 500 kB, layer tuyến tải lazy 2.80 kB; không coi cảnh báo này là lỗi build |
| `node docs/features/011-multi-vehicle-simulator/verification/live-readonly.mjs` | Pass trên Spring/Vite thật sau khi khởi động lại; GET-only, pageErrors/blockedWrites []; 1 chuyến đủ điều kiện, 1 tuyến, 1 marker |

Evidence mới: [JSON kiểm tra tuyến](artifacts/routes/results.json), [hai xe đang chạy trên hai tuyến khác nhau](artifacts/routes/two-running-routes.png), [mobile](artifacts/routes/mobile-routes.png), [GET-only dữ liệu thật](artifacts/live-readonly/results.json). Fixture có tuyến đi chung một đoạn rồi rẽ khác hướng; đây là API/SSE bộ nhớ riêng, không ghi database. Bản đồ nền ngoài có thể không tải trong sandbox; kiểm thử xác minh geometry và tương tác của layer ứng dụng, không nghiệm thu tile provider/load lớn/HERE live đa xe.

Dữ liệu thật đầu lượt: GET snapshot trả #2 và #3 đều RUNNING, phù hợp ảnh người dùng. Khi kiểm tra cuối, #3 đã COMPLETED, #2 IN_PROGRESS với run PAUSED. Vì thế hiện chỉ còn tuyến #2 trong đội mô phỏng; không tự chạy lại chuyến #3 hoặc gửi play cho #2 để tạo bằng chứng giả. Muốn chạy lại cả hai: Tiếp tục #2 và dùng luồng Chạy lại của chuyến đã kết thúc (hoặc tạo chuyến chờ mới) cho xe còn lại.

Môi trường: frontend/backend cũ ngừng phản hồi giữa lượt; đã chạy Vite cổng 5173 và backend cổng 8080. `mvnw.cmd spring-boot:run` lỗi wrapper `Cannot index into a null array`; dùng Maven có sẵn. Lần khởi động đầu lỗi PostgreSQL timezone `Asia/Saigon`; chạy với JVM `-Duser.timezone=UTC` thành công. Không sửa `.env`, backend source hoặc migration. Lệnh cơ bản tái chạy: `mvn.cmd spring-boot:run '-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC'`; không chạy Maven test vì sửa frontend. Không sửa dữ liệu chuyến qua API để kiểm tra. Thay đổi 011 và hồ sơ mới vẫn nằm working tree/local ignore, chưa commit/push.

## Mốc triển khai ban đầu — 2026-09-14

Triển khai frontend trong working tree ngày 2026-09-14. Backend đã có run/clock theo trip; không sửa backend/schema/.env, không tạo xe/chuyến thật, không commit/push. Tài liệu 011 mới vẫn local theo `.gitignore` của repository.

## Kết quả đã chạy

| Lệnh từ root (trừ kiểm tra frontend) | Kết quả |
| --- | --- |
| `node --experimental-strip-types --test docs/features/011-multi-vehicle-simulator/verification/fleet.test.mjs` | 7 pass, 0 fail |
| `node docs/features/011-multi-vehicle-simulator/verification/browser.mjs` | 9 nhóm pass, pageErrors [], 3 xe/2 trạm, 2 xe chạy đồng thời |
| `node docs/features/010-route-traffic-tooltip/verification/tracking-browser.mjs` | Hồi quy 12 nhóm pass; preview trước play thay ETA chờ từ 010; ETA sau play vẫn kiểm tra traffic/stale/blocked/race |
| `$env:VERIFICATION_API=$null; $env:VERIFICATION_TRIP=$null; node docs/features/006-telemetry-simulator/verification/live-browser.mjs` | Hồi quy 10 nhóm lifecycle hai tab pass, pageErrors [] |
| `cd vehicletracking-frontend; npm.cmd run lint` | Exit 0 |
| `cd vehicletracking-frontend; .\node_modules\.bin\tsc.cmd --noEmit` | Exit 0 |
| `cd vehicletracking-frontend; npm.cmd run build` | Exit 0; 1912 modules; main 499.85 kB; layer và danh sách đội xe tải riêng; không cảnh báo chunk >500 kB |

Evidence: [kết quả đa xe](artifacts/results.json), [xe chờ](artifacts/waiting-desktop.png), [xe đang chạy](artifacts/running-desktop.png), [mobile 320 px](artifacts/mobile-320.png). Browser chạy Edge headless qua Playwright hiện có của 004; API/SSE fixture có HTTP server riêng, không dùng database/HERE thật. Commands trong results chứng minh play A/play B/pause A/speed A/play A; không có POST khi chỉ mở hoặc chọn xe.

Smoke GET-only ở mốc ban đầu ghi nhận app thật có 0 chuyến đủ điều kiện chờ/đang mô phỏng, 1 marker lịch sử. Các artifact `live-readonly` đã được thay bằng kết quả mới ngày 2026-09-15 ở mục trên; không dùng chúng để chứng minh lại số liệu cũ. Không thêm dữ liệu demo vào database.

## Đối chiếu acceptance criteria

- AC1/4: `src/utils/simulationFleet.ts#simulationFleetTrips/waitingSimulationTrips` chọn một chuyến mỗi vehicle, active ưu tiên, chờ chọn lịch sớm nhất, bỏ completed/cancelled, tránh chiếm xe đang dùng GPS. `src/hooks/useSimulationFleet.ts#useSimulationFleet` lấy trạm đầu từ `fetchTrip`, giới hạn 4 GET đồng thời, cache theo trip, abort và retry. Không tạo telemetry/check-in trước play.
- AC2: `src/components/operations/SimulationFleetLayer.tsx#SimulationFleetLayer` nhóm xe chờ theo đúng tọa độ; popup DOM textContent chống HTML injection, chọn từng tripId. `SimulationFleetList` có tổng số xe/đang chạy, danh sách chọn, Xem tất cả và quản lý chuyến; thu danh sách khi đã chọn xe để ưu tiên điều khiển.
- AC3/4: `MapComponent#selectSimulationVehicle` nối click marker/list với `useSimulator.select`, tuyến và panel; `useVehicleMarkers` khi mode mô phỏng chọn trip theo vehicle. Các xe đang chạy gần nhau có popup chọn xe, không dịch tọa độ. Command vẫn `/trips/{id}/simulation/{action}`. Script browser xác minh pause/multiplier A không thay B và reload nhận lại hai runs.
- AC5: `MapComponent#mapSnapshot` ẩn vị trí lịch sử của xe đang có preview trong chế độ mô phỏng, không trộn tọa độ preview vào telemetry. Khi run/telemetry xuất hiện, preview biến mất. `SimulationFleetLayer` cleanup layer/listeners và `useVehicleMarkers` cleanup popup khi rời mode. Đổi mode không gọi stop/pause.
- AC6: hook có lỗi/retry theo chuyến, list có empty/loading, marker/popup/list keyboard được; mobile 320 px chọn xe và mở play được. `SimulatorPanel` hiện 0 km/h + trạm đầu cho SCHEDULED chưa có run; không mount ETA card trong lúc tải trạm đầu, tránh query ETA sớm. Khi có run quay về `TripTrafficSummary` 010. `Xem vị trí xe...` định vị preview hoặc telemetry tùy trạng thái.

Đường dẫn source trên tính trong `vehicletracking-frontend/`. Backend evidence: `SimulationScheduler#tick`, `SimulationService#activeTripIds/play/pause/speed`, `OperationsSnapshotService#snapshot`, `V4#uq_trips_running_vehicle`, `V5#simulation_runs`. Không chạy Maven vì không thay backend. Chưa có bằng chứng load lớn/HERE với nhiều xe thật; scheduler đang lặp tuần tự từng chuyến.

## Cách dùng

1. Trong Đội xe, tạo các xe khác nhau và chuyến ở trạng thái chờ, gán tuyến/giờ xuất phát cho từng xe.
2. Mở Mô phỏng: xe chờ hiện tại trạm đầu, điểm có nhiều xe có badge số lượng. Đây là vị trí chờ giả lập theo lịch, không phải GPS.
3. Bấm xe (hoặc popup/danh sách) → Bắt đầu. Chọn xe khác → Bắt đầu để chạy cùng lúc. Pause/Continue/1×/5×/10× áp dụng riêng xe đang chọn.
4. Chọn xe khác hoặc rời mode không dừng các xe đang chạy. Một xe có nhiều chuyến vẫn chỉ thực hiện một chuyến tại một thời điểm; cần kết thúc/hủy chuyến cũ trước khi chạy chuyến kế tiếp.

Không tự hồi sinh các chuyến COMPLETED thành xe chờ: dùng Chạy lại (tạo chuyến mới có xác nhận) hoặc tạo chuyến chờ mới. Cấu hình ở phạm vi này là chọn chuyến đã gán xe/tuyến/lịch và tốc độ phát; chưa thêm nhập vận tốc km/h tùy ý.
