package com.quangkhai.vehicletracking_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VehicletrackingBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(VehicletrackingBackendApplication.class, args);
    }
}
