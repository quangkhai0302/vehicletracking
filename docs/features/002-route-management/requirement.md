# Requirement — 002-route-management

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | 002-route-management |
| Trạng thái | DRAFT — chờ người dùng phê duyệt Plan |
| Người soạn | Codex |
| Ngày | 2026-09-03 |

## Mục tiêu

Cho phép người vận hành quản lý tuyến xe: xem danh sách, tạo, sửa và xóa tuyến; chọn các trạm theo một thứ tự rõ ràng; và bảo đảm một tuyến luôn bắt đầu tại trạm `START`, kết thúc tại trạm `END`. Hệ thống phải tính lại khoảng cách và thời lượng ước tính khi thứ tự trạm thay đổi.

## Phạm vi

### Trong phạm vi

- REST API quản lý tuyến tại `/api/routes`: giữ đọc danh sách/chi tiết, bổ sung sửa tuyến và hoàn thiện xử lý lỗi cho tạo/xóa.
- Giao diện quản lý tuyến để tạo, sửa, xóa và sắp xếp danh sách trạm bằng các thao tác thêm, xóa, di chuyển lên/xuống.
- Kiểm tra nghiệp vụ phía backend: tối thiểu hai trạm; đầu là `START`; cuối là `END`; mọi trạm giữa là `STOP`.
- Tính lại `distanceToNextKm`, `estimatedTimeToNextMinutes`, `totalDistanceKm` và `estimatedDurationMinutes` theo thứ tự đã lưu.
- Từ chối sửa hoặc xóa tuyến đã được `Trip` tham chiếu để không làm sai lịch/check-in của chuyến hiện hữu.
- Bổ sung kiểm thử backend và các bước xác minh frontend; cập nhật Evidence sau khi Gemini triển khai.

### Ngoài phạm vi

- Tối ưu lộ trình, chỉ đường hoặc thời gian giao thông thời gian thực.
- Tạo, gán, sửa hoặc hủy `Trip`; thay đổi simulator, check-in và WebSocket.
- Chuyển đổi tuyến đang chạy sang một tuyến khác hay tạo phiên bản lịch sử của tuyến.
- Kéo-thả, import/export tuyến, phân quyền người dùng hoặc thay đổi schema/migration.
- Đổi tuyến đang được hiển thị toàn cục trên bản đồ. Hộp quản lý hiển thị thứ tự trạm của tuyến đang tạo/sửa; việc chọn tuyến cho chuyến/simulator là feature sau.

## Requirements

### REQ-001 — Xem và nhận diện tuyến

Người dùng có thể mở màn hình quản lý để xem các tuyến hiện có cùng mã, tên, danh sách trạm theo `stopOrder`, tổng quãng đường và thời lượng ước tính; họ có thể chọn một tuyến để sửa hoặc xóa.

**Ưu tiên:** Must

**Phụ thuộc:** API `GET /api/routes` và `GET /api/routes/{id}` hiện có; giao diện React hiện có.

### REQ-002 — Tạo tuyến với thứ tự trạm hợp lệ

Người dùng có thể tạo tuyến mới bằng tên, mã tùy chọn, mô tả tùy chọn và danh sách ID trạm đã sắp xếp. Backend là nguồn kiểm tra cuối cùng cho thứ tự `START → STOP* → END` và trả lại tuyến đã lưu cùng các số liệu ước tính.

**Ưu tiên:** Must

**Phụ thuộc:** Dữ liệu trạm quản lý ở feature 001 và API `POST /api/routes`.

### REQ-003 — Sửa tuyến và sắp xếp lại trạm

Người dùng có thể sửa thông tin tuyến chưa được chuyến nào tham chiếu và thay thế toàn bộ danh sách trạm bằng thứ tự mới. Việc sửa phải giữ nguyên ID/`createdAt` của tuyến, thay thế các `RouteStation` cũ, rồi tính lại các số liệu theo thứ tự mới trong cùng một giao dịch.

**Ưu tiên:** Must

**Phụ thuộc:** REQ-002; API `PUT /api/routes/{id}` mới.

### REQ-004 — Xóa tuyến an toàn

Người dùng có thể xóa tuyến chưa được chuyến nào tham chiếu. Nếu tuyến đã được `Trip` tham chiếu, hệ thống không xóa hoặc sửa tuyến và trả lỗi xung đột có thông điệp có thể hiển thị trên UI.

**Ưu tiên:** Must

**Phụ thuộc:** Quan hệ `Trip.route` hiện có.

### REQ-005 — Phản hồi lỗi nhất quán và không làm hỏng API đọc

API phải phân biệt dữ liệu không hợp lệ (`400`), không tìm thấy tuyến/trạm (`404`) và xung đột mã tuyến hoặc tuyến đang được dùng (`409`). Các endpoint đọc hiện có phải tiếp tục trả dữ liệu tương thích, trong đó danh sách trạm được sắp theo `stopOrder`.

**Ưu tiên:** Must

**Phụ thuộc:** Cơ chế xử lý lỗi Spring hiện có và contract `RouteResponseDto`.

### REQ-006 — Có thể kiểm chứng

Các hành vi backend phải có test tự động; giao diện phải được xác minh bằng lint/build và kiểm tra thủ công các luồng tạo, sửa, xóa, sắp xếp, lỗi START/END. Mọi kết luận triển khai chỉ được đánh dấu PASS khi có Evidence thực tế.

**Ưu tiên:** Must

**Phụ thuộc:** `docs/workflow.md`, `docs/templates/evidence-template.md`.

## Tiêu chí chấp nhận

### AC-001 — Tạo tuyến hợp lệ

**Given** có một trạm `START`, không hoặc nhiều trạm `STOP`, và một trạm `END` đang tồn tại

**When** người dùng gửi/tạo tuyến theo thứ tự `START → STOP* → END`

**Then** hệ thống lưu tuyến, trả `201`, đánh số `stopOrder` từ 1 theo thứ tự gửi và trả lại tổng quãng đường/thời lượng đã tính.

Liên kết: REQ-001, REQ-002, REQ-005.

### AC-002 — Không chấp nhận thứ tự trạm sai

**Given** danh sách trạm có ít hơn hai phần tử, không bắt đầu bằng `START`, không kết thúc bằng `END`, hoặc có trạm giữa không phải `STOP`

**When** người dùng tạo hoặc sửa tuyến

**Then** backend không ghi dữ liệu và trả `400` nêu rõ quy tắc bị vi phạm.

Liên kết: REQ-002, REQ-003, REQ-005.

### AC-003 — Sắp xếp/sửa tuyến hợp lệ

**Given** một tuyến chưa được `Trip` nào tham chiếu

**When** người dùng thay đổi tên/mô tả/mã hoặc dùng các nút lên/xuống để thay thế thứ tự trạm hợp lệ rồi lưu

**Then** `PUT /api/routes/{id}` trả `200`, ID và `createdAt` không đổi, danh sách phản hồi đúng thứ tự mới và số liệu được tính lại.

Liên kết: REQ-001, REQ-003, REQ-005.

### AC-004 — Bảo vệ tuyến đã được dùng

**Given** một `Trip` tham chiếu tuyến

**When** người dùng yêu cầu sửa hoặc xóa tuyến đó

**Then** hệ thống không thay đổi dữ liệu, trả `409`, và UI hiện thông báo cảnh báo từ lỗi đó.

Liên kết: REQ-004, REQ-005.

### AC-005 — Xóa tuyến chưa được dùng

**Given** một tuyến tồn tại và không có `Trip` tham chiếu

**When** người dùng xác nhận xóa

**Then** API trả `204`, tuyến biến mất khỏi danh sách UI và `GET /api/routes/{id}` sau đó trả `404`.

Liên kết: REQ-001, REQ-004, REQ-005.

### AC-006 — Xác minh có bằng chứng

**Given** Gemini đã hoàn tất implementation

**When** các lệnh và kiểm tra trong Test-Plan được thực sự chạy

**Then** `evidence.md` chứa kết quả/log/nguồn tương ứng; trạng thái chỉ là `PASS`, `FAIL` hoặc `INCONCLUSIVE`, và Review có thể lần theo Requirement → Spec → Test → Evidence.

Liên kết: REQ-006.

## Yêu cầu phi chức năng

- Tính nhất quán: thay thế danh sách trạm và số liệu phải là một giao dịch; lỗi không để lại `RouteStation` dở dang.
- Khả năng sử dụng: không cần thư viện kéo-thả mới; các nút thêm/xóa/lên/xuống phải đủ để kiểm soát thứ tự trên giao diện hiện tại.
- Tương thích: giữ nguyên đường dẫn và cấu trúc phản hồi đọc hiện có; `code` bị bỏ trống lúc tạo tiếp tục được backend tạo tự động như hành vi hiện có.
- Bảo mật: không đưa API key hoặc cấu hình bí mật vào source code, tài liệu hay evidence.
- Khả năng quan sát: lỗi REST phải có trạng thái HTTP và thông điệp đủ để UI hiển thị; các lệnh xác minh được lưu/ghi rõ trong Evidence.

## Giả định và quyết định cần người dùng review

- Chỉnh sửa/xóa một tuyến đã có bất kỳ `Trip` tham chiếu nào bị chặn, kể cả chuyến đã hoàn thành. Đây là quyết định an toàn dữ liệu vì trip/check-in hiện lưu theo các trạm của tuyến tại lúc tạo; evidence ở Survey EVD-008.
- Một `stationId` có thể xuất hiện lặp lại nếu người dùng chủ động thêm nhiều lần. Requirement không cấm tuyến vòng và schema chỉ bắt buộc duy nhất `(route_id, stop_order)`, không phải `(route_id, station_id)`. Mỗi lần xuất hiện là một điểm dừng riêng. Đây là quyết định phạm vi, không phải suy đoán về một quy tắc hiện hữu.
- Form frontend yêu cầu tên tuyến để dễ sử dụng. API vẫn cho phép thiếu `code` khi tạo để giữ hành vi tự sinh mã hiện có; khi sửa, `code` để trống giữ lại mã cũ.

## Câu hỏi mở

Không có câu hỏi mở chặn Plan. Các quyết định phạm vi ở trên cần được người dùng xác nhận khi review trước implementation.
