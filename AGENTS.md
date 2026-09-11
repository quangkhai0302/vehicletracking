# Hướng dẫn làm việc với repository

File này áp dụng cho toàn bộ repository `vehicletracking`. Mục tiêu là giữ dự án gọn, đúng phạm vi và mọi thay đổi đều có thể kiểm chứng từ source code thực tế.

## 1. Ngôn ngữ và cách trao đổi

- Trao đổi với người dùng bằng tiếng Việt, trừ khi người dùng yêu cầu ngôn ngữ khác.
- Nêu kết quả và tác động trước, sau đó mới giải thích chi tiết kỹ thuật.
- Không tự mở rộng phạm vi. Nếu một thay đổi chưa được yêu cầu hoặc chưa được duyệt trong feature, không triển khai nó.
- Phân biệt rõ ba loại thông tin: điều đã tồn tại trong code, điều suy luận và điều đang đề xuất.

## 2. Cấu trúc dự án hiện tại

- `vehicletracking-backend/`: Spring Boot, Java 26, Maven, Spring MVC, Spring Data JPA, PostgreSQL và Flyway.
- `vehicletracking-frontend/`: React, TypeScript, Vite và Leaflet.
- `docs/`: tài liệu kiến trúc, feature, kế hoạch, bằng chứng và review.
- `compose.yaml`: các dịch vụ phục vụ môi trường phát triển cục bộ.

Backend tổ chức theo feature. Một feature nghiệp vụ như `station` có thể chứa các package `controller`, `dto`, `entity`, `repository` và `service`. Không gom toàn bộ entity, controller hoặc service của mọi feature vào các package dùng chung ở cấp ứng dụng.

Frontend hiện có `components`, `services`, `types` và `data`. Component chịu trách nhiệm hiển thị/tương tác; lời gọi HTTP đặt trong `services`; kiểu dữ liệu dùng chung đặt trong `types`. Không đặt logic gọi API trực tiếp rải rác trong component nếu có thể tách thành service.

## 3. Quy trình bắt buộc

- Đọc [docs/workflow.md](docs/workflow.md) trước khi tạo hoặc thay đổi một feature.
- Feature mới phải có ID kế tiếp và thư mục `docs/features/<NNN>-<slug>/`.
- Nếu người dùng yêu cầu các giai đoạn Requirement → Research → Survey → Spec → Test-Plan → Plan, chỉ tạo tài liệu đến hết `plan.md`, sau đó dừng để người dùng review. Không implement source code trong lượt đó.
- Chỉ triển khai sau khi kế hoạch được người dùng chấp thuận hoặc người dùng yêu cầu trực tiếp việc triển khai.
- Sửa lỗi nhỏ hoặc cập nhật tài liệu có thể dùng quy trình rút gọn theo `docs/workflow.md`, trừ khi người dùng yêu cầu quy trình đầy đủ.

## 4. Evidence và khảo sát repository

- Trước khi nhận xét hoặc lập kế hoạch, phải đọc source code, cấu hình, migration và test liên quan.
- Mọi nhận định về repository trong tài liệu feature phải dẫn evidence bằng đường dẫn file và dòng hoặc tên symbol cụ thể.
- Tài liệu cũ, walkthrough và lời mô tả không phải bằng chứng rằng code hiện tại đã implement chức năng.
- Không tuyên bố test/build thành công nếu chưa chạy lệnh tương ứng. Ghi rõ lệnh, kết quả và giới hạn môi trường nếu có.

## 5. Quy ước backend

- Dùng Spring Data JPA cho persistence nghiệp vụ. Class ánh xạ bảng đặt trong package `entity` và dùng hậu tố `Entity` khi cần phân biệt với DTO/domain model.
- Không trả JPA entity trực tiếp ra API. Controller nhận/trả DTO; service chịu trách nhiệm nghiệp vụ và chuyển đổi dữ liệu.
- Controller chỉ xử lý HTTP, validation đầu vào và chuyển tiếp sang service. Không đặt truy vấn hoặc nghiệp vụ trong controller.
- Ranh giới transaction đặt tại service. Các thao tác ghi dùng transaction; thao tác đọc có thể dùng read-only transaction khi phù hợp.
- Flyway là nguồn sự thật của schema. Giữ `spring.jpa.hibernate.ddl-auto=validate`; không dùng `create`, `create-drop` hoặc `update` làm cơ chế quản lý schema.
- Migration đã được chia sẻ hoặc áp dụng không được sửa nội dung. Thay đổi schema bằng migration phiên bản mới.
- Ràng buộc quan trọng phải có ở cả validation/API và database khi phù hợp, ví dụ `NOT NULL`, `UNIQUE`, `CHECK` và foreign key.
- Lombok dùng để giảm boilerplate, nhưng không dùng `@Data` tùy tiện cho JPA entity. Tránh sinh `equals`, `hashCode` hoặc `toString` dựa trên quan hệ lazy và toàn bộ field.
- Không thêm abstraction, filter, wrapper response hoặc infrastructure chỉ vì “có thể sẽ cần”. Mỗi thành phần mới phải phục vụ acceptance criterion cụ thể.

## 6. Quy ước frontend

- TypeScript phải giữ type rõ ràng; tránh `any` nếu có thể mô hình hóa dữ liệu.
- API base URL có thể cấu hình bằng biến public như `VITE_API_BASE_URL`. Secret và API key của nhà cung cấp tuyệt đối không đặt trong biến `VITE_*`.
- HERE hoặc nhà cung cấp bên thứ ba cần secret phải được gọi qua backend. Frontend chỉ gọi API nội bộ của ứng dụng.
- State của form/drawer/panel đặt gần luồng sở hữu nó; state bản đồ và dữ liệu nghiệp vụ không được trộn thành một component khổng lồ.
- Khi tạo layer, marker, listener hoặc timer cho bản đồ, phải có cleanup tương ứng.
- Dữ liệu mock phải được đặt tên rõ là mock/simulator và không được âm thầm dùng như dữ liệu vận hành thật.
- UI quản lý cần ưu tiên trạng thái chọn, tạo, sửa, xóa, loading, lỗi, empty state và xác nhận hành động phá hủy; không chỉ hiển thị bản đồ.

## 7. Bảo mật và cấu hình

- Không hardcode hoặc commit API key, mật khẩu, token và connection string có credential thật.
- Không đọc hoặc in giá trị secret từ `.env` vào log, tài liệu, test output hay câu trả lời.
- File `.env.example` chỉ chứa tên biến và giá trị mẫu vô hại.
- Mọi biến `VITE_*` đều được xem là công khai vì được đóng gói vào trình duyệt.
- Validate cấu hình bắt buộc khi ứng dụng khởi động và trả lỗi có kiểm soát khi nhà cung cấp bên ngoài không khả dụng.

## 8. Kỷ luật thay đổi

- Kiểm tra `git status` trước và sau khi sửa. Mọi thay đổi có sẵn được xem là của người dùng; không ghi đè hoặc hoàn tác chúng.
- Chỉ sửa file thuộc phạm vi yêu cầu. Không xóa code, migration hoặc dữ liệu nếu chưa được người dùng cho phép rõ ràng.
- Ưu tiên thay đổi nhỏ, dễ review; tránh refactor diện rộng đi kèm feature nếu không cần thiết.
- Không commit hoặc push nếu người dùng chưa yêu cầu.

## 9. Kiểm tra tối thiểu

Chạy các kiểm tra liên quan tới phần đã thay đổi, trong phạm vi môi trường cho phép.

Backend:

```bash
cd vehicletracking-backend
./mvnw test
```

Frontend yêu cầu Node.js từ 22.12; repository hiện định hướng Node 24 qua `.nvmrc`:

```bash
cd vehicletracking-frontend
npm run lint
./node_modules/.bin/tsc --noEmit
npm run build
```

Nếu test tích hợp cần Docker/PostgreSQL nhưng môi trường không đáp ứng, ghi rõ test nào chưa chạy và lý do; không thay nó bằng tuyên bố phỏng đoán.

## 10. Điều kiện hoàn thành

Một thay đổi chỉ được coi là hoàn thành khi:

- Acceptance criteria trong spec đã được đáp ứng.
- Migration, API contract, type và UI nhất quán với nhau.
- Kiểm tra phù hợp đã chạy và có evidence.
- Không lộ secret và không phát sinh thay đổi ngoài phạm vi.
- Tài liệu feature phản ánh đúng trạng thái code sau cùng.
