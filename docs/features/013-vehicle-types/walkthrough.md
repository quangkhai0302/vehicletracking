# Walkthrough

Trong **Theo dõi → Đội xe**, chọn **Thêm xe mới** hoặc sửa xe hiện có. Chọn **Ô tô** hoặc **Xe máy**, nhập biển số/tên rồi lưu. Danh sách hiển thị icon và nhãn loại xe.

Khi xe có vị trí GPS hoặc đang mô phỏng, marker bản đồ dùng hình nhìn từ trên xuống tương ứng và vẫn xoay theo heading. Xe chưa chạy ở trạm đầu cũng dùng icon đúng loại; nếu nhiều loại cùng vị trí, marker nhóm hiển thị cả hai glyph. Tooltip có thêm nhãn Ô tô/Xe máy.

Backend tự áp dụng migration V10 khi khởi động. Xe đã tồn tại và request client cũ không gửi `vehicleType` mặc định là `CAR`. Feature này không đổi travel mode của HERE; tuyến hiện tại vẫn được tính theo cấu hình routing đang có.
