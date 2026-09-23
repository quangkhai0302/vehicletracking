# Evidence — 031

Ngày kiểm tra: 2026-09-22. Trạng thái: Verified, chưa có review độc lập.

## Phạm vi working tree

Working tree đã có nhiều thay đổi tracked/untracked ở cả hai ứng dụng trước feature này. Không commit, không reset và không sửa backend/migration trong feature 031. `git status --short` được kiểm tra trước/sau. Vì nhiều file giao diện đã untracked, diff so với HEAD không phải toàn bộ phạm vi feature 031.

File chính (đường dẫn dưới `vehicletracking-frontend/`):

- `src/ui-refresh.css`: thay lớp refresh trước bằng theme opt-in, responsive và trạng thái chung.
- `src/components/business/{PageHeading,AuthLayout,FleetManagementTable,SidePanel}.tsx`: component mới.
- `src/app/ApplicationShell.tsx`: chỉ opt-in theme khi route không fullBleed; skip link cho trang nghiệp vụ.
- `src/components/fleet/FleetWorkspace.tsx`: bảng chỉ trong nhánh `lockedTab`; map vẫn dùng các card hiện có.
- `src/pages/{DashboardPage,ReportsPage,AlertsManagementPage,ScheduleManagementPage,UserManagementPage}.tsx`: header chung; lịch dùng SidePanel.
- `src/pages/{LoginPage,AdminRegistrationPage,DriverPortalPage}.tsx`: auth layout, theme portal, drawer chi tiết chuyến.
- `src/main.tsx`: import stylesheet refresh sau style nền.

## Mapping acceptance criteria

| AC | Evidence source | Kiểm chứng |
|---|---|---|
| 1 | `src/ui-refresh.css` `.business-ui`, `.page-heading`, metric/status/form rules; `PageHeading.tsx` | Ảnh thật của các trang có dữ liệu ở 3 viewport; kiểm tra empty/error của 6 trang |
| 2 | `FleetWorkspace.tsx` nhánh `lockedTab`; `FleetManagementTable.tsx` | Search xe, mở editor xe/tài xế/chuyến, chi tiết chuyến, xác nhận ngừng xe; bảng desktop và hàng có nhãn mobile |
| 3 | `AuthLayout.tsx`; `DriverPortalPage.tsx`; `ui-refresh.css` auth/driver scroll owner | Login/register và 2 trang portal ở 3 viewport; đăng ký sai xác nhận rồi thành công qua API fixture; driver status/detail mobile |
| 4 | `SidePanel.tsx`; responsive rules `ui-refresh.css`; `ApplicationShell.tsx` mobile navigation | Không overflow container chính tại 1440/768/390px; form lịch cuộn tới ngày cuối, footer nhìn thấy, focus không đi vào nền, Tab/Shift+Tab/Escape và trả focus |
| 5 | `ApplicationShell.tsx:43`; `FleetWorkspace.tsx:88`; hashes bên dưới | 3 route map × 2 viewport không có business-ui; computed styles trước/sau tắt theme mới giống nhau cho các node/thuộc tính được chọn |
| 6 | `verification/ui-smoke.mjs`, kết quả CLI dưới đây | Type check, lint, build exit 0; browser 34 mục ghi nhận, 61 ảnh, không failure/pageerror |

## Lệnh và kết quả

Frontend chạy bằng Node v24.16.0, tại `vehicletracking-frontend/`:

```bash
env PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint
env PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH ./node_modules/.bin/tsc --noEmit
env PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run build
```

- Lint: exit 0, không error; 6 warning có sẵn: `AuthContext.tsx` only-export-components (2); `useFleetWorkspace.ts` set-state-in-effect (2); `UserManagementPage.tsx`, `DriverPortalPage.tsx` set-state-in-effect (2).
- TypeScript: exit 0.
- Vite build: exit 0, 1958 modules; CSS entry 238.48 kB, JS entry 373.70 kB (chưa gzip).
- `git diff --check -- vehicletracking-frontend/src/components/fleet/FleetWorkspace.tsx vehicletracking-frontend/src/main.tsx`: exit 0. Không dùng kết quả này để khẳng định toàn dirty tree sạch.

Browser, từ repository root với Vite chạy `http://127.0.0.1:5174`:

```bash
env UI_PLAYWRIGHT_MODULE=/home/khainq/.npm/_npx/705bc6b22212b352/node_modules/playwright/index.mjs \
  /home/khainq/.nvm/versions/node/v24.16.0/bin/node \
  docs/features/031-business-interface-redesign/verification/ui-smoke.mjs
```

Kết quả cuối: exit 0, **34 checks, 61 screenshots, runtimeErrors=[], failures=[]**. Chromium cần chạy ngoài sandbox do giới hạn môi trường. Toàn bộ API được intercept, request ngoài origin bị chặn; không ghi dữ liệu thật.

Script mặc định xuất `/tmp/vehicletracking-ui-031`. Bản sao [results.json](verification/artifacts/results.json) lưu trong repo; đường dẫn screenshot còn lại trong JSON trỏ tới thư mục tạm. Bốn ảnh đại diện được lưu lâu dài:

- [Dashboard desktop](verification/artifacts/dashboard-1440.png)
- [Phương tiện desktop](verification/artifacts/vehicles-1440.png)
- [Đăng nhập mobile](verification/artifacts/login-390.png)
- [Cuối form lịch mobile](verification/artifacts/schedule-editor-end-390.png)

## Bảo toàn maps

SHA-256 trước/sau giống nhau; tên file dưới `vehicletracking-frontend/`:

```text
c5c4243a946e5f49440dd62e6ae066985d41a3e305afce154af759407a260083  src/components/MapComponent.tsx
697250a63ff13f5dff7e007489d0be7665b64ad903be27075a4ac7dbbf5923a3  src/workspace.css
5003d35a756fafe6c41e20fbe4ed3562450df06f86730442fdfd35a96e5ee319  src/index.css
fddb95801c0994886109bceedc34e862e789691c181b7c82810928961ad971fd  src/app/application-shell.css
60d11ed3468423d8816dd0fbab37eea2edee54abfd069d13c9da4be6dea47845  src/components/fleet/fleet.css
```

Kiểm tra computed style ở `/operations`, `/routes`, `/stations`, rộng 1440/390px: shell/sidebar/navigation/topbar/content/map/fleet nodes có mặt; so sánh display/color/background/border/size/padding/fontSize/shadow/grid/position. Đây là kiểm tra cô lập CSS, không chứng minh mọi tương tác bản đồ hay kết nối provider.

## Giới hạn

- Không chạy Maven/backend tests vì không sửa backend, API hoặc schema trong phạm vi này. Không thay migration.
- Browser chỉ dùng fixture, không phải E2E authentication/CRUD thật, GPS, SSE lâu dài, hoặc HERE traffic.
- Chưa kiểm tra Firefox/Safari, thiết bị thật, screen reader hay audit WCAG đầy đủ.
- Frontend survey độc lập đã hoàn thành. Reviewer Sol/high được gọi nhưng bị giới hạn sử dụng; main agent đã tự rà soát, không ghi thành review độc lập.
- Không đọc giá trị secret từ `.env`; không thêm secret/dependency/provider key vào UI. Fixture chứa thông tin kiểm thử được đặt tên rõ, chỉ nằm trong script verification.
