# Test plan

| AC | Kiểm tra |
|---|---|
| AC1 | Lint/type/build, kiểm tra checkbox và callback |
| AC2 | So sánh ảnh POI bật/tắt tại cùng tọa độ/zoom; bắt buộc kiểm tra provider |
| AC3 | Kiểm tra URL giữ lyrs/traffic; code không thay marker hoặc camera |
| AC4 | So sánh đường bộ/ban đêm/vệ tinh với traffic bật và tắt |

Không dùng build pass để kết luận ảnh POI đã bị ẩn. Chưa có browser/provider verification thì ghi pending.

