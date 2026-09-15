# Requirement: Tạo tuyến đường từ danh sách điểm dừng

## 1. Thông tin feature

- **Feature ID:** 003
- **Trạng thái:** Ready for Review
- **Người lập kế hoạch:** Codex
- **Người triển khai dự kiến:** Gemini, sau khi kế hoạch được phê duyệt
- **Ngày lập:** 11/09/2026

## 2. Bối cảnh

Hệ thống hiện đã có dữ liệu trạm và giao diện quản lý trạm nhưng chưa có khái niệm tuyến đường trong database hoặc API. Người vận hành cần chọn các trạm đã có, sắp xếp chúng thành thứ tự đi qua, tính đường chạy thực tế trên mạng lưới giao thông và xem thời gian dự kiến hoàn thành tuyến.

Nếu chỉ nối tọa độ các trạm bằng đường thẳng, geometry không phản ánh đường giao thông và không thể dùng đáng tin cậy cho simulator, ETA hoặc theo dõi tiến độ sau này. Vì vậy feature phải lấy tuyến từ routing provider, trong phạm vi này là HERE Routing API v8.

## 3. Mục tiêu nghiệp vụ

1. Cho phép người vận hành tạo một tuyến từ danh sách trạm theo thứ tự xác định.
2. Phân biệt rõ trạm đầu, các trạm dừng và trạm cuối dựa trên vị trí trong danh sách.
3. Cho phép khai báo thời gian dừng dự kiến tại từng trạm trung gian.
4. Tính đường đi thực tế, quãng đường và thời gian di chuyển dự kiến bằng HERE Routing.
5. Tính thời gian dự kiến hoàn thành tuyến bằng thời gian di chuyển có xét traffic cộng thời gian dừng.
6. Lưu snapshot tuyến để có thể xem lại và tái sử dụng geometry cho simulator/trip ở feature sau.
7. Hiển thị tuyến và các điểm dừng trên bản đồ hiện tại.

## 4. Thuật ngữ

- **Route:** Tuyến đường mẫu được tạo từ một danh sách trạm có thứ tự.
- **Route stop:** Một trạm nằm trong tuyến tại một thứ tự cụ thể.
- **START:** Phần tử đầu tiên trong danh sách route stop.
- **STOP:** Các phần tử nằm giữa START và END.
- **END:** Phần tử cuối cùng trong danh sách route stop.
- **Dwell time:** Số giây xe dự kiến dừng tại một trạm trung gian.
- **Route section:** Một đoạn geometry do HERE trả về. Một chặng giữa hai trạm có thể gồm một hoặc nhiều section.
- **Estimated travel duration:** Tổng thời gian xe di chuyển do HERE tính với dữ liệu time-aware/traffic.
- **Estimated trip duration:** Estimated travel duration cộng dwell time tại các trạm trung gian.
- **Snapshot:** Dữ liệu route, stop và geometry tại thời điểm tính tuyến; không tự thay đổi khi trạm được sửa sau đó.

## 5. Phạm vi

### 5.1 In scope

- Thêm schema `routes`, `route_stops` và `route_sections` bằng Flyway migration mới.
- Tạo route bằng tên tuyến và danh sách 2–50 stop có thứ tự.
- Chỉ cho phép sử dụng station đang hoạt động.
- Cho phép một station xuất hiện lại ở vị trí không liền kề, hỗ trợ tuyến vòng có START và END cùng station.
- Không cho phép hai stop liền kề trỏ tới cùng một station.
- Dwell time từ 0–3600 giây cho stop trung gian; START và END bắt buộc bằng 0.
- Dùng transport mode `car` và routing mode `fast` trong phiên bản đầu.
- Backend gọi HERE Routing API v8; frontend không nhận hoặc sử dụng HERE API key.
- Tính tuyến theo thứ tự stop người dùng đã chọn, không thay đổi thứ tự đó.
- Lưu route snapshot chỉ khi HERE trả về kết quả hợp lệ.
- API tạo route, lấy danh sách route và xem chi tiết route.
- Workspace “Tuyến đường” trên frontend để tạo, chọn và xem route.
- Vẽ geometry thật từ flexible polyline lên Leaflet và fit map theo tuyến được chọn.
- Hiển thị tổng quãng đường, thời gian di chuyển, thời gian dừng và thời gian hoàn thành dự kiến.
- Hiển thị khoảng cách/thời gian từ stop trước và thời gian lũy kế đến từng stop.
- Loading, empty state, validation và lỗi provider rõ ràng.

### 5.2 Out of scope

- Sửa, đổi tên, tính lại, xóa hoặc ngừng sử dụng route.
- Tự động tối ưu thứ tự điểm dừng.
- Route alternatives và cho người dùng chọn phương án thay thế.
- Gán vehicle hoặc driver vào route.
- Tạo và vận hành trip/lịch trình theo giờ cụ thể.
- Simulator, telemetry, WebSocket và vehicle movement.
- Automatic check-in/geofence processing.
- ETA realtime của xe đang chạy.
- Traffic incident overlay, rerouting và thông báo thay đổi lịch trình.
- Xác thực, phân quyền và audit người tạo.
- Turn-by-turn instruction.

## 6. Actor và luồng chính

### 6.1 Actor

- **Người vận hành:** Quản lý danh sách tuyến của hệ thống.
- **HERE Routing:** Nhà cung cấp route geometry và travel duration.

### 6.2 Luồng tạo tuyến

1. Người vận hành mở workspace “Tuyến đường”.
2. Hệ thống tải danh sách route đã tạo và danh sách station đang hoạt động.
3. Người vận hành chọn “Tạo tuyến mới”.
4. Người vận hành nhập tên tuyến, thêm ít nhất hai station và sắp xếp đúng thứ tự.
5. Người vận hành nhập dwell time cho các stop trung gian nếu cần.
6. UI xác định START, STOP, END theo vị trí nhưng không ghi vai trò vào station.
7. Người vận hành nhấn “Tạo tuyến”.
8. Backend kiểm tra request và trạng thái station.
9. Backend gửi origin, via và destination tới HERE theo đúng thứ tự.
10. Khi HERE trả về route hợp lệ, backend chuẩn hóa sections, tính tổng và lưu snapshot trong một transaction.
11. Frontend nhận route detail, chọn route mới, vẽ geometry và hiển thị số liệu.

### 6.3 Luồng lỗi

- Input không hợp lệ: UI hiển thị validation; backend vẫn từ chối bằng HTTP 400.
- Station không tồn tại hoặc đã inactive: backend trả HTTP 422; không gọi hoặc không lưu route.
- HERE Routing chưa bật/chưa cấu hình: backend trả HTTP 503; các API đọc route đã lưu vẫn hoạt động.
- HERE không tìm được đường hoặc trả critical notice: backend trả HTTP 422; không lưu dữ liệu một phần.
- HERE timeout: backend trả HTTP 504.
- HERE trả lỗi/malformed response khác: backend trả HTTP 502 hoặc 503 tùy nhóm lỗi; không lộ key hoặc raw response nhạy cảm.

## 7. Functional requirements

- **FR-01:** Route name bắt buộc, được trim và tối đa 150 ký tự.
- **FR-02:** Request phải có từ 2 đến 50 stop.
- **FR-03:** Mỗi stop phải tham chiếu station ID dương và station active.
- **FR-04:** Thứ tự stop trong request là thứ tự phải đi qua; backend không tự tối ưu.
- **FR-05:** Hai stop liên tiếp không được trùng station ID; trùng không liên tiếp được phép.
- **FR-06:** START là stop đầu, END là stop cuối, các stop còn lại là STOP; vai trò được suy ra, không phải thuộc tính của station.
- **FR-07:** START và END có dwell time bằng 0; STOP có dwell time 0–3600 giây.
- **FR-08:** HERE request phải dùng WGS84 coordinates snapshot từ station và gửi các via theo đúng thứ tự.
- **FR-09:** Kết quả phải chứa ít nhất một route hợp lệ, section geometry và summary/travel summary cần thiết.
- **FR-10:** Mỗi HERE section phải được ánh xạ vào chặng kết thúc tại một route stop cụ thể.
- **FR-11:** Tổng distance/travel duration được cộng từ sections; trip duration bằng travel duration cộng dwell time trung gian.
- **FR-12:** Không lưu route nếu validation hoặc provider thất bại.
- **FR-13:** `POST /api/v1/routes` trả HTTP 201, `Location` và route detail.
- **FR-14:** `GET /api/v1/routes` trả summary, không mang toàn bộ polyline.
- **FR-15:** `GET /api/v1/routes/{id}` trả stop/section detail cần để vẽ bản đồ.
- **FR-16:** UI hỗ trợ thêm, bỏ và reorder stop mà không cần dependency drag-and-drop mới.
- **FR-17:** UI không vẽ đường thẳng thay thế khi provider không có geometry.

## 8. Non-functional requirements

- **NFR-01 — Security:** HERE key chỉ đọc từ biến môi trường backend, không nằm trong source, response, log hoặc biến `VITE_*`.
- **NFR-02 — Reliability:** Gọi provider phải có connect/read timeout; provider lỗi không để lại route dở dang.
- **NFR-03 — Database:** Flyway là nguồn schema; giữ `ddl-auto=validate`; không sửa V1/V2.
- **NFR-04 — Maintainability:** Backend tổ chức theo feature `route`; API không trả JPA entity.
- **NFR-05 — Testability:** Business service phụ thuộc interface routing provider để test không gọi HERE thật.
- **NFR-06 — Performance:** API list không trả polyline; giới hạn 50 stops/request; tránh N+1 khi đọc route detail.
- **NFR-07 — UX:** Map vẫn là workspace chính; panel/drawer compact theo `docs/design.md`.
- **NFR-08 — Compatibility:** Chức năng station và tracking hiện tại tiếp tục hoạt động khi HERE Routing bị tắt.
- **NFR-09 — Data consistency:** Snapshot station và route sections phải được lưu atomically sau provider success.

## 9. Acceptance criteria

| ID | Tiêu chí nghiệm thu |
|---|---|
| AC-01 | Migration mới tạo đúng ba bảng, constraint, foreign key và index; JPA `validate` thành công. |
| AC-02 | Người dùng có thể chọn 2–50 station active, thêm/bỏ/reorder và thấy vai trò START/STOP/END đúng. |
| AC-03 | Dwell time chỉ hợp lệ 0–3600 giây ở stop trung gian; START/END luôn bằng 0. |
| AC-04 | Backend gửi origin/via/destination tới HERE đúng thứ tự, dùng `car`, `fast`, `polyline`, `summary` và `travelSummary`. |
| AC-05 | HERE API key không xuất hiện trong frontend, API response, log hoặc tài liệu evidence. |
| AC-06 | Route chỉ được lưu sau HERE success; provider fail không tạo row ở bất kỳ bảng route nào. |
| AC-07 | POST route trả 201 và detail gồm totals, ordered stops, cumulative ETA offsets và sections. |
| AC-08 | Tổng distance/travel/trip duration khớp sections và dwell time, kể cả chặng có nhiều HERE sections. |
| AC-09 | GET list trả summary gọn; GET detail trả đúng route hoặc 404. |
| AC-10 | Station thiếu/inactive, consecutive duplicate và request sai trả lỗi có kiểm soát, không gây HTTP 500. |
| AC-11 | Routing disabled trả 503 cho create nhưng API station và API đọc route vẫn dùng được. |
| AC-12 | Route được chọn hiển thị geometry thật và numbered stops trên Leaflet; map fit theo bounds của route. |
| AC-13 | UI có loading, empty, provider error và submit-disabled state; không fallback sang đường chim bay. |
| AC-14 | Backend test chạy bằng JDK 26; frontend lint, typecheck và production build thành công. |
| AC-15 | Evidence và walkthrough phản ánh kết quả thật; Codex review implementation trước khi feature được đánh dấu Reviewed. |

## 10. Giả định và quyết định cần người review xác nhận

1. Phiên bản đầu dùng `transportMode=car`; chưa mô hình hóa kích thước/giới hạn của bus.
2. Route là snapshot bất biến. Khi muốn thay đổi danh sách stop, người dùng tạo route mới; edit/delete để feature sau.
3. Tính traffic theo thời điểm gửi request tạo route; đây không phải ETA realtime của một trip đang chạy.
4. Giới hạn 50 stops là giới hạn ứng dụng để bảo vệ request và UI, không được mô tả như giới hạn chính thức của HERE.
5. Dwell time được gửi vào via waypoint để HERE dự phóng traffic cho các section sau, đồng thời backend tự tính tổng trip duration từ travel duration và dwell time để contract rõ ràng.
