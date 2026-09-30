package com.quangkhai.vehicletracking_backend.driverportal;

import com.quangkhai.vehicletracking_backend.driverportal.service.DriverRouteRebaser;
import com.quangkhai.vehicletracking_backend.route.dto.*;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DriverRouteRebaserTest {
    List<RouteDetailResponse.RouteSectionResponse> route() {
        return List.of(new RouteDetailResponse.RouteSectionResponse(1, 2, FlexiblePolyline.encode(List.of(
                new FlexiblePolyline.Point(10, 106), new FlexiblePolyline.Point(10.001, 106), new FlexiblePolyline.Point(10.002, 106))),
                222, 20, 20, List.of(new RouteInstruction("depart", null, "Khởi hành", 0), new RouteInstruction("turn", "right", "Rẽ phải", 2))));
    }
    @Test void removesTravelledPrefixAndKeepsInstructionsAligned() {
        var rebased = DriverRouteRebaser.rebase(route(), 10.0012, 106);
        assertThat(FlexiblePolyline.decode(rebased.getFirst().encodedPolyline()).getFirst().latitude()).isEqualTo(10.0012);
        assertThat(rebased.getFirst().distanceMeters()).isLessThan(100);
        assertThat(rebased.getFirst().instructions()).singleElement().satisfies(i -> assertThat(i.offset()).isEqualTo(1));
    }
    @Test void rejectsPositionOutsideChosenRoad() {
        assertThatThrownBy(() -> DriverRouteRebaser.rebase(route(), 10, 106.01)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsAlreadyArrivedOrMissingGeometry() {
        assertThatThrownBy(() -> DriverRouteRebaser.rebase(route(), 10.002, 106)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DriverRouteRebaser.rebase(List.of(), 10, 106)).isInstanceOf(IllegalArgumentException.class);
    }
}
