# Requirement — 004-automatic-station-checkin

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `004-automatic-station-checkin` |
| Trạng thái | `READY — chờ người dùng review Plan trước Gemini` |
| Người tạo | Codex |
| Người xác nhận | Người dùng |
| Ngày tạo/cập nhật | 2026-09-04 |

## Tổng quan

Trong một chuyến đi, hệ thống cần tự ghi nhận xe đã đến trạm khi vị trí xe đi vào vùng địa lý quanh trạm. Việc ghi nhận phải áp dụng cho trạm `START`, các trạm `STOP` và trạm `END`, theo đúng thứ tự của lịch trình. Người điều hành cần nhìn thấy trạng thái check-in mới trên dashboard realtime mà không phải thao tác thủ công.

Feature này hoàn thiện luồng check-in tự động trong simulator hiện có. Nguồn vị trí GPS thật bên ngoài, định tuyến mới và quản lý trạm/tuyến là phạm vi của feature khác hoặc nằm ngoài feature này.

## Mục tiêu

- Tự chuyển đúng `TripCheckIn` tiếp theo sang `CHECKED_IN` khi xe ở trong bán kính geofence của trạm.
- Ghi `actualArrivalTime` bằng thời gian server có thể kiểm thử deterministically.
- Không bỏ qua trạm, không ghi nhận hoặc phát event lặp lại do cùng một vị trí được xử lý nhiều lần.
- Phát sự kiện check-in cho dashboard realtime và cô lập sự kiện của các chuyến khác.
- Khi trạm cuối được check-in, giữ nhất quán trạng thái hoàn thành của chuyến và xe đang được simulator sử dụng.

## Người dùng và bên liên quan

| Actor/Bên liên quan | Nhu cầu | Quyền hoặc giới hạn liên quan |
|---|---|---|
| Điều hành viên | Theo dõi xe đã đi qua trạm nào | Chỉ xem trạng thái của chuyến đang chọn trên dashboard |
| Simulator/backend | Gửi các vị trí xe và xử lý geofence | Không được tự bỏ qua thứ tự trạm |
| Frontend dashboard | Hiển thị thông báo check-in mới | Không được để event của Trip khác ghi đè UI hiện tại |
| Database | Lưu lịch sử check-in của từng chuyến | Một `TripCheckIn` chỉ chuyển trạng thái một lần từ `PENDING` |

## Phạm vi

### Trong phạm vi

- Quyết định xe có ở trong geofence dựa trên tọa độ xe, tọa độ trạm và `Station.radiusMeters`.
- Chọn trạm `PENDING` có `stopOrder` nhỏ nhất của chuyến.
- Cập nhật trạng thái, thời điểm đến thực tế và event realtime.
- Gọi luồng completion hiện có sau khi trạm cuối cùng được ghi nhận.
- Tích hợp với các waypoint của `SimulatorService`, bao gồm waypoint đầu, waypoint giữa và waypoint cuối.
- Unit/service test, test tích hợp cần thiết và manual verification của event/UI.

### Ngoài phạm vi

- Tạo/sửa/xóa trạm hoặc tuyến đường (`001-station-management`, `002-route-management`).
- Thu thập GPS từ thiết bị thật hoặc thêm REST endpoint telemetry mới.
- Tính ETA hoặc xử lý sự cố giao thông (`003-route-eta` và các feature liên quan).
- Bản đồ, routing provider, PostGIS, Redis queue hoặc cơ chế replay event.
- Check-in thủ công, skip trạm và thay đổi quyền authentication/authorization.

## Yêu cầu chức năng

### REQ-001 — Check-in khi vào geofence

**Mô tả:** Khi vị trí xe của một chuyến nằm trong hoặc đúng biên bán kính geofence của trạm `START`, `STOP` hoặc `END` tiếp theo, hệ thống phải tự động ghi nhận trạm đó.

**Lý do:** Ghi nhận hành trình mà không cần nhân viên hoặc tài xế thao tác thủ công.

**Độ ưu tiên:** `MUST`

**Dependency:** `TripCheckIn`, `Station` và vị trí từ simulator hiện có.

### REQ-002 — Tuân thủ thứ tự và không ghi nhận trùng

**Mô tả:** Hệ thống chỉ được check-in trạm `PENDING` có `stopOrder` nhỏ nhất; việc gọi lại với cùng vị trí sau khi đã check-in không được tạo lần lưu hoặc event check-in thứ hai.

**Lý do:** Bảo toàn tính hợp lệ của lịch trình và tránh số liệu, toast hoặc completion bị lặp.

**Độ ưu tiên:** `MUST`

**Dependency:** `TripCheckInRepository` phải trả kết quả theo `stopOrder`.

### REQ-003 — Tích hợp đầy đủ với simulator

**Mô tả:** Simulator phải đưa vị trí tại trạm xuất phát vào luồng geofence trước khi xe rời trạm và phải kiểm tra mọi waypoint đi qua trong một tick, kể cả khi tốc độ/multiplier làm xe nhảy qua nhiều waypoint.

**Lý do:** Nếu chỉ kiểm tra vị trí cuối tick, trạm đầu hoặc trạm giữa có thể không bao giờ được check-in.

**Độ ưu tiên:** `MUST`

**Dependency:** `SimulatorService` và danh sách waypoint từ route.

### REQ-004 — Persist và thông báo realtime

**Mô tả:** Sau khi check-in thành công, hệ thống phải lưu trạng thái/thời điểm thực tế và phát `CheckInEvent` trên topic hiện có để frontend hiển thị thông báo cho đúng chuyến.

**Lý do:** Điều hành viên cần thấy thay đổi gần realtime và có thể đối chiếu với dữ liệu lưu trữ.

**Độ ưu tiên:** `MUST`

**Dependency:** STOMP topic `/topic/checkins`, `CheckInEventDto`, frontend `WebSocketService`.

### REQ-005 — Xử lý biên và tính nhất quán

**Mô tả:** Vị trí ngoài geofence, không còn trạm `PENDING`, tọa độ không hợp lệ và dữ liệu trạm không hợp lệ phải không làm thay đổi check-in hoặc phát event sai; check-in trạm cuối phải dùng completion path hiện có.

**Lý do:** Tránh check-in giả do dữ liệu lỗi và tránh trạng thái Trip/Vehicle không nhất quán.

**Độ ưu tiên:** `MUST`

**Dependency:** `GeoUtil`, `TripService#completeTrip`, validation trạm hiện có.

## Yêu cầu phi chức năng

### Performance

- Xử lý một vị trí bằng tối đa một query tìm trạm `PENDING` trước khi quyết định; không gọi external service.
- Tick simulator hiện có phải tiếp tục xử lý các session khác nếu một vị trí không hợp lệ hoặc geofence không match.

### Security

- Không thêm credential, API key hoặc endpoint nhận dữ liệu không được xác thực.
- Chỉ dùng dữ liệu định danh chuyến/xe/trạm đã có trong server; log không ghi secret.

### Reliability

- Chuyển trạng thái chỉ từ `PENDING` sang `CHECKED_IN` và ghi `actualArrivalTime` cùng operation.
- Gọi lặp tuần tự sau khi đã check-in phải là no-op; không phát duplicate event.

### Compatibility

- Giữ nguyên REST simulator và payload field hiện có; event check-in dùng topic/payload đang được frontend subscribe.
- Frontend không được hiển thị event của Trip khác.

### Observability

- Ghi log có thể tìm lại khi auto check-in thành công hoặc tọa độ bị bỏ qua; không ghi token/secret.

## Ràng buộc

| Nhóm | Ràng buộc | Lý do/Nguồn |
|---|---|---|
| Công nghệ | Tái sử dụng Spring service/JPA, `GeoUtil` và STOMP hiện có | Survey repository, EVD-002..EVD-005 |
| Database | Không thêm bảng/cột cho feature này | `TripCheckIn` đã có status và actual arrival |
| API/Event | Không thêm REST check-in; dùng `/topic/checkins` | Contract hiện có ở backend/frontend |
| Compatibility | Không đổi shape bắt buộc của `CheckInEventDto` | Frontend `CheckInEvent` đang parse payload hiện có |
| Thời gian | Dùng `Clock` bean được inject | Cho phép test timestamp deterministic |

## Business Rules đã biết

- Trạm được check-in khi khoảng cách tính từ tọa độ xe tới tâm trạm nhỏ hơn hoặc bằng bán kính trạm.
- Chỉ trạm tiếp theo theo `stopOrder` được check-in; không được check-in trạm sau trước trạm trước.
- Trạm đã `CHECKED_IN` không được lưu/event lại bởi cùng luồng tự động.
- Khi không còn check-in `PENDING` sau khi xử lý trạm cuối, phải ủy nhiệm cho `TripService` xử lý completion.

## Acceptance Criteria

### AC-REQ-001-01 — Check-in trong và đúng biên geofence

```text
Given một Trip có trạm tiếp theo PENDING và Station có radius hợp lệ
When vị trí xe có khoảng cách tới tâm trạm nhỏ hơn hoặc bằng radius
Then TripCheckIn của trạm đó chuyển sang CHECKED_IN
And actualArrivalTime được ghi bằng thời gian server
```

### AC-REQ-001-02 — Không check-in ngoài geofence

```text
Given một Trip có trạm tiếp theo PENDING
When vị trí xe nằm ngoài radius của trạm
Then TripCheckIn vẫn là PENDING
And không phát CheckInEvent
```

### AC-REQ-002-01 — Không bỏ qua thứ tự

```text
Given trạm A stopOrder 1 PENDING và trạm B stopOrder 2 PENDING
When xe được gửi tới tọa độ của B trước A
Then A vẫn PENDING
And B không được CHECKED_IN
```

### AC-REQ-002-02 — Lặp vị trí là idempotent theo state

```text
Given một lần gọi đã chuyển trạm A sang CHECKED_IN
When cùng trip và tọa độ được xử lý lại
Then không có lần save/event check-in thứ hai cho A
```

### AC-REQ-003-01 — Check-in START trước khi rời trạm

```text
Given Trip mới có START là PENDING và simulator khởi động tại tọa độ START
When simulator bắt đầu hoặc tick đầu tiên chạy
Then START được check-in trước khi luồng chỉ còn xét các trạm sau
```

### AC-REQ-003-02 — Không bỏ sót waypoint trung gian

```text
Given simulator tăng currentWaypointIndex qua nhiều waypoint trong một tick
When các waypoint trung gian có tọa độ trạm
Then geofence được kiểm tra theo thứ tự cho từng waypoint đã đi qua
```

### AC-REQ-004-01 — Event và UI đúng Trip

```text
Given check-in được persist thành công
When backend phát trên /topic/checkins
Then payload chứa tripId, stationId, stopOrder và checkInTime đúng dữ liệu đã lưu
And dashboard hiển thị toast cho currentTrip tương ứng
```

### AC-REQ-004-02 — Không rò event giữa các Trip

```text
Given dashboard đang xem Trip A
When frontend nhận CheckInEvent của Trip B
Then timeline, bản đồ và toast của Trip A không thay đổi bởi event của Trip B
```

### AC-REQ-005-01 — Dữ liệu lỗi không tạo check-in giả

```text
Given tọa độ xe hoặc tọa độ/radius trạm không hợp lệ, hoặc Trip không còn PENDING
When geofence được xử lý
Then không save TripCheckIn và không phát CheckInEvent sai
And simulator không bị dừng xử lý toàn bộ session chỉ vì input đó
```

### AC-REQ-005-02 — Hoàn thành sau trạm cuối

```text
Given trạm cuối là PENDING và các trạm trước đã CHECKED_IN
When xe vào geofence trạm cuối
Then trạm cuối được lưu CHECKED_IN
And TripService.completeTrip được gọi với cùng thời điểm check-in
```

## Giả định và dependency

- `TripService#createTrip` khởi tạo một `TripCheckIn` cho từng `RouteStation`; feature không thay đổi cách tạo Trip.
- Simulator là nguồn vị trí được kiểm chứng trong scope hiện tại; chưa có telemetry ingestion từ thiết bị thật.
- `StationService` đã giới hạn radius ở 30–150 mét cho dữ liệu qua DTO; geofence vẫn phải xử lý an toàn nếu gặp dữ liệu legacy không hợp lệ.
- STOMP broker hiện có thể phát event transient; feature không yêu cầu replay event sau reconnect.

## Rủi ro sản phẩm

| Rủi ro | Khả năng | Ảnh hưởng | Cách giảm thiểu/Xác nhận cần thiết |
|---|---|---|---|
| GPS nhiễu làm xe nằm trong bán kính quá sớm | Vừa | Vừa | Cho phép cấu hình radius theo từng Station trong giới hạn hiện có; test boundary rõ ràng |
| Gọi đồng thời từ nhiều nguồn tạo duplicate | Thấp trong simulator hiện tại | Cao | Giữ chuyển trạng thái theo `PENDING`, kiểm tra concurrency trong review; nếu mở telemetry thật cần bổ sung lock/idempotency phân tán |
| Event gửi trước khi transaction commit | Thấp | Vừa | Event chỉ phát sau `save`; ghi giới hạn này trong evidence/review và không thêm queue ngoài scope |
| Không có backend dependency cache/network | Vừa | Vừa | Gemini phải ghi `INCONCLUSIVE` nếu Maven không chạy được, không suy đoán PASS |

## Câu hỏi chưa được giải quyết

| ID | Câu hỏi | Người quyết định | Ảnh hưởng nếu chưa trả lời | Trạng thái |
|---|---|---|---|---|
| Q-001 | Có cần nhận GPS thật ngoài simulator trong feature này không? | Người dùng | Làm đổi API ingestion và kiến trúc realtime | `RESOLVED — ngoài phạm vi feature này` |
| Q-002 | Có cần cơ chế replay event sau reconnect không? | Người dùng | Làm đổi event store/REST contract | `RESOLVED — ngoài phạm vi; DB/GET Trip là nguồn đọc lại` |

## Xác nhận Requirement

- [x] Mọi Requirement có ID và độ ưu tiên.
- [x] Trong/ngoài phạm vi rõ ràng.
- [x] Acceptance Criteria đo được và có edge case phù hợp.
- [x] Mỗi Requirement quan trọng có thể truy vết tới Evidence sau implementation.
- [x] Yêu cầu phi chức năng liên quan đã được ghi nhận.
- [x] Không chứa giải pháp kỹ thuật chưa được Research/Survey xác minh.
- [x] Câu hỏi ảnh hưởng thiết kế đã được giải quyết hoặc đánh dấu blocker.
