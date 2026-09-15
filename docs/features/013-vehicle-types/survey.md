# Survey

- `VehicleEntity` và migration V4 hiện chỉ lưu biển số, tên, mô tả, trạng thái; chưa có loại xe.
- `VehicleUpsertRequest`/`VehicleResponse` và `types/fleet.ts` chưa truyền loại xe.
- `TripSummaryResponse.from` đã đọc quan hệ vehicle và là nguồn dữ liệu chuyến trong operations snapshot, phù hợp để đưa loại xe đến marker mà không thêm lời gọi API.
- `useVehicleMarkers` đang dùng một SVG ô tô cho mọi vị trí; `SimulationFleetLayer` dùng emoji xe van cho mọi xe chờ.
- `VehicleEditor` chưa có lựa chọn loại; `FleetWorkspace` dùng `BusFront` cho mọi thẻ xe.
- Working tree có thay đổi feature 012 và UI từ các lượt trước; các thay đổi 013 phải giữ nguyên chúng.
