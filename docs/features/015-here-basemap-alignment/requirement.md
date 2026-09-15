# 015 — Đồng bộ nền bản đồ HERE

Trạng thái: Verified

Tuyến được tạo bởi HERE Routing phải được hiển thị trên nền HERE, tránh cảm giác lệch làn/đường khi so với dữ liệu Google. API key chỉ tồn tại ở backend.

## Acceptance criteria

1. Frontend tải nền đường bộ, vệ tinh và ban đêm qua ứng dụng, không gọi Google Maps tile trực tiếp.
2. Lớp giao thông dùng HERE Raster Tile riêng với tuyến và marker nằm phía trên.
3. API key HERE không xuất hiện trong bundle frontend hay URL trình duyệt.
