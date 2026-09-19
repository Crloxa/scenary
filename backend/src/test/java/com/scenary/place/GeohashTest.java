package com.scenary.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GeohashTest {

    @Test
    void encodesKnownVectors() {
        // geohash.org 权威向量：42.605°N, 5.603°W → ezs42（5 位经典示例）
        assertEquals("ezs42", Geohash.encode(42.605, -5.603, 5));
        // 57.64911, 10.40744 → u4pruydqqvj（11 位），前 5 位即 u4pru
        assertEquals("u4pru", Geohash.encode(57.64911, 10.40744, 5));
    }

    @Test
    void nearbyPointsShareCell() {
        // 同一 ≈4.9km 网格内的两点共享 geohash-5（缓存聚合语义的前提）
        assertEquals(Geohash.encode(31.2304, 121.4737, 5),
                Geohash.encode(31.2350, 121.4700, 5));
    }

    @Test
    void respectsPrecisionAndRejectsInvalid() {
        assertEquals(5, Geohash.encode(42.605, -5.603, 5).length());
        assertEquals(1, Geohash.encode(42.605, -5.603, 1).length());
        assertThrows(IllegalArgumentException.class, () -> Geohash.encode(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Geohash.encode(0, 0, 13));
    }
}
