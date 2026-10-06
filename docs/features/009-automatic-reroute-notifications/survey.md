# Survey repository

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| ETA đã trả source/status, thời điểm fetch và segment ảnh hưởng | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/traffic/eta/TripEtaResponse.java` — `TripEtaResponse` | Có đủ tín hiệu để đánh giá breach |
| Trip/route snapshot hiện immutable | `.../trip/entity/TripEntity.java` — `route`, `stops` | Revision phải lưu bảng riêng |
| HERE provider nhận nhiều waypoint và trả section chuẩn hóa | `.../route/provider/HereRoutingProvider.java` — `calculate`, `normalizeResponse` | Có thể tính tuyến thay thế từ vị trí xe |
| SSE phát snapshot mỗi giây | `.../telemetry/service/OperationsStreamService.java` | Thêm notifications additive, giữ tương thích |
| AlertStream trước đây là placeholder | `vehicletracking-frontend/src/components/operations/AlertStream.tsx` | Cần nối notification state/API |

## Khảo sát UI copy — 06/10/2026

- `reroute/service/RerouteEvaluationService.java:buildRevision/createUnavailable` (dưới backend `src/main/java/com/quangkhai/vehicletracking_backend/`) tạo hai chuỗi có tên nhà cung cấp và lưu vào entity.
- `reroute/dto/NotificationResponse.java:from` trước sửa chuyển reason nguyên văn; được dùng bởi `NotificationService:recent/markRead` và `DashboardService:summary`. `RouteRevisionResponse:from` cũng trả reasonDetail nguyên văn. Vì vậy chỉ đổi producer không sửa hiển thị bản tin lịch sử.
- `vehicletracking-frontend/src/pages/AlertsManagementPage.vue:alertDetail` trả reason của API trực tiếp cho loại reroute; không cần thay frontend để áp dụng nội dung API mới.
- Có thay đổi frontend từ lượt menu tài khoản trước; lượt này giữ nguyên các thay đổi đó. Schema/persistence shape không đổi; không sửa migration hoặc dữ liệu đã lưu.
