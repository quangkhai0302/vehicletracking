package com.quangkhai.vehiceltracking_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Size(max = 50, message = "Mã tuyến đường không được vượt quá 50 ký tự")
    private String code;

    @NotBlank(message = "Tên tuyến đường không được để trống")
    @Size(max = 150, message = "Tên tuyến đường không được vượt quá 150 ký tự")
    private String name;

    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;

    @NotNull(message = "Danh sách trạm không được để trống")
    @Size(min = 2, message = "Tuyến đường phải có ít nhất 2 trạm dừng")
    private List<@NotNull(message = "ID trạm không được để trống") Long> stationIds;
}
