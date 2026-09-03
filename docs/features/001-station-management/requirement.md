# Requirement — Quản lý trạm đầu, trạm cuối và trạm dừng

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `001-station-management` |
| Trạng thái | `READY` |
| Người tạo | `Codex` |
| Người xác nhận | `Developer/User` |
| Ngày tạo/cập nhật | `2026-09-03` |

## Tổng quan

Người vận hành cần quản lý danh mục các điểm mà xe sử dụng trong tuyến đường: trạm đầu, trạm dừng trung gian và trạm cuối. Hiện danh mục phải có thể được xem, thêm, sửa và xóa từ giao diện; thay đổi hợp lệ phải được lưu bền vững và phản ánh trên bản đồ mà không làm hỏng tuyến đã có.

## Mục tiêu

- Quản lý đầy đủ vòng đời của một trạm từ giao diện web.
- Phân biệt rõ ba loại `START`, `STOP`, `END` trong dữ liệu và trên bản đồ.
- Ngăn dữ liệu trạm không hợp lệ, trùng mã hoặc thao tác xóa làm mất toàn vẹn tuyến.
- Trả lỗi có thể hiểu và giữ trạng thái giao diện nhất quán khi thao tác thất bại.

## Người dùng và bên liên quan

| Actor/Bên liên quan | Nhu cầu | Quyền hoặc giới hạn liên quan |
|---|---|---|
| Người vận hành | Xem, thêm, sửa, xóa trạm | Giữ nguyên cơ chế truy cập hiện tại; feature này không bổ sung phân quyền |
| Dịch vụ quản lý tuyến | Tham chiếu trạm ổn định | Không được để lại tham chiếu tuyến bị hỏng khi xóa trạm |
| Người theo dõi bản đồ | Thấy đúng vị trí và loại trạm | Chỉ thấy thay đổi sau khi backend xác nhận thành công |

## Phạm vi

### Trong phạm vi

- Xem danh sách và thông tin trạm hiện có.
- Tạo trạm `START`, `STOP` hoặc `END` với mã, tên, tọa độ, địa chỉ tùy chọn và bán kính check-in.
- Sửa toàn bộ trường có thể thay đổi của trạm.
- Xóa trạm sau xác nhận khi trạm không được tuyến tham chiếu.
- Validation, thông báo lỗi và đồng bộ danh sách/marker bản đồ sau thao tác thành công.

### Ngoài phạm vi

- Tạo/sắp xếp tuyến, ETA, check-in, simulator, dữ liệu giao thông và thông báo thay đổi lịch trình.
- Phân quyền quản trị trạm hoặc thay đổi cơ chế xác thực hiện tại.
- Xóa cascade tuyến khi xóa trạm.
- Realtime đồng bộ danh mục trạm giữa nhiều trình duyệt.
- Geocoding địa chỉ, kéo-thả marker hoặc nhập trạm hàng loạt.

## Yêu cầu chức năng

### REQ-001 — Xem danh sách trạm

**Mô tả:** Người vận hành phải xem được danh sách trạm với mã, tên, loại, tọa độ, địa chỉ và bán kính check-in.

**Lý do:** Người vận hành cần nhận diện đúng trạm trước khi sửa hoặc xóa.

**Độ ưu tiên:** `MUST`

**Dependency:** Backend và database hiện có.

### REQ-002 — Thêm trạm

**Mô tả:** Người vận hành phải tạo được trạm thuộc một trong ba loại `START`, `STOP`, `END` từ tọa độ đã chọn trên bản đồ hoặc tọa độ nhập rõ ràng.

**Lý do:** Danh mục trạm là đầu vào để xây dựng tuyến và lịch trình.

**Độ ưu tiên:** `MUST`

**Dependency:** `REQ-005`.

### REQ-003 — Sửa trạm

**Mô tả:** Người vận hành phải chọn một trạm, thấy dữ liệu hiện tại và cập nhật được mã, tên, loại, tọa độ, địa chỉ và bán kính check-in.

**Lý do:** Dữ liệu trạm có thể thay đổi mà không nên buộc xóa rồi tạo lại.

**Độ ưu tiên:** `MUST`

**Dependency:** `REQ-001`, `REQ-005`.

### REQ-004 — Xóa trạm an toàn

**Mô tả:** Người vận hành phải xóa được trạm không còn được tuyến tham chiếu sau bước xác nhận; hệ thống phải từ chối xóa trạm đang được tham chiếu.

**Lý do:** Xóa nhầm hoặc xóa cascade có thể làm hỏng dữ liệu tuyến.

**Độ ưu tiên:** `MUST`

**Dependency:** `REQ-001`, dữ liệu tuyến hiện có.

### REQ-005 — Bảo vệ tính hợp lệ và duy nhất của dữ liệu

**Mô tả:** Backend phải kiểm tra trường bắt buộc, giới hạn tọa độ/bán kính/độ dài, chuẩn hóa mã và từ chối mã trùng; frontend phải hiển thị lỗi tương ứng mà không giả báo thành công.

**Lý do:** Validation ở giao diện không đủ để bảo vệ dữ liệu khi API được gọi trực tiếp.

**Độ ưu tiên:** `MUST`

**Dependency:** Database hiện có.

### REQ-006 — Phản ánh thay đổi trên giao diện và bản đồ

**Mô tả:** Sau create/update/delete thành công, danh sách và marker phải hiển thị trạng thái mới; marker phải dựa trên `stationType`, không dựa trên thứ tự phần tử.

**Lý do:** Dữ liệu hiển thị sai loại hoặc trạng thái cũ khiến người vận hành ra quyết định sai.

**Độ ưu tiên:** `MUST`

**Dependency:** `REQ-001`, `REQ-002`, `REQ-003`, `REQ-004`.

## Yêu cầu phi chức năng

### Performance

- Không thêm truy vấn theo từng phần tử khi lấy danh sách trạm; mỗi thao tác ghi chỉ thực hiện số truy vấn cố định không phụ thuộc số trạm.

### Security

- Backend là nơi bắt buộc bảo vệ invariant, không tin validation phía client.
- Nội dung do người dùng nhập không được thực thi như HTML/script trong popup bản đồ.
- Không đưa credential hoặc dữ liệu nhạy cảm vào lỗi/log.

### Reliability

- Create/update/delete phải có transaction; khi thất bại không được hiển thị trạng thái thành công hoặc làm mất dữ liệu liên quan.
- Trạng thái form phải được giữ khi backend từ chối create/update để người dùng sửa và gửi lại.

### Scalability

- Không đặt mục tiêu phân trang trong feature này vì Requirement chưa cung cấp quy mô; giữ contract danh sách hiện tại.

### Compatibility

- Giữ nguyên các endpoint `/api/stations` và shape chính của `StationDto` hiện có.
- Hỗ trợ runtime theo manifest của từng module; môi trường verification phải đáp ứng các version đó.

### Observability

- UI hiển thị thông báo thành công hoặc thất bại cho từng thao tác; lỗi validation/conflict phải có HTTP status phân biệt được.

## Ràng buộc

| Nhóm | Ràng buộc | Lý do/Nguồn |
|---|---|---|
| Công nghệ | Tái sử dụng React/TypeScript và Spring Boot/JPA hiện có | `survey.md`, `EVD-003` |
| Database | Giữ bảng `stations` và quan hệ `route_stations.station_id` | `survey.md`, `EVD-005`, `EVD-006` |
| API/Event | Giữ REST `/api/stations`; không thêm event | `survey.md`, `EVD-004` |
| Compatibility | Không thêm dependency mới | Giải pháp hiện có đủ đáp ứng; `research.md` |
| Thời gian | Không có deadline được cung cấp | Yêu cầu người dùng |

## Business Rules đã biết

- Mã trạm sau khi bỏ khoảng trắng đầu/cuối và chuyển uppercase phải là duy nhất.
- Loại trạm chỉ nhận `START`, `STOP`, `END`; cho phép có nhiều trạm cùng loại trong danh mục toàn cục.
- Latitude thuộc `[-90, 90]`, longitude thuộc `[-180, 180]`.
- Bán kính check-in thuộc `[30, 150]` mét, giữ theo giới hạn giao diện hiện tại.
- Không được xóa trạm đang được ít nhất một `RouteStation` tham chiếu.

## Acceptance Criteria

### AC-REQ-001-01 — Hiển thị danh sách đầy đủ

```text
Given backend có các trạm thuộc nhiều loại
When người vận hành mở màn hình quản lý trạm
Then mỗi trạm hiển thị mã, tên, loại, vị trí, địa chỉ nếu có và bán kính
```

### AC-REQ-002-01 — Tạo được cả ba loại trạm

```text
Given form có dữ liệu hợp lệ và tọa độ rõ ràng
When người vận hành lần lượt tạo START, STOP và END
Then backend trả 201 cho từng trạm
And danh sách và bản đồ hiển thị các trạm vừa lưu
```

### AC-REQ-002-02 — Không lưu create không hợp lệ

```text
Given mã trùng sau chuẩn hóa hoặc một trường vi phạm validation
When gửi yêu cầu tạo trạm
Then backend trả 409 cho mã trùng hoặc 400 cho dữ liệu không hợp lệ
And không tạo thêm bản ghi
And form không bị đóng như thể đã thành công
```

### AC-REQ-003-01 — Cập nhật trạm

```text
Given một trạm đang tồn tại
When người vận hành chọn sửa, thay đổi các trường và lưu dữ liệu hợp lệ
Then backend trả 200 với representation đã chuẩn hóa
And danh sách và marker phản ánh dữ liệu mới
And id và createdAt không đổi
```

### AC-REQ-003-02 — Không cập nhật trùng mã hoặc thiếu trạm

```text
Given mã mới thuộc trạm khác hoặc id không tồn tại
When gửi yêu cầu cập nhật
Then backend trả tương ứng 409 hoặc 404
And dữ liệu trạm trước đó không bị thay đổi một phần
```

### AC-REQ-004-01 — Xóa trạm không được tham chiếu

```text
Given trạm không thuộc tuyến nào và người dùng đã xác nhận
When người vận hành xóa trạm
Then backend trả 204
And trạm biến mất khỏi database, danh sách và bản đồ
```

### AC-REQ-004-02 — Từ chối xóa trạm đang được dùng

```text
Given trạm đang được ít nhất một tuyến tham chiếu
When người vận hành xác nhận xóa
Then backend trả 409 với thông báo dễ hiểu
And trạm và tuyến liên quan vẫn tồn tại
```

### AC-REQ-005-01 — Kiểm tra biên dữ liệu

```text
Given code/name rỗng hoặc quá dài, tọa độ ngoài miền, radius ngoài 30–150, hay stationType không hợp lệ
When gọi create hoặc update
Then backend trả 400
And database không thay đổi
```

### AC-REQ-006-01 — Hiển thị đúng loại và chỉ cập nhật sau thành công

```text
Given thứ tự danh sách không trùng với vai trò trạm
When bản đồ render marker và một thao tác ghi thành công hoặc thất bại
Then style/nhãn marker dựa trên stationType
And UI chỉ áp dụng trạng thái mới sau response thành công
And lỗi được thông báo khi response thất bại
```

### AC-REQ-006-02 — Nội dung popup an toàn

```text
Given tên, mã hoặc địa chỉ trạm chứa chuỗi giống HTML/script
When marker mở popup
Then chuỗi được hiển thị như text
And không có HTML/script do dữ liệu trạm cung cấp được thực thi
```

## Giả định và dependency

- Việc duyệt bộ tài liệu này xác nhận chính sách không xóa trạm đang được tuyến tham chiếu.
- Giới hạn bán kính 30–150 m kế thừa giao diện hiện tại; thay đổi giới hạn này cần cập nhật Requirement/Spec trước implementation.
- Không áp đặt số lượng tối đa `START`/`END` trên danh mục toàn cục; ràng buộc theo từng tuyến thuộc feature quản lý tuyến.
- Backend và database là dependency nội bộ; feature không phụ thuộc dịch vụ ngoài.

## Rủi ro sản phẩm

| Rủi ro | Khả năng | Ảnh hưởng | Cách giảm thiểu/Xác nhận cần thiết |
|---|---|---|---|
| Người dùng muốn xóa trạm đang nằm trong tuyến | Vừa | Cao | Trả 409 và yêu cầu gỡ trạm khỏi tuyến trước |
| Chọn nhầm tọa độ khi mở form không qua click bản đồ | Vừa | Cao | Không dùng tọa độ mặc định ẩn; hiển thị/cho sửa tọa độ |
| API cũ đã gửi radius/type rỗng | Thấp | Vừa | Ghi rõ request contract và kiểm tra compatibility trong test |

## Câu hỏi chưa được giải quyết

Không có câu hỏi chặn Plan. Các giả định sản phẩm ở trên cần được Developer/User xác nhận khi duyệt Plan trước khi giao Gemini.

## Xác nhận Requirement

- [x] Mọi Requirement có ID và độ ưu tiên.
- [x] Trong/ngoài phạm vi rõ ràng.
- [x] Acceptance Criteria đo được và có edge case phù hợp.
- [x] Mỗi Requirement quan trọng có thể truy vết tới Evidence sau implementation.
- [x] Yêu cầu phi chức năng liên quan đã được ghi nhận.
- [x] Không chứa giải pháp kỹ thuật chưa được Research/Survey xác minh.
- [x] Câu hỏi ảnh hưởng thiết kế đã được giải quyết hoặc đánh dấu blocker.
