package com.quangkhai.vehiceltracking_backend;

import com.quangkhai.vehiceltracking_backend.util.GeoUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilTest {

    @Test
    void testCalculateDistanceMeters() {
        // Tọa độ Bến xe Miền Đông và Ngã tư Hàng Xanh
        double lat1 = 10.814387;
        double lon1 = 106.711822;
        double lat2 = 10.801642;
        double lon2 = 106.711449;

        double distance = GeoUtil.calculateDistanceMeters(lat1, lon1, lat2, lon2);
        assertTrue(distance > 1300 && distance < 1500, "Khoảng cách thực tế khoảng 1.4km");
    }

    @Test
    void testCalculateBearing() {
        // Đi từ Bắc xuống Nam (kinh độ giữ nguyên, vĩ độ giảm)
        double lat1 = 10.814387;
        double lon1 = 106.711822;
        double lat2 = 10.801642;
        double lon2 = 106.711822;

        double bearing = GeoUtil.calculateBearing(lat1, lon1, lat2, lon2);
        assertEquals(180.0, bearing, 0.1, "Hướng đi thẳng về phía Nam phải là 180 độ");
    }

    @Test
    void testInterpolate() {
        double[] midPoint = GeoUtil.interpolate(10.0, 100.0, 20.0, 110.0, 0.5);
        assertEquals(15.0, midPoint[0], 0.0001);
        assertEquals(105.0, midPoint[1], 0.0001);
    }
}
