package com.example.webdienthoai.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShippingPricingTest {

    @Test
    void freeAtThreshold() {
        assertEquals(0, ShippingPricing.computeForNetMerchandise(new BigDecimal("500000")).intValue());
    }

    @Test
    void freeAboveThreshold() {
        assertEquals(0, ShippingPricing.computeForNetMerchandise(new BigDecimal("600000")).intValue());
    }

    @Test
    void flatBelowThreshold() {
        assertEquals(30_000, ShippingPricing.computeForNetMerchandise(new BigDecimal("499999")).intValue());
    }

    @Test
    void flatForZero() {
        assertEquals(30_000, ShippingPricing.computeForNetMerchandise(BigDecimal.ZERO).intValue());
    }
}
