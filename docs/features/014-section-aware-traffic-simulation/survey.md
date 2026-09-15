# Survey

| Nhận định | Evidence | Ý nghĩa |
|---|---|---|
| Simulator tiến theo `elapsedSeconds` và từng lấy rate từ tổng ETA | `SimulationService.advance` | Kẹt ở xa có thể ảnh hưởng xe ngay tại vị trí hiện tại. |
| ETA ghép flow đầu tiên cho mỗi section | `TrafficEtaService.calculate` | Không phản ánh nhiều đoạn flow khác nhau trên cùng section. |
| Geometry và thời lượng section đã tồn tại | `RouteDetailResponse.RouteSectionResponse` | Có thể lấy mẫu theo section mà không đổi schema. |
| HERE Flow mang geometry và speed | `HereTrafficProvider.mapFlow` | Có dữ liệu để chọn flow gần xe. |

Working tree trước implementation sạch.
