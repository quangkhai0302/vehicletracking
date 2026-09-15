# 007 — Check-in tự động theo lần ghé trạm

Trạng thái: **Implementing** · Ngày cập nhật: 2026-09-14 · Đã được triển khai theo yêu cầu trực tiếp của người dùng.

## Bối cảnh và mục tiêu

Theo [PROJECT_PROGRESS](../../PROJECT_PROGRESS.md#007--check-in-tự-động) và [SESSION_HANDOFF](../../SESSION_HANDOFF.md), sau telemetry/simulator là ghi nhận xe đi qua trạm. Người điều hành cần biết **đã ghi nhận lần ghé nào, lúc nào, từ nguồn nào**, không nhầm lịch kế hoạch với thực tế.

Source nền đã có ingestion, trip stop snapshot và SSE, nhưng chưa có visit persistence hoặc xử lý geofence: [survey E01–E07](survey.md). Đây là kết luận từ source, không phải xác nhận chạy thành công toàn hệ thống.

**Phụ thuộc:** 007 dùng telemetry accepted và snapshot trip stop của 005–006. HERE Traffic/ETA thuộc 008, không phải điều kiện chạy check-in. Phần backend core đã có; nghiệm thu browser/edge cases còn được theo dõi trong [evidence](evidence.md).

## Actor và luồng chính

Người điều hành chọn chuyến đã có xe/tuyến → bắt đầu chuyến hoặc phát simulator → backend nhận vị trí hợp lệ → nhận diện lần ghé tiếp theo → lưu check-in → hai trình duyệt thấy cùng kết quả → tải lại vẫn có lịch sử.

## Phạm vi

**Trong phạm vi:** GPS và SIMULATOR qua backend; điểm đầu/trung gian/cuối theo thứ tự; đi ngang không cần dừng; chống ghi lặp; timestamp và evidence; lịch sử check-in theo trip; cập nhật timeline và tóm tắt simulator bằng SSE hiện có; giữ dữ liệu sau restart/reset.

**Ngoài phạm vi:** check-out/actual departure, xác nhận đón trả khách, sửa/bỏ qua/check-in thủ công, backfill telemetry cũ, sửa baseline, tự kết thúc chuyến GPS, HERE Traffic/ETA động/reroute, notification center/read status, auth/device onboarding, MQTT, nhiều scheduler instance. Không thêm luồng cảnh báo nghiệp vụ vào AlertStream ở 007.

## Yêu cầu chức năng và acceptance criteria

Các AC dưới đây là contract nghiệp vụ; [spec](spec.md) chốt cách diễn giải và [test-plan](test-plan.md) ánh xạ kiểm thử.

| AC | Yêu cầu/kết quả quan sát được |
|---|---|
| AC-01 | Chỉ telemetry được chấp nhận của đúng xe/chuyến IN_PROGRESS mới tạo check-in. Chỉ đổi lifecycle sang start mà chưa có vị trí không tạo check-in. |
| AC-02 | Một lần ghé được định danh bằng trip + thứ tự stop. Dùng tên/tọa độ/radius snapshot của trip; sửa station sau đó không đổi kết quả chuyến cũ. |
| AC-03 | Nhận diện điểm nằm trong hoặc trên ranh vùng trạm và đoạn quỹ đạo đi ngang vùng giữa hai mẫu. Không yêu cầu xe đứng yên hoặc đủ dwell. |
| AC-04 | Chỉ nhận stop kế tiếp; không nhảy qua stop thiếu bằng chứng. Tuyến A→B→A không check-in A cuối lúc xuất phát. Vùng chồng nhau/đứng yên/jitter không tự tiêu thụ nhiều stop. |
| AC-05 | GPS accuracy kém, gap dài hoặc nhảy vị trí không đủ điều kiện không tạo giao cắt giả. UI ghi “Chưa ghi nhận”, không kết luận chắc chắn xe chưa đến. |
| AC-06 | Retry, concurrent ingestion, reconnect và restart không nhân bản hoặc mất visit đã commit. Visit/evidence/latest/checkpoint cùng commit hoặc rollback. |
| AC-07 | Mỗi visit có giờ ghi nhận, nguồn, vị trí và evidence. Giờ suy ra từ quỹ đạo được đánh dấu; giờ giả lập tách khỏi wall clock; baseline không đổi. |
| AC-08 | SIMULATOR 1×/5×/10× dùng quỹ đạo đã chạy, không bỏ qua stop chỉ do tick vượt đỉnh đường cong. Pause/recover không tạo hành trình trong thời gian nghỉ. |
| AC-09 | API lịch sử trả đủ visits theo trip; snapshot/SSE và hai tab đồng bộ kết quả sau commit; reload/reconnect khôi phục cả những visit bị lỡ khi mất kết nối. |
| AC-10 | Timeline phân biệt Kế hoạch / Ghi nhận, GPS / GIẢ LẬP và thời gian suy ra; có loading/error/retry/empty, mất kết nối, selected trip đúng và responsive 320 px. |
| AC-11 | Complete/cancel giữ visits, không bịa những stop còn thiếu. Reset tạo trip mới có lịch sử riêng; retry reset không nhân trip hoặc visits. |
| AC-12 | Migration nâng cấp không sửa V1–V5 hoặc backfill giả; hồi quy 004–006 đạt bằng kiểm tra liên quan, không lộ secret và không cần HERE live để test 007. |

## Phi chức năng

- Độ tin cậy: database là nguồn sự thật; không phụ thuộc tab trình duyệt hay cache RAM để chống trùng.
- Hiệu năng: không quét toàn bộ trạm hoặc toàn bộ telemetry history mỗi tick. Mục tiêu dev: hai tab thấy visit trong tối đa 2 giây sau commit ở tải kiểm tra 10 chuyến × 1 mẫu/giây; phải đo, chưa phải cam kết production.
- Bảo mật: vị trí là dữ liệu nhạy cảm; không in payload/toạ độ hàng loạt ra log, không lấy vị trí trình duyệt của người điều hành làm vị trí xe. Không đưa HERE key lên frontend.
- Quan sát: lưu evidence đủ tái hiện quyết định; lỗi persistence trả lỗi có kiểm soát, không âm thầm bỏ visit trong khi báo ingestion thành công.

## Giả định cần review

1. Ưu tiên tránh ghi nhận sai hơn tự đoán các trạm đã bỏ lỡ. Nếu mất bằng chứng ở điểm đầu, hệ thống chờ điểm đầu, không tự nhảy tới B. Đây là giới hạn có chủ đích, không có thao tác sửa tay trong 007.
2. Điểm tiếp theo đang nằm trong vùng chồng nhau khi vừa ghi nhận điểm trước phải có bằng chứng ra ngoài rồi vào lại. Hai trạm khác ID nhưng trùng vùng có thể không được ghi nhận hết trong một lượt; xem [spec §2](spec.md).
3. Ngưỡng gap/accuracy/tốc độ là policy đề xuất trong spec, không phải chuẩn HERE hoặc đảm bảo độ chính xác thiết bị thật.
4. Check-out được hoãn; hysteresis chỉ dùng nội bộ để kích hoạt lần ghé kế tiếp. Đề xuất này giữ 007 tập trung vào yêu cầu “đi ngang”.

Người dùng đã yêu cầu triển khai 007 trực tiếp. HERE Traffic/ETA vẫn để feature 008; các AC chưa có evidence runtime được liệt kê trong [evidence](evidence.md).
