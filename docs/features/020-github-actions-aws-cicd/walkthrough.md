# Walkthrough — Thiết lập CI/CD một lần

Workflow nằm tại `.github/workflows/ci-cd.yml`. Sau khi hoàn tất các bước dưới đây, mỗi push vào `master` sẽ chạy CI và redeploy EC2 bằng Systems Manager.

## 1. Cho EC2 kết nối Systems Manager

Trong AWS Console, vào **IAM → Roles → Create role**:

1. Trusted entity: **AWS service**.
2. Use case: **EC2**.
3. Permission: `AmazonSSMManagedInstanceCore`.
4. Role name: `vehicletracking-ec2-ssm`.

Sau đó vào **EC2 → Instances → chọn instance → Actions → Security → Modify IAM role**, gắn role `vehicletracking-ec2-ssm`.

Trên EC2 kiểm tra agent:

```bash
sudo systemctl status snap.amazon-ssm-agent.amazon-ssm-agent.service --no-pager
```

Nếu agent chưa có:

```bash
sudo snap install amazon-ssm-agent --classic
sudo systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service
```

Vào **AWS Systems Manager → Fleet Manager → Managed nodes** và chờ instance hiện trạng thái online.

## 2. Tạo GitHub OIDC provider trong AWS

Vào **IAM → Identity providers → Add provider**:

- Provider type: `OpenID Connect`
- Provider URL: `https://token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`

AWS hiện không yêu cầu nhập certificate fingerprint cho provider này.

## 3. Tạo policy cho GitHub deploy

Vào **IAM → Policies → Create policy → JSON**. Sao chép `deploy/aws/github-deploy-policy.example.json`, thay:

- `<AWS_ACCOUNT_ID>` bằng account ID AWS.
- `<EC2_INSTANCE_ID>` bằng instance ID production.

Đặt tên policy: `VehicleTrackingGitHubDeploy`.

## 4. Tạo OIDC deploy role

Vào **IAM → Roles → Create role → Web identity**:

1. Identity provider: `token.actions.githubusercontent.com`.
2. Audience: `sts.amazonaws.com`.
3. GitHub organization: `quangkhai0302`.
4. Repository: `vehicletracking`.
5. Branch: `master`.
6. Gắn policy `VehicleTrackingGitHubDeploy`.
7. Role name: `vehicletracking-github-deploy`.

Mở role vừa tạo và kiểm tra trust policy tương ứng `deploy/aws/github-oidc-trust-policy.example.json`. Sao chép Role ARN để dùng ở bước tiếp theo.

## 5. Tạo GitHub Actions variables

Vào repository GitHub → **Settings → Secrets and variables → Actions → Variables → New repository variable** và tạo:

| Variable | Giá trị |
|---|---|
| `AWS_ACCOUNT_ID` | AWS account ID |
| `AWS_DEPLOY_ROLE_ARN` | ARN của role `vehicletracking-github-deploy` |
| `AWS_INSTANCE_ID` | Instance ID EC2 production |

Không đưa HERE API key, mật khẩu PostgreSQL hoặc file PEM vào GitHub.

## 6. Chạy lần đầu

Vào tab **Actions → CI/CD production → Run workflow**, chọn branch `master`.

Kết quả đúng:

- `Frontend checks`: xanh.
- `Backend tests`: xanh.
- `Deploy to EC2 through SSM`: xanh.
- Website vẫn trả HTTP 200 và `docker compose ps` trên EC2 hiển thị ba service Up.

## 7. Các lần sau

Chỉ cần commit và push lên `master`. Workflow tự kiểm tra và deploy chính commit đó. Xem log deploy trong GitHub Actions hoặc trên EC2 bằng:

```bash
cd ~/vehicletracking
docker compose --env-file .env.production -f compose.production.yaml ps
docker compose --env-file .env.production -f compose.production.yaml logs --tail=150 backend
```
