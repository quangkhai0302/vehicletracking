# Walkthrough — bật và kiểm tra Feature 018

## 1. Điều kiện chạy

- Backend dùng Java 26 và PostgreSQL; khởi động lại để Flyway áp dụng V12.
- Frontend dùng Node 24 (tối thiểu 22.12).
- Bật Routes API và Map Tiles API trong Google Cloud project có billing/quota phù hợp. Hạn chế key theo đúng API và môi trường backend; không đặt key vào biến `VITE_*`.
- Giữ HERE Traffic để lấy Flow/Incidents. Google và HERE có thể dùng hai key riêng theo chính sách tài khoản.

## 2. Cấu hình backend

Đặt các biến trong môi trường backend, không commit giá trị thật:

```dotenv
ROUTING_PROVIDER=GOOGLE
GOOGLE_ROUTES_ENABLED=true
GOOGLE_ROUTES_API_KEY=<server-side-key>
GOOGLE_MAP_TILES_ENABLED=true
GOOGLE_MAP_TILES_API_KEY=<server-side-key>
GOOGLE_ROUTES_REFRESH_SECONDS=60
GOOGLE_ROUTES_MAX_CONCURRENT_REQUESTS=4
GOOGLE_ROUTES_MAX_REQUESTS_PER_MINUTE=60
GOOGLE_ROUTES_MAX_REQUESTS_PER_DAY=1000
```

Frontend chỉ cần URL backend và công tắc public, không có secret:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_GOOGLE_MAP_TILES_ENABLED=true
```

Nếu Google chưa sẵn sàng, giữ `ROUTING_PROVIDER=HERE` và hai flag Google `false`; tuyến HERE đã lưu tiếp tục dùng HERE. Đổi default không chuyển các tuyến cũ.

## 3. Kiểm tra thao tác

1. Tạo hai xe: một ô tô và một xe máy.
2. Tạo tuyến mới, chọn đúng loại phương tiện, chọn 2–3 trạm và lưu. Chi tiết phải ghi provider GOOGLE, encoding Google và line bám đường.
3. Mở **Kéo chỉnh đường đi**, kéo tuyến, tính lại. Có thể lưu bản sao bằng Google hoặc HERE. Tắt “bản sao” phải quay về provider của tuyến gốc.
4. Tạo chuyến bằng xe cùng loại. Xe khác loại phải bị từ chối rõ, không tạo dữ liệu một phần.
5. Chọn xe/chuyến. ETA phải có nhãn Google; sự cố/tốc độ phân tích có nhãn HERE. Khi chọn tuyến Google, không được thấy hai lớp màu flow chồng nhau.
6. Chạy simulator ở 1×/5×/10×. Đoạn NORMAL đi nhanh hơn SLOW/JAM; tổng thời gian theo Google duration và số km/h không nhân hệ số phát.
7. Dùng fixture hoặc tình huống đủ ngưỡng reroute. Revision phải giữ mọi trạm và điểm dẫn đường còn ở phía trước; nếu geometry không nối được thì không teleport xe.

## 4. Kiểm tra trước production

Chạy backend `./mvnw test` trên máy có Docker/PostgreSQL và frontend `npm run lint`, `./node_modules/.bin/tsc --noEmit`, `npm run build` bằng Node 24. Sau đó canary một tuyến/xe, đo request count/latency/chi phí và xác nhận điều khoản lưu dữ liệu. Chỉ đổi default sang GOOGLE sau khi các bước này đạt.
