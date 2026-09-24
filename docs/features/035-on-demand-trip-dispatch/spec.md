# Spec — Điều phối chuyến tức thời

Trạng thái: **Approved**

## Mô hình nghiệp vụ

- `ON_DEMAND`: chuyến do điều phối viên tạo thủ công. Không có giờ kế hoạch do người dùng nhập.
- `FIXED_SCHEDULE`: chuyến do lịch chạy tự động sinh. Occurrence là giờ xuất phát kế hoạch.
- Nguồn được suy ra từ `trips.schedule_id`; không bổ sung cột mới.
- `scheduled_departure_at` tiếp tục không-null để làm mốc kỹ thuật tương thích. Với `ON_DEMAND`, giá trị là thời điểm tạo theo operations clock và không được trình bày như lịch kế hoạch.

## API

### Tạo chuyến tức thời

`POST /api/v1/trips`

```json
{
  "vehicleId": 1,
  "routeId": 2,
  "driverId": 3
}
```

`driverId` có thể null. Request không còn nhận `scheduledDepartureAt`.

### Response chuyến

`TripSummaryResponse` bổ sung:

- `dispatchMode`: `ON_DEMAND` hoặc `FIXED_SCHEDULE`.
- `scheduleId`: ID lịch nguồn, null với chuyến tức thời.
- `scheduleName`: tên lịch nguồn, null với chuyến tức thời.

Các field thời gian cũ được giữ để tương thích. Client phải dựa vào `dispatchMode` trước khi gắn nhãn thời gian kế hoạch.

### Sửa giờ chuyến

Loại bỏ `PUT /api/v1/trips/{id}` khỏi UI và controller. Giờ lịch được thay đổi qua API lịch tự động; occurrence đã sinh là snapshot lịch sử.

## Quy tắc service

- `create`: dùng `operationsClock.instant()` làm mốc kỹ thuật; không kiểm tra chồng lịch dự kiến.
- `createFromSchedule`: dùng occurrence, kiểm tra xung đột thời gian với các chuyến `FIXED_SCHEDULE` chưa kết thúc của cùng xe.
- `start`: không dùng mốc dự kiến để chặn. Giữ kiểm tra trạng thái, xe/tài xế hoạt động, đã gán tài xế và xe/tài xế không có chuyến `IN_PROGRESS` khác.
- Replay của chuyến cố định không được ghi đè giờ kế hoạch; replay chuyến tức thời có thể cập nhật mốc kỹ thuật cho attempt mới.

## UI

- Form “Điều phối chuyến ngay” chọn xe, tài xế, tuyến; không có input thời gian.
- Preview chỉ hiển thị thời lượng, số điểm và khoảng cách của tuyến.
- Chuyến `FIXED_SCHEDULE`: nhãn “Theo lịch cố định”, hiển thị xuất phát/hoàn thành kế hoạch.
- Chuyến `ON_DEMAND`: nhãn “Điều phối tức thời”, hiển thị thời điểm tạo, khởi hành và kết thúc thực tế.
- Không có nút hoặc form “Sửa giờ xuất phát” trong chi tiết chuyến.
- Danh sách/dashboard/cổng tài xế/thẻ theo dõi dùng nhãn tương ứng với nguồn chuyến.

## Tương thích

Không migration và không sửa dữ liệu cũ. Bản ghi cũ không có `schedule_id` được xem là `ON_DEMAND`. Các field thời gian response cũ vẫn tồn tại nhằm tránh phá luồng mô phỏng và consumer chưa chuyển đổi.

## Acceptance criteria

Áp dụng AC-01 đến AC-07 trong `requirement.md`.
