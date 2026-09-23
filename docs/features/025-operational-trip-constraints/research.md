# Research 025 — Ràng buộc vận hành chuyến

Không cần research bên ngoài. Feature dùng lifecycle, route duration, check-in và transaction/locking nội bộ đã có trong repository. Quyết định cửa sổ thời gian và conflict interval là policy nghiệp vụ MVP, được cấu hình backend thay vì hardcode ở frontend.

## Quyết định kỹ thuật

- Backend kiểm tra mọi ràng buộc; frontend chỉ phản ánh trạng thái và cải thiện feedback.
- Conflict dùng interval half-open `[departure, plannedEnd)` để hai chuyến không chồng nhau; các chuyến đã `COMPLETED/CANCELLED` không khóa tài nguyên.
- Lý do hủy lưu trong bảng `trips`; chưa gắn actor vì repository chưa có authentication/RBAC.
