package com.pharmax.service;

import com.pharmax.model.Product;
import com.pharmax.model.ProductUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductUnitServiceTest {

    @Test
    void returnsDefaultUnitWhenProductHasNoSavedUnits() {
        Product product = new Product();
        product.setBaseUnit("strip");
        product.setUnitPrice(3500.0);
        product.setUnitPriceUsd(2.5);
        product.setBarcode("STRIP-001");

        ProductUnitService service = new ProductUnitService();
        List<ProductUnit> units = service.getUnitsForProductOrDefault(product);

        assertEquals(1, units.size());

        ProductUnit unit = units.get(0);
        assertEquals("strip", unit.getUnitName());
        assertEquals("STRIP-001", unit.getBarcode());
        assertEquals(1.0, unit.getEffectiveConversionFactor());
        assertEquals(3500.0, unit.getSalePrice());
        assertEquals(2.5, unit.getSalePriceUsd());
        assertTrue(Boolean.TRUE.equals(unit.getIsDefault()));
        assertTrue(Boolean.TRUE.equals(unit.getIsActive()));
        assertNotNull(unit.getProduct());
    }
}
