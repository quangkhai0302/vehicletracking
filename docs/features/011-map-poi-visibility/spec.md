# Spec

Checkbox Hiển thị địa điểm đặt trong Chi tiết bản đồ. State showPlaces mặc định true, giữ khi chuyển workspace/theme, reset khi reload như các tùy chọn lớp hiện có.

Khi true giữ nguyên URL nền. Khi false thêm style chỉ tác động poi/labels, không dùng ẩn tất cả labels. URL giữ base type và traffic hiện tại. Tách builder ở services/mapTiles.ts để tập trung phụ thuộc endpoint.

Không thêm HTTP API hay DB. Rủi ro: tham số của endpoint Google `/vt` không được bảo đảm bằng tài liệu chính thức; chưa coi AC2/AC4 đạt trước khi kiểm chứng ảnh thực tế.

