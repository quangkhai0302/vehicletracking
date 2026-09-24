# Survey — Điều phối chuyến tức thời

## Cây file liên quan

- Backend: `trip/dto`, `trip/controller/TripController`, `trip/service/TripService`, `trip/entity/TripEntity`, `schedule/entity/TripScheduleEntity`.
- Frontend: `features/fleet`, `features/tracking`, `pages/DashboardPage.vue`, `pages/DriverPortalPage.vue`.
- Test: trip service/controller/repository/integration và unit test fleet/map.

## Luồng hiện tại

Form tạo chuyến luôn dựng một thời điểm trước 15 phút, gửi `scheduledDepartureAt`; service lưu mốc này cho cả chuyến thủ công lẫn chuyến do scheduler tạo. Response không cho frontend biết nguồn tạo, vì vậy mọi nơi đều hiển thị giờ như lịch kế hoạch và màn chi tiết còn cho sửa trực tiếp.

## Evidence

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Request tạo chuyến bắt buộc giờ | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/trip/dto/TripCreateRequest.java`, record `TripCreateRequest` | Contract đang trộn điều phối thủ công với lập lịch |
| Service dùng cùng một hàm cho tạo thủ công và scheduler | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/trip/service/TripService.java`, `create`, `createFromSchedule`, `createInternal` | Có thể tách quy tắc theo provenance mà không nhân đôi logic |
| Trip đã có quan hệ nullable tới lịch nguồn | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/trip/entity/TripEntity.java`, fields `schedule`, `scheduleOccurrenceAt` | Không cần migration mới để phân loại nguồn |
| Lịch tự động sở hữu giờ khởi hành | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/schedule/entity/TripScheduleEntity.java`, field `departureTime` | Phù hợp quy tắc nghiệp vụ mới |
| Form thủ công luôn nhập giờ | `vehicletracking-frontend/src/features/fleet/components/TripEditor.vue`, refs `departure`, `validTime` và input `datetime-local` | Cần loại bỏ input và validation này |
| Chi tiết cho sửa giờ trực tiếp | `vehicletracking-frontend/src/features/fleet/components/TripDetailPanel.vue`, `beginScheduleEdit`, `saveSchedule` | Trái nguyên tắc lịch là nguồn sự thật |
| Nhiều màn hình gắn nhãn mốc nội bộ là khởi hành dự kiến | `FleetManagementTable.vue`, `FleetWorkspace.vue`, `DashboardPage.vue`, `TrackingVehicleCard.vue`, `DriverPortalPage.vue` | Cần cập nhật đồng bộ, tránh chỉ sửa form |

## Working tree và rủi ro

Working tree đang có nhiều thay đổi chưa commit, trong đó có `TripService`, `TripEntity` và các component fleet/map. Việc triển khai phải sửa nối tiếp, không hoàn tác các thay đổi hiện hữu. Rủi ro chính là test/fixture đang khởi tạo request và summary theo contract cũ.
