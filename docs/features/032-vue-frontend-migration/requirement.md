# 032 — Chuyển frontend React sang Vue, giữ nguyên giao diện

Trạng thái: **Implementing**. Người dùng đã yêu cầu tiếp tục sau khi được báo phương án TypeScript 6.0.3; compiler này chỉ dùng cho frontend. Yêu cầu gốc: chuyển **toàn bộ** frontend hiện tại từ React sang Vue, không thay layout, giữ nguyên màu sắc.

Cập nhật 23/09: người dùng đã chấp nhận giao diện Vue và duyệt chuyển hẳn entry mặc định/gỡ React. Cutover triển khai theo xác nhận đó; chấp nhận trực tiếp của người dùng không thay thế evidence tự động cho browser/visual matrix còn thiếu. Xem kết quả mới nhất ở `evidence.md`.

## Bối cảnh và mục tiêu

Đổi framework triển khai, không thiết kế lại sản phẩm. Bản React trong working tree tại lúc bắt đầu implementation là chuẩn đối chiếu giao diện và hành vi; không lấy HEAD cũ hoặc ảnh thiết kế cũ làm chuẩn. Phần maps cũng phải được chuyển khỏi React nhưng giữ nguyên cách hiển thị, bố cục và các tối ưu hiện có.

## Phạm vi

In scope:

- Toàn bộ app entry, router, auth/role guards, shell, các trang quản trị, login/register, cổng tài xế, components, hooks/state, map/layers, modal/drawer, realtime và simulator phía frontend.
- Điều chỉnh toolchain/build/lint/type-check/test cần thiết cho Vue; giữ TypeScript, Vite và các contract triển khai phù hợp.
- Giữ lại services/types/utilities, CSS/assets độc lập framework nếu tương thích; kiểm chứng parity giao diện và hành vi trước khi bỏ React.

Out of scope:

- Redesign, đổi màu/font/spacing/breakpoint, dùng UI kit để thay hình thức hiện tại, thêm tính năng nghiệp vụ.
- Thay API/backend/auth model/database/migration, đổi nhà cung cấp map hoặc thuật toán tuyến/ETA/mô phỏng.
- Commit/push/deploy, xoá dữ liệu hoặc ghi lên môi trường vận hành thật trong giai đoạn lập kế hoạch.

## Actor và luồng chính

- Khách: đăng nhập, đăng ký admin theo luồng hiện có, thông báo lỗi/thành công và redirect.
- Admin: dashboard, quản lý xe/tài xế/chuyến/tuyến/trạm/lịch, cảnh báo, báo cáo, người dùng, giám sát trực tiếp và simulator.
- Tài xế: xem chuyến/lịch được phân công, mở chi tiết và đăng xuất; không được mở màn hình/API quản trị.
- Người triển khai: build ứng dụng, phục vụ SPA và deep link cùng cấu hình hiện hữu.

## Yêu cầu chức năng và phi chức năng

- FR1: Tất cả màn hình đang phục vụ phải được render/quản lý bằng Vue; không còn React mount hoặc React island trong kết quả cuối.
- FR2: Giữ đường dẫn, query, điều hướng/back-forward, dữ liệu hiển thị, validation, trạng thái loading/empty/error/disabled và thao tác hiện có.
- FR3: Giữ layout, màu, typography, icon, responsive và maps theo baseline cố định; không coi việc dùng cùng CSS là đủ chứng minh parity.
- FR4: Giữ authentication bằng cookie, CSRF, quyền truy cập, API request/response và xử lý abort/lỗi hiện tại.
- NFR1: Không rò rỉ subscription, timer, RAF, map instance/layer/listener khi chuyển trang hoặc đổi tab; không nhân đôi SSE/polling.
- NFR2: Không thêm secret vào bundle hoặc đưa mock vào runtime; không thay dữ liệu vận hành thật để kiểm thử.
- NFR3: Build/lint/typecheck và bộ test có thể chạy lại bằng dependency được khoá, có evidence trước/sau.
- NFR4: Giữ khả năng dùng bàn phím, focus, Escape, scroll owner và semantics của form/dialog.

## Acceptance criteria

| AC | Điều kiện chấp nhận |
|---|---|
| AC1 | Toàn bộ route/component/state đã chuyển sang Vue; không còn React/react-dom/react-router-dom/lucide-react hoặc plugin/types React trong dependency/import/runtime hoạt động. |
| AC2 | So sánh baseline React và Vue cùng fixture/browser/viewport chứng minh không đổi bố cục, màu và responsive; sai khác có chủ đích phải xin duyệt, không cập nhật ảnh chuẩn để che regression. |
| AC3 | Các luồng quản trị, auth, tài xế, CRUD/filter/chi tiết/xác nhận và trạng thái lỗi giữ hành vi đã ghi trong inventory. |
| AC4 | Giám sát/map/tuyến/trạm/simulator/traffic/check-in/history/replay giữ hành vi; vào/ra trang nhiều lần không rò rỉ tài nguyên hoặc trùng subscription. |
| AC5 | Route/query/deep link/redirect/role/session/CSRF/API contract giữ nguyên, không đổi backend/schema. |
| AC6 | Typecheck Vue SFC, lint script/template, test hồi quy, production build và smoke phục vụ SPA qua deployment contract đều đạt hoặc có blocker rõ ràng, không tự coi test bỏ qua là pass. |
| AC7 | Có checkpoint baseline/rollback an toàn, bảo toàn dirty worktree; cập nhật tài liệu và review độc lập trước kết luận cuối. |

## Giả định và phụ thuộc

- Vue ổn định, không SSR/Nuxt nếu không có nhu cầu từ contract hiện tại; phiên bản/toolchain cụ thể xác minh trong Research.
- Người dùng đã duyệt implementation và yêu cầu tiếp tục phương án TypeScript 6.0.3 sau khi được báo kết quả spike TS7 không tương thích.
- Quyền cài dependency/chạy browser hoặc môi trường backend kiểm thử sẽ được xử lý khi implementation, không thực hiện trong lượt lập kế hoạch.
- Nếu hành vi cũ có bug không liên quan migration, ghi riêng và xin duyệt thay vì âm thầm sửa cùng lần chuyển framework.
