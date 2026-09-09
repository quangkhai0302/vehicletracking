# Hướng dẫn dành cho Codex

## Context bắt buộc

Trước khi lập kế hoạch, triển khai hoặc review, đọc `CONTEXT.md`,
`docs/features/README.md` và tám artifact của feature active.

## Workflow

```text
Requirement → Research → Survey → Spec → Test-Plan → Plan → Implement → Evidence → Review
```

- Yêu cầu mới nhất của người dùng là Source of Truth cao nhất.
- Tài liệu feature viết bằng tiếng Việt và không chứa secret.
- Chỉ thêm cấu trúc cần thiết cho feature đang làm.
- Bảo toàn package backend `com.quangkhai.vehicletracking_backend`.

## Baseline

- Frontend là React + TypeScript + Vite skeleton, chưa có map hay nghiệp vụ.
- Backend là Spring Boot skeleton, chưa có API, database hay nghiệp vụ.
- Feature active: `009-project-reset`.

## Verification

```bash
cd vehicletracking-frontend && npm run lint && npm run build
cd vehicletracking-backend && bash ./mvnw test
```
