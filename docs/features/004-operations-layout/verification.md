# Verification 004 — 2026-09-13

> **UI hiện hành là revision Map-First:** [kết quả triển khai và kiểm chứng](map-first-implementation.md), [browser results](artifacts/map-first-app/results.json). Các kiểm tra bố cục hai cột/nền sáng bên dưới là lịch sử bản dashboard đã được thay thế. Kết quả backend vẫn là kết quả khảo sát trước, không phải kiểm tra mới của revision Map-First.

## Môi trường và phạm vi

Windows/PowerShell, Node 22.20.0, npm 10.9.3, Java 26.0.1. Source frontend được sửa; backend/migration giữ nguyên. Không đọc nội dung `.env`. Trước thay đổi `git status --short` rỗng, HEAD `8d398b0`.

`docs/workflow.md` không có trong checkout; đã kiểm tra inventory trước khi tạo tài liệu. Các tài liệu mới dưới `docs/` vẫn bị ignore (`.gitignore`, dòng `docs/`).

## Lệnh đã chạy

| Thư mục | Lệnh | Kết quả |
| --- | --- | --- |
| frontend | `npm.cmd ci --no-audit --no-fund` | Exit 0 sau khi chạy ngoài sandbox; lần đầu bị EPERM cache |
| frontend | `npm.cmd run lint` | Exit 0 |
| frontend | `.\node_modules\.bin\tsc.cmd --noEmit` | Exit 0 |
| frontend | `npm.cmd run build` | Exit 0, Vite 8.2.2, 1865 modules |
| backend | `.\mvnw.cmd test` | Exit 1, 75 kết quả, 0 failures, 2 errors; chi tiết bên dưới |
| root | `node docs/features/004-operations-layout/verification/layout-smoke.mjs` | Exit 0, 13 nhóm kiểm tra đạt, không có pageerror; Edge headless, kết quả và ảnh tại `artifacts/` |

Frontend lint/tsc/build chạy riêng từng lệnh ở lượt kiểm tra cuối để xác nhận từng exit code. Package/lockfile ứng dụng không thay đổi; Playwright chỉ cài trong thư mục verification ignored.

## Backend: 73 pass, 2 lỗi khởi tạo integration class

Evidence: `vehicletracking-backend/target/surefire-reports/com.quangkhai.vehicletracking_backend.*.txt`, dòng `Tests run` và lỗi khởi tạo.

| Test class | Pass | Lỗi |
| --- | ---: | ---: |
| RouteConfigurationTest | 3 | 0 |
| RouteControllerTest | 13 | 0 |
| RouteExceptionHandlerRegressionTest | 1 | 0 |
| HereRoutingProviderTest | 27 | 0 |
| RouteServiceTest | 14 | 0 |
| StationControllerTest | 6 | 0 |
| StationServiceTest | 9 | 0 |
| RouteRepositoryIntegrationTest | 0 | 1 ở khởi tạo class |
| StationRepositoryIntegrationTest | 0 | 1 ở khởi tạo class |

Lỗi: `Could not find a valid Docker environment` và `Previous attempts to find a Docker environment failed`. Lệnh đã được thử ngoài sandbox; lỗi còn tồn tại. Đây không phải 75 phương thức nghiệp vụ đều được thực thi: hai lỗi cấp class khiến test repository chưa chạy đầy đủ. Cần chạy lại `mvnw.cmd test` trên môi trường Docker hoạt động. Không khởi động/sửa database của người dùng để thay thế Testcontainers.

Provider test sử dụng `MockRestServiceServer` tại `HereRoutingProviderTest.setUp`, không phải gọi HERE thật.

## Browser smoke

Script: [layout-smoke.mjs](verification/layout-smoke.mjs). Mọi `/api/` được `context.route` intercept; các request không nhận diện bị abort. Dữ liệu station/route là fixture, không ghi vào API/database thật. Map tiles công khai có thể tải qua mạng; không dùng HERE credential.

Kiểm tra:

1. Layout desktop 1440px: hai cột không chồng, bản đồ có diện tích thao tác.
2. Danh sách → detail → đóng giữ giá trị tìm kiếm; list bị ẩn khỏi tab order/accessibility khi drawer hiển thị.
3. Tạo/sửa/ngừng sử dụng trạm qua API fixture; xác nhận bỏ form, ở lại và giữ dữ liệu.
4. Tạo tuyến với dwell, timeline và polyline.
5. Detail tại 1024px, 768px: không tràn trang hoặc chồng cột; resize từ desktop tự fit lại đủ marker tuyến trong khung map. Lỗi cắt marker phát hiện ở lượt rà ảnh đã được sửa bằng `plannedRouteBoundsRef` trong callback ResizeObserver và kiểm tra lại.
6. Tại 390px và 320px: chọn tọa độ trên map tự quay lại form, chuyển view giữ nháp, đổi lớp bản đồ.
7. Tại 390px và 320px: form tạo tuyến không cuộn ngang, reorder stop hoạt động, chuyển map fit đủ marker trong bounds.
8. Lỗi tải trạm không hiển thị sai “chưa có trạm”; lỗi tuyến có retry và chuyển sang empty khi API fixture thành công.
9. Không có `pageerror` JavaScript trong các luồng trên.

Kết quả máy đọc được: [results.json](artifacts/results.json). Ảnh fixture tiêu biểu:

- [Tracking desktop](artifacts/tracking-desktop-fixture.png)
- [Danh sách trạm desktop](artifacts/stations-desktop-fixture.png)
- [Chi tiết tuyến desktop](artifacts/route-detail-desktop-fixture.png)
- [Form trạm mobile](artifacts/form-mobile-390-fixture.png)
- [Form tuyến mobile](artifacts/route-form-mobile-320-fixture.png)
- [Bản đồ tuyến mobile](artifacts/route-map-mobile-390-fixture.png)

## Giới hạn

Chưa xác minh backend/database thật qua UI, HERE Routing/Traffic live, GPS thật, Safari/iOS hoặc mọi thiết bị di động. Smoke kiểm tra chức năng/layout hiện có, không chứng minh realtime, simulator, ETA động hay check-in đã được triển khai. Không thêm dữ liệu fixture vào runtime ứng dụng. Hình học fixture chỉ phục vụ kiểm tra render/fit, không phải tuyến HERE thực tế.

Acceptance criteria 004 được đối chiếu với source `App`, `MapComponent`, `MapControls`, `TrackingPanel`, `StationPanel`, `RouteDrawer` và `workspace.css`; phần source/evidence chi tiết tại `docs/PROJECT_PROGRESS.md`, mục 2–3. Chưa commit/push.
