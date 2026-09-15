# Feature 018 — Google Routes kết hợp HERE Traffic

Trạng thái: **Approved / Implementing**. Ngày: 2026-09-15. Người dùng đã yêu cầu triển khai Feature 018; kết quả và giới hạn xác minh nằm trong [evidence](evidence.md).

## Bối cảnh và mục tiêu

Người vận hành muốn tuyến vẽ phù hợp nền Google, ETA và đường thay thế do Google tính, đồng thời giữ tốc độ dòng xe, jamFactor và sự cố của HERE. Hệ thống hiện phụ thuộc HERE ở tuyến và ETA; xem [survey](survey.md). Đây là chuyển đổi xuyên backend/frontend/database, không chỉ thêm một HTTP client.

## Phạm vi

- Google: nền bản đồ qua API chính thức, hình học tuyến, ETA traffic, phương án thay thế, màu traffic của tuyến được chọn.
- HERE: Flow theo khu vực/hành lang, tốc độ và jamFactor để phân tích, Incidents để cảnh báo và kiểm tra ứng viên đổi tuyến.
- Giữ Leaflet, bố cục quản lý, kéo chỉnh tuyến, check-in, chọn xe, mô phỏng nhiều xe, phát lại theo attempt.
- Provider ghi rõ theo từng tuyến/chuyến. Tuyến HERE cũ tiếp tục hoạt động; chuyển tuyến cũ sang Google bằng bản sao có chủ đích.
- Cấu hình quota, lỗi/fallback, nguồn/thời điểm dữ liệu, kế hoạch lưu trữ và kiểm thử.

Ngoài phạm vi: thay toàn bộ UI bằng Google Maps JavaScript API; Google TrafficLayer; GPS phần cứng; deploy AWS; thanh toán/tài khoản người dùng; tối ưu thứ tự trạm; cam kết vận tốc mô phỏng giống một xe thật; tự động chuyển tất cả dữ liệu HERE sang Google.

## Actor và luồng

- Người lập tuyến: chọn trạm và chế độ phương tiện → tính Google → kéo điểm dẫn đường → xem trước → lưu hoặc lưu bản sao.
- Điều hành: tạo chuyến → chọn xe → xem tuyến/ETA Google và thông tin HERE có nhãn riêng.
- Simulator: cập nhật vị trí theo tuyến đang áp dụng, check-in đúng thứ tự; nhận revision khả dụng khi đổi tuyến.
- Hệ thống: cập nhật traffic theo chu kỳ thực, đánh giá trễ/đóng đường, tìm ứng viên Google, chỉ áp dụng sau kiểm tra nối tuyến và các trạm bắt buộc.

## Acceptance criteria

| AC | Yêu cầu kiểm chứng |
|---|---|
| AC01 | Tuyến mới Google có provider/mode rõ; tuyến HERE cũ vẫn đọc/chạy; cấu hình sai không âm thầm đổi nhà cung cấp. |
| AC02 | Google polyline được giải mã đúng; vẽ, matching, simulator, kéo tuyến và check-in dùng cùng geometry/version; không nối thẳng hai section rời nhau. |
| AC03 | Giữ 2–50 trạm, thứ tự occurrence kể cả A→B→A, tối đa 20 điểm dẫn đường; chia request theo giới hạn Google, không bỏ trạm hay cộng dwell hai lần. |
| AC04 | ETA chính của tuyến Google lấy Google, gồm dwell còn lại; có source/time/geometry identity; HERE không cộng thêm độ trễ vào ETA này. |
| AC05 | NORMAL/SLOW/TRAFFIC_JAM tô đúng interval của polyline tương ứng; thiếu traffic là UNKNOWN, không suy thành thông thoáng. |
| AC06 | HERE Flow/Incidents còn dùng được, ghép theo geometry/hướng/thời hạn; đường song song và sự cố ngoài tuyến không tự chặn chuyến. |
| AC07 | Trễ vượt ngưỡng hoặc closure có thể kích hoạt tính lại bằng Google; phương án giữ các trạm, tôn trọng điểm dẫn đường bắt buộc, không teleport và không lặp cảnh báo. |
| AC08 | Simulator có profile thời gian theo Google và nhãn tốc độ ước tính; 1×/5×/10× không nhân số km/h; mượt, pause/reset/check-in/final marker không hồi quy. |
| AC09 | Backend gộp request đồng thời và giới hạn chu kỳ/ngân sách; tick/SSE/hover không phát sinh một Google request mỗi giây. |
| AC10 | Không lộ key; nền Google được nạp qua API chính thức, đủ attribution; chỉ bật production khi chốt quyền dùng/lưu Google và HERE cho kiến trúc kết hợp. |
| AC11 | Migration tiến tới từ DB hiện có, không sửa V1–V11; lỗi provider không làm hỏng tuyến đã lưu, dữ liệu GPS/check-in hoặc trạng thái simulator. |
| AC12 | UI phân biệt lỗi/đang tải/dữ liệu cũ/nguồn; đổi xe hoặc revision không hiển thị response của chuyến trước; hướng dẫn sử dụng cập nhật. |

## Yêu cầu phi chức năng

- Giữ bí mật API key ở backend; giới hạn đầu vào và số request outbound, không nhận URL upstream tùy ý.
- HTTP provider nằm ngoài transaction khóa chuyến; kiểm tra version khi ghi kết quả.
- Theo dõi latency, số request, cache hit, quota và lý do từ chối reroute; không log raw payload/key.
- Test fixture có thể chạy không cần tài khoản Google; nghiệm thu live được ghi riêng và cần ngân sách được chấp thuận.

## Giả định và điểm cần review

1. Đề xuất giữ Leaflet, thay URL tile hiện tại bằng Google Map Tiles API chính thức. Đây là hạng mục bổ sung cần thiết trong kế hoạch phát hành, có thể phát sinh phí bản đồ.
2. Google là nguồn thời lượng cho simulator tuyến Google; tốc độ HERE được hiển thị phân tích riêng. Không coi category Google là tốc độ km/h thực đo.
3. Cần xác nhận phạm vi lưu dữ liệu Google, đặc biệt replay lâu dài, metrics lịch trình và telemetry mô phỏng suy ra từ geometry. Không cam kết replay Google vĩnh viễn khi chưa có cơ sở cho quyền lưu.
4. Chưa có thông tin billing/quota Google và điều khoản tài khoản HERE; P0 của [plan](plan.md) phải đóng các điểm này trước tích hợp production. Không cần cung cấp key trong chat.
