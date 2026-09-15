# Survey: Khảo sát Hiện trạng Mã nguồn Giao diện & Luồng Dữ liệu

## 1. Cây File Liên quan
```text
vehicletracking-frontend/
├── src/
│   ├── App.tsx                          # Shell ứng dụng, header điều hướng giữa tracking & stations
│   ├── main.tsx                         # Entry point React
│   ├── index.css                        # CSS hệ thống, tokens, styles cho layout, panel, markers
│   ├── components/
│   │   ├── MapComponent.tsx             # Component bản đồ chính, quản lý Leaflet map và state
│   │   ├── MapControls.tsx              # Điều khiển theme bản đồ, reset center, hiển thị tọa độ
│   │   ├── StationPanel.tsx             # Panel danh sách trạm bên trái
│   │   ├── StationDrawer.tsx            # Drawer xem chi tiết và form tạo/sửa trạm bên phải
│   │   └── TrackingPanel.tsx            # Panel theo dõi xe hiện tại (chỉ có empty state tĩnh)
│   ├── data/
│   │   └── mockData.ts                  # Dữ liệu mẫu trạm và polyline Tuyến 01 TP.HCM
│   ├── services/
│   │   ├── stations.ts                  # API client gọi backend CRUD trạm (/api/v1/stations)
│   │   ├── hereTraffic.ts               # Dịch vụ traffic HERE (chưa hoàn thiện)
│   │   └── polyline.ts                  # Tiện ích giải mã polyline
│   └── types/
│       ├── map.ts                       # Định nghĩa Station, Route, Traffic
│       ├── station.ts                   # Định nghĩa Station, StationInput, FormState
│       └── workspace.ts                 # Type WorkspaceMode ('tracking' | 'stations')
```

## 2. Bảng Khảo sát & Evidence

| Hiện trạng trong Code | File & Dòng / Ký hiệu | Đánh giá & Tác động |
|---|---|---|
| `TrackingPanel` chỉ có số liệu 0 tĩnh và giao diện rỗng | [TrackingPanel.tsx:18-28](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/TrackingPanel.tsx#L18-L28) | Màn hình "Theo dõi xe" hoàn toàn không có xe, không có dữ liệu để vận hành hay quan sát. |
| `MapComponent` chưa có layer quản lý vehicle hoặc route polyline | [MapComponent.tsx:50-55](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.tsx#L50-L55) | Chỉ có `stationLayerRef` và `draftLayerRef`, thiếu `vehicleLayerRef` và `routeLayerRef`. |
| Header badge báo trạng thái cứng "Chờ nguồn telemetry" | [App.tsx:41-45](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/App.tsx#L41-L45) | Chưa kết nối với bộ mô phỏng telemetry để hiển thị trạng thái phát sóng thực tế. |
| Phân loại trạm (Đầu/Cuối/Dừng) dựa trên phỏng đoán tên tiếng Việt | [StationPanel.tsx:20-35](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationPanel.tsx#L20-L35) | Logic `lower.includes('đầu')` hoặc dựa vào số thứ tự index trong danh sách gây phân loại sai lệch khi lọc/sắp xếp. |
| Nút Sửa/Xóa trạm trong StationCard chỉ hiện khi hover | [StationPanel.tsx:174-205](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationPanel.tsx#L174-L205) | Trên màn hình cảm ứng hoặc người dùng không rê chuột, các nút chức năng bị ẩn, khó thao tác. |
| Ô nhập bán kính check-in chỉ là input number đơn điệu | [StationDrawer.tsx:208-218](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationDrawer.tsx#L208-L218) | Người dùng khó hình dung diện tích bao phủ của bán kính, không có nút chọn nhanh mốc chuẩn. |
| Chưa có cơ chế bảo vệ form khi đóng dở dang (dirty state) | [StationDrawer.tsx:63-71](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/StationDrawer.tsx#L63-L71) | Bấm nút X hoặc phím Esc sẽ hủy toàn bộ nội dung đã nhập mà không hỏi xác nhận. |
| Chọn vị trí trạm chỉ hỗ trợ click map, không có nút lấy tâm bản đồ | [MapComponent.tsx:161-176](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.tsx#L161-L176) | Người dùng đã căn giữa trạm trên bản đồ vẫn phải click thêm lần nữa, dễ lệch vị trí. |
| Đã có sẵn dữ liệu polyline và tọa độ Tuyến 01 TP.HCM | [mockData.ts:61-70](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/data/mockData.ts#L61-L70) | Có thể tái sử dụng trực tiếp để làm dữ liệu nền tảng cho Fleet Simulator di chuyển mượt mà. |

## 3. Hành vi Còn thiếu & Điểm không nhất quán
1. **Thiếu hoàn toàn chức năng theo dõi xe**: Người dùng vào web thấy một bản đồ và một panel rỗng "0 xe", không thể hiện chức năng "Vehicle Tracking".
2. **Thiếu sự gắn kết giữa Bản đồ và Dữ liệu xe**: Không có marker xe, không có polyline lộ trình xe đang chạy, không có tương tác chọn xe để phóng to (focus/center) hoặc bám theo xe (follow).
3. **Form quản lý trạm thiếu thân thiện**: Thao tác nhập liệu tọa độ và bán kính đòi hỏi người dùng phải tự gõ số thay vì có công cụ tương tác trực quan (quick presets, slider, center button).
4. **Thiếu Simulator Controls**: Không có thanh công cụ Play/Pause/Reset để trình diễn luồng xe chạy realtime.

## 4. Ràng buộc Kỹ thuật & Rủi ro Hồi quy
- **Bảo toàn CRUD Backend**: Backend API `/api/v1/stations` không thay đổi. Mọi cải tiến form trạm phải giữ nguyên kiểu dữ liệu gửi lên:
  `{ name: string, address: string | null, latitude: number, longitude: number, checkinRadiusMeters: number }`.
- **Hiệu năng Render**: Khi xe di chuyển liên tục mỗi giây, không được trigger re-render toàn bộ `MapComponent`. Cần tách logic cập nhật marker Leaflet và state React hợp lý để tránh giật khung hình.
