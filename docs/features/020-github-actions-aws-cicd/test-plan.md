# Test plan — GitHub Actions AWS CI/CD

| AC | Kiểm tra | Kết quả mong đợi |
|---|---|---|
| 1 | Parse workflow và chạy các lệnh frontend tương ứng | Lint/type/build exit 0 trên source được commit |
| 2 | `./mvnw test` trên GitHub Ubuntu runner | Toàn bộ test pass với Docker/Testcontainers |
| 3 | Review workflow/IAM templates | Không có static AWS key hoặc PEM |
| 4 | Review resource ARN trong deploy policy | Chỉ đúng instance và AWS document |
| 5 | Chạy deploy với `.env.production` 600 trên EC2 | Secret ở lại server; stack được cập nhật |
| 6 | Gửi hai deployment gần nhau | `flock` ngăn hai Compose build chạy đồng thời |
| 7 | Chưa tạo GitHub variables | Hai job CI chạy; deploy hiển thị skipped |
| Health | Làm backend không khởi động được | Deploy job fail và log `ps`/container được trả về |
