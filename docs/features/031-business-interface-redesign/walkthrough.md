# Walkthrough — Giao diện quản trị đồng bộ

## Kết quả

Các trang quản lý chuyển sang nền sáng, sidebar xanh đậm và hành động chính xanh ngọc. Header, số liệu, trạng thái, form và thông báo dùng cùng một hệ màu/khoảng cách. Các màn hình maps giữ nguyên giao diện đang tối ưu.

Phương tiện/tài xế/chuyến dùng bảng có cột rõ trên desktop; mobile hiển thị nhãn từng trường. Người dùng vẫn tìm kiếm, lọc, mở form, xem chi tiết và xác nhận ngừng sử dụng qua callback/API hiện có (`FleetWorkspace` → `useFleetWorkspace` → services). Không thêm dữ liệu mẫu trong sản phẩm.

Login/register dùng `AuthLayout`: giới thiệu sản phẩm ở một bên, form ở bên còn lại; mobile xếp một cột và cuộn được. Cổng tài xế dùng theme chung, giữ trạng thái chuyến ở mobile và drawer lịch trình riêng.

## Điểm triển khai

- `vehicletracking-frontend/src/ui-refresh.css`: theme nằm trong `.business-ui`, không đưa token lên `:root`.
- `src/app/ApplicationShell.tsx`: chỉ gắn theme khi `route.fullBleed` không bật. `/operations`, `/routes`, `/stations` không opt-in.
- `src/components/business/PageHeading.tsx`: header dùng lại giữa các trang quản lý.
- `src/components/business/FleetManagementTable.tsx`: danh sách mới, chỉ được render khi `FleetWorkspace.lockedTab` tồn tại.
- `src/components/business/SidePanel.tsx`: native dialog, nền inert, Escape có xét busy, trả focus về nút mở khi unmount.
- `src/components/business/AuthLayout.tsx`: bố cục auth dùng chung, không đổi luồng đăng nhập/đăng ký.

Các đường dẫn `src/` thuộc frontend. Không đổi contract API, database hoặc quyền người dùng.

## Kiểm tra lại

1. Chạy frontend bằng Node 24 và `npm run dev`.
2. Mở dashboard, phương tiện, tài xế, chuyến, lịch, cảnh báo, báo cáo và người dùng; thử tìm kiếm/lọc và mở form/chi tiết.
3. Kiểm tra ở 1440px, 768px và 390px; mở menu mobile, đóng bằng Escape.
4. Mở form lịch, cuộn xuống trường cuối, kiểm tra nút lưu/huỷ; Tab/Shift+Tab không thao tác được trang nền. Đóng drawer và kiểm tra focus trả về nút mở.
5. Kiểm tra login/register và portal tài xế; chuyển sang 3 route maps để đối chiếu giao diện cũ.

Script tự động, lệnh chạy, ảnh minh chứng và giới hạn kiểm tra xem [evidence.md](evidence.md). Các số liệu trong ảnh là fixture kiểm tra, không phải dữ liệu vận hành thật.

Review độc lập chưa hoàn tất do giới hạn sử dụng subagent; feature được ghi **Verified**, chưa ghi Reviewed.
