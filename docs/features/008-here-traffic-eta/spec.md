# Spec 008 — HERE Traffic, ETA động và simulator theo giao thông

Trạng thái: **Implementing** · Ngày cập nhật: 2026-09-14 · Source MVP đã triển khai; upstream live spike đã pass; phụ thuộc source 005 (route/trip),
006 (telemetry/simulator), 007 (check-in). Không có migration bắt buộc trong MVP; cache
traffic/ETA không phải nguồn sự thật của route hoặc check-in.

## 1. Nguyên tắc contract

- HERE được gọi **chỉ từ backend**. Frontend gọi API nội bộ và không biết API key,
  upstream URL hoặc raw error body.
- `observedAt` là `sourceUpdated` của HERE; `fetchedAt` là thời điểm backend nhận response.
  Hai thời điểm có thể khác nhau.
- `source` luôn được trả cùng dữ liệu: `HERE_LIVE`, `HERE_LAST_KNOWN`,
  `ROUTE_SNAPSHOT` hoặc `UNAVAILABLE`.
- Traffic không sửa route snapshot, thứ tự stop, dwell gốc, check-in hoặc lifecycle trip.
  Reroute/revision và notification là 009.
- Dữ liệu upstream chưa match route chỉ dùng cho lớp bản đồ; không tự làm thay đổi ETA.

## 2. Cấu hình backend

Các property được bind dưới `here.traffic`:

| Property | Mặc định/ý nghĩa |
|---|---|
| `enabled` | `false`; false thì không gọi HERE và không tạo request upstream |
| `base-url` | host Traffic API; provider chuẩn hóa path `/v7` đúng một lần |
| `api-key` | bắt buộc khi enabled; chỉ backend, không log/response |
| `connect-timeout-ms` | 2000 |
| `read-timeout-ms` | 5000 |
| `cache-ttl-seconds` | 60; fresh window |
| `stale-ttl-seconds` | 300; tối đa cho last-known |
| `corridor-radius-meters` | 100 đề xuất; phải chốt bằng live spike/test |
| `poll-interval-seconds` | Không có scheduler traffic riêng trong MVP; ETA UI làm mới theo chu kỳ nhưng backend cache vẫn bảo vệ upstream |

Tên env public được hỗ trợ: `HERE_TRAFFIC_ENABLED`, `HERE_TRAFFIC_BASE_URL`,
`HERE_TRAFFIC_TILE_BASE_URL`,
`HERE_API_KEY`, `HERE_CONNECT_TIMEOUT_MS`/`HERE_TRAFFIC_CONNECT_TIMEOUT_MS`,
`HERE_READ_TIMEOUT_MS`/`HERE_TRAFFIC_READ_TIMEOUT_MS`. Các tên `TRAFFIC_PROVIDER`,
`TRAFFIC_CACHE_TTL_SECONDS` và `REROUTE_*` chỉ có tác dụng nếu được bind rõ trong bước
implementation; không âm thầm đọc chúng từ frontend.

Khi `enabled=true` nhưng key trống/blank hoặc base URL sai, application phải fail validation
hoặc trả trạng thái cấu hình unavailable theo một policy duy nhất đã test; không khởi tạo
provider nửa vời. Bean HTTP traffic phải có qualifier riêng, không nhập nhằng với HERE
Routing `RestClient`.

## 3. DTO nội bộ và nguồn dữ liệu

### 3.1 Envelope chung

```json
{
  "source": "HERE_LIVE",
  "status": "AVAILABLE",
  "observedAt": "2026-09-14T02:10:00Z",
  "fetchedAt": "2026-09-14T02:10:01Z",
  "ageSeconds": 1,
  "warning": null,
  "results": []
}
```

`status` là `AVAILABLE`, `STALE`, `BLOCKED` hoặc `UNAVAILABLE`. `warning` là mã/thông
điệp an toàn cho UI, không chứa upstream body hoặc credential.

### 3.2 Flow item

```json
{
  "id": "stable-segment-key",
  "description": "Nguyen Hue, District 1",
  "lengthMeters": 500,
  "points": [[10.7745, 106.7025], [10.7755, 106.7035]],
  "speedKmh": 29.99,
  "freeFlowKmh": 39.996,
  "jamFactor": 4.5,
  "traversability": "open",
  "confidence": 0.95
}
```

`speed`/`freeFlow` từ HERE là m/s và phải đổi sang km/h ở provider; `jamFactor` giữ
0–10; points dùng `[lat,lng]` nhất quán với Leaflet. Nếu upstream không có id, provider
tạo key ổn định từ location reference/geometry, không dùng array index.

### 3.3 Incident item

```json
{
  "id": "incident-id",
  "description": "Road resurfacing work",
  "type": "ROAD_WORKS",
  "criticality": "major",
  "startTime": "2026-09-14T01:30:00Z",
  "endTime": "2026-09-14T12:00:00Z",
  "points": [[10.7801, 106.6985], [10.7815, 106.7001]],
  "center": [10.7808, 106.6993],
  "status": "ACTIVE"
}
```

`status` nội bộ là `ACTIVE` hoặc `EXPIRED`; item expired có thể còn xuất hiện trong
overlay nếu response vừa nhận nhưng không được tính ETA. Criticality chuẩn hóa về
`low|minor|major|critical`.

## 4. API backend

### 4.1 Flow theo viewport

`GET /api/v1/traffic/flow?west={lng}&south={lat}&east={lng}&north={lat}`

- Bắt buộc bốn số finite, `-180≤longitude≤180`, `-90≤latitude≤90`, west < east và
  south < north; giới hạn kích thước bbox để bảo vệ quota (giá trị cụ thể chốt trong
  Plan/test).
- Backend gọi `{base}/v7/flow?in=bbox:west,south,east,north&locationReferencing=shape`
  và thêm api key server-side. Không trả URL upstream cho client.
- `200` trả envelope và `results=[]` khi HERE không có flow; fresh/stale phân biệt bằng
  source/status. `400` `TRAFFIC_BOUNDS_INVALID`; `503` disabled/no usable last-known;
  `504` timeout; `502` malformed/other provider response; 401/403/429/5xx được map ổn định.

### 4.2 Incidents theo viewport

`GET /api/v1/traffic/incidents?west={lng}&south={lat}&east={lng}&north={lat}`

Validation, upstream `in=bbox:...&locationReferencing=shape`, envelope và status/error
giống flow. Có thể truyền filter criticality/type nếu cần ở UI, nhưng filter phải nằm
trong cache key và không được mở rộng tùy tiện ngoài acceptance criteria.

### 4.3 Raster tile cho lớp bản đồ

`GET /api/v1/traffic/tiles/{z}/{x}/{y}.png`

- Backend kiểm tra `0≤z≤20` và chỉ nhận `x`, `y` trong phạm vi ma trận tile của zoom;
  tọa độ không hợp lệ trả về tile PNG trong suốt để Leaflet không làm hỏng thao tác pan/zoom.
- Backend gọi HERE Traffic Raster Tile API v3 theo mẫu
  `https://traffic.maps.hereapi.com/v3/flow/mc/{z}/{x}/{y}/png8?apiKey=...`;
  API key chỉ nằm ở server-side. Client không gọi HERE trực tiếp.
- Response luôn có `Content-Type: image/png` và cache-control 60 giây. Tile thành công
  được cache trong bộ nhớ theo `z/x/y`; upstream lỗi/disabled trả tile trong suốt.
- Tile là lớp hiển thị giao thông realtime; Flow JSON không được tải theo viewport để
  render bản đồ. Incidents JSON vẫn được dùng cho marker và popup chi tiết.

### 4.4 ETA theo trip

`GET /api/v1/trips/{tripId}/eta`

Response 200:

```json
{
  "tripId": 42,
  "routeId": 7,
  "calculatedAt": "2026-09-14T02:10:01Z",
  "source": "HERE_LIVE",
  "status": "AVAILABLE",
  "trafficObservedAt": "2026-09-14T02:10:00Z",
  "trafficFetchedAt": "2026-09-14T02:10:01Z",
  "nextStopSequence": 3,
  "totalRemainingSeconds": 1320,
  "stops": [
    {"sequenceNumber": 3, "state": "NEXT", "etaAt": "2026-09-14T02:18:00Z", "etaSeconds": 480, "source": "HERE_LIVE"},
    {"sequenceNumber": 4, "state": "PLANNED", "etaAt": "2026-09-14T02:32:00Z", "etaSeconds": 1320, "source": "HERE_LIVE"}
  ],
  "affectedSegments": [],
  "warning": null
}
```

Stop `state` là `CHECKED_IN`, `NEXT` hoặc `PLANNED`; stop checked-in có `actualArrivalAt`
và không có ETA tương lai. Khi provider không dùng được nhưng route snapshot còn hợp lệ,
API vẫn trả 200 với `source=ROUTE_SNAPSHOT`, `status=UNAVAILABLE` hoặc warning và ETA
baseline được ghi rõ là fallback. Khi section bị closure, trả 200 `status=BLOCKED`,
`warning=TRAFFIC_BLOCKED` và ETA của phần bị chặn là null; không đổi route hoặc tự reroute.

Lỗi `404` là trip/route không tồn tại; `409` là trip không có snapshot/geometry cần ETA;
`503` chỉ dùng khi không thể tạo cả baseline ETA. Error body dùng Problem Details + `code`
như các API hiện có.

## 5. Upstream request, cache và freshness

1. Chuẩn hóa bbox/corridor và filter trước khi tạo cache key; thứ tự tham số không được
   làm phát sinh key khác.
2. Cache thành công gồm envelope đã chuẩn hóa, `observedAt`, `fetchedAt`, expiration.
   Trong fresh TTL trả `HERE_LIVE`; trong stale window trả bản cuối với
   `HERE_LAST_KNOWN`, `status=STALE`, `ageSeconds` và warning.
3. Quá stale hoặc chưa từng thành công không trả dữ liệu như live. Flow/incidents trả
   lỗi có kiểm soát; ETA có thể dùng route snapshot theo §4.4.
4. Concurrent requests cùng key dùng single-flight/dedupe; lỗi không cache dài hạn. Không
   gọi HERE ở mỗi frame/SSE heartbeat; scheduler/poll chỉ làm mới khi cần.
5. Cache memory chỉ cam kết một backend instance. Restart mất last-known; không giả lập
   persistence traffic hoặc dùng database route làm cache traffic.

## 6. Route matching và ETA

- Với map, hiển thị mọi item trong bbox. Với ETA, tạo corridor từ geometry của các section
  chưa đi qua, bán kính cấu hình; không dùng bbox toàn thành phố.
- Flow/incident được coi là ảnh hưởng nếu geometry cắt corridor hoặc khoảng cách tới
  section ≤ bán kính và hướng tương thích khi item có ít nhất hai điểm. Item ngoài
  corridor, expired hoặc thuộc section đã đi qua bị loại khỏi `affectedSegments`.
- Hướng được so bằng vector đầu-cuối đã chuẩn hóa; sai khác ngược rõ ràng không match.
  Nếu geometry quá ngắn/thiếu hướng, dùng spatial match nhưng đánh dấu confidence thấp
  và không suy closure chỉ từ thiếu hướng.
- Mỗi remaining section bắt đầu bằng `travelDurationSeconds` snapshot. Nếu có flow mở,
  dùng `lengthMeters / speed` (speed đã đổi về m/s nội bộ) với ngưỡng speed dương và
  clamp an toàn; nếu không có speed hợp lệ, giữ baseline và ghi warning. `jamFactor`
  dùng phân loại/hiển thị, không nhân duration hai lần.
- `traversability=closed` hoặc incident road closure ảnh hưởng section tạo `BLOCKED`;
  không dùng baseline như thể đường mở. `reversibleNotRoutable` cũng không được coi là
  `open` nếu hướng hiện tại không hợp lệ.
- Cộng dwell của stop chưa hoàn thành. Vị trí hiện tại lấy từ frame simulator hoặc
  telemetry mới nhất; nếu không đủ position, ETA bắt đầu từ section kế tiếp/route
  snapshot và phải ghi source fallback. Không dùng khoảng cách Euclid hoặc speed GPS
  tức thời làm ETA chính.
- ETA từng stop là tổng duration động từ vị trí hiện tại đến section kết thúc stop đó.
  Timestamp dùng server clock; simulator giữ `simulatedAt` riêng.

## 7. Simulator và operations snapshot

- Traffic/ETA service là dependency dùng chung; không gọi HERE trong `RouteMotion.at` hoặc
  mỗi render. Tick chỉ đọc profile cache gần nhất.
- Frame/SimulationResponse mở rộng additive với một `traffic` metadata nullable:
  `source`, `status`, `nextStopEtaSeconds`, `observedAt`, `fetchedAt`, `blocked`,
  `warning`. Field cũ vẫn giữ để client 006 không vỡ; UI ưu tiên field traffic khi
  `HERE_LIVE`/`HERE_LAST_KNOWN`, nếu không thì ghi `ROUTE_SNAPSHOT`.
- Jam làm giảm effective speed/tăng ETA ở tick tiếp theo, không teleport vị trí và không
  lùi elapsed clock. Multiplier 5×/10× chỉ tăng simulated clock; không tạo upstream call
  nhiều hơn TTL.
- Closure giữ geometry/route snapshot và đặt metadata `blocked=true`, speed frame bằng
  0, elapsed không tăng; MVP giữ run ở `RUNNING` với frame blocked để không tự đổi
  lifecycle. Không tự sinh route revision; reroute thuộc feature 009.
- Snapshot/SSE operations truyền metadata mới cùng revision/state đã commit. Nếu traffic
  lỗi, vị trí/check-in/simulation state vẫn phát bình thường với warning fallback.

## 8. Frontend contract

- `services/hereTraffic.ts` dùng request backend có `AbortSignal`, parse envelope/type,
  phân biệt 4xx/5xx và không fallback sang HERE từ browser.
- `MapComponent` sở hữu state `trafficEnabled`, cleanup layer/timer và lớp raster tile HERE
  theo viewport (Leaflet tự tải/ghép các tile 256px khi pan/zoom). Flow JSON không còn được
  tải để render bản đồ; endpoint flow vẫn dành cho ETA/matching server-side. Incident JSON
  nhẹ vẫn dùng cho marker theo criticality; legend giải thích màu traffic/timestamp.
- `MapControls` bỏ disabled chỉ khi backend feature/config cho phép; hiển thị connecting,
  last updated, stale/unavailable và nút retry. Empty không phải error.
- `SimulatorPanel`/`TripDetailPanel` hiển thị ETA từng trạm và badge source; actual check-in
  không bị thay bằng ETA. Khi fallback, copy phải nói “route snapshot”; khi blocked, nói
  đường bị đóng và chưa có reroute.
- Responsive 320/390/768/1440 px, giữ focus/selection/draft của layout 004; chuyển mode
  hoặc unmount phải dọn timer, fetch abort và Leaflet layers.

## 9. Data model, migration và compatibility

- MVP không thêm bảng bắt buộc: traffic response/cache là dữ liệu tạm, không phải audit.
  Route/trip/check-in tables và migration V1–V6 không sửa.
- Nếu implementation cần lưu snapshot để phục hồi sau restart, phải mở spec/migration
  riêng; không nhét traffic raw vào `RouteSectionEntity` hoặc bảng telemetry.
- Các field traffic/ETA trên snapshot/simulation là nullable/additive; client cũ bỏ qua.
  Route `duration/baseDuration`, planned schedule và check-in history giữ nguyên.

## 10. Lỗi, bảo mật và quan sát

| Tình huống | HTTP/API | UI |
|---|---|---|
| Disabled/thiếu key | 503 `TRAFFIC_UNAVAILABLE` | unavailable, không có dữ liệu giả |
| Bbox sai | 400 `TRAFFIC_BOUNDS_INVALID` | validation, giữ lớp cũ |
| Timeout | 504 `TRAFFIC_PROVIDER_TIMEOUT` | last-known nếu còn hạn, nếu không unavailable |
| 401/403 | 503 `TRAFFIC_PROVIDER_UNAVAILABLE` + log mã lỗi an toàn | cấu hình HERE không hợp lệ |
| 429/5xx | 503 `TRAFFIC_PROVIDER_UNAVAILABLE` | retry sau backoff/cache |
| Payload sai | 502 `TRAFFIC_PROVIDER_INVALID_RESPONSE` | không vẽ partial không rõ nguồn |
| Closure match | 200 `BLOCKED`/warning | cảnh báo đường đóng, không tự reroute |

Log chỉ ghi endpoint operation, status, duration, cache state, request correlation id và
không ghi apiKey, full URI có query key, geometry hàng loạt hoặc raw response. Metrics
request/cache/timeout/stale phải phân biệt MAP và ETA.
