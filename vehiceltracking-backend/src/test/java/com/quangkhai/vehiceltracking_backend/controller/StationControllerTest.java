package com.quangkhai.vehiceltracking_backend.controller;

import com.quangkhai.vehiceltracking_backend.dto.StationDto;
import com.quangkhai.vehiceltracking_backend.entity.Station;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.repository.RouteStationRepository;
import com.quangkhai.vehiceltracking_backend.repository.StationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private RouteStationRepository routeStationRepository;

    @Test
    @DisplayName("TC-001: GET /api/stations and /api/stations/{id} return 200; missing id returns 404 Problem Details")
    void testGetStations_and_getById() throws Exception {
        // Create a test fixture
        Station station = stationRepository.save(Station.builder()
                .code("TC01-STA")
                .name("Station TC01")
                .latitude(10.7)
                .longitude(106.6)
                .address("123 Test Street")
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build());

        // GET all
        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());

        // GET existing by id
        mockMvc.perform(get("/api/stations/{id}", station.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(station.getId()))
                .andExpect(jsonPath("$.code").value("TC01-STA"))
                .andExpect(jsonPath("$.stationType").value("STOP"));

        // GET non-existing by id
        mockMvc.perform(get("/api/stations/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("TC-003: POST /api/stations creates START, STOP, and END stations with 201")
    void testCreateStations_threeTypes() throws Exception {
        for (StationType type : StationType.values()) {
            StationDto dto = StationDto.builder()
                    .code("tc03-" + type.name().toLowerCase())
                    .name("Trạm " + type.name())
                    .latitude(10.75)
                    .longitude(106.65)
                    .address("Địa chỉ " + type.name())
                    .radiusMeters(60.0)
                    .stationType(type)
                    .build();

            MvcResult result = mockMvc.perform(post("/api/stations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.code").value("TC03-" + type.name()))
                    .andExpect(jsonPath("$.stationType").value(type.name()))
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            Long createdId = node.get("id").asLong();
            assertTrue(stationRepository.findById(createdId).isPresent());
        }
    }

    @Test
    @DisplayName("TC-004: POST duplicate code with different casing/whitespace returns 409 Problem Details")
    void testCreateStation_duplicateNormalized() throws Exception {
        stationRepository.save(Station.builder()
                .code("TC04-DUP")
                .name("Trạm Gốc")
                .latitude(10.7)
                .longitude(106.6)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build());

        long countBefore = stationRepository.count();

        StationDto dupDto = StationDto.builder()
                .code("  tc04-dup  ")
                .name("Trạm Trùng Lặp")
                .latitude(10.7)
                .longitude(106.6)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build();

        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupDto)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Mã trạm đã tồn tại: TC04-DUP"));

        assertEquals(countBefore, stationRepository.count());
    }

    @Test
    @DisplayName("TC-005: Validation checks reject invalid inputs on POST and accept boundaries")
    void testValidation_postErrorsAndBoundaries() throws Exception {
        long countBefore = stationRepository.count();

        // 1. Blank code
        StationDto blankCode = StationDto.builder()
                .code("   ")
                .name("Valid Name")
                .latitude(10.5).longitude(106.5).radiusMeters(60.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankCode)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        // 2. Code exceeding 50 chars
        StationDto longCode = StationDto.builder()
                .code("A".repeat(51))
                .name("Valid Name")
                .latitude(10.5).longitude(106.5).radiusMeters(60.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 3. Blank name
        StationDto blankName = StationDto.builder()
                .code("TC05-BLANKNAME")
                .name("   ")
                .latitude(10.5).longitude(106.5).radiusMeters(60.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 4. Name exceeding 150 chars
        StationDto longName = StationDto.builder()
                .code("TC05-LONGNAME")
                .name("B".repeat(151))
                .latitude(10.5).longitude(106.5).radiusMeters(60.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 5. Address exceeding 255 chars
        StationDto longAddress = StationDto.builder()
                .code("TC05-LONGADDR")
                .name("Valid Name")
                .latitude(10.5).longitude(106.5).radiusMeters(60.0).stationType(StationType.STOP)
                .address("C".repeat(256))
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longAddress)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 6. Null required fields (null lat, null lng, null radius, null stationType)
        StationDto nullFields = StationDto.builder()
                .code("TC05-NULLS")
                .name("Valid Name")
                .latitude(null).longitude(null).radiusMeters(null).stationType(null)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullFields)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 7. Invalid latitude (< -90, > 90)
        StationDto latTooLow = StationDto.builder()
                .code("TC05-LATLOW")
                .name("Lat Too Low")
                .latitude(-90.1).longitude(106.5).radiusMeters(60.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(latTooLow)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        StationDto latTooHigh = StationDto.builder()
                .code("TC05-LATHIGH")
                .name("Lat Too High")
                .latitude(90.1).longitude(106.5).radiusMeters(60.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(latTooHigh)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 8. Invalid longitude (< -180, > 180)
        StationDto lngTooLow = StationDto.builder()
                .code("TC05-LNGLOW")
                .name("Lng Too Low")
                .latitude(10.5).longitude(-180.1).radiusMeters(60.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lngTooLow)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        StationDto lngTooHigh = StationDto.builder()
                .code("TC05-LNGHIGH")
                .name("Lng Too High")
                .latitude(10.5).longitude(180.1).radiusMeters(60.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lngTooHigh)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 9. Invalid radius (< 30, > 150)
        StationDto radiusTooLow = StationDto.builder()
                .code("TC05-RADLOW")
                .name("Radius Low")
                .latitude(10.5).longitude(106.5).radiusMeters(29.9).stationType(StationType.STOP)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(radiusTooLow)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        StationDto radiusTooHigh = StationDto.builder()
                .code("TC05-RADHIGH")
                .name("Radius High")
                .latitude(10.5).longitude(106.5).radiusMeters(150.1).stationType(StationType.STOP)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(radiusTooHigh)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 10. Invalid enum string
        String badEnumJson = """
                {
                    "code": "TC05-BADENUM",
                    "name": "Bad Enum",
                    "latitude": 10.5,
                    "longitude": 106.6,
                    "radiusMeters": 60,
                    "stationType": "INVALID_ENUM"
                }
                """;
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badEnumJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));

        // Verify DB count did not change after all failed POST requests
        assertEquals(countBefore, stationRepository.count());

        // 11. Boundary valid values: min boundaries (lat -90, lon -180, radius 30, code 50 chars, name 150 chars, address 255 chars)
        StationDto boundaryMin = StationDto.builder()
                .code("TC05-" + "M".repeat(45)) // length exactly 50
                .name("N".repeat(150)) // length exactly 150
                .address("D".repeat(255)) // length exactly 255
                .latitude(-90.0)
                .longitude(-180.0)
                .radiusMeters(30.0)
                .stationType(StationType.START)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(boundaryMin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").value(-90.0))
                .andExpect(jsonPath("$.longitude").value(-180.0))
                .andExpect(jsonPath("$.radiusMeters").value(30.0));

        // 12. Boundary valid values: max boundaries (lat 90, lon 180, radius 150)
        StationDto boundaryMax = StationDto.builder()
                .code("TC05-BNDMAX")
                .name("Boundary Max")
                .latitude(90.0)
                .longitude(180.0)
                .radiusMeters(150.0)
                .stationType(StationType.END)
                .build();
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(boundaryMax)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TC05-BNDMAX"))
                .andExpect(jsonPath("$.latitude").value(90.0))
                .andExpect(jsonPath("$.longitude").value(180.0))
                .andExpect(jsonPath("$.radiusMeters").value(150.0));
    }

    @Test
    @DisplayName("TC-005: Validation checks reject invalid inputs on PUT and preserve existing record")
    void testValidation_putErrorsAndBoundaries() throws Exception {
        Station original = stationRepository.save(Station.builder()
                .code("TC05-PUTORIG")
                .name("Trạm Gốc")
                .latitude(10.77)
                .longitude(106.69)
                .address("Địa chỉ gốc")
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build());

        Long id = original.getId();

        // 1. PUT with blank code
        StationDto invalidCode = StationDto.builder()
                .code("   ")
                .name("Tên Mới")
                .latitude(10.78).longitude(106.70).radiusMeters(65.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(put("/api/stations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCode)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));

        // 2. PUT with blank name
        StationDto invalidName = StationDto.builder()
                .code("TC05-PUTORIG")
                .name("   ")
                .latitude(10.78).longitude(106.70).radiusMeters(65.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(put("/api/stations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 3. PUT with invalid latitude (< -90)
        StationDto invalidLat = StationDto.builder()
                .code("TC05-PUTORIG")
                .name("Tên Mới")
                .latitude(-91.0).longitude(106.70).radiusMeters(65.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(put("/api/stations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLat)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 4. PUT with invalid longitude (> 180)
        StationDto invalidLng = StationDto.builder()
                .code("TC05-PUTORIG")
                .name("Tên Mới")
                .latitude(10.78).longitude(181.0).radiusMeters(65.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(put("/api/stations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLng)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 5. PUT with invalid radius (< 30)
        StationDto invalidRadius = StationDto.builder()
                .code("TC05-PUTORIG")
                .name("Tên Mới")
                .latitude(10.78).longitude(106.70).radiusMeters(20.0).stationType(StationType.STOP)
                .build();
        mockMvc.perform(put("/api/stations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRadius)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 6. PUT with address > 255 chars
        StationDto longAddress = StationDto.builder()
                .code("TC05-PUTORIG")
                .name("Tên Mới")
                .latitude(10.78).longitude(106.70).radiusMeters(60.0).stationType(StationType.STOP)
                .address("X".repeat(256))
                .build();
        mockMvc.perform(put("/api/stations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longAddress)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Verify entity in DB was NOT partially updated
        Station afterFailures = stationRepository.findById(id).orElseThrow();
        assertEquals("TC05-PUTORIG", afterFailures.getCode());
        assertEquals("Trạm Gốc", afterFailures.getName());
        assertEquals(10.77, afterFailures.getLatitude(), 0.0001);
        assertEquals(106.69, afterFailures.getLongitude(), 0.0001);
        assertEquals("Địa chỉ gốc", afterFailures.getAddress());
        assertEquals(60.0, afterFailures.getRadiusMeters(), 0.0001);
        assertEquals(StationType.STOP, afterFailures.getStationType());

        // 7. PUT with boundary values (lat -90.0, lon 180.0, radius 150.0) -> succeeds
        StationDto boundaryPut = StationDto.builder()
                .code("TC05-PUTBND")
                .name("Trạm Sửa Boundary")
                .latitude(-90.0)
                .longitude(180.0)
                .radiusMeters(150.0)
                .stationType(StationType.END)
                .build();
        mockMvc.perform(put("/api/stations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(boundaryPut)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TC05-PUTBND"))
                .andExpect(jsonPath("$.latitude").value(-90.0))
                .andExpect(jsonPath("$.longitude").value(180.0))
                .andExpect(jsonPath("$.radiusMeters").value(150.0))
                .andExpect(jsonPath("$.stationType").value("END"));
    }

    @Test
    @DisplayName("TC-006: PUT /api/stations/{id} updates editable fields and preserves id/createdAt")
    void testUpdateStation_success() throws Exception {
        Station station = stationRepository.save(Station.builder()
                .code("TC06-ORIG")
                .name("Tên Cũ")
                .latitude(10.1)
                .longitude(106.1)
                .radiusMeters(50.0)
                .stationType(StationType.STOP)
                .build());

        Long id = station.getId();
        var originalCreatedAt = station.getCreatedAt();

        StationDto updateDto = StationDto.builder()
                .code("tc06-mod")
                .name("Tên Mới Đã Sửa")
                .latitude(10.9)
                .longitude(106.9)
                .address("Địa chỉ sửa đổi")
                .radiusMeters(120.0)
                .stationType(StationType.START)
                .build();

        mockMvc.perform(put("/api/stations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value("TC06-MOD"))
                .andExpect(jsonPath("$.name").value("Tên Mới Đã Sửa"))
                .andExpect(jsonPath("$.latitude").value(10.9))
                .andExpect(jsonPath("$.longitude").value(106.9))
                .andExpect(jsonPath("$.address").value("Địa chỉ sửa đổi"))
                .andExpect(jsonPath("$.radiusMeters").value(120.0))
                .andExpect(jsonPath("$.stationType").value("START"));

        Station reloaded = stationRepository.findById(id).orElseThrow();
        assertEquals("TC06-MOD", reloaded.getCode());
        assertEquals("Tên Mới Đã Sửa", reloaded.getName());
        assertEquals(originalCreatedAt, reloaded.getCreatedAt());
    }

    @Test
    @DisplayName("TC-007: PUT /api/stations/{id} with code of another station returns 409 without partial update")
    void testUpdateStation_duplicateOtherStation() throws Exception {
        Station stA = stationRepository.save(Station.builder()
                .code("TC07-STA")
                .name("Trạm A")
                .latitude(10.1)
                .longitude(106.1)
                .radiusMeters(50.0)
                .stationType(StationType.STOP)
                .build());

        Station stB = stationRepository.save(Station.builder()
                .code("TC07-STB")
                .name("Trạm B")
                .latitude(10.2)
                .longitude(106.2)
                .radiusMeters(50.0)
                .stationType(StationType.STOP)
                .build());

        // Attempt to update A with code of B
        StationDto conflictingDto = StationDto.builder()
                .code("  tc07-stb  ")
                .name("Tên Muốn Đổi Nhưng Trùng")
                .latitude(10.99)
                .longitude(106.99)
                .radiusMeters(100.0)
                .stationType(StationType.END)
                .build();

        mockMvc.perform(put("/api/stations/{id}", stA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictingDto)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Mã trạm đã tồn tại: TC07-STB"));

        // Verify A was not modified
        Station reloadedA = stationRepository.findById(stA.getId()).orElseThrow();
        assertEquals("TC07-STA", reloadedA.getCode());
        assertEquals("Trạm A", reloadedA.getName());
        assertEquals(10.1, reloadedA.getLatitude());

        // Verify B was not modified
        Station reloadedB = stationRepository.findById(stB.getId()).orElseThrow();
        assertEquals("TC07-STB", reloadedB.getCode());
        assertEquals("Trạm B", reloadedB.getName());
    }

    @Test
    @DisplayName("TC-008: PUT /api/stations/{missingId} returns 404 Problem Details")
    void testUpdateStation_notFound() throws Exception {
        StationDto updateDto = StationDto.builder()
                .code("TC08-MISS")
                .name("Missing")
                .latitude(10.1)
                .longitude(106.1)
                .radiusMeters(50.0)
                .stationType(StationType.STOP)
                .build();

        mockMvc.perform(put("/api/stations/{id}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    @DisplayName("TC-009: DELETE /api/stations/{id} for unreferenced station returns 204 and removes record")
    void testDeleteStation_unreferenced() throws Exception {
        Station station = stationRepository.save(Station.builder()
                .code("TC09-DEL")
                .name("Trạm Sắp Xóa")
                .latitude(10.1)
                .longitude(106.1)
                .radiusMeters(50.0)
                .stationType(StationType.STOP)
                .build());

        Long id = station.getId();

        mockMvc.perform(delete("/api/stations/{id}", id))
                .andExpect(status().isNoContent());

        assertFalse(stationRepository.findById(id).isPresent());

        // Second delete returns 404
        mockMvc.perform(delete("/api/stations/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-010: DELETE /api/stations/{id} for referenced station returns 409 and does not delete")
    void testDeleteStation_referencedByRoute() throws Exception {
        // Station from DataSeeder "ST-MD" is linked to ROUTE-01
        Station seededStation = stationRepository.findByCode("ST-MD").orElseThrow();
        assertTrue(routeStationRepository.existsByStationId(seededStation.getId()));

        mockMvc.perform(delete("/api/stations/{id}", seededStation.getId()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Không thể xóa trạm vì đang được sử dụng trong tuyến đường"));

        assertTrue(stationRepository.findById(seededStation.getId()).isPresent());
    }

    @Test
    @DisplayName("TC-011: DELETE /api/stations/{missingId} returns 404 Problem Details")
    void testDeleteStation_notFound() throws Exception {
        mockMvc.perform(delete("/api/stations/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }
}
