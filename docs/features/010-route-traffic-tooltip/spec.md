# 010 — Thông tin tuyến khi hover

## Điều chỉnh phạm vi mới nhất (2026-09-14)

Người dùng rút yêu cầu xem thông số tất cả đường nền, yêu cầu tập trung vị trí xe, vận tốc và thời gian tới trạm theo giao thông. Phần mở rộng AC8–10 ở dưới đã bị thay thế: gỡ `RoadInspectionLayer` và tải flow theo viewport; giữ marker xe, lớp sự cố và tooltip tuyến đã chọn. Không đổi thuật toán backend trong lượt chỉnh UI này.

Nghiệm thu thay thế: (A) map không tạo hit path/tải flow cho mọi đường; (B) chọn xe hoặc chuyến hiển thị tốc độ từ telemetry/frame, trạm tiếp theo, ETA và nguồn; (C) liệt kê ảnh hưởng từ `TripEta.affectedSegments` như ùn tắc/tai nạn/thi công, không suy ra incident từ tốc độ; (D) blocked không fallback thành ETA hữu hạn, stale/lỗi/phương án tuyến đã lưu có nhãn; (E) thông số chuyến đặt trước playback, nút định vị xe và kiểm tra browser desktop/mobile, lint/tsc/build. Evidence có sẵn: `SimulationService#tick/describe` điều chỉnh tốc độ theo traffic; `TrafficEtaService#calculate` trả ETA/ảnh hưởng; `useVehicleMarkers` nhận SSE positions; `SimulatorPanel` đã dùng `useTripEta` nhưng đang ưu tiên số countdown trước trạng thái blocked.

Người dùng đã yêu cầu trực tiếp triển khai tooltip sau khi thống nhất các thông số tuyến/traffic. Sau bản đầu chỉ dành cho tuyến đã lưu, người dùng làm rõ cần hover cả đường có sẵn trên bản đồ. Phạm vi cập nhật: tuyến đã lưu và các đoạn đường được HERE cung cấp geometry/flow trong viewport, không cần chọn hoặc tạo tuyến. Không thêm vận tốc tùy chỉnh simulator.

## Khảo sát và evidence

- `AGENTS.md` yêu cầu đọc `docs/workflow.md`; file không tồn tại khi tìm cả hidden. Dùng hồ sơ spec/plan/verification này và quyền triển khai trực tiếp. Mã 007–009 đã được sử dụng trong `docs/PROJECT_PROGRESS.md`, dù thư mục tương ứng đang thiếu; chọn ID tiếp theo 010.
- `MapComponent.tsx`, effect Render Planned Route: các đường đều `interactive:false`, stop markers đã có tooltip. Không thay pipeline lưu tuyến hoặc camera.
- `types/route.ts#RouteSection/RouteDetail`: có geometry, chiều dài, thời gian snapshot và thời điểm tính tuyến.
- `services/hereTraffic.ts#fetchHereTrafficFlow/fetchHereIncidents` và backend `traffic/controller/TrafficController`: đã có HTTP bbox qua backend; không cần key/frontend hay schema mới.
- `traffic/TrafficFlowSegment.java` có chiều dài, tốc độ, free flow, jam, traversability và confidence; `TrafficIncident.java` có thời gian hiệu lực. `useTraffic` hiện chỉ tải incidents cho lớp map.

## Acceptance criteria

1. Hover tuyến đã vẽ hiển thị tên tuyến, chặng theo thứ tự stop, số liệu chặng đã lưu và thời điểm tính. Không gán thời gian snapshot là traffic live.
2. Lấy flow/incidents trong vùng nhỏ quanh vị trí hover; reuse khi di chuột cùng vùng, refresh khi đang mở, abort/ignore kết quả cũ khi đổi vùng/tuyến/ẩn layer. Không request theo từng mousemove.
3. Chọn flow gần vị trí trên tuyến và cùng hướng cục bộ; loại ngược chiều, giao cắt vuông góc, đoạn xa, geometry thiếu. Thông số traffic và ước tính đi qua chỉ áp dụng đoạn flow khớp, không áp dụng một vận tốc cho toàn chặng.
4. Hiển thị tốc độ dòng xe, free flow (không phải tốc độ giới hạn), trạng thái, chiều dài đoạn traffic, thời gian ước tính và chậm thêm khi đủ dữ liệu. Zero speed/closure không hiển thị thời gian hữu hạn sai.
5. Nguồn và tuổi flow/incidents tách riêng; loading, unavailable/no-match, stale và failure đều có nhãn. Sự cố gần vị trí chỉ hiển thị khi còn hiệu lực; không kết luận sự cố cùng phía đường nếu API không đủ hướng.
6. Hover gọn, click/tap hoặc Enter ghim, Esc/close đóng, keyboard focus được trên từng chặng; card nằm trong viewport 320 px. Pan/zoom/ẩn layer/đổi tuyến đóng nội dung cũ. Không chặn chọn/kéo trạm hoặc camera.
7. Dữ liệu chuỗi được React escape; cleanup layer, listener, request và timer. Không sửa backend/.env, không tạo traffic giả trong luồng ứng dụng.
8. Khi bật Giao thông trực tiếp, tự tải flow cùng incidents theo vùng bản đồ. Các đoạn có dữ liệu có màu và nhận hover/tap kể cả khi chưa có tuyến được chọn. Tắt lớp thì bỏ các hit paths/card tương ứng; lỗi flow không xóa dữ liệu incidents thành công và ngược lại.
9. Tooltip đường nền có tên, hướng cục bộ từ geometry, tốc độ, trạng thái/độ dài/thời gian/chậm thêm, nguồn/tuổi và sự cố gần vị trí. Không gán tên tuyến/chặng khi không có tuyến. Hình học flow là phạm vi đo trực tiếp, không cần suy khớp với route.
10. Tuyến đã chọn/marker giữ ưu tiên tương tác. Chỉ một card hiện khi chuyển từ đường nền sang tuyến; card ghim đường cập nhật cùng response mới và đóng nếu đoạn mất khỏi dữ liệu. Bản đồ nền raster tự nó không cung cấp geometry cho đường chưa có flow: thông báo coverage/empty rõ, không tạo số liệu giả.

Giới hạn: khớp geometry/hướng là suy luận, không có road-link ID chung để bảo đảm phân biệt tuyệt đối đường song song/cầu tầng. Tooltip ghi phạm vi và ước tính; chỉ HERE có coverage mới hiện số liệu. Browser fixture không thay nghiệm thu HERE live.
