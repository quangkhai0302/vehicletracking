# Kế hoạch 011

## Sửa lỗi 2026-09-15: nhiều xe nhưng chỉ một tuyến

Người dùng cung cấp ảnh hai xe đang chạy nhưng chỉ thấy một polyline. Evidence: `MapComponent#plannedRoute` chỉ lấy `simulator.detail.route`; effect Render Planned Route xóa layer và vẽ duy nhất route này. GET `/telemetry/snapshot` xác nhận chuyến #2 và #3 đều RUNNING. Kiểm thử 011 trước đây kiểm tra marker/run, chưa kiểm tra hình học của nhiều tuyến khác nhau.

1. Mở rộng `useSimulationFleet` lấy route của mọi chuyến trong đội, cache theo trip/route, giữ giới hạn request và cleanup; lỗi một tuyến không làm mất tuyến khác.
2. Layer riêng vẽ đồng thời các tuyến mô phỏng, màu nhận diện theo xe, tuyến được chọn nổi bật, click/keyboard chọn xe; đoạn trùng cho chọn trong popup. Chỉ vẽ trạm đánh số của chuyến chọn để tránh chồng nhãn.
3. Xem tất cả/vừa khung bao trọn hình học các tuyến; tắt lớp tuyến hoặc rời mode dọn layer. Giữ tracking/editor và điều khiển xe độc lập.
4. Bổ sung kiểm thử hai xe chạy trên tuyến khác nhau, đổi selection, reload, trùng tuyến, lỗi/retry, mobile, bật/tắt lớp; kiểm tra GET-only app thật, lint/tsc/build; cập nhật evidence.

1. Model chọn một chuyến hiển thị cho mỗi xe (active ưu tiên, rồi scheduled sớm nhất); hook tải snapshot trạm đầu có concurrency giới hạn/cache/abort/retry.
2. Layer preview xe chờ, nhóm theo tọa độ trạm, popup chọn từng xe và fit đội xe. Marker xe đang chạy dùng luồng telemetry hiện có, bấm mở điều khiển simulator khi ở mode mô phỏng.
3. Thêm danh sách đội mô phỏng, trạng thái và liên kết cấu hình chuyến; giữ điều khiển riêng và ETA 010. Hiển thị tốc độ chờ 0/tọa độ kế hoạch khi chưa có run.
4. Test policy, browser fixture 2+ xe cùng/khác trạm, hoạt động đồng thời, pause/multiplier riêng, reload, cleanup và mobile; lint/tsc/build, cập nhật verification/progress/handoff.

Đã triển khai và chạy kiểm tra; kết quả, giới hạn và cách dùng trong [verification.md](verification.md). Browser tìm thấy marker đang chạy chồng nhau; đã bổ sung popup chọn xe gần vị trí click. Preview và danh sách tải lazy để giữ chunk chính dưới ngưỡng cảnh báo mặc định.
