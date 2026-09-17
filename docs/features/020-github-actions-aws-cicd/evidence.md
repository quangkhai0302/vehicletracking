# Evidence — GitHub Actions AWS CI/CD

## File triển khai

- `.github/workflows/ci-cd.yml`
- `deploy/aws/deploy.sh`
- `deploy/aws/github-deploy-policy.example.json`
- `deploy/aws/github-oidc-trust-policy.example.json`

## Kiểm tra local

| Kiểm tra | Kết quả |
|---|---|
| `actionlint ci-cd.yml` trong container chỉ mount workflow read-only, không có network | Exit code 0 |
| `bash -n deploy/aws/deploy.sh` | Exit code 0 |
| Parse hai IAM JSON bằng `ConvertFrom-Json` | Thành công |
| `git diff --check` | Không có whitespace error trong file Feature 020 |
| Quét static credential marker trong `.github`, `deploy`, tài liệu Feature 020 | Không có kết quả |

## Trạng thái

Implementation local và static validation đã hoàn tất. Còn chờ commit/push, thiết lập AWS IAM/SSM và chạy workflow end-to-end.

Các thay đổi UI có sẵn trong working tree không thuộc feature và không được đưa vào commit này.
