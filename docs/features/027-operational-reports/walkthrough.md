# Walkthrough — Báo cáo và thống kê vận hành

1. Mở menu **Báo cáo**; trang không còn nhãn Roadmap.
2. Chọn `Từ ngày`, `Đến ngày`, tùy chọn xe và tài xế. Metadata xe/tài xế lấy từ API fleet hiện có.
3. Kiểm tra bốn KPI chính và ba KPI rủi ro. Chú thích cuối trang giải thích distance planned (tổng chiều dài tuyến), runtime actual, GPS overspeed episode theo ngưỡng cấu hình.
4. Khi API lỗi, chọn **Thử lại**; khi không có record, trang hiển thị empty state.

API có thể kiểm tra bằng `GET /api/v1/reports/operations?from=2026-09-01&to=2026-09-21`.
