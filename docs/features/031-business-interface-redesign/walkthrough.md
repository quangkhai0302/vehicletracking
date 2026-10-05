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

## Follow-up 2026-10-05 — Bố cục thông tin lịch chạy trong Vue

Trang `/schedules` đặt mỗi nhãn ngay trên giá trị trong grid responsive. Xe và tài xế có mục riêng; “Giờ khởi hành” và “Cách khởi hành” không còn trùng nhãn. Múi giờ `Asia/Ho_Chi_Minh` hiển thị “Giờ Việt Nam”. Chữ giá trị 14px, nhãn đậm màu hơn và dữ liệu dài được xuống dòng.

Các file hiện tại: `vehicletracking-frontend/src/pages/ScheduleManagementPage.vue`, `src/features/schedules/styles/schedule-management.css` và `src/ui-refresh.css`. Không thay đổi lịch hoặc thời điểm tạo chuyến.

Mở `/schedules` với một hoặc nhiều lịch; kiểm tra tên tài xế dài, khởi hành thủ công/tự động và trạng thái không có lần chạy tiếp theo. Kết quả browser ở 320/390/768/1440px cùng giới hạn lint/typecheck/unit/build được ghi ở phần follow-up trong [evidence.md](evidence.md).
