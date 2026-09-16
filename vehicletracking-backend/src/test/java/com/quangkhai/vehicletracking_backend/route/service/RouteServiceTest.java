package com.quangkhai.vehicletracking_backend.route.service;

import com.quangkhai.vehicletracking_backend.route.dto.RouteCreateRequest;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.dto.RouteSummaryResponse;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
import com.quangkhai.vehicletracking_backend.route.error.RouteErrorCode;
import com.quangkhai.vehicletracking_backend.route.error.RouteOperationException;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedRoute;
import com.quangkhai.vehicletracking_backend.route.provider.CalculatedSection;
import com.quangkhai.vehicletracking_backend.route.provider.RoutingProvider;
import com.quangkhai.vehicletracking_backend.route.provider.RoutingWaypoint;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private StationRepository stationRepository;

    @Mock
    private RoutingProvider routingProvider;

    @Mock
    private RoutePersistenceService routePersistenceService;

    @Mock
    private TripRepository tripRepository;

    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeService = new RouteService(
                routeRepository,
                stationRepository,
                routingProvider,
                routePersistenceService,
                tripRepository
        );
    }

    @Test
    void create_normalizesNameAndPreservesStopOrderAndCalculatesMetrics() {
        StationEntity s1 = createStation(1L, "Trạm 1", "10.80", "106.70");
        StationEntity s2 = createStation(2L, "Trạm 2", "10.81", "106.71");
        StationEntity s3 = createStation(3L, "Trạm 3", "10.82", "106.72");

        when(stationRepository.findAllByIdInAndActiveTrue(Set.of(1L, 2L, 3L)))
                .thenReturn(List.of(s3, s1, s2)); // intentionally shuffled order from DB

        Instant depTime = Instant.parse("2026-09-11T05:00:00Z");
        CalculatedRoute calculatedRoute = new CalculatedRoute(depTime, List.of(
                new CalculatedSection(1, 2, "poly1", 2000L, 300L, 250L),
                new CalculatedSection(2, 3, "poly2", 3000L, 400L, 350L)
        ));
        when(routingProvider.calculate(any())).thenReturn(calculatedRoute);

        when(routePersistenceService.persistRoute(any())).thenAnswer(inv -> {
            RouteEntity entity = inv.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 100L);
            ReflectionTestUtils.setField(entity, "createdAt", Instant.now());
            return entity;
        });

        RouteCreateRequest request = new RouteCreateRequest(
                "   Tuyến Mẫu 01   ",
                List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(2L, 60),
                        new RouteCreateRequest.RouteStopInput(3L, 0)
                )
        );

        RouteDetailResponse response = routeService.create(request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.name()).isEqualTo("Tuyến Mẫu 01");
        assertThat(response.totalDistanceMeters()).isEqualTo(5000L);
        assertThat(response.estimatedTravelDurationSeconds()).isEqualTo(700L);
        assertThat(response.baseTravelDurationSeconds()).isEqualTo(600L);
        assertThat(response.totalDwellDurationSeconds()).isEqualTo(60L);
        assertThat(response.estimatedTripDurationSeconds()).isEqualTo(760L);

        // Verify stops order and derived roles
        assertThat(response.stops()).hasSize(3);
        assertThat(response.stops().get(0).role()).isEqualTo("START");
        assertThat(response.stops().get(0).sequenceNumber()).isEqualTo(1);
        assertThat(response.stops().get(0).stationId()).isEqualTo(1L);
        assertThat(response.stops().get(0).arrivalOffsetSeconds()).isEqualTo(0L);
        assertThat(response.stops().get(0).departureOffsetSeconds()).isEqualTo(0L);

        assertThat(response.stops().get(1).role()).isEqualTo("STOP");
        assertThat(response.stops().get(1).sequenceNumber()).isEqualTo(2);
        assertThat(response.stops().get(1).stationId()).isEqualTo(2L);
        assertThat(response.stops().get(1).distanceFromPreviousMeters()).isEqualTo(2000L);
        assertThat(response.stops().get(1).travelDurationFromPreviousSeconds()).isEqualTo(300L);
        assertThat(response.stops().get(1).arrivalOffsetSeconds()).isEqualTo(300L);
        assertThat(response.stops().get(1).departureOffsetSeconds()).isEqualTo(360L);

        assertThat(response.stops().get(2).role()).isEqualTo("END");
        assertThat(response.stops().get(2).sequenceNumber()).isEqualTo(3);
        assertThat(response.stops().get(2).stationId()).isEqualTo(3L);
        assertThat(response.stops().get(2).distanceFromPreviousMeters()).isEqualTo(3000L);
        assertThat(response.stops().get(2).travelDurationFromPreviousSeconds()).isEqualTo(400L);
        assertThat(response.stops().get(2).arrivalOffsetSeconds()).isEqualTo(760L);
        assertThat(response.stops().get(2).departureOffsetSeconds()).isEqualTo(760L);

        ArgumentCaptor<RouteEntity> captor = ArgumentCaptor.forClass(RouteEntity.class);
        verify(routePersistenceService).persistRoute(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Tuyến Mẫu 01");
    }

    @Test
    void create_acceptsLoopRoute_whenStartAndEndShareSameStation() {
        StationEntity s1 = createStation(1L, "Trạm 1", "10.80", "106.70");
        StationEntity s2 = createStation(2L, "Trạm 2", "10.81", "106.71");

        when(stationRepository.findAllByIdInAndActiveTrue(Set.of(1L, 2L)))
                .thenReturn(List.of(s1, s2));

        CalculatedRoute calculatedRoute = new CalculatedRoute(Instant.now(), List.of(
                new CalculatedSection(1, 2, "poly1", 1000L, 100L, 90L),
                new CalculatedSection(2, 3, "poly2", 1000L, 100L, 90L)
        ));
        when(routingProvider.calculate(any())).thenReturn(calculatedRoute);
        when(routePersistenceService.persistRoute(any())).thenAnswer(inv -> inv.getArgument(0));

        RouteCreateRequest request = new RouteCreateRequest(
                "Tuyến Vòng",
                List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(2L, 30),
                        new RouteCreateRequest.RouteStopInput(1L, 0)
                )
        );

        RouteDetailResponse response = routeService.create(request);
        assertThat(response.stops()).hasSize(3);
        assertThat(response.stops().get(0).stationId()).isEqualTo(1L);
        assertThat(response.stops().get(0).role()).isEqualTo("START");
        assertThat(response.stops().get(2).stationId()).isEqualTo(1L);
        assertThat(response.stops().get(2).role()).isEqualTo("END");
    }

    @Test
    void create_whenConsecutiveStopsDuplicate_throwsBadRequest() {
        RouteCreateRequest request = new RouteCreateRequest(
                "Tuyến Trùng",
                List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(2L, 0)
                )
        );

        assertThatThrownBy(() -> routeService.create(request))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_VALIDATION_FAILED);
                });

        verify(routingProvider, never()).calculate(any());
        verify(routePersistenceService, never()).persistRoute(any());
    }

    @Test
    void create_whenStartOrEndHasNonZeroDwell_throwsBadRequest() {
        RouteCreateRequest request = new RouteCreateRequest(
                "Tuyến Sai Dwell",
                List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 60), // Start cannot have dwell
                        new RouteCreateRequest.RouteStopInput(2L, 0)
                )
        );

        assertThatThrownBy(() -> routeService.create(request))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_VALIDATION_FAILED);
                });
    }

    @Test
    void create_whenStationsUnavailableOrInactive_throwsUnprocessableEntity() {
        StationEntity s1 = createStation(1L, "Trạm 1", "10.80", "106.70");
        when(stationRepository.findAllByIdInAndActiveTrue(Set.of(1L, 99L)))
                .thenReturn(List.of(s1)); // 99L is missing or inactive

        RouteCreateRequest request = new RouteCreateRequest(
                "Tuyến Thiếu Trạm",
                List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(99L, 0)
                )
        );

        assertThatThrownBy(() -> routeService.create(request))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_STATION_UNAVAILABLE);
                    assertThat(roe.getMessage()).contains("99");
                });

        verify(routingProvider, never()).calculate(any());
        verify(routePersistenceService, never()).persistRoute(any());
    }

    @Test
    void create_whenProviderFails_doesNotCallPersistence() {
        StationEntity s1 = createStation(1L, "Trạm 1", "10.80", "106.70");
        StationEntity s2 = createStation(2L, "Trạm 2", "10.81", "106.71");

        when(stationRepository.findAllByIdInAndActiveTrue(Set.of(1L, 2L)))
                .thenReturn(List.of(s1, s2));

        when(routingProvider.calculate(any())).thenThrow(new RouteOperationException(
                HttpStatus.GATEWAY_TIMEOUT,
                RouteErrorCode.ROUTING_PROVIDER_TIMEOUT,
                "Timeout"
        ));

        RouteCreateRequest request = new RouteCreateRequest(
                "Tuyến Timeout",
                List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(2L, 0)
                )
        );

        assertThatThrownBy(() -> routeService.create(request))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                });

        verify(routePersistenceService, never()).persistRoute(any());
    }

    @Test
    void create_whenProviderThrowsInvalidResponse_doesNotCallPersistence() {
        StationEntity s1 = createStation(1L, "Trạm 1", "10.80", "106.70");
        StationEntity s2 = createStation(2L, "Trạm 2", "10.81", "106.71");

        when(stationRepository.findAllByIdInAndActiveTrue(Set.of(1L, 2L)))
                .thenReturn(List.of(s1, s2));

        when(routingProvider.calculate(any())).thenThrow(new RouteOperationException(
                HttpStatus.BAD_GATEWAY,
                RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE,
                "Malformed response"
        ));

        RouteCreateRequest request = new RouteCreateRequest(
                "Tuyến Malformed",
                List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(2L, 0)
                )
        );

        assertThatThrownBy(() -> routeService.create(request))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTING_PROVIDER_INVALID_RESPONSE);
                });

        verify(routePersistenceService, never()).persistRoute(any());
    }

    @Test
    void update_flushesOrphanedChildrenBeforeAttachingReplacementSnapshot() {
        StationEntity oldStart = createStation(1L, "Trạm cũ 1", "10.80", "106.70");
        StationEntity oldEnd = createStation(2L, "Trạm cũ 2", "10.81", "106.71");
        StationEntity newEnd = createStation(3L, "Trạm mới 3", "10.82", "106.72");

        RouteEntity current = new RouteEntity(
                "Tuyến cũ", RouteTransportMode.CAR, RoutingProviderName.HERE,
                1000L, 100L, 90L, 0L, 100L, Instant.now(), Instant.now()
        );
        ReflectionTestUtils.setField(current, "id", 7L);
        current.addStop(new com.quangkhai.vehicletracking_backend.route.entity.RouteStopEntity(
                oldStart, 1, oldStart.getName(), oldStart.getLatitude(), oldStart.getLongitude(), 0));
        current.addStop(new com.quangkhai.vehicletracking_backend.route.entity.RouteStopEntity(
                oldEnd, 2, oldEnd.getName(), oldEnd.getLatitude(), oldEnd.getLongitude(), 0));
        current.addSection(new com.quangkhai.vehicletracking_backend.route.entity.RouteSectionEntity(
                1, 2, "old-polyline", 1000L, 100L, 90L));

        when(routeRepository.findLockedById(7L)).thenReturn(Optional.of(current));
        when(tripRepository.existsByRouteId(7L)).thenReturn(false);
        when(stationRepository.findAllByIdInAndActiveTrue(Set.of(1L, 3L)))
                .thenReturn(List.of(oldStart, newEnd));
        when(routingProvider.calculate(any())).thenReturn(new CalculatedRoute(Instant.now(), List.of(
                new CalculatedSection(1, 2, "new-polyline", 2200L, 240L, 210L)
        )));
        when(routeRepository.saveAndFlush(current)).thenReturn(current);

        RouteDetailResponse response = routeService.update(7L, new RouteCreateRequest(
                "Tuyến mới",
                List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(3L, 0)
                )
        ));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Tuyến mới");
        assertThat(response.stops()).extracting(RouteDetailResponse.RouteStopResponse::stationId)
                .containsExactly(1L, 3L);
        assertThat(response.sections()).extracting(RouteDetailResponse.RouteSectionResponse::encodedPolyline)
                .containsExactly("new-polyline");

        InOrder order = inOrder(routeRepository);
        order.verify(routeRepository).flush();
        order.verify(routeRepository).saveAndFlush(current);
    }

    @Test
    void update_whenRouteAlreadyUsedByTrip_returnsConflictWithoutCallingHere() {
        RouteEntity current = new RouteEntity(
                "Tuyến đang dùng", RouteTransportMode.CAR, RoutingProviderName.HERE,
                1000L, 100L, 90L, 0L, 100L, Instant.now(), Instant.now()
        );
        ReflectionTestUtils.setField(current, "id", 8L);
        when(routeRepository.findLockedById(8L)).thenReturn(Optional.of(current));
        when(tripRepository.existsByRouteId(8L)).thenReturn(true);

        assertThatThrownBy(() -> routeService.update(8L, new RouteCreateRequest(
                "Tuyến sửa", List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(2L, 0)
                )
        )))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_VALIDATION_FAILED);
                });

        verify(routingProvider, never()).calculate(any());
        verify(routeRepository, never()).flush();
    }

    @Test
    void create_whenNullStopItem_throwsBadRequest() {
        RouteCreateRequest request = new RouteCreateRequest(
                "Tuyến Null Stop",
                java.util.Arrays.asList(
                        null,
                        new RouteCreateRequest.RouteStopInput(2L, 0)
                )
        );

        assertThatThrownBy(() -> routeService.create(request))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_VALIDATION_FAILED);
                });
    }

    @Test
    void create_whenDwellNegative_throwsBadRequest() {
        RouteCreateRequest request = new RouteCreateRequest(
                "Tuyến Dwell Âm",
                List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(2L, -5),
                        new RouteCreateRequest.RouteStopInput(3L, 0)
                )
        );

        assertThatThrownBy(() -> routeService.create(request))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_VALIDATION_FAILED);
                });
    }

    @Test
    void create_whenDwellOver3600_throwsBadRequest() {
        RouteCreateRequest request = new RouteCreateRequest(
                "Tuyến Dwell Quá 3600",
                List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(2L, 3601),
                        new RouteCreateRequest.RouteStopInput(3L, 0)
                )
        );

        assertThatThrownBy(() -> routeService.create(request))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_VALIDATION_FAILED);
                });
    }

    @Test
    void create_whenBlankName_throwsBadRequest() {
        RouteCreateRequest request = new RouteCreateRequest(
                "    ",
                List.of(
                        new RouteCreateRequest.RouteStopInput(1L, 0),
                        new RouteCreateRequest.RouteStopInput(2L, 0)
                )
        );

        assertThatThrownBy(() -> routeService.create(request))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_VALIDATION_FAILED);
                });
    }

    @Test
    void create_whenLessThan2Stops_throwsBadRequest() {
        RouteCreateRequest request = new RouteCreateRequest(
                "Tuyến 1 trạm",
                List.of(new RouteCreateRequest.RouteStopInput(1L, 0))
        );

        assertThatThrownBy(() -> routeService.create(request))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_VALIDATION_FAILED);
                });
    }

    @Test
    void findById_whenExists_returnsDetailResponse() {
        RouteEntity route = new RouteEntity(
                "Tuyến 1",
                RouteTransportMode.CAR,
                RoutingProviderName.HERE,
                1000L, 100L, 90L, 0L, 100L,
                Instant.now(), Instant.now()
        );
        ReflectionTestUtils.setField(route, "id", 5L);
        when(routeRepository.findById(5L)).thenReturn(Optional.of(route));

        RouteDetailResponse response = routeService.findById(5L);
        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.name()).isEqualTo("Tuyến 1");
    }

    @Test
    void findById_whenNotFound_throwsNotFoundException() {
        when(routeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.findById(999L))
                .isInstanceOf(RouteOperationException.class)
                .satisfies(ex -> {
                    RouteOperationException roe = (RouteOperationException) ex;
                    assertThat(roe.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(roe.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_NOT_FOUND);
                });
    }

    private StationEntity createStation(Long id, String name, String lat, String lng) {
        StationEntity entity = new StationEntity(name, "Addr", new BigDecimal(lat), new BigDecimal(lng), 50);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
