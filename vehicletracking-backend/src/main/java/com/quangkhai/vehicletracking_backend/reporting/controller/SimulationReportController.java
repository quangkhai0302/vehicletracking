package com.quangkhai.vehicletracking_backend.reporting.controller;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.quangkhai.vehicletracking_backend.reporting.dto.SimulationReportMetric;
import com.quangkhai.vehicletracking_backend.reporting.dto.SimulationReportResponse;
import com.quangkhai.vehicletracking_backend.reporting.service.SimulationReportService;

import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/v1/reports") @RequiredArgsConstructor
public class SimulationReportController {
    private final SimulationReportService service;
    @GetMapping("/simulation")
    public SimulationReportResponse simulation(
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required=false) Long vehicleId, @RequestParam(required=false) Long driverId,
        @RequestParam(defaultValue="ALL") SimulationReportMetric metric,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.report(from,to,vehicleId,driverId,metric,page,size);
    }
}

