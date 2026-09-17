# Plan — GitHub Actions AWS CI/CD

**Status:** Implementing

1. Tạo workflow CI song song cho React/TypeScript và Spring Boot/Testcontainers.
2. Thêm job deploy dùng GitHub OIDC, AWS STS và SSM Run Command.
3. Thêm script deploy trên EC2 với lock, validation, Compose và health check.
4. Thêm IAM policy/trust policy mẫu giới hạn branch và instance.
5. Viết hướng dẫn cấu hình EC2 instance role, GitHub OIDC role và repository variables.
6. Xác minh cú pháp workflow/script, push, cấu hình AWS một lần và chạy workflow end-to-end.
