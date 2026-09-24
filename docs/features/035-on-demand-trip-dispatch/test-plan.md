# Test plan — Điều phối chuyến tức thời

| AC | Mức test | Kịch bản | Kết quả mong đợi |
|---|---|---|---|
| AC-01 | Controller/frontend unit | Gửi/tạo chuyến với xe, tuyến, tài xế | Request hợp lệ và không có giờ xuất phát |
| AC-02 | Service unit/integration | Tạo thủ công và tạo từ lịch | Response lần lượt là `ON_DEMAND` và `FIXED_SCHEDULE`, lịch nguồn đúng |
| AC-03 | Frontend unit | Render chuyến tức thời | Không có nhãn xuất phát/hoàn thành theo lịch |
| AC-04 | Frontend unit | Render chuyến từ lịch | Hiển thị giờ kế hoạch |
| AC-05 | Controller/frontend unit | Mở chi tiết chuyến | Không có thao tác sửa giờ và endpoint update |
| AC-06 | Service unit/integration | Có chuyến chờ khác rồi tạo/khởi hành chuyến tức thời; tài xế/xe đang chạy | Chuyến chờ không gây conflict thời gian; tài nguyên đang chạy vẫn trả conflict |
| AC-07 | Build | Chạy backend test và toàn bộ kiểm tra frontend chuẩn | Các lệnh kết thúc exit code 0 |

Kiểm tra thủ công: tạo chuyến từ tab Chuyến đi, xác nhận không nhập giờ; mở chi tiết; so sánh với một chuyến được sinh từ Lịch chạy tự động.
