# 008 — HERE Traffic, ETA động và simulator theo giao thông

Trạng thái: **Implementing** · Ngày lập/cập nhật: 2026-09-14 · Source MVP đã triển khai; upstream live spike đã pass, browser/full-suite còn thiếu.

## Bối cảnh và vấn đề

Vehicle Tracking đã có tuyến lưu từ HERE Routing, lịch trip, telemetry, simulator và
check-in tự động. Tuy nhiên thời gian đang hiển thị là duration của route snapshot hoặc
đồng hồ mô phỏng; bản đồ chưa có lớp giao thông/sự cố trực tiếp. Frontend có service traffic
nhưng backend chưa có provider nghiệp vụ, controller hoặc consumer. API key trong backend
không tự tạo ra luồng dữ liệu live.

Feature này bổ sung một trust boundary rõ ràng: backend gọi HERE Traffic API v7, chuẩn
hóa dữ liệu và cung cấp API nội bộ; frontend chỉ gọi API nội bộ. Traffic live được dùng
để tính ETA phần đường còn lại và làm chậm simulator, nhưng không tự đổi tuyến (đó là
feature 009).

## Mục tiêu người dùng/nghiệp vụ

- Người điều hành bật lớp giao thông để xem tốc độ, mức ùn tắc và sự cố hiện hành trên
  khu vực bản đồ.
- Người điều hành xem ETA tới các trạm chưa đi qua, biết ETA dùng dữ liệu HERE live,
  last-known hay route snapshot.
- Simulator dùng cùng dữ liệu traffic/ETA với màn hình vận hành; dữ liệu cũ, lỗi hoặc
  đường bị đóng phải được ghi nhãn rõ và không bị trình bày như dữ liệu live.
- API key và chi tiết lỗi của HERE không rời backend; quota và timeout được kiểm soát.

## Phạm vi

### Trong phạm vi

- HERE Traffic API v7 flow/incidents qua backend và HERE Traffic Raster Tile API v3 qua
  proxy tile backend.
- Request theo bounding box cho lớp bản đồ và corridor/phần geometry còn lại cho ETA.
- Chuẩn hóa geometry, speed, free-flow speed, jam factor, traversability, confidence,
  incident id/type/criticality/thời gian hiệu lực, observedAt và fetchedAt.
- Cache in-memory có TTL fresh 60 giây và cửa sổ last-known tối đa 300 giây; chống các
  request trùng trong cùng một key.
- Matching flow/incident với phần đường còn lại của route, có xét geometry và hướng khi
  dữ liệu đủ để xác định hướng.
- ETA theo từng trạm chưa check-in, cộng travel time động và dwell chưa hoàn thành; stop
  đã ghi nhận hiển thị actual arrival.
- Simulator đọc cùng traffic snapshot; jam làm giảm tốc/tăng ETA, closure không được
  coi là đường chạy bình thường.
- Lớp HERE Raster Tile và incident marker trên bản đồ, trạng thái loading/empty/stale/
  unavailable và nhãn nguồn dữ liệu trong simulator.
- Unit, integration, API, frontend build/lint/type-check và live spike có kiểm soát.

### Ngoài phạm vi

- Tự tính và lưu route revision, bỏ stop, reroute hoặc thông báo nghiệp vụ (009).
- Lưu lịch sử traffic dài hạn, phân tích xu hướng, dự báo giao thông tương lai.
- MQTT/GPS device onboarding, authentication/authorization người dùng, SMS/email.
- Đưa HERE key vào frontend hoặc gọi HERE trực tiếp từ trình duyệt.
- HERE Traffic Vector Tile SDK hoặc thay Leaflet bằng SDK bản đồ khác; lớp HERE Raster
  Tile API dùng qua backend proxy thuộc phạm vi của 008.
- Distributed cache/scheduler nhiều backend instance; chỉ cam kết một backend instance
  trong giai đoạn đầu.
- Nút tạo sự kiện kẹt xe/tai nạn/công trường giả lập trộn vào incidents thật.

## Actor và luồng chính

Actor là điều hành viên và backend scheduler/provider.

1. Frontend tải raster tile theo viewport và yêu cầu incidents theo viewport, hoặc chọn
   một trip đang chạy.
2. Backend kiểm tra bbox/corridor, đọc cache; nếu hết fresh TTL thì gọi HERE với API key
   backend và `locationReferencing=shape`.
3. Backend chuẩn hóa response, ghi `observedAt` từ HERE và `fetchedAt` từ server, lưu
   cache rồi trả envelope có source/freshness.
4. Frontend hiển thị raster tile giao thông, marker incident theo criticality và cảnh báo
   nguồn/stale nếu có; Flow JSON chỉ phục vụ ETA/matching ở backend.
5. ETA service lấy phần route còn lại, loại stop đã check-in, ghép traffic phù hợp và
   trả ETA từng stop. Simulator dùng kết quả đó ở tick tiếp theo; không tạo request HERE
   ở mỗi frame.

## Yêu cầu chức năng và acceptance criteria

| AC | Kết quả có thể quan sát/kiểm thử |
|---|---|
| AC-01 | Khi `HERE_TRAFFIC_ENABLED=false` hoặc thiếu key, backend không gọi HERE; API trả lỗi có kiểm soát và UI hiển thị unavailable, không giả dữ liệu. Khi bật hợp lệ, provider được tạo nhưng chỉ gọi khi có request cần thiết. |
| AC-02 | Flow API nhận bbox hợp lệ, gửi request HERE v7 với `in=bbox:west,south,east,north`, `locationReferencing=shape`, map được speed/freeFlow về km/h, giữ jamFactor 0–10, traversability, confidence, geometry, `observedAt`/`fetchedAt`. |
| AC-03 | Incidents API nhận bbox hợp lệ, map được id, type, criticality, description, geometry/center, start/end và loại bỏ item hết hiệu lực khỏi kết quả ảnh hưởng ETA; danh sách rỗng là trạng thái hợp lệ. |
| AC-04 | Cache trả dữ liệu fresh trong 60 giây; sau đó được phép trả last-known tối đa 300 giây với source `HERE_LAST_KNOWN` và tuổi dữ liệu; quá hạn hoặc chưa từng thành công trả `UNAVAILABLE`, không hiển thị stale như live. Request đồng thời cùng key không tạo nhiều upstream calls. |
| AC-05 | Chỉ flow/incident giao cắt hoặc nằm trong ngưỡng corridor của phần geometry còn lại mới ảnh hưởng trip; item chỉ nằm trong bbox nhưng ngoài route không làm đổi ETA. Khi có shape hai điểm, hướng ngược tuyến không được ghép như cùng chiều. |
| AC-06 | ETA trả từng stop chưa check-in theo thứ tự, cộng travel duration động của các section còn lại và dwell chưa hoàn thành; stop đã check-in có actual arrival và không bị tính lại. Khi không có traffic match, source là `ROUTE_SNAPSHOT`, không dùng khoảng cách thẳng hoặc tốc độ GPS tức thời. |
| AC-07 | Jam factor/speed thấp làm ETA tăng và simulator không vượt tốc độ traffic profile; traversability `closed` tạo trạng thái `BLOCKED`/ETA không khả dụng cho phần đó, không âm thầm chạy theo tốc độ baseline. Reroute không tự xảy ra. |
| AC-08 | Bản đồ có toggle HERE traffic tile/incidents, legend và marker criticality; có loading, empty, stale, unavailable, retry và timestamp. Tắt lớp dọn tile/layer/listener; không lộ key trong bundle hoặc network call tới HERE từ browser. |
| AC-09 | Simulator và snapshot/SSE ghi rõ `HERE_LIVE`, `HERE_LAST_KNOWN`, `ROUTE_SNAPSHOT`, `BLOCKED` hoặc `UNAVAILABLE`; cập nhật traffic không làm lùi clock, vị trí, check-in hoặc baseline route. 5×/10× chỉ tăng clock, không tăng tần suất gọi HERE vượt TTL. |
| AC-10 | Timeout, 401/403, 429, 5xx, payload sai và bbox sai được map thành status/error contract ổn định; log không chứa API key, URL có query key hoặc payload traffic đầy đủ. Fallback không che giấu lỗi provider. |
| AC-11 | API flow/incidents/ETA có validation, CORS và response type nhất quán; frontend chỉ gọi backend. Các endpoint 005–007, lịch route snapshot và check-in hiện tại không đổi khi traffic tắt. |
| AC-12 | Unit/integration/API/frontend checks chứng minh AC; có live spike opt-in tại TP.HCM ghi số request, status, coverage/empty response và giới hạn quota riêng với fixture. Không tuyên bố HERE live đạt nếu chưa chạy key hợp lệ. |

## Phi chức năng

- **Bảo mật:** key chỉ ở process backend; không commit, log, screenshot hoặc truyền qua
  `VITE_*`; upstream error không trả credential. Key đã từng xuất hiện trong hội thoại
  phải được rotate trước khi kiểm thử live.
- **Độ tin cậy:** timeout kết nối 2 giây/đọc 5 giây; lỗi upstream không xóa route hoặc
  check-in; cache stale có tuổi dữ liệu và source rõ ràng.
- **Hiệu năng/quota:** không gọi provider theo mỗi SSE tick/frame; cache key ổn định,
  request đồng thời được hợp nhất. Mục tiêu dev: flow/incident response p95 dưới 6 giây
  khi gọi upstream, cache hit dưới 200 ms; ETA cho 10 trip × 50 stop không tạo N request
  HERE theo stop.
- **Quan sát:** đếm request success/error/timeout/cache-hit/stale và thời gian upstream;
  không ghi tọa độ/response nguyên khối hoặc secret vào log.
- **Tương thích:** giữ duration/baseDuration và route snapshot 005; các field traffic/ETA
  mới là additive/nullable để client cũ vẫn đọc được snapshot.

## Giả định, phụ thuộc và câu hỏi cần chốt

- HERE app/key có quyền Traffic API v7 và khu vực TP.HCM có coverage; live spike phải
  xác minh thay vì suy ra từ fixture.
- `HERE_TRAFFIC_BASE_URL` sẽ được chuẩn hóa thành host + `/v7` đúng một lần; không nối
  lặp `/v7` khi người vận hành đã cấu hình URL có version.
- Geometry route đang là HERE Flexible Polyline và route section có distance/travel
  duration; cần tái sử dụng decoder/motion hiện tại.
- 008 dùng cache memory một instance; nếu triển khai nhiều instance sẽ mở rộng riêng.
- Cần chốt trước implementation: bán kính corridor/matching (đề xuất 100 m), ngưỡng
  jam để hiển thị màu, và UI xử lý khi closure (giữ run ở trạng thái blocked hay pause
  có lý do). Plan mặc định dùng `BLOCKED` trong traffic metadata, không tự reroute.
