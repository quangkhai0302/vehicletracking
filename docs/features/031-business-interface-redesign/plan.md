# Plan

Trạng thái: **Verified** (2026-09-22). Người dùng yêu cầu trực tiếp làm lại toàn bộ UI trừ maps. Chưa chuyển Reviewed: subagent reviewer bị giới hạn sử dụng, chưa có kết quả review độc lập.

1. Thêm PageHeading/AuthLayout và render bảng fleet riêng cho management; file trong `src/components/business/`, pages và FleetWorkspace (chỉ nhánh lockedTab). Giữ toàn bộ callback/API.
2. Thay nội dung `src/ui-refresh.css` bằng theme scoped hoàn chỉnh, cập nhật class opt-in tại shell/auth/driver; không thay root/map CSS.
3. Hoàn thiện focus/scroll/responsive của sidebar, form, drawer. Ưu tiên native dialog cho drawer ở page quản trị riêng.
4. Chạy frontend checks, browser fixtures có dữ liệu/error/empty ở nhiều viewport, hash bảo toàn maps. Sửa findings thực tế.
5. Reviewer độc lập chỉ đọc (Sol/high); cập nhật evidence/walkthrough/review.

Không đổi contract/migration/dependency runtime. Rủi ro chính: CSS cascade với component fleet dùng chung, breakpoint và dark text còn sót; kiểm tra computed styles và ảnh thật để kiểm soát.

Đã hoàn thành bước 1–4 và cập nhật tài liệu bước 5. Rà soát của main agent và giới hạn review được ghi tại `review.md`; lệnh/kết quả thực tế tại `evidence.md`.
