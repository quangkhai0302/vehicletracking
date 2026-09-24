# Walkthrough — Không gian “Kế hoạch vận hành”

## Điều hướng chính

Sidebar chỉ còn một mục **Tuyến & chuyến** với tiêu đề trang **Kế hoạch vận hành**. Hai URL cũ vẫn được giữ:

- `/routes`: mở tab **Tuyến đường**.
- `/trips`: mở tab **Chuyến đi**.

Hai tab là link thật, có `aria-current`, hỗ trợ Back/Forward và bàn phím. Khi ở một trong hai URL, cùng một mục sidebar được đánh dấu active.

## Luồng từ tuyến sang chuyến

1. Mở tab **Tuyến đường** và chọn xem một tuyến.
2. Chọn **Tạo chuyến từ tuyến này** trong drawer chi tiết.
3. Hệ thống chuyển tới `/trips?routeId=<id>&create=1` và mở form tạo chuyến.
4. Tuyến đã được chọn sẵn; người dùng tiếp tục chọn xe, tài xế rồi bấm **Tạo chuyến tức thời**. Giờ chạy cố định được cấu hình ở **Lịch chạy tự động**, không nhập tại đây.
5. Nếu đóng form ngay mà chưa sửa dữ liệu, không xuất hiện cảnh báo bản nháp giả. Nếu tuyến không còn hoạt động, form báo tuyến không khả dụng và yêu cầu chọn tuyến khác.

## Luồng từ chuyến về tuyến

Trong chi tiết chuyến, chọn **Xem tuyến đường**. Hệ thống mở `/routes?routeId=<id>` và tải drawer của đúng tuyến. Trạng thái tải/lỗi nằm trong drawer, không làm mất danh sách tuyến.

## Màn hình chi tiết chuyến

- Phần đầu nhận diện nhanh phương tiện, tuyến đường, tài xế và trạng thái chuyến.
- Chuyến từ lịch cố định hiển thị mốc kế hoạch và thực tế; chuyến điều phối tức thời chỉ hiển thị thời điểm tạo và mốc thực tế.
- Timeline trạm là vùng nội dung chính; trạm đã ghi nhận có trạng thái và giờ thực tế/giờ mô phỏng riêng.
- Cột phụ hiển thị tỷ lệ check-in và ETA. Trên tablet/mobile, cột này chuyển xuống dưới timeline, còn metadata và nút thao tác tự xếp dọc.

## Tương thích và phạm vi

- Query `vehicleId` của trang chuyến vẫn hoạt động.
- Query ID phải là số nguyên dương; giá trị sai bị bỏ qua an toàn.
- Toàn bộ dữ liệu vẫn lấy từ API hiện hữu; không tạo model hợp nhất hoặc dữ liệu giả.
- Không thay đổi màn hình giám sát `/operations`, logic bản đồ, API backend hay schema database.
