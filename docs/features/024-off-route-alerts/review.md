# Review 024 — Cảnh báo xe lệch tuyến

Ngày review: **2026-09-21**.

## Review bổ sung 2026-09-30 — chỉ phạm vi simulator giả lập

Phạm vi người dùng xác nhận: **chỉ simulator, không có GPS thật**. `SimulationService.java:216-218` ghi telemetry với nguồn `SIMULATOR`; `OffRouteEvaluationService.java:51-55` bỏ qua nguồn này. Vì vậy finding GPS dồn dập của bản review trước **không áp dụng** và đã được loại bỏ. Phần review 024 phía dưới là lịch sử của feature GPS, không phải đánh giá sản phẩm simulator-only.

### Cảnh báo đang có trong phạm vi

- Simulator có thể dẫn tới thông báo đổi tuyến `REROUTE_CREATED`/`REROUTE_UNAVAILABLE` khi `RerouteEvaluationService.java:45-51,70-91,100-114` nhận được HERE live/last-known hợp lệ; không thể suy ra một sự cố giao thông chỉ từ chuyển động giả lập nếu provider không có dữ liệu.
- Tài xế đổi đường tạo `DRIVER_ROUTE_CHANGED` (`DriverNavigationService.java:198`); luồng điều phối theo lịch tạo `DISPATCH_ATTENTION`, `DRIVER_UNAVAILABLE`, `TRIP_AUTO_STARTED` (`TripDispatchJobService.java:50-140`, `DriverDispatchService.java:85,134`). Simulator **không** phát `OFF_ROUTE_DETECTED` theo thiết kế 024.

### Findings

1. **High — Chưa có thao tác tài xế báo sự cố độc lập trong chuyến giả lập.** `DriverNavigationController.java:18-34` chỉ có navigation/start/route-options/apply; `DriverDispatchController.java:19-54` có ready/báo bận/offer/inbox; `NotificationType.java:3-11` không có loại báo sự cố từ tài xế. Nếu mục tiêu là tài xế báo hỏng xe/tai nạn/tình huống trên tuyến, cần đặc tả feature mới: form, loại/mô tả, trip/actor/time, API quyền tài xế, notification admin, audit và test. Đây không phải lỗi của AC-024.
2. **High — Chưa có trạng thái xử lý thực sự (còn mở).** `AlertsManagementPage.vue` hiện đã đổi nút và toast từ “Đã xử lý” sang “Đã đọc”, đúng với `NotificationService.java:26-30` chỉ đặt `readAt`. `DashboardService.java:40-52` vẫn loại cảnh báo đã đọc khỏi danh sách chưa đọc dù tình trạng điều phối/sự cố nguồn chưa được giải quyết. Nếu cần xử lý thật, phải đặc tả lifecycle, actor, thời điểm, ghi chú và điều kiện đóng theo trạng thái nguồn.
3. **Medium — Polling ghi đè thao tác cục bộ (đã sửa).** `AlertsManagementPage.vue` hiện hủy GET cũ khi poll mới bắt đầu và giữ overlay cho ID đã đọc/xóa thành công trước khi gán response. `page-workflows.test.ts` kiểm tra GET cũ đến sau read/delete và hai GET về sai thứ tự. Chưa kiểm chứng bằng browser/backend thật.
4. **Medium — Chỉ xem được 50 thông báo gần nhất.** `TripNotificationRepository.java:14-15` giới hạn 50, còn `/alerts` lọc/đếm chưa đọc trên danh sách cục bộ (`AlertsManagementPage.vue:94-109`). Khi nhiều sự kiện simulator/điều phối, mục cũ không thể mở từ trung tâm; cần server-side paging/filter và KPI tổng riêng.
5. **Low — KPI/bộ lọc GPS trên trang giả lập (đã sửa).** `/alerts` nay thay KPI “Lệch tuyến” bằng “Đổi tuyến” và bỏ filter lệch tuyến; notification lệch tuyến lịch sử vẫn xem được khi chọn “Tất cả”. `AlertsManagementPage.vue` và `page-workflows.test.ts` chứng minh hành vi UI này; backend `OffRouteEvaluationService.java:54-55` vẫn giữ quy tắc chỉ nhận GPS, không thay đổi.

### Acceptance, kiểm tra và kết luận

- Review này **không đánh giá AC-024 về GPS**. Luồng tự động đổi tuyến/thông báo của feature 009 có code và test tích hợp; báo sự cố thủ công và resolved workflow chưa có trong requirement 009/024.
- Đã chạy sau sửa: frontend Node 24 `npm run lint`, `npm run typecheck`, `npm run test:unit` (**26 file, 196 test**), `npm run test:motion` (**5/5**) và `npm run build` đều exit 0; build còn cảnh báo bundle chính 535.55 kB > 500 kB. Test mục tiêu `page-workflows.test.ts` qua **8/8**. Backend không đổi source trong lượt này; test tích hợp `RerouteSimulationIntegrationTest` ở vòng review trước exit 0 (**4 test**, PostgreSQL Testcontainers). Chưa kiểm thử browser/HERE production hay full backend suite sau sửa UI.
- **Kết luận:** Request changes nếu mục tiêu là “Báo sự cố và xử lý cảnh báo” đầy đủ trên simulator vì F1/F2/F4 còn mở. F3/F5 đã sửa và kiểm tra tự động; luồng cảnh báo đổi tuyến tự động hiện có vẫn dùng được trong phạm vi HERE. Không mở rộng backend/schema trong lượt này.

## Phạm vi đã rà soát

- Detector backend, state persistence và lock theo trip/attempt.
- Migration V16, cấu hình runtime và notification contract.
- Hook telemetry sau commit, snapshot/SSE notification hiện hữu.
- Trang `/alerts`, AlertStream trên operations và điều hướng `tripId` về bản đồ.
- Test state/detector, type check và lint.

## Kết quả

- Không phát hiện lỗi contract rõ ràng trong source đã rà soát.
- Reroute được đánh giá trước off-route trong callback sau commit để detector đọc geometry/revision mới nhất nếu reroute vừa được tạo.
- Detector có debounce theo số mẫu + thời gian, effective threshold theo accuracy, dedupe theo episode và re-arm khi xe quay lại corridor.
- Notification cũ vẫn dùng constructor tương thích; field mới nullable nên không phá dữ liệu hiện có.

## Giới hạn còn lại

- Reviewer subagent độc lập không hoàn tất do giới hạn usage của môi trường; review này là self-review có evidence từ source.
- Non-Docker Maven suite (254 tests) và frontend production build đã đạt trên JDK 26/Node 24; 6 integration test còn cần Docker daemon.
- Chưa chạy integration test PostgreSQL/Flyway vì Docker daemon không khả dụng trong môi trường này.

## Kết luận

Implementation đáp ứng phạm vi MVP; non-Docker tests và frontend build đã đạt. Trước khi phát hành production cần chạy Flyway/integration test trên môi trường có Docker.
