package com.quangkhai.vehicletracking_backend.driver.controller;

import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.driver.dto.DriverResponse;
import com.quangkhai.vehicletracking_backend.driver.dto.DriverUpsertRequest;
import com.quangkhai.vehicletracking_backend.driver.service.DriverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DriverController.class, properties = {"app.cors.allowed-origins=http://localhost:5173"})
@EnableConfigurationProperties(CorsProperties.class)
class DriverControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DriverService drivers;

    @Test
    void create_returnsLocationAndDto() throws Exception {
        when(drivers.create(any())).thenReturn(new DriverResponse(7L, "Nguyễn Văn A", "0901234567",
                "B2-123", true, Instant.now(), Instant.now()));

        mvc.perform(post("/api/v1/drivers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" Nguyễn Văn A \",\"phoneNumber\":\" 0901234567 \",\"licenseNumber\":\" b2-123 \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/drivers/7"))
                .andExpect(jsonPath("$.licenseNumber").value("B2-123"));

        var input = ArgumentCaptor.forClass(DriverUpsertRequest.class);
        verify(drivers).create(input.capture());
        assertThat(input.getValue().fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(input.getValue().phoneNumber()).isEqualTo("0901234567");
        assertThat(input.getValue().licenseNumber()).isEqualTo("b2-123");
    }

    @Test
    void invalidDriver_doesNotReachService() throws Exception {
        mvc.perform(post("/api/v1/drivers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"phoneNumber\":\"abc\",\"licenseNumber\":\"***\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(drivers);
    }

    @Test
    void updateAndDeactivate_delegate() throws Exception {
        var input = new DriverUpsertRequest("Nguyễn Văn A", "0901234567", "B2-123");
        when(drivers.update(eq(7L), any())).thenReturn(new DriverResponse(7L, input.fullName(), input.phoneNumber(),
                input.licenseNumber(), true, Instant.now(), Instant.now()));

        mvc.perform(put("/api/v1/drivers/7").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Nguyễn Văn A\",\"phoneNumber\":\"0901234567\",\"licenseNumber\":\"B2-123\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/drivers/7")).andExpect(status().isNoContent());

        verify(drivers).update(eq(7L), any());
        verify(drivers).deactivate(7L);
    }

    @Test
    void removedAvatarEndpoints_areNotMapped() throws Exception {
        mvc.perform(get("/api/v1/drivers/7/avatar")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/drivers/7/avatar")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/drivers/7/avatar")).andExpect(status().isNotFound());

        verifyNoInteractions(drivers);
    }
}
