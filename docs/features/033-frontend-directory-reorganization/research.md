# 033 — Nghiên cứu Kiến trúc Tái cấu trúc Thư mục Frontend

## 1. So sánh mô hình tổ chức mã nguồn Vue 3

### 1.1 Layer-based (Technical-type Architecture) — Hiện trạng

* **Cấu trúc:** Gom file theo tính chất kỹ thuật (`components/`, `services/`, `composables/`, `types/`, `utils/`).
* **Ưu điểm:** Dễ hiểu ban đầu với dự án nhỏ (dưới 5 màn hình).
* **Nhược điểm nghiêm trọng khi dự án phình to (như hiện tại với 12 pages, 17 services, 16 types, 15 composables):**
  * **Low Cohesion (Tính gắn kết thấp):** Một thay đổi về trạm dừng (`station`) đòi hỏi lập trình viên phải mở đồng thời 5 thư mục cách xa nhau: `src/services/stations.ts`, `src/types/station.ts`, `src/composables/useStationWorkspace.ts`, `src/components/StationPanel.vue`, `src/components/StationDrawer.vue`.
  * **High Cognitive Load (Tải nhận thức cao):** Cây thư mục quá phẳng, mỗi thư mục chứa 15–20 file không có phân nhóm, khó phân biệt file nào thuộc màn hình nào.
  * **Khó dọn dẹp code chết (Dead code accumulation):** Khi gỡ bỏ một tính năng, rất dễ bỏ sót các file DTO/type hoặc service mồ côi nằm rải rác ở các thư mục dùng chung.
  * **Bất đồng bộ với Backend:** Backend đã áp dụng hoàn chỉnh *Package-by-Feature* ([AGENTS.md mục 2](file:///home/khainq/Code/vehicletracking/AGENTS.md#L18-L23)). Việc frontend đi theo mô hình layer-based gây ra sự lệch pha về mô hình tư duy giữa hai phía.

### 1.2 Feature-Driven / Domain-Driven Architecture — Đề xuất

* **Cấu trúc:** Gom toàn bộ tài nguyên (UI, API client, State/Composable, Types, Domain Utils) phục vụ một nghiệp vụ vào một thư mục đại diện (`src/features/<feature-name>/`).
* **Ưu điểm:**
  * **High Cohesion & Low Coupling:** Mã nguồn thay đổi cùng nhau được đặt cạnh nhau (nguyên lý *Co-location*).
  * **Ranh giới trách nhiệm rõ ràng (Clear Module Boundaries):** Mỗi feature tự chịu trách nhiệm về giao diện, trạng thái và API contract của mình.
  * **Dễ mở rộng và kiểm thử độc lập:** Dễ dàng viết unit test tương ứng ngay cạnh component hoặc trong thư mục feature.
  * **Đồng nhất với [docs/design.md mục 29](file:///home/khainq/Code/vehicletracking/docs/design.md#L577-L593):** Đúng định hướng kiến trúc đã được quy định trong tài liệu thiết kế của dự án.

---

## 2. Nghiên cứu cấu hình Path Alias (`@/*`)

### 2.1 Cấu hình Vite ([vite.config.js](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/vite.config.js))

Sử dụng hàm chuẩn của Node ESM `fileURLToPath` và `URL` để định nghĩa alias:

```js
import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: { port: 5173, strictPort: true },
})
```

### 2.2 Cấu hình TypeScript ([tsconfig.json](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tsconfig.json))

Thêm `baseUrl` và `paths` vào `compilerOptions`:

```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  }
}
```

### 2.3 Cấu hình Vitest ([vitest.config.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/vitest.config.ts))

Vitest sử dụng cấu hình tương tự Vite. Đảm bảo cấu hình alias được khai báo hoặc kế thừa tự động để các file trong [tests/unit/](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/unit) nhận diện được `@/`.

### 2.4 Lưu ý về `tests/vehicleMotion.test.ts`

Kiểm tra lệnh kiểm thử motion:
`node --test tests/vehicleMotion.test.ts`
Test này được chạy trực tiếp bằng Node.js test runner (`node --test`), không qua Vite bundler. Do đó, trong các file utility chạy trực tiếp dưới Node CLI mà không có loader phân giải alias, cần giữ import relative hoặc kiểm tra khả năng tương thích. Khảo sát cho thấy [tests/vehicleMotion.test.ts](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/tests/vehicleMotion.test.ts#L4) import `../src/utils/vehicleMotion.ts`. Khi di chuyển `vehicleMotion.ts` sang `features/fleet/utils/vehicleMotion.ts`, test này chỉ cần cập nhật đường dẫn relative tương ứng.

---

## 3. Nghiên cứu phân ranh giới và bóc tách các thành phần lớn

### 3.1 Phân định lớp `shared/` vs `features/`

| Tiêu chí | `shared/` | `features/<name>/` |
|---|---|---|
| **Nghiệp vụ xe/tuyến/trạm?** | Tuyệt đối không | Chứa logic nghiệp vụ cốt lõi |
| **Phụ thuộc vào feature khác?** | Không bao giờ import từ `features/` | Có thể import từ `shared/` |
| **Khả năng tái sử dụng** | Dùng chung toàn ứng dụng | Chủ yếu phục vụ màn hình của feature đó |
| **Ví dụ thành phần** | `http.ts`, `PageHeading.vue`, `SidePanel.vue`, `format.ts`, `useCompactLayout.ts` | `useStationWorkspace.ts`, `RouteDrawer.vue`, `FleetWorkspace.vue` |

### 3.2 Quy tắc giải quyết God Component [MapComponent.vue](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/components/MapComponent.vue)

Hiện tại `MapComponent.vue` gánh vác:
1. Vòng đời Leaflet Map (tạo map container, set view, zoom, camera animation).
2. Xử lý các mode làm việc khác nhau: `stations`, `routes`, `tracking`, `simulation`.
3. Nhúng trực tiếp toàn bộ các panel và layer con.

**Hướng xử lý an toàn:**
- Bước 1 (Trong phạm vi feature 033): Đưa `MapComponent.vue` và các thành phần cốt lõi của Leaflet (như `MapControls.vue`, `useMapCamera.ts`, `polyline.ts`) vào `src/features/map/`.
- Cập nhật import của `MapComponent.vue` để trỏ tới các component con nằm trong `features/stations/`, `features/routes/`, `features/fleet/`, `features/simulation/`, `features/traffic/`.
- Bước 2 (Giai đoạn tiếp theo sau khi ổn định cấu trúc thư mục): Bóc tách các layer thành plugin/slot động để `MapComponent` chỉ giữ trách nhiệm điều phối Leaflet Map Container.

---

## 4. Nghiên cứu quản lý CSS và Scoped Styles

* Hiện tại, các file CSS lớn ở root: [src/index.css](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/index.css), [src/workspace.css](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/workspace.css), [src/ui-refresh.css](file:///home/khainq/Code/vehicletracking/vehicletracking-frontend/src/ui-refresh.css) chứa toàn bộ design system token và style nền tảng.
* Các file CSS gắn liền với feature (`fleet.css`, `route.css`, `simulator.css`, `traffic.css`) sẽ được di dời vào thư mục của feature tương ứng (`src/features/<feature>/styles/` hoặc import trực tiếp trong component chính của feature).
* Các file CSS gắn liền với page (`schedule-management.css`, `alerts-management.css`, `driver-portal.css`...) được đưa vào cùng thư mục với page hoặc vào feature tương ứng, chuẩn bị cho bước scoped hoá.
