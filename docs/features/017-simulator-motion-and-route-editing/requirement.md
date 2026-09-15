# 017 — Chuyển động và chỉnh tuyến

Trạng thái: Approved (người dùng yêu cầu sửa; xác nhận cả kéo tuyến và tự đổi tuyến).

AC1: Tốc độ simulator khớp flow HERE tại xe, không bị trần 1,5× tốc độ trung bình.
AC2: Marker nội suy qua các snapshot, đi theo geometry khi có dữ liệu, dừng khi pause/mất dữ liệu; replay không chạy ngược về đầu.
AC3: Người dùng đặt/kéo điểm dẫn đường, xem trước, lưu hoặc hủy; các điểm này không thành trạm check-in.
AC4: Simulator áp dụng revision ACTIVE phù hợp và giữ đoạn đã chạy, check-in và ETA theo geometry mới.
AC5: Tuyến đã dùng bởi chuyến vẫn bất biến; chỉnh bản sao cần thao tác rõ ràng. Dữ liệu điểm dẫn đường và mốc áp dụng revision được lưu qua Flyway.

Ngoài phạm vi: GPS thật tự điều khiển xe; mô hình đèn đỏ/làn đường; thay nhà cung cấp bản đồ.

