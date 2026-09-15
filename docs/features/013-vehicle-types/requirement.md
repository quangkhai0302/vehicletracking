# 013 — Phân biệt ô tô và xe máy

Trạng thái: Implemented, PostgreSQL/browser verification pending (người dùng yêu cầu trực tiếp ngày 2026-09-15).

AC1: Khi tạo/sửa xe, người dùng chọn được **Ô tô** hoặc **Xe máy** và lựa chọn được lưu trong database.  
AC2: API danh sách/chi tiết xe và chuyến trả loại phương tiện; dữ liệu/request cũ thiếu loại mặc định là `CAR`.  
AC3: Danh sách đội xe, marker realtime và marker chờ mô phỏng dùng icon khác nhau theo loại.  
AC4: Giá trị ngoài `CAR`/`MOTORCYCLE` bị từ chối.  
AC5: Không thay đổi thuật toán định tuyến hoặc travel mode của HERE trong feature này.
