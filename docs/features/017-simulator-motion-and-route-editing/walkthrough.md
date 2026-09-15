# Hướng dẫn kiểm tra

## Khởi chạy

1. Khởi động lại backend Java 26 để Flyway áp dụng V11. Giữ `ddl-auto=validate`; không sửa/xóa V1–V10 hoặc dữ liệu chuyến cũ.
2. Khởi động frontend bằng Node 24 và tải lại trang để nhận bundle mới.
3. Chạy Maven test trên môi trường truy cập được Docker/PostgreSQL trước khi chấp nhận thay đổi.

## Chuyển động và vận tốc

Chọn xe → Mô phỏng → Bắt đầu. 1x/5x/10x vẫn là tốc độ phát, không nhân số km/h hiển thị. `useVehicleMarkers` phát trễ khoảng 1,5 giây để có hai mẫu cho nội suy; server vẫn là nguồn dữ liệu chính. Mất stream thì xe giữ ở mẫu cuối, không tự tiếp tục chạy.

Kiểm tra góc cua ở 1x/5x/10x, bật theo xe, kéo bản đồ, tạm dừng, chạy tiếp, chạy lại và chuyển màn hình. Khi có geometry khớp, marker nội suy theo đường; khi chưa tải được geometry dùng nội suy giữa các tọa độ. Chưa có phép đo FPS browser để cam kết 60 FPS.

`TrafficEtaService.currentSectionRate` lấy flow khớp vị trí/hướng đường hiện tại, quy đổi so với vận tốc nền geometry. Không còn giới hạn tỷ lệ 1,5. HERE 37 km/h không còn bị ép về 15 khi nền khoảng 10 km/h. Đoạn không có flow hợp lệ vẫn dùng nền tuyến; không ép mọi đường lên 30–37 km/h.

## Kéo chỉnh tuyến

Tuyến & trạm → chọn tuyến → **Kéo chỉnh đường đi**. Kéo đường xanh đến đường muốn đi (chuột), hoặc bấm đường để thêm điểm trắng rồi kéo điểm. Có thể pan map ở vùng ngoài đường. Bấm **Tính lại tuyến** để gọi backend/HERE, xem preview rồi **Lưu tuyến**.

Trạm/check-in không thay đổi; điểm trắng chỉ là điểm dẫn đường. Tối đa 20 điểm và tổng trạm + điểm không quá 50. Cho phép bỏ điểm và thay thứ tự trong cùng chặng. Lỗi provider giữ bản gốc; hủy bản nháp có xác nhận.

Tuyến đã có chuyến không bị ghi đè: chọn **Lưu thành tuyến mới**, rồi chọn tuyến mới khi tạo chuyến. Editor này không sửa thủ công trực tiếp chuyến đang chạy. Form “Sửa tuyến” thông thường tính lại từ các trạm và bỏ điểm dẫn đường, có thông báo riêng.

## Tự đổi tuyến

`RerouteEvaluationService` giữ chính sách ngưỡng/cooldown hiện có. Khi HERE Routing trả tuyến thay thế được chấp nhận, simulator thử áp dụng revision ACTIVE ở tick tiếp theo, giữ prefix đã chạy. Việc đọc cache không xóa bộ đếm điều kiện kẹt xe. Không bỏ trạm chỉ vì xe vừa check-in tại mép vùng bán kính.

Simulator, API `GET /api/v1/trips/{id}/route`, ETA và kiểm tra trace check-in dùng hình học đã áp dụng. Lưu mốc áp dụng theo attempt để khôi phục sau restart; chạy lại về tuyến gốc, giữ lịch sử revision cũ.

Không đảm bảo HERE luôn tìm được đường khác. Khi provider không trả tuyến tốt hơn hoặc không nối được geometry an toàn, xe không được teleport sang tuyến mới. Tính năng không bổ sung thuật toán tìm mọi tuyến thay thế hoặc điều chỉnh tín hiệu giao thông/đèn đỏ.

## Còn cần xác minh

Test PostgreSQL bản cuối và thao tác kéo tuyến bằng browser thực tế. Xem `evidence.md` cho kết quả và giới hạn môi trường.
