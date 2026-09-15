# Plan: Triển khai tạo tuyến đường từ danh sách điểm dừng

## 1. Trạng thái bàn giao

- **Feature:** 003-route-creation-from-stops
- **Trạng thái hiện tại:** Ready for Review
- **Planning:** Codex hoàn thành Requirement → Research → Survey → Spec → Test-Plan → Plan.
- **Implementation owner:** Gemini sau khi người dùng phê duyệt.
- **Review owner:** Codex sau khi Gemini hoàn thành implementation, evidence và walkthrough.
- **Planning gate:** Chưa được sửa source, tạo migration hoặc cài dependency trong giai đoạn hiện tại.

## 2. Quyết định đã đóng trong plan

1. HERE Routing được gọi từ backend; không có HERE key trong frontend.
2. Route là snapshot bất biến ở feature này; chỉ có create/list/detail.
3. Route giữ nguyên thứ tự stop người dùng chọn; không tự tối ưu.
4. 2–50 stops; consecutive duplicate bị từ chối, lặp không liền kề được phép.
5. START/END suy ra từ thứ tự và dwell bằng 0; STOP có dwell 0–3600 giây.
6. HERE dùng `car`, `fast`, current-time traffic, `polyline,summary,travelSummary`.
7. Lưu ba bảng normalized để hỗ trợ nhiều sections trong một leg và tái sử dụng cho simulator sau này.
8. Không fallback đường chim bay.

## 3. Danh sách file dự kiến

### 3.1 Backend tạo mới

```text
src/main/resources/db/migration/V3__create_routes_tables.sql

src/main/java/com/quangkhai/vehicletracking_backend/route/
├── controller/RouteController.java
├── dto/
│   ├── RouteCreateRequest.java
│   ├── RouteSummaryResponse.java
│   └── RouteDetailResponse.java
├── entity/
│   ├── RouteEntity.java
│   ├── RouteStopEntity.java
│   ├── RouteSectionEntity.java
│   ├── RouteTransportMode.java
│   └── RoutingProviderName.java
├── repository/RouteRepository.java
├── service/
│   ├── RouteService.java
│   └── RoutePersistenceService.java
├── provider/
│   ├── RoutingProvider.java
│   ├── RoutingWaypoint.java
│   ├── CalculatedRoute.java
│   ├── HereRoutingProperties.java
│   ├── HereRoutingHttpClientConfig.java
│   └── HereRoutingProvider.java
└── error/
    ├── RouteErrorCode.java
    ├── RouteOperationException.java
    └── RouteExceptionHandler.java
```

Provider-specific HERE JSON records nên là private/package-private nested records trong `HereRoutingProvider` nếu đọc được rõ ràng; không tạo nhiều DTO public không cần thiết.

### 3.2 Backend sửa

```text
src/main/java/.../station/repository/StationRepository.java
src/main/resources/application.yaml
src/test/resources/application-test.yaml (chỉ khi cần default routing disabled/test-safe)
.env.example
```

Không sửa V1/V2, không thay station API/entity/service nếu không có dependency bắt buộc.

### 3.3 Backend tests tạo mới

```text
src/test/java/.../route/controller/RouteControllerTest.java
src/test/java/.../route/service/RouteServiceTest.java
src/test/java/.../route/provider/HereRoutingProviderTest.java
src/test/java/.../route/repository/RouteRepositoryIntegrationTest.java
src/test/java/.../route/config/HereRoutingConfigurationTest.java

src/test/resources/fixtures/here-route-multi-stop.json
src/test/resources/fixtures/here-route-multi-section-leg.json
src/test/resources/fixtures/here-route-no-route.json
src/test/resources/fixtures/here-route-critical-notice.json
```

### 3.4 Frontend tạo mới

```text
src/types/route.ts
src/services/routes.ts
src/components/route/RouteWorkspace.tsx
src/components/route/RoutePanel.tsx
src/components/route/RouteDrawer.tsx
src/components/route/route.css
```

### 3.5 Frontend sửa

```text
src/types/workspace.ts
src/App.tsx
src/components/MapComponent.tsx
```

Chỉ sửa `src/index.css` nếu cần semantic token hoặc responsive rule dùng chung; style riêng ưu tiên đặt trong `route.css`.

## 4. Các bước implementation cho Gemini

### Bước 0 — Xác nhận baseline và bảo toàn working tree

**Mục tiêu:** Không ghi đè thay đổi station/frontend đang có.

**Thực hiện:**

- Đọc lại `AGENTS.md`, `docs/workflow.md`, toàn bộ sáu file feature 003 và `docs/design.md:351-381`.
- Chạy `git status --short`, lưu danh sách thay đổi có sẵn vào evidence draft.
- Xác nhận JDK 26 cho Maven và Node 24 theo `.nvmrc`.
- Không đọc/in `.env`; chỉ kiểm tra tên biến cấu hình.

**Test:** Baseline commands trong `test-plan.md` nếu môi trường đáp ứng.

**Rủi ro:** Java 17 từng làm Surefire chạy 0 test; không được ghi baseline là pass.

**Done khi:** Working tree gốc được ghi nhận và toolchain đúng version hoặc limitation được báo trước khi code.

### Bước 1 — Tạo V3 migration

**Mục tiêu:** Thiết lập schema chính xác trước JPA.

**File:** `V3__create_routes_tables.sql`.

**Thực hiện:**

- Tạo `routes`, `route_stops`, `route_sections` theo toàn bộ field/null/default/check/FK/index trong `spec.md`.
- Tạo unique `(route_id, sequence_number)` trước composite FK từ route sections.
- Dùng `ON DELETE RESTRICT` cho station và `ON DELETE CASCADE` cho child route data.
- Thêm SQL comments giải thích snapshot, dwell và section mapping.

**Test:** Khởi động Testcontainers/Flyway từ database sạch; kiểm tra constraint negative cases.

**Rủi ro:** Thứ tự tạo constraint/composite FK; tên schema `vehicle_tracking`.

**Done khi:** V1→V3 migrate thành công trên PostgreSQL 17 và schema khớp spec.

### Bước 2 — Tạo JPA entities và repository

**Mục tiêu:** Ánh xạ schema không rò entity ra API.

**Files:** Ba entity, hai enum, `RouteRepository`, sửa `StationRepository`.

**Thực hiện:**

- Dùng `@Getter`, protected no-args constructor; không dùng `@Data`/public setters.
- Route sở hữu stop/section collections qua cascade persist và helper add methods giữ hai phía quan hệ.
- Giữ ordered collections theo `sequenceNumber`/`sectionSequence`.
- Route stop tham chiếu lazy station và giữ name/coordinate snapshots.
- RouteRepository có list summary projection/query và detail fetch strategy tránh N+1.
- StationRepository thêm batch active lookup; không dùng `findAllById` đơn thuần.

**Test:** `RouteRepositoryIntegrationTest` cho persist/read/order/FK/check/cascade/rollback.

**Rủi ro:** Multiple bag fetch trong Hibernate. Không join-fetch đồng thời hai `List` bags; dùng `@OrderBy` + batch fetch, `Set`, hoặc hai query có chủ đích và chứng minh không N+1.

**Done khi:** Hibernate validate, repository tests pass và detail trả đúng order.

### Bước 3 — Tạo routing provider boundary và HERE client

**Mục tiêu:** Tính route thật mà service không phụ thuộc HERE JSON/HTTP.

**Files:** Các file trong `route/provider`, cấu hình `application.yaml`.

**Thực hiện:**

- Định nghĩa `RoutingProvider`, `RoutingWaypoint`, `CalculatedRoute` và normalized sections.
- Tạo `HereRoutingProperties` với conditional validation: disabled không cần key; enabled bắt buộc key.
- Bổ sung `HERE_ROUTING_ENABLED`, `HERE_ROUTING_BASE_URL`, connect/read timeout vào root `.env.example`; dùng lại tên `HERE_API_KEY` đã có và chỉ đặt giá trị mẫu vô hại.
- Tạo named routing `RestClient` có connect/read timeout riêng.
- Tạo URI bằng builder; không concatenate/log URL chứa key.
- Gửi origin/via/destination đúng order và dwell via options.
- Parse first route; validate routes/sections/polyline/travelSummary/notices.
- Ánh xạ waypoint indices sang `destinationStopSequence`; hỗ trợ nhiều sections/leg.
- Base duration thiếu thì fallback dynamic duration.
- Chuyển timeout, auth, rate-limit, no-route và malformed response thành typed route errors.

**Test:** `HereRoutingProviderTest` với HTTP mock và bốn fixtures; không gọi internet.

**Rủi ro:** Query parameter `via` lặp và ký tự `!`; URI builder không được encode sai structural syntax. Test phải kiểm tra request thực tế.

**Done khi:** Provider tests chứng minh request/mapping/error/redaction theo spec.

### Bước 4 — Tạo route service, transaction và API

**Mục tiêu:** Hoàn thiện create/list/detail contract.

**Files:** DTO, service, error handler và controller.

**Thực hiện:**

- `RouteCreateRequest` dùng nested validated stop record để giảm file boilerplate.
- RouteService validate name, count, dwell, consecutive duplicates.
- Batch load station active và dựng lại đúng input order, kể cả ID lặp không liền kề.
- Gọi provider trước persistence transaction.
- Tính totals, role, leg metrics và arrival/departure offsets bằng integer seconds/meters.
- `RoutePersistenceService` mở một transaction cho graph save; không chứa provider HTTP call.
- Read methods dùng read-only transaction.
- Controller cung cấp POST/list/detail đúng status/Location.
- RouteExceptionHandler trả ProblemDetail + stable `code`, không trả raw provider content.

**Test:** `RouteServiceTest`, `RouteControllerTest`, config-disabled test.

**Rủi ro:** Transaction rollback, overflow khi cộng duration/distance. Dùng `long` và `Math.addExact` hoặc kiểm tra overflow có kiểm soát.

**Done khi:** API/controller/service tests bao phủ success và negative cases, không lưu partial data.

### Bước 5 — Tạo frontend route types và API service

**Mục tiêu:** Đồng bộ TypeScript 1:1 với API.

**Files:** `types/route.ts`, `services/routes.ts`.

**Thực hiện:**

- Khai báo create input, summary, detail, stop/section và role unions; không dùng `any`.
- API functions: `fetchRoutes`, `fetchRouteById`, `createRoute`.
- Dùng `VITE_API_BASE_URL`; không thêm biến HERE.
- Parse ProblemDetail `detail` và `code`; trả lỗi an toàn.
- List type không chứa sections/polyline.

**Test:** Typecheck/lint; inspect network contract khi manual.

**Rủi ro:** Java `long` lớn hơn JS safe integer. ID identity hiện được dùng number như station; giữ nhất quán trong feature, ghi follow-up nếu scale vượt `Number.MAX_SAFE_INTEGER`.

**Done khi:** Types khớp JSON spec và không có `any`/secret/provider call trực tiếp.

### Bước 6 — Tạo RouteWorkspace, RoutePanel và RouteDrawer

**Mục tiêu:** UI tạo/xem route đầy đủ nhưng không làm MapComponent thành form monolith.

**Files:** `components/route/*`, `types/workspace.ts`, `App.tsx`.

**Thực hiện:**

- Thêm workspace union `routes` và một navigation button duy nhất.
- RouteWorkspace sở hữu fetch/list/select/create/form state và báo selected detail cho MapComponent.
- RoutePanel có search, list summary, selected/loading/empty/error và create action.
- RouteDrawer có browse/create mode.
- Builder dùng station active collection, search/add/remove/reorder buttons và dwell number input.
- Role được derive sau mỗi state change; START/END dwell reset về 0.
- Inline validation và submit disabled; provider fail giữ form.
- Success cập nhật list, chọn detail mới và hiển thị feedback.
- Style map-centric/compact/responsive trong `route.css` theo `docs/design.md`.

**Test:** Manual builder/accessibility checklist + lint/tsc/build.

**Rủi ro:** State stop bị mutate khi reorder. Luôn tạo array mới và dùng stable local key tách khỏi station ID vì station có thể lặp.

**Done khi:** AC-02/03/13 đạt bằng manual evidence và code giữ type rõ ràng.

### Bước 7 — Tích hợp route vào Leaflet map

**Mục tiêu:** Vẽ geometry thật và stop markers mà không xung đột tracking/station.

**File:** `MapComponent.tsx`; có thể thêm helper nhỏ trong `components/route` nếu render logic quá dài.

**Thực hiện:**

- Rename/tách existing route ref thành `vehicleRouteLayerRef` và `plannedRouteLayerRef`.
- Khởi tạo/cleanup hai layer đúng vòng đời map.
- Nhận selected `RouteDetail` từ RouteWorkspace.
- Decode sections theo sequence; bỏ section invalid và đưa ra UI error nếu không có geometry hợp lệ.
- Vẽ solid route polyline và numbered stop markers; không nối station coordinates thành fallback.
- Fit bounds chỉ khi selected route ID thay đổi.
- Phân nhánh station marker click rõ cho `tracking`, `stations`, `routes`.
- Chuyển workspace clear đúng planned layer nhưng không ảnh hưởng vehicle layer.

**Test:** Manual map checklist, đặc biệt route nhiều sections/leg và chuyển workspace liên tục.

**Rủi ro:** Effect dependency làm fit map lặp, layer bị clear nhầm hoặc listener leak.

**Done khi:** AC-12 đạt và map instance không recreate.

### Bước 8 — Hoàn thiện automated tests và verification

**Mục tiêu:** Chạy đầy đủ ma trận test bằng toolchain đúng.

**Files:** Toàn bộ tests/fixtures liệt kê ở Mục 3.

**Thực hiện:**

- Chạy tất cả backend tests bằng JDK 26, không chỉ route tests.
- Chạy frontend bằng Node 24: lint, tsc, build.
- Chạy secret pattern check mà không đọc/in `.env`.
- Nếu Docker không dùng được, ghi rõ repository integration tests chưa chạy; không thay bằng H2.
- Nếu HERE smoke test không có key/quota, ghi skipped; mocked provider tests vẫn bắt buộc.

**Done khi:** Commands và exit codes thật được ghi vào evidence; mọi failed/skipped test có lý do.

### Bước 9 — Evidence và walkthrough do Gemini cập nhật

**Mục tiêu:** Bàn giao implementation có thể kiểm chứng.

**Files tạo sau implementation:**

```text
docs/features/003-route-creation-from-stops/evidence.md
docs/features/003-route-creation-from-stops/walkthrough.md
```

**Evidence bắt buộc:**

- Working tree/commit được kiểm tra.
- File thực tế đã tạo/sửa.
- AC → code/test mapping có path/symbol/dòng.
- Command, exit code, test count.
- Migration/PostgreSQL result.
- Manual builder/map checklist và ảnh nếu có.
- Secret check đã redact.
- Sai lệch so với plan và lý do.

**Done khi:** Không dùng walkthrough thay test evidence và không tuyên bố phần chưa kiểm tra là pass.

### Bước 10 — Codex review sau implementation

**Mục tiêu:** Review độc lập trước khi chấp nhận feature.

**File:** `docs/features/003-route-creation-from-stops/review.md`.

**Thực hiện:**

- Codex đọc source/migration/test thực tế, không chỉ walkthrough của Gemini.
- Review ưu tiên finding Critical/High/Medium/Low.
- Kiểm tra transaction boundary, section mapping, totals, error mapping, no-secret và frontend layer cleanup.
- Chạy lại các lệnh khả thi.
- Kết luận `Accept`, `Accept with follow-up` hoặc `Request changes`.

**Done khi:** Review có evidence và feature chuyển Reviewed chỉ khi acceptance criteria thực sự đạt.

## 5. Thứ tự phụ thuộc

```text
V3 migration
  → JPA entities/repositories
    → provider normalized contract
      → service/API
        → frontend types/API
          → route workspace
            → map rendering
              → full verification
                → evidence/walkthrough
                  → Codex review
```

Provider interface/value objects có thể được tạo song song về mặt code với entities, nhưng Gemini không được nối UI trước khi API/spec types ổn định.

## 6. Guardrails bắt buộc cho Gemini

- Không implement edit/delete route, trip, simulator, check-in, route optimization, traffic overlay hoặc rerouting.
- Không sửa migration V1/V2.
- Không đưa HERE key vào `VITE_*`, source, fixture, log, response hoặc docs.
- Không gọi HERE trực tiếp từ frontend.
- Không dùng mock route/straight line làm fallback production.
- Không dùng `findAllById` để bỏ qua active state station.
- Không giữ DB transaction mở trong lúc gọi HERE.
- Không giả định một leg luôn có đúng một HERE section.
- Không dùng route handle làm persistent geometry.
- Không cài frontend test/drag-drop library nếu chưa cập nhật và được duyệt lại plan.
- Không refactor toàn bộ station/tracking hoặc đổi design ngoài phần route cần thiết.
- Không reset/checkout/xóa thay đổi có sẵn của người dùng.

## 7. Điểm cần dừng và xin duyệt lại

Gemini phải dừng, cập nhật spec/plan và xin người dùng duyệt nếu gặp một trong các trường hợp:

- HERE account không hỗ trợ request nhiều via theo contract đã chọn.
- Cần đổi transport mode khỏi `car` hoặc thêm vehicle profile.
- Cần nâng/hạ giới hạn 50 stop vì provider test thật.
- Cần đổi schema ba bảng hoặc API JSON shape đáng kể.
- Cần thêm route edit/delete để hoàn thành UI.
- Cần cài dependency production/test mới ngoài những dependency hiện có.
- Có xung đột với thay đổi station/frontend trong working tree không thể merge an toàn.

## 8. Definition of Done

Feature chỉ hoàn thành khi:

- AC-01 đến AC-15 đạt và có evidence.
- V3/JPA/API/frontend contract nhất quán.
- Provider failure không tạo partial route.
- Geometry thật hiển thị trên map, không fallback đường chim bay.
- HERE key chỉ tồn tại trong backend runtime environment.
- Backend suite chạy bằng JDK 26; frontend lint/tsc/build pass.
- Gemini hoàn thành evidence/walkthrough và Codex hoàn thành review.

## 9. Hành động sau tài liệu này

**Dừng tại planning gate.** Người dùng review năm quyết định quan trọng: route bất biến, create/list/detail only, transport mode CAR, giới hạn 50 stop và schema ba bảng. Sau khi người dùng chấp thuận rõ ràng, chuyển nguyên thư mục feature 003 cho Gemini triển khai theo Bước 0→9. Codex quay lại thực hiện Bước 10.
