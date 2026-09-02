package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.TripCheckInDto;
import com.quangkhai.vehiceltracking_backend.dto.TripDto;
import com.quangkhai.vehiceltracking_backend.entity.*;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import com.quangkhai.vehiceltracking_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final TripCheckInRepository tripCheckInRepository;
    private final RouteRepository routeRepository;
    private final RouteStationRepository routeStationRepository;
    private final VehicleRepository vehicleRepository;

    public List<TripDto> getAllTrips() {
        return tripRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public TripDto getTripById(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến đi với ID: " + id));
        return toDto(trip);
    }

    @Transactional
    public TripDto createTrip(Long routeId, Long vehicleId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tuyến đường với ID: " + routeId));

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy xe với ID: " + vehicleId));

        List<RouteStation> routeStations = routeStationRepository.findByRouteIdOrderByStopOrderAsc(routeId);
        if (routeStations.isEmpty()) {
            throw new IllegalArgumentException("Tuyến đường chưa có trạm dừng nào!");
        }

        String tripCode = "TRIP-" + System.currentTimeMillis() % 1000000;
        LocalDateTime now = LocalDateTime.now();

        Trip trip = Trip.builder()
                .tripCode(tripCode)
                .route(route)
                .vehicle(vehicle)
                .startTime(now)
                .status(TripStatus.RUNNING)
                .build();

        Trip savedTrip = tripRepository.save(trip);

        // Khởi tạo các điểm check-in dự kiến cho toàn bộ hành trình
        List<TripCheckIn> checkIns = new ArrayList<>();
        LocalDateTime runningSchedule = now;

        for (int i = 0; i < routeStations.size(); i++) {
            RouteStation rs = routeStations.get(i);

            TripCheckIn checkIn = TripCheckIn.builder()
                    .trip(savedTrip)
                    .station(rs.getStation())
                    .stopOrder(rs.getStopOrder())
                    .scheduledArrivalTime(runningSchedule)
                    .status(CheckInStatus.PENDING)
                    .build();

            checkIns.add(checkIn);

            // Cộng dồn thời gian tới trạm tiếp theo
            if (rs.getEstimatedTimeToNextMinutes() != null && rs.getEstimatedTimeToNextMinutes() > 0) {
                long seconds = Math.round(rs.getEstimatedTimeToNextMinutes() * 60);
                runningSchedule = runningSchedule.plusSeconds(seconds);
            }
        }

        tripCheckInRepository.saveAll(checkIns);
        savedTrip.setCheckIns(checkIns);

        // Đặt tọa độ ban đầu của xe tại trạm xuất phát
        Station startStation = routeStations.get(0).getStation();
        vehicle.setCurrentLatitude(startStation.getLatitude());
        vehicle.setCurrentLongitude(startStation.getLongitude());
        vehicle.setStatus(VehicleStatus.IN_TRANSIT);
        vehicleRepository.save(vehicle);

        return toDto(savedTrip);
    }

    @Transactional
    public void completeTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến đi"));
        trip.setStatus(TripStatus.COMPLETED);
        trip.setEndTime(LocalDateTime.now());
        tripRepository.save(trip);

        Vehicle vehicle = trip.getVehicle();
        if (vehicle != null) {
            vehicle.setStatus(VehicleStatus.IDLE);
            vehicle.setCurrentSpeed(0.0);
            vehicleRepository.save(vehicle);
        }
    }

    public TripDto toDto(Trip trip) {
        List<TripCheckInDto> checkInDtos = tripCheckInRepository.findByTripIdOrderByStopOrderAsc(trip.getId())
                .stream()
                .map(ci -> TripCheckInDto.builder()
                        .id(ci.getId())
                        .stationId(ci.getStation().getId())
                        .stationName(ci.getStation().getName())
                        .stationCode(ci.getStation().getCode())
                        .latitude(ci.getStation().getLatitude())
                        .longitude(ci.getStation().getLongitude())
                        .stopOrder(ci.getStopOrder())
                        .scheduledArrivalTime(ci.getScheduledArrivalTime())
                        .actualArrivalTime(ci.getActualArrivalTime())
                        .status(ci.getStatus())
                        .build())
                .collect(Collectors.toList());

        return TripDto.builder()
                .id(trip.getId())
                .tripCode(trip.getTripCode())
                .routeId(trip.getRoute().getId())
                .routeName(trip.getRoute().getName())
                .vehicleId(trip.getVehicle().getId())
                .vehiclePlateNumber(trip.getVehicle().getPlateNumber())
                .startTime(trip.getStartTime())
                .endTime(trip.getEndTime())
                .status(trip.getStatus())
                .checkIns(checkInDtos)
                .createdAt(trip.getCreatedAt())
                .build();
    }
}
