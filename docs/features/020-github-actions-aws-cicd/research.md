# Research — GitHub Actions AWS CI/CD

**Ngày khảo sát:** 2026-09-17

- GitHub Actions có thể lấy AWS credential ngắn hạn bằng OIDC; workflow cần `id-token: write`, AWS cần OIDC provider và IAM trust policy giới hạn repository/branch.
- `aws-actions/configure-aws-credentials` hỗ trợ OIDC và kiểm tra AWS account đích.
- Systems Manager Run Command yêu cầu EC2 trở thành managed node và instance profile có `AmazonSSMManagedInstanceCore`.
- Quyền `ssm:SendCommand` tương đương khả năng chạy lệnh quản trị trên node, nên policy chỉ cho phép đúng `AWS-RunShellScript` và đúng instance ARN.

Nguồn chính thức:

- https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws
- https://github.com/aws-actions/configure-aws-credentials
- https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-setting-up-ec2.html
- https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command-setting-up.html
- https://docs.aws.amazon.com/systems-manager/latest/userguide/walkthrough-cli.html
