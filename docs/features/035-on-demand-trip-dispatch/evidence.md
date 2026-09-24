# Evidence — Điều phối chuyến tức thời

Trạng thái: **Verified ngày 2026-09-24**.

## Kết quả triển khai

- `TripCreateRequest` chỉ còn xe, tuyến và tài xế tùy chọn. `TripService.create` tự lấy operations clock làm mốc kỹ thuật cho chuyến thủ công.
- `TripSummaryResponse` trả `dispatchMode`, `scheduleId`, `scheduleName`; nguồn được suy ra từ quan hệ `TripEntity.schedule` hiện có nên không cần migration.
- `TripService.createFromSchedule` giữ occurrence làm giờ kế hoạch và chỉ kiểm tra chồng thời gian giữa các lịch cố định của cùng xe.
- `TripService.start` chỉ chặn xung đột vận hành thật: xe hoặc tài xế đang có chuyến `IN_PROGRESS`; các chuyến chờ khác không ngăn gán tài xế hoặc khởi hành.
- Endpoint và DTO sửa trực tiếp giờ chuyến đã được loại bỏ. Thời gian cố định tiếp tục được quản lý tại lịch chạy tự động.
- Form chuyến đổi thành **Điều phối chuyến ngay**, không còn input ngày/giờ. Danh sách, chi tiết, dashboard, giám sát và cổng tài xế phân biệt `ON_DEMAND` với `FIXED_SCHEDULE`.
- Dashboard/báo cáo chỉ tính trễ và đúng giờ cho chuyến sinh từ lịch cố định; chuyến tức thời vẫn được tính vào tổng chuyến, quãng đường và thời gian chạy.
- Replay giữ nguyên giờ kế hoạch của chuyến cố định; chuyến tức thời có thể làm mới mốc kỹ thuật cho lần chạy mới.

## Mapping acceptance criteria

| AC | Evidence code/test | Kết quả |
|---|---|---|
| AC-01 | `TripCreateRequest`; `TripEditor.vue`; `FleetControllerTest.onDemandTripCreationDoesNotRequireDepartureTimeAndReturnsLocation` | Request tạo thủ công không nhận/gửi giờ xuất phát. |
| AC-02 | `TripDispatchMode`; `TripSummaryResponse.from`; các test `create_snapshotsLoopAndScheduleAcrossMidnight`, `scheduledCreation_allowsDriverOverlapOnAnotherVehicle` | Chuyến thủ công trả `ON_DEMAND`; occurrence trả `FIXED_SCHEDULE` và lịch nguồn. |
| AC-03 | `TripDetailPanel.vue`; `FleetWorkspace.vue`; `FleetManagementTable.vue`; test render chuyến tức thời trong `fleet.test.ts` | Không gắn nhãn kế hoạch cho mốc kỹ thuật của chuyến tức thời. |
| AC-04 | `TripDetailPanel.vue`; test `fixed-schedule trip keeps planned time labels...` | Chuyến lịch cố định vẫn hiển thị xuất phát/hoàn thành kế hoạch. |
| AC-05 | `TripController`; đã xóa `TripUpdateRequest`; `FleetControllerTest` kiểm tra `PUT /api/v1/trips/{id}` trả 405 | Không còn sửa giờ trực tiếp trên chuyến. |
| AC-06 | `TripService.start`; `TripServiceTest.assignDriver_allowsOverlappingScheduledTrip`, `start_rejectsDriverRunningAnotherTrip`, `start_blocksSecondRunningTrip`; `FleetRepositoryIntegrationTest` | Một tài xế có thể được phân nhiều chuyến chờ; chỉ xung đột chuyến đang chạy mới chặn khởi hành. |
| AC-07 | Các lệnh kiểm tra bên dưới | Frontend sạch; backend test liên quan sạch, full integration bị giới hạn Docker được ghi rõ. |

## Kiểm tra đã chạy

Môi trường: Node.js `v24.16.0`; OpenJDK `26.0.1-amzn`.

| Lệnh | Kết quả |
|---|---|
| `npm run lint` | Exit 0. |
| `npm run typecheck` | Exit 0. |
| `npm run test:unit -- --run` | 14 file, 98 test đạt. |
| `npm run test:motion` | 1 test đạt. |
| `npm run build` | Exit 0; Vite build production thành công, 2031 module transformed. |
| `./mvnw -q -DskipTests test-compile` | Exit 0 trên Java 26. |
| `./mvnw -q -Dtest=TripServiceTest,FleetControllerTest,DashboardServiceTest,OperationalReportServiceTest,SimulationReplayTest test` | 58 test đạt, 0 failure/error. |
| `./mvnw -q test` trong sandbox | 265 test được phát hiện: 259 đạt, 6 integration test lỗi trước khi chạy do Testcontainers không truy cập Docker; 0 assertion failure. |
| `git diff --check` | Exit 0. |

## Giới hạn kiểm chứng

- Sáu integration test dùng PostgreSQL Testcontainers chưa chạy được trong sandbox vì không có quyền `/var/run/docker.sock`. Yêu cầu chạy ngoài sandbox không khởi tạo được do phiên Codex không làm mới được access token; đây là giới hạn môi trường, không phải lỗi assertion của feature.
- `npm run verify:vue` là audit migration tùy chọn và đang tham chiếu đường dẫn CSS cũ `src/app/application-shell.css`; lệnh chuẩn `npm run build` vẫn thành công. Không sửa script audit ngoài phạm vi feature này.
- Chưa thực hiện walkthrough bằng trình duyệt với backend thật trong lượt này; logic hiển thị được kiểm chứng bằng unit component và production build.
