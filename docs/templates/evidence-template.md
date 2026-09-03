# Evidence — <Tên feature>

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `<feature-id>` |
| Requirement/Spec/Test-Plan/Plan version | `<commit/ngày>` |
| Implementation được kiểm chứng | `<commit, commit range hoặc mô tả worktree>` |
| Trạng thái tài liệu | `DRAFT` |
| Người thu thập | `<Codex/Gemini/tên>` |
| Người kiểm tra | `<Codex/tên hoặc Chưa kiểm tra>` |
| Ngày cập nhật | `<YYYY-MM-DD HH:mm timezone>` |

## Mục đích

Tài liệu này ghi bằng chứng có thể kiểm chứng cho các kết luận quan trọng của feature.

**Claim** là điều Agent cho rằng đúng.

**Evidence** là bằng chứng có thể kiểm chứng hỗ trợ Claim đó.

Các câu như “implementation đúng”, “feature hoạt động tốt”, “không có regression”, “tests đã đầy đủ” hoặc “API hoạt động chính xác” không phải Evidence nếu không có nguồn, cách kiểm chứng và kết quả thực tế đi kèm.

## Nguyên tắc

- Mỗi Evidence có ID ổn định dạng `EVD-001`, `EVD-002`, `EVD-003`, ...
- Không tái sử dụng ID đã xóa; đánh dấu record cũ là superseded nếu cần thay thế.
- Evidence phải ghi đúng implementation/commit và môi trường đã được kiểm chứng.
- Không ghi API key, token, mật khẩu, personal data hoặc secret vào command, output, log, ảnh hay URL.
- Không sửa hoặc rút gọn output theo cách làm thay đổi ý nghĩa. Nếu chỉ trích đoạn, ghi rõ vị trí artifact đầy đủ.
- Source code chứng minh code tồn tại nhưng không mặc định chứng minh hành vi runtime, concurrency, performance hoặc không có regression.
- Screenshot chứng minh trạng thái UI quan sát được nhưng không mặc định chứng minh business logic backend.
- Không có Evidence không đồng nghĩa với `FAIL`, nhưng cũng không được coi là `PASS`.

## Trạng thái Evidence

Chỉ sử dụng ba trạng thái:

```text
PASS
FAIL
INCONCLUSIVE
```

- `PASS`: Evidence đủ mạnh và kết quả thực tế hỗ trợ Claim trong phạm vi đã nêu.
- `FAIL`: Evidence thực tế cho thấy Claim, Requirement hoặc Spec không được đáp ứng.
- `INCONCLUSIVE`: chưa có Evidence, Evidence không đủ mạnh hoặc không thể thực hiện verification.

Nếu không thể chạy command/test, dùng `INCONCLUSIVE` và ghi lý do. Không suy đoán test “có vẻ sẽ pass”.

## Evidence Matrix

Mỗi Requirement quan trọng phải có ít nhất một dòng. Tách thành nhiều dòng nếu một Requirement có nhiều Spec/Test/Evidence.

| Requirement | Spec / Business Rule | Test Case | Implementation | Evidence | Status |
|---|---|---|---|---|---|
| `REQ-001` | `SPEC-001 / BR-001` | `TC-001` | `<path#symbol hoặc commit>` | `EVD-001` | `PASS` |
| `REQ-002` | `SPEC-003` | `TC-004` | `<path#symbol hoặc commit>` | `EVD-003` | `INCONCLUSIVE` |

Không ghi `PASS` ở Matrix nếu Evidence được tham chiếu không tồn tại hoặc record đó không đủ để chứng minh Claim.

## Coverage Summary

| Requirement | Critical? | Evidence PASS | Evidence FAIL | Evidence INCONCLUSIVE | Kết luận hiện tại |
|---|---|---|---|---|---|
| `<REQ-*>` | `Có/Không` | `<EVD-*>` | `<EVD-* hoặc Không có>` | `<EVD-* hoặc Không có>` | `PASS/FAIL/INCONCLUSIVE` |

## Evidence từ Research

Các Claim phụ thuộc thông tin bên ngoài phải có nguồn thật. Thứ tự ưu tiên:

1. Official documentation.
2. Standard/specification.
3. Source code chính thức.
4. Repository/documentation của dependency.
5. Nguồn kỹ thuật đáng tin cậy.

Không tạo URL hoặc citation giả. Nếu không kiểm chứng được, ghi nguyên văn:

```text
Chưa có evidence xác minh.
```

và đặt trạng thái `INCONCLUSIVE`.

## Evidence từ Survey

Claim về repository phải trỏ tới path, symbol, manifest, configuration, test hoặc command output cụ thể. Không suy đoán architecture chỉ từ tên project, folder hoặc technology được nhắc trong README.

Ví dụ quan hệ đúng:

```text
Claim: Backend dùng Spring Boot phiên bản X.
Evidence: pom.xml chứa Spring Boot parent phiên bản X.
```

## Evidence từ Implementation

Gemini cập nhật phần này sau khi implement và chạy verification. Evidence có thể đến từ:

- test output;
- build output;
- lint output;
- type-check output;
- API request/response;
- database schema, constraint, query hoặc migration;
- source code và git diff;
- log runtime;
- screenshot/video UI;
- manual verification có steps tái lập được.

Gemini tạo Evidence nhưng không tự review hoặc quyết định feature được approve.

---

## EVD-001 — <Tên Evidence>

### Claim

`<Một kết luận cụ thể mà Evidence này muốn chứng minh. Tránh câu quá rộng như “feature hoạt động tốt”.>`

### Liên kết

- Requirement: `<REQ-001 hoặc Không áp dụng>`
- Acceptance Criteria: `<AC-REQ-... hoặc Không áp dụng>`
- Spec / Business Rule: `<SPEC-001 / BR-001 hoặc Không áp dụng>`
- Test Case: `<TC-001 hoặc Không áp dụng>`
- Plan Step: `<Step 1 hoặc Không áp dụng>`
- Finding: `<REV-001 hoặc Không áp dụng>`

### Loại Evidence

Chọn đúng một giá trị chính:

```text
SOURCE_CODE
TEST
BUILD
LINT
TYPE_CHECK
API
DATABASE
LOG
UI
MANUAL
EXTERNAL_SOURCE
```

**Loại:** `<giá trị>`

### Nguồn

Ghi nguồn đủ chính xác để tìm lại:

- File/symbol: `<path#symbol hoặc Không áp dụng>`
- Command/test/API: `<command, test name, METHOD /endpoint hoặc Không áp dụng>`
- External URL: `<URL thật hoặc Không áp dụng>`
- Artifact: `<path tới output/screenshot/log đã redact hoặc Không áp dụng>`
- Commit/worktree: `<commit hoặc mô tả trạng thái>`
- Thời điểm quan sát: `<YYYY-MM-DD HH:mm timezone>`

### Môi trường và điều kiện tiên quyết

- Môi trường: `<local/test/staging, OS/runtime/version liên quan>`
- Configuration: `<tên biến/config, không ghi giá trị secret>`
- Dữ liệu ban đầu: `<fixture/state>`

### Cách kiểm chứng

```bash
<command nếu áp dụng>
```

Hoặc các bước manual/API/UI:

1. `<bước có thể tái lập>`
2. `<bước>`

### Kết quả

**Mong đợi:** `<Expected result lấy từ Test-Plan/Spec>`

**Thực tế:** `<Actual result, exit code, assertion count, HTTP status, response đã redact hoặc hành vi UI quan sát được>`

Không ghi dự đoán vào phần kết quả thực tế.

### Trạng thái

`PASS | FAIL | INCONCLUSIVE`

### Lý do trạng thái

`<Vì sao Evidence đủ/chưa đủ hoặc đã chứng minh failure>`

### Ghi chú

- Giới hạn của Evidence: `<điều Evidence này không chứng minh>`
- Rủi ro còn lại: `<nếu có>`
- Evidence bổ sung liên quan: `<EVD-* hoặc Không có>`

---

## EVD-002 — <Tên Evidence tiếp theo>

Sao chép đầy đủ cấu trúc của `EVD-001`. Không rút gọn các trường bắt buộc.

## Evidence bị thay thế

| Evidence cũ | Evidence thay thế | Lý do | Ngày |
|---|---|---|---|
| `<EVD-*>` | `<EVD-*>` | `<implementation hoặc verification đã thay đổi>` | `<YYYY-MM-DD>` |

## Evidence còn thiếu

| Requirement/Claim | Evidence cần có | Lý do chưa có | Trạng thái | Hành động tiếp theo |
|---|---|---|---|---|
| `<REQ-*/Claim>` | `<test/API/UI/...>` | `<blocker>` | `INCONCLUSIVE` | `<người/bước xử lý>` |

## Checklist bàn giao cho Review

- [ ] Mọi Requirement quan trọng có dòng trong Evidence Matrix.
- [ ] Mọi `EVD-*` có Claim cụ thể, liên kết, loại, nguồn và cách kiểm chứng.
- [ ] Kết quả thực tế ghi command/steps, exit code hoặc quan sát phù hợp.
- [ ] Không có `PASS` cho command/test chưa chạy.
- [ ] Research URL/citation tồn tại và thực sự hỗ trợ Claim.
- [ ] Survey Claim trỏ tới bằng chứng thật trong repository.
- [ ] Evidence gắn đúng commit/worktree được review.
- [ ] Secret và dữ liệu nhạy cảm đã được loại bỏ.
- [ ] Evidence Matrix không tham chiếu ID không tồn tại.
- [ ] Requirement quan trọng còn `INCONCLUSIVE` đã được ghi rõ để Codex không approve nhầm.
