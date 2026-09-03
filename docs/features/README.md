# Quản lý tài liệu feature

Thư mục này chứa toàn bộ tài liệu theo từng feature. Mỗi feature phải giữ Requirement, Research, Survey, Spec, Test-Plan, Plan, Evidence và Review ở cùng một vị trí để developer, Codex và Gemini có thể tải đầy đủ context.

## Quy tắc đặt tên

Mỗi feature dùng ID tăng dần gồm ba chữ số và tên kebab-case:

```text
<feature-id>-<feature-name-kebab-case>
```

Ví dụ tên hợp lệ: `001-station-management`. Đây chỉ là ví dụ về quy tắc đặt tên, không tạo folder feature cho đến khi có feature thật.

Trước khi chọn ID mới, liệt kê các thư mục hiện có trong `docs/features/` và dùng số tiếp theo. Không tái sử dụng ID của feature đã xóa hoặc ngừng phát triển nếu lịch sử repository đã ghi nhận ID đó.

## Cấu trúc bắt buộc

```text
docs/features/<feature-id>-<feature-name-kebab-case>/
├── requirement.md
├── research.md
├── survey.md
├── spec.md
├── test-plan.md
├── plan.md
├── evidence.md
└── review.md
```

Tạo các file bằng cách sao chép template tương ứng trong `docs/templates/`. Không tổ chức tài liệu theo các folder `specs/`, `plans/` hoặc `reviews/` tách rời.

## Người chịu trách nhiệm

| Tài liệu | Người chịu trách nhiệm chính | Mục đích |
|---|---|---|
| `requirement.md` | User/Developer và Codex | Xác định hệ thống cần đạt gì |
| `research.md` | Codex | Ghi giải pháp, nguồn và trade-off |
| `survey.md` | Codex | Ghi hiện trạng repository và phần tái sử dụng |
| `spec.md` | Codex | Định nghĩa hành vi kỹ thuật cần implement |
| `test-plan.md` | Codex | Định nghĩa cách chứng minh implementation đúng |
| `plan.md` | Codex | Chia thay đổi code cụ thể cho Gemini |
| `evidence.md` | Codex và Gemini | Ghi Claim, bằng chứng kiểm chứng, Evidence Matrix và trạng thái |
| `review.md` | Codex | Ghi findings và kết luận sau implementation |

Codex tạo Evidence cho Claim trong Research/Survey. Gemini chịu trách nhiệm implement, viết test, chạy verification và cập nhật Evidence từ Implementation. Codex kiểm tra Evidence và quyết định Review; Gemini không tự approve implementation của mình.

## Trạng thái feature

Ghi trạng thái trong phần metadata của từng file bằng một trong các giá trị:

```text
DRAFT
READY
IN_PROGRESS
BLOCKED
CHANGES_REQUESTED
APPROVED
DONE
```

Không dùng `DONE` khi Review chưa được `APPROVED` hoặc `APPROVED WITH NOTES` và các test bắt buộc chưa có kết quả đạt.

Các giá trị trên là trạng thái vòng đời của tài liệu/feature, không phải trạng thái Evidence. Trạng thái của từng Evidence và kết luận Requirement chỉ dùng `PASS`, `FAIL`, `INCONCLUSIVE`.

## Checklist tạo feature

- [ ] ID là số tiếp theo và tên folder dùng kebab-case.
- [ ] Tám file tài liệu nằm cùng một folder.
- [ ] Requirement có `REQ-*` và Acceptance Criteria.
- [ ] Spec liên kết Requirement và có `BR-*` khi có business rule.
- [ ] Test-Plan có `TC-*` liên kết với Requirement/Spec.
- [ ] Plan chỉ dùng path và command đã được Survey xác minh.
- [ ] Evidence dùng `EVD-*`, có Evidence Matrix và chỉ dùng `PASS/FAIL/INCONCLUSIVE`.
- [ ] Mọi Requirement quan trọng có đường dẫn Requirement → Spec → Test → Implementation → Evidence → Review.
- [ ] Không có secret hoặc credential trong tài liệu.
- [ ] Không bắt đầu Implement trước khi tài liệu bắt buộc được duyệt.
