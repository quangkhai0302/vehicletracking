# Research: Công nghệ và Thiết kế cho Station Management

## 1. Nghiên cứu bên ngoài
Không cần nghiên cứu thư viện bên ngoài mới.
Hệ thống sử dụng các thư viện sẵn có:
- **React 18 / Vite**: Quản lý state UI cục bộ và render luồng component.
- **Leaflet 1.9**: Quản lý bản đồ, custom divIcon HTML/CSS cho markers, và L.circle cho geofence.
- **Lucide React**: Bộ icon trực quan cho hệ thống giao thông và điều hành.

## 2. Nghiên cứu chuẩn thiết kế từ `docs/design.md`
- **Mục 2 - Fleet Operations Aesthetic**: Giao diện tối (Dark application shell), compact, mật độ thông tin cao, ưu tiên tính công thái học trong vận hành.
- **Mục 3 - Typography**: Font chính là **Inter**, số liệu sử dụng `tabular-nums`.
- **Mục 4 - Semantic Tokens**: `--color-bg`, `--color-surface`, `--color-border`, `--color-primary`, `--color-text-*`.
- **Mục 10, 11 - Markers & Geofence**: Leaflet marker có anchor chuẩn xác, halo viền sáng khi selected, geofence circle chỉ bật cho selected/draft station.
- **Mục 12, 13 - Station Management Layout & Modes**: Cột danh sách trạm bên trái, bản đồ ở giữa, detail drawer bên phải. 3 chế độ `BROWSE`, `CREATE`, `EDIT`.
