package com.pharmax.service;

import com.pharmax.database.Repository.ProductRepository;
import com.pharmax.model.Category;
import com.pharmax.model.Product;
import com.pharmax.model.ProductBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InventoryService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);
    private final AccessControlService accessControlService = new AccessControlService();
    private final AuditLogService auditLogService = new AuditLogService();
    private static final double QTY_EPSILON = 1e-6;
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductBatchService productBatchService;
    
    public InventoryService() {
        this.productRepository = new ProductRepository();
        this.categoryService = new CategoryService();
        this.productBatchService = new ProductBatchService();
    }
    
    public Product createProduct(Product product) {
        logger.info("Creating new product: {}", product.getName());
        
        // Generate unique product code
        if (product.getProductCode() == null || product.getProductCode().isEmpty()) {
            product.setProductCode(generateProductCode());
        }
        
        // Validate product data
        validateProduct(product);
        
        return productRepository.save(product);
    }
    
    public Product updateProduct(Product product) {
        logger.info("Updating product: {}", product.getId());
        
        // Validate product data
        validateProduct(product);
        
        return productRepository.save(product);
    }
    
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }
    
    public Optional<Product> getProductByCode(String productCode) {
        return productRepository.findByProductCode(productCode);
    }
    
    public Optional<Product> getProductByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode);
    }

    public Optional<ProductUnitService.BarcodeLookupResult> getProductOrUnitByBarcode(String barcode) {
        return new ProductUnitService().findProductOrUnitByBarcode(barcode);
    }
    
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
    
    public List<Product> getActiveProducts() {
        return productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .toList();
    }
    
    public List<Product> searchProductsByName(String name) {
        return productRepository.findByNameContaining(name);
    }
    
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }
    
    public List<Product> getLowStockProducts() {
        return productRepository.findLowStock();
    }

    public List<DataQualityAlert> getDataQualityAlerts() {
        List<DataQualityAlert> alerts = new ArrayList<>();
        for (Product product : productRepository.findAll()) {
            if (product == null || product.getId() == null || !Boolean.TRUE.equals(product.getIsActive())) {
                continue;
            }

            double quantityInStock = product.getQuantityInStock() != null ? product.getQuantityInStock() : 0.0;
            double batchTotal = productBatchService.getTotalBatchQuantity(product.getId());
            List<ProductBatch> allBatches = productBatchService.getAllBatches(product.getId());

            if (isBlank(product.getBarcode())) {
                alerts.add(new DataQualityAlert(
                        "MISSING_BARCODE",
                        "بدون باركود",
                        product,
                        "المنتج محفوظ بدون باركود.",
                        quantityInStock,
                        batchTotal,
                        allBatches.size(),
                        null
                ));
            }

            if (!hasPositiveValue(product.getUnitPrice()) && !hasPositiveValue(product.getUnitPriceUsd())) {
                alerts.add(new DataQualityAlert(
                        "MISSING_SALE_PRICE",
                        "بدون سعر بيع",
                        product,
                        "لا يوجد سعر بيع مفرد بالدينار أو الدولار.",
                        quantityInStock,
                        batchTotal,
                        allBatches.size(),
                        null
                ));
            }

            if (!hasPositiveValue(product.getCostPrice()) && !hasPositiveValue(product.getCostPriceUsd())) {
                alerts.add(new DataQualityAlert(
                        "MISSING_COST_PRICE",
                        "بدون سعر تكلفة",
                        product,
                        "لا يوجد سعر تكلفة بالدينار أو الدولار.",
                        quantityInStock,
                        batchTotal,
                        allBatches.size(),
                        null
                ));
            }

            if (quantityInStock > 0 && allBatches.isEmpty()) {
                alerts.add(new DataQualityAlert(
                        "NO_BATCH_RECORDS",
                        "بدون دفعات",
                        product,
                        "المنتج لديه مخزون ملخص لكن لا توجد له أي سجلات دفعات.",
                        quantityInStock,
                        batchTotal,
                        0,
                        null
                ));
            }

            if (quantityInStock < 0) {
                alerts.add(new DataQualityAlert(
                        "NEGATIVE_QUANTITY",
                        "كمية سالبة",
                        product,
                        "الكمية الحالية سالبة ويجب مراجعتها.",
                        quantityInStock,
                        batchTotal,
                        allBatches.size(),
                        null
                ));
            }

            double mismatch = quantityInStock - batchTotal;
            if (Math.abs(mismatch) > QTY_EPSILON) {
                alerts.add(new DataQualityAlert(
                        "QUANTITY_MISMATCH",
                        "عدم تطابق الكمية",
                        product,
                        "الكمية الظاهرة لا تساوي مجموع كميات الدفعات.",
                        quantityInStock,
                        batchTotal,
                        allBatches.size(),
                        mismatch
                ));
            }
        }
        return alerts;
    }
    
    public void deleteProduct(Long id) {
        accessControlService.requireProductEdit("PRODUCT_DELETE", "product", id);
        auditLogService.record("PRODUCT_DELETED", "product", id, "تم حذف المنتج من خلال خدمة المخزون");
        logger.info("Deleting product: {}", id);
        productRepository.deleteById(id);
    }
    
    public void deleteProduct(Product product) {
        accessControlService.requireProductEdit("PRODUCT_DELETE", "product", product != null ? product.getId() : null);
        auditLogService.record("PRODUCT_DELETED", "product", product != null ? product.getId() : null,
                product != null ? "تم حذف المنتج: " + product.getName() : "تم حذف منتج");
        logger.info("Deleting product: {}", product != null ? product.getId() : null);
        productRepository.delete(product);
    }
    
    public Product addStock(Long productId, Double quantity) {
        accessControlService.requireProductEdit("MANUAL_STOCK_ADD", "product", productId);
        if (quantity <= 0) {
            throw new IllegalArgumentException("الكمية يجب أن تكون أكبر من صفر");
        }
        
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.setQuantityInStock(product.getQuantityInStock() + quantity);
            auditLogService.record("MANUAL_STOCK_ADJUSTMENT", "product", product.getId(),
                    "إضافة مخزون يدوي بمقدار " + quantity + " للمنتج " + product.getName());
            logger.info("Added {} units to product: {}", quantity, product.getName());
            return productRepository.save(product);
        }
        throw new IllegalArgumentException("المنتج غير موجود");
    }
    
    public Product removeStock(Long productId, Double quantity) {
        accessControlService.requireProductEdit("MANUAL_STOCK_REMOVE", "product", productId);
        if (quantity <= 0) {
            throw new IllegalArgumentException("الكمية يجب أن تكون أكبر من صفر");
        }
        
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            
            double currentStock = product.getQuantityInStock() != null ? product.getQuantityInStock() : 0.0;
            if (currentStock < quantity) {
                throw new IllegalArgumentException("الكمية المطلوبة غير متوفرة في المخزون");
            }
            product.setQuantityInStock(currentStock - quantity);
            auditLogService.record("MANUAL_STOCK_ADJUSTMENT", "product", product.getId(),
                    "خصم مخزون يدوي بمقدار " + quantity + " من المنتج " + product.getName());
            logger.info("Removed {} units from product: {}", quantity, product.getName());
            return productRepository.save(product);
        }
        throw new IllegalArgumentException("المنتج غير موجود");
    }
    
    public boolean isStockAvailable(Long productId, Double requiredQuantity) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty() || requiredQuantity == null || requiredQuantity <= 0) {
            return false;
        }
        Product product = productOpt.get();
        double stock = product.getQuantityInStock() != null ? product.getQuantityInStock() : 0.0;
        return stock >= requiredQuantity && Boolean.TRUE.equals(product.getIsActive());
    }

    public boolean isStockAvailableForUnit(Long productId, Double soldQuantity, Double conversionFactor) {
        if (soldQuantity == null || soldQuantity <= 0) {
            return false;
        }
        double factor = conversionFactor != null && conversionFactor > 0 ? conversionFactor : 1.0;
        return isStockAvailable(productId, soldQuantity * factor);
    }
    
    private String generateProductCode() {
        return "PROD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    public void validateProduct(Product product) {
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("اسم المنتج مطلوب");
        }

        if (product.getBaseUnit() == null || product.getBaseUnit().trim().isEmpty()) {
            String fallbackUnit = product.getUnitOfMeasure();
            product.setBaseUnit(fallbackUnit != null && !fallbackUnit.trim().isEmpty() ? fallbackUnit : "قطعة");
        }
        if (product.getUnitOfMeasure() == null || product.getUnitOfMeasure().trim().isEmpty()) {
            product.setUnitOfMeasure(product.getBaseUnit());
        }
        
        validateCurrencyPricePair("سعر التكلفة", product.getCostPrice(), product.getCostPriceUsd(), false);
        validateCurrencyPricePair("السعر المفرد", product.getUnitPrice(), product.getUnitPriceUsd(), true);
        validateCurrencyPricePair("سعر الجملة", product.getWholesalePrice(), product.getWholesalePriceUsd(), false);
        validateCurrencyPricePair("السعر الخاص", product.getSpecialPrice(), product.getSpecialPriceUsd(), false);
        
        if (product.getQuantityInStock() == null || product.getQuantityInStock() < 0) {
            throw new IllegalArgumentException("الكمية في المخزون يجب أن تكون أكبر من أو تساوي صفر");
        }
        
        if (product.getMinimumStock() != null && product.getMinimumStock() < 0) {
            throw new IllegalArgumentException("الحد الأدنى للمخزون يجب أن يكون أكبر من أو يساوي صفر");
        }
        
        // Check for duplicate product code (for updates)
        if (product.getId() != null) {
            Optional<Product> existing = productRepository.findByProductCode(product.getProductCode());
            if (existing.isPresent() && !existing.get().getId().equals(product.getId())) {
                throw new IllegalArgumentException("كود المنتج مستخدم بالفعل");
            }
        }
    }

    private void validateCurrencyPricePair(String fieldName, Double iqdValue, Double usdValue, boolean required) {
        if ((iqdValue != null && iqdValue < 0) || (usdValue != null && usdValue < 0)) {
            throw new IllegalArgumentException(fieldName + " يجب أن يكون أكبر من صفر");
        }

        if (required && !hasPositiveValue(iqdValue) && !hasPositiveValue(usdValue)) {
            throw new IllegalArgumentException("يجب إدخال " + fieldName + " بالدينار أو بالدولار أو كليهما");
        }
    }

    private boolean hasPositiveValue(Double value) {
        return value != null && value > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
    
    public List<String> getAllCategories() {
        return categoryService.getActiveCategories().stream()
                .map(Category::getName)
                .distinct()
                .sorted()
                .toList();
    }
    
    public List<Product> getProductsNeedingRestock() {
        return productRepository.findAll().stream()
                .filter(product -> Boolean.TRUE.equals(product.getIsActive()))
                .filter(product -> {
                    double qty = product.getQuantityInStock() == null ? 0 : product.getQuantityInStock();
                    double min = product.getMinimumStock() == null ? 0 : product.getMinimumStock();
                    return qty <= min;
                })
                .toList();
    }
    
    public double getTotalInventoryValue() {
        return productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .mapToDouble(product -> {
                    double qty = product.getQuantityInStock() == null ? 0 : product.getQuantityInStock();
                    double cost = product.getCostPrice() == null ? 0.0 : product.getCostPrice();
                    return qty * cost;
                })
                .sum();
    }
    
    public double getTotalStockCount() {
        return productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .mapToDouble(p -> p.getQuantityInStock() == null ? 0 : p.getQuantityInStock())
                .sum();
    }

    public double getBatchBackedQuantity(Long productId) {
        return productBatchService.getTotalBatchQuantity(productId);
    }

    public Product syncProductQuantityFromBatches(Long productId) {
        return productBatchService.syncProductSummaryQuantity(productId);
    }

    public static class DataQualityAlert {
        private final String typeCode;
        private final String typeLabel;
        private final Product product;
        private final String message;
        private final double quantityInStock;
        private final double batchTotalQuantity;
        private final int batchRecordCount;
        private final Double mismatchQuantity;
        private final LocalDateTime createdAt;

        public DataQualityAlert(String typeCode,
                                String typeLabel,
                                Product product,
                                String message,
                                double quantityInStock,
                                double batchTotalQuantity,
                                int batchRecordCount,
                                Double mismatchQuantity) {
            this.typeCode = typeCode;
            this.typeLabel = typeLabel;
            this.product = product;
            this.message = message;
            this.quantityInStock = quantityInStock;
            this.batchTotalQuantity = batchTotalQuantity;
            this.batchRecordCount = batchRecordCount;
            this.mismatchQuantity = mismatchQuantity;
            this.createdAt = LocalDateTime.now();
        }

        public String getTypeCode() {
            return typeCode;
        }

        public String getTypeLabel() {
            return typeLabel;
        }

        public Product getProduct() {
            return product;
        }

        public String getMessage() {
            return message;
        }

        public double getQuantityInStock() {
            return quantityInStock;
        }

        public double getBatchTotalQuantity() {
            return batchTotalQuantity;
        }

        public int getBatchRecordCount() {
            return batchRecordCount;
        }

        public Double getMismatchQuantity() {
            return mismatchQuantity;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
}
