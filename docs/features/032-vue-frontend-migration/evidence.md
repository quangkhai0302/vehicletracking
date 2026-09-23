# Evidence — 032, cutover Vue mặc định

Ngày cập nhật: **2026-09-23. Đã cutover source/build sang Vue theo duyệt trực tiếp của người dùng; regression đầy đủ chưa hoàn tất.** `index.html` nạp `src/main.ts`; đã gỡ 51TSX,14hooks và metadata icon React, cùng dependency/plugin/types/JSX. Người dùng xác nhận giao diện Vue đã đủ và đồng ý chuyển mặc định; không coi xác nhận này là pixel matrix/E2E tự động đã pass. Main agent tự triển khai, không dùng subagent. Các checkpoint candidate bên dưới là lịch sử.

Checkpoint mới nhất: **85 tests / 14 files pass**, clean install/typecheck/lint/motion/build pass. Audit entry production và lockfile không có React/TSX/test fixture;58file baseline giữ lại, gồm16CSS, còn nguyên. `npm run dev` và alias `dev:vue` dùng cùng config đọc `.env`, port5173 strict. **Chưa chạy browser smoke hoặc readiness loop mới**, container/remote CI; không deploy. Xem [walkthrough.md](walkthrough.md) và mục cutover cuối tài liệu.

## Bảo toàn baseline

- Script [capture-baseline.mjs](verification/capture-baseline.mjs) chỉ đọc/copy whitelist `src/public/tests` và các file config công khai; không đọc `.env`, không copy node_modules hay backend.
- Đã lưu **137 file**, bao gồm 51 TSX, 14 hook và 16 CSS. Manifest [react-baseline-manifest.json](verification/artifacts/react-baseline-manifest.json) có SHA256 từng file và mapping migration dự kiến (status vẫn pending).
- [react-baseline.tar.gz](verification/artifacts/react-baseline.tar.gz) chứa source/config snapshot cùng build `dist` React để tiếp tục đối chiếu mà không phụ thuộc source candidate. SHA256 archive: `16115fa4c3516b7fed42ac1a58440b62bdbe80a545b472e711ff82639b16bbd2`.
- Snapshot local: `/tmp/vehicletracking-react-baseline-BIOCEx`. Build từ snapshot không có `.env`, truyền `VITE_API_BASE_URL=/`; thư viện build dùng node_modules hiện hữu qua symlink, symlink đó **không nằm trong archive**.
- Sau kiểm tra, so SHA256 của 137 file ứng dụng với manifest: `changed=[]`. Working tree bẩn trước đó được giữ nguyên; không commit/reset/stash.
- Checkpoint source/build này được giữ nguyên. Bộ ảnh mới được ghi riêng bên dưới; chưa đạt toàn bộ visual stability/maps theo G0.

## Lệnh đã chạy — React hiện tại

Node `v24.16.0`, dùng PATH `/home/khainq/.nvm/versions/node/v24.16.0/bin` (Node mặc định shell là18, không dùng để build).

| Lệnh                                                                              | Working directory                                           | Kết quả                                                      |
| --------------------------------------------------------------------------------- | ----------------------------------------------------------- | ------------------------------------------------------------ |
| `npm run lint`                                                                    | `vehicletracking-frontend`                                  | Exit0, 6 warning có sẵn, không error                         |
| `./node_modules/.bin/tsc --noEmit`                                                | `vehicletracking-frontend`                                  | Exit0, TypeScript7.0.2                                       |
| `node --test tests/vehicleMotion.test.ts`                                         | `vehicletracking-frontend`                                  | Exit0, runner báo 1 test file pass, 0 fail                   |
| `VITE_API_BASE_URL=/ npm run build`                                               | snapshot React `/tmp/vehicletracking-react-baseline-BIOCEx` | Exit0, 1958 modules; entry CSS238.48kB, JS373.46kB chưa gzip |
| Compiler TS6 đối chứng `tsc --noEmit -p …/vehicletracking-frontend/tsconfig.json` | `/tmp/vehicletracking-vue-ts6-jecdqW`                       | Exit0; dùng compiler tạm, không đổi dependency ứng dụng      |

Warning hiện có: AuthContext only-export-components (2), useFleetWorkspace set-state-in-effect (2), UserManagementPage và DriverPortalPage set-state-in-effect (2). Không sửa warning như phần phụ của migration.

## Spike tương thích

Nguồn fixture và lockfile tái lập:

- [spike-ts7/package.json](verification/spike-ts7/package.json), source `src/App.vue` và `TypedCard.vue`.
- [spike-ts6/package.json](verification/spike-ts6/package.json), cùng source + fixture lỗi `negative/Invalid.vue`.

Các bản khoá: Vue3.5.43, Vite8.2.2, plugin-vue6.0.9, vue-tsc3.3.11, Leaflet1.9.4, @types/leaflet1.9.22, @lucide/vue1.43.0. SFC dùng typed props/events/slot, shallowRef Leaflet instance và mount/unmount cleanup.

| Spike           | Install            | `npm run typecheck`                               | `npm run build`     |
| --------------- | ------------------ | ------------------------------------------------- | ------------------- |
| TypeScript7.0.2 | Exit0, 50 packages | **Exit1**, lỗi dưới đây trước khi kiểm tra source | Exit0, 1844 modules |
| TypeScript6.0.3 | Exit0, 49 packages | **Exit0**                                         | Exit0, 1844 modules |

```text
Error [ERR_PACKAGE_PATH_NOT_EXPORTED]: Package subpath './lib/tsc'
is not defined by "exports" in …/node_modules/typescript/package.json
at resolveTscPath (…/node_modules/vue-tsc/index.js:73:43)
```

Source `vue-tsc/index.js` bản đã cài gọi `require.resolve('typescript/lib/tsc')`; TypeScript7 package không export subpath này. Chưa patch vendor hoặc bỏ typecheck để vượt qua lỗi.

Negative proof TS6: chạy `./node_modules/.bin/vue-tsc --noEmit -p tsconfig.negative.json` với string truyền vào prop number; checker trả **TS2322 / exit2** tại `negative/Invalid.vue`, chứng minh template được kiểm tra. Fixture lỗi nằm ngoài include mặc định của spike hợp lệ.

Install các spike dùng `npm install --ignore-scripts --no-audit --no-fund`, không force/legacy-peer-deps. Các phiên bản được xác minh npm registry trước khi cài; sandbox DNS không truy cập được nên chỉ lệnh npm cần network được chạy với quyền đã cấp.

## Cách tái lập an toàn

Copy riêng một thư mục spike sang thư mục tạm mới, dùng Node24, chạy `npm ci --ignore-scripts --no-audit --no-fund`, `npm run typecheck`, `npm run build`. Không chạy install ở root ứng dụng để thử compiler. Với spike TS6, lệnh negative ở trên phải fail do lỗi kiểu prop, không phải crash tool.

Archive React chỉ nên giải nén vào thư mục trống để phục vụ/đối chiếu; không tự giải nén đè working tree. Kiểm tra hash manifest nếu cần khôi phục riêng file sau này.

## Toolchain và visual harness đã thêm sau khi tiếp tục

- `vehicletracking-frontend/package.json` / lock: Vue3.5.43, Vue Router4.6.4, @lucide/vue1.43.0, plugin-vue6.0.9, vue-tsc3.3.11, TypeScript6.0.3; ESLint10.11.0, eslint-plugin-vue10.11.0, typescript-eslint8.70.1; Vitest5.0.1, VTU2.5.1, jsdom30.1.1, Playwright1.62.1. React dependencies giữ tạm cho entry hiện hữu, chưa đạt AC1.
- `vite.config.js` hỗ trợ cả source cũ và SFC trong giai đoạn chuẩn bị. `eslint.config.js` kiểm tra Vue template, `vitest.config.ts` chỉ nhận unit tests; `tsconfig.json` include cả `tests/unit` để typecheck SFC test thật.
- `tests/unit/fixtures/ToolchainFixture.vue` và `toolchain.test.ts`: typed prop/event, slot, icon và Leaflet shallowRef mount/unmount.
- `tests/fixtures/api.mjs` và `tests/visual/capture.mjs`: fixture HTTP/SSE riêng test, fixed time/timezone/viewport, cache font Inter công khai, provider tile xác định. Dùng Playwright trực tiếp với phiên bản khóa, không còn đường dẫn npm cache cá nhân. Chưa có full E2E runner/CI visual.
- `verification/serve-baseline.mjs`: phục vụ frozen React dist với SPA fallback tại localhost; không đọc `.env`.

| Kiểm tra trên app sau cài toolchain                                 | Kết quả thực tế                                                                         |
| ------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `npm install --no-audit --no-fund` với Node24                       | Exit0, thêm201 packages, không force/legacy-peer-deps                                   |
| `npm run typecheck`                                                 | Exit0, vue-tsc với TS6; include SFC fixture                                             |
| `npm run test`                                                      | Exit0, 1 test/1 file pass (SFC/icon/Leaflet cleanup)                                    |
| `npm run lint`                                                      | Exit0, giữ6 warning React cũ, Vue fixture không error                                   |
| `eslint --stdin --stdin-filename src/Negative.vue` với `<div v-if>` | Exit1, `vue/valid-v-if`, đúng negative proof                                            |
| `npm run test:motion`                                               | Exit0, 1 test file pass                                                                 |
| `npm run build`                                                     | Exit0,1958 modules; đây vẫn là React build, **không chứng minh Vue migration hoàn tất** |

Lint thử đầu tiên dùng `eslint src` fail exit2 vì chưa có SFC trong src; đã sửa glob `"**/*.vue"` để bao gồm fixture, chạy lại đạt. Không bỏ lint template để che lỗi.

## Ảnh React và giới hạn baseline

- Lần A `/tmp/vehicletracking-react-032-a`:72 ảnh,0 runtime errors, script exit1 do selector cũ `Thêm xe mới` thay vì tên nút quản trị `Thêm phương tiện`.
- Sau sửa selector, lần B `/tmp/vehicletracking-react-032-b`: **76 ảnh,0 runtime errors, exit0**. Bao gồm15 URLs+404 ở1440/768/390, maps320/landscape844, data/empty/error và4editor. Inter loaded; API hoàn toàn fixture.
- Artifact [react-screens-checkpoint.tar.gz](verification/artifacts/react-screens-checkpoint.tar.gz) lưu lần B, gồm screenshots, metrics và request capture fixture. Đây là checkpoint, **không phải baseline đã nghiệm thu**.
- So72 ảnh chung A/B:65 file PNG byte-identical,7khác; geometry/colors/fonts của các selector được đo trong `metrics.json` giống hoàn toàn. Chưa xác minh hết nguồn khác pixel.
- Quan sát ảnh map lần B cho thấy nền tile trống. Đã sửa matcher `/vt/` → `startsWith('/vt')`, thêm reduced-motion và đưa con trỏ về vị trí cố định. Các lần C/D sau đó đều76ảnh,0runtime errors nhưng nền map vẫn trống; sửa matcher chưa đủ. Harness tiếp tục đổi fixed clock sang controlled clock có bước chạy animation, **bản sửa clock chưa chạy lại**. Giả thuyết Leaflet fade bị đóng băng chưa được chứng minh. Không đánh dấu maps pass hoặc nới tolerance để bỏ qua khác biệt.
- Toàn bộ16CSS ứng dụng giữ byte-identical với snapshot. Không sửa màu/layout để làm ảnh khớp.

## Giới hạn ở checkpoint P0/P1 trước đó

Lần chạy Chromium/preview server tiếp theo bị auto-review từ chối do giới hạn sử dụng phiên, worker trước đó cũng báo cùng giới hạn. Không chạy vòng qua sandbox hoặc tạo worker thay thế để vượt giới hạn. Các yêu cầu “tiếp tục” sau đó được thực hiện bằng port source/test local độc lập, chưa cutover khi visual gate thiếu. Chưa chạy backend/Docker/full E2E/provider, chưa có review implementation độc lập hoặc trạng thái Verified/Reviewed.

Researcher Luna/medium đã kiểm tra nguồn chính thức để xác minh incompatibility (xem `research.md`); đây là research chỉ đọc, không phải review migration hoàn chỉnh.

## Candidate Vue checkpoint22/09 — main agent triển khai trực tiếp

Các đường dẫn dưới đây tương đối với `vehicletracking-frontend/`:

| Phạm vi                                                      | Evidence source/test                                                                                                          | Trạng thái                                                                                                                                            |
| ------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Auth/session/role guards                                     | `src/auth/{authState,useAuth,routerGuards}.ts`, `tests/unit/auth.test.ts`                                                     | Bootstrap single-flight, stale-session protection, login/logout/role redirect có unit test                                                            |
| Shell + admin/auth/driver pages                              | `src/app/{ApplicationShell.vue,navigation.ts}`, các `src/pages/*.vue`, `src/components/business/*.vue`                        | Source đã port; preview build được, chưa browser/visual parity                                                                                        |
| Fleet state + editor/detail                                  | `src/composables/{useFleetWorkspace,useTripCheckIns,useTripEta}.ts`, `src/components/fleet/*.vue`, `tests/unit/fleet.test.ts` | 10 test về stale selection/replay/status, mutation, editor payload, check-in revision, polling cleanup                                                |
| Realtime/simulator/route/traffic/camera/marker/station state | `src/composables/use*.ts`, `tests/unit/map-state.test.ts`                                                                     | 14 composable tổng cộng; 12 test bổ sung về SSE vs HTTP, reconnect/disposal, replay, cache/dwell/abort, planned anchors, marker reuse, station intent |
| Map controls + operations panels/layers                      | `src/components/MapControls.vue`, `src/components/operations/*.vue`                                                           | Giữ classes/text/callback và Leaflet geometry; source chưa nối vào main map Vue                                                                       |
| Traffic inspection + route interactions                      | `src/components/traffic/*.vue`, `src/components/route/{RouteInspectionLayer,RouteShapeEditor,SortableStopList}.vue`           | Teleport tương ứng portal cũ, ResizeObserver cleanup, keyboard reorder/cancel, preview/save/copy và shape layer cleanup                               |
| Form/panel trạm                                              | `src/components/{StationPanel,StationDrawer}.vue`                                                                             | Tìm kiếm/sort, edit/select không bubble, string fields, radius, dirty discard, invalid details reveal                                                 |
| Layer/camera/component regressions                           | `tests/unit/map-components.test.ts`                                                                                           | 12 test bổ sung; gồm20lần mount/unmount selected-route layer, popup handlers, text injection, camera padding, GPS/connection gate, station/shape form |

Các test component/layer dùng jsdom + Leaflet thật, fake provider/API. Đây **không** phải bằng chứng20lần vào/ra toàn bộ app trên Chromium, không chứng minh pixel parity. Test SVG layer bật capability SVG trong jsdom; test native dialog mô phỏng `showModal/close`, chưa chứng minh focus trap native browser.

Preview business entry `tests/visual/vue-preview.{html,ts}` và `vite.preview.config.ts` chưa chứa route bản đồ; không được dùng thay production. Build vào `dist-vue-preview` (đã ignore Git/Docker), không ghi đè `dist` React.

Không sửa CSS để bù khác biệt Vue, không đổi backend/API/schema. Bổ sung guard sau dispose cho mutation/polling, giữ snapshot intent trước reset form trạm để thông báo/callback edit không bị đổi sang create. Manifest baseline giữ bất biến; trạng thái pending trong manifest không phải bảng tiến độ hiện tại.

## Verify checkpoint source22/09 (Node24.16.0)

Working directory `vehicletracking-frontend/`, trừ lệnh Git/checksum ở root repository:

| Lệnh                                                      | Kết quả                                                                                   |
| --------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `npm run typecheck`                                       | Exit0; toàn bộ SFC source và unit tests qua vue-tsc                                       |
| `npm run test:unit`                                       | Exit0; **44 tests / 5 files** (auth9, fleet10, map-state12, map-components12, toolchain1) |
| `npm run test:motion`                                     | Exit0; 1 test file pass, 0 fail; giữ nguyên utility motion                                |
| `npm run lint`                                            | Exit0; Vue0error,6warning React cũ                                                        |
| `npm run build`                                           | Exit0;1958modules, production vẫn là React, không phải Vue full app                       |
| `vite build --config tests/visual/vite.preview.config.ts` | Exit0;1933modules, Vue business preview JS234.02kB/CSS238.48kB (chưa gzip)                |
| SHA256124file `src/` trong manifest với working tree      | `changed=[]`; gồm16CSS byte-identical                                                     |

Các lỗi phát hiện khi chạy và đã sửa: fixture `TripStop` thiếu field đúng contract; mock cleanup SSE trả cùng callback giữa2subscription; type fixture incident thiếu `points`; mô phỏng SVG capability jsdom phải giữ descriptor readonly. Hai layer không có DOM dùng explicit `render: () => null` thay template rỗng bị `vue/valid-template-root` chặn. Đã chạy lại lint/typecheck/test đạt, không tắt rule hoặc nới type.

`git diff --check` toàn worktree báo whitespace có sẵn tại `src/index.css:48`. File này byte-identical baseline, không sửa whitespace để giữ nguyên CSS. Kiểm tra này không thay cho lint SFC untracked. Chưa có clean-worktree claim.

## Checkpoint23/09 — hoàn tất source inventory, tích hợp map/router

Đường dẫn source/test bên dưới tương đối với `vehicletracking-frontend/`. Danh sách từng file đối ứng tại [migration-inventory.md](verification/migration-inventory.md); manifest baseline không sửa để ghi tiến độ.

- `src/main.ts`, `src/App.vue`, `src/app/router.ts`: entry Vue candidate,15URL+root redirect+catch-all; preview dùng entry này thay vì router riêng cho test. `index.html`/React entry không đổi. `tests/unit/router.test.ts`: role guards trên route graph thật, query/back-forward không remount operations; chuyển map workspace/fleet tab có remount; query lặp lấy giá trị đầu như `URLSearchParams.get` của React.
- `src/components/MapComponent.vue`, `src/pages/MapPage.vue`, `src/composables/useMapLayers.ts`: single Leaflet owner; nối station/fleet/route/traffic/simulator/alert panels, camera/markers/realtime. Giữ panes/options/CSS; dispose map sau layer scopes, hủy timer retry tile khi đổi theme/unmount.
- `src/components/route/{RoutePanel,RouteWorkspace,RouteDrawer,RouteCreateContent,RouteViewContent}.vue`: danh sách/xem/tạo/sửa tuyến; giữ draft occurrences, form normalization, dirty discard và stable key. `tests/unit/routes.test.ts` kiểm tra responseA trả sauB, refresh khi đang sửa, busy/disposal, trạm inactive/lặp kề nhau và dwell.
- `src/components/{TrackingPanel,VehicleDrawer}.vue`: port hai component còn lại dù chưa có route dùng trực tiếp, không nối thêm tính năng. `tests/unit/tracking-panels.test.ts` kiểm tra filter/keyboard/progress/empty state, selected/follow/timeline/callbacks.
- `tests/unit/map-integration.test.ts`: mount component map đầy đủ với Leaflet thật,20vòng mount/unmount; mỗi lần map/SSE được dọn; đổi mode không tạo thêm owner; đổi basemap giữ một tile layer và hủy listener/retry cũ. Đây là jsdom+fakeAPI, **không** thay cho20vòng điều hướng Chromium hoặc kiểm tra memory/browser.
- `tests/unit/business-pages.test.ts`: report filter/retry/stale abort, user create payload/busy/available drivers/toggle, scheduleONCE/WEEKLY validation/confirm/retry, FleetConfirmDialog slot/error/busy Escape/disabled-confirm. Dialog API được mô phỏng ở jsdom, chưa chứng minh native focus trap.

### Lệnh kiểm chứng23/09

Node24.16.0, working directory `vehicletracking-frontend/`:

| Lệnh                                                                          | Kết quả                                                                                                                                                                        |
| ----------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `npm run test:unit`                                                           | Exit0; **65tests/10files**,0unhandled errors: auth9, fleet10, map-state12, map-components12, routes5, map-integration3, router4, tracking-panels2, business-pages7, toolchain1 |
| `npm run typecheck`                                                           | Exit0, kiểm tra toàn bộ SFC/entry/router/unit tests                                                                                                                            |
| `npm run lint`                                                                | Exit0,0error;6warning React cũ                                                                                                                                                 |
| `npm run test:motion`                                                         | Exit0,1test file pass                                                                                                                                                          |
| `npm run build`                                                               | Exit0,1958modules; production vẫn React                                                                                                                                        |
| `./node_modules/.bin/vite build --config tests/visual/vite.preview.config.ts` | Exit0,2019modules; Vue entry246.63kB, MapComponent287.81kB trước gzip                                                                                                          |
| SHA256124file source baseline                                                 | `changed=[]`;16CSS byte-identical                                                                                                                                              |

Ba CSS chunk build Vue và React có cùng hash: `BcmlkbS4`238.48kB, `2MReJumJ`22.16kB, `DZ2T5WZb`2.80kB. Không sửa CSS/layout/backend/API/migration để đạt test. Build React có thể đọc cấu hình local theo hành vi hiện hữu; preview `envDir:false`, capture chỉ dùng fixture.

Các lỗi test/tooling đã xử lý: jsdom thiếu `HTMLElement.checkVisibility` (thêm fixture và restore descriptor, không sửa production để chiều test); mock dynamic SFC cần ES-module marker; unit dùng `Array.at` ngoài targetES2020 đã đổi sang `slice(-1)[0]`. Không tắt rule/checker hoặc bỏ unhandled errors để qua test.

### Browser/visual23/09 — chưa đạt gate

Đã được môi trường cho phép chạy lại serverlocalhost5175(baseline frozen dist),5176(Vue preview) và Chromium151.0.7922.34. Sau đó quyền chạy thêm bị chặn bởi hạn mức, không chạy vòng tránh.

| Lượt                      | Kết quả thực tế                                                                                              |
| ------------------------- | ------------------------------------------------------------------------------------------------------------ |
| E Vue                     | 76ảnh,exit0,0pageerror; soReactD có50PNG byte-identical,76bộmetrics khớp; chưa cùng harnessclock             |
| F React + Vue             | Mỗi bản76ảnh,exit0,0pageerror; cùngclock/font/API fixtures; **54/76exact-pixel**, **76/76DOM metrics khớp**  |
| G map-only React + Vue    | Mỗi bản17ảnh,exit0,0pageerror; có `tiles.json` đo decode/opacity                                             |
| H readiness gate đầu tiên | Reactexit1,0ảnh do chờ `.leaflet-tile` khiclockpaused làm lazy map chưa mount; Vueexit0,17ảnh                |
| Readiness loop sửa sauH   | Đã đổi sang tiếnclock trong vòng chờ cả lazy map/tile; **chưa chạy** vì auto-review báo usage limit tới13:00 |

F:59ảnh ngoài map có54ảnh pixel-identical;5ảnh còn lại khác1–33pixel.17ảnh map khác lớn. Chưa có lặp baseline đủ để chấp nhận tolerance; **không nới threshold, không mask map, không kết luận parity**. Các selector đo trong `capture.mjs` không bao phủ mọi element, nên76bộmetrics giống nhau cũng chưa đủ nghiệm thu.

G xác nhận ở1440px cảhai bản đều30tile đãdecode(`naturalWidth=256`, `leaflet-tile-loaded`), nhưng React opacity0, Vue opacity1. Leaflet `node_modules/leaflet/src/layer/tile/GridLayer.js::_tileReady/_updateOpacity` dùng Date và RAF để fade; giả thuyết còn lại là thời điểm lazy map/effects và controlledclock trong harness. Không coi đây là lỗi sản phẩm đã xác định. Đã sửa SVG fixture thêm `fill="none"` cho đường stroke để không tô đen nền; chỉ fixture test thay đổi.

Harness `tests/visual/capture.mjs` hiện kiểm tra readiness cho map và có `UI_SCOPE=maps`; `tests/visual/compare.mjs` đọcPNG offline, báo exact pixels/metrics, không cập nhật baseline hoặc quyết định gate thay người review. SSE fixture có counters trong `window.__fixtureLiveResources` để dùng cho browser lifecycle tiếp theo; chưa coi counters này là test đã chạy.

Artifact [vue-map-checkpoint-2026-09-23.tar.gz](verification/artifacts/vue-map-checkpoint-2026-09-23.tar.gz) lưuF/G gồm ảnhhai bản,metrics,results,requests vàtile diagnostics; SHA256 `25638b0f4745a01a3f10794ce46d13163609b1d5398a424242f86aa268810bb0`. Đây là checkpoint **chưa nghiệm thu**, không ghi đè React baseline. Tất cả API/mutation của các lượt capture đều fixture; không dùng credential/nghiệp vụ thật.

## Checkpoint tiếp tục 23/09 — workflows, HTTP và candidate preview

Main agent thực hiện trực tiếp, không gọi subagent. Không sửa source nghiệp vụ, CSS/layout, backend/API/schema, cấu hình `.env` hoặc production entry trong lượt này. Các đường dẫn dưới đây tương đối với `vehicletracking-frontend/`.

### Các kiểm tra và công cụ đã thêm

- `tests/unit/page-workflows.test.ts`: 6 test mount App/router/auth/services thật với HTTP fixture; kiểm tra driver bị chuyển khỏi route admin và chỉ gọi assigned endpoints, ngày nghiệp vụ Việt Nam qua ranh giới UTC, stale detail/unmount abort, lỗi/retry/logout, cảnh báo đọc một/đọc tất cả/deep link, xác nhận xóa có conflict/retry/busy, polling dashboard/cảnh báo được dọn khi đổi trang. Native dialog được mô phỏng tại jsdom; không coi đây là browser focus test.
- `tests/unit/http-contract.test.ts`: 5 test dùng HTTP wrapper thật, kiểm tra credentials và safe methods, CSRF bootstrap single-flight không bị hủy theo request của trang, giải mã cookie, bootstrap lỗi không gửi mutation và cho retry. Không gọi backend thật hoặc đọc credential local.
- `tests/unit/preview-server.test.mjs`: 5 test middleware không mở socket, kiểm tra dev/built deep link dùng đúng candidate HTML, assets/JSON/public guide đi qua, lỗi thiếu build được chuyển tiếp. `vitest.config.ts` nhận cả `.test.ts` và `.test.mjs`; test Node middleware dùng JS vì app không cài `@types/node`, không nới typecheck hoặc thêm dependency chỉ để ép config vào browser TypeScript.
- `tests/visual/vite.preview.config.ts#configurePreviewServer` phục vụ candidate HTML đã build cho cold deep link; cả dev và preview bỏ qua `/huong-dan/` để trang hướng dẫn không bị SPA ghi đè. `envDir:false` được giữ. `package.json#scripts` thêm `dev:vue`, `build:vue`, `verify:vue`, `preview:vue`, `test:e2e`; `dev`/`build` chính vẫn React.
- `tests/visual/audit-candidate.mjs`: build và kiểm tra module graph, entry candidate, hash baseline; report [vue-candidate-audit-2026-09-23.json](verification/artifacts/vue-candidate-audit-2026-09-23.json) ghi 2018 module IDs, không React/TSX/fixture trong graph, 124 file source và 16 CSS không đổi. Đây không phải kiểm tra đã gỡ React khỏi toàn package/lockfile.
- `tests/e2e/migration-smoke.mjs`: đã viết 7 kịch bản browser cho dirty-discard/native dialog/focus, report query, driver route/detail/logout, mobile nav và schedule drawer, map query/back-forward, 20 vòng điều hướng map và public guide. Chỉ nhận localhost, intercept API và chặn nguồn ngoài fixture. **Chỉ syntax-check, chưa thực thi browser**. Counters SSE/DOM không thay cho audit toàn bộ memory/listener/RAF hoặc full CRUD/shape/simulator matrix.
- `tests/visual/capture.mjs`: chặn target ngoài localhost, ghi screenshot đang chạy và failure vào report; khi readiness loop hết hạn ghi `map-readiness-failure.json` gồm trạng thái lazy map, tile decode/opacity. Không sửa opacity/CSS hoặc mask map. Readiness loop hiện tại vẫn chưa được chạy lại.

### Lệnh đã chạy tại checkpoint này

Node 24.16.0, working directory `vehicletracking-frontend/`:

| Lệnh                                                                                       | Kết quả thực tế                                                                                                                          |
| ------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `npm run test:unit`                                                                        | Exit 0; **81 tests / 13 files**, 0 unhandled errors; thêm page-workflows 6, http-contract 5, preview-server 5 so với checkpoint 65 tests |
| `npm run test:motion`                                                                      | Exit 0; 1 test file pass                                                                                                                 |
| `npm run typecheck`                                                                        | Exit 0; SFC và TypeScript unit tests được kiểm tra                                                                                       |
| `npm run lint`                                                                             | Exit 0; 0 error, 6 warning React có sẵn                                                                                                  |
| `npm run build:vue`                                                                        | Exit 0; 2019 transformed modules; CSS chunk hashes giữ nguyên                                                                            |
| `npm run verify:vue`                                                                       | Exit 0; build Vue và audit graph/hash pass; production entry còn React                                                                   |
| `node --check tests/e2e/migration-smoke.mjs`                                               | Exit 0; chỉ kiểm tra cú pháp, không phải E2E pass                                                                                        |
| `node --check tests/visual/capture.mjs` và `node --check tests/visual/audit-candidate.mjs` | Exit 0                                                                                                                                   |

Các lỗi test lúc soạn đã sửa trước khi chạy toàn bộ: assertion lỗi HTTP không truy cập `.name` trên union `Response | Error`; mock Node filesystem giữ default export cho Vite. Không sửa HTTP production hoặc tắt checker để chiều fixture.

Không chạy browser/server mới ở checkpoint này: lần trước auto-review báo giới hạn đến 13:00 ngày 23/09. Không thử đường vòng. Chưa chạy `test:e2e`, backend/Docker, candidate built-browser hoặc visual loop mới; không dùng unit/audit pass để đánh dấu G0/G3–G8 hoàn tất. Hướng dẫn chạy và phần cần kiểm tra tiếp có trong [walkthrough.md](walkthrough.md).

## Phần còn lại tại checkpoint trước khi người dùng duyệt cutover

- Chạy lại readiness loop, xác định tile/clock của baseline và lặp baseline ổn định; soảnh Vue cảdev vàproduction build, cácstate/viewport chưa bao phủ.
- Hoàn thiện browser functional/network/lifecycle matrix: CRUD/phân công/status, fullmap coordinate/shape/simulator/traffic, keyboard/native dialog, cookie/CSRF và20vòng điều hướngmap. Unit/mock pass không thay cho những phần này.
- Chỉ khi parity đủ bằng chứng mới gỡ React/dependencies, đổi `index.html`, cập nhật CI/tài liệu dev và chạy cleaninstall/build/container regression. Chưa deploy/commit/push, chưa Verified/Reviewed.

## Cutover 23/09 — người dùng chấp nhận giao diện và duyệt gỡ React

### Cơ sở và phạm vi

Người dùng xác nhận “ok rồi, giao diện chuyển từ react sang vue đã đủ”, sau đó trả lời “có” khi được hỏi chuyển Vue thành mặc định và gỡ source/dependency React. Thực hiện theo duyệt mới; cập nhật Spec/Plan để phân biệt **chấp nhận giao diện thủ công** với **automated visual/E2E chưa đầy đủ**. Không thay ngưỡng soảnh, không mask map hay ghi đè baseline. Feature chưa ghi Verified/Reviewed toàn bộ.

Trước xóa, kiểm tra manifest:124file source có checksum khớp,66file React đều có đối ứng tồn tại, không có TSX mới ngoài baseline. Đã gỡ đúng51TSX + 14hooks + `src/app/routeConfig.ts`; metadata Vue ở `src/app/navigation.ts`. Archive React137file+build vẫn nguyên SHA256 `16115fa4c3516b7fed42ac1a58440b62bdbe80a545b472e711ff82639b16bbd2`, có thể phục hồi có chọn lọc sau khi đối chiếu thay đổi mới. Không reset/stash hoặc xóa file ngoài inventory được duyệt.

### Evidence source/config

- `vehicletracking-frontend/index.html` → `src/main.ts` → `App.vue`/`app/router.ts`: entry production Vue duy nhất, giữ root và CSS import order. `src/pages/MapPage.vue` giữ lazy map; không sửa layout/logic map trong cutover.
- `package.json`, `package-lock.json`, `vite.config.js`, `tsconfig.json`, `.oxlintrc.json`: gỡ React/react-dom/react-router-dom/lucide-react/types/plugin, không còn JSX compiler/rules React. Vue plugin, TypeScript strict và lint template được giữ. `npm install --offline --ignore-scripts --no-audit --no-fund` cập nhật lock và gỡ11packages; sau đó clean `npm ci` được chạy thực tế.
- `vite.config.js#server` giữ port5173 và strictPort; không đặt `envDir:false` trong config chính. Scripts `dev:vue`/`build:vue`/`preview:vue` là alias script chính. Vì vậy dev Vue đọc `.env` theo cơ chế Vite thông thường và không còn mặc định dùng test preview5176; không đọc/in giá trị hoặc sửa `.env` của người dùng. Không đổi backend CORS/session/CSRF.
- `tests/unit/cutover.test.mjs`:4test kiểm tra entry/root, config môi trường/cổng/aliases, plugin/strict/noJSX, package và lock không React. Initial run4test fail vì filesystem URL bị biến đổi trong môi trường test; đổi sang `resolve(process.cwd(), path)` đọc đúng project root, không sửa production để chiều test. Sau sửa85test pass.
- `tests/visual/audit-vue.mjs` thay audit candidate: build entry production thật bằng config chính với `envDir:false`, output biệt lập `dist-vue-audit/`. Audit package/lock/source và graph, không có test-only entry. `--baseline` đối chiếu58file source giữ lại. Report [vue-cutover-audit-2026-09-23.json](verification/artifacts/vue-cutover-audit-2026-09-23.json):2017module IDs, không React/TSX/fixture, không legacy source còn lại,16CSS không đổi.
- `.github/workflows/ci-cd.yml#jobs.frontend`: thay `tsc` bằng `npm run typecheck` để kiểm tra SFC, thêm unit và motion; giữ npm ci/build. Không sửa backend/deploy jobs/triggers và chưa chạy pipeline từ xa.
- `AGENTS.md`, `docs/workflow.md`, frontend `README.md`, feature Spec/Plan/Inventory/Walkthrough được cập nhật. `Dockerfile`/`Caddyfile` không đổi: build Vue vào `dist`, giữ SPA/static guide/API/SSE proxy contract.

### Lệnh kiểm chứng sau clean install

Node24.16.0, working directory `vehicletracking-frontend/`:

| Lệnh                                                                                      | Kết quả                                                                                    |
| ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `npm ci --offline --no-audit --no-fund`                                                   | Exit0, cài234packages từ lock/cache, chạy lifecycle scripts bình thường                    |
| `npm run typecheck`                                                                       | Exit0; Vue SFC/TS/unit tests                                                               |
| `npm run lint`                                                                            | Exit0; không còn warning React, không tắt lint Vue                                         |
| `npm run test:unit`                                                                       | Exit0; **85 tests / 14 files**, gồm4cutover regression mới và81test hiện hữu               |
| `npm run test:motion`                                                                     | Exit0;1test file pass                                                                      |
| `npm run build`                                                                           | Exit0; **2018modules**, `dist/index.html`; entry JS246.63kB, map287.79kB trước gzip        |
| `npm run verify:vue -- --baseline`                                                        | Exit0;2017graph module IDs,58source/16CSS unchanged, không React/TSX/fixture/source legacy |
| `npm ls --depth=0`                                                                        | Exit0; dependency tree hợp lệ, không React                                                 |
| `node --check tests/e2e/migration-smoke.mjs` và `node --check tests/visual/audit-vue.mjs` | Exit0; chỉ syntax-check                                                                    |
| `git diff --check` với các file config/CI/README/AGENTS/workflow đã sửa trong cutover     | Exit0; không kiểm nhận sạch toàn worktree bẩn có sẵn                                       |

CSS production vẫn cùng chunk hashes `BcmlkbS4`238.48kB, `2MReJumJ`22.16kB, `DZ2T5WZb`2.80kB như baseline. Không sửa CSS, public assets, services/types/utils hoặc backend/schema ở lượt cutover.

### Giới hạn còn lại

- Không chạy browser/server mới trong cutover: hạn mức môi trường trước đó còn đến13:00 ngày23/09. Không thử vòng tránh, không tự coi `test:e2e` hoặc readiness loop là pass.
- Browser native focus/keyboard, full CRUD/shape/simulator matrix, built-browser và container/API/SSE smoke chưa chạy đầy đủ. Unit/jsdom và build không thay cho các kiểm tra này.
- Không chạy backend tests vì không đổi backend; không đăng nhập/mutation vào dữ liệu thật. Người dùng báo kết nối/giao diện đã ổn là xác nhận của người dùng, không phải automated backend integration.
- Chưa deploy/commit/push. Tự review ở `review.md`, không có review độc lập. Runtime source đã Vue-only; trạng thái regression toàn feature vẫn Implementing.
