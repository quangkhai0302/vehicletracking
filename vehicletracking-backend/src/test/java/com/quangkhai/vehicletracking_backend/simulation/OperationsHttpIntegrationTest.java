package com.quangkhai.vehicletracking_backend.simulation;

import com.quangkhai.vehicletracking_backend.simulation.service.SimulationService;
import com.quangkhai.vehicletracking_backend.telemetry.service.OperationsStreamService;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.dto.*;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "here.routing.enabled=false","here.traffic.enabled=false","app.cors.allowed-origins=http://127.0.0.1:5173"})
class OperationsHttpIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17");
    @LocalServerPort int port;
    @Autowired TripService trips;
    @Autowired SimulationService simulator;
    @Autowired OperationsStreamService stream;
    @Autowired StationRepository stations;
    @Autowired RouteRepository routes;
    @Autowired VehicleRepository vehicles;
    @Autowired ObjectMapper json;
    static final AtomicInteger ids=new AtomicInteger();
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String base() { return "http://127.0.0.1:"+port+"/api/v1"; }
    private TripDetailResponse fixture() {
        var a=stations.saveAndFlush(TripFixtures.station("A · SIMULATOR HTTP"));
        var b=stations.saveAndFlush(TripFixtures.station("B · SIMULATOR HTTP"));
        var route=routes.saveAndFlush(SimulationFixtures.route(a,b));
        var vehicle=vehicles.saveAndFlush(new VehicleEntity("HTTP"+ids.incrementAndGet(),"Xe kiểm tra HTTP 006",null));
        return trips.create(new TripCreateRequest(vehicle.getId(),route.getId(),Instant.now()));
    }
    private HttpResponse<String> post(String path,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base()+path)).timeout(Duration.ofSeconds(8))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request,HttpResponse.BodyHandlers.ofString());
    }
    @Test void httpValidationSourceAndLifecycleContracts() throws Exception {
        var trip=fixture();long id=trip.trip().id();
        assertThat(post("/telemetry","{}").statusCode()).isEqualTo(400);
        var body=json.writeValueAsString(Map.of("eventId",UUID.randomUUID(),"vehicleId",trip.trip().vehicleId(),"tripId",id,
            "recordedAt",Instant.now().toString(),"latitude",10.77,"longitude",106.7,"speedKmh",0,"heading",0,"accuracyMeters",0,"source","SIMULATOR"));
        assertThat(post("/telemetry",body).statusCode()).isEqualTo(400);
        var play=post("/trips/"+id+"/simulation/play","{}");
        assertThat(play.statusCode()).isEqualTo(200);
        assertThat(json.readTree(play.body()).get("status").asString()).isEqualTo("RUNNING");
        assertThat(post("/trips/"+id+"/simulation/speed","{\"multiplier\":2}").statusCode()).isEqualTo(400);
        assertThat(post("/trips/"+id+"/simulation/speed","{}").statusCode()).isEqualTo(400);
        assertThat(post("/trips/"+id+"/simulation/pause","{}").statusCode()).isEqualTo(200);
        assertThat(post("/trips/"+id+"/simulation/stop","{}").statusCode()).isEqualTo(200);
        assertThat(post("/trips/"+id+"/simulation/play","{}").statusCode()).isEqualTo(409);
        assertThat(post("/trips/"+id+"/simulation/reset","{}").statusCode()).isEqualTo(200);
    }
    private InputStream connect(String lastId) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base()+"/telemetry/stream")).header("Origin","http://127.0.0.1:5173");
        if(lastId!=null) builder.header("Last-Event-ID",lastId);
        var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("http://127.0.0.1:5173");
        return response.body();
    }
    private JsonNode readSnapshot(InputStream stream,long tripId,boolean requirePosition) throws Exception {
        var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
        for(String line;(line=reader.readLine())!=null;) {
            if(!line.startsWith("data:")) continue;
            var snapshot=json.readTree(line.substring(5));
            if(!requirePosition) return snapshot;
            for(var point:snapshot.get("positions")) if(point.get("tripId").asLong()==tripId) return snapshot;
        }
        throw new EOFException("No snapshot");
    }
    @Test void twoStreamsReceiveCommittedStateAndReconnectResyncs() throws Exception {
        var trip=fixture();long id=trip.trip().id();
        try(var one=connect(null);var two=connect(null);var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->readSnapshot(one,id,true));
            var second=executor.submit(()->readSnapshot(two,id,true));
            long start=System.nanoTime();
            assertThat(post("/trips/"+id+"/simulation/play","{}").statusCode()).isEqualTo(200);
            assertThat(first.get(8,TimeUnit.SECONDS).get("positions").isArray()).isTrue();
            assertThat(second.get(8,TimeUnit.SECONDS).get("simulations").isArray()).isTrue();
            long latency=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            System.out.println("006 SSE two subscribers command-to-snapshot latency_ms="+latency);
        }
        simulator.pause(id);
        try(var reconnect=connect("old-or-unknown-event");var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var snapshot=executor.submit(()->readSnapshot(reconnect,id,true)).get(8,TimeUnit.SECONDS);
            boolean paused=false;
            for(var run:snapshot.get("simulations")) if(run.get("tripId").asLong()==id) paused=run.get("status").asString().equals("PAUSED");
            assertThat(paused).isTrue();
        }
        simulator.stop(id);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(stream.subscriberCount()!=0 && System.nanoTime()<deadline) Thread.sleep(100);
        assertThat(stream.subscriberCount()).isZero();

        // Optional live browser extension of this HTTP test; writes only this test container's database.
        if(Boolean.getBoolean("verification.browser006")) {
            var browserTrip=fixture();
            var root=Path.of("..").toAbsolutePath().normalize();
            var builder=new ProcessBuilder("node",root.resolve("docs/features/006-telemetry-simulator/verification/live-browser.mjs").toString());
            builder.directory(root.toFile()); builder.inheritIO();
            builder.environment().put("VERIFICATION_API",base());
            builder.environment().put("VERIFICATION_TRIP",Long.toString(browserTrip.trip().id()));
            var process=builder.start();
            assertThat(process.waitFor(180,TimeUnit.SECONDS)).as("Browser verification completed").isTrue();
            assertThat(process.exitValue()).isZero();
        }
    }
}
