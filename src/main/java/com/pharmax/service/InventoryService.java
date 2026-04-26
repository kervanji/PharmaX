package com.pharmax.service;

import com.pharmax.database.Repository.ProductRepository;
import com.pharmax.model.Category;
import com.pharmax.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InventoryService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    
    public InventoryService() {
        this.productRepository = new ProductRepository();
        this.categoryService = new CategoryService();
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
    
    public void deleteProduct(Long id) {
        logger.info("Deleting product: {}", id);
        productRepository.deleteById(id);
    }
    
    public void deleteProduct(Product product) {
        logger.info("Deleting product: {}", product.getId());
        productRepository.delete(product);
    }
    
    public Product addStock(Long productId, Double quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("الكمية يجب أن تكون أكبر من صفر");
        }
        
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.setQuantityInStock(product.getQuantityInStock() + quantity);
            logger.info("Added {} units to product: {}", quantity, product.getName());
            return productRepository.save(product);
        }
        throw new IllegalArgumentException("المنتج غير موجود");
    }
    
    public Product removeStock(Long productId, Double quantity) {
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
    
    private String generateProductCode() {
        return "PROD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    public void validateProduct(Product product) {
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("اسم المنتج مطلوب");
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
}
