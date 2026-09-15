package com.quangkhai.vehicletracking_backend.traffic.controller;

import com.quangkhai.vehicletracking_backend.traffic.*;
import com.quangkhai.vehicletracking_backend.traffic.service.TrafficQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/traffic")
@RequiredArgsConstructor
public class TrafficController {
    private final TrafficQueryService traffic;

    @GetMapping("/flow")
    public TrafficEnvelope<TrafficFlowSegment> flow(@RequestParam double west, @RequestParam double south,
                                                     @RequestParam double east, @RequestParam double north) {
        return traffic.flow(west, south, east, north);
    }

    @GetMapping("/incidents")
    public TrafficEnvelope<TrafficIncident> incidents(@RequestParam double west, @RequestParam double south,
                                                       @RequestParam double east, @RequestParam double north) {
        return traffic.incidents(west, south, east, north);
    }

    @GetMapping(value = {"/tiles/{z}/{x}/{y}", "/tiles/{z}/{x}/{y}.png"}, produces = org.springframework.http.MediaType.IMAGE_PNG_VALUE)
    public org.springframework.http.ResponseEntity<byte[]> tile(
            @org.springframework.web.bind.annotation.PathVariable int z,
            @org.springframework.web.bind.annotation.PathVariable int x,
            @org.springframework.web.bind.annotation.PathVariable int y
    ) {
        byte[] image = traffic.tile(z, x, y);
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.maxAge(60, java.util.concurrent.TimeUnit.SECONDS).cachePublic())
                .body(image);
    }

    @GetMapping("/map-tiles/{style}/{z}/{x}/{y}")
    public org.springframework.http.ResponseEntity<byte[]> mapTile(
            @org.springframework.web.bind.annotation.PathVariable HereMapStyle style,
            @org.springframework.web.bind.annotation.PathVariable int z,
            @org.springframework.web.bind.annotation.PathVariable int x,
            @org.springframework.web.bind.annotation.PathVariable int y
    ) {
        TrafficProvider.RasterTile tile = traffic.mapTile(style, z, x, y);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(tile.contentType()))
                .cacheControl(org.springframework.http.CacheControl.maxAge(1, java.util.concurrent.TimeUnit.DAYS).cachePublic())
                .body(tile.data());
    }

    @GetMapping("/vector-styles/{style}")
    public org.springframework.http.ResponseEntity<byte[]> vectorStyle(
            @org.springframework.web.bind.annotation.PathVariable HereVectorStyle style
    ) {
        return vectorResponse(traffic.vectorStyle(style));
    }

    @GetMapping("/vector-resources")
    public org.springframework.http.ResponseEntity<byte[]> vectorResource(
            @RequestParam String url
    ) {
        return vectorResponse(traffic.vectorResource(url));
    }

    private org.springframework.http.ResponseEntity<byte[]> vectorResponse(TrafficProvider.RasterTile resource) {
        if (resource.data().length == 0) return org.springframework.http.ResponseEntity.notFound().build();
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(resource.contentType()))
                .cacheControl(org.springframework.http.CacheControl.maxAge(1, java.util.concurrent.TimeUnit.DAYS).cachePublic())
                .body(resource.data());
    }
}
