# Survey repository

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| ETA đã trả source/status, thời điểm fetch và segment ảnh hưởng | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/traffic/eta/TripEtaResponse.java` — `TripEtaResponse` | Có đủ tín hiệu để đánh giá breach |
| Trip/route snapshot hiện immutable | `.../trip/entity/TripEntity.java` — `route`, `stops` | Revision phải lưu bảng riêng |
| HERE provider nhận nhiều waypoint và trả section chuẩn hóa | `.../route/provider/HereRoutingProvider.java` — `calculate`, `normalizeResponse` | Có thể tính tuyến thay thế từ vị trí xe |
| SSE phát snapshot mỗi giây | `.../telemetry/service/OperationsStreamService.java` | Thêm notifications additive, giữ tương thích |
| AlertStream trước đây là placeholder | `vehicletracking-frontend/src/components/operations/AlertStream.tsx` | Cần nối notification state/API |
