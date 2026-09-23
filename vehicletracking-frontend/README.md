# Vehicle Tracking Frontend

Vue 3 + TypeScript + Vue Router + Vite + Leaflet. Entry duy nhất là `src/main.ts`; các component dùng Vue SFC và state/lifecycle dùng composables.

Yêu cầu Node.js 22.12 trở lên (khuyến nghị Node.js 24).

## Cấu hình môi trường

Tạo file `.env` từ `.env.example`:

```bash
cp .env.example .env
```

Biến môi trường hỗ trợ:

- `VITE_API_BASE_URL`: Địa chỉ API backend (mặc định `http://localhost:8080`).

> [!NOTE]
> Frontend tuyệt đối không lưu trữ, quản lý hoặc gửi bất kỳ API Key/Secret của các nhà cung cấp bên thứ ba (như HERE API). Mọi truy vấn giao thông hoặc dịch vụ ngoài đều được điều hướng qua backend proxy an toàn.

## Khởi chạy phát triển

```bash
npm ci
npm run dev
```

Mở `http://localhost:5173`. Dev server đọc `.env` như bình thường và không tự nhảy sang cổng khác nếu 5173 đang bận; dừng frontend cũ trước khi chạy. `npm run dev:vue` là alias của `npm run dev`, không còn chạy cấu hình test ở cổng 5176.

Dùng cùng hostname `localhost` cho frontend và backend local để session/CSRF cookie hoạt động đúng. Nếu backend không ở `http://localhost:8080`, đặt `VITE_API_BASE_URL` đúng địa chỉ, không kèm `/api/v1` (services tự thêm prefix này). Backend mặc định cho phép CORS từ localhost/127.0.0.1 cổng 5173; không tự bật wildcard CORS hoặc tắt CSRF để xử lý lỗi kết nối.

## Kiểm tra và build

```bash
npm run lint
npm run typecheck
npm run test:unit
npm run test:motion
npm run build
```

`typecheck` dùng `vue-tsc --noEmit` để kiểm tra cả template Vue. Output production vẫn là `dist/`, Docker/Caddy và API/SSE proxy không đổi. Build cho same-origin proxy dùng `VITE_API_BASE_URL=/ npm run build`.

`npm run preview` phục vụ build tại cổng 4173 mặc định; đây không phải backend proxy. Khi thử backend local với CORS mặc định, dừng dev server và dùng `npm run preview -- --port 5173 --strictPort`, mở `http://localhost:5173`. `build:vue` và `preview:vue` chỉ là alias tương ứng.

`npm run verify:vue` build entry chính biệt lập vào `dist-vue-audit/`, không đọc `.env`, và kiểm tra module graph/package/lockfile không chứa React hoặc test fixture. Thêm `-- --baseline` khi cần đối chiếu checksum CSS/services gốc của feature032; không dùng cờ này làm gate cho các feature mới chủ động sửa CSS/services.

Browser smoke dùng API fixture, không kiểm tra backend thật: chạy server local, rồi `UI_BASE_URL=http://localhost:5173 npm run test:e2e`. Phạm vi đã chạy và giới hạn kiểm chứng ở [evidence feature032](../docs/features/032-vue-frontend-migration/evidence.md).

## Hướng dẫn người sử dụng

Sau khi chạy frontend, mở `/huong-dan/` để xem tài liệu hướng dẫn HTML/CSS. Trong ứng dụng, bấm **Hướng dẫn** trên thanh điều hướng để mở tài liệu ở tab mới. Trang có mục lục, hướng dẫn quản lý xe/trạm/tuyến, kéo chỉnh đường đi, tạo chuyến, mô phỏng, giao thông và xử lý lỗi; dùng Ctrl/Command + P để lưu PDF.
