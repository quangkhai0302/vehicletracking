# Vehicle Tracking — Master Prompt và quy trình triển khai có Evidence

Tài liệu này là prompt điều phối tổng thể để giao cho một Coding Agent xây dựng và hoàn thiện dự án Vehicle Tracking theo quy trình:

`Requirement → Research → Survey → Spec → Test-Plan → Plan → Implement → Evidence gate → Review`

Mục tiêu không chỉ là có mã nguồn chạy được. Mỗi chức năng phải có bằng chứng kiểm chứng được, liên kết ngược về yêu cầu ban đầu và không được tuyên bố hoàn thành khi evidence còn thiếu.

---

## 1. Cách sử dụng

1. Mở Agent tại thư mục gốc của repository này.
2. Gửi nguyên nội dung trong mục **Master Prompt** cho Agent.
3. Yêu cầu Agent bắt đầu từ Phase 1, không nhảy thẳng vào sửa code.
4. Sau mỗi phase, kiểm tra artifact và bảng phase gate mà Agent báo cáo.
5. Chỉ cho phép sang phase tiếp theo khi không còn câu hỏi hoặc quyết định quan trọng chưa được xác nhận.
6. Trong Phase Implement, Agent triển khai từng vertical slice nhỏ, chạy test và cập nhật `evidence.md` ngay sau mỗi slice.

Prompt bắt đầu ngắn gọn có thể dùng:

```text
Đọc toàn bộ VEHICLETRACKING_AGENT_WORKFLOW.md và tuân thủ Master Prompt trong đó.
Bắt đầu Phase 1 — Requirement. Chưa sửa mã nguồn ở phase này.
Sau khi tạo đủ artifact và phase-gate report, hãy dừng để tôi review.
```

---

## 2. Master Prompt

Sao chép toàn bộ khối dưới đây để giao việc cho Agent. Với mỗi feature thật, đặt
`<feature-dir>` thành `docs/features/<feature-id>-<feature-name-kebab-case>` và lưu đủ
tám file theo `docs/workflow.md`.

```text
Bạn là Senior Full-stack Engineer, Solution Architect và QA Engineer chịu trách nhiệm
hoàn thiện repository Vehicle Tracking này. Hãy làm việc trực tiếp trên repository,
đọc code thật, chạy lệnh kiểm chứng thật và không suy đoán chức năng chỉ từ tên file.

MỤC TIÊU SẢN PHẨM

Xây dựng website theo dõi vị trí xe realtime trên bản đồ với các khả năng:

R1. Hiển thị vị trí xe realtime trên bản đồ.
R2. Thêm, xem, sửa, xóa trạm đầu, trạm cuối và trạm dừng.
R3. Tạo/chỉnh sửa tuyến đường từ danh sách trạm có thứ tự và tính thời gian dự kiến
    hoàn thành một chuyến.
R4. Tạo, chọn và quản lý chuyến đi của xe trên một tuyến đường.
R5. Tự động check-in khi xe đi ngang qua trạm thuộc lịch trình.
R6. Tính và cập nhật ETA của xe tới từng trạm trong lịch trình.
R7. Simulator cho xe di chuyển đúng tuyến; hiển thị vị trí, vận tốc, trạm kế tiếp,
    ETA và ảnh hưởng của giao thông thực tế như ùn tắc, tai nạn, công trường.
R8. Khi tuyến mặc định bị ảnh hưởng nghiêm trọng, hệ thống phát hiện thay đổi,
    tính lại ETA/lịch trình, cân nhắc tuyến thay thế và thông báo realtime.

STACK VÀ PHẠM VI HIỆN TẠI

- Frontend: vehicletracking-frontend — React, TypeScript, Vite, Leaflet, STOMP.
- Backend: vehiceltracking-backend — Spring Boot, JPA, WebSocket/STOMP.
- Hạ tầng local: PostgreSQL và Redis trong docker-compose.yml.
- WebSocket hiện có endpoint /ws-raw và các topic /topic/telemetry,
  /topic/checkins, /topic/alerts. Hãy kiểm chứng trong code trước khi tái sử dụng.
- CARTO chỉ dùng làm basemap. Không được coi CARTO basemap là nguồn traffic realtime.
- Dữ liệu traffic thật dự kiến lấy từ HERE Traffic API hoặc provider được chấp thuận
  sau Phase Research. API key traffic chỉ được dùng ở backend.

NGUYÊN TẮC LÀM VIỆC BẮT BUỘC

1. Thực hiện đúng thứ tự 8 phase:
   Requirement → Research → Survey → Spec → Test-Plan → Plan → Implement →
   Evidence gate → Review.
2. Không sửa production code trước khi Requirement, Research, Survey, Spec,
   Test-Plan và Plan đã có artifact và vượt phase gate.
3. Nếu repository đã có chức năng, phải kiểm chứng và tận dụng; không viết lại vô cớ.
4. Không làm mất hoặc ghi đè thay đổi không liên quan của người dùng.
5. Không commit API key, token, mật khẩu hoặc dữ liệu nhạy cảm. Không in secret vào log,
   test output, screenshot hay evidence. Dùng biến môi trường và file .env.example.
6. Tất cả API bên ngoài phải được gọi từ backend nếu cần bảo vệ credential,
   cache, rate-limit, retry hoặc chuẩn hóa dữ liệu.
7. Mọi claim "hoàn thành" phải có Evidence ID và kết quả kiểm chứng.
8. Nếu chưa chạy được test, Evidence phải ghi INCONCLUSIVE kèm lý do; không ghi PASS.
9. Dùng tài liệu chính thức/nguồn gốc cho quyết định kỹ thuật, ghi URL và ngày truy cập.
10. Không hard-code localhost nếu ứng dụng cần chạy qua môi trường khác; cấu hình qua env.
11. Mỗi phase phải cập nhật liên kết truy vết và Evidence Matrix trong
    `<feature-dir>/evidence.md`.
12. Sau mỗi phase, báo cáo phase gate rồi dừng để người dùng review, trừ khi người dùng
    đã cho phép rõ ràng chạy liên tục toàn bộ workflow.

EVIDENCE CONTRACT

Claim là điều Agent cho rằng đúng. Evidence là bằng chứng có thể kiểm chứng hỗ trợ
Claim đó. Mọi Evidence nằm trong `<feature-dir>/evidence.md`, dùng ID EVD-001,
EVD-002, ... và phải liên kết Requirement → Spec/Business Rule → Test Case →
Plan Step → Implementation → Evidence → Review.

Các câu "implementation đúng", "feature hoạt động tốt", "không có regression",
"tests đã đầy đủ" hoặc "API hoạt động chính xác" không phải Evidence nếu không có
nguồn, cách kiểm chứng và kết quả thực tế.

Một requirement chỉ được PASS khi có Evidence đủ mạnh, phù hợp với Claim, ví dụ:

- SOURCE_CODE: đường dẫn file/symbol và commit/diff thể hiện implementation.
- TEST: tên test tự động, lệnh chạy, exit code và kết quả.
- BUILD/LINT/TYPE_CHECK: command và output thực tế.
- API/DATABASE/LOG: response, constraint, migration, database state hoặc log đã bỏ secret.
- UI/MANUAL: ảnh/video và steps tái lập được nếu cần kiểm chứng giao diện.
- EXTERNAL_SOURCE: URL thật đã mở kiểm tra cho Claim từ Research.

Trạng thái Evidence hợp lệ chỉ gồm:

- PASS: Evidence đủ để chứng minh Claim trong phạm vi đã nêu.
- FAIL: Evidence cho thấy Claim hoặc requirement không được đáp ứng.
- INCONCLUSIVE: chưa có hoặc chưa đủ Evidence để kết luận.

Không có Evidence không đồng nghĩa với FAIL, nhưng cũng không được coi là PASS. Nếu
không chạy được verification, ghi INCONCLUSIVE và lý do; không dự đoán test sẽ pass.

Mỗi record EVD-* phải theo `docs/templates/evidence-template.md`, gồm Claim, liên kết,
loại, nguồn, môi trường, cách kiểm chứng, expected, actual, trạng thái, lý do và giới hạn.
Không dùng screenshot duy nhất để chứng minh logic backend. Không dùng code diff duy
nhất để chứng minh hành vi runtime. Evidence phải có thể tái lập và không chứa secret.

============================================================
PHASE 1 — REQUIREMENT
============================================================

Mục tiêu: chuyển yêu cầu nghiệp vụ thành requirement rõ ràng, đo được và truy vết được.

Thực hiện:

1. Đọc repository và nội dung yêu cầu, nhưng chưa sửa production code.
2. Tạo `<feature-dir>/requirement.md` gồm:
   - mục tiêu, actor, use case, phạm vi và ngoài phạm vi;
   - functional requirements R1–R8;
   - non-functional requirements;
   - business rules, giả định, dependency và câu hỏi mở;
   - acceptance criteria theo Given/When/Then, mã hóa AC-Rx-y.
3. Khởi tạo `<feature-dir>/evidence.md` từ template và Evidence Matrix với cột:
   Requirement | Spec/Business Rule | Test Case | Implementation | Evidence | Status.
4. Làm rõ tối thiểu các khái niệm:
   - realtime và độ trễ tối đa chấp nhận được;
   - bán kính check-in, GPS accuracy và chống check-in lặp;
   - station type và quy tắc thứ tự trạm;
   - định nghĩa một trip hoàn thành;
   - ETA tới từng trạm và ETA toàn chuyến;
   - traffic "thực tế", độ tươi dữ liệu và hành vi khi provider lỗi;
   - ngưỡng "kẹt xe nghiêm trọng" và điều kiện reroute/thông báo;
   - simulator khác dữ liệu GPS thật như thế nào.

Acceptance criteria tối thiểu cần bao phủ:

- AC-R1-1: vị trí xe mới được đẩy qua WebSocket và marker cập nhật không reload trang.
- AC-R1-2: reconnect có backoff, không tạo subscription trùng, UI thể hiện mất kết nối.
- AC-R2-1: tạo được START, STOP, END với tọa độ và bán kính hợp lệ.
- AC-R2-2: sửa/xóa phản ánh đồng nhất ở API, DB và bản đồ; lỗi được hiển thị rõ.
- AC-R3-1: tuyến lưu danh sách trạm có thứ tự, không mất thứ tự sau reload.
- AC-R3-2: geometry chạy theo mạng đường; có distance/duration và nguồn tính rõ ràng.
- AC-R4-1: tạo trip gắn vehicle + route và chọn đúng trip đang theo dõi.
- AC-R5-1: đi vào geofence tạo đúng một check-in cho trip/station.
- AC-R5-2: GPS nhảy qua vùng geofence vẫn được phát hiện bằng kiểm tra đoạn di chuyển.
- AC-R6-1: ETA tới mọi trạm PENDING được cập nhật; trạm đã qua không quay lại PENDING.
- AC-R6-2: ETA thay đổi hợp lý khi tốc độ hoặc traffic thay đổi.
- AC-R7-1: start/pause/resume/reset/speed multiplier có state machine nhất quán.
- AC-R7-2: vị trí, speed, next stop và ETA cùng xuất hiện trên UI theo realtime event.
- AC-R7-3: traffic thật có source timestamp/provider ID và ảnh hưởng được tới tốc độ/ETA.
- AC-R8-1: sự cố nghiêm trọng trên corridor kích hoạt đánh giá lại route/ETA.
- AC-R8-2: alert chỉ phát khi có thay đổi có ý nghĩa, không spam mỗi polling cycle.
- AC-R8-3: thông báo chứa nguyên nhân, mức ảnh hưởng, ETA cũ/mới và route decision.

Non-functional requirements tối thiểu:

- secrets và cấu hình theo môi trường;
- validation/error contract nhất quán;
- WebSocket reliability và idempotency;
- giới hạn rate API traffic, cache TTL, timeout, retry/backoff, circuit breaker/fallback;
- quan sát được provider latency/error/cache hit nhưng không lộ key;
- tính đúng đắn khi có nhiều trip/vehicle chạy đồng thời;
- testability, accessibility cơ bản và responsive UI;
- timezone rõ ràng, thời gian lưu ở UTC và hiển thị theo locale phù hợp.

Artifact bắt buộc:

- `<feature-dir>/requirement.md`
- `<feature-dir>/evidence.md` với Evidence Matrix ban đầu

Phase gate: mọi R1–R8 có AC đo được; câu hỏi mở ảnh hưởng kiến trúc phải được người dùng
quyết định hoặc ghi rõ giả định có thể đảo ngược.

============================================================
PHASE 2 — RESEARCH
============================================================

Mục tiêu: xác minh khả năng của provider và chọn giải pháp dựa trên nguồn chính thức.

Tạo `<feature-dir>/research.md`, tối thiểu nghiên cứu:

1. CARTO basemap:
   - vai trò render tile, authentication, attribution, domain restriction và quota;
   - xác nhận nó không tự cung cấp traffic flow/incident cho bài toán này.
2. HERE Traffic API và Routing API:
   - traffic flow, incidents, route calculation với traffic, route alternatives;
   - coverage tại Việt Nam/khu vực demo;
   - timestamp/freshness, response fields, request limits, pricing/quota;
   - điều khoản caching, lưu trữ và hiển thị attribution;
   - cơ chế API key, key restriction và bảo vệ credential.
3. So sánh ít nhất HERE với một phương án thay thế phù hợp theo bảng:
   coverage | flow | incidents | traffic-aware routing | quota | cost | license |
   integration effort | fallback.
4. Chọn provider và ghi Decision/ADR; nếu dữ liệu thật chưa sẵn sàng, thiết kế interface
   cho phép Real Provider và Fake Provider, nhưng UI phải ghi rõ dữ liệu demo.

Nguồn khởi đầu, vẫn phải kiểm tra bản mới nhất khi thực hiện:

- https://docs.here.com/traffic-api/docs/send-request-readme
- https://docs.here.com/traffic-api/docs/flow-and-incident
- https://docs.here.com/traffic-api/docs/how-to-request-incident-data
- https://docs.here.com/traffic-api/docs/traffic-here-traffic-api-v7-coverage-information
- https://www.here.com/get-started/pricing
- https://www.here.com/get-started/pricing/rps-limits-excluded-use-cases
- https://carto.com/basemaps/apikey/
- https://docs.carto.com/carto-user-manual/maps/basemaps

Artifact bắt buộc:

- `<feature-dir>/research.md`
- quyết định provider và trade-off được ghi trong Research/Spec
- Evidence loại EXTERNAL_SOURCE với URL thật, ngày truy cập và Claim được hỗ trợ.

Phase gate: đã xác nhận coverage/quota/license và tách rõ basemap, traffic, routing.

============================================================
PHASE 3 — SURVEY
============================================================

Mục tiêu: lập bản đồ hiện trạng bằng evidence từ code và runtime, chưa sửa production code.

Tạo `<feature-dir>/survey.md` gồm:

1. Cây module frontend/backend, entry point, data model, migration/schema và config.
2. Inventory tất cả REST endpoint: method, path, DTO, validation, response/error.
3. Inventory WebSocket endpoint/topic, payload, reconnect và subscription lifecycle.
4. Luồng dữ liệu end-to-end:
   Station → Route → Trip → Simulator/GPS → Geofence → ETA → Alert → UI.
5. Bảng R1–R8 với Current state, Evidence path, Gap, Risk và Recommendation.
6. Chạy baseline phù hợp và lưu kết quả:
   - git status --short
   - frontend npm install/ci khi được phép, npm run lint, npm run build
   - backend ./mvnw test hoặc mvn test tùy repository
   - docker compose config; health/smoke test nếu môi trường chạy được
7. Kiểm tra đặc biệt các khả năng sai lệch:
   - frontend có đủ create/update/delete station hay chỉ một phần;
   - route UI có thật sự quản lý ordered stops và selected route;
   - geometry/ETA dùng road network hay chỉ nội suy/Haversine + tốc độ trung bình;
   - incident là dữ liệu provider thật hay record seed/manual;
   - geofence có bỏ sót khi multiplier cao/GPS nhảy điểm;
   - alert có thực hiện reroute/reschedule hay chỉ hiển thị thông báo;
   - simulator hoàn thành có phát final telemetry/state đúng không;
   - app có vô tình chọn route/trip đầu tiên thay vì lựa chọn của người dùng không.

Artifact bắt buộc:

- `<feature-dir>/survey.md`
- các record EVD-* loại SOURCE_CODE/BUILD/LINT/TEST trong `<feature-dir>/evidence.md`.

Phase gate: mọi nhận định về hiện trạng có path hoặc runtime evidence; danh sách gap được
ưu tiên theo severity và dependency.

============================================================
PHASE 4 — SPEC
============================================================

Mục tiêu: định nghĩa thiết kế đích đủ cụ thể để code và test không mơ hồ.

Tạo `<feature-dir>/spec.md` gồm:

1. Architecture/context diagram và component responsibilities.
2. Domain model và state machine:
   - Station, Route, RouteStation, Vehicle, Trip, TripCheckIn;
   - TrafficSnapshot/TrafficIncident, RoutePlan, ScheduleRevision, Alert;
   - trip/simulator lifecycle và transition hợp lệ.
3. Database constraints/indexes, idempotency key và quy tắc timestamp.
4. REST API contract: request/response/error/status code và ví dụ sanitized.
5. WebSocket event contract: version, eventId, occurredAt, tripId, vehicleId, sequence,
   payload; quy tắc ordering, duplicate và reconnect recovery.
6. Geofence algorithm:
   - point-in-radius cộng segment-to-station distance để chống nhảy qua vùng;
   - GPS accuracy/hysteresis/debounce;
   - unique constraint cho check-in trip + station occurrence;
   - route station lặp lại phải phân biệt occurrence/order.
7. Routing/ETA algorithm:
   - route geometry theo road network;
   - current map-matched position tới remaining stops;
   - traffic-aware travel time, dwell time, actual check-in và uncertainty;
   - recompute trigger, cadence, stale-data policy và fallback.
8. Traffic integration:
   - TrafficProvider và RoutingProvider interface;
   - HERE adapter, DTO mapping, cache, timeout/retry/backoff/rate limit;
   - provider status, observedAt, expiresAt và degraded/demo mode hiển thị rõ.
9. Reroute/reschedule policy:
   - incident-to-route corridor intersection hoặc routing comparison;
   - severity/delay threshold và cooldown;
   - so sánh route hiện tại với alternative;
   - lưu schedule revision, ETA before/after, reason và quyết định KEEP/REROUTE;
   - alert deduplication.
10. Security/config:
    - HERE_API_KEY ở backend environment;
    - VITE_CARTO_API_KEY chỉ dùng cho tile key phù hợp để xuất hiện ở browser;
    - .env.example chỉ chứa placeholder;
    - CORS/WebSocket origin theo cấu hình, không wildcard trong production.
11. UX states: loading, empty, error, reconnecting, stale traffic và provider degraded.
12. Mermaid sequence diagrams cho telemetry, check-in và traffic-triggered reroute.

Artifact bắt buộc:

- `<feature-dir>/spec.md`
- API/event contract nằm trong Spec hoặc artifact bổ sung được Spec tham chiếu
- quyết định quan trọng và trade-off nằm trong Research/Spec.

Phase gate: mỗi AC có SPEC-ID; contract đủ để viết test trước khi implementation.

============================================================
PHASE 5 — TEST-PLAN
============================================================

Mục tiêu: quyết định trước cách chứng minh từng acceptance criterion.

Tạo `<feature-dir>/test-plan.md` và cập nhật Evidence Matrix trong
`<feature-dir>/evidence.md`.

Test pyramid đề xuất:

- Backend unit: GeoUtil/geofence segment crossing, ETA, traffic mapping, reroute policy,
  state transition, alert dedupe.
- Backend integration: repository constraints, REST validation/errors, HERE adapter với
  mock HTTP server, WebSocket publish và concurrent/idempotent check-in.
- Frontend unit/component: form validation, route ordered stops, telemetry reducer,
  reconnect/subscription cleanup, ETA/traffic status rendering.
- Contract: OpenAPI và WebSocket payload compatibility frontend/backend.
- End-to-end: CRUD station → create route/trip → run simulator → realtime marker →
  auto check-in → ETA update → inject/mock traffic → reschedule/reroute alert.
- Non-functional: reconnect, provider timeout/429/5xx, stale cache, multiple vehicles,
  high multiplier, duplicate/out-of-order telemetry và basic responsive/accessibility.

Mỗi test case có:

Test ID | Requirement/AC | Layer | Preconditions | Steps/Input | Expected |
Automation | Evidence ID | Status.

Test data phải có ít nhất:

- route với START, nhiều STOP, END;
- hai trạm gần nhau và một route có station occurrence lặp;
- xe đi vào, đi ra, đứng trên biên và nhảy xuyên qua geofence;
- traffic bình thường, congestion, accident, construction;
- HERE success, empty, timeout, 401/403, 429, 5xx và stale response;
- reroute tốt hơn, không tốt hơn và cooldown/dedup scenario.

Artifact bắt buộc:

- `<feature-dir>/test-plan.md`
- `<feature-dir>/evidence.md` có EVD-* dự kiến cho từng Test Case/Requirement quan trọng.

Phase gate: mọi AC có ít nhất một test; critical path có integration hoặc E2E test,
không chỉ unit test.

============================================================
PHASE 6 — PLAN
============================================================

Mục tiêu: chia implementation thành vertical slice nhỏ, có dependency và rollback rõ.

Tạo `<feature-dir>/plan.md`. Mỗi task phải có:

Task ID | Requirements | Files/components dự kiến | DB/API/event changes | Tests |
Evidence | Dependency | Risk | Definition of Done.

Thứ tự mặc định, điều chỉnh dựa trên Survey/Spec:

1. Baseline, config, error contract và test infrastructure.
2. Station CRUD hoàn chỉnh end-to-end.
3. Route ordered-stop management và road geometry/duration.
4. Vehicle/trip management và selection rõ ràng.
5. Versioned realtime telemetry contract và reliable frontend connection.
6. Geofence/check-in idempotent, gồm segment crossing.
7. ETA engine cho remaining stops.
8. TrafficProvider/RoutingProvider + HERE adapters + cache/fallback.
9. Simulator sử dụng route geometry, traffic và deterministic clock/test mode.
10. Reroute/reschedule + alert dedupe + audit trail.
11. UI integration, E2E, observability, documentation và evidence finalization.

Ưu tiên vertical slice có thể demo; không tạo một task lớn kiểu "làm toàn bộ backend".

Artifact bắt buộc:

- `<feature-dir>/plan.md`
- Evidence Matrix cập nhật Plan Step và loại Evidence cần thu thập.

Phase gate: không còn task mơ hồ, dependency theo thứ tự, mỗi task có test/evidence và
safe rollback/migration strategy nếu thay đổi schema.

============================================================
PHASE 7 — IMPLEMENT
============================================================

Mục tiêu: triển khai từng task đã duyệt và thu evidence ngay khi hoàn thành.

Quy trình bắt buộc cho mỗi task:

1. Kiểm tra git status và đọc chính xác file liên quan.
2. Viết/cập nhật test thể hiện acceptance criterion.
3. Thực hiện thay đổi nhỏ nhất đáp ứng spec.
4. Chạy test tập trung, sau đó chạy regression phù hợp.
5. Chạy runtime/API/UI verification nếu task có integration.
6. Tạo/cập nhật record EVD-* trong `<feature-dir>/evidence.md` và Evidence Matrix.
7. Cập nhật Plan Step, implementation path và trạng thái Evidence.
8. Báo cáo files changed, commands, result, evidence và remaining risks.
9. Không đánh dấu task Done khi test/evidence bắt buộc chưa có.

Ràng buộc implementation:

- Backend là nguồn sự thật cho trip state, check-in, ETA, traffic normalization và alert.
- Frontend không tự suy luận nghiệp vụ quan trọng khác backend.
- Tiền tệ/thời gian/tọa độ dùng kiểu dữ liệu và timezone nhất quán.
- External API có abstraction và mock/fake để test không phụ thuộc Internet.
- Fake/manual incident được phép cho test/demo nhưng phải được gắn source=SIMULATED và
  UI không được trình bày nó là traffic thật.
- Dữ liệu thật phải có source/provider ID/observedAt và freshness status.
- Simulator phải di chuyển theo polyline mạng đường, không nối thẳng station nếu spec
  yêu cầu road route.
- Check-in là idempotent và transaction-safe.
- WebSocket event có eventId/sequence để client xử lý duplicate/out-of-order.
- Realtime URL, API URL, provider config và threshold lấy từ cấu hình.
- Không thêm secret thật vào repository; nếu phát hiện secret đã lộ, báo người dùng cần
  rotate/revoke và thay bằng placeholder.
- Mọi thay đổi schema dùng migration có thể tái lập; không phụ thuộc ddl-auto cho production.

Runtime artifact ưu tiên lưu dạng text/JSON sanitized bên cạnh feature hoặc trong thư
mục artifact được `evidence.md` tham chiếu; ảnh UI đặt tên:

`EVD-<sequence>-<slug>.<ext>`

Ví dụ:

- `<feature-dir>/artifacts/EVD-001-telemetry-websocket.json`
- `<feature-dir>/artifacts/EVD-003-update-station-ui.png`
- `<feature-dir>/artifacts/EVD-005-segment-crossing-test.txt`
- `<feature-dir>/artifacts/EVD-008-reroute-alert.json`

============================================================
PHASE 8 — REVIEW
============================================================

Mục tiêu: review độc lập dựa trên requirement/evidence, không chỉ xem diff đẹp.

Tạo `<feature-dir>/review.md` và thực hiện:

1. Review theo severity: Critical, High, Medium, Low.
2. Kiểm tra correctness, race/concurrency, transaction, idempotency và data integrity.
3. Kiểm tra frontend cleanup, duplicate subscription, stale state và error/loading UX.
4. Kiểm tra API/WS compatibility, validation và backward compatibility.
5. Kiểm tra traffic data thật, timestamp/freshness, fallback và nhãn simulated/degraded.
6. Kiểm tra secrets bằng repository search; không ghi lại giá trị tìm thấy.
7. Chạy full backend tests, frontend lint/build/tests, integration/E2E và smoke test.
8. Đối chiếu từng dòng Evidence Matrix với EVD-* và artifact thật.
9. Re-run critical evidence R1, R5, R6, R7, R8 trên môi trường sạch nếu khả thi.
10. Ghi known limitations và production-readiness gaps trung thực.
11. Không mặc định tin kết quả do Implementer báo cáo; đối chiếu với source code/git diff.
12. Tạo finding cho Claim quan trọng thiếu Evidence hoặc Evidence không đủ mạnh.
13. Không APPROVED nếu requirement quan trọng còn INCONCLUSIVE.

Final report phải theo mẫu:

# Final Delivery Report

## Outcome
- Kết quả tổng thể và phạm vi thực sự hoàn thành.

## Requirement status
| Requirement | Status | Acceptance result | Evidence | Remaining gap |

Status của kết luận requirement chỉ dùng PASS, FAIL hoặc INCONCLUSIVE.

## Verification commands
| Command | Exit code | Result | Evidence |

## Architecture decisions
- Decision, lý do và trade-off.

## Security/configuration
- Biến môi trường cần thiết, không chứa giá trị secret.

## Known limitations and risks
- Nêu rõ giới hạn provider, coverage, quota và phần chưa production-ready.

## How to run and reproduce
- Lệnh chạy local, test và kịch bản demo end-to-end.

## Changed files
- Nhóm file theo backend, frontend, infrastructure và tài liệu feature/Evidence.

## Suggested next steps
- Chỉ đề xuất sau khi phân biệt rõ bắt buộc và tùy chọn.

Phase gate cuối: không còn Critical/High issue chưa xử lý; full verification có kết quả;
R1–R8 đều có status và Evidence; không có requirement quan trọng INCONCLUSIVE; tài liệu
chạy lại được bởi người khác.

============================================================
DEFINITION OF DONE TOÀN DỰ ÁN
============================================================

Dự án chỉ được tuyên bố Done khi:

- R1–R8 có acceptance criteria rõ và trạng thái trung thực.
- Station/route/trip có luồng UI + API + DB đầy đủ theo phạm vi đã duyệt.
- Xe cập nhật realtime không reload và reconnect an toàn.
- Check-in tự động đúng thứ tự, chống lặp và không bỏ sót segment crossing.
- ETA tới từng trạm được tính từ vị trí hiện tại và phản ứng với traffic.
- Simulator đi theo road geometry và hiển thị đúng speed/next stop/ETA/state.
- Traffic thật không bị nhầm với basemap hoặc dữ liệu seed; provider/freshness hiển thị được.
- Sự cố nghiêm trọng kích hoạt recompute và thông báo có before/after, không spam.
- Secrets không nằm trong source/evidence; config mẫu và hướng dẫn setup đầy đủ.
- Full build/test/lint và critical E2E pass; phần chưa đủ bằng chứng phải ghi
  INCONCLUSIVE và không được coi là hoàn thành.
- Evidence Matrix không có Claim thiếu implementation/test/Evidence và không tham chiếu
  EVD-* không tồn tại.

Khi gặp quyết định sản phẩm có thể làm thay đổi đáng kể kết quả (provider trả phí,
ngưỡng reroute, phạm vi coverage, SLA realtime), hãy trình bày lựa chọn và dừng xin xác
nhận. Với chi tiết triển khai có thể đảo ngược và nằm trong spec đã duyệt, hãy tự chọn
phương án hợp lý rồi ghi lại quyết định.
```

---

## 3. Prompt tiếp tục cho từng phase

Nếu Agent dừng sau phase gate, dùng một trong các prompt sau:

```text
Tiếp tục Phase 2 — Research theo VEHICLETRACKING_AGENT_WORKFLOW.md.
Dùng nguồn chính thức, tạo đủ artifact/evidence, cập nhật traceability rồi dừng ở phase gate.
```

```text
Tiếp tục Phase 3 — Survey. Chỉ khảo sát code/runtime và baseline; chưa sửa production code.
Mọi đánh giá hiện trạng phải có file path, command output hoặc runtime evidence.
```

```text
Tiếp tục Phase 4 — Spec. Giải quyết các gap đã xác nhận trong Survey thành contract,
algorithm, state machine và architecture cụ thể; cập nhật traceability rồi dừng.
```

```text
Tiếp tục Phase 5 — Test-Plan. Mỗi AC phải có test và Evidence ID; bao phủ negative,
provider failure, duplicate event, GPS jump và severe traffic/reroute scenarios.
```

```text
Tiếp tục Phase 6 — Plan. Chia thành vertical slices nhỏ, có dependency, test, evidence,
risk và Definition of Done. Chưa implement.
```

```text
Tiếp tục Phase 7 — Implement theo plan.md đã duyệt. Làm từng task nhỏ, chạy verification,
tạo EVD-* và cập nhật Evidence Matrix ngay sau mỗi task. Không bỏ qua lỗi baseline mới.
```

```text
Tiếp tục Phase 8 — Review độc lập. Chạy full verification, đối chiếu R1–R8 với evidence,
git diff và source code; không APPROVED nếu requirement quan trọng còn INCONCLUSIVE.
```

---

## 4. Checklist dành cho người review Agent

Trước khi chấp nhận kết quả, kiểm tra nhanh:

- [ ] Agent không nhảy qua Research/Survey để code ngay.
- [ ] CARTO được mô tả là basemap, không phải traffic provider.
- [ ] API key traffic chỉ nằm ở backend environment và evidence không lộ secret.
- [ ] Requirement có Given/When/Then và ID truy vết.
- [ ] Current implementation được xác minh bằng code/runtime, không đoán.
- [ ] Test-plan được viết trước phần implementation tương ứng.
- [ ] Mỗi PASS có code + test + runtime; UI feature có thêm screenshot/video.
- [ ] Traffic demo/seed không được ghi là dữ liệu giao thông thật.
- [ ] ETA/reroute có before/after và source timestamp.
- [ ] Geofence test được GPS jump, duplicate và route có station lặp.
- [ ] WebSocket test reconnect, duplicate, out-of-order và nhiều xe.
- [ ] Build/lint/test command có exit code và output tái lập được.
- [ ] Final report nêu rõ FAIL/INCONCLUSIVE/known limitations nếu còn thiếu.
