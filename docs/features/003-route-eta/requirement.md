# Requirement — 003-route-eta

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `003-route-eta` |
| Trạng thái | `READY — chờ người dùng phê duyệt trước Gemini` |
| Người tạo | Codex |
| Người xác nhận | Người dùng |
| Ngày tạo/cập nhật | 2026-09-04 |

## Tổng quan

Người điều hành cần biết xe sẽ tới từng trạm trong một chuyến đi khi nào và khi nào chuyến sẽ kết thúc. Ứng dụng hiện có lịch `TripCheckIn` và telemetry ETA một phần, nhưng không có contract/UI tường minh cho ETA hoàn thành; trạng thái kết thúc xe/chuyến/telemetry cũng chưa nhất quán. Feature này hoàn thiện ETA cho **một chuyến được simulator xử lý** mà không thay đổi chức năng quản lý tuyến hay tích hợp dữ liệu giao thông bên ngoài.

## Mục tiêu

- Hiển thị lịch dự kiến cho tất cả trạm và thời điểm hoàn thành ngay sau khi Trip được tạo, trước khi simulator phát telemetry.
- Trong khi simulator chạy, hiển thị ETA động và thời điểm đến dự kiến của mọi trạm chưa check-in, cùng ETA/thời điểm hoàn thành chuyến.
- Khi xe check-in trạm cuối, trạng thái Trip, Vehicle, telemetry và UI cùng phản ánh chuyến đã hoàn thành.

## Người dùng và bên liên quan

| Actor/Bên liên quan | Nhu cầu | Quyền hoặc giới hạn liên quan |
|---|---|---|
| Điều hành viên | Theo dõi lịch đến trạm và lúc xe hoàn thành chuyến. | Chỉ quan sát/điều khiển simulator đang có; không chọn hoặc tạo Trip mới trong feature này. |
| Simulator backend | Phát ETA theo vị trí/tốc độ/sự cố hiện tại và chốt completion. | Là nguồn ETA động duy nhất trong phạm vi feature. |
| Frontend dashboard | Hiển thị lịch tĩnh, ETA động và completion mà không đoán dữ liệu thiếu. | Chỉ nhận telemetry thuộc `currentTrip`. |

## Phạm vi

### Trong phạm vi

- Tính và trả lịch dự kiến `scheduledArrivalTime` từng trạm cùng thời điểm kết thúc theo metric tuyến khi tạo Trip.
- Tính ETA động/`estimatedArrivalTime` cho từng trạm chưa check-in theo vị trí simulator, vận tốc hiệu dụng và TrafficIncident đang active.
- Bổ sung dữ liệu ETA hoàn thành tường minh vào payload telemetry hiện có.
- Đồng bộ completion khi auto check-in trạm cuối: Trip hoàn thành, xe dừng và telemetry cuối được phát.
- Hiển thị fallback lịch tĩnh trước telemetry; hiển thị ETA động và completion khi có telemetry.
- Bổ sung test, lint/build và evidence có thể kiểm chứng.

### Ngoài phạm vi

- Tích hợp HERE, Google, Mapbox, OSRM hoặc bất kỳ provider traffic/routing bên ngoài nào.
- ETA chính xác cho GPS thật, dự báo lịch sử, machine learning, thời gian dừng tại trạm hoặc tái định tuyến.
- Tạo/chọn/hủy nhiều Trip, phân quyền, lịch biểu định kỳ, hoặc điều khiển nhiều simulator song song trên UI.
- Đổi thuật toán metric tĩnh của Route hoặc sửa CRUD Route/Station đã được duyệt.

## Yêu cầu chức năng

### REQ-001 — Lịch dự kiến khi khởi tạo Trip

**Mô tả:** Khi tạo Trip từ Route hợp lệ, hệ thống phải tạo `TripCheckIn` theo `stopOrder`; giờ dự kiến từng trạm được cộng dồn từ `startTime` và `estimatedTimeToNextMinutes`. Giờ dự kiến trạm cuối là giờ dự kiến hoàn thành ban đầu của chuyến.

**Lý do:** Điều hành viên cần có lịch trước khi xe bắt đầu giả lập.

**Độ ưu tiên:** `MUST`

**Dependency:** Route có `RouteStation` theo thứ tự và metric đã tính.

### REQ-002 — ETA động theo từng trạm

**Mô tả:** Trong simulator đang chạy, telemetry của Trip phải gồm một phần tử ETA cho mọi trạm theo `stopOrder`. Trạm đã check-in phải hiển thị giờ đến thực tế; trạm chờ phải có khoảng cách còn lại, ETA giây và giờ đến dự kiến được tính lại từ trạng thái mô phỏng hiện tại.

**Lý do:** Người dùng cần biết ảnh hưởng hiện tại của vị trí, vận tốc và sự cố đến từng trạm sau.

**Độ ưu tiên:** `MUST`

**Dependency:** REQ-001, simulator, geofencing và TrafficIncident hiện có.

### REQ-003 — ETA hoàn thành chuyến và completion nhất quán

**Mô tả:** Telemetry phải cung cấp rõ ETA giây và thời điểm dự kiến hoàn thành chuyến. Khi check-in trạm cuối, Trip phải là `COMPLETED`, `endTime` là thời điểm check-in cuối, Vehicle là `IDLE` với tốc độ `0`, và một telemetry cuối phải cho biết không còn ETA chờ.

**Lý do:** Tránh tình trạng lịch hiển thị hoàn thành nhưng dashboard vẫn coi xe/simulator đang chạy.

**Độ ưu tiên:** `MUST`

**Dependency:** REQ-002, TripCheckIn trạm cuối.

### REQ-004 — Hiển thị lịch/ETA đúng ngữ cảnh Trip

**Mô tả:** Timeline phải hiển thị lịch `TripCheckIn` và completion dự kiến khi chưa có telemetry. Khi có telemetry của `currentTrip`, Timeline phải thay bằng ETA động cho từng trạm và completion; telemetry của Trip khác không được ghi đè thông tin đang xem.

**Lý do:** Dashboard cần thông tin hữu ích cả trước khi bắt đầu và trong lúc mô phỏng.

**Độ ưu tiên:** `MUST`

**Dependency:** REQ-001..REQ-003, REST `GET /api/trips/{id}` và topic telemetry hiện có.

### REQ-005 — Tính đúng, an toàn và tương thích telemetry

**Mô tả:** ETA không được thay đổi dữ liệu lịch gốc, không có giá trị âm, và completion lặp lại không được ghi đè `endTime` đã chốt. Các field mới của telemetry phải là additive để client cũ vẫn đọc được payload cũ.

**Lý do:** Bảo toàn lịch sử check-in và tránh regression client/WebSocket.

**Độ ưu tiên:** `MUST`

**Dependency:** REQ-001..REQ-004.

### REQ-006 — Verification và Evidence

**Mô tả:** Implementation phải có test tái lập được cho lịch tĩnh, ETA động, completion/terminal telemetry và UI flow; cập nhật `evidence.md` bằng output/artifact thực tế.

**Lý do:** Các giá trị thời gian và state transition không thể được chấp nhận chỉ bằng đọc source code.

**Độ ưu tiên:** `MUST`

**Dependency:** REQ-001..REQ-005.

## Yêu cầu phi chức năng

### Performance

- Mỗi tick chỉ được đọc danh sách check-in đã sắp xếp một lần để tính ETA; không phát sinh truy vấn theo từng trạm.
- Không thêm polling frontend mới; ETA động tiếp tục dùng telemetry STOMP hiện có.

### Reliability

- Tất cả timestamps dùng cùng nguồn thời gian injectable trong các service ETA/completion để test deterministic.
- Telemetry terminal phải được phát trong tick có check-in cuối, trước khi session bị bỏ qua ở các tick sau.

### Compatibility

- Không migration database; `TripCheckIn.scheduledArrivalTime` và `actualArrivalTime` tiếp tục là source of truth lịch/đến thực tế.
- Field telemetry mới chỉ được thêm, không đổi/xóa field hiện tại.

### Observability

- Log completion tiếp tục dùng trip/vehicle ID hoặc code hiện có; không ghi secret hoặc payload nhạy cảm.

## Ràng buộc

| Nhóm | Ràng buộc | Lý do/Nguồn |
|---|---|---|
| Công nghệ | Dùng Spring Boot/JPA/STOMP và React/TypeScript hiện có; không thêm dependency. | Survey EVD-003..EVD-008. |
| Time | Giữ timezone mặc định hiện hành nhưng dùng `Clock` injectable. | Research EVD-001; tương thích `LocalDateTime.now()` hiện có. |
| API/Event | Không thêm REST endpoint; mở rộng additive `/topic/telemetry`. | Survey EVD-005..EVD-007. |
| Database | Không thêm field/table/migration. | ETA dynamic là snapshot; lịch/check-in đã có chỗ lưu. |
| Dữ liệu giao thông | Chỉ dùng `TrafficIncident` active của simulator. | Không có provider routing/traffic ngoài repository (Survey EVD-007). |

## Business Rules đã biết

- `scheduledArrivalTime` của stop đầu bằng `Trip.startTime`; mỗi stop sau cộng thời lượng chặng đứng trước.
- ETA động cho trạm PENDING là tổng khoảng cách còn lại theo thứ tự route chia cho tốc độ hiệu dụng; ETA completion là ETA của trạm cuối PENDING.
- Hệ số tua nhanh là điều khiển playback simulator, không phải thay đổi vận tốc vật lý được báo cáo; feature không dùng multiplier để sửa ETA nghiệp vụ.
- Stop đã `CHECKED_IN` luôn có ETA bằng `0`, khoảng cách còn lại bằng `0` và giờ hiển thị là `actualArrivalTime`.
- Completion idempotent: lần completion đầu tiên chốt `endTime`; yêu cầu sau không được ghi đè timestamp này.

## Acceptance Criteria

### AC-REQ-001-01 — Tạo lịch dự kiến đầy đủ

```text
Given Route có START, STOP và END với estimatedTimeToNextMinutes xác định
When tạo Trip tại thời điểm T
Then TripCheckIn được tạo theo stopOrder tăng dần
And scheduledArrivalTime của START bằng T
And scheduledArrivalTime của END bằng T cộng tổng thời lượng các chặng
```

### AC-REQ-002-01 — Tính ETA động cho các stop pending

```text
Given simulator của một Trip đang ở vị trí P và có một số stop PENDING
When simulator phát telemetry
Then stationsEta chứa đúng một item cho mọi stop theo stopOrder
And item PENDING có distanceRemainingMeters không âm, etaSeconds không âm và estimatedArrivalTime
And ETA của stop sau không nhỏ hơn ETA của stop pending đứng trước
```

### AC-REQ-002-02 — Bảo toàn check-in đã xảy ra

```text
Given một TripCheckIn đã CHECKED_IN tại thời điểm A
When simulator tính telemetry mới
Then item của stop đó có status CHECKED_IN, etaSeconds = 0 và estimatedArrivalTime = A
```

### AC-REQ-003-01 — ETA completion và tick cuối

```text
Given telemetry có stop cuối đang PENDING
When simulator tính ETA
Then etaSecondsToCompletion và estimatedCompletionTime bằng ETA/thời điểm của stop cuối
When xe auto check-in stop cuối
Then Trip là COMPLETED, endTime bằng actualArrivalTime của stop cuối, Vehicle là IDLE/tốc độ 0
And telemetry cuối được phát với tripStatus COMPLETED, ETA completion bằng 0 và không còn target pending
```

### AC-REQ-004-01 — Timeline có fallback và cập nhật realtime

```text
Given currentTrip đã có checkIns nhưng chưa có telemetry
When Timeline render
Then từng stop hiển thị scheduledArrivalTime và completion dự kiến là giờ stop cuối
Given telemetry của chính currentTrip đến
Then Timeline hiển thị ETA động/completion từ telemetry
And telemetry thuộc Trip khác không thay thế state đang xem
```

### AC-REQ-005-01 — Tương thích và idempotency

```text
Given client chỉ dùng các field telemetry cũ
When backend thêm field ETA completion và tripStatus
Then các field cũ giữ tên và kiểu dữ liệu cũ
Given completion bị xử lý lại sau khi Trip đã COMPLETED
When completeTrip được gọi
Then endTime đã có không đổi
```

### AC-REQ-006-01 — Evidence thực thi

```text
Given Gemini hoàn thành implementation
When chạy test backend, lint/type-check/build frontend và manual UI flow
Then evidence.md có command/actual result/artifact tương ứng
And không Requirement MUST nào được ghi PASS nếu evidence chưa chạy
```

## Giả định và dependency

- Route và Trip hiện có là dữ liệu hợp lệ theo feature 001/002; feature không sửa route topology.
- ETA là ước lượng cho simulator/current traffic incident, không phải cam kết thời gian đến thực tế ngoài đời.
- `currentTrip` vẫn là Trip đầu tiên được App nạp; selection UI nhiều Trip không thuộc feature này.
- Không có câu hỏi mở làm thay đổi public contract hoặc data model; các giới hạn trên là quyết định scope của feature.

## Rủi ro sản phẩm

| Rủi ro | Khả năng | Ảnh hưởng | Cách giảm thiểu/Xác nhận cần thiết |
|---|---|---|---|
| Người dùng hiểu ETA simulator là traffic thật | Vừa | Cao | Gắn rõ phạm vi trong UI/tài liệu; không gọi nó là HERE/live traffic. |
| Telemetry terminal không phát | Vừa | Cao | TC completion kiểm tra cả DB state, Vehicle state và message STOMP. |
| Lệch thời gian làm test flaky | Vừa | Vừa | Clock injectable/fixed trong unit test theo Research EVD-001. |
| Telemetry Trip khác ghi đè UI | Thấp | Vừa | Lọc theo `currentTrip.id`, TC UI/manual kiểm tra. |

## Câu hỏi chưa được giải quyết

Không có câu hỏi chặn. Những giới hạn về real traffic, multi-trip và route rerouting đã được đưa vào ngoài phạm vi.

## Xác nhận Requirement

- [x] Mọi Requirement có ID và độ ưu tiên.
- [x] Trong/ngoài phạm vi rõ ràng.
- [x] Acceptance Criteria đo được và có edge case phù hợp.
- [x] Mỗi Requirement quan trọng có thể truy vết tới Evidence sau implementation.
- [x] Câu hỏi ảnh hưởng thiết kế đã được giải quyết bằng scope nêu trên.
