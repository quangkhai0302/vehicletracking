# 006 — Telemetry và simulator cơ bản

Người dùng yêu cầu triển khai trực tiếp. `docs/workflow.md` không có trong inventory kể cả tìm hidden; áp dụng AGENTS.md. Giữ nguyên thay đổi 004–005 và không đọc `.env`.

## Khảo sát và lựa chọn

- `route/entity/RouteSectionEntity` lưu polyline, destinationStopSequence, travel duration; `RouteDetailResponse.from` tính offset với dwell. `trip/entity/TripStopEntity` đã snapshot lịch/radius. Các đường dẫn Java tính từ backend `src/main/java/com/quangkhai/vehicletracking_backend/`.
- `trip/service/TripService.transition` khóa trip → vehicle và bảo vệ lifecycle; V4 có unique IN_PROGRESS/xe. Luồng 006 dùng cùng thứ tự khóa, không sửa migration V1–V4.
- Frontend `MapComponent` chưa có nguồn vị trí; `SimulatorPanel` disabled. `FleetWorkspace/useFleetWorkspace` giữ draft và selection độc lập với map. Thêm một nguồn realtime chung cho map/panel, không gọi API từ code marker.
- Dùng Spring MVC SseEmitter với full snapshot lúc kết nối và mỗi giây (resync khi reconnect, không hứa replay toàn lịch sử). Snapshot chỉ đọc dữ liệu đã commit. Scheduler simulator tách khỏi SSE để client chậm không dừng đồng hồ. Tham khảo [Spring async/SSE](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html), [MDN EventSource](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events).
- Giải mã Flexible Polyline phía backend theo [đặc tả HERE](https://github.com/heremaps/flexible-polyline), nội suy theo chiều dài segment trong từng section và thời lượng snapshot, dwell theo stop occurrence.

## Acceptance criteria

1. POST telemetry HTTP có eventId UUID, vehicleId/tripId, recordedAt, latitude/longitude, speedKmh, heading, accuracyMeters, source GPS. Simulator dùng cùng service ingestion với source SIMULATOR; nguồn SIMULATOR do backend phát, HTTP client không được tự giả mạo. Lưu receivedAt riêng; mô phỏng có simulatedAt riêng, recordedAt luôn wall clock.
2. JPA lưu lịch sử và vị trí mới nhất; unique eventId, FK/validation/check phù hợp. Retry cùng eventId và nội dung trả mẫu cũ; cùng ID khác nội dung hoặc timestamp bằng/cũ hơn latest bị 409, không làm marker lùi. Timestamp tương lai quá 30 giây/bất hợp lệ bị 400. Chỉ nhận cho đúng xe và trip IN_PROGRESS; không trộn GPS vào trip đang thuộc simulator.
3. GET snapshot và SSE cung cấp vị trí mới nhất, tóm tắt trip/run và serverTime; reconnect nhận snapshot đầy đủ từ DB. Cleanup emitter khi lỗi/timeout/disconnect; heartbeat bằng snapshot định kỳ. Frontend đóng EventSource/timer khi unmount, phân biệt connecting/live/reconnecting và vị trí quá 15 giây (stale), quá 60 giây (offline) theo recordedAt; không che mất dữ liệu cuối đã biết.
4. Simulator clock/lifecycle ở backend, lưu tiến độ vào DB. Play bắt đầu trip SCHEDULED hoặc tiếp tục run paused; tối đa một run/trip. 1×/5×/10× tăng tốc thời gian mô phỏng, không nhân tốc độ vật lý hiển thị. Pause giữ vị trí, tốc độ 0; tiếp tục không cộng thời gian pause. Chạy hết route tự hoàn thành trip. Stop hủy trip; hoàn thành/hủy thủ công dừng tick. Restart backend đưa run đang chạy về paused, không tự nhảy tiến qua thời gian downtime.
5. Geometry decode có kiểm soát; giữ thứ tự section và stop lặp, không nối đường thẳng thay geometry hỏng. Di chuyển theo khoảng cách segment, dừng theo dwell, ETA/countdown theo simulated clock snapshot; tiến độ/next stop không được coi là check-in. Run lỗi phải có trạng thái/message, không lặp tick vô hạn.
6. Reset giữ lịch sử run/trip cũ, tạo trip mới cùng xe/tuyến với run paused; lặp reset cùng run trả chuyến đã tạo. Start đồng thời, pause/speed/reset và tick được tuần tự hóa bằng DB lock. Thiết kế triển khai một backend instance; chưa có distributed scheduler.
7. Map-First có marker thực từ backend, tooltip nguồn/thời điểm/tốc độ/trạng thái, chọn/follow xe; SimulatorPanel chọn trip, play/pause/speed/stop/reset có busy/error/confirmation, đồng bộ state hai tab. Fleet lịch/status cập nhật từ snapshot mà không mất draft. Responsive desktop/mobile. Nút traffic injector vẫn disabled và có giải thích chưa nối traffic.
8. Test thuật toán geometry/dwell/loop/clock; service/controller/JPA validation/dedupe/race/lifecycle/restart; SSE reconnect; lint/tsc/build, browser hai tab với backend và PostgreSQL tạm, regression 004–005. Báo riêng fixture/live và thời gian trễ đo được.

## Giới hạn được chọn

006 chưa có HERE Traffic, check-in, reroute, cảnh báo lưu bền, thiết bị GPS thật, driver/auth/broker. Endpoint GPS là hợp đồng phát triển cục bộ chưa xác thực thiết bị. Lịch baseline 005 bất biến. Không tự áp dụng migration vào DB người dùng để tạo demo; thử end-to-end bằng DB tạm. Toàn bộ lịch sử được giữ; chưa thêm retention/pagination cho quy mô production.

## Trạng thái 2026-09-14

Đã có source AC 1–7, compile backend và 24 test thuật toán/JPA chọn lọc đạt; frontend lint/tsc/build và 39 nhóm browser fixture đạt. AC 8 và phần end-to-end AC 3/7 còn chờ chạy HTTP/SSE/full Maven và browser nối Spring/PostgreSQL do auto-review từ chối quyền thực thi khi tài khoản hết hạn mức. **Chưa đánh dấu feature hoàn thành.** Chi tiết kết quả/lệnh tiếp tục: [verification.md](verification.md).
