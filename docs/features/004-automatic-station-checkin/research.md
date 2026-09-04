# Research — 004-automatic-station-checkin

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `004-automatic-station-checkin` |
| Requirement liên quan | `REQ-001..REQ-005` |
| Trạng thái | `READY — chờ người dùng review Plan trước Gemini` |
| Người thực hiện | Codex |
| Ngày nghiên cứu | 2026-09-04 |

## Mục tiêu nghiên cứu

Xác định cách phát hiện xe vào geofence, nơi lưu trạng thái và cách phát event mà không thêm provider, dependency hoặc hệ thống lưu trữ mới. Nghiên cứu tập trung vào các lựa chọn có thể triển khai trong repository hiện tại.

## Phạm vi nghiên cứu

### Trong phạm vi

- Thuật toán đo khoảng cách trong phạm vi bán kính trạm.
- Nguồn dữ liệu radius và trạng thái check-in.
- Transaction, thời gian và event realtime.
- Cách kiểm thử tọa độ biên, thứ tự, lặp và simulator waypoint.

### Ngoài phạm vi

- So sánh nhà cung cấp bản đồ/routing hoặc traffic API.
- PostGIS/spatial database, Redis Stream và message broker bên ngoài.
- GPS hardware, map matching và chống nhiễu nâng cao.

## Các câu hỏi cần trả lời

| ID | Câu hỏi | Requirement liên quan | Tiêu chí để trả lời |
|---|---|---|---|
| RQ-001 | Có thể dùng phép đo khoảng cách hiện có không? | REQ-001, REQ-005 | Có hàm repository-local trả về mét và test hiện có |
| RQ-002 | Radius và trạng thái check-in đã có ở đâu? | REQ-001, REQ-002 | Entity/DTO/repository có field/query phù hợp |
| RQ-003 | Luồng transaction và event hiện tại có thể tái sử dụng không? | REQ-004, REQ-005 | Service, annotation và STOMP topic/payload thực tế |
| RQ-004 | Có thể làm timestamp deterministic cho test không? | REQ-001, REQ-005 | Bean `Clock` hoặc convention tương đương trong source |
| RQ-005 | Simulator hiện gửi đủ vị trí để bắt mọi trạm chưa? | REQ-003 | Trace `startSimulation`, waypoint và tick thực tế |

## Tiêu chí đánh giá

| Tiêu chí | Mức quan trọng | Cách đánh giá |
|---|---|---|
| Correctness khoảng cách | Bắt buộc | Kết quả mét, điều kiện `<= radius`, test boundary |
| Bảo toàn thứ tự/idempotency | Bắt buộc | Query ordered và test gọi lặp/skip |
| Tương thích repository | Cao | Không thêm dependency/API/schema nếu thành phần hiện có đủ |
| Testability | Cao | Có thể inject fixed `Clock`, mock repository/messaging |
| Reliability | Cao | Không làm hỏng simulator khi no-op hoặc input lỗi |

## Các giải pháp được xem xét

### Giải pháp A — Dùng `GeoUtil` Haversine trong backend

#### Cách hoạt động

Backend tính khoảng cách từ tọa độ xe tới tọa độ `Station` bằng `GeoUtil.calculateDistanceMeters`, sau đó so sánh với `Station.radiusMeters`. Service chọn `TripCheckIn` đầu tiên có status `PENDING` theo `stopOrder`.

#### Ưu điểm

- Không thêm dependency hoặc external call.
- Đơn vị mét phù hợp trực tiếp với radius hiện có.
- Có thể unit test deterministic bằng tọa độ cố định.

#### Nhược điểm

- Khoảng cách đường chim bay, chưa phải khoảng cách theo đường xe chạy.
- Không xử lý map matching/GPS noise nâng cao.

#### Rủi ro

- Radius nhỏ và GPS nhiễu có thể làm check-in sớm hoặc muộn.
- Cần từ chối tọa độ không hữu hạn để tránh quyết định không xác định.

#### Mức độ phù hợp với project

Phù hợp cho geofence bán kính nhỏ của simulator hiện tại; route/map matching không thuộc Requirement này.

#### Evidence hỗ trợ

- `EVD-002`, `EVD-003` trong `evidence.md`.

#### Prototype/kiểm chứng nếu có

- Thao tác/command: đọc `GeoUtil.java` và `GeoUtilTest.java`; chạy `GeoUtilTest` khi Maven dependency sẵn sàng.
- Kết quả: source và test hiện có xác nhận hàm tính khoảng cách theo mét và test khoảng cách thực tế.
- Giới hạn: baseline test full backend chưa chạy được trong môi trường khảo sát do Maven không resolve dependency.

### Giải pháp B — Dùng spatial database hoặc routing provider

#### Cách hoạt động

Đưa tọa độ vào PostGIS hoặc gọi external routing/geospatial API để truy vấn khoảng cách và trạng thái vùng.

#### Ưu điểm

- Có thể mở rộng cho spatial query, map matching hoặc khoảng cách theo đường.
- Có thể tối ưu khi dữ liệu vị trí rất lớn nếu thiết kế đầy đủ.

#### Nhược điểm

- Thêm schema/provider, credential, failure mode và latency.
- Không cần thiết cho một phép kiểm tra bán kính nhỏ trên simulator hiện tại.

#### Rủi ro

- Vendor lock-in, chi phí/quota và test phụ thuộc external service.
- Scope tăng sang migration, adapter và vận hành.

#### Mức độ phù hợp với project

Chưa phù hợp với Requirement hiện tại vì repository đã có phép đo khoảng cách local và chưa có spatial boundary.

#### Evidence hỗ trợ

- Không có evidence repository cho nhu cầu spatial provider; đây là phương án bị loại do scope, không phải claim về một dịch vụ bên ngoài.

#### Prototype/kiểm chứng nếu có

- Không thực hiện; không có external service trong scope.

## So sánh

| Tiêu chí | Giải pháp A: `GeoUtil` | Giải pháp B: spatial/provider | Nhận xét |
|---|---|---|---|
| Correctness bán kính nhỏ | Đủ với Haversine | Có thể cao hơn nhưng chưa cần | A đáp ứng AC hiện tại |
| Dependency/operation | Không thêm | Thêm DB/provider/quota | A ít rủi ro hơn |
| Testability offline | Cao | Cần mock/contract service | A phù hợp môi trường hiện tại |
| Mở rộng map matching | Hạn chế | Tốt hơn | Để feature tương lai |
| Scope | Chỉ service/reuse code | Đổi kiến trúc và vận hành | A đúng phạm vi |

## Giải pháp đề xuất

**Lựa chọn:** Giải pháp A — dùng `GeoUtil` trong `GeofencingService`, kết hợp `TripCheckInRepository`, `Clock`, `SimpMessagingTemplate` và waypoint hiện có.

**Requirement được đáp ứng:** `REQ-001..REQ-005`.

**Điều kiện áp dụng:** `Station.radiusMeters` là dữ liệu mét hợp lệ theo validation hiện có; vị trí xe phải là số hữu hạn trong giới hạn latitude/longitude; test phải dùng fixed `Clock`.

## Lý do lựa chọn

Giải pháp A đạt hành vi yêu cầu với ít thay đổi nhất và giữ đúng convention backend hiện tại. Trade-off là khoảng cách không tính theo đường thực tế và không có chống nhiễu GPS nâng cao; các khả năng đó không nằm trong yêu cầu check-in simulator và sẽ cần Research riêng nếu đưa GPS thật vào hệ thống.

## Giải pháp không được chọn

| Giải pháp | Lý do không chọn | Khi nào nên xem xét lại |
|---|---|---|
| Spatial database/provider | Thêm dependency, failure mode và scope chưa được yêu cầu | Khi cần geofence hàng triệu điểm, map matching hoặc GPS thật quy mô lớn |
| Tính geofence ở frontend | Frontend không phải source of truth và client có thể bị mất/kết nối sai | Không dùng cho quyết định persist; chỉ có thể dùng để preview UI |

## Ảnh hưởng dự kiến tới Spec và Plan

- Data model/API/Event: tái sử dụng `TripCheckIn`, không thêm bảng/cột; giữ `/topic/checkins` và payload hiện có.
- Dependency/config: không thêm dependency/config; dùng `Clock` bean hiện tại.
- Testability/failure mode: bổ sung test service/simulator cho boundary, no-op, thứ tự, lặp và input lỗi.
- Security/operation: không thêm endpoint; log auto check-in không chứa secret.

## Câu hỏi và rủi ro còn lại

- Current simulator chưa gọi geofence cho waypoint đầu khi khởi động; đây là gap phải xử lý trong Spec/Plan (`EVD-005`).
- Current service chưa có kiểm tra rõ tọa độ hữu hạn; Spec cần quy định no-op an toàn (`EVD-007`).
- Current query/save chưa có distributed lock; scope hiện tại chỉ bảo đảm idempotency tuần tự trong một simulator process. Nếu bổ sung telemetry đa tiến trình, phải mở Research concurrency riêng.

## Evidence từ nguồn bên ngoài

Không có quyết định trong feature phụ thuộc nguồn bên ngoài. Feature dùng thuật toán và contract đã có trong repository; không tạo URL/citation bên ngoài chưa được kiểm chứng.

## Nguồn tham khảo

| ID | Evidence | Nguồn | URL | Ngày truy cập | Claim/Kết luận được hỗ trợ |
|---|---|---|---|---|---|
| — | — | Không áp dụng | Không áp dụng | — | Không sử dụng nguồn bên ngoài |

## Checklist hoàn thành

- [x] Tất cả Research Question đã được trả lời hoặc ghi blocker.
- [x] Có ít nhất hai giải pháp khi thực sự tồn tại lựa chọn hợp lý.
- [x] Đề xuất dựa trên tiêu chí liên kết Requirement.
- [x] Trade-off và rủi ro được ghi rõ.
- [x] Nguồn bên ngoài không áp dụng; không có URL/citation chưa kiểm chứng.
- [x] Claim quan trọng về repository có `EVD-*` trong `evidence.md`.
- [x] Không có URL/citation được suy đoán hoặc chưa mở kiểm tra.
- [x] Không Research hoặc tạo abstraction cho nhu cầu ngoài scope.
