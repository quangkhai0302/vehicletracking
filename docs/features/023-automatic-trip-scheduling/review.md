# Review 023 — Lập lịch chuyến tự động

## Kết quả review độc lập

Reviewer đã đối chiếu requirement → research → survey → spec → test-plan → code và ban đầu ghi nhận các rủi ro high về stale schedule khi polling, double-booking, hard-delete trip tự sinh; cùng các rủi ro medium về DST/list và route lock.

## Xử lý sau review

- Đã thêm `trip_schedules.version` với optimistic locking; `createFromSchedule` reload/recheck enabled và version trước khi tạo.
- Đã kiểm tra xe/tài xế sau khi lock resource, chặn trip scheduled/in-progress có khoảng thời gian chồng lấn và khóa route cho luồng tự động.
- Đã thêm provenance pairing check và cấm hard-delete trip có `schedule_id`.
- Đã cô lập lỗi resolver khỏi `GET /api/v1/schedules`, chọn earlier offset cho DST overlap và từ chối DST gap.
- Đã đổi default timezone UI thành `Asia/Ho_Chi_Minh`, từ chối fixed offset và ghi `lastRunAt` theo thời điểm xử lý thực tế.

## Findings còn mở

- Chưa có PostgreSQL/Testcontainers test chứng minh migration, optimistic-lock race và unique guard dưới hai transaction.
- Station deactivation vẫn có thể cạnh tranh với active check trong một transaction riêng; cần integration test/lock policy nếu yêu cầu đảm bảo tuyến–trạm tuyệt đối.
- Conflict đã tính theo khoảng thời gian dự kiến của tuyến; race hai transaction vẫn cần PostgreSQL integration test.
- Non-Docker backend suite (254 tests) và frontend production build đã đạt trên JDK 26/Node 24; 6 integration test còn cần Docker daemon.

Kết luận: **Implementation review — chấp nhận có điều kiện**, non-Docker quality gates đã đạt; chỉ còn runtime integration phụ thuộc Docker.
