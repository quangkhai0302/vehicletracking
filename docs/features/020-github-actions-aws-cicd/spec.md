# Spec — GitHub Actions AWS CI/CD

## Pipeline

```text
push master
   ├── frontend: npm ci → lint → tsc → build
   └── backend: Java 26 → mvnw test (Testcontainers)
             │
             └── deploy: GitHub OIDC → AWS STS → SSM Run Command → EC2
```

- Hai job CI chạy song song.
- Job deploy chỉ chạy khi cả hai job thành công và ba GitHub Actions variables đã tồn tại.
- OIDC trust chỉ chấp nhận `quangkhai0302/vehicletracking`, ref `refs/heads/master`, audience `sts.amazonaws.com`.
- IAM deploy policy chỉ cho phép `AWS-RunShellScript` trên đúng instance production.
- SSM reset checkout server về chính `GITHUB_SHA` đã qua CI, rồi gọi `deploy/aws/deploy.sh`.
- Script khóa deploy bằng `flock`, validate `.env.production`, build/up Compose, chờ snapshot HTTP 200 tối đa 180 giây và prune image không dùng.
- Không chạy `docker compose down -v`; volume PostgreSQL được giữ nguyên.

## GitHub variables

- `AWS_ACCOUNT_ID`
- `AWS_DEPLOY_ROLE_ARN`
- `AWS_INSTANCE_ID`

Không cần GitHub secret chứa AWS access key, EC2 PEM, HERE key hoặc mật khẩu PostgreSQL.
