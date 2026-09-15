# Test plan

- Unit: tạo xe mặc định `CAR`, tạo/sửa `MOTORCYCLE`, DTO trả đúng loại.
- MVC: request có `MOTORCYCLE` được nhận; giá trị enum sai trả HTTP 400.
- Migration/integration: Flyway V1–V10 và Hibernate validate trên PostgreSQL nếu Docker khả dụng.
- Frontend: lint, TypeScript `--noEmit`, production build; kiểm tra source marker chọn glyph theo `vehicleType`.
