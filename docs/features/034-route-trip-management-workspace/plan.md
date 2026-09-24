# Plan — Không gian quản lý tuyến và chuyến

Trạng thái: Approved. Người dùng đã chấp thuận bố cục hai tab và yêu cầu triển khai.

1. **Gộp route/menu:** tạo page wrapper tại `src/pages/`, trỏ `/routes` và `/trips` vào wrapper, đổi sidebar thành một mục có active cho cả hai path. Không đổi API/schema. Test router và active navigation.
2. **Liên kết tuyến → chuyến:** từ route detail điều hướng với `routeId`/`create`; `FleetManagementPage`/`FleetWorkspace`/`TripEditor` nhận route ID ban đầu, mở form nhưng không tự lưu. Test query hợp lệ/sai và form preselect.
3. **Liên kết chuyến → tuyến:** `TripDetailPanel` cung cấp action xem tuyến; route page nhận query `routeId`, tải drawer tương ứng, xử lý loading/error. Test deep link và Back/Forward.
4. **Responsive và kiểm chứng:** CSS tab trong module phù hợp, giữ nguyên các màn hình con và bản đồ. Chạy lint/typecheck/unit/motion/build; ghi evidence và walkthrough. Rủi ro: watcher URL mở lặp drawer, test route wrapper remount; xử lý bằng state theo query và test hồi quy.

Điều kiện hoàn thành: toàn bộ AC trong `spec.md` có code/test tương ứng và kiểm tra có kết quả thực tế.
