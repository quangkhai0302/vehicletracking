# 007 — Evidence triển khai và kiểm tra

Trạng thái: **Implementing** · Cập nhật 2026-09-14

## Đã triển khai

- Migration `V6__create_trip_stop_checkins.sql` tạo checkpoint bền vững và visit append-only, unique theo `(trip_id, stop_sequence)`, composite FK provenance và CHECK source/evidence/time.
- `checkin` backend tự xử lý mẫu telemetry đã accepted trong cùng transaction: khóa checkpoint, xét stop kế tiếp, geofence POINT/SEGMENT cho GPS, ROUTE_TRACE theo polyline simulator, hysteresis và revision.
- GET `/api/v1/trips/{tripId}/check-ins` trả read model; `OperationsSnapshot`/SSE có `checkIns` cho mọi trip, gồm revision 0 khi chưa có dữ liệu.
- Frontend có type/service/hook với AbortController và merge theo revision; Fleet trip timeline và Simulator hiển thị số lượt ghi nhận, nguồn GPS/GIẢ LẬP và thời điểm suy ra.

## Lệnh và kết quả thực tế

| Lệnh | Kết quả |
|---|---|
| `JAVA_HOME=/home/khainq/.sdkman/candidates/java/26.0.1-amzn JAVA_TOOL_OPTIONS=... bash ./mvnw -q test` (backend) | **BUILD SUCCESS**, 178 tests, 0 failures/errors/skipped; Testcontainers PostgreSQL 17, Flyway V1→V8, 2026-09-14. The check-in integration slice contains 12 service tests and 2 HTTP/SSE tests. |
| `... -Dtest=OperationsIntegrationTest test` | **BUILD SUCCESS**, 12 tests; GPS POINT→SEGMENT và simulator POINT→ROUTE_TRACE/3 stop theo thứ tự |
| `... -Dtest=OperationsHttpIntegrationTest test` | **BUILD SUCCESS**, 2 tests; GET check-ins, HTTP/SSE hai subscriber và reconnect |
| `npm run lint` (frontend), Node `v24.16.0` | Exit 0 |
| `./node_modules/.bin/tsc --noEmit`, Node `v24.16.0` | Exit 0 |
| `npm run build`, Node `v24.16.0` | **BUILD PASS**, 1899 modules; JS 486.98 kB (gzip 144.74 kB), CSS 119.05 kB (gzip 26.30 kB) |
| Browser fixture `live-browser.mjs`, Chrome, 1440/390/320 px | **PASS**, 10 checks, `pageErrors=[]`; explicit empty check-in state and read model included |
| Browser via Spring/PostgreSQL `-Dverification.browser006=true`, Chrome (`VERIFICATION_BROWSER_PATH=/usr/bin/google-chrome`, Node `v24.16.0`) | **PASS**, 9 checks, `pageErrors=[]`; ordered simulator visits verified against isolated Testcontainers database, artifact `features/006-telemetry-simulator/artifacts/live-test-database/results.json` |

## Giới hạn còn lại

Browser verification riêng cho 007 đã chạy qua wrapper và xác nhận ba visit simulator theo thứ tự. Chưa đo tải 10×50 stop/2 tab và chưa chạy đầy đủ ma trận edge case của test-plan (overlap, rollback fault injection). Vì vậy feature chưa chuyển sang `Verified`; cần bổ sung nghiệm thu hiệu năng/edge P6 trước khi đánh dấu hoàn tất.

Khi chạy lại hai regression script cũ sau khi bổ sung `checkIns` vào fixture snapshot, regression 004 dừng ở locator form trạm trên flow layout hiện tại và regression 005 dừng do fixture không có summary cho trip được chọn. Hai lỗi này không nằm trong detector/API 007; không dùng chúng làm bằng chứng pass hoặc sửa ngoài phạm vi.

Không có HERE Traffic/ETA hoặc API key trong feature này; đó là phạm vi 008.
