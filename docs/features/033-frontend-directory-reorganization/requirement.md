# 033 — Tái cấu trúc thư mục Frontend theo kiến trúc Feature-Driven

Trạng thái: **Verified**. Tất cả các tiêu chí chấp nhận đã hoàn thành và kiểm chứng tự động.

## Bối cảnh và mục tiêu

Mã nguồn frontend Vue 3 hiện tại đã hoàn thành chuyển đổi từ React (Feature 032), tuy nhiên cấu trúc thư mục đang ở trạng thái lai tạp (hybrid) giữa Layer-based (chia theo tầng kỹ thuật phẳng) và Feature-based nửa vời:
- Backend đã tổ chức chặt chẽ theo từng package nghiệp vụ (`station`, `route`, `fleet`, `trip`, `telemetry`, `traffic`, `simulation` theo quy ước tại [AGENTS.md mục 2](file:///home/khainq/Code/vehicletracking/AGENTS.md#L18-L23)).
- Trong khi đó, frontend gom toàn bộ vào các thư mục phẳng cấp ứng dụng: 17 file API services tại [src/services/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services), 16 file kiểu dữ liệu tại [src/types/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types), 15 file hooks tại [src/composables/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables).
- Component vừa nằm phẳng ở root của [src/components/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components) vừa có thư mục domain lẻ tẻ. Thư mục [src/components/business/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/business) chứa lẫn lộn từ UI layout chung đến nghiệp vụ xe và lịch trình.
- Xuất hiện God Component [MapComponent.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.vue) (1.084 dòng) và Mega Composable [useMapLayers.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useMapLayers.ts) (hơn 20KB).
- Tài liệu định hướng thiết kế giao diện [docs/design.md mục 29](file:///home/khainq/Code/vehicletracking/docs/design.md#L577-L593) đã quy định: *"Ưu tiên feature-based structure: features/stations, routes, vehicles, trips, tracking, simulator, traffic"*, nhưng source code chưa được đồng bộ theo thiết kế này.

**Mục tiêu chính:**
1. Tổ chức lại toàn bộ mã nguồn frontend theo mô hình **Feature-Driven / Co-location Architecture**, gom toàn bộ components, composables, services (api), types, utils thuộc về một domain vào thư mục của feature đó.
2. Tách bạch lớp dùng chung toàn hệ thống (`src/shared/` hoặc `src/core/`) gồm các thành phần không phụ thuộc nghiệp vụ cụ thể: HTTP client, reusable UI atoms/molecules, generic utilities, global design tokens.
3. Thiết lập Path Alias `@/` trỏ tới `src/` trong [vite.config.js](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/vite.config.js) và [tsconfig.json](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tsconfig.json) để loại bỏ các import relative phức tạp (`../../../`).
4. Giữ **nguyên vẹn 100%** giao diện người dùng, hành vi nghiệp vụ, luồng API, và đảm bảo toàn bộ bộ test tự động (`lint`, `typecheck`, `test:unit`, `test:motion`, `build`) đều pass.

## Phạm vi

### In scope

- Tái cấu trúc cây thư mục trong [vehicletracking-frontend/src/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src):
  - Khởi tạo thư mục `shared/` cho HTTP client, generic UI, composable dùng chung, styles token và generic utils.
  - Phân chia `features/`: `auth`, `stations`, `routes`, `fleet`, `tracking`, `simulation`, `traffic`, `schedules`, `reports`, `map`.
  - Giữ lại `pages/` như các trang mỏng (thin page components) chỉ đóng vai trò entry point điều hướng cho Vue Router.
  - Giữ và chuẩn hóa `app/` cho ApplicationShell, Router và Navigation.
- Cấu hình alias `@/` trong Vite, TypeScript và Vitest.
- Cập nhật toàn bộ các câu lệnh `import` trong mã nguồn `src/` và `tests/`.
- Phân tách và dọn dẹp các thư mục/file lai tạp ([src/components/business/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/business), [src/components/operations/ConfirmStationDelete.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/operations/ConfirmStationDelete.vue)).
- Phân nhóm utils nghiệp vụ (`vehicleMotion.ts`, `simulationFleet.ts`, `tripTraffic.ts`) về đúng feature.

### Out of scope

- Không thay đổi backend, API endpoints, DTO, schema cơ sở dữ liệu hay Flyway migration.
- Không thay đổi thiết kế giao diện (UI layout, màu sắc, font chữ, breakpoints) so với hiện tại.
- Không thêm mới tính năng nghiệp vụ hoặc sửa đổi quy tắc nghiệp vụ.
- Không thay thế Leaflet bằng thư viện bản đồ khác.
- Không xóa các test case hiện có; chỉ cập nhật import path trong test để phù hợp cấu trúc mới.

## Actor và luồng chính

- **Lập trình viên / Maintainer:** Dễ dàng định vị toàn bộ mã nguồn liên quan đến một tính năng trong một thư mục duy nhất; bổ sung hoặc loại bỏ tính năng mà không gây ảnh hưởng chéo.
- **CI / Build Pipeline:** Chạy linting, typechecking, unit test, motion test và production build với kết quả không đổi và thời gian build tương đương hoặc tối ưu hơn.
- **Người dùng cuối (Admin / Dispatcher / Driver):** Trải nghiệm ứng dụng không thay đổi, không phát sinh lỗi giao diện hoặc mất mát dữ liệu phiên làm việc.

## Yêu cầu chức năng và phi chức năng

- **FR1 — Cấu trúc Feature-Driven nhất quán:** Toàn bộ component, api client, composable, type của từng nghiệp vụ (`stations`, `routes`, `fleet`, `tracking`, `simulation`, `traffic`, `schedules`, `reports`, `auth`, `map`) phải được co-locate trong thư mục `src/features/<feature-name>/`.
- **FR2 — Tách biệt lớp Shared:** Các thành phần phi nghiệp vụ (`http.ts`, `PageHeading.vue`, `SidePanel.vue`, `format.ts`, `useCompactLayout.ts`, design tokens) phải nằm trong `src/shared/`.
- **FR3 — Thiết lập Path Alias `@/`:** Cấu hình `@/` trỏ tới `src/` trên toàn bộ toolchain (Vite, TypeScript, Vitest).
- **FR4 — Chuẩn hóa Import Boundary:** Áp dụng quy tắc: import nội bộ feature dùng relative path (`./`, `../`), import xuyên module (cross-feature) hoặc dùng shared layer thì sử dụng `@/features/...` hoặc `@/shared/...`.
- **FR5 — Giữ nguyên Route Contract:** Toàn bộ route, query parameter, lazy chunking và route guards trong [src/app/router.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/app/router.ts) giữ nguyên contract.
- **NFR1 — UI Parity & Zero Visual Regression:** Giao diện hiển thị, animation, icon, layout không có bất kỳ sai khác nào.
- **NFR2 — Parity về vòng đời tài nguyên:** Các listener bản đồ, SSE subscription, timer của simulator không bị rò rỉ hoặc nhân đôi.
- **NFR3 — Kiểm thử và Build sạch:** `npm run lint`, `npm run typecheck`, `npm run test:unit`, `npm run test:motion`, `npm run build` phải kết thúc với exit code 0, không có warning/error mới.
- **NFR4 — Không tạo code rác/file mồ côi:** Sau khi di dời, toàn bộ các file cũ và thư mục rỗng phải được dọn dẹp sạch sẽ.

## Acceptance Criteria

| AC | Điều kiện chấp nhận |
|---|---|
| **AC1** | Thư mục `src/shared/` được tạo và chứa đầy đủ: API client ([http.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services/http.ts)), generic UI components ([PageHeading.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/business/PageHeading.vue), [SidePanel.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/business/SidePanel.vue)), generic composables ([useCompactLayout.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables/useCompactLayout.ts)), generic utils ([format.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/utils/format.ts)). |
| **AC2** | Thư mục `src/features/` chứa đầy đủ 10 modules: `auth`, `stations`, `routes`, `fleet`, `tracking`, `simulation`, `traffic`, `schedules`, `reports`, `map`. Mỗi module có cấu trúc con chuẩn hóa (`api/`, `components/`, `composables/`, `types/`, `utils/`). |
| **AC3** | Các thư mục phẳng phân mảnh ở root gồm [src/services/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/services), [src/types/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/types), [src/composables/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/composables), [src/components/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components) và [src/utils/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/utils) được di dời triệt để vào `shared/` hoặc `features/`. Không còn file mồ côi hoặc thư mục rác như `components/business/`. |
| **AC4** | Path alias `@/*` được cấu hình đồng bộ trong [vite.config.js](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/vite.config.js), [tsconfig.json](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tsconfig.json), [vitest.config.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/vitest.config.ts). |
| **AC5** | Toàn bộ 14 test file và 85 test case trong [tests/unit/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit) cùng motion tests trong [tests/vehicleMotion.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/vehicleMotion.test.ts) chạy thành công 100%. |
| **AC6** | `npm run lint`, `npm run typecheck` và `npm run build` chạy thành công với 0 warning, 0 error. Bundle sinh ra hoạt động chính xác. |
| **AC7** | Tài liệu kiến trúc và hướng dẫn dự án ([docs/design.md](file:///home/khainq/Code/vehicletracking/docs/design.md), [AGENTS.md](file:///home/khainq/Code/vehicletracking/AGENTS.md)) được cập nhật đồng bộ với cấu trúc thư mục mới. |

## Giả định và rủi ro

- **Rủi ro vỡ đường dẫn import trong test:** Nhiều file test đang import relative từ `../../src/services/...`. Khi di chuyển file, cần cập nhật tương ứng sang `@/features/...` hoặc relative mới.
- **Rủi ro chu trình phụ thuộc (Circular Dependency):** Khi gom các composables và types vào features, cần kiểm tra không để feature A import feature B và ngược lại tạo thành vòng lặp. Các types dùng chung nhiều nơi cần đặt ở `shared/types` hoặc module chủ quản rõ ràng.
- **Môi trường thực thi:** Kiểm thử và build frontend yêu cầu Node 24 (`v24.16.0`) theo đúng `.nvmrc` đã thiết lập.
