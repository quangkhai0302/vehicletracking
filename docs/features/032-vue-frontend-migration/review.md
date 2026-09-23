# Review — 032, cutover Vue

Ngày23/09/2026. **Tự review của main agent**, không phải review độc lập. Người dùng yêu cầu không dùng subagent; không gọi reviewer. Scope review này là cutover entry/toolchain/React cleanup/config, không tuyên bố review mới toàn bộ nghiệp vụ/backend.

## Findings và follow-up

### Medium — Browser/deployment regression chưa chạy lại sau cutover

- Evidence: `vehicletracking-frontend/tests/e2e/migration-smoke.mjs` và `tests/visual/capture.mjs`; lịch sử lượtF–H và giới hạn13:00 trong `evidence.md`.
- Unit/jsdom và build đã đạt nhưng chưa xác minh native focus, browser20vòng điều hướng map, toàn bộ interaction matrix hoặc container/API/SSE smoke sau cutover. CSS hash giống không tự chứng minh pixel parity.
- Người dùng đã chấp nhận giao diện và duyệt chuyển mặc định dù được báo phần browser còn thiếu. Đây là cho phép cutover source, không phải chứng nhận các test chưa chạy.
- Follow-up: khi môi trường cho phép, chạy browser smoke trên dev và build, visual readiness loop, kiểm tra container/deep-link/API/SSE trước deploy. Không nới tolerance hoặc mask map để qua gate.

Không phát hiện blocker mới trong phạm vi entry/toolchain sau khi kiểm tra dưới đây. Không suy ra không có lỗi ở các luồng chưa kiểm chứng.

## Phần đã rà và kết quả

- `index.html`, `src/main.ts`, `vite.config.js`, `package.json`: entry Vue-only, đúng root/CSS import order; config chính đọc môi trường thông thường, cổng5173 strict; aliases chuyển tiếp CLI flags với `--`. Test-only config vẫn tách khỏi dev chính để không lặp lỗi không đọc `.env`/CORS5176.
- `package-lock.json`, `tsconfig.json`, `.oxlintrc.json`, `tests/unit/cutover.test.mjs`, `tests/visual/audit-vue.mjs`: không dependency/plugin/types/JSX React; strict/template checking còn hoạt động; graph production không import React/test entry/fixture.
- Trước xóa đối chiếu66file với archive và source Vue tương ứng. Sau xóa còn58file baseline dùng chung không đổi,16CSS giữ checksum. Không xóa file TSX ngoài inventory hoặc sửa CSS/backend/API/schema.
- CI chỉ thay frontend typecheck/add unit+motion. Docker/Caddy giữ output `dist` và proxy/fallback. Không đổi trigger/backend/deploy job, không tự triển khai.
- `npm ci --offline`, lint/typecheck,85unit tests, motion, production build, graph/hash audit đều pass; xem lệnh và kết quả thực tế trong `evidence.md`. Remote CI chưa chạy.

## Đối chiếu acceptance criteria

| AC | Trạng thái có bằng chứng |
|---|---|
| AC1 Vue-only source/dependencies/runtime | Đạt static/import/lock/production build graph;66React files gỡ, không bridge |
| AC2 layout/màu | Người dùng chấp nhận giao diện;16CSS không đổi. Automated visual map parity vẫn thiếu, không ghi pass |
| AC3 nghiệp vụ/UI | Unit/component/page workflows pass; full browser matrix chưa đủ |
| AC4 map/realtime | Unit map/layer/cleanup và20mount/unmount pass; native browser lifecycle/full simulator chưa đủ |
| AC5 routing/auth/API | Router/session/HTTP/CSRF tests pass, shared service contract giữ nguyên; không kiểm thử backend thật trong cutover |
| AC6 toolchain/deploy contract | Clean install/lint/typecheck/tests/build pass; container/API/SSE smoke và remote CI chưa chạy |
| AC7 preservation/evidence/review | Archive/hash/inventory và docs cập nhật; chỉ tự review, chưa có review độc lập |

Kết luận: **Accept with follow-up cho cutover source theo phê duyệt người dùng**. Chưa nghiệm thu regression toàn feature, không ghi Verified/Reviewed và không deploy. Hướng dẫn chạy/khôi phục có trong `walkthrough.md`.
