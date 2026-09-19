package com.scenary.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class NominatimProviderTest {

    private final NominatimProvider provider =
            new NominatimProvider(new PlaceProperties(), new ObjectMapper());

    @Test
    void idIsNominatim() {
        assertEquals("nominatim", provider.id());
    }

    @Test
    void parsesShortNameFirst() {
        String body = "{\"name\":\"外滩\",\"display_name\":\"外滩, 黄浦区, 上海市, 中国\"}";
        assertEquals("外滩", provider.parseName(body));
    }

    @Test
    void fallsBackToDisplayName() {
        String body = "{\"name\":\"\",\"display_name\":\"某某路, 某某区, 某某市\"}";
        assertEquals("某某路, 某某区, 某某市", provider.parseName(body));
    }

    @Test
    void oceanErrorBodyYieldsEmpty() {
        assertNull(provider.parseName("{\"error\":\"Unable to geocode\"}"));
    }

    @Test
    void malformedAndBlankBodiesYieldNull() {
        assertNull(provider.parseName(null));
        assertNull(provider.parseName("  "));
        assertNull(provider.parseName("not-json"));
    }

    @Test
    void truncatesToColumnWidth() {
        String longName = "很长的地名".repeat(40);
        String parsed = provider.parseName("{\"name\":\"" + longName + "\"}");
        assertEquals(NominatimProvider.MAX_NAME_LENGTH, parsed.length());
    }

    @Test
    void defaultsKeepProviderDisabledWithTwoSecondTimeout() {
        PlaceProperties properties = new PlaceProperties();
        assertEquals(false, properties.isProviderEnabled());
        assertEquals(Duration.ofMillis(2000).toMillis(), properties.getProviderTimeoutMs());
        assertEquals("http://nominatim:8080", properties.getProviderUrl());
        assertTrue(properties.getCacheTtlDays() == 30);
    }
}
