# Survey 021 — Quản lý tài xế

## Working tree

Trước feature có `AGENTS.md` và `.codex/` đang thay đổi do cấu hình subagent. Không có thay đổi backend/frontend liên quan tài xế; các thay đổi có sẵn được giữ nguyên.

## Hiện trạng và cây file liên quan

```text
vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/
├── vehicle/{controller,dto,entity,repository,service}
└── trip/{controller,dto,entity,repository,service}
vehicletracking-backend/src/main/resources/db/migration/
├── V4__create_vehicles_and_trips.sql
└── V10__add_vehicle_type.sql
vehicletracking-frontend/src/
├── components/fleet/{FleetWorkspace,VehicleEditor,TripEditor,TripDetailPanel}.tsx
├── hooks/useFleetWorkspace.ts
├── services/fleet.ts
└── types/fleet.ts
```

Không có package, bảng, API hay test driver. Các hit `driver` ở frontend cũ chỉ là `driverName`/`driverPhone`; component đó không nối vào Fleet Workspace hiện hành nên không phải đặc tả.

## Luồng hiện tại

- Vehicle controller gọi service transactional; vehicle dùng soft-deactivate và lock khi ghi.
- Trip create lock xe, kiểm tra xe/tuyến, tạo snapshot biển số và stop schedule; lifecycle lock trip + xe.
- Frontend Fleet Workspace tải xe/chuyến qua `services/fleet.ts`, state/mutation nằm trong `useFleetWorkspace`, rồi render editor/detail theo screen union.
- Flyway quản lý schema, Hibernate `ddl-auto=validate`; migration mới nhất là V13.

## Evidence

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Vehicle chưa có quan hệ tài xế | `vehicle/.../VehicleEntity.java:13-51` (`VehicleEntity`) | Cần mở rộng entity/DTO/migration |
| Vehicle CRUD dùng DTO, service transaction và soft-delete | `VehicleController.java:12-28`; `VehicleService.java:23-72` | Tái sử dụng convention cho Driver |
| Trip create chỉ nhận xe, tuyến, giờ | `trip/dto/TripCreateRequest.java:6-10`; `TripService.java:43-67` | Cần mở rộng contract driver |
| Trip lưu snapshot biển số | `TripEntity.java:26-27,42-45` | Mẫu cho snapshot driver |
| Một xe chỉ có một trip đang chạy được bảo vệ hai lớp | `TripService.java:96-123`; `V4__create_vehicles_and_trips.sql:33-36` | Áp dụng tương tự cho driver |
| Trip update hiện chỉ sửa schedule | `TripUpdateRequest.java:6-7`; `TripService.java:69-77` | Assignment nên có endpoint riêng |
| Fleet UI có hai tab và state đầy đủ | `FleetWorkspace.tsx:21-93` | Thêm tab Tài xế trong cùng workspace |
| HTTP tập trung ở service | `vehicletracking-frontend/src/services/fleet.ts:4-31` | Không gọi API trực tiếp từ component |
| Type xe/chuyến chưa có driver | `vehicletracking-frontend/src/types/fleet.ts:9-36` | Cần đồng bộ contract backend/frontend |
| Vehicle/Trip editor là điểm chọn phân công | `VehicleEditor.tsx:6-40`; `TripEditor.tsx:14-64` | Bổ sung selector với active drivers |
| Frontend chưa có component test runner | `vehicletracking-frontend/package.json:9-14` | Verify bằng lint/tsc/build + browser/manual |
| PostgreSQL 17 và Flyway chạy khi backend start | `compose.yaml:1-18`; `application.yaml:9-25` | Migration V14 phải additive |

## Rủi ro hồi quy

- Mở rộng trip summary ảnh hưởng REST list/detail và operations snapshot dùng chung type.
- Fetch relation lazy cần entity graph hoặc mapping trong transaction để tránh N+1/lazy loading.
- Assignment concurrent cần lock có thứ tự ổn định và DB unique backstop.
- Dữ liệu chuyến cũ phải hợp lệ với các cột driver nullable.
