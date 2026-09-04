# Research — 003-route-eta

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `003-route-eta` |
| Requirement liên quan | `REQ-001..REQ-006` |
| Trạng thái | `READY — chờ người dùng phê duyệt trước Gemini` |
| Người thực hiện | Codex |
| Ngày nghiên cứu | 2026-09-04 |

## Mục tiêu nghiên cứu

Quyết định cách hoàn thiện ETA cho từng stop và completion mà không thêm provider routing/traffic, migration hoặc abstraction không cần thiết; đồng thời chọn cách tạo timestamp deterministic cho test.

## Phạm vi nghiên cứu

### Trong phạm vi

- Nguồn dữ liệu/thuật toán ETA cho simulator.
- Contract ETA completion realtime.
- Nguồn thời gian injectable cho time-based test.

### Ngoài phạm vi

- Độ chính xác dữ liệu giao thông ngoài đời, license/quota của map provider và thuật toán routing đường bộ.

## Các câu hỏi cần trả lời

| ID | Câu hỏi | Requirement liên quan | Tiêu chí để trả lời |
|---|---|---|---|
| RQ-001 | Dùng dữ liệu nào để ETA thay đổi theo trạng thái simulator? | REQ-002, REQ-003 | Có dữ liệu vị trí, tốc độ và traffic giả lập ngay trong repository; không tạo external dependency. |
| RQ-002 | ETA completion có cần field tường minh hay suy diễn ở client từ stop cuối? | REQ-003, REQ-004 | Contract dễ dùng, additive và tránh client tự suy diễn state terminal. |
| RQ-003 | Làm sao test chính xác timestamp ETA/completion? | REQ-005, REQ-006 | Có time source controllable bằng JDK, không thêm dependency. |

## Tiêu chí đánh giá

| Tiêu chí | Mức quan trọng | Cách đánh giá |
|---|---|---|
| Correctness | Bắt buộc | ETA/order/completion khớp Business Rules và test fixed time. |
| Scope/cost | Bắt buộc | Không thêm provider, secret, migration hoặc dependency. |
| Tương thích | Cao | REST giữ nguyên; telemetry chỉ thêm field. |
| Testability | Cao | Có thể assert absolute timestamp, completion và payload. |
| Vận hành | Vừa | Không tăng polling; không có quota/network failure mới. |

## Các giải pháp được xem xét

### Giải pháp A — Tái sử dụng simulator, TripCheckIn và TrafficIncident hiện có

#### Cách hoạt động

Tính cumulative distance từ vị trí simulator qua các stop PENDING theo `stopOrder`, chia cho vận tốc hiệu dụng hiện có. Giữ lịch khởi tạo trong `TripCheckIn`; dùng ETA stop cuối làm cơ sở cho hai field completion tường minh trên `VehicleTelemetryDto`.

#### Ưu điểm

- Tái sử dụng toàn bộ data/service/WebSocket sẵn có (Survey EVD-003..EVD-007).
- Không có key, quota, network failure hoặc dependency mới.
- Giá trị ETA phản ánh đúng mô hình incident/speed hiện đang điều khiển simulator.

#### Nhược điểm

- Là khoảng cách địa lý/waypoint đơn giản, không phải thời gian giao thông đường bộ thật.
- Chỉ có ETA động sau khi simulator phát telemetry.

#### Rủi ro

- Nếu UI không phân biệt lịch tĩnh và ETA simulator, người dùng có thể hiểu sai độ chính xác.

#### Mức độ phù hợp với project

Phù hợp nhất vì requirement giới hạn feature ETA/simulator hiện hữu và repository đã có đủ nguồn dữ liệu.

#### Evidence hỗ trợ

- EVD-003, EVD-004, EVD-005, EVD-006, EVD-007.

### Giải pháp B — Tích hợp provider routing/traffic bên ngoài

#### Cách hoạt động

Gọi directions/traffic API theo vị trí và stop còn lại để lấy ETA thật.

#### Ưu điểm

- Có thể gần thực tế hơn nếu provider có coverage và dữ liệu traffic phù hợp.

#### Nhược điểm

- Cần provider, credential, quota/cost, xử lý timeout/rate limit và thay đổi đáng kể Test-Plan.
- Repository không có adapter/config cho provider routing/traffic (Survey EVD-007).

#### Rủi ro

- Lộ secret, vendor lock-in, availability external service và không đủ evidence cho chất lượng dữ liệu.

#### Mức độ phù hợp với project

Không phù hợp với scope và ràng buộc hiện tại.

#### Evidence hỗ trợ

- EVD-007.

### Giải pháp C — Chỉ để frontend suy diễn completion từ `stationsEta` cuối

#### Cách hoạt động

Frontend lấy phần tử cuối trong danh sách ETA để tự hiển thị completion.

#### Ưu điểm

- Không đổi backend DTO.

#### Nhược điểm

- Contract completion phụ thuộc vào quy ước thứ tự ngầm; khó xử lý all-checked/terminal và client khác.
- Không diễn tả rõ `tripStatus` completion.

#### Mức độ phù hợp với project

Không chọn vì REQ-003 cần state completion quan sát được, không chỉ suy đoán UI.

#### Evidence hỗ trợ

- EVD-004, EVD-006.

## So sánh

| Tiêu chí | A — Reuse simulator | B — Provider ngoài | C — Client suy diễn |
|---|---|---|---|
| Đáp ứng ETA simulator | Có | Có, nhưng vượt scope | Một phần |
| ETA traffic thật | Không | Có thể | Không |
| Dependency/config/secret mới | Không | Có | Không |
| Completion contract rõ | Có, additive | Có thể | Không |
| Test deterministic | Có | Khó hơn | Một phần |

## Giải pháp đề xuất

**Lựa chọn:** Giải pháp A, kèm `Clock` injectable và field telemetry completion additive.

**Requirement được đáp ứng:** `REQ-001..REQ-006`.

**Điều kiện áp dụng:** Chỉ sử dụng simulator/TrafficIncident hiện có; `Clock.systemDefaultZone()` là default production để giữ semantics `LocalDateTime.now()` hiện hữu.

## Lý do lựa chọn

Repository đã tính lịch tĩnh và ETA stop theo tốc độ effective. Việc hoàn thiện contract completion và terminal state là thay đổi nhỏ nhất đạt yêu cầu. Oracle Java SE 26 mô tả `Clock` là abstraction injectable để thay thế static current-time access, hỗ trợ `Clock.fixed` trong test (EVD-001); vì vậy không cần thư viện mock time hoặc assertion theo đồng hồ thật.

Đánh đổi được chấp nhận là ETA không dùng routing/traffic thật. Nếu sản phẩm yêu cầu ETA ngoài đời hoặc tái định tuyến, cần feature riêng có provider, policy quota/failure, secret configuration và research độc lập.

## Giải pháp không được chọn

| Giải pháp | Lý do không chọn | Khi nào nên xem xét lại |
|---|---|---|
| Provider routing/traffic ngoài | Vượt scope, thêm secret/chi phí/failure mode, repository chưa có integration. | Requirement mới yêu cầu ETA giao thông thật và cấp provider/credential. |
| Client suy diễn completion | State terminal không rõ, dễ lệch giữa client. | Chỉ có UI demo tối thiểu và không có consumer telemetry khác. |
| Lưu snapshot ETA động vào database mỗi tick | Không cần cho dashboard realtime hiện tại, tạo write load/lifecycle mới. | Cần lịch sử ETA, analytics hoặc phục hồi state sau restart. |

## Ảnh hưởng dự kiến tới Spec và Plan

- Data model: không migration; dùng `TripCheckIn` hiện có. Thêm field DTO telemetry và frontend type.
- API/Event: không REST mới; `/topic/telemetry` thêm `tripStatus`, `etaSecondsToCompletion`, `estimatedCompletionTime`.
- Dependency/config: tạo `Clock` bean JDK, không dependency/env/secret mới.
- Testability: test service dùng fixed `Clock`; test completion kiểm tra state DB/mock repository và message terminal.

## Câu hỏi và rủi ro còn lại

- Tốc độ/traffic giả lập là mô hình sản phẩm hiện tại, không được diễn giải là dữ liệu giao thông thật.
- Không thay đổi meaning của speed multiplier: nó vẫn là playback control, không phải base/effective speed nghiệp vụ.

## Nguồn tham khảo

| ID | Evidence | Nguồn | URL | Ngày truy cập | Claim/Kết luận được hỗ trợ |
|---|---|---|---|---|---|
| SRC-001 | EVD-001 | Java SE 26 API — `java.time.Clock` | https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/time/Clock.html | 2026-09-04 | Clock là time source injectable; `fixed` giúp test không phụ thuộc current time. |

## Checklist hoàn thành

- [x] Research Question đã được trả lời.
- [x] Có các giải pháp hợp lý được so sánh.
- [x] Trade-off và rủi ro đã ghi rõ.
- [x] Nguồn ngoài có URL thật, đã mở kiểm tra và liên kết EVD-001.
- [x] Không có dependency hay provider ngoài scope.
