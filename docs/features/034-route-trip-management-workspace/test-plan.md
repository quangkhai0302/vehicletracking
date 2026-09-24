# Test plan — Không gian quản lý tuyến và chuyến

| AC | Mức test | Kịch bản | Fixture | Kết quả mong đợi |
|---|---|---|---|---|
| 1 | Router/unit | Vào `/routes`, `/trips`, chuyển tab, Back/Forward | Memory router + auth admin | Cùng workspace, đúng tab, một nav active. |
| 2 | Unit/workflow | Tạo/xem tuyến, mở/tạo/chuyển trạng thái chuyến | API fixture có route/trip | Thành phần cũ vẫn hiển thị và gọi API cũ. |
| 3 | Router/unit | Từ tuyến mở tạo chuyến; từ chuyến mở tuyến | Route/trip fixture có ID | Query đúng ID, form/chi tiết chọn sẵn, chưa POST tự động. |
| 4 | Router/unit | Deep link và query sai; đổi tab và quay lại | Route/vehicle IDs hợp lệ và sai | Không crash; lọc xe cũ còn hiệu lực; drawer không rò state. |
| 5 | Build/check | `npm run lint`, `typecheck`, `test:unit`, `test:motion`, `build` | Node theo `.nvmrc` | Exit code 0; ghi giới hạn nếu thiếu môi trường browser. |
| 2 | Browser/visual | Mở chi tiết chuyến ở 1440 px, 768 px và 390 px | HTTP fixture có chuyến, tài xế, tuyến và trạm | Tổng quan, thời gian, timeline, tiến độ, ETA và thao tác không tràn hoặc mất nội dung; không có runtime error. |
