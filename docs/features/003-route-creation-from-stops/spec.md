# Spec: Tạo tuyến đường từ danh sách điểm dừng

## 1. Trạng thái và nguyên tắc

- **Feature:** 003-route-creation-from-stops
- **Trạng thái:** Ready for Review
- **Implementation owner sau approval:** Gemini
- Route trong feature này là **snapshot bất biến**.
- Station là địa điểm vật lý; START/STOP/END là vai trò theo vị trí trong route, không thêm role vào bảng station.
- Thứ tự stop do người dùng cung cấp; hệ thống không tự tối ưu.
- Không có fallback đường chim bay khi HERE thất bại.

## 2. Kiến trúc và luồng dữ liệu

```text
RouteWorkspace / RouteDrawer
        │
        ├── GET /api/v1/stations
        ├── GET /api/v1/routes
        ├── GET /api/v1/routes/{id}
        └── POST /api/v1/routes
                       │
                RouteController
                       │
                  RouteService
                ┌──────┴─────────┐
                │                │
        StationRepository   RoutingProvider
                │                │
          active stations   HereRoutingProvider
                                 │
                         HERE Routing API v8
                                 │
                    normalized route result
                                 │
                       transactional persist
                                 │
       RouteRepository → routes/route_stops/route_sections
                                 │
                         RouteDetailResponse
                                 │
             decode Flexible Polyline → Leaflet layers
```

Provider call phải hoàn tất trước transaction ghi route. Không giữ transaction/database connection trong thời gian chờ HERE.

## 3. Database contract

Tạo migration mới:

```text
vehicletracking-backend/src/main/resources/db/migration/
V3__create_routes_tables.sql
```

Không sửa `V1__create_application_schema.sql` hoặc `V2__create_stations_table.sql`.

### 3.1 Bảng `vehicle_tracking.routes`

| Cột | Kiểu | Null/default | Ràng buộc | Nhiệm vụ |
|---|---|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | NOT NULL | PK | ID nội bộ ổn định của route. |
| `name` | `VARCHAR(150)` | NOT NULL | `LENGTH(TRIM(name)) > 0` | Tên do người vận hành nhập; service trim trước khi lưu. Không unique vì nghiệp vụ chưa quy định tên duy nhất. |
| `transport_mode` | `VARCHAR(20)` | NOT NULL | `CHECK (transport_mode IN ('CAR'))` | Transport mode đã dùng khi tính snapshot. Feature đầu chỉ hỗ trợ CAR. |
| `routing_provider` | `VARCHAR(20)` | NOT NULL | `CHECK (routing_provider IN ('HERE'))` | Ghi nguồn geometry/duration, tránh hiểu dữ liệu là tự tính. |
| `total_distance_meters` | `BIGINT` | NOT NULL | `CHECK >= 0` | Tổng length của tất cả route sections. |
| `estimated_travel_duration_seconds` | `BIGINT` | NOT NULL | `CHECK >= 0` | Tổng dynamic travel duration có time-aware traffic, không gồm dwell. |
| `base_travel_duration_seconds` | `BIGINT` | NOT NULL | `CHECK >= 0` | Tổng travel duration không time-aware; section thiếu base duration dùng dynamic duration. |
| `total_dwell_duration_seconds` | `BIGINT` | NOT NULL | `CHECK >= 0` | Tổng dwell của tất cả STOP trung gian. |
| `estimated_trip_duration_seconds` | `BIGINT` | NOT NULL | `CHECK >= estimated_travel_duration_seconds` | Thời gian hoàn thành dự kiến = travel + dwell. |
| `estimated_departure_at` | `TIMESTAMPTZ` | NOT NULL | — | Thời điểm khởi hành mà HERE dùng/trả về cho snapshot. |
| `calculated_at` | `TIMESTAMPTZ` | NOT NULL | — | Thời điểm backend hoàn tất tính route; giúp người dùng biết estimate cũ hay mới. |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, default `CURRENT_TIMESTAMP` | — | Thời điểm snapshot được lưu. |

Index:

- `idx_routes_created_at` trên `(created_at DESC, id DESC)` phục vụ danh sách mới nhất trước.

Invariant do service bảo đảm:

```text
estimated_trip_duration_seconds
= estimated_travel_duration_seconds + total_dwell_duration_seconds
```

### 3.2 Bảng `vehicle_tracking.route_stops`

| Cột | Kiểu | Null/default | Ràng buộc | Nhiệm vụ |
|---|---|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | NOT NULL | PK | ID row route stop. |
| `route_id` | `BIGINT` | NOT NULL | FK → `routes(id)` ON DELETE CASCADE | Route sở hữu stop. Cascade chỉ bảo vệ thao tác quản trị DB; feature không có delete API. |
| `station_id` | `BIGINT` | NOT NULL | FK → `stations(id)` ON DELETE RESTRICT | Liên kết station gốc. Station đang dùng soft-delete nên route lịch sử không mất. |
| `sequence_number` | `INTEGER` | NOT NULL | `CHECK >= 1`, UNIQUE `(route_id, sequence_number)` | Thứ tự 1-based. Role được suy ra từ giá trị này và số stop. |
| `station_name_snapshot` | `VARCHAR(150)` | NOT NULL | non-blank | Tên station tại lúc tính route để route detail ổn định khi station đổi tên. |
| `latitude_snapshot` | `NUMERIC(8,6)` | NOT NULL | `CHECK BETWEEN -90 AND 90` | Vĩ độ đã gửi tới provider. |
| `longitude_snapshot` | `NUMERIC(9,6)` | NOT NULL | `CHECK BETWEEN -180 AND 180` | Kinh độ đã gửi tới provider. |
| `dwell_duration_seconds` | `INTEGER` | NOT NULL, default 0 | `CHECK BETWEEN 0 AND 3600` | Thời gian dừng dự kiến. Service ép START/END bằng 0. |

Index/constraint:

- Unique `(route_id, sequence_number)` đồng thời là target của composite foreign key từ route section.
- `idx_route_stops_station_id` trên `(station_id)` để tìm route tham chiếu station khi cần.
- Không unique `(route_id, station_id)` vì tuyến vòng có thể đi qua station lại lần nữa.

Không lưu cột `role`: `sequence=1` là START, `sequence=max` là END, còn lại là STOP. Cách này tránh role mâu thuẫn với thứ tự.

### 3.3 Bảng `vehicle_tracking.route_sections`

| Cột | Kiểu | Null/default | Ràng buộc | Nhiệm vụ |
|---|---|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | NOT NULL | PK | ID section snapshot. |
| `route_id` | `BIGINT` | NOT NULL | FK → `routes(id)` ON DELETE CASCADE | Route sở hữu section. |
| `section_sequence` | `INTEGER` | NOT NULL | `CHECK >= 1`, UNIQUE `(route_id, section_sequence)` | Thứ tự section toàn route đúng như HERE response. |
| `destination_stop_sequence` | `INTEGER` | NOT NULL | `CHECK >= 2`; composite FK `(route_id, destination_stop_sequence)` → `route_stops(route_id, sequence_number)` ON DELETE CASCADE | Stop mà section này đang tiến tới. Nhiều sections có thể cùng một destination stop. |
| `encoded_polyline` | `TEXT` | NOT NULL | non-blank | Flexible Polyline của section để frontend/simulator dùng. Không lưu route handle. |
| `distance_meters` | `BIGINT` | NOT NULL | `CHECK >= 0` | Travel length của section. |
| `travel_duration_seconds` | `BIGINT` | NOT NULL | `CHECK >= 0` | Dynamic/time-aware travel duration, không gồm dwell. |
| `base_travel_duration_seconds` | `BIGINT` | NOT NULL | `CHECK >= 0` | Base duration; fallback dynamic duration khi HERE không trả base. |

Index:

- `idx_route_sections_route_destination` trên `(route_id, destination_stop_sequence, section_sequence)` để tổng hợp ETA đến stop.

## 4. JPA/domain contract

Package mới:

```text
com.quangkhai.vehicletracking_backend.route
├── controller
├── dto
├── entity
├── repository
├── service
└── provider
```

### 4.1 Entity

- `RouteEntity`: sở hữu ordered collections `RouteStopEntity` và `RouteSectionEntity`; cascade persist; không dùng `@Data`.
- `RouteStopEntity`: `@ManyToOne(fetch = LAZY)` tới route và station, đồng thời giữ station snapshots/dwell.
- `RouteSectionEntity`: `@ManyToOne(fetch = LAZY)` tới route; giữ normalized HERE section.
- Dùng `@Getter`, protected no-args constructor và factory/constructor có kiểm soát.
- Không sinh public setter toàn entity.
- API mapping thực hiện trong service/mapper khi transaction đọc còn mở; không trả entity.

### 4.2 Enum/value types

- `RouteTransportMode.CAR` ↔ database `CAR` ↔ HERE `car`.
- `RoutingProviderName.HERE` ↔ database `HERE`.
- `RouteStopRole` chỉ dùng trong response: `START`, `STOP`, `END`.
- `RoutingWaypoint`: station ID, coordinate, dwell duration, sequence.
- `CalculatedRoute`: departure time và ordered normalized sections.
- `CalculatedSection`: section sequence, destination stop sequence, polyline, distance, travel/base duration.

### 4.3 Repository

- `RouteRepository extends JpaRepository<RouteEntity, Long>`.
- Query list summary theo `createdAt DESC, id DESC` không fetch polyline nếu có thể dùng projection.
- Query detail dùng `@EntityGraph` hoặc explicit fetch strategy để lấy stops/sections có thứ tự, tránh N+1.
- Bổ sung station batch query chỉ lấy active stations theo tập ID; service dựng lại đúng thứ tự request và phát hiện ID thiếu/inactive.

## 5. Routing provider contract

### 5.1 Interface

```text
RoutingProvider.calculate(List<RoutingWaypoint>) -> CalculatedRoute
```

Interface không phụ thuộc HTTP hoặc HERE DTO. `RouteService` chỉ biết normalized contract để unit test không cần network.

### 5.2 HERE request

- Base URL mặc định: `https://router.hereapi.com`.
- Path: `/v8/routes`.
- Query:
  - `transportMode=car`
  - `routingMode=fast`
  - `origin={lat},{lng}` từ stop 1
  - một `via={lat},{lng}!stopDuration={seconds}` cho từng stop giữa; bỏ option nếu dwell bằng 0
  - `destination={lat},{lng}` từ stop cuối
  - `return=polyline,summary,travelSummary`
  - `apiKey` chỉ gắn trong backend client
- Không yêu cầu alternatives, instructions, incidents hoặc route handle.
- Không dùng `departureTime=any`; HERE dùng current request time và traffic mặc định.

### 5.3 Mapping sections

1. Bắt đầu với `destinationStopSequence=2`.
2. Duyệt HERE sections theo thứ tự, gán section vào destination stop hiện tại.
3. Nếu `arrival.place.waypoint=k`, yêu cầu `k == destinationStopSequence - 2`, sau đó tăng destination stop lên 1.
4. Section cuối không có via waypoint được gán tới END.
5. Một destination stop được phép có nhiều sections.
6. Thiếu waypoint boundary, sai thứ tự, routes rỗng, polyline/summary thiếu hoặc critical notice làm response bị coi là malformed/no-route; không persist.
7. Base duration thiếu thì dùng travel duration cho section đó.

### 5.4 Configuration

Thêm cấu hình riêng:

```yaml
here:
  routing:
    enabled: ${HERE_ROUTING_ENABLED:false}
    base-url: ${HERE_ROUTING_BASE_URL:https://router.hereapi.com}
    api-key: ${HERE_API_KEY:}
    connect-timeout-ms: ${HERE_ROUTING_CONNECT_TIMEOUT_MS:2000}
    read-timeout-ms: ${HERE_ROUTING_READ_TIMEOUT_MS:10000}
```

Quy tắc:

- Khi `enabled=false`, application vẫn startup; POST route trả 503.
- Khi `enabled=true`, API key bắt buộc non-blank và timeout phải dương.
- Có named `RestClient` riêng cho routing; không dùng nhầm Traffic base URL/client.
- `toString`, log và exception không chứa API key hoặc full URI có query key.
- Test dùng key giả và mocked HTTP; không gọi HERE thật.

## 6. API contract

Base path: `/api/v1/routes`.

Response lỗi dùng `application/problem+json` và thêm `code` ổn định khi lỗi route/provider cần frontend phân biệt. Không trả raw HERE body.

### 6.1 POST `/api/v1/routes`

Request:

```json
{
  "name": "Tuyến Miền Đông - Bến Thành",
  "stops": [
    { "stationId": 1, "dwellDurationSeconds": 0 },
    { "stationId": 3, "dwellDurationSeconds": 120 },
    { "stationId": 7, "dwellDurationSeconds": 0 }
  ]
}
```

Bean validation:

- `name`: `@NotBlank`, max 150.
- `stops`: non-null, size 2–50, mỗi item `@Valid`.
- `stationId`: non-null, positive.
- `dwellDurationSeconds`: non-null, 0–3600.

Business validation:

- START/END dwell phải 0.
- Không có hai station IDs bằng nhau ở hai vị trí liên tiếp.
- Tất cả IDs phải là station active.

Success:

- Status `201 Created`.
- Header `Location: /api/v1/routes/{id}`.
- Body là `RouteDetailResponse`.

### 6.2 GET `/api/v1/routes`

- Status `200 OK`.
- Sort mới nhất trước theo `createdAt DESC, id DESC`.
- Trả `RouteSummaryResponse[]`; không trả sections/polyline.

Summary shape:

```json
{
  "id": 12,
  "name": "Tuyến Miền Đông - Bến Thành",
  "transportMode": "CAR",
  "routingProvider": "HERE",
  "startStationName": "Bến xe Miền Đông",
  "endStationName": "Chợ Bến Thành",
  "stopCount": 3,
  "totalDistanceMeters": 8200,
  "estimatedTravelDurationSeconds": 1260,
  "totalDwellDurationSeconds": 120,
  "estimatedTripDurationSeconds": 1380,
  "calculatedAt": "2026-09-11T04:30:00Z",
  "createdAt": "2026-09-11T04:30:01Z"
}
```

### 6.3 GET `/api/v1/routes/{id}`

- `200 OK` với `RouteDetailResponse`.
- `404 Not Found` khi route không tồn tại.

Detail shape:

```json
{
  "id": 12,
  "name": "Tuyến Miền Đông - Bến Thành",
  "transportMode": "CAR",
  "routingProvider": "HERE",
  "totalDistanceMeters": 8200,
  "estimatedTravelDurationSeconds": 1260,
  "baseTravelDurationSeconds": 1050,
  "totalDwellDurationSeconds": 120,
  "estimatedTripDurationSeconds": 1380,
  "estimatedDepartureAt": "2026-09-11T11:30:00+07:00",
  "calculatedAt": "2026-09-11T04:30:00Z",
  "createdAt": "2026-09-11T04:30:01Z",
  "stops": [
    {
      "sequenceNumber": 1,
      "role": "START",
      "stationId": 1,
      "stationName": "Bến xe Miền Đông",
      "latitude": 10.801234,
      "longitude": 106.710123,
      "dwellDurationSeconds": 0,
      "distanceFromPreviousMeters": 0,
      "travelDurationFromPreviousSeconds": 0,
      "arrivalOffsetSeconds": 0,
      "departureOffsetSeconds": 0
    },
    {
      "sequenceNumber": 2,
      "role": "STOP",
      "stationId": 3,
      "stationName": "Hàng Xanh",
      "latitude": 10.800100,
      "longitude": 106.711100,
      "dwellDurationSeconds": 120,
      "distanceFromPreviousMeters": 3100,
      "travelDurationFromPreviousSeconds": 480,
      "arrivalOffsetSeconds": 480,
      "departureOffsetSeconds": 600
    }
  ],
  "sections": [
    {
      "sectionSequence": 1,
      "destinationStopSequence": 2,
      "encodedPolyline": "BFoz5xJ67i1B1B7PzIhaxL7Y",
      "distanceMeters": 3100,
      "travelDurationSeconds": 480,
      "baseTravelDurationSeconds": 410
    }
  ]
}
```

`arrivalOffsetSeconds`/`departureOffsetSeconds` được service tính từ normalized sections và dwell; không cần lưu thêm cột lũy kế.

### 6.4 Error mapping

| Trường hợp | HTTP | `code` đề xuất |
|---|---:|---|
| Bean/business validation sai | 400 | `ROUTE_VALIDATION_FAILED` |
| Station thiếu hoặc inactive | 422 | `ROUTE_STATION_UNAVAILABLE` |
| HERE không tìm được route / critical route notice | 422 | `ROUTE_NOT_FOUND_BY_PROVIDER` |
| Route ID không tồn tại | 404 | `ROUTE_NOT_FOUND` |
| Routing disabled hoặc thiếu config khi enabled | 503 | `ROUTING_UNAVAILABLE` |
| Provider rate limited/unavailable | 503 | `ROUTING_PROVIDER_UNAVAILABLE` |
| Provider timeout | 504 | `ROUTING_PROVIDER_TIMEOUT` |
| Provider response malformed/không ánh xạ được | 502 | `ROUTING_PROVIDER_INVALID_RESPONSE` |

## 7. Service/transaction contract

Luồng `RouteService.create`:

1. Normalize và validate request thuần.
2. Batch load station active; map theo ID, dựng lại theo đúng input order.
3. Tạo immutable waypoint snapshots.
4. Gọi `RoutingProvider` ngoài transaction ghi.
5. Validate normalized result và tính totals/offsets.
6. Gọi một persistence boundary có `@Transactional` để persist route + stops + sections bằng cascade.
7. Map entity vừa lưu thành detail response.

Nếu bước 1–5 lỗi, không mở transaction ghi. Nếu bước 6 lỗi, toàn bộ ba bảng rollback.

## 8. Frontend contract

### 8.1 Files và ownership

```text
src/
├── components/route/
│   ├── RouteWorkspace.tsx   # sở hữu list/create/select state
│   ├── RoutePanel.tsx       # list + empty/loading/error
│   ├── RouteDrawer.tsx      # browse/create form
│   └── route.css            # style riêng của feature
├── services/routes.ts       # chỉ gọi backend
├── types/route.ts
├── types/workspace.ts       # thêm 'routes'
├── App.tsx                  # thêm navigation
└── components/MapComponent.tsx
```

Không chuyển toàn bộ station/tracking sang cấu trúc mới trong feature này.

### 8.2 Route workspace

- Navigation có mục “Tuyến đường”.
- Left panel hiển thị route summary compact, search theo tên, selected state và nút “Tạo tuyến”.
- Empty state hướng dẫn cần tạo ít nhất hai station trước.
- Chọn route gọi detail endpoint nếu chưa có detail, vẽ route và mở drawer.

### 8.3 Route builder

- Tên route có label và inline validation.
- Danh sách station active cho phép tìm kiếm và thêm vào stop list.
- Stop list đánh số 1-based và badge START/STOP/END tự cập nhật sau mọi reorder/remove.
- Mỗi row có nút lên, xuống và bỏ; disable khi thao tác không hợp lệ.
- STOP có numeric dwell input 0–3600 giây; START/END hiển thị 0 và disabled.
- Không thêm dependency drag-and-drop; keyboard sử dụng được.
- Submit disabled khi thiếu tên, ít hơn 2 stop, invalid dwell hoặc đang gửi.
- Khi submit, UI giữ form và hiển thị lỗi nếu API/provider fail.
- Khi success, list thêm route, route mới được chọn, drawer chuyển browse và map fit route.

### 8.4 Route detail

- Hiển thị tên, START → END, tổng số stop.
- Hiển thị distance, travel duration, dwell duration và trip duration bằng format dễ đọc.
- Hiển thị thời điểm `calculatedAt` để không gây hiểu nhầm là ETA realtime.
- Timeline stop hiển thị role, station snapshot name, dwell, distance/time từ stop trước và arrival offset.
- Không hiển thị raw enum nếu có thể dùng nhãn tiếng Việt.

### 8.5 Map rendering

- Tách `vehicleRouteLayerRef` và `plannedRouteLayerRef`.
- Khi workspace không phải routes hoặc không chọn route, clear planned route layer.
- Decode mọi `sections[].encodedPolyline` bằng utility hiện có và vẽ theo `sectionSequence`.
- Route chính dùng solid cyan/blue line theo `docs/design.md`; không nối thẳng station khi decode/provider data lỗi.
- Vẽ stop marker/label có sequence; START/END khác biệt vừa đủ, không che bản đồ.
- `fitBounds` chỉ chạy khi người dùng chọn route hoặc route mới được tạo, không chạy lại ở mọi render.
- Leaflet layers/listeners phải cleanup.
- Trong workspace routes, click station marker không được mở station drawer. Có thể chỉ center/tooltip; thêm stop bằng route builder là luồng chính.

## 9. Trạng thái và edge cases

- Danh sách station rỗng hoặc chỉ có một station: nút create vẫn có thể mở nhưng submit disabled và có hướng dẫn tạo thêm station.
- Station lặp ở vị trí không liền kề: hợp lệ.
- START = END cho tuyến vòng: hợp lệ nếu có ít nhất một stop khác ở giữa.
- Hai stop liên tiếp giống nhau: HTTP 400.
- Station bị deactivate giữa lúc UI tải và submit: HTTP 422; không persist.
- HERE snap waypoint sang đường gần đó: route lưu coordinate station snapshot và polyline HERE; UI thể hiện stop snapshot, không tự sửa station.
- Section polyline decode rỗng: không vẽ fallback; hiển thị lỗi dữ liệu route.
- Route detail cũ vẫn đọc được khi HERE disabled hoặc offline.
- Tạo route đồng thời với cùng payload: được phép tạo hai snapshot; feature chưa có idempotency/unique business key.

## 10. Compatibility và security

- Không thay đổi station API contract.
- Không sửa traffic config hiện có ngoài phần thật sự cần để tránh bean name conflict.
- Không đưa HERE key vào request/response nội bộ.
- Không log URI sau khi gắn query `apiKey`.
- `here.routing.enabled=false` là default an toàn cho developer không dùng route creation.
- Frontend chỉ biết `VITE_API_BASE_URL`; không thêm `VITE_HERE_API_KEY`.

## 11. Acceptance mapping

Các acceptance criteria chính thức là AC-01 đến AC-15 trong `requirement.md`. `test-plan.md` phải ánh xạ từng AC tới ít nhất một kiểm tra tự động hoặc manual có evidence.
