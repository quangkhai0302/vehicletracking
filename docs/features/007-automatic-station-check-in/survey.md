# 007 — Survey repository

Trạng thái: **Implemented** · Khảo sát 2026-09-14; source sau triển khai được đối chiếu trong [evidence](evidence.md).

## Trạng thái và khác biệt với bàn giao

- `git status --short` trước khi lập hồ sơ: chỉ `.gitignore` modified; thay đổi thêm rule `docs/` của người dùng được giữ nguyên.
- `git ls-files` xác nhận hai tài liệu đầu vào đã tracked. Hồ sơ 007 mới bị ignore theo `git check-ignore`; không tự force-add/commit hoặc sửa ignore.
- `docs/workflow.md` hiện tồn tại và đã đọc. Inventory có 001–006, chưa có 007 trước lượt này; `docs/templates/` không tồn tại. Khác các mô tả filesystem/Git của session Windows trước; không sửa lịch sử bàn giao để giả các mốc giống nhau.
- Không đọc `.env`, không chạy app/migration/build/test. Các số 118 test mốc 005, 24 test chọn lọc và 39 nhóm browser fixture mốc 006 chỉ là evidence lịch sử trong [handoff §9–10](../../SESSION_HANDOFF.md). Chưa kiểm tra lại blocker môi trường cũ trên máy này.

## Cây file liên quan hiện có

Quy ước trong bảng evidence: **B** = `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend`; **T** = `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend`; **F** = `vehicletracking-frontend/src`; **M** = `vehicletracking-backend/src/main/resources/db/migration`. Mỗi đường dẫn kèm `#symbol` là file/symbol hiện có, tính từ root repository.

```text
B/
  trip/{controller,dto,entity,repository,service}/
  telemetry/{controller,dto,entity,repository,service}/
  simulation/{config,controller,dto,entity,motion,repository,service}/
M/ V1…V5
T/ simulation/{RouteMotionTest,OperationsIntegrationTest,OperationsHttpIntegrationTest,SimulationFixtures}.java
F/
  components/fleet/{TripDetailPanel,FleetWorkspace}.tsx, fleet.css
  components/operations/{SimulatorPanel,AlertStream}.tsx
  hooks/{useLiveOperations,useFleetWorkspace,useSimulator}.ts
  services/{fleet,operations}.ts
  types/{fleet,operations}.ts
```

## Evidence và tác động

| ID | Nhận định từ source | Evidence | Ý nghĩa cho 007 |
|---|---|---|---|
| E01 | Ingestion normalize microseconds, dedupe trước/sau lock; khóa trip→vehicle, validate lifecycle/source; lưu history/latest cùng transaction. | `B/telemetry/service/TelemetryService.java:28–61`, `#ingest`, `#duplicate`; `B/telemetry/entity/TelemetrySampleEntity.java#matches` | Gắn detector sau save sample và trước cập nhật latest; duplicate không chạy lại detector. |
| E02 | Latest là theo xe, không theo trip; telemetry có recordedAt/receivedAt/simulatedAt; repository chưa có truy vấn lịch sử theo trang. | `B/telemetry/service/TelemetryService.java#ingest`; `B/telemetry/entity/VehiclePositionEntity.java`; `B/telemetry/repository/TelemetryRepository.java` | Không nối mẫu trip cũ với trip mới; checkpoint riêng để không quét history. |
| E03 | TripStop snapshot sequence, station name/coords/radius/dwell và giờ kế hoạch; create lấy radius tại thời điểm tạo trip. | `B/trip/entity/TripStopEntity.java#TripStopEntity`; `B/trip/service/TripService.java#create`; `B/trip/entity/TripEntity.java#stops` | Dùng occurrence, không tra station hiện tại khi detect. |
| E04 | V4 unique trip+sequence, radius 10–1000 m; V5 có sample/latest/run nhưng chưa có visit/checkpoint check-in. | `M/V4__create_vehicles_and_trips.sql#uq_trip_stops_sequence`; `M/V5__create_telemetry_and_simulation.sql`; inventory migrations V1–V5 | Đề xuất migration V6, không sửa migration cũ. |
| E05 | RouteMotion giữ leg geometry/time và nội suy theo khoảng cách; `at` chỉ trả một frame. `SimulationService.emit` chỉ phát frame hiện tại; `advance` emit trước complete. | `B/simulation/motion/RouteMotion.java#Leg`, `#at`, `#distance`; `B/simulation/service/SimulationService.java#emit`, `#advance` | Cần trace khoảng đã chạy để 10× không nối tắt góc cua; không suy check-in từ nextStopSequence của frame. |
| E06 | Reset tạo trip mới; recover paused, không cộng downtime. | `B/simulation/service/SimulationService.java#reset`, `#recover`; `B/trip/service/TripService.java#transition` | Lịch sử visit theo trip giữ lại; detector không quyết định lifecycle. |
| E07 | Snapshot REPEATABLE_READ gồm trips/positions/simulations; SSE full snapshot mỗi giây, không replay event. | `B/telemetry/service/OperationsSnapshotService.java#snapshot`; `B/telemetry/dto/OperationsSnapshot.java`; `B/telemetry/service/OperationsStreamService.java#subscribe`, `#broadcast` | Thêm read model checkIns vào cùng snapshot; không thêm stream khác. |
| E08 | API trip trả detail có stops kế hoạch và route; không có actual visit. | `B/trip/controller/TripController.java`; `B/trip/dto/TripDetailResponse.java#Stop`; `B/trip/service/TripService.java#findById` | Giữ contract baseline; thêm GET lịch sử riêng thay vì nhét actual vào entity snapshot. |
| E09 | Timeline dùng sequenceNumber làm key, hiện chỉ có lịch; fleet hook hợp nhất summary từ live snapshot, chưa merge stop visits. | `F/components/fleet/TripDetailPanel.tsx#TripDetailPanel`; `F/hooks/useFleetWorkspace.ts#visibleDetail`; `F/types/fleet.ts#TripStop` | Tách dữ liệu ghi nhận khỏi TripDetail, giữ lịch/draft/selection. |
| E10 | `useLiveOperations` chia sẻ GET + EventSource, bỏ snapshot cũ và cleanup; service chỉ validate arrays hiện có. | `F/hooks/useLiveOperations.ts#useLiveOperations`; `F/services/operations.ts#subscribeOperations`; `F/types/operations.ts#OperationsSnapshot`; `F/components/MapComponent.tsx#MapComponent` | Mở rộng type/validation đồng bộ; không tạo EventSource ở mỗi panel. |
| E11 | Simulator có detail abort và command guard; countdown từ frame; AlertStream là placeholder. | `F/hooks/useSimulator.ts#useSimulator`; `F/components/operations/SimulatorPanel.tsx#SimulatorPanel`; `F/components/operations/AlertStream.tsx#AlertStream` | Thêm số lần ghé đã ghi nhận, không đổi countdown thành ETA thực hoặc notification module. |
| E12 | Copy station/simulator/trip vẫn nói check-in chưa nối. | `F/components/StationDrawer.tsx` phần `radius-helper-text`; `F/components/fleet/TripDetailPanel.tsx` phần `route-snapshot-note`; `F/components/operations/SimulatorPanel.tsx#simulator-unavailable` | Cập nhật đúng phạm vi sau implement; vòng hiện tại trên map là radius station hiện tại, không bằng chứng bán kính chuyến cũ. |
| E13 | Full JPA/clock tests và HTTP/SSE harness đã có; fixture loop có geometry đủ, unlike fixture snapshot cũ. | `T/simulation/OperationsIntegrationTest.java#clockPauseResumeMultiplierAndCompletionKeepBaseline`, `#resetRetainsHistoryAndIsIdempotent`; `T/simulation/OperationsHttpIntegrationTest.java#twoStreamsReceiveCommittedStateAndReconnectResyncs`; `T/simulation/SimulationFixtures.java#route`; `T/simulation/RouteMotionTest.java` | Tái dùng Testcontainers, clock và browser harness; giữ assertion baseline nguyên vẹn. |
| E14 | Flyway + Hibernate validate, ProblemDetail bật; Java 26; frontend lint/tsc/build, không có app component runner. | `vehicletracking-backend/src/main/resources/application.yaml#spring`; `vehicletracking-backend/pom.xml#java.version`; `vehicletracking-frontend/package.json#scripts`; `F/services/fleet.ts#request` | Không đổi nền DB/response errors, dùng công cụ test sẵn có. |
| E15 | Browser 006 dùng Playwright từ verification 004, mặc định msedge; có fixture/live-test-database tùy VERIFICATION_API. | `docs/features/006-telemetry-simulator/verification/live-browser.mjs:1–16`; `T/simulation/OperationsHttpIntegrationTest.java` phần `verification.browser006` | Plan phải chuẩn bị harness trên Linux; không gọi fixture là bằng chứng Spring thật. |

## Luồng hiện tại và điểm gắn đề xuất

Hiện tại: GPS HTTP / simulator tick → TelemetryService → sample + latest → snapshot transaction → SSE → shared hook → map/panels (E01, E05, E07, E10).

Đề xuất: trong transaction ingestion thêm detect(next occurrence) → visits + checkpoint; snapshot đọc thêm checkIns. UI giữ baseline từ TripDetail và ghép read model check-in theo tripId/sequence. Không phát check-in từ frontend hoặc từ `SimulationResponse.frame.nextStopSequence`.

## Rủi ro hồi quy cần xử lý

- Circular dependency nếu CheckInService gọi SimulationService vốn gọi TelemetryService (E01, E05). Chỉ dùng pure RouteMotion/trace, không inject ngược SimulationService.
- Check-in sau commit làm mất tính nguyên tử (E01, E07); duplicate return sau chuyến kết thúc vẫn phải giữ behavior hiện có.
- Mô phỏng tăng tốc có simulatedAt khác wall clock; nối thẳng hai frame có thể mất cả vòng (E02, E05).
- Thêm actual vào TripStop sẽ làm assertion bất biến stops của 006 đổi nghĩa (E08, E13). Chọn endpoint/read model riêng.
- Full snapshot đang đọc toàn danh mục mỗi giây (E07); chỉ mở rộng trong mô hình dev hiện tại, đo tải nhỏ và tránh truy vấn N+1/history scan. Không tuyên bố scale production.
