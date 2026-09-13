# Map-First Canvas — Thiết kế thay thế layout 004

Ngày 2026-09-13. Trạng thái: **đã được người dùng duyệt và triển khai phần UI Map-First vào source ứng dụng**. Phạm vi hiện thực và evidence ở [map-first-implementation.md](map-first-implementation.md). Các luồng realtime/simulator/traffic dưới đây vẫn là thiết kế đích; runtime hiển thị chưa kết nối cho các dịch vụ chưa tồn tại. Đây là revision thiết kế của 004; không chiếm ID 005 dự kiến cho xe/chuyến đi.

## 1. Hướng thiết kế

Bản đồ là mặt phẳng làm việc duy nhất, chiếm `100vw × 100dvh`. Người điều hành giữ được vị trí xe, các điểm dừng và sự cố trong cùng ngữ cảnh khi đổi chế độ. Các công cụ nằm trên bản đồ, có thể thu gọn theo nhu cầu.

Chọn **thanh điều hướng ngang phía trên** để dành hai cạnh trái/phải cho công cụ nghiệp vụ. Ba chế độ: **Theo dõi · Tuyến & trạm · Mô phỏng**. Việc đổi mode thay nội dung/độ mở panel, giữ map instance, camera và selection. Trong mô phỏng, nhãn SIM luôn có trên xe, panel và sự kiện; dữ liệu thật có nguồn và thời điểm cập nhật riêng.

Prototype: [map-first-prototype.html](map-first-prototype.html). Đây là sơ đồ tương tác bằng SVG với tọa độ, tốc độ, ETA và sự kiện minh họa; không gọi backend, HERE hoặc dịch vụ bản đồ. Nền SVG không mô tả chính xác địa lý/đường đi thực tế. Prototype phục vụ review bố cục, không phải simulator hoặc hệ thống GIS đã triển khai.

## 2. Bố cục và quy tắc tránh che khuất

| Vùng | Desktop đề xuất | Nội dung và hành vi |
| --- | --- | --- |
| Canvas bản đồ | `inset: 0`, độc lập DOM với panel | Basemap tối, flow, incidents, route, stop, vehicle, geofence, preview |
| ModeBar | Cách cạnh 16px; cao 52–56px | Logo gọn, mode, tìm xe/trạm theo ngữ cảnh, nguồn dữ liệu và trạng thái kết nối; không tự hiện LIVE khi chưa có dữ liệu |
| ContextDrawer trái | `left:16px; top:88px; width:344px; bottom:48px`; cho phép tới 380px trên màn hình lớn | Route/station list, editor hoặc stop timeline; header/footer cố định, thân cuộn; thu gọn thành nút mở 44px |
| SimulatorPanel phải trên | `right:16px; top:88px; width:304px`; 320–380px cao tùy nội dung | Xe/chuyến, playback, clock, telemetry, bộ tạo sự cố; có chế độ thu gọn còn một dòng |
| AlertStream phải dưới | Cùng cột phải; bám đáy cách 48px; tối đa 240px | 3–4 sự kiện gần nhất, số chưa đọc, bộ lọc; mở rộng lịch sử theo yêu cầu |
| MapControls | Dưới giữa vùng bản đồ trống, cao 40px | Zoom, fit tuyến, theo xe, layers; attribution vẫn ở cạnh dưới bản đồ |
| InteractionHint | Phía trên vùng bản đồ trống | “Chọn vị trí trạm” hoặc “Chọn đoạn đường tạo kẹt xe” + Hủy; chỉ một công cụ đặt điểm hoạt động |

Trên 1440px, vùng trung tâm còn khoảng 728px giữa hai cột. Bản đồ vẫn được render bên dưới mọi panel, nhưng các mục tiêu được đặt trong **vùng nhìn an toàn**: toàn bộ canvas trừ hình chữ nhật panel đang mở. `fitBounds`/pan dùng padding theo kích thước panel thật. Khi panel đổi kích thước, cập nhật vùng an toàn; chỉ pan tối thiểu để giữ mục tiêu được chọn, không tự reset zoom mỗi lần có GPS.

Panel ngoài vùng nội dung dùng `pointer-events:none`; card/nút bên trong dùng `pointer-events:auto`. Cuộn, kéo reorder và chọn text trong panel không pan/zoom map. Kéo map bằng tay tạm tắt follow; nút “Theo xe” cho phép bật lại. Không dựng thêm drawer bên phải khi đã mở simulator: chi tiết xe mở rộng trong chính panel đó.

**Khả năng thích ứng:**

- ≥1280px: trái + simulator + alert stream mở đồng thời.
- 900–1279px: trái 320px; cột phải mặc định thu gọn khi editor mở. Người dùng mở simulator thì drawer trái thu nhỏ, tránh phần nhìn map quá hẹp. Cảnh báo có chip đếm, mở theo yêu cầu.
- <900px hoặc viewport thấp: chỉ một panel nghiệp vụ mở tại một thời điểm. Dùng bottom sheet với mức thu gọn 64px, xem nhanh khoảng 40%, sửa chi tiết tối đa 75% chiều cao; thanh mode gọn ở trên. Sheet có nút mở/thu gọn, không bắt buộc dùng cử chỉ kéo.
- Khi chọn điểm/đoạn trên mobile, sheet về mức 64px; đặt xong quay lại form với dữ liệu nguyên vẹn. Không mở panel khác che tool đang hoạt động.

## 3. Kiến trúc component đề xuất

```text
MapFirstApp
├── MapViewport                       # một instance Leaflet tồn tại xuyên mode
│   ├── BasemapLayer
│   ├── TrafficFlowLayer
│   ├── TrafficIncidentLayer
│   ├── RouteLayer                    # planned / active / proposed alternative
│   ├── StationLayer                  # role và trạng thái visit tách riêng
│   ├── VehicleLayer                  # marker heading, selection, tooltip
│   └── InteractionPreviewLayer       # draft marker, geofence, event preview
├── OverlayLayout                     # desktop docking / mobile bottom sheet
│   ├── ModeBar
│   ├── ContextDrawer
│   │   ├── RouteStationTabs
│   │   ├── RouteList / StationList
│   │   ├── StationEditor
│   │   └── RouteEditor
│   │       ├── RouteSummary
│   │       ├── SortableStopList
│   │       │   └── StopRow           # role, dwell, planned/ETA/actual, status
│   │       └── CalculationFooter
│   ├── SimulatorPanel
│   │   ├── SimulationRunSelector
│   │   ├── PlaybackControls          # Play/Pause/Reset, 1×/5×/10×
│   │   ├── VehicleTelemetryCard
│   │   └── TrafficEventComposer
│   ├── AlertStream
│   │   ├── AlertFilters / UnreadCount
│   │   ├── AlertItem
│   │   └── RouteChangeReview
│   ├── MapControls / LayerSwitcher / MapLegend
│   └── InteractionHint
└── DialogHost / ToastAnnouncer
```

**Ranh giới state và dữ liệu:**

| Chủ sở hữu | State/trách nhiệm |
| --- | --- |
| App/selection state | mode, selectedVehicleId, selectedTripId, selectedRouteId, selectedStopOccurrence; chia sẻ để map/list/alert đồng bộ |
| OverlayLayout | Panel expanded/collapsed, mobile sheet, kích thước thực của vùng che map |
| Map interaction state | Idle / PickStation / MoveStation / PlaceTrafficEvent, follow target; chỉ một công cụ map hoạt động |
| RouteEditor / StationEditor | draft, dirty, errors, reorder, selection tạm; không trộn vào telemetry tick |
| Route query/calculation state | idle/loading/ready/error/stale, requestId + draftVersion; response cũ không ghi đè draft mới |
| Simulation session | runId, stopped/running/paused/completed, multiplier, simulatedAt; lệnh được backend xác nhận khi có engine |
| Data subscriptions | telemetry/ETA/check-in/notification theo trip; source, timestamp, freshness, eventId, revision |
| Map layer hooks | Tạo/cập nhật marker, line, listener và cleanup; update marker theo ID, không rebuild toàn bộ map mỗi tick |

HTTP ở `services`; state map và form tách thành hook/controller gần luồng sở hữu. Backend là nguồn quyết định check-in, ETA và revision; UI hiển thị sự kiện xác nhận. Marker có thể nội suy để chuyển động mượt nhưng không dùng vị trí nội suy UI làm bằng chứng check-in.

Không mặc định cần global state library. Dùng context/reducer nhỏ cho selection, state cục bộ cho editor, ref cho map. Chỉ thêm abstraction phục vụ các ranh giới trên.

## 4. Nội dung panel

### Drawer trái — tuyến và trạm

Header: tên tuyến, badge Nháp/Đã lưu/Đang chạy; overview ngắn gồm quãng đường, thời gian dự kiến và độ mới dữ liệu. Hai tab “Điểm dừng”/“Trạm” thay hai panel riêng.

Một `StopRow` gồm:

```text
⠿  04  Công viên Hoàng Văn Thụ       [Trạm dừng]
        Kế hoạch 08:12 · ETA 08:17   [+5 phút]
        Dừng 60 giây                [⋯]
```

- Stop đã qua: thay ETA bằng **Đã đến 08:09** và badge check-in xanh; nếu chỉ đi ngang, event mô tả “đi qua vùng trạm”, không tuyên bố xe đã dừng đón khách.
- Stop kế tiếp: nhấn mạnh số thứ tự và “Còn 3 phút”; planned/ETA dùng giờ tuyệt đối, remaining dùng thời lượng, tránh lẫn hai kiểu.
- Snapshot chưa nối ETA: ghi “Ước tính khi tạo tuyến”, thời điểm tính; không gọi dữ liệu này realtime.
- START/STOP/END là vai trò của stop occurrence trong tuyến; station trong danh mục là địa điểm dùng lại được. Dùng occurrence ID để drag/reorder và key, tránh lỗi cùng station xuất hiện nhiều lần.
- Menu tách **Bỏ khỏi tuyến** với **Ngừng sử dụng trạm**. Bỏ một stop không xóa station master. Ngừng sử dụng có xác nhận và hiển thị ảnh hưởng; quy tắc cập nhật trip đang chạy cần backend quy định.
- Station editor thay nội dung trong drawer trái, có nút quay lại draft tuyến, giữ draft và focus. Form có tên, địa chỉ, tọa độ, bán kính, chọn trên map/kéo marker và validation.

**Reorder:** drag handle riêng, placeholder và marker đánh số đồng bộ; hỗ trợ nút Lên/Xuống, bàn phím pick/drop bằng Space, di chuyển bằng mũi tên, Escape hủy, thông báo thứ tự mới qua live region. Không phụ thuộc drag-and-drop để hoàn thành thao tác. START/END tính lại theo thứ tự, dwell endpoint trở về 0 với thông báo rõ, ngăn trùng liên tiếp.

Sau reorder, draft chuyển “Cần tính lại”; ETA cũ bị đánh dấu stale ngay. Preview có debounce sau khi thả/thay đổi hợp lệ, không gọi provider mỗi pixel drag. Trong giai đoạn chỉ có API POST tạo snapshot, nút **Tính & lưu tuyến mới** là hành động rõ ràng; không gọi POST ngầm để giả preview. Khi bổ sung preview API, trả geometry/metrics mà không tạo record; Save snapshot riêng.

### Simulator phải trên

Header “Mô phỏng” + SIM + chọn run/xe. Play/Pause/Reset và **1× / 5× / 10×**; số nhân là tốc độ thời gian giả lập, không nhân trực tiếp giá trị km/h hiển thị. Tách giờ mô phỏng khỏi thời điểm lấy traffic. Chạy 5×/10× không làm traffic thật trở thành dữ liệu tương lai.

Telemetry card chỉ ưu tiên: biển số, vận tốc **km/h**, trạm tiếp theo, ETA, progress và thời điểm nhận bản tin. Thiếu vị trí dùng “— / Chưa có tín hiệu”; OFFLINE, paused simulator và RUNNING hiển thị khác nhau. Không coi mọi trạng thái khác DELAYED đều là đang chạy.

Ba injector: **Tạo kẹt xe · Tai nạn · Công trường**. Chỉ mở trong phiên SIM. Bấm injector → chọn đoạn trên map → preview phạm vi/hướng/mức độ/thời hạn → áp dụng → sự kiện có nhãn “Giả lập” và nút kết thúc sự cố. Sự cố giả lập nằm trong session, không ghi thành sự cố nhà cung cấp và không ảnh hưởng trip LIVE.

Reset tạo lượt mô phỏng mới; nếu run đang chạy/draft chưa áp dụng, xác nhận nội dung sẽ bỏ; giữ lịch sử run trước. Lỗi backend không đổi nút sang Running thành công; hiển thị retry với trạng thái được xác nhận gần nhất.

### Cảnh báo phải dưới

Luồng sự kiện lưu bền (khi backend hỗ trợ) gồm thời điểm, xe, nội dung, nguồn, trạng thái read/unread, liên kết vị trí. Ví dụ minh họa:

- `08:09:16 · SIM · BUS-102 đi qua vùng check-in Trạm 4.`
- `08:10:05 · SIM · Kẹt xe trên Cộng Hòa: ETA Trạm 5 tăng 5 phút.`
- `08:10:08 · SIM · Có phương án thay thế giảm 3 phút. [So sánh]`

Check-in thường vào stream, không bật toast/modal cho mọi xe. Toast dành cho hành động trực tiếp hoặc thay đổi đáng chú ý ở trip được chọn; tối đa một toast, không che control map, không lấy focus. Sự kiện quan trọng vẫn trong stream sau khi toast hết hạn. Âm thanh chỉ khi người dùng bật.

Khi người dùng đang đọc lịch sử, ngừng auto-scroll, hiện “3 sự kiện mới”; không tự cuộn người đọc về đầu. Gộp event trùng theo ID/revision, nhóm thông báo lặp. Badge “đã đọc” độc lập với “sự cố đã kết thúc”. Click alert chọn đúng vehicle/trip/stop và pan tối thiểu tới vị trí.

## 5. Ngôn ngữ thị giác

| Token/đối tượng | Đề xuất |
| --- | --- |
| Nền | `#080F19`; basemap tối giảm nhãn POI phụ, giữ tên trục đường và địa danh cần định hướng |
| Panel glass | `rgba(13, 23, 38, .94)`; blur 12px; fallback opaque `#0D1726` |
| Border / shadow | `#29394F`; 1px; shadow `0 12px 32px rgba(0,0,0,.28)` |
| Chữ chính / phụ | `#E8F0FC` / `#A5B6CD`; không dùng opacity trên toàn card làm mờ cả chữ |
| Tuyến đang chạy | Cyan `#39D6F5`, 4–5px + casing tối 8–9px, mũi tên hướng ở mức zoom phù hợp |
| Tuyến kế hoạch / thay thế | Xám xanh nét đứt / tím `#B19CFF` nét đứt + nhãn “Đề xuất” |
| Kẹt xe / tai nạn | Amber `#FBBF24` / đỏ `#FF6375`; flow vẽ lệch/cạnh đường cyan để hai tín hiệu cùng đọc được |
| Công trường | Amber với icon rào chắn, label “Công trường”; không chỉ đổi màu |
| Check-in | Xanh `#36D399` + dấu ✓; chỉ sau event xác nhận của đúng stop occurrence |
| Mất tín hiệu | Xám `#8190A7` + icon ngắt kết nối + tuổi bản tin |
| SIM | Badge tím, viền đứt nét trên event giả lập; LIVE chỉ khi có nguồn kết nối xác nhận |
| Kích thước | Bo card 12–14px; controls 8px; spacing 8/12/16/24px |
| Chữ | System/Inter có tiếng Việt; nội dung 13–14px, hỗ trợ 12px, title 16px, vận tốc 32px; số dùng tabular numerals |

Chỉ tooltip xe được hover/focus/chọn mới mở: biển số, nguồn, vận tốc, next stop ETA và tuổi dữ liệu. Mobile tap pin tooltip, tap nền đóng; không dựa vào hover. Các marker khác dùng label tối thiểu; cluster xe/trạm khi zoom thấp, nhưng xe được chọn luôn giữ riêng.

Chuẩn thiết kế đặt mục tiêu tương phản chữ thường ≥4.5:1 trên nền panel thực; cần đo lại trong implementation, không coi mã màu đề xuất là chứng nhận đạt chuẩn. Focus ring rõ, hit target tối thiểu 44×44px cho thao tác cảm ứng, thứ tự focus theo mode→editor→simulator→alerts→map tools. Reduced motion tắt pulse liên tục và giảm nội suy. Không thông báo screen reader mỗi GPS tick; chỉ sự kiện có ý nghĩa được gộp.

## 6. Luồng tương tác chủ chốt

1. **Lập tuyến:** vào Tuyến & trạm → tạo nháp → chọn station từ list/map → sắp thứ tự/dwell → tính tuyến → xem thời gian từng stop → lưu. Khi provider lỗi, giữ draft và hành động thử lại, bỏ nhãn ETA mới nếu kết quả không hợp lệ. Sửa tuyến đang chạy tạo draft/phiên bản mới, không đổi trực tiếp trip hiện hành.
2. **Tạo/sửa station:** chọn Thêm trạm hoặc marker → drawer station editor → pick map/geofence preview → Lưu → quay về tuyến đang sửa. Trường hợp station chưa lưu mà đóng/chuyển mode có dirty confirmation; không mất tọa độ do map re-render.
3. **Chạy simulator:** chọn tuyến đã tính + run/xe → Play → marker di chuyển, card và timeline đồng bộ → Pause hoặc thay 1×/5×/10×. Chưa có engine: disabled Play với lý do, không phát live demo âm thầm.
4. **Tạo sự cố:** bấm Tạo kẹt xe → công cụ đặt đoạn → preview/áp dụng → overlay amber và alert SIM → ETA mới nếu engine trả kết quả → So sánh lộ trình khi có alternative. Escape hủy preview; sự cố ngoài tuyến không mặc định làm mọi ETA tăng.
5. **Auto check-in:** vị trí qua vùng stop → backend kiểm tra thứ tự/bán kính → event → marker/row xanh, actual time → trạm tiếp theo → stream. Không suy ra check-in từ animation hoặc progress UI. Tuyến vòng phân biệt lần ghé đầu/cuối của cùng station.
6. **Đổi lịch/reroute:** incident ảnh hưởng phần đường còn lại → ETA revision → thông báo tự động; nếu có alternative, overlay hai phương án + delta ETA/trạm ảnh hưởng. Chế độ gợi ý: người điều hành áp dụng. Chế độ tự áp dụng phải là chính sách đã bật trước; sự kiện ghi rõ “Đã đổi lộ trình” thay vì “Đề xuất”. Không có đường thay thế: giữ tuyến, báo bị chặn. Lưu baseline và lịch mới riêng.
7. **Mất dữ liệu:** timestamp quá cũ hoặc kết nối mất → badge stale/offline, marker ngừng giả tiến tiếp; ETA cuối có tuổi dữ liệu hoặc “Chưa xác định”. Nối lại lấy snapshot/reconcile revision, không tạo lại hàng loạt alert check-in.

## 7. Tái sử dụng code và phần cần bổ sung

Các đường dẫn frontend dưới đây nằm trong `vehicletracking-frontend/src/`.

| Hiện có — evidence source | Hướng thay đổi đề xuất |
| --- | --- |
| `App.tsx`, `App`; `workspace.css`, `.application-shell` là grid sidebar/header/map | Thay shell ở implementation bằng map full viewport + OverlayLayout; không chồng thêm một lớp CSS override thứ ba |
| `components/MapComponent.tsx`, `MapComponent`: Leaflet instance/layers, state station/forms, `vehicles=[]`, `plannedRouteBoundsRef` | Giữ Leaflet và logic geometry; tách state editor khỏi map, quản lý occlusion padding; giữ cleanup listener/observer |
| `components/StationDrawer.tsx`, `StationDrawer`; `services/stations.ts`, CRUD | Tái sử dụng form/validation/service, trình bày trong ContextDrawer |
| `components/route/RouteDrawer.tsx`, `RouteCreateContent`, `handleMoveUp`/`handleMoveDown`, `normalizeStops` | Giữ rule/reorder bằng nút, thêm drag handle và keyboard reorder; tách StopRow/StopTimeline |
| `components/route/RouteWorkspace.tsx`, `handleSelectRoute`, `detailAbortRef`, `createRequestIdRef` | Giữ chống response race; thêm preview độc lập khi backend hỗ trợ |
| `components/MapControls.tsx`, `MapControls` | Chuyển thành toolbar nhỏ trong vùng trống; duy trì zoom/layers/fit/follow theo mode |
| `components/SimulatorControls.tsx`, `MULTIPLIERS=[1,2,5,10]`, chưa nối App; `types/vehicle.ts`, `Vehicle` | UI mới dùng 1/5/10 theo yêu cầu; cần engine/source/timestamps/nullability, không coi presence của component là simulator đã chạy |
| `components/VehicleDrawer.tsx`, `VehicleDrawer`: branch chỉ xét DELAYED, nội dung trễ 4 phút cố định | Tái cấu trúc telemetry card; trạng thái/độ trễ phải từ dữ liệu, xử lý OFFLINE/AT_STATION/paused rõ |
| `services/hereTraffic.ts`, `fetchHereTrafficFlow`/`fetchHereIncidents` không có consumer; `types/map.ts`, Traffic types | Cần backend traffic và source/freshness; thống nhất type với `types/station.ts`/`types/route.ts`, tránh dùng Station/Route legacy trong `types/map.ts` cho editor |
| Backend `route/controller/RouteController.java`, chỉ GET/list/detail và POST create; `src/main/resources/db/migration/V3__create_routes_tables.sql`, route snapshot bất biến | Preview/revision/route edit không được giả là có sẵn; bổ sung hợp đồng API trước khi nối calculation-on-drop |
| Backend `route/dto/RouteDetailResponse.java`, `from` và offset fields; `route/service/RouteServiceTest.java`, `create_normalizesNameAndPreservesStopOrderAndCalculatesMetrics` | Phân biệt planned snapshot/ETA realtime/actual trong UI; test hiện có chỉ chứng minh phép cộng snapshot |

Backend source trong bảng thuộc `vehicletracking-backend/src/main/java/com/quangkhai/vehicletracking_backend/` trừ migration/test. Test nằm tại `vehicletracking-backend/src/test/java/com/quangkhai/vehicletracking_backend/`.

`docs/workflow.md` tiếp tục không có trong checkout (kiểm tra `Test-Path`, inventory trước thiết kế). Hồ sơ áp dụng hướng dẫn trong `AGENTS.md`, không tái dựng workflow cũ. Bản dashboard 004 vẫn tồn tại trong working tree; tài liệu này thay thế hướng thiết kế, không khẳng định app đã được đổi.

## 8. Thứ tự triển khai và tiêu chí review thiết kế

**UI trước:** shell/overlay/drawer/layer legend + station/route CRUD hiện có → interaction modes và drag reorder → thu gọn panel, mobile sheet, focus/occlusion. **Dữ liệu tiếp theo:** xe/trip/telemetry và simulator → check-in → traffic/ETA → revisions/alerts theo lộ trình nghiệp vụ. Trong lúc chưa có nguồn, panel thể hiện unavailable/empty đúng dữ liệu.

Checklist review:

- Trên 1440×900, map phủ toàn màn hình, vùng trung tâm thấy được tuyến/xe khi mọi panel mở; 1024×768 không ép ba panel cạnh nhau; 390×844 có một sheet và vùng map thao tác được.
- Thu/mở panel không khởi tạo lại bản đồ hoặc mất selection; auto-follow không kéo camera ngược khi người dùng đang khám phá map.
- Reorder bằng chuột, keyboard và nút đều đạt cùng kết quả; dirty draft giữ qua thao tác chọn điểm; response cũ không khôi phục ETA sai.
- Flow, route, incident, source SIM/LIVE, stop role và check-in status đọc được đồng thời; không mã hóa tất cả bằng màu.
- Play, event injectors, ETA và alert biểu diễn trạng thái backend hoặc có nhãn prototype; không giả live data.
- Sự kiện mới không cướp focus/scroll; toast không thay stream lưu bền; map attribution/control không bị panel che.

Prototype chỉ kiểm chứng một số tương tác minh họa: mode, collapse, chọn stop, reorder bằng nút/drag, playback 1/5/10, đặt sự cố, layer visibility và feed. Hợp đồng API, traffic/live ETA, check-in engine, precision bản đồ và mobile sheet drag vẫn là thiết kế đề xuất.

## 9. Kiểm tra artifact thiết kế

Đã chạy `node docs/features/004-operations-layout/verification/map-first-prototype-smoke.mjs`: exit 0; bảy nhóm kiểm tra prototype đạt, không có pageerror. Trình duyệt Edge headless, ảnh desktop 1440×900, tablet 1024×768, mobile 390×844. Đã xem ảnh để review vùng bản đồ, panel và chữ.

Evidence: [kết quả](artifacts/map-first/results.json), [desktop](artifacts/map-first/desktop.png), [mobile simulator](artifacts/map-first/mobile-simulator.png), [mobile editor](artifacts/map-first/mobile-editor.png). Các kiểm tra này không xác minh application/API/GPS/HERE. Không chạy lại lint/build/backend tests vì lượt này chỉ thêm artifact thiết kế và cập nhật tài liệu; các thay đổi source đã có trước lượt được giữ nguyên. `git diff --check` không báo lỗi whitespace.
