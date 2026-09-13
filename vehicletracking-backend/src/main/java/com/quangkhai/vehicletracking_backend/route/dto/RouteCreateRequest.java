package com.quangkhai.vehicletracking_backend.route.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RouteCreateRequest(
        @NotBlank(message = "Tên tuyến không được để trống")
        @Size(max = 150, message = "Tên tuyến tối đa 150 ký tự")
        String name,

        @NotNull(message = "Danh sách điểm dừng không được để trống")
        @Size(min = 2, max = 50, message = "Tuyến phải có từ 2 đến 50 điểm dừng")
        List<@NotNull(message = "Điểm dừng không được null") @Valid RouteStopInput> stops
) {
    public record RouteStopInput(
            @NotNull(message = "Mã trạm không được để trống")
            @Positive(message = "Mã trạm phải là số dương")
            Long stationId,

            @NotNull(message = "Thời gian dừng không được để trống")
            @Min(value = 0, message = "Thời gian dừng tối thiểu 0 giây")
            @Max(value = 3600, message = "Thời gian dừng tối đa 3600 giây")
            Integer dwellDurationSeconds
    ) {}
}
