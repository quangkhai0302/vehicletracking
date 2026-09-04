# Plan — 002-route-management

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | 002-route-management |
| Trạng thái | AWAITING USER APPROVAL |
| Điều kiện bắt đầu | Người dùng review/phê duyệt bộ tài liệu feature 002 |

## Ranh giới implementation

- Gemini chỉ triển khai sau khi người dùng phê duyệt Plan này.
- Không đổi schema/migration, không thêm dependency frontend, không đụng simulator/WebSocket/traffic API.
- Mọi thao tác source code phải bám SPEC-001..SPEC-007; khi cần đổi thiết kế phải cập nhật docs trước, không tự mở rộng scope.

## Các bước implementation đề xuất cho Gemini

### P-001 — Hoàn thiện request/query contract route

**Mục tiêu:** Tạo input validation và repository query cần cho update/delete an toàn.

**Files dự kiến:**

- Sửa `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/dto/RouteRequestDto.java`.
- Sửa `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/repository/RouteRepository.java`.
- Sửa `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/repository/TripRepository.java`.

**Thay đổi:**

- Validation `name`, `stationIds` và phần tử station IDs theo BR-001/BR-003.
- Thêm query kiểm tra code trùng, loại trừ route hiện đang update.
- Thêm `existsByRouteId(Long routeId)` cho Trip.
- Không đưa logic nghiệp vụ START/END vào controller/DTO; logic thuộc service.

**Liên kết:** REQ-002..REQ-005; SPEC-002, SPEC-003.

### P-002 — Cài đặt lifecycle create/update/delete trong RouteService

**Mục tiêu:** Centralize validation, chuẩn hóa code, tính metric và transaction an toàn.

**Files dự kiến:**

- Sửa `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/service/RouteService.java`.
- Có thể dùng lại `RouteStationRepository.java` hiện có; chỉ sửa khi cần hỗ trợ thao tác thay collection an toàn.

**Thay đổi:**

- Tách helper đọc/validate station list và helper xây RouteStation/metric để POST/PUT không lệch rule.
- Kiểm tra `START → STOP* → END`, ID tồn tại, và code normalized uniqueness trước mutation.
- Bổ sung `updateRoute(Long id, RouteRequestDto request)` dùng `@Transactional`; route có Trip trả conflict trước khi thay collection.
- Thay thế collection theo BR-005 và chứng minh bằng test rằng không xảy ra xung đột `(route_id, stop_order)` khi đổi thứ tự.
- Sửa delete để kiểm tra route tồn tại và không được Trip tham chiếu trước delete.
- Giữ response DTO và công thức metric hiện có; không mô tả kết quả là traffic ETA.

**Liên kết:** REQ-002..REQ-005; SPEC-001..SPEC-005.

### P-003 — Bổ sung REST endpoint và error contract scope route

**Mục tiêu:** Expose PUT và các phản hồi 400/404/409 ổn định mà không làm regression Station.

**Files dự kiến:**

- Sửa `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/controller/RouteController.java`.
- Thêm `RouteNotFoundException.java`, `RouteConflictException.java`, `RouteExceptionHandler.java` trong `vehiceltracking-backend/src/main/java/com/quangkhai/vehiceltracking_backend/exception/` (hoặc một handler chung chỉ khi test chứng minh Station không regression).

**Thay đổi:**

- Thêm `PUT /api/routes/{id}` và HTTP status theo SPEC-004.
- Chuyển các failure semantics thành exception có nghĩa, map sang ProblemDetail 400/404/409 theo SPEC-005.
- Giữ `StationExceptionHandler` và behavior Station độc lập nếu chọn handler Route-specific.

**Liên kết:** REQ-003..REQ-005; SPEC-004, SPEC-005.

### P-004 — Viết test tự động backend trước/sát implementation

**Mục tiêu:** Chứng minh toàn bộ API và rule nghiệp vụ, bao gồm các nhánh bảo vệ dữ liệu.

**Files dự kiến:**

- Thêm `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/service/RouteServiceTest.java`.
- Thêm `vehiceltracking-backend/src/test/java/com/quangkhai/vehiceltracking_backend/controller/RouteControllerTest.java`.
- Sửa test fixture chung chỉ khi thực sự cần thiết và không làm yếu test khác.

**Thay đổi:**

- Bao phủ TC-001 đến TC-009, đặc biệt update topology, lỗi START/END, `404`, unique code case-insensitive và `409` khi có Trip.
- Dùng dữ liệu riêng/unique code, không phụ thuộc thứ tự DataSeeder.

**Liên kết:** REQ-006; Test plan TC-001..TC-009.

### P-005 — Hoàn thiện client API route

**Mục tiêu:** Frontend gọi được PUT và diễn giải lỗi backend nhất quán.

**Files dự kiến:**

- Sửa `vehicletracking-frontend/src/services/api.ts`.
- Chỉ sửa `vehicletracking-frontend/src/types/index.ts` nếu TypeScript thiếu type cho request/response; không tạo duplicate type nếu `Route` hiện có đủ dùng.

**Thay đổi:**

- Bổ sung `updateRoute(id, data)` gọi PUT.
- Áp dụng parser lỗi thống nhất cho create/update/delete route, ưu tiên ProblemDetail `detail` mà vẫn xử lý fallback an toàn.
- Giữ base URL/config qua environment hiện có; không thêm key vào source.

**Liên kết:** REQ-002..REQ-005; SPEC-004, SPEC-005.

### P-006 — Tạo RouteModal và tích hợp state của App

**Mục tiêu:** Cung cấp CRUD/sắp xếp route trong UI mà không thay đổi behavior Trip/simulator ngoài scope.

**Files dự kiến:**

- Thêm `vehicletracking-frontend/src/components/RouteModal.tsx`.
- Sửa `vehicletracking-frontend/src/App.tsx`.
- Sửa `vehicletracking-frontend/src/components/SimulatorPanel.tsx` để có callback/nút mở modal.

**Thay đổi:**

- Lấy `stations` đã có từ App để form thêm/xóa/lên/xuống theo danh sách có thứ tự.
- Local validation đối chiếu START/STOP/END trước submit, hiển thị lỗi API, disable action trùng lặp khi pending, và yêu cầu xác nhận trước delete.
- Thêm handlers refresh route sau mutation thành công. Cập nhật `selectedRoute` chỉ khi route đó chính là route hiện được giữ trong state; không thêm route switcher toàn cục hay đổi Trip/simulator.
- Giữ cách style/component convention hiện có, không thêm thư viện drag-drop.

**Liên kết:** REQ-001..REQ-004; SPEC-006.

### P-007 — Xác minh, thu thập Evidence và chuẩn bị review

**Mục tiêu:** Biến kết quả thực chạy thành bằng chứng có thể kiểm tra.

**Commands bắt buộc:**

```bash
cd vehiceltracking-backend && ./mvnw clean test
cd vehicletracking-frontend && npm run lint
cd vehicletracking-frontend && npm run build
git diff --check
```

**Thay đổi tài liệu:**

- Cập nhật `docs/features/002-route-management/evidence.md` với command, thời điểm, output/đường dẫn artifact, liên kết TC và trạng thái thật.
- Chỉ đánh dấu EVD-013/014/015/016 PASS khi kết quả thực tế tương ứng pass; môi trường không chạy được phải là INCONCLUSIVE và nêu lý do.
- Không sửa `review.md` để tự approve. Gemini dừng sau Evidence để Codex review.

**Liên kết:** REQ-006; SPEC-007; TC-001..TC-012.

## Thứ tự và rủi ro

1. P-001 → P-003 → P-004 trước, để contract/error/data integrity có test backend.
2. P-005 → P-006 sau khi API ổn định.
3. P-007 là bắt buộc trước khi gửi Codex review.

| Rủi ro | Giảm thiểu |
|---|---|
| Thay collection có thể va unique stopOrder trong cùng flush | Test TC-006; dùng transaction và thứ tự xóa orphan/flush rõ ràng nếu cần. |
| Sửa route làm lệch check-in Trip | Kiểm tra `existsByRouteId` trước PUT/DELETE; test TC-007/008. |
| Handler mới ảnh hưởng lỗi Station | Scope handler cho RouteController hoặc có regression test rõ ràng. |
| UI tin validation client | Backend luôn validate cùng rule; TC-003 kiểm tra trực tiếp API. |
| Lint/build chưa chứng minh interaction | Có TC-010 manual evidence tách riêng; không nâng cấp thành PASS nếu chưa thực hiện. |

## Điều kiện bàn giao cho Codex review

- Git diff chỉ chứa source/test/doc trong scope P-001..P-007.
- Tất cả command bắt buộc đã chạy hoặc ghi INCONCLUSIVE cùng nguyên nhân.
- `evidence.md` và Evidence Matrix đầy đủ đường dẫn REQ → SPEC → TC → EVD.
- Gemini không ghi APPROVED; Codex là bên duy nhất cập nhật kết luận trong `review.md`.
