# Specification — 002-route-management

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | 002-route-management |
| Trạng thái | DRAFT — chờ người dùng phê duyệt |
| Liên kết | `requirement.md`, `research.md`, `survey.md` |

## Traceability

| Requirement | Specification | Test cases dự kiến |
|---|---|---|
| REQ-001 | SPEC-001, SPEC-004, SPEC-006 | TC-001, TC-009, TC-010 |
| REQ-002 | SPEC-002, SPEC-003, SPEC-004 | TC-001, TC-002, TC-003, TC-004, TC-005 |
| REQ-003 | SPEC-003, SPEC-004, SPEC-006 | TC-003, TC-006, TC-010 |
| REQ-004 | SPEC-005, SPEC-006 | TC-007, TC-008, TC-010 |
| REQ-005 | SPEC-004, SPEC-005 | TC-002, TC-003, TC-004, TC-005, TC-007, TC-008, TC-009 |
| REQ-006 | SPEC-007 | TC-011, TC-012 |

## SPEC-001 — Mô hình tuyến và thứ tự lưu trữ

Feature dùng nguyên model hiện có:

- `Route` là aggregate root; `RouteStation` là các điểm dừng có thứ tự.
- Mỗi phần tử `stationIds[i]` sinh đúng một `RouteStation` với `stopOrder = i + 1`.
- Response luôn trả `stations` tăng dần theo `stopOrder`.
- Một station có thể xuất hiện ở nhiều stopOrder khác nhau; feature không cấm tuyến vòng vì hiện không có requirement/schema cấm điều đó.
- Không thêm migration hoặc đổi schema trong feature 002. Việc thay thế collection phải dựa vào relation `cascade = ALL` và `orphanRemoval = true` hiện có (Survey EVD-005).

## SPEC-002 — Business rules cho tạo/sửa

### BR-001 — Danh sách trạm bắt buộc và có thứ tự

`stationIds` là danh sách có thứ tự, không null và có ít nhất 2 phần tử. Mỗi phần tử phải là ID không null của một Station tồn tại.

### BR-002 — Quy tắc loại trạm

Danh sách hợp lệ khi và chỉ khi:

1. Phần tử đầu có `stationType = START`.
2. Phần tử cuối có `stationType = END`.
3. Mọi phần tử ở giữa có `stationType = STOP`.

Sai BR-001/BR-002 là `400 Bad Request`; không tìm thấy một station ID là `404 Not Found`. Backend kiểm tra các rule trước khi mutate Route/RouteStation.

### BR-003 — Mã và tên route

- `name` bắt buộc, sau trim không rỗng, tối đa 150 ký tự để phù hợp cột `Route.name` hiện có.
- `code` là tùy chọn. Khi tạo, code null/rỗng sau trim được tự sinh theo convention `ROUTE-...` hiện tại; khi có giá trị, backend trim và chuẩn hóa uppercase bằng `Locale.ROOT` trước khi kiểm tra/lưu.
- Khi sửa, code null/rỗng giữ nguyên code đã lưu; code mới phải duy nhất sau chuẩn hóa, không tính chính route đang sửa.
- Mã trùng là `409 Conflict`. Ràng buộc unique database vẫn là lớp bảo vệ cuối cùng; lỗi cạnh tranh ở lúc save cũng được chuyển thành `409`.
- `description` là tùy chọn; giá trị null được phép để xóa mô tả.

### BR-004 — Tính metric deterministic, không phải traffic ETA

Sau khi validate, backend tính lại toàn bộ metric cho create và update bằng công thức đang có:

- Khoảng cách chặng = `GeoUtil.calculateDistanceKm(current, next)`.
- Thời gian chặng = `(distanceKm / 35) * 60 + 1.5` phút.
- `totalDistanceKm` = tổng khoảng cách các chặng.
- `estimatedDurationMinutes` = tổng thời gian các chặng.
- Metric của trạm cuối là `0` theo behavior hiện có.

Các giá trị được làm tròn theo quy ước `RouteService` hiện có. Không tuyên bố số liệu này phản ánh kẹt xe hoặc ETA thời gian thực.

## SPEC-003 — Tính toàn vẹn khi thay thế route

### BR-005 — Update thay thế atomically

`PUT` thay thế metadata có thể sửa và toàn bộ topology (danh sách RouteStation) của route trong một transaction:

1. Đọc route, kiểm tra route chưa được Trip tham chiếu và validate toàn bộ request.
2. Tính danh sách RouteStation/metric mới trước khi làm thay đổi có thể nhìn thấy.
3. Thay thế collection cũ theo cách bảo đảm orphan được xóa trước khi persist các stopOrder mới (dùng flush có chủ đích nếu cần để tránh unique `(route_id, stop_order)` trung gian).
4. Lưu route và trả response đã sắp xếp.

Khi thành công, `Route.id` và `createdAt` không đổi. Nếu bất kỳ bước nào lỗi, transaction rollback và route cũ vẫn nguyên vẹn.

### BR-006 — Không sửa/xóa route đã được dùng

Nếu `TripRepository.existsByRouteId(routeId)` là true, `PUT` và `DELETE` trả `409 Conflict` và không thay đổi route. Rule bảo vệ toàn bộ Trip, không chỉ Trip `RUNNING`, nhằm tránh làm lệch các check-in/lịch đã được tạo từ RouteStation (Survey EVD-008).

## SPEC-004 — REST API contract

Base path: `/api/routes`.

| Operation | Endpoint | Thành công | Hành vi |
|---|---|---|---|
| Liệt kê | `GET /api/routes` | `200` | Giữ contract hiện có; từng `stations` tăng dần theo stopOrder. |
| Chi tiết | `GET /api/routes/{id}` | `200` | Giữ contract hiện có. |
| Tạo | `POST /api/routes` | `201` | Validate BR-001..BR-004, lưu route mới. |
| Sửa | `PUT /api/routes/{id}` | `200` | Validate BR-001..BR-006, thay thế atomically. |
| Xóa | `DELETE /api/routes/{id}` | `204` | Chỉ xóa nếu route tồn tại và không được Trip tham chiếu. |

### Request body của POST/PUT

```json
{
  "code": "R-HCM-01",
  "name": "Bến xe Miền Đông — Thảo Cầm Viên",
  "description": "Tuyến minh họa",
  "stationIds": [101, 102, 103]
}
```

- `name` và `stationIds` bắt buộc theo BR-001/BR-003.
- `code` có thể bỏ qua khi POST; khi PUT bỏ qua/để trống nghĩa là giữ code hiện tại.
- Thứ tự trong `stationIds` chính là thứ tự route mong muốn, không phải một set.
- Response POST/PUT dùng `RouteResponseDto` hiện có: `id`, `code`, `name`, `description`, `totalDistanceKm`, `estimatedDurationMinutes`, `stations`, `createdAt`.

## SPEC-005 — Error contract

RouteController phải có xử lý lỗi tách bạch, không mở rộng scope của Station handler một cách vô tình.

| Điều kiện | HTTP | Ví dụ thông điệp cho UI |
|---|---:|---|
| DTO/BR-001/BR-002/BR-003 không hợp lệ | 400 | `Tuyến phải bắt đầu bằng trạm START, kết thúc bằng trạm END và các trạm giữa phải là STOP.` |
| Route không tồn tại | 404 | `Không tìm thấy tuyến đường với ID: …` |
| Station trong request không tồn tại | 404 | `Không tìm thấy trạm với ID: …` |
| Code chuẩn hóa đã tồn tại | 409 | `Mã tuyến đã tồn tại.` |
| Route đã có Trip tham chiếu | 409 | `Không thể sửa hoặc xóa tuyến đã được gán cho chuyến đi.` |
| Unique/data-integrity race | 409 | Thông điệp an toàn, không lộ stack trace/database internals. |

Implementation dùng `ProblemDetail`/handler theo convention feature Station hiện có, scoped cho RouteController hoặc một handler dùng chung có test regression cho Station. Frontend phải ưu tiên đọc `detail`/message qua parser lỗi thống nhất.

## SPEC-006 — Giao diện quản lý route

### Điểm truy cập và danh sách

- Bổ sung nút mở quản lý tuyến tại khu vực điều khiển hiện có (`SimulatorPanel`), tương tự điểm mở quản lý trạm.
- `RouteModal` hiển thị danh sách route với mã, tên, số trạm, tổng khoảng cách và thời lượng; mỗi hàng có thao tác sửa/xóa.
- Modal có form tạo/sửa. Xóa cần bước xác nhận; lỗi API vẫn giữ modal/dữ liệu cần thiết để người dùng xử lý.

### Sắp xếp trạm

- Form hiển thị danh sách trạm đã chọn có số thứ tự và loại `START`/`STOP`/`END`.
- Người dùng thêm trạm, xóa trạm, và di chuyển từng trạm lên/xuống. UI gửi một mảng `stationIds` duy nhất theo thứ tự hiển thị.
- Trước submit, UI kiểm tra tối thiểu và rule START/END để phản hồi sớm. Backend vẫn là nguồn xác thực cuối cùng.
- Form yêu cầu tên route; code là tùy chọn. Không thêm thư viện drag-and-drop.

### Đồng bộ state

- Sau create/update/delete thành công, App tải lại danh sách route từ server (không giả định state local là source of truth).
- Nếu route đang được `selectedRoute` được update thành công, App thay state đó bằng response mới để không giữ metric cũ. Khi xóa selected route thành công, chọn route đầu tiên còn lại hoặc `null`.
- Feature không thêm cơ chế thay route của Trip/simulator đang chạy; UI chỉ quản lý dữ liệu route. Điều này tránh thay đổi ngầm lịch trình hiện hữu.

## SPEC-007 — Verification và Evidence

- Backend bổ sung unit/service tests và controller integration tests cho tất cả nhánh AC-001..AC-005.
- Frontend không có test script hiện hữu (Survey EVD-012), do đó chạy `npm run lint`, `npm run build` và manual flow trong browser.
- Sau implementation Gemini ghi output thực tế (hoặc đường dẫn artifact) vào `evidence.md`. Command chưa chạy phải là `INCONCLUSIVE`, không được PASS bằng suy đoán.
- Codex review liên kết REQ → SPEC → TC → EVD; không approve nếu REQ Must quan trọng còn INCONCLUSIVE/FAIL.

## Tương thích, bảo mật và quan sát

- Giữ endpoint GET và trường response hiện có; không thay đường dẫn API hay schema database.
- Không truyền API key/config bí mật qua request/response/log/evidence.
- Exception handler không được bắt rộng làm thay đổi lỗi của controller khác mà không có test.
- Các lỗi 400/404/409 phải hiển thị được trên UI; không log hoặc trả stack trace cho client.
