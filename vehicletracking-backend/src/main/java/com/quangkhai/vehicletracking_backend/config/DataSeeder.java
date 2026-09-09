package com.quangkhai.vehiceltracking_backend.config;

import com.quangkhai.vehiceltracking_backend.dto.RouteRequestDto;
import com.quangkhai.vehiceltracking_backend.dto.RouteResponseDto;
import com.quangkhai.vehiceltracking_backend.dto.StationDto;
import com.quangkhai.vehiceltracking_backend.dto.VehicleDto;
import com.quangkhai.vehiceltracking_backend.entity.TrafficIncident;
import com.quangkhai.vehiceltracking_backend.enums.IncidentType;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import com.quangkhai.vehiceltracking_backend.repository.StationRepository;
import com.quangkhai.vehiceltracking_backend.repository.TrafficIncidentRepository;
import com.quangkhai.vehiceltracking_backend.service.RouteService;
import com.quangkhai.vehiceltracking_backend.service.StationService;
import com.quangkhai.vehiceltracking_backend.service.TripService;
import com.quangkhai.vehiceltracking_backend.service.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final StationRepository stationRepository;
    private final TrafficIncidentRepository incidentRepository;
    private final StationService stationService;
    private final RouteService routeService;
    private final VehicleService vehicleService;
    private final TripService tripService;

    @Override
    public void run(String... args) {
        if (stationRepository.count() > 0) {
            log.info("Dữ liệu đã tồn tại, bỏ qua DataSeeder.");
            return;
        }

        log.info("Bắt đầu khởi tạo dữ liệu mẫu thực tế cho TP. Hồ Chí Minh...");

        // 1. Tạo 5 trạm dừng
        StationDto st1 = stationService.createStation(StationDto.builder()
                .code("ST-MD")
                .name("Bến xe Miền Đông (Đinh Bộ Lĩnh)")
                .latitude(10.814387)
                .longitude(106.711822)
                .address("292 Đinh Bộ Lĩnh, Phường 26, Bình Thạnh, TP.HCM")
                .radiusMeters(60.0)
                .stationType(StationType.START)
                .build());

        StationDto st2 = stationService.createStation(StationDto.builder()
                .code("ST-HX")
                .name("Ngã tư Hàng Xanh")
                .latitude(10.801642)
                .longitude(106.711449)
                .address("Ngã tư Hàng Xanh, Phường 21, Bình Thạnh, TP.HCM")
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build());

        StationDto st3 = stationService.createStation(StationDto.builder()
                .code("ST-TCV")
                .name("Thảo Cầm Viên Sài Gòn")
                .latitude(10.787612)
                .longitude(106.705298)
                .address("2 Nguyễn Bỉnh Khiêm, Bến Nghé, Quận 1, TP.HCM")
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build());

        StationDto st4 = stationService.createStation(StationDto.builder()
                .code("ST-NHL")
                .name("Nhà hát Thành Phố (Công Trường Lam Sơn)")
                .latitude(10.776735)
                .longitude(106.703212)
                .address("07 Công Trường Lam Sơn, Bến Nghé, Quận 1, TP.HCM")
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build());

        StationDto st5 = stationService.createStation(StationDto.builder()
                .code("ST-BT")
                .name("Bến xe Buýt Sài Gòn (Chợ Bến Thành)")
                .latitude(10.771216)
                .longitude(106.697426)
                .address("Công viên 23/9, Phạm Ngũ Lão, Quận 1, TP.HCM")
                .radiusMeters(70.0)
                .stationType(StationType.END)
                .build());

        log.info("Đã tạo 5 trạm dừng thành công.");

        // 2. Tạo Tuyến đường liên kết 5 trạm
        RouteResponseDto route = routeService.createRoute(RouteRequestDto.builder()
                .code("ROUTE-01")
                .name("Tuyến số 01: BX Miền Đông - Chợ Bến Thành")
                .description("Tuyến buýt trục chính kết nối bến xe Miền Đông với trung tâm Quận 1")
                .stationIds(List.of(st1.getId(), st2.getId(), st3.getId(), st4.getId(), st5.getId()))
                .build());

        log.info("Đã tạo Tuyến đường: {} (Quãng đường: {} km, Thời gian dự kiến: {} phút)",
                route.getName(), route.getTotalDistanceKm(), route.getEstimatedDurationMinutes());

        // 3. Tạo Xe khách / xe buýt
        VehicleDto vehicle = vehicleService.createVehicle(VehicleDto.builder()
                .plateNumber("51B-299.88")
                .model("Thaco City Bus 40 chỗ")
                .status(VehicleStatus.IDLE)
                .currentLatitude(st1.getLatitude())
                .currentLongitude(st1.getLongitude())
                .currentSpeed(0.0)
                .currentHeading(180.0)
                .build());

        log.info("Đã tạo phương tiện xe: {} ({})", vehicle.getPlateNumber(), vehicle.getModel());

        // 4. Tạo 1 chuyến đi mẫu
        var trip = tripService.createTrip(route.getId(), vehicle.getId());
        log.info("Đã khởi tạo chuyến đi mẫu mã: {} cho xe {}", trip.getTripCode(), trip.getVehiclePlateNumber());

        // 5. Tạo 1 điểm sự cố giao thông thực tế trên lộ trình (khu vực giữa Ngã tư Hàng Xanh và Thảo Cầm Viên)
        TrafficIncident incident = TrafficIncident.builder()
                .title("Ùn tắc nghiêm trọng tại Cầu Điện Biên Phủ")
                .type(IncidentType.CONGESTION)
                .latitude(10.795000)
                .longitude(106.708500)
                .radiusMeters(250.0)
                .speedReductionPercent(75.0) // giảm 75% tốc độ khi đi qua khu vực này
                .description("Va chạm giữa 2 ô tô vào giờ tan tầm gây ùn tắc kéo dài hướng về Quận 1.")
                .active(true)
                .build();
        incidentRepository.save(incident);

        log.info("Đã tạo sự cố giao thông giả lập: {}", incident.getTitle());
        log.info("Khởi tạo dữ liệu mẫu hoàn tất!");
    }
}
