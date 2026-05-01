package com.pharmax.database;

import com.pharmax.model.Category;
import com.pharmax.model.Customer;
import com.pharmax.model.Product;
import com.pharmax.model.ProductBatch;
import com.pharmax.model.ProductUnit;
import com.pharmax.model.Sale;
import com.pharmax.model.SaleItemBatch;
import com.pharmax.model.SaleItem;
import com.pharmax.model.Receipt;
import com.pharmax.model.SaleReturn;
import com.pharmax.model.ReturnItem;
import com.pharmax.model.ReturnItemBatch;
import com.pharmax.model.InventoryMovement;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class Repository<T> {
    private static final Logger logger = LoggerFactory.getLogger(Repository.class);
    private final Class<T> entityClass;
    
    public Repository(Class<T> entityClass) {
        this.entityClass = entityClass;
    }
    
    public T save(T entity) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = DatabaseManager.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            session.saveOrUpdate(entity);
            transaction.commit();
            logger.debug("Entity saved: {}", entityClass.getSimpleName());
            return entity;
        } catch (Exception e) {
            if (transaction != null) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    logger.error("Failed to rollback transaction", rollbackEx);
                }
            }
            logger.error("Failed to save entity", e);
            throw new RuntimeException("Failed to save entity", e);
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception closeEx) {
                    logger.warn("Failed to close session", closeEx);
                }
            }
        }
    }
    
    public Optional<T> findById(Long id) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            T entity = session.get(entityClass, id);
            return Optional.ofNullable(entity);
        } catch (Exception e) {
            logger.error("Failed to find entity by id: {}", id, e);
            return Optional.empty();
        }
    }
    
    public List<T> findAll() {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<T> query = session.createQuery("FROM " + entityClass.getSimpleName(), entityClass);
            return query.list();
        } catch (Exception e) {
            logger.error("Failed to find all entities", e);
            throw new RuntimeException("Failed to find all entities", e);
        }
    }
    
    public void delete(T entity) {
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.delete(entity);
            transaction.commit();
            logger.debug("Entity deleted: {}", entityClass.getSimpleName());
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            logger.error("Failed to delete entity", e);
            throw new RuntimeException("Failed to delete entity", e);
        }
    }
    
    public void deleteById(Long id) {
        Optional<T> entity = findById(id);
        if (entity.isPresent()) {
            delete(entity.get());
        } else {
            logger.warn("Entity not found for deletion: {}", id);
        }
    }
    
    // Specific repository methods for different entities
    public static class CustomerRepository extends Repository<Customer> {
        public CustomerRepository() {
            super(Customer.class);
        }
        
        public String getNextCustomerCode() {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Long> query = session.createQuery("SELECT MAX(id) FROM Customer", Long.class);
                Long maxId = query.uniqueResult();
                long next = (maxId == null) ? 1L : maxId + 1L;
                return String.valueOf(next);
            } catch (Exception e) {
                logger.error("Failed to generate next customer code", e);
                throw new RuntimeException("Failed to generate next customer code", e);
            }
        }
        
        public Optional<Customer> findByCustomerCode(String customerCode) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Customer> query = session.createQuery(
                    "FROM Customer WHERE customerCode = :customerCode", Customer.class);
                query.setParameter("customerCode", customerCode);
                return query.uniqueResultOptional();
            } catch (Exception e) {
                logger.error("Failed to find customer by code: {}", customerCode, e);
                return Optional.empty();
            }
        }
        
        public List<Customer> findByNameContaining(String name) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Customer> query = session.createQuery(
                    "FROM Customer WHERE name LIKE :name", Customer.class);
                query.setParameter("name", "%" + name + "%");
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find customers by name: {}", name, e);
                throw new RuntimeException("Failed to find customers by name", e);
            }
        }
    }

    public static class SaleItemRepository extends Repository<SaleItem> {
        public SaleItemRepository() {
            super(SaleItem.class);
        }
    }
    
    public static class ProductRepository extends Repository<Product> {
        public ProductRepository() {
            super(Product.class);
        }
        
        public Optional<Product> findByProductCode(String productCode) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Product> query = session.createQuery(
                    "FROM Product WHERE productCode = :productCode", Product.class);
                query.setParameter("productCode", productCode);
                return query.uniqueResultOptional();
            } catch (Exception e) {
                logger.error("Failed to find product by code: {}", productCode, e);
                return Optional.empty();
            }
        }
        
        public List<Product> findByNameContaining(String name) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Product> query = session.createQuery(
                    "FROM Product WHERE name LIKE :name", Product.class);
                query.setParameter("name", "%" + name + "%");
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find products by name: {}", name, e);
                throw new RuntimeException("Failed to find products by name", e);
            }
        }
        
        public List<Product> findByCategory(String category) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Product> query = session.createQuery(
                    "FROM Product WHERE category = :category", Product.class);
                query.setParameter("category", category);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find products by category: {}", category, e);
                throw new RuntimeException("Failed to find products by category", e);
            }
        }
        
        public List<Product> findLowStock() {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Product> query = session.createQuery(
                    "FROM Product WHERE quantityInStock <= minimumStock AND isActive = true", Product.class);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find low stock products", e);
                throw new RuntimeException("Failed to find low stock products", e);
            }
        }
        
        public Optional<Product> findByBarcode(String barcode) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Product> query = session.createQuery(
                    "FROM Product WHERE barcode = :barcode AND isActive = true", Product.class);
                query.setParameter("barcode", barcode);
                return query.uniqueResultOptional();
            } catch (Exception e) {
                logger.error("Failed to find product by barcode: {}", barcode, e);
                return Optional.empty();
            }
        }
    }

    public static class ProductUnitRepository extends Repository<ProductUnit> {
        public ProductUnitRepository() {
            super(ProductUnit.class);
        }

        public List<ProductUnit> findByProductId(Long productId) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<ProductUnit> query = session.createQuery(
                    "FROM ProductUnit u WHERE u.product.id = :productId AND u.isActive = true " +
                    "ORDER BY u.isDefault DESC, u.conversionFactor ASC, u.unitName ASC",
                    ProductUnit.class
                );
                query.setParameter("productId", productId);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find product units by product id: {}", productId, e);
                throw new RuntimeException("Failed to find product units", e);
            }
        }

        public Optional<ProductUnit> findByBarcode(String barcode) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<ProductUnit> query = session.createQuery(
                    "SELECT u FROM ProductUnit u " +
                    "LEFT JOIN FETCH u.product p " +
                    "WHERE u.barcode = :barcode AND u.isActive = true AND p.isActive = true",
                    ProductUnit.class
                );
                query.setParameter("barcode", barcode);
                return query.uniqueResultOptional();
            } catch (Exception e) {
                logger.error("Failed to find product unit by barcode: {}", barcode, e);
                return Optional.empty();
            }
        }

        public void replaceForProduct(Product product, List<ProductUnit> units) {
            if (product == null || product.getId() == null) {
                throw new IllegalArgumentException("Product must be saved before assigning units");
            }

            Transaction transaction = null;
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                transaction = session.beginTransaction();
                session.createQuery("DELETE FROM ProductUnit u WHERE u.product.id = :productId")
                        .setParameter("productId", product.getId())
                        .executeUpdate();
                session.flush();

                Product managedProduct = session.get(Product.class, product.getId());
                for (ProductUnit unit : units) {
                    ProductUnit savedUnit = new ProductUnit();
                    savedUnit.setProduct(managedProduct);
                    savedUnit.setUnitName(unit.getUnitName());
                    savedUnit.setBarcode(unit.getBarcode());
                    savedUnit.setConversionFactor(unit.getConversionFactor());
                    savedUnit.setSalePrice(unit.getSalePrice());
                    savedUnit.setSalePriceUsd(unit.getSalePriceUsd());
                    savedUnit.setIsDefault(unit.getIsDefault());
                    savedUnit.setIsActive(unit.getIsActive());
                    session.save(savedUnit);
                }

                transaction.commit();
            } catch (Exception e) {
                if (transaction != null) {
                    try {
                        transaction.rollback();
                    } catch (Exception rollbackEx) {
                        logger.error("Failed to rollback product unit replacement", rollbackEx);
                    }
                }
                logger.error("Failed to replace product units for product: {}", product.getId(), e);
                throw new RuntimeException("Failed to replace product units", e);
            }
        }
    }

    public static class ProductBatchRepository extends Repository<ProductBatch> {
        public ProductBatchRepository() {
            super(ProductBatch.class);
        }

        public List<ProductBatch> findByProductId(Long productId) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<ProductBatch> query = session.createQuery(
                        "FROM ProductBatch b WHERE b.product.id = :productId ORDER BY " +
                                "CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, b.expiryDate ASC, b.createdAt ASC, b.id ASC",
                        ProductBatch.class);
                query.setParameter("productId", productId);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find batches by product id: {}", productId, e);
                throw new RuntimeException("Failed to find product batches", e);
            }
        }

        public List<ProductBatch> findSellableByProductId(Long productId, LocalDate today) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<ProductBatch> query = session.createQuery(
                        "FROM ProductBatch b WHERE b.product.id = :productId " +
                                "AND COALESCE(b.quantity, 0) > 0 " +
                                "AND b.status = :status " +
                                "AND (b.expiryDate IS NULL OR b.expiryDate >= :today) " +
                                "ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, b.expiryDate ASC, b.createdAt ASC, b.id ASC",
                        ProductBatch.class);
                query.setParameter("productId", productId);
                query.setParameter("status", "ACTIVE");
                query.setParameter("today", today);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find sellable batches by product id: {}", productId, e);
                throw new RuntimeException("Failed to find sellable product batches", e);
            }
        }

        public Optional<ProductBatch> findByProductIdAndBatchNumber(Long productId, String batchNumber) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<ProductBatch> query = session.createQuery(
                        "FROM ProductBatch b WHERE b.product.id = :productId AND b.batchNumber = :batchNumber",
                        ProductBatch.class);
                query.setParameter("productId", productId);
                query.setParameter("batchNumber", batchNumber);
                return query.uniqueResultOptional();
            } catch (Exception e) {
                logger.error("Failed to find batch by product and batch number: {} / {}", productId, batchNumber, e);
                return Optional.empty();
            }
        }

        public double getTotalQuantityByProductId(Long productId) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Double> query = session.createQuery(
                        "SELECT COALESCE(SUM(b.quantity), 0) FROM ProductBatch b " +
                                "WHERE b.product.id = :productId AND b.status = :status",
                        Double.class);
                query.setParameter("productId", productId);
                query.setParameter("status", "ACTIVE");
                Double result = query.uniqueResult();
                return result != null ? result : 0.0;
            } catch (Exception e) {
                logger.error("Failed to total batches by product id: {}", productId, e);
                throw new RuntimeException("Failed to total product batches", e);
            }
        }
    }

    public static class InventoryMovementRepository extends Repository<InventoryMovement> {
        public InventoryMovementRepository() {
            super(InventoryMovement.class);
        }

        public List<InventoryMovement> findByProductId(Long productId) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<InventoryMovement> query = session.createQuery(
                        "FROM InventoryMovement m WHERE m.product.id = :productId ORDER BY m.createdAt DESC, m.id DESC",
                        InventoryMovement.class);
                query.setParameter("productId", productId);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find movements by product id: {}", productId, e);
                throw new RuntimeException("Failed to find inventory movements", e);
            }
        }

        public List<InventoryMovement> findByBatchId(Long batchId) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<InventoryMovement> query = session.createQuery(
                        "FROM InventoryMovement m WHERE m.batch.id = :batchId ORDER BY m.createdAt DESC, m.id DESC",
                        InventoryMovement.class);
                query.setParameter("batchId", batchId);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find movements by batch id: {}", batchId, e);
                throw new RuntimeException("Failed to find inventory movements", e);
            }
        }

        public boolean existsByReference(String movementType, String referenceType, Long referenceId, Long referenceItemId, Long batchId) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Long> query = session.createQuery(
                        "SELECT COUNT(m.id) FROM InventoryMovement m " +
                                "WHERE m.movementType = :movementType " +
                                "AND m.referenceType = :referenceType " +
                                "AND m.referenceId = :referenceId " +
                                "AND m.referenceItemId = :referenceItemId " +
                                "AND ((:batchId IS NULL AND m.batch IS NULL) OR m.batch.id = :batchId)",
                        Long.class);
                query.setParameter("movementType", movementType);
                query.setParameter("referenceType", referenceType);
                query.setParameter("referenceId", referenceId);
                query.setParameter("referenceItemId", referenceItemId);
                query.setParameter("batchId", batchId);
                Long count = query.uniqueResult();
                return count != null && count > 0;
            } catch (Exception e) {
                logger.error("Failed to check movement existence for reference {} / {} / {}", referenceId, referenceItemId, batchId, e);
                throw new RuntimeException("Failed to check inventory movement duplication", e);
            }
        }
    }

    public static class SaleItemBatchRepository extends Repository<SaleItemBatch> {
        public SaleItemBatchRepository() {
            super(SaleItemBatch.class);
        }
    }
    
    public static class SaleRepository extends Repository<Sale> {
        public SaleRepository() {
            super(Sale.class);
        }

        public long getNextSaleCodeNumeric() {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Long> query = session.createQuery("SELECT COALESCE(MAX(id), 0) FROM Sale", Long.class);
                Long maxId = query.uniqueResult();
                long next = (maxId == null ? 0L : maxId) + 1L;
                return next;
            } catch (Exception e) {
                logger.error("Failed to generate next sale code", e);
                throw new RuntimeException("Failed to generate next sale code", e);
            }
        }

        public Optional<Sale> findByIdWithDetails(Long id) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Sale> query = session.createQuery(
                    "SELECT DISTINCT s FROM Sale s " +
                        "LEFT JOIN FETCH s.customer " +
                        "LEFT JOIN FETCH s.saleItems si " +
                        "LEFT JOIN FETCH si.product " +
                        "WHERE s.id = :id",
                    Sale.class
                );
                query.setParameter("id", id);
                return query.uniqueResultOptional();
            } catch (Exception e) {
                logger.error("Failed to find sale with details by id: {}", id, e);
                return Optional.empty();
            }
        }
        
        public Optional<Sale> findBySaleCode(String saleCode) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Sale> query = session.createQuery(
                    "FROM Sale WHERE saleCode = :saleCode", Sale.class);
                query.setParameter("saleCode", saleCode);
                return query.uniqueResultOptional();
            } catch (Exception e) {
                logger.error("Failed to find sale by code: {}", saleCode, e);
                return Optional.empty();
            }
        }
        
        public List<Sale> findByCustomerId(Long customerId) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Sale> query = session.createQuery(
                    "SELECT DISTINCT s FROM Sale s " +
                    "LEFT JOIN FETCH s.customer " +
                    "LEFT JOIN FETCH s.saleItems si " +
                    "LEFT JOIN FETCH si.product " +
                    "WHERE s.customer.id = :customerId ORDER BY s.saleDate DESC", Sale.class);
                query.setParameter("customerId", customerId);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find sales by customer: {}", customerId, e);
                throw new RuntimeException("Failed to find sales by customer", e);
            }
        }

        public List<Sale> findForAccountStatement(Long customerId,
                                                  String projectLocation,
                                                  LocalDateTime from,
                                                  LocalDateTime to,
                                                  boolean includeItems) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                StringBuilder hql = new StringBuilder();
                hql.append("SELECT DISTINCT s FROM Sale s ");
                hql.append("LEFT JOIN FETCH s.customer ");
                if (includeItems) {
                    hql.append("LEFT JOIN FETCH s.saleItems si ");
                    hql.append("LEFT JOIN FETCH si.product ");
                }
                hql.append("WHERE s.customer.id = :customerId ");

                if (projectLocation != null && !projectLocation.trim().isEmpty()) {
                    hql.append("AND s.projectLocation = :projectLocation ");
                }
                if (from != null) {
                    hql.append("AND s.saleDate >= :from ");
                }
                if (to != null) {
                    hql.append("AND s.saleDate <= :to ");
                }
                hql.append("ORDER BY s.saleDate DESC");

                Query<Sale> query = session.createQuery(hql.toString(), Sale.class);
                query.setParameter("customerId", customerId);
                if (projectLocation != null && !projectLocation.trim().isEmpty()) {
                    query.setParameter("projectLocation", projectLocation);
                }
                if (from != null) {
                    query.setParameter("from", from);
                }
                if (to != null) {
                    query.setParameter("to", to);
                }
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to load sales for account statement", e);
                throw new RuntimeException("Failed to load sales for account statement", e);
            }
        }

        public List<Sale> findAllWithCustomer() {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Sale> query = session.createQuery(
                    "SELECT DISTINCT s FROM Sale s " +
                        "LEFT JOIN FETCH s.customer " +
                        "ORDER BY s.saleDate DESC",
                    Sale.class
                );
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to load sales with customer data", e);
                throw new RuntimeException("Failed to load sales with customer data", e);
            }
        }
    }
    
    public static class ReceiptRepository extends Repository<Receipt> {
        public ReceiptRepository() {
            super(Receipt.class);
        }

        public void deleteByIdDirect(Long id) {
            Transaction transaction = null;
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                transaction = session.beginTransaction();
                int deleted = session.createQuery("DELETE FROM Receipt WHERE id = :id")
                        .setParameter("id", id)
                        .executeUpdate();
                if (deleted > 0) {
                    logger.debug("Entity deleted: {}", Receipt.class.getSimpleName());
                } else {
                    logger.warn("Entity not found for deletion: {}", id);
                }
                transaction.commit();
            } catch (Exception e) {
                if (transaction != null) {
                    try {
                        transaction.rollback();
                    } catch (Exception rollbackEx) {
                        logger.error("Failed to rollback transaction", rollbackEx);
                    }
                }
                logger.error("Failed to delete entity", e);
                throw new RuntimeException("Failed to delete entity", e);
            }
        }

        public void deleteByIdTransactional(Long id) {
            Transaction transaction = null;
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                transaction = session.beginTransaction();
                Receipt receipt = session.get(Receipt.class, id);
                if (receipt != null) {
                    session.delete(receipt);
                    logger.debug("Entity deleted: {}", Receipt.class.getSimpleName());
                } else {
                    logger.warn("Entity not found for deletion: {}", id);
                }
                transaction.commit();
            } catch (Exception e) {
                if (transaction != null) {
                    try {
                        transaction.rollback();
                    } catch (Exception rollbackEx) {
                        logger.error("Failed to rollback transaction", rollbackEx);
                    }
                }
                logger.error("Failed to delete entity", e);
                throw new RuntimeException("Failed to delete entity", e);
            }
        }

        public Optional<Receipt> findByIdWithDetails(Long id) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Receipt> query = session.createQuery(
                    "SELECT DISTINCT r FROM Receipt r " +
                        "LEFT JOIN FETCH r.sale s " +
                        "LEFT JOIN FETCH s.customer " +
                        "LEFT JOIN FETCH s.saleItems si " +
                        "LEFT JOIN FETCH si.product " +
                        "WHERE r.id = :id",
                    Receipt.class
                );
                query.setParameter("id", id);
                return query.uniqueResultOptional();
            } catch (Exception e) {
                logger.error("Failed to find receipt with details by id: {}", id, e);
                return Optional.empty();
            }
        }

        public List<Receipt> findAllWithDetails() {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Receipt> query = session.createQuery(
                    "SELECT DISTINCT r FROM Receipt r " +
                        "LEFT JOIN FETCH r.sale s " +
                        "LEFT JOIN FETCH s.customer " +
                        "ORDER BY r.receiptDate DESC",
                    Receipt.class
                );
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find all receipts with details", e);
                throw new RuntimeException("Failed to find all receipts with details", e);
            }
        }

        public List<Receipt> findBySaleId(Long saleId) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Receipt> query = session.createQuery(
                    "FROM Receipt WHERE sale.id = :saleId ORDER BY id ASC",
                    Receipt.class
                );
                query.setParameter("saleId", saleId);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find receipts by sale id: {}", saleId, e);
                throw new RuntimeException("Failed to find receipts by sale id", e);
            }
        }

        public List<Long> findSaleIdsWithMultipleReceipts() {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Long> query = session.createQuery(
                    "SELECT r.sale.id FROM Receipt r GROUP BY r.sale.id HAVING COUNT(r.id) > 1",
                    Long.class
                );
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find duplicate receipts", e);
                throw new RuntimeException("Failed to find duplicate receipts", e);
            }
        }

        public long getNextReceiptNumberNumeric() {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<String> query = session.createQuery(
                    "SELECT r.receiptNumber FROM Receipt r",
                    String.class
                );
                List<String> numbers = query.list();
                long max = 0L;
                if (numbers != null) {
                    for (String n : numbers) {
                        if (n == null) {
                            continue;
                        }
                        String trimmed = n.trim();
                        if (trimmed.isEmpty()) {
                            continue;
                        }
                        try {
                            long v = Long.parseLong(trimmed);
                            if (v > max) {
                                max = v;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                return max + 1L;
            } catch (Exception e) {
                logger.error("Failed to generate next receipt number", e);
                throw new RuntimeException("Failed to generate next receipt number", e);
            }
        }
        
        public Optional<Receipt> findByReceiptNumber(String receiptNumber) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Receipt> query = session.createQuery(
                    "FROM Receipt WHERE receiptNumber = :receiptNumber", Receipt.class);
                query.setParameter("receiptNumber", receiptNumber);
                return query.uniqueResultOptional();
            } catch (Exception e) {
                logger.error("Failed to find receipt by number: {}", receiptNumber, e);
                return Optional.empty();
            }
        }
    }
    
    public static class CategoryRepository extends Repository<Category> {
        public CategoryRepository() {
            super(Category.class);
        }
        
        public Optional<Category> findByName(String name) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Category> query = session.createQuery(
                    "FROM Category WHERE name = :name", Category.class);
                query.setParameter("name", name);
                return query.uniqueResultOptional();
            } catch (Exception e) {
                logger.error("Failed to find category by name: {}", name, e);
                return Optional.empty();
            }
        }
        
        public List<Category> findActiveCategories() {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<Category> query = session.createQuery(
                    "FROM Category WHERE isActive = true ORDER BY name", Category.class);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find active categories", e);
                throw new RuntimeException("Failed to find active categories", e);
            }
        }
    }

    public static class SaleReturnRepository extends Repository<SaleReturn> {
        public SaleReturnRepository() {
            super(SaleReturn.class);
        }

        public String generateReturnCode() {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<String> query = session.createQuery(
                    "SELECT r.returnCode FROM SaleReturn r", String.class);
                List<String> codes = query.list();
                long max = 0L;
                if (codes != null) {
                    for (String c : codes) {
                        if (c == null) continue;
                        String trimmed = c.trim();
                        if (trimmed.isEmpty()) continue;
                        // Support old RET-XXXXXX format
                        if (trimmed.startsWith("RET-")) {
                            trimmed = trimmed.substring(4);
                        }
                        try {
                            long v = Long.parseLong(trimmed);
                            if (v > max) max = v;
                        } catch (NumberFormatException ignored) {}
                    }
                }
                return String.valueOf(max + 1L);
            } catch (Exception e) {
                logger.error("Failed to generate return code", e);
                return String.valueOf(System.currentTimeMillis());
            }
        }

        public List<SaleReturn> findBySaleId(Long saleId) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<SaleReturn> query = session.createQuery(
                    "SELECT DISTINCT r FROM SaleReturn r " +
                    "LEFT JOIN FETCH r.customer " +
                    "LEFT JOIN FETCH r.sale " +
                    "LEFT JOIN FETCH r.returnItems ri " +
                    "LEFT JOIN FETCH ri.originalSaleItem " +
                    "LEFT JOIN FETCH ri.product " +
                    "WHERE r.sale.id = :saleId ORDER BY r.returnDate DESC", SaleReturn.class);
                query.setParameter("saleId", saleId);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find returns by sale id: {}", saleId, e);
                return List.of();
            }
        }

        public List<SaleReturn> findByCustomerId(Long customerId) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<SaleReturn> query = session.createQuery(
                    "SELECT DISTINCT r FROM SaleReturn r " +
                    "LEFT JOIN FETCH r.sale s " +
                    "LEFT JOIN FETCH r.customer " +
                    "LEFT JOIN FETCH r.returnItems ri " +
                    "LEFT JOIN FETCH ri.originalSaleItem " +
                    "LEFT JOIN FETCH ri.product " +
                    "WHERE r.customer.id = :customerId ORDER BY r.returnDate DESC", SaleReturn.class);
                query.setParameter("customerId", customerId);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find returns by customer id: {}", customerId, e);
                return List.of();
            }
        }

        public List<SaleReturn> findAllWithDetails() {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                Query<SaleReturn> query = session.createQuery(
                    "SELECT DISTINCT r FROM SaleReturn r " +
                    "LEFT JOIN FETCH r.sale s " +
                    "LEFT JOIN FETCH r.customer " +
                    "LEFT JOIN FETCH r.returnItems ri " +
                    "LEFT JOIN FETCH ri.originalSaleItem " +
                    "LEFT JOIN FETCH ri.product " +
                    "ORDER BY r.returnDate DESC", SaleReturn.class);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find all returns with details", e);
                return List.of();
            }
        }

        public Double getTotalReturnsByCustomerAndProject(Long customerId, String projectLocation) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                String hql = "SELECT COALESCE(SUM(r.totalReturnAmount), 0) FROM SaleReturn r " +
                             "WHERE r.customer.id = :customerId AND r.returnStatus = 'COMPLETED'";
                if (projectLocation != null && !projectLocation.trim().isEmpty()) {
                    hql += " AND r.sale.projectLocation = :projectLocation";
                }
                Query<Double> query = session.createQuery(hql, Double.class);
                query.setParameter("customerId", customerId);
                if (projectLocation != null && !projectLocation.trim().isEmpty()) {
                    query.setParameter("projectLocation", projectLocation);
                }
                return query.uniqueResult();
            } catch (Exception e) {
                logger.error("Failed to get total returns", e);
                return 0.0;
            }
        }

        public List<SaleReturn> findForAccountStatement(Long customerId,
                                                        String projectLocation,
                                                        LocalDateTime from,
                                                        LocalDateTime to) {
            try (Session session = DatabaseManager.getSessionFactory().openSession()) {
                StringBuilder hql = new StringBuilder();
                hql.append("SELECT DISTINCT r FROM SaleReturn r ");
                hql.append("LEFT JOIN FETCH r.customer ");
                hql.append("LEFT JOIN FETCH r.sale ");
                hql.append("LEFT JOIN FETCH r.returnItems ri ");
                hql.append("LEFT JOIN FETCH ri.product ");
                hql.append("WHERE r.customer.id = :customerId ");
                hql.append("AND r.returnStatus = 'COMPLETED' ");

                if (projectLocation != null && !projectLocation.trim().isEmpty()) {
                    hql.append("AND r.sale.projectLocation = :projectLocation ");
                }
                if (from != null) {
                    hql.append("AND r.returnDate >= :from ");
                }
                if (to != null) {
                    hql.append("AND r.returnDate <= :to ");
                }
                hql.append("ORDER BY r.returnDate DESC");

                Query<SaleReturn> query = session.createQuery(hql.toString(), SaleReturn.class);
                query.setParameter("customerId", customerId);
                if (projectLocation != null && !projectLocation.trim().isEmpty()) {
                    query.setParameter("projectLocation", projectLocation);
                }
                if (from != null) {
                    query.setParameter("from", from);
                }
                if (to != null) {
                    query.setParameter("to", to);
                }
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to load returns for account statement", e);
                return List.of();
            }
        }
    }

    public static class ReturnItemRepository extends Repository<ReturnItem> {
        public ReturnItemRepository() {
            super(ReturnItem.class);
        }
    }

    public static class ReturnItemBatchRepository extends Repository<ReturnItemBatch> {
        public ReturnItemBatchRepository() {
            super(ReturnItemBatch.class);
        }
    }

    public static class CustomerPaymentRepository extends Repository<com.pharmax.model.CustomerPayment> {
        public CustomerPaymentRepository() {
            super(com.pharmax.model.CustomerPayment.class);
        }

        public List<com.pharmax.model.CustomerPayment> findByCustomerId(Long customerId) {
            Session session = null;
            try {
                session = DatabaseManager.getSessionFactory().openSession();
                Query<com.pharmax.model.CustomerPayment> query = session.createQuery(
                    "FROM CustomerPayment cp WHERE cp.customer.id = :customerId ORDER BY cp.paymentDate DESC",
                    com.pharmax.model.CustomerPayment.class
                );
                query.setParameter("customerId", customerId);
                return query.list();
            } catch (Exception e) {
                logger.error("Failed to find payments by customer", e);
                throw new RuntimeException("Failed to find payments", e);
            } finally {
                if (session != null) {
                    session.close();
                }
            }
        }

        public String generatePaymentCode() {
            Session session = null;
            try {
                session = DatabaseManager.getSessionFactory().openSession();
                Query<Long> query = session.createQuery(
                    "SELECT COALESCE(MAX(CAST(SUBSTRING(cp.paymentCode, 4) AS long)), 0) + 1 FROM CustomerPayment cp WHERE cp.paymentCode LIKE 'PAY%'",
                    Long.class
                );
                Long nextNumber = query.uniqueResult();
                return String.format("PAY%06d", nextNumber);
            } catch (Exception e) {
                logger.error("Failed to generate payment code", e);
                return "PAY" + System.currentTimeMillis();
            } finally {
                if (session != null) {
                    session.close();
                }
            }
        }
    }
}
