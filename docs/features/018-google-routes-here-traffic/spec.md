# Spec — thiết kế đề xuất

Trạng thái: **Approved / Implementing**. Liên kết: [requirement](requirement.md), [research](research.md), [survey/evidence E01–E18](survey.md), [implementation evidence](evidence.md). Tài liệu này giữ thiết kế đích; các khoảng trống so với code hiện tại được ghi trong [review](review.md).

## 1. Phân chia trách nhiệm

| Dữ liệu | Tuyến GOOGLE | Tuyến HERE cũ |
|---|---|---|
| Geometry, thời lượng và ứng viên thay thế | Google Routes | HERE Routing |
| ETA chính | Google, gắn geometry đang áp dụng | HERE theo logic hiện có |
| Flow, jamFactor, tốc độ quan sát, incidents | HERE, nhãn độc lập | HERE |
| Màu tuyến được chọn | Google intervals | Kiểu hiện có |
| Vị trí thật | GPS nhận từ thiết bị | GPS nhận từ thiết bị |
| Vị trí mô phỏng | Motion trên geometry Google; profile thời gian Google | Motion HERE hiện có |

Chọn provider mặc định chỉ áp dụng tuyến mới. Tuyến đã lưu pin provider/mode; không đọc default mới để đổi một chuyến đang chạy. Google lỗi: dùng dữ liệu Google còn hợp lệ theo chính sách lưu, hoặc snapshot hợp lệ có nhãn; không âm thầm đổi sang geometry/ETA HERE. HERE lỗi không làm mất Google ETA. HERE closure mới có thể báo BLOCKED dù Google còn trả ETA hữu hạn; UI không trình bày ETA đó như cam kết có thể đi qua đường đóng.

## 2. Provider và geometry

- Thay contract `calculate(List<RoutingWaypoint>)` (E01) bằng request có provider, transportMode, departureTime, purpose (CREATE/SHAPE/ETA/REROUTE), ordered stops/via và tùy chọn alternatives. Registry chọn đúng adapter; giữ adapter HERE tương thích.
- Kết quả chuẩn hóa: provider, mode, requestedDepartureAt, fetchedAt, distance/duration/staticDuration, sections theo destinationStopSequence, encoding, geometryVersion, traffic intervals và warnings.
- Domain mode: CAR → Google DRIVE, MOTORCYCLE → TWO_WHEELER. HERE adapter hiện chỉ CAR; báo unsupported rõ nếu yêu cầu mode chưa hỗ trợ. Form tuyến chọn mode; chuyến mới phải chọn xe tương thích. Tuyến cũ vẫn CAR, không đổi mode chỉ vì sửa icon xe.
- Google gửi POST `https://routes.googleapis.com/directions/v2:computeRoutes`; key header phía server; HIGH_QUALITY, ENCODED_POLYLINE, TRAFFIC_AWARE cho tính định kỳ, TRAFFIC_AWARE_OPTIMAL cho tìm ứng viên nếu tài khoản hỗ trợ. Yêu cầu TRAFFIC_ON_POLYLINE khi cần màu/profile.
- Field mask cụ thể: `routes.distanceMeters,routes.duration,routes.staticDuration,routes.polyline,routes.legs.distanceMeters,routes.legs.duration,routes.legs.staticDuration,routes.legs.polyline,routes.legs.travelAdvisory.speedReadingIntervals,routes.routeLabels,routes.travelAdvisory.speedReadingIntervals`. Không dùng `*`; kiểm tra bằng request contract test.
- Encoding enum: HERE_FLEXIBLE_POLYLINE / GOOGLE_ENCODED_POLYLINE. Backend/frontend có dispatcher dùng chung các consumer, không đoán encoding từ ký tự. Giữ nguyên thứ tự điểm Google; decode sang latitude/longitude nội bộ, chỉ đổi longitude/latitude ở boundary GeoJSON nếu dùng.
- Section Google tương ứng leg giữa hai trạm bắt buộc; via nằm trong leg và không sinh check-in. Interval là index của polyline section, không phải của tuyến ghép. Với `[start,end)` của các segment, renderer dùng điểm start đến end để vẽ đủ segment cuối; start thiếu mặc định 0. Validate `0 <= start < end <= pointCount-1`, overlap/category; sai interval bỏ màu về UNKNOWN kèm warning, geometry hợp lệ vẫn dùng được.
- Geometry không hợp lệ/thiếu leg/bỏ stop → lỗi có kiểm soát trước ghi DB; không nối bằng đường thẳng để che lỗi.

## 3. Nhiều trạm, shaping và thời gian

- Giữ 2–50 trạm và tối đa 20 shaping points toàn tuyến. Mỗi request Google tối đa 25 intermediates, đếm cả via. Chia lô tại trạm bắt buộc, lặp trạm nối giữa hai lô nhưng không tạo occurrence/check-in/dwell trùng. Một leg không vượt giới hạn vì chỉ có tối đa 20 shaping points.
- Mapping theo occurrence sequence, không theo stationId; tuyến vòng A→B→A phải có hai occurrence A riêng.
- Tính các lô theo thứ tự: departure lô sau = departure lô trước + travel + dwell thực sự trước lô sau. Google không có trường dwellSeconds tổng quát cho trạm: cộng dwell tại ứng dụng đúng một lần. ETA trong một lô có nhiều dwell là xấp xỉ về traffic theo thời điểm; trả warning `DWELL_TRAFFIC_APPROXIMATION` khi cần, không hứa mô hình tín hiệu giao thông hoàn hảo.
- Khi chỉ còn một điểm đến, vẫn dùng origin là vị trí xe và destination là trạm; mọi trạm đã hoàn thành bị loại. Check-in xảy ra tại mép bán kính không đồng nghĩa đã hoàn tất dwell; giữ trạm xe còn đang tiếp cận/dừng theo motion state (E11).
- Tạo tuyến không có giờ yêu cầu: dùng now UTC. Tuyến có departure tương lai: gửi RFC3339. Chuyến quá khứ/live replay: dùng now cho quan sát traffic, không gửi quá khứ DRIVE/TWO_WHEELER; lịch giả lập vẫn tách riêng.
- Preview/save/copy dùng một calculation ID ngắn hạn gắn request hash để tái sử dụng khi được phép, tránh tính lại nếu input không đổi; sai/hết hạn thì yêu cầu tính lại. Form sửa tuyến đã gắn chuyến chỉ cho copy như E04.

## 4. ETA theo đúng tuyến đang chạy

Đề xuất `TripEtaService` làm facade; `HereTripEtaCalculator` giữ behavior cũ, `GoogleTripEtaCalculator` xử lý Google. HERE analysis là đầu vào riêng, không tính lại Google duration bằng tốc độ HERE.

1. Chụp state tripId, attempt, revision, mode, vị trí/heading và trạm còn lại.
2. Request Google từ vị trí xe qua trạm/via còn lại. Dùng điểm ràng buộc để giữ hành lang đang chạy, trong giới hạn request.
3. So sánh thứ tự trạm, chiều đi và geometry trả về với đường đang áp dụng. Ngưỡng khởi đầu đề xuất: tối thiểu 95% chiều dài trùng trong hành lang 30m, không có đoạn lệch liên tục >100m; giao lộ/đường song song cần heading/topology, không chỉ khoảng cách. Ngưỡng phải được kiểm thử spike P0; đây không phải bảo đảm map matching chính xác tuyệt đối.
4. Geometry đổi đáng kể: lưu ứng viên ngắn hạn cho evaluator; giữ ETA cũ còn hợp lệ hoặc trả ESTIMATED/UNAVAILABLE, không báo ETA đường mới trên line đường cũ.
5. Geometry tương đương: chiếu interval về geometry đang áp dụng; không dùng trực tiếp index của polyline mới lên polyline cũ. Mapping mơ hồ → giữ profile cũ/UNKNOWN. GeometryVersion đổi chỉ khi áp revision có kiểm soát.
6. `etaAt = referenceTime + thời lượng còn lại + dwell chưa hoàn thành`; actual check-in giữ riêng. `referenceTime` là wall clock cho GPS, simulatedAt cho simulator; `trafficFetchedAt` luôn wall clock. Thời lượng Google là thời lượng chuyến dự báo, không thời gian đã chia multiplier.

Không ghi đè planned schedule bằng dynamic ETA. Lịch kế hoạch là dữ liệu riêng; phạm vi được lưu dài hạn phải được chốt ở §9.

## 5. Simulator

- Giữ buffer/RAF 1500ms, follow, pause, replay/attempt, final marker (E11–E12).
- Profile Google là **ước tính mô phỏng**, không phải tốc độ đo. Đề xuất trọng số chậm NORMAL=1, SLOW=2, TRAFFIC_JAM=4; UNKNOWN=1 có warning. Với leg duration T, segment length d và trọng số w: `t_i = T*d_i*w_i / sum(d*w)`; speed suy từ d_i/t_i. Tổng travel time khớp Google duration. Không gán category thành một km/h cố định.
- HERE speed hiển thị riêng để phân tích; không nhân thêm vào profile Google. Closure HERE xác nhận đúng tuyến có thể dừng tiến độ, pending reroute.
- Khi refresh, bảo toàn quãng đường đã đi và dwell, chỉ thay thời gian còn lại; không map elapsed cũ vào một timeline mới làm xe nhảy. Dùng progress distance + occurrence làm mốc cập nhật profile, giữ checkpoint persistence/recovery nhất quán.
- 5× thay tốc độ đồng hồ mô phỏng; number km/h không nhân 5. HERE/Google refresh vẫn theo wall clock.
- Route geometry/revision đổi: xóa animation buffer cũ, nối tại vị trí phù hợp; nếu không nối được giữ tuyến cũ và phát cảnh báo lỗi áp dụng rõ.

## 6. HERE analysis và tự đổi tuyến

- HERE Flow theo hành lang/viewport có giới hạn, cache hiện tại; không tải Flow mỗi mousemove. Ghép geometry/hướng/thời hạn và distance, báo confidence/UNKNOWN khi không đủ cơ sở. Bridge/parallel-road test bắt buộc.
- Delay trigger dùng Google ETA so với baseline Google của cùng phần đường còn lại; mặc định đồng thời >=600s và >=30%, hai snapshot Google mới, cooldown 300s. HERE Flow cao chỉ yêu cầu kiểm tra Google sớm có rate limit, không tự cộng delay.
- Closure trigger dùng hai observations HERE mới và hợp lệ; timestamp Google refresh không được tính là một HERE observation mới. Checkpoint tách source, incident ID, attempt/revision; không tái trigger một fingerprint không đổi.
- Từ vị trí hiện tại đến trạm bắt buộc kế tiếp: request alternatives khi không có via. Giữ tối đa ba ứng viên để đánh giá, rồi nối đuôi qua mọi trạm còn lại; tính lại departure đuôi theo ETA của từng ứng viên, không chọn chỉ theo chặng đầu nhanh nhất.
- Nếu có điểm dẫn đường bắt buộc trên chặng: gọi computeRoutes giữ via, không bật alternatives không hỗ trợ; có thể không tìm được detour. Không xóa via/trạm âm thầm. Hệ thống không bảo đảm tìm tuyến tối ưu toàn cục.
- Ứng viên phải khác geometry đủ ý nghĩa, đầy đủ trạm, mode đúng, nối được, không đi qua closure đã xác nhận. Google không hỗ trợ tránh HERE incident ID; hậu kiểm là bắt buộc. Không còn candidate → REROUTE_UNAVAILABLE.
- Đề xuất ngưỡng chấp nhận delay: nhanh hơn ít nhất 60s VÀ 5% so với ETA hiện tại. Closure: chấp nhận đường khả dụng tránh closure ngay cả khi lâu hơn. Cấu hình này là mới, cần review.
- Mạng ngoài transaction: đọc snapshot → gọi providers → transaction ngắn khóa trip và đối chiếu attempt/revision/progress. Kết quả cũ, xe đã hoàn thành/reset/qua trạm → bỏ; không giữ khóa DB xuyên HTTP.
- Persist revision + notification nguyên tử. Chỉ ghi “đã áp dụng” sau geometry được kiểm tra và chuyển thành công; đề xuất DTO applicationState=PENDING/APPLIED/REJECTED. GPS thật chỉ nhận kế hoạch/cảnh báo, không điều khiển tài xế tự rẽ.

## 7. API/UI contract

Giữ đường dẫn CRUD, shape preview/save/copy, trip ETA và route-geometry hiện có (E04, E11, E15). Thay backend/frontend cùng release; additive fields có fallback legacy.

| Contract | Bổ sung dự kiến |
|---|---|
| POST /api/v1/routes và PUT /api/v1/routes/{id} | transportMode?: CAR/MOTORCYCLE; departureTime?: RFC3339. Provider do backend default với create, pin provider với update. |
| Shape preview/save/copy hiện có | calculationId tùy chọn để tham chiếu preview; copy có targetProvider?: HERE/GOOGLE phục vụ chuyển có chủ đích. |
| RouteDetail / sections | routingProvider, geometryVersion; mỗi section polylineEncoding, trafficIntervals[], trafficFetchedAt?, contentExpiresAt?; số đo nullable khi hết quyền sử dụng. |
| GET /api/v1/trips/{id}/eta | source thêm GOOGLE_LIVE/GOOGLE_LAST_KNOWN; geometryVersion, attemptNumber, routeRevisionId?, referenceTime; googleStatus/hereStatus riêng, analysisSources; numeric ETA nullable khi unavailable/blocked. |
| Revision DTO / simulation metadata / SSE | routingProvider, geometryVersion, applicationState, attempt, etaSource, speedSource, fetchedAt; không trả API key/raw provider payload. |
| GET /api/v1/maps/tiles/{theme}/{z}/{x}/{y} | Proxy Google Map Tiles cố định host, validate z/x/y/theme, response image và cache headers phù hợp chính sách. |
| GET /api/v1/maps/viewport?theme=...&zoom=...&north=...&south=...&east=...&west=... | Attribution/max zoom từ provider qua backend; không trả key/session credentials. |

API vẫn dùng ProblemDetail với code ổn định: 400 validation, 404 not found, 409 stale calculation/revision hoặc route đang dùng, 422 provider unsupported/no route, 503 provider/quota unavailable, 504 timeout, 502 invalid upstream. GET ETA có snapshot hợp lệ trả 200 với source/status/warning, không giả số 0 khi unknown. Google refresh là tác vụ tính cache/candidate, GET không tự ghi revision ngoài evaluator workflow rõ ràng.

Frontend:

- Giữ panels hiện tại; hover chia “ETA/mức giao thông Google” và “Tốc độ/sự cố HERE”. Không dùng nhãn HERE LIVE cho Google.
- Một chế độ traffic tô màu tại một thời điểm: GOOGLE_ROUTE mặc định (chỉ tuyến đã chọn), HERE_AREA tùy chọn (theo viewport, Canvas + cache), OFF. HERE_AREA ẩn màu Google intervals nhưng giữ line chọn; không chồng flow Google/HERE. Thanh nổi ghi rõ phạm vi, nguồn, freshness; không nhân đôi trong Lớp bản đồ.
- HERE_AREA tận dụng Flow, cần giới hạn zoom/bbox và debounce 600ms, không tải JSON cả thành phố khi zoom nhỏ. Ẩn hoặc yêu cầu zoom gần khi quá phạm vi; không bỏ đoạn trong thuật toán ETA chỉ vì giảm chi tiết render.
- Google Map Tiles qua Leaflet là đề xuất mặc định: giữ road/satellite; kiểm tra style dark hỗ trợ ở P0 trước cam kết màu như cũ. Không coi tile API là Google TrafficLayer.
- Empty/error/expired có retry có giới hạn; response đổi xe/attempt/revision phải bị loại. Lưu shape cần preview hợp lệ; chuyển provider route cũ bằng copy có xác nhận.

## 8. Chi phí, cache và cấu hình

Các giá trị sau là đề xuất, không phải quota nhà cung cấp: refresh tối thiểu 60s/trip hoạt động, tối đa 4 request Google đồng thời toàn backend, budget mặc định 60 requests/phút và 1000/ngày; admin có thể chỉnh sau dự toán. Hết budget giữ snapshot hợp lệ hoặc UNAVAILABLE, không retry vô hạn.

- Single-flight theo provider/trip/attempt/revision/mode/request hash/time bucket; gom mọi tab/SSE/GET/tick. Partial failure retry chỉ phần cần thiết; backoff 429/5xx có jitter, tối đa 2 retry trong budget.
- Mỗi subrequest tính vào budget, gồm chia lô và đuôi ứng viên. Ví dụ 10 chuyến chạy 8 giờ, một request/phút đã là 4800 request/ngày trước alternatives/shaping; cần chủ động chọn ngân sách, không coi default đủ cho 10 xe.
- Cache TTL nghiệp vụ tối đa 60s cho traffic fresh, stale tối đa 300s **chỉ nếu quyền cache loại dữ liệu đó đã xác minh ở P0**; expiresAt pháp lý/nghiệp vụ lấy giá trị sớm hơn. Key vị trí không dùng mọi số thập phân để phá cache; dùng snapshot version và tiến độ quantized có validation không vượt trạm.
- `ROUTING_PROVIDER=here|google` (default here khi rollout); `GOOGLE_ROUTES_ENABLED`, `GOOGLE_ROUTES_API_KEY`, `GOOGLE_MAP_TILES_ENABLED`, `GOOGLE_MAP_TILES_API_KEY`, connect/read timeouts; `GOOGLE_ROUTES_REFRESH_SECONDS`, `GOOGLE_ROUTES_MAX_CONCURRENT_REQUESTS`, `GOOGLE_ROUTES_MAX_REQUESTS_PER_MINUTE/DAY`; existing HERE flags giữ cho legacy/analysis.
- Key giới hạn API/quyền phù hợp, chỉ backend; browser chỉ VITE_API_BASE_URL. Không bật Google khi thiếu cấu hình bắt buộc; không in giá trị key trong validation/toString/log.
- Outbound metrics phân theo operation/provider/status, không nhãn vị trí đầy đủ; cảnh báo request vượt dự toán. Không gọi live test trước khi budget/key được cấp trong môi trường an toàn.

## 9. Data model, retention và migration

Schema đích dưới đây là đề xuất để review. **Chưa được tạo migration cho Google persistence trước khi hoàn thành P0 về quyền lưu metrics/traffic/replay.** Tọa độ tối đa 30 ngày theo nguồn không là quyền lưu vô thời hạn toàn bộ response.

Migration tiếp theo dự kiến V12 (kiểm tra lại max version khi triển khai), không sửa migration cũ:

| Bảng | Field/constraint dự kiến | Ý nghĩa |
|---|---|---|
| routes | routing_provider CHECK HERE/GOOGLE; transport_mode CHECK CAR/MOTORCYCLE; geometry_version BIGINT NOT NULL DEFAULT 1 CHECK >0 | Provider/mode và phiên bản rõ; record cũ giữ HERE/CAR. |
| route_sections, trip_route_revision_sections | polyline_encoding VARCHAR(32) NOT NULL DEFAULT 'HERE_FLEXIBLE_POLYLINE' CHECK enum | Đọc đúng dữ liệu cũ và Google. |
| trip_route_revisions | routing_provider VARCHAR(20) NOT NULL DEFAULT 'HERE' CHECK enum; application_state VARCHAR(16) CHECK enum; observation_source VARCHAR(16), observation_fetched_at TIMESTAMPTZ | Không suy provider từ default mới; trace quyết định. Backfill applied state từ V11. |
| trip_traffic_alert_states | google_fetched_at/here_fetched_at TIMESTAMPTZ nullable; attempt_number INTEGER; geometry_version BIGINT; count/fingerprint theo từng source | Không đếm nhầm refresh giữa providers/attempt. |
| routes và trip_route_revisions | provider_content_expires_at TIMESTAMPTZ nullable (bắt buộc GOOGLE sau khi policy chốt) | Metadata vòng đời nội dung; index phục vụ cleanup. |

Traffic intervals Google ưu tiên transient cache gắn geometryVersion, không thêm bảng lịch sử traffic. Không dump response Google vào JSONB hoặc log.

P0 phải rà tất cả bản sao derived data: route metrics/sections, trip planned offsets, revision schedules, simulation_attempts, telemetry SIMULATOR và cache/export/backup. Dữ liệu người dùng nhập (xe, tên tuyến, thứ tự trạm, điểm đặt, giờ mong muốn) và GPS thật được tách khỏi provider content. Không mặc định simulator samples sinh từ Google là GPS thật hay dữ liệu độc lập được giữ vĩnh viễn.

Hai hướng lưu cần chốt trước P1:

- Nếu hợp đồng cho phép snapshot nghiệp vụ mong muốn: lưu đúng phạm vi/thời hạn đã xác minh, có cleanup đồng bộ các bản sao và test hết hạn. Không dùng quyền riêng này cho dữ liệu không được bao phủ.
- Nếu chỉ được cache hạn chế: lưu route definition do người dùng nhập dài hạn, payload provider theo hạn cho phép; metrics/geometry expired trở thành nullable ở DB/DTO có check constraint theo provider, không trả 0 giả. Khi mở/chạy lại, tính Google mới bằng hành động có kiểm soát và tạo attempt; không tuyên bố tái dựng đúng lịch sử traffic cũ. Chuyến đang chạy tới hạn cần refresh trước hoặc pause có thông báo; không tự xóa geometry dưới xe. Giữ ID và lịch sử nghiệp vụ hợp lệ, báo nội dung tuyến cũ đã hết hạn.

Đề xuất bắt đầu với hướng cache hạn chế; tác động replay/lịch kế hoạch cần được người dùng review. Nếu chưa xác minh được quyền cache metrics dù ngắn hạn, chỉ chạy adapter bằng fixtures cho tới khi có kết luận, không bật Google production. Schema nullable/purge cuối cùng phải bổ sung cụ thể vào spec sau P0, không quyết định ngầm khi implement.

## 10. Rollout và nghiệm thu

1. Thêm hỗ trợ đọc provider/encoding nhưng mặc định HERE; chạy hồi quy legacy.
2. Test Google bằng fixtures, migration Testcontainers và browser stub.
3. Sau gate tài khoản/retention: canary một tuyến Google mới, 1 xe, 1×, quota nhỏ; đối chiếu hình học/ETA/closure/chi phí.
4. Chỉ sau nghiệm thu mới đổi default GOOGLE. Không bulk recalculate chuyến cũ.
5. Rollback default về HERE chỉ ảnh hưởng tuyến mới; binary vẫn cần hiểu Google rows. Không rollback migration bằng xóa dữ liệu. Chuyến Google đang chạy được pause hoặc phục vụ bởi phiên bản còn hỗ trợ Google.

Điều kiện hoàn tất: AC01–AC12 đạt qua [test-plan](test-plan.md), evidence ghi riêng fixture/live và mọi test chưa chạy. Chưa đủ bằng chứng để cam kết exact replay Google dài hạn hoặc optimal reroute toàn tuyến.
