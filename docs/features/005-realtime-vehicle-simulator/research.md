# Research — 005-realtime-vehicle-simulator

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `005-realtime-vehicle-simulator` |
| Requirement liên quan | `REQ-001..REQ-005` |
| Trạng thái | `READY — chờ người dùng review Plan trước Gemini` |
| Người thực hiện | Codex |
| Ngày nghiên cứu | 2026-09-04 |

## Mục tiêu nghiên cứu

Chọn cách làm telemetry STOMP đủ tin cậy cho simulator hiện có khi client có thể reconnect hoặc nhận message không theo publish order. Quyết định phải tránh broker/provider/dependency mới và vẫn cho frontend xác định snapshot nào là mới nhất của một run.

## Phạm vi nghiên cứu

### Trong phạm vi

- Semantics Spring STOMP simple broker và ordering delivery.
- Chiến lược nhận biết/lọc telemetry stale/out-of-order.
- Trade-off giữa sequence ở application và cấu hình preserve publish order của Spring.

### Ngoài phạm vi

- Benchmark broker/load test, broker relay, multi-region WebSocket và event sourcing.
- So sánh MQTT/Kafka/Redis Stream hoặc nguồn traffic/routing ngoài.

## Các câu hỏi cần trả lời

| ID | Câu hỏi | Requirement liên quan | Tiêu chí để trả lời |
|---|---|---|---|
| RQ-001 | Simple broker hiện có có phát được snapshot đến subscribers theo topic không? | REQ-004 | Official Spring documentation và Survey config/source |
| RQ-002 | Có cần xử lý publish/receive out-of-order ở application không? | REQ-004, REQ-005 | Official Spring documentation về executor/ordering |
| RQ-003 | Cách nào cho client phân biệt telemetry cũ khi Start/Reset tạo run mới mà không thêm broker? | REQ-001, REQ-003, REQ-004 | Identity/ordering contract có thể test bằng unit/manual |
| RQ-004 | Có cần broker relay hoặc dependency mới cho simulator local một JVM không? | REQ-002, REQ-005 | Đối chiếu nhu cầu với Survey hiện trạng |

## Tiêu chí đánh giá

| Tiêu chí | Mức quan trọng | Cách đánh giá |
|---|---|---|
| Correctness UI | Bắt buộc | Client không nhận foreign/stale/out-of-order telemetry |
| Tương thích | Bắt buộc | Giữ topic/endpoint và fields cũ; thay đổi JSON additive |
| Độ đơn giản vận hành | Cao | Không dependency/broker/provider/config secret mới |
| Testability | Cao | Unit capture payload và manual STOMP stimulus có thể tái lập |
| Hiệu năng | Vừa | Không kích hoạt global ordering overhead khi identity/sequence đã đủ cho use case |

## Các giải pháp được xem xét

### Giải pháp A — Run ID + sequence ở application, giữ STOMP simple broker

#### Cách hoạt động

Mỗi lần Start tạo UUID `simulationRunId`; session giữ counter `sequence` tăng trước mỗi telemetry. Producer đưa hai field đó vào `VehicleTelemetryDto` và phát cùng snapshot đến hai topic hiện có. Command response trả run ID để frontend đặt expected run. Client chỉ dispatch snapshot match `tripId`, expected run và `sequence` lớn hơn giá trị đã render.

#### Ưu điểm

- Xử lý stale/out-of-order theo dữ liệu business của một simulation run, kể cả sau Start/Reset.
- Không đổi broker/transport, không thêm dependency và không cần global ordering cho mọi destination.
- Có thể test deterministic bằng payload/Mockito, không phụ thuộc timing mạng.

#### Nhược điểm

- Cần thêm field DTO/type và state ref ở frontend.
- Không phải persistence/replay: client reconnect vẫn chờ tick snapshot mới.

#### Rủi ro

- Nếu backend quên gắn run ID/sequence tại một branch terminal, client phải bỏ snapshot đó theo contract; test cần cover normal và terminal path.
- Counter chỉ có nghĩa trong một run; cần UUID mới khi Start mới.

#### Mức độ phù hợp với project

Phù hợp: source hiện có một `SimulationSession`/Trip và đã broadcast `VehicleTelemetryDto` qua `SimpMessagingTemplate`; frontend đã có một điểm tập trung nhận telemetry. Không có requirement replay hoặc multi-instance.

#### Evidence hỗ trợ

- `EVD-001` (Spring documentation về STOMP simple broker).
- `EVD-002` (Spring documentation về receive ordering không mặc định được bảo toàn).
- `EVD-003`, `EVD-005` (Survey backend/frontend hiện có).

#### Prototype/kiểm chứng nếu có

- Thao tác/command: Đọc tài liệu Spring tại các URL trong bảng nguồn và trace `SimulatorService#tickSingleSimulation`/`WebSocketService#onConnect`.
- Kết quả: broker/topic và single telemetry callback đã tồn tại; docs xác nhận client outbound có thể dùng executor nên receive order không mặc định bằng publish order.
- Giới hạn: Chưa có implementation feature; correctness runtime vẫn là `INCONCLUSIVE` cho đến TC-001..TC-011.

### Giải pháp B — Bật `setPreservePublishOrder(true)` cho broker client outbound

#### Cách hoạt động

Spring cho phép cấu hình preserve publish order cho client outbound channel. Mỗi session client được đảm bảo xử lý publish theo thứ tự, đổi lại message được tuần tự hóa trên executor.

#### Ưu điểm

- Giảm khả năng client nhận message không đúng thứ tự do executor Spring.
- Cấu hình Spring rõ ràng, không cần tự sắp xếp chỉ cho cùng một publish sequence.

#### Nhược điểm

- Tài liệu Spring nêu preserve order có performance overhead vì message của session được tuần tự hóa.
- Không giải quyết identity theo run: một message thuộc run cũ có thể vẫn đúng thứ tự nhưng không còn hợp lệ sau Reset/Start; cũng không giải quyết foreign Trip.

#### Rủi ro

- Áp dụng global cho tất cả realtime topic trong project dù chỉ simulator cần semantic per-run.
- Có thể tạo cảm giác đã giải quyết stale data trong khi không có application contract/test cho reset boundary.

#### Mức độ phù hợp với project

Không chọn trong feature này. Có thể đánh giá lại khi có benchmark chứng minh UI cần strict server publish order trên tất cả topic hoặc khi application sequence không đủ.

#### Evidence hỗ trợ

- `EVD-002` (Spring official documentation nêu behavior và cost).

#### Prototype/kiểm chứng nếu có

- Không thay config trong phase planning vì việc đó là source change ngoài phạm vi Survey.

### Giải pháp C — Broker relay/event store hoặc topic riêng dynamic cho từng run

#### Cách hoạt động

Đổi simple broker sang external broker relay hoặc lưu event/snapshot để client subscribe/replay theo trip/run.

#### Ưu điểm

- Có thể mở rộng replay, multi-instance và operational observability.

#### Nhược điểm

- Thêm hạ tầng, configuration/credential, error mode và test contract vượt Requirement.
- Không cần thiết khi user chỉ yêu cầu simulator local/WebSocket snapshot và repository hiện chỉ cấu hình simple broker.

#### Mức độ phù hợp với project

Không phù hợp scope; không có evidence về nhu cầu replay hoặc multi-instance trong Requirement.

#### Evidence hỗ trợ

- `EVD-001`, `EVD-003`: simple broker/topic hiện có đủ cho publish/subscribe của scope hiện tại.

## So sánh

| Tiêu chí | A: run ID + sequence | B: preserve publish order | C: relay/event store |
|---|---|---|---|
| Chặn telemetry run cũ sau Reset | Có | Không đủ | Có thể, nhưng quá mức |
| Chặn foreign Trip | Có ở frontend | Không | Có thể |
| Nhận biết out-of-order | Có, deterministic per run | Có cho client session nhưng không có run semantics | Có thể |
| Dependency/hạ tầng mới | Không | Không | Có |
| Overhead chung | Thấp, per payload | Có overhead tuần tự hóa | Cao |
| Phù hợp requirement hiện tại | Cao | Vừa | Thấp |

## Giải pháp đề xuất

**Lựa chọn:** Giải pháp A — `simulationRunId` UUID và `sequence` strictly increasing ở application; giữ broker simple `/topic` hiện có. Không bật `setPreservePublishOrder(true)` trong feature này.

**Requirement được đáp ứng:** `REQ-001`, `REQ-003`, `REQ-004`, `REQ-005`.

**Điều kiện áp dụng:** Một simulator process; command Start trả run ID; mọi branch telemetry (normal/terminal) sử dụng một builder/publish helper; frontend dùng `useRef` cho expected run/last sequence và reset các ref khi Start/Reset/trip đổi.

## Lý do lựa chọn

Tài liệu Spring nói rõ STOMP broker có thể publish đến subscriber nhưng client outbound executor không mặc định giữ receive order; chỉ dựa vào timestamp không đủ phân biệt Restart. Run identity và sequence giải quyết chính xác domain needed với chi phí nhỏ, kiểm chứng được và không buộc mọi realtime message vào publish-order setting. Đánh đổi là không có replay khi reconnect; Requirement đã xác nhận snapshot tick sau và `GET Trip` là đủ.

## Giải pháp không được chọn

| Giải pháp | Lý do không chọn | Khi nào nên xem xét lại |
|---|---|---|
| Preserve publish order toàn cục | Không thay thế run identity và có overhead được Spring nêu rõ | Có benchmark/realtime UI yêu cầu strict order mọi topic |
| Relay/event store | Mở rộng scope sang hạ tầng, persistence, credential/operation | Cần nhiều backend instance hoặc replay/audit telemetry |
| Dùng timestamp client/server để sort | Không có identity run và clock/network không là ordering authority | Không nên dùng làm primary ordering |

## Ảnh hưởng dự kiến tới Spec và Plan

- Data model/API/Event: Additive `simulationRunId`, `sequence` vào telemetry; command response typed/additive chứa run ID/state; không migration.
- Dependency/config: không có package, broker hoặc env mới.
- Testability/failure mode: test monotonic sequence, two-topic identical payload, terminal branch, UI foreign/stale/out-of-order; manual reconnect còn là snapshot-only.
- Security/operation: log ID kỹ thuật/tên state, không log full payload/secret; không lưu UUID telemetry làm dữ liệu audit.

## Câu hỏi và rủi ro còn lại

- Simple broker là in-memory/transient; reconnect không replay snapshot cũ. Đây là accepted limitation, không phải PASS về replay.
- UUID/run sequence không giải quyết hai application instance cùng chạy một Trip. Nếu deployment thay đổi, cần Research riêng về ownership/lock/broker relay.
- Test command runtime phụ thuộc Java 26/Node environment; nếu không chạy được, Evidence implementation phải ghi `INCONCLUSIVE`.

## Evidence từ nguồn bên ngoài

`EVD-001` và `EVD-002` trong `evidence.md` là external evidence đã kiểm tra. Không có URL/citation nào được suy đoán.

## Nguồn tham khảo

| ID | Evidence | Nguồn | URL | Ngày truy cập | Claim/Kết luận được hỗ trợ |
|---|---|---|---|---|---|
| `SRC-001` | `EVD-001` | Spring Framework Reference — STOMP overview | https://docs.spring.io/spring/reference/web/websocket/stomp/overview.html | 2026-09-04 | Spring STOMP hỗ trợ SEND/SUBSCRIBE pub/sub; scheduled service có thể gửi qua `SimpMessagingTemplate`. |
| `SRC-002` | `EVD-002` | Spring Framework Reference — Ordered Messages | https://docs.spring.io/spring-framework/reference/web/websocket/stomp/ordered-messages.html | 2026-09-04 | Client outbound executor có thể làm receive order khác publish order; preserve order có overhead. |
| `SRC-003` | `EVD-004` | Spring Framework Reference — Simple Broker | https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-simple-broker.html | 2026-09-04 | Simple broker giữ subscriptions và broadcast tới matching destination. |

## Checklist hoàn thành

- [x] Tất cả Research Question đã được trả lời hoặc ghi accepted limitation.
- [x] Có ít nhất hai giải pháp hợp lý được so sánh.
- [x] Đề xuất liên kết Requirement và Survey thực tế.
- [x] Trade-off/rủi ro được ghi rõ.
- [x] Claim external có URL thật, ngày truy cập và `EVD-*`.
- [x] Không có URL/citation được suy đoán hoặc chưa mở kiểm tra.
- [x] Không đưa provider/hạ tầng ngoài scope vào Plan.
