# Plan

Approved: người dùng đã yêu cầu triển khai và xác nhận cả hai cách chỉnh tuyến.

1. Sửa trần speed, buffer marker/camera và regression tests.
2. Migration điểm dẫn đường và mốc áp dụng revision; service preview/save/copy, DTO/controller.
3. RouteMotion nối prefix và revision; dùng chung geometry cho simulator, check-in, ETA, API tuyến đang chạy.
4. Frontend editor điểm dẫn đường, service/types; cập nhật map khi revision đổi.
5. Test Java26, Node native, tsc/lint/build; cập nhật evidence và walkthrough. Không commit/push.

Rủi ro: nối revision sai vị trí hoặc sai attempt; kiểm tra nối hình học và lọc attempt. Không sửa chuyến đã dùng tuyến bằng endpoint quản lý tuyến.

Tiến độ 2026-09-15: bước 1–4 đã triển khai; bước 5 đã qua 58 test backend mục tiêu, 4 test frontend, tsc/lint/build. Pending test PostgreSQL bản cuối và thao tác browser do giới hạn môi trường; chưa đánh dấu Verified. Chi tiết trong evidence.md/walkthrough.md/review.md.
