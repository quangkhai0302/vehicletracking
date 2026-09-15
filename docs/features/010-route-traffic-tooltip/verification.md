# Verification 010 — Route traffic tooltip

> Sau feature 011, chuyến SCHEDULED chưa run hiển thị xe chờ 0 km/h tại trạm đầu, bắt đầu xong mới mở ETA card. Script tracking đã cập nhật nghiệm thu trạng thái chờ; các kết quả/kích thước bundle ở dưới là mốc 010. Trạng thái mới và kiểm tra đa xe tại [verification 011](../011-multi-vehicle-simulator/verification.md).

Trạng thái ngày 2026-09-14: đã chỉnh theo yêu cầu cuối — tập trung **vị trí xe, vận tốc và ETA tới trạm**. Đã gỡ lớp hover mọi đường nền và request flow theo viewport; giữ tooltip tuyến xanh đã có. Không sửa backend, migration hoặc `.env` trong lượt chỉnh này.

## Nghiệm thu phần theo dõi xe

| Lệnh | Kết quả thực tế |
| --- | --- |
| `node --experimental-strip-types --test docs/features/010-route-traffic-tooltip/verification/trip-traffic.test.mjs` | 9 pass, 0 fail |
| `node docs/features/010-route-traffic-tooltip/verification/tracking-browser.mjs` | 12 nhóm pass; `pageErrors: []`, 0 request flow; thêm đổi chuyến khi ETA cũ trả chậm, bỏ chọn dừng polling, marker nhận tương tác khi mở thẻ desktop/mobile; kết quả mới trong JSON bên dưới |
| `$env:VERIFICATION_API = $null; $env:VERIFICATION_TRIP = $null; node docs/features/006-telemetry-simulator/verification/live-browser.mjs` | 10 nhóm lifecycle fixture pass, `pageErrors: []`; hai tab/pause/continue/5×/10×/reset/mobile/409/reconnect; kết thúc 23:24:59 +07 |
| `node docs/features/010-route-traffic-tooltip/verification/tracking-live-readonly.mjs` | Exit 0; marker xe và speed/ETA hiển thị; không có hit path đường nền hoặc request flow theo viewport; API ETA HTTP 200 |
| `cd vehicletracking-frontend; npm.cmd run lint` | Exit 0 |
| `cd vehicletracking-frontend; .\node_modules\.bin\tsc.cmd --noEmit` | Exit 0 |
| `cd vehicletracking-frontend; npm.cmd run build` | Exit 0, 1908 modules; main JS 494.24 kB, route tooltip tải riêng 13.12 kB |

Evidence: [tracking results](artifacts/vehicle-tracking/results.json), [thẻ simulator](artifacts/vehicle-tracking/simulator-desktop.png), [thẻ xe mobile](artifacts/vehicle-tracking/vehicle-mobile.png), [GET-only ứng dụng thật](artifacts/vehicle-tracking-live/results.json), [ảnh ứng dụng thật](artifacts/vehicle-tracking-live/simulator.png).

Hồi quy lifecycle: [results 006](../006-telemetry-simulator/artifacts/fixture/results.json). Script 006 cập nhật selector sau pause sang nhãn hiện tại “Tiếp tục mô phỏng” (lượt đầu bị timeout vì vẫn tìm “Bắt đầu mô phỏng”). Chạy bằng fixture riêng sau khi bỏ biến `VERIFICATION_API`/`VERIFICATION_TRIP` trong tiến trình shell; không gửi lệnh điều khiển tới backend thật.

Browser fixture dùng HTTP/SSE server riêng từ verification 006 và mock endpoint ETA; API provider/font/tile bên ngoài bị chặn. Xác nhận vị trí thay đổi, speed frame và GPS tách riêng, ETA 10→20 phút khi response congestion đổi, tên tai nạn/thi công, blocked không ra countdown, stale/fallback/error/retry, follow, thẻ mobile và đóng chọn xe. Fixture không chứng minh thuật toán backend tính đúng mọi tình huống giao thông; không ghi dữ liệu ứng dụng thật.

GET-only trên Spring/Vite hiện chạy: có một marker, chuyến hiện có đã kết thúc, `nextStopSequence: null`, nguồn `ROUTE_SNAPSHOT`. Không bấm bắt đầu/dừng hoặc sửa chuyến thật; **chưa kiểm thử một chuyến mới chạy qua sự cố HERE thật** trong lượt này. Kết quả hover road HERE trước đó ở `artifacts/live-readonly` là mốc lịch sử, không phải UI hiện tại.

Evidence source cho phạm vi điều chỉnh (đường dẫn trong `vehicletracking-frontend/src`):

- A: `MapComponent` chỉ mount `TrafficLayer` và `RouteInspectionLayer`; `useTraffic` chỉ query incidents cho viewport. Đã bỏ `RoadInspectionLayer`.
- B/E: `components/operations/TripTrafficSummary.tsx#TripTrafficSummary` hiện tốc độ theo context simulator/GPS, next stop, nguồn, thời gian cập nhật; dùng trong `SimulatorPanel` trước playback và `.live-follow` khi chọn xe. `MapComponent#onShowRoute` bật follow; marker vẫn do `useVehicleMarkers` cập nhật từ snapshot.
- B/E (camera): wrapper `.live-follow` trong `MapComponent` giữ mounted và đánh dấu `data-map-edge="top"` để `useMapCamera`/ResizeObserver tính vùng thẻ che; `simulator.css` giới hạn chiều cao mobile 45dvh. Khi chọn xe, đổi kích thước hoặc mở chi tiết tọa độ, camera chừa khoảng nhìn xe. Browser kiểm tra `elementFromPoint` tại marker trên desktop và 320 px, kể cả khi disclosure đang mở. Đây là sửa lỗi marker bị thẻ che phát hiện trong nghiệm thu.
- C: `utils/tripTraffic.ts#tripTrafficView` hiển thị các ảnh hưởng từ `TripEta.affectedSegments`, bỏ phần đã đi và gộp loại trùng; không suy ra tai nạn chỉ từ tốc độ chậm. Trễ hiển thị cho phần tuyến còn lại, không gán là trễ riêng trạm kế tiếp.
- D: `tripTrafficView` giữ countdown/trạm/nguồn cùng response; blocked thắng fallback, metadata unavailable không đè blocked ETA, dữ liệu mới có thể phục hồi từ blocked; completed không dùng frame countdown cũ. `TripTrafficSummary` ghi dữ liệu gần nhất/lỗi/ROUTE_SNAPSHOT, dùng tuổi theo observedAt/fetchedAt, chỉ query trip được chọn qua `useTripEta`.

Backend dùng lại: `SimulationService#tick/describe` đã nhân tốc độ/progress với traffic rate; `TrafficEtaService#calculate` trả ETA/affectedSegments. Không sửa hay chạy Maven ở lượt chỉnh frontend. Giới hạn đọc source: ETA engine hiện chọn flow matching đầu tiên của từng section; chưa nghiệm thu tích hợp đầy đủ của engine/reroute. `SimulationService#trafficMetadata` có fallback UNAVAILABLE khi gặp ETA null; unit mới bảo đảm frontend không dùng fallback đó đè dữ liệu blocked từ endpoint ETA.

## Hồi quy tooltip tuyến đã chọn

| Lệnh | Kết quả thực tế |
| --- | --- |
| `node --experimental-strip-types --test docs/features/010-route-traffic-tooltip/verification/geometry.test.mjs` | 10 test pass, 0 fail |
| `node docs/features/010-route-traffic-tooltip/verification/browser.mjs` | 12 nhóm pass, `pageErrors: []`, 13 request flow tại vị trí hover tuyến trong toàn bộ các ca; kết thúc 2026-09-14 23:14:55 +07 |

Browser chạy Microsoft Edge headless với Playwright sẵn có tại verification 004, Node 22.20.0; Vite phục vụ ở `http://127.0.0.1:5173`. Mọi `/api/` được intercept bằng fixture, request tới provider/font/tile bên ngoài bị chặn; không ghi dữ liệu ứng dụng. Không chạy Maven vì không thay backend.

Evidence: [results.json](artifacts/results.json), [ảnh desktop](artifacts/desktop-fixture.png), [ảnh 320 px](artifacts/mobile-320-fixture.png), [browser script](verification/browser.mjs), [test hình học](verification/geometry.test.mjs). Chuỗi `<img ...>` xuất hiện dạng chữ trong ảnh là fixture kiểm tra escape, không phải HTML thực thi. `failure.png` nếu còn là artifact của lượt lỗi cũ, không thay kết quả cuối trong results.json.

## Acceptance criteria và source

- AC1: `components/route/RouteInspectionLayer.tsx` hiển thị tên tuyến/chặng theo stop sequence, metrics section và `calculatedAt` từ snapshot; riêng metrics traffic có phạm vi đoạn tương ứng. Không đổi thời gian snapshot thành ETA live.
- AC2: `hooks/useRouteTraffic.ts#useRouteTraffic` dùng service sẵn có, debounce 200 ms, reuse ô bbox 0.01 độ, cache frontend tối đa 30 ô trong 60 giây, refresh khi card mở. Abort + cờ alive và key kiểm soát response cũ; failure thay dữ liệu cũ bằng trạng thái lỗi. Browser xác nhận cùng ô không gọi lại và response trễ không thay chặng mới.
- AC3: `utils/routeInspection.ts#project/matchFlow` dùng tangent cục bộ, khoảng cách tối đa 18 m, lệch hướng tối đa 30 độ, bỏ flow không đáng tin hoặc geometry lỗi; không chọn khi hai candidate khác ID có khoảng cách chênh dưới 3 m. Unit test gồm cong/ngược chiều/vuông góc/xa/song song mơ hồ. Đây là policy tooltip, không dùng biến corridor của ETA backend.
- AC4: `segmentMetrics` tính thời gian trên chiều dài flow / speed và chậm thêm so với free flow; zero speed/closure cho thời gian chưa xác định/bị chặn. `types/map.ts#TrafficFlowSegment.lengthMeters` optional khớp field backend, fallback geometry cho response cũ thiếu field. Không cần đổi API/schema.
- AC5: `TrafficSourceLine`, `trafficAge`, `usableTraffic`, `nearbyIncidents` trình bày nguồn/tuổi flow và incidents riêng. Sự cố phải còn hiệu lực và cách vị trí không quá 50 m; UI không khẳng định chiều ảnh hưởng của sự cố chỉ từ độ gần. Browser kiểm tra stale, empty, opposite, lỗi/retry; unit kiểm tra thời gian hiệu lực và tuổi dữ liệu.
- AC6: `RouteInspectionLayer` tạo hit paths SVG, hỗ trợ hover/click/tap/focus/Enter/Space/Escape. Pane riêng ở z-index 460, dưới marker và trên route Canvas; map vẫn pan/zoom được. Layer ẩn hoặc thao tác camera đóng card. Portal đặt card trong viewport; test touch 320×720 đạt.
- AC7: React render nội dung chuỗi, không dùng HTML từ provider; test XSS chỉ hiện chữ. Effect cleanup remove layer/renderer, listener, RAF, timer và abort request. `MapComponent` chỉ tích hợp component lazy, tắt hit paths khi đang chọn vị trí trạm.

## Lỗi thực tế tìm thấy khi nghiệm thu

`workspace.css`: mobile đặt `top:76px`, nhưng selector desktop `.map-first[data-drawer-open="false"] .gm-layers-widget` có specificity cao hơn nên vẫn giữ `bottom`, làm vùng bấm của control kéo dài vô hình qua bản đồ. Điều chỉnh selector media query đủ specificity để `bottom:auto` có hiệu lực. Browser xác nhận `elementFromPoint` tại tuyến là hit path và tap mở card, đồng thời các control bật/tắt lớp vẫn hoạt động.

## Cách dùng và giới hạn

Luồng chính: chọn xe trên bản đồ để mở thẻ vận tốc/ETA và Theo xe; hoặc chọn chuyến trong Mô phỏng, bắt đầu/tiếp tục chuyến rồi dùng “Xem vị trí xe và tuyến đang chạy”. Thẻ mô phỏng hiển thị vận tốc, trạm tiếp theo và ETA trước các điều khiển; 1×/5×/10× chỉ đổi tốc độ phát. Tắt lớp giao thông trên bản đồ không tắt tính ETA chuyến ở backend.

Tooltip tuyến đã chọn vẫn dùng được: chọn tuyến đã lưu trong Tuyến & trạm hoặc tuyến của chuyến đang xem, rồi rê chuột lên tuyến xanh. Bấm/chạm hoặc Enter để ghim; nút × hoặc Escape để đóng. Khi HERE không có dữ liệu khớp, UI ghi rõ trạng thái và giữ thông tin tuyến đã lưu.

Phạm vi hiện tại là tuyến được vẽ, không phải mọi đường của basemap. Matching geometry/hướng là suy luận, không có road-link ID chung để đảm bảo phân biệt tuyệt đối đường song song hoặc cầu tầng. Thời gian là ước tính từ dòng xe; chưa phải thời gian thực đo của xe đang mô phỏng. Không thêm vận tốc tùy chỉnh hoặc ghi sự cố giả lập.

`verification/roads-browser.mjs` và `verification/live-readonly.mjs` mô tả phạm vi hover mọi đường **đã rút**, giữ để đọc lịch sử, không chạy như acceptance hiện tại. Dùng `tracking-browser.mjs` và `tracking-live-readonly.mjs` cho thay đổi cuối.

`docs/` hiện bị ignore bởi `.gitignore`; hồ sơ 010 và artifacts vẫn nằm local, chưa stage/commit. Giữ nguyên rule của người dùng. Có thể chuyển session trên cùng workspace để tiếp tục; không coi clone HEAD là đã có các file mới này.
