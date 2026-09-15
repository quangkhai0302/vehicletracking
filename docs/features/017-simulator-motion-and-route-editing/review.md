# Self-review

Không phải review độc lập. Kết luận: **Request changes / pending verification** trước khi đánh dấu Verified; triển khai source đã có nhưng còn cần các kiểm tra dưới đây.

- **High — khoảng trống kiểm thử persistence:** `RerouteSimulationIntegrationTest`, `RouteRepositoryIntegrationTest` bản cuối chưa chạy được do sandbox chặn Docker. Cần chạy ngoài sandbox để chứng minh V11, cập nhật collection không xung đột, check-in sau detour và replay trên transaction thật.
- **Medium — khoảng trống UX:** `RouteShapeEditor.tsx` và `useVehicleMarkers.ts` chưa được thử thủ công trên browser; test thuần không chứng minh độ mượt, theo xe, drag/touch và cleanup khi chuyển màn hình.
- **Low — giới hạn hiển thị:** buffer cố định 1,5 giây; mạng có khoảng trống dài hơn vẫn phải giữ xe ở mẫu cuối. Không nội suy vô hạn để che mất kết nối.

Các điểm đã sửa trong self-review: bỏ assertion sai rằng ETA/progress phải giữ nguyên sau detour; giữ nguyên tọa độ/vận tốc prefix; từ chối revision bỏ trạm; ETA đọc tuyến gốc không bắt buộc geometry mô phỏng; không loại trạm vẫn đang đi tới chỉ vì check-in sớm; footer xuống dòng trên panel hẹp.

AC1/AC2/AC3/AC5 có test logic/typecheck/build tương ứng; AC4 có test thuần, còn pending DB/browser. Không tuyên bố feature Verified cho đến khi hoàn tất kiểm tra tích hợp và thao tác thực tế.
