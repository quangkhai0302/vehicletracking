# Requirement 023 — Lập lịch chuyến tự động

Trạng thái: **Implemented** — kiểm tra non-Docker đạt ngày 2026-09-21; PostgreSQL integration cần Docker.

## Bối cảnh và mục tiêu

Trang `/schedules` hiện chỉ là roadmap, trong khi `trips` mới chỉ hỗ trợ tạo từng chuyến. Feature này cho phép điều phối viên cấu hình lịch cố định theo tuyến, ngày/giờ hoặc thứ trong tuần; backend tự sinh các chuyến cụ thể với xe và tài xế đã chọn.

## Phạm vi

### In scope

- Tạo, sửa, xem danh sách, bật/tắt lịch.
- Hai kiểu lặp: một lần (`ONCE`) hoặc lặp theo thứ trong tuần (`WEEKLY`).
- Một giờ khởi hành địa phương cho mỗi lịch và timezone IANA rõ ràng.
- Gán bắt buộc một tuyến active, xe active và tài xế active cho lịch.
- Scheduler tạo trip `SCHEDULED` trong cửa sổ tương lai hữu hạn và không tạo trùng cùng một occurrence.
- Hiển thị lần chạy kế tiếp và kết quả sinh gần nhất.

### Out of scope

- Auto-assign theo pool tài nguyên, nhiều giờ trong một lịch, ngày lễ/ngoại lệ, hoặc lịch theo tháng.
- Bù vô hạn các occurrence đã lỡ khi backend ngừng chạy.
- Phân quyền/authentication thực tế.
- Chỉnh sửa lịch đã sinh ngược lại các chuyến đã tạo.
- Xóa vật lý trip đã được sinh từ lịch; trip đó chỉ có thể chuyển trạng thái theo vòng đời hiện có.

## Acceptance criteria

- AC-01: Người dùng tạo được lịch ONCE hoặc WEEKLY với route, vehicle, driver, local time, timezone và ngày hiệu lực hợp lệ.
- AC-02: Người dùng xem được danh sách lịch, trạng thái bật/tắt, quy tắc lặp, lần chạy kế tiếp và kết quả sinh gần nhất.
- AC-03: Người dùng sửa lịch; lịch tắt không sinh chuyến mới; bật lại tiếp tục theo quy tắc hiện tại.
- AC-04: Scheduler tạo trip `SCHEDULED` với route/vehicle/driver và snapshot hiện có, không gọi HTTP nội bộ.
- AC-05: Một occurrence chỉ tạo tối đa một trip dù scheduler chạy lại hoặc có hai transaction cạnh tranh.
- AC-06: Lịch không sinh từ route/vehicle/driver inactive; lỗi được ghi vào trạng thái lần chạy gần nhất và không làm dừng các lịch khác.
- AC-07: UI có loading, empty, error/retry, validation, xác nhận bật/tắt và responsive layout.
- AC-08: Flyway migration mới, API, type và UI nhất quán; lint, typecheck, backend test và build phù hợp có evidence.

## Giả định nghiệp vụ

- Một lịch chỉ có một xe và một tài xế cố định; cả hai là bắt buộc để tránh ngầm suy diễn từ quan hệ xe–tài xế.
- `WEEKLY` dùng bit ngày ISO thứ Hai–Chủ nhật; `ONCE` dùng một ngày địa phương.
- Timezone mặc định trên UI là `Asia/Ho_Chi_Minh`, nhưng được lưu cùng lịch và backend là nguồn sự thật; offset cố định không được chấp nhận.
- Scheduler chỉ mở rộng trong 7 ngày tới; occurrence đã qua không tự sinh bù vô hạn.
