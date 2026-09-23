# Survey

Working tree đã dirty khi bắt đầu; bảo toàn các thay đổi backend và các file map hiện có.

| Nhận định | Evidence | Tác động |
|---|---|---|
| Map và quản trị chung shell, phân biệt bằng fullBleed | `src/app/ApplicationShell.tsx:43`, `src/app/routeConfig.ts:36` | Chỉ gắn theme mới khi data-map-focus=false |
| Lớp refresh chỉ phủ một phần dark styles | `src/ui-refresh.css`, `src/pages/alerts-management.css` `.alerts-error`, `src/pages/user-management.css` `.user-role` | Thay lớp refresh bằng bộ quy tắc thống nhất, phủ trạng thái và form |
| Body khóa cuộn | `src/index.css` `body`; `src/pages/driver-portal.css` `.driver-portal` | Tạo scroll owner riêng cho auth/driver |
| Fleet dùng cùng component ở map và management | `src/components/fleet/FleetWorkspace.tsx` `lockedTab` | Render bảng mới chỉ khi lockedTab được truyền từ FleetManagementPage |
| Sidebar đã xử lý focus trap/Escape | `src/app/ApplicationShell.tsx` `useEffect` | Giữ hành vi và kiểm tra bàn phím |
| Header mỗi page viết riêng | `src/pages/{DashboardPage,ReportsPage,ScheduleManagementPage,AlertsManagementPage,UserManagementPage}.tsx` | Trích PageHeading để dùng chung |

Các đường dẫn `src/` thuộc `vehicletracking-frontend/`. Khảo sát độc lập: frontend_surveyor (Terra/medium), chỉ đọc. Luồng dữ liệu pages → services → API giữ nguyên, không cần khảo sát schema vì không đổi contract.
