# Quy trình phát triển feature

Tài liệu này quy định cách phân tích, lập kế hoạch, triển khai và review feature trong dự án Vehicle Tracking. Quy trình ưu tiên bằng chứng từ repository và ngăn việc triển khai vượt quá yêu cầu đã được duyệt.

## 1. Khi nào áp dụng

Áp dụng quy trình đầy đủ cho:

- Feature nghiệp vụ mới.
- Thay đổi API hoặc database contract.
- Tích hợp dịch vụ bên ngoài.
- Thay đổi kiến trúc hoặc luồng dữ liệu giữa frontend và backend.
- Công việc mà người dùng yêu cầu rõ các giai đoạn Requirement → Research → Survey → Spec → Test-Plan → Plan.

Sửa typo, tài liệu đơn giản hoặc bug nhỏ có thể dùng quy trình rút gọn: Survey → Plan ngắn → Implement → Verify. Nếu người dùng yêu cầu quy trình đầy đủ thì không được rút gọn.

## 2. Feature ID và thư mục

Mỗi feature dùng một thư mục:

```text
docs/features/<NNN>-<slug>/
```

Quy tắc:

- `NNN` gồm ba chữ số, lấy số lớn nhất đang có trong `docs/features/` cộng một.
- Không tái sử dụng ID cũ, kể cả feature đã hủy.
- `slug` dùng chữ thường và dấu gạch ngang, mô tả ngắn mục tiêu.
- Ví dụ: `011-station-management-ui`.

Bộ tài liệu chuẩn:

```text
requirement.md
research.md
survey.md
spec.md
test-plan.md
plan.md
evidence.md
walkthrough.md
review.md
```

Sáu file đầu thuộc giai đoạn phân tích và lập kế hoạch. Ba file cuối chỉ tạo/cập nhật khi feature đã được triển khai hoặc review. Nếu `docs/templates/` có template tương ứng thì phải dùng template đó.

## 3. Trạng thái feature

Feature đi qua các trạng thái sau:

```text
Draft → Ready for Review → Approved → Implementing → Verified → Reviewed
```

- **Draft**: đang thực hiện sáu giai đoạn lập kế hoạch.
- **Ready for Review**: đã hoàn thành `plan.md`, chưa sửa source code.
- **Approved**: người dùng đã duyệt hoặc yêu cầu triển khai rõ ràng.
- **Implementing**: đang thay đổi source code.
- **Verified**: acceptance criteria đã được kiểm tra và có evidence.
- **Reviewed**: đã review độc lập hoặc người dùng chấp nhận kết quả.

Không tự suy diễn trạng thái Approved từ việc người dùng chỉ yêu cầu “tạo feature”, “lập kế hoạch” hoặc “review”.

## 4. Sáu giai đoạn lập kế hoạch

Các giai đoạn phải thực hiện theo thứ tự. Trong toàn bộ sáu giai đoạn này không sửa source code sản phẩm.

### 4.1 Requirement — `requirement.md`

Mục tiêu là chuyển yêu cầu ban đầu thành phạm vi có thể kiểm chứng.

Nội dung bắt buộc:

- Bối cảnh và vấn đề cần giải quyết.
- Mục tiêu người dùng/nghiệp vụ.
- In scope và out of scope.
- Actor và luồng sử dụng chính.
- Functional requirements.
- Non-functional requirements: bảo mật, hiệu năng, độ tin cậy, khả năng quan sát nếu liên quan.
- Acceptance criteria đánh số, có thể kiểm thử.
- Giả định, phụ thuộc và câu hỏi chưa chốt.

Không đưa chi tiết triển khai vào Requirement nếu chưa cần để xác định hành vi.

### 4.2 Research — `research.md`

Mục tiêu là xác minh kiến thức bên ngoài mà feature phụ thuộc vào.

Nội dung có thể gồm:

- Tài liệu chính thức của framework, API provider hoặc tiêu chuẩn.
- Giới hạn API, authentication, quota, rate limit, licensing và error model.
- Các lựa chọn kỹ thuật và trade-off.
- Kết luận nào sẽ ảnh hưởng đến spec.

Quy tắc:

- Ưu tiên tài liệu chính thức và nguồn sơ cấp.
- Ghi link, ngày truy cập và phần thông tin được sử dụng.
- Phân biệt dữ kiện từ nguồn với suy luận của người viết.
- Nếu feature không cần nghiên cứu bên ngoài, ghi rõ “Không cần research bên ngoài” và lý do; không bịa nội dung để lấp file.
- Research không được dùng để khẳng định trạng thái source code nội bộ.

### 4.3 Survey — `survey.md`

Mục tiêu là khảo sát trạng thái thực tế của repository trước khi thiết kế thay đổi.

Phải khảo sát các phần liên quan trong cả hai ứng dụng khi feature đi xuyên frontend/backend:

- `vehicletracking-backend`: controller, DTO, entity, repository, service, config, migration và test.
- `vehicletracking-frontend`: component, service, type, state/data flow, style và cấu hình build.

Nội dung bắt buộc:

- Cây file liên quan.
- Luồng dữ liệu hiện tại từ UI đến API/database hoặc provider.
- Hành vi đã có, hành vi còn thiếu và điểm không nhất quán.
- Ràng buộc kỹ thuật, code đang dùng lại được và rủi ro hồi quy.
- Working tree có thay đổi liên quan hay không.
- Bảng evidence.

Mỗi nhận định về repository phải có evidence theo dạng:

```text
| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| API tạo trạm nhận DTO có validation | vehicletracking-backend/.../StationUpsertRequest.java:12 | Có thể tái sử dụng contract hiện tại |
```

Ưu tiên số dòng; nếu số dòng dễ biến động, bổ sung tên class, method hoặc symbol. Không dùng walkthrough cũ làm bằng chứng thay cho code.

### 4.4 Spec — `spec.md`

Mục tiêu là mô tả chính xác hệ thống phải làm gì sau khi feature hoàn thành.

Nội dung tùy phạm vi nhưng phải bao phủ:

- Luồng chính, luồng thay thế và trạng thái lỗi.
- API contract: method, path, request, response, status code, validation và error shape.
- Data model: bảng/cột mới, kiểu dữ liệu, nullability, default, constraint, index, foreign key và ý nghĩa từng trường.
- UI contract: bố cục, hành động, loading, empty, error, confirmation, selected state và responsive behavior.
- Quy tắc nghiệp vụ và các trường hợp biên.
- Bảo mật: nơi lưu secret, quyền truy cập, dữ liệu nhạy cảm và trust boundary.
- Tương thích ngược và migration strategy.
- Acceptance criteria cuối cùng, liên kết lại Requirement.

Spec mô tả contract, không phải bản chép code dự kiến.

### 4.5 Test-Plan — `test-plan.md`

Mục tiêu là chứng minh từng acceptance criterion sẽ được kiểm tra như thế nào.

Phải có ma trận:

```text
| AC | Mức test | Kịch bản | Dữ liệu/fixture | Kết quả mong đợi |
|---|---|---|---|---|
```

Cân nhắc các lớp kiểm tra sau:

- Unit test cho quy tắc nghiệp vụ thuần.
- Repository/integration test cho JPA, constraint và migration.
- Controller/API test cho validation, status code và response.
- Frontend test nếu dự án có test runner phù hợp.
- Type check, lint và production build.
- Manual verification cho tương tác bản đồ/UX khó tự động hóa.
- Negative case: input sai, not found, conflict, provider timeout và mất kết nối.

Không đề xuất một loại test mà repository chưa hỗ trợ nếu Plan không bao gồm việc thiết lập công cụ đó.

### 4.6 Plan — `plan.md`

Mục tiêu là chia implementation thành các bước nhỏ, có thứ tự và dễ review.

Mỗi bước phải nêu:

- Mục tiêu của bước.
- File tạo/sửa dự kiến.
- Thay đổi contract hoặc migration.
- Test sẽ thêm/chạy.
- Phụ thuộc và rủi ro.
- Điều kiện hoàn thành của bước.

Thứ tự khuyến nghị cho feature full-stack:

1. Migration và data model.
2. Repository và service nghiệp vụ.
3. DTO và API controller.
4. Frontend types và API service.
5. Component/state/UI.
6. Test tự động và kiểm tra thủ công.
7. Cập nhật evidence/walkthrough.

Thứ tự có thể thay đổi nếu dependency thực tế yêu cầu, nhưng phải giải thích trong Plan.

## 5. Planning gate

Sau khi hoàn thành `plan.md`:

- Đặt trạng thái feature là **Ready for Review**.
- Tóm tắt phạm vi, quyết định lớn, rủi ro và câu hỏi còn mở.
- Dừng lại để người dùng review nếu đó là yêu cầu ban đầu.
- Không chỉnh sửa backend/frontend, không tạo migration và không cài dependency trước khi được duyệt.

Nếu kế hoạch được chuyển cho Gemini hoặc một người/agent khác, tài liệu phải đủ rõ để bên nhận không cần đoán contract hay phạm vi.

## 6. Implementation sau khi được duyệt

Chỉ bắt đầu khi feature ở trạng thái Approved.

Nguyên tắc:

- Kiểm tra `git status` và bảo toàn thay đổi đang có của người dùng.
- Triển khai theo từng bước trong Plan; ghi lại mọi sai lệch cần thiết.
- Không sửa migration đã áp dụng; tạo phiên bản migration mới.
- Không đưa secret vào source code hoặc biến `VITE_*`.
- Không thêm chức năng “tiện thể” ngoài acceptance criteria.
- Nếu phát hiện yêu cầu phải đổi contract hoặc mở rộng đáng kể phạm vi, dừng và xin duyệt lại Spec/Plan.

## 7. Verify và Evidence — `evidence.md`

Evidence phải phản ánh kết quả thật, không phải kết quả kỳ vọng.

Ghi tối thiểu:

- Commit hoặc trạng thái working tree được review, nếu có.
- Danh sách file chính đã thay đổi.
- Mapping acceptance criterion → code/test chứng minh.
- Lệnh kiểm tra đã chạy, exit code và tóm tắt kết quả.
- Kiểm tra thủ công và ảnh chụp khi phù hợp.
- Kiểm tra migration/database.
- Kiểm tra không lộ secret.
- Các giới hạn: test bị bỏ qua, môi trường thiếu Docker/API key hoặc hành vi chưa xác minh.

Ví dụ lệnh chuẩn:

```bash
cd vehicletracking-backend
./mvnw test

cd ../vehicletracking-frontend
npm run lint
./node_modules/.bin/tsc --noEmit
npm run build
```

## 8. Walkthrough — `walkthrough.md`

Walkthrough là hướng dẫn người review hiểu kết quả:

- Bài toán đã giải quyết.
- Luồng người dùng sau thay đổi.
- Kiến trúc và luồng dữ liệu sau thay đổi.
- Điểm code quan trọng.
- Cách chạy và kiểm tra feature.
- Hạn chế còn lại.

Walkthrough không thay thế Evidence và không được tuyên bố một chức năng hoàn tất nếu test/evidence không chứng minh điều đó.

## 9. Review — `review.md`

Review phải ưu tiên finding thay vì tóm tắt.

Mỗi finding gồm:

- Mức độ: Critical, High, Medium hoặc Low.
- Vị trí file/dòng.
- Hành vi hiện tại.
- Tác động hoặc kịch bản lỗi.
- Đề xuất sửa cụ thể.

Sau findings, ghi:

- Acceptance criteria đạt/chưa đạt.
- Test đã kiểm tra và khoảng trống test.
- Kết luận: Accept, Accept with follow-up hoặc Request changes.

Nếu không có finding, vẫn phải nêu rõ phần đã khảo sát và rủi ro còn lại.

## 10. Các gate bắt buộc

- **Scope gate**: không implement ngoài in-scope.
- **Evidence gate**: nhận định về repo phải có file/code thực tế.
- **Secret gate**: không có secret trong Git, browser bundle, log hoặc tài liệu.
- **Database gate**: schema thay đổi qua Flyway migration mới và được JPA validate.
- **Contract gate**: backend DTO, frontend type và API behavior phải đồng bộ.
- **Quality gate**: test/lint/typecheck/build liên quan phải chạy hoặc được ghi rõ là chưa thể chạy.
- **Review gate**: khi người dùng yêu cầu dừng ở Plan, tuyệt đối không chuyển sang Implementation.
