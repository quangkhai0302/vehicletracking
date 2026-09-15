# Feature 010 — Management endpoints

## Mục tiêu

Bổ sung API quản lý cho các phần còn thiếu CRUD nhưng không phá vỡ dữ liệu lịch sử.

## Tích hợp frontend — yêu cầu trực tiếp ngày 2026-09-14

Người vận hành cần thao tác các endpoint mới ngay trên UI hiện có. Phạm vi: sửa/ngừng tuyến; đổi giờ/xóa chuyến SCHEDULED; xem lịch sử vị trí theo chuyến, nguồn và thời gian có phân trang; đọc tất cả/xóa thông báo; xem và ngừng hiệu lực revision. Không thêm backend, migration, GPS ingestion UI hoặc cơ chế áp dụng revision lên xe.

AC-UI1: Sửa tuyến nạp sẵn tên/điểm dừng, PUT giữ ID; ngừng tuyến cần xác nhận và xóa tuyến khỏi danh sách/bản đồ khi thành công.
AC-UI2: Chỉ chuyến SCHEDULED hiển thị sửa lịch/xóa; lịch chi tiết và danh sách đồng bộ sau PUT; DELETE có xác nhận, không hồi sinh từ snapshot cũ.
AC-UI3: Lịch sử có lọc GPS/SIMULATOR, from/to, trang trước/sau, loading/empty/error/retry; chọn mẫu để xem vị trí.
AC-UI4: Đọc tất cả và xóa thông báo chỉ cập nhật UI sau API thành công; lỗi hiển thị, xóa có xác nhận.
AC-UI5: Danh sách revision có trạng thái và xác nhận supersede, không mô tả thao tác là tạo tuyến thay thế.
AC-UI6: Khóa thao tác khi gửi; giữ dữ liệu nhập khi lỗi; lint/typecheck/build và browser fixture checks có evidence. Không lộ secret.

## Phạm vi

- Route: cập nhật bằng cách tính lại HERE khi chưa từng được gắn vào trip; ngừng sử dụng bằng soft-delete.
- Trip: sửa giờ xuất phát khi còn `SCHEDULED`; xóa khi chưa có dữ liệu vận hành.
- Telemetry: truy vấn lịch sử có phân trang/bộ lọc; không sửa/xóa bản ghi audit.
- Notification: đánh dấu một/tất cả đã đọc và xóa thông báo.
- Route revision: đánh dấu revision đang hoạt động là `SUPERSEDED`.

## Acceptance criteria

1. Endpoint mới trả lỗi có kiểm soát khi tài nguyên không tồn tại hoặc đã vào trạng thái bất biến.
2. Route inactive không xuất hiện trong danh sách route; route/trip đang được sử dụng không bị sửa/xóa phá snapshot.
3. Trip chỉ được sửa/xóa khi `SCHEDULED`; lịch các stop được cập nhật đồng bộ khi đổi giờ.
4. Telemetry history hỗ trợ `tripId`, `vehicleId`, `source`, `from`, `to`, `page`, `size`.
5. Notification read-all/delete và revision supersede hoạt động idempotent.
