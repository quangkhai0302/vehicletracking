# Quy trình phát triển phần mềm với Codex và Gemini

## Mục tiêu

Workflow này phân tách trách nhiệm thiết kế và kiểm chứng khỏi trách nhiệm viết code:

- **Developer/User** xác nhận mục tiêu, phạm vi và các quyết định sản phẩm.
- **Codex** là Planner + Reviewer.
- **Gemini** là Implementer.

Mục tiêu là giảm việc Agent tự suy đoán, giữ tài liệu và code truy vết được, đồng thời tạo vòng phản hồi rõ ràng khi implementation chưa đạt.

## Luồng chuẩn

```text
Requirement
    ↓
Research
    ↓
Survey
    ↓
Spec
    ↓
Test-Plan
    ↓
Plan
    ↓
Implement (Gemini)
    ↓
Evidence (Gemini thu thập, Codex kiểm tra)
    ↓
Review (Codex)
        ↓
    PASS?
   /     \
 NO      YES
 ↓        ↓
Gemini   Done
fix
 ↓
Codex Review
```

Vòng lặp thực thi:

```text
Codex Plan
     ↓
Gemini Implement
     ↓
Gemini Verification + Evidence
     ↓
Codex Review
     ↓
Có finding?
  ↓       ↓
 Có      Không
 ↓        ↓
Gemini   Done
Fix
 ↓
Codex Review lại
```

## Source of Truth

```text
Requirement
    ↓
Spec
    ↓
Test-Plan
    ↓
Plan
    ↓
Implementation
    ↓
Evidence
    ↓
Review
```

- Requirement trả lời: cần đạt điều gì?
- Spec trả lời: hệ thống phải hoạt động như thế nào?
- Test-Plan trả lời: chứng minh đúng bằng cách nào?
- Plan trả lời: thay đổi repository ở đâu và theo thứ tự nào?
- Implementation hiện thực hóa các tài liệu phía trên.
- Evidence trả lời: bằng chứng kiểm chứng nào hỗ trợ từng Claim?
- Review trả lời: Evidence và implementation có đủ để chấp nhận feature không?

Nếu tài liệu cấp dưới mâu thuẫn tài liệu cấp trên, sửa tài liệu cấp dưới trước khi tiếp tục. Review không được tự động thay đổi Requirement hoặc Spec.

## Trách nhiệm theo phase

### 1. Requirement

Người dùng/Developer mô tả vấn đề, mục tiêu và giới hạn. Codex chuẩn hóa thành `requirement.md`, cấp ID `REQ-*`, viết Acceptance Criteria và liệt kê câu hỏi chưa giải quyết.

Điều kiện hoàn thành:

- phạm vi trong/ngoài rõ ràng;
- Requirement đo được;
- câu hỏi có thể làm thay đổi thiết kế đã được xác nhận hoặc đánh dấu blocker.

### 2. Research

Codex nghiên cứu công nghệ, thư viện, external service hoặc thuật toán khi quyết định không thể được đưa ra chỉ từ repository. Kết quả phải có giải pháp được xem xét, trade-off, rủi ro, đề xuất và nguồn. Claim phụ thuộc thông tin bên ngoài phải có nguồn thật và `EVD-*`; nếu chưa xác minh được, ghi “Chưa có evidence xác minh.”

Không Research lan man những vấn đề không ảnh hưởng feature.

### 3. Survey

Codex đọc source code hiện tại để xác định architecture, convention, thành phần tái sử dụng, technical debt liên quan và file có thể bị ảnh hưởng. Mọi nhận định quan trọng cần path, symbol hoặc command làm Evidence. Không suy đoán kiến trúc chỉ dựa trên tên project hoặc tên file.

Survey là hoạt động read-only đối với source code ứng dụng.

### 4. Spec

Codex viết đặc tả kỹ thuật chính cho Gemini. Spec phải xử lý business rule, data model, API/event contract, validation, error handling, security, edge case và compatibility phù hợp với feature.

Mỗi phần quan trọng liên kết về `REQ-*`. Nếu Spec không đáp ứng Requirement, quay lại Requirement/Spec trước khi lập Test-Plan.

### 5. Test-Plan

Codex mô tả cách chứng minh implementation đáp ứng Spec. Test Case có ID `TC-*`, expected result rõ ràng, liên kết Requirement/Spec và loại Evidence dự kiến.

Không dùng việc compile thành công làm bằng chứng duy nhất cho correctness.

### 6. Plan

Codex lập danh sách file tạo/sửa, database/API changes, implementation steps, test cần viết, Evidence cần thu thập và command kiểm tra. Path và command phải đến từ Survey, không được tự giả định.

Plan phải đủ cụ thể để Gemini không phải thiết kế lại feature.

### 7. Implement

Gemini đọc ít nhất `requirement.md`, `spec.md`, `test-plan.md`, `plan.md`, bảo đảm `evidence.md` tồn tại, sau đó viết code và test đúng phạm vi. Gemini chạy verification, cập nhật Evidence Matrix và từng record `EVD-*`, rồi báo cáo diff, command, exit code, vấn đề còn lại và sai lệch đã được cho phép.

Nếu tài liệu mâu thuẫn hoặc Plan không khả thi, Gemini dừng phần bị ảnh hưởng và báo lại thay vì tự đổi thiết kế.

### Evidence gate giữa Implement và Review

Evidence không thay thế tám phase ban đầu; đây là cổng bắt buộc sau Implement và trước Review.

**Claim** là điều Agent cho rằng đúng. **Evidence** là bằng chứng có thể kiểm chứng hỗ trợ Claim đó. Những câu như “implementation đúng”, “API hoạt động chính xác” hoặc “không có regression” không phải Evidence nếu không có nguồn, cách kiểm chứng và kết quả thực tế.

Mỗi Evidence có ID `EVD-*`, nằm trong `evidence.md` của feature và chỉ dùng:

```text
PASS
FAIL
INCONCLUSIVE
```

Không có Evidence không đồng nghĩa với `FAIL`, nhưng cũng không được coi là `PASS`. Khi không thể chạy verification, trạng thái là `INCONCLUSIVE` và phải ghi lý do.

### 8. Review

Codex review implementation dựa trên Requirement, Spec, Test-Plan, Plan, Implementation, Evidence và architecture hiện tại. Codex không mặc định tin báo cáo của Gemini: phải đối chiếu Evidence với source code/git diff và chạy lại command quan trọng khi có thể. Review tập trung vào findings có severity và tác động cụ thể.

Mỗi kết luận quan trọng tham chiếu Evidence tương ứng, ví dụ:

```text
REQ-001 → SPEC-002 → TC-003 → EVD-004 → PASS
```

Không được `APPROVED` nếu Requirement quan trọng còn `INCONCLUSIVE`.

Kết luận:

```text
APPROVED
APPROVED WITH NOTES
CHANGES REQUESTED
```

Khi có `CHANGES REQUESTED`, Gemini sửa findings bắt buộc và Codex review lại.

## Khi Review phát hiện Spec sai

Không sửa code để workaround. Quay lại workflow:

```text
Review
↓
Spec
↓
Test-Plan
↓
Plan
↓
Implement
↓
Evidence
↓
Review
```

Codex ghi lý do thay đổi Spec, phần Requirement bị ảnh hưởng và test cần cập nhật. Người dùng xác nhận nếu thay đổi làm đổi phạm vi hoặc hành vi sản phẩm.

## Quản lý feature

Mỗi feature dùng ID tăng dần và tên kebab-case:

```text
docs/features/<feature-id>-<feature-name-kebab-case>/
```

Tám tài liệu của feature phải nằm cùng folder, gồm `evidence.md`. Xem `docs/features/README.md` và template trong `docs/templates/`.

## Cách bắt đầu một feature

1. Developer/User mô tả feature và các constraint.
2. Codex xác định ID tiếp theo, tạo folder và sao chép tám template.
3. Codex hoàn thành lần lượt Requirement đến Plan, dừng xác nhận ở quyết định quan trọng.
4. Gemini nhận đường dẫn feature, implement, chạy verification và cập nhật `evidence.md`.
5. Codex kiểm tra Evidence, chạy lại kiểm chứng quan trọng khi có thể rồi review.
6. Lặp Gemini Fix → Codex Review cho đến khi đạt.

Prompt gợi ý cho Codex:

```text
Tạo tài liệu cho feature <tên feature> theo AGENTS.md và docs/workflow.md.
Bắt đầu từ Requirement, không sửa source code ứng dụng. Dừng khi có câu hỏi cần tôi xác nhận.
```

Prompt gợi ý cho Gemini:

```text
Implement feature tại docs/features/<feature-folder>/ theo GEMINI.md.
Đọc requirement.md, spec.md, test-plan.md, plan.md và evidence.md trước khi sửa code.
Không thay đổi scope hoặc Spec; chạy verification, cập nhật EVD-* và báo blocker nếu tài liệu mâu thuẫn.
```

Prompt gợi ý cho Codex khi review:

```text
Review implementation của docs/features/<feature-folder>/ theo AGENTS.md.
Đối chiếu Requirement, Spec, Test-Plan, Plan, Evidence và git diff; chạy lại verification quan trọng khi có thể; ghi findings vào review.md.
Không tự sửa source code nếu tôi chưa yêu cầu.
```

## Nguyên tắc chống over-engineering

- Không tạo abstraction khi chưa cần.
- Không thêm dependency khi giải pháp hiện tại phù hợp.
- Ưu tiên architecture và convention đang có.
- Không refactor hoặc mở rộng scope ngoài feature.
- Không thiết kế cho nhu cầu giả định trong tương lai.
- Giải pháp đơn giản đáp ứng đầy đủ Requirement và Spec được ưu tiên.

## Bảo mật và tính trung thực của kiểm chứng

- Không ghi secret vào source code, tài liệu, log hoặc ảnh minh chứng.
- Command chưa chạy hoặc không thể chạy tạo Evidence `INCONCLUSIVE`, kèm lý do; không ghi `PASS`.
- Test thất bại tạo Evidence `FAIL`, kèm exit code và phạm vi ảnh hưởng.
- External service phải có failure case trong Test-Plan nếu feature phụ thuộc nó.
- Không coi test pass là đủ nếu hành vi vẫn khác Requirement hoặc Spec.
- Không có Evidence không đồng nghĩa với `FAIL`, nhưng cũng không được coi là `PASS`.
