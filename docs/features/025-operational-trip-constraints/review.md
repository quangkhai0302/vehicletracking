# Review 025 — Ràng buộc vận hành chuyến

Ngày review: **2026-09-21**.

## Phạm vi đã rà soát

- Trip lifecycle backend: start, complete, cancel, assignment, update và schedule-generated trip.
- Repository overlap query, lock order và migration V17.
- Contract cancel reason giữa controller, service, DTO và frontend service/type.
- Readiness UI cho driver active, final stop visit và cancel dialog.
- Service/controller tests, lint/typecheck và giới hạn build môi trường.

## Kết quả

- Không phát hiện lỗi contract rõ ràng trong phạm vi P0 đã triển khai.
- Draft trip vẫn có thể được lưu khi chưa có tài xế, nhưng không thể start; lịch tự động vẫn bắt buộc driver active từ feature schedule.
- Conflict kiểm tra ở cả tạo, sửa giờ, gán driver và start; trip terminal không khóa tài nguyên.
- Check final stop dùng repository query có điều kiện attempt hiện tại, tránh hoàn thành dựa trên dữ liệu replay cũ.
- V17 cho phép dữ liệu lịch sử CANCELLED thiếu reason nhưng cấm reason trên status khác CANCELLED.

## Giới hạn còn lại

- Frontend production build và non-Docker backend suite (254 tests) đã đạt trên Node 24/JDK 26; PostgreSQL/Flyway integration còn cần Docker daemon.
- Review này là self-review; backend surveyor không hoàn tất khởi tạo trong phiên, nên các kết luận backend được đối chiếu trực tiếp từ source/test.

## Kết luận

Implementation đáp ứng phạm vi P0 của Feature 025 và đã có evidence runtime non-Docker. Trước khi phát hành cần chạy Flyway/integration trên môi trường có Docker.
