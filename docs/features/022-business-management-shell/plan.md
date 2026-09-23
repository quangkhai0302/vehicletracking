# Plan 022 — Business Management Shell

Trạng thái: **Reviewed** theo yêu cầu triển khai trực tiếp ngày 2026-09-18.

1. **Routing và shell**
   - Thêm React Router, route metadata, `ApplicationShell`, sidebar/topbar/responsive CSS.
   - Test bằng typecheck/build và manual URL/history.

2. **Dashboard trung thực với dữ liệu**
   - Tạo dashboard fetch API hiện có, metric cards, quick links, unavailable states.
   - Không tạo backend aggregate hoặc suy diễn delayed/off-route.

3. **Trang quản trị xe/tài xế/chuyến**
   - Mở rộng `FleetWorkspace` bằng page mode; tạo wrapper page và CSS business surface.
   - Giữ service/hook/API hiện có và regression Feature 021.

4. **Map module và route map-dependent**
   - Cho `MapComponent` chạy embedded với initial workspace.
   - Route `/operations`, `/routes`, `/stations` mount đúng mode; giữ cleanup/layers.

5. **Roadmap pages**
   - Tạo page có scope/dependency cho schedules, alerts, reports, users; không giả implementation.

6. **Verify và tài liệu**
   - Lint, tsc, build, diff check; manual smoke nếu runtime/browser cho phép.
   - Cập nhật evidence/walkthrough; thông báo trước rồi gọi reviewer theo model đã cấu hình.

## Điều kiện hoàn thành

- AC-01 đến AC-09 có evidence.
- Map không mount trên business pages.
- Deep links build/deploy-compatible.
- Không đổi backend/schema và không ghi đè Feature 021.
