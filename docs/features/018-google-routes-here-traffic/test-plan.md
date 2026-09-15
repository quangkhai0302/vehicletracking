# Test plan

Trạng thái: **đang thực hiện**. Kết quả test fixture đã chạy và giới hạn môi trường được ghi tại [evidence](evidence.md); chưa có nghiệm thu Google live.

| AC | Mức test | Kịch bản / fixture dự kiến | Kết quả mong đợi |
|---|---|---|---|
| 01 | Unit + Spring wiring | Default HERE/GOOGLE, flags/key thiếu, route HERE đang chạy khi đổi default | Đúng một provider theo route; cấu hình lỗi rõ; không provider fallback ngầm. |
| 02 | Java + Node | Google encoded fixture và HERE flexible cùng tọa độ; negative lat/lng, payload hỏng, section rời, duplicate boundary | Tọa độ/distance trong tolerance; không nối đường giả; mọi consumer dùng đúng decoder. |
| 03 | Adapter/controller | 2/27/28/50 trạm, 20 via, A→B→A, no-route ở lô giữa, dwell 0/3600 | Mỗi request <=25 intermediates, không thiếu/trùng occurrence; thất bại không ghi tuyến một phần. |
| 03 | Unit + browser | Drag point vào leg thứ hai; via không sinh leg; preview sửa trong lúc response cũ về; copy provider | Stop mapping đúng, bỏ stale response, giữ tuyến đã gắn chuyến. |
| 04 | ETA policy | Google duration 600s, static 400s, dwell 60s; HERE trả flow chậm hơn | ETA 660s, không cộng delay HERE; source Google. |
| 04 | ETA/geometry | Google refresh chọn đường khác; trả hình học tương đương nhưng số điểm khác; cùng cầu/đường song song | Không gắn ETA/interval lên sai tuyến; mismatch là candidate hoặc unavailable. |
| 04 | Clock | Departure tương lai/quá khứ, replay 10×, xe dwelling, check-in mép trạm | Traffic request thời gian hợp lệ; clock mô phỏng tách wall; dwell đúng một lần. |
| 05 | Node + browser | Interval thiếu start=0; cuối tại pointCount-1; overlap, index vượt, UNKNOWN | Màu đúng từng segment, điểm nối liền; dữ liệu sai không crash/giả thông thoáng. |
| 06 | Matching | HERE incident cùng đường/chiều, ngược chiều, đường song song, cầu vượt, incident hết hạn | Chỉ evidence đủ tin cậy trên phần đường còn lại mới ảnh hưởng; ambiguous warning. |
| 07 | Policy | Hai refresh Google; HERE-only refresh; cache reuse; cooldown; cùng fingerprint; reset attempt | Đếm đúng source; không trigger khi thiếu điều kiện; không trùng notifications. |
| 07 | Adapter + integration | Alternatives không intermediates; via bắt buộc; candidate nhanh chặng đầu nhưng chậm toàn đuôi; closure nằm trên candidate | Giữ trạm/via, chọn theo toàn phần còn lại, loại candidate đóng; unavailable nếu không có. |
| 07,11 | PostgreSQL transaction | Xe qua trạm/reset khi provider đang trả; hai worker cùng revision; save notification fail | Không ghi kết quả cũ; một ACTIVE revision; rollback nguyên tử; telemetry vẫn thành công. |
| 08 | Motion unit + replay integration | Profile 1/2/4 cùng leg duration; update profile giữa leg; pause/dwell/10×; apply revision nối lỗi | Tổng thời gian đúng, không teleport; km/h không nhân playback; check-in đủ thứ tự, giữ xe cuối. |
| 09 | Concurrent fake clock | 3 tab + scheduler mỗi giây + hover; 50 trạm; 429 + retry; budget ngày hết | Single-flight, refresh cap, mọi subrequest được đếm; quá quota không bão request. |
| 10 | Request/log + browser | Invalid input proxy/SSRF, upstream error có key; inspect JS/network; logo viewport; cached tile private/no-store | Không lộ secret/open proxy; attribution hiển thị; headers được tôn trọng. |
| 10,11 | Retention + migration | V1–V11 database có route/attempt HERE; thêm Google rồi advance clock tới expiresAt | Legacy không mất; expiry/purge đúng policy đã chốt ở P0, không dùng hết hạn/không tự xóa dưới xe. |
| 11 | Regression | CRUD trạm/xe/tuyến/chuyến, check-in replay, speed37 HERE, revision restore | Behavior legacy giữ nguyên, không dùng Google khi route HERE. |
| 12 | Browser | Đổi xe/mode/revision khi loading; Google down/HERE up và ngược lại; mobile; traffic modes | Đúng thông tin xe/nguồn, một flow color mode, retry hợp lệ, không che controls. |

## Test suites và công cụ

- Dự kiến thêm: GoogleRoutingProviderTest, RoutingProviderSelectionTest, GooglePolylineTest, GoogleEtaServiceTest, GoogleRequestBudgetTest, HybridReroutePolicyTest, ProviderContentRetentionTest; mở rộng RouteShapeServiceTest, RouteRepositoryIntegrationTest, RerouteSimulationIntegrationTest, CheckInReplayTest.
- Fixtures JSON tự tạo theo schema Google, không ghi API key/dữ liệu production. Java MockRestServiceServer và Clock giả; không cần gọi provider live trong unit test.
- Frontend mở rộng native Node tests (đã có `tests/vehicleMotion.test.ts`) cho decoder/interval/motion. Browser dùng scripts Playwright theo cách repository đã dùng; nếu chưa có runtime thì bổ sung harness tối thiểu vào bước P7, không cài test framework lớn ngoài phạm vi.
- Backend Java 26: `./mvnw test`; integration cần PostgreSQL Testcontainers/Docker.
- Frontend Node 24: `npm run lint`, `./node_modules/.bin/tsc --noEmit`, `npm run build`, chạy từng native Node test mới và cũ.
- `git diff --check`, xác minh schema bằng Hibernate validate và migration từ bản V11 có dữ liệu.

## Nghiệm thu live sau phê duyệt

Tuyến TP.HCM 2 trạm và nhiều trạm qua khu đường song song/cầu vượt; một chuyến Google mới ở 1×. Ghi request count, latency, provider source/fetchedAt, geometry alignment, screenshot màu route/hover, pause/replay và expiry scenario. Chỉ dùng tài khoản test/budget đã chốt. Sự cố thật không đảm bảo xuất hiện: luồng closure/reroute dùng fixture điều khiển được, đánh dấu rõ không phải bằng chứng live.

Nếu Docker/key/browser không khả dụng, ghi test cụ thể chưa chạy và lý do. Chỉ đánh dấu Verified sau AC đạt, không đổi tên lỗi môi trường thành kết quả pass.
