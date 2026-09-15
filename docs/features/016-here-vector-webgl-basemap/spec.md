# Spec

- Backend có `GET /api/v1/traffic/vector-styles/{ROADMAP|DARK}` và `GET /api/v1/traffic/vector-resources?url=...`.
- Resource proxy chỉ chấp nhận HTTPS host `vector.hereapi.com` hoặc `assets.vector.hereapi.com`; backend thay API key trước upstream request.
- Frontend dùng `@maplibre/maplibre-gl-leaflet` với `interactive:false`; Leaflet nhận pan/zoom và render layer nghiệp vụ phía trên.
- `here-satellite` vẫn đi qua raster proxy.
