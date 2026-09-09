package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.StationDto;
import com.quangkhai.vehiceltracking_backend.entity.Station;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.exception.StationConflictException;
import com.quangkhai.vehiceltracking_backend.exception.StationNotFoundException;
import com.quangkhai.vehiceltracking_backend.repository.RouteStationRepository;
import com.quangkhai.vehiceltracking_backend.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StationServiceTest {

    @Mock
    private StationRepository stationRepository;

    @Mock
    private RouteStationRepository routeStationRepository;

    @InjectMocks
    private StationService stationService;

    private Station sampleStation;
    private StationDto sampleDto;

    @BeforeEach
    void setUp() {
        sampleStation = Station.builder()
                .id(1L)
                .code("ST-01")
                .name("Trạm 01")
                .latitude(10.762622)
                .longitude(106.660172)
                .address("227 Nguyễn Văn Cừ")
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .createdAt(LocalDateTime.of(2026, 9, 3, 10, 0, 0))
                .build();

        sampleDto = StationDto.builder()
                .code("ST-01")
                .name("Trạm 01")
                .latitude(10.762622)
                .longitude(106.660172)
                .address("227 Nguyễn Văn Cừ")
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build();
    }

    @Test
    @DisplayName("TC-004: normalizeCode trims and converts to uppercase")
    void testNormalizeCode_success() {
        assertEquals("ST-01", stationService.normalizeCode("  st-01  "));
        assertEquals("TEST-CODE", stationService.normalizeCode("test-code"));
    }

    @Test
    @DisplayName("normalizeCode throws on blank or null or too long")
    void testNormalizeCode_invalid() {
        assertThrows(IllegalArgumentException.class, () -> stationService.normalizeCode(null));
        assertThrows(IllegalArgumentException.class, () -> stationService.normalizeCode("   "));
        assertThrows(IllegalArgumentException.class, () -> stationService.normalizeCode("A".repeat(51)));
    }

    @Test
    @DisplayName("TC-001: getAllStations returns mapped DTO list")
    void testGetAllStations() {
        when(stationRepository.findAll()).thenReturn(List.of(sampleStation));

        List<StationDto> result = stationService.getAllStations();

        assertEquals(1, result.size());
        assertEquals("ST-01", result.get(0).getCode());
        assertEquals(StationType.STOP, result.get(0).getStationType());
    }

    @Test
    @DisplayName("TC-001: getStationById returns DTO when found")
    void testGetStationById_found() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(sampleStation));

        StationDto dto = stationService.getStationById(1L);

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("ST-01", dto.getCode());
    }

    @Test
    @DisplayName("TC-001: getStationById throws StationNotFoundException when missing")
    void testGetStationById_notFound() {
        when(stationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(StationNotFoundException.class, () -> stationService.getStationById(999L));
    }

    @Test
    @DisplayName("TC-003: createStation normalizes code, saves entity, returns DTO")
    void testCreateStation_success() {
        StationDto input = StationDto.builder()
                .code("  st-new  ")
                .name("  Trạm Mới  ")
                .latitude(10.5)
                .longitude(106.5)
                .address("  123 Đường Mới  ")
                .radiusMeters(60.0)
                .stationType(StationType.START)
                .build();

        when(stationRepository.existsByCode("ST-NEW")).thenReturn(false);
        when(stationRepository.save(any(Station.class))).thenAnswer(invocation -> {
            Station toSave = invocation.getArgument(0);
            toSave.setId(10L);
            toSave.setCreatedAt(LocalDateTime.now());
            return toSave;
        });

        StationDto created = stationService.createStation(input);

        assertNotNull(created);
        assertEquals("ST-NEW", created.getCode());
        assertEquals("Trạm Mới", created.getName());
        assertEquals("123 Đường Mới", created.getAddress());
        assertEquals(StationType.START, created.getStationType());

        ArgumentCaptor<Station> captor = ArgumentCaptor.forClass(Station.class);
        verify(stationRepository).save(captor.capture());
        assertEquals("ST-NEW", captor.getValue().getCode());
        assertEquals("Trạm Mới", captor.getValue().getName());
        assertEquals("123 Đường Mới", captor.getValue().getAddress());
    }

    @Test
    @DisplayName("TC-004: createStation throws StationConflictException when normalized code exists")
    void testCreateStation_duplicate() {
        StationDto input = StationDto.builder()
                .code(" st-01 ")
                .name("Trạm Trùng")
                .latitude(10.5)
                .longitude(106.5)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build();

        when(stationRepository.existsByCode("ST-01")).thenReturn(true);

        assertThrows(StationConflictException.class, () -> stationService.createStation(input));
        verify(stationRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-004: createStation handles DataIntegrityViolationException for duplicate code")
    void testCreateStation_dataIntegrityViolation_uniqueCode() {
        when(stationRepository.existsByCode("ST-NEW")).thenReturn(false);
        when(stationRepository.save(any(Station.class))).thenReturn(sampleStation);
        doThrow(new DataIntegrityViolationException("Unique index or primary key violation: uk_station_code"))
                .when(stationRepository).flush();

        StationDto input = StationDto.builder()
                .code("ST-NEW")
                .name("Trạm Mới")
                .latitude(10.5)
                .longitude(106.5)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build();

        StationConflictException ex = assertThrows(StationConflictException.class, () -> stationService.createStation(input));
        assertTrue(ex.getMessage().contains("Mã trạm đã tồn tại"));
    }

    @Test
    @DisplayName("TC-004: createStation handles DataIntegrityViolationException for other integrity constraint")
    void testCreateStation_dataIntegrityViolation_other() {
        when(stationRepository.existsByCode("ST-NEW")).thenReturn(false);
        when(stationRepository.save(any(Station.class))).thenReturn(sampleStation);
        doThrow(new DataIntegrityViolationException("Check constraint violation"))
                .when(stationRepository).flush();

        StationDto input = StationDto.builder()
                .code("ST-NEW")
                .name("Trạm Mới")
                .latitude(10.5)
                .longitude(106.5)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build();

        StationConflictException ex = assertThrows(StationConflictException.class, () -> stationService.createStation(input));
        assertEquals("Xung đột toàn vẹn dữ liệu trạm dừng", ex.getMessage());
    }

    @Test
    @DisplayName("TC-005: validateStationDto boundary and invalid checks")
    void testValidateStationDto() {
        // Invalid latitude
        sampleDto.setLatitude(90.1);
        assertThrows(IllegalArgumentException.class, () -> stationService.validateStationDto(sampleDto));
        sampleDto.setLatitude(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> stationService.validateStationDto(sampleDto));
        sampleDto.setLatitude(10.0);

        // Invalid longitude
        sampleDto.setLongitude(-180.1);
        assertThrows(IllegalArgumentException.class, () -> stationService.validateStationDto(sampleDto));
        sampleDto.setLongitude(106.0);

        // Invalid radius
        sampleDto.setRadiusMeters(29.9);
        assertThrows(IllegalArgumentException.class, () -> stationService.validateStationDto(sampleDto));
        sampleDto.setRadiusMeters(150.1);
        assertThrows(IllegalArgumentException.class, () -> stationService.validateStationDto(sampleDto));
        sampleDto.setRadiusMeters(60.0);

        // Blank name
        sampleDto.setName("   ");
        assertThrows(IllegalArgumentException.class, () -> stationService.validateStationDto(sampleDto));
        sampleDto.setName("Valid Name");

        // Null stationType
        sampleDto.setStationType(null);
        assertThrows(IllegalArgumentException.class, () -> stationService.validateStationDto(sampleDto));
    }

    @Test
    @DisplayName("TC-006: updateStation preserves id and createdAt, updates fields")
    void testUpdateStation_success() {
        LocalDateTime originalCreatedAt = sampleStation.getCreatedAt();
        when(stationRepository.findById(1L)).thenReturn(Optional.of(sampleStation));
        when(stationRepository.existsByCodeAndIdNot("ST-UPDATED", 1L)).thenReturn(false);
        when(stationRepository.save(any(Station.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StationDto updateDto = StationDto.builder()
                .code("  st-updated  ")
                .name("  Tên Đã Sửa  ")
                .latitude(10.8)
                .longitude(106.7)
                .address("Địa chỉ mới")
                .radiusMeters(90.0)
                .stationType(StationType.END)
                .build();

        StationDto result = stationService.updateStation(1L, updateDto);

        assertEquals("ST-UPDATED", result.getCode());
        assertEquals("Tên Đã Sửa", result.getName());
        assertEquals(10.8, result.getLatitude());
        assertEquals(106.7, result.getLongitude());
        assertEquals("Địa chỉ mới", result.getAddress());
        assertEquals(90.0, result.getRadiusMeters());
        assertEquals(StationType.END, result.getStationType());
        assertEquals(1L, result.getId());
        assertEquals(originalCreatedAt, result.getCreatedAt());
    }

    @Test
    @DisplayName("TC-006: updateStation allows same station to keep its own code")
    void testUpdateStation_keepOwnCode() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(sampleStation));
        when(stationRepository.existsByCodeAndIdNot("ST-01", 1L)).thenReturn(false);
        when(stationRepository.save(any(Station.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StationDto updateDto = StationDto.builder()
                .code("  st-01  ")
                .name("Tên Mới")
                .latitude(10.76)
                .longitude(106.66)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build();

        StationDto result = stationService.updateStation(1L, updateDto);
        assertEquals("ST-01", result.getCode());
        assertEquals("Tên Mới", result.getName());
    }

    @Test
    @DisplayName("TC-007: updateStation throws StationConflictException when code belongs to another station")
    void testUpdateStation_duplicateOtherStation() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(sampleStation));
        when(stationRepository.existsByCodeAndIdNot("ST-OTHER", 1L)).thenReturn(true);

        StationDto updateDto = StationDto.builder()
                .code("ST-OTHER")
                .name("Tên Mới")
                .latitude(10.76)
                .longitude(106.66)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build();

        assertThrows(StationConflictException.class, () -> stationService.updateStation(1L, updateDto));
        verify(stationRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-007: updateStation handles DataIntegrityViolationException for duplicate code")
    void testUpdateStation_dataIntegrityViolation_uniqueCode() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(sampleStation));
        when(stationRepository.existsByCodeAndIdNot("ST-CONCURRENT", 1L)).thenReturn(false);
        when(stationRepository.save(any(Station.class))).thenReturn(sampleStation);
        doThrow(new DataIntegrityViolationException("Unique index or primary key violation: uk_stations_code"))
                .when(stationRepository).flush();

        StationDto updateDto = StationDto.builder()
                .code("ST-CONCURRENT")
                .name("Tên Mới")
                .latitude(10.76)
                .longitude(106.66)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build();

        StationConflictException ex = assertThrows(StationConflictException.class, () -> stationService.updateStation(1L, updateDto));
        assertTrue(ex.getMessage().contains("Mã trạm đã tồn tại: ST-CONCURRENT"));
    }

    @Test
    @DisplayName("TC-007: updateStation handles DataIntegrityViolationException for other integrity constraint without misclassifying existing code")
    void testUpdateStation_dataIntegrityViolation_other() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(sampleStation));
        // Khi update trạm 1: existsByCodeAndIdNot("ST-01", 1L) là false, không bị nhầm với existsByCode
        when(stationRepository.existsByCodeAndIdNot("ST-01", 1L)).thenReturn(false);
        when(stationRepository.save(any(Station.class))).thenReturn(sampleStation);
        doThrow(new DataIntegrityViolationException("Check constraint violation: radius_meters_check"))
                .when(stationRepository).flush();

        StationDto updateDto = StationDto.builder()
                .code("ST-01")
                .name("Tên Mới")
                .latitude(10.76)
                .longitude(106.66)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build();

        StationConflictException ex = assertThrows(StationConflictException.class, () -> stationService.updateStation(1L, updateDto));
        assertEquals("Xung đột toàn vẹn dữ liệu trạm dừng", ex.getMessage());
    }

    @Test
    @DisplayName("TC-007: updateStation handles concurrent unique race via existsByCodeAndIdNot fallback")
    void testUpdateStation_dataIntegrityViolation_raceFallback() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(sampleStation));
        when(stationRepository.existsByCodeAndIdNot("ST-RACE", 1L)).thenReturn(false, true);
        when(stationRepository.save(any(Station.class))).thenReturn(sampleStation);
        doThrow(new DataIntegrityViolationException("could not execute statement"))
                .when(stationRepository).flush();

        StationDto updateDto = StationDto.builder()
                .code("ST-RACE")
                .name("Tên Mới")
                .latitude(10.76)
                .longitude(106.66)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build();

        StationConflictException ex = assertThrows(StationConflictException.class, () -> stationService.updateStation(1L, updateDto));
        assertTrue(ex.getMessage().contains("Mã trạm đã tồn tại: ST-RACE"));
    }

    @Test
    @DisplayName("TC-008: updateStation throws StationNotFoundException when id does not exist")
    void testUpdateStation_notFound() {
        when(stationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(StationNotFoundException.class, () -> stationService.updateStation(999L, sampleDto));
    }

    @Test
    @DisplayName("TC-009: deleteStation deletes unreferenced station")
    void testDeleteStation_success() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(sampleStation));
        when(routeStationRepository.existsByStationId(1L)).thenReturn(false);

        stationService.deleteStation(1L);

        verify(stationRepository).delete(sampleStation);
        verify(stationRepository).flush();
    }

    @Test
    @DisplayName("TC-010: deleteStation throws StationConflictException when referenced by RouteStation")
    void testDeleteStation_referencedByRoute() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(sampleStation));
        when(routeStationRepository.existsByStationId(1L)).thenReturn(true);

        assertThrows(StationConflictException.class, () -> stationService.deleteStation(1L));
        verify(stationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("TC-010: deleteStation handles DataIntegrityViolationException on delete")
    void testDeleteStation_dataIntegrityViolation() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(sampleStation));
        when(routeStationRepository.existsByStationId(1L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("FK violation")).when(stationRepository).flush();

        assertThrows(StationConflictException.class, () -> stationService.deleteStation(1L));
    }

    @Test
    @DisplayName("TC-011: deleteStation throws StationNotFoundException when missing id")
    void testDeleteStation_notFound() {
        when(stationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(StationNotFoundException.class, () -> stationService.deleteStation(999L));
        verify(stationRepository, never()).delete(any());
    }
}
