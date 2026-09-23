# Walkthrough 022 — Business Management Shell

## Kết quả người dùng nhìn thấy

Ứng dụng khởi động tại `/dashboard` thay vì mở thẳng bản đồ. Layout mới gồm sidebar theo module, topbar mô tả ngữ cảnh và vùng nội dung business có thể cuộn độc lập.

### Dashboard

- Hiển thị số xe/tài xế hoạt động và trạng thái chuyến từ ba API hiện có.
- Có loading skeleton, lỗi có nút thử lại, empty state và danh sách chuyến gần đây.
- Các KPI chưa có backend như lệch tuyến, cảnh báo cần xử lý và đúng giờ được ghi rõ “Chưa có nguồn dữ liệu tổng hợp”.

### Quản lý vận hành

- `/vehicles`, `/drivers`, `/trips` dùng lại toàn bộ luồng CRUD và phân công của Feature 021 trong trang nội dung rộng.
- Ba trang có business module header riêng, KPI lấy từ dữ liệu đã tải, CTA chính ở đầu trang, filter toolbar sáng và list card phù hợp hơn với từng domain; form/detail vẫn giữ luồng vận hành hiện có.
- Trạng thái loading, lỗi, empty và bộ lọc được giữ rõ ràng; khi API không khả dụng, UI hiển thị lỗi và nút thử lại thay vì số liệu giả.
- `/operations` giữ bản đồ, realtime, traffic và mô phỏng. Cảnh báo được gom vào nút Cảnh báo trên command bar với badge chưa đọc; drawer chỉ mở khi người vận hành cần xem, giúp bản đồ không bị chiếm bởi panel cố định. Trên mobile drawer hiển thị như sheet và đóng bằng Escape hoặc nút đóng.
- `/routes`, `/stations` mở đúng workspace cần bản đồ.

### Lộ trình sản phẩm

- `/schedules`, `/alerts`, `/reports`, `/users` có trang mô tả phạm vi và dependency kỹ thuật.
- Các trang này không gọi API giả, không hiển thị số liệu giả và không tuyên bố phân quyền đã hoạt động.

### Responsive và điều hướng

- Desktop business pages dùng sidebar cố định; ba trang bản đồ dùng rail 72 px có thể mở rộng để ưu tiên diện tích quan sát.
- Trên desktop map pages, topbar mô tả được bỏ và thanh Theo dõi/Tuyến/Mô phỏng bám sát mép trên bản đồ.
- Từ 960 px trở xuống sidebar chuyển thành drawer phủ; có thể đóng bằng nút, overlay hoặc Escape.
- URL là nguồn điều hướng thật nên Back/Forward và direct URL hoạt động với Caddy SPA fallback hiện có.

## Ghi chú vận hành

- Bản đồ được lazy-load, nên dashboard và trang roadmap không phải tải chunk Leaflet/operations ngay lúc đầu.
- Chưa có authentication; mọi route hiện vẫn là route giao diện công khai như hệ thống trước feature này.
- Các feature roadmap cần feature ID/spec/migration/API riêng trước khi triển khai.

### Trạng thái cảnh báo hiện tại

- Alert center hiện đọc và quản lý notification đổi tuyến từ API hiện có.
- Cảnh báo lệch tuyến, quá tốc độ và các rule cảnh báo vận hành vẫn là dependency backend của roadmap; UI không hiển thị số liệu hoặc cảnh báo giả.
