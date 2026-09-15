# 007 — Kế hoạch triển khai check-in tự động

Trạng thái: **Implementing** · Ngày cập nhật: 2026-09-14 · Người dùng yêu cầu trực tiếp “tiến hành thực hiện 007 đi”. P1–P5 đã triển khai; P6 nghiệm thu browser/edge cases và P7 bàn giao còn lại.

## Quyết định và điều kiện bắt đầu

Feature tiếp theo theo roadmap là **007 — Check-in tự động theo lần ghé trạm**. Sáu tài liệu đã lập:

1. [Requirement](requirement.md): phạm vi, AC-01…AC-12, giả định cần duyệt.
2. [Research](research.md): nguồn chính thức, lựa chọn hình học/transaction và ngưỡng đề xuất.
3. [Survey](survey.md): source evidence tại HEAD `ac0c7a7`, khác biệt với handoff cũ.
4. [Spec](spec.md): thứ tự trạm, chất lượng, trace simulator, schema/API/UI.
5. [Test plan](test-plan.md): ma trận AC, negative/concurrency/upgrade/browser và lệnh dự kiến.
6. Plan này: các bước implementation sau phê duyệt.

**Gate trước implementation:** (1) nghiệm thu 006 bằng HTTP/SSE, browser Spring/PostgreSQL và full suite; (2) người dùng yêu cầu trực tiếp triển khai 007. Hai điều kiện đã được đáp ứng trong session này; không dùng lỗi hạn mức trên máy Windows cũ để khẳng định máy Linux hiện còn bị chặn.

Quyết định lớn cần duyệt: strict-order không tự bỏ trạm; overlap cần exit/reentry; policy GPS 15 s/1 km/160 km/h/accuracy; simulator dùng trace thực đã chạy; check-out và HERE Traffic/ETA vẫn ngoài phạm vi. Nếu muốn vận hành bỏ qua một trạm thiếu evidence phải mở rộng spec được duyệt, không thêm nút sửa tay trong lúc implement.

## P0 — Khóa baseline và xác nhận gate 006

- **Mục tiêu:** có nền telemetry/simulator đã nghiệm thu trước thay đổi 007.
- **File liên quan:** 006 `verification.md`, test `OperationsHttpIntegrationTest.java`, harness `verification/live-browser.mjs`; tài liệu tiến độ/handoff chỉ cập nhật khi có kết quả thật.
- **Contract/migration:** không thêm 007 ở bước này. Giữ working tree và `.gitignore` của người dùng; không đọc/in `.env`.
- **Kiểm tra:** kiểm tra Java/Node/Docker/browser hiện có; theo [handoff §10](../../SESSION_HANDOFF.md), dùng `./mvnw -Dtest=OperationsHttpIntegrationTest test`, extension `-Dverification.browser006=true`, rồi `./mvnw test`. Chuẩn bị frontend dev server riêng, không kill process không rõ chủ sở hữu.
- **Phụ thuộc/rủi ro:** harness đang dùng msedge và Playwright trong docs004 (survey E15); Linux có thể thiếu browser/dependency. Nếu cần đổi lựa chọn browser cho nghiệm thu, sửa nhỏ harness trong phạm vi verification, giữ chế độ Spring/fixture phân biệt. Không đi vòng khi công cụ hiện hành từ chối quyền.
- **Hoàn tất:** HTTP/SSE, browser Spring/PostgreSQL và full backend suite đã có evidence; fixture/browser 006 vẫn được tách nhãn khỏi browser 007.

## P1 — Migration và mô hình persistence

- **Mục tiêu:** visits append-only và checkpoint durable.
- **Tạo dự kiến:** `vehicletracking-backend/src/main/resources/db/migration/V6__create_trip_stop_visits.sql`; package `checkin/entity/{TripStopVisitEntity,TripCheckInStateEntity,CheckInEvidenceKind}.java`; `checkin/repository/{TripStopVisitRepository,TripCheckInStateRepository}.java`.
- **Sửa dự kiến:** mapping telemetry unique metadata nếu cần đồng bộ DDL, không sửa nội dung V1–V5. Kiểm tra lại inventory để tránh trùng V6.
- **Contract:** hai bảng và composite FK/UNIQUE/CHECK theo spec §4. Không thêm actual fields vào TripStop; giữ TripDetail snapshot không đổi. Không backfill history cũ.
- **Test:** tạo `checkin/TripCheckInIntegrationTest.java`, fixtures; upgrade V5→V6, Hibernate validate, constraints và lookup theo trip/sequence.
- **Phụ thuộc/rủi ro:** P0 + approval; FK composite/JPA mapping, index dư. Tránh cascade delete vào history.
- **Hoàn tất:** schema lên được từ V5 và DB trống; invalid evidence trip/source bị chặn; baseline cũ nguyên vẹn.

## P2 — Hình học và state machine thuần

- **Mục tiêu:** quyết định POINT/SEGMENT/TRACE và arming theo thứ tự có thể unit test độc lập.
- **Tạo dự kiến:** `checkin/geometry/GeofenceCrossing.java`, `checkin/service/StopSequencePolicy.java`, các record trajectory/result nhỏ phục vụ trực tiếp detector; unit `GeofenceCrossingTest`, `StopSequencePolicyTest`.
- **Sửa dự kiến:** `simulation/motion/RouteMotion.java`, `simulation/RouteMotionTest.java` để xuất trace của khoảng elapsed, giữ `at` và contract Frame hiện tại.
- **Contract:** spec §2–3; xử lý finite/boundary/zero-length/kinh tuyến/cực; GPS quality và overlap rõ; simulator không dùng GPS wall-clock thresholds. Không thêm SDK/PostGIS/engine khác.
- **Test:** đủ geometry/order/gap/jump/quality/trace fixtures; 10× qua góc cua và nhiều stop/tick; sai số trace≤0,5 m; không nội suy qua section gap.
- **Phụ thuộc/rủi ro:** có thể chuẩn bị thuật toán sau P1, trước gắn ingestion; tránh phụ thuộc Spring/JPA/provider trong pure geometry. Trace phải dùng cùng chuẩn nội suy với RouteMotion, không thay hình học để làm test qua.
- **Hoàn tất:** unit tests chứng minh các AC hình học, chính sách có named constants, không cần network/database.

## P3 — Gắn detector vào transaction telemetry

- **Mục tiêu:** vị trí accepted → visits/state/latest cùng commit.
- **Tạo dự kiến:** `checkin/service/CheckInService.java`; test service/concurrency trong `TripCheckInIntegrationTest`.
- **Sửa dự kiến:** `telemetry/service/TelemetryService.java`; `simulation/service/SimulationService.java` chỉ nếu cần truyền thông tin trace thuần; repository lookup liên quan. Không inject SimulationService vào CheckInService.
- **Contract:** giữ input/output telemetry 006; cùng lock trip→vehicle. Duplicate không detect; initial checkpoint không backfill; state.lastSample không nối chuyến cũ. Lỗi quality bỏ suy luận nhưng không bỏ telemetry; lỗi persistence rollback tất cả. Trước simulator complete phải xử lý trace của tick cuối.
- **Test:** same event/concurrent events, lifecycle race, injected rollback, restart durable, reset, multiplier/pause/recover, fixed baseline. Chạy lại `OperationsIntegrationTest`, `RouteMotionTest`.
- **Phụ thuộc/rủi ro:** P1–P2; precision microsecond, last-sample pointer cập nhật trước khi detector đọc, trace allocation, circular dependencies. Không đổi assertion baseline của 006 sang yếu hơn.
- **Hoàn tất:** AC-01…AC-08, AC-11 ở lớp integration có bằng chứng; không có event commit dở dang.

## P4 — API lịch sử và snapshot/SSE

- **Mục tiêu:** có read model nhất quán và hồi phục khi mất kết nối.
- **Tạo dự kiến:** `checkin/dto/{TripCheckInsResponse,StopVisitResponse}.java`; `checkin/controller/CheckInController.java`; `checkin/service/CheckInQueryService.java`; `checkin/CheckInHttpIntegrationTest.java`.
- **Sửa dự kiến:** `telemetry/dto/OperationsSnapshot.java`, `telemetry/service/OperationsSnapshotService.java`; constructor fixtures/test snapshot cần trường mới. Stream service chỉ sửa nếu contract thật cần, giữ event/cleanup.
- **Contract:** GET `/api/v1/trips/{tripId}/check-ins`, ProblemDetail 400/404; snapshot `checkIns` bắt buộc cho mọi trip; revision; full state/batch queries. TripDetail/telemetry POST không đổi.
- **Test:** 200/404/400/empty/terminal, JSON nullability/enum/ordering; hai SSE subscribers, committed vs rollback, reconnect, cleanup; snapshot không N+1 do 007.
- **Phụ thuộc/rủi ro:** P3; query read-only REPEATABLE_READ; tránh vòng phụ thuộc với SimulationService hiện được snapshot gọi. Full payload tăng theo số trip, chỉ cam kết tải dev đã test.
- **Hoàn tất:** schema/API/tests nhất quán, response không trả entity/lazy proxy hoặc secret; HTTP test chạy thật qua Spring/PostgreSQL.

## P5 — Frontend types, service, state và timeline

- **Mục tiêu:** đọc/hiển thị visits mà không sửa lịch kế hoạch hoặc tạo event tại browser.
- **Tạo dự kiến:** `vehicletracking-frontend/src/types/checkin.ts`, `services/checkins.ts`, `hooks/useTripCheckIns.ts`.
- **Sửa dự kiến:** `types/operations.ts`, `services/operations.ts`; `hooks/useFleetWorkspace.ts`, `hooks/useSimulator.ts`; `components/fleet/{FleetWorkspace,TripDetailPanel}.tsx`, `components/fleet/fleet.css`; `components/operations/SimulatorPanel.tsx`, `components/StationDrawer.tsx`. `MapComponent`/`AlertStream` chỉ wiring/copy cần thiết, không chuyển form/HTTP vào shell.
- **Contract:** merge read models theo tripId/revision, GET có abort/retry; shared SSE không mở mới theo panel; timeline Kế hoạch/Ghi nhận; SIMULATOR ghi giờ giả lập riêng. Backend thiếu field mới là unavailable, không giả visits rỗng. Giữ countdown giả lập và traffic chưa kết nối.
- **Test:** lint/tsc/build; browser loading/error/empty/retry, overlap label, two tabs, HTTP/SSE race, mobile/focus/selection/reset. Không có destructive action mới nên không thêm confirmation thừa.
- **Phụ thuộc/rủi ro:** P4; tránh state lùi khi GET cũ trả về, duplicate announcements mỗi tick, drawer hidden làm locator bắt nhầm; giữ draft/mode/camera/marker cleanup 004–006.
- **Hoàn tất:** AC-09/10/11 thể hiện đúng, không giả live traffic hoặc diễn giải check-in là đã dừng đón/trả.

## P6 — Nghiệm thu xuyên hệ thống và hiệu năng

- **Mục tiêu:** chứng minh toàn AC bằng code đang review, không chỉ mocks.
- **Đã tạo:** `docs/features/007-automatic-station-check-in/verification/live-browser.mjs` bọc harness 006, bật assertion check-in khi chạy backend thật; artifacts/log theo chế độ; `OperationsHttpIntegrationTest` truyền backend port/trip fixture bằng cờ `verification.browser006=true` vì đây là extension tương thích của harness 006.
- **Thiết lập harness:** tái dùng Playwright ngoài dependencies ứng dụng như 006; xác nhận browser executable trên máy. Nếu chưa có package/lock tái lập trong harness, bổ sung manifest/lock riêng tại verification007 khi implement và ghi phiên bản đã cài thực tế; không đoán version mới hoặc ép browser Windows trên Linux. Cài đặt chỉ trong bước được duyệt, không thực hiện trong lượt planning.
- **Contract:** không thêm API sản phẩm chỉ để seeding test; fixture do integration test/DB tạm cung cấp. Fixture-only có nhãn riêng, live script thiếu backend target phải fail thay vì lặng lẽ chuyển sang mock.
- **Test:** ma trận đầy đủ [test-plan](test-plan.md); `./mvnw test`, frontend lint/tsc/build; browser Spring hai tab, reload/reconnect; hồi quy 004–006; đo 10 trip × 1 sample/giây, max50 stop/trip, 2 tab, 60 s, latency mục tiêu ≤2 s.
- **Phụ thuộc/rủi ro:** P0–P5, Docker/browser; giới hạn môi trường phải ghi unrun, không bỏ assertions/tăng ngưỡng để ghi đạt. Thiết bị GPS/HERE live không phải fixture evidence.
- **Hoàn tất hiện tại:** browser core và ordered visits đã có evidence; migrations/JPA/API/type/UI đồng bộ và không secret. Load/edge/fault-injection còn thiếu nên chưa chuyển `Verified`.

## P7 — Bàn giao kết quả sau implement

- **Mục tiêu:** người đổi máy/session hiểu đúng trạng thái mới.
- **File:** khi đã chạy mới tạo `evidence.md`, `walkthrough.md`; cập nhật `docs/PROJECT_PROGRESS.md`, `docs/SESSION_HANDOFF.md` và trạng thái hồ sơ 007. `review.md` chỉ khi thật sự review.
- **Contract:** không thêm functionality; ghi commands/exit/test counts/latency và giới hạn cụ thể. Không đánh dấu 008 Traffic đã làm vì 007 dùng simulator.
- **Kiểm tra:** AC mapping, đường dẫn, git diff/status, secret safety; giữ thay đổi người dùng.
- **Phụ thuộc/rủi ro:** P6 đạt; tài liệu mới bị rule docs/ ignore nên chuyển máy cần copy hoặc người dùng chủ động duyệt việc đưa vào Git. Không tự sửa ignore/force-add/commit/push.
- **Hoàn tất:** trạng thái phản ánh evidence, hướng dẫn demo và limitations rõ; sau review/chấp thuận mới dùng Reviewed.

## Trạng thái sau triển khai

P1–P5 đã có source Java/TypeScript/SQL và test hồi quy. V6 đã được Flyway áp dụng trên PostgreSQL Testcontainers; full backend đạt 155 tests. Browser Spring/PostgreSQL đã xác nhận ba visit simulator theo thứ tự. P6 còn load/edge evidence; 008 HERE Traffic/ETA và 009 reroute/thông báo vẫn là bước sau, không triển khai kèm 007.
