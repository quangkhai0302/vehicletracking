# 016 — HERE Vector Tiles với WebGL

Trạng thái: Verified

Thay nền đường bộ raster bằng HERE Vector Tiles được MapLibre GL render qua WebGL, nhưng giữ nguyên Leaflet và toàn bộ layer vận hành hiện có.

## Acceptance criteria

1. Đường bộ và Ban đêm render bằng HERE Vector Tile API qua MapLibre GL.
2. API key không xuất hiện ở trình duyệt; style, sprite, glyph và vector tile HERE được proxy qua backend có allow-list host.
3. Xe, trạm, tuyến, traffic overlay và điều khiển Leaflet vẫn hoạt động.
4. Vệ tinh tiếp tục dùng raster vì imagery không phải vector data.
