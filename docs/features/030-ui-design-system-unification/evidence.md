# Evidence: Business UI refresh (Feature 030)

## Phạm vi triển khai

Feature này chuẩn hóa lại các màn hình nghiệp vụ mà không chạm vào workspace bản đồ.
Thiết kế cuối cùng chọn business console sáng, tương phản cao và phù hợp với thao tác
quản trị; bản đồ, lớp Leaflet và các route `data-map-focus="true"` tiếp tục dùng hệ
thống map-first hiện có.

| Hạng mục | Evidence | Kết quả |
|---|---|---|
| Điểm vào stylesheet refresh | `vehicletracking-frontend/src/main.tsx:6` | `ui-refresh.css` được nạp sau các stylesheet hiện hữu để giữ nguyên logic React và API |
| Shell nghiệp vụ | `vehicletracking-frontend/src/ui-refresh.css:11-136` | Sidebar, topbar, account pill, canvas và responsive spacing dùng cùng token/màu business |
| Dashboard và các trang vận hành | `vehicletracking-frontend/src/ui-refresh.css:139-239` | Hero, KPI, heading, surface và trạng thái hiển thị nhất quán |
| Fleet workspace | `vehicletracking-frontend/src/ui-refresh.css:242-311` | Tabs, bộ lọc, thẻ xe/tài xế/chuyến, editor và nút hành động cùng một surface/form language |
| Lịch, cảnh báo, báo cáo, người dùng | `vehicletracking-frontend/src/ui-refresh.css:314-354` | Filter, list card, badge trạng thái, drawer và empty/error surface được chuẩn hóa |
| Bảo toàn bản đồ | `vehicletracking-frontend/src/ui-refresh.css:11,41,...` | Selector shell đều dùng `.business-shell:not([data-map-focus="true"])`; không có selector Leaflet/map trong file refresh |

## Kiểm tra frontend

Đã chạy tại `vehicletracking-frontend` với Node.js `v24.16.0`:

```bash
env PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH ./node_modules/.bin/tsc --noEmit
env PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run lint
env PATH=/home/khainq/.nvm/versions/node/v24.16.0/bin:$PATH npm run build
```

- TypeScript: đạt, không có lỗi.
- Lint: đạt, 0 error và 6 warning React đã tồn tại trong các file nghiệp vụ/auth.
- Production build: đạt, Vite hoàn tất với 1,954 module được transform.
- `ui-refresh.css` không có trailing whitespace.

`git diff --check` vẫn báo một trailing whitespace có sẵn tại
`vehicletracking-frontend/src/index.css:48`; dòng này không thuộc thay đổi refresh và
không được chỉnh sửa để tránh ghi đè thay đổi trước đó.

## Giới hạn kiểm chứng

Chưa chạy browser/E2E screenshot trong lượt này. Kiểm chứng trực quan còn lại là bước
manual trên các viewport desktop, tablet và mobile sau khi khởi động frontend; cần xác
nhận riêng các trạng thái dữ liệu rỗng, loading, lỗi và drawer editor.
