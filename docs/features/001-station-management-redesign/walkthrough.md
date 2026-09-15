# Walkthrough: Station Management Redesign

## 1. Bài toán đã giải quyết
Tái thiết kế màn hình Quản lý trạm theo hướng hiện đại, chuẩn trung tâm điều hành đội xe (Fleet Operations), lấy bản đồ làm trung tâm, danh sách trạm compact, drawer chi tiết theo ngữ cảnh, geofence vòng tròn chỉ hiển thị cho trạm được chọn, các chế độ BROWSE / CREATE / EDIT rõ ràng, tuân thủ nghiêm ngặt `docs/design.md`.

## 2. Các điểm cải tiến chính
1. **Map-centric Layout**: Bản đồ Leaflet phủ toàn bộ không gian làm việc. Các bảng điều khiển và danh sách trạm nổi lên trên với giao diện dark panel và bo góc 12px tinh tế.
2. **Compact Station List**: Danh sách trạm bên trái gọn gàng, hỗ trợ tìm kiếm realtime theo tên hoặc địa chỉ. Mỗi item thể hiện tên trạm, địa chỉ và bán kính check-in.
3. **Selected State & Geofence**:
   - Khi chọn trạm (từ danh sách hoặc nhấp marker), bản đồ tự động pan/center vào vị trí trạm.
   - Marker trạm được chọn có viền hào quang (subtle halo) sáng và nâng z-index.
   - Vòng tròn Geofence xanh dương với nền bán trong suốt (`opacity: 0.12`) chỉ xuất hiện quanh trạm được chọn.
4. **Contextual Detail Drawer**:
   - Nằm ở phía phải màn hình.
   - Hiển thị đầy đủ: Tên trạm, Trạng thái (Active), ID, Địa chỉ, Tọa độ (định dạng `tabular-nums`), Bán kính check-in, Thời gian cập nhật cuối.
   - Tuân thủ cấu trúc thuộc tính dọc theo Mục 12 `docs/design.md`, không dùng card chia vụn.
5. **Chế độ Tạo & Sửa rõ ràng**:
   - Chế độ `CREATE`: Banner hướng dẫn nhấp bản đồ, con trỏ crosshair, marker nháp màu hổ phách (amber) có thể kéo thả, vòng tròn geofence nét đứt preview bán kính tức thời.
   - Chế độ `EDIT`: Marker và geofence chuyển sang trạng thái nháp có thể chỉnh sửa; nút "Hủy" phục hồi nguyên trạng thái cũ.
6. **Typography & Design Tokens**:
   - Font Inter tiêu chuẩn cho toàn bộ ứng dụng.
   - Hệ thống token `--color-bg`, `--color-surface`, `--color-border`, `--color-primary`, `--color-text-*`.

## 3. Cách kiểm tra
```bash
bash -c 'source ~/.nvm/nvm.sh && nvm use 24 && cd /home/khainq/Code/vehicletracking/vehicletracking-frontend && npm run lint && ./node_modules/.bin/tsc --noEmit && npm run build'
```
