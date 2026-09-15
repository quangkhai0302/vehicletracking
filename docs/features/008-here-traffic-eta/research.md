# Research 008 — HERE Traffic và ETA

Ngày truy cập nguồn: **2026-09-14**. Nguồn dưới đây là tài liệu chính thức của HERE;
không dùng chúng để khẳng định source nội bộ đã hoạt động.

## 1. HERE Traffic API v7

| Chủ đề | Kết luận từ tài liệu | Ảnh hưởng đến feature |
|---|---|---|
| Tổng quan | [Introduction to HERE Traffic API v7](https://docs.here.com/traffic-api/docs/introduction-to-here-traffic-api-v7) mô tả Traffic API cung cấp flow và incident theo vùng địa lý trong JSON, kèm geometry tùy location referencing. | Backend phải gọi server-side và chuẩn hóa response; fixture không thay live evidence. |
| Flow endpoint | [Real-Time Flow Information](https://docs.here.com/traffic-api/reference/traffic-api-v7-getflow) dùng `GET https://data.traffic.hereapi.com/v7/flow`. `in` và `locationReferencing` là tham số bắt buộc; hỗ trợ bbox, circle, corridor và segment references. | Map dùng bbox; ETA dùng corridor theo phần route còn lại để giảm dữ liệu/quota. |
| Flow fields | [Flow concepts](https://docs.here.com/traffic-api/docs/flow) nêu `speed`, `speedUncapped`, `freeFlow` là m/s; `jamFactor` trong 0–10; `traversability` có `open`, `closed`, `reversibleNotRoutable`; `confidence` biểu thị tỷ lệ dữ liệu real-time trong tốc độ. `jamFactor=10` dành cho đường bị chặn. | DTO nội bộ đổi đơn vị một lần (km/h), giữ raw semantics cần cho blocked/jam và hiển thị confidence; không suy closure chỉ từ màu. |
| Incidents endpoint | [Real-Time Incident Information](https://docs.here.com/traffic-api/reference/traffic-api-v7-getincidents) dùng `GET https://data.traffic.hereapi.com/v7/incidents`, có bbox/circle/corridor và lọc criticality/type/thời gian. | Incident cần id, type, criticality, location, start/end và trạng thái hiệu lực; item ngoài route chỉ dành cho lớp bản đồ, không tự ảnh hưởng ETA. |
| Request parameters | [How to request flow data](https://docs.here.com/traffic-api/docs/how-to-request-flow-data) và [How to request incident data](https://docs.here.com/traffic-api/docs/how-to-request-incident-data) yêu cầu geospatial filter `in` và `locationReferencing`; `shape` trả geometry phù hợp cho bản đồ/matching. | Backend dùng `locationReferencing=shape`; validate bbox/corridor trước khi tiêu quota. |
| Auth và lỗi | Tài liệu request minh họa API key qua query hoặc OAuth Bearer; reference endpoint liệt kê 200, 400, 401, 403, 414, 500, 503. | API key chỉ ở backend; map 401/403 thành cấu hình/credential error, 429/5xx/timeout thành provider unavailable/timeout, không trả body có key. |
| Freshness | Response có `sourceUpdated` là thời điểm nguồn cập nhật; đây không phải thời điểm backend nhận response. | Envelope tách `observedAt=sourceUpdated` và `fetchedAt=server clock`; stale phải hiển thị tuổi dữ liệu. |

### Lựa chọn location referencing

`shape` được chọn cho MVP vì project đã có polyline/Leaflet và chưa có bộ giải mã OLR/TMC.
`olr`, `tmc` và `segmentRef` có thể hữu ích cho matching chính xác hơn nhưng sẽ tạo thêm
phụ thuộc catalog/decoder và không thuộc 008. Nếu HERE account không cấp `shape`, live
spike phải ghi nhận giới hạn và feature không được tự fallback sang dữ liệu không thể
diễn giải.

### Bbox hay corridor

HERE cho phép corridor bằng Flexible Polyline và bán kính. Bbox đơn giản cho lớp bản đồ
nhưng trả cả đường ngoài tuyến; corridor phù hợp ETA và quota hơn. Hai use case dùng
chung parser/DTO nhưng khác cache key và mục đích: `MAP_VIEW` có thể rộng, `TRIP_REMAINING`
phải hẹp theo geometry chưa đi qua.

### Raster tile cho lớp bản đồ

HERE có [Traffic Raster Tile API v3](https://docs.here.com/traffic-api/reference/here-traffic-raster-tile-api-v3-gettile)
với endpoint `/v3/flow/mc/{zoom}/{column}/{row}/{format}` và định dạng `png/png8`.
Tile đã được HERE render theo mảnh nên phù hợp để Leaflet tải khi pan/zoom; không đưa hàng
trăm nghìn điểm flow vào DOM/canvas của trình duyệt. Chọn proxy backend để giữ API key,
cache tile 60 giây và chỉ giữ Incidents JSON nhẹ cho marker/popup. Flow JSON vẫn giữ ở
backend cho ETA/matching, không còn là nguồn render lớp bản đồ.

## 2. HERE Routing và giới hạn của duration đã lưu

- [Traffic in routing](https://docs.here.com/routing/docs/routing-v8-traffic) cho biết
  route time-aware mặc định dùng traffic tại thời điểm request khi không chỉ định
  `departureTime`; chỉ một lần tính tuyến không tạo cơ chế cập nhật liên tục.
- [Duration, baseDuration, typicalDuration](https://docs.here.com/routing/docs/routing-v8-duration)
  phân biệt `duration` có thể dùng dữ liệu động và `baseDuration` không time-aware.
- [Route summary](https://docs.here.com/routing/docs/routing-v8-route-summary) nêu summary
  nằm trên từng section, muốn tổng route phải cộng duration các section.

Hệ quả: 005 có thể giữ duration HERE lúc tạo tuyến làm baseline; 008 phải tính lại ETA
trên remaining sections khi có flow live/last-known. Không ghi đè route snapshot gốc và
không gọi Routing API ở mỗi tick. `departureTime` cụ thể có thể được bổ sung ở feature
route revision/009 nếu cần, nhưng không thay thế Traffic API flow/incidents.

## 3. Các lựa chọn kỹ thuật

### Gọi trực tiếp từ browser hay qua backend

- **Browser → HERE:** loại vì lộ API key, khó kiểm soát quota/CORS, nhiều tab gây request
  trùng và không thống nhất policy stale.
- **Browser → backend proxy/provider → HERE:** chọn. Backend giữ key, cache/dedupe,
  chuẩn hóa lỗi và là nơi ghép route/ETA; frontend chỉ biết contract nội bộ.

### Cache

- **Không cache:** dữ liệu gần realtime hơn nhưng mỗi viewport/tick đốt quota và dễ bị
  rate-limit.
- **Cache in-memory một instance:** chọn cho 008, TTL fresh 60 giây + last-known 300
  giây theo đề xuất handoff; đơn giản, không thêm Redis/infra. Đổi lại restart/multi-
  instance mất cache, ghi rõ giới hạn.
- **Cache phân tán:** để feature vận hành/scale sau khi có yêu cầu.

### Tính ETA

- **Gọi Routing API lại toàn route mỗi lần:** có thể chính xác hơn khi cần reroute nhưng
  tốn quota, thay đổi geometry và xung đột baseline/check-in.
- **Ghép flow vào sections còn lại:** chọn cho 008; giữ geometry/snapshot, dùng speed
  hoặc trạng thái closure của flow, cộng dwell chưa hoàn thành. Reroute/revision để 009.
- **Dùng tốc độ GPS tức thời hoặc khoảng cách thẳng:** loại vì nhiễu, không phản ánh road
  network và bỏ qua dwell/sections.

### Closure

`traversability=closed` hoặc incident closure phải tạo trạng thái `BLOCKED`/ETA không
khả dụng cho phần bị ảnh hưởng; không thay bằng tốc độ baseline. 008 chỉ hiển thị và
giữ route hiện tại, còn tìm route thay thế/thông báo thuộc 009.

## 4. Kết luận research

1. Provider server-side cần hai request shape: bbox cho overlay và corridor cho ETA.
2. DTO nội bộ phải ghi đơn vị, geometry, `observedAt`/`fetchedAt`, source/freshness và
   trạng thái blocked; không truyền nguyên payload HERE ra UI.
3. Cache/dedupe và timeout là điều kiện bảo vệ quota, không phải tối ưu tùy chọn.
4. ETA động là read model mới dựa trên route snapshot + traffic match; không sửa route,
   check-in hoặc lifecycle 005–007.
5. Live spike tại TP.HCM là acceptance prerequisite để xác minh key có quyền Traffic,
   coverage và schema thực tế; fixture chỉ dùng cho deterministic tests.
