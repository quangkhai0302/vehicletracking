# Test plan — 032

Đây là kế hoạch kiểm tra được duyệt; kết quả chạy từng checkpoint nằm tại [evidence.md](evidence.md). Đã có unit tests và browser capture một phần, **chưa hoàn tất ma trận này**. Không dùng pass của feature031 hay React build để kết luận migration đạt.

## 1. Thiết lập cần có sau phê duyệt

- Khoá Node 24, browser revision, OS/container, timezone `Asia/Ho_Chi_Minh`, locale và DPR cho cùng một lần so sánh.
- Thêm `@playwright/test`, config và fixture shared dưới frontend `tests/e2e`, `tests/visual`, `tests/fixtures`. Không tiếp tục phụ thuộc đường dẫn Playwright cache của máy cá nhân như script 031.
- Thêm Vitest + Vue Test Utils + DOM test environment tương thích vào devDependencies; config chỉ nhận tests unit/component mới, không tự chạy nhầm `node:test` hiện hữu. Motion tests tiếp tục chạy bằng Node riêng.
- Test server phục vụ React baseline artifact và Vue candidate với SPA fallback; cùng fixture, cùng paths, cùng provider fixture. Baseline không hot-reload từ source đang port.
- Fixture chỉ nằm trong test/harness, không mock ngầm API của bản production. Unsafe requests bị intercept trong UI suite; backend integration chỉ dùng test environment tách biệt.

## 2. Quy tắc visual parity (AC2)

1. Chụp lại React hiện tại trước đổi source: 15 URL trong Survey + 404/auth loading, mọi chế độ map; 1440×1000, 768×1000, 390×844. Kiểm tra thêm 320px và 844×390 landscape cho maps/dialog/coordinate picker.
2. Mỗi nhóm có data, loading, empty, API error/retry; thêm editor/detail/confirmation/filter/selected/disabled. Chụp đúng scroll owner, cả đầu/cuối form dài, không chỉ fullPage của body bị khoá cuộn.
3. Đóng băng clock/date; feed telemetry snapshot ở thời điểm xác định và đợi marker/RAF tới mốc kiểm tra. Fix tiles/font bằng fixture cùng nguồn cho hai bản. Google fonts hiện được import qua mạng (`src/index.css`): bảo đảm font thực sự load hoặc ghi rõ baseline fallback giống nhau; phải có thêm kiểm tra Inter loaded, không chỉ ảnh khi font bị chặn.
4. Provider tile động tách khỏi chrome/overlay: dùng tiles fixture xác định, map markers/polyline/controls/legend/popup vẫn so sánh. Không mask toàn bản đồ hoặc các component vừa port; không mask chữ, màu, spacing để làm test pass.
5. Baseline-to-baseline phải ổn định trước React-to-Vue. Mặc định không cho pixel difference trong cảnh deterministic; nếu raster/anti-alias noise có thật, đo bằng lặp baseline rồi xin duyệt tolerance cục bộ, không đặt threshold % lớn tùy tiện.
6. Đo bổ sung `getBoundingClientRect` cho sidebar/topbar/canvas/panel/table/form/dialog; chênh lệch vị trí/kích thước không quá 1 CSS px do rounding. `color`, background, border, typography tokens/size/weight, icon size/stroke phải giống; không có horizontal overflow ngoài vùng cuộn chủ đích vốn có.
7. So sánh trên dev **và production build**; cold deep-link và navigation sau khi lazy CSS đã load đều phải đúng. Không auto-update baseline bằng ảnh Vue.

## 3. Ma trận acceptance

| AC | Mức test | Kịch bản | Fixture / dữ liệu | Mong đợi |
|---|---|---|---|---|
| 1 | Static + build graph | Scan import/dependency/runtime sau cutover | Toàn `src`, config, package-lock, manifest build | Không React/react-dom/react-router-dom/lucide-react/plugin/types React hoặc React JSX runtime; không TSX còn làm nguồn app |
| 1,7 | Inventory review | So khớp 51 TSX, 14 hooks và route metadata | Manifest đầu implementation | Mỗi file có đối ứng/disposition, không mất chức năng; component chưa route không bị bỏ âm thầm |
| 2 | Visual + DOM metrics | 15 URL + catch-all/auth loading, states và breakpoint | Clock/font/tile/API cố định | Layout/màu/typography/icon/scroll như baseline theo mục 2 |
| 3 | Browser + HTTP capture | Xe/tài xế CRUD/phân công, chuyến tạo/sửa lịch/tài xế/chuyển trạng thái/huỷ/xoá | Fleet fixture active/inactive, thiếu điều kiện, 400/404/409 | Request method/path/body giống baseline, busy/validation/confirm/error đúng |
| 3 | Browser | Lịch ONCE/WEEKLY, ngày/giờ/timezone/phân công/toggle; filter/clear | Schedule valid/invalid/conflict | Không tự đổi thời gian, mask thứ, null hoặc điều kiện hoạt động |
| 3 | Browser | Dashboard, alerts read/dismiss/read-all, reports date/vehicle/driver filters, users create/enable/disable | Empty/data/503, invalid dates, tài khoản linked/unlinked | Dữ liệu/chỉ số/thông báo/callback giữ đúng; polling không trùng |
| 3,5 | Browser + auth state unit | Login/register/logout, mismatch password, 401/403, role redirect, disabled account | Guest/admin/driver và lỗi network | Request/redirect/status như React; không auth bằng localStorage, không fetch admin data ở driver |
| 3 | Browser | Cổng tài xế: chuyển today/schedules, chi tiết, logout, empty/error | Chuyến nhiều trạng thái, giờ gần ranh ngày VN | Đúng lịch/chuyến được giao, timezone và scroll |
| 4 | Browser + composable | SSE snapshot trước/sau HTTP initial, snapshot cũ, disconnect/retry, malformed event | EventSource fixture có kiểm soát và clock | Không ghi đè newer snapshot; trạng thái kết nối/cadence đúng, close khi unmount |
| 4 | Unit + browser | Marker interpolation, heading, selected/follow vehicle, kéo map ngừng follow | Motion tests hiện có + 1x/5x/10x telemetry | Không teleport/duplicate markers; không RAF sau unmount |
| 4 | Browser | Trạm create/edit/pick coordinate/drag/cancel/delete; route reorder stops/shape edit/save/inspect | Stops/route sections fixture, provider error, delayed response | Selection/draft/map viewport đúng; response cũ không chiếm selection mới |
| 4 | Browser + HTTP capture | Simulator play/pause/speed/stop/reset/attempts, multi-vehicle, check-in, ETA, route revision, telemetry history | Trips scheduled/running/completed/cancelled, attempts và notifications | Nút/callback/endpoint đúng; giữ attempt/history và tuyến đang chọn |
| 4 | Unit + instrumentation browser | Vào/ra 3 map routes và query toggle 20 lần | Counters Map/Layer/EventSource/listener/RAF/timer/ResizeObserver | Không “container already initialized”; số owner đang sống về baseline sau unmount; không stream/RAF/poll của page cũ |
| 4 | Browser | Traffic bbox/debounce/toggle/theme/retry; zoom/fit/follow/fullscreen; lazy layers | Tile/traffic fixtures, network failure | Layer ordering/cadence/padding/tile error UI đúng; không đổi provider |
| 5 | Router unit + browser | Refresh/deep link/back-forward, `tripId`/`vehicleId` sai/hợp lệ, operations mode switch | 15 URLs, query list/empty/invalid | Đúng route/role/query; operations không remount khi chỉ đổi mode/tripId; fleet page key reset đúng |
| 5 | Network + test backend smoke | Cookie credentials, CSRF concurrent bootstrap, unsafe request, SSE credential | Isolated backend/test accounts, không production | Header/cookie/status giống contract; server vẫn enforce ADMIN/DRIVER |
| 3,4 | Component + browser keyboard | Open/close drawer/dialog, busy Escape, focus restoration, sidebar, forms dài | Desktop/mobile, async detail | Nền inert, không focus vào page nền, nút cuối form dùng được, state không mất do đổi v-if/v-show |
| 6 | CLI + negative proof | Vue typecheck, script/template lint, unit/motion, build | Temporary intentionally invalid template/type ở spike | Lỗi template/type thực sự bị bắt; không có blanket any/declare module làm typecheck giả pass |
| 6 | Local container smoke | npm ci → build → Caddy, deep links, `/api/*`, SSE, assets, guide URL | Local test config, không dùng production credentials | Build context/dist như trước; refresh không404; API không trả index.html; SSE không buffer |
| 7 | Read-only review | Compare requirements/spec/plan/manifest/screenshots/network/cleanup | Artifacts và logs cuối | Không scope creep, không secret; ghi rõ blocker/gap trước Accept |

## 4. Lệnh dự kiến khi runner đã được thiết lập

Trong `vehicletracking-frontend/`, chưa chạy trong lượt planning:

```bash
npm ci
npm run lint
npm run typecheck
npm run test:unit
npm run test:motion
npm run test:e2e
npm run test:visual
npm run build
```

`test:motion` chạy bộ `tests/vehicleMotion.test.ts` bằng Node24; `typecheck` chạy Vue-aware checker, không chỉ `tsc` trên `.ts`. Cấu hình các script là phần implementation, không giả định repo hiện đã có.

Static gate dùng manifest/import graph + `npm ls`/lock inspection; không grep chuỗi “react” mù trong tài liệu lịch sử hoặc từ `reactive` rồi kết luận fail. CI frontend chạy lint/typecheck/unit/motion/build; Playwright browser/visual trên image/browser đã khoá, lưu diff/report khi lỗi. Không thay đổi deploy job hoặc tự push để thử pipeline.

## 5. Evidence và giới hạn

Lưu: source/CSS hash manifest, pinned dependency/tool versions, React baseline/Vue actual/diff, computed metrics, request captures đã bỏ cookie/credential, lifecycle counters, stdout/exit code và review. Không log response auth nhạy cảm hoặc `.env`.

Nếu Docker/provider/browser/network không có thì ghi đúng nhóm chưa chạy, không thay bằng mocked pass rồi nói đã E2E. Backend code không đổi nên không bắt buộc chạy toàn Maven ở mỗi bước port UI; giữ backend CI hiện hữu và chạy targeted integration khi có test environment. Lỗi của baseline được ghi riêng; không tự sửa ngoài scope, nhưng regression do Vue phải được xử lý trước gate.
