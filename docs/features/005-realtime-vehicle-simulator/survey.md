# Survey repository — 005-realtime-vehicle-simulator

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `005-realtime-vehicle-simulator` |
| Requirement liên quan | `REQ-001..REQ-005` |
| Trạng thái | `READY — chờ người dùng review Plan trước Gemini` |
| Người khảo sát | Codex |
| Commit/worktree được khảo sát | `f81419d`; `git status --short` không có output trước khi tạo thư mục feature 005 |
| Ngày khảo sát | 2026-09-04 |

## Tổng quan repository

Repository có backend `vehiceltracking-backend` và frontend `vehicletracking-frontend`. Backend Spring Boot chứa REST `/api`, JPA entity/repository, scheduler simulator và STOMP. Frontend React tập trung REST ở `src/services/api.ts`, STOMP ở `src/services/websocket.ts`; `App.tsx` giữ Trip đang xem và truyền telemetry vào Map/Timeline/Simulator control.

## Tech stack

| Thành phần | Công nghệ/Version | Bằng chứng |
|---|---|---|
| Frontend | React `^19.2.8`, TypeScript `^7.0.2`, Vite `^8.2.2`, Leaflet, `@stomp/stompjs` `^7.3.0` | `vehicletracking-frontend/package.json:6-28` |
| Backend | Spring Boot `4.1.1`, Java `26`, JPA, validation, WebSocket | `vehiceltracking-backend/pom.xml:5-9,29-56` |
| Database local | PostgreSQL 16 và Redis 7 Compose; backend có PostgreSQL/H2 runtime dependency | `docker-compose.yml:1-26`, `pom.xml:58-67` |
| Realtime | Spring STOMP simple broker `/topic`, endpoint `/ws` SockJS và `/ws-raw` native | `WebSocketConfig.java:9-31` |
| Test/lint scripts | Backend có Spring test dependencies; frontend chỉ `lint`, `build`, `dev`, `preview`, không có test script | `pom.xml:73-101`, `package.json:6-10` |

## Claim và Evidence từ repository

| Claim | Evidence ID | Nguồn trong repository | Cách kiểm chứng | Trạng thái |
|---|---|---|---|---|
| `SimulatorService` giữ `ConcurrentHashMap` session, scheduler fixed-rate 1 giây và các method start/pause/resume/reset/multiplier | `EVD-003` | `service/SimulatorService.java:42-44,74-159` | `nl -ba .../SimulatorService.java` | PASS |
| Một tick tính speed/incident, di chuyển waypoint, gọi geofence/ETA, lưu Vehicle và publish hai topic telemetry | `EVD-003` | `SimulatorService.java:177-389` | Trace method bằng line-numbered source | PASS |
| Broker hiện có là simple broker `/topic`; frontend native STOMP subscribe `/topic/telemetry` | `EVD-004`, `EVD-005` | `config/WebSocketConfig.java:13-30`, `src/services/websocket.ts:17-70` | Đọc config/subscription | PASS |
| Payload telemetry hiện không chứa identity run/sequence | `EVD-006` | `dto/VehicleTelemetryDto.java:17-40`, `types/index.ts:95-119` | So sánh field backend/frontend | PASS |
| App chỉ filter theo `tripId`, và useEffect WebSocket phụ thuộc `simStatus`, nên mỗi state simulator đổi sẽ cleanup/disconnect/reconnect | `EVD-005` | `src/App.tsx:82-160` | Đọc dependency array, cleanup và callback | PASS |
| Map cập nhật marker/vận tốc/hướng từ một `VehicleTelemetry`; Timeline dùng telemetry matching Trip để render dynamic ETA | `EVD-005` | `components/MapComponent.tsx:252-297`, `components/TimelinePanel.tsx:11-97` | Đọc component source | PASS |
| Vehicle có field persisted cho vị trí/speed/heading/status; Trip/TripCheckIn có status/end time/check-in fields cần thiết cho reset | `EVD-007` | `entity/Vehicle.java:17-54`, `entity/Trip.java:21-55`, `repository/TripCheckInRepository.java:12-16` | Đọc entity/repository source | PASS |
| Traffic incident active có location/radius/reduction và repository query active; TimeConfig inject được Clock | `EVD-008` | `entity/TrafficIncident.java:21-67`, `repository/TrafficIncidentRepository.java:10-11`, `config/TimeConfig.java:8-14` | Đọc source | PASS |
| Có Simulator service test, nhưng chưa có Simulator controller test và existing service test không cover lifecycle response/run ID/out-of-order client contract | `EVD-009` | `src/test/.../service/SimulatorServiceTest.java`, danh sách test files, `rg` controller simulator | Đọc list/test methods và search | PASS |

## Cấu trúc thư mục liên quan

```text
vehiceltracking-backend/
├── src/main/java/com/quangkhai/vehiceltracking_backend/
│   ├── config/{TimeConfig,WebSocketConfig}.java
│   ├── controller/SimulatorController.java
│   ├── dto/VehicleTelemetryDto.java
│   ├── entity/{Trip,TripCheckIn,Vehicle,TrafficIncident}.java
│   ├── exception/{RouteExceptionHandler,StationExceptionHandler}.java
│   ├── repository/{TripCheckInRepository,RouteStationRepository,TrafficIncidentRepository,...}.java
│   └── service/{SimulatorService,GeofencingService,TripService}.java
└── src/test/java/com/quangkhai/vehiceltracking_backend/
    ├── controller/{RouteControllerTest,StationControllerTest,TripControllerTest}.java
    └── service/{SimulatorServiceTest,GeofencingServiceTest,TripServiceTest}.java

vehicletracking-frontend/src/
├── App.tsx
├── components/{MapComponent,SimulatorPanel,TimelinePanel,ToastNotification}.tsx
├── services/{api,websocket}.ts
└── types/index.ts
```

## Kiến trúc và luồng hiện tại

```mermaid
flowchart LR
    UI[SimulatorPanel] --> APP[App.tsx handlers]
    APP --> API[src/services/api.ts]
    API --> CTRL[SimulatorController]
    CTRL --> SIM[SimulatorService]
    SIM --> DB[(Trip / CheckIn / Vehicle)]
    SIM --> GF[GeofencingService]
    SIM --> ETA[calculateEtas]
    SIM --> STOMP[SimpMessagingTemplate]
    STOMP --> TOPIC[/topic/telemetry]
    TOPIC --> WS[WebSocketService]
    WS --> APP
    APP --> MAP[MapComponent]
    APP --> TIME[TimelinePanel]
```

1. `SimulatorPanel` gọi handler App cho Start/Pause/Resume/Reset/multiplier (`SimulatorPanel.tsx:88-145`).
2. `App.tsx` gọi method `api` cùng tên; `api.ts:166-190` hiện parse JSON trực tiếp mà không kiểm tra `res.ok` cho các endpoint simulator.
3. `SimulatorController` chuyển command sang service và luôn trả map success hiện có (`SimulatorController.java:17-54`).
4. `SimulatorService#startSimulation` build `SimulationSession`, put theo `tripId`, rồi tạo scheduler task 1 giây (`SimulatorService.java:74-110,155-159`).
5. `tickSingleSimulation` tính heading, active incidents, speed, step waypoint, geofence, ETA, cập nhật Vehicle và phát cùng DTO qua `/topic/telemetry` và `/topic/vehicle/{vehicleId}` (`SimulatorService.java:191-389`).
6. `WebSocketService` parse JSON `/topic/telemetry` và gọi callbacks; App chỉ bỏ payload có `tripId` không đúng `currentTrip` (`websocket.ts:34-40`, `App.tsx:90-124`).
7. Map cập nhật `Leaflet.Marker`; Timeline render speed, ETA, station check-in từ telemetry (`MapComponent.tsx:252-297`, `TimelinePanel.tsx:64-97`).

## Thành phần liên quan đến feature

| Loại | Path/Symbol | Trách nhiệm hiện tại | Requirement liên quan |
|---|---|---|---|
| REST controller | `controller/SimulatorController` | REST controls và GET status, response `Map`/inner session | REQ-001, REQ-003, REQ-005 |
| Simulator service | `service/SimulatorService#startSimulation/#tickAllSimulations/#tickSingleSimulation` | State memory, scheduler, movement, traffic, telemetry | REQ-001, REQ-002, REQ-003, REQ-005 |
| DTO/event | `dto/VehicleTelemetryDto` | Snapshot gửi trên hai STOMP topic | REQ-002, REQ-004 |
| WebSocket config | `config/WebSocketConfig` | Simple broker và endpoints | REQ-004 |
| Entity/repository | `Vehicle`, `Trip`, `TripCheckIn`, repositories | Persist location/state/reset data | REQ-002, REQ-003 |
| Feature dependency | `GeofencingService`, `TripService` | Check-in/completion state | REQ-002, REQ-005 |
| Frontend API | `src/services/api.ts` | Simulator REST wrappers, error parser có thể tái sử dụng | REQ-001, REQ-003, REQ-005 |
| Frontend realtime | `src/services/websocket.ts` | Connection, topic subscription, callback cleanup | REQ-004, REQ-005 |
| Frontend owner state | `src/App.tsx` | current Trip, simulator state, filtering telemetry, command callbacks | REQ-001, REQ-003, REQ-004, REQ-005 |
| UI | `MapComponent`, `TimelinePanel`, `SimulatorPanel` | Marker/timeline/control rendering | REQ-002, REQ-003, REQ-004 |

## Data model hiện tại

| Entity/Table | Field/Constraint liên quan | Ghi chú feature |
|---|---|---|
| `Vehicle` / `vehicles` | `status`, `currentLatitude`, `currentLongitude`, `currentSpeed`, `currentHeading`, `lastUpdatedAt` | Đủ để persist snapshot xe; non-terminal code hiện set fields nhưng không set persisted `status=IN_TRANSIT` tại `SimulatorService.java:341-350`. |
| `Trip` / `trips` | `status`, `startTime`, `endTime`, route/vehicle association | Reset simulator cần chuyển state `COMPLETED` trở lại `RUNNING` và clear `endTime` theo Requirement xác nhận. |
| `TripCheckIn` / `trip_checkins` | station, `stopOrder`, status, actual arrival | Reset đã có loop set PENDING/null trong `SimulatorService.java:126-141`, nhưng chưa reset Trip/Vehicle/remove session. |
| `TrafficIncident` / `traffic_incidents` | `active`, location, radius, speed reduction | Reuse factor hiện có; không đổi semantics incident. |

Không thêm table/cột/migration: run ID và sequence là field snapshot in-memory/DTO, không là audit telemetry.

## Convention đang được sử dụng

### Backend

- Service dùng Lombok `@RequiredArgsConstructor`; `SimulatorService` dùng nested `@Data`/`@Builder` session và `SimpMessagingTemplate` (`SimulatorService.java:28-72`).
- Error handler dành riêng controller dùng `@RestControllerAdvice(assignableTypes=...)`, `ProblemDetail` và status 400/404/409 (`RouteExceptionHandler.java:23-108`).
- Test service dùng JUnit 5 + Mockito (`SimulatorServiceTest.java:32-60`); controller test existing dùng `@SpringBootTest` + `@AutoConfigureMockMvc` (`RouteControllerTest.java:36-42`).

### Frontend

- Type contract tập trung ở `src/types/index.ts`; REST wrapper tập trung ở `src/services/api.ts`; STOMP callback cleanup tập trung ở `src/services/websocket.ts`.
- `SimulatorPanel` hiện chỉ expose controls `1x/2x/5x/10x`, đúng tập multiplier Requirement (`SimulatorPanel.tsx:116-145`).
- App hiện dùng `addToast` cho feedback nhưng simulator handlers chỉ `console.error` hoặc không catch nhất quán (`App.tsx:162-203`).

## Khoảng cách giữa hiện trạng và Requirement

| Requirement | Hiện trạng | Gap | Mức ảnh hưởng | Bằng chứng |
|---|---|---|---|---|
| REQ-001 | Một phần | Session/start/scheduler có sẵn; pause/resume/reset/multiplier silently no-op nếu không có session; controller luôn success map; không có run ID | Cao | EVD-003, EVD-009 |
| REQ-002 | Một phần | Waypoint/incident/ETA/two-topic publish đã có; Vehicle non-terminal không set persisted `IN_TRANSIT`; no contract tick snapshot unique | Cao | EVD-003, EVD-007, EVD-008 |
| REQ-003 | Một phần | UI có 4 multiplier, reset check-in một phần; backend nhận mọi positive finite/nonfinite không bị whitelist và reset để session tiếp tục (`paused=false`) | Cao | EVD-003, EVD-005 |
| REQ-004 | Một phần | Topic/callback và filter Trip có sẵn; không run ID/sequence, parser không guard JSON invalid, client effect reconnect theo `simStatus` | Cao | EVD-004, EVD-005, EVD-006 |
| REQ-005 | Một phần | Terminal early return được test; `tickAllSimulations` catch bao quanh cả loop nên exception A có thể skip session B trong cùng tick; UI error handling chưa nhất quán | Cao | EVD-003, EVD-005, EVD-009 |

## Technical debt liên quan

| Technical debt | Ảnh hưởng | Xử lý trong feature? | Lý do |
|---|---|---|---|
| Simple broker transient, không replay | Client reconnect không nhận event cũ | Không | Requirement chỉ cần snapshot tick tiếp và Trip refresh |
| Session state chỉ memory một JVM | Không ownership đa instance | Không | Cần research/hạ tầng khác, ngoài scope |
| `Vehicle#onUpdate` dùng `LocalDateTime.now()` thay Clock | Time persistence không fully deterministic | Không | Không cần sửa để contract telemetry/run identity; test assert fields deterministic ở DTO/service |
| `SimulatorController#getStatus` trả nested session object | Leaks internal shape | Có, thay response DTO status tối thiểu | Cần contract state/run ID rõ ràng cho frontend |

## File dự kiến bị ảnh hưởng

### File có thể tạo mới

- `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/dto/SimulatorCommandResponseDto.java` — typed/additive response command/status.
- `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/exception/SimulatorConflictException.java` — state conflict 409.
- `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/exception/SimulatorNotFoundException.java` — Trip/session missing 404.
- `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/exception/SimulatorExceptionHandler.java` — ProblemDetail controller-scoped.
- `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/controller/SimulatorControllerTest.java` — REST status/validation tests.
- `docs/features/005-realtime-vehicle-simulator/artifacts/` — Gemini chỉ thêm log/screenshot/recording verification, không ghi secret.

### File có thể chỉnh sửa

- `.../service/SimulatorService.java` — state transition, reset, per-session error isolation, run/sequence, Vehicle state và publish helper.
- `.../controller/SimulatorController.java` — typed response và error semantics.
- `.../dto/VehicleTelemetryDto.java` — additive run/sequence fields.
- `.../service/TripService.java` — method reset simulator state trong transaction, hoặc service helper tương đương ở `SimulatorService` nếu không tạo circular dependency; Spec quyết định `SimulatorService` giữ ownership reset để tránh scope/DI vòng.
- `.../service/SimulatorServiceTest.java` — lifecycle, sequence/two topic, reset, isolation tests.
- `vehicletracking-frontend/src/types/index.ts` — telemetry/command response types.
- `vehicletracking-frontend/src/services/api.ts` — typed simulator methods, `res.ok`/`parseErrorMessage` reuse.
- `vehicletracking-frontend/src/services/websocket.ts` — defensive message parse và single lifecycle semantics.
- `vehicletracking-frontend/src/App.tsx` — expected run/sequence refs, stable STOMP effect, command error toast/reset refresh.

## Command khảo sát đã chạy

| Command | Exit code | Kết quả/tóm tắt | Evidence |
|---|---:|---|---|
| `git status --short` | 0 | Không output trước khi tạo feature 005 | EVD-010 |
| `git rev-parse --short HEAD` | 0 | `f81419d` | EVD-010 |
| `nl -ba .../SimulatorService.java`, `.../SimulatorController.java`, `.../VehicleTelemetryDto.java`, `.../WebSocketConfig.java`, frontend realtime/UI files | 0 | Đọc line-numbered source xác minh claims trên | EVD-003..EVD-009 |
| `rg -n "@ControllerAdvice|ProblemDetail|..." ...` | 0 | Xác nhận convention `ProblemDetail` chỉ gắn Route/Station controller và chưa có simulator controller test | EVD-009 |

Không chạy build/lint/test trong Survey; các command đó là verification sau implementation và đang `INCONCLUSIVE` trong Evidence Matrix.

## Rủi ro và câu hỏi cần Spec giải quyết

- Spec phải chốt run ID/sequence branch terminal và reset boundary, tránh client nhận một message cũ ngay sau Start/Reset.
- Spec phải chốt status HTTP cho Trip/session/state/multiplier lỗi và không thay response existing destructively.
- Spec phải tách try/catch từng session để `tickAllSimulations` không dừng sau lỗi session đầu.
- Test-Plan cần có controller, service, two-topic, stale payload và UI manual evidence; lint/build không thay thế behavior test.

## Checklist hoàn thành

- [x] Không sửa source code ứng dụng trong phase Survey.
- [x] Tên path, symbol, command và stack lấy từ repository thật.
- [x] Luồng hiện tại/gap có Evidence source cụ thể.
- [x] Mỗi Claim quan trọng về repository có `EVD-*` và nguồn cụ thể.
- [x] Không có kết luận chỉ dựa trên tên project/folder/file.
- [x] Convention tái sử dụng đã được ghi nhận.
- [x] Technical debt chỉ giới hạn phần ảnh hưởng feature.
- [x] File dự kiến bị ảnh hưởng đã đủ để bắt đầu Spec.
