# Plan — 032: React → Vue, giữ nguyên giao diện

**Trạng thái: Implementing. Người dùng đã duyệt triển khai qua yêu cầu “tiến hành thực hiện”.**

**Cập nhật (2026-09-22):** Người dùng yêu cầu tiếp tục sau khi được báo phương án TypeScript `6.0.3` đã kiểm chứng; đã áp dụng bộ dependency và test runner trong frontend. React entry vẫn giữ cho tới khi Vue candidate đầy đủ. Chi tiết tại [evidence.md](evidence.md).

**Cập nhật (2026-09-23):** Đã port toàn bộ source inventory, có `src/main.ts`, `App.vue`, `app/router.ts` và Map/Route workspace Vue chạy trong preview. Đã chụp76ảnh mỗi bản, chưa đạt visual gate vì tile baseline chưa hiện trong ảnh; điều tra clock/readiness đang tiếp tục. Không cutover hoặc gỡ React. [Inventory đối ứng](verification/migration-inventory.md) và evidence phân biệt rõ source đã port với hành vi đã kiểm chứng.

**Cập nhật cutover (2026-09-23, sau các checkpoint trên):** Người dùng xác nhận giao diện Vue đã đủ và duyệt chuyển mặc định/gỡ React. Đã chuyển `index.html` sang `src/main.ts`, gỡ 51TSX + 14hooks + metadata icon React, dependency/plugin/types/JSX; cập nhật CI frontend. `dev` và `dev:vue` cùng cấu hình thông thường, đọc `.env`, port5173 strict để không lặp lỗi origin/API của preview. Không thay CSS/backend/deploy. Những dòng trạng thái trước cập nhật này là lịch sử.

Chuyển framework hoàn toàn, không redesign. P7 source/build/toolchain đã cutover theo duyệt trực tiếp của người dùng; P8 browser/visual/container regression chưa đầy đủ. Không gọi chấp nhận giao diện thủ công là pixel-parity pass, không tự ghi Verified/Reviewed. Scope và điều kiện chấp nhận tại [requirement.md](requirement.md), contract tại [spec.md](spec.md), kiểm thử tại [test-plan.md](test-plan.md).

## Quyết định chính

- Vue 3 + Composition API/SFC TypeScript + Vue Router; giữ Vite/Leaflet và services/types/utils.
- Giữ nguyên CSS/assets/DOM contract, dùng baseline React hiện tại và ảnh so sánh để kiểm chứng layout/màu. Maps cũng port khỏi React, không để React island.
- Không thêm Nuxt/SSR/Pinia/UI kit/map wrapper trong phạm vi này; auth state typed ở cấp app, business state gần owner.
- Port theo nhóm có test, chỉ bàn giao/cutover khi toàn bộ đạt. React baseline read-only luôn còn để đối chiếu; không phải runtime dùng chung với Vue.
- Không đổi API/database/backend/provider. Điều chỉnh CI frontend typecheck/test là cần thiết; không đụng deployment trigger hoặc tự deploy.

## P0 — Đóng băng baseline và inventory

- **Mục tiêu:** có chuẩn thật để chứng minh “không đổi giao diện”, không làm mất dirty/untracked code.
- **File/đầu ra:** thêm manifest và artifacts dưới `docs/features/032-vue-frontend-migration/verification/` khi implement; baseline source/build snapshot whitelist ở thư mục kiểm thử riêng, không chứa `.env`/secrets.
- **Thực hiện:** kiểm tra lại git status; map từng TSX/hook/component/route/API interaction; ghi checksum 16 CSS và assets; giữ snapshot tất cả frontend source cần thiết, kể cả untracked. Build React và chụp các state/viewport theo Test-Plan, test baseline ổn định qua hai lần chụp.
- **Kiểm tra:** lint/tsc/build hiện tại, motion tests, network/visual/lifecycle baseline. Warning/bug cũ ghi rõ, không tự sửa.
- **Contract/schema:** không đổi.
- **Rủi ro/phụ thuộc:** HEAD không chứa đủ UI hiện tại; chỉ chụp ảnh 031 hoặc chỉ git diff sẽ thiếu code/state. Không tự commit/stash.
- **Done gate G0:** manifest đầy đủ, baseline chạy độc lập với source candidate và phục hồi được; screenshot có font/map data đúng, không có secret.

## P1 — Compatibility spike và bộ kiểm chứng

- **Mục tiêu:** chứng minh toolchain Vue phù hợp trước khi đổi hàng loạt.
- **File dự kiến:** sau spike mới sửa `vehicletracking-frontend/package.json`, `package-lock.json`, `vite.config.js`, `tsconfig.json`, `.oxlintrc.json`; thêm `eslint.config.js`, `vitest.config.ts`, `playwright.config.ts`, `tests/{unit,e2e,visual,fixtures}/`.
- **Thực hiện:** spike bằng temp project biệt lập không làm mất app; xác minh published versions/peer của Vue, Vue Router, Vue plugin, vue-tsc, TypeScript 7, `@lucide/vue`, Vitest/VTU/DOM environment, ESLint Vue và Playwright. Build một SFC có typed props/events/slots, Leaflet shallowRef, dialog và icon đang dùng. Không `--force`, không compiler downgrade âm thầm.
- **Kiểm tra:** type lỗi có chủ đích phải fail; template lỗi phải fail lint; import icon/SVG match; production build và basic mount/unmount pass. Sau đó khóa phiên bản và runner tái lập, test harness dùng cả React baseline và Vue candidate.
- **Contract/schema:** không đổi runtime API; thêm dev scripts/dependencies có mục tiêu kiểm thử.
- **Rủi ro/phụ thuộc:** Vue tooling chưa được kiểm thử với TS7 của repo. Nếu không tương thích, **dừng G1 và trình lựa chọn version**, không port tiếp với checker bị vô hiệu hoá.
- **Done gate G1:** bộ dependency đã được chạy thực tế và lock, React baseline vẫn sử dụng được; lint script/template/typecheck thực sự kiểm tra SFC.

## P2 — Entry, router, auth và shell

- **Mục tiêu:** có Vue candidate độc lập, giữ URL/session và khung hình.
- **File dự kiến:** `src/main.ts`, `App.vue`, `app/router.ts`, `app/ApplicationShell.vue`, `app/routeConfig.ts`, `auth/{authState,useAuth}.ts`, `pages/MapPage.vue`, `index.html`; auth/business primitives cần cho shell. Giữ `#root` và CSS import order.
- **Thực hiện:** port 15 route + redirect/catch-all/guards; app-wide auth bootstrap, history state `from`, roles, logout; shell/skip link/mobile nav/fullBleed scope; map/fleet stable key rules. Vue candidate không import component React; các route chưa port không được xem là hoàn tất hoặc deploy.
- **Kiểm tra:** auth/router unit, deep-link/query/back-forward, guest/admin/driver, nav focus/Escape, shell visual mọi viewport. Không fetch dữ liệu admin bằng driver.
- **Contract/schema:** không đổi HTTP/auth/CSRF; dùng lại services.
- **Rủi ro/phụ thuộc:** G0/G1; Vue route reuse khác React key; import CSS/lazy order gây layout drift.
- **Done gate G2:** entry/shell/auth/router hoạt động đúng trong candidate; baseline React vẫn phục vụ cổng riêng để so sánh. Chưa phải app migration hoàn chỉnh.

## P3 — Business primitives, auth UI và trang quản trị

- **Mục tiêu:** các màn hình không map đạt parity trước khi tích hợp map.
- **File dự kiến:** `.vue` đối ứng `components/business/*`, `pages/{LoginPage,AdminRegistrationPage,DashboardPage,AlertsManagementPage,ReportsPage,ScheduleManagementPage,UserManagementPage,DriverPortalPage,NotFoundPage,RoadmapPage}.tsx`; composable dùng chung cần thiết. RoadmapPage port nhưng không thêm route.
- **Thực hiện:** giữ DOM/template text/styles, form validation/busy, metric/filter/error/empty/retry, polling và timezone; native dialog theme/focus. Khung auth và driver giữ scroll owner.
- **Kiểm tra:** browser API request capture và state screenshots, registration validation/success, reports filters, schedule drawer/toggle, alerts actions, user roles và driver portal; timer cleanup khi chuyển trang.
- **Contract/schema:** không đổi.
- **Rủi ro/phụ thuộc:** G2; `v-model` coercion, wrapper/Teleport, async dialog focus và stale page response.
- **Done gate G3:** mỗi trang hoàn chỉnh qua functional/visual theo manifest, không bù mismatch bằng sửa CSS/màu.

## P4 — Fleet: xe, tài xế, chuyến và chi tiết

- **Mục tiêu:** port luồng state/mutation dùng chung giữa quản trị và map.
- **File dự kiến:** `composables/useFleetWorkspace.ts`, `useTripEta.ts`, `useTripCheckIns.ts`; `pages/FleetManagementPage.vue`; `.vue` đối ứng toàn `components/fleet/`, `components/business/FleetManagementTable`.
- **Thực hiện:** chuyển screen union/ref, selection/filter/query, abort/reload/mutation, editor/detail/history/revision/confirmation. Giữ `lockedTab` cho bảng riêng trang quản trị, card mode cho maps; giữ mounted/hidden/reset semantics.
- **Kiểm tra:** CRUD/gán xe-tài xế-chuyến, transition/busy/conflict, trip filter deep-link, ETA/check-in/history/revision; thao tác nhanh chọn A→B khi response A trả trễ; keyboard và desktop/mobile parity.
- **Contract/schema:** không đổi, dùng services/types hiện có.
- **Rủi ro/phụ thuộc:** G2; business primitives từ P3; callback/event payload khác giữa React/Vue. Không dùng type any để né ref unwrapping.
- **Done gate G4:** management fleet và nhánh card dùng cho maps cùng đạt tests, không mất khả năng chỉnh sửa/xem lịch sử.

## P5 — Lõi map và composables realtime

- **Mục tiêu:** giữ tối ưu maps/telemetry nhưng bỏ React lifecycle.
- **File dự kiến:** `components/MapComponent.vue`; `composables/{useCompactLayout,useLiveOperations,useMapCamera,useVehicleMarkers,useTraffic,useRouteTraffic,useSelectedVehicleRoute,usePlannedVehicleAnchors,useSimulationFleet,useSimulator}.ts`; Vue controls/drawers/panels gốc.
- **Thực hiện:** single map owner, raw instances/layers, panes/camera/ResizeObserver, RAF marker interpolation, SSE bootstrap/reconnect, polling/cache/abort; reuse utilities nguyên trạng. Giữ Google raster hiện tại/HERE backend boundary, themes/toggles, selected/follow vehicle.
- **Kiểm tra:** unit cleanup/stale response; lifecycle counters sau 20 vòng vào/ra; query mode switch không reset simulator; traffic request cadence; source motion tests; map screenshot với tile/telemetry xác định.
- **Contract/schema:** không đổi SSE/backend/provider, không thêm SDK.
- **Rủi ro/phụ thuộc:** G2/G4; rủi ro cao nhất: reactive proxy class, watcher phát lặp, duplicate listeners, map remount và camera padding.
- **Done gate G5:** core map/realtime chạy không duplicate resource; visual/camera parity kiểm chứng. Không coi map trống hoặc screenshot chỉ CSS là pass đầy đủ.

## P6 — Trạm/tuyến/traffic/simulator và các layer chuyên biệt

- **Mục tiêu:** hoàn tất mọi thao tác trên bản đồ và các lazy component.
- **File dự kiến:** `composables/useStationWorkspace.ts`; `.vue` đối ứng `components/{route,traffic,operations}/` và các panel/drawer/controls còn lại trong manifest, kể cả file không gắn trực tiếp một route.
- **Thực hiện:** station coordinate picker/CRUD, route draft/reorder/shape edit/inspection, traffic overlay/popups, simulator selected/multi-vehicle controls/attempts/replay và cảnh báo. Giữ request sequence token, stale guard, DOM popup listener cleanup; lazy import fallback giữ nguyên.
- **Kiểm tra:** map test matrix P5 mở rộng theo Test-Plan; route edits khi request lỗi/chậm, drag/select/cancel, simulation1x/5x/10x và reset, lịch sử/check-in/ETA; so CSS cả cold-load và sau tải lazy chunk.
- **Contract/schema:** không đổi.
- **Rủi ro/phụ thuộc:** G4/G5; side effects giao nhau khi đổi workspace, popup DOM nằm ngoài Vue tree, thứ tự cleanup layer với map.
- **Done gate G6:** toàn bộ inventory frontend có đối ứng Vue, 15 URL/catch-all đủ hành vi, maps không thay giao diện hay tối ưu đang có.

## P7 — Gỡ React và tích hợp build/CI

- **Mục tiêu:** một frontend Vue hoàn chỉnh, không bridge hoặc mã React hoạt động.
- **File dự kiến:** package/lock/plugin/tsconfig/lint, `index.html`, React `.tsx`/hooks cũ đã được thay theo manifest; `.github/workflows/ci-cd.yml` chỉ frontend checks; cập nhật mục frontend của `AGENTS.md`, `docs/workflow.md` và hướng dẫn dev có liên quan.
- **Thực hiện:** chỉ xoá file React đã có đối ứng verified; remove React deps/types/plugin/jsx runtime config, giữ Vue main duy nhất; `npm run typecheck` dùng `vue-tsc`; thêm test CI với browser/version đã khoá. Giữ Docker build context, `dist`, Caddy/proxy/guide assets; không cần đổi compose hoặc backend.
- **Kiểm tra:** clean npm ci, lint/typecheck/tests/build; import/runtime/lock graph scan; local container deep-link/API/SSE proxy smoke. Không dùng check grep chữ `reactive` như React dependency.
- **Contract/schema:** không đổi API/deploy runtime; command kiểm tra Vue khác React có chủ đích.
- **Rủi ro/phụ thuộc:** G3–G6; hidden React import ở metadata hoặc component ít dùng; source untracked mới của người dùng không được xoá.
- **Done gate G7:** không React dependency/import/runtime trong app cuối, build/test/deep-link qua; không có fallback React cho màn hình chưa port.

## P8 — Regression toàn diện và review bàn giao

- **Mục tiêu:** chứng minh AC1–AC7 trước kết luận hoàn thành.
- **File/đầu ra:** evidence/walkthrough/review sau implementation, artifacts baseline/actual/diff và logs; cập nhật trạng thái feature đúng thực tế.
- **Thực hiện:** chạy full matrix chức năng/visual/network/lifecycle và test backend boundary trên môi trường tách biệt; tự rà scope/secret/dirty changes. Theo chỉ đạo mới của người dùng, main agent tự thực hiện và tự review; không gọi reviewer/subagent. Không coi tự review là review độc lập.
- **Kiểm tra:** 15 URL + redirect/404, 3 viewport chính và map mobile bổ sung, từng state/flow trong Test-Plan, candidate production build; không chỉ `npm run build`.
- **Contract/schema:** không đổi; không auto push/deploy.
- **Rủi ro/phụ thuộc:** G7; thiếu Docker/provider/browser hoặc reviewer phải ghi blocker/gap, không tự coi là đã pass.
- **Done gate G8:** AC có bằng chứng và findings migration đã xử lý; Verified rồi Reviewed chỉ khi review độc lập/người dùng chấp nhận. Nếu sai layout/màu hoặc thiếu một luồng map, chưa hoàn thành.

## Điều phối và ownership dự kiến khi implement

Theo yêu cầu mới “loại bỏ hết subagent đi, giờ bạn thực hiện cho tôi thôi”, main agent thực hiện toàn bộ công việc, không gọi hoặc khởi động lại subagent. Không xóa cấu hình `.codex` vì yêu cầu hiện tại là dừng delegation trong quá trình triển khai. Các phân chia ownership trước đó không còn áp dụng.

## Lịch sử duyệt và trạng thái thực hiện

Bộ kế hoạch hoàn chỉnh: [Requirement](requirement.md) → [Research](research.md) → [Survey](survey.md) → [Spec](spec.md) → [Test-Plan](test-plan.md) → Plan này. Người dùng đã duyệt P0–P8. Thay compiler nếu spike không đạt phải được báo và duyệt riêng theo G1.

- P0: snapshot137file+manifest+build bất biến; các lần B/C/D có76ảnh React,0runtime errors. Lần F có76ảnh mỗi bản,54ảnh exact-pixel và76bộ DOM metrics khớp. Lần G xác nhận tile React đã decode nhưng opacity0, Vue opacity1; chưa đạt baseline stability/maps. Harness có readiness gate mới nhưng lượt chạy cuối bị hạn mức môi trường chặn; G0 chưa đạt.
- P1: đã khóa TS6.0.3/Vue/test tooling, chạy typecheck/lint/unit thực tế; còn browser compatibility/harness đầy đủ. Không tắt checker hoặc nới tolerance để qua gate.
- P2–P4: đã có Vue auth/router guards/shell và toàn bộ trang business/auth/driver/fleet, editor/detail/history. Preview nạp `src/main.ts` → `App.vue`/`app/router.ts` dùng15URL+404; production `index.html` vẫn nạp `src/main.tsx`. Unit tests kiểm tra role/query/back-forward/fleet key; chưa đầy đủ matrix browser, chưa đánh dấu G2–G4 hoàn tất.
- P5/P6: đã port14hook sang14composable, thêm `useMapLayers` cho single map owner, hoàn tất source `MapComponent.vue`, `MapPage.vue`, các RouteWorkspace/Drawer/content và TrackingPanel/VehicleDrawer chưa nối route. Full map unit test20mount/unmount, tile retry cleanup và các form/route test đã có; functional/visual gate vẫn chưa đạt đầy đủ.
- Checkpoint kiểm chứng tiếp theo: 81 tests / 13 files pass, typecheck/lint/motion/build Vue pass. Thêm page workflows với App/router/services thật, HTTP credentials/CSRF tests và preview middleware tests; xem `evidence.md`. Candidate có lệnh dev/build/preview riêng, deep link và public guide được kiểm tra bằng middleware unit tests. Audit build graph không có React/TSX/fixture, 124 file source baseline cùng 16 CSS giữ nguyên; audit chỉ xét candidate, không phải đã gỡ dependency React.
- Đã viết browser smoke harness cho dialog/mobile nav/report/driver/map lifecycle và bổ sung readiness diagnostics; chưa chạy do giới hạn môi trường. Không dùng syntax-check hoặc jsdom để thay gate browser/visual. `walkthrough.md` hướng dẫn chạy candidate và các kiểm tra còn lại.
- Trước xác nhận giao diện của người dùng: P7 chưa cutover, P8 regression chưa đầy đủ.
- Sau xác nhận giao diện và duyệt cutover: P7 đã đổi entry/default scripts và gỡ đúng 66file React có source đối ứng; hash tất cả file bị gỡ khớp archive trước khi xóa. CI frontend dùng Vue typecheck + unit/motion/build, không đổi backend/deploy jobs. P8 còn browser regression/container và review độc lập; chỉ có tự review của main agent, không spawn subagent.
