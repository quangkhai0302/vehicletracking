# Kế hoạch 004

> Kế hoạch dưới đây thuộc bản dashboard trước đó. Người dùng đã duyệt triển khai revision [Map-First](map-first-design.md); phạm vi, bước làm và kết quả hiện hành: [map-first-implementation.md](map-first-implementation.md).

1. Đối chiếu handoff với source, migration, cấu hình và test; ghi tiến độ và lộ trình tại `docs/PROJECT_PROGRESS.md`.
2. Thay application shell bằng sidebar + header, dùng CSS grid cho cột thao tác và bản đồ. Danh sách và drawer cùng một cột; giữ component được mount để bảo toàn tìm kiếm/form.
3. Chuẩn hóa màu sáng, cỡ chữ, nút, card, input; giữ sidebar tối để phân cấp điều hướng. Tối ưu luồng chọn vị trí trên mobile.
4. Gắn ResizeObserver tại vòng đời Leaflet để invalidateSize khi layout thay đổi; kiểm tra việc fit route khi quay lại bản đồ.
5. Chạy kiểm tra frontend và browser fixture; ghi giới hạn backend/test HERE live, cập nhật handoff.

Đây là kế hoạch cho phần UI được yêu cầu triển khai trong lượt này. Các chức năng nghiệp vụ tiếp theo chỉ được đề xuất trong lộ trình, chưa triển khai.
