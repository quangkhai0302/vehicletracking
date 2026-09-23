# Test Plan 026 — Dashboard quản lý vận hành

| AC | Mức test | Kịch bản | Kết quả mong đợi |
|---|---|---|---|
| AC-01 | Unit/controller | Summary có các count và server time | DTO đủ field, endpoint trả `200` |
| AC-02 | Unit/service | Trip IN_PROGRESS trước/sau planned end | Chỉ trip quá hạn được tính |
| AC-03 | Repository/service | State active của trip running và completed | Chỉ state active của running trip được tính |
| AC-04 | Repository/service | Nhiều unread vượt 50, pending list | Count đầy đủ, list tối đa 5 |
| AC-05 | Frontend type/manual | KPI, trip list, alert list và deeplink | Hiển thị đúng dữ liệu và link hành động |
| AC-06 | Frontend/manual | Poll 15 giây, lỗi API, retry, empty | Không mất dữ liệu cũ khi poll lỗi; có feedback rõ |
| AC-07 | Quality | Maven test, lint, tsc, diff check | Đạt hoặc ghi rõ giới hạn runtime |
