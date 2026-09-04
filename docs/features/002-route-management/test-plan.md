# Test Plan — 002-route-management

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | 002-route-management |
| Trạng thái | DRAFT — chạy sau implementation |
| Liên kết | `requirement.md`, `spec.md`, `evidence.md` |

## Môi trường và quy ước

- Backend test chạy từ `vehiceltracking-backend` với `./mvnw clean test`.
- Frontend chạy từ `vehicletracking-frontend` với `npm run lint` và `npm run build`.
- Không có script frontend test tại baseline (Survey EVD-012); các luồng UI được manual verify sau build/dev server.
- Mỗi test dùng dữ liệu độc lập/unique code để không phụ thuộc seed data.
- Gemini lưu output đầy đủ vào `docs/features/002-route-management/artifacts/` hoặc ghi command/output trực tiếp trong Evidence. Không tạo evidence PASS trước khi lệnh thực sự chạy.

## Test cases backend

### TC-001 — Tạo route hợp lệ theo START → STOP* → END

- **Liên kết:** REQ-001, REQ-002; SPEC-001, SPEC-002, SPEC-004; AC-001.
- **Loại:** Service unit + controller integration.
- **Given:** Một START, một STOP và một END tồn tại.
- **When:** Gửi POST với thứ tự `[startId, stopId, endId]`.
- **Then:** Nhận `201`; response có stopOrder `1,2,3`, đúng các station tương ứng, metrics đã tính và route có thể GET lại cùng thứ tự.
- **Evidence dự kiến:** EVD-013.

### TC-002 — Chặn request cấu trúc không hợp lệ

- **Liên kết:** REQ-002, REQ-005; SPEC-002, SPEC-005; AC-002.
- **Loại:** Controller integration.
- **Given:** Các body thiếu `name`, thiếu/rỗng `stationIds`, ít hơn hai IDs hoặc có station ID null.
- **When:** POST/PUT request.
- **Then:** `400`, response có thông tin lỗi an toàn; database không có route/route station dở dang.
- **Evidence dự kiến:** EVD-013.

### TC-003 — Chặn thứ tự hoặc loại trạm sai

- **Liên kết:** REQ-002, REQ-003, REQ-005; SPEC-002, SPEC-005; AC-002.
- **Loại:** Service unit + controller integration.
- **Given:** Các tổ hợp START không ở đầu, END không ở cuối hoặc START/END ở giữa.
- **When:** POST/PUT request.
- **Then:** `400`; dữ liệu route trước update không đổi.
- **Evidence dự kiến:** EVD-013.

### TC-004 — Báo không tìm thấy route/trạm

- **Liên kết:** REQ-002, REQ-003, REQ-005; SPEC-002, SPEC-005.
- **Loại:** Controller integration.
- **Given:** ID route hoặc station không tồn tại.
- **When:** Gọi GET/PUT/DELETE route hoặc POST/PUT kèm station ID không tồn tại.
- **Then:** `404` với thông điệp phù hợp; không mutate dữ liệu.
- **Evidence dự kiến:** EVD-013.

### TC-005 — Chuẩn hóa và bảo vệ unique code

- **Liên kết:** REQ-002, REQ-005; SPEC-002, SPEC-005.
- **Loại:** Service unit + controller integration.
- **Given:** Route code `R-HCM-01` đã tồn tại.
- **When:** Tạo route với `r-hcm-01` hoặc update route khác thành giá trị đó.
- **Then:** Backend chuẩn hóa code và trả `409`; update cùng chính route với code không đổi vẫn hợp lệ.
- **Evidence dự kiến:** EVD-013.

### TC-006 — Sửa route chưa được dùng và tính lại metric

- **Liên kết:** REQ-003, REQ-005; SPEC-003, SPEC-004; AC-003.
- **Loại:** Service unit + controller integration.
- **Given:** Một route không có Trip tham chiếu.
- **When:** PUT metadata và danh sách hợp lệ với các STOP được sắp lại/thay thế.
- **Then:** `200`; ID/createdAt không đổi; collection cũ không còn; thứ tự/metric phản hồi đúng công thức và không vi phạm unique `(route_id, stop_order)`.
- **Evidence dự kiến:** EVD-013.

### TC-007 — Không sửa route đã có Trip

- **Liên kết:** REQ-004, REQ-005; SPEC-003, SPEC-005; AC-004.
- **Loại:** Service unit + controller integration.
- **Given:** Một Trip đã tham chiếu route.
- **When:** PUT route đó.
- **Then:** `409`; metadata, RouteStation, metric của route không thay đổi.
- **Evidence dự kiến:** EVD-013.

### TC-008 — Xóa route an toàn

- **Liên kết:** REQ-004, REQ-005; SPEC-004, SPEC-005; AC-004, AC-005.
- **Loại:** Controller integration.
- **Given:** (a) route không có Trip, (b) route có Trip.
- **When:** DELETE từng route.
- **Then:** (a) `204` và GET sau đó `404`; (b) `409`, route/Trip còn nguyên.
- **Evidence dự kiến:** EVD-013.

### TC-009 — Regression endpoint đọc

- **Liên kết:** REQ-001, REQ-005; SPEC-001, SPEC-004.
- **Loại:** Controller integration.
- **Given:** Route có nhiều RouteStation được lưu khác thứ tự insert.
- **When:** Gọi GET list và detail.
- **Then:** Contract response tương thích và `stations` luôn tăng theo `stopOrder`.
- **Evidence dự kiến:** EVD-013.

## Test cases frontend và kiểm tra thủ công

### TC-010 — Luồng RouteModal

- **Liên kết:** REQ-001, REQ-002, REQ-003, REQ-004; SPEC-006; AC-001, AC-003, AC-004, AC-005.
- **Loại:** MANUAL/UI.
- **Given:** Backend feature đã chạy và có trạm START/STOP/END.
- **When:** Mở quản lý tuyến; tạo route; dùng các nút thêm/xóa/lên/xuống; sửa route chưa có Trip; thử tạo thứ tự sai; xóa route trống; thử sửa/xóa route đang có Trip.
- **Then:** Form gửi đúng thứ tự, validation lỗi hiển thị, dữ liệu danh sách refresh sau success, còn giữ được ngữ cảnh sau fail, và thông báo `409` hiển thị được.
- **Evidence dự kiến:** EVD-016 (ảnh hoặc các bước/manual result có ngày giờ).

### TC-011 — Lint frontend

- **Liên kết:** REQ-006; SPEC-007; AC-006.
- **Loại:** LINT.
- **Command:** `npm run lint` trong `vehicletracking-frontend`.
- **Then:** Exit code 0.
- **Evidence dự kiến:** EVD-014.

### TC-012 — Build/type check frontend

- **Liên kết:** REQ-006; SPEC-007; AC-006.
- **Loại:** BUILD / TYPE_CHECK.
- **Command:** `npm run build` trong `vehicletracking-frontend`.
- **Then:** Exit code 0; Vite/TypeScript hoàn tất build.
- **Evidence dự kiến:** EVD-015.

## Lệnh xác minh bắt buộc sau implementation

```bash
cd vehiceltracking-backend && ./mvnw clean test
cd vehicletracking-frontend && npm run lint
cd vehicletracking-frontend && npm run build
git diff --check
```

`git diff --check` chỉ phát hiện lỗi whitespace; không thay thế test/lint/build/API verification.

## Exit criteria

- TC-001 đến TC-009 pass bằng test thực chạy.
- TC-010 có manual evidence; nếu không thể chạy browser/backend thì ghi INCONCLUSIVE và lý do.
- TC-011, TC-012 pass bằng output lệnh thực tế.
- Evidence Matrix liên kết mọi REQ Must với SPEC, TC và EVD. Không có PASS suy đoán.
