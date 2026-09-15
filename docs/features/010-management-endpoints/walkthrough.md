# Walkthrough

1. Chạy Flyway để áp dụng V8.
2. Dùng `PUT /routes/{id}` với route chưa có trip; dùng `DELETE` để ngừng route.
3. Dùng `PUT /trips/{id}` đổi giờ trip đang chờ và `DELETE` để xóa trip chưa chạy.
4. Dùng `GET /telemetry/history` để lọc dữ liệu GPS/SIMULATOR.
5. Dùng notification read-all/delete và revision supersede trong màn điều hành.

## Luồng trên giao diện

- Vào **Tuyến & trạm**, chọn một tuyến đã lưu: bấm **Sửa tuyến** để nạp lại các điểm dừng và tính PUT qua HERE; bấm **Ngừng sử dụng** để soft-delete sau khi xác nhận.
- Vào **Đội xe → Chuyến đi**, mở chuyến đang **Chờ khởi hành**: bấm **Sửa giờ xuất phát** hoặc **Xóa chuyến**. Chuyến đã chạy chỉ còn các thao tác lifecycle.
- Trong chi tiết chuyến, mở **Lịch sử vị trí** để lọc nguồn/thời gian và chuyển trang; chọn một dòng để đưa bản đồ tới tọa độ telemetry.
- Mục **Phiên bản tuyến** hiển thị ACTIVE/SUPERSEDED và cho phép ngừng hiệu lực revision ACTIVE. Đây không phải thao tác tạo tuyến mới.
- Mục **Cảnh báo** có **Đọc tất cả**, đánh dấu từng cảnh báo và xóa từng cảnh báo; mỗi hành động chỉ cập nhật sau khi backend trả thành công.
