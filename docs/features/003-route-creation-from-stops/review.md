# Review độc lập vòng 6: Tạo tuyến đường từ danh sách điểm dừng

- **Ngày:** 11/09/2026 — **Reviewer:** Codex.
- **Phạm vi:** Re-review bản sửa M5-01/L5-01 và regression trực tiếp của các bản sửa async UI trước; đối chiếu AGENTS.md, workflow Mục 9, feature contract và evidence/walkthrough. Không phải một vòng audit mới toàn bộ hệ thống.
- **Kết luận hiện tại: Accept with follow-up.** Hai finding vòng 5 đã được xử lý. Không tìm thấy finding Critical/High/Medium/Low mới trong phạm vi bản sửa. Cần chạy lại backend suite ở môi trường cho phép Mockito/Testcontainers và giữ các ca regression browser thành bằng chứng lâu dài.

## 1. Findings và kết quả đóng finding cũ

| Finding vòng 5 | Kết quả | Evidence thực tế |
| --- | --- | --- |
| M5-01 — Response A mở khóa form B đang lưu | **Đã sửa, đã kiểm chứng browser** | `vehicletracking-frontend/src/components/route/RouteWorkspace.tsx:198-208`: nhánh stale success không còn gọi `setSavingRoute(false)`; nhánh response hiện hành mới reset saving. Browser xác nhận Lưu/Hủy/Đóng của B vẫn disabled sau A success, không gửi POST lặp; B success/failure kết thúc trạng thái của chính nó đúng. |
| L5-01 — Media evidence không truy cập được | **Đã sửa** | Ba file dưới `docs/features/003-route-creation-from-stops/artifacts/` tồn tại, đọc được metadata; hai PNG 1920×961 và một WebP. Đã mở xem `routes_workspace_empty_1789115262930.png`, đúng màn hình danh sách 0/0 tuyến. Chưa xem toàn bộ WebP; không dùng media cũ để chứng minh fix M5-01. |

Không còn yêu cầu sửa code bắt buộc từ vòng 5. Bản sửa giữ nguyên cập nhật danh sách/toast của response cũ, nhưng không cho nó đổi selection/map hoặc saving của phiên mới.

## 2. Kiểm chứng độc lập vòng này

### Frontend — Node 24.16

Đã chạy trong `vehicletracking-frontend`:

```bash
source /home/khainq/.nvm/nvm.sh
nvm use 24
npm run lint && ./node_modules/.bin/tsc --noEmit && npm run build
```

**Exit 0**. Vite build 1864 modules, asset `index-CuUDD_VV.js`, hoàn tất 616ms. `git diff --check` cũng exit 0.

### Browser regression

Chạy `/tmp/vehicletracking-review6-browser.cjs` bằng Chromium trên production build mới, localhost tạm và HTTP intercepted. **Exit 0**, không có pageerror. Không gọi HERE, không ghi database thật, không thêm mock vào source; tile bên ngoài bị chặn. Log: `/tmp/vehicletracking-review6-browser.log`; ảnh cuối phiên: `/tmp/vehicletracking-review6-browser.png`.

| Kịch bản | Kết quả |
| --- | --- |
| Chọn route qua parent render | Pass: giữ 2 numbered markers, list không refetch. |
| POST success cũ sau chọn tuyến khác | Pass: không ghi đè drawer tuyến đang xem. |
| POST failure cũ sau chọn tuyến khác | Pass: không hiển thị lỗi cũ lên phiên mới. |
| POST success sau unmount/chuyển workspace | Pass: không phục hồi map/drawer khi quay lại. |
| POST success nhưng refresh list lỗi | Pass: route vừa tạo vẫn được chọn và có markers. |
| A success khi B pending | Pass: Lưu/Hủy/Đóng B vẫn disabled; thử click nút disabled không phát sinh POST mới. |
| B success sau A success | Pass: chọn đúng detail B. |
| A failure khi B pending | Pass: B tiếp tục saving, không nhận lỗi của A. |
| B failure | Pass: mở khóa submit, giữ tên form B và hiển thị lỗi của B. |
| B success trước, A success sau | Pass: A không thay detail B. |

Các ca trên kiểm chứng đúng vấn đề thứ tự phản hồi; không suy ra từ lint hoặc chỉ đọc code. Script/ảnh/log `/tmp` là artifact tạm của reviewer, chưa phải regression test đã được tích hợp trong repository.

### Backend — Java 26

Đã chạy `./mvnw test` trong `vehicletracking-backend`, với `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn` và PATH tương ứng.

- **Lần chạy hiện tại: exit 1**, do Mockito/Byte Buddy không self-attach được trong sandbox. Log `/tmp/vehicletracking-review6-backend.log:707,721`, kết thúc 16:11:42 +07:00. Không kết luận lỗi nghiệp vụ từ thất bại môi trường này.
- Yêu cầu chạy lại ngoài sandbox bị cơ chế phê duyệt môi trường từ chối trước khi thực thi. Reviewer không tìm cách vượt giới hạn; **không có kết quả backend pass mới trong vòng 6**.
- **Bằng chứng lịch sử:** Vòng 5 đã chạy độc lập 85/85 tests pass, không skipped, có 11 route repository integration tests; log `/tmp/vehicletracking-review5-backend-escalated.log`. Đây là kết quả vòng trước, không gắn lại thành kết quả lượt hiện tại.
- Follow-up: chạy lại exact command trên máy/CI có quyền Mockito attach và Docker, lưu output mới. Không cần đổi source/pom để làm cho sandbox pass.

## 3. Gate

| Gate | Đánh giá |
| --- | --- |
| Scope | Đạt trong phạm vi re-review: sửa nhánh stale response và bàn giao media/docs. Working tree vẫn chứa feature route untracked; `git diff` so với HEAD không thể đại diện đầy đủ cho diff giữa hai vòng Gemini. |
| Secret | Không phát hiện vấn đề mới: `HereRoutingProperties` lấy key backend và redact `toString`; `services/routes.ts` chỉ gọi API nội bộ. Scan frontend src không có `VITE_*HERE`/HERE routing endpoint. Không đọc/in `.env`; không tuyên bố audit toàn bộ Git history. |
| Database | Giữ kết quả review trước: V3 và JPA validate; `application.yaml:12-17` giữ schema/validate. Integration đã pass vòng 5, chưa tái xác nhận runtime vòng 6. |
| Contract | Không có thay đổi contract trong bản sửa: DTO/Type/API của feature tiếp tục theo bảng đối chiếu vòng 5; sửa async UI không đổi request/response. |
| Evidence | Hai finding tài liệu/code đã đóng; đường dẫn media hiện truy cập được. Giới hạn backend và phạm vi browser của reviewer được ghi rõ tại đây. |
| Quality | Frontend/build/browser đạt; backend tái chạy là follow-up do môi trường. |
| Review | Đã review độc lập vòng 6; không tự thay đổi trạng thái trong requirement/plan hay commit/push. |

## 4. Đánh giá AC-01 đến AC-15

Đối với backend không thuộc bản sửa UI lần này, đánh giá kế thừa khảo sát source/test vòng 5 và ghi rõ giới hạn tái chạy; không trình bày như đã chạy lại thành công toàn bộ suite. Đường dẫn Java nằm dưới backend `src/main/java/com/quangkhai/vehicletracking_backend/route/`; đường dẫn frontend dưới `vehicletracking-frontend/src/`.

| AC | Đánh giá | Evidence và giới hạn |
| --- | --- | --- |
| AC-01 | Đạt theo bằng chứng vòng 5 | `V3__create_routes_tables.sql`, Hibernate validate, `RouteRepositoryIntegrationTest` 11/11 vòng 5; chưa tái chạy integration thành công vòng này. |
| AC-02 | Đạt theo source đã review | `RouteDrawer.RouteCreateContent`, `normalizeStops`, `getStopRole`: active stations, 2–50, thêm/bỏ/reorder; không chạy lại đủ 50 trạm trên browser. |
| AC-03 | Đạt theo source/test vòng 5 | `RouteService.validateStops`, request validation, `RouteDrawer.normalizeStops`, CHECK V3. |
| AC-04 | Đạt theo source/test vòng 5 | `HereRoutingProvider.buildUri` và tests nhiều via/car/fast/return fields. |
| AC-05 | Đạt trong phạm vi khảo sát | `HereRoutingProperties`, backend config, frontend internal API; timeout secret test pass vòng 5, không phải vòng 6. |
| AC-06 | Đạt theo source/test vòng 5 | `RouteService.create` gọi provider trước `RoutePersistenceService.persistRoute`; provider-failure tests. |
| AC-07 | Đạt theo source/test vòng 5 | `RouteController.create`, `RouteDetailResponse.from`; controller 201/Location/detail test. |
| AC-08 | Đạt theo source/test vòng 5 | `RouteService.create:92-107` cộng totals, DTO tính offsets, multi-section tests. |
| AC-09 | Đạt theo source/test vòng 5 | `RouteService.findAll/findById`, DTO summary/detail, controller GET/404 tests. |
| AC-10 | Đạt theo source/test vòng 5 | Request/service validation null, inactive, consecutive duplicate và invalid input. |
| AC-11 | Đạt theo source/test vòng 5 | `HereRoutingProvider.calculate` disabled/key guard; read services không gọi provider. |
| AC-12 | Đạt trong luồng kiểm chứng | `MapComponent` planned-route effect/fitBounds và `RouteWorkspace` session guard; browser markers, selection, stale response/unmount pass. Không xác nhận HERE live. |
| AC-13 | **Đạt sau sửa M5-01** | `RouteWorkspace:198-208`, `RouteDrawer` disabled theo saving; browser xác nhận không mở khóa B bởi A, B success/failure xử lý đúng. |
| AC-14 | **Chưa tái xác nhận đầy đủ vòng 6** | Frontend lint/tsc/build pass; backend bị giới hạn môi trường. Đã đạt 85/85 ở vòng 5, cần output mới khi môi trường cho phép. |
| AC-15 | Đạt cho việc bàn giao review lần này | M5-01 và L5-01 được đối chiếu code/artifact thực; review ghi cả kết quả đạt và kiểm tra bị chặn, không che giới hạn. |

## 5. Follow-up và kết luận

**Accept with follow-up:** chấp nhận bản sửa hai finding vòng 5; chưa thấy lỗi chức năng mới trong các ca regression đã chạy. Không yêu cầu Gemini làm lại feature hay mở rộng schema/API.

Các việc còn lại:

1. Chạy lại backend suite Java 26 trên môi trường cho phép và lưu log để đóng phần tái xác nhận AC-14.
2. Nên lưu ca regression A/B, selection, unmount, list-refresh failure thành test/bằng chứng có thể chạy lại trong repository khi bố trí tooling phù hợp. `package.json` hiện chưa có frontend test runner; đây là follow-up độ bền kiểm thử, không phải lỗi runtime mới.

Chưa kiểm chứng HERE live end-to-end, toàn bộ mobile layout, mọi thứ tự nhiều request GET/POST hoặc mọi manual checklist của test-plan; không dùng kết luận này như chứng nhận toàn diện cho các phần đó. Reviewer chỉ cập nhật `review.md`, giữ nguyên source/migration/dependency và dữ liệu của người dùng. Tài liệu/artifact trong `docs/` hiện bị Git ignore, cần lưu ý khi bàn giao qua Git.

---

# Lịch sử: Review độc lập vòng 5 (không phải kết luận hiện tại)

- **Ngày:** 11/09/2026 — **Reviewer:** Codex.
- **Phạm vi:** Re-review bản sửa M4-01/L4-01 trên working tree; đối chiếu AGENTS.md, workflow Mục 9, requirement/spec/test-plan/plan và evidence/walkthrough. Khảo sát lại frontend lifecycle, service/type, DTO, migration/config và chạy verification. Không sửa source code.
- **Kết luận hiện tại: Request changes.** Còn 1 Medium về trạng thái lưu của request mới bị request cũ thay đổi; 1 Low về artifact evidence không truy cập được. Không phát hiện Critical/High mới.

## 1. Findings

### Medium — M5-01: Request tạo tuyến cũ mở khóa nút Lưu của request mới đang chạy

- **Vị trí:** `vehicletracking-frontend/src/components/route/RouteWorkspace.tsx:205-208`, nhánh stale success của `handleSaveRoute`; `vehicletracking-frontend/src/components/route/RouteDrawer.tsx:369-376`, nút Hủy/Lưu phụ thuộc `saving`.
- **Hành vi hiện tại:** Nhánh `else if (isMountedRef.current)` chạy khi `createRequestIdRef.current !== createId`, nhưng vẫn gọi `setSavingRoute(false)`. Token đã bảo vệ selection/drawer/map, song chưa bảo vệ trạng thái saving của phiên mới. Người dùng vẫn được chọn tuyến khác hoặc bấm “Tạo tuyến” từ panel trong lúc POST cũ chờ phản hồi.
- **Tái hiện browser độc lập:**
  1. Tạo `Concurrent A`, giữ POST A chưa trả về.
  2. Chọn một tuyến đã có, mở form mới và submit `Concurrent B`; giữ POST B chưa trả về. Nút submit lúc này disabled đúng.
  3. Trả success cho POST A. Drawer vẫn là form B nhưng nút submit của B chuyển thành enabled, dù POST B chưa kết thúc.
  4. Bấm Lưu một lần nữa: browser gửi thêm POST có payload giống hệt B. Script đã so sánh hai request bodies và xác nhận trùng nhau.
- **Tác động:** UI mất trạng thái đang lưu, có thể gửi lặp cùng một tuyến và gọi HERE thêm lần nữa. Backend cho phép nhiều snapshot cùng payload theo `spec.md` Mục 9, nên không có ràng buộc nghiệp vụ chống trùng để bù cho lỗi khóa submit. Lượt kiểm thử chỉ intercept HTTP, không tạo dữ liệu trùng trong database thật.
- **Đề xuất sửa nhỏ:** Không cho response stale thay đổi `savingRoute` của phiên mới: bỏ `setSavingRoute(false)` tại nhánh stale success hoặc gắn saving với request ID và chỉ reset khi ID khớp. Giữ nguyên cập nhật danh sách/toast nếu đó là UX đã chọn; không cần khóa toàn bộ workspace, thêm idempotency backend hoặc thay schema.
- **Regression cần có:** A pending → mở/submit B → A success; assert B vẫn saving, nút Lưu/Hủy/Đóng vẫn disabled và không phát sinh POST B thứ hai. Sau đó B success/failure phải tự mở khóa đúng. Giữ các ca stale success/failure sau selection và unmount đang pass.
- **Bằng chứng:** `/tmp/vehicletracking-review5-browser.log` có `OBSERVED pending B submit disabled after older A succeeds: false (expected true)` và `REPRODUCED duplicate POST with identical B payload while original B is pending`; script `/tmp/vehicletracking-review5-browser.cjs`. Ảnh `/tmp/vehicletracking-review5-browser.png` chụp sau khi đã gửi request B lặp, nên ảnh thể hiện trạng thái saving trở lại, không dùng riêng ảnh để chứng minh thời điểm nút bị mở khóa.

### Low — L5-01: Đường dẫn media trong evidence chưa dùng được để kiểm chứng

- **Vị trí:** `docs/features/003-route-creation-from-stops/evidence.md:227,249` (ảnh danh sách rỗng và video browser).
- **Hành vi hiện tại:** Tài liệu đã bổ sung đường dẫn tuyệt đối dưới `/home/khainq/.gemini/antigravity-ide/brain/42864023-af69-4e73-a869-aaefe95ae3d8/.user_uploaded/`, nhưng hai đích `routes_workspace_empty_1789115262930.png` và `verify_route_ui_1789115204741.webp` được kiểm tra bằng `ls -l` đều trả `No such file or directory` trong môi trường review.
- **Tác động:** Reviewer không xem lại được media được dẫn để chứng minh ca UI. Điều này không chứng minh Gemini chưa từng chạy browser; chỉ xác nhận bằng chứng hiện không truy cập được. Mục 5 cũng chưa có ca deferred POST A/B để bắt M5-01.
- **Đề xuất:** Cung cấp đúng artifact có thể truy cập hoặc lưu bản sao trong thư mục evidence được dự án cho phép. Nếu artifact đã mất, ghi rõ và dẫn log/ca mới thay thế; không ghi một đường dẫn chưa kiểm tra là bằng chứng đã bàn giao. Artifact `/tmp` của reviewer cũng chỉ là tạm, không phải test tự động đã thêm vào repo.

## 2. Trạng thái findings vòng 4

| Finding | Kết quả vòng 5 | Evidence |
| --- | --- | --- |
| M4-01 — Response cũ chiếm selection/map | **Đã sửa hành vi được nêu, còn biến thể saving tại M5-01** | `RouteWorkspace`: token create tại `:161`, guard success `:198`, guard error `:168`, mounted cleanup `:64-73`; header close tại `RouteDrawer:205` đã disabled khi saving. Browser xác nhận stale success/failure không thay detail B và POST success sau unmount không khôi phục map/drawer. |
| L4-01 — Mã lỗi/class/ownership và mức độ khẳng định evidence | **Các mô tả chính đã sửa** | `evidence.md:82` dùng `ROUTING_UNAVAILABLE`; `:121` dùng `RouteOperationException`; AC-08 dẫn `RouteService:92-107`. Đã bỏ khẳng định kiểm thử “toàn diện”. Riêng media chưa xem được: L5-01. |

Ghi chú tài liệu nhỏ: bảng sửa M4-01 nói unmount tăng create token; thực tế cleanup dùng `isMountedRef.current = false`. Cơ chế này vẫn bảo vệ unmount trong ca đã chạy; nên mô tả đúng, không coi khác biệt này là bug chức năng.

## 3. Gate và Acceptance Criteria

| Gate | Đánh giá |
| --- | --- |
| Scope | Đạt. Bản sửa mới tập trung `RouteWorkspace`/`RouteDrawer` và tài liệu; không thêm dependency frontend hay tính năng nghiệp vụ mới. `git status` vẫn chứa toàn bộ feature route untracked, nên Git diff không phải diff riêng giữa hai vòng review. |
| Secret | Đạt trong phạm vi source/config/contract đã khảo sát: key ở backend `HereRoutingProperties`/`application.yaml`; frontend `services/routes.ts` chỉ gọi API nội bộ. Không đọc/in giá trị `.env`; không phát hiện pattern HERE key ở frontend src. Không khẳng định audit toàn bộ Git history. |
| Database | Đạt. V3 tạo routes/stops/sections; Hibernate giữ validate; 11 route repository integration tests trên PostgreSQL pass, không skip. |
| Contract | Đạt. `RouteCreateRequest`, `RouteSummaryResponse`, `RouteDetailResponse` khớp `src/types/route.ts`; không đổi payload/status cho việc sửa async UI. |
| Quality | Chưa đạt đầy đủ: lệnh bắt buộc pass nhưng browser xác nhận M5-01. |
| Evidence | Còn follow-up L5-01; kết quả independent tests được ghi rõ ở dưới. |
| Review | Đã thực hiện; chưa Accept feature. |

Trong bảng dưới, tên Java nằm dưới `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/`; tên frontend nằm dưới `vehicletracking-frontend/src/`. Test tương ứng nằm dưới backend `src/test/java/.../route/`.

| AC | Kết quả | Bằng chứng |
| --- | --- | --- |
| AC-01 | Đạt | `V3__create_routes_tables.sql`, `application.yaml` validate; `RouteRepositoryIntegrationTest` 11/11 pass. |
| AC-02 | Đạt theo source | `RouteDrawer.RouteCreateContent`, `normalizeStops`, `getStopRole`: active stations, 2–50, thêm/bỏ/reorder. Vòng này không thao tác browser đủ 50 trạm. |
| AC-03 | Đạt | `RouteDrawer.normalizeStops`, `RouteService.validateStops`, `RouteCreateRequest.RouteStopInput` và CHECK V3; service/repository tests pass. |
| AC-04 | Đạt | `HereRoutingProvider.buildUri` và provider test nhiều via đúng thứ tự, car/fast và return fields; provider suite 27/27 pass. |
| AC-05 | Đạt trong phạm vi khảo sát | `HereRoutingProperties`, API DTO/type và frontend service không mang key; provider timeout log secret test pass. Giới hạn theo Secret gate. |
| AC-06 | Đạt | `RouteService.create` gọi provider trước `RoutePersistenceService.persistRoute`; tests provider failure không gọi persistence pass. |
| AC-07 | Đạt | `RouteController.create`, `RouteDetailResponse.from`; controller test trả 201/Location/detail pass. |
| AC-08 | Đạt | `RouteService.create:92-107` tính totals; `RouteDetailResponse.from` tính cumulative offsets; tests multi-section và metrics pass. |
| AC-09 | Đạt | `RouteService.findAll/findById`, DTO summary/detail; controller list/detail/404 pass. |
| AC-10 | Đạt | `RouteService.validateStops/create` và request validation; null/consecutive duplicate/inactive/invalid tests pass. |
| AC-11 | Đạt | `HereRoutingProvider.calculate` kiểm tra enabled/key, read services không gọi HERE; config/controller disabled tests pass. |
| AC-12 | Đạt cho luồng đã kiểm chứng | `MapComponent` planned route effect/fitBounds và `RouteWorkspace` session guards; browser selected route giữ markers, stale POST và unmount không ghi đè map. Geometry kiểm thử là fixture, không phải HERE live. |
| AC-13 | **Chưa đạt** | `RouteDrawer:376` phụ thuộc saving; stale success ở `RouteWorkspace:207` mở khóa submit khi POST mới còn chạy — M5-01. Loading/empty/error/no-fallback còn lại giữ nguyên. |
| AC-14 | Đạt | Java 26: 85/85 tests pass; Node 24: lint, tsc, build exit 0. |
| AC-15 | **Chưa đạt đầy đủ** | Review độc lập đã thực hiện; cần xử lý media evidence L5-01 và cập nhật kết quả thực tế sau M5-01. |

## 4. Kiểm chứng độc lập

### Backend — Java 26

Chạy `./mvnw test` trong `vehicletracking-backend` với `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn` và thêm `$JAVA_HOME/bin` vào PATH.

- Lần chạy sandbox thất bại vì Mockito/Byte Buddy không self-attach được (`/tmp/vehicletracking-review5-backend.log`); không tính đây là regression nghiệp vụ.
- Chạy lại đúng lệnh ngoài sandbox với quyền đã duyệt: **exit 0, BUILD SUCCESS; 85 tests, 0 failures, 0 errors, 0 skipped**, kết thúc 16:04:40 +07:00 ngày 11/09/2026. Log `/tmp/vehicletracking-review5-backend-escalated.log`.
- Gồm 69 route tests và 16 station tests; có đủ 11 route repository integration tests. Không thay kết quả chạy hiện tại bằng kết quả vòng trước.

### Frontend — Node 24.16

Đã chạy `source /home/khainq/.nvm/nvm.sh`, `nvm use 24`, rồi trong `vehicletracking-frontend`: `npm run lint && ./node_modules/.bin/tsc --noEmit && npm run build`.

- **Exit 0**; Vite build 1864 modules, asset `index-COZyA41A.js`, hoàn tất 398ms. `git diff --check` cũng exit 0.
- Browser script chạy trên production build, localhost tạm và HTTP responses intercepted; không gọi HERE, không ghi database thật, tile ngoài bị chặn. Dữ liệu kiểm thử chỉ trong `/tmp`, không thêm mock vào source.
- `/tmp/vehicletracking-review5-browser.cjs` chạy **exit 0**: các assertions xác nhận những ca pass dưới đây và xác nhận tái hiện M5-01; exit 0 của script tái hiện không có nghĩa feature hết lỗi.

| Ca browser | Kết quả quan sát |
| --- | --- |
| Selected route qua parent render | Pass: giữ 2 numbered markers, list không refetch. |
| POST success cũ sau chọn B | Pass: drawer vẫn B. |
| POST failure cũ sau chọn B | Pass: lỗi cũ không xuất hiện trong UI mới. |
| POST success sau unmount/chuyển workspace | Pass: không khôi phục map/drawer khi quay lại. |
| POST success, background list lỗi | Pass: giữ route detail và markers vừa tạo. |
| Header Đóng khi saving | Pass: disabled. |
| A success trong khi POST B đang chờ | **Fail: nút Lưu B enabled; gửi lại được POST có body trùng B.** |

Log browser: `/tmp/vehicletracking-review5-browser.log`; không có pageerror. Chưa có regression test tương ứng được thêm vào repository (`package.json` không có frontend test runner; không tìm thấy test/spec dưới frontend src). Không đo line/branch coverage.

**Giới hạn:** Chưa chạy thành công HERE live end-to-end, mobile layout, đủ 50 trạm, partial decode trên browser hoặc mọi thứ tự trả response của nhiều POST/GET. Không diễn giải lint/typecheck/build thành bằng chứng đúng cho các trường hợp đó. Các phần backend/contract không liên quan finding mới được kiểm tra theo source và suite hiện tại; không mở rộng review thành refactor toàn hệ thống.

## 5. Kết luận bàn giao

**Request changes** vì M5-01 vi phạm submit-disabled khi đang gửi và đã tái hiện gửi lặp HTTP. Phần bảo vệ selection/map của M4-01 đã hoạt động; không cần làm lại cơ chế đó.

Hướng sửa tập trung: response stale không được thay đổi saving của request mới; thêm case A/B pending; sửa đường dẫn evidence hoặc ghi rõ giới hạn. Sau đó chạy lại regression và verification. Reviewer chỉ sửa `review.md`, giữ nguyên code/migration/dependency/dữ liệu của người dùng. `docs/` đang bị Git ignore nên báo cáo không hiện trong `git status --short` thông thường.

---

# Lịch sử: Review độc lập vòng 4 (không phải kết luận hiện tại)

- **Feature:** `003-route-creation-from-stops`
- **Ngày review:** 11/09/2026
- **Reviewer:** Codex
- **Phạm vi:** Working tree sau lần sửa mới nhất của Gemini; đối chiếu AGENTS.md, workflow Mục 9, requirement, spec, test-plan, plan, evidence và walkthrough. Không sửa source code.
- **Kết luận hiện tại: Request changes.** Còn 1 finding Medium về phản hồi tạo tuyến đến muộn và 1 finding Low về độ chính xác của evidence. Không phát hiện Critical/High mới.

## 1. Findings hiện tại

### Medium — M4-01: Phản hồi tạo tuyến đến muộn ghi đè tuyến người dùng vừa chọn

- **Vị trí:** `vehicletracking-frontend/src/components/route/RouteWorkspace.tsx:146-165`, `handleSaveRoute`; cùng file `:135-143`, `handleCloseRouteDrawer`; `vehicletracking-frontend/src/components/route/RouteDrawer.tsx:201-207`.
- **Hành vi hiện tại:** Sau `await createRoute(input)`, code luôn cập nhật selected route, drawer và callback hiển thị map. Không có generation/request token hoặc kiểm tra workspace còn mounted cho POST. Nút đóng ở đầu form vẫn dùng được khi saving, trong khi nút Hủy ở cuối form bị khóa. Close/select chỉ vô hiệu hóa request GET detail, không vô hiệu hóa việc cập nhật UI từ POST đang chờ.
- **Tái hiện độc lập trên browser:** Mở form tạo `Review Pending` → gửi POST nhưng giữ response chưa trả → bấm nút Đóng đầu form → chọn `Review Route B` và chờ drawer B hiện → trả POST thành công cho `Review Pending`. Drawer và route trên map chuyển trở lại `Review Pending`, ghi đè lựa chọn B. Đây là lỗi quan sát được với API kiểm thử bị trì hoãn, không chỉ suy luận từ code.
- **Tác động:** Kết quả cũ chiếm lại quyền điều khiển drawer/map sau thao tác mới của người dùng. Cùng đường code còn có nguy cơ gọi callback parent sau khi chuyển workspace và child unmount; tình huống unmount trong lúc POST chưa được chạy riêng trên browser ở vòng này.
- **Đề xuất sửa:** Theo dõi phiên tương tác/request tạo tuyến; chỉ tự mở detail và gọi callback map nếu response còn thuộc phiên hiện hành và workspace còn mounted. Khi đóng/chọn tuyến khác/chuyển workspace, vô hiệu hóa quyền cập nhật selection của request cũ. Có thể cập nhật danh sách tuyến sau thành công mà không ghi đè lựa chọn mới. Nếu chọn khóa thao tác trong lúc lưu, phải khóa nhất quán cả nút Đóng và các đường điều hướng; vẫn cần bảo vệ callback sau unmount. Hủy HTTP phía client không đồng nghĩa hủy việc ghi ở server.
- **Test cần thêm:** Deferred POST success và failure sau close/select/unmount; xác nhận detail/map/error của phiên mới không bị response cũ ghi đè. Giữ regression test POST thành công nhưng refresh list thất bại.
- **Bằng chứng phiên review:** Script `/tmp/vehicletracking-review4-browser.cjs`; ảnh `/tmp/vehicletracking-review4-browser.png` cho thấy drawer `Review Pending` sau chuỗi thao tác trên. Đây là artifact tạm, không phải file source hay test đã được thêm vào repository.

### Low — L4-01: Evidence vẫn sai một số contract/symbol và mô tả quá mức phạm vi kiểm thử UI

- **Vị trí:** `docs/features/003-route-creation-from-stops/evidence.md:82,121,265,272,280`.
- **Hành vi hiện tại:** Dòng 82 ghi lỗi 401 dùng `ROUTING_PROVIDER_UNAVAILABLE`, nhưng `HereRoutingProvider.java:65-69` trả `ROUTING_UNAVAILABLE`. Dòng 121 ghi `RouteNotFoundException`, nhưng `RouteService.java:159-165` dùng `RouteOperationException`. Dòng 265 quy việc tính tổng trip duration cho entity/DTO, trong khi phép cộng ở `RouteService.java:92-107`. Dòng 272/280 khẳng định toàn bộ symbol chính xác và UI được kiểm chứng toàn diện, vượt quá evidence thực tế.
- **Tác động:** Người bàn giao có thể hiểu sai error contract và nơi cần sửa nghiệp vụ. Các ca browser của Gemini trong Mục 5 chủ yếu là danh sách rỗng, create bị routing disabled và chuyển workspace; chưa chứng minh success path hoặc async race M4-01.
- **Đề xuất sửa:** Sửa ba mô tả theo source nêu trên; thay khẳng định “toàn diện/100%” bằng danh sách case thực sự chạy và giới hạn. Ghi đường dẫn truy cập được tới ảnh/video thay vì chỉ basename. Không cần sửa implementation cho finding này.

## 2. Đối chiếu findings vòng 3

| Finding cũ | Kết quả vòng 4 | Evidence hiện tại |
| --- | --- | --- |
| H3-01 — Callback không ổn định | **Đã sửa** | `MapComponent.tsx:689` truyền `setPlannedRoute`; `RouteWorkspace.tsx:32-67` dùng callback ref, fetch effect và cleanup unmount tách riêng. Browser kiểm thử chọn route giữ được numbered markers qua parent render, không phát sinh list refetch. |
| M3-01 — Null route/section/notice | **Đã sửa** | `HereRoutingProvider.java:168-194,288-300` kiểm tra null; `HereRoutingProviderTest.calculate_whenNullRouteElement_throwsRoutingProviderException`, `calculate_whenNullSectionElement_throwsRoutingProviderException`, `calculate_whenNullNoticeElement_throwsRoutingProviderException` đều pass. |
| M3-02 — Evidence sai/chưa có runtime | **Đã cải thiện, còn Low** | Có bổ sung browser evidence và sửa nhiều enum/class; phần còn sai thu hẹp tại L4-01. Không giữ nguyên mức Medium của vòng trước. |
| L3-01 — State trong render | **Đã loại bỏ phần code được nêu** | `MapComponent` không còn `prevWorkspace`; việc clear giao cho lifecycle `RouteWorkspace`. Lưu ý state update có điều kiện trong render không tự nó chứng minh bug; vòng này không dùng nó làm blocker. |

## 3. Gate bắt buộc

| Gate | Kết quả | Bằng chứng/giới hạn |
| --- | --- | --- |
| Scope | Đạt | Diff tập trung route, tích hợp workspace/map, station lookup, V3/config và tests. Thay đổi `pom.xml` thêm Surefire `-XX:+EnableDynamicAgentLoading` phục vụ chạy test Java 26. Không thấy thêm tính năng ngoài phạm vi route creation. |
| Secret | Đạt trong phạm vi khảo sát | Routing key lấy ở backend qua cấu hình; frontend `services/routes.ts` gọi API nội bộ; DTO không chứa key. Timeout/parse error log dùng thông điệp tĩnh tại `HereRoutingProvider.calculate`; test không lộ key qua timeout pass. Không đọc/in secret `.env`. Không coi đây là audit toàn bộ lịch sử Git hoặc mọi loại log bên ngoài ứng dụng. |
| Database | Đạt | `V3__create_routes_tables.sql`, cấu hình Flyway và Hibernate `validate`; đã chạy độc lập 11 route repository integration tests trên PostgreSQL qua Testcontainers, không skip. V1/V2 không có diff thay đổi trong working tree. |
| Contract | Đạt | Đối chiếu `route/dto/RouteCreateRequest`, `RouteSummaryResponse`, `RouteDetailResponse` với `src/types/route.ts` và `src/services/routes.ts`; giữ payload, thứ tự stops, đơn vị giây/mét, sections và mã lỗi theo backend. L4-01 là sai tài liệu, không phải DTO/Type mismatch. |
| Evidence | Chưa đạt đầy đủ | L4-01 còn mô tả sai và quá mức. Số lượng 85 backend tests đã được xác nhận độc lập. |
| Quality | Chưa đạt đầy đủ | Các lệnh bắt buộc đều pass nhưng browser tìm được M4-01; lint/typecheck không kiểm tra được thứ tự phản hồi mạng. |
| Review | Đã thực hiện | Có review độc lập vòng 4; chưa chấp nhận feature. |

## 4. Bảng Acceptance Criteria (AC-01 đến AC-15)

Đường dẫn viết gọn trong bảng: backend route = `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/`; frontend = `vehicletracking-frontend/src/`. Tên test được đối chiếu dưới `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/route/`.

| AC | Kết quả | Evidence mã nguồn và kiểm chứng |
| --- | --- | --- |
| AC-01 | Đạt | `src/main/resources/db/migration/V3__create_routes_tables.sql` tạo ba bảng; `application.yaml` giữ Hibernate validate. `RouteRepositoryIntegrationTest`: 11/11 pass, kiểm tra schema/constraints/FK. |
| AC-02 | Đạt | `components/route/RouteDrawer.tsx`: `RouteCreateContent`, `normalizeStops`, `getStopRole`; lọc active, giới hạn 2–50, thêm/bỏ/reorder. Đánh giá đầy đủ giới hạn theo code, browser vòng này không thao tác 50 trạm. |
| AC-03 | Đạt | `RouteDrawer.normalizeStops`, `service/RouteService.validateStops`, request validation và CHECK của V3 giữ START/END bằng 0, dwell trung gian 0–3600. Service/repository tests pass. |
| AC-04 | Đạt | `provider/HereRoutingProvider.buildUri`; `calculate_withTwoViaWaypoints_sendsMultipleViaInOrderAndMapsDestinations` kiểm tra query và thứ tự via; provider suite pass. |
| AC-05 | Đạt | Cấu hình key backend, DTO và frontend API nội bộ; `calculate_whenTimeout_throwsGatewayTimeout_andDoesNotLogApiKey` pass. Giới hạn audit như Secret gate. |
| AC-06 | Đạt | `RouteService.create:89-90,147` gọi provider trước `RoutePersistenceService.persistRoute` có transaction; tests provider failure không gọi persistence pass. |
| AC-07 | Đạt | `controller/RouteController.create`, `dto/RouteDetailResponse.from`; `create_validPayload_returns201AndLocationAndBody` pass cho 201/Location/detail. |
| AC-08 | Đạt | `RouteService.create:92-107` cộng section travel/distance và dwell; `RouteDetailResponse.from` xây cumulative offsets; provider xử lý boundary/multi-section. Provider và service tests pass. |
| AC-09 | Đạt | `RouteService.findAll/findById`, `RouteSummaryResponse.from`, `RouteDetailResponse.from`; controller GET list/detail/404 tests pass. |
| AC-10 | Đạt | `RouteService.validateStops/create` kiểm tra null, consecutive duplicate, missing/inactive; controller/service tests pass. Ba malformed provider null tests mới cũng pass. |
| AC-11 | Đạt | `HereRoutingProvider.calculate` kiểm tra enabled/key và trả 503 `ROUTING_UNAVAILABLE`; `RouteService.findAll/findById` không gọi provider. Config/controller disabled tests pass. |
| AC-12 | **Chưa đạt đầy đủ** | `MapComponent.tsx:443-513` decode sections, numbered markers và fitBounds; callback regression cũ đã sửa. Nhưng response POST cũ vẫn thay route người dùng vừa chọn — M4-01, đã tái hiện browser. Không phải lỗi decode/fitBounds mới. |
| AC-13 | Đạt các trạng thái được liệt kê | `RoutePanel` có loading/empty/error; `RouteDrawer` có saving/submit disabled; map từ chối partial decode, không nối chim bay. Luồng async selection còn thiếu bảo vệ tại M4-01, không bị che bởi việc nút submit đã disabled. |
| AC-14 | Đạt | Java 26: `./mvnw test` 85/85 pass. Node 24: lint, `tsc --noEmit`, build đều exit 0. |
| AC-15 | **Chưa đạt đầy đủ** | Review độc lập đã thực hiện, nhưng `evidence.md` còn L4-01. Không đánh dấu Reviewed/Accepted trước khi sửa và xác nhận M4-01. |

## 5. Kiểm chứng độc lập và độ phủ

### Backend

```bash
export JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn
export PATH="$JAVA_HOME/bin:$PATH"
cd vehicletracking-backend
./mvnw test
```

- **Kết quả:** exit 0, BUILD SUCCESS; **85 tests, 0 failures, 0 errors, 0 skipped**. Log: `/tmp/vehicletracking-review4-backend-escalated.log`, hoàn thành lúc 15:36:48 +07:00 ngày 11/09/2026.
- Phân bố: HERE provider 27; route configuration 3; route controller 13; error regression 1; route repository 11; route service 14; station controller 6; station repository 1; station service 9.
- Lần đầu trong sandbox gặp giới hạn Mockito attach/Docker. Chạy lại đúng lệnh ngoài sandbox với quyền đã được duyệt thì toàn bộ suite pass. Không còn giới hạn “chưa chạy integration” của review vòng 3.

### Frontend

```bash
source /home/khainq/.nvm/nvm.sh
nvm use 24
cd vehicletracking-frontend
npm run lint
./node_modules/.bin/tsc --noEmit
npm run build
```

- Cả ba lệnh **exit 0** trên Node 24.16; production build thành công. `git diff --check` cũng exit 0.
- Browser Chromium chạy trên production build với HTTP API intercepted, không thêm mock vào source, không tạo route trong database thật, không gọi HERE. Tile requests bên ngoài bị chặn nên ảnh nền trống; đây không phải finding về map tile.
- Các assert trước bước chụp ảnh đã kiểm tra: chọn tuyến giữ hai numbered markers sau parent render và không fetch list lại; chuyển workspace rồi quay lại đã clear markers; POST success vẫn giữ route/markers khi refresh list lỗi. Sau đó script đóng form đang POST, chọn B, trả response cũ và chụp được drawer `Review Pending`, xác nhận M4-01.
- Đã xem ảnh `/tmp/vehicletracking-review4-browser.png` và đối chiếu script `/tmp/vehicletracking-review4-browser.cjs`. Lần xin chạy lặp để lưu thêm console log bị cơ chế duyệt môi trường từ chối; không dùng lần chưa chạy đó làm bằng chứng pass. Các artifact `/tmp` chỉ có giá trị tạm trong môi trường review.

### Khoảng trống kiểm thử

- Chưa có component regression tests trong repository cho callback identity, deferred POST, chọn tuyến nhanh hoặc unmount khi đang tạo tuyến. Cần ưu tiên M4-01, không suy ra UI đúng chỉ vì backend/lint/build pass.
- Browser của reviewer dùng response kiểm thử, chưa xác nhận thành công end-to-end với HERE thật. Không khẳng định geometry trong ảnh là kết quả HERE live.
- Chưa chạy riêng browser case partial polyline lỗi, đủ 50 trạm, reorder/dwell nhiều trạm hoặc mạng lỗi sau unmount. Các phần được đánh giá đạt theo source/backend tests đã được ghi rõ trong bảng AC.
- Số lượng test không phải tỷ lệ line/branch coverage; vòng này không chạy công cụ đo coverage.

## 6. Kết luận và phạm vi bàn giao

**Request changes** vì M4-01 là lỗi runtime có thể tái hiện: response tạo tuyến cũ ghi đè selection mới. Các lỗi chính của vòng 3 đã được sửa; backend/database và các lệnh kiểm tra bắt buộc hiện đều pass.

Gemini chỉ cần sửa quyền cập nhật UI của async create, bổ sung regression test tương ứng và chỉnh L4-01; không cần làm lại feature, đổi schema hay mở rộng sang simulator/traffic realtime. Sau sửa, review lại deferred POST success/failure, close/select/unmount và chạy các lệnh kiểm tra phù hợp.

Reviewer chỉ cập nhật `review.md`; không sửa source code, migration, dependency hay dữ liệu ứng dụng. Working tree vẫn giữ nguyên các thay đổi của người dùng/Gemini. `docs/` đang bị Git ignore nên báo cáo có thể không xuất hiện trong `git status --short`.

---

# Lịch sử: Review độc lập vòng 3 (không phải kết luận hiện tại)

- **Feature:** `003-route-creation-from-stops`
- **Ngày review:** 11/09/2026
- **Reviewer:** Codex
- **Phạm vi:** Đối chiếu bản sửa sau review vòng 2 với tài liệu feature và source code thực tế trên working tree.
- **Kết luận:** **Request changes**

## 1. Kết quả tổng quan

Gemini đã sửa đúng phần lớn findings vòng 2:

- Provider từ chối route thiếu section cuối tới END.
- Malformed JSON, departure time thiếu/sai và metric âm được chuyển thành lỗi 502.
- Route vừa tạo được đưa vào state trước khi refresh list.
- Partial polyline không còn được vẽ.
- DTO/TypeScript, secret handling, database migration và validation cũ vẫn nhất quán.

Feature chưa thể Accept vì cách cleanup mới trong `RouteWorkspace` phụ thuộc vào một callback không ổn định từ parent. Khi parent render lại, effect có thể tự abort request, xóa route vừa chọn khỏi bản đồ và tải lại danh sách. Ngoài ra, provider vẫn còn một nhóm malformed structure có thể gây NPE/HTTP 500 và evidence tiếp tục dẫn một số tên lỗi/class không tồn tại.

## 2. Trạng thái findings vòng 2

| Finding vòng 2 | Trạng thái | Bằng chứng |
| --- | --- | --- |
| H2-01 — Thiếu section tới END | **Đã sửa** | `HereRoutingProvider.java:222-266` từ chối final section kết thúc tại via và kiểm tra coverage destination stop 2..N; test `calculate_whenMissingEndSection_throwsRoutingProviderException`. |
| M2-01 — HERE malformed response | **Đã sửa phần chính** | `HereRoutingProvider.java:122-129,276-359` xử lý parse error, timestamp và metric âm. Malformed structure chứa phần tử null vẫn còn tại M3-01. |
| M2-02 — Create thành công nhưng refresh lỗi | **Đã sửa** | `RouteWorkspace.tsx:133-178` tách lỗi POST khỏi background refresh và cập nhật route cục bộ trước. |
| M2-03 — Route cũ còn hiển thị | **Phát sinh regression mới** | Clear cũ đã được thêm tại `RouteWorkspace.tsx:81-87`, nhưng cleanup dependency không ổn định gây H3-01. |
| M2-04 — Vẽ partial polyline | **Đã sửa** | `MapComponent.tsx:451-475` từ chối toàn bộ route khi một section decode lỗi. |
| M2-05 — Evidence sai symbol | **Sửa chưa đủ** | Tên method chính đã được cập nhật, nhưng tên exception/error code và mô tả ownership vẫn sai; xem M3-02. |
| L2-01 — Timer/prop thừa | **Đã sửa** | Prop `onDecodeError` đã bỏ; timer có cleanup tại `MapComponent.tsx:447-475,518-520`. |

## 3. Findings

### Critical

Không phát hiện finding Critical.

### High

#### H3-01 — Callback không ổn định làm cleanup chạy lại và xóa route đang hiển thị

- **Vị trí:** `vehicletracking-frontend/src/components/route/RouteWorkspace.tsx:32-54`; `vehicletracking-frontend/src/components/MapComponent.tsx:693-699`.
- **Hành vi hiện tại:** Effect tải danh sách phụ thuộc `onPlannedRouteDisplay` và cleanup luôn gọi `onPlannedRouteDisplay(null)`. Parent truyền callback inline `(detail) => setPlannedRoute(detail)`, nên callback có identity mới sau mỗi lần `MapComponent` render.
- **Kịch bản lỗi:** Khi detail route được trả về, `onPlannedRouteDisplay(detail)` cập nhật state của parent. Parent render lại và tạo callback mới; React chạy cleanup của effect cũ, abort request và gọi callback cũ với `null`. Kết quả route vừa chọn có thể lập tức bị xóa khỏi bản đồ. Mỗi lần parent render vì toast hoặc state khác cũng có thể khởi động lại request danh sách.
- **Tác động:** Drawer có detail nhưng map trống, request bị hủy/lặp ngoài ý muốn và AC-12/AC-13 không ổn định trong runtime. Logic được thêm để sửa M2-03 tạo ra regression mới.
- **Đề xuất sửa:** Truyền callback ổn định từ parent, đơn giản nhất là `onPlannedRouteDisplay={setPlannedRoute}`, hoặc dùng `useCallback`. Tách effect fetch list và effect cleanup nếu cần; cleanup unmount không được chạy chỉ vì callback đổi identity. Thêm component test chứng minh chọn route làm parent render lại nhưng geometry/detail vẫn giữ nguyên và list không refetch.

### Medium

#### M3-01 — Malformed structure có phần tử null vẫn gây NPE thay vì lỗi 502

- **Vị trí:** `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/provider/HereRoutingProvider.java:168-174,197-223,271-273`.
- **Hành vi hiện tại:** Code dereference `firstRoute.sections()`, `sec.notices()` và `n.severity()` mà không kiểm tra `firstRoute`, phần tử section hoặc phần tử notice có null hay không. JSON hợp lệ cú pháp như `{"routes":[null]}`, `{"routes":[{"sections":[null]}]}` hoặc notices chứa null sẽ qua deserialization rồi gây NPE.
- **Tác động:** HERE malformed response có thể thành HTTP 500 thay vì `ROUTING_PROVIDER_INVALID_RESPONSE`/502, trái luồng lỗi tại `requirement.md:96-103` và `spec.md:350-361`.
- **Đề xuất sửa:** Validate route/section/notice element trước khi dereference hoặc normalize null-safe và ném typed 502. Bổ sung provider tests cho null route, null section và null notice item.

#### M3-02 — Evidence vẫn dùng exception/error code không tồn tại và manual evidence chưa chứng minh đã chạy UI

- **Vị trí:** `docs/features/003-route-creation-from-stops/evidence.md:63-87,221-240,252,255-261`.
- **Hành vi hiện tại:** Evidence dùng các tên không tồn tại trong `RouteErrorCode.java` như `ROUTING_DISABLED`, `ROUTING_NO_ROUTE_FOUND`, `ROUTING_GATEWAY_TIMEOUT`; mô tả `RouteValidationException` và `RoutingUnavailableException` trong khi implementation dùng `RouteOperationException`. AC-11 ghi `RouteService` ném lỗi disabled nhưng lỗi thật phát sinh tại `HereRoutingProvider.calculate`. Mục manual mô tả “logic kiểm soát” và kết quả dự kiến, không có bằng chứng browser/screenshot hoặc log thao tác thực tế.
- **Tác động:** Evidence gate và AC-15 chưa đạt; người review không thể đối chiếu chính xác error contract hoặc xác nhận UI runtime, đặc biệt khi H3-01 không thể được phát hiện bởi lint/build.
- **Đề xuất sửa:** Dùng đúng enum `ROUTING_UNAVAILABLE`, `ROUTE_NOT_FOUND_BY_PROVIDER`, `ROUTING_PROVIDER_TIMEOUT`, `ROUTING_PROVIDER_UNAVAILABLE`, `ROUTING_PROVIDER_INVALID_RESPONSE` và class `RouteOperationException`. Ghi rõ manual case là “chưa chạy” nếu chỉ đọc code; nếu đã chạy thì bổ sung output/screenshot và kết quả quan sát thật.

### Low

#### L3-01 — Reset workspace đang cập nhật state ngay trong render

- **Vị trí:** `vehicletracking-frontend/src/components/MapComponent.tsx:137-144`.
- **Hành vi hiện tại:** Component lưu `prevWorkspace` rồi gọi `setPrevWorkspace`/`setPlannedRoute` trực tiếp trong render khi prop thay đổi.
- **Tác động:** React phải render lại ngay và luồng ownership trở nên khó theo dõi khi đã có cleanup ở `RouteWorkspace`. Đây chưa phải lỗi chức năng độc lập, nhưng làm lifecycle route phức tạp và góp phần khiến regression H3-01 khó nhận ra.
- **Đề xuất sửa:** Dùng một cơ chế rõ ràng: callback chuyển workspace ở parent hoặc effect phụ thuộc `workspace`; tránh duy trì hai cơ chế cleanup cạnh tranh. Nếu giữ cleanup trong child thì callback phải ổn định.

## 4. Đánh giá gate bắt buộc

| Gate | Kết quả | Bằng chứng/ghi chú |
| --- | --- | --- |
| Scope gate | **Đạt** | Thay đổi tập trung vào feature route, migration và tích hợp map; không thấy refactor ngoài phạm vi đáng kể. |
| Evidence gate | **Chưa đạt** | Evidence còn sai error code/class và chưa có manual runtime evidence, xem M3-02. |
| Secret gate | **Đạt** | Không có `VITE_*HERE`; key chỉ lấy từ `${HERE_API_KEY:}`; timeout/parse log không chứa URI hoặc raw payload. |
| Database gate | **Đạt theo source và evidence, chưa tái chạy độc lập** | V3 và `ddl-auto=validate` đúng thiết kế. Docker không khả dụng trong lượt review nên integration tests chưa chạy lại. |
| Contract gate | **Đạt** | Backend DTO và `types/route.ts` tiếp tục khớp; API frontend chỉ gọi backend. |
| Quality gate | **Chưa đạt đầy đủ** | Frontend lint/typecheck/build pass. Backend exact `./mvnw test` không pass trong môi trường review; 70 non-integration tests pass khi cấu hình Mockito javaagent. Không có frontend component tests để bắt H3-01. |
| Review gate | **Đạt** | Codex đã khảo sát và review độc lập; chưa chuyển feature sang Reviewed vì kết luận Request changes. |

## 5. Đánh giá Acceptance Criteria

| AC | Kết quả | Bằng chứng và nhận xét |
| --- | --- | --- |
| AC-01 | **Đạt** | V3 tạo đủ ba bảng/constraint/FK/index và application giữ Hibernate `ddl-auto=validate`; integration pass được ghi trong evidence nhưng chưa tái chạy do Docker. |
| AC-02 | **Đạt** | UI hỗ trợ 2–50 active stations, add/remove/reorder và suy ra START/STOP/END. |
| AC-03 | **Đạt** | START/END luôn 0; STOP được kiểm tra 0–3600 ở UI, API, service và DB bounds. |
| AC-04 | **Đạt** | Provider gửi origin/via/destination đúng thứ tự với `car`, `fast`, `polyline,summary,travelSummary`; provider tests pass. |
| AC-05 | **Đạt** | Không phát hiện HERE key trong frontend, response contract, log hoặc tài liệu. |
| AC-06 | **Đạt** | Provider chạy trước transactional persistence; lỗi provider không gọi persistence. |
| AC-07 | **Đạt** | POST trả 201/Location/detail với totals, stops, offsets và sections. |
| AC-08 | **Đạt** | Mapping yêu cầu section tới mọi destination stop và test multi-section/missing END đã có. |
| AC-09 | **Đạt** | List trả summary không có polyline; detail và 404 có contract/test. |
| AC-10 | **Đạt** | Request/station invalid được trả lỗi kiểm soát; null request item đã được xử lý. |
| AC-11 | **Đạt** | Disabled routing chỉ chặn create; API read/station không gọi provider. |
| AC-12 | **Chưa đạt** | H3-01 có thể xóa planned route ngay sau khi detail được chọn; chưa có browser/component evidence phủ runtime này. |
| AC-13 | **Chưa đạt** | UI có các state cần thiết trong source, nhưng lifecycle callback hiện làm luồng chọn/hiển thị route không ổn định. |
| AC-14 | **Chưa đạt trong review độc lập** | Frontend ba gate exit 0; exact backend command exit 1 do Mockito self-attach và Docker. Targeted non-integration suite 70/70 pass với javaagent. |
| AC-15 | **Chưa đạt** | Evidence còn sai contract tên lỗi và review vẫn có finding High. |

## 6. Kiểm chứng độc lập

### Backend — Java 26

Đã chạy lệnh bắt buộc:

```bash
export JAVA_HOME=~/.sdkman/candidates/java/26.0.1-amzn
export PATH=$JAVA_HOME/bin:$PATH
cd vehicletracking-backend
./mvnw test
```

Kết quả: **exit 1**; `Tests run: 72, Failures: 0, Errors: 45`. Nguyên nhân môi trường là Mockito không self-attach được trên Java 26 và Testcontainers không tìm thấy Docker. Không có assertion failure nghiệp vụ được ghi nhận trong lần chạy này.

Để kiểm chứng các suite không phụ thuộc Docker, reviewer chạy Mockito dưới dạng javaagent:

```bash
./mvnw \
  -DargLine=-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar \
  -Dtest=HereRoutingProviderTest,RouteConfigurationTest,RouteServiceTest,RouteControllerTest,RouteExceptionHandlerRegressionTest,StationServiceTest,StationControllerTest \
  test
```

Kết quả: **exit 0 — 70 tests, 0 failures, 0 errors, 0 skipped**. Trong đó toàn bộ 55 non-integration route tests đã pass.

### Frontend — Node 24

Đã chạy bằng Node `v24.16.0`:

```bash
npm run lint
./node_modules/.bin/tsc --noEmit
npm run build
```

Kết quả: **cả ba lệnh exit 0**; Vite build 1.864 modules thành công.

### Kiểm tra tĩnh

- `git status --short`: source feature vẫn là các thay đổi chưa commit; không reset hoặc ghi đè thay đổi của người dùng.
- `git diff --check`: exit 0.
- Secret scan: chỉ thấy placeholder vô hại trong `.env.example`; không thấy secret frontend hoặc `VITE_*HERE`.

## 7. Độ phủ kiểm thử và khoảng trống

Backend có độ phủ tốt cho controller validation, service calculation/persistence boundary, HERE query/status/malformed mapping, config validation và database constraints. Các test mới cho missing END, JSON lỗi, timestamp và metric âm đều có assertion đúng typed error.

Khoảng trống còn lại:

1. Provider structure có null route/section/notice item.
2. Frontend chưa có component test cho effect cleanup/callback identity.
3. POST success + refresh failure mới chỉ được mô tả manual, chưa có test tự động.
4. Partial polyline, A → B race và routes → stations → routes chưa có bằng chứng chạy browser thật.
5. Full suite cần chạy lại ở môi trường có Docker và Mockito agent được cấu hình ổn định trong Maven.

## 8. Kết luận

**Request changes.**

Không nên đánh dấu feature là Reviewed hoặc merge ở trạng thái này. Cần sửa H3-01 trước vì nó ảnh hưởng trực tiếp luồng chọn và hiển thị route chính. Sau đó xử lý M3-01, cập nhật evidence theo M3-02 và chạy lại review. L3-01 có thể giải quyết cùng lúc bằng cách đơn giản hóa ownership cleanup route.
