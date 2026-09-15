# Test Plan: Kiểm thử Station Management Redesign

## 1. Ma trận kiểm thử

| AC | Mức test | Kịch bản | Dữ liệu kiểm thử | Kết quả mong đợi |
|---|---|---|---|---|
| AC-1 | Build & Lint | Chạy oxlint, tsc và vite build | Node 24 | Không có cảnh báo/lỗi, bundle sinh thành công |
| AC-2 | Browse & Select | Click chọn trạm từ danh sách | Trạm bất kỳ | Danh sách highlight, map center đến trạm, geofence circle hiển thị, drawer bên phải mở |
| AC-3 | Map marker click | Click vào marker trạm trên bản đồ | Marker trên map | Bản đồ center vào marker, trạm được chọn trên danh sách, drawer mở |
| AC-4 | Create station | Nhấn "+ Thêm" -> click map -> nhập form -> Lưu | Tên: Trạm Test, R: 100 | Tạo mới trạm qua API, hiển thị toast, chọn trạm mới trên map |
| AC-5 | Edit station & Cancel | Chọn trạm -> Sửa -> Kéo marker -> Hủy | Trạm đang có | Trạng thái cũ được khôi phục nguyên vẹn, không đổi vị trí |
| AC-6 | Edit station & Save | Chọn trạm -> Sửa thông tin -> Lưu | Đổi tên/bán kính | Cập nhật qua API, toast thành công, geofence và chi tiết cập nhật |
| AC-7 | Deactivate | Nhấn "Ngừng sử dụng" -> xác nhận dialog | Trạm đang có | Gọi API xóa, loại bỏ khỏi danh sách active, đóng drawer |
| AC-8 | Typography & Tokens | Kiểm tra biến CSS font và màu sắc | CSS inspect | Sử dụng font Inter và semantic tokens |
