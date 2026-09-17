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
| Parse `.github/workflows/ci-cd.yml` bằng YAML parser | Thành công |
| `bash -n deploy/aws/deploy.sh` | Exit code 0 |
| Parse hai IAM JSON bằng `ConvertFrom-Json` | Thành công |
| `git diff --check` | Không có whitespace error trong file Feature 020 |
| Quét static credential marker trong `.github`, `deploy`, tài liệu Feature 020 | Không có kết quả |

## Trạng thái

Implementation local và static validation đã hoàn tất. Trust policy đã chuyển sang chỉ cho phép subject OIDC bất biến
`repo:quangkhai0302@213111072/vehicletracking@1354976400:ref:refs/heads/master` và bước validate
repository variables. Còn chờ commit/push, cập nhật trust policy trên AWS và chạy workflow end-to-end.

Thay đổi nội suy marker trong working tree được ghi nhận cùng đợt sửa lỗi realtime; chưa có kiểm thử trình duyệt tự động.
