# Spec — 032

Trạng thái: **đã được duyệt triển khai**. Contract giao diện/nghiệp vụ hiện có theo `survey.md`; AC1–AC7 theo `requirement.md`. Sau kết quả spike và yêu cầu tiếp tục của người dùng, dùng TypeScript 6.0.3 với vue-tsc 3.3.11 (xem `evidence.md`).

**Bổ sung quyết định 23/09:** Người dùng xác nhận “ok rồi, giao diện chuyển từ react sang vue đã đủ”, sau đó trả lời “có” khi được hỏi chuyển Vue thành mặc định và gỡ React. Cho phép cutover theo chấp nhận giao diện trực tiếp này; không coi đó là kết quả pass cho pixel matrix hoặc full browser E2E chưa chạy. Các khoảng trống kiểm chứng vẫn ghi trong Evidence/Review; không đổi layout, màu, API hoặc dữ liệu. Quyết định mới thay điều kiện trì hoãn cutover ở mục 7, không thay các yêu cầu chức năng.

## 1. Kiến trúc đích

- Vue 3 stable, Composition API, Single File Components (`.vue`, `<script setup lang="ts">`), Vue Router; giữ TypeScript strict, Vite, Leaflet và cấu trúc theo feature.
- Không Nuxt/SSR, React compatibility bridge, Vue map wrapper, UI kit hoặc CSS framework mới. App cuối chỉ một runtime Vue.
- Auth dùng một state instance cấp app, khởi tạo bằng factory và cấp qua typed provide/inject; router guards dùng cùng instance. Không tạo auth state mới trong từng page; không thêm persistence/localStorage/JWT.
- State form/filter/panel ở component sở hữu; state dùng lại đặt trong `src/composables/`. Giữ services/types/utils độc lập Vue; không chuyển HTTP vào template/component.
- Leaflet map/layer/marker, EventSource, DOM, AbortController dùng `shallowRef` hoặc biến raw phù hợp. Reactive snapshot và derived state không deep-proxy class instances.
- Giữ import động cho MapComponent, inspection/simulator layers như hiện tại; fallback giữ cùng nội dung/class. Dùng route dynamic import/async component có lifecycle rõ, không thêm transition làm đổi animation.

## 2. Mapping chuyển đổi

Đường dẫn dưới `vehicletracking-frontend/` trừ khi ghi khác.

| Hiện tại | Đích đề xuất | Điều phải giữ |
|---|---|---|
| `src/main.tsx`, `src/App.tsx` | `src/main.ts`, `src/App.vue`, `src/app/router.ts`, `src/pages/MapPage.vue` | `#root`, route tree, shell, map key/query, fallback |
| `src/auth/AuthContext.tsx`, `RouteGuards.tsx` | `src/auth/authState.ts`, `useAuth.ts`, router guards | Bootstrap/session/loading/login/logout/role/return path |
| `ApplicationShell.tsx`, 11 page TSX | Component `.vue` tương ứng | DOM, CSS class, layout, URL và hành vi hiện hữu; không gắn route mới cho RoadmapPage |
| `components/business/*`, `fleet/*` TSX | SFC cùng tên/trách nhiệm | Locked tab table/card, typed props/events, form/detail/dialog |
| Map/panels/operations/route/traffic TSX | SFC cùng tên/trách nhiệm | Canvas ownership, layers, popups, controls, lazy boundaries |
| 14 file `src/hooks/use*.ts` | `src/composables/use*.ts` | Return contract ý nghĩa tương đương, request abort, cleanup, cadence |
| `src/app/routeConfig.ts` | Giữ file, port icon component type | Route metadata/fullBleed/title/icon chính xác |
| `src/services/*`, `types/*`, `utils/*` | Giữ đường dẫn và contract | Không sửa nghiệp vụ/endpoint để thích nghi UI |
| 16 CSS và `public/` | Giữ nội dung và assets | Không đổi design token, font, spacing, breakpoint, kích thước hoặc màu |
| Toolchain/CI/docs | Vue-aware cấu hình/checks | npm ci, dist, Node24, base URL, Caddy/SSE/deep-link |

Mỗi file TSX/hook phải có mục trong manifest: port, replace bằng cơ chế Vue tương đương, hoặc giữ asset thuần. Không được bỏ sót component chưa có route. Chỉ xoá source React đã có đối ứng và đã qua test; không xoá chức năng, dữ liệu hoặc code không thuộc migration.

## 3. Contract giao diện — không redesign

1. Chụp baseline **React đang chạy từ working tree**, gồm source/CSS checksum, DOM structure, computed styles và ảnh. Baseline mới phải phủ maps có dữ liệu; ảnh feature 031 chỉ tham khảo coverage, không thay baseline.
2. Giữ toàn bộ CSS và root `#root`; giữ vị trí/class/id/data-attributes, nesting quan trọng, table semantics và order. Tên class icon giữ nếu selector phụ thuộc.
3. Không thêm wrapper DOM để tiện template. Dùng fragment/template không sinh element khi tương đương React Fragment; kiểm tra whitespace text, đặc biệt text cạnh icon/inline element.
4. Giữ thứ tự import/cascade, kể cả CSS tải theo lazy chunk. `ui-refresh.css` không được bị style nền tải sau đè lên. Không đổi sang scoped CSS hoặc tự Teleport dialog ra `body` khiến mất `.business-ui` ancestor.
5. Giữ light business theme, dark/map theme, các theme basemap và trạng thái toggle như baseline. Không sửa màu “cho hợp Vue”, đổi icon gần giống hay thêm animation.
6. Phân biệt “ẩn nhưng giữ state” (`hidden`/nhánh còn mounted) và “unmount” (conditional rendering). Không thay tất cả bằng `v-if`; form/drawer/tab được reset đúng thời điểm theo React key hiện có.
7. Vue `v-model` chỉ dùng nếu giữ đúng value/null/number/boolean/event timing/trim đang có. Không tự thêm `.trim`/`.number` khiến payload khác, mất số 0 đầu hoặc khác validation. Giữ `required`, min/max/pattern, `preventDefault`, disabled/busy và error messages.
8. Native dialog mở sau mount, đóng/cleanup đúng owner; giữ focus ban đầu, nền inert, Escape/busy, trả focus opener còn tồn tại. Giữ scroll owner, mobile navigation và resize behavior; chưa thay a11y semantics bằng thư viện mới.

Pass visual theo `test-plan.md`: màu/token tuyệt đối giống, geometry các khối chính không đổi ngoài sai số raster/subpixel đã định; ảnh deterministic mặc định không chấp nhận sai khác. Sai khác cần điều tra hoặc xin duyệt, không tăng tolerance/baseline tự động.

## 4. Routing và auth

- Giữ 15 URL, `/` và catch-all theo bảng Survey. Vue Router dùng web history cùng SPA fallback; không đổi thành hash URL.
- Preserve `/trips?vehicleId=…`, `mode=simulation`, `tripId` và điều kiện ID nguyên dương; back/forward/query update phải phản chiếu UI.
- Map key giữ ổn định khi đổi query/tracking↔simulation trong `/operations`; key riêng khi chuyển `/routes` và `/stations`. Không key map bằng `fullPath`, không remount theo từng SSE snapshot. Fleet route/tab dùng key riêng như hiện tại để không rò form/filter sang module khác. Không áp dụng KeepAlive toàn app vì sẽ giữ subscription ngoài ý muốn.
- Giữ `from` là internal pathname trong history state khi chưa đăng nhập như React hiện có. Không đổi sang redirect URL bên ngoài hoặc vô tình tự “sửa” việc chưa giữ query của luồng cũ.
- Bootstrap auth một lần dùng chung promise; không render protected data trước resolve. Giữ loading text và redirect theo ADMIN/DRIVER; không gọi admin endpoints trong driver page.
- Đăng ký admin giữ contract hiện có, không thêm mã mời hoặc đổi quyền. Không tự đăng nhập sau đăng ký nếu luồng hiện tại không làm thế.
- Session hết hạn, tài khoản bị khoá, 401/403 và network error hiển thị/redirect theo hành vi baseline; ghi bug cũ riêng thay vì thêm global interceptor mới ngoài scope.

## 5. Realtime, maps và lifecycle

- Mỗi map workspace có đúng một owner của `L.Map`; giữ config camera, panes/z-index, layer order, selection, focus/follow, bounds/padding và resize calculations theo source hiện tại.
- Google raster hiện hữu và HERE traffic/routing backend giữ nguyên; không chuyển provider. Không thêm khoá provider vào `VITE_*`.
- Mount map sau DOM ready; layer phụ chỉ chạy khi map ready. Watch dependency cụ thể, cleanup trước lần thay thế và unmount. Không deep-watch toàn bộ snapshot rồi tạo lại toàn map.
- `useLiveOperations`: HTTP initial snapshot + SSE `snapshot`, bỏ serverTime cũ, server offset, reconnect indicator và cadence giữ nguyên. Cleanup stop subscription, abort pending fetch và clear timer. Rời operations không còn stream của owner cũ.
- ETA/check-in/route traffic/planned anchors/fleet/server list giữ guard chống stale response và abort theo selected trip/vehicle/route. Kết quả request cũ không được ghi vào selection mới hoặc page đã unmount.
- Marker interpolation dùng lại `vehicleMotion.ts`; RAF dùng shared state/raw refs phù hợp, không phát Vue component rerender toàn cây mỗi frame. Drag map dừng follow như trước.
- Popup listeners, drag handlers, `ResizeObserver`, timers (kể cả retry tile), RAF, layer group và subscription có teardown tương ứng; test tạo/hủy/đổi route lặp lại. Dọn resource không làm thay thuật toán hay semantics sự kiện.
- Simulator giữ play/pause/speed 1x/5x/10x/stop/reset/replay/attempts, multi-vehicle layers, selected trip, realtime check-in/ETA và route revision/history. Giữ controls bị disable khi thiếu điều kiện theo baseline/API.

## 6. API/data/security/deployment contract

Không tạo/sửa endpoint, payload, entity, bảng, cột hoặc Flyway migration. Nguồn contract dùng lại là `src/services/*` và `src/types/*`, đối chiếu backend khi cần.

- API prefix, `VITE_API_BASE_URL`, `credentials: include`, cookie `XSRF-TOKEN` → header `X-XSRF-TOKEN`, shared bootstrap và status/error handling giữ nguyên (`services/http.ts`).
- `EventSource` giữ `withCredentials: true`; không thay SSE bằng polling chỉ để port nhanh.
- Backend vẫn là authority phân quyền; client guard không phải thay thế validation server.
- `vehicletracking-frontend` vẫn là build context, output `dist`; Caddy giữ `/api/*` proxy, SPA fallback và SSE flush. CI đổi command typecheck, thêm test cần thiết; không sửa deploy trigger/remote command trong scope.
- Node 24 giữ nguyên nếu bộ dependencies qua spike. Không lưu secret hoặc dữ liệu thật trong fixture/artifacts; baseline snapshot whitelist source/assets/config không chứa `.env` thật.

## 7. Chuyển đổi, compatibility gate và rollback

Triển khai theo module ở local, **chỉ bàn giao/cutover hoàn chỉnh khi tất cả gate qua**. Không đưa bản nửa React/nửa Vue lên production.

1. Lưu checkpoint React read-only (bao gồm dirty/untracked frontend cần thiết và build artifact phục vụ so sánh), hash manifest; không tự commit/stash hoặc bỏ thay đổi người dùng. Chỉ đóng gói whitelist, loại `.env`, credential, node_modules và dữ liệu ngoài scope.
2. Sau phê duyệt, spike trong thư mục tạm biệt lập: Vue + plugin + vue-tsc + TypeScript + icon + test/lint. Chọn published versions/pin lockfile. Nếu không tương thích TypeScript 7, dừng trước bulk port và trình lựa chọn compiler; không giấu lỗi bằng skip/force.
3. Port trong `vehicletracking-frontend`: có thể tồn tại `.tsx` cũ và `.vue` mới trong quá trình phát triển, nhưng Vue candidate không mount React. Bản React baseline được phục vụ ở cổng riêng từ artifact. Route chưa port không phải output để bàn giao; không coi intermediate build là hoàn tất.
4. Xoá đúng các React source đã thay thế sau khi inventory/parity pass; remove React deps/plugin/types/JSX config. Vue entry `main.ts` trở thành entry duy nhất. Không để bridge, legacy runtime hoặc dev-only fixture trong output cuối.
5. Nếu gate thất bại, tiếp tục sửa candidate; baseline vẫn nguyên vẹn. Rollback source chỉ khôi phục file đã sửa trong migration dựa trên manifest, phải kiểm tra thay đổi mới của người dùng trước khi overwrite; không `git reset --hard` toàn repo. Deploy thật là thao tác riêng cần được yêu cầu, rollback deploy dùng artifact/image trước nếu sau này triển khai.

## 8. Quality và chấp nhận

Các dependencies kiểm thử mới chỉ phục vụ AC có thật: Vue SFC/template type checking, lifecycle/component tests, network/visual parity. Không chọn framework testing mà không cài/cấu hình trong Plan.

- CLI đích: `npm run lint` (script + template), `npm run typecheck` (`vue-tsc --noEmit`), tests unit/motion/e2e/visual, `npm run build`.
- Toàn bộ AC1–AC7 phải có evidence; asset/runtime/import scan phải chứng minh React đã hết trong app, không chỉ đổi package.json.
- Không yêu cầu benchmark “Vue nhanh hơn React”. Ghi request counts/listener lifecycle và performance baseline cùng fixture; không thêm subscription/RAF trùng, không tăng call API ngoài cadence cũ.
- Reviewer độc lập sau implementation kiểm tra parity/contract/leak/coverage; nếu unavailable phải ghi rõ, không tự ghi Reviewed.
