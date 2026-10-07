package com.quangkhai.vehicletracking_backend.reporting;

import com.quangkhai.vehicletracking_backend.reporting.controller.OperationalReportController;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportResponse;
import com.quangkhai.vehicletracking_backend.reporting.dto.EmployeeOccupancySummary;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportDriverRow;
import com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportDriverTrip;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.reporting.service.OperationalReportService;
import com.quangkhai.vehicletracking_backend.reporting.service.OperationalReportDetailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationalReportController.class)
class OperationalReportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OperationalReportService service;
    @MockitoBean OperationalReportDetailService detailService;

    @Test
    void operationsReturnsAggregatedMetricsForFilters() throws Exception {
        when(service.operations(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21), 7L, 3L))
                .thenReturn(new OperationalReportResponse(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
                        Instant.parse("2026-09-21T08:00:00Z"), 10, 8, 123_000, 72_000, 87.5, 1, 2, 3, 80));

        mvc.perform(get("/api/v1/reports/operations")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-21")
                        .param("vehicleId", "7")
                        .param("driverId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripCount").value(10))
                .andExpect(jsonPath("$.totalDistanceMeters").value(123000))
                .andExpect(jsonPath("$.onTimeRatePercent").value(87.5))
                .andExpect(jsonPath("$.overspeedEventCount").value(3));
    }

    @Test
    void detailReturnsBreakdownsAndEmployeeDataStatus() throws Exception {
        OperationalReportResponse summary = new OperationalReportResponse(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
                Instant.parse("2026-09-21T08:00:00Z"), 2, 1, 1000, 300,
                100, 0, 1, 0, 80);
        when(detailService.detail(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21), null, null))
                .thenReturn(new com.quangkhai.vehicletracking_backend.reporting.dto.OperationalReportDetailResponse(
                        summary.from(), summary.to(), summary.generatedAt(), summary,
                        List.of(), List.of(new OperationalReportDriverRow(3L, "Tài xế A", 1, 0, 0, 0, 0, null,
                                List.of(new OperationalReportDriverTrip(7L, "Tuyến trường học", "51B12345",
                                        summary.generatedAt(), summary.generatedAt(), null, TripStatus.IN_PROGRESS)))),
                        List.of(), List.of(), false,
                        "Chưa có xác nhận số người lên tại các điểm đón của chuyến đã hoàn tất.",
                        new EmployeeOccupancySummary(1, 0, 1, 1, 0, null, null, null),
                        List.of(), List.of(), List.of(), List.of(), List.of()));

        mvc.perform(get("/api/v1/reports/operations/detail")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.tripCount").value(2))
                .andExpect(jsonPath("$.vehicles").isArray())
                .andExpect(jsonPath("$.drivers[0].trips[0].tripId").value(7))
                .andExpect(jsonPath("$.drivers[0].trips[0].routeName").value("Tuyến trường học"))
                .andExpect(jsonPath("$.drivers[0].trips[0].vehiclePlateNumber").value("51B12345"))
                .andExpect(jsonPath("$.drivers[0].trips[0].startedAt").value("2026-09-21T08:00:00Z"))
                .andExpect(jsonPath("$.drivers[0].trips[0].endedAt").isEmpty())
                .andExpect(jsonPath("$.drivers[0].trips[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.employeePassengerDataAvailable").value(false))
                .andExpect(jsonPath("$.employeeOccupancy.tripsMissingBoardingData").value(1))
                .andExpect(jsonPath("$.employeeOccupancyByDay").isArray())
                .andExpect(jsonPath("$.employeeOccupancyByStation").isArray())
                .andExpect(jsonPath("$.employeeOccupancyTrips").isArray());
    }
}
