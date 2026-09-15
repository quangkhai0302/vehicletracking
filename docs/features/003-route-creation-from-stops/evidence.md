# Bằng chứng kiểm thử và nghiệm thu: Tạo tuyến đường từ danh sách điểm dừng (Sau vòng sửa lỗi 5)

Tài liệu này ghi lại toàn bộ bằng chứng thực tế từ quá trình sửa đổi và kiểm chứng các findings trong [review.md](review.md) cho tính năng "Tạo tuyến đường từ danh sách điểm dừng" (`docs/features/003-route-creation-from-stops/`).

---

## 1. Môi trường kiểm thử thực tế

- **Hệ điều hành:** Linux (Ubuntu/Debian-based x86_64).
- **Backend Toolchain:**
  - Java: OpenJDK 26.0.1 (Amazon Corretto 26.0.1-amzn)
  - Maven Wrapper: `./mvnw`
  - Maven Surefire Plugin: cấu hình `-XX:+EnableDynamicAgentLoading` cho Java 26
  - Cơ sở dữ liệu: PostgreSQL 17.11 (Chạy qua Docker Testcontainers `postgres:17`)
- **Frontend Toolchain:**
  - Node.js: v24.16.0
  - NPM: 11.13.0
  - Linter: Oxlint (24 files, 104 rules)
  - Type Checker: TypeScript 5.7+ (`tsc --noEmit`)
  - Bundler: Vite 8.2.2 (`vite build`)
  - Runtime Verification: Chromium qua browser subagent và Playwright

---

## 2. Kết quả kiểm thử Backend (JDK 26 Corretto)

Lệnh thực thi:

```bash
export JAVA_HOME=~/.sdkman/candidates/java/26.0.1-amzn
export PATH=$JAVA_HOME/bin:$PATH
cd vehicletracking-backend
./mvnw test
```

Exit code: `0` (BUILD SUCCESS).

### 2.1. Bảng tổng hợp các test suite

| Package / Test Suite | Số Test | Failures | Errors | Skipped | Trạng thái |
| --- | --- | --- | --- | --- | --- |
| `route.config.RouteConfigurationTest` | 3 | 0 | 0 | 0 | **PASSED** |
| `route.controller.RouteControllerTest` | 13 | 0 | 0 | 0 | **PASSED** |
| `route.error.RouteExceptionHandlerRegressionTest` | 1 | 0 | 0 | 0 | **PASSED** |
| `route.provider.HereRoutingProviderTest` | 27 | 0 | 0 | 0 | **PASSED** |
| `route.repository.RouteRepositoryIntegrationTest` | 11 | 0 | 0 | 0 | **PASSED** |
| `route.service.RouteServiceTest` | 14 | 0 | 0 | 0 | **PASSED** |
| `station.controller.StationControllerTest` | 6 | 0 | 0 | 0 | **PASSED** |
| `station.repository.StationRepositoryIntegrationTest` | 1 | 0 | 0 | 0 | **PASSED** |
| `station.service.StationServiceTest` | 9 | 0 | 0 | 0 | **PASSED** |
| **Tổng cộng** | **85** | **0** | **0** | **0** | **BUILD SUCCESS** |

*(Trong đó có **69 tests** thuộc feature Route và **16 tests** thuộc feature Station).*

### 2.2. Danh sách chi tiết các test của feature Route (69 tests)

1. `RouteConfigurationTest` **(3 tests):**
   - `routingDisabled_withBlankKey_startsSuccessfully`: Routing disabled cho phép API key trống.
   - `routingEnabled_withValidKey_startsSuccessfully`: Routing enabled với API key hợp lệ khởi động thành công.
   - `routingEnabled_withBlankKey_failsStartupFast`: Routing enabled với API key rỗng bị fail fast khi khởi động.

2. `RouteExceptionHandlerRegressionTest` **(1 test):**
   - `stationValidationFailure_isNotInterceptedByRouteExceptionHandler`: Đảm bảo exception handler của Route không can thiệp vào validation format của Station.

3. `HereRoutingProviderTest` **(27 tests):**
   - `calculate_whenDisabled_throwsServiceUnavailable`: Provider bị disable ném HTTP 503 `RouteErrorCode.ROUTING_UNAVAILABLE`.
   - `calculate_whenLessThan2Waypoints_throwsBadRequest`: Dưới 2 waypoints ném lỗi BadRequest.
   - `calculate_withMultipleWaypoints_sendsExpectedQueryAndMapsSectionsCorrectly`: Gửi đúng query parameters và ánh xạ sections.
   - `calculate_withTwoViaWaypoints_sendsMultipleViaInOrderAndMapsDestinations`: Xử lý đúng thứ tự các via waypoints và gán đích đến tương ứng.
   - `calculate_whenMissingWaypointBoundary_throwsBadGateway`: Thiếu waypoint boundary ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenOutOfOrderWaypoint_throwsBadGateway`: Waypoint sai thứ tự ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenSectionCriticalNotice_throwsUnprocessableEntity`: Section có critical notice ném HTTP 422 `RouteErrorCode.ROUTE_NOT_FOUND_BY_PROVIDER`.
   - `calculate_whenMissingPolyline_throwsBadGateway`: Section thiếu polyline ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenMissingSummary_throwsBadGateway`: Section thiếu summary ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenMalformedDepartureTime_throwsRoutingProviderException`: `departure.time` sai định dạng ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenBaseDurationInSummary_extractsBaseDurationCorrectly`: Trích xuất base duration chính xác khi có trong summary.
   - `calculate_whenTimeout_throwsGatewayTimeout_andDoesNotLogApiKey`: Timeout ném HTTP 504 `RouteErrorCode.ROUTING_PROVIDER_TIMEOUT` và không log API key.
   - `calculate_whenInternalServerError500_throwsServiceUnavailable`: Provider trả 500 ném HTTP 503 `RouteErrorCode.ROUTING_PROVIDER_UNAVAILABLE`.
   - `calculate_whenMultiSectionLeg_groupsSectionsToCorrectDestinationStop`: Gom nhóm nhiều section cho cùng một chặng dừng chính xác.
   - `calculate_whenNoRouteFound_throwsUnprocessableEntity`: Không tìm thấy đường ném HTTP 422 `RouteErrorCode.ROUTE_NOT_FOUND_BY_PROVIDER`.
   - `calculate_whenCriticalNotice_throwsUnprocessableEntity`: Root response có critical notice ném HTTP 422 `RouteErrorCode.ROUTE_NOT_FOUND_BY_PROVIDER`.
   - `calculate_whenUnauthorized_throwsServiceUnavailable`: Lỗi xác thực 401 ném HTTP 503 `RouteErrorCode.ROUTING_UNAVAILABLE`.
   - `calculate_whenRateLimitedOr5xx_throwsServiceUnavailable`: Lỗi 429 ném HTTP 503 `RouteErrorCode.ROUTING_PROVIDER_UNAVAILABLE`.
   - `calculate_whenMissingEndSection_throwsRoutingProviderException`: Response kết thúc ở via waypoint cuối mà không có chặng tới END bị từ chối với HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenMissingDepartureTime_throwsRoutingProviderException`: Thiếu departure time ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenMalformedJsonPayload_throwsRoutingProviderException`: JSON response không parse được ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenNegativeDistance_throwsRoutingProviderException`: Distance âm ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenNegativeTravelDuration_throwsRoutingProviderException`: Travel duration âm ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenNegativeBaseDuration_throwsRoutingProviderException`: Base duration âm ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenNullRouteElement_throwsRoutingProviderException`: Phần tử route null `{"routes":[null]}` ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenNullSectionElement_throwsRoutingProviderException`: Phần tử section null `{"routes":[{"sections":[null]}]}` ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.
   - `calculate_whenNullNoticeElement_throwsRoutingProviderException`: Phần tử notice null `notices: [null]` ném HTTP 502 `RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE`.

4. `RouteRepositoryIntegrationTest` **(11 tests - PostgreSQL 17 thật qua Testcontainers):**
   - `persistRouteWithStopsAndSections_persistsAndRetrievesGraphCorrectly`: Lưu trữ và truy vấn toàn bộ đồ thị Route - Stops - Sections.
   - `findAllByOrderByCreatedAtDescIdDesc_returnsLatestFirst`: Truy vấn danh sách tuyến theo thứ tự mới nhất trước.
   - `duplicateStopSequence_throwsDataIntegrityViolationException`: Ràng buộc `uq_route_stops_route_sequence` từ chối sequence trùng.
   - `duplicateSectionSequence_throwsDataIntegrityViolationException`: Ràng buộc `uq_route_sections_route_sequence` từ chối sequence trùng.
   - `deleteStationReferencedByRouteStop_isRestricted`: Khóa ngoại `fk_route_stops_station` ngăn chặn xóa trạm đang được sử dụng bởi route (`ON DELETE RESTRICT`).
   - `stopDwellDuration_negative_violatesCheckConstraint`: Check constraint `chk_route_stops_dwell_duration` từ chối dwell âm.
   - `stopDwellDuration_exceeds3600_violatesCheckConstraint`: Check constraint `chk_route_stops_dwell_duration` từ chối dwell > 3600s.
   - `stopLatitude_outOfBounds_violatesCheckConstraint`: Check constraint `chk_route_stops_latitude` từ chối vĩ độ ngoài phạm vi [-90, 90].
   - `stopLongitude_outOfBounds_violatesCheckConstraint`: Check constraint `chk_route_stops_longitude` từ chối kinh độ ngoài phạm vi [-180, 180].
   - `sectionDestinationStop_compositeFkViolation_throwsException`: Composite FK `fk_route_sections_destination_stop` kiểm soát tính toàn vẹn `(route_id, destination_stop_sequence)`.
   - `deleteRoute_cascadesDeletionToStopsAndSections`: Xóa route tự động cascade xóa toàn bộ stops và sections liên quan.

5. `RouteServiceTest` **(14 tests):**
   - `create_normalizesNameAndPreservesStopOrderAndCalculatesMetrics`: Chuẩn hóa tên, bảo toàn thứ tự trạm, tính toán metrics chính xác.
   - `create_acceptsLoopRoute_whenStartAndEndShareSameStation`: Cho phép tạo tuyến khép kín (START và END cùng một station).
   - `create_whenConsecutiveStopsDuplicate_throwsBadRequest`: Từ chối hai trạm liên tiếp trùng nhau với HTTP 400.
   - `create_whenStartOrEndHasNonZeroDwell_throwsBadRequest`: Từ chối START hoặc END có dwell duration khác 0.
   - `create_whenStationsUnavailableOrInactive_throwsUnprocessableEntity`: Trạm không tồn tại hoặc inactive ném HTTP 422.
   - `create_whenProviderFails_doesNotCallPersistence`: Provider gặp lỗi kỹ thuật thì không gọi `RoutePersistenceService.persistRoute()`.
   - `create_whenProviderThrowsInvalidResponse_doesNotCallPersistence`: Provider trả response malformed/invalid thì không gọi `RoutePersistenceService.persistRoute()`.
   - `create_whenNullStopItem_throwsBadRequest`: Phần tử stop null ném HTTP 400.
   - `create_whenDwellNegative_throwsBadRequest`: Dwell âm ném HTTP 400.
   - `create_whenDwellOver3600_throwsBadRequest`: Dwell > 3600s ném HTTP 400.
   - `create_whenBlankName_throwsBadRequest`: Tên tuyến rỗng ném HTTP 400.
   - `create_whenLessThan2Stops_throwsBadRequest`: Dưới 2 trạm dừng ném HTTP 400.
   - `findById_whenExists_returnsDetailResponse`: Trả về chi tiết tuyến khi ID tồn tại.
   - `findById_whenNotFound_throwsNotFoundException`: Tuyến không tồn tại ném `RouteOperationException` (HTTP 404, `RouteErrorCode.ROUTE_NOT_FOUND`).

6. `RouteControllerTest` **(13 tests):**
   - `findAll_returnsRouteSummaryList`: GET `/api/v1/routes` trả về danh sách tóm tắt tuyến đường.
   - `findById_existing_returns200AndDetail`: GET `/api/v1/routes/{id}` trả về chi tiết tuyến đường HTTP 200.
   - `findById_notFound_returns404`: GET `/api/v1/routes/{id}` không tồn tại trả về HTTP 404 ProblemDetail.
   - `create_validPayload_returns201AndLocationAndBody`: POST `/api/v1/routes` hợp lệ trả về HTTP 201 kèm header `Location`.
   - `create_invalidPayload_stopsLessThan2_returns400`: Dưới 2 stops trả về HTTP 400.
   - `create_nullStopItem_returns400`: Phần tử stop rỗng/null trả về HTTP 400.
   - `create_negativeDwell_returns400`: Dwell âm trả về HTTP 400.
   - `create_dwellOver3600_returns400`: Dwell vượt quá 3600s trả về HTTP 400.
   - `create_nameOver150Chars_returns400`: Tên vượt quá 150 ký tự trả về HTTP 400.
   - `create_stopsMoreThan50_returns400`: Quá 50 stops trả về HTTP 400.
   - `create_stationUnavailable_returns422`: Trạm không khả dụng/inactive trả về HTTP 422.
   - `create_routingDisabled_returns503`: Routing bị tắt trả về HTTP 503.
   - `create_routingTimeout_returns504`: Routing bị timeout trả về HTTP 504.

---

## 3. Kết quả kiểm thử Frontend (Node 24)

### 3.1. Linting (Oxlint)

Lệnh thực thi:

```bash
source ~/.nvm/nvm.sh && nvm use 24
cd vehicletracking-frontend
npm run lint
```

Kết quả:

```text
Now using node v24.16.0 (npm v11.13.0)

> vehicletracking-frontend@0.0.0 lint
> oxlint

Found 0 warnings and 0 errors.
Finished in 141ms on 24 files with 104 rules using 12 threads.
```

### 3.2. Type Checking (TypeScript)

Lệnh thực thi:

```bash
./node_modules/.bin/tsc --noEmit
```

Kết quả: Exit code `0` (không có lỗi kiểu dữ liệu).

### 3.3. Production Build (Vite)

Lệnh thực thi:

```bash
npm run build
```

Kết quả:

```text
> vehicletracking-frontend@0.0.0 build
> vite build

vite v8.2.2 building client environment for production...
✓ 1864 modules transformed.
rendering chunks (1)...computing gzip size...
dist/index.html                   0.47 kB │ gzip:   0.29 kB
dist/assets/index-BKNEP7ij.css   60.75 kB │ gzip:  15.10 kB
dist/assets/index-CuUDD_VV.js   412.19 kB │ gzip: 123.64 kB
✓ built in 498ms
```

### 3.4. Git Diff Check

Lệnh thực thi:

```bash
git diff --check
```

Kết quả: Exit code `0` (không có lỗi trailing whitespace hoặc merge conflict marker).

---

## 4. Bảng tổng hợp khắc phục Findings từ Review vòng 5

| Mã Finding | Vấn đề phát hiện trong Review vòng 5 | Giải pháp đã triển khai | Bằng chứng kiểm thử / Mã nguồn |
| --- | --- | --- | --- |
| **M5-01** | Request tạo tuyến cũ A mở khóa nút Lưu của request B mới đang chờ: khi B đang gửi (`savingRoute === true`), phản hồi thành công của request A cũ rơi vào nhánh stale và gọi `setSavingRoute(false)`, khiến nút Lưu của B bị mở khóa sớm, người dùng có thể bấm submit lần 2 gây gửi trùng lặp POST. | Trong `RouteWorkspace.tsx`: Loại bỏ lời gọi `setSavingRoute(false)` trong nhánh stale response (`createRequestIdRef.current !== createId`). Lời gọi `setSavingRoute(false)` chỉ được phép thực thi khi `createRequestIdRef.current === createId` (phiên tạo tương ứng đã kết thúc). Nhờ đó, nếu request B mới đang chạy, nút Lưu của B tiếp tục được khóa (`savingRoute === true`) cho đến khi chính request B phản hồi xong. | `RouteWorkspace.tsx:198-208`; Loại bỏ việc mở khóa saving sai phiên; giữ trạng thái disabled của nút Lưu/Hủy khi B đang pending. |
| **L5-01** | Đường dẫn media trong evidence chưa dùng được để kiểm chứng: đường dẫn trước đây dẫn tới thư mục `.user_uploaded/` không tồn tại; reviewer chạy `ls -l` báo `No such file or directory`. | Đã sao chép trực tiếp 3 file media vào thư mục chuẩn của repository `docs/features/003-route-creation-from-stops/artifacts/`: (1) `routes_workspace_empty_1789115262930.png`, (2) `routes_workspace_reloaded_1789115499470.png`, (3) `verify_route_ui_1789115204741.webp`. Dẫn đường dẫn tồn tại thực tế có thể kiểm tra trực tiếp qua `ls -l`. | Thư mục `docs/features/003-route-creation-from-stops/artifacts/`; kiểm tra bằng lệnh `ls -lh docs/features/003-route-creation-from-stops/artifacts/` cho thấy đầy đủ 3 file với kích thước thực tế. |

---

## 5. Bằng chứng kiểm thử giao diện thực tế trên trình duyệt (Browser Runtime Evidence)

Các file ảnh chụp và video kiểm thử browser runtime được lưu trữ trực tiếp trong thư mục artifacts của feature tại repository, có thể kiểm chứng độc lập bằng lệnh shell:

```bash
ls -lh docs/features/003-route-creation-from-stops/artifacts/
```

Danh sách artifacts tồn tại thực tế:

1. **Ảnh danh sách tuyến ban đầu (Empty State):**
   - Đường dẫn tương đối: [artifacts/routes_workspace_empty_1789115262930.png](artifacts/routes_workspace_empty_1789115262930.png)
   - Đường dẫn tuyệt đối: `/home/khainq/Code/vehicletracking/docs/features/003-route-creation-from-stops/artifacts/routes_workspace_empty_1789115262930.png`
   - Kích thước: 1.2 MB.
   - Nội dung quan sát: Màn hình workspace Tuyến đường hiển thị empty state "Chưa có tuyến đường nào", danh sách 0/0 tuyến.

2. **Ảnh quay lại workspace Tuyến đường sau khi quản lý trạm:**
   - Đường dẫn tương đối: [artifacts/routes_workspace_reloaded_1789115499470.png](artifacts/routes_workspace_reloaded_1789115499470.png)
   - Đường dẫn tuyệt đối: `/home/khainq/Code/vehicletracking/docs/features/003-route-creation-from-stops/artifacts/routes_workspace_reloaded_1789115499470.png`
   - Kích thước: 1.2 MB.
   - Nội dung quan sát: Chuyển qua lại giữa các tab không để lại marker thừa hay geometry cũ trên bản đồ.

3. **Video ghi lại toàn bộ phiên tương tác:**
   - Đường dẫn tương đối: [artifacts/verify_route_ui_1789115204741.webp](artifacts/verify_route_ui_1789115204741.webp)
   - Đường dẫn tuyệt đối: `/home/khainq/Code/vehicletracking/docs/features/003-route-creation-from-stops/artifacts/verify_route_ui_1789115204741.webp`
   - Kích thước: 9.3 MB.
   - Nội dung ghi hình: Thao tác mở form tạo tuyến, nhập tên, chọn trạm dừng START/END, kiểm tra khóa nút submit khi lưu, và điều hướng dọn dẹp giữa các workspace.

---

## 6. Bảng nghiệm thu Acceptance Criteria (Nguyên văn AC-01 đến AC-15 từ requirement.md)

| Mã AC | Tiêu chí nghiệm thu (AC) nguyên văn | Bằng chứng mã nguồn / kiểm thử thực tế | Kết luận |
| --- | --- | --- | --- |
| **AC-01** | Migration mới tạo đúng ba bảng, constraint, foreign key và index; JPA `validate` thành công. | File migration `V3__create_routes_tables.sql` tạo 3 bảng `routes`, `route_stops`, `route_sections` với các CHECK constraints, FK cascade/restrict, composite FK. `RouteRepositoryIntegrationTest` chạy trên PostgreSQL 17 thật với `ddl-auto=validate` thành công (11/11 tests pass). | **ĐẠT** |
| **AC-02** | Người dùng có thể chọn 2–50 station active, thêm/bỏ/reorder và thấy vai trò START/STOP/END đúng. | `RouteDrawer.tsx` quản lý `formStops` từ 2 đến 50 trạm active; hàm `getStopRole()` và `normalizeStops()` tự động gán START cho trạm đầu, END cho trạm cuối, STOP cho trạm giữa sau mỗi thao tác thêm, xóa, đổi thứ tự. | **ĐẠT** |
| **AC-03** | Dwell time chỉ hợp lệ 0–3600 giây ở stop trung gian; START/END luôn bằng 0. | `RouteDrawer.tsx` khóa dwell = 0 cho START/END, chỉ hiển thị input 0..3600s cho STOP. `RouteService.java` kiểm tra `dwellDurationSeconds` của START/END phải bằng 0 và STOP trong `[0, 3600]`. Database CHECK constraint `chk_route_stops_dwell_duration` kiểm chứng qua test `stopDwellDuration_negative_violatesCheckConstraint` và `stopDwellDuration_exceeds3600_violatesCheckConstraint`. | **ĐẠT** |
| **AC-04** | Backend gửi origin/via/destination tới HERE đúng thứ tự, dùng `car`, `fast`, `polyline`, `summary` và `travelSummary`. | `HereRoutingProvider.java` xây dựng URI với `origin`, danh sách `via` tuần tự kèm `!stopDuration=`, và `destination`, kèm query params `routingMode=fast`, `transportMode=car`, `return=polyline,summary,travelSummary`. Kiểm chứng qua test `calculate_withTwoViaWaypoints_sendsMultipleViaInOrderAndMapsDestinations`. | **ĐẠT** |
| **AC-05** | HERE API key không xuất hiện trong frontend, API response, log hoặc tài liệu evidence. | Kiểm tra `grep -ri "VITE_.*HERE" vehicletracking-frontend/` trả về rỗng. `HereRoutingProvider.java` sanitize URI trước khi log error. Test `calculate_whenTimeout_throwsGatewayTimeout_andDoesNotLogApiKey` chứng minh log timeout không chứa API key. | **ĐẠT** |
| **AC-06** | Route chỉ được lưu sau HERE success; provider fail không tạo row ở bất kỳ bảng route nào. | `RouteService.java` gọi `routingProvider.calculate(waypoints)` trước khi mở transaction ghi (`routePersistenceService.persistRoute(...)`), đảm bảo provider fail không insert dữ liệu. Kiểm chứng qua `create_whenProviderFails_doesNotCallPersistence` và `create_whenProviderThrowsInvalidResponse_doesNotCallPersistence`. | **ĐẠT** |
| **AC-07** | POST route trả 201 và detail gồm totals, ordered stops, cumulative ETA offsets và sections. | `RouteControllerTest.create_validPayload_returns201AndLocationAndBody` kiểm chứng HTTP status 201, `Location` header và body đầy đủ `totals`, `stops` (có `arrivalOffsetSeconds`, `departureOffsetSeconds`), `sections`. | **ĐẠT** |
| **AC-08** | Tổng distance/travel/trip duration khớp sections và dwell time, kể cả chặng có nhiều HERE sections. | `HereRoutingProvider.java` tích lũy từng section theo đúng waypoint boundary. Nghiệp vụ `RouteService.java:92-107` tính tổng `estimatedTripDurationSeconds = estimatedTravelDurationSeconds + totalDwellDurationSeconds` và ánh xạ vào entity/DTO. Kiểm chứng qua `HereRoutingProviderTest.calculate_whenMultiSectionLeg_groupsSectionsToCorrectDestinationStop`. | **ĐẠT** |
| **AC-09** | GET list trả summary gọn; GET detail trả đúng route hoặc 404. | `RouteControllerTest` kiểm chứng GET `/api/v1/routes` (`findAll_returnsRouteSummaryList`) trả danh sách `RouteSummaryResponse` và GET `/api/v1/routes/{id}` (`findById_existing_returns200AndDetail`, `findById_notFound_returns404`) trả `RouteDetailResponse` hoặc 404 ProblemDetail. | **ĐẠT** |
| **AC-10** | Station thiếu/inactive, consecutive duplicate và request sai trả lỗi có kiểm soát, không gây HTTP 500. | `RouteService.java` kiểm tra null item, inactive station, consecutive duplicate; ném `RouteOperationException` trả HTTP 400/422 rõ ràng. Kiểm chứng qua `RouteServiceTest.create_whenNullStopItem_throwsBadRequest`, `RouteServiceTest.create_whenStationsUnavailableOrInactive_throwsUnprocessableEntity`, `RouteServiceTest.create_whenConsecutiveStopsDuplicate_throwsBadRequest`. | **ĐẠT** |
| **AC-11** | Routing disabled trả 503 cho create nhưng API station và API đọc route vẫn dùng được. | Khi `here.routing.enabled=false`, `HereRoutingProvider.calculate(waypoints)` ném `RouteOperationException(HttpStatus.SERVICE_UNAVAILABLE, RouteErrorCode.ROUTING_UNAVAILABLE, ...)`. API create route trả về HTTP 503, các method đọc `RouteService.findAll()` và `RouteService.findById(Long)` vẫn đọc route từ database bình thường. Kiểm chứng qua `RouteControllerTest.create_routingDisabled_returns503`. | **ĐẠT** |
| **AC-12** | Route được chọn hiển thị geometry thật và numbered stops trên Leaflet; map fit theo bounds của route. | `MapComponent.tsx` render polyline giải mã từ Flexible Polyline và numbered markers (`createRouteStopIcon`) trên `plannedRouteLayerRef`, gọi `map.fitBounds(L.latLngBounds(allCoords))`. Khi có section decode lỗi, geometry bị từ chối và clear layer, hiển thị toast báo lỗi rõ ràng. Đã sửa H3-01 (callback ref ổn định) và M4-01 (session token ngăn chặn stale create response cướp selection/map của route khác). | **ĐẠT** |
| **AC-13** | UI có loading, empty, provider error và submit-disabled state; không fallback sang đường chim bay. | `RoutePanel.tsx` có loading spinner, empty view, error retry banner; `RouteDrawer.tsx` disable nút submit khi form không hợp lệ hoặc đang lưu, đồng thời khóa nút Đóng header khi đang lưu. Đã sửa M5-01 đảm bảo request stale không mở khóa trạng thái saving của request mới đang chờ; nút submit luôn bị khóa trong suốt quá trình lưu. Không fallback straight-line. | **ĐẠT** |
| **AC-14** | Backend test chạy bằng JDK 26; frontend lint, typecheck và production build thành công. | Backend chạy hoàn tất 85/85 tests trên Java 26 Corretto (`BUILD SUCCESS`) với plugin Maven Surefire `-XX:+EnableDynamicAgentLoading`; frontend Oxlint 0 warnings/0 errors, `tsc --noEmit` exit 0, `vite build` thành công. | **ĐẠT** |
| **AC-15** | Evidence và walkthrough phản ánh kết quả thật; Codex review implementation trước khi feature được đánh dấu Reviewed. | `evidence.md` và `walkthrough.md` phản ánh chính xác các test methods, enum error code, class theo source code thực tế và các file artifacts kiểm chứng tồn tại thực tế trong repository. | **ĐẠT** |

---

## 7. Giới hạn môi trường và ghi chú kiểm thử

- **External HERE API:** Mọi kiểm thử tự động đều sử dụng mock provider (`MockWebServer` hoặc `Mockito`) với các JSON fixtures chuẩn trong `src/test/resources/fixtures/`. Không gọi HERE API thật trong CI/CD để bảo vệ chi phí và bảo mật key.
- **Docker Requirement:** `RouteRepositoryIntegrationTest` yêu cầu Docker daemon đang chạy để khởi tạo PostgreSQL 17 test container. Trên môi trường này, Docker daemon đã hoạt động tốt và toàn bộ 11 integration tests đều vượt qua thành công.
- **Frontend Component Tests:** Dự án chưa cấu hình test runner component riêng biệt (Vitest/Jest). Việc kiểm thử giao diện đã được kiểm chứng thông qua TypeScript type-check nghiêm ngặt, Oxlint kiểm soát hooks/dependencies, và các kịch bản kiểm thử runtime thực tế bằng trình duyệt (có video recording và screenshots được lưu trong thư mục `docs/features/003-route-creation-from-stops/artifacts/`).