# Test plan

AC1/2: integration reset cùng ID, route/trip count không tăng, sample/visit cũ còn, truy vấn lịch sử theo attempt. AC3/4: phát mẫu mới, trạm đầu được ghi lại, checkpoint monotonic, snapshot/ETA chỉ lần hiện tại. AC5: conflict khi GPS, xe inactive, chuyến khác đang chạy; không ghi dở. Unit test lifecycle + frontend typecheck/lint/build. Chạy Maven test nếu môi trường cho phép; ghi rõ thiếu Docker. Browser hiện bị bộ duyệt chặn do giới hạn sử dụng; không tìm cách vượt qua giới hạn đó.
