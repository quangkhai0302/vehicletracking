# Requirement — GitHub Actions AWS CI/CD

**Status:** Approved

## Mục tiêu

Mỗi lần push vào `master`, hệ thống tự kiểm tra frontend/backend và chỉ redeploy đúng commit đã kiểm tra lên EC2 khi toàn bộ kiểm tra thành công.

## Yêu cầu

1. Frontend phải chạy install, lint, type check và production build.
2. Backend phải chạy toàn bộ Maven test, bao gồm Testcontainers.
3. Deploy không lưu AWS access key hoặc EC2 private key trong GitHub.
4. GitHub chỉ được phép ra lệnh trên đúng EC2 production.
5. EC2 giữ `.env.production` cục bộ; CI không đọc hoặc in secret runtime.
6. Deploy phải tuần tự, kiểm tra HTTP health và báo thất bại trong GitHub Actions.
7. Khi chưa cấu hình AWS variables, CI vẫn chạy và deploy được bỏ qua có kiểm soát.

## Ngoài phạm vi

- Blue/green deployment và zero-downtime database migration.
- Tự động rollback migration Flyway.
- Container registry riêng.
