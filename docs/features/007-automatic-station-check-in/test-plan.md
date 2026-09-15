# 007 — Kế hoạch kiểm thử

Trạng thái: **Implementing** · Test hồi quy và test GPS/simulator/API đã chạy; ma trận edge/browser 007 còn bổ sung.

## Điều kiện trước triển khai

Backend 006 đã được kiểm tra cùng full suite trong lượt triển khai này. Kết quả thực tế của 007 nằm trong [evidence](evidence.md); không lấy test fixture thay thế cho browser Spring/PostgreSQL.

Môi trường cần xác nhận lại: Java 26, Maven wrapper, PostgreSQL 17 Testcontainers/Docker, Node >=22.12 (repo định hướng 24), trình duyệt cho harness. Source harness hiện mặc định msedge trên Windows; máy mới Linux cần browser hợp lệ, có bước chuẩn bị trong [plan P0/P6](plan.md). Không dùng DB dev người dùng làm DB nghiệm thu.

## Ma trận AC

| AC | Mức test | Kịch bản | Dữ liệu/fixture | Kết quả mong đợi |
|---|---|---|---|---|
| AC-01 | Service + HTTP | Start chưa có mẫu; GPS đúng/sai xe, SCHEDULED/terminal, source giả, mẫu trước startedAt | TripCheckInIntegrationTest; CheckInHttpIntegrationTest; clock cố định | Chỉ mẫu mới được chấp nhận sau start mới tạo visit; giữ status 400/404/409 của ingestion 006; không dùng vị trí chuyến cũ. |
| AC-02 | JPA integration | Sửa name/coords/radius station sau tạo trip; loop lặp station | CheckInFixtures A→B→A; snapshot radius 20 m, station đổi 200 m | Dùng radius/tên/coords snapshot, unique theo occurrence, baseline không đổi. |
| AC-03 | Geometry unit + integration | POINT trong/ngoài/rìa; hai đầu ngoài cắt vòng; tiếp tuyến; đoạn dài 0; tốc độ không bằng 0 | GeofenceCrossingTest, tọa độ tổng hợp với khoảng cách/mốc kỳ vọng độc lập | Đi ngang được ghi nhận một lần, tìm entry sớm nhất; không bắt dwell; sai số ≤0,01 m tại biên theo spec. |
| AC-04 | State machine + JPA | Đứng yên nhiều mẫu, jitter, vùng chồng/đồng tâm, A→B→A, bỏ lỡ target, nhiều stop trong một trace | StopSequencePolicyTest; trip 3–5 stops, hai station ID cùng tọa độ | Không nhảy target; không dùng cùng điểm entry hai lần; overlap phải exit+hysteresis rồi reenter; thứ tự visits đúng. |
| AC-05 | Unit + HTTP/JPA | Accuracy/gap/distance/speed đúng ngưỡng và vượt ngưỡng; GPS jump đặt điểm cuối trong vòng | Clock + cặp mẫu kiểm soát | Bad-quality vẫn lưu history/latest nếu hợp lệ 006 nhưng không tạo crossing; jump ngắn chặn cả POINT; gap dài cho POINT hiện tại, không backdate. |
| AC-06 | PostgreSQL + concurrency | Cùng event retry/đồng thời, event khác cùng target, restart, lỗi sau insert visit | Hai transaction qua barrier; injected failure; checkpoint persisted | Một visit/occurrence; same payload idempotent; collision/old packets không tăng revision; rollback sample/latest/visit/state/run nhất quán. |
| AC-07 | Unit + API + browser | Giờ POINT/SEGMENT/TRACE, microseconds, qua ngày, start trễ, receivedAt khác recordedAt | Clock cố định; lịch departure khác now | Nguồn/fraction/sample/time chính xác, nhãn suy ra, giờ giả lập riêng; schedule/lifecycle không bị detector sửa. |
| AC-08 | RouteMotion + simulator integration | 1×/5×/10× qua góc cua, dwell, nhiều stop/tick, trọn loop trong một tick, pause/recover | Trace fixture cong nơi đường nối đầu/cuối không qua stop; dùng geometry encode fixture | Trace khớp at trong 0,5 m; không nối tắt/gap; nhận visits theo quỹ đạo; clock nghỉ không sinh travel; tick cuối xử lý trước complete. |
| AC-09 | HTTP/SSE + browser Spring | Hai subscribers, disconnect lúc có visit, reload, old Last-Event-ID, HTTP chậm hơn SSE | CheckInHttpIntegrationTest + Playwright hai tab; DB tạm | GET/SSE cùng read model; full resync phục hồi visits; revision không lùi; cleanup subscribers; không thấy dữ liệu rollback. |
| AC-10 | Browser + lint/tsc/build | Loading/error/retry/empty/reconnecting, chọn nhanh hai trip, target chờ exit, 320 px | Fixture có nhãn riêng và browser Spring; 320/390/768/1440 px | Không lẫn visit giữa trip, baseline vẫn xem được khi lỗi; đúng nhãn GPS/giả lập/suy ra; không tràn ngang/mất focus/draft. |
| AC-11 | Integration + browser | Complete/cancel thiếu stop; reset hai lần; tiếp tục trên trip mới; reload trip cũ | SimulationFixtures mở rộng, lifecycle REST | Visits cũ giữ nguyên, không sinh visit cho stop còn thiếu; reset cùng replacement, trip mới trống tới khi play; không nối từ trip cũ. |
| AC-12 | Migration + regression | DB đã V5 có history, upgrade V6, Hibernate validate; thử vi phạm constraint; suite 004–006 | PostgreSQL tạm, migration target V5 rồi migrate V6 | Không backfill/sửa migration cũ; FK/UNIQUE/CHECK hoạt động; regressions đạt; không cần key/HERE live. |

## Trường hợp biên bắt buộc

- Geometry: kinh tuyến ±180°, gần cực, radius 10/1000 m, float finite, hai mẫu trùng tọa độ, phân số 0/1, tiếp tuyến; không lấy expected value từ cùng hàm đang test.
- Quality: gap 15 s và 15 s +1 µs; accuracy=min(30,r/2) và vừa vượt; inferred speed 160 và vừa vượt; đoạn 1000 và vừa vượt. Kiểm tra các nhánh riêng bằng fixture không vô tình vi phạm ngưỡng khác trước.
- Gap: ngoài→ngoài qua target trong 60 s không có visit; ngoài→trong sau 60 s chỉ POINT tại giờ mẫu cuối; accuracy mẫu trước xấu không nối về mẫu tốt cũ; first sample sau triển khai không backfill.
- Order: khi target tiếp theo được kích hoạt trong vùng overlap, nhiều POINT đứng yên không arm; ra ngoài radius+hysteresis mới arm; đi vào radius mới visit. Kích hoạt trong vành hysteresis cũng phải ra ngoài trước.
- Race: telemetry transaction giữ trip lock trong lúc complete/cancel chạy; thứ tự thắng khác nhau phải cho kết quả nhất quán. Hai trip cùng xe không làm cross-trip segment. Test UNIQUE/FK trực tiếp, không chỉ thử service đã khóa sẵn.
- DB failures: lỗi checkpoint sau tạo visit, lỗi latest sau detect; assert toàn bộ rollback. Same-event retry sau terminal/restart trả sample cũ và không tạo state mới.
- Migration: chạy V5 trước, tạo dữ liệu cũ rồi V6; không sửa checksum; composite FK ngăn from/to khác trip/source và last_sample khác trip; null/from/kind/source/fraction invalid bị DB chặn.
- Simulator: trace qua hai section có gap không tạo giả crossing; 0 elapsed, zero-length geometry segment, đổi multiplier giữa hai tick, reset/recover, curved polyline nơi chord cắt một trạm không thuộc quỹ đạo phải **không** ghi nhận trạm đó.
- UI race: GET revision thấp trả sau SSE revision cao; create/reset trip mới chưa có trong snapshot cũ; backend thiếu checkIns báo không tương thích, không giả visits rỗng; mất kết nối không xóa lịch sử đã thấy.

## Hiệu năng và kết quả cần lưu

Môi trường dev: 10 trip đang chạy, mỗi trip 50 stop, 1 sample/giây trong 60 giây, 2 tab/subscriber; không gọi provider. Đo thời gian commit→UI cho visit mới, số query ingestion/snapshot, payload và errors. Mục tiêu mỗi visit xuất hiện trong ≤2 giây trên cả hai tab; báo số đo thật và cấu hình máy, không suy thành SLA production. Sử dụng giới hạn wait rộng hơn mục tiêu để tránh treo, nhưng assertion nghiệm thu latency vẫn 2 giây.

Không quét history theo trip mỗi sample, không query mỗi visit/sample trong snapshot để dựng DTO; baseline có thể có các query sẵn có nhưng query do 007 thêm phải batch. Nếu vượt mục tiêu cần profiling và điều chỉnh trong phạm vi trước khi Verified, không chỉ tăng timeout assertion.

## Lệnh kiểm tra dự kiến

Sau implement, từ `vehicletracking-backend/`:

```bash
./mvnw -Dtest=GeofenceCrossingTest,StopSequencePolicyTest,RouteMotionTest,TripCheckInIntegrationTest,CheckInHttpIntegrationTest test
./mvnw test
```

Lệnh full suite đã chạy với PostgreSQL Testcontainers: 155 tests pass. Test GPS/simulator/API/geometry được bổ sung vào `OperationsIntegrationTest`, `OperationsHttpIntegrationTest` và `GeofenceCrossingTest`; browser Spring/PostgreSQL đã có wrapper 007, còn load/edge cases là phần P6 chưa chạy.

Từ `vehicletracking-frontend/`:

```bash
npm run lint
./node_modules/.bin/tsc --noEmit
npm run build
```

Browser 007: `verification/live-browser.mjs` bọc harness 006, yêu cầu URL backend/ID fixture do Java test truyền và bật assertion ba visit simulator; fixture-only dùng script riêng có nhãn rõ. Không cài component/E2E runner vào ứng dụng ngoài bước chuẩn bị harness được duyệt. Chạy lại regressions 004–006 và manual map picking/reorder/focus/mobile; ghi mọi thay đổi selector do copy 007, không bỏ assertion nghiệp vụ.

Evidence sau thực thi nằm ở [evidence.md](evidence.md) và [walkthrough.md](walkthrough.md); fixture browser, Spring/PostgreSQL browser, GPS thiết bị thật và load benchmark được tách rõ, không tuyên bố pass khi chưa chạy.
