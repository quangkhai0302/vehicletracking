# Review — <Tên feature>

## Thông tin review

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `<feature-id>` |
| Reviewer | `Codex/<tên>` |
| Implementer | `Gemini/<tên>` |
| Thời điểm | `<YYYY-MM-DD HH:mm timezone>` |
| Commit/diff được review | `<commit range hoặc git diff/worktree>` |
| Requirement/Spec/Test-Plan/Plan/Evidence version | `<commit/ngày>` |
| Lần review | `<1, 2, ...>` |

## Kết luận

Chọn đúng một:

```text
APPROVED
APPROVED WITH NOTES
CHANGES REQUESTED
```

**Kết luận hiện tại:** `<giá trị>`

**Lý do ngắn:** `<tóm tắt dựa trên findings và test result>`

Không approve chỉ vì đọc code, code compile hoặc một nhóm test pass. Implementation phải đáp ứng Requirement, Spec, Test-Plan, Plan và có Evidence đủ mạnh. Không được `APPROVED` nếu Requirement quan trọng còn `INCONCLUSIVE`.

## Phạm vi và phương pháp review

- Diff/commit đã đọc: `<...>`
- File/tài liệu đã đối chiếu: `<...>`
- Evidence Matrix và `EVD-*` đã kiểm tra: `<...>`
- Command/test đã tự xác minh: `<...>`
- Phần không thể xác minh: `<...>`

## Findings

Severity hợp lệ:

```text
BLOCKER
HIGH
MEDIUM
LOW
NOTE
```

### REV-001 — <Tiêu đề finding>

**Severity:** `<BLOCKER|HIGH|MEDIUM|LOW|NOTE>`

**File:** `<path>`

**Vị trí:** `<line hoặc symbol>`

**Requirement:** `<REQ-*>`

**Spec/Business Rule:** `<SPEC-*/BR-*>`

**Test:** `<TC-* hoặc test còn thiếu>`

**Evidence:** `<EVD-* hoặc Không có Evidence>`

**Vấn đề:**

`<Mô tả hành vi sai hoặc rủi ro cụ thể. Phân biệt rõ observation với suy luận.>`

**Cách tái hiện/Bằng chứng:**

1. `<precondition>`
2. `<steps hoặc command>`
3. `<actual result>`

**Ảnh hưởng:**

`<Ảnh hưởng tới người dùng, dữ liệu, security, compatibility hoặc operation>`

**Đề xuất:**

`<Hướng sửa nhỏ nhất; không bắt Gemini refactor ngoài scope nếu không cần>`

**Bắt buộc xử lý trước review lại:** `Có | Không`

**Trạng thái:** `OPEN | FIXED_PENDING_REVIEW | VERIFIED | ACCEPTED_RISK`

Lặp lại cấu trúc cho từng finding. Nếu không có finding, ghi `Không có` và vẫn hoàn thành các checklist bên dưới.

## Kiểm tra Requirement

| Requirement/AC | Spec/Test | Implementation liên quan | Evidence | Kết quả |
|---|---|---|---|---|
| `<REQ-*/AC-*>` | `<SPEC-*/TC-*>` | `<path/symbol>` | `<EVD-* hoặc Không có Evidence>` | `PASS/FAIL/INCONCLUSIVE` |

## Kiểm tra Spec

| Spec/BR/API/Event | Implementation | Evidence | Kết quả | Finding nếu có |
|---|---|---|---|---|
| `<SPEC-*/BR-*>` | `<path/symbol>` | `<EVD-*>` | `PASS/FAIL/INCONCLUSIVE` | `<REV-*>` |

Kiểm tra tối thiểu business logic, validation, error handling, security, edge cases, compatibility, transaction/idempotency/concurrency và configuration khi liên quan.

## Kiểm tra Test-Plan

| Test Case | Test implementation | Evidence | Kết quả | Gap/Finding |
|---|---|---|---|---|
| `<TC-*>` | `<path/test name>` | `<EVD-* hoặc Không có>` | `PASS/FAIL/INCONCLUSIVE` | `<REV-* hoặc ghi chú>` |

Test pass nhưng assertion không chứng minh expected result phải được ghi finding.

## Kiểm tra Plan

| Plan step | Trạng thái | Sai lệch | Đã được chấp thuận? | Ảnh hưởng |
|---|---|---|---|---|
| `<Step 1>` | `DONE/PARTIAL/NOT DONE` | `<không có hoặc mô tả>` | `Có/Không/Không áp dụng` | `<...>` |

Trạng thái Plan Step là tiến độ triển khai, không phải trạng thái Evidence. Kết luận Requirement/Evidence vẫn chỉ dùng `PASS/FAIL/INCONCLUSIVE`.

## Kiểm tra Evidence

- [ ] Mỗi Claim quan trọng có Evidence ID tồn tại.
- [ ] Evidence gắn đúng Requirement, Spec/Business Rule, Test Case và Plan Step.
- [ ] Nguồn, command/steps, expected và actual đủ để tái lập.
- [ ] Evidence trỏ đúng commit/worktree được review.
- [ ] Codex đã đối chiếu Evidence với source code và git diff.
- [ ] Codex đã chạy lại test/command quan trọng khi có thể.
- [ ] Research không có URL/citation giả; Survey không suy đoán từ tên file/project.
- [ ] Không có secret hoặc dữ liệu nhạy cảm trong artifact.
- [ ] Evidence Matrix không tham chiếu `EVD-*` không tồn tại.
- [ ] Requirement quan trọng không còn `INCONCLUSIVE` trước khi `APPROVED`.

## Kiểm tra chất lượng và kiến trúc

- [ ] Correctness và business logic.
- [ ] Happy path và edge case liên quan.
- [ ] Error handling và input validation.
- [ ] Security, authorization, secret và log nhạy cảm.
- [ ] Performance, query và index khi liên quan.
- [ ] Concurrency, transaction và idempotency khi liên quan.
- [ ] API/Event backward compatibility.
- [ ] Frontend state, cleanup, loading và error UX khi liên quan.
- [ ] Không có duplicated/unnecessary code đáng kể.
- [ ] Không có dependency ngoài Plan.
- [ ] Architecture và convention repository được giữ nhất quán.
- [ ] Không refactor hoặc mở rộng scope không cần thiết.

## Kiểm tra Regression

| Khu vực | Command/Test | Kết quả | Ghi chú |
|---|---|---|---|
| `<module/flow>` | `<command/test>` | `PASS/FAIL/INCONCLUSIVE` | `<EVD-* và ghi chú>` |

## Các test đã xác minh

| Command | Working directory | Exit code | Kết quả | Thời điểm |
|---|---|---|---|---|
| `<command>` | `<path>` | `<code>` | `<tóm tắt>` | `<time>` |

Không ghi `PASS` cho command chưa chạy. Nếu không thể chạy, ghi `INCONCLUSIVE`, lý do và Evidence thiếu. Không đưa secret vào output trích dẫn.

## Phản hồi xử lý findings

Gemini có thể cập nhật phần này sau khi sửa nhưng không được thay đổi kết luận review.

| Finding | File/thay đổi xử lý | Test đã chạy | Trạng thái do Gemini báo |
|---|---|---|---|
| `<REV-001>` | `<path/summary>` | `<command/TC>` | `FIXED_PENDING_REVIEW` |

## Review lại

Codex ghi kết quả xác minh sau khi Gemini sửa:

| Finding | Cách xác minh | Kết quả | Ghi chú |
|---|---|---|---|
| `<REV-001>` | `<diff/test/reproduction>` | `VERIFIED/REOPENED` | `<...>` |

## Vấn đề còn tồn tại

| Vấn đề | Severity/Rủi ro | Quyết định | Người xác nhận |
|---|---|---|---|
| `<known limitation>` | `<...>` | `<fix/defer/accepted risk>` | `<...>` |

## Findings bắt buộc trước lần review tiếp theo

Chỉ dùng khi kết luận là `CHANGES REQUESTED`:

- `<REV-*>` — `<điều kiện để được xem là đã xử lý>`

## Kết luận cuối cùng

`<Nêu rõ feature có đáp ứng Requirement/Spec không, test nào đã chạy, risk nào được chấp nhận và có sẵn sàng merge/release không.>`

## Checklist đóng review

- [ ] Mỗi finding có severity, vị trí, ảnh hưởng và đề xuất.
- [ ] Requirement, Spec, Test-Plan và Plan đã được đối chiếu.
- [ ] Evidence Matrix và từng Evidence quan trọng đã được kiểm tra độc lập.
- [ ] Regression phù hợp đã chạy hoặc ghi blocker.
- [ ] Findings bắt buộc đã được liệt kê khi `CHANGES REQUESTED`.
- [ ] Findings đã sửa được Codex xác minh lại, không chỉ dựa trên báo cáo của Gemini.
- [ ] Kết luận phản ánh đúng evidence và vấn đề còn tồn tại.
- [ ] Không `APPROVED` khi Requirement quan trọng còn `INCONCLUSIVE`.
