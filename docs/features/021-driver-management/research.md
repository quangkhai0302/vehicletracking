# Research 021 — Quản lý tài xế

Ngày truy cập: 2026-09-18. Research chỉ xác minh cơ chế kỹ thuật, không dùng để kết luận trạng thái repository.

## Kết quả xác minh

- PostgreSQL foreign key không tự tạo index ở cột tham chiếu. `RESTRICT`/`NO ACTION` phù hợp khi hai bản ghi độc lập và cần ngăn xóa; `CASCADE` phù hợp khi bản ghi con không thể tồn tại độc lập. Với lịch sử chuyến, dùng FK `ON DELETE RESTRICT` kết hợp soft-delete thay vì cascade. Nguồn: [PostgreSQL 18 — Constraints](https://www.postgresql.org/docs/18/ddl-constraints.html).
- PostgreSQL hỗ trợ unique index có predicate `WHERE`; uniqueness chỉ áp dụng cho rows thỏa predicate. Vì `driver_id` và `status` cùng ở bảng `trips`, partial unique index có thể bảo vệ quy tắc một chuyến `IN_PROGRESS` trên mỗi tài xế. Nguồn: [PostgreSQL 17 — Partial Indexes](https://www.postgresql.org/docs/17/indexes-partial.html), [CREATE INDEX](https://www.postgresql.org/docs/17/sql-createindex.html).
- `CHECK` chỉ được dựa trên row hiện tại, không phù hợp cho invariant xuyên bảng. FK, partial unique index và transaction service phải đảm nhiệm các phần khác nhau. Nguồn: [PostgreSQL 18 — CREATE TABLE](https://www.postgresql.org/docs/18/sql-createtable.html).
- Spring Data JPA cho phép `@Lock` trên repository query method. Jakarta `PESSIMISTIC_WRITE` tuần tự hóa transaction cập nhật và yêu cầu transaction đang hoạt động. Nguồn: [Spring Data JPA — Locking](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/locking.html), [Jakarta Persistence — LockModeType](https://jakarta.ee/specifications/persistence/4.0/apidocs/jakarta.persistence/jakarta/persistence/lockmodetype).
- Transaction boundary nên đặt ở service/facade bao trọn unit of work. Nguồn: [Spring Data JPA — Transactionality](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html).

## Kết luận áp dụng

- Lưu `driver_id` và snapshot trực tiếp trên `trips` để vừa có FK vừa bảo toàn lịch sử hiển thị.
- Dùng partial unique index cho driver đang chạy; pessimistic lock giảm race ở service nhưng không thay DB backstop.
- Dùng FK `RESTRICT`, soft-delete driver và index riêng cho các FK tra cứu.
