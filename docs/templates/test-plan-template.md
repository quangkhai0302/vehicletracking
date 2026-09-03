# Test-Plan — <Tên feature>

## Thông tin tài liệu

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `<feature-id>` |
| Requirement/Spec version | `<commit/ngày>` |
| Trạng thái | `DRAFT` |
| Người viết | `<tên>` |
| Người duyệt | `<tên>` |
| Ngày cập nhật | `<YYYY-MM-DD>` |

## Mục tiêu kiểm thử

Trả lời câu hỏi: làm sao chứng minh implementation đáp ứng Requirement và Spec? Nêu các rủi ro chính mà Test-Plan cần phát hiện.

## Phạm vi kiểm thử

### Trong phạm vi

- `<Requirement, Spec contract và flow cần kiểm tra>`

### Ngoài phạm vi

- `<Loại test hoặc hệ thống không thuộc feature, kèm lý do>`

## Chiến lược kiểm thử

Mô tả tỷ lệ và mục đích của các lớp test. Không yêu cầu mọi feature phải có mọi loại test; mục không áp dụng phải ghi rõ lý do.

### Unit Test

- Đối tượng: `<service, util, reducer, component logic>`
- Mục tiêu: `<business rule/edge case cần cô lập>`
- Framework hiện có hoặc cần bổ sung theo Plan: `<...>`
- Không áp dụng nếu: `<lý do cụ thể>`

### Integration Test

- Boundary: `<service + database, external adapter, WebSocket...>`
- Dependency thật/mocked: `<...>`
- Mục tiêu: `<transaction, serialization, query, contract...>`
- Không áp dụng nếu: `<lý do>`

### API Test

- Endpoint: `<METHOD /endpoint>`
- Mục tiêu: `<status, payload, validation, error contract>`
- Không áp dụng nếu: `<lý do>`

### UI Test

- Flow/component: `<...>`
- Mục tiêu: `<render, interaction, loading/error/realtime state>`
- Công cụ: `<chỉ ghi công cụ đã có hoặc được Plan phê duyệt>`
- Không áp dụng nếu: `<lý do>`

### Realtime/Event Test

- Topic/event: `<...>`
- Mục tiêu: `<payload, ordering, duplicate, reconnect, delivery>`
- Không áp dụng nếu: `<lý do>`

### End-to-End/Acceptance Test

- Luồng: `<hành vi người dùng từ đầu đến cuối>`
- Môi trường: `<local/test/staging>`
- Không áp dụng nếu: `<lý do và cách thay thế>`

## Môi trường và điều kiện tiên quyết

| Thành phần | Yêu cầu | Cách chuẩn bị |
|---|---|---|
| Runtime | `<version>` | `<command/config>` |
| Database | `<loại/trạng thái>` | `<migration/fixture>` |
| External service | `<mock/sandbox/real>` | `<cấu hình không chứa secret>` |
| Frontend/Browser | `<yêu cầu>` | `<cách chạy>` |

## Test Data

| ID | Dữ liệu | Mục đích | Cách tạo/dọn dẹp |
|---|---|---|---|
| `TD-001` | `<dữ liệu hợp lệ>` | `<happy path>` | `<fixture/API>` |
| `TD-002` | `<boundary/invalid/duplicate>` | `<edge case>` | `<fixture/API>` |

Không dùng dữ liệu production chứa thông tin nhạy cảm.

## Test Cases

### TC-001 — <Tên test case>

**Loại:** `Unit | Integration | API | UI | Realtime | E2E | Manual`

**Mức ưu tiên:** `Critical | High | Medium | Low`

**Liên kết:** `<REQ-001, AC-REQ-001-01, SPEC-001, BR-001>`

**Mục tiêu:** `<Điều test chứng minh>`

**Precondition:**

- `<trạng thái ban đầu>`

**Input/Test data:** `<TD-001 hoặc giá trị cụ thể>`

**Steps:**

1. `<bước>`
2. `<bước>`

**Expected result:**

- `<kết quả quan sát/đo được>`
- `<side effect hoặc state không được thay đổi>`

**Automation:** `Có | Không | Dự kiến`

**Test file dự kiến:** `<path hoặc Chưa xác định trong Survey>`

**Evidence dự kiến:** `<EVD-* nếu đã cấp ID; loại và artifact như test output/API response/WebSocket frame/screenshot>`

### TC-002 — <Invalid input hoặc edge case phù hợp>

Lặp lại cấu trúc TC-001.

## Ma trận tình huống cần xem xét

Chỉ tạo Test Case cho tình huống thực sự liên quan; ghi `Không áp dụng` và lý do cho phần còn lại.

| Tình huống | Áp dụng? | Test Case | Lý do/Ghi chú |
|---|---|---|---|
| Happy path | `<Có/Không>` | `<TC-*>` | `<...>` |
| Invalid input | `<Có/Không>` | `<TC-*>` | `<...>` |
| Boundary value | `<Có/Không>` | `<TC-*>` | `<...>` |
| Duplicate request/event | `<Có/Không>` | `<TC-*>` | `<...>` |
| Missing data/not found | `<Có/Không>` | `<TC-*>` | `<...>` |
| Permission/authentication | `<Có/Không>` | `<TC-*>` | `<...>` |
| Concurrency/race condition | `<Có/Không>` | `<TC-*>` | `<...>` |
| Timeout | `<Có/Không>` | `<TC-*>` | `<...>` |
| External service failure | `<Có/Không>` | `<TC-*>` | `<...>` |
| Database failure/rollback | `<Có/Không>` | `<TC-*>` | `<...>` |
| Reconnect/out-of-order event | `<Có/Không>` | `<TC-*>` | `<...>` |
| Regression | `<Có/Không>` | `<TC-*>` | `<...>` |

## Acceptance Test

| Acceptance Criteria | Test Case | Cách chạy | Evidence | Trạng thái Evidence |
|---|---|---|---|---|
| `<AC-REQ-*>` | `<TC-*>` | `<automated/manual steps>` | `<EVD-* và artifact cần có>` | `INCONCLUSIVE` |

Mỗi Acceptance Criteria phải có ít nhất một Test Case. Manual test cần steps và expected result tái lập được.

## Regression Test

| Khu vực có nguy cơ ảnh hưởng | Test hiện có cần chạy | Lý do |
|---|---|---|
| `<module/flow>` | `<test/command>` | `<dependency/coupling>` |

## Lệnh kiểm tra

Chỉ ghi command đã được Survey xác minh hoặc Plan sẽ bổ sung rõ ràng.

```bash
<command>
```

| Command | Mục đích | Điều kiện PASS | Evidence output |
|---|---|---|---|
| `<command>` | `<...>` | `exit code 0 và <điều kiện>` | `<path/log>` |

## Quy tắc Evidence

Với mỗi Test Case critical, lưu đủ thông tin để người khác tái lập:

```text
Evidence ID: EVD-XXX
Claim:
Requirement / Spec / Business Rule:
Test Case:
Plan Step:
Loại Evidence:
Nguồn:
Môi trường/Precondition:
Cách kiểm chứng:
Expected:
Actual:
Trạng thái: PASS | FAIL | INCONCLUSIVE
Artifact:
```

- Không ghi secret hoặc token.
- Code diff không thay thế runtime/test evidence.
- Screenshot không thay thế test business logic backend.
- Command chưa chạy hoặc không thể chạy ghi `INCONCLUSIVE` cùng lý do; thất bại ghi `FAIL`.
- Không có Evidence không đồng nghĩa với `FAIL`, nhưng cũng không được coi là `PASS`.

## Tiêu chí dừng và xử lý lỗi test

- Dừng bàn giao nếu test Critical thất bại.
- Không sửa test chỉ để khớp implementation nếu implementation khác Spec.
- Flaky test phải được báo cáo và điều tra; không chạy lặp đến khi may mắn pass rồi bỏ qua.
- External service không ổn định phải có mock/contract test và policy cho test thật.

## Definition of Done

- [ ] Mọi Requirement/Acceptance Criteria có Test Case.
- [ ] Happy path và edge/failure case liên quan được bao phủ.
- [ ] Test Data và precondition tái lập được.
- [ ] Command kiểm tra tồn tại hoặc được Plan bổ sung rõ ràng.
- [ ] Acceptance và regression scope được xác định.
- [ ] Evidence yêu cầu được mô tả và không chứa secret.
- [ ] Không có Critical Test Case ở trạng thái `FAIL` hoặc Evidence `INCONCLUSIVE` khi đề nghị approve.
