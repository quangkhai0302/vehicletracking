# Spec: Đặc tả Giao diện Station Management Redesign

## 1. Bố cục không gian (Layout Contract)
- **Top Header**: Cao 60px, kính mờ tối (dark glassmorphism nhẹ), chứa logo điều hành, bộ chuyển workspace (`Theo dõi xe` / `Quản lý trạm`) và trạng thái hệ thống.
- **Bản đồ chính**: Chiếm toàn bộ khung nhìn nền (`100vw x 100vh`).
- **Station Panel (Bên trái)**:
  - Vị trí: `top: 92px; left: 16px; width: min(350px, calc(100vw - 32px))`.
  - Header: Eyebrow "DỮ LIỆU VẬN HÀNH", tiêu đề "Danh sách trạm", nút "+ Thêm" (Primary).
  - Search input: Tìm kiếm realtime theo tên và địa chỉ trạm.
  - Danh sách trạm: Thiết kế compact với icon pin, tên trạm in đậm, địa chỉ và bán kính check-in.
  - Trạng thái chọn: Nền màu cyan nhạt (`rgba(56, 189, 248, 0.09)`), viền cyan nổi bật.
  - Footer: Hiển thị tổng số trạm và trạng thái mode hiện tại.
- **Contextual Detail Drawer (Bên phải)**:
  - Vị trí: `top: 92px; right: 16px; width: min(380px, calc(100vw - 32px))`.
  - Xuất hiện khi có trạm được chọn hoặc khi đang trong mode `create` / `edit`.
  - Khi drawer mở, panel điều khiển bản đồ góc dưới bên phải dịch sang trái để không bị che khuất.

## 2. Các chế độ hoạt động (Modes)
- **BROWSE**:
  - Xem chi tiết trạm đang chọn.
  - Thông tin hiển thị dạng danh sách thuộc tính chuẩn hóa: Tên trạm, Trạng thái (Đang hoạt động), ID, Địa chỉ, Vĩ độ, Kinh độ (định dạng `tabular-nums`), Bán kính check-in, Thời gian cập nhật cuối.
  - Hành động: "Chỉnh sửa" (Primary) và "Ngừng sử dụng" (Secondary Danger).
- **CREATE**:
  - Banner "Nhấp lên bản đồ để đặt vị trí trạm" hiển thị ở đáy màn hình.
  - Chuột đổi thành crosshair.
  - Click trên bản đồ đặt marker nháp màu cam (draft) và vẽ geofence preview nét đứt.
  - Form nhập: Tên trạm (*), Địa chỉ, Vĩ độ (*), Kinh độ (*), Bán kính check-in (*).
  - Nút "Hủy" (Secondary) và "Tạo trạm" (Primary). Hủy sẽ dọn dẹp draft marker và đóng drawer.
- **EDIT**:
  - Form điền sẵn dữ liệu hiện tại của trạm.
  - Marker trên bản đồ chuyển thành draft marker màu cam có thể kéo thả để tinh chỉnh vị trí, hoặc bấm "Chọn lại vị trí".
  - Geofence circle màu cam phản ánh bán kính check-in ngay khi người dùng gõ số.
  - Nút "Hủy" và "Lưu thay đổi". Khi "Hủy", khôi phục nguyên vẹn trạng thái cũ và trở về chế độ BROWSE của trạm đó.

## 3. Bản đồ & Geofence Contract
- Marker trạm thường: Màu xanh cyan/sky, bo tròn nhọn đáy (pin shape), chấm trắng ở giữa.
- Marker trạm được chọn: Nổi bật với subtle halo ring xung quanh (`box-shadow: 0 0 0 6px rgba(...)`), phóng to 1.18x, nâng z-index lên 500.
- Draft marker: Màu hổ phách (amber), hỗ trợ kéo thả `draggable: true` với tooltip hướng dẫn.
- Geofence Circle: Chỉ vẽ cho trạm được chọn (`#0284c7`, fillOpacity: 0.12) hoặc trạm draft (`#f59e0b`, dashArray: '6 6', fillOpacity: 0.12).
