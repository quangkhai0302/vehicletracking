# Vehicle Tracking — Project Handoff

> Snapshot kiểm tra ngày 2026-09-13. Tài liệu này dùng để bàn giao dự án sang máy hoặc phiên làm việc khác. Không chứa API key, mật khẩu thật hoặc dữ liệu nhạy cảm.

## 1. Trạng thái Git tại thời điểm bàn giao

- Repository: `https://github.com/quangkhai0302/vehicletracking.git`
- Branch: `master`
- HEAD: `91a3cd9 feat: add comprehensive route management module with HERE routing integration and frontend workspace UI`
- Tại thời điểm khảo sát, `master`, `origin/master` và `origin/HEAD` cùng trỏ tới `91a3cd9`.
- Source của feature quản lý tuyến đã được commit; đây không còn là feature chỉ tồn tại trong working tree.
- File tài liệu bàn giao này được tạo sau lần kiểm tra trạng thái trên và cần được commit/push nếu muốn máy mới nhận nó qua `git clone`.

### Các file cố ý không được Git theo dõi

Các rule hiện tại ignore:

- Mọi file `*.env`, gồm `.env`, `vehicletracking-backend/.env` và `vehicletracking-frontend/.env`.
- `.env.example` ở root.
- Toàn bộ `docs/`, bao gồm workflow, design, feature specification, evidence và artifacts.
- `target/`, `dist/`, `node_modules/`, cấu hình IDE.

Hệ quả: clone repository trên máy mới nhận được source, migration và test, nhưng không nhận được secret, database local hoặc bộ tài liệu dưới `docs/`.

## 2. Tổng quan sản phẩm hiện tại

Ứng dụng hiện là một hệ thống map-centric có ba workspace:

1. `tracking`: giao diện theo dõi phương tiện, hiện chưa có nguồn telemetry.
2. `stations`: quản lý trạm đầy đủ từ UI đến PostgreSQL.
3. `routes`: tạo, liệt kê và xem tuyến được tính bằng HERE Routing.

Đánh giá ngắn:

| Nhóm | Trạng thái thực tế |
| --- | --- |
| Application shell và bản đồ Leaflet | Đã triển khai |
| Quản lý trạm | Đã triển khai đầy đủ trong phạm vi hiện tại |
| Tạo/xem tuyến HERE | Đã triển khai và commit |
| Theo dõi xe realtime | Mới có khung UI, chưa có dữ liệu/API realtime |
| Simulator telemetry | Có component điều khiển rời, chưa được nối vào app |
| HERE Traffic | Có cấu hình và frontend service rời, chưa có backend API/consumer hoàn chỉnh |
| Trip/lịch trình, vehicle, driver | Chưa triển khai |
| Geofence check-in tự động | Chưa triển khai |
| Authentication/authorization/audit | Chưa triển khai |

Source hiện tại là bằng chứng chính. Một số tài liệu Feature 002 mô tả simulator đã hoàn thành nhưng không còn khớp source: `vehicles` đang là mảng rỗng, `SimulatorControls` không được render và không có simulator engine.

## 3. Công nghệ và phiên bản

### Backend

- Java 26.
- Spring Boot 4.1.1.
- Spring MVC.
- Spring Validation.
- Spring Data JPA/Hibernate.
- PostgreSQL 17.
- Flyway.
- Maven Wrapper.
- Lombok.
- Testcontainers PostgreSQL cho integration test.

### Frontend

- React 19.2.
- TypeScript 7.
- Vite 8.2.
- Leaflet 1.9.
- Lucide React.
- Oxlint.
- Node tối thiểu 22.12; repository định hướng Node 24 qua `.nvmrc`.
- Chưa có Vitest/Jest/Playwright trong `package.json`.

### Local infrastructure

`compose.yaml` chỉ chạy:

- PostgreSQL 17 ở port mặc định `5432`.
- pgAdmin ở port `5050`.

Backend và frontend được chạy trực tiếp, không nằm trong Compose.

## 4. Cấu trúc repository

```text
vehicletracking/
├── AGENTS.md
├── compose.yaml
├── PROJECT_HANDOFF.md
├── docs/                              # local/ignored by Git
│   ├── design.md
│   ├── workflow.md
│   └── features/
│       ├── 001-station-management-redesign/
│       ├── 002-fleet-tracking-dashboard-and-station-ux/
│       └── 003-route-creation-from-stops/
├── vehicletracking-backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/.../
│       │   ├── config/
│       │   ├── station/
│       │   └── route/
│       ├── main/resources/
│       │   ├── application.yaml
│       │   └── db/migration/
│       └── test/
└── vehicletracking-frontend/
    ├── package.json
    └── src/
        ├── components/
        ├── components/route/
        ├── services/
        ├── types/
        └── utils/
```

## 5. Quy ước làm việc bắt buộc

Đọc `AGENTS.md` trước khi thay đổi source. Nếu thư mục `docs/` đã được chuyển sang máy mới, đọc thêm `docs/workflow.md` và `docs/design.md`.

Các nguyên tắc chính:

- Trao đổi bằng tiếng Việt nếu người dùng không yêu cầu ngôn ngữ khác.
- Feature nghiệp vụ mới phải có feature ID kế tiếp và bộ tài liệu tương ứng.
- Khi người dùng yêu cầu đủ Requirement → Research → Survey → Spec → Test-Plan → Plan, dừng sau `plan.md`, không implement trong cùng lượt.
- Flyway là source of truth của schema; giữ `ddl-auto=validate`.
- Không sửa migration đã chia sẻ; tạo migration mới.
- API dùng DTO, không trả JPA entity trực tiếp.
- Transaction boundary đặt tại service.
- Frontend HTTP call đặt trong `services`, shared type đặt trong `types`.
- Không đưa secret vào `VITE_*` hoặc source frontend.
- Kiểm tra `git status` trước/sau thay đổi và không ghi đè thay đổi của người khác.
- Không commit/push nếu người dùng chưa yêu cầu.

## 6. Backend hiện tại

Base URL khi chạy local: `http://localhost:8080`.

### 6.1 Station feature

Package: `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/station/`.

API:

| Method | Path | Hành vi |
| --- | --- | --- |
| GET | `/api/v1/stations` | Danh sách station active, theo tên rồi ID |
| GET | `/api/v1/stations/{id}` | Chi tiết station active; 404 nếu không tồn tại/inactive |
| POST | `/api/v1/stations` | Tạo station; trả 201 và `Location` |
| PUT | `/api/v1/stations/{id}` | Cập nhật station active |
| DELETE | `/api/v1/stations/{id}` | Soft-delete bằng `active=false`; trả 204 |

Request create/update:

```json
{
  "name": "Trạm Bến Thành",
  "address": "Quận 1, TP.HCM",
  "latitude": 10.772000,
  "longitude": 106.698000,
  "checkinRadiusMeters": 50
}
```

Validation:

- `name`: bắt buộc, không blank, tối đa 150 ký tự.
- `address`: nullable, tối đa 255 ký tự.
- `latitude`: `[-90, 90]`.
- `longitude`: `[-180, 180]`.
- `checkinRadiusMeters`: số nguyên `[10, 1000]`.
- Service trim name/address; địa chỉ blank được lưu thành `null`.

Station không bị xóa vật lý. API list/detail/update chỉ nhìn thấy station active. Hiện chưa có API liệt kê hoặc khôi phục station inactive.

### 6.2 Route feature

Package: `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/`.

API:

| Method | Path | Hành vi |
| --- | --- | --- |
| GET | `/api/v1/routes` | Danh sách summary, mới nhất trước; không trả polyline |
| GET | `/api/v1/routes/{id}` | Detail gồm metrics, stops và sections; 404 nếu thiếu |
| POST | `/api/v1/routes` | Validate, gọi HERE, lưu snapshot; trả 201 và `Location` |

Request tạo route:

```json
{
  "name": "Tuyến 01",
  "stops": [
    { "stationId": 1, "dwellDurationSeconds": 0 },
    { "stationId": 2, "dwellDurationSeconds": 120 },
    { "stationId": 3, "dwellDurationSeconds": 0 }
  ]
}
```

Quy tắc:

- Tên route bắt buộc, trim, tối đa 150 ký tự.
- Từ 2 đến 50 stops.
- Chỉ station active mới được dùng.
- Hai stops liền nhau không được trùng station; trùng không liền nhau được phép.
- Stop đầu là `START`, stop cuối là `END`, ở giữa là `STOP`.
- START/END bắt buộc dwell `0`.
- STOP cho phép dwell `0..3600` giây.
- Chỉ dùng `transportMode=car`, `routingMode=fast`.
- HERE được yêu cầu trả `polyline,summary,travelSummary`.
- Provider được gọi trước transaction ghi database.
- Chỉ persist sau khi provider trả dữ liệu hợp lệ.
- Route là snapshot bất biến; station name/toạ độ và section geometry được lưu tại thời điểm tính.
- `estimatedTripDuration = estimatedTravelDuration + totalDwellDuration`.

Route detail trả:

- Tổng distance, travel/base/dwell/trip duration.
- Thời điểm departure/calculated/created.
- Ordered stops với role, snapshot, distance/travel từ stop trước.
- `arrivalOffsetSeconds` và `departureOffsetSeconds` lũy kế.
- Ordered sections với flexible polyline và metrics.

Chưa có API edit, rename, recalculate, delete hoặc deactivate route. Khi cần thay đổi stops, hiện phải tạo snapshot route mới.

### 6.3 HERE Routing behavior

Class chính: `route/provider/HereRoutingProvider.java`.

- Nếu routing chưa bật hoặc key rỗng: HTTP 503, code `ROUTING_UNAVAILABLE`.
- Key sai/không được cấp quyền: HTTP 503.
- Provider rate-limit hoặc 5xx: HTTP 503.
- Timeout: HTTP 504.
- Không tìm thấy đường hoặc request tọa độ bị từ chối: HTTP 422.
- Response malformed: HTTP 502/422 tùy nhóm lỗi.
- Không log API key hoặc raw URI chứa key.
- Critical notices và malformed metrics/geometry bị từ chối; không lưu route dở dang.

Route error response dùng RFC Problem Details và bổ sung property `code`.

Các error code hiện có:

```text
ROUTE_VALIDATION_FAILED
ROUTE_STATION_UNAVAILABLE
ROUTE_NOT_FOUND_BY_PROVIDER
ROUTE_NOT_FOUND
ROUTING_UNAVAILABLE
ROUTING_PROVIDER_UNAVAILABLE
ROUTING_PROVIDER_TIMEOUT
ROUTING_PROVIDER_INVALID_RESPONSE
```

### 6.4 CORS và persistence

- CORS áp dụng cho `/api/**`.
- Origins lấy từ `APP_CORS_ALLOWED_ORIGINS`/`CORS_ALLOWED_ORIGINS`.
- Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH.
- Flyway chạy từ `classpath:db/migration` trong schema `vehicle_tracking`.
- Hibernate `ddl-auto=validate`, `open-in-view=false`.

## 7. Database schema

Migration đã commit:

- `V1__create_application_schema.sql`: tạo schema `vehicle_tracking`.
- `V2__create_stations_table.sql`: tạo `stations`.
- `V3__create_routes_tables.sql`: tạo `routes`, `route_stops`, `route_sections`.

### `stations`

Các cột chính: `id`, `name`, `address`, `latitude`, `longitude`, `checkin_radius_meters`, `active`, `created_at`, `updated_at`.

Constraint quan trọng:

- Tên không blank.
- Tọa độ hợp lệ.
- Bán kính `10..1000`.
- Partial index cho station active theo `(name, id)`.

### `routes`

Lưu route metadata, provider, mode, tổng metrics và timestamps. Mode chỉ cho `CAR`, provider chỉ cho `HERE`, metrics không âm.

### `route_stops`

- FK tới route dùng `ON DELETE CASCADE`.
- FK tới station dùng `ON DELETE RESTRICT`.
- Snapshot name/latitude/longitude.
- Sequence dương và unique theo `(route_id, sequence_number)`.
- Dwell `0..3600`.

### `route_sections`

- FK tới route dùng cascade.
- Section sequence unique trong route.
- Flexible polyline không blank.
- Metrics không âm.
- `destination_stop_sequence` tham chiếu `(route_id, sequence_number)` của route stop bằng FK deferred.

Không chuyển dữ liệu PostgreSQL bằng Git. Nếu cần giữ dữ liệu local, tạo dump riêng trước khi chuyển máy.

## 8. Frontend hiện tại

Base URL API lấy từ `VITE_API_BASE_URL`, mặc định `http://localhost:8080`.

### 8.1 Application shell

`App.tsx` sở hữu workspace hiện hành. Ba workspace là `tracking | stations | routes`.

`MapComponent.tsx` sở hữu:

- Leaflet map instance và layer lifecycle.
- Station state và form workflow.
- Selected vehicle/follow state.
- Planned route đang vẽ.
- Toast và map controls.

Map mặc định ở trung tâm TP.HCM. Tile hiện dùng trực tiếp Google tile URLs cho roadmap/satellite/dark; cần review điều khoản sử dụng trước production.

### 8.2 Station UI

Đã có:

- Load station từ backend.
- Loading/error/empty states.
- Tìm theo name/address/coordinate.
- Sort theo tên hoặc bán kính.
- Chọn station từ list hoặc marker.
- Detail drawer.
- Create/edit form.
- Chọn tọa độ bằng click map.
- Lấy tâm map.
- Kéo draft marker.
- Geofence preview theo bán kính.
- Input, slider và preset bán kính.
- Validation realtime.
- Dirty-form confirmation.
- Modal xác nhận soft-delete.
- Toast sau create/update/deactivate.

### 8.3 Route UI

Đã có:

- Route workspace trong top navigation.
- Load/search/list route summaries.
- Retry, loading, error và empty states.
- Form tạo route từ station active.
- Thêm/bỏ/reorder stops không cần thư viện drag-and-drop.
- Hiển thị START/STOP/END và dwell time.
- Submit-disabled trong lúc đang lưu.
- Load detail route khi chọn.
- Hiển thị totals và stop timeline/offsets.
- Decode HERE flexible polyline.
- Vẽ route, numbered stop markers và fit bounds.
- Nếu bất kỳ section polyline nào hỏng, không vẽ route một phần.
- Có request token/AbortController để response cũ không ghi đè selection, drawer, map hoặc saving state mới.

### 8.4 Tracking UI — chỉ là skeleton

Các component/type đã có:

- `TrackingPanel` với counts, filters và search.
- `VehicleDrawer` với metrics, timeline và follow controls.
- Code tạo vehicle marker và auto-pan.
- `Vehicle`/`VehicleStatus` TypeScript types.
- `SimulatorControls` component.

Nhưng runtime hiện tại:

- `vehicles` được khởi tạo cố định là `[]` trong `MapComponent`.
- Không có API vehicle/telemetry.
- Không có MQTT/WebSocket/SSE.
- Không có simulator engine hoặc mock dataset được nối vào app.
- `SimulatorControls` không được import/render.
- Callback `onFitRoute` trong `VehicleDrawer` đang là no-op.
- Header đúng theo source hiện hiển thị `Chờ nguồn telemetry`.

Không được tuyên bố tracking realtime hoặc simulator đã hoàn thành chỉ dựa vào component/type tồn tại.

### 8.5 Traffic — chưa hoàn chỉnh

Frontend có `services/hereTraffic.ts` gọi dự kiến:

- `/api/v1/traffic/flow`
- `/api/v1/traffic/incidents`

Nhưng service này không có consumer hiện tại và backend không có traffic controller/service tương ứng. Backend mới chỉ có properties và conditional `RestClient` cho HERE Traffic.

Các biến `ROUTING_PROVIDER`, `TRAFFIC_PROVIDER`, cache TTL và reroute settings hiện không được source sử dụng.

## 9. Cấu hình môi trường trên máy mới

### 9.1 Nguyên tắc bảo mật

- Không copy API key vào tài liệu, commit, screenshot hoặc chat.
- Không đặt HERE key trong frontend `.env` hay bất kỳ biến `VITE_*` nào.
- API key từng được chia sẻ trong hội thoại nên được xem là đã lộ; hãy rotate/revoke và dùng key mới trên máy mới.
- Base URL routing mà code mong đợi là host gốc `https://router.hereapi.com`; code tự nối `/v8/routes`.
- Spring Boot không tự động đọc file `.env`. Phải `source` file hoặc cấu hình Environment variables trong IDE/Run Configuration.

### 9.2 Mẫu root `.env` sạch

Tạo thủ công file `.env` ở root, không commit:

```dotenv
POSTGRES_DB=vehicle_tracking
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<local-password>
POSTGRES_PORT=5432

DB_URL=jdbc:postgresql://localhost:5432/vehicle_tracking
DB_USERNAME=postgres
DB_PASSWORD=<local-password>

HERE_ROUTING_ENABLED=true
HERE_ROUTING_BASE_URL=https://router.hereapi.com
HERE_ROUTING_CONNECT_TIMEOUT_MS=2000
HERE_ROUTING_READ_TIMEOUT_MS=10000
HERE_API_KEY=<new-here-api-key>

HERE_TRAFFIC_ENABLED=false
HERE_TRAFFIC_BASE_URL=https://data.traffic.hereapi.com
HERE_CONNECT_TIMEOUT_MS=2000
HERE_READ_TIMEOUT_MS=5000

APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

Không nên duy trì đồng thời nhiều `.env` backend có cùng key. Chọn một file làm source of truth và source đúng file đó khi chạy.

### 9.3 Frontend `.env`

Tạo `vehicletracking-frontend/.env`:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

## 10. Khởi động trên máy mới

### 10.1 Clone và chuẩn bị runtime

```bash
git clone https://github.com/quangkhai0302/vehicletracking.git
cd vehicletracking
git checkout master
```

Cài:

- Docker/Compose.
- Java 26.
- Node 24 và npm.

### 10.2 Database

Sau khi tạo root `.env`:

```bash
docker compose up -d postgres pgadmin
docker compose ps
```

Flyway sẽ tự tạo schema/bảng khi backend khởi động.

### 10.3 Backend

```bash
cd /path/to/vehicletracking
set -a
source .env
set +a
cd vehicletracking-backend
./mvnw spring-boot:run
```

Nếu không muốn gọi HERE khi phát triển station/list route, đặt `HERE_ROUTING_ENABLED=false`. Khi false, tạo route trả HTTP 503 nhưng station API và route GET vẫn hoạt động.

### 10.4 Frontend

Ở terminal khác:

```bash
cd /path/to/vehicletracking/vehicletracking-frontend
nvm use 24
npm ci
npm run dev
```

Mặc định Vite chạy ở `http://localhost:5173`.

## 11. Kiểm tra chất lượng

### Backend

Yêu cầu Java 26 và Docker hoạt động:

```bash
cd vehicletracking-backend
./mvnw test
```

Kết quả xác nhận gần nhất ngày 2026-09-13:

- `85` tests.
- `0` failures.
- `0` errors.
- `0` skipped.
- Có station controller/service/repository tests.
- Có route controller/service/provider/config/error/repository integration tests.
- Provider tests dùng fixtures; kết quả này không phải HERE live end-to-end.

Trong sandbox hạn chế attach/Docker, Mockito và Testcontainers có thể thất bại dù source không regression. Chạy lại ở host/CI có Docker.

### Frontend

```bash
cd vehicletracking-frontend
nvm use 24
npm run lint
./node_modules/.bin/tsc --noEmit
npm run build
```

Kết quả xác nhận gần nhất ngày 2026-09-13: cả ba lệnh đều exit `0`; Vite build 1864 modules.

Frontend chưa có automated component/E2E test runner. Các async route race regressions đã từng được kiểm tra bằng browser script tạm trong quá trình review nhưng script đó không nằm trong source đã commit.

## 12. Feature docs local

Nếu chuyển cả thư mục local, `docs/features/` có ba hồ sơ:

- `001-station-management-redesign`: review kết luận Accepted.
- `002-fleet-tracking-dashboard-and-station-ux`: review từng kết luận Verified, nhưng phần tracking/simulator trong tài liệu đã stale so với source hiện tại.
- `003-route-creation-from-stops`: review vòng 6 kết luận Accept with follow-up; các lỗi race response cũ và media evidence đã được xử lý.

Feature 003 có fixtures, screenshots và WebP evidence. Vì `docs/` bị ignore, các tài liệu/artifact này không có trên Git remote hiện tại.

## 13. Phạm vi chưa triển khai

Ưu tiên nghiệp vụ hợp lý tiếp theo:

1. Vehicle và driver master data.
2. Trip/lịch trình và gán route–vehicle–driver.
3. Telemetry ingestion qua HTTP/MQTT và realtime push qua WebSocket/SSE.
4. Nối dữ liệu thật vào TrackingPanel, vehicle marker và VehicleDrawer.
5. Simulator có nhãn rõ để demo khi chưa có GPS thật.
6. Traffic flow/incidents backend proxy và map overlay.
7. ETA realtime, rerouting và cảnh báo trễ/lệch tuyến/quá tốc độ.
8. Automatic geofence check-in/check-out.
9. Route lifecycle: rename/edit/recalculate/deactivate/delete.
10. Authentication, authorization và audit trail.
11. Frontend component/E2E tests.
12. Production deployment, HTTPS, observability và backup.

Không có code hiện tại cho trip, vehicle persistence, driver persistence, authentication hoặc notification.

## 14. Checklist chuyển máy

### Nếu dùng Git

1. Xác nhận source đã push: `git status`, `git log -1`, `git push` khi được phép.
2. Commit/push `PROJECT_HANDOFF.md` nếu muốn nhận tài liệu này qua clone.
3. Quyết định cách lưu `docs/`: thay `.gitignore` và commit các tài liệu không nhạy cảm, hoặc copy riêng.
4. Không force-add bất kỳ `.env` nào.
5. Tạo HERE key mới ở máy đích.
6. Tạo lại root `.env` và frontend `.env` từ mẫu trong tài liệu này.
7. Nếu cần dữ liệu cũ, tạo PostgreSQL dump và chuyển qua kênh an toàn.
8. Chạy đủ backend/frontend verification sau restore.

### Nếu copy nguyên thư mục

- Loại `node_modules/`, `target/`, `dist/` để giảm dung lượng; chúng có thể tạo lại.
- Có thể copy `docs/` vì đây là tri thức dự án.
- Không gửi `.env` qua kênh không an toàn.
- Không cần copy PostgreSQL volume thô giữa máy; ưu tiên `pg_dump`/`pg_restore`.

Ví dụ dump dữ liệu, sau khi đã nạp biến môi trường phù hợp:

```bash
docker compose exec -T postgres pg_dump -U postgres -d vehicle_tracking -Fc > vehicle_tracking.dump
```

Khôi phục trên máy mới sau khi PostgreSQL đã chạy:

```bash
docker compose exec -T postgres pg_restore -U postgres -d vehicle_tracking --clean --if-exists < vehicle_tracking.dump
```

Kiểm tra kỹ target database trước khi dùng `--clean` vì thao tác này xóa object hiện có trong database đích.

## 15. Những điều không nên suy luận

- Component `VehicleDrawer` tồn tại không có nghĩa vehicle tracking đã hoạt động.
- `SimulatorControls` tồn tại không có nghĩa simulator đã được nối.
- `hereTraffic.ts` tồn tại không có nghĩa backend traffic endpoint đã có.
- Route duration hiện là snapshot theo thời điểm gọi HERE, không phải ETA realtime của xe đang chạy.
- `checkin_radius_meters` mới chỉ được dùng để hiển thị geofence, chưa có automatic check-in.
- Frontend build pass không chứng minh HERE live, responsive toàn bộ hoặc các race case đều đúng.
- `.env` hiện diện trên máy không có nghĩa Spring Boot đã nạp nó.

## 16. Điểm bắt đầu cho người/agent tiếp theo

Trước khi sửa:

1. Đọc `AGENTS.md`.
2. Chạy `git status --short` và không ghi đè thay đổi có sẵn.
3. Đọc source trực tiếp của feature liên quan; không dùng walkthrough cũ thay cho evidence.
4. Nếu có `docs/`, đọc `docs/workflow.md`, `docs/design.md` và feature spec/review tương ứng.
5. Xác nhận runtime Java 26, Node 24 và Docker.
6. Không đọc/in API key từ `.env` vào log hoặc câu trả lời.
7. Sau thay đổi, chạy test/build phù hợp và ghi rõ lệnh/kết quả thực tế.

