# Test Plan 024 — Cảnh báo xe lệch tuyến

| AC | Mức test | Kịch bản | Kết quả mong đợi |
|---|---|---|---|
| AC-01 | Unit/service | GPS trên và ngoài polyline, route revision/next stop | Distance và section đúng |
| AC-02 | Unit/service | Trong ngưỡng, accuracy lớn, simulator, trip scheduled/completed | Không có state active/notification |
| AC-03 | Unit/service | Chưa đủ mẫu/thời gian, đủ điều kiện, callback lặp | Một notification cho episode |
| AC-04 | Unit/integration | Quay lại route, lệch lại, đổi attempt replay | State clear và notification episode mới |
| AC-05 | Controller/integration | List/read/delete/snapshot/SSE notification | Field mới đúng, notification hiển thị |
| AC-06 | Service/integration | Geometry lỗi, notification lỗi, telemetry đã commit | Không rollback telemetry |
| AC-07 | Frontend/manual | Loading, error/retry, filter, read-all, delete, mobile | UI business đầy đủ, không overflow |
| AC-08 | CI/local | Flyway/JPA, Maven test, lint, tsc, build, diff check | Lệnh đạt hoặc giới hạn môi trường được ghi evidence |
