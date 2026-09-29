package com.quangkhai.vehicletracking_backend.station.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.quangkhai.vehicletracking_backend.station.configs.HereGeocodingProperties;
import com.quangkhai.vehicletracking_backend.station.dto.StationAddressResponse;
@Service 
public class StationGeocodingService {
    
    private final RestClient restClient;
    private final HereGeocodingProperties properties;
    
    public StationGeocodingService(
        @Qualifier("hereGeocodingRestClient") RestClient restClient,
        HereGeocodingProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public StationAddressResponse reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 
                "Chức năng lấy địa chỉ đang tắt hoặc chưa được cấu hình."
            );
        }
        
        HereResponse response;

        try {
            response = restClient.get()
                .uri(builder -> builder
                        .path("/v1/revgeocode")
                        .queryParam(
                            "at",
                            latitude.toPlainString() + "," + longitude.toPlainString()
                        )
                        .queryParam("lang", "vi")
                        .queryParam("limit", 1)
                        .queryParam("apiKey", properties.apiKey())
                        .build())
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        (request, providerResponse) -> {
                            int status = providerResponse.getStatusCode().value();

                            if (status == 401 || status == 403) {
                                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                    "Dịch vụ địa chỉ chưa được cấp quyền sử dụng."
                                );
                            }

                            if (status == 429 || status == 500) {
                                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                    "Dịch vụ địa chỉ đang bận. Hãy thử lại sau."
                                );
                            }

                            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Dịch vụ địa chỉ từ chối yêu cầu."
                            );

                        }
                )
                .body(HereResponse.class);

        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Không thể kết nối dịch vụ địa chỉ. Bạn có thể nhập thủ công."
            );

        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Không đọc được phản hồi từ dịch vụ địa chỉ."
            );
        }

        if (response == null || response.items() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Dịch vụ địa chỉ trả về dữ liệu không hợp lệ."
            );
        }

        if (response.items().isEmpty()) {
            return new StationAddressResponse(null, null);
        }

        HereItem item = response.items().getFirst();

        if (item == null || item.address() == null || item.address().label() == null || item.address().label().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Kết quả tra cứu không có địa chỉ hợp lệ."
            );
        }

        Double distance = item.distance();

        if (distance != null && (!Double.isFinite(distance) || distance < 0)) {
            distance = null;
        }

        return new StationAddressResponse(
            item.address().label().strip(),
            distance
        );
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereResponse(List<HereItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereItem(HereAddress address, Double distance) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HereAddress(String label) {
    }
}
