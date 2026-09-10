# Vehicle Tracking Frontend

React + TypeScript + Vite skeleton.

Yêu cầu Node.js 22.12 trở lên (khuyến nghị Node.js 24).

## Cấu hình môi trường

Tạo file `.env` từ `.env.example`:

```bash
cp .env.example .env
```

Biến môi trường hỗ trợ:
- `VITE_API_BASE_URL`: Địa chỉ API backend (mặc định `http://localhost:8080`).

> [!NOTE]
> Frontend tuyệt đối không lưu trữ, quản lý hoặc gửi bất kỳ API Key/Secret của các nhà cung cấp bên thứ ba (như HERE API). Mọi truy vấn giao thông hoặc dịch vụ ngoài đều được điều hướng qua backend proxy an toàn.

## Khởi chạy phát triển

```bash
npm install
npm run dev
```

## Kiểm tra lint và build

```bash
npm run lint
npm run build
```
