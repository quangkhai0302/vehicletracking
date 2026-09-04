# Requirement — 005-realtime-vehicle-simulator

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `005-realtime-vehicle-simulator` |
| Trạng thái | `READY — chờ người dùng review Plan trước Gemini` |
| Người tạo | Codex |
| Người xác nhận | Người dùng |
| Ngày tạo/cập nhật | 2026-09-04 |

## Tổng quan

Dashboard đã có nút điều khiển simulator, bản đồ và kênh STOMP telemetry, nhưng người điều hành chưa có một contract đầy đủ để tin rằng xe mô phỏng di chuyển đúng trạng thái, dữ liệu realtime mới hơn không bị ghi đè bởi message cũ, và Reset thực sự dừng chuyến mô phỏng. Feature này hoàn thiện simulator cục bộ cho một chuyến đi và luồng cập nhật WebSocket để vị trí, vận tốc, hướng, ETA và trạng thái hiển thị nhất quán trên bản đồ/timeline.

`003-route-eta` là owner của công thức ETA và `004-automatic-station-checkin` là owner của transition check-in. Feature này chỉ điều phối các capability đó trong một phiên mô phỏng và làm contract realtime/control đáng tin cậy.

## Mục tiêu

- Người điều hành có thể start, pause, resume, đổi tốc độ và reset một simulator của Trip qua UI hiện có.
- Mỗi tick của phiên đang chạy phát snapshot telemetry đầy đủ, đúng phiên và đúng thứ tự để Map, Timeline và tốc độ xe cùng cập nhật.
- Pause không làm xe di chuyển; Reset khôi phục trạng thái mô phỏng ban đầu và không để scheduler tiếp tục tick phiên cũ.
- Message của Trip/phiên khác, hoặc message sequence cũ, không được ghi đè dashboard đang xem.
- Lỗi của một session không làm ngừng tick của session khác trong cùng process.

## Người dùng và bên liên quan

| Actor/Bên liên quan | Nhu cầu | Quyền hoặc giới hạn liên quan |
|---|---|---|
| Điều hành viên | Điều khiển xe giả lập và thấy xe di chuyển realtime | Dashboard hiện chọn một `currentTrip`; chỉ render telemetry khớp Trip/phiên đó |
| Simulator backend | Duy trì state một phiên/Trip, cập nhật Vehicle và phát snapshot | Không là nguồn GPS thật, không chạy distributed simulator |
| Frontend dashboard | Hiển thị marker, vận tốc, ETA/timeline và lỗi điều khiển | Không tự tính/persist vị trí hay ETA nghiệp vụ |
| `003-route-eta` / `004-automatic-station-checkin` | Cung cấp ETA và auto check-in | Không đổi công thức ETA hoặc rule geofence trong feature này |

## Phạm vi

### Trong phạm vi

- State machine simulator cho `IDLE`, `RUNNING`, `PAUSED`, `COMPLETED` và REST control hiện có.
- Sinh định danh phiên mô phỏng và sequence tăng dần trên telemetry để client lọc message stale/out-of-order.
- Di chuyển theo waypoint của tuyến, multiplier hợp lệ, incident speed factor, persistence snapshot Vehicle và phát STOMP.
- Reset kiểm soát được: dừng/xóa session, khôi phục check-in/Trip/Vehicle về trạng thái sẵn sàng mô phỏng, không còn tick cũ.
- Xử lý lỗi từng session trong scheduler, validation/error response control, frontend state/reconnect/UX cho control.
- Backend unit/controller test, frontend lint/type-check/build và manual realtime verification có evidence.

### Ngoài phạm vi

- GPS thật, map matching, routing provider, traffic feed thật, rerouting hoặc cảnh báo sự cố mới.
- Thay đổi quản lý Station/Route (`001`, `002`), công thức ETA (`003`) hay business rule auto check-in (`004`).
- Multi-instance ownership/leader election, broker relay, event persistence/replay hoặc đảm bảo exactly-once qua mạng.
- Authentication/authorization mới, quyền theo người dùng, database migration hoặc thêm dependency/test framework frontend.

## Yêu cầu chức năng

### REQ-001 — Vòng đời phiên simulator

**Mô tả:** Hệ thống phải tạo một phiên mô phỏng mới cho Trip hợp lệ có ít nhất hai trạm, khởi đầu ở trạm đầu; hỗ trợ `RUNNING`, `PAUSED`, `COMPLETED`, và chỉ cho phép một session active cho mỗi Trip. Start, pause, resume, reset phải trả trạng thái thực tế thay vì trả thành công khi không có thay đổi.

**Lý do:** Điều hành viên cần control có ý nghĩa, không có “thành công giả” khi Trip/session không tồn tại hoặc state không phù hợp.

**Độ ưu tiên:** `MUST`

**Dependency:** Route/Trip/Vehicle đã được tạo; `SimulatorService`, `SimulatorController` hiện có.

### REQ-002 — Di chuyển và snapshot xe mô phỏng

**Mô tả:** Trong trạng thái `RUNNING`, simulator phải tiến dọc waypoint theo base speed, traffic incident speed factor và multiplier; ở mỗi tick cập nhật vị trí, vận tốc, hướng và trạng thái `Vehicle`, đồng thời gọi luồng ETA/check-in hiện có theo đúng thứ tự.

**Lý do:** Bản đồ và timeline phải phản ánh cùng một trạng thái xe mô phỏng thay vì chỉ có control UI.

**Độ ưu tiên:** `MUST`

**Dependency:** `003-route-eta`, `004-automatic-station-checkin`, `TrafficIncidentRepository`, `GeoUtil`.

### REQ-003 — Điều khiển tốc độ và Reset nhất quán

**Mô tả:** Frontend chỉ được chọn multiplier `1`, `2`, `5`, `10`; backend phải xác thực cùng danh sách và từ chối input không hữu hạn/không được hỗ trợ. Reset phải dừng session, xóa telemetry UI hiện tại, đưa Vehicle về tọa độ trạm đầu với `IDLE`/speed `0`, đặt lại check-in thành `PENDING`, và đưa Trip trở lại trạng thái có thể mô phỏng.

**Lý do:** Reset và multiplier là thao tác simulator; trạng thái lưu trữ, scheduler và UI phải không mâu thuẫn.

**Độ ưu tiên:** `MUST`

**Dependency:** `TripCheckInRepository`, `TripRepository`, `VehicleRepository`, UI `SimulatorPanel`.

### REQ-004 — Telemetry WebSocket có thứ tự và cô lập phiên

**Mô tả:** Mỗi telemetry mới phải chứa `tripId`, `simulationRunId` và `sequence` tăng dần trong một run. Backend phải phát cùng snapshot lên `/topic/telemetry` và `/topic/vehicle/{vehicleId}`. Frontend chỉ nhận snapshot đúng `currentTrip`, đúng run mà command response xác nhận, và có `sequence` lớn hơn snapshot đã render; snapshot sai/thiếu identity hoặc cũ phải bị bỏ qua.

**Lý do:** STOMP/WebSocket có thể reconnect và delivery không đảm bảo client nhận đúng publish order; UI không được nhảy lùi vị trí hoặc hoàn thành nhầm.

**Độ ưu tiên:** `MUST`

**Dependency:** Spring STOMP broker, `VehicleTelemetryDto`, `WebSocketService`, `App.tsx`.

### REQ-005 — Hoàn thành, lỗi và cô lập session

**Mô tả:** Khi toàn bộ check-in hoàn thành, simulator phải phát đúng một terminal telemetry `COMPLETED`/`IDLE`, rồi không tick/phát thêm session đó. Nếu một session ném exception trong scheduler, backend phải log an toàn theo Trip và tiếp tục session kế tiếp. Frontend phải hiển thị lỗi REST control và không tạo reconnect/subscriber trùng khi state UI thay đổi.

**Lý do:** Tránh missing telemetry của xe khác, duplicate completion và dashboard im lặng khi control thất bại.

**Độ ưu tiên:** `MUST`

**Dependency:** Scheduler `SimulatorService`, `SimpMessagingTemplate`, `parseErrorMessage`, `WebSocketService`.

## Yêu cầu phi chức năng

### Performance

- Với mỗi session `RUNNING`, mỗi tick 1 giây phát tối đa một telemetry logical snapshot; không thêm polling frontend hay external call.
- Một tick chỉ đọc active incidents một lần/session và dùng các repository hiện có; không thêm write bảng lịch sử telemetry.

### Security

- Không thêm API key, token, credential hoặc dữ liệu định danh mới vào telemetry/evidence/log.
- REST error không đưa stack trace ra UI; payload chỉ dùng fields Trip/Vehicle hiện có cộng run ID kỹ thuật.

### Reliability

- Mỗi Trip có tối đa một active session trong JVM.
- `simulationRunId` là UUID mới khi start; `sequence` bắt đầu từ 1 và strictly tăng trong run.
- Reset/terminal không được để scheduler phát snapshot của state cũ sau khi command đã thành công.

### Compatibility

- Giữ endpoint và topic hiện có; response command vẫn có `message` và `status` hiện dùng, chỉ thêm fields additive.
- Field telemetry mới được frontend coi là bắt buộc với producer mới, nhưng parser phải fail-safe với message malformed/legacy và không crash app.

### Observability

- Log lifecycle/start/reset/terminal và lỗi session với `tripId`, `simulationRunId` (nếu có), không log full payload mỗi tick hoặc secret.
- Evidence implementation phải lưu output test/build và transcript STOMP hoặc screenshot/video reproducible đã redact.

## Ràng buộc

| Nhóm | Ràng buộc | Lý do/Nguồn |
|---|---|---|
| Công nghệ | Tái sử dụng Spring scheduler, `SimpMessagingTemplate`, STOMP simple broker và `@stomp/stompjs` | Survey, EVD-002..EVD-006 |
| Database | Không migration/table telemetry mới; chỉ cập nhật Trip, TripCheckIn, Vehicle đã có | Survey, EVD-007 |
| API/Event | Giữ `/api/simulator/*`, `/topic/telemetry`, `/topic/vehicle/{vehicleId}`; contract chỉ additive | Survey, EVD-003, EVD-005 |
| Compatibility | Không hard-code URL WebSocket/API mới hoặc đổi map configuration | `websocket.ts`, `api.ts`, Survey EVD-005 |
| Testing | Không dùng `npm test` vì manifest không có script đó | Survey EVD-001 |

## Business Rules đã biết

- Mỗi run thuộc duy nhất một Trip; Start khi session active/paused/completed chưa Reset bị xung đột `409`.
- Pause không làm index/Vehicle/telemetry tiến; Resume tiếp tục chính session/run ID.
- Reset là action simulator có chủ ý: xóa session, set toàn bộ `TripCheckIn` về `PENDING`/`actualArrivalTime=null`, Trip `RUNNING`/`endTime=null`, Vehicle tại START `IDLE`/speed `0`; sau đó chỉ Start mới tạo run mới.
- Completion chỉ khi flow check-in hiện có xác nhận tất cả stop `CHECKED_IN`; terminal payload phát một lần.
- Client không được dùng timestamp để sắp xếp telemetry; `simulationRunId + sequence` là authority của dashboard.

## Acceptance Criteria

### AC-REQ-001-01 — Start tạo session chạy có identity

```text
Given Trip có route START → ... → END hợp lệ và chưa có simulator session
When điều hành viên gọi POST /api/simulator/start/{tripId}
Then response là 200 với status RUNNING và simulationRunId UUID không rỗng
And tick đầu phát telemetry mang cùng tripId, simulationRunId và sequence = 1
```

### AC-REQ-001-02 — State không hợp lệ bị từ chối rõ ràng

```text
Given Trip không tồn tại, route có dưới hai stop, hoặc Trip đang có session active/paused/completed chưa Reset
When client gọi Start hoặc gọi Pause/Resume/Reset cho session không hợp lệ
Then backend không mutate session/Vehicle/check-in
And trả ProblemDetail 400, 404 hoặc 409 đúng loại lỗi
```

### AC-REQ-002-01 — Tick cập nhật xe và phát snapshot

```text
Given một session RUNNING có ít nhất hai waypoint và không bị pause
When simulator chạy một tick
Then currentWaypointIndex tiến về phía trước nhưng không vượt waypoint cuối
And Vehicle được lưu vị trí/speed/heading phù hợp, status IN_TRANSIT
And telemetry có ETA/check-in fields hiện có, run ID và sequence kế tiếp
```

### AC-REQ-002-02 — Pause không làm xe tiến

```text
Given session đang PAUSED tại waypoint k
When scheduler chạy tick
Then index, Vehicle và số telemetry cho session không thay đổi
```

### AC-REQ-003-01 — Multiplier hợp lệ và input lỗi

```text
Given session RUNNING
When client chọn 1x, 2x, 5x hoặc 10x
Then response trả multiplier đã áp dụng và tick sau dùng giá trị đó
When request gửi 0, âm, NaN, Infinity hoặc giá trị không nằm trong danh sách
Then response là 400 và multiplier/session không đổi
```

### AC-REQ-003-02 — Reset không còn session cũ

```text
Given một Trip đang PAUSED, RUNNING hoặc COMPLETED với Vehicle/check-in đã thay đổi
When client gọi Reset thành công
Then GET status trả IDLE và không có tick từ run cũ sau thời điểm reset
And check-in đều PENDING/không actual arrival, Trip RUNNING/endTime null, Vehicle ở START/IDLE/speed 0
And frontend bỏ telemetry cũ, refresh Trip và hiển thị có thể Start lại
```

### AC-REQ-004-01 — Client loại telemetry foreign/stale/out-of-order

```text
Given dashboard đang điều khiển run R của Trip A và đã render sequence 7
When subscriber nhận telemetry Trip B, run khác R, sequence <= 7 hoặc payload identity không hợp lệ
Then Map, Timeline, simStatus và telemetry state của Trip A không thay đổi
When nhận Trip A, run R, sequence 8
Then dashboard render snapshot đó đúng một lần
```

### AC-REQ-004-02 — Hai topic nhận cùng snapshot

```text
Given một tick/terminal tick được tạo cho Vehicle V
When backend publish telemetry
Then `/topic/telemetry` và `/topic/vehicle/V` nhận payload có cùng tripId, simulationRunId, sequence và timestamp
```

### AC-REQ-005-01 — Terminal đúng một lần và session khác tiếp tục

```text
Given tất cả check-in của session A được CHECKED_IN trong tick hiện tại
When tick hoàn tất
Then phát đúng một terminal telemetry COMPLETED/IDLE của A và tick sau không phát thêm A
Given session A ném exception trong scheduler còn session B hợp lệ
When scheduler xử lý cùng vòng lặp
Then lỗi A được log an toàn và B vẫn được xử lý/phát telemetry
```

### AC-REQ-005-02 — Lỗi control hiển thị và không reconnect trùng

```text
Given REST control trả ProblemDetail lỗi
When người dùng thao tác control
Then App giữ state trước đó và hiển thị toast cảnh báo có message phù hợp
When simStatus thay đổi RUNNING/PAUSED/COMPLETED
Then connection/subscriber STOMP hiện hữu không bị disconnect/reconnect chỉ vì state này đổi
```

## Giả định và dependency

- `004-automatic-station-checkin` đã được merge/available: geofence chỉ chuyển PENDING theo thứ tự và terminal check-in có semantics đã kiểm chứng.
- Một backend process là phạm vi runtime; `ConcurrentHashMap` hiện có không bảo đảm ownership giữa nhiều instance.
- Simulator dùng incident data được lưu nội bộ, không tuyên bố traffic thật; live traffic là feature khác.
- Browser hỗ trợ native WebSocket; endpoint `/ws-raw` hiện là transport dashboard đang sử dụng.

## Rủi ro sản phẩm

| Rủi ro | Khả năng | Ảnh hưởng | Cách giảm thiểu/Xác nhận cần thiết |
|---|---|---|---|
| Message cũ đến sau message mới | Vừa | Cao | run ID + strictly increasing sequence ở producer/client; TC-008/TC-009 |
| Reset ghi đè lịch sử Trip hoàn thành | Vừa | Vừa | Giới hạn đây là simulator action, hiển thị Reset rõ ràng; không dùng cho audit GPS thật |
| Exception một session chặn xe khác | Vừa | Cao | try/catch quanh từng session và unit test scheduler isolation |
| Reconnect khiến subscription trùng | Vừa | Vừa | Effect kết nối không phụ thuộc `simStatus`, cleanup subscription rõ ràng; manual verification |
| Gọi control đồng thời | Thấp | Vừa | Serialize transition per Trip/session và test conflict; multi-instance ngoài scope |

## Câu hỏi chưa được giải quyết

| ID | Câu hỏi | Người quyết định | Ảnh hưởng nếu chưa trả lời | Trạng thái |
|---|---|---|---|---|
| Q-001 | Reset một Trip `COMPLETED` có được phép mở lại Trip đó trong môi trường simulator không? | Người dùng | BR reset và persisted Trip state | `RESOLVED — Có; đây là action simulator, reset Trip về RUNNING/endTime null` |
| Q-002 | Có cần persistence/replay telemetry sau reconnect không? | Người dùng | Broker/data model | `RESOLVED — Không; snapshot tick tiếp theo + GET Trip là đủ trong scope` |
| Q-003 | Có cần selector chạy nhiều Trip từ UI không? | Người dùng | UI scope | `RESOLVED — Không; giữ currentTrip hiện có, chỉ bảo đảm isolation` |

## Xác nhận Requirement

- [x] Mọi Requirement có ID và độ ưu tiên.
- [x] Trong/ngoài phạm vi rõ ràng.
- [x] Acceptance Criteria đo được và có edge case phù hợp.
- [x] Mỗi Requirement quan trọng có thể truy vết tới Evidence sau implementation.
- [x] Yêu cầu phi chức năng liên quan đã được ghi nhận.
- [x] Không chứa giải pháp kỹ thuật chưa được Research/Survey xác minh.
- [x] Câu hỏi ảnh hưởng thiết kế đã được giải quyết hoặc đánh dấu blocker.
