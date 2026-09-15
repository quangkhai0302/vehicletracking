# Test Plan: Kế hoạch Kiểm thử Tính năng Theo dõi Xe & Trải nghiệm Quản lý Trạm

## 1. Ma trận Kiểm thử

| AC | Mức Kiểm thử | Kịch bản Kiểm thử | Dữ liệu / Đầu vào | Kết quả Mong đợi |
|---|---|---|---|---|
| AC-01 | Component & Manual | Mở ứng dụng vào tab "Theo dõi xe", kiểm tra hiển thị panel bên trái | Dữ liệu mô phỏng từ `telemetrySimulator.ts` | Hiển thị 3 chỉ số tổng quan; danh sách xe hiển thị đầy đủ biển số, tốc độ km/h, ETA và trạng thái. |
| AC-02 | Visual & Integration | Kiểm tra marker xe hiển thị và di chuyển trên bản đồ | Tọa độ xe chạy dọc Tuyến 01 TP.HCM | Marker xuất hiện trên bản đồ, xoay đầu theo hướng di chuyển, cập nhật vị trí mỗi giây. |
| AC-03 | Component & State | Thao tác trên thanh điều khiển Simulator | Bấm Pause → Chạy dừng; Bấm Play → Xe tiếp tục chạy; Chọn 5x → Xe di chuyển nhanh gấp 5 lần | Trạng thái mô phỏng thay đổi tương ứng, nhịp cập nhật tọa độ đồng bộ với multiplier. |
| AC-04 | Interaction | Nhấp chọn một xe trên danh sách hoặc bản đồ | Nhấp xe `51B-299.88` | Bản đồ phóng to tới xe, vẽ polyline lộ trình, mở VehicleDrawer bên phải đầy đủ thuộc tính và timeline. |
| AC-05 | Interaction | Chuyển sang "Quản lý trạm", bấm "+ Thêm trạm", chọn tọa độ | Nhấp bản đồ HOẶC bấm nút "Lấy vị trí tâm bản đồ" | Tọa độ vĩ độ/kinh độ tự động điền vào form; marker dự thảo xuất hiện kèm vòng tròn geofence. |
| AC-06 | Form UX | Kéo slider bán kính check-in hoặc bấm preset 50m / 100m | Chọn preset `100m` | Ô số cập nhật `100`, slider di chuyển đến mốc 100, vòng tròn geofence trên bản đồ mở rộng đúng 100m. |
| AC-07 | Form Guard | Đang nhập dở tên trạm hoặc tọa độ, bấm nút "Hủy" hoặc nút "X" | Form đã thay đổi dữ liệu | Hiển thị popup hỏi xác nhận hủy bỏ thay đổi; nếu người dùng chọn Tiếp tục chỉnh sửa thì không mất dữ liệu. |
| AC-08 | Dialog & CRUD | Chọn xóa một trạm trên danh sách | Nhấp biểu tượng thùng rác của trạm | Hộp thoại hiển thị tên trạm, tọa độ rõ ràng; bấm "Ngừng sử dụng" gọi API soft-delete thành công. |
| AC-09 | Tự động hóa (CI/CD) | Kiểm tra toàn bộ mã nguồn | Scripts trong `package.json` | Linter `oxlint`, `tsc --noEmit`, và `vite build` thoát với mã 0. |

## 2. Kịch bản Kiểm thử Thủ công (Manual Test Steps)

### Kịch bản 1: Giám sát Đội xe & Theo dõi Hành trình
1. Mở web ở chế độ "Theo dõi xe".
2. Quan sát số liệu tổng quan trên `TrackingPanel` (ví dụ: 3 xe đang hoạt động).
3. Thử tìm kiếm biển số `51B-299.88` trên ô search; danh sách lọc chính xác xe cần tìm.
4. Nhấp vào thẻ xe `51B-299.88`:
   - Bản đồ chuyển tâm tới xe.
   - Polyline tuyến đường màu xanh lam xuất hiện dọc cung đường Bến xe Miền Đông ⇄ Chợ Bến Thành.
   - `VehicleDrawer` mở ra ở cạnh phải, hiển thị đồng hồ tốc độ, trạm kế tiếp và timeline.
5. Bật nút "Bám theo xe" (`Follow Vehicle`); quan sát bản đồ tự động cuộn theo xe khi xe di chuyển.
6. Thử bấm Pause trên thanh Simulator Controls: xe dừng lại; bấm 5x: xe tăng tốc di chuyển.

### Kịch bản 2: Trải nghiệm Thêm trạm với Nút Tâm Bản đồ & Slider Bán kính
1. Nhấp chuyển sang tab "Quản lý trạm".
2. Bấm nút "+ Thêm trạm".
3. Di chuyển bản đồ đến một vị trí mong muốn, bấm nút "Lấy vị trí tâm bản đồ".
4. Quan sát vĩ độ và kinh độ được tự động điền chính xác.
5. Kéo slider bán kính từ 50m lên 150m, bấm thử nút preset 80m. Vòng tròn đứt nét trên bản đồ co giãn tương ứng.
6. Nhập tên trạm "Trạm Thử Nghiệm", bấm "Tạo trạm".
7. Trạm mới xuất hiện trên danh sách và bản đồ, hiển thị toast thông báo thành công.

### Kịch bản 3: Bảo vệ Form Dở dang (Dirty Form Protection)
1. Bấm "+ Thêm trạm", gõ một phần tên trạm "Trạm Dở Dang".
2. Bấm nút "Hủy".
3. Hệ thống hiển thị cảnh báo xác nhận có thay đổi chưa lưu.
4. Bấm "Ở lại chỉnh sửa" -> Dữ liệu được bảo toàn nguyên vẹn.
5. Bấm "Hủy thay đổi" -> Form đóng an toàn và reset trạng thái.

## 3. Lệnh Kiểm tra Tự động
```bash
cd vehicletracking-frontend
npm run lint
./node_modules/.bin/tsc --noEmit
npm run build
```
