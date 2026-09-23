# Test plan

| AC | Kiểm tra | Kịch bản | Mong đợi |
|---|---|---|---|
| 1 | Browser với fixture ghi rõ | Tất cả trang có dữ liệu/error/empty | Text dễ đọc, bộ component thống nhất |
| 2 | Browser interaction | Search/filter, mở edit/detail/confirm | Hành vi cũ còn dùng được |
| 3 | Browser | Login/register, portal tài xế nội dung dài | Form/links, scroll tới nội dung cuối |
| 4 | Browser 1440/768/390 | Toàn bộ page, menu mobile, drawer | Không overflow viewport; Escape đóng và trả focus |
| 5 | Source hash + browser | File map trước/sau; map shell computed styles | Không có thay đổi maps |
| 6 | CLI | tsc --noEmit, npm run lint, npm run build | Exit 0; ghi rõ warning |

Playwright/Chromium có sẵn trong môi trường; không thêm dependency vào package.json. Fixtures chỉ được inject ở browser verification. Đây là kiểm tra frontend, không thay thế E2E backend hoặc xác thực thật.
