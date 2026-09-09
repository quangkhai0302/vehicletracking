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
        if (tripId == null || tripId <= 0) {
            log.warn("Auto check-in bị từ chối: tripId không hợp lệ ({})", tripId);
            return Optional.empty();
        }

        if (!Double.isFinite(vehicleLat) || vehicleLat < -90.0 || vehicleLat > 90.0
                || !Double.isFinite(vehicleLng) || vehicleLng < -180.0 || vehicleLng > 180.0) {
            log.warn("Auto check-in bị từ chối: Tọa độ xe không hợp lệ (lat: {}, lng: {}) cho tripId: {}",
                    vehicleLat, vehicleLng, tripId);
            return Optional.empty();
        }

        // Tìm trạm kế tiếp đang ở trạng thái PENDING
        Optional<TripCheckIn> pendingCheckInOpt = tripCheckInRepository
                .findFirstByTripIdAndStatusOrderByStopOrderAsc(tripId, CheckInStatus.PENDING);

        if (pendingCheckInOpt.isEmpty()) {
            return Optional.empty();
        }

        TripCheckIn checkIn = pendingCheckInOpt.get();
        Station station = checkIn.getStation();
        if (station == null) {
            log.warn("Auto check-in bị từ chối: Station của checkIn #{} là null cho tripId: {}", checkIn.getId(), tripId);
            return Optional.empty();
        }

        Double stationLat = station.getLatitude();
        Double stationLng = station.getLongitude();
        if (stationLat == null || !Double.isFinite(stationLat) || stationLat < -90.0 || stationLat > 90.0
                || stationLng == null || !Double.isFinite(stationLng) || stationLng < -180.0 || stationLng > 180.0) {
            log.warn("Auto check-in bị từ chối: Tọa độ station #{} không hợp lệ (lat: {}, lng: {})",
                    station.getId(), stationLat, stationLng);
            return Optional.empty();
        }

        Double radiusMeters = station.getRadiusMeters();
        if (radiusMeters == null || !Double.isFinite(radiusMeters) || radiusMeters < 30.0 || radiusMeters > 150.0) {
            log.warn("Auto check-in bị từ chối: Bán kính station #{} không hợp lệ ({}m)",
                    station.getId(), radiusMeters);
            return Optional.empty();
        }

        double distanceMeters = GeoUtil.calculateDistanceMeters(
                vehicleLat, vehicleLng,
                stationLat, stationLng
        );

        if (distanceMeters <= radiusMeters) {
            LocalDateTime now = LocalDateTime.now(clock);
            checkIn.setStatus(CheckInStatus.CHECKED_IN);
            checkIn.setActualArrivalTime(now);
            TripCheckIn savedCheckIn = tripCheckInRepository.save(checkIn);

            Trip trip = savedCheckIn.getTrip();
            String plateNumber = (trip != null && trip.getVehicle() != null) ? trip.getVehicle().getPlateNumber() : "Unknown";
            Long vehicleId = (trip != null && trip.getVehicle() != null) ? trip.getVehicle().getId() : null;
            String tripCode = trip != null ? trip.getTripCode() : null;

            log.info("AUTO CHECK-IN: Xe {} đã vào trạm {} (cự ly: {}m, bán kính trạm: {}m)",
                    plateNumber, station.getName(), Math.round(distanceMeters), radiusMeters);

            // Bắn sự kiện CheckInEventDto qua WebSocket
            CheckInEventDto event = CheckInEventDto.builder()
                    .tripId(tripId)
                    .tripCode(tripCode)
                    .vehicleId(vehicleId)
                    .plateNumber(plateNumber)
                    .stationId(station.getId())
                    .stationName(station.getName())
                    .stopOrder(savedCheckIn.getStopOrder())
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
                    .vehicleId(vehicleId)
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
                        .message("Xe [" + plateNumber + "] đã hoàn thành toàn bộ lộ trình chuyến đi " + (tripCode != null ? tripCode : "") + "!")
                        .tripId(tripId)
                        .vehicleId(vehicleId)
                        .timestamp(now)
                        .build();
                messagingTemplate.convertAndSend("/topic/alerts", completedAlert);
            }

            return Optional.of(savedCheckIn);
        }

        return Optional.empty();
    }
}
