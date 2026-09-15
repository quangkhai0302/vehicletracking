# Sửa giao diện chọn vị trí trạm — 2026-09-15

## Nguyên nhân

`.map-picking-banner` nhận đồng thời `top` từ `workspace.css` và `bottom` từ `index.css`, khiến khối banner giãn gần toàn bộ chiều cao bản đồ. Khối trong suốt này bắt pointer event nên người dùng không kéo Leaflet để tìm vị trí đặt trạm.

## Thay đổi

- Ghi đè `bottom: auto` để banner chỉ cao theo nội dung.
- Cho pointer event xuyên qua nền banner; chỉ hai nút xác nhận/hủy nhận thao tác.
- Thêm tâm ngắm tại tâm vùng bản đồ khả dụng, có xét các panel đang che bản đồ.
- Đổi hướng dẫn thành “kéo bản đồ → đưa điểm vào tâm ngắm → Chọn vị trí này”.
- Đổi con trỏ sang `grab`/`grabbing`; vẫn giữ cách nhấp trực tiếp lên bản đồ và kéo marker sau khi chọn.

## Evidence

- `npm run lint`: exit 0, 0 error; còn 2 warning có sẵn tại `useFleetWorkspace` không liên quan thay đổi này.
- `tsc --noEmit`: exit 0.
- `npm run build`: exit 0.
- Chrome headless desktop: banner nhỏ hơn 90 px, tâm banner không che map, kéo map làm đổi tọa độ, tâm ngắm đứng yên và nút xác nhận ghi tọa độ mới — đạt.
- Kịch bản mobile đã được bổ sung vào `verification/station-map-picker.mjs`, nhưng chưa chạy lại vì môi trường từ chối quyền mở server sau khi đạt giới hạn sử dụng. Không tuyên bố mobile browser đã đạt.
