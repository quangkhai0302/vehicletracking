# Plan — Feature 018

**Approved / Implementing — source đã triển khai qua P1–P6 ở mức fixture; chưa bật Google production và chưa hoàn tất P7 live/DB/browser.**

Mục tiêu: Google chịu trách nhiệm hình học/ETA/đường thay thế; HERE giữ phân tích Flow/Incidents. Giữ Leaflet và trải nghiệm vận hành. Đọc theo thứ tự: [Requirement](requirement.md) → [Research](research.md) → [Survey](survey.md) → [Spec](spec.md) → [Test plan](test-plan.md) → Plan này.

## Quyết định chính đề xuất

1. Provider pin theo tuyến, không đổi chuyến cũ bằng default toàn hệ thống.
2. Google Map Tiles API chính thức trên Leaflet; không chuyển toàn app sang Google Maps JS trong feature này.
3. Google duration là nguồn ETA và profile simulator của tuyến Google. HERE speed/jam/incident có nhãn riêng; closure đủ tin cậy được chặn và tìm đường khác.
4. Alternatives tính theo chặng khi không có intermediates; giữ trạm/điểm bắt buộc, so tổng thời gian phần còn lại.
5. Retention/phát lại lịch sử Google phải chốt trước persistence, không sao chép nguyên mô hình snapshot vô thời hạn đang dùng.

## Các bước triển khai sau khi duyệt

| Bước | Mục tiêu / file dự kiến | Contract và test | Phụ thuộc, rủi ro và điều kiện xong |
|---|---|---|---|
| P0 — Chốt khả thi | Bổ sung research/spec: ma trận retention cho routes/trips/revisions/attempts/telemetry, bản đồ chính thức, request matrix Google; spike fixture và sau đó live có budget | Xác minh via/alternative/DRIVE/TWO_WHEELER, clock, geometry equivalence, Map Tiles theme/attribution; ước lượng request theo số xe và số trạm | Chạy trước migration vì quyền lưu ảnh hưởng schema. Xong khi có quyết định lưu được gì/bao lâu, replay sau expiry và nguồn ngân sách. Điểm chưa chốt phải ghi rõ, không bật production. |
| P1 — Model và compatibility | Migration mới dự kiến V12; route/entity, route/dto, reroute/entity, telemetry/simulation metadata, frontend types | Provider/mode/encoding/version/source/applicationState, nullable/expiry cụ thể sau P0; PostgreSQL migrate V11 có dữ liệu + Hibernate validate | Không sửa V1–V11. Xong khi HERE legacy không đổi, contract retention được review và test. |
| P2 — Google adapter và codec | route/provider/GoogleRoutingProvider, routing registry/request/result, config/GoogleRoutingProperties, Google RestClient; services/polyline dispatcher frontend/backend | Request masks, deadlines, error mapping, batching <=25 intermediates, mode, departure/dwell; fixtures, codec parity | P1; rủi ro leg index/lat-lng. Xong khi 50 trạm+20 via, A→B→A, no-route partial và HERE regression đạt. |
| P3 — Tạo/sửa/kéo tuyến | RouteService, RouteShapeService, RoutePersistenceService, route DTO/controllers; RouteDrawer/RouteShapeEditor/RouteWorkspace/services/routes | Preview token/hash/expiry; pin provider, copy chuyển provider, validation mode; HTTP + repository + browser drag | P2. HTTP ngoài transaction ghi; không mutate chuyến đang dùng tuyến. Xong khi preview/save/copy và planned schedule nhất quán. |
| P4 — ETA và HERE analysis | TrafficEtaService facade, GoogleTripEtaCalculator, HERE calculator, request coordinator/budget, DTO/source/config; useTripEta/tripTraffic/TripTrafficSummary | ETA geometry identity, source separation, clock/dwell, freshness, no double delay, quota/single-flight; fake-clock concurrency tests | P2/P3; đường Google thay đổi không được masquerade thành ETA đường cũ. Xong khi snapshot/UI thống nhất, Google down/HERE down xử lý độc lập. |
| P5 — Simulator và reroute | SimulationService, RouteMotion, TripRouteGeometryService, CheckInService consumer; ReroutePolicy/EvaluationService/state/notifications; frontend motion buffer | Profile ước tính khớp duration; trigger theo source, alternatives/chặng, closure veto; optimistic snapshot→HTTP→commit, applicationState | P4. Xong khi apply/reject rõ, không teleport, đủ check-in, reset không mang revision cũ, concurrent tests đạt. |
| P6 — Map và inspector | maps provider/controller/cache/Google Map Tiles config; frontend services/mapTiles, MapComponent, TrafficLayer, MapControls, RouteInspectionLayer, TrafficInspectionCard, styles | Proxy fixed host/session lifecycle/viewport attribution; GOOGLE_ROUTE/HERE_AREA/OFF; interval mapping, source labels; key-leak/error/browser tests | P0/P4/P5. Giữ Leaflet; không hứa Google TrafficLayer; nghiệm thu desktop/mobile và Google/HERE colors không chồng nhau. |
| P7 — Nghiệm thu và rollout | Tests/fixtures theo test-plan; public/huong-dan; docs feature evidence/walkthrough/review; cập nhật PROJECT_PROGRESS/SESSION_HANDOFF theo kết quả thật | Full Maven, frontend lint/tsc/build/Node/browser; retention/backup policy; canary 1 xe có budget | Mọi AC đạt, ghi rõ live khác fixture; HERE default tới lúc canary được chấp nhận. Không tự deploy/commit/push. |

## Mốc review

- **M1 (P0–P2):** provider/codec/migration tương thích, quyền lưu và số request rõ.
- **M2 (P3–P4):** tạo/kéo tuyến Google + ETA đúng tuyến, dữ liệu HERE tách nguồn.
- **M3 (P5–P6):** mô phỏng/đổi tuyến và giao diện hoàn chỉnh trên Google map chính thức.
- **M4 (P7):** nghiệm thu fixture, DB, browser và canary live; đủ cơ sở bật Google mặc định.

Không ước lượng ngày hoàn thành chính xác trước P0: điểm ảnh hưởng lớn nhất là persistence/replay theo quyền Google và matching ETA trên tuyến kéo chỉnh.

## Cần người dùng review trong hồ sơ này

- Chấp nhận Google Map Tiles chính thức trên Leaflet và chi phí API tương ứng; Google TrafficLayer ngoài scope.
- Chấp nhận Google làm nguồn thời lượng simulator; HERE speed dùng phân tích, không lấy hai nguồn điều khiển vận tốc cùng lúc.
- Chấp nhận phương án cache có thời hạn nếu không có quyền snapshot lâu dài: chuyến cũ giữ thông tin nghiệp vụ hợp lệ nhưng có thể cần tính lại Google để chạy lại; không cam kết replay đúng đường/traffic cũ vĩnh viễn.
- Xác định ngân sách thử nghiệm khi đến bước live; không gửi key trong tài liệu/chat.

## Trạng thái kiểm tra lượt lập kế hoạch

Đã đọc source/config/migrations/tests và tài liệu provider để lập sáu file. Chỉ kiểm tra tính đầy đủ/liên kết/tính nhất quán tài liệu; chưa chạy build/test sản phẩm, chưa gọi Google/HERE tính phí, chưa tạo migration hay thay env. Phê duyệt hồ sơ mới bắt đầu P0 implementation spike; các quyết định retention chưa giải quyết phải được chốt trước P1.

Kiểm tra tài liệu: đủ 6 file, liên kết nội bộ tồn tại, AC01–AC12 có dòng trong test matrix, không có trailing whitespace. `git diff --check` không báo lỗi. Ban đầu thư mục mới bị Git ignore; ở lần kiểm tra cuối `.gitignore` được thay đổi đồng thời từ bên ngoài và feature 018 đã hiện untracked. Giữ nguyên thay đổi đó cùng các file ngoài phạm vi; lượt này chỉ tạo sáu tài liệu 018, không sửa `.gitignore`/stage/commit.

## Tiến độ triển khai 2026-09-15

- P1–P3: có migration V12, provider/mode/encoding rõ, Google adapter, batching, create/update/copy provider và codec frontend/backend.
- P4–P5: Google ETA có single-flight, budget và kiểm tra geometry; simulator phân bổ duration Google theo NORMAL/SLOW/TRAFFIC_JAM; reroute dùng provider đã pin và giữ điểm dẫn đường còn ở phía trước.
- P6: Google Map Tiles proxy phía backend, Google route intervals và HERE area Flow/Incidents không chồng màu.
- P7: test fixture/non-Docker đạt; còn chờ PostgreSQL/Docker, Node 24 build, browser và canary live. Xem [evidence](evidence.md) và [review](review.md).
