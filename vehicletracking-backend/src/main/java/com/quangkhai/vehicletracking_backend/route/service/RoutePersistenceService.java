package com.quangkhai.vehicletracking_backend.route.service;

import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoutePersistenceService {

    private final RouteRepository routeRepository;

    public RoutePersistenceService(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    @Transactional
    public RouteEntity persistRoute(RouteEntity route) {
        return routeRepository.save(route);
    }
}
