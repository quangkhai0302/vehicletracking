# Research

Ngày truy cập: 2026-09-15. Nguồn chính thức; các lựa chọn thiết kế bên dưới là đề xuất, không phải khả năng đã có trong repository.

| Nguồn | Dữ kiện và tác động |
|---|---|
| [Traffic-aware polylines](https://developers.google.com/maps/documentation/routes/traffic_on_polylines) | Google có encoded polyline riêng và HIGH_QUALITY. Traffic trả category, không phải km/h; có ở route/leg, không ở step. Request cần TRAFFIC_ON_POLYLINE, mode DRIVE/TWO_WHEELER và preference traffic-aware. Interval index gắn đúng polyline; start bị bỏ nghĩa là 0. Không đơn giản hóa geometry trước khi dùng index. |
| [Compute Routes reference](https://developers.google.com/maps/documentation/routes/reference/rest/v2/TopLevel/computeRoutes) | POST directions/v2:computeRoutes, field mask; leg có duration và staticDuration. Departure trong quá khứ chỉ hợp lệ cho TRANSIT. Không lấy thời gian simulator đã tăng tốc làm thời điểm traffic thực. API không nhận một arbitrary polyline để trả ETA chính xác trên polyline đó. |
| [Intermediate waypoints](https://developers.google.com/maps/documentation/routes/intermed_waypoints) | Tối đa 25 intermediates/request. Trạm dừng chia leg; via không chia leg. Phải tính cả trạm lẫn shaping points khi phân lô. |
| [Alternative routes](https://developers.google.com/maps/documentation/routes/alternative-routes) | Có thể yêu cầu tối đa 3 phương án ngoài mặc định, nhưng không hỗ trợ khi request có intermediates; có thể không có alternative. Tuyến nhiều trạm cần chiến lược theo chặng, không bật cờ alternatives trên request toàn tuyến. |
| [Two-wheeler](https://developers.google.com/maps/documentation/routes/route_two_wheel), [coverage](https://developers.google.com/maps/documentation/routes/coverage-two-wheeled) | Có mode TWO_WHEELER và Việt Nam được liệt kê; cần tuân thủ cảnh báo beta trong tài liệu. Icon xe máy không tự biến tuyến DRIVE thành tuyến xe máy. |
| [Routes usage/billing](https://developers.google.com/maps/documentation/routes/usage-and-billing) | Cần billing; SKU/quota phụ thuộc tùy chọn. Traffic-aware polyline, mode và số waypoint ảnh hưởng chi phí. Không giả định gói miễn phí đủ cho scheduler. |
| [Routes policies](https://developers.google.com/maps/documentation/routes/policies), [Service-specific terms §19](https://cloud.google.com/maps-platform/terms/maps-service-terms) | Khi hiển thị route trên map phải dùng Google map. Quyền cache tọa độ có giới hạn 30 ngày theo §19.3; không suy rộng thành quyền lưu toàn bộ response/ETA/traffic 30 ngày. Chính sách yêu cầu Terms/Privacy thích hợp. Xác minh hợp đồng áp dụng trước khi thiết kế persistence Google lâu dài. |
| [Map Tiles 2D](https://developers.google.com/maps/documentation/tile/2d-tiles-overview), [Map Tiles policies](https://developers.google.com/maps/documentation/tile/policies) | API tile có session, map theme và viewport attribution; hỗ trợ renderer bên thứ ba theo chính sách. Phải giữ logo/attribution, tôn trọng cache headers. Không mặc định URL google.com/vt hiện tại là integration Google Maps Platform được hỗ trợ. |
| [HERE Traffic API](https://www.here.com/docs/bundle/traffic-api-v7-api-reference/page/index.html) | Trang trả HTTP 403 khi truy cập trong lượt này; chưa xác minh nội dung từ trang. Cần đọc lại schema/quyền tài khoản ở P0. Source adapter hiện tại được khảo sát riêng ở survey; không coi việc đang gọi được API là bằng chứng đã có quyền mọi cách kết hợp/lưu trữ. |

## Kết luận thiết kế

1. Chuẩn hóa geometry cùng thứ tự điểm; không đưa chuỗi Google vào decoder HERE. Giữ encoding tường minh để backfill legacy an toàn.
2. Tính ETA phải kiểm tra hình học kết quả vẫn tương ứng tuyến đang áp dụng. Nếu Google chọn đường mới, đó là ứng viên reroute, không phải refresh ETA trên đường cũ.
3. HERE closure không tự trở thành một avoid-polygon trong Google Routes. Phải lọc ứng viên giao cắt vùng đóng; không có ứng viên thì báo unavailable.
4. Có thể giữ Leaflet với Map Tiles chính thức; Google Maps JavaScript API là lựa chọn khác nếu cần Google TrafficLayer, ngoài phạm vi mặc định.
5. Không gộp hai nhà cung cấp thành một nhãn LIVE. Thời điểm Google nhận response khác với HERE observedAt; không bịa timestamp quan sát của Google.
6. Retention là quyết định ảnh hưởng schema: tọa độ được cache có thời hạn không đồng nghĩa metrics, bản sao trong trip/revision, hoặc đường mô phỏng được lưu vĩnh viễn. P0 phải lập ma trận field → cơ sở cho phép → TTL → xử lý hết hạn, bao gồm backup/export.

## Chưa xác minh bằng live request

- Quota/billing thực tế, quyền API và điều khoản của tài khoản người dùng.
- Khả năng giữ đường đã kéo bằng via trong các ca đường song song, cầu vượt ở TP.HCM.
- Độ phủ HERE trên Google geometry; ngưỡng matching cần hiệu chỉnh bằng mẫu thực.
- Thời lượng/lưu lượng với tuyến 50 trạm. Không hứa chi phí cụ thể hoặc line trùng Google ở mọi mức zoom.
