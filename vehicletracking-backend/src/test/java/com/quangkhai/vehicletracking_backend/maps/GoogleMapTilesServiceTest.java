package com.quangkhai.vehicletracking_backend.maps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleMapTilesServiceTest {
    private MockRestServiceServer server;
    private GoogleMapTilesService service;

    @BeforeEach
    void setUp() {
        var properties = new GoogleMapTilesProperties();
        properties.setEnabled(true);
        properties.setApiKey("fixture-map-key");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://tile.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new GoogleMapTilesService(builder.build(), properties);
    }

    @Test
    void createsServerSideSessionAndReturnsTileWithoutExposingSessionContractToFrontend() {
        server.expect(requestTo("https://tile.googleapis.com/v1/createSession?key=fixture-map-key"))
                .andExpect(jsonPath("$.mapType").value("roadmap"))
                .andExpect(jsonPath("$.region").value("VN"))
                .andRespond(withSuccess("{\"session\":\"fixture-session\",\"expiry\":\"4102444800\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://tile.googleapis.com/v1/2dtiles/12/3210/1987?session=fixture-session&key=fixture-map-key"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_PNG));

        var tile = service.tile(GoogleMapTilesService.Style.ROADMAP, 12, 3210, 1987);

        assertThat(tile.data()).containsExactly(1, 2, 3);
        assertThat(tile.contentType()).isEqualTo("image/png");
        server.verify();
    }

    @Test
    void rejectsOutOfRangeTileBeforeCallingGoogle() {
        assertThatThrownBy(() -> service.tile(GoogleMapTilesService.Style.ROADMAP, 2, 4, 0))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
        server.verify();
    }
}
