# Research — Điều phối chuyến tức thời

Không cần research bên ngoài. Đây là điều chỉnh ranh giới nghiệp vụ nội bộ; nguồn sự thật là entity, API, scheduler và UI hiện có của repository.

Quyết định quan trọng: không đổi schema. Quan hệ nullable `trips.schedule_id` đã phân biệt được chuyến thủ công và chuyến sinh từ lịch, trong khi mốc `scheduled_departure_at` vẫn cần cho tương thích thuật toán mô phỏng hiện hữu.
