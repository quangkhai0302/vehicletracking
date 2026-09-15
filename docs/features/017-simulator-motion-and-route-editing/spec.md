# Spec

Tốc độ: flow hợp lệ tại xe chuyển thành tỷ lệ so với tốc độ geometry free-flow; bỏ trần tỷ lệ. Dwell không bị traffic làm kéo dài.

Hiển thị: bộ đệm 1,5 giây, nội suy theo timestamp/geometry; trùng event không reset. Pause/replay snap có kiểm soát. Ngừng ở điểm cuối nếu hết mẫu.

Manual API: POST /api/v1/routes/{id}/shape/preview; PUT /api/v1/routes/{id}/shape; POST /api/v1/routes/{id}/shape/copy. Request points[{destinationStopSequence,latitude,longitude}], tối đa 20, đúng thứ tự từng chặng và bounds. Response RouteDetailResponse có shapingPoints. Preview không ghi; PUT chặn tuyến đã dùng; copy tạo tuyến mới qua hành động riêng. Provider lỗi không thay tuyến đã lưu.

DB: route_shape_points (route_id, point_order, destination_stop_sequence, latitude, longitude); revision bổ sung simulation_start_elapsed và simulation_attempt_number để tái dựng tuyến đã áp dụng theo lần chạy.

Auto: áp dụng revision mới tại mốc elapsed hiện tại, giữ prefix cũ; từ chối geometry không nối được vị trí hiện tại. Khôi phục sau restart bằng các revision đã áp dụng của attempt. Replay trở lại tuyến gốc. ETA và API tuyến đang chạy dùng cùng snapshot đã nối. Không đổi route dùng chung.

UI: trong chi tiết tuyến mở Chỉnh đường đi trên bản đồ; thêm điểm trên đoạn, kéo marker, tính lại, lưu/bản sao, hủy. Thông báo lỗi/loading và không lưu khi preview chưa đúng bản nháp.

