# Self-review — Feature 018

Kết luận hiện tại: **Request changes before production**. Phần tích hợp fixture đã chạy, nhưng chưa đủ evidence để bật Google mặc định ở môi trường thật.

- **High — persistence/retention:** `provider_content_expires_at` đã có để chặn nội dung hết hạn, nhưng thời hạn/quyền lưu metrics, geometry và replay phải được xác nhận theo tài khoản/điều khoản trước production. Không xem giá trị mặc định trong code là kết luận pháp lý.
- **Medium — transaction concurrency:** reroute đã tách snapshot → HTTP `NOT_SUPPORTED` → compare-and-write `REQUIRES_NEW`, đối chiếu attempt, route, revision và telemetry sample. Cần chạy integration PostgreSQL để xác nhận tranh chấp hai worker và rollback notification trong DB thật.
- **Medium — closure candidate:** ETA đã mang geometry incident HERE và `RerouteCandidateValidator` loại ứng viên nằm trong hành lang 30 m. Unit test giao/cách xa closure đạt; bridge, đường song song và incident upstream thiếu geometry còn cần nghiệm thu fixture/browser.
- **Medium — alternatives:** adapter chọn route có duration thấp nhất trong response; Google không cho alternatives với stopover intermediates nên tuyến nhiều trạm/via chỉ nhận route khả dụng, chưa phải tối ưu toàn phần nhiều ứng viên.
- **Medium — verification:** V12 chưa chạy trên PostgreSQL, frontend chưa build Node 24 và chưa nghiệm thu browser/live API.
- **Low — traffic mode UX:** code tự chọn Google route traffic khi đang xem tuyến Google, HERE area Flow khi không xem tuyến Google. Chưa có control ba trạng thái GOOGLE_ROUTE/HERE_AREA/OFF riêng như thiết kế đích.

Các lỗi đã sửa trong self-review: update giữ mode/provider cũ; preview provider có request key đúng; interval traffic được vẽ sau lõi xanh; ETA cache phân biệt attempt/geometry/revision; UI loại response ETA cũ; Google refresh đường song song bị từ chối; request đồng thời được single-flight và budget; reroute giữ shaping point còn ở phía trước.
