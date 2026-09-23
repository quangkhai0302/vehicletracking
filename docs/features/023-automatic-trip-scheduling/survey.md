# Survey 023 — Lập lịch chuyến tự động

Ngày khảo sát: **2026-09-21**. Working tree có thay đổi Feature 021/022 chưa commit; phải bảo toàn.

## Evidence

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Trip hiện là một lần chạy có `scheduledDepartureAt`, route, vehicle và driver tùy chọn | `trip/entity/TripEntity.java:18-58`; `trip/dto/TripCreateRequest.java:6-12` | Có thể tái sử dụng Trip làm bản ghi được sinh |
| Quy tắc tạo trip đã kiểm tra vehicle/driver/route active và snapshot stops | `trip/service/TripService.java:47-82` | Scheduler phải gọi service nội bộ dùng chung |
| Lock hiện tại dùng pessimistic lock trên vehicle/driver | `vehicle/repository/VehicleRepository.java:15-20`; `driver/repository/DriverRepository.java:15-20` | Có nền tảng bảo vệ cạnh tranh tài nguyên |
| Không có domain schedule và `/schedules` chỉ là roadmap | `frontend/src/App.tsx:42-45`; `frontend/src/app/routeConfig.ts:49-55` | Cần module backend/frontend mới |
| Clock nghiệp vụ hiện là UTC | `simulation/service/SimulationConfig.java:4`; `TripService.java:188-190` | Schedule phải lưu timezone và resolve local date/time trước khi tạo Instant |
| Flyway dùng `ddl-auto=validate`, migration mới nhất là V14 | `backend/src/main/resources/application.yaml:14-25`; `db/migration` | Tạo V15, không sửa migration cũ |
| Chưa có Redis/Quartz/ShedLock/distributed scheduler | `compose.yaml`; `compose.production.yaml`; `pom.xml` | MVP dùng polling + unique database guard, chưa tuyên bố scale ngang an toàn tuyệt đối |
| Frontend fleet đã có request/error/form patterns | `frontend/src/services/fleet.ts:1-45`; `FleetWorkspace.tsx:57-137`; `fleet.css:260-378` | Schedule page nên là module riêng, tái sử dụng style business surface |

## Rủi ro

- Xung đột hai lịch cùng xe/tài xế vào cùng thời điểm được xử lý bằng lỗi occurrence; không tự đổi tài nguyên.
- DST/giờ không tồn tại phụ thuộc `ZoneRules`; API phải trả lỗi validation có kiểm soát.
- Scheduler restart chỉ tạo occurrence trong horizon 7 ngày và bỏ qua thời điểm đã qua.
