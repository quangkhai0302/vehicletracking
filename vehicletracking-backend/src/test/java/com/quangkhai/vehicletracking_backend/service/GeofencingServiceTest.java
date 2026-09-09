package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.CheckInEventDto;
import com.quangkhai.vehiceltracking_backend.entity.*;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import com.quangkhai.vehiceltracking_backend.repository.*;
import com.quangkhai.vehiceltracking_backend.util.GeoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    private Station midStation;
    private Station endStation;
    private TripCheckIn checkInStart;
    private TripCheckIn checkInMid;
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

        midStation = Station.builder()
                .id(2L)
                .code("STM")
                .name("Trạm Bình Triệu")
                .latitude(10.8150)
                .longitude(106.7150)
                .radiusMeters(100.0)
                .stationType(StationType.STOP)
                .build();

        endStation = Station.builder()
                .id(3L)
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
                .status(CheckInStatus.PENDING)
                .scheduledArrivalTime(fixedNow)
                .build();

        checkInMid = TripCheckIn.builder()
                .id(202L)
                .trip(testTrip)
                .station(midStation)
                .stopOrder(2)
                .status(CheckInStatus.PENDING)
                .scheduledArrivalTime(fixedNow.plusMinutes(15))
                .build();

        checkInEnd = TripCheckIn.builder()
                .id(203L)
                .trip(testTrip)
                .station(endStation)
                .stopOrder(3)
                .status(CheckInStatus.PENDING)
                .scheduledArrivalTime(fixedNow.plusMinutes(30))
                .build();
    }

    @Test
    @DisplayName("TC-001: Check-in khi trong geofence, lưu trạng thái CHECKED_IN và timestamp từ fixed Clock")
    void checkAndProcessAutoCheckIn_InGeofence_TransitionsToCheckedInAndRecordsTime() {
        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(checkInStart))
                .thenReturn(Optional.of(checkInMid)); // sau save vẫn còn checkInMid
        when(tripCheckInRepository.save(checkInStart)).thenReturn(checkInStart);

        // Xe ở ngay tâm của startStation (khoảng cách = 0 <= 100m)
        Optional<TripCheckIn> result = geofencingService.checkAndProcessAutoCheckIn(
                100L, startStation.getLatitude(), startStation.getLongitude()
        );

        assertTrue(result.isPresent());
        assertEquals(CheckInStatus.CHECKED_IN, result.get().getStatus());
        assertEquals(fixedNow, result.get().getActualArrivalTime());
        verify(tripCheckInRepository, times(1)).save(checkInStart);

        // Event được phát qua WebSocket
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/checkins"), any(CheckInEventDto.class));
        // Chưa phải trạm cuối nên không gọi completeTrip
        verify(tripService, never()).completeTrip(anyLong(), any());
    }

    @Test
    @DisplayName("TC-002: Kiểm tra exact boundary (distance == radius) thì CHECKED_IN, outside (distance > radius) thì giữ PENDING")
    void checkAndProcessAutoCheckIn_BoundaryWithinRadius_TransitionsSuccessfully_OutsideRadius_NoOp() {
        // 1. Exact boundary test: tính khoảng cách thực tế giữa xe và trạm, rồi đặt radius bằng chính khoảng cách đó
        double vehicleLat = startStation.getLatitude() + 0.0008;
        double vehicleLng = startStation.getLongitude();
        double exactDistance = GeoUtil.calculateDistanceMeters(vehicleLat, vehicleLng, startStation.getLatitude(), startStation.getLongitude());
        // exactDistance khoảng 88.94m, nằm trong khoảng hợp lệ [30.0, 150.0]
        startStation.setRadiusMeters(exactDistance);
        assertEquals(exactDistance, startStation.getRadiusMeters(), 1e-6, "Khoảng cách cự ly phải đúng bằng bán kính trạm (exact boundary)");

        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(checkInStart))
                .thenReturn(Optional.of(checkInMid));
        when(tripCheckInRepository.save(checkInStart)).thenReturn(checkInStart);

        Optional<TripCheckIn> boundaryResult = geofencingService.checkAndProcessAutoCheckIn(100L, vehicleLat, vehicleLng);
        assertTrue(boundaryResult.isPresent(), "Tại đúng biên khoảng cách == radius thì phải check-in thành công");
        assertEquals(CheckInStatus.CHECKED_IN, boundaryResult.get().getStatus());
        verify(tripCheckInRepository, times(1)).save(checkInStart);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/checkins"), any(CheckInEventDto.class));

        // 2. Outside test: bán kính nhỏ hơn cự ly thực tế (distance > radius) dù chỉ 0.5m
        reset(tripCheckInRepository, messagingTemplate);
        checkInStart.setStatus(CheckInStatus.PENDING);
        checkInStart.setActualArrivalTime(null);
        startStation.setRadiusMeters(exactDistance - 0.5); // bán kính nhỏ hơn cự ly

        double distOutside = GeoUtil.calculateDistanceMeters(vehicleLat, vehicleLng, startStation.getLatitude(), startStation.getLongitude());
        assertTrue(distOutside > startStation.getRadiusMeters(), "Tọa độ outside phải > radius");

        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(checkInStart));

        Optional<TripCheckIn> outsideResult = geofencingService.checkAndProcessAutoCheckIn(100L, vehicleLat, vehicleLng);
        assertTrue(outsideResult.isEmpty(), "Khi distance > radius thì không được check-in");
        assertEquals(CheckInStatus.PENDING, checkInStart.getStatus());
        assertNull(checkInStart.getActualArrivalTime());
        verify(tripCheckInRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("TC-003: Không bỏ qua trạm sau - khi xe tới trạm 2 nhưng trạm 1 đang PENDING thì không check-in trạm 2")
    void checkAndProcessAutoCheckIn_PreservesStopOrder_DoesNotSkipPendingStation() {
        // Query luôn trả về stopOrder 1 trước tiên theo convention
        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(checkInStart));

        // Xe ở ngay tọa độ của midStation (trạm 2)
        double vehicleAtMidLat = midStation.getLatitude();
        double vehicleAtMidLng = midStation.getLongitude();

        Optional<TripCheckIn> result = geofencingService.checkAndProcessAutoCheckIn(100L, vehicleAtMidLat, vehicleAtMidLng);

        // Kết quả trả về empty vì xe chưa vào bán kính trạm 1 (cách ~6km > 100m)
        assertTrue(result.isEmpty());
        assertEquals(CheckInStatus.PENDING, checkInStart.getStatus());
        assertEquals(CheckInStatus.PENDING, checkInMid.getStatus());
        verify(tripCheckInRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("TC-004: Gọi lặp không tạo duplicate - các lần gọi tiếp theo không save hoặc publish lại cho record đã CHECKED_IN")
    void checkAndProcessAutoCheckIn_RepeatedCalls_DoesNotCreateDuplicateTransitionsOrEvents() {
        // Lần 1: trả về checkInStart PENDING, sau save tìm tiếp thì thấy checkInMid
        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(checkInStart))
                .thenReturn(Optional.of(checkInMid));
        when(tripCheckInRepository.save(checkInStart)).thenReturn(checkInStart);

        // Gọi lần 1 tại trạm 1
        Optional<TripCheckIn> firstCall = geofencingService.checkAndProcessAutoCheckIn(
                100L, startStation.getLatitude(), startStation.getLongitude()
        );
        assertTrue(firstCall.isPresent());
        assertEquals(CheckInStatus.CHECKED_IN, checkInStart.getStatus());

        // Lần 2: tiếp tục gọi tại trạm 1, lúc này next PENDING là checkInMid (cách xa)
        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(checkInMid));

        Optional<TripCheckIn> secondCall = geofencingService.checkAndProcessAutoCheckIn(
                100L, startStation.getLatitude(), startStation.getLongitude()
        );
        assertTrue(secondCall.isEmpty());

        // Chỉ save đúng 1 lần cho checkInStart, không save checkInMid
        verify(tripCheckInRepository, times(1)).save(checkInStart);
        verify(tripCheckInRepository, never()).save(checkInMid);
        // Chỉ phát đúng 1 event checkin
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/checkins"), any(CheckInEventDto.class));
        verify(tripService, never()).completeTrip(anyLong(), any());
    }

    @Test
    @DisplayName("TC-007: Payload event sau save - đảm bảo save database trước khi publish WebSocket và payload đầy đủ")
    void checkAndProcessAutoCheckIn_PersistBeforePublish_AndEventPayloadMatches() {
        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(checkInStart))
                .thenReturn(Optional.of(checkInMid));
        when(tripCheckInRepository.save(checkInStart)).thenReturn(checkInStart);

        geofencingService.checkAndProcessAutoCheckIn(100L, startStation.getLatitude(), startStation.getLongitude());

        // Kiểm tra thứ tự: save phải gọi trước khi publish event
        InOrder inOrder = inOrder(tripCheckInRepository, messagingTemplate);
        inOrder.verify(tripCheckInRepository).save(checkInStart);
        inOrder.verify(messagingTemplate).convertAndSend(eq("/topic/checkins"), any(CheckInEventDto.class));

        // Kiểm tra chi tiết payload
        ArgumentCaptor<CheckInEventDto> eventCaptor = ArgumentCaptor.forClass(CheckInEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/checkins"), eventCaptor.capture());
        CheckInEventDto payload = eventCaptor.getValue();

        assertNotNull(payload);
        assertEquals(100L, payload.getTripId());
        assertEquals("TRIP-100", payload.getTripCode());
        assertEquals(10L, payload.getVehicleId());
        assertEquals("51B-11111", payload.getPlateNumber());
        assertEquals(1L, payload.getStationId());
        assertEquals("Trạm Bến Thành", payload.getStationName());
        assertEquals(1, payload.getStopOrder());
        assertEquals(fixedNow, payload.getCheckInTime());
        assertNotNull(payload.getMessage());
        assertTrue(payload.getMessage().contains("Trạm Bến Thành"));
    }

    @Test
    @DisplayName("TC-009: Invalid inputs và no-pending an toàn không mutate state và không ném ngoại lệ")
    void checkAndProcessAutoCheckIn_InvalidInputsAndNoPending_SafelyReturnsEmptyWithoutMutation() {
        // 1. tripId null hoặc <= 0
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(null, 10.7719, 106.6983).isEmpty());
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(0L, 10.7719, 106.6983).isEmpty());
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(-5L, 10.7719, 106.6983).isEmpty());

        // 2. vehicleLat / vehicleLng không hợp lệ (NaN, Infinity, out of bounds)
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, Double.NaN, 106.6983).isEmpty());
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, Double.POSITIVE_INFINITY, 106.6983).isEmpty());
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 91.0, 106.6983).isEmpty());
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, -90.1, 106.6983).isEmpty());
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, Double.NaN).isEmpty());
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, Double.NEGATIVE_INFINITY).isEmpty());
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 180.1).isEmpty());
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, -180.1).isEmpty());

        // 3. Station null
        TripCheckIn nullStationCheckIn = TripCheckIn.builder().id(999L).trip(testTrip).station(null).stopOrder(1).status(CheckInStatus.PENDING).build();
        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(nullStationCheckIn));
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        // 4. Station latitude / longitude không hợp lệ (null, NaN, Infinity, ngoài [-90, 90] / [-180, 180])
        Station invalidCoordStation = Station.builder().id(881L).latitude(null).longitude(106.6983).radiusMeters(60.0).build();
        TripCheckIn invalidCoordCheckIn = TripCheckIn.builder().id(991L).trip(testTrip).station(invalidCoordStation).stopOrder(1).status(CheckInStatus.PENDING).build();
        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(invalidCoordCheckIn));

        // Latitude null, NaN, Infinity, out-of-range
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        invalidCoordStation.setLatitude(Double.NaN);
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        invalidCoordStation.setLatitude(Double.POSITIVE_INFINITY);
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        invalidCoordStation.setLatitude(90.1);
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        invalidCoordStation.setLatitude(-90.1);
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        // Longitude null, NaN, Infinity, out-of-range
        invalidCoordStation.setLatitude(10.7719);
        invalidCoordStation.setLongitude(null);
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        invalidCoordStation.setLongitude(Double.NaN);
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        invalidCoordStation.setLongitude(Double.NEGATIVE_INFINITY);
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        invalidCoordStation.setLongitude(180.1);
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        invalidCoordStation.setLongitude(-180.1);
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        // 5. Station radius không hợp lệ (null, NaN, < 30, > 150)
        Station invalidRadiusStation = Station.builder().id(888L).latitude(10.7719).longitude(106.6983).radiusMeters(null).build();
        TripCheckIn invalidRadiusCheckIn = TripCheckIn.builder().id(998L).trip(testTrip).station(invalidRadiusStation).stopOrder(1).status(CheckInStatus.PENDING).build();
        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(invalidRadiusCheckIn));
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        invalidRadiusStation.setRadiusMeters(20.0); // < 30m
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        invalidRadiusStation.setRadiusMeters(200.0); // > 150m
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        invalidRadiusStation.setRadiusMeters(Double.NaN);
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        // 6. No pending record
        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.empty());
        assertTrue(geofencingService.checkAndProcessAutoCheckIn(100L, 10.7719, 106.6983).isEmpty());

        // Đảm bảo không có tác vụ mutate, publish hoặc completeTrip nào diễn ra
        verify(tripCheckInRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(tripService, never()).completeTrip(anyLong(), any());
    }

    @Test
    @DisplayName("TC-010: Trạm cuối check-in thành công ủy nhiệm hoàn thành chuyến đi cho TripService cùng mốc timestamp")
    void checkAndProcessAutoCheckIn_FinalStation_DelegatesToTripServiceWithSameTimestamp() {
        when(tripCheckInRepository.findFirstByTripIdAndStatusOrderByStopOrderAsc(100L, CheckInStatus.PENDING))
                .thenReturn(Optional.of(checkInEnd))
                .thenReturn(Optional.empty()); // Sau khi lưu trạm cuối thì không còn trạm PENDING nào
        when(tripCheckInRepository.save(checkInEnd)).thenReturn(checkInEnd);

        // Xe đi vào bán kính của trạm cuối (khoảng cách = 0)
        double currentLat = endStation.getLatitude();
        double currentLng = endStation.getLongitude();

        Optional<TripCheckIn> result = geofencingService.checkAndProcessAutoCheckIn(100L, currentLat, currentLng);

        assertTrue(result.isPresent());
        // 1. Trạm cuối được check-in với fixedNow
        verify(tripCheckInRepository).save(checkInEnd);
        assertEquals(CheckInStatus.CHECKED_IN, checkInEnd.getStatus());
        assertEquals(fixedNow, checkInEnd.getActualArrivalTime());

        // 2. Phát event check-in qua WebSocket với cùng fixedNow
        ArgumentCaptor<CheckInEventDto> eventCaptor = ArgumentCaptor.forClass(CheckInEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/checkins"), eventCaptor.capture());
        CheckInEventDto event = eventCaptor.getValue();
        assertEquals(100L, event.getTripId());
        assertEquals(3L, event.getStationId());
        assertEquals(3, event.getStopOrder());
        assertEquals(fixedNow, event.getCheckInTime());

        // 3. Ủy nhiệm hoàn thành chuyến đi cho TripService.completeTrip với đúng fixedNow
        verify(tripService, times(1)).completeTrip(100L, fixedNow);
    }
}
