# Research: HERE Routing cho tuyến nhiều điểm dừng

## 1. Phạm vi nghiên cứu

Nghiên cứu tập trung vào HERE Routing API v8 cho route nhiều waypoint, geometry, duration có traffic, dwell time và cách xử lý response sections. Đây là kiến thức bên ngoài; trạng thái repository được ghi riêng trong `survey.md`.

- **Ngày truy cập:** 11/09/2026
- **Nguồn ưu tiên:** HERE official documentation và repository chính thức của HERE.

## 2. Nguồn chính thức

1. [HERE Routing API v8 — Introduction](https://docs.here.com/routing/docs/routing-v8-intro)
2. [Calculate routes via GET — API reference](https://docs.here.com/routing/reference/routing-api-v8-calculateroutes)
3. [How to add via waypoints to a route](https://docs.here.com/routing/docs/routing-v8-intermediate-waypoints)
4. [What are route sections](https://docs.here.com/routing/docs/routing-v8-route-section)
5. [Route geometry](https://docs.here.com/routing/docs/routing-v8-route-geometry)
6. [Traffic in routing](https://docs.here.com/routing/docs/routing-v8-traffic-in-routing)
7. [Duration, baseDuration, typicalDuration](https://docs.here.com/routing/docs/routing-v8-duration)
8. [Route summary](https://docs.here.com/routing/docs/routing-v8-route-summary)
9. [Time-dependent routing](https://docs.here.com/routing/docs/routing-v8-time-dependent-routing)
10. [Flexible Polyline specification](https://github.com/heremaps/flexible-polyline)
11. [HERE API key authorization](https://docs.here.com/identity-and-access-management/docs/plat-using-apikeys)

## 3. Dữ kiện từ tài liệu

### 3.1 Endpoint và waypoint

- Endpoint tính route là `GET https://router.hereapi.com/v8/routes`.
- `origin`, `destination` và `transportMode` là các input cốt lõi.
- Có thể truyền nhiều `via` parameters; HERE đi qua các via theo đúng thứ tự request.
- Via mặc định là stopover (`passThrough=false`) và làm route tách section tại waypoint.
- `stopDuration` có thể gắn vào via; giá trị phải nhỏ hơn 50.000 giây. Nó không dùng đồng thời với `passThrough=true`.
- Arrival/departure của section tại via chứa `place.waypoint`; index này cho phép nhận biết section đã đến via thứ mấy.

### 3.2 Section và geometry

- Route gồm một hoặc nhiều section.
- Section thường biểu diễn phần giữa hai waypoint liên tiếp, nhưng có thể phát sinh thêm section do implicit transport-mode change như ferry.
- `return=polyline` yêu cầu HERE trả geometry từng section ở định dạng Flexible Polyline.
- Flexible Polyline là encoding nén có version/precision; frontend phải decode trước khi đưa tọa độ cho Leaflet.
- Không nên giả định `sections.length === stops.length - 1`; cần nhóm sections theo waypoint boundary.

### 3.3 Distance và duration

- `return=summary` trả `duration`, `baseDuration` và `length` theo section.
- `return=travelSummary` chỉ tính phần di chuyển, loại pre/post actions như thời gian chờ tại stop.
- `duration` sử dụng dữ liệu dynamic traffic, historical traffic và free-flow khi khả dụng.
- `baseDuration` không time-aware; với ferry có thể không tồn tại. Tài liệu HERE hướng dẫn dùng `duration` thay cho base duration của ferry khi cộng tổng.
- HERE không trả summary cho toàn route; client phải cộng các section summaries.
- Chênh lệch `duration - baseDuration` có thể âm, vì tốc độ traffic hiện tại đôi khi cao hơn base speed. Không nên luôn gọi giá trị đó là “độ trễ”.

### 3.4 Traffic và thời điểm tính

- Nếu không truyền `departureTime` hoặc `arrivalTime`, HERE dùng thời điểm request làm departure time và time-aware routing được bật mặc định.
- `departureTime=any` là planning mode và bỏ qua phần lớn current/predicted traffic, nên không phù hợp mục tiêu “traffic tại thời điểm tạo”.
- Khi có dwell time tại via, thời điểm khởi hành section sau được dịch theo thời gian dừng; điều này giúp dự phóng traffic downstream hợp lý hơn.

### 3.5 Authentication và lỗi

- HERE API key được truyền bằng query parameter `apiKey`.
- Credential gắn với một HERE application và có cơ chế rotation.
- API reference mô tả các response 400, 401, 403 và 500; ngoài HTTP status, route calculation có thể trả HTTP success nhưng không có route hợp lệ hoặc có notice nghiêm trọng.
- Không được chỉ kiểm tra HTTP 200; client phải kiểm tra `routes`, `sections`, required summaries/polyline và notices.

## 4. Quyết định kỹ thuật cho feature 003

| Chủ đề | Quyết định | Lý do |
|---|---|---|
| Provider | HERE Routing API v8 qua backend | Có route thực tế, sections, polyline và duration traffic-aware; giữ key ngoài browser. |
| Transport mode | `car` | Ổn định và đủ cho MVP; `bus/privateBus` được HERE ghi là beta và dự án chưa có vehicle profile. |
| Routing mode | `fast` | Phù hợp bài toán ETA/thời gian hoàn thành. |
| Waypoint | START → origin, STOP → via stopover, END → destination | Giữ đúng thứ tự nghiệp vụ và tạo waypoint boundary. |
| Dwell time | Gửi `stopDuration` cho via và tự lưu dwell | HERE dùng dwell khi dự phóng section sau; backend vẫn có contract tính tổng rõ ràng. |
| Return fields | `polyline,summary,travelSummary` | Đủ geometry, distance, dynamic/base travel duration và loại được dwell khỏi travel summary. |
| Departure time | Không gửi `departureTime=any` | HERE mặc định dùng thời điểm request và bật traffic. |
| Section mapping | Dùng waypoint indices, không dùng section count đơn thuần | Hỗ trợ trường hợp một leg có nhiều section. |
| Persistence | Lưu normalized section geometry và station snapshots | Không phụ thuộc route handle; route xem lại ổn định khi station đổi. |
| Route handle | Không lưu làm nguồn geometry | HERE ghi route handle không phù hợp lưu trữ bền vững và có thể mất hiệu lực. |
| Alternatives | `alternatives=0`/mặc định | Feature chỉ tạo một route; tránh tăng payload và UI ngoài phạm vi. |
| App stop limit | 50 | Guard nội bộ cho UI/request; không khẳng định đây là quota HERE. Phải kiểm tra bằng integration smoke test với account thật. |

## 5. Công thức chuẩn hóa

Với section `s`:

```text
sectionDistance = travelSummary.length (hoặc summary.length nếu provider chỉ trả ở summary)
sectionTravelDuration = travelSummary.duration
sectionBaseTravelDuration = travelSummary.baseDuration ?? sectionTravelDuration
```

Với route:

```text
totalDistanceMeters = Σ sectionDistance
estimatedTravelDurationSeconds = Σ sectionTravelDuration
baseTravelDurationSeconds = Σ sectionBaseTravelDuration
totalDwellDurationSeconds = Σ dwellTimeSeconds của STOP
estimatedTripDurationSeconds = estimatedTravelDurationSeconds + totalDwellDurationSeconds
```

Với stop thứ `n`:

```text
arrivalOffset(n) = tổng travel duration của các section đến stop n
                 + tổng dwell của các stop trước n

departureOffset(n) = arrivalOffset(n) + dwellTime(n)
```

START có cả hai offset bằng 0. END có dwell bằng 0 nên arrival/departure offset bằng nhau.

## 6. Rủi ro và biện pháp

| Rủi ro | Biện pháp trong spec/plan |
|---|---|
| HERE trả HTTP 200 nhưng routes rỗng hoặc notice critical | Provider validate semantic response và chuyển thành lỗi domain có kiểm soát. |
| Một leg có nhiều sections | Lưu `route_sections.destination_stop_sequence` và nhóm theo waypoint boundary. |
| Base duration thiếu ở ferry | Fallback bằng dynamic duration theo hướng dẫn tổng hợp của HERE. |
| URL dài khi nhiều via | Giới hạn ứng dụng 50 stops; thêm smoke test và giảm giới hạn nếu account/provider thực tế không đáp ứng. |
| Traffic thay đổi sau khi route được tạo | Gắn `calculatedAt`; mô tả đây là snapshot, không phải ETA realtime. |
| HERE key bị lộ | Chỉ cấu hình backend; redact log; frontend gọi `/api/v1/routes`. |
| Provider disabled làm hỏng station | Routing config mặc định disabled; chỉ create route trả 503, ứng dụng vẫn startup. |

## 7. Điều không suy ra từ tài liệu

- Tài liệu được khảo sát không đưa ra một giới hạn via phù hợp để áp dụng trực tiếp cho account này. Mốc 50 là quyết định của ứng dụng, phải xác minh bằng môi trường thật.
- Quota, giá và entitlement phụ thuộc HERE plan/account hiện tại; không được hardcode giả định vào code hoặc báo cáo.
- Research không chứng minh repository đã có routing implementation; bằng chứng repository nằm trong `survey.md`.
