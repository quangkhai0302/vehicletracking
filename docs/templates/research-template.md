# Research — <Tên feature>

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `<feature-id>` |
| Requirement liên quan | `<REQ-001, REQ-002>` |
| Trạng thái | `DRAFT` |
| Người thực hiện | `<tên>` |
| Ngày nghiên cứu | `<YYYY-MM-DD>` |

## Mục tiêu nghiên cứu

Nêu quyết định cần đưa ra và vì sao repository hiện tại hoặc Requirement chưa đủ thông tin để quyết định.

## Phạm vi nghiên cứu

### Trong phạm vi

- `<Công nghệ, thuật toán, provider hoặc vấn đề cần nghiên cứu>`

### Ngoài phạm vi

- `<Chủ đề không ảnh hưởng quyết định của feature>`

## Các câu hỏi cần trả lời

| ID | Câu hỏi | Requirement liên quan | Tiêu chí để trả lời |
|---|---|---|---|
| `RQ-001` | `<câu hỏi>` | `<REQ-*>` | `<dữ liệu/nguồn cần có>` |

## Tiêu chí đánh giá

| Tiêu chí | Mức quan trọng | Cách đánh giá |
|---|---|---|
| `<correctness/coverage/cost/...>` | `Bắt buộc/Cao/Vừa/Thấp` | `<định lượng hoặc cách so sánh>` |

## Các giải pháp được xem xét

### Giải pháp A — <Tên giải pháp>

#### Cách hoạt động

`<Mô tả ngắn, đủ để hiểu tác động tới project>`

#### Ưu điểm

- `<ưu điểm>`

#### Nhược điểm

- `<nhược điểm>`

#### Rủi ro

- `<rủi ro kỹ thuật, vận hành, license, chi phí hoặc vendor lock-in>`

#### Mức độ phù hợp với project

`<Đánh giá dựa trên Requirement và Survey sơ bộ, không dựa trên sở thích cá nhân>`

#### Evidence hỗ trợ

- `<EVD-* trỏ tới EXTERNAL_SOURCE hoặc SOURCE_CODE; nếu chưa có, ghi “Chưa có evidence xác minh.”>`

#### Prototype/kiểm chứng nếu có

- Thao tác hoặc command: `<command/read-only experiment>`
- Kết quả: `<kết quả đã bỏ secret>`
- Giới hạn của kết quả: `<điều chưa chứng minh được>`

### Giải pháp B — <Tên giải pháp>

Lặp lại cấu trúc của Giải pháp A.

## So sánh

| Tiêu chí | Giải pháp A | Giải pháp B | Nhận xét |
|---|---|---|---|
| `<tiêu chí>` | `<đánh giá>` | `<đánh giá>` | `<trade-off>` |

## Giải pháp đề xuất

**Lựa chọn:** `<Giải pháp được đề xuất>`

**Requirement được đáp ứng:** `<REQ-*>`

**Điều kiện áp dụng:** `<quota, cấu hình, license, version hoặc dependency>`

## Lý do lựa chọn

Giải thích quyết định và trade-off. Nêu rõ điều gì bị đánh đổi, vì sao chấp nhận được trong phạm vi hiện tại và dấu hiệu nào sẽ khiến quyết định cần xem xét lại.

## Giải pháp không được chọn

| Giải pháp | Lý do không chọn | Khi nào nên xem xét lại |
|---|---|---|
| `<tên>` | `<lý do>` | `<điều kiện>` |

## Ảnh hưởng dự kiến tới Spec và Plan

- Data model/API/Event: `<ảnh hưởng>`
- Dependency/config: `<ảnh hưởng>`
- Testability/failure mode: `<ảnh hưởng>`
- Security/operation: `<ảnh hưởng>`

## Câu hỏi và rủi ro còn lại

- `<vấn đề chưa thể kết luận>`

## Evidence từ nguồn bên ngoài

Mỗi quyết định quan trọng phụ thuộc thông tin bên ngoài phải liên kết tới record `EVD-*` trong `evidence.md`. Ưu tiên theo thứ tự:

1. Official documentation.
2. Standard/specification.
3. Source code chính thức.
4. Repository/documentation của dependency.
5. Nguồn kỹ thuật đáng tin cậy.

Không tạo URL hoặc citation giả. Phải mở và kiểm tra nội dung nguồn, không chỉ dựa vào search snippet. Nếu không thể kiểm chứng, ghi `Chưa có evidence xác minh.` và để kết luận liên quan ở trạng thái `INCONCLUSIVE`.

## Nguồn tham khảo

| ID | Evidence | Nguồn | URL | Ngày truy cập | Claim/Kết luận được hỗ trợ |
|---|---|---|---|---|---|
| `SRC-001` | `EVD-001` | `<tên tài liệu>` | `<URL thật>` | `<YYYY-MM-DD>` | `<Claim cụ thể>` |

Không đưa secret, API key hoặc response chứa dữ liệu nhạy cảm vào tài liệu.

## Checklist hoàn thành

- [ ] Tất cả Research Question đã được trả lời hoặc ghi blocker.
- [ ] Có ít nhất hai giải pháp khi thực sự tồn tại lựa chọn hợp lý.
- [ ] Đề xuất dựa trên tiêu chí liên kết Requirement.
- [ ] Trade-off và rủi ro được ghi rõ.
- [ ] Nguồn có URL, ngày truy cập và kết luận tương ứng.
- [ ] Claim quan trọng dùng thông tin bên ngoài có `EVD-*` hoặc ghi rõ chưa có Evidence.
- [ ] Không có URL/citation được suy đoán hoặc chưa mở kiểm tra.
- [ ] Không Research hoặc tạo abstraction cho nhu cầu ngoài scope.
