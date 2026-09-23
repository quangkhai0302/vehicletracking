# Test Plan: 030-ui-design-system-unification

## 1. Ma trận kiểm thử Acceptance Criteria

| AC | Mức kiểm thử | Kịch bản kiểm thử | Dữ liệu / Thao tác | Kết quả mong đợi |
|---|---|---|---|---|
| **AC-1** | Trực quan (E2E) | Kiểm tra sự đồng nhất của Shell trên tất cả các trang | Điều hướng qua Dashboard, Operations, Vehicles, Drivers, Trips, Routes, Stations, Schedules, Alerts, Reports, Users | Sidebar, Topbar và Content nền Dark Slate đồng bộ, không bị chói lóa hay giật layout |
| **AC-2** | Trực quan | Kiểm tra chuẩn Card & Surfaces | Quan sát thẻ KPI ở Dashboard, Reports, Schedules, Alerts; thẻ xe ở Vehicles; thẻ lịch ở Schedules | Bề mặt kính mờ (Glassmorphic), viền mỏng tinh tế, bo góc 12-14px, hover nâng nhẹ mượt mà |
| **AC-3** | Tương tác UI | Kiểm tra Form Controls & Buttons | Thử nhập liệu tại form Thêm xe, form Tạo lịch, ô tìm kiếm và dropdown bộ lọc | Input nền tối, chữ trắng rõ, focus viền cyan glow; nút Primary gradient nổi bật; nút Secondary thanh lịch |
| **AC-4** | Trực quan | Kiểm tra bảng màu ngữ nghĩa (Semantic Badges) | Quan sát trạng thái xe (Đang sử dụng/Ngừng), chuyến đi (Đang chạy/Hoàn tất/Hủy), cảnh báo (Critical/Major/Info) | Badge hiển thị đúng màu chuẩn: Xanh lá cho Active, Xanh dương cho In Progress, Đỏ cho Critical, Vàng cho Warning |
| **AC-5** | Tương tác & E2E | Kiểm tra trang Đăng nhập & Đăng ký | Truy cập `/login` và `/register` trên trình duyệt | Giao diện đăng nhập Cyber Operations kính mờ sang trọng, form input sắc nét, đăng nhập thành công |
| **AC-6** | Tích hợp & Build | Kiểm tra hồi quy kỹ thuật (Code Quality & Build) | Chạy lint, typecheck và production build của frontend | `npm run lint`, `tsc --noEmit` và `npm run build` thành công 0 lỗi |

## 2. Kịch bản kiểm thử thủ công qua Browser
1. **Luồng Đăng nhập**:
   - Mở `http://localhost:5173/login`.
   - Kiểm tra visual layout, độ sắc nét, hiệu ứng focus input.
   - Nhập `admin` / `admin12345678` -> Bấm Đăng nhập -> Kiểm tra chuyển hướng mượt mà tới `/dashboard`.
2. **Luồng Khảo sát từng trang**:
   - `/dashboard`: Kiểm tra Hero, 5 KPI cards, bản đồ mini, danh sách chuyến đi, cảnh báo nhanh.
   - `/operations`: Kiểm tra bản đồ toàn màn hình, thanh topbar ẩn/hiện mượt mà, floating drawer ăn khớp với theme.
   - `/vehicles`, `/drivers`, `/trips`: Kiểm tra danh sách thẻ xe, form thêm xe mới, dialog xác nhận xóa.
   - `/schedules`: Kiểm tra hero, KPI, bộ lọc, danh sách thẻ lịch, drawer tạo lịch chạy.
   - `/alerts`: Kiểm tra danh sách cảnh báo phát sáng viền theo mức độ, nút đọc tất cả, bộ lọc loại cảnh báo.
   - `/reports`: Kiểm tra bộ lọc thời gian, lưới 8 chỉ số thống kê, ghi chú thuật ngữ.
   - `/users`: Kiểm tra danh sách tài khoản, badge ADMIN/DRIVER, form cấp tài khoản.
3. **Kiểm tra Responsive**:
   - Co giãn màn hình về kích thước tablet (768px - 1024px) và mobile (< 640px) để đảm bảo sidebar drawer mở/đóng tốt và không tràn khung ngang.
