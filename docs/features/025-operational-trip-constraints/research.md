# Research 025 — Ràng buộc vận hành chuyến

Không cần research bên ngoài. Feature dùng lifecycle, route duration, check-in và transaction/locking nội bộ đã có trong repository. Cửa sổ thời gian là quyết định MVP ban đầu, đã bị loại bỏ theo yêu cầu ngày 2026-09-24. Cùng ngày, conflict interval chỉ còn áp dụng cho xe; tài xế chỉ bị khóa khi đã có chuyến `IN_PROGRESS`.

## Quyết định kỹ thuật

- Backend kiểm tra mọi ràng buộc; frontend chỉ phản ánh trạng thái và cải thiện feedback.
- Conflict của xe dùng interval half-open `[departure, plannedEnd)` để hai chuyến của cùng xe không chồng nhau; các chuyến đã `COMPLETED/CANCELLED` không khóa xe.
- Phân công tài xế không dùng khoảng thời gian dự kiến làm điều kiện loại trừ. Row lock và unique partial index của database bảo đảm một tài xế không thể đồng thời có hai chuyến `IN_PROGRESS`.
- Lý do hủy lưu trong bảng `trips`; chưa gắn actor vì repository chưa có authentication/RBAC.
