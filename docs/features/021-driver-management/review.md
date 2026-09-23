# Review 021 — Quản lý tài xế

Ngày review: 2026-09-18.

Phạm vi đã đọc: migration V14; toàn bộ package `driver`; thay đổi vehicle/trip controller, DTO, entity, repository, service; frontend type/service/hook/component/CSS; unit/controller/integration test và tài liệu feature.

## Finding đã xử lý

### Medium — payload cập nhật xe có thể vô tình bỏ assignment

- Vị trí ban đầu: `VehicleUpsertRequest` và `VehicleService.update`.
- Hành vi: nếu `driverId` nullable nằm trong payload CRUD xe, JSON từ client cũ không có field này và yêu cầu bỏ gán chủ động đều trở thành `null` như nhau.
- Tác động: client cũ sửa tên/mô tả xe có thể làm mất tài xế hiện tại.
- Sửa: giữ nguyên payload CRUD xe; frontend gọi endpoint assignment chuyên biệt. Thêm regression test `VehicleServiceTest.updateDetails_preservesExistingDriverAssignment` và refresh danh sách nếu bước đồng bộ assignment frontend thất bại một phần.
- Trạng thái: **Resolved**.

### Low — thẻ xe có thể hiển thị hồ sơ tài xế cũ sau khi sửa

- Vị trí ban đầu: `useFleetWorkspace.saveDriver`.
- Hành vi: state danh sách driver được cập nhật nhưng `vehicle.driver` đang giữ summary cũ cho tới lần reload tiếp theo.
- Tác động: tab Đội xe có thể tạm hiển thị tên/điện thoại/GPLX cũ, dù API đã lưu thành công.
- Sửa: cập nhật summary tài xế trong state xe sau khi lưu hồ sơ; không cập nhật `trip.driver` vì đó là snapshot lịch sử theo thiết kế.
- Trạng thái: **Resolved**.

## Finding còn mở

Không phát hiện finding Critical/High/Medium/Low còn mở trong review trực tiếp.

## Acceptance criteria và test gap

- AC-01 đến AC-09 có code/test evidence trong `evidence.md`.
- Full backend suite đạt 254/254; PostgreSQL/Flyway V14, Hibernate validation, lint, typecheck và production build đều đạt.
- Khoảng trống: chưa kiểm tra browser thủ công responsive/accessibility và repository chưa có frontend component/E2E tests.
- Review Agent `gpt-5.6-sol/high` không chạy được do usage limit, vì vậy review này là fallback self-review, không phải review độc lập.

## Kết luận

**Accept with follow-up** — source và automated quality gates đạt; nên chạy smoke test browser và review độc lập khi reviewer model khả dụng. Feature giữ trạng thái **Verified**, chưa nâng lên **Reviewed**.