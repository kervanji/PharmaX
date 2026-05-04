package com.pharmax.service;

import com.pharmax.database.Repository.ProductBatchRepository;
import com.pharmax.database.Repository.ProductRepository;
import com.pharmax.model.Customer;
import com.pharmax.model.Product;
import com.pharmax.model.ProductBatch;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ProductBatchService {
    private static final Logger logger = LoggerFactory.getLogger(ProductBatchService.class);
    private final ProductBatchRepository productBatchRepository;
    private final ProductRepository productRepository;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;

    public ProductBatchService() {
        this.productBatchRepository = new ProductBatchRepository();
        this.productRepository = new ProductRepository();
        this.accessControlService = new AccessControlService();
        this.auditLogService = new AuditLogService();
    }

    public List<ProductBatch> getAvailableBatches(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("المنتج غير موجود");
        }
        return filterSellableBatches(productBatchRepository.findByProductId(productId), LocalDate.now());
    }

    public List<ProductBatch> getAllBatches(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("المنتج غير موجود");
        }
        return productBatchRepository.findByProductId(productId);
    }

    public List<ProductBatch> getExpiredBatches(LocalDate today) {
        LocalDate effectiveToday = today != null ? today : LocalDate.now();
        return filterExpiryCandidates(productBatchRepository.findAll()).stream()
                .filter(batch -> batch.getExpiryDate().isBefore(effectiveToday))
                .sorted(expiryComparator())
                .toList();
    }

    public List<ProductBatch> getExpiringBatchesWithinDays(int days, LocalDate today) {
        if (days <= 0) {
            return List.of();
        }
        LocalDate effectiveToday = today != null ? today : LocalDate.now();
        LocalDate limitDate = effectiveToday.plusDays(days);
        return filterExpiryCandidates(productBatchRepository.findAll()).stream()
                .filter(batch -> !batch.getExpiryDate().isBefore(effectiveToday))
                .filter(batch -> !batch.getExpiryDate().isAfter(limitDate))
                .sorted(expiryComparator())
                .toList();
    }

    public List<ProductBatch> getExpiryAlertBatches(LocalDate today) {
        LocalDate effectiveToday = today != null ? today : LocalDate.now();
        LocalDate maxWindow = effectiveToday.plusDays(90);
        return filterExpiryCandidates(productBatchRepository.findAll()).stream()
                .filter(batch -> batch.getExpiryDate().isBefore(effectiveToday) || !batch.getExpiryDate().isAfter(maxWindow))
                .sorted(expiryComparator())
                .toList();
    }

    public double getTotalBatchQuantity(Long productId) {
        if (productId == null) {
            return 0.0;
        }
        return productBatchRepository.getTotalQuantityByProductId(productId);
    }

    public Optional<ProductBatch> findByProductIdAndBatchNumber(Long productId, String batchNumber) {
        if (productId == null || batchNumber == null || batchNumber.trim().isEmpty()) {
            return Optional.empty();
        }
        return productBatchRepository.findByProductIdAndBatchNumber(productId, batchNumber.trim());
    }

    public ProductBatch createOrUpdateBatch(Product product,
                                            String batchNumber,
                                            LocalDate expiryDate,
                                            Double quantityDelta,
                                            Double unitCost,
                                            String currency,
                                            Customer supplierCustomer,
                                            Boolean isOpeningBatch) {
        return createOrUpdateBatch(product, batchNumber, expiryDate, null,
                quantityDelta, unitCost, currency, supplierCustomer, isOpeningBatch);
    }

    public ProductBatch createOrUpdateBatch(Product product,
                                            String batchNumber,
                                            LocalDate expiryDate,
                                            LocalDate productionDate,
                                            Double quantityDelta,
                                            Double unitCost,
                                            String currency,
                                            Customer supplierCustomer,
                                            Boolean isOpeningBatch) {
        accessControlService.requireProductEdit("BATCH_CORRECTION", "product_batch", null);
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("المنتج يجب أن يكون محفوظاً قبل إدارة الدفعات");
        }
        if (batchNumber == null || batchNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("رقم التشغيلة مطلوب");
        }
        if (quantityDelta == null || Math.abs(quantityDelta) < 1e-9) {
            throw new IllegalArgumentException("كمية الدفعة يجب ألا تكون صفراً");
        }

        Optional<ProductBatch> existingOpt = productBatchRepository.findByProductIdAndBatchNumber(
                product.getId(), batchNumber.trim());
        ProductBatch batch = existingOpt.orElseGet(ProductBatch::new);

        if (batch.getId() == null) {
            batch.setProduct(product);
            batch.setBatchNumber(batchNumber.trim());
            batch.setOriginalQuantity(0.0);
            batch.setQuantity(0.0);
            batch.setCreatedAt(LocalDateTime.now());
        }

        if (expiryDate != null) {
            batch.setExpiryDate(expiryDate);
        }
        if (productionDate != null) {
            batch.setProductionDate(productionDate);
        }
        if (unitCost != null) {
            batch.setUnitCost(unitCost);
        }
        if (currency != null && !currency.trim().isEmpty()) {
            batch.setCurrency(currency.trim());
        }
        if (supplierCustomer != null) {
            batch.setSupplierCustomer(supplierCustomer);
        }
        if (isOpeningBatch != null) {
            batch.setIsOpeningBatch(isOpeningBatch);
        }

        batch.setQuantity(batch.getQuantity() + quantityDelta);
        batch.setOriginalQuantity(Math.max(batch.getOriginalQuantity(), batch.getQuantity()));
        batch.setStatus(batch.getQuantity() > 0 ? "ACTIVE" : "DEPLETED");
        batch.setUpdatedAt(LocalDateTime.now());

        ProductBatch saved = productBatchRepository.save(batch);
        syncProductSummaryQuantity(product.getId());
        auditLogService.record("BATCH_CORRECTION", "product_batch", saved.getId(),
                "تعديل يدوي على دفعة " + saved.getBatchNumber() + " للمنتج " + product.getName());
        logger.info("Batch {} synced for product {}", saved.getBatchNumber(), product.getName());
        return saved;
    }

    public ProductBatch updateBatchExpiry(ProductBatch batch, LocalDate expiryDate) {
        accessControlService.requireProductEdit("BATCH_EXPIRY_UPDATE", "product_batch", batch != null ? batch.getId() : null);
        if (batch == null || batch.getId() == null) {
            throw new IllegalArgumentException("الدفعة غير موجودة");
        }
        batch.setExpiryDate(expiryDate);
        batch.setUpdatedAt(LocalDateTime.now());
        ProductBatch saved = productBatchRepository.save(batch);
        if (saved.getProduct() != null && saved.getProduct().getId() != null) {
            syncProductSummaryQuantity(saved.getProduct().getId());
        }
        auditLogService.record("BATCH_EXPIRY_UPDATED", "product_batch", saved.getId(),
                "تم تحديث صلاحية الدفعة " + saved.getBatchNumber() + " إلى " + (expiryDate != null ? expiryDate : "-"));
        return saved;
    }

    public ProductBatch createOrUpdateBatch(Session session,
                                            Product product,
                                            String batchNumber,
                                            LocalDate expiryDate,
                                            Double quantityDelta,
                                            Double unitCost,
                                            String currency,
                                            Customer supplierCustomer,
                                            Boolean isOpeningBatch) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("المنتج يجب أن يكون محفوظاً قبل إدارة الدفعات");
        }
        if (batchNumber == null || batchNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("رقم التشغيلة مطلوب");
        }
        if (quantityDelta == null || Math.abs(quantityDelta) < 1e-9) {
            throw new IllegalArgumentException("كمية الدفعة يجب ألا تكون صفراً");
        }

        ProductBatch batch = findByProductIdAndBatchNumber(session, product.getId(), batchNumber.trim()).orElse(null);
        if (batch == null) {
            batch = new ProductBatch();
            batch.setProduct(product);
            batch.setBatchNumber(batchNumber.trim());
            batch.setOriginalQuantity(0.0);
            batch.setQuantity(0.0);
            batch.setCreatedAt(LocalDateTime.now());
        } else if (expiryDate != null && batch.getExpiryDate() != null && !expiryDate.equals(batch.getExpiryDate())) {
            throw new IllegalArgumentException("رقم التشغيلة مستخدم مسبقاً بتاريخ صلاحية مختلف للمنتج: " + product.getName());
        }

        if (expiryDate != null) {
            batch.setExpiryDate(expiryDate);
        }
        if (unitCost != null) {
            batch.setUnitCost(unitCost);
        }
        if (currency != null && !currency.trim().isEmpty()) {
            batch.setCurrency(currency.trim());
        }
        if (supplierCustomer != null) {
            batch.setSupplierCustomer(supplierCustomer);
        }
        if (isOpeningBatch != null) {
            batch.setIsOpeningBatch(isOpeningBatch);
        }

        double newQuantity = batch.getQuantity() + quantityDelta;
        if (newQuantity < -1e-9) {
            throw new IllegalArgumentException("كمية الدفعة لا يمكن أن تصبح سالبة");
        }
        batch.setQuantity(newQuantity);
        batch.setOriginalQuantity(Math.max(batch.getOriginalQuantity(), newQuantity));
        batch.setStatus(newQuantity > 0 ? "ACTIVE" : "DEPLETED");
        batch.setUpdatedAt(LocalDateTime.now());

        session.saveOrUpdate(batch);
        return batch;
    }

    public Product syncProductSummaryQuantity(Session session, Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("المنتج غير موجود");
        }

        Product product = session.get(Product.class, productId);
        if (product == null) {
            throw new IllegalArgumentException("المنتج غير موجود");
        }
        product.setQuantityInStock(getTotalBatchQuantity(session, productId));
        product.setUpdatedAt(LocalDateTime.now());
        session.saveOrUpdate(product);
        return product;
    }

    public double getTotalBatchQuantity(Session session, Long productId) {
        Query<Double> query = session.createQuery(
                "SELECT COALESCE(SUM(b.quantity), 0) FROM ProductBatch b " +
                        "WHERE b.product.id = :productId AND b.status = :status",
                Double.class);
        query.setParameter("productId", productId);
        query.setParameter("status", "ACTIVE");
        Double result = query.uniqueResult();
        return result != null ? result : 0.0;
    }

    public List<ProductBatch> getAvailableBatches(Session session, Long productId, LocalDate today) {
        Query<ProductBatch> query = session.createQuery(
                "FROM ProductBatch b WHERE b.product.id = :productId " +
                        "AND COALESCE(b.quantity, 0) > 0 " +
                        "AND b.status = :status " +
                        "ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, b.expiryDate ASC, b.createdAt ASC, b.id ASC",
                ProductBatch.class);
        query.setParameter("productId", productId);
        query.setParameter("status", "ACTIVE");
        return filterSellableBatches(query.list(), today != null ? today : LocalDate.now());
    }

    private List<ProductBatch> filterSellableBatches(List<ProductBatch> batches, LocalDate today) {
        LocalDate effectiveToday = today != null ? today : LocalDate.now();
        return batches.stream()
                .filter(batch -> batch != null)
                .filter(batch -> "ACTIVE".equalsIgnoreCase(batch.getStatus()))
                .filter(batch -> batch.getQuantity() != null && batch.getQuantity() > 0)
                .filter(batch -> batch.getExpiryDate() == null || !batch.getExpiryDate().isBefore(effectiveToday))
                .toList();
    }

    private List<ProductBatch> filterExpiryCandidates(List<ProductBatch> batches) {
        List<ProductBatch> filtered = new ArrayList<>();
        for (ProductBatch batch : batches) {
            if (batch == null) {
                continue;
            }
            if (!"ACTIVE".equalsIgnoreCase(batch.getStatus())) {
                continue;
            }
            if (batch.getQuantity() == null || batch.getQuantity() <= 0) {
                continue;
            }
            if (batch.getExpiryDate() == null) {
                continue;
            }
            filtered.add(batch);
        }
        return filtered;
    }

    private Comparator<ProductBatch> expiryComparator() {
        return Comparator
                .comparing(ProductBatch::getExpiryDate)
                .thenComparing(batch -> batch.getCreatedAt() != null ? batch.getCreatedAt() : LocalDateTime.MIN)
                .thenComparing(batch -> batch.getId() != null ? batch.getId() : Long.MAX_VALUE);
    }

    public Optional<ProductBatch> findByProductIdAndBatchNumber(Session session, Long productId, String batchNumber) {
        Query<ProductBatch> query = session.createQuery(
                "FROM ProductBatch b WHERE b.product.id = :productId AND b.batchNumber = :batchNumber",
                ProductBatch.class);
        query.setParameter("productId", productId);
        query.setParameter("batchNumber", batchNumber);
        return query.uniqueResultOptional();
    }

    public Product syncProductSummaryQuantity(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("المنتج غير موجود");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("المنتج غير موجود"));
        product.setQuantityInStock(getTotalBatchQuantity(productId));
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }
}
