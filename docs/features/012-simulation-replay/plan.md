# Plan — Approved

1. Migration V9, attempt fields và archive entity/repository. 2. Reset transaction có validation, archive rồi chuyển trạng thái. 3. Chọn current attempt trong read-model/check-in/ETA/reroute; API lịch sử opt-in. 4. Frontend dùng attempt để bỏ trạng thái cũ và sửa thông báo Chạy lại. 5. Regression tests và evidence. Không chạy migration trực tiếp lên DB người dùng; không xóa dữ liệu. Đây là implementation được yêu cầu trực tiếp.
