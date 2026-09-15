# Survey: Trạng thái repository trước feature tạo tuyến

## 1. Phạm vi khảo sát

Khảo sát source thực tế ngày 11/09/2026 ở cả backend và frontend. Walkthrough/review cũ không được dùng làm bằng chứng implementation.

Các khu vực đã đọc:

```text
vehicletracking-backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/quangkhai/vehicletracking_backend/
    │   │   ├── VehicletrackingBackendApplication.java
    │   │   ├── config/
    │   │   │   ├── HereTrafficProperties.java
    │   │   │   ├── HttpClientConfig.java
    │   │   │   └── WebConfig.java
    │   │   └── station/
    │   │       ├── controller/StationController.java
    │   │       ├── dto/{StationResponse,StationUpsertRequest}.java
    │   │       ├── entity/StationEntity.java
    │   │       ├── repository/StationRepository.java
    │   │       └── service/StationService.java
    │   └── resources/
    │       ├── application.yaml
    │       └── db/migration/{V1__create_application_schema,V2__create_stations_table}.sql
    └── test/
        ├── java/.../station/{controller,repository,service}/...
        └── resources/...

vehicletracking-frontend/src/
├── App.tsx
├── components/
│   ├── MapComponent.tsx
│   ├── StationDrawer.tsx
│   ├── StationPanel.tsx
│   ├── TrackingPanel.tsx
│   └── VehicleDrawer.tsx
├── services/{stations,hereTraffic,polyline}.ts
├── types/{station,vehicle,workspace,map}.ts
└── index.css
```

## 2. Trạng thái chức năng hiện có

### 2.1 Backend

- Schema hiện chỉ có application schema và bảng `stations`; chưa có migration route.
- Station dùng Spring Data JPA và soft-delete bằng `active=false`.
- API station cung cấp list/detail/create/update/delete dưới `/api/v1/stations`.
- Route mới chỉ được phép dùng station active; repository đã có `findByIdAndActiveTrue` nhưng chưa có batch query cho danh sách ID.
- `StationEntity` chứa WGS84 latitude/longitude đủ làm waypoint input.
- JPA đang dùng `ddl-auto=validate`, vì vậy schema route bắt buộc đi qua migration trước entity.
- Có cấu hình HERE Traffic conditional, nhưng base URL là Traffic API và không thể tái sử dụng như Routing API client.
- Không có package `route` hoặc routing provider trong `src/main`.

### 2.2 Frontend

- App chỉ có hai workspace: `tracking` và `stations`.
- `MapComponent` khởi tạo một Leaflet map duy nhất và đã có các layer ref cho station, draft, vehicle và route.
- `routeLayerRef` hiện thuộc effect hiển thị vehicle/tracking và bị clear ở mỗi lần effect chạy; chưa thể dùng chung an toàn cho planned route workspace.
- Station data đã được tải từ backend và có thể tái sử dụng làm danh sách chọn stop.
- Có sẵn flexible polyline decoder trả tọa độ `[latitude, longitude]` cho Leaflet.
- Không có type, API service, state hoặc component quản lý route.
- Frontend có service gọi traffic proxy nhưng backend hiện không có `/api/v1/traffic/*`; service này không chứng minh routing đã tồn tại.
- Không có test runner frontend; kiểm tra hiện có là lint, TypeScript và Vite build.

## 3. Luồng dữ liệu hiện tại

### 3.1 Station

```text
StationDrawer / StationPanel
→ MapComponent giữ state
→ services/stations.ts
→ /api/v1/stations
→ StationController
→ StationService
→ StationRepository
→ vehicle_tracking.stations
```

### 3.2 Route

```text
Chưa tồn tại UI/API/database/provider flow.

routeLayerRef hiện chỉ được clear/chuẩn bị cho vehicle tracking,
không có route geometry thật được nạp vào state.
```

## 4. Bảng evidence

| Nhận định về repository | Evidence source thực tế | Ý nghĩa cho feature 003 |
|---|---|---|
| Backend dùng Java 26 và Spring Boot 4.1.1 | `vehicletracking-backend/pom.xml:7-12,20-22` | Gemini phải chạy test bằng JDK 26; không hạ Java version trong feature route. |
| JPA, Flyway và PostgreSQL đã có dependency | `vehicletracking-backend/pom.xml:46-67` | Không cần thêm persistence framework/dependency mới. |
| Schema do Flyway quản lý và Hibernate chỉ validate | `vehicletracking-backend/src/main/resources/application.yaml:9-20` | Tạo V3, không sửa V1/V2 và không dùng Hibernate auto-update. |
| Migration hiện dừng ở V2 và chỉ tạo stations | `vehicletracking-backend/src/main/resources/db/migration/V1__create_application_schema.sql`; `V2__create_stations_table.sql:1-20` | `routes`, `route_stops`, `route_sections` chưa tồn tại. |
| Station có latitude/longitude với precision phù hợp | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/station/entity/StationEntity.java:33-40` | Có thể dùng làm WGS84 waypoint và snapshot vào route stop. |
| Station bị ngừng sử dụng bằng soft-delete | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/station/entity/StationEntity.java:42-43,87-90`; `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/station/service/StationService.java:60-63` | Existing route vẫn tham chiếu station row; route mới chỉ nhận active station. |
| Repository chỉ đọc station active từng ID hoặc toàn bộ | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/station/repository/StationRepository.java:9-13` | Cần batch lookup active stations để tránh N query khi tạo route. |
| Controller station dùng DTO, validation và REST v1 | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/station/controller/StationController.java:20-60` | Route API nên giữ cùng style `/api/v1/routes`, DTO và `@Valid`. |
| Service station đặt transaction boundary và không trả entity | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/station/service/StationService.java:23-63`; `station/dto/StationResponse.java:8-31` | Route phải theo cùng quy ước và tách provider call khỏi transaction ghi kéo dài. |
| HERE Traffic đang optional và tắt mặc định | `vehicletracking-backend/src/main/resources/application.yaml:25-31`; `HereTrafficProperties.java:8-23`; `HttpClientConfig.java:11-24` | Routing cần config riêng, cũng không được làm station startup phụ thuộc HERE key. |
| Application scan configuration properties toàn package | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/VehicletrackingBackendApplication.java:7-8` | `HereRoutingProperties` có thể được scan nhưng validation phải cho phép disabled/no-key. |
| Workspace type chỉ có tracking/stations | `vehicletracking-frontend/src/types/workspace.ts:1` | Phải bổ sung `routes` và navigation tương ứng. |
| Header chỉ render hai workspace buttons | `vehicletracking-frontend/src/App.tsx:25-40` | Route workspace chưa có entry point. |
| Map được giữ trong ref và cleanup đúng | `vehicletracking-frontend/src/components/MapComponent.tsx:72-82,144-210` | Có thể mở rộng layer mà không recreate map instance. |
| Route layer hiện bị vehicle effect quản lý | `vehicletracking-frontend/src/components/MapComponent.tsx:333-377` | Cần tách vehicle route layer và planned route layer để tránh clear lẫn nhau. |
| Station được fetch một lần vào MapComponent | `vehicletracking-frontend/src/components/MapComponent.tsx:84-96,117-135` | Route builder có thể nhận collection station active hiện có, không gọi HERE/frontend. |
| Map marker hiện chỉ phân nhánh tracking và non-tracking | `vehicletracking-frontend/src/components/MapComponent.tsx:243-284` | Khi thêm `routes`, phải xử lý explicit để không kích hoạt station browse/form sai workspace. |
| Flexible Polyline decoder đã có | `vehicletracking-frontend/src/services/polyline.ts:1-109` | Tái sử dụng để vẽ HERE section, không thêm package decode mới nếu kiểm tra fixture đạt. |
| Frontend API base URL đã dùng `VITE_API_BASE_URL` | `vehicletracking-frontend/src/services/stations.ts:1-4` | Route service chỉ gọi backend; không thêm `VITE_HERE_*`. |
| Root `.env.example` đã có tên biến `HERE_API_KEY` cho backend, frontend example chỉ có `VITE_API_BASE_URL` | `.env.example` (section HERE Traffic); `vehicletracking-frontend/.env.example` | Feature chỉ bổ sung tên biến routing backend vào root example, không tạo HERE variable ở frontend. |
| Không có frontend test script | `vehicletracking-frontend/package.json:9-13,21-28` | Plan không tự thêm test framework; dùng lint/tsc/build và manual map verification. |
| Design yêu cầu map + ordered route stop list, role, dwell, distance, duration và đường thật | `docs/design.md:351-381` | Spec UI/data phải bao phủ đầy đủ các trường này. |
| `docs/` đang bị Git ignore | `.gitignore:10` | Tài liệu feature được tạo trên filesystem nhưng sẽ không xuất hiện trong Git cho đến khi quy tắc ignore được người dùng xử lý. |

## 5. Thành phần có thể tái sử dụng

- `StationEntity` và station repository cho waypoint source.
- Cách tổ chức package `controller/dto/entity/repository/service` của station.
- Spring `RestClient` và timeout pattern từ cấu hình traffic, nhưng route phải có named client/config riêng.
- `VITE_API_BASE_URL` convention từ `services/stations.ts`.
- `decodeFlexiblePolyline()` cho section geometry.
- Leaflet map instance/layer lifecycle và `fitBounds` API hiện có thể bổ sung mà không recreate map.
- Semantic CSS/design direction từ `docs/design.md`.

## 6. Khoảng trống cần triển khai

1. V3 migration và toàn bộ JPA model route.
2. Routing provider boundary, HERE request/response mapping và error model.
3. Route create/list/detail service và controller.
4. Batch station validation giữ đúng input order.
5. Frontend route types/API service.
6. Route workspace, ordered stop builder, dwell input và route detail.
7. Planned route layer, numbered stop markers và fit bounds.
8. Backend unit/controller/integration/provider tests.
9. Evidence, walkthrough và review sau implementation.

## 7. Rủi ro hồi quy

- Thêm `routes` vào `WorkspaceMode` nhưng không sửa toàn bộ branch có thể làm map click mở station state ngoài ý muốn.
- Dùng lại `routeLayerRef` có thể khiến vehicle route và planned route xóa geometry của nhau.
- Nếu gọi HERE trong một transaction DB dài, connection bị giữ trong lúc chờ network.
- Nếu dùng `findAllById`, station inactive vẫn có thể lọt vào route.
- Nếu mapping section chỉ dựa trên số lượng stop, implicit sections làm sai ETA/geometry theo stop.
- Nếu chỉ lưu station ID, việc sửa tọa độ station sau đó làm marker không còn khớp route snapshot.
- Nếu thêm API key vào `VITE_*`, secret sẽ xuất hiện trong browser bundle.
- Working tree tracked/unignored đang sạch tại thời điểm survey; Gemini vẫn phải kiểm tra lại trước implementation vì trạng thái có thể thay đổi sau planning gate.

## 8. Baseline verification ngày 11/09/2026

| Kiểm tra | Kết quả |
|---|---|
| Frontend `npm run lint` bằng Node 24.16.0 | PASS, exit 0 |
| Frontend `tsc --noEmit` bằng Node 24.16.0 | PASS, exit 0 |
| Frontend `npm run build` bằng Node 24.16.0 | PASS, exit 0 |
| Backend `./mvnw test` | Không chạy được test: Maven dùng Java 17 nhưng bytecode/project yêu cầu Java 26; Surefire dừng trước test, exit 1 |

Baseline backend là giới hạn môi trường, không phải bằng chứng test nghiệp vụ đang fail. Implementation chỉ được đánh dấu Verified sau khi chạy bằng JDK 26.

## 9. Working tree tại thời điểm survey

`git status --short` không trả dòng nào tại thời điểm survey, nghĩa là các file tracked/unignored đang sạch. Tuy nhiên `.gitignore:10` đang ignore toàn bộ `docs/`, nên sáu planning documents mới không xuất hiện trong `git status`. Planning này chỉ thêm tài liệu trong `docs/features/003-route-creation-from-stops/`, không sửa source.
