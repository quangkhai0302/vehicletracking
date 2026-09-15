# Test Plan: Tạo tuyến đường từ danh sách điểm dừng

## 1. Mục tiêu

Chứng minh route được tạo đúng thứ tự, tính đúng distance/duration/dwell, lưu atomically, giữ HERE key ở backend và hiển thị geometry thật trên Leaflet. Test tự động không gọi HERE production.

## 2. Chiến lược kiểm thử

- **Unit test:** business validation, totals, stop roles/offsets và provider section mapping.
- **Provider contract test:** mock HTTP response của HERE bằng fixture, kiểm tra query và normalized result.
- **Controller test:** request validation, status, Location, response/error contract.
- **Repository/integration test:** V3 migration, JPA mapping, order, FK/check/unique constraint và rollback.
- **Frontend static verification:** oxlint, TypeScript, Vite production build.
- **Manual:** route builder, map geometry, reorder, keyboard, responsive và lỗi provider.
- **Smoke test tùy môi trường:** một request tới HERE sandbox/account thật; không đưa key hoặc full URL vào evidence.

Repository chưa có frontend test runner, nên feature này không tự cài Vitest/Testing Library. Các luồng frontend được kiểm tra bằng type/lint/build và checklist manual; việc bổ sung test framework là feature riêng nếu cần.

## 3. Ma trận acceptance criteria

| AC | Mức test | Kịch bản | Dữ liệu/fixture | Kết quả mong đợi |
|---|---|---|---|---|
| AC-01 | Integration | Start context với PostgreSQL sạch và chạy Flyway | Testcontainers PostgreSQL 17 | V1→V3 chạy thành công; Hibernate validate; đủ 3 bảng/constraint/index. |
| AC-01 | Repository | Persist route, stops, sections rồi đọc detail | 3 stops, 3 sections | Quan hệ đúng, stop/section order ổn định, snapshot fields không mất. |
| AC-02 | Service + manual | Tạo/reorder danh sách 2–50 stop | IDs active; tuyến vòng | Role đầu/giữa/cuối đúng; input order được giữ; loop hợp lệ. |
| AC-03 | Controller/service/manual | Dwell hợp lệ và các edge cases | -1, 0, 120, 3600, 3601; dwell ở START/END | 0–3600 cho STOP; START/END khác 0 bị 400; UI disable/inline error. |
| AC-04 | Provider test | Verify HERE request query | 4 waypoints, 2 via có dwell | Đúng origin/via/destination order, `car`, `fast`, return fields và stopDuration. |
| AC-05 | Static/security | Search source, build output và captured logs | Patterns `VITE_.*HERE`, real key, `apiKey=` | Không có HERE secret ở frontend/response/log; evidence redact. |
| AC-06 | Service/integration | Provider throw timeout/no-route/malformed | Mock exceptions | RouteRepository/persistence không được gọi; DB không có row dở dang. |
| AC-07 | Controller | POST success | Fixture multi-stop | 201, Location đúng, totals/stops/sections đúng JSON shape. |
| AC-08 | Unit/provider | Một leg có nhiều sections và dwell ở stop giữa | HERE fixture có implicit extra section | Sections nhóm đúng destination stop; totals và offsets đúng công thức. |
| AC-08 | Unit | Base duration thiếu ở một section | Section ferry-like `baseDuration=null` | Base duration fallback dynamic; tổng không null/âm. |
| AC-09 | Controller/repository | List và detail | Hai route khác createdAt; unknown ID | List mới nhất trước, không chứa polyline; detail ordered; missing trả 404. |
| AC-10 | Controller/service | Invalid input/station state | blank name, 1/51 stops, ID âm, missing/inactive ID, consecutive duplicate | 400/422 đúng contract; không 500; provider/persistence không gọi sai. |
| AC-11 | Context/controller | Routing disabled | `here.routing.enabled=false` | App/context và station API khởi động; GET route dùng được; POST route trả 503. |
| AC-12 | Manual | Chọn route detail trên UI | Route 3 stops có nhiều section | Solid road geometry, numbered stops và fit bounds đúng; không recreate map. |
| AC-13 | Manual | Loading/empty/provider fail/decode fail | Backend off, 503/504/502, invalid polyline | Thông báo rõ; form giữ dữ liệu; không vẽ đường chim bay. |
| AC-14 | Build suite | Chạy backend và frontend commands | JDK 26, Node >=22.12 (ưu tiên Node 24) | Tất cả exit 0. |
| AC-15 | Documentation/review | Kiểm tra evidence/walkthrough/review sau implement | Working tree + command output thật | Không tuyên bố quá kết quả; Codex review finding và kết luận. |

## 4. Backend test cases chi tiết

### 4.1 `RouteServiceTest`

1. Trim route name và giữ đúng stop order dù repository trả entity không theo thứ tự.
2. Suy ra START/STOP/END đúng với 2 stops và nhiều stops.
3. Chấp nhận station lặp không liền kề và START=END cho route loop.
4. Từ chối consecutive duplicate trước khi gọi provider.
5. Từ chối START/END dwell khác 0.
6. Từ chối missing/inactive station và báo đầy đủ ID không khả dụng.
7. Provider disabled → 503-domain exception; không gọi persistence.
8. Provider no-route/malformed/timeout → mapped exception; không gọi persistence.
9. Tổng hợp nhiều sections về cùng destination stop.
10. Tính distance, dynamic/base travel duration, dwell và trip duration.
11. Tính arrival/departure offset cho từng stop.
12. Provider success → persistence gọi đúng một lần với route snapshot đầy đủ.

### 4.2 `HereRoutingProviderTest`

Dùng `MockRestServiceServer` hoặc HTTP stub tương đương gắn với named `RestClient`:

1. Hai stops → origin/destination, không có via.
2. Bốn stops → hai via đúng thứ tự; via dwell >0 có `stopDuration`.
3. Verify `transportMode=car`, `routingMode=fast`, `return=polyline,summary,travelSummary`.
4. HTTP 200 multi-waypoint fixture → normalized destination stop sequences đúng.
5. HTTP 200 có multiple sections trong một leg → mapping theo waypoint index, không theo count.
6. HTTP 200 routes rỗng/critical notice → no-route exception.
7. HTTP 200 thiếu polyline/travelSummary/sai waypoint order → invalid-response exception.
8. Base duration null → fallback duration.
9. HTTP 401/403 → provider/config error, không lộ body/key.
10. HTTP 429/5xx → unavailable/rate-limit error.
11. Connect/read timeout → timeout error.
12. Không assert/log full request URL có API key; chỉ kiểm tra query qua sanitized request matcher.

Fixture dự kiến:

```text
src/test/resources/fixtures/
├── here-route-multi-stop.json
├── here-route-multi-section-leg.json
├── here-route-no-route.json
└── here-route-critical-notice.json
```

### 4.3 `RouteControllerTest`

1. POST valid → 201 + Location + body detail.
2. Blank name, null stops, 1 stop, 51 stops → 400 và service không được gọi.
3. Null/non-positive station ID hoặc dwell ngoài miền → 400.
4. Service station unavailable → 422 ProblemDetail + code.
5. Routing disabled → 503 ProblemDetail + code.
6. Provider timeout → 504.
7. GET list → 200 summary array không có `sections`.
8. GET detail existing → 200.
9. GET detail missing → 404.

### 4.4 `RouteRepositoryIntegrationTest`

1. Persist graph route/stops/sections bằng cascade và flush.
2. Đọc detail có ordered stops/sections, không N+1 ngoài query strategy đã chọn.
3. Cho phép cùng station ở sequence 1 và cuối; từ chối duplicate `(route_id, sequence_number)`.
4. Từ chối dwell <0 hoặc >3600 bằng DB constraint.
5. Từ chối coordinate snapshot ngoài WGS84 bounds.
6. Từ chối section tham chiếu destination stop sequence không tồn tại.
7. FK station restrict và route cascade behavior đúng.
8. Transaction persist lỗi giữa graph → không còn row route/stop/section.
9. List ordering theo `created_at DESC, id DESC`.

## 5. Frontend manual checklist

### 5.1 Builder

- [ ] Route workspace mở được từ header và active navigation đúng.
- [ ] Station chưa đủ 2 → hướng dẫn rõ, submit disabled.
- [ ] Search station, thêm stop, bỏ stop không tạo duplicate event.
- [ ] Reorder bằng nút và bàn phím; START/STOP/END đổi đúng ngay lập tức.
- [ ] Hai stop liên tiếp giống nhau bị ngăn/cảnh báo; station lặp không liền kề được phép.
- [ ] Dwell chỉ edit ở STOP, nhận 0 và 3600, từ chối ngoài miền.
- [ ] Loading khóa submit chống gửi lặp.
- [ ] API lỗi giữ nguyên form và thứ tự stop.
- [ ] Success chọn route mới và chuyển drawer sang browse.

### 5.2 List/detail

- [ ] Loading, empty và error state phân biệt rõ.
- [ ] List không render payload polyline lớn.
- [ ] Search route theo tên hoạt động.
- [ ] Detail format mét/km và giây/phút/giờ dễ đọc, dùng tabular numbers.
- [ ] Hiển thị calculated time và nhãn đây là estimate snapshot.
- [ ] Timeline stop có role, dwell, distance, travel time và arrival offset.

### 5.3 Map

- [ ] Mỗi encoded section decode và nối trực quan đúng thứ tự.
- [ ] Không có đoạn thẳng nối station khi provider/decode lỗi.
- [ ] Numbered stop marker không che quá nhiều road label.
- [ ] Chọn route gọi fit bounds một lần.
- [ ] Chuyển tracking/stations/routes clear đúng layer, không xóa nhầm vehicle route.
- [ ] Click station trong routes không mở station drawer.
- [ ] Resize desktop/tablet/mobile không che toàn bộ map hoặc mất action chính.

## 6. Lệnh verification bắt buộc

Backend, bằng JDK 26:

```bash
cd vehicletracking-backend
./mvnw test
```

Frontend, bằng Node phù hợp `.nvmrc`:

```bash
cd vehicletracking-frontend
npm run lint
./node_modules/.bin/tsc --noEmit
npm run build
```

Kiểm tra secret, chỉ tìm tên biến/pattern; không in nội dung `.env`:

```bash
rg -n "VITE_.*HERE|apiKey=|HERE_API_KEY" vehicletracking-frontend/src vehicletracking-backend/src/main
```

Kết quả hợp lệ phải cho thấy `HERE_API_KEY` chỉ được tham chiếu trong cấu hình backend, không có giá trị key thật.

## 7. Smoke test HERE tùy chọn nhưng khuyến nghị

- Chỉ chạy khi người review cung cấp môi trường có `HERE_ROUTING_ENABLED=true` và key hợp lệ.
- Dùng 2–3 station test tại TP.HCM.
- Không ghi request URL đầy đủ, response thô hoặc secret vào evidence.
- Xác minh provider trả geometry, totals hợp lý và stop order đúng.
- Smoke test không thay thế mocked provider tests.

## 8. Điều kiện Verified

- Mọi AC có evidence tự động hoặc manual.
- Backend suite chạy thật bằng JDK 26, không phải 0 tests.
- PostgreSQL integration test chứng minh V3/JPA.
- Frontend lint/typecheck/build exit 0.
- Không có request thật tới HERE trong test suite mặc định.
- Không có secret trong source/bundle/log.
- Không có source change ngoài file đã duyệt hoặc sai lệch đã được ghi rõ.
