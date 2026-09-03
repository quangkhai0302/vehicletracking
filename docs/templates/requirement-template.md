# Requirement — <Tên feature>

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `<feature-id>` |
| Trạng thái | `DRAFT` |
| Người tạo | `<tên>` |
| Người xác nhận | `<tên>` |
| Ngày tạo/cập nhật | `<YYYY-MM-DD>` |

## Tổng quan

Mô tả ngắn vấn đề feature cần giải quyết, người gặp vấn đề và bối cảnh sử dụng. Không mô tả giải pháp kỹ thuật tại đây.

## Mục tiêu

Liệt kê kết quả có thể quan sát hoặc đo được sau khi feature hoàn thành.

- `<Mục tiêu 1>`
- `<Mục tiêu 2>`

## Người dùng và bên liên quan

| Actor/Bên liên quan | Nhu cầu | Quyền hoặc giới hạn liên quan |
|---|---|---|
| `<actor>` | `<nhu cầu>` | `<giới hạn>` |

## Phạm vi

### Trong phạm vi

- `<Hành vi hoặc kết quả feature phải xử lý>`

### Ngoài phạm vi

- `<Hành vi không thuộc feature này>`

Ghi rõ ngoài phạm vi để Agent không tự mở rộng feature.

## Yêu cầu chức năng

Mỗi yêu cầu dùng ID ổn định. Viết về hành vi cần đạt, không khóa vào implementation nếu chưa có lý do bắt buộc.

### REQ-001 — <Tên yêu cầu>

**Mô tả:** `<Hệ thống/người dùng phải làm được gì>`

**Lý do:** `<Giá trị nghiệp vụ hoặc vấn đề được giải quyết>`

**Độ ưu tiên:** `MUST | SHOULD | COULD`

**Dependency:** `<REQ khác hoặc hệ thống ngoài; Không có nếu không áp dụng>`

### REQ-002 — <Tên yêu cầu>

**Mô tả:** `<...>`

**Lý do:** `<...>`

**Độ ưu tiên:** `MUST | SHOULD | COULD`

**Dependency:** `<...>`

## Yêu cầu phi chức năng

Chỉ giữ các mục thực sự liên quan và làm chúng đo được.

### Performance

- `<Ví dụ: độ trễ, throughput, giới hạn dữ liệu>`

### Security

- `<Authentication, authorization, secret, input validation hoặc privacy>`

### Reliability

- `<Retry, idempotency, availability, reconnect hoặc fallback>`

### Scalability

- `<Số người dùng, vehicle, event hoặc request dự kiến>`

### Compatibility

- `<Browser, API client, database hoặc backward compatibility>`

### Observability

- `<Log, metric, trace hoặc audit cần có; không chứa secret>`

## Ràng buộc

| Nhóm | Ràng buộc | Lý do/Nguồn |
|---|---|---|
| Công nghệ | `<ràng buộc>` | `<lý do>` |
| Database | `<ràng buộc>` | `<lý do>` |
| API/Event | `<ràng buộc>` | `<lý do>` |
| Compatibility | `<ràng buộc>` | `<lý do>` |
| Thời gian | `<deadline nếu có>` | `<lý do>` |

## Business Rules đã biết

Ghi quy tắc nghiệp vụ đã được người dùng xác nhận. ID chi tiết sẽ được chuẩn hóa trong Spec.

- `<Quy tắc>`

## Acceptance Criteria

Mỗi tiêu chí phải liên kết Requirement và có thể kiểm chứng. Ưu tiên Given/When/Then.

### AC-REQ-001-01 — <Tên tiêu chí>

```text
Given <điều kiện ban đầu>
When <hành động hoặc sự kiện>
Then <kết quả quan sát được>
And <kết quả bổ sung nếu có>
```

### AC-REQ-001-02 — <Tên tiêu chí biên/lỗi>

```text
Given <điều kiện>
When <invalid input, duplicate, timeout hoặc edge case phù hợp>
Then <hành vi mong đợi>
```

## Giả định và dependency

- `<Giả định cần Research/Survey xác minh>`
- `<External service, dữ liệu hoặc feature phụ thuộc>`

## Rủi ro sản phẩm

| Rủi ro | Khả năng | Ảnh hưởng | Cách giảm thiểu/Xác nhận cần thiết |
|---|---|---|---|
| `<rủi ro>` | `Thấp/Vừa/Cao` | `Thấp/Vừa/Cao` | `<hành động>` |

## Câu hỏi chưa được giải quyết

| ID | Câu hỏi | Người quyết định | Ảnh hưởng nếu chưa trả lời | Trạng thái |
|---|---|---|---|---|
| `Q-001` | `<câu hỏi>` | `<người>` | `<phase/REQ bị ảnh hưởng>` | `OPEN` |

Không chuyển sang Spec nếu còn câu hỏi mở có thể làm thay đổi đáng kể scope, data model, public contract hoặc lựa chọn công nghệ.

## Xác nhận Requirement

- [ ] Mọi Requirement có ID và độ ưu tiên.
- [ ] Trong/ngoài phạm vi rõ ràng.
- [ ] Acceptance Criteria đo được và có edge case phù hợp.
- [ ] Mỗi Requirement quan trọng có thể truy vết tới Evidence sau implementation.
- [ ] Yêu cầu phi chức năng liên quan đã được ghi nhận.
- [ ] Không chứa giải pháp kỹ thuật chưa được Research/Survey xác minh.
- [ ] Câu hỏi ảnh hưởng thiết kế đã được giải quyết hoặc đánh dấu blocker.
