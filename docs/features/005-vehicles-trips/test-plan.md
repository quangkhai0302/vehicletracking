# Test plan 005

- Vehicle: normalize plate, trùng kể cả inactive, blank/invalid/too long, update giữ ID, deactivate bị chặn khi pending/running trip, lặp deactivate.
- Trip: route/vehicle thiếu hoặc inactive; inactive station; loop A→B→A giữ sequence; giờ qua ngày và timezone; snapshot sau station edit; schedule baseline không đổi khi start; các chuyển trạng thái sai/retry; hai start cùng vehicle chỉ một thành công.
- Database: Flyway + Hibernate validate; unique plate/partial index running, FK, state timestamp CHECK; persistence và concurrency qua service trong transaction riêng.
- Browser: dữ liệu rỗng/lỗi/retry, tạo/sửa/deactivate xe và giữ draft, tạo/xem/start/complete/cancel trip, conflict, chọn xe lọc trip, route map đúng, thay đổi mode không mất draft, stale responses; 1440/390/320 và regression UI 004.
- Kết quả/giới hạn môi trường ghi trong verification.md sau khi chạy, không suy ra từ source.
