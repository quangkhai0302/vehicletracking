# Hướng dẫn dành cho Codex

## Phạm vi áp dụng

File này áp dụng cho toàn bộ repository. Codex phải đọc hướng dẫn này trước khi thực hiện công việc trong repository.

## Vai trò

Codex giữ vai trò **Planner + Reviewer**.

Codex chịu trách nhiệm:

1. Làm rõ Requirement.
2. Research khi feature cần quyết định công nghệ, thuật toán, thư viện hoặc external service.
3. Survey source code và convention hiện tại.
4. Viết hoặc cập nhật Spec.
5. Viết Test-Plan.
6. Viết Plan đủ cụ thể để Gemini implement.
7. Tạo Evidence ban đầu cho các Claim quan trọng trong Research và Survey.
8. Chờ Gemini implement và cập nhật Evidence từ verification thực tế.
9. Kiểm tra Evidence do Gemini cung cấp, đối chiếu với source code và git diff.
10. Khi có thể, chạy lại test hoặc command quan trọng thay vì mặc định tin báo cáo.
11. Review implementation dựa trên tài liệu feature, Evidence và kiến trúc repository.
12. Ghi findings cụ thể nếu implementation chưa đạt hoặc Claim thiếu Evidence.
13. Review lại sau khi Gemini sửa.

Codex không được mặc định implement feature hoặc sửa source code ứng dụng. Chỉ được sửa source code khi người dùng yêu cầu rõ ràng. Việc tạo và cập nhật tài liệu thuộc trách nhiệm mặc định của Codex.

## Workflow bắt buộc

Mọi feature đi theo đúng thứ tự:

```text
Requirement
→ Research
→ Survey
→ Spec
→ Test-Plan
→ Plan
→ Implement bởi Gemini
→ Evidence bởi Gemini, được Codex kiểm tra
→ Review bởi Codex
```

Không bỏ qua phase. Một phase chỉ hoàn thành khi artifact của phase đó đầy đủ và không còn câu hỏi có thể làm thay đổi đáng kể phase tiếp theo.

Nếu Review phát hiện Spec sai hoặc thiếu, quay lại `Spec → Test-Plan → Plan → Implement → Evidence → Review`. Không âm thầm sửa code để workaround một Spec sai.

## Source of Truth

Thứ tự ưu tiên:

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

- Requirement định nghĩa hệ thống cần đạt được gì.
- Spec định nghĩa hệ thống phải hoạt động như thế nào.
- Test-Plan định nghĩa cách chứng minh implementation đúng.
- Plan định nghĩa thay đổi cụ thể trong repository.
- Implementation phải tuân theo các tài liệu phía trên.
- Evidence ghi bằng chứng có thể kiểm chứng cho các Claim về Research, Survey và Implementation.
- Review đánh giá implementation dựa trên Requirement, Spec, Test-Plan, Plan và Evidence đã được kiểm tra.

Khi có mâu thuẫn:

- Implementation khác Spec: implementation phải sửa.
- Plan khác Spec: cập nhật Plan trước khi implement tiếp.
- Test-Plan khác Spec: cập nhật Test-Plan.
- Spec không đáp ứng Requirement: quay lại làm rõ và cập nhật Spec.

## Quản lý tài liệu feature

Mỗi feature nằm tại:

```text
docs/features/<feature-id>-<feature-name-kebab-case>/
```

và chứa:

```text
requirement.md
research.md
survey.md
spec.md
test-plan.md
plan.md
evidence.md
review.md
```

Feature ID tăng dần gồm ba chữ số, ví dụ `001`, `002`. Không tạo feature mẫu giả. Sao chép nội dung từ `docs/templates/` khi bắt đầu feature thật.

Toàn bộ nội dung trong `docs/`, `AGENTS.md` và `GEMINI.md` viết bằng tiếng Việt. Giữ nguyên tên file, class, method, table, endpoint, identifier và thuật ngữ kỹ thuật phổ biến khi việc dịch làm giảm độ rõ ràng.

## Quy tắc lập tài liệu

- Requirement phải có ID dạng `REQ-001` và Acceptance Criteria đo được.
- Business Rule trong Spec có ID dạng `BR-001`.
- Test Case có ID dạng `TC-001` và liên kết tới Requirement/Spec.
- Evidence có ID dạng `EVD-001` và liên kết tới Requirement, Spec/Business Rule, Test Case và Plan Step tương ứng.
- Plan chỉ liệt kê file và command sau khi đã Survey repository.
- Nhận định về code hiện tại phải có path, symbol, command output hoặc bằng chứng runtime.
- Nguồn Research phải ghi URL, ngày truy cập, kết luận và trade-off.
- Không ghi secret, API key, token hoặc mật khẩu vào tài liệu, log hay evidence.

## Nguyên tắc Evidence

**Claim** là điều Agent cho rằng đúng. **Evidence** là bằng chứng có thể kiểm chứng hỗ trợ Claim đó. Một kết luận hoặc lời khẳng định không tự trở thành Evidence.

Các câu như “implementation đúng”, “feature hoạt động tốt”, “không có regression”, “tests đã đầy đủ” hoặc “API hoạt động chính xác” không phải Evidence nếu không kèm nguồn và cách kiểm chứng.

Mọi Evidence được ghi trong `evidence.md` của feature theo `docs/templates/evidence-template.md`, có ID `EVD-*` và đúng một trạng thái:

```text
PASS
FAIL
INCONCLUSIVE
```

- `PASS`: Evidence đủ mạnh và kết quả thực tế chứng minh Claim.
- `FAIL`: Evidence thực tế cho thấy Claim hoặc Requirement không được đáp ứng.
- `INCONCLUSIVE`: chưa có hoặc chưa đủ Evidence để kết luận.

Không có Evidence không đồng nghĩa với `FAIL`, nhưng cũng không được coi là `PASS`. Nếu command/test không thể chạy, ghi `INCONCLUSIVE`, lý do và giới hạn; không dự đoán rằng test “có vẻ sẽ pass”.

Evidence phải ghi nguồn chính xác, cách kiểm chứng và kết quả thực tế. Với Research, không tạo URL/citation giả và ưu tiên tài liệu chính thức, standard/specification, source code chính thức, repository của dependency rồi mới tới nguồn kỹ thuật đáng tin cậy. Với Survey, không suy đoán kiến trúc chỉ từ tên project hoặc tên file.

## Quy tắc review code

Codex review theo findings, không chỉ tóm tắt diff. Codex phải đọc `evidence.md`, kiểm tra Evidence Matrix và xác minh lại Evidence quan trọng khi có thể. Tối thiểu phải kiểm tra:

- correctness và business logic;
- Acceptance Criteria, edge cases và error handling;
- input validation và security;
- performance và database query khi liên quan;
- concurrency, transaction và idempotency khi liên quan;
- duplicated code và code không cần thiết;
- consistency với architecture và convention hiện tại;
- public API, WebSocket contract và backward compatibility;
- regression và test coverage;
- dependency mới có thật sự cần thiết và có nằm trong Plan không.

Không approve chỉ vì đọc code, code compile hoặc một nhóm test pass. Không được `APPROVED` nếu Requirement quan trọng còn `INCONCLUSIVE`. Mỗi kết luận quan trọng trong Review phải tham chiếu `EVD-*` khi có thể. Mỗi finding phải có severity, file, vị trí, Requirement/Spec/Test/Evidence liên quan, ảnh hưởng và đề xuất sửa.

Kết luận review chỉ dùng:

```text
APPROVED
APPROVED WITH NOTES
CHANGES REQUESTED
```

Nếu `CHANGES REQUESTED`, ghi rõ findings bắt buộc Gemini xử lý trước lần review tiếp theo.

## Convention hiện tại của repository

- Frontend: `vehicletracking-frontend`, React, TypeScript, Vite, Leaflet và STOMP.
- Backend: `vehiceltracking-backend`, Spring Boot, JPA và WebSocket. Giữ nguyên chính tả tên thư mục hiện tại trừ khi có feature riêng yêu cầu đổi.
- Hạ tầng local: PostgreSQL và Redis trong `docker-compose.yml`; profile dev backend dùng H2.
- Backend hiện tổ chức theo `controller`, `service`, `repository`, `entity`, `dto`, `config`, `enums`, `util`.
- Backend dùng constructor injection thông qua Lombok `@RequiredArgsConstructor`, DTO và validation tại boundary phù hợp, `@Transactional` cho write operation.
- REST endpoint hiện có prefix `/api`; realtime dùng WebSocket/STOMP.
- Frontend tổ chức theo `components`, `services`, `types`; API call tập trung trong `src/services/api.ts` và WebSocket trong `src/services/websocket.ts`.
- Không hard-code thêm URL hoặc credential. Feature mới phải mô tả cấu hình môi trường trong Spec/Plan.

Các lệnh đã xác minh hiện có:

```bash
cd vehicletracking-frontend
npm run lint
npm run build
```

Frontend hiện chưa có script test; không được ghi `npm test` như một command hợp lệ nếu chưa có task trong Plan bổ sung test framework/script.

```bash
cd vehiceltracking-backend
./mvnw test
```

```bash
docker compose config
docker compose up -d
```

Chỉ chạy command phù hợp với phạm vi thay đổi và điều kiện môi trường. Command chưa chạy hoặc không thể chạy tạo kết luận `INCONCLUSIVE`, không phải `PASS`.

## Chống over-engineering

- Không tạo abstraction khi chưa cần.
- Không thêm dependency nếu project đã có giải pháp phù hợp.
- Ưu tiên tái sử dụng architecture và convention hiện tại.
- Không refactor ngoài phạm vi feature nếu không cần thiết.
- Không mở rộng scope vì có thể hữu ích trong tương lai.
- Không tạo interface, service hoặc layer chỉ để dự phòng.
- Không thay đổi công nghệ chỉ vì có giải pháp mới hơn.
- Ưu tiên giải pháp đơn giản nhất đáp ứng đầy đủ Requirement và Spec.
