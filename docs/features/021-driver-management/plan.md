# Plan 021 — Quản lý tài xế

Trạng thái: **Verified** ngày 2026-09-18. Implementation và kết quả kiểm tra được ghi tại `evidence.md`.

1. **Migration và persistence**
   - Tạo V14 với bảng drivers, FK/snapshot/index/constraint cho vehicle và trip.
   - Thêm package `driver` entity/repository; mở rộng Vehicle/Trip entity và repository query/lock.
   - Test PostgreSQL cho unique/FK/snapshot/partial index.

2. **Driver API và nghiệp vụ assignment**
   - Thêm Driver DTO/service/controller theo convention hiện tại.
   - Thêm endpoint gán/bỏ tài xế cho vehicle và trip; mở rộng response/create request.
   - Giữ transaction tại service, lock driver/vehicle/trip và ánh xạ conflict rõ ràng.
   - Thêm service/controller tests cho validation, lifecycle và lỗi.

3. **Frontend contract và state**
   - Mở rộng `types/fleet.ts`, `services/fleet.ts`, `useFleetWorkspace.ts` cho driver CRUD/assignment.
   - Giữ request cancellation, busy/error/toast và không gọi API trực tiếp trong component.

4. **Frontend UI**
   - Thêm tab/list/form tài xế và confirmation soft-delete.
   - Mở rộng VehicleEditor, TripEditor, TripDetailPanel và card để quản lý assignment/snapshot.
   - Bổ sung CSS responsive trong phạm vi Fleet Workspace.

5. **Verify và tài liệu**
   - Chạy backend tests; frontend lint, typecheck, build; kiểm tra UI nếu môi trường cho phép.
   - Cập nhật `evidence.md`, `walkthrough.md`; gọi Review Agent độc lập và ghi `review.md`.

## Rủi ro và điều kiện hoàn thành

- Không làm thay đổi snapshot trip cũ hoặc lifecycle hiện tại.
- Không để race assignment/start vượt DB constraint.
- API, Java DTO và TypeScript type phải đồng bộ.
- AC-01 đến AC-09 có code/test evidence; mọi kiểm tra chưa chạy phải ghi rõ.
