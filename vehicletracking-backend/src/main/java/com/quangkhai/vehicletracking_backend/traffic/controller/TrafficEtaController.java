package com.quangkhai.vehicletracking_backend.traffic.controller;

import com.quangkhai.vehicletracking_backend.traffic.eta.TripEtaResponse;
import com.quangkhai.vehicletracking_backend.traffic.eta.TrafficEtaService;
import com.quangkhai.vehicletracking_backend.reroute.service.RerouteEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.ObjectProvider;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/eta")
@RequiredArgsConstructor
public class TrafficEtaController {
    private static final System.Logger LOG = System.getLogger(TrafficEtaController.class.getName());
    private final TrafficEtaService eta;
    private final ObjectProvider<RerouteEvaluationService> reroutes;

    @GetMapping
    public TripEtaResponse calculate(@PathVariable long tripId) {
        TripEtaResponse response = eta.calculate(tripId);
        try {
            reroutes.ifAvailable(service -> service.evaluate(tripId, response));
        } catch (RuntimeException ex) {
            // Evaluation has its own transaction; its failure must not discard
            // an ETA already calculated successfully. Do not log provider URLs.
            LOG.log(System.Logger.Level.WARNING, "Reroute evaluation failed for trip " + tripId
                    + " (" + ex.getClass().getSimpleName() + ")");
        }
        return response;
    }
}
