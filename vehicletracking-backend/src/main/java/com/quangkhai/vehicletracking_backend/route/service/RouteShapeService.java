package com.quangkhai.vehicletracking_backend.route.service;

import com.quangkhai.vehicletracking_backend.route.dto.*;
import com.quangkhai.vehicletracking_backend.route.entity.*;
import com.quangkhai.vehicletracking_backend.route.provider.*;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service @RequiredArgsConstructor
public class RouteShapeService {
    private final RouteRepository routes;
    private final TripRepository trips;
    private final RoutingProviderRegistry routing;

    @Transactional(readOnly=true)
    public RouteDetailResponse preview(long id,RouteShapeRequest request) {
        return RouteDetailResponse.from(calculate(require(id,false),request,false,true));
    }
    @Transactional
    public RouteDetailResponse save(long id,RouteShapeRequest request,boolean copy) {
        var source=require(id,true);
        if (!copy && trips.existsByRouteId(id)) throw new ResponseStatusException(CONFLICT,
            "Tuyến đã có chuyến đi. Chọn Lưu thành tuyến mới để giữ lịch sử chuyến cũ.");
        var replacement=calculate(source,request,copy,copy);
        if (copy) return RouteDetailResponse.from(routes.saveAndFlush(replacement));
        source.replaceDefinitionMetadata(replacement);
        source.clearDefinitionChildren();routes.flush();
        source.appendDefinitionChildren(replacement);
        return RouteDetailResponse.from(routes.saveAndFlush(source));
    }
    private RouteEntity require(long id,boolean lock) {
        var source=(lock?routes.findLockedById(id):routes.findById(id))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,"Không tìm thấy tuyến."));
        if (!source.isActive()) throw new ResponseStatusException(CONFLICT,"Tuyến đã ngừng sử dụng.");
        if (source.getStops().stream().anyMatch(s -> !s.getStation().isActive()))
            throw new ResponseStatusException(CONFLICT,"Tuyến có trạm đã ngừng sử dụng.");
        return source;
    }
    private RouteEntity calculate(RouteEntity source,RouteShapeRequest request,boolean copy,boolean allowProviderChange) {
        if (request==null || request.points()==null || request.points().size()>20)
            throw new ResponseStatusException(BAD_REQUEST,"Tối đa 20 điểm dẫn đường.");
        int previous=2;
        for (var p:request.points()) {
            if (p==null || p.latitude()==null || p.longitude()==null || p.latitude().abs().doubleValue()>90
                || p.longitude().abs().doubleValue()>180 || p.destinationStopSequence()<previous
                || p.destinationStopSequence()>source.getStops().size())
                throw new ResponseStatusException(BAD_REQUEST,"Điểm dẫn đường sai vị trí hoặc thứ tự chặng.");
            previous=p.destinationStopSequence();
        }
        List<RoutingWaypoint> waypoints=new ArrayList<>();
        List<Integer> destinations=new ArrayList<>();
        for (var stop:source.getStops()) {
            for (var p:request.points()) if (p.destinationStopSequence()==stop.getSequenceNumber()) {
                waypoints.add(new RoutingWaypoint(null,"Điểm dẫn đường",p.latitude(),p.longitude(),waypoints.size()+1,0));
                destinations.add(stop.getSequenceNumber());
            }
            waypoints.add(new RoutingWaypoint(stop.getStation().getId(),stop.getStationNameSnapshot(),
                stop.getLatitudeSnapshot(),stop.getLongitudeSnapshot(),waypoints.size()+1,stop.getDwellDurationSeconds()));
            destinations.add(stop.getSequenceNumber());
        }
        if (waypoints.size()>50) throw new ResponseStatusException(BAD_REQUEST,"Tổng trạm và điểm dẫn đường tối đa 50.");
        RoutingProviderName target=request.targetProvider()==null?source.getRoutingProvider():request.targetProvider();
        if (!allowProviderChange && request.targetProvider()!=null && target!=source.getRoutingProvider())
            throw new ResponseStatusException(CONFLICT,"Chỉ được đổi nhà cung cấp khi lưu thành tuyến mới.");
        var result=routing.calculate(target,
            new RoutingRequest(waypoints,source.getTransportMode(),Instant.now(),false));
        if (result.sections().isEmpty()) throw new ResponseStatusException(BAD_GATEWAY,"Nhà cung cấp không trả về tuyến.");
        long distance=result.sections().stream().mapToLong(CalculatedSection::distanceMeters).sum();
        long travel=result.sections().stream().mapToLong(CalculatedSection::travelDurationSeconds).sum();
        long base=result.sections().stream().mapToLong(CalculatedSection::baseTravelDurationSeconds).sum();
        String name=copy?source.getName().substring(0,Math.min(138,source.getName().length()))+" (bản sao)":source.getName();
        var replacement=new RouteEntity(name,source.getTransportMode(),target,distance,travel,base,
            source.getTotalDwellDurationSeconds(),travel+source.getTotalDwellDurationSeconds(),result.estimatedDepartureAt(),Instant.now());
        source.getStops().forEach(s -> replacement.addStop(new RouteStopEntity(s.getStation(),s.getSequenceNumber(),
            s.getStationNameSnapshot(),s.getLatitudeSnapshot(),s.getLongitudeSnapshot(),s.getDwellDurationSeconds())));
        for (var section:result.sections()) {
            int local=section.destinationStopSequence();
            if (local<2 || local>destinations.size()) throw new ResponseStatusException(BAD_GATEWAY,"Nhà cung cấp trả về chặng không hợp lệ.");
            replacement.addSection(new RouteSectionEntity(section.sectionSequence(),destinations.get(local-1),section.encodedPolyline(),
                section.polylineEncoding(),
                section.distanceMeters(),section.travelDurationSeconds(),section.baseTravelDurationSeconds(),section.trafficIntervals()));
        }
        int order=0;
        for (var p:request.points()) replacement.addShapingPoint(new RouteShapePointEntity(++order,p.destinationStopSequence(),p.latitude(),p.longitude()));
        return replacement;
    }
}
