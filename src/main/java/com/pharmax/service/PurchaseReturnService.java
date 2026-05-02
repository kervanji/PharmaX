package com.pharmax.service;

import com.pharmax.database.DatabaseManager;
import com.pharmax.model.Customer;
import com.pharmax.model.Product;
import com.pharmax.model.ProductBatch;
import com.pharmax.model.PurchaseReturn;
import com.pharmax.model.PurchaseReturnItem;
import com.pharmax.model.Voucher;
import com.pharmax.model.VoucherItem;
import com.pharmax.model.VoucherType;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PurchaseReturnService {
    private static final Logger logger = LoggerFactory.getLogger(PurchaseReturnService.class);
    private static final double QTY_EPSILON = 1e-6;

    private final ProductBatchService productBatchService;
    private final InventoryMovementService inventoryMovementService;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;

    public PurchaseReturnService() {
        this.productBatchService = new ProductBatchService();
        this.inventoryMovementService = new InventoryMovementService();
        this.accessControlService = new AccessControlService();
        this.auditLogService = new AuditLogService();
    }

    public List<Voucher> getPurchaseVouchersBySupplier(Long supplierId) {
        if (supplierId == null) {
            return List.of();
        }
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<Voucher> query = session.createQuery(
                    "SELECT DISTINCT v FROM Voucher v " +
                            "LEFT JOIN FETCH v.customer " +
                            "LEFT JOIN FETCH v.items i " +
                            "LEFT JOIN FETCH i.product " +
                            "LEFT JOIN FETCH i.batch " +
                            "WHERE v.voucherType = :type " +
                            "AND v.isCancelled = false " +
                            "AND v.customer.id = :customerId " +
                            "ORDER BY v.voucherDate DESC, v.id DESC",
                    Voucher.class);
            query.setParameter("type", VoucherType.PURCHASE);
            query.setParameter("customerId", supplierId);
            return query.list();
        }
    }

    public List<PurchaseReturnableItem> getReturnableItemsForVoucher(Long voucherId) {
        if (voucherId == null) {
            return List.of();
        }
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Voucher voucher = session.get(Voucher.class, voucherId);
            if (voucher == null || voucher.getVoucherType() != VoucherType.PURCHASE || Boolean.TRUE.equals(voucher.getIsCancelled())) {
                return List.of();
            }

            Map<Long, Double> previousReturned = getPreviouslyReturnedQuantitiesByVoucher(session, voucherId);
            List<PurchaseReturnableItem> rows = new ArrayList<>();
            if (voucher.getItems() == null) {
                return rows;
            }

            for (VoucherItem item : voucher.getItems()) {
                if (item == null || !Boolean.TRUE.equals(item.getAddToInventory()) || item.getProduct() == null || item.getProduct().getId() == null) {
                    continue;
                }
                ProductBatch batch = item.getBatch();
                double purchasedQuantity = safe(item.getQuantity());
                double previouslyReturnedQuantity = previousReturned.getOrDefault(item.getId(), 0.0);
                double availableToReturn = Math.max(0.0, purchasedQuantity - previouslyReturnedQuantity);
                double currentBatchQuantity = batch != null ? safe(batch.getQuantity()) : 0.0;

                rows.add(new PurchaseReturnableItem(
                        item,
                        item.getProduct(),
                        batch,
                        purchasedQuantity,
                        previouslyReturnedQuantity,
                        availableToReturn,
                        currentBatchQuantity
                ));
            }
            return rows;
        }
    }

    public PurchaseReturn createPurchaseReturn(Long customerId,
                                               Long sourceVoucherId,
                                               Long sourceVoucherItemId,
                                               Double returnQuantity,
                                               String reason,
                                               String notes,
                                               String actor) {
        accessControlService.requireAdmin("PURCHASE_RETURN_CREATE", "purchase_return", null);
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Customer customer = session.get(Customer.class, customerId);
            Voucher voucher = session.get(Voucher.class, sourceVoucherId);
            VoucherItem sourceItem = session.get(VoucherItem.class, sourceVoucherItemId);

            validatePurchaseReturnRequest(customer, voucher, sourceItem, returnQuantity);

            ProductBatch batch = sourceItem.getBatch();
            if (batch == null || batch.getId() == null) {
                throw new IllegalArgumentException("سطر المشتريات القديم لا يحتوي على دفعة مرتبطة، لذلك لا يمكن إرجاعه بأمان");
            }

            Map<Long, Double> previousReturned = getPreviouslyReturnedQuantitiesByVoucher(session, voucher.getId());
            double previouslyReturnedQuantity = previousReturned.getOrDefault(sourceItem.getId(), 0.0);
            double purchasedQuantity = safe(sourceItem.getQuantity());
            double requestedQuantity = safe(returnQuantity);
            double remainingReturnable = purchasedQuantity - previouslyReturnedQuantity;

            if (requestedQuantity <= 0) {
                throw new IllegalArgumentException("كمية المرتجع يجب أن تكون أكبر من صفر");
            }
            if (requestedQuantity - remainingReturnable > QTY_EPSILON) {
                throw new IllegalArgumentException("كمية المرتجع تتجاوز الكمية المشتراة المتبقية للإرجاع");
            }

            ProductBatch managedBatch = session.get(ProductBatch.class, batch.getId());
            if (managedBatch == null) {
                throw new IllegalArgumentException("دفعة الشراء غير موجودة");
            }
            double quantityBefore = safe(managedBatch.getQuantity());
            if (requestedQuantity - quantityBefore > QTY_EPSILON) {
                throw new IllegalArgumentException("كمية المرتجع تتجاوز الكمية الحالية المتوفرة في الدفعة");
            }

            Product product = session.get(Product.class, sourceItem.getProduct().getId());
            if (product == null) {
                throw new IllegalArgumentException("المنتج غير موجود");
            }

            transaction = session.beginTransaction();

            PurchaseReturn purchaseReturn = new PurchaseReturn();
            purchaseReturn.setCustomer(customer);
            purchaseReturn.setSourceVoucher(voucher);
            purchaseReturn.setReturnDate(LocalDateTime.now());
            purchaseReturn.setCurrency(voucher.getCurrency());
            purchaseReturn.setNotes(notes);
            purchaseReturn.setCreatedBy(actor);
            session.save(purchaseReturn);
            session.flush();

            PurchaseReturnItem returnItem = new PurchaseReturnItem();
            returnItem.setPurchaseReturn(purchaseReturn);
            returnItem.setSourceVoucherItem(sourceItem);
            returnItem.setProduct(product);
            returnItem.setBatch(managedBatch);
            returnItem.setBatchNumberSnapshot(managedBatch.getBatchNumber());
            returnItem.setExpirationDateSnapshot(managedBatch.getExpiryDate() != null ? managedBatch.getExpiryDate().toString() : sourceItem.getExpirationDate());
            returnItem.setQuantity(requestedQuantity);
            returnItem.setUnitCost(sourceItem.getUnitPrice());
            returnItem.setLineTotal(requestedQuantity * safe(sourceItem.getUnitPrice()));
            returnItem.setReason(reason);
            session.save(returnItem);
            session.flush();

            ProductBatch updatedBatch = productBatchService.createOrUpdateBatch(
                    session,
                    product,
                    managedBatch.getBatchNumber(),
                    managedBatch.getExpiryDate(),
                    -requestedQuantity,
                    managedBatch.getUnitCost(),
                    managedBatch.getCurrency(),
                    managedBatch.getSupplierCustomer(),
                    managedBatch.getIsOpeningBatch()
            );
            double quantityAfter = safe(updatedBatch.getQuantity());

            inventoryMovementService.recordMovement(
                    session,
                    product,
                    updatedBatch,
                    "purchase_return",
                    "purchase_return",
                    purchaseReturn.getId(),
                    returnItem.getId(),
                    -requestedQuantity,
                    quantityBefore,
                    quantityAfter,
                    sourceItem.getUnitPrice(),
                    buildMovementNote(voucher, reason),
                    actor
            );

            productBatchService.syncProductSummaryQuantity(session, product.getId());
            applyPurchaseReturnBalanceInSession(session, customer, returnItem.getLineTotal(), voucher.getCurrency());

            purchaseReturn.setTotalAmount(returnItem.getLineTotal());
            purchaseReturn.addItem(returnItem);
            session.saveOrUpdate(purchaseReturn);
            auditLogService.record(session, "PURCHASE_RETURN_CREATED", "purchase_return", purchaseReturn.getId(),
                    "تم إنشاء مرتجع شراء على الفاتورة " + voucher.getVoucherNumber() + " بمبلغ " + purchaseReturn.getTotalAmount());

            transaction.commit();
            logger.info("Purchase return {} saved for voucher {}", purchaseReturn.getId(), voucher.getVoucherNumber());
            return purchaseReturn;
        } catch (Exception e) {
            if (transaction != null) {
                try {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                } catch (Exception rollbackEx) {
                    logger.warn("Failed to rollback purchase return transaction cleanly", rollbackEx);
                }
            }
            logger.error("Failed to create purchase return", e);
            throw new RuntimeException("فشل في حفظ مرتجع الشراء: " + e.getMessage(), e);
        }
    }

    private void validatePurchaseReturnRequest(Customer customer, Voucher voucher, VoucherItem sourceItem, Double returnQuantity) {
        if (customer == null || customer.getId() == null) {
            throw new IllegalArgumentException("المورد/الحساب غير موجود");
        }
        if (voucher == null || voucher.getId() == null) {
            throw new IllegalArgumentException("فاتورة الشراء غير موجودة");
        }
        if (voucher.getVoucherType() != VoucherType.PURCHASE) {
            throw new IllegalArgumentException("السند المحدد ليس فاتورة مشتريات");
        }
        if (Boolean.TRUE.equals(voucher.getIsCancelled())) {
            throw new IllegalArgumentException("لا يمكن إنشاء مرتجع شراء لفاتورة مشتريات ملغاة");
        }
        if (voucher.getCustomer() == null || !voucher.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("الفاتورة المختارة لا تخص المورد/الحساب المحدد");
        }
        if (sourceItem == null || sourceItem.getId() == null) {
            throw new IllegalArgumentException("سطر المشتريات غير موجود");
        }
        if (sourceItem.getVoucher() == null || !sourceItem.getVoucher().getId().equals(voucher.getId())) {
            throw new IllegalArgumentException("سطر المشتريات لا ينتمي إلى الفاتورة المحددة");
        }
        if (sourceItem.getProduct() == null || sourceItem.getProduct().getId() == null) {
            throw new IllegalArgumentException("سطر المشتريات لا يحتوي على منتج صالح");
        }
        if (!Boolean.TRUE.equals(sourceItem.getAddToInventory())) {
            throw new IllegalArgumentException("هذا السطر لا يضيف للمخزون، لذلك لا يمكن إرجاعه كمخزون");
        }
        if (returnQuantity == null || returnQuantity <= 0) {
            throw new IllegalArgumentException("كمية المرتجع يجب أن تكون أكبر من صفر");
        }
    }

    private Map<Long, Double> getPreviouslyReturnedQuantitiesByVoucher(Session session, Long voucherId) {
        Query<Object[]> query = session.createQuery(
                "SELECT pri.sourceVoucherItem.id, COALESCE(SUM(pri.quantity), 0) " +
                        "FROM PurchaseReturnItem pri " +
                        "WHERE pri.purchaseReturn.sourceVoucher.id = :voucherId " +
                        "GROUP BY pri.sourceVoucherItem.id",
                Object[].class);
        query.setParameter("voucherId", voucherId);
        Map<Long, Double> quantities = new LinkedHashMap<>();
        for (Object[] row : query.list()) {
            quantities.put((Long) row[0], row[1] != null ? ((Number) row[1]).doubleValue() : 0.0);
        }
        return quantities;
    }

    private void applyPurchaseReturnBalanceInSession(Session session, Customer customer, Double amount, String currency) {
        double value = safe(amount);
        boolean isUsd = "دولار".equals(currency) || "USD".equalsIgnoreCase(currency);

        // الشراء يخصم من رصيد المورد، لذلك مرتجع الشراء يعيد نفس المبلغ إلى رصيده.
        if (isUsd) {
            customer.setBalanceUsd(customer.getBalanceUsd() + value);
        } else {
            customer.setBalanceIqd(customer.getBalanceIqd() + value);
            customer.setCurrentBalance(customer.getCurrentBalance() + value);
        }
        session.saveOrUpdate(customer);
    }

    private String buildMovementNote(Voucher voucher, String reason) {
        String voucherNumber = voucher != null ? voucher.getVoucherNumber() : "-";
        String normalizedReason = reason != null && !reason.trim().isEmpty() ? reason.trim() : "مرتجع شراء";
        return "مرتجع شراء من الفاتورة " + voucherNumber + " - " + normalizedReason;
    }

    private double safe(Double value) {
        return value != null ? value : 0.0;
    }

    public Optional<PurchaseReturn> getPurchaseReturnById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            return Optional.ofNullable(session.get(PurchaseReturn.class, id));
        }
    }

    public static class PurchaseReturnableItem {
        private final VoucherItem sourceVoucherItem;
        private final Product product;
        private final ProductBatch batch;
        private final double purchasedQuantity;
        private final double previouslyReturnedQuantity;
        private final double availableToReturnQuantity;
        private final double currentBatchQuantity;

        public PurchaseReturnableItem(VoucherItem sourceVoucherItem,
                                      Product product,
                                      ProductBatch batch,
                                      double purchasedQuantity,
                                      double previouslyReturnedQuantity,
                                      double availableToReturnQuantity,
                                      double currentBatchQuantity) {
            this.sourceVoucherItem = sourceVoucherItem;
            this.product = product;
            this.batch = batch;
            this.purchasedQuantity = purchasedQuantity;
            this.previouslyReturnedQuantity = previouslyReturnedQuantity;
            this.availableToReturnQuantity = availableToReturnQuantity;
            this.currentBatchQuantity = currentBatchQuantity;
        }

        public VoucherItem getSourceVoucherItem() {
            return sourceVoucherItem;
        }

        public Product getProduct() {
            return product;
        }

        public ProductBatch getBatch() {
            return batch;
        }

        public double getPurchasedQuantity() {
            return purchasedQuantity;
        }

        public double getPreviouslyReturnedQuantity() {
            return previouslyReturnedQuantity;
        }

        public double getAvailableToReturnQuantity() {
            return availableToReturnQuantity;
        }

        public double getCurrentBatchQuantity() {
            return currentBatchQuantity;
        }
    }
}
