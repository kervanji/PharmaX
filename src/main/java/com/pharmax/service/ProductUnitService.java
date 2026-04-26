package com.pharmax.service;

import com.pharmax.database.Repository.ProductRepository;
import com.pharmax.database.Repository.ProductUnitRepository;
import com.pharmax.model.Product;
import com.pharmax.model.ProductUnit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProductUnitService {
    private static final String DEFAULT_BASE_UNIT = "قطعة";

    private final ProductRepository productRepository;
    private final ProductUnitRepository productUnitRepository;

    public ProductUnitService() {
        this.productRepository = new ProductRepository();
        this.productUnitRepository = new ProductUnitRepository();
    }

    public List<ProductUnit> getUnitsForProduct(Long productId) {
        if (productId == null) {
            return List.of();
        }
        return productUnitRepository.findByProductId(productId);
    }

    public List<ProductUnit> getUnitsForProductOrDefault(Product product) {
        if (product == null) {
            return List.of();
        }
        List<ProductUnit> units = product.getId() != null ? getUnitsForProduct(product.getId()) : List.of();
        if (units.isEmpty()) {
            return List.of(buildDefaultUnit(product));
        }
        return units;
    }

    public ProductUnit buildDefaultUnit(Product product) {
        ProductUnit unit = new ProductUnit();
        unit.setProduct(product);
        unit.setUnitName(resolveBaseUnit(product));
        unit.setBarcode(product != null ? trimToNull(product.getBarcode()) : null);
        unit.setConversionFactor(1.0);
        unit.setSalePrice(product != null ? product.getUnitPrice() : null);
        unit.setSalePriceUsd(product != null ? product.getUnitPriceUsd() : null);
        unit.setIsDefault(true);
        unit.setIsActive(true);
        return unit;
    }

    public void replaceUnitsForProduct(Product product, List<ProductUnit> units) {
        List<ProductUnit> normalized = normalizeUnits(product, units);
        productUnitRepository.replaceForProduct(product, normalized);
    }

    public ProductUnit saveUnit(ProductUnit unit) {
        validateUnit(unit);
        return productUnitRepository.save(unit);
    }

    public Optional<ProductUnit> findByBarcode(String barcode) {
        String normalized = trimToNull(barcode);
        if (normalized == null) {
            return Optional.empty();
        }
        return productUnitRepository.findByBarcode(normalized);
    }

    public Optional<BarcodeLookupResult> findProductOrUnitByBarcode(String barcode) {
        String normalized = trimToNull(barcode);
        if (normalized == null) {
            return Optional.empty();
        }

        Optional<ProductUnit> unit = productUnitRepository.findByBarcode(normalized);
        if (unit.isPresent()) {
            return Optional.of(new BarcodeLookupResult(unit.get().getProduct(), unit.get()));
        }

        Optional<Product> product = productRepository.findByBarcode(normalized);
        return product.map(value -> new BarcodeLookupResult(value, null));
    }

    public String resolveBaseUnit(Product product) {
        if (product == null) {
            return DEFAULT_BASE_UNIT;
        }
        String baseUnit = trimToNull(product.getBaseUnit());
        if (baseUnit != null) {
            return baseUnit;
        }
        String legacyUnit = trimToNull(product.getUnitOfMeasure());
        return legacyUnit != null ? legacyUnit : DEFAULT_BASE_UNIT;
    }

    private List<ProductUnit> normalizeUnits(Product product, List<ProductUnit> units) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("يجب حفظ المنتج قبل حفظ وحدات التعبئة");
        }

        String baseUnit = resolveBaseUnit(product);
        Map<String, ProductUnit> normalizedByName = new LinkedHashMap<>();
        boolean hasDefault = false;
        boolean hasBaseUnit = false;

        if (units != null) {
            for (ProductUnit input : units) {
                if (input == null) {
                    continue;
                }
                ProductUnit unit = copyForProduct(product, input);
                validateUnit(unit);
                String key = unit.getUnitName().trim().toLowerCase();
                if (normalizedByName.containsKey(key)) {
                    throw new IllegalArgumentException("وحدة التعبئة مكررة: " + unit.getUnitName());
                }
                if (baseUnit.equals(unit.getUnitName())) {
                    hasBaseUnit = true;
                    unit.setConversionFactor(1.0);
                    unit.setIsDefault(true);
                }
                if (Boolean.TRUE.equals(unit.getIsDefault())) {
                    hasDefault = true;
                }
                normalizedByName.put(key, unit);
            }
        }

        if (!hasBaseUnit) {
            ProductUnit defaultUnit = buildDefaultUnit(product);
            normalizedByName.put(defaultUnit.getUnitName().trim().toLowerCase(), defaultUnit);
            hasDefault = true;
        }

        if (!hasDefault && !normalizedByName.isEmpty()) {
            normalizedByName.values().iterator().next().setIsDefault(true);
        }

        ensureUniqueBarcodes(normalizedByName.values().stream().toList());
        return new ArrayList<>(normalizedByName.values());
    }

    private ProductUnit copyForProduct(Product product, ProductUnit input) {
        ProductUnit unit = new ProductUnit();
        unit.setProduct(product);
        unit.setUnitName(trimToNull(input.getUnitName()));
        unit.setBarcode(trimToNull(input.getBarcode()));
        unit.setConversionFactor(input.getConversionFactor());
        unit.setSalePrice(input.getSalePrice());
        unit.setSalePriceUsd(input.getSalePriceUsd());
        unit.setIsDefault(Boolean.TRUE.equals(input.getIsDefault()));
        unit.setIsActive(input.getIsActive() == null || Boolean.TRUE.equals(input.getIsActive()));
        return unit;
    }

    private void validateUnit(ProductUnit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("وحدة التعبئة غير صحيحة");
        }
        if (trimToNull(unit.getUnitName()) == null) {
            throw new IllegalArgumentException("اسم وحدة التعبئة مطلوب");
        }
        if (unit.getConversionFactor() == null || unit.getConversionFactor() <= 0) {
            throw new IllegalArgumentException("عامل التحويل يجب أن يكون أكبر من صفر");
        }
        if (unit.getSalePrice() != null && unit.getSalePrice() < 0) {
            throw new IllegalArgumentException("سعر الوحدة لا يمكن أن يكون سالباً");
        }
        if (unit.getSalePriceUsd() != null && unit.getSalePriceUsd() < 0) {
            throw new IllegalArgumentException("سعر الوحدة بالدولار لا يمكن أن يكون سالباً");
        }
    }

    private void ensureUniqueBarcodes(List<ProductUnit> units) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (ProductUnit unit : units) {
            String barcode = trimToNull(unit.getBarcode());
            if (barcode == null) {
                continue;
            }
            String previousUnit = seen.putIfAbsent(barcode, unit.getUnitName());
            if (previousUnit != null) {
                throw new IllegalArgumentException("الباركود مستخدم لأكثر من وحدة: " + barcode);
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class BarcodeLookupResult {
        private final Product product;
        private final ProductUnit productUnit;

        public BarcodeLookupResult(Product product, ProductUnit productUnit) {
            this.product = product;
            this.productUnit = productUnit;
        }

        public Product getProduct() {
            return product;
        }

        public ProductUnit getProductUnit() {
            return productUnit;
        }
    }
}
