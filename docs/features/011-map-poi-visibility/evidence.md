# Evidence

Trạng thái: Implementing — chờ xác minh provider/browser.

- `MapControls.tsx`: checkbox Hiển thị địa điểm và callback; `MapComponent.tsx`: state mặc định true, effect reload tile khi đổi POI và cleanup retry timer/listener.
- `services/mapTiles.ts#mapTileUrl`: builder giữ loại nền/traffic và chỉ thêm style poi/labels khi tắt. Đã sửa dấu phân cách query `/vt?lyrs=`; URL cũ `/vt/lyrs=` đặt các tham số trong path, khiến kiểm tra URLSearchParams không tìm thấy style.
- Kiểm tra cục bộ `/tmp/vehicletracking-poi-url-check.mjs`: PASS 12 biến thể (3 nền × 2 traffic × 2 POI), xác nhận query style/base/traffic; không chứng minh nội dung ảnh từ provider.
- Node 24.16.0: npm run lint, tsc --noEmit, npm run build — exit 0, 1900 modules. git diff --check không báo lỗi.
- Ảnh thử đầu với `e:labels` không ẩn POI. Chưa kiểm chứng `s.e:labels` và URL cuối: tải ảnh bị automatic approval review từ chối do usage limit. AC2/AC4 pending. Không có thay đổi API/schema/credential hoặc dữ liệu người dùng.
- Không tuyên bố hoàn thành hành vi ẩn địa điểm cho đến khi xác nhận ảnh ở cả đường bộ/ban đêm/vệ tinh với traffic bật và tắt. `/vt` và apistyle không có bảo đảm chính thức như Map Tiles API.
