# Test Plan 021 — Quản lý tài xế

| AC | Mức test | Kịch bản | Dữ liệu/fixture | Kết quả mong đợi |
| --- | --- | --- | --- | --- |
| AC-01 | Service/controller | CRUD, normalize GPLX, validation, duplicate | Driver hợp lệ/sai/trùng | 201/200/400/409 đúng contract |
| AC-02 | Service | Deactivate idempotent; block khi đang dùng | Driver free/assigned | active=false hoặc 409 |
| AC-03 | Service/PostgreSQL integration | Gán/bỏ xe; hai xe tranh cùng driver | 2 xe active, 1 driver | Chỉ một assignment thành công |
| AC-04 | Service/controller/UI | Form preselect driver xe; API nhận null/override | Xe có driver A, chọn null/B | UI mặc định A; API lưu null hoặc snapshot B đúng request |
| AC-05 | Service/controller | Đổi/bỏ driver scheduled; thử khi running | Trip theo lifecycle | scheduled thành công, running 409 |
| AC-06 | PostgreSQL/service | Sửa driver/gán xe sau khi tạo trip | Trip snapshot trước thay đổi | Snapshot trip giữ nguyên |
| AC-07 | Service/PostgreSQL integration | Hai trip start cùng driver; inactive driver | 2 trip scheduled | Một start; trip kia 409/constraint |
| AC-08 | Manual/browser | Tab/list/form/search/filter/assign/confirm, mobile | API fixture hoặc backend local | UI đúng state và responsive |
| AC-09 | Quality | Backend suite, lint, typecheck, build | Repository hiện tại | Lệnh exit 0 hoặc ghi giới hạn |

## Lệnh kiểm tra

```bash
cd vehicletracking-backend
./mvnw test

cd ../vehicletracking-frontend
npm run lint
./node_modules/.bin/tsc --noEmit
npm run build
```

Frontend hiện không có React component test runner; không bổ sung dependency ngoài phạm vi. Manual verification phải bao phủ loading, empty, error, pending lock và confirm destructive action.