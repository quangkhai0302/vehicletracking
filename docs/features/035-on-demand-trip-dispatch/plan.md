# Plan — Điều phối chuyến tức thời

Trạng thái: **Implemented / Verified**

1. Đổi contract tạo chuyến và bổ sung provenance trong response; không thay schema.
2. Tách quy tắc service giữa `create` tức thời và `createFromSchedule`, bỏ cập nhật giờ trên trip, giữ các ràng buộc tài nguyên đang chạy.
3. Cập nhật frontend type/API/form tạo chuyến và bỏ luồng sửa giờ.
4. Cập nhật mọi màn hình tiêu thụ `TripSummary` để dùng `dispatchMode` và nhãn đúng nghiệp vụ.
5. Điều chỉnh fixture/test backend và frontend; thêm negative case cho conflict thật khi khởi hành.
6. Chạy test chuẩn, ghi `evidence.md` và `walkthrough.md`; không chỉnh bản đồ giám sát.

Rủi ro: `scheduledDepartureAt` còn được mô phỏng, dashboard và sorting dùng nội bộ. Vì vậy implementation giữ field này và chỉ thay ý nghĩa hiển thị dựa trên provenance.
