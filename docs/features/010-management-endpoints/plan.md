# Implementation plan

1. Migration V8 + route active/update/deactivate.
2. Trip schedule update/delete và API client.
3. Telemetry history page/filter.
4. Notification read-all/delete và revision supersede.
5. Chạy compile/unit/frontend checks, cập nhật evidence.

## Kế hoạch nối frontend — Approved theo yêu cầu trực tiếp 2026-09-14

1. Bổ sung client notifications read-all/delete/supersede và đồng bộ error handling; không thay API/schema.
2. `RouteWorkspace`/`RouteDrawer`: edit dùng lại form, deactivate confirmation, map/list sync. Rủi ro request cũ ghi đè state: khóa khi mutation và abort GET liên quan.
3. `useFleetWorkspace`/`TripDetailPanel`: update/delete chuyến, giữ local mutation trước snapshot cũ. Dùng lại datetime utility và confirmation.
4. Panel lịch sử telemetry và revision tách riêng, mount theo chuyến; GET cleanup, pagination/filter/error. Không trộn logic HTTP vào component.
5. `AlertStream`: thao tác thông báo, busy/error/confirmation và bảo vệ local success trước snapshot cũ.
6. Chạy lint/typecheck/build bằng Node 24; thêm browser fixture verification các AC. Cập nhật evidence/walkthrough theo kết quả thực tế, nêu giới hạn live backend.
