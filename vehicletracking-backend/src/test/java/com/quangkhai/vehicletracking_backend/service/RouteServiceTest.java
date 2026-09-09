package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.RouteRequestDto;
import com.quangkhai.vehiceltracking_backend.dto.RouteResponseDto;
import com.quangkhai.vehiceltracking_backend.entity.Route;
import com.quangkhai.vehiceltracking_backend.entity.RouteStation;
import com.quangkhai.vehiceltracking_backend.entity.Station;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.exception.RouteConflictException;
import com.quangkhai.vehiceltracking_backend.exception.RouteNotFoundException;
import com.quangkhai.vehiceltracking_backend.repository.RouteRepository;
import com.quangkhai.vehiceltracking_backend.repository.StationRepository;
import com.quangkhai.vehiceltracking_backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private StationRepository stationRepository;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private StationService stationService;

    @InjectMocks
    private RouteService routeService;

    private Station startStation;
    private Station stopStation;
    private Station endStation;
    private Route sampleRoute;

    @BeforeEach
    void setUp() {
        startStation = Station.builder()
                .id(101L)
                .code("ST-START")
                .name("Bến Xe Bắt Đầu")
                .latitude(10.80)
                .longitude(106.70)
                .radiusMeters(60.0)
                .stationType(StationType.START)
                .build();

        stopStation = Station.builder()
                .id(102L)
                .code("ST-STOP")
                .name("Trạm Dừng Giữa")
                .latitude(10.82)
                .longitude(106.72)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build();

        endStation = Station.builder()
                .id(103L)
                .code("ST-END")
                .name("Bến Xe Kết Thúc")
                .latitude(10.85)
                .longitude(106.75)
                .radiusMeters(70.0)
                .stationType(StationType.END)
                .build();

        sampleRoute = Route.builder()
                .id(1L)
                .code("ROUTE-01")
                .name("Tuyến 01: Start - Stop - End")
                .description("Mô tả tuyến 01")
                .totalDistanceKm(10.5)
                .estimatedDurationMinutes(22.0)
                .routeStations(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("TC-001: createRoute with valid START -> STOP -> END succeeds and calculates metrics")
    void testCreateRoute_success() {
        RouteRequestDto request = RouteRequestDto.builder()
                .code("R-TEST-01")
                .name("Tuyến Thử Nghiệm")
                .description("Mô tả")
                .stationIds(Arrays.asList(101L, 102L, 103L))
                .build();

        when(routeRepository.existsByCode("R-TEST-01")).thenReturn(false);
        when(stationRepository.findById(101L)).thenReturn(Optional.of(startStation));
        when(stationRepository.findById(102L)).thenReturn(Optional.of(stopStation));
        when(stationRepository.findById(103L)).thenReturn(Optional.of(endStation));
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> {
            Route r = invocation.getArgument(0);
            r.setId(10L);
            return r;
        });

        RouteResponseDto response = routeService.createRoute(request);

        assertNotNull(response);
        assertEquals("R-TEST-01", response.getCode());
        assertEquals("Tuyến Thử Nghiệm", response.getName());
        assertEquals(3, response.getStations().size());
        assertTrue(response.getTotalDistanceKm() > 0.0);
        assertTrue(response.getEstimatedDurationMinutes() > 0.0);
        verify(routeRepository).save(any(Route.class));
    }

    @Test
    @DisplayName("TC-001: createRoute auto-generates code if missing")
    void testCreateRoute_autoGenerateCode() {
        RouteRequestDto request = RouteRequestDto.builder()
                .code("")
                .name("Tuyến Không Mã")
                .stationIds(Arrays.asList(101L, 103L))
                .build();

        when(stationRepository.findById(101L)).thenReturn(Optional.of(startStation));
        when(stationRepository.findById(103L)).thenReturn(Optional.of(endStation));
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RouteResponseDto response = routeService.createRoute(request);

        assertNotNull(response);
        assertTrue(response.getCode().startsWith("ROUTE-"));
    }

    @Test
    @DisplayName("TC-002: createRoute throws IllegalArgumentException on invalid input structure")
    void testCreateRoute_invalidStructure() {
        // Null name
        RouteRequestDto nullName = RouteRequestDto.builder()
                .name(null)
                .stationIds(Arrays.asList(101L, 103L))
                .build();
        assertThrows(IllegalArgumentException.class, () -> routeService.createRoute(nullName));

        // Empty stationIds
        RouteRequestDto emptyStations = RouteRequestDto.builder()
                .name("Tuyến Test")
                .stationIds(new ArrayList<>())
                .build();
        assertThrows(IllegalArgumentException.class, () -> routeService.createRoute(emptyStations));

        // Less than 2 stations
        RouteRequestDto oneStation = RouteRequestDto.builder()
                .name("Tuyến Test")
                .stationIds(List.of(101L))
                .build();
        assertThrows(IllegalArgumentException.class, () -> routeService.createRoute(oneStation));
    }

    @Test
    @DisplayName("TC-003: createRoute throws IllegalArgumentException when station type order is invalid")
    void testCreateRoute_invalidStationTypes() {
        // First is STOP instead of START
        RouteRequestDto badStart = RouteRequestDto.builder()
                .name("Tuyến Test")
                .stationIds(Arrays.asList(102L, 103L))
                .build();
        when(stationRepository.findById(102L)).thenReturn(Optional.of(stopStation));
        when(stationRepository.findById(103L)).thenReturn(Optional.of(endStation));
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> routeService.createRoute(badStart));
        assertTrue(ex1.getMessage().contains("Tuyến phải bắt đầu bằng trạm START"));

        // Last is STOP instead of END
        RouteRequestDto badEnd = RouteRequestDto.builder()
                .name("Tuyến Test")
                .stationIds(Arrays.asList(101L, 102L))
                .build();
        when(stationRepository.findById(101L)).thenReturn(Optional.of(startStation));
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> routeService.createRoute(badEnd));
        assertTrue(ex2.getMessage().contains("kết thúc bằng trạm END"));

        // Middle contains START
        RouteRequestDto badMiddle = RouteRequestDto.builder()
                .name("Tuyến Test")
                .stationIds(Arrays.asList(101L, 101L, 103L))
                .build();
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () -> routeService.createRoute(badMiddle));
        assertTrue(ex3.getMessage().contains("các trạm giữa phải là STOP"));
    }

    @Test
    @DisplayName("TC-004: createRoute throws RouteNotFoundException when station ID does not exist")
    void testCreateRoute_stationNotFound() {
        RouteRequestDto request = RouteRequestDto.builder()
                .name("Tuyến Test")
                .stationIds(Arrays.asList(101L, 9999L))
                .build();

        when(stationRepository.findById(101L)).thenReturn(Optional.of(startStation));
        when(stationRepository.findById(9999L)).thenReturn(Optional.empty());

        RouteNotFoundException ex = assertThrows(RouteNotFoundException.class, () -> routeService.createRoute(request));
        assertTrue(ex.getMessage().contains("9999"));
    }

    @Test
    @DisplayName("TC-005: createRoute throws RouteConflictException on duplicate code")
    void testCreateRoute_duplicateCode() {
        RouteRequestDto request = RouteRequestDto.builder()
                .code("r-dup-01")
                .name("Tuyến Test")
                .stationIds(Arrays.asList(101L, 103L))
                .build();

        when(routeRepository.existsByCode("R-DUP-01")).thenReturn(true);

        RouteConflictException ex = assertThrows(RouteConflictException.class, () -> routeService.createRoute(request));
        assertTrue(ex.getMessage().contains("Mã tuyến đã tồn tại: R-DUP-01"));
        verify(routeRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-002: updateRoute throws IllegalArgumentException on invalid input structure")
    void testUpdateRoute_invalidStructure() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(sampleRoute));
        when(tripRepository.existsByRouteId(1L)).thenReturn(false);

        // Blank name
        RouteRequestDto blankName = RouteRequestDto.builder()
                .name("   ")
                .stationIds(Arrays.asList(101L, 103L))
                .build();
        assertThrows(IllegalArgumentException.class, () -> routeService.updateRoute(1L, blankName));

        // Less than 2 stations
        RouteRequestDto oneStation = RouteRequestDto.builder()
                .name("Tuyến Sửa")
                .stationIds(List.of(101L))
                .build();
        assertThrows(IllegalArgumentException.class, () -> routeService.updateRoute(1L, oneStation));
    }

    @Test
    @DisplayName("TC-003: updateRoute throws IllegalArgumentException when station type order is invalid")
    void testUpdateRoute_invalidStationTypes() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(sampleRoute));
        when(tripRepository.existsByRouteId(1L)).thenReturn(false);

        // First is STOP
        RouteRequestDto badStart = RouteRequestDto.builder()
                .name("Tuyến Sửa")
                .stationIds(Arrays.asList(102L, 103L))
                .build();
        when(stationRepository.findById(102L)).thenReturn(Optional.of(stopStation));
        when(stationRepository.findById(103L)).thenReturn(Optional.of(endStation));
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> routeService.updateRoute(1L, badStart));
        assertTrue(ex1.getMessage().contains("Tuyến phải bắt đầu bằng trạm START"));

        // Last is STOP
        RouteRequestDto badEnd = RouteRequestDto.builder()
                .name("Tuyến Sửa")
                .stationIds(Arrays.asList(101L, 102L))
                .build();
        when(stationRepository.findById(101L)).thenReturn(Optional.of(startStation));
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> routeService.updateRoute(1L, badEnd));
        assertTrue(ex2.getMessage().contains("kết thúc bằng trạm END"));

        // Middle contains START
        RouteRequestDto badMiddle = RouteRequestDto.builder()
                .name("Tuyến Sửa")
                .stationIds(Arrays.asList(101L, 101L, 103L))
                .build();
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () -> routeService.updateRoute(1L, badMiddle));
        assertTrue(ex3.getMessage().contains("các trạm giữa phải là STOP"));
    }

    @Test
    @DisplayName("TC-004: updateRoute throws RouteNotFoundException when station ID does not exist")
    void testUpdateRoute_stationNotFound() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(sampleRoute));
        when(tripRepository.existsByRouteId(1L)).thenReturn(false);

        RouteRequestDto request = RouteRequestDto.builder()
                .name("Tuyến Sửa Trạm Ảo")
                .stationIds(Arrays.asList(101L, 9999L))
                .build();
        when(stationRepository.findById(101L)).thenReturn(Optional.of(startStation));
        when(stationRepository.findById(9999L)).thenReturn(Optional.empty());

        RouteNotFoundException ex = assertThrows(RouteNotFoundException.class, () -> routeService.updateRoute(1L, request));
        assertTrue(ex.getMessage().contains("9999"));
    }

    @Test
    @DisplayName("TC-005: updateRoute throws RouteConflictException when updating to code of another route")
    void testUpdateRoute_duplicateCode_otherRoute() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(sampleRoute));
        when(tripRepository.existsByRouteId(1L)).thenReturn(false);
        when(routeRepository.existsByCodeAndIdNot("R-EXISTING-OTHER", 1L)).thenReturn(true);

        RouteRequestDto request = RouteRequestDto.builder()
                .code("r-existing-other")
                .name("Tuyến Sửa Trùng Mã")
                .stationIds(Arrays.asList(101L, 103L))
                .build();

        RouteConflictException ex = assertThrows(RouteConflictException.class, () -> routeService.updateRoute(1L, request));
        assertTrue(ex.getMessage().contains("Mã tuyến đã tồn tại: R-EXISTING-OTHER"));
        verify(routeRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-006: updateRoute replaces topology, recalculates metrics, preserves ID and createdAt")
    void testUpdateRoute_success() {
        LocalDateTime originalCreatedAt = sampleRoute.getCreatedAt();
        when(routeRepository.findById(1L)).thenReturn(Optional.of(sampleRoute));
        when(tripRepository.existsByRouteId(1L)).thenReturn(false);
        when(routeRepository.existsByCodeAndIdNot("R-UPDATED", 1L)).thenReturn(false);
        when(stationRepository.findById(101L)).thenReturn(Optional.of(startStation));
        when(stationRepository.findById(102L)).thenReturn(Optional.of(stopStation));
        when(stationRepository.findById(103L)).thenReturn(Optional.of(endStation));
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RouteRequestDto updateRequest = RouteRequestDto.builder()
                .code("  r-updated  ")
                .name("Tên Tuyến Đã Đổi")
                .description("Mô tả mới")
                .stationIds(Arrays.asList(101L, 102L, 103L))
                .build();

        RouteResponseDto response = routeService.updateRoute(1L, updateRequest);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("R-UPDATED", response.getCode());
        assertEquals("Tên Tuyến Đã Đổi", response.getName());
        assertEquals("Mô tả mới", response.getDescription());
        assertEquals(originalCreatedAt, response.getCreatedAt());
        assertEquals(3, response.getStations().size());
        verify(routeRepository, times(2)).flush();
        verify(routeRepository).save(sampleRoute);
    }

    @Test
    @DisplayName("TC-006: updateRoute allows route to keep its existing code")
    void testUpdateRoute_keepOwnCode() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(sampleRoute));
        when(tripRepository.existsByRouteId(1L)).thenReturn(false);
        when(stationRepository.findById(101L)).thenReturn(Optional.of(startStation));
        when(stationRepository.findById(103L)).thenReturn(Optional.of(endStation));
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RouteRequestDto updateRequest = RouteRequestDto.builder()
                .code("") // Blank code means keep existing code
                .name("Tên Mới")
                .stationIds(Arrays.asList(101L, 103L))
                .build();

        RouteResponseDto response = routeService.updateRoute(1L, updateRequest);

        assertEquals("ROUTE-01", response.getCode());
        assertEquals("Tên Mới", response.getName());
    }

    @Test
    @DisplayName("TC-007: updateRoute throws RouteConflictException when route is referenced by a Trip")
    void testUpdateRoute_referencedByTrip() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(sampleRoute));
        when(tripRepository.existsByRouteId(1L)).thenReturn(true);

        RouteRequestDto request = RouteRequestDto.builder()
                .name("Tuyến Cập Nhật")
                .stationIds(Arrays.asList(101L, 103L))
                .build();

        RouteConflictException ex = assertThrows(RouteConflictException.class, () -> routeService.updateRoute(1L, request));
        assertTrue(ex.getMessage().contains("Không thể sửa hoặc xóa tuyến đã được gán cho chuyến đi"));
        verify(routeRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-008: deleteRoute deletes unreferenced route successfully")
    void testDeleteRoute_success() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(sampleRoute));
        when(tripRepository.existsByRouteId(1L)).thenReturn(false);

        routeService.deleteRoute(1L);

        verify(routeRepository).delete(sampleRoute);
        verify(routeRepository).flush();
    }

    @Test
    @DisplayName("TC-008: deleteRoute throws RouteConflictException when route has Trips")
    void testDeleteRoute_referencedByTrip() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(sampleRoute));
        when(tripRepository.existsByRouteId(1L)).thenReturn(true);

        RouteConflictException ex = assertThrows(RouteConflictException.class, () -> routeService.deleteRoute(1L));
        assertTrue(ex.getMessage().contains("Không thể sửa hoặc xóa tuyến đã được gán cho chuyến đi"));
        verify(routeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("TC-008: deleteRoute throws RouteNotFoundException when route ID does not exist")
    void testDeleteRoute_notFound() {
        when(routeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RouteNotFoundException.class, () -> routeService.deleteRoute(999L));
    }

    @Test
    @DisplayName("TC-005: updateRoute handles DataIntegrityViolationException for duplicate code")
    void testUpdateRoute_dataIntegrityViolation_uniqueCode() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(sampleRoute));
        when(tripRepository.existsByRouteId(1L)).thenReturn(false);
        when(stationRepository.findById(101L)).thenReturn(Optional.of(startStation));
        when(stationRepository.findById(103L)).thenReturn(Optional.of(endStation));
        when(routeRepository.existsByCodeAndIdNot("R-CONCURRENT", 1L)).thenReturn(false);
        when(routeRepository.save(any(Route.class))).thenReturn(sampleRoute);
        doNothing().doThrow(new DataIntegrityViolationException("Unique index or primary key violation: uk_routes_code"))
                .when(routeRepository).flush();

        RouteRequestDto request = RouteRequestDto.builder()
                .code("R-CONCURRENT")
                .name("Tên Tuyến")
                .stationIds(Arrays.asList(101L, 103L))
                .build();

        RouteConflictException ex = assertThrows(RouteConflictException.class, () -> routeService.updateRoute(1L, request));
        assertTrue(ex.getMessage().contains("Mã tuyến đã tồn tại: R-CONCURRENT"));
    }

    @Test
    @DisplayName("TC-005: updateRoute handles DataIntegrityViolationException for other integrity constraint")
    void testUpdateRoute_dataIntegrityViolation_other() {
        when(routeRepository.findById(1L)).thenReturn(Optional.of(sampleRoute));
        when(tripRepository.existsByRouteId(1L)).thenReturn(false);
        when(stationRepository.findById(101L)).thenReturn(Optional.of(startStation));
        when(stationRepository.findById(103L)).thenReturn(Optional.of(endStation));
        when(routeRepository.existsByCodeAndIdNot("ROUTE-01", 1L)).thenReturn(false);
        when(routeRepository.save(any(Route.class))).thenReturn(sampleRoute);
        doNothing().doThrow(new DataIntegrityViolationException("Check constraint violation"))
                .when(routeRepository).flush();

        RouteRequestDto request = RouteRequestDto.builder()
                .code("ROUTE-01")
                .name("Tên Tuyến")
                .stationIds(Arrays.asList(101L, 103L))
                .build();

        RouteConflictException ex = assertThrows(RouteConflictException.class, () -> routeService.updateRoute(1L, request));
        assertEquals("Xung đột toàn vẹn dữ liệu tuyến đường", ex.getMessage());
    }
}
