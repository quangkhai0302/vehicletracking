# 007 — Đặc tả check-in tự động

Trạng thái: **Implementing** · 2026-09-14 · Contract đã được triển khai ở backend/frontend; một số AC nâng cao chưa có bằng chứng runtime.

Đầu vào: [requirement](requirement.md), [research](research.md), [survey](survey.md). Evidence ký hiệu E01–E15 trỏ tới bảng survey, không phải kết quả nghiệm thu.

## 1. Ranh giới và thứ tự xử lý

Giữ telemetry API, baseline TripStop và lifecycle 005–006. Thêm package feature `checkin/{entity,repository,service,dto,controller,geometry}`; không có HTTP ghi check-in thủ công.

Luồng một mẫu mới:

1. TelemetryService validate, dedupe và khóa **trip → vehicle** như hiện tại (E01). Duplicate cùng payload trả record cũ, không tăng revision hoặc chạy detector.
2. Chụp tham chiếu mẫu trước trước khi thay latest; lưu sample mới. Detector nhận trip snapshot + sample; tuyệt đối không dùng latest của chuyến khác làm đầu đoạn.
3. Trong **cùng transaction**, đọc/tạo checkpoint, xác định trajectory hợp lệ, xử lý stop kế tiếp, ghi visits và checkpoint. Sau đó cập nhật latest. Simulator checkpoint và lifecycle vẫn nằm trong transaction ngoài (E05).
4. Commit xong mới được snapshot reader/SSE thấy. Không dùng REQUIRES_NEW, message broker hoặc listener async để ghi visits.

Mẫu không đủ chất lượng check-in vẫn được lưu telemetry nếu đáp ứng contract 006. Đó không phải lỗi HTTP. Lỗi bất ngờ khi ghi visit phải rollback cả transaction, không nuốt lỗi và trả ingestion thành công. Thuật toán hình học có nhánh “không đủ bằng chứng” tường minh thay cho exception ở input hợp lệ.

Không thay đổi bất biến lifecycle: check-in đủ trạm **không tự complete chuyến GPS**; simulator vẫn complete theo hết duration. Complete/cancel không sinh visit bổ sung. Retry telemetry cũ hợp lệ sau terminal vẫn chỉ trả mẫu cũ.

## 2. Occurrence và thứ tự nhận diện

### 2.1. Quy tắc chung

- Occurrence = `(tripId, sequenceNumber)`, bắt đầu từ 1. Tọa độ/radius lấy TripStop snapshot (E03), không từ station đang sửa trên map.
- Target là stop chưa có visit nhỏ nhất theo sequence. Không quét trạm ngoài trip, không bỏ target để tìm trạm gần xe hơn.
- Target đầu tiên được phép POINT check-in khi nhận mẫu đủ chất lượng nằm trong vùng, recordedAt ≥ trip.startedAt. Nút start không tự lấy vị trí cũ để check-in. Mẫu trước thời điểm start có thể vẫn được telemetry 006 chấp nhận nhưng không là evidence của 007.
- Sau mỗi visit, kích hoạt target tiếp theo tại vị trí giao cắt đó. Nếu vị trí kích hoạt nằm **ngoài radius + hysteresis**, target được armed. Nếu chưa ngoài, target phải chờ một điểm/quỹ đạo đáng tin cậy đi ra ngoài ngưỡng rồi mới vào lại radius.
- Hysteresis nội bộ = `max(5 m, 10% radius)`. Khoảng giữa radius và radius+hysteresis không arm lại; không lưu hoặc hiển thị check-out/actual departure.
- Chỉ các giao cắt có thứ tự tăng trên trajectory mới được dùng cho target sau. Không dùng lại cùng điểm/fraction để check-in hai occurrence; không tua ngược đoạn để tìm entry đã xảy ra trước khi target kích hoạt.
- Có thể nhận nhiều stop trong một trajectory có đủ bằng chứng ra/vào theo thứ tự; không giới hạn cứng “một sample chỉ một visit”, vì 10× có thể đi qua nhiều stop. Số visit mới tối đa số stop còn lại (route hiện có tối đa 50).
- A→B→A: A cuối chỉ xét sau visit B và điều kiện armed; A đầu không tự hoàn thành A cuối. Hai vùng chồng nhau có thể yêu cầu ra/vào lại, kể cả stationId khác nhau. Không tự đoán có phục vụ hai trạm khi xe đứng nguyên trong phần giao nhau.
- Nếu bỏ lỡ target do mất GPS, các stop sau vẫn “Chưa ghi nhận”; không đổi thành SKIPPED và không có nút sửa tay. Đây là trade-off cần duyệt, không phải lỗi bị che.

### 2.2. GPS: điểm và đoạn

Policy khởi đầu cố định trong feature, có named constants + unit tests, không thêm env/UI cấu hình:

| Điều kiện | Giá trị/diễn giải |
|---|---|
| Accuracy của mỗi đầu được sử dụng | `0 ≤ accuracyMeters ≤ min(30, targetRadius/2)` |
| Gap cho nội suy | `0 < ΔrecordedAt ≤ 15 s` |
| Độ dài cung ngắn GPS | ≤1.000 m |
| Vận tốc suy ra từ tọa độ | `distance / ΔrecordedAt * 3.6 ≤ 160 km/h` |
| Ranh check-in | `distance ≤ radius`; sai số số học tối đa 0,01 m, không nới vùng theo accuracy |

Những ngưỡng này là policy đề xuất, không phải chuẩn HERE/W3C. SpeedKmh được gửi lên không đủ để chứng minh đoạn hợp lệ; phải tính tốc độ dịch chuyển độc lập.

Ưu tiên SEGMENT nếu có cặp mẫu liên tiếp cùng trip/source, sau start, đủ chất lượng; tìm entry sớm nhất trong cung hợp lệ. Nếu không có đoạn dùng được do gap dài/mẫu trước kém chất lượng, có thể dùng POINT của mẫu hiện tại đủ chất lượng; **không** suy thời gian đã qua vùng trong khoảng mất tín hiệu.

Nếu cặp đủ accuracy, gap ngắn nhưng nhảy vượt giới hạn khoảng cách/vận tốc thì loại **cả POINT của mẫu hiện tại khỏi phát sinh visit và arming trong lần xử lý này**, tránh bypass kiểm tra jump. Mẫu vẫn nằm trong history/latest; mẫu kế tiếp được đánh giá lại với cặp liên tiếp mới, không bỏ qua một mẫu xấu để nối đoạn dài về mẫu tốt cũ.

Đoạn dài 0 dùng POINT; không chia cho 0. Tiếp tuyến với vùng tính là đi ngang, nếu target armed. Geometry trên mặt cầu, xử lý đổi kinh tuyến/gần cực và số hữu hạn; công thức đề xuất tại research. Không coi một đoạn thẳng GPS đủ chứng minh xe đã đi đúng mạng đường: SEGMENT luôn là suy luận và gắn nhãn.

### 2.3. SIMULATOR: quỹ đạo đã chạy

Không áp dụng giới hạn GPS 15 s/160 km/h vào Δwall clock của simulator; như vậy 10× sẽ bị từ chối sai. Telemetry source SIMULATOR vẫn chỉ backend tạo, accuracy=0 theo emit hiện tại (E05).

- Lần đầu sau play: dùng POINT tại frame đầu. Những lần sau khôi phục khoảng elapsed từ simulatedAt của mẫu đã xử lý tới sample mới, theo cùng route immutable và cùng `RouteMotion`.
- Mở rộng pure RouteMotion để cung cấp trajectory có thứ tự thời gian: đầu/cuối khoảng, các đỉnh geometry nằm giữa, ranh section và dwell. Detector đi qua từng đoạn của trace; không chỉ nối hai frame đầu/cuối. Nội suy phải khớp `at` trong sai số ≤0,5 m ở fixtures, cùng bán kính Trái Đất. Không tạo trace ngoài khoảng thực sự đã chạy.
- Không nối qua khoảng hở geometry giữa section bằng đoạn thẳng giả. Xử lý điểm bắt đầu section mới là điểm riêng; không tạo crossing trong gap. Không thay policy geometry của 006 ngoài phần cần xuất trace.
- Dùng mẫu telemetry thực ở hai đầu làm provenance; điểm trung gian tính toán không được ngụy trang thành GPS sample được đo. Không bắt buộc ghi từng đỉnh thành telemetry row.
- Pause/resume/recover có simulatedAt không tăng: chỉ điểm đứng yên, không tạo travel/dwell mới. Restart tiếp tục từ checkpoint đã commit; không cộng downtime. Không gọi ngược SimulationService từ CheckInService để tránh dependency cycle (E05).
- Tick vượt nhiều stop/cuối chuyến được xử lý toàn khoảng trước complete; reset trip mới không nối trace với trip cũ. Trace reconstruction chỉ đọc geometry immutable và checkpoint; không backfill toàn history.

## 3. Thời gian và evidence

Mỗi visit ghi các trường không thay đổi sau tạo:

- `actualArrivalAt`: wall-clock của evidence; POINT bằng recordedAt của sample hiện tại. SEGMENT là thời điểm nội suy entry giữa recordedAt hai mẫu. ROUTE_TRACE là thời điểm wall-clock **suy ra** bằng tỉ lệ tiến độ simulatedAt trong khoảng hai mẫu, không dùng giờ nhận server làm actual arrival.
- `simulatedArrivalAt`: null cho GPS; với SIMULATOR là giờ trong lịch giả lập tại điểm entry, gồm scheduledDepartureAt + elapsed tại entry. Có thể khác wall-clock nhiều; không hiển thị như giờ GPS thực.
- `detectedAt`: clock server khi quyết định được lưu, UTC microsecond. Không áp constraint actualArrivalAt≤detectedAt vì telemetry 006 cho clock skew tương lai tối đa 30 s (E01).
- `evidenceKind`: POINT / SEGMENT / ROUTE_TRACE. POINT cũng chỉ chứng minh có mặt lúc đo, không bảo đảm thời điểm xe bắt đầu vào vùng. SEGMENT/ROUTE_TRACE bắt buộc UI ghi “Thời điểm suy ra”.
- `fromSampleId`: null khi POINT; đầu khoảng khi SEGMENT/ROUTE_TRACE. `toSampleId`: mẫu hiện tại. `evidenceFraction`: 1 khi POINT, `[0,1]` cho SEGMENT, tỉ lệ thời gian giả lập toàn khoảng cho ROUTE_TRACE.
- `latitude`, `longitude`: vị trí ghi nhận/entry; `source`: GPS/SIMULATOR; không có tọa độ/mốc thời gian giả định khi thiếu bằng chứng.

Không sửa plannedArrivalAt/plannedDepartureAt/dwell/startedAt/endedAt để khớp visit. Không tự tính ETA, độ trễ so với baseline hoặc giờ rời thực tế ở 007.

## 4. Data model và migration

Migration dự kiến `V6__create_trip_stop_visits.sql`; kiểm tra lại số kế tiếp ngay trước implement. Không sửa V1–V5. Mọi timestamp TIMESTAMPTZ ↔ Instant normalize microseconds. JPA persistence, controller không trả entity.

### 4.1. `vehicle_tracking.trip_stop_visits`

| Cột | Kiểu / null / default | Constraint / ý nghĩa |
|---|---|---|
| id | BIGINT identity, NOT NULL | PK |
| trip_id, stop_sequence | BIGINT, INTEGER, NOT NULL | UNIQUE cặp; composite FK tới trip_stops(trip_id, sequence_number), DELETE RESTRICT |
| source | VARCHAR(20), NOT NULL | CHECK GPS hoặc SIMULATOR |
| evidence_kind | VARCHAR(20), NOT NULL | CHECK POINT/SEGMENT/ROUTE_TRACE; SEGMENT chỉ GPS, ROUTE_TRACE chỉ SIMULATOR |
| actual_arrival_at, detected_at | TIMESTAMPTZ, NOT NULL | Không default; service truyền mốc chính xác |
| simulated_arrival_at | TIMESTAMPTZ, nullable | CHECK có giá trị iff source=SIMULATOR |
| from_sample_id | BIGINT, nullable | Null iff POINT; khác to_sample_id khi có |
| to_sample_id | BIGINT, NOT NULL | FK provenance |
| evidence_fraction | DOUBLE PRECISION, NOT NULL | CHECK finite và 0..1; POINT=1 |
| latitude, longitude | DOUBLE PRECISION, NOT NULL | CHECK finite và khoảng [-90,90]/[-180,180] |

Thêm UNIQUE `(id, trip_id, source)` trên telemetry_samples trong V6 và dùng composite FK `(from_sample_id, trip_id, source)` / `(to_sample_id, trip_id, source)` để DB không nhận evidence khác trip/source. FK tùy chọn from_sample dùng null theo POINT; tất cả DELETE RESTRICT. Không lặp vehicleId/station name/radius vào visit vì suy từ trip immutable. Index unique trip+sequence phục vụ list theo trip; không thêm index không có query tiêu thụ.

Service kiểm tra thời gian từ/to tăng, fraction nằm trong trace thật, coordinate/time khớp evidence, sequence liên tiếp và actualArrivalAt không trước startedAt. Không dùng CHECK đọc hàng khác.

### 4.2. `vehicle_tracking.trip_checkin_states`

| Cột | Kiểu / null / default | Ý nghĩa |
|---|---|---|
| trip_id | BIGINT PK + FK trips, NOT NULL | Một checkpoint/trip, DELETE RESTRICT |
| next_stop_sequence | INTEGER, nullable | Target; null khi đã ghi nhận hết; composite FK (trip_id,next_stop_sequence) tới trip_stops |
| armed | BOOLEAN NOT NULL DEFAULT FALSE | Chỉ null target mới bắt buộc false; target đầu được set true khi khởi tạo |
| last_sample_id | BIGINT NOT NULL | Mẫu cuối đã xử lý, kể cả thiếu chất lượng; FK telemetry_samples(id), DELETE RESTRICT |
| revision | BIGINT NOT NULL | CHECK ≥1; tăng đúng 1 mỗi sample mới, không tăng theo số visit |

Service bảo đảm last sample cùng trip và state/visits đồng nhất dưới trip lock; thêm UNIQUE `(id,trip_id)` ở telemetry_samples và composite FK `(last_sample_id,trip_id)` để ràng buộc trip tại DB. Checkpoint chỉ tạo khi có sample mới sau start đủ điều kiện thời gian, không tạo hàng trống khi GET. Không quét toàn history và không lưu state quyết định chỉ trong RAM.

Migration không backfill: các trip trước 007 chưa có state/visits trả revision=0, target=1, awaitingExit=false. Mẫu đầu xử lý sau triển khai chỉ là POINT, không nối trace/segment từ history trước 007. Trip cũ đã terminal tiếp tục “Chưa ghi nhận” tại các stop thiếu evidence; UI giải thích chưa có dữ liệu, không nói migration đã chứng minh xe không ghé.

## 5. API và read model

### 5.1. GET `/api/v1/trips/{tripId}/check-ins`

Controller thuộc feature checkin. Không request body/query; 200 nếu trip tồn tại (kể cả terminal hoặc xe inactive); 404 nếu không có trip; path sai kiểu trả 400 theo Spring. Không có endpoint POST/PUT/DELETE visits.

```json
{
  "tripId": 42,
  "revision": 3,
  "nextStopSequence": 2,
  "awaitingExit": false,
  "visits": [{
    "id": 7,
    "tripId": 42,
    "stopSequence": 1,
    "source": "GPS",
    "evidenceKind": "POINT",
    "actualArrivalAt": "2026-09-14T02:00:01Z",
    "simulatedArrivalAt": null,
    "detectedAt": "2026-09-14T02:00:02Z",
    "fromSampleId": null,
    "toSampleId": 101,
    "evidenceFraction": 1,
    "latitude": 10.77,
    "longitude": 106.7
  }]
}
```

Đây là **ví dụ contract dùng dữ liệu giả**, không phải response đã chạy. Visits sắp stopSequence tăng; tối đa số stop của trip. `awaitingExit` = có target và target chưa armed. Revision=0 biểu diễn chưa có checkpoint. Khi hết visits yêu cầu: nextStopSequence=null, awaitingExit=false. Terminal vẫn có thể có target chưa ghi nhận; lifecycle không được suy từ target.

Read service chạy transaction read-only REPEATABLE_READ để checkpoint/revision/visits không lệch nhau. Error theo ProblemDetail sẵn có `{type,title,status,detail,instance}` (E14), không thêm wrapper. Lỗi DB 5xx không lộ SQL/credential; giữ xử lý framework hiện có.

### 5.2. Snapshot và SSE

Thêm trường **bắt buộc** `checkIns: TripCheckInsResponse[]` vào OperationsSnapshot, mỗi trip trong `trips` có một read model tương ứng, kể cả revision=0/visits=[] và terminal. Giữ các trường cũ và event name `snapshot`, full resync, cleanup của 006 (E07). GET snapshot và SSE cùng contract, đọc trong cùng snapshot transaction, batch query visits/checkpoints, không N+1 và không query telemetry history.

Không thêm REST call mỗi giây mỗi panel hoặc stream riêng. Reconnect nhận full state nên các visits bị lỡ vẫn hiện. Telemetry POST response không đổi; TripDetail và lifecycle responses không đổi. Client cũ bỏ qua trường mới; client 007 gặp backend thiếu checkIns phải báo chưa có dữ liệu check-in/phiên bản không tương thích, không biến thành danh sách rỗng xác nhận “chưa đến”. Deploy backend trước frontend.

Revision theo trip bảo vệ race GET lịch sử/SSE: dùng read model revision lớn hơn; bằng nhau phải cùng nội dung. HTTP detail chậm không được ghi đè visits revision mới, snapshot trước commit không làm mất visit. Không dùng actualArrivalAt hay simulatedArrivalAt để xếp “response mới hơn”.

## 6. UI contract

- TripDetailPanel giữ timeline kế hoạch; thêm từng stop trạng thái **Đã ghi nhận / Chưa ghi nhận**, giờ ghi nhận, nguồn và nhãn suy ra. GPS hiển thị actualArrivalAt; SIMULATOR ưu tiên simulatedArrivalAt với nhãn **Giờ GIẢ LẬP**, có wall-clock ghi nhận riêng trong phần chi tiết. Hiển thị ngày và giờ địa phương, xử lý qua ngày.
- Đặt giải thích ngắn: “Ghi nhận đi qua vùng trạm, không xác nhận dừng đón/trả khách”. Không đổi chữ Rời kế hoạch thành actual departure.
- Tải check-in riêng với AbortController khi chọn trip; baseline/detail vẫn xem được nếu GET check-ins lỗi. Chưa tải xong hiển thị Đang tải ghi nhận; lỗi có Tải lại; giữ dữ liệu đã có khi stream reconnect và gắn nhãn chưa cập nhật. Revision=0: “Chưa có dữ liệu check-in cho chuyến này”. Visits rỗng không phải lỗi.
- Target chờ ra ngoài hiển thị “Chờ ra khỏi vùng rồi vào lại”; target bị mất bằng chứng chỉ “Chưa ghi nhận”, không tuyên bố đã bỏ trạm hoặc tự đổi lịch.
- Shared `useTripCheckIns` đề xuất nhận tripId + shared snapshot, merge theo revision; dùng được trong fleet và simulator, không tự mở EventSource. Abort/race/cleanup giữ selected trip đúng; đổi mode không làm mất form nháp (E09–E11).
- SimulatorPanel thêm “Đã ghi nhận x/n điểm” và lần ghé mới nhất từ read model, không từ frame.nextStopSequence. Countdown hiện tại giữ nhãn thời lượng giả lập; không đổi thành ETA live. Không thêm lệnh check-in hay confirmation mới.
- Giữ focus stop và map mounted, không tự pan theo từng visit. Dùng chữ/icon chứ không chỉ màu, layout 320/390/768/1440 px không tràn ngang. Không thông báo aria-live lặp lại mỗi snapshot không đổi.
- Cập nhật copy StationDrawer/TripDetailPanel/SimulatorPanel đúng phạm vi (E12). AlertStream vẫn chưa nối cảnh báo; sửa lời placeholder nếu cần để không hứa check-in đã hiển thị ở đó. Không thêm notification read state/feed/persistence thứ hai.

## 7. Bảo mật, vận hành và tương thích

Không key/env/provider mới. Không đọc `.env` để xử lý geofence. Dữ liệu SIMULATOR chỉ qua internal service; API GPS vẫn có giới hạn trust của 006, chưa xác thực thiết bị — 007 không đủ điều kiện tự mở ra Internet.

Không commit logs có vị trí vận hành thật; test dùng fixtures có nhãn. Không thêm retention/delete visits hoặc sửa schema cũ. Giữ schema/JPA validate. Feature không sửa route snapshot, telemetry contract hoặc permission model. Các thay đổi migration/DTO/UI phải được phát hành đồng bộ như §5.

## 8. AC cuối cùng và gate

Giữ nguyên **AC-01…AC-12** trong [requirement](requirement.md), lần lượt được cụ thể hóa bởi §1; §2/4; §2; §2; §2/6; §1/4/5; §3; §2.3; §5; §6; §1/4; §4/7. Ma trận đầy đủ tại [test-plan](test-plan.md).

Trạng thái implement: migration, detector, API/snapshot/SSE và UI core đã có; evidence runtime ghi tại [evidence 007](evidence.md). Browser core đã đạt, nhưng load/edge cases còn thiếu nên chưa đánh dấu `Verified`. 008 HERE Traffic/ETA không nằm trong phạm vi này.
