# Research

Google mô tả style poi/labels/visibility tại https://developers.google.com/maps/documentation/tile/style-reference (đọc 2026-09-14), nhưng contract đó dùng JSON trong session request của Map Tiles API, không chứng minh tham số cho endpoint `/vt` hiện có.

Thử trực tiếp tile z17/x104384/y61588: `apistyle=s.t:poi|e:labels|p.v:off` KHÔNG ẩn POI; ảnh đã được xem. Biến thể `s.t:poi|s.e:labels|p.v:off` cần kiểm chứng, không được xem là contract chính thức. Lần tải kiểm chứng biến thể này bị automatic approval review chặn do usage limit. Không tự chuyển sang dịch vụ cần billing/key.

