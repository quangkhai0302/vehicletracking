# Specification

## API contract

- `PUT /api/v1/routes/{id}` nhận `RouteCreateRequest`; chỉ route chưa được trip sử dụng mới được tính lại.
- `DELETE /api/v1/routes/{id}` soft-delete route; trả `204`, chặn nếu có trip `SCHEDULED/IN_PROGRESS`.
- `PUT /api/v1/trips/{id}` nhận `{scheduledDepartureAt}`; chỉ `SCHEDULED`.
- `DELETE /api/v1/trips/{id}` xóa trip `SCHEDULED` chưa có dữ liệu phụ thuộc; lỗi FK trả `409`.
- `GET /api/v1/telemetry/history` trả `{items,page,size,totalElements,totalPages}` và nhận bộ lọc tùy chọn.
- `POST /api/v1/notifications/read-all` trả `{updated}`; `DELETE /api/v1/notifications/{id}` trả `204`.
- `POST /api/v1/trips/{tripId}/revisions/{revisionId}/supersede` đánh dấu revision cũ.

Route/trip snapshot và telemetry evidence không bị chỉnh sửa tùy tiện. Route active flag dùng migration V8.

## UI contract bổ sung

- AC-UI1: Drawer chi tiết tuyến có Sửa tuyến/Ngừng sử dụng. Form edit tái sử dụng validation tạo tuyến, nạp stop theo thứ tự. Backend quyết định conflict tuyến từng có trip; lỗi giữ nguyên form. DELETE 204 đóng drawer, bỏ selection và map geometry.
- AC-UI2: Chi tiết chuyến SCHEDULED có form datetime-local đổi giờ (gửi ISO UTC) và xóa có xác nhận. Không cho thay xe/tuyến. Hiển thị lỗi 400/404/409 từ ProblemDetail. Snapshot cũ không ghi đè lịch vừa lưu hoặc hồi sinh chuyến vừa xóa.
- AC-UI3: Mở lịch sử theo tripId ở chi tiết chuyến; page size 20, page bắt đầu 0; source GPS/SIMULATOR hoặc tất cả; from/to tùy chọn chuyển sang ISO, from <= to. Bộ lọc áp dụng reset page; request cũ bị abort, data cũ không hiển thị dưới bộ lọc mới. Ghi rõ giờ recordedAt và simulatedAt khi có. Không sửa/xóa telemetry.
- AC-UI4: Hiển thị tối đa 50 thông báo gần nhất; đọc tất cả POST read-all, xóa DELETE 204. Chỉ áp dụng local override cho ID đã xác nhận thành công, không đánh dấu nhầm thông báo mới xuất hiện khi request đang chạy. Lỗi có role alert.
- AC-UI5: Panel revision theo chuyến, tải lại thủ công, status ACTIVE/SUPERSEDED, lý do và thời gian dự kiến. ACTIVE có nút ngừng hiệu lực và dialog nhắc thao tác không tạo tuyến thay thế. Sau POST cập nhật theo response.
- AC-UI6: Nút disabled khi busy, dialog chặn đóng khi gửi, panel cuộn/responsive theo layout hiện có. GET cleanup AbortController. Không thêm secret, API public contract hoặc schema.
