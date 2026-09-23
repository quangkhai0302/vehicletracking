# Review — Báo cáo và thống kê vận hành

Review nội bộ sau implementation:

- Scope bám yêu cầu: bộ lọc ngày/xe/tài xế và bảy chỉ số đã có contract.
- Không thêm migration vì dữ liệu cần thiết đã tồn tại; ngưỡng quá tốc độ được cấu hình thay vì hardcode trong UI.
- Query report giới hạn theo trip IDs cho telemetry và notification type để tránh đếm dữ liệu ngoài phạm vi.
- Baseline đúng giờ/trễ dùng offset lịch gốc của stop cuối, không dùng live ETA đã bị traffic cập nhật; tài nguyên inactive vẫn xuất hiện trong bộ lọc lịch sử.
- Frontend đã bỏ Roadmap marker cho `/reports`, có state loading/error/empty và không polling lịch sử.
- Cần chạy lại Maven test bằng JDK 26 và frontend build bằng Node >=22.12 trước khi phát hành.

Independent reviewer đã re-review sau các chỉnh sửa về cohort trip, baseline ETA, max range và UX state; không còn blocker thực chất. Reviewer ghi nhận follow-up còn lại là integration test cho JPQL/boundary UTC và chạy backend trên JDK 26.

Trạng thái: **Verified with environment limitations; Accept with follow-up**.
