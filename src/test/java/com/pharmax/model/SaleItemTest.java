package com.pharmax.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaleItemTest {

    @Test
    void legacySaleItemsFallbackToConversionFactorOne() {
        SaleItem item = new SaleItem();
        item.setQuantity(3.0);
        item.setConversionFactor(null);
        item.setBaseQuantity(null);

        assertEquals(1.0, item.getEffectiveConversionFactor());
        assertEquals(3.0, item.getEffectiveBaseQuantity());
    }

    @Test
    void storedBaseQuantityWinsWhenPresent() {
        SaleItem item = new SaleItem();
        item.setQuantity(2.0);
        item.setConversionFactor(10.0);
        item.setBaseQuantity(15.0);

        assertEquals(10.0, item.getEffectiveConversionFactor());
        assertEquals(15.0, item.getEffectiveBaseQuantity());
    }
}
