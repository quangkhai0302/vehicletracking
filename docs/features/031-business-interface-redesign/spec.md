# Spec

## UI contract

- Theme scope `.business-ui` chỉ gắn shell không fullBleed và trang auth/driver; giữ nguyên root/map tokens.
- Canvas xám sáng, surfaces trắng, navigation xanh đậm, accent xanh ngọc. Nội dung 14px, trợ giúp 12px, badge/metadata có thể 11px, heading 25–30px; branding dùng cỡ nhỏ riêng. Không dùng glow trên màn hình quản trị.
- PageHeading chung: nhãn nghiệp vụ, tiêu đề, mô tả, action phía phải; wrap trên mobile.
- Fleet: semantic table trên desktop, các ô có nhãn trên mobile; status bằng chữ + màu. Gọi lại callback hiện hữu cho edit/deactivate/view trips/select trip. Loading/error/empty vẫn hiển thị.
- Auth: vùng giới thiệu sản phẩm và form rõ ràng; mobile một cột; giữ field validation, error, success, redirect.
- Các form, dialog xác nhận, drawer lịch/tài xế có focus rõ, contrast đủ đọc; nhãn/disabled/busy giữ nguyên. Drawer quản trị dùng native dialog để hỗ trợ Escape/focus trapping.
- Reports/dashboard chỉ hiển thị dữ liệu hiện có, không tự tạo số liệu hoặc biểu đồ tăng trưởng giả.
- Sidebar mobile hiển thị overlay, Escape và trả focus về nút mở. Non-map shell 1 cột khi <=960px. Auth/driver có viewport scroll riêng.

AC1–AC6 theo requirement.md. Không thay API, database, quyền hoặc thuật toán bản đồ. Kiểm chứng hash file map trước/sau và computed styles khi bật/tắt refresh trên map shell.
