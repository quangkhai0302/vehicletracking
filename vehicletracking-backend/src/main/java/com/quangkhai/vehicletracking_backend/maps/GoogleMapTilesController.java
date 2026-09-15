package com.quangkhai.vehicletracking_backend.maps;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maps/tiles")
public class GoogleMapTilesController {
    private final GoogleMapTilesService service;

    public GoogleMapTilesController(GoogleMapTilesService service) { this.service = service; }

    @GetMapping("/{style}/{z}/{x}/{y}")
    public ResponseEntity<byte[]> tile(@PathVariable GoogleMapTilesService.Style style,
                                       @PathVariable int z, @PathVariable int x, @PathVariable int y) {
        GoogleMapTilesService.Tile tile = service.tile(style, z, x, y);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, tile.cacheControl())
                .contentType(MediaType.parseMediaType(tile.contentType()))
                .body(tile.data());
    }
}
