# Requirement: Redesign Màn hình Station Management

## 1. Mục tiêu
Tái thiết kế giao diện Quản lý trạm (Station Management) trên `vehicletracking-frontend` theo đúng quy định tại `docs/design.md`, `AGENTS.md` và `docs/workflow.md`.
Tuyệt đối KHÔNG bổ sung chức năng mới ngoài phạm vi, giữ nguyên toàn bộ hành vi CRUD và không thay đổi backend API.

## 2. Tiêu chí chi tiết
1. **Map-centric**: Bản đồ toàn màn hình làm workspace chính, không nhúng bản đồ vào card nhỏ.
2. **Compact station list**: Danh sách trạm dạng compact ở panel bên trái, có ô tìm kiếm tức thời theo tên hoặc địa chỉ.
3. **Selected marker/list state**: Thể hiện rõ nét trạng thái trạm đang được chọn trên cả danh sách trạm và bản đồ (marker có viền halo sáng nổi bật, nâng z-index). Khi chọn trạm, tự động pan/center bản đồ đến vị trí trạm.
4. **Contextual detail drawer**: Panel chi tiết trạm mở ở cạnh phải màn hình khi chọn một trạm hoặc khi tạo/chỉnh sửa trạm. Trình bày dạng danh sách thuộc tính trực quan theo Mục 12 `docs/design.md`, không phân mảnh thành các metric card con.
5. **Geofence visualization**: Vòng tròn bán kính check-in chỉ hiển thị cho trạm đang được chọn hoặc trạm đang tạo/sửa (draft). Đường viền rõ nét, nền mờ bán trong suốt, không che khuất nhãn đường phố.
6. **Create/Edit mode rõ ràng**: Phân định tường minh 3 trạng thái `BROWSE`, `CREATE`, `EDIT`. Khi Cancel ở chế độ `EDIT`, khôi phục nguyên trạng thái cũ của trạm mà không làm biến đổi dữ liệu.
7. **Bảo toàn CRUD**: Giữ nguyên 4 thao tác xem danh sách, tạo mới, chỉnh sửa thông tin và ngừng sử dụng trạm qua các API hiện có.
8. **Tuân thủ Typography & Design Tokens**: Sử dụng font chữ Inter và hệ thống màu sắc semantic tokens từ `docs/design.md`.
