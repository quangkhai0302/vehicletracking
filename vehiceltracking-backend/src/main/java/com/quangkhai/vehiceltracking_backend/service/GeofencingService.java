package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.AlertMessageDto;
import com.quangkhai.vehiceltracking_backend.dto.CheckInEventDto;
import com.quangkhai.vehiceltracking_backend.entity.Station;
import com.quangkhai.vehiceltracking_backend.entity.Trip;
import com.quangkhai.vehiceltracking_backend.entity.TripCheckIn;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import com.quangkhai.vehiceltracking_backend.repository.TripCheckInRepository;
import com.quangkhai.vehiceltracking_backend.repository.TripRepository;
import com.quangkhai.vehiceltracking_backend.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeofencingService {

    private final TripCheckInRepository tripCheckInRepository;
    private final TripRepository tripRepository;
    private final TripService tripService;
    private final SimpMessagingTemplate messagingTemplate;
    private final java.time.Clock clock;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Kiểm tra geofence và tự động check-in khi xe đi vào bán kính trạm tiếp theo
     */
    @Transactional
    public Optional<TripCheckIn> checkAndProcessAutoCheckIn(Long tripId, double vehicleLat, double vehicleLng) {
        // Tìm trạm kế tiếp đang ở trạng thái PENDING
        Optional<TripCheckIn> pendingCheckInOpt = tripCheckInRepository
                .findFirstByTripIdAndStatusOrderByStopOrderAsc(tripId, CheckInStatus.PENDING);

        if (pendingCheckInOpt.isEmpty()) {
            return Optional.empty();
        }

        TripCheckIn checkIn = pendingCheckInOpt.get();
        Station station = checkIn.getStation();
        double radiusMeters = station.getRadiusMeters() != null ? station.getRadiusMeters() : 60.0;

        double distanceMeters = GeoUtil.calculateDistanceMeters(
                vehicleLat, vehicleLng,
                station.getLatitude(), station.getLongitude()
        );

        if (distanceMeters <= radiusMeters) {
            LocalDateTime now = LocalDateTime.now(clock);
            checkIn.setStatus(CheckInStatus.CHECKED_IN);
            checkIn.setActualArrivalTime(now);
            TripCheckIn savedCheckIn = tripCheckInRepository.save(checkIn);

            Trip trip = checkIn.getTrip();
            String plateNumber = trip.getVehicle() != null ? trip.getVehicle().getPlateNumber() : "Unknown";

            log.info("AUTO CHECK-IN: Xe {} đã vào trạm {} (cự ly: {}m, bán kính trạm: {}m)",
                    plateNumber, station.getName(), Math.round(distanceMeters), radiusMeters);

            // Bắn sự kiện CheckInEventDto qua WebSocket
            CheckInEventDto event = CheckInEventDto.builder()
                    .tripId(tripId)
                    .tripCode(trip.getTripCode())
                    .vehicleId(trip.getVehicle() != null ? trip.getVehicle().getId() : null)
                    .plateNumber(plateNumber)
                    .stationId(station.getId())
                    .stationName(station.getName())
                    .stopOrder(checkIn.getStopOrder())
                    .checkInTime(now)
                    .message("Xe " + plateNumber + " đã check-in thành công tại " + station.getName() + " lúc " + now.format(TIME_FORMATTER))
                    .build();

            messagingTemplate.convertAndSend("/topic/checkins", event);

            // Gửi alert thông báo xanh
            AlertMessageDto alert = AlertMessageDto.builder()
                    .id(UUID.randomUUID().toString())
                    .level("INFO")
                    .title("Check-in Thành Công")
                    .message("Xe [" + plateNumber + "] vừa ghi nhận qua trạm: " + station.getName())
                    .tripId(tripId)
                    .vehicleId(trip.getVehicle() != null ? trip.getVehicle().getId() : null)
                    .timestamp(now)
                    .build();
            messagingTemplate.convertAndSend("/topic/alerts", alert);

            // Kiểm tra xem đây có phải trạm cuối cùng không
            boolean hasMore = tripCheckInRepository
                    .findFirstByTripIdAndStatusOrderByStopOrderAsc(tripId, CheckInStatus.PENDING)
                    .isPresent();

            if (!hasMore) {
                tripService.completeTrip(tripId, now);

                AlertMessageDto completedAlert = AlertMessageDto.builder()
                        .id(UUID.randomUUID().toString())
                        .level("INFO")
                        .title("Hoàn thành chuyến đi")
                        .message("Xe [" + plateNumber + "] đã hoàn thành toàn bộ lộ trình chuyến đi " + trip.getTripCode() + "!")
                        .tripId(tripId)
                        .vehicleId(trip.getVehicle() != null ? trip.getVehicle().getId() : null)
                        .timestamp(now)
                        .build();
                messagingTemplate.convertAndSend("/topic/alerts", completedAlert);
            }

            return Optional.of(savedCheckIn);
        }

        return Optional.empty();
    }
}
