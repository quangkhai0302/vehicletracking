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

## Follow-up 2026-10-05 — Thông tin lịch chạy dễ đọc

Áp dụng quy trình rút gọn Survey → Plan ngắn → Implement → Verify cho yêu cầu trực tiếp chỉnh bố cục thông tin lịch chạy. Phần bên trên là evidence lịch sử của implementation React; phần này kiểm chứng code Vue hiện tại.

Survey: các hàng `dl` trong `ScheduleManagementPage.vue` trước thay đổi dùng nhãn bên trái và giá trị canh phải; CSS `.schedule-card dl div` có `justify-content: space-between`, khiến một lịch đơn kéo nhãn và giá trị ra hai mép. `ui-refresh.css` còn đặt nhãn màu `--text-muted`, rộng cố định 90px; hai trường giờ và cách khởi hành cùng mang tên “Khởi hành”.

Plan/implementation chỉ thay phần trình bày: nhóm nhãn/giá trị thành grid responsive, tách xe và tài xế, phân biệt giờ với cách khởi hành, tăng độ tương phản và cho nội dung dài xuống dòng. Bảo toàn các thay đổi đang có về cấu hình chuyển tiếp và các feature khác; không sửa API, backend, schema hay trạng thái lịch.

| Hành vi sau thay đổi | Evidence code/verification |
|---|---|
| Sáu nhóm rõ ràng: Lịch chạy, Giờ khởi hành, Xe, Tài xế, Cách khởi hành, Lần chạy kế tiếp | `vehicletracking-frontend/src/pages/ScheduleManagementPage.vue`, template `.schedule-details` |
| Nhãn nằm ngay trên giá trị, canh trái; giá trị 14px, nội dung dài không bị ellipsis | `vehicletracking-frontend/src/features/schedules/styles/schedule-management.css`, `.schedule-details`, `dt`, `dd` |
| Màu nhãn `--text-secondary` trên nền sáng, không kế thừa cách canh phải cũ | `vehicletracking-frontend/src/ui-refresh.css`, `.business-ui .schedule-details dt/dd` |
| Múi giờ Việt Nam hiển thị “Giờ Việt Nam”; múi giờ khác vẫn giữ giá trị thật | `ScheduleManagementPage.vue`, `.schedule-details dd small` |
| Một/nhiều lịch, tên tài xế dài, khởi hành thủ công/tự động, giờ kế tiếp có/không có | Browser fixture-only, 8 trường hợp ở 320/390/768/1440px; [results](verification/artifacts/schedule-readability-results.json) |

Lệnh thực chạy tại `vehicletracking-frontend/`, Node 24.16.0:

```bash
npm run lint
npm run typecheck
npm run test:unit
npm run test:motion
npm run build
npm run test:unit -- tests/unit/business-pages.test.ts -t schedule
```

- Lint: exit 0. Motion: exit 0, 1 test pass. Production build cuối: exit 0; còn cảnh báo chunk JS >500 kB.
- Các test lịch đã có: exit 0, 2 pass, 7 test khác không được chọn bởi filter.
- Full unit: exit 1, 287 pass/1 fail trong 288 test, 38 file pass/1 fail. Finding ngoài phần đã sửa: test reports tại `tests/unit/business-pages.test.ts:222` kỳ vọng 7 `.reports-metric`, nhưng trang Báo cáo hiện render 6.
- Typecheck: exit 2, ba import chưa sử dụng `BusFront`, `Gauge`, `UserRound` trong `src/pages/ReportsPage.vue`. Không sửa trang Báo cáo hoặc test của nó trong follow-up này.
- Browser: preview production tại `http://localhost:4190`, Chromium headless; script `/tmp/vehicletracking-schedule-readability-check.mjs`, exit 0. Kiểm chứng viewport/giá trị không tràn ngang, nhãn và giá trị cùng mép trái, khoảng cách tối đa 8px, giá trị >=14px, độ tương phản nhãn/giá trị >=4.5:1, không pageerror và không mutation API. Fixture intercept mọi request nghiệp vụ; không phải kiểm thử backend thật.
- Logs: `/tmp/vehicletracking-schedule-readability-{lint,typecheck,test-unit,test-motion,build,schedule-tests,browser}.log`.
- Ảnh đã xem trực tiếp: [desktop một lịch](verification/artifacts/schedule-readability-1440.png), [mobile nhiều lịch](verification/artifacts/schedule-readability-390.png).

`git status` được kiểm tra trước/sau; không commit/push, không đọc giá trị `.env`. Không chạy backend tests vì không thay backend. Follow-up UI đã được kiểm chứng trong trình duyệt; toàn frontend chưa đạt quality gate do các lỗi trang Báo cáo nêu trên.


## Follow-up 2026-10-05 — Sửa lỗi trang Báo cáo chặn deploy

Áp dụng quy trình rút gọn Survey → Plan ngắn → Implement → Verify cho yêu cầu sửa lỗi TS6133 khi deploy. Base commit trước sửa: d9c05325; working tree ban đầu sạch.

Survey: `vehicletracking-frontend/src/pages/ReportsPage.vue` vẫn import BusFront/Gauge/UserRound, nhưng Gauge không còn metric sử dụng và BusFront/UserRound chỉ xuất hiện trong template đã comment. Trang hiện render sáu metric; `vehicletracking-frontend/tests/unit/business-pages.test.ts`, reports reject incomplete dates without HTTP and can reset/retry an API failure, vẫn kỳ vọng bảy. `.github/workflows/ci-cd.yml` chạy lint → typecheck → unit → motion → build, nên cả typecheck và test cũ sẽ chặn pipeline.

Plan/implementation: bỏ đúng ba import thừa; cập nhật kỳ vọng số metric từ7 về6 theo giao diện hiện có. Không đổi nội dung/logic trang, API, backend hoặc workflow. Chạy đủ các frontend checks của CI bằng Node24.16.0 tại vehicletracking-frontend.

| Lệnh | Exit | Kết quả |
|---|---:|---|
| npm run lint | 0 | Pass |
| npm run typecheck | 0 | Không còn TS6133 |
| npm run test:unit | 0 | 231/231tests, 28/28files pass |
| npm run test:motion | 0 | 5/5pass |
| npm run build | 0 | Production build thành công; cảnh báo chunk529.34kB có sẵn |
| git diff --check | 0 | Pass |

Các lệnh dùng `PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH`. Logs: `/tmp/vehicletracking-reports-deploy-{lint,typecheck,unit,motion,build}.log`. Đây là kiểm chứng local các bước frontend CI, chưa chạy lại workflow trên GitHub hoặc deploy production. Không chạy backend test vì không đổi backend; không mở/in giá trị secret từ .env, không commit/push.

Các lỗi ReportsPage từng ghi trong các follow-up trước đã được xử lý tại lượt này; giữ nội dung trước đó làm evidence lịch sử.
