# Spec

- `GET /api/v1/traffic/map-tiles/{style}/{z}/{x}/{y}` nhận `ROADMAP`, `SATELLITE`, hoặc `DARK`.
- Backend chuyển đổi style sang HERE Raster Tile API, trả đúng `image/png` hoặc `image/jpeg`, cache public một ngày.
- Frontend dùng endpoint này làm Leaflet base layer; `GET /api/v1/traffic/tiles/...` là HERE traffic overlay riêng.
- Khi HERE không khả dụng, proxy trả tile trong suốt thay vì lộ secret hoặc làm crash UI.
