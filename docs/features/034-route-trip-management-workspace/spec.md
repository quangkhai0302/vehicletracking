# Spec — Không gian quản lý tuyến và chuyến

## UI contract

- Sidebar có một mục “Tuyến & chuyến”, mở tab Chuyến đi theo path `/trips`; cả `/trips` và `/routes` đều làm mục này active.
- Hai tab có tên rõ, trạng thái `aria-current` hoặc tương đương, điều hướng bằng link. `/routes` mở tab Tuyến đường, `/trips` mở tab Chuyến đi; Back/Forward đổi tab đúng.
- Nội dung tab tái sử dụng toàn bộ trang hiện hành. Không nhồi danh sách tuyến và chuyến vào một bảng; drawer/form và empty/error/loading giữ nguyên.
- Chi tiết tuyến có “Tạo chuyến từ tuyến này”; điều hướng tới `/trips?routeId=<id>&create=1`, mở form tạo chuyến với tuyến được chọn sẵn. Người dùng vẫn chọn xe, tài xế và giờ xuất phát.
- Chi tiết chuyến có “Xem tuyến đường”; điều hướng tới `/routes?routeId=<id>`, mở drawer chi tiết tuyến. Nếu dữ liệu không tải được, hiển thị lỗi hiện hữu, không mất trang.
- Chi tiết chuyến dùng bố cục quản trị rộng: nhận diện xe/tuyến/tài xế và bốn mốc thời gian ở đầu; hành trình trạm là nội dung chính; tiến độ check-in và ETA là khối hỗ trợ riêng. Các hành động sửa lịch, đổi tài xế, xem tuyến, mở mô phỏng và chuyển trạng thái giữ nguyên.
- Ở chiều rộng trung bình, hành trình và khối hỗ trợ xếp một cột; ở mobile, metadata, mốc thời gian và nút thao tác tiếp tục xếp dọc, không tràn ngang hoặc ẩn dữ liệu nghiệp vụ.
- `vehicleId` query của chuyến vẫn lọc xe; query sai/không tồn tại không phá trang. Tab responsive và vẫn truy cập được bằng bàn phím.

## Contract và dữ liệu

Không thêm/sửa API, bảng, cột, migration hoặc quyền. Các query mới chỉ lưu ID để điều hướng, luôn validate số nguyên dương trước khi tự chọn; API backend hiện hành vẫn là nguồn sự thật.

## Ngoài phạm vi

Không đổi map `/operations`, route geometry editor, nghiệp vụ start/complete/cancel hoặc logic tính lộ trình. Không tự động tạo chuyến khi bấm liên kết; chỉ mở form.
