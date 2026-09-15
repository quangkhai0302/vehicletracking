# Spec

Thêm enum `VehicleType` gồm `CAR`, `MOTORCYCLE`. Migration V10 thêm `vehicles.vehicle_type VARCHAR(20) NOT NULL DEFAULT 'CAR'` và check constraint. Entity/API xe lưu và trả trường này; request thiếu trường được chuẩn hóa thành `CAR` để tương thích dữ liệu/client cũ.

`TripSummaryResponse` trả `vehicleType` từ xe hiện tại để operations snapshot và frontend chọn icon mà không thay schema trip. Frontend thêm trường bắt buộc trong form và hiển thị nhãn/icon loại xe. Marker realtime dùng SVG nhìn từ trên xuống riêng cho ô tô/xe máy; marker xe chờ mô phỏng cũng dùng đúng glyph. Nhóm nhiều xe cùng điểm hiển thị glyph hỗn hợp khi có cả hai loại.

Loại xe trong feature này là metadata nhận diện và trình bày. HERE routing vẫn dùng travel mode hiện có; thay đổi tính tuyến xe máy là phạm vi khác.
