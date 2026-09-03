# Research — Quản lý trạm đầu, trạm cuối và trạm dừng

## Thông tin tài liệu

| Thuộc tính | Giá trị |
| --- | --- |
| Feature ID | `001-station-management` |
| Requirement liên quan | `REQ-001` đến `REQ-006` |
| Trạng thái | `READY` |
| Người thực hiện | `Codex` |
| Ngày nghiên cứu | `2026-09-03` |

## Mục tiêu nghiên cứu

Chọn cách mở rộng CRUD trạm mà vẫn tương thích với REST/layer hiện tại, xác định semantics update/conflict và cách áp dụng validation ở backend mà không thêm dependency hoặc kiến trúc mới.

## Phạm vi nghiên cứu

### Trong phạm vi

- Semantics của `PUT` và HTTP status cho xung đột trạng thái.
- Cơ chế validation đã có trong Jakarta Validation.
- Lựa chọn mở rộng API/component hiện tại hay tạo contract/layer mới.

### Ngoài phạm vi

- Routing, giao thông, geocoding, WebSocket và thuật toán check-in.
- So sánh framework frontend/backend khác.

## Các câu hỏi cần trả lời

| ID | Câu hỏi | Requirement liên quan | Tiêu chí để trả lời |  |  |
| --- | --- | --- | --- | --- | --- |
|  | `RQ-001` | Có nên giữ `PUT /api/stations/{id}` cho update? |  | `REQ-003` | Semantics chuẩn, compatibility và hiện trạng repository |
| `RQ-002` | Duplicate code và xóa trạm đang được dùng nên trả status nào? | `REQ-004`, `REQ-005` | HTTP semantics và khả năng client phân biệt lỗi |  |  |
| `RQ-003` | Có cần thêm thư viện validation không? | `REQ-005` | Dependency hiện có và constraint chuẩn |  |  |
| `RQ-004` | Có cần tách module/state-management mới cho frontend không? | `REQ-001` đến `REQ-006` | Phạm vi thay đổi, convention và testability |  |  |

## Tiêu chí đánh giá

| Tiêu chí | Mức quan trọng | Cách đánh giá |
| --- | --- | --- |
| Correctness/data integrity | Bắt buộc | Không cho dữ liệu invalid/trùng và không làm hỏng route reference |
| Backward compatibility | Cao | Giữ endpoint và representation hiện có khi có thể |
| Scope/complexity | Cao | Số layer/dependency mới tối thiểu |
| Error clarity | Cao | Client phân biệt được 400/404/409 |
| Testability | Cao | Business rule có thể kiểm tra bằng unit/API test |

## Các giải pháp được xem xét

### Giải pháp A — Mở rộng CRUD hiện có

#### Cách hoạt động

Giữ `StationController`, `StationService`, `StationRepository`, `StationDto`, REST endpoint và component hiện tại; bổ sung validation, update frontend, delete guard và xử lý lỗi trong đúng các boundary đó.

#### Ưu điểm

- Ít thay đổi contract và khớp convention repository.
- Không cần dependency mới.
- Có thể kiểm thử theo service/API và manual UI.

#### Nhược điểm

- `StationModal` tiếp tục chịu cả form và danh sách; cần kiểm soát state create/edit cẩn thận.
- Contract `PUT` yêu cầu gửi representation đầy đủ của các trường editable.

#### Rủi ro

- Client cũ gửi request thiếu `stationType` hoặc `radiusMeters` có thể bị 400 khi validation được siết.
- Race condition duplicate/delete cần cả pre-check và database constraint/exception handling.

#### Mức độ phù hợp với project

Cao: API và layer CRUD đã tồn tại; gap chính nằm ở validation, safe delete và frontend update.

#### Evidence hỗ trợ

- `EVD-001`, `EVD-002`, `EVD-004`, `EVD-007`, `EVD-008`.

#### Prototype/kiểm chứng nếu có

- Thao tác hoặc command: đọc `StationController`, `StationService`, `StationModal`, `api.ts` và các manifest.
- Kết quả: endpoint `PUT` đã có ở backend nhưng chưa có client/update UI.
- Giới hạn của kết quả: source code chưa chứng minh runtime behavior; cần Evidence sau implementation.

### Giải pháp B — Tạo API PATCH và module quản trị trạm mới

#### Cách hoạt động

Thêm partial-update contract, DTO riêng, state-management/form layer mới và thay component hiện tại bằng màn hình quản trị tách biệt.

#### Ưu điểm

- Partial update có thể gửi ít field hơn.
- Có thể tách trách nhiệm UI rõ hơn nếu nghiệp vụ quản trị mở rộng đáng kể.

#### Nhược điểm

- Tăng public contract, file và test cần bảo trì.
- Trùng chức năng với `PUT` và `StationModal` đã có.
- Không có Requirement về partial update hoặc màn hình quản trị riêng.

#### Rủi ro

- Scope creep và hai semantics update song song.
- Tạo abstraction trước khi có nhu cầu.

#### Mức độ phù hợp với project

Thấp trong feature hiện tại.

#### Evidence hỗ trợ

- `EVD-004`, `EVD-007`, `EVD-008`.

#### Prototype/kiểm chứng nếu có

- Không tạo prototype vì Survey đã đủ để loại phương án theo tiêu chí scope và compatibility.

## So sánh

| Tiêu chí | Giải pháp A | Giải pháp B | Nhận xét |
| --- | --- | --- | --- |
| Correctness | Đáp ứng bằng validation/guard | Đáp ứng được | Không có lợi thế nghiệp vụ cho B |
| Compatibility | Giữ endpoint hiện tại | Thêm contract mới | A ít ảnh hưởng client hơn |
| Complexity | Thấp | Cao | B thêm layer không được Requirement yêu cầu |
| Testability | Service/API/UI hiện có | Cần thêm test cho contract mới | A đủ cho phạm vi |
| Dependency | Không thêm | Có thể cần form/state tooling | A phù hợp nguyên tắc repository |

## Giải pháp đề xuất

**Lựa chọn:** Giải pháp A — mở rộng CRUD hiện có.

**Requirement được đáp ứng:** `REQ-001` đến `REQ-006`.

**Điều kiện áp dụng:** Dùng dependency hiện có; giữ `PUT` là update đầy đủ các trường editable; môi trường verification phải dùng runtime đúng manifest.

## Lý do lựa chọn

`PUT /api/stations/{id}` đã tồn tại và phù hợp khi client gửi representation đầy đủ mong muốn của trạm. RFC 9110 mô tả `PUT` là tạo/thay thế trạng thái của resource đích bằng representation trong request; RFC cũng dành 409 cho xung đột với trạng thái hiện tại. Jakarta Validation đã cung cấp constraint annotation và metadata để bảo vệ DTO boundary. Vì vậy không cần PATCH, module mới hay dependency validation mới. Đánh đổi là request update phải rõ các field bắt buộc và client cũ gửi payload thiếu có thể cần điều chỉnh.

## Giải pháp không được chọn

| Giải pháp | Lý do không chọn | Khi nào nên xem xét lại |
| --- | --- | --- |
| PATCH + module quản trị mới | Không có Requirement partial update; tăng contract và kiến trúc | Khi có nhiều màn hình/role, form phức tạp hoặc partial update là requirement công khai |
| Xóa cascade route | Có nguy cơ mất dữ liệu ngoài phạm vi | Chỉ khi Requirement quản lý lifecycle route xác nhận rõ |
| Thêm thư viện form/validation | Jakarta Validation và React state hiện có đủ | Khi form mở rộng lớn và có quyết định dependency riêng |

## Ảnh hưởng dự kiến tới Spec và Plan

- Data model/API/Event: không đổi bảng hay endpoint; siết request validation, status 400/404/409 và safe delete.
- Dependency/config: không thêm dependency hoặc cấu hình.
- Testability/failure mode: thêm backend unit/API tests; frontend dùng lint/build và manual UI vì chưa có test script.
- Security/operation: escape nội dung popup; lỗi frontend không phụ thuộc duy nhất một shape error body.

## Câu hỏi và rủi ro còn lại

- Runtime local hiện thấp hơn manifest ở cả backend/frontend nên baseline test/build chưa chạy đạt; đây là prerequisite verification, không phải lý do thay đổi Spec.
- Không có câu hỏi kỹ thuật chặn Plan.

## Evidence từ nguồn bên ngoài

- `EVD-001` kiểm chứng semantics `PUT` và HTTP 409 từ RFC 9110.
- `EVD-002` kiểm chứng constraint-based validation từ Jakarta Validation 3.1.
- `EVD-014` kiểm chứng format Problem Details cho lỗi HTTP API từ RFC 9457.

## Nguồn tham khảo

| ID | Evidence | Nguồn | URL | Ngày truy cập | Claim/Kết luận được hỗ trợ |
| --- | --- | --- | --- | --- | --- |
| `SRC-001` | `EVD-001` | RFC 9110 — HTTP Semantics | https://www.rfc-editor.org/rfc/rfc9110.html | `2026-09-03` | `PUT` thay thế trạng thái resource; 409 biểu thị xung đột với trạng thái hiện tại |
| `SRC-002` | `EVD-002` | Jakarta Validation 3.1 Specification | https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1.html | `2026-09-03` | Constraint annotation/metadata có thể khai báo và kiểm tra invariant tại DTO boundary |
| `SRC-003` | `EVD-014` | RFC 9457 — Problem Details for HTTP APIs | https://www.rfc-editor.org/rfc/rfc9457.html | `2026-09-03` | `application/problem+json` cung cấp `status`, `title`, `detail` và extension member cho lỗi API |

## Checklist hoàn thành

- [x] Tất cả Research Question đã được trả lời hoặc ghi blocker.

- [x] Có ít nhất hai giải pháp khi thực sự tồn tại lựa chọn hợp lý.

- [x] Đề xuất dựa trên tiêu chí liên kết Requirement.

- [x] Trade-off và rủi ro được ghi rõ.

- [x] Nguồn có URL, ngày truy cập và kết luận tương ứng.

- [x] Claim quan trọng dùng thông tin bên ngoài có `EVD-*` hoặc ghi rõ chưa có Evidence.

- [x] Không có URL/citation được suy đoán hoặc chưa mở kiểm tra.

- [x] Không Research hoặc tạo abstraction cho nhu cầu ngoài scope.