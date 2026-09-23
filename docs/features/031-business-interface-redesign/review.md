# Review — 031

Trạng thái: main-agent self-review sau implementation; **chưa có review độc lập**. Reviewer Sol/high đã được gọi nhưng không chạy được do giới hạn sử dụng. Không suy diễn rằng reviewer đã chấp nhận thay đổi.

## Findings trong quá trình kiểm tra (đã sửa)

1. **Medium — `src/components/business/SidePanel.tsx`, effect cleanup.** React tháo dialog khiến đóng bằng Escape không trả focus về nút mở. Đã lưu opener và restore focus khi còn connected; kiểm chứng ở 1440/390px.
2. **Medium — `src/ui-refresh.css`, `.schedule-editor footer` mobile.** Rule cũ `bottom: -20px` làm cụm nút lưu/huỷ bị cắt ở mép viewport. Đã override `bottom: 0`, thêm khoảng cuộn; browser kiểm tra trường ngày cuối và toàn bộ footer trong viewport.
3. **Low — `src/ui-refresh.css`, dashboard metric/auth input/users heading.** Cascade cũ còn để metric xếp sai cột, input auth viền kép và heading tài khoản nền tối. Đã bổ sung scoped rules, xem lại ảnh desktop/mobile.

Các đường dẫn `src/` thuộc `vehicletracking-frontend/`. Các lỗi trên được phát hiện qua browser thực tế trong quá trình triển khai, không còn tái hiện ở vòng cuối.

## Phạm vi đã rà soát

- Đối chiếu requirement/spec/plan với `ApplicationShell`, các component business mới, các page được thay đổi và CSS responsive.
- `FleetWorkspace` dùng bảng chỉ khi lockedTab; nhánh card của maps giữ nguyên callback và kiểu hiển thị.
- Theme không lan sang map qua root variables; hash file maps và computed styles kiểm tra như evidence.
- Auth/driver cuộn được; empty/error/confirmation hiển thị; native dialog không cho focus nền và phục hồi focus khi đóng.

AC1–AC6 đã có evidence trong giới hạn frontend fixture. CLI lint/typecheck/build exit 0; browser 34 mục ghi nhận, 61 ảnh, không pageerror/failure. Test native dialog chấp nhận browser chrome tại ranh giới Tab: kiểm tra page focus quay lại modal, không yêu cầu focus phải wrap trực tiếp giữa hai button như custom focus trap.

## Khoảng trống / kết luận

Main-agent self-review: **Accept with follow-up** cho phạm vi UI đã kiểm tra. Đây không thay thế independent review.

Follow-up: review độc lập khi subagent khả dụng; UAT trên dữ liệu thật và trình duyệt/thiết bị mục tiêu. Chưa xác minh E2E backend/provider, Firefox/Safari hay accessibility audit đầy đủ. Sáu lint warnings có sẵn chưa thuộc phạm vi sửa theme; chi tiết tại `evidence.md`.
