# Vehicle Tracking — UI/UX Design Guidelines

## 1. Mục tiêu

File này là source of truth cho toàn bộ giao diện frontend. Mọi agent khi sửa hoặc tạo UI phải đọc file này trước khi implement.

Mục tiêu:

- Giao diện nhất quán giữa các màn hình.
- Map là thành phần trung tâm.
- Phù hợp với hệ thống điều hành xe realtime.
- Ưu tiên thông tin vận hành hơn trang trí.
- Dễ mở rộng cho nhiều xe, tuyến, trạm, traffic và simulator.
- Responsive tốt trên desktop và tablet.
- Hạn chế agent tự ý tạo design pattern mới.

## 2. Design Direction

Phong cách tổng thể: **Modern Fleet Operations / Transportation Control Dashboard**.

Đặc trưng:

- Map-centric.
- Dark application shell.
- Giao diện compact.
- Data-dense nhưng dễ đọc.
- Cyan/blue làm primary accent.
- Green, amber và red dùng cho semantic status.
- Border nhẹ.
- Shadow tối thiểu.
- Không lạm dụng gradient, glow hoặc glassmorphism.
- Không tạo card cho mọi field.

Ứng dụng phải có cảm giác là:

> Một công cụ vận hành đội xe realtime.

Không phải:

> Admin CRUD dashboard thông thường.

## 3. Typography

Font chính: **Inter**.

Fallback:

```css
font-family:
  Inter,
  ui-sans-serif,
  system-ui,
  -apple-system,
  BlinkMacSystemFont,
  "Segoe UI",
  sans-serif;
```

Typography hierarchy:

| Thành phần | Kích thước / độ đậm |
| --- | --- |
| Page title | 20px / 600–700 |
| Section title | 14px / 600 |
| Body | 13–14px / 400–500 |
| Metadata | 11–12px / 400–500 |
| Important value | 14–16px / 600 |

- Numeric values nên dùng `tabular-nums` nếu có thể.
- Không dùng quá nhiều font size.
- Không dùng ALL CAPS cho đoạn text dài.
- Uppercase chỉ dành cho label nhỏ như `CHI TIẾT TRẠM`, `LỊCH TRÌNH`, `TRẠNG THÁI` và `ETA`.

## 4. Color System

Dùng semantic token thay vì hardcode màu rải rác.

Core tokens:

```css
--color-bg
--color-surface
--color-surface-elevated
--color-border

--color-text-primary
--color-text-secondary
--color-text-muted

--color-primary
--color-success
--color-warning
--color-danger
--color-info
```

Design direction:

| Vai trò | Hướng màu sắc |
| --- | --- |
| Background | Deep neutral / slate |
| Surface | Dark neutral |
| Border | Subtle neutral |
| Primary | Cyan / blue-cyan |
| Success | Green |
| Warning | Amber |
| Danger | Red |
| Text primary | Near-white |
| Text secondary | Muted slate |

- Không dùng pure black cho toàn bộ UI.
- Không dùng cyan ở mọi nơi.
- Primary accent chỉ dùng cho selected state, primary button, active navigation và important map interaction.

## 5. Spacing

Dùng spacing system thống nhất: `4`, `8`, `12`, `16`, `20`, `24` và `32`.

Không dùng random spacing như `13px`, `19px` hoặc `27px`, trừ khi thật sự cần.

## 6. Border Radius

| Thành phần | Giá trị khuyến nghị |
| --- | --- |
| Input | 8px |
| Button | 8px |
| Panel | 10–12px |
| Badge | 6px hoặc pill khi phù hợp |

Không làm mọi component thành pill.

## 7. Application Layout

Desktop mặc định:

```text
┌───────────────────────────────────────────────────────────┐
│ Topbar                                                    │
├───────────────┬───────────────────────────────────────────┤
│ Sidebar/Panel │                                           │
│               │                  MAP                      │
│               │                                           │
│               │                                           │
└───────────────┴───────────────────────────────────────────┘
```

Map phải là workspace chính cho:

- Live Tracking.
- Station Management.
- Route Management.
- Simulator.
- Traffic.

Không đặt map trong card nhỏ nếu không có lý do nghiệp vụ.

## 8. Navigation

Navigation chính nên hướng tới:

- Theo dõi xe.
- Chuyến đi.
- Tuyến đường.
- Trạm.
- Phương tiện.
- Simulator.
- Thông báo.

Không duplicate cùng navigation ở nhiều nơi.

## 9. Map Rules

Map là thành phần quan trọng nhất.

Map phải hỗ trợ:

- Station markers.
- Vehicle markers.
- Route polyline.
- Incident markers.
- Geofence circle.
- Selected state.

Map controls chỉ giữ những control cần thiết:

- Zoom.
- Fit route.
- Center selected vehicle/station.
- Traffic toggle nếu có.

Không recreate map instance khi React rerender. Không đặt quá nhiều floating button.

## 10. Station Marker

Station marker phải:

- Dễ nhận biết.
- Không che map.
- Có selected state rõ.
- Có hover state nhẹ.
- Không pulse liên tục.

Selected station nên có subtle halo.

Khi select station:

```text
select station
→ center map
→ highlight marker
→ show geofence
→ open detail drawer
```

## 11. Geofence

Geofence chỉ hiển thị cho:

- Selected station.
- Station đang tạo.
- Station đang chỉnh sửa.

Không hiển thị geofence của mọi station mặc định.

Geofence circle cần:

- Transparent fill.
- Accent border.
- Không che road label quá nhiều.

## 12. Station Management

Layout đề xuất:

```text
┌──────────────────────────────────────────────────────────┐
│ Quản lý trạm                                             │
├───────────────┬──────────────────────────────────────────┤
│ Danh sách trạm│                                          │
│               │                                          │
│ Search        │                   MAP                    │
│               │                                          │
│ Station A     │                           Detail Drawer   │
│ Station B     │                                          │
│ Station C     │                                          │
│               │                                          │
│ + Thêm trạm   │                                          │
└───────────────┴──────────────────────────────────────────┘
```

Không chia metadata thành nhiều metric card nhỏ.

Detail station nên hiển thị dạng:

```text
Tên trạm
Status

Địa chỉ
...

Tọa độ
...

Bán kính check-in
...

Cập nhật
...
```

## 13. Create / Edit Station

UI phải có mode rõ ràng:

- `BROWSE`.
- `CREATE`.
- `EDIT`.

Luồng tạo:

```text
+ Thêm trạm
→ click map
→ temporary marker
→ geofence preview
→ nhập thông tin
→ save
```

Luồng chỉnh sửa:

```text
select station
→ edit
→ marker/geofence editable
→ save / cancel
```

Cancel phải restore state cũ.

## 14. Vehicle Tracking

Khi click xe, phải hiển thị:

- Biển số.
- Loại xe.
- Model.
- Trạng thái.
- Vận tốc hiện tại.
- Vận tốc mong muốn.
- Speed limit hiện tại.
- Traffic speed.
- Trạm tiếp theo.
- ETA.
- Route.
- Trip progress.
- Last update.

Ví dụ:

```text
51B-299.88
BUS
● Đang chạy

Vận tốc hiện tại     18 km/h
Vận tốc mong muốn    45 km/h
Giới hạn đường       50 km/h
Traffic speed        18 km/h

Trạm tiếp theo
Thảo Cầm Viên
ETA 7 phút
```

## 15. Vehicle Marker

Vehicle marker phải:

- Nổi bật hơn station marker.
- Xoay theo heading.
- Có selected state rõ.
- Di chuyển mượt.
- Click được.

Không hiển thị full biển số dưới tất cả xe nếu gây clutter.

Selected vehicle có thể bật **Follow Vehicle**. Nếu user tự pan map thì follow mode nên tắt hoặc báo rõ trạng thái.

## 16. Route Visualization

Route chính phải là solid line đủ rõ.

| Route | Cách hiển thị |
| --- | --- |
| Active route | Solid |
| Alternative route | Dashed |
| Previous route | Faded |

Không dùng đường quá mảnh hoặc quá chìm.

## 17. Route Management

Layout:

```text
Map
+
Route stop list
```

Danh sách stop phải hỗ trợ:

- Reorder.
- Start/stop/end role.
- Dwell time.
- Distance.
- Estimated duration.

Route phải được vẽ theo đường thật từ routing provider, không nối chim bay giữa station.

## 18. Trip Timeline

Trip detail phải hiển thị Planned, ETA và Actual.

Ví dụ:

```text
✓ Miền Đông
  Planned 15:18
  Actual  15:19

→ Hàng Xanh
  Planned 15:22
  ETA     15:25

○ Thảo Cầm Viên
  Planned 15:26
  ETA     15:31
```

Status:

- `PENDING`.
- `APPROACHING`.
- `ARRIVED`.
- `DEPARTED`.
- `SKIPPED`.

Không hiển thị raw enum trực tiếp nếu UI có thể dùng label dễ đọc hơn.

## 19. Simulator

Simulator là development/demo tool.

Controls:

- Start.
- Pause.
- Resume.
- Stop.
- Reset.

Simulation multiplier: `1x`, `2x`, `5x` và `10x`.

Simulation speed multiplier khác vehicle speed.

Ví dụ:

```text
Vehicle speed = 35 km/h
Simulation multiplier = 5x
```

Không được hiểu hai giá trị này là một.

## 20. Traffic

Traffic có thể hiển thị:

- `NORMAL`.
- `SLOW`.
- `CONGESTED`.
- `SEVERE`.
- `CLOSED`.

Incident:

- `ACCIDENT`.
- `CONSTRUCTION`.
- `ROAD_CLOSED`.
- `CONGESTION`.
- `OTHER`.

Incident marker phải nổi bật nhưng không gây nhiễu toàn map. Click incident mở detail panel/popover.

## 21. Realtime State

Ứng dụng phải hiển thị trạng thái kết nối:

- `LIVE`.
- `RECONNECTING`.
- `OFFLINE`.

Nếu dữ liệu xe quá cũ:

- `LIVE`.
- `STALE`.
- `OFFLINE`.

Không để vị trí cũ trông giống dữ liệu realtime.

## 22. Forms

Form phải có:

- Label rõ.
- Validation gần input.
- Loading state.
- Disabled submit khi đang gửi.
- Error message dễ hiểu.
- Không dùng placeholder thay label.

Không dùng `alert()` cho validation/error.

## 23. Buttons

Hierarchy:

1. Primary.
2. Secondary.
3. Ghost.
4. Danger.

Primary dùng cho action chính:

- Thêm trạm.
- Lưu thay đổi.
- Tạo tuyến.
- Bắt đầu chuyến.

Danger dùng cho:

- Xóa.
- Hủy chuyến.
- Ngừng sử dụng.

Không đặt danger action ngang prominence với primary action.

## 24. Tables / Lists

Dùng list hoặc table phù hợp với context. Không tạo card lớn cho mỗi row nếu dữ liệu cần compact.

List phải có:

- Selected state.
- Hover state.
- Search.
- Empty state.
- Loading state.
- Error state.

## 25. Loading / Empty / Error

Mọi feature phải xem xét:

- `DEFAULT`.
- `LOADING`.
- `EMPTY`.
- `ERROR`.
- `SUCCESS`.

Realtime có thêm:

- `CONNECTED`.
- `RECONNECTING`.
- `DISCONNECTED`.
- `STALE`.

Không chỉ implement happy path.

## 26. Responsive

| Mức ưu tiên | Viewport | Cách bố trí |
| --- | --- | --- |
| Primary | Desktop ≥ 1280px | Panel + Map + contextual drawer |
| Secondary | Tablet ≥ 768px | Collapsible panel + Map + drawer overlay |

Mobile không phải môi trường vận hành chính nhưng không được overflow nghiêm trọng.

## 27. Accessibility

Bắt buộc:

- Keyboard focus visible.
- Icon button có `aria-label`.
- Form input có label.
- Sufficient contrast.
- Selected state không chỉ dựa vào màu.
- Touch target hợp lý.

## 28. Performance

Performance đặc biệt quan trọng với map/realtime.

Không:

- Recreate map mỗi render.
- Recreate toàn bộ marker collection khi một xe update.
- Refetch toàn bộ list khi một marker thay đổi.
- Gọi routing API mỗi GPS update.
- Gọi traffic API mỗi giây.

Realtime event chỉ update state cần thiết.

## 29. Component Design

Ưu tiên feature-based structure.

Ví dụ:

```text
features/
├── stations/
├── routes/
├── vehicles/
├── trips/
├── tracking/
├── simulator/
└── traffic/
```

Map components:

- `MapView`.
- `StationMarker`.
- `VehicleMarker`.
- `IncidentMarker`.
- `RoutePolyline`.
- `GeofenceCircle`.
- `MapControls`.

Shared UI:

- `Button`.
- `Input`.
- `Select`.
- `Drawer`.
- `Dialog`.
- `Badge`.
- `EmptyState`.
- `ErrorState`.
- `LoadingState`.

## 30. Agent Rules

Mọi coding agent trước khi sửa UI phải:

- Đọc file này.
- Khảo sát component hiện tại.
- Tái sử dụng design pattern có sẵn.
- Không tự ý thêm UI framework mới.
- Không tự ý thêm font khác.
- Không thêm animation library nếu không cần.
- Không redesign ngoài phạm vi feature.
- Không hardcode màu nếu có token.
- Không duplicate component.
- Không phá API/business logic chỉ để phục vụ UI.
- Nếu requirement xung đột với `design.md`, phải báo conflict.

## 31. Definition of Done

Frontend feature chỉ hoàn thành khi:

- Tuân thủ `design.md`.
- Không phá chức năng hiện tại.
- Có loading state.
- Có error state.
- Có empty state nếu applicable.
- Form có validation.
- Selected state rõ.
- Responsive ở viewport mục tiêu.
- Không có console error rõ ràng.
- Không có layout overflow.
- Không làm map rerender/recreate không cần thiết.
- Interactive control có accessible label.

