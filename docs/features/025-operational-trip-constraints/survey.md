# Survey 025 — Ràng buộc vận hành chuyến

Ngày khảo sát: **2026-09-21**. Working tree có thay đổi chưa commit của Features 021–024; không hoàn tác.

| Nhận định | Evidence | Gap/tác động |
|---|---|---|
| Start đã khóa trip/xe và chống hai chuyến `IN_PROGRESS`, nhưng cho phép trip không có driver | `vehicletracking-backend/.../trip/service/TripService.java`, `transition()` nhánh `IN_PROGRESS`: chỉ `if (trip.getDriver() != null)` mới lock/check driver | Cần driver bắt buộc trước start |
| Create manual cho phép `driverId=null` | `TripService.create()` → `createInternal(... input.driverId())`; `TripCreateRequest` driver nullable | UI/API có thể tạo chuyến không thể khởi hành |
| Schedule bắt buộc driver nhưng conflict chỉ cùng `scheduledDepartureAt` | `TripService.createInternal()`; `TripRepository.existsBy...ScheduledDepartureAt...` | Hai chuyến khác giờ nhưng overlap duration vẫn được tạo |
| Complete chỉ đổi status | `TripService.transition()` nhánh `COMPLETED` | Chưa yêu cầu check-in trạm cuối |
| Cancel chưa nhận request body | `TripController.cancel()` và `TripService.cancel(long)` | Không lưu được lý do hủy |
| Check-in có stop sequence/attempt và query visits | `CheckInService`; `TripStopVisitRepository.findAllByTripIdOrderByStopSequenceAsc()` | Có thể xác định final stop đã check-in |
| UI cho phép “Chưa gán tài xế”, nút start/complete không có readiness guard | `TripEditor.tsx`, `TripDetailPanel.tsx` | Cần đồng bộ contract và lỗi nghiệp vụ |
| DB đã có unique running vehicle/driver và snapshot pairing | `V4__create_vehicles_and_trips.sql`, `V14__create_drivers_and_assignments.sql` | Có thể giữ làm backstop, bổ sung interval query bằng service |

Các ràng buộc active vehicle/driver, route/station active, ordered check-in và duplicate schedule occurrence đã có; Feature025 chỉ bổ sung phần còn thiếu trong MVP.
