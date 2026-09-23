# Test Plan 022 — Business Management Shell

| AC | Mức kiểm tra | Kịch bản | Kết quả mong đợi |
|---|---|---|---|
| AC-01 | Type/build/manual | Dashboard load/success/error/retry | Metric đúng source, không giả metric thiếu |
| AC-02 | Type/manual | Chọn từng link sidebar | URL, title và active state đúng |
| AC-03 | Build/config/manual | Reload `/drivers`, Back/Forward | Caddy fallback và router render đúng page |
| AC-04 | Regression/manual | CRUD/gán xe-tài xế/chuyến ở page riêng | Hành vi Feature 021 giữ nguyên |
| AC-05 | Build/manual/keyboard | Mở Operations, realtime/map/simulator; mở nút Cảnh báo và đóng bằng Escape | Map mount, controls và cleanup hoạt động; alert drawer không chiếm chỗ thường trực, trigger có badge/aria và focus trở về khi đóng |
| AC-06 | Manual | Mở `/routes`, `/stations` | Đúng workspace map, picker/editor hoạt động |
| AC-07 | Source/manual | Mở roadmap pages | Không API/số liệu/quyền giả |
| AC-08 | Lint/manual keyboard | Mobile open/close/Escape/overlay/tab | Semantic/focus/visibility đúng |
| AC-09 | Automated | lint, tsc, build, diff check | Exit 0; không backend change do feature 022 |

## Lệnh

```bash
cd vehicletracking-frontend
npm run lint
./node_modules/.bin/tsc --noEmit
npm run build

cd ..
git diff --check
```

Browser smoke nếu môi trường cho phép: desktop 1440 px và mobile 390 px; kiểm tra dashboard, tất cả route, Back/Forward, reload deep link, sidebar keyboard, Operations map và CRUD Feature 021.
