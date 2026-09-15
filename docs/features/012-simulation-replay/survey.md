# Survey

`SimulationService.reset` đang gọi `TripService.create`, giữ routeId nhưng tạo tripId mới. `V6__create_trip_stop_checkins.sql` unique(trip_id,stop_sequence) chưa hỗ trợ nhiều lần. `CheckInService` checkpoint theo trip; ETA và reroute lấy visits từ TripStopVisitRepository. `useFleetWorkspace.newerTrip` không cho trạng thái terminal quay lại, cần phân biệt lần chạy. Working tree có thay đổi UI từ các lượt trước; giữ nguyên. Không có docs/templates.
