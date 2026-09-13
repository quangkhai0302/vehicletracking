package com.quangkhai.vehicletracking_backend.simulation.config;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration
public class SimulationConfig {
    @Bean public Clock operationsClock() { return Clock.systemUTC(); }
}
