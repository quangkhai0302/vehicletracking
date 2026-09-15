# 007 — Research

Trạng thái: **Implemented** · Truy cập nguồn ngày 2026-09-14; quyết định đã áp dụng ở source 007.

## Nguồn sơ cấp và tác động

| Nguồn | Dữ kiện từ nguồn | Quyết định đề xuất cho 007 |
|---|---|---|
| [W3C Geolocation, accuracy và timestamp](https://www.w3.org/TR/geolocation/) | Accuracy được biểu diễn theo mét, gắn mức tin cậy; timestamp là lúc thu nhận vị trí, không phải lúc server nhận. | Giữ recordedAt/receivedAt riêng; dùng accuracy làm điều kiện chất lượng. Không cộng accuracy vào bán kính để “cho dễ đạt”. |
| [PostgreSQL 17: Constraints](https://www.postgresql.org/docs/17/ddl-constraints.html) | UNIQUE có thể áp dụng nhiều cột; FK bảo vệ quan hệ giữa bảng. CHECK không phù hợp để bảo đảm điều kiện phụ thuộc dữ liệu hàng khác. | UNIQUE(trip_id, stop_sequence), composite FK tới trip_stops; quan hệ evidence với đúng trip/source kiểm tra tại service và composite FK khi khả thi. |
| [Spring: Transaction Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html) | REQUIRED tham gia transaction bên ngoài; REQUIRES_NEW là transaction vật lý độc lập. | Check-in cùng transaction ingestion, không REQUIRES_NEW, không async ghi visit sau khi telemetry đã commit. |

**Giới hạn diễn giải W3C:** contract telemetry của dự án không chứng minh thiết bị GPS gửi accuracy đúng chuẩn Geolocation. Không tuyên bố đạt xác suất đúng 95% cho check-in và không thêm browser Geolocation vào app. Thiết bị thật cần được hiệu chuẩn riêng trước khi vận hành.

## So sánh phương án

| Phương án | Lợi ích | Rủi ro / kết luận |
|---|---|---|
| Chỉ kiểm tra điểm hiện tại | Nhỏ, dễ test | Bỏ sót đi ngang giữa hai mẫu; không đủ AC-03. |
| Xét mọi đoạn nối GPS bất kể chất lượng | Nhận nhiều giao cắt | Bịa quỹ đạo khi mất GPS hoặc nhảy vị trí; loại. |
| Xét điểm + đoạn ngắn có điều kiện | Đáp ứng đi ngang, kiểm soát suy luận | Chọn cho GPS; gap/accuracy/tốc độ cần policy và test ranh. |
| Dùng đường nối hai frame simulator | Tái sử dụng detector GPS | Đường tắt có thể khác đường cong đã chạy; không chọn khi có thể khôi phục quỹ đạo simulator. |
| Cắt geometry theo thời gian simulator đã chạy | Giữ đỉnh đường cong và occurrence ở 10× | Chọn cho SIMULATOR; mở rộng nhỏ pure RouteMotion, không thêm engine thứ hai. |
| PostGIS / provider geofencing mới | Có công cụ không gian phong phú | Chưa có nhu cầu index tìm mọi trạm; chỉ kiểm tra next stop. Không thêm extension, SDK, key hoặc quota mới. |

Các kết luận so sánh là **phân tích thiết kế**, không phải tính năng provider hay benchmark đã chạy. Nền dùng lại được có evidence tại [survey E01–E05](survey.md).

## Quy tắc hình học đề xuất

- Dùng mô hình mặt cầu với bán kính 6.371.000 m, thống nhất đơn vị với `RouteMotion.distance` hiện tại ([survey E05](survey.md)). Không tính khoảng cách trực tiếp bằng số độ lat/lon và không dùng pixel Leaflet.
- Điểm: khoảng cách tâm ≤ radius. Đoạn GPS: cung ngắn giữa hai điểm, tìm giao cắt sớm nhất với vùng tròn trên mặt cầu. Có thể giải trên vector đơn vị 3D để tránh lỗi đổi kinh tuyến ±180°/gần cực; không thêm phụ thuộc chỉ để xử lý đường ngắn.
- Với segment có đầu A, vector tiếp tuyến đơn vị U, tâm C, tham số góc θ: điểm trên cung là `A*cos(θ) + U*sin(θ)`; biên vùng thỏa `C·point = cos(radius/R)`. Giới hạn nghiệm vào đoạn thật, xử lý đoạn dài 0 và tiếp tuyến riêng. Đây là phép suy dẫn hình học của thiết kế, không phải công thức trích từ HERE.
- Trace simulator giữ các đỉnh polyline và mốc travel/dwell của khoảng thời gian đã chạy; không nối xuyên section gap bằng đường giả. Sai số hình học và mốc thời gian phải được kiểm tra bằng fixture độc lập, không chỉ dùng hàm cần test để tạo expected result.
- Gap GPS ≤15 s, khoảng cách đoạn ≤1.000 m, vận tốc suy ra ≤160 km/h và accuracy ≤min(30 m, radius/2) là **ngưỡng khởi đầu được đề xuất**. Không nguồn ngoài nào xác nhận đó là ngưỡng nghiệp vụ tối ưu. Chốt sau review, điều chỉnh sau khi có dữ liệu thiết bị.

## Chưa được xác minh

Không gọi HERE, không thử thiết bị GPS thật, không benchmark hoặc chạy implementation geofence trong lượt lập kế hoạch. Testcontainers/browser evidence lịch sử không thay cho các phép đo cần bổ sung ở [test-plan](test-plan.md).
