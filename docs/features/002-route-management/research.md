# Research — 002-route-management

| Thuộc tính | Giá trị |
| --- | --- |
| Feature ID | 002-route-management |
| Trạng thái | READY FOR REVIEW |
| Ngày khảo sát | 2026-09-03 |

## Câu hỏi cần nghiên cứu

### RS-001 — API nào phù hợp để thay thế thứ tự trạm của một tuyến?

**Nguồn bên ngoài:** [RFC 9110 — HTTP Semantics, mục PUT](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.4), đã mở kiểm tra ngày 2026-09-03.

RFC mô tả `PUT` yêu cầu resource đích được tạo hoặc thay bằng trạng thái của representation gửi tới. Danh sách trạm có thứ tự là một phần nhất quán của trạng thái tuyến, nên một `PUT /api/routes/{id}` với toàn bộ `stationIds` theo thứ tự là contract rõ ràng hơn nhiều thao tác đổi vị trí riêng lẻ.

**EVD-001 — RFC cho PUT và Conflict**

- **Claim:** `PUT` phù hợp để cập nhật toàn bộ trạng thái có thể sửa của route; `409 Conflict` phù hợp khi request xung đột với trạng thái resource/server.
- **Liên kết:** REQ-003, REQ-004, SPEC-004, SPEC-005.
- **Loại Evidence:** EXTERNAL_SOURCE.
- **Nguồn:** RFC 9110, [§9.3.4 PUT](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.4) và [§15.5.10 Conflict](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.5.10).
- **Cách kiểm chứng:** Mở trực tiếp hai liên kết RFC.
- **Kết quả:** RFC nêu semantics thay thế trạng thái với PUT và mã 409 khi request xung đột với trạng thái hiện tại.
- **Trạng thái:** PASS.
- **Ghi chú:** RFC chỉ xác nhận semantics HTTP; quy tắc START/END và chặn route đã có Trip là quyết định sản phẩm dựa trên Survey, không phải nội dung của RFC.

## Phương án và đánh đổi

### DEC-001 — Cập nhật toàn bộ route bằng PUT

| Phương án | Ưu điểm | Nhược điểm | Quyết định |
| --- | --- | --- | --- |
| `PUT /api/routes/{id}` chứa metadata và toàn bộ `stationIds` có thứ tự | Atomic, dễ kiểm tra START/END và tính lại metric, đơn giản cho UI nút lên/xuống | Client gửi lại toàn bộ danh sách | **Chọn** |
| Nhiều endpoint chèn/xóa/đổi vị trí từng trạm | Có thể giảm payload cho mỗi thao tác | Dễ tạo thứ tự trung gian không hợp lệ, nhiều round-trip và khó rollback | Không chọn |
| Version hóa route mỗi lần sửa | Bảo toàn lịch sử route | Cần schema, UX/chọn version, quy tắc gán Trip mới; vượt phạm vi | Không chọn trong feature 002 |

### DEC-002 — Chặn sửa/xóa route đã được Trip tham chiếu

| Phương án | Ưu điểm | Nhược điểm | Quyết định |
| --- | --- | --- | --- |
| Trả `409` khi tồn tại Trip tham chiếu | Không làm lệch check-in/lịch đã tạo và tránh lỗi khóa ngoại khi xóa | Muốn đổi tuyến phải tạo tuyến mới | **Chọn** |
| Cho sửa route đang được dùng | Ít thao tác hơn với người dùng | Làm khác thứ tự route so với check-in/lịch của Trip hiện hữu | Không chọn |
| Xóa cascade toàn bộ Trip | Xóa được route | Mất dữ liệu nghiệp vụ, trái yêu cầu an toàn | Không chọn |

Quyết định DEC-002 dựa trên evidence repository EVD-008 trong `survey.md`.

### DEC-003 — Sắp xếp bằng nút thay vì thêm thư viện kéo-thả

| Phương án | Ưu điểm | Nhược điểm | Quyết định |
| --- | --- | --- | --- |
| Thêm/xóa/di chuyển lên/xuống trong RouteModal | Không thêm dependency, có thể kiểm chứng rõ thứ tự | Nhiều click với danh sách rất dài | **Chọn** |
| Kéo-thả | Trực quan với danh sách dài | Dependency/khả năng truy cập và test phức tạp hơn | Không chọn trong feature 002 |

## Kết luận Research

- Contract được đề xuất: `PUT /api/routes/{id}` thay thế metadata được phép sửa và toàn bộ thứ tự trạm, trả route đã tính lại.
- `409 Conflict` được dùng cho mã route trùng hoặc route có Trip tham chiếu; `400` dành cho danh sách/loại trạm không hợp lệ; `404` dành cho ID route/trạm không tồn tại.
- Không cần nguồn bên ngoài cho thuật toán khoảng cách/thời lượng: feature giữ cách tính hiện có của repository (Survey EVD-004), không tuyên bố đó là ETA giao thông thực tế.