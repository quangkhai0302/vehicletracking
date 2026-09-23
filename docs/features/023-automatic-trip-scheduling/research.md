# Research 023 — Lập lịch chuyến tự động

## Kết luận

Không cần tích hợp dịch vụ bên ngoài. Feature dùng Spring scheduling đã có trong runtime Spring Boot và PostgreSQL/Flyway hiện hữu. Các quyết định timezone, idempotency và transaction được kiểm chứng từ source repository và mô tả trong `survey.md`/`spec.md`.

## Quyết định

- Dùng `ZoneId` IANA lưu trong từng lịch, không lấy `TZ` của process làm nguồn sự thật.
- Dùng unique partial index trên `(schedule_id, schedule_occurrence_at)` làm lớp chống sinh trùng cuối cùng.
- Scheduler polling hữu hạn trong backend hiện tại; thiết kế không tuyên bố đã có distributed lock/auto-assign pool.
