# Test plan

| AC | Mức test | Kịch bản | Kết quả |
|---|---|---|---|
| 1–3 | Service/controller | PUT/DELETE route và trip ở từng trạng thái | Thành công đúng trạng thái, conflict khi bất biến |
| 4 | Repository/API | History với bộ lọc, khoảng thời gian, page/size sai | Page metadata đúng, 400 cho input sai |
| 5 | Service/controller | read-all, delete, supersede lặp lại | Idempotent và không tạo bản ghi mới |
| Regression | Compile/lint/build | Backend compile/unit và frontend lint/tsc/build | Không hồi quy contract cũ |

## Kiểm tra frontend bổ sung

| AC | Mức | Fixture/kịch bản | Mong đợi |
|---|---|---|---|
| UI1 | Browser + API intercept | Edit tên/stop, PUT lỗi rồi thành công; ngừng tuyến | Payload đúng, giữ draft khi lỗi, map/list bỏ tuyến |
| UI2 | Browser + API intercept | SCHEDULED sửa giờ, xóa; IN_PROGRESS | ISO đúng, detail/list đồng bộ, chỉ SCHEDULED cho sửa/xóa |
| UI3 | Browser + API intercept | Lịch sử 2 trang, bộ lọc/empty/error | Query đúng, reset page, retry |
| UI4 | Browser + API intercept | read-all, delete lỗi/thành công | Confirmation, không optimistic khi lỗi |
| UI5 | Browser + API intercept | ACTIVE revision supersede | POST đúng path, status cập nhật |
| UI6 | CLI + browser | lint, tsc, build; desktop/mobile | Pass; không overflow panel |

Dùng Playwright đã có tại `docs/features/004-operations-layout/verification/node_modules/playwright`. Fixture không phải evidence dữ liệu HERE/DB thật.
