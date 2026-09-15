package com.quangkhai.vehicletracking_backend.route.controller;
import com.quangkhai.vehicletracking_backend.route.dto.*;
import com.quangkhai.vehicletracking_backend.route.service.RouteShapeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/routes/{id}/shape")
public class RouteShapeController {
    private final RouteShapeService service;
    @PostMapping("/preview") public RouteDetailResponse preview(@PathVariable long id,@Valid @RequestBody RouteShapeRequest input) { return service.preview(id,input); }
    @PutMapping public RouteDetailResponse save(@PathVariable long id,@Valid @RequestBody RouteShapeRequest input) { return service.save(id,input,false); }
    @PostMapping("/copy") public RouteDetailResponse copy(@PathVariable long id,@Valid @RequestBody RouteShapeRequest input) { return service.save(id,input,true); }
}
