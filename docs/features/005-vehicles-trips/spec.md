# 005 — Xe và chuyến đi

Người dùng yêu cầu triển khai trực tiếp sau đề xuất 005; không triển khai engine 006 trong feature này. `docs/workflow.md` không có trong inventory; áp dụng AGENTS.md. Giữ working tree Map-First 004 và không đọc .env.

## Evidence khảo sát trước triển khai

- Backend chỉ có station/route controller, migration V1–V3. `RouteEntity` lưu snapshot immutable, `RouteDetailResponse.from` tính arrival/departure offsets; `RouteServiceTest.create_normalizesNameAndPreservesStopOrderAndCalculatesMetrics` kiểm tra offsets.
- `RouteStopEntity` có snapshot tên/tọa độ, không có radius; `StationEntity.getCheckinRadiusMeters` cung cấp bán kính lúc tạo trip.
- `MapComponent` giữ `vehicles=[]`; `SimulatorPanel` disabled. `useMapCamera` đã có fit/focus cho panel nổi.
- `application.yaml` dùng Flyway + ddl-auto=validate; `pom.xml` có JPA/validation/PostgreSQL/Testcontainers. Test pattern: `StationControllerTest`, `RouteRepositoryIntegrationTest`.

## Acceptance criteria

1. GET/POST/PUT/DELETE /api/v1/vehicles, GET /{id}; biển số chuẩn hóa hoa, bỏ khoảng trắng/dấu chấm/gạch nối, unique kể cả xe inactive. Tên xe bắt buộc, mô tả tùy chọn. Delete chuyển inactive; chặn nếu còn chuyến SCHEDULED/IN_PROGRESS.
2. GET/POST /api/v1/trips, GET /{id}; danh sách lọc vehicleId tùy chọn. Tạo từ xe active + route tồn tại + các station còn active; scheduledDepartureAt hợp lệ. Không gọi lại HERE khi tạo trip.
3. Trip có SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED. POST /{id}/start, /complete, /cancel. Start chỉ scheduled; complete chỉ in-progress; cancel scheduled/in-progress; lặp lại cùng trạng thái đích trả cùng kết quả, không đổi timestamp.
4. Chỉ một trip IN_PROGRESS/vehicle, kể cả request đồng thời; lock vehicle và unique partial index. Trạng thái/timestamps có DB CHECK; FK/history không cascade delete.
5. Trip stop snapshot sequence/name/coords/radius/dwell/offset và plannedArrivalAt/plannedDepartureAt, timestamp UTC. Lịch kế hoạch = scheduled departure + offset, giữ nguyên khi start trễ hoặc station bị sửa. Geometry tham chiếu route immutable qua FK RESTRICT. Khi sửa tên hoặc tọa độ trạm, các route active chưa từng gắn trip được tự động cập nhật snapshot và tính lại geometry; route đã gắn trip không đổi để bảo toàn lịch sử.
6. Map-First tab Đội xe/Chuyến đi: list/search/filter, vehicle form/soft deactivate confirmation; trip form chọn xe/tuyến/giờ địa phương, detail lịch trình/ngày/giờ và lifecycle; chọn xe xem các chuyến của xe, chọn chuyến vẽ route bằng camera hiện có.
7. Loading/error/empty/retry; bảo toàn draft qua đổi mode; request cũ không thay selection mới; khóa ghi khi pending; conflict 409 hiển thị rõ. Không tạo marker xe/ETA/tốc độ giả khi chưa có telemetry.
8. Test service/controller, PostgreSQL integration cho snapshot/constraint/race; lint/tsc/build; browser fixture CRUD/lifecycle/responsive và regression trạm/tuyến.

## Quy tắc phạm vi

Không quản lý tài xế, không cập nhật GPS/SSE/simulator, không tự tính ETA theo traffic. Xe có thể có nhiều chuyến chờ; ngừng dùng xe yêu cầu hoàn thành/hủy các chuyến chưa kết thúc. Không tự gán tọa độ đầu tuyến làm vị trí xe. Ngày xuất phát được phép nằm trong quá khứ để lập/ghi nhận kế hoạch; giới hạn năm 2000–2100 để tránh timestamp không thể hiển thị và tràn cộng offset.

## Trạng thái sau triển khai

Đã hoàn thành AC 1–8 trong working tree ngày 2026-09-13. Evidence implementation, kiểm thử và giới hạn: [verification.md](verification.md). Không áp dụng migration vào database vận hành của người dùng trong lượt này; V4 đã được chạy trong PostgreSQL Testcontainers. Route geometry vẫn tham chiếu snapshot immutable hiện có; route chưa gắn trip có thể được đồng bộ khi station đổi tên/tọa độ, còn route đã gắn trip phải giữ bất biến.
