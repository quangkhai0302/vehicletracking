package com.quangkhai.vehiceltracking_backend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequestDto {
    private String code;
    private String name;
    private String description;

    @NotEmpty(message = "Tuyến đường phải có ít nhất 2 trạm dừng")
    private List<Long> stationIds; // Danh sách ID các trạm theo đúng thứ tự dừng
}
