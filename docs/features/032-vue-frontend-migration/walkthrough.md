# Walkthrough — 032, frontend Vue mặc định

Đã cutover theo xác nhận của người dùng ngày23/09: giao diện Vue được chấp nhận và cho phép gỡ React. Entry mặc định là `index.html` → `src/main.ts` → `App.vue`/`app/router.ts`. Source/dependency/plugin/types React đã gỡ; không có React bridge. Feature vẫn **Implementing** vì browser/container regression đầy đủ còn thiếu; không coi chấp nhận giao diện thủ công là kết quả pixel test.

Kết quả và giới hạn: [evidence.md](evidence.md). Mapping source: [migration-inventory.md](verification/migration-inventory.md). Tự review: [review.md](review.md).

## Chạy ứng dụng

Dùng Node24 theo `.nvmrc`, chạy từ `vehicletracking-frontend/`:

```bash
npm ci
npm run dev
```

Mở **http://localhost:5173**. `dev:vue` là alias của `dev`; không còn dùng cổng5176/cấu hình test. Dừng frontend cũ trước khi chạy; port5173 strict không tự chuyển sang cổng khác.

- Vite chính đọc `.env` như bản React trước đây. Không đổi nội dung `.env` trong migration.
- `VITE_API_BASE_URL` mặc định là `http://localhost:8080`; dùng cùng hostname `localhost` để cookie session/CSRF hoạt động đúng. URL không kèm `/api/v1`, services tự nối prefix.
- Backend mặc định chỉ cho CORS từ localhost/127.0.0.1:5173 (`application.yaml#app.cors.allowed-origins`, `SecurityConfig#corsConfigurationSource`). Không mở wildcard hoặc tắt CSRF.
- App không tự nạp mock. Thao tác tạo/sửa/xóa khi mở thủ công gọi backend thật; dùng dữ liệu test khi kiểm tra.
- Config `tests/visual/vite.preview.config.ts` còn giữ `envDir:false` cho visual fixture biệt lập, không phải cấu hình chạy thông thường.

## Build và kiểm tra

```bash
npm run typecheck
npm run lint
npm run test:unit
npm run test:motion
npm run build
npm run verify:vue -- --baseline
```

Output chính vẫn `dist/`; Docker/Caddy/API/SSE proxy và `public/huong-dan/` không đổi. Build cho same-origin proxy dùng `VITE_API_BASE_URL=/ npm run build`.

`npm run preview` phục vụ build ở4173, không proxy backend. Với backend local/CORS mặc định, dừng dev server rồi dùng `npm run preview -- --port 5173 --strictPort`, mở localhost:5173. `build:vue` và `preview:vue` chỉ là alias.

`verify:vue` build **entry production thật** vào `dist-vue-audit/` với `envDir:false`, không đụng `dist/`; kiểm tra graph/package/lockfile/source không có React, JSX hoặc test entry/fixture. Report tại `dist-vue-audit/vue-audit.json`. Cờ `--baseline` kiểm tra checksum58file source giữ lại (gồm16CSS); không áp gate checksum cũ cho feature tương lai chủ động sửa các file này.

CI frontend đã dùng `vue-tsc` qua script `typecheck`, thêm unit/component và motion test, giữ build. Backend/deploy jobs và trigger không đổi; chưa chạy remote CI hoặc deploy.

## Browser smoke và visual còn cần chạy

Browser smoke đã viết nhưng chưa thực thi sau cutover do giới hạn môi trường:

```bash
UI_BASE_URL=http://localhost:5173 UI_OUTPUT_DIR=/tmp/vehicletracking-vue-cutover-smoke npm run test:e2e
```

Chạy lần lượt với dev server và build preview. Script chỉ nhận localhost, intercept API và chặn nguồn ngoài fixture; không dùng credential thật. Phạm vi: dirty-discard/native dialog, report query, driver redirect/detail/logout, mobile nav, giữ map khi đổi query/back-forward,20lần vào/ra map và public guide. Counters SSE/DOM không đo toàn bộ memory/listener/RAF; chưa bao phủ full CRUD/shape/simulator matrix.

Baseline React vẫn ở snapshot/archive riêng để đối chiếu, không chạy từ `src/` hiện tại:

```bash
# Từ repository root; chỉ dùng snapshot đã được kiểm tra.
node docs/features/032-vue-frontend-migration/verification/serve-baseline.mjs /tmp/vehicletracking-react-baseline-BIOCEx/dist 5175
```

Từ frontend, khi browser được phép chạy:

```bash
UI_SCOPE=maps UI_BASE_URL=http://127.0.0.1:5175 UI_OUTPUT_DIR=/tmp/vehicletracking-react-map-next npm run test:visual
UI_SCOPE=maps UI_BASE_URL=http://localhost:5173 UI_OUTPUT_DIR=/tmp/vehicletracking-vue-map-next npm run test:visual
node tests/visual/compare.mjs /tmp/vehicletracking-react-map-next /tmp/vehicletracking-vue-map-next
```

Readiness loop tiến controlledclock chờ lazy map/tile fade, không mask map hoặc sửa opacity/CSS. Nếu thất bại, `map-readiness-failure.json` ghi diagnostics. Người dùng đã chấp nhận giao diện, nhưng automated map pixel parity vẫn chưa được chứng minh; không ghi đè baseline để che khác biệt.

## Bảo toàn và khôi phục

Đã gỡ đúng **66file** có đối ứng:51TSX,14React hooks và `src/app/routeConfig.ts` (Vue dùng `navigation.ts`). Trước xóa, hash của toàn bộ124file source khớp baseline; không có TSX mới nằm ngoài danh sách.16CSS và58file source dùng chung còn nguyên.

[react-baseline.tar.gz](verification/artifacts/react-baseline.tar.gz) giữ nguyên source/config/build React, gồm cả code từng untracked. SHA256: `16115fa4c3516b7fed42ac1a58440b62bdbe80a545b472e711ff82639b16bbd2`.

Muốn khôi phục: giải nén vào thư mục tạm trống, đối chiếu manifest và các thay đổi mới trước khi chọn file. Không giải nén đè working tree, không dùng reset/checkout toàn repo. Chuyển runtime trở lại React cần phục hồi đồng bộ source, entry và toolchain, không chỉ đổi `index.html`. Chưa có deploy trong lượt này nên không có thao tác rollback môi trường thật.

## Giới hạn bàn giao

- Browser E2E/readiness loop mới, built-browser và container/API/SSE smoke chưa chạy sau cutover. Lần trước môi trường báo giới hạn tới13:00 ngày23/09; không chạy vòng tránh.
- Các test local dùng HTTP fixture, không thay backend authorization/session integration thực tế.
- Không thay backend/schema/CSS hoặc secret; không commit/push/deploy, không có review độc lập. Xác nhận của người dùng chỉ áp dụng giao diện và cho phép cutover, không tự suy ra nghiệm thu toàn bộ regression.
