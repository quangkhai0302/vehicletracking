# Requirement 021 — Quản lý tài xế

Trạng thái: **Approved** — người dùng yêu cầu trực tiếp triển khai feature ngày 2026-09-18.

## Bối cảnh và mục tiêu

Hệ thống đã quản lý xe và chuyến nhưng chưa có dữ liệu tài xế. Feature bổ sung danh mục tài xế, phân công tài xế hiện tại cho xe và ghi nhận tài xế thực hiện từng chuyến mà không làm mất lịch sử khi thông tin/phân công thay đổi.

## Phạm vi

### In scope

- Tạo, xem, sửa và ngừng sử dụng tài xế.
- Thông tin tài xế: họ tên, số điện thoại, số giấy phép lái xe (GPLX), trạng thái và timestamps.
- Gán/bỏ gán một tài xế hiện tại cho xe.
- Gán/bỏ gán tài xế cho chuyến khi chuyến còn `SCHEDULED`.
- Khi tạo chuyến, UI mặc định chọn tài xế hiện đang gán cho xe; người dùng có thể chọn tài xế khác hoặc không gán.
- Lưu snapshot thông tin tài xế trên chuyến để bảo toàn lịch sử.
- Hiển thị và thao tác trong workspace Đội xe/Tài xế/Chuyến đi hiện có.

### Out of scope

- Ca làm, bảng chấm công, lương, tài liệu/ảnh GPLX và ngày hết hạn.
- Lịch sử nhiều lần đổi tài xế cho cùng một xe hoặc chuyến.
- Xác thực/phân quyền mới, thông báo hoặc tích hợp nhà cung cấp bên ngoài.
- Tự động tối ưu lịch hoặc phân tài xế theo vị trí.

## Actor và luồng chính

- Điều phối viên quản lý danh mục tài xế, gán tài xế active cho xe, chọn tài xế khi tạo chuyến và có thể đổi/bỏ gán trước khi chuyến khởi hành.
- Điều phối viên xem tài xế đã chốt trong danh sách/chi tiết chuyến; thay đổi thông tin hay phân công xe sau đó không làm đổi snapshot chuyến.

## Functional requirements

- FR-01: CRUD tài xế dùng soft-delete; GPLX là định danh duy nhất kể cả tài xế inactive.
- FR-02: Chỉ tài xế active được gán mới cho xe/chuyến.
- FR-03: Mỗi xe active có tối đa một tài xế hiện tại; một tài xế active chỉ được gán hiện tại cho tối đa một xe active.
- FR-04: Chuyến có tối đa một tài xế; có thể không có tài xế để tương thích dữ liệu/luồng hiện tại.
- FR-05: Chỉ chuyến `SCHEDULED` được đổi/bỏ tài xế. Snapshot được cập nhật khi phân công chuyến thay đổi và bất biến sau khi bắt đầu.
- FR-06: Một tài xế không được có nhiều hơn một chuyến `IN_PROGRESS`; các chuyến `SCHEDULED` trùng thời gian không bị chặn trong phạm vi này.
- FR-07: Không thể ngừng tài xế nếu còn gán cho xe active hoặc chuyến `SCHEDULED`/`IN_PROGRESS`.
- FR-08: UI có loading, error, empty state, search/filter, xác nhận ngừng sử dụng và khóa thao tác đang gửi.

## Non-functional requirements

- Tính nhất quán được bảo vệ ở service transaction và constraint/index PostgreSQL phù hợp.
- Không hard-delete dữ liệu đã tham gia lịch sử vận hành; không cascade xóa chuyến.
- API dùng DTO, Bean Validation và Problem Details theo convention hiện tại.
- Migration additive và tương thích các row xe/chuyến hiện có.

## Acceptance criteria

- AC-01: Người dùng tạo/sửa/xem/tìm tài xế với họ tên, điện thoại và GPLX; input sai nhận lỗi validation; GPLX trùng nhận `409`.
- AC-02: Xóa tài xế hợp lệ chuyển `active=false`; xóa lặp lại idempotent; tài xế đang được sử dụng nhận `409`.
- AC-03: Người dùng gán/bỏ gán tài xế active cho xe; không thể gán cùng tài xế cho hai xe active và conflict đồng thời được DB bảo vệ.
- AC-04: Form tạo chuyến preselect tài xế của xe; API lưu đúng tài xế active được chọn hoặc `null` khi không gán.
- AC-05: Người dùng đổi/bỏ tài xế của chuyến `SCHEDULED`; thao tác với chuyến đã bắt đầu nhận `409`.
- AC-06: Tên, điện thoại và GPLX snapshot trên chuyến không đổi khi hồ sơ tài xế hoặc phân công xe thay đổi.
- AC-07: Không thể bắt đầu chuyến khi tài xế đã inactive hoặc đang chạy chuyến khác; race start được partial unique index chặn.
- AC-08: Workspace có tab Tài xế và hiển thị phân công trong xe/chuyến, hoạt động ở desktop/mobile với loading/error/empty/confirm.
- AC-09: Backend tests, frontend lint/typecheck/build và migration PostgreSQL liên quan đều đạt hoặc giới hạn môi trường được ghi rõ.

## Giả định

- Không bắt buộc mọi xe/chuyến phải có tài xế để không phá vỡ dữ liệu và API hiện tại.
- GPLX được chuẩn hóa trim + chữ hoa; điện thoại được trim nhưng giữ định dạng hiển thị hợp lệ.
- Không triển khai authorization mới vì repository hiện chưa có mô hình người dùng/quyền.
