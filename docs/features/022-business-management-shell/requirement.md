# Requirement 022 — Business Management Shell

Trạng thái: **Reviewed** — người dùng chấp thuận triển khai ngày 2026-09-18; implementation được review độc lập cùng ngày.

## Bối cảnh và mục tiêu

Ứng dụng hiện lấy bản đồ làm composition root và đặt quản lý xe, tài xế, chuyến, tuyến, trạm trong các drawer nổi. Cấu trúc này phù hợp giám sát trực tiếp nhưng không phù hợp để phát triển thành sản phẩm quản trị có lịch chạy, cảnh báo, dashboard, báo cáo và phân quyền.

Feature này tạo khung ứng dụng quản trị nghiệp vụ, chuyển bản đồ thành phân hệ **Giám sát vận hành**, đồng thời tạo URL và vùng nội dung độc lập cho các danh mục hiện có và roadmap đã thống nhất.

## Phạm vi

### In scope

- Application shell gồm sidebar, topbar, vùng nội dung, navigation desktop/mobile.
- URL/deep link cho tổng quan, giám sát, xe, tài xế, chuyến, tuyến, trạm và các module roadmap.
- Dashboard tổng quan dùng đúng dữ liệu hiện có; chỉ số chưa có nguồn phải ghi rõ chưa khả dụng.
- Trang quản trị độc lập cho xe, tài xế và chuyến, tái sử dụng API/nghiệp vụ Feature 021.
- Giữ map-first hiện tại trong trang Giám sát; các luồng tuyến/trạm cần bản đồ vẫn mở trong map với URL riêng.
- Trang định hướng cho Lịch chạy, Cảnh báo, Báo cáo, Người dùng để thể hiện IA, không giả implementation.
- Responsive sidebar/drawer, keyboard navigation, current-page semantics và browser history.

### Out of scope

- Scheduler tự động sinh chuyến, off-route/speed alert, report aggregate, authentication/RBAC thực tế.
- Thiết kế driver portal.
- Pagination/search phía server và thay đổi backend/database.
- Viết lại Leaflet, simulator, telemetry, ETA hoặc route/station editor.

## Actor và luồng chính

- Điều phối viên vào Dashboard, xem tổng quan hiện tại và chuyển tới danh mục hoặc Giám sát vận hành.
- Người quản trị mở URL trực tiếp tới Xe/Tài xế/Chuyến, thao tác trong vùng nội dung rộng thay vì drawer bản đồ.
- Người dùng mobile mở navigation drawer, chọn trang, drawer đóng và focus trở về hợp lý.
- Người dùng reload/back/forward tại URL nghiệp vụ mà không mất shell hay rơi vào 404 production.

## Functional requirements

- FR-01: Shell ổn định với brand, navigation có nhóm và topbar mô tả trang hiện tại.
- FR-02: URL là nguồn sự thật của trang; deep link và Back/Forward hoạt động.
- FR-03: Dashboard chỉ tính các metric có nguồn dữ liệu hiện tại và hiển thị trạng thái loading/error/retry.
- FR-04: Xe, tài xế, chuyến có trang độc lập, giữ CRUD/assignment/lifecycle hiện có.
- FR-05: Giám sát vận hành giữ Leaflet/realtime/simulator/traffic và cleanup hiện tại.
- FR-06: Tuyến/trạm giữ workflow map-dependent qua URL riêng.
- FR-07: Module roadmap chưa implement có trạng thái rõ ràng và không gọi API giả.
- FR-08: Navigation responsive, đóng bằng Escape/click overlay, có `aria-current` và focus visible.

## Non-functional requirements

- Không thay đổi API/database và không thêm secret.
- Không tạo state global mới nếu routing và component ownership đủ giải quyết.
- Node 24; lint, TypeScript và production build phải đạt.
- Không để map mount trên các trang quản trị, tránh SSE/Leaflet/tile traffic không cần thiết.

## Acceptance criteria

- AC-01: Mở `/dashboard` hiển thị shell business và metric từ API hiện có với loading/error/retry.
- AC-02: Sidebar điều hướng đúng tới `/operations`, `/vehicles`, `/drivers`, `/trips`, `/routes`, `/stations`; active page được công bố bằng semantic link.
- AC-03: Back/Forward và reload deep link production hoạt động; Caddy fallback được giữ và có kiểm tra cấu hình.
- AC-04: Xe, tài xế, chuyến hiển thị trong content page rộng và giữ thao tác Feature 021.
- AC-05: `/operations` giữ bản đồ, realtime, simulator, traffic và alert stream hiện có.
- AC-06: `/routes` và `/stations` mở đúng map workflow tương ứng, không tạo editor giả không có map.
- AC-07: `/schedules`, `/alerts`, `/reports`, `/users` giải thích trạng thái roadmap và dependency, không hiển thị số liệu/quyền giả.
- AC-08: Sidebar mobile đóng bằng Escape/overlay, trigger có `aria-expanded`, focus visible và không che nội dung desktop.
- AC-09: Frontend lint, typecheck, build và `git diff --check` đạt; không phát sinh backend migration/API change.

## Giả định

- Chưa có đăng nhập nên shell không hiển thị danh tính hay quyền giả định.
- Dữ liệu hiện tại có quy mô phù hợp list unpaged; pagination server-side thuộc feature sau.
- Feature 021 trong working tree là baseline phải được bảo toàn.
