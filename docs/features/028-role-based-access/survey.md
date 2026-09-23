# Survey — Phân quyền người dùng và cổng tài xế

## Backend hiện tại

| Nhận định | Evidence | Hệ quả |
|---|---|---|
| Trước feature chưa có auth filter, account hay role; controller nghiệp vụ nhận request trực tiếp | `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/config/WebConfig.java` — chỉ cấu hình CORS; các controller dưới `trip/controller`, `vehicle/controller`, `schedule/controller` | Cần security chain tập trung, không dựa vào ẩn menu |
| Hồ sơ driver đã là entity độc lập và có trạng thái active | `driver/entity/DriverEntity.java` — `id`, `fullName`, `licenseNumber`, `active` | Tài khoản driver nên liên kết qua FK một-một và kiểm tra trạng thái hồ sơ |
| Chuyến và lịch đã có quan hệ driver | `trip/entity/TripEntity.java` — `driver`; `schedule/entity/TripScheduleEntity.java` — `driver`; `TripRepository.java`, `TripScheduleRepository.java` | Có thể tạo finder scoped theo `driverId`, không copy dữ liệu ra bảng portal |
| `TripService.findById` phục vụ admin không tự giới hạn theo người dùng | `trip/service/TripService.java` — `findById(long id)` | Driver cần service/repository riêng, tránh tái sử dụng endpoint admin |

## Frontend hiện tại

| Nhận định | Evidence | Hệ quả |
|---|---|---|
| `main.tsx` render `App` và chưa có auth context/guard | `vehicletracking-frontend/src/main.tsx`, `src/App.tsx` | Thêm `AuthProvider` và route guard ở root |
| `ApplicationShell` chứa menu admin và map/fleet workspace có thao tác ghi | `src/app/ApplicationShell.tsx`, `src/components/fleet/FleetWorkspace.tsx` | Không cho driver dùng shell admin hoặc snapshot toàn đội |
| Service frontend dùng `fetch`/EventSource riêng lẻ | `src/services/*.ts`, `src/services/operations.ts` | Bổ sung `credentials: include`; SSE dùng `withCredentials` |

## Rủi ro và quyết định

- Session cần CORS `allowCredentials` và origin cụ thể; không dùng wildcard.
- Username/mật khẩu bootstrap admin phải qua environment; không ghi secret vào repo/log.
- UI role chỉ là trải nghiệm; backend matcher là ranh giới bảo mật thật.
