package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.CheckInEventDto;
import com.quangkhai.vehiceltracking_backend.entity.*;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import com.quangkhai.vehiceltracking_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeofencingServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripCheckInRepository tripCheckInRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private TripService tripService;

    @Mock
    private Clock clock;

    @InjectMocks
    private GeofencingService geofencingService;

    private final ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Instant fixedInstant = Instant.parse("2026-09-04T03:00:00Z");
    private LocalDateTime fixedNow;

    private Trip testTrip;
    private Vehicle testVehicle;
    private Station startStation;
    private Station endStation;
    private TripCheckIn checkInStart;
    private TripCheckIn checkInEnd;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(fixedInstant);
        lenient().when(clock.getZone()).thenReturn(zoneId);
        fixedNow = LocalDateTime.ofInstant(fixedInstant, zoneId);

        startStation = Station.builder()
                .id(1L)
                .code("STA")
                .name("Trạm Bến Thành")
                .latitude(10.7719)
                .longitude(106.6983)
                .radiusMeters(100.0)
                .stationType(StationType.START)
                .build();

        endStation = Station.builder()
                .id(2L)
                .code("STB")
                .name("Trạm Suối Tiên")
                .latitude(10.8659)
                .longitude(106.8028)
                .radiusMeters(100.0)
                .stationType(StationType.END)
                .build();

        testVehicle = Vehicle.builder()
                .id(10L)
                .plateNumber("51B-11111")
                .status(VehicleStatus.IN_TRANSIT)
                .currentSpeed(40.0)
                .build();

        testTrip = Trip.builder()
                .id(100L)
                .tripCode("TRIP-100")
                .vehicle(testVehicle)
                .status(TripStatus.RUNNING)
                .startTime(fixedNow.minusMinutes(30))
                .build();

        checkInStart = TripCheckIn.builder()
                .id(201L)
                .trip(testTrip)
                .station(startStation)
                .stopOrder(1)
                .status(CheckInStatus.CHECKED_IN)
                .actualArrivalTime(fixedNow.minusMinutes(30))
                .scheduledArrivalTime(fixedNow.minusMinutes(30))
                .build();

        checkInEnd = TripCheckIn.builder()
                .id(202L)
                .trip(testTrip)
                .station(endStation)
                .stopOrder(2)
                .status(CheckInStatus.PENDING)
                .scheduledArrivalTime(fixedNow)
                .build();
    }

    @Test
    @DisplayName("TC-005: Geofencing tự động check-in trạm cuối và ủy nhiệm hoàn thành chuyến đi cho TripService")
    void checkAndProcessAutoCheckIn_FinalStation_DelegatesToTripServiceCompleteTrip() {
        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(checkInEnd))
                .thenReturn(Optional.empty());
        when(tripCheckInRepository.save(checkInEnd)).thenReturn(checkInEnd);

        // Xe đi vào bán kính của trạm cuối (khoảng cách = 0)
        double currentLat = endStation.getLatitude();
        double currentLng = endStation.getLongitude();

        geofencingService.checkAndProcessAutoCheckIn(100L, currentLat, currentLng);

        // 1. Trạm cuối được check-in
        verify(tripCheckInRepository).save(checkInEnd);
        assertEquals(CheckInStatus.CHECKED_IN, checkInEnd.getStatus());
        assertEquals(fixedNow, checkInEnd.getActualArrivalTime());

        // 2. Phát event check-in qua WebSocket
        ArgumentCaptor<CheckInEventDto> eventCaptor = ArgumentCaptor.forClass(CheckInEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/checkins"), eventCaptor.capture());
        CheckInEventDto event = eventCaptor.getValue();
        assertEquals(100L, event.getTripId());
        assertEquals(2L, event.getStationId());
        assertEquals(2, event.getStopOrder());
        assertEquals(fixedNow, event.getCheckInTime());

        // 3. Ủy nhiệm hoàn thành chuyến đi cho TripService.completeTrip với đúng checkInTime
        verify(tripService).completeTrip(100L, fixedNow);
    }
}
