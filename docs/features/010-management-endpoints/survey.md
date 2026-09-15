# Survey

## Khảo sát bổ sung 2026-09-14 (trước nối UI)

Working tree có nhiều sửa đổi chưa commit, bao gồm service/type/frontend của 010; giữ nguyên và mở rộng tại các điểm dưới. Bảng cũ bên dưới là khảo sát trước implementation backend.

| Hiện trạng | Evidence (đường dẫn từ repository root) | Tích hợp |
|---|---|---|
| Đã có updateRoute/deactivateRoute nhưng workspace chỉ tạo/xem | `vehicletracking-frontend/src/services/routes.ts#updateRoute`, `vehicletracking-frontend/src/components/route/RouteWorkspace.tsx#handleSaveRoute` | Tái sử dụng form với mode edit |
| Có updateTrip/deleteTrip, hook chưa gọi | `vehicletracking-frontend/src/services/fleet.ts#updateTrip`, `vehicletracking-frontend/src/hooks/useFleetWorkspace.ts#transition` | Mutation cập nhật detail/list |
| Lịch sử có page/filter và thứ tự recordedAt DESC | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/telemetry/service/TelemetryHistoryService.java#find`, `vehicletracking-frontend/src/services/telemetry.ts#fetchTelemetryHistory` | Panel chỉ đọc theo trip |
| AlertStream chỉ đọc một, nuốt lỗi | `vehicletracking-frontend/src/components/operations/AlertStream.tsx#read` | Bổ sung action và error state |
| Supersede chỉ đổi trạng thái bản ghi | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/reroute/service/RouteRevisionService.java#supersede` | Nói rõ không tạo revision mới |
| Ngừng route giữ dữ liệu lịch sử | `vehicletracking-backend/src/main/resources/db/migration/V8__add_route_active_flag.sql`, `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/route/service/RouteService.java#deactivate` | Không thêm migration |
| Spring wiring regression đã có | `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/route/service/RouteServiceWiringTest.java` | Không thay backend trong lượt UI |

| Nhận định | Evidence | Khoảng thiếu |
|---|---|---|
| Route chỉ có POST/GET | `route/controller/RouteController.java` | Thiếu PUT/DELETE, chưa có cờ active |
| Trip chỉ tạo/đổi lifecycle | `trip/controller/TripController.java` | Thiếu sửa/xóa trip SCHEDULED |
| Telemetry là append-only | `telemetry/service/TelemetryService.java` | Thiếu API history phân trang |
| Notification đã có read một bản ghi | `reroute/service/NotificationService.java` | Thiếu read-all/delete |
| Revision đã có list | `reroute/controller/RouteRevisionController.java` | Thiếu thao tác supersede thủ công |
