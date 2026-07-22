package com.pharmax.service;

import com.pharmax.database.DatabaseManager;
import com.pharmax.database.Repository.SaleRepository;
import com.pharmax.database.Repository.CustomerRepository;
import com.pharmax.database.Repository.ProductRepository;
import com.pharmax.database.Repository.ProductBatchRepository;
import com.pharmax.database.Repository.SaleItemRepository;
import com.pharmax.model.*;
import com.pharmax.util.SessionManager;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SalesService {
    private static final Logger logger = LoggerFactory.getLogger(SalesService.class);
    private final SaleRepository saleRepository;
    @SuppressWarnings("unused") private final SaleItemRepository saleItemRepository;
    @SuppressWarnings("unused") private final CustomerRepository customerRepository;
    @SuppressWarnings("unused") private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final ProductBatchService productBatchService;
    private final InventoryMovementService inventoryMovementService;
    private final CashboxService cashboxService;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    
    public SalesService() {
        this.saleRepository = new SaleRepository();
        this.saleItemRepository = new SaleItemRepository();
        this.customerRepository = new CustomerRepository();
        this.productRepository = new ProductRepository();
        this.inventoryService = new InventoryService();
        this.customerService = new CustomerService();
        this.productBatchService = new ProductBatchService();
        this.inventoryMovementService = new InventoryMovementService();
        this.cashboxService = new CashboxService();
        this.accessControlService = new AccessControlService();
        this.auditLogService = new AuditLogService();
    }
    
    public Sale createSale(SaleRequest saleRequest) {
        logger.info("Creating new sale for customer: {}", saleRequest.getCustomerId());
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Customer customer = session.get(Customer.class, saleRequest.getCustomerId());
            if (customer == null) {
                throw new IllegalArgumentException("العميل غير موجود");
            }

            Sale sale = new Sale();
            sale.setSaleCode(generateSaleCode());
            sale.setCustomer(customer);
            sale.setPaymentMethod(saleRequest.getPaymentMethod());
            sale.setCurrency(saleRequest.getCurrency() != null ? saleRequest.getCurrency() : "دينار");
            sale.setNotes(saleRequest.getNotes());
            sale.setCreatedBy(saleRequest.getCreatedBy());

            List<PreparedSaleItem> preparedItems = buildSalePlan(session, saleRequest, sale.getCurrency());
            double totalAmount = preparedItems.stream().mapToDouble(p -> p.saleItem.getTotalPrice()).sum();

            transaction = session.beginTransaction();

            sale.setTotalAmount(totalAmount);
            sale.setDiscountAmount(saleRequest.getAdditionalDiscount() != null ? saleRequest.getAdditionalDiscount() : 0.0);
            sale.setTaxAmount(0.0);
            sale.setFinalAmount(totalAmount - sale.getDiscountAmount());

            double paidAmount = saleRequest.getPaidAmount() != null ? saleRequest.getPaidAmount() : 0.0;
            sale.setPaidAmount(paidAmount);
            sale.setPaymentStatus(paidAmount + 1e-9 >= sale.getFinalAmount() ? "PAID" : "PENDING");

            session.save(sale);
            session.flush();

            List<SaleItem> savedItems = new ArrayList<>();
            for (PreparedSaleItem prepared : preparedItems) {
                prepared.saleItem.setSale(sale);
                applySaleItemSnapshot(prepared.saleItem, prepared.allocations);
                session.save(prepared.saleItem);
                session.flush();

                for (BatchAllocationPlan allocation : prepared.allocations) {
                    double quantityBefore = allocation.batch.getQuantity();
                    double quantityAfter = quantityBefore - allocation.quantitySold;
                    if (quantityAfter < -1e-9) {
                        throw new IllegalArgumentException("الكمية المطلوبة غير متوفرة للمنتج: " + prepared.product.getName());
                    }

                    allocation.batch.setQuantity(quantityAfter);
                    allocation.batch.setStatus(quantityAfter > 0 ? "ACTIVE" : "DEPLETED");
                    allocation.batch.setUpdatedAt(LocalDateTime.now());
                    session.saveOrUpdate(allocation.batch);

                    SaleItemBatch saleItemBatch = new SaleItemBatch();
                    saleItemBatch.setSaleItem(prepared.saleItem);
                    saleItemBatch.setBatch(allocation.batch);
                    saleItemBatch.setQuantitySold(allocation.quantitySold);
                    saleItemBatch.setBatchNumberSnapshot(allocation.batch.getBatchNumber());
                    saleItemBatch.setExpirationDateSnapshot(allocation.batch.getExpiryDate() != null
                            ? allocation.batch.getExpiryDate().toString()
                            : null);
                    saleItemBatch.setUnitCostSnapshot(allocation.batch.getUnitCost());
                    saleItemBatch.setQuantityBefore(quantityBefore);
                    saleItemBatch.setQuantityAfter(quantityAfter);
                    session.save(saleItemBatch);

                    inventoryMovementService.recordMovement(
                            session,
                            prepared.product,
                            allocation.batch,
                            "sale",
                            "sale",
                            sale.getId(),
                            prepared.saleItem.getId(),
                            -allocation.quantitySold,
                            quantityBefore,
                            quantityAfter,
                            allocation.batch.getUnitCost(),
                            buildSaleMovementNote(sale, prepared.saleItem, allocation),
                            sale.getCreatedBy());
                }

                if (Boolean.TRUE.equals(prepared.product.getIsUnlimitedStock())) {
                    inventoryMovementService.recordMovement(
                            session,
                            prepared.product,
                            null,
                            "sale",
                            "sale",
                            sale.getId(),
                            prepared.saleItem.getId(),
                            -prepared.saleItem.getEffectiveBaseQuantity(),
                            null,
                            null,
                            prepared.saleItem.getUnitCostSnapshot(),
                            buildUnlimitedStockSaleMovementNote(sale, prepared.saleItem),
                            sale.getCreatedBy());
                }

                if (!Boolean.TRUE.equals(prepared.product.getIsUnlimitedStock())) {
                    productBatchService.syncProductSummaryQuantity(session, prepared.product.getId());
                }
                savedItems.add(prepared.saleItem);
            }

            updateCustomerBalanceInSession(customer, paidAmount - sale.getFinalAmount(), sale.getCurrency());
            sale.setSaleItems(savedItems);

            if ("CASH".equalsIgnoreCase(sale.getPaymentMethod()) && paidAmount > 0) {
                String customerName = customer != null && customer.getName() != null ? customer.getName() : "نقدي";
                cashboxService.recordEntry(
                        session,
                        sale.getSaleDate(),
                        "cash_sale",
                        "IN",
                        paidAmount,
                        sale.getCurrency(),
                        "sale",
                        sale.getId(),
                        0L,
                        customer,
                        null,
                        null,
                        "CASH",
                        "بيع نقدي فاتورة " + sale.getSaleCode() + " - العميل: " + customerName,
                        sale.getCreatedBy());
            }

            transaction.commit();
            logger.info("Sale created successfully: {}", sale.getSaleCode());
            return sale;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    logger.error("Failed to rollback sale transaction", rollbackEx);
                }
            }
            logger.error("Failed to create sale", e);
            throw new RuntimeException("فشل في إنشاء الفاتورة: " + e.getMessage(), e);
        }
    }
    
    public Optional<Sale> getSaleById(Long id) {
        return saleRepository.findById(id);
    }
    
    public Optional<Sale> getSaleByCode(String saleCode) {
        return saleRepository.findBySaleCode(saleCode);
    }
    
    public List<Sale> getAllSales() {
        new ReceiptService().ensureSingleReceiptPerSale();
        return saleRepository.findAllWithCustomer();
    }
    
    public List<Sale> getSalesByCustomerId(Long customerId) {
        return saleRepository.findByCustomerId(customerId);
    }

    public List<Sale> getSalesForAccountStatement(Long customerId,
                                                  LocalDateTime from,
                                                  LocalDateTime to,
                                                  boolean includeItems) {
        if (customerId == null) {
            throw new IllegalArgumentException("العميل غير موجود");
        }
        return saleRepository.findForAccountStatement(customerId, from, to, includeItems);
    }
    
    public void deleteSale(Long id) {
        accessControlService.requireInvoiceDeletionPrivilege("SALE_DELETE", "sale", id);
        Optional<Sale> saleOpt = saleRepository.findById(id);
        if (saleOpt.isPresent()) {
            Sale sale = saleOpt.get();
            
            // Restore inventory
            for (SaleItem item : sale.getSaleItems()) {
                if (item.getProduct() != null && Boolean.TRUE.equals(item.getProduct().getIsUnlimitedStock())) {
                    continue;
                }
                if (item.getBatchAllocations() != null && !item.getBatchAllocations().isEmpty()) {
                    for (SaleItemBatch allocation : item.getBatchAllocations()) {
                        ProductBatch batch = allocation.getBatch();
                        if (batch != null && batch.getId() != null) {
                            batch.setQuantity(batch.getQuantity() + allocation.getQuantitySold());
                            batch.setStatus("ACTIVE");
                            new ProductBatchRepository().save(batch);
                        }
                    }
                    inventoryService.syncProductQuantityFromBatches(item.getProduct().getId());
                } else {
                    inventoryService.addStock(item.getProduct().getId(), item.getEffectiveBaseQuantity());
                }
            }
            
            // Revert customer balance effect of this sale
            double paid = sale.getPaidAmount() != null ? sale.getPaidAmount() : 0.0;
            customerService.updateCustomerBalanceByCurrency(
                sale.getCustomer().getId(),
                sale.getFinalAmount() - paid,
                sale.getCurrency()
            );
            
            saleRepository.delete(sale);
            auditLogService.record("SALE_DELETED", "sale", id,
                    "تم حذف فاتورة البيع " + sale.getSaleCode());
            logger.info("Sale deleted: {}", id);
        }
    }
    
    public Sale updatePaymentStatus(Long saleId, String newStatus) {
        Optional<Sale> saleOpt = saleRepository.findById(saleId);
        if (saleOpt.isPresent()) {
            Sale sale = saleOpt.get();
            sale.setPaymentStatus(newStatus);
            double debtPaymentAmount = 0.0;
            
            // If payment is completed, update customer balance
            if ("PAID".equals(newStatus)) {
                double currentPaid = sale.getPaidAmount() != null ? sale.getPaidAmount() : 0.0;
                double remaining = sale.getFinalAmount() - currentPaid;
                if (Math.abs(remaining) > 1e-9) {
                    customerService.updateCustomerBalanceByCurrency(sale.getCustomer().getId(), remaining, sale.getCurrency());
                    debtPaymentAmount = remaining;
                }
                sale.setPaidAmount(sale.getFinalAmount());
            }
            
            Sale savedSale = saleRepository.save(sale);
            if ("PAID".equals(newStatus) && debtPaymentAmount > 1e-9) {
                recordSaleDebtPayment(savedSale, debtPaymentAmount);
            }
            return savedSale;
        }
        throw new IllegalArgumentException("البيع غير موجود");
    }

    private void recordSaleDebtPayment(Sale sale, double amount) {
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            Customer customer = sale.getCustomer() != null && sale.getCustomer().getId() != null
                    ? session.get(Customer.class, sale.getCustomer().getId())
                    : null;
            String customerName = customer != null && customer.getName() != null ? customer.getName() : "نقدي";
            String collector = SessionManager.getInstance().getCurrentDisplayName();
            String creditGiver = sale.getCreatedBy() != null ? sale.getCreatedBy() : "-";
            cashboxService.recordEntry(
                    session,
                    LocalDateTime.now(),
                    "sale_debt_payment",
                    "IN",
                    amount,
                    sale.getCurrency(),
                    "sale",
                    sale.getId(),
                    0L,
                    customer,
                    null,
                    customer,
                    "CASH",
                    "تحصيل دين فاتورة " + (sale.getSaleCode() != null ? sale.getSaleCode() : sale.getId())
                            + " من العميل: " + customerName,
                    collector,
                    creditGiver
            );
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            logger.error("Failed to record sale debt payment in cashbox", e);
            throw new RuntimeException("تم تحديث الفاتورة لكن فشل تسجيل حركة الصندوق: " + e.getMessage(), e);
        }
    }
    
    private String generateSaleCode() {
        return String.valueOf(saleRepository.getNextSaleCodeNumeric());
    }

    private double getProductPriceForCurrency(Product product, String currency) {
        Double price = "دولار".equals(currency) ? product.getUnitPriceUsd() : product.getUnitPrice();
        if (price == null || price <= 0) {
            throw new IllegalArgumentException("لا يوجد سعر صالح للمنتج: " + product.getName());
        }
        return price;
    }

    private List<PreparedSaleItem> buildSalePlan(Session session, SaleRequest saleRequest, String saleCurrency) {
        List<PreparedSaleItem> preparedItems = new ArrayList<>();
        Map<Long, List<ProductBatch>> availableBatchesByProduct = new HashMap<>();
        Map<Long, Double> remainingByBatchId = new HashMap<>();

        for (SaleItemRequest itemRequest : saleRequest.getItems()) {
            Product product = session.get(Product.class, itemRequest.getProductId());
            if (product == null) {
                throw new IllegalArgumentException("المنتج غير موجود: " + itemRequest.getProductId());
            }

            double conversionFactor = itemRequest.getConversionFactor() != null && itemRequest.getConversionFactor() > 0
                    ? itemRequest.getConversionFactor()
                    : 1.0;
            double baseQuantity = itemRequest.getBaseQuantity() != null && itemRequest.getBaseQuantity() > 0
                    ? itemRequest.getBaseQuantity()
                    : itemRequest.getQuantity() * conversionFactor;

            boolean unlimitedStock = Boolean.TRUE.equals(product.getIsUnlimitedStock());
            List<BatchAllocationPlan> allocations;
            if (unlimitedStock) {
                allocations = List.of();
            } else {
                List<ProductBatch> availableBatches = availableBatchesByProduct.computeIfAbsent(
                        product.getId(),
                        id -> productBatchService.getAvailableBatches(session, id, LocalDate.now()));
                allocations = allocateAcrossBatches(product, availableBatches, remainingByBatchId, baseQuantity);
            }
            if (!unlimitedStock && allocations.isEmpty()) {
                throw new IllegalArgumentException("الكمية غير متوفرة للمنتج: " + product.getName());
            }

            SaleItem saleItem = new SaleItem();
            saleItem.setProduct(product);
            saleItem.setQuantity(itemRequest.getQuantity());
            saleItem.setPriceType(itemRequest.getPriceType() != null ? itemRequest.getPriceType() : "مفرد");
            saleItem.setSoldUnit(itemRequest.getSoldUnit());
            saleItem.setConversionFactor(conversionFactor);
            saleItem.setBaseQuantity(baseQuantity);

            double unitPrice = itemRequest.getUnitPrice() != null
                    ? itemRequest.getUnitPrice()
                    : getProductPriceForCurrency(product, saleCurrency);
            saleItem.setUnitPrice(unitPrice);

            double discountPercent = itemRequest.getDiscountPercentage() != null ? itemRequest.getDiscountPercentage() : 0.0;
            saleItem.setDiscountPercentage(discountPercent);
            double itemTotal = unitPrice * itemRequest.getQuantity();
            double discountAmount = itemTotal * (discountPercent / 100.0);
            saleItem.setDiscountAmount(discountAmount);
            saleItem.setTotalPrice(itemTotal - discountAmount);

            preparedItems.add(new PreparedSaleItem(product, saleItem, allocations));
        }

        return preparedItems;
    }

    private List<BatchAllocationPlan> allocateAcrossBatches(Product product,
                                                            List<ProductBatch> availableBatches,
                                                            Map<Long, Double> remainingByBatchId,
                                                            double requiredQuantity) {
        double remainingRequired = requiredQuantity;
        List<BatchAllocationPlan> allocations = new ArrayList<>();

        for (ProductBatch batch : availableBatches) {
            if (remainingRequired <= 1e-9) {
                break;
            }

            double remainingInBatch = remainingByBatchId.computeIfAbsent(
                    batch.getId(),
                    id -> batch.getQuantity() != null ? batch.getQuantity() : 0.0);
            if (remainingInBatch <= 1e-9) {
                continue;
            }

            double quantityToSell = Math.min(remainingRequired, remainingInBatch);
            allocations.add(new BatchAllocationPlan(batch, quantityToSell));
            remainingByBatchId.put(batch.getId(), remainingInBatch - quantityToSell);
            remainingRequired -= quantityToSell;
        }

        if (remainingRequired > 1e-9) {
            throw new IllegalArgumentException("الكمية غير متوفرة للمنتج: " + product.getName());
        }

        return allocations;
    }

    private void applySaleItemSnapshot(SaleItem saleItem, List<BatchAllocationPlan> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            saleItem.setBatch(null);
            saleItem.setBatchNumberSnapshot(null);
            saleItem.setExpirationDateSnapshot(null);
            saleItem.setUnitCostSnapshot(saleItem.getProduct() != null ? saleItem.getProduct().getCostPrice() : null);
            return;
        }
        if (allocations.size() == 1) {
            ProductBatch batch = allocations.get(0).batch;
            saleItem.setBatch(batch);
            saleItem.setBatchNumberSnapshot(batch.getBatchNumber());
            saleItem.setExpirationDateSnapshot(batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : null);
            saleItem.setUnitCostSnapshot(batch.getUnitCost());
            return;
        }

        saleItem.setBatch(null);
        saleItem.setBatchNumberSnapshot("MULTI");
        saleItem.setExpirationDateSnapshot(null);
        saleItem.setUnitCostSnapshot(null);
    }

    private String buildSaleMovementNote(Sale sale, SaleItem saleItem, BatchAllocationPlan allocation) {
        String productName = saleItem.getProduct() != null ? saleItem.getProduct().getName() : "-";
        String batchNumber = allocation.batch != null ? allocation.batch.getBatchNumber() : "-";
        return "Sale " + sale.getSaleCode() + " - " + productName + " - batch " + batchNumber;
    }

    private String buildUnlimitedStockSaleMovementNote(Sale sale, SaleItem saleItem) {
        String productName = saleItem.getProduct() != null ? saleItem.getProduct().getName() : "-";
        return "Sale " + sale.getSaleCode() + " - " + productName + " - unlimited stock";
    }

    private void updateCustomerBalanceInSession(Customer customer, Double amount, String currency) {
        double safeAmount = amount != null ? amount : 0.0;
        if ("دولار".equals(currency) || "USD".equalsIgnoreCase(currency)) {
            customer.setBalanceUsd(customer.getBalanceUsd() + safeAmount);
        } else {
            customer.setBalanceIqd(customer.getBalanceIqd() + safeAmount);
            customer.setCurrentBalance(customer.getCurrentBalance() + safeAmount);
        }
    }

    private static class PreparedSaleItem {
        private final Product product;
        private final SaleItem saleItem;
        private final List<BatchAllocationPlan> allocations;

        private PreparedSaleItem(Product product, SaleItem saleItem, List<BatchAllocationPlan> allocations) {
            this.product = product;
            this.saleItem = saleItem;
            this.allocations = allocations;
        }
    }

    private static class BatchAllocationPlan {
        private final ProductBatch batch;
        private final double quantitySold;

        private BatchAllocationPlan(ProductBatch batch, double quantitySold) {
            this.batch = batch;
            this.quantitySold = quantitySold;
        }
    }
    
    
    public double getTotalSalesAmount() {
        return saleRepository.findAll().stream()
                .mapToDouble(Sale::getFinalAmount)
                .sum();
    }
    
    public double getSalesAmountByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return saleRepository.findAll().stream()
                .filter(sale -> !sale.getSaleDate().isBefore(startDate) && 
                               !sale.getSaleDate().isAfter(endDate))
                .mapToDouble(Sale::getFinalAmount)
                .sum();
    }
    
    public List<Sale> getPendingPayments() {
        return saleRepository.findAll().stream()
                .filter(sale -> "PENDING".equals(sale.getPaymentStatus()))
                .toList();
    }
    
    // Request DTOs
    public static class SaleRequest {
        private Long customerId;
        private String paymentMethod;
        private String currency;
        private String notes;
        private String createdBy;
        private Double additionalDiscount;
        private Double paidAmount;
        private List<SaleItemRequest> items;
        
        // Getters and Setters
        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }
        
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        
        public Double getAdditionalDiscount() { return additionalDiscount; }
        public void setAdditionalDiscount(Double additionalDiscount) { this.additionalDiscount = additionalDiscount; }

        public Double getPaidAmount() { return paidAmount; }
        public void setPaidAmount(Double paidAmount) { this.paidAmount = paidAmount; }
        
        public List<SaleItemRequest> getItems() { return items; }
        public void setItems(List<SaleItemRequest> items) { this.items = items; }
    }
    
    public static class SaleItemRequest {
        private Long productId;
        private Double quantity;
        private Double unitPrice;
        private Double discountPercentage;
        private String priceType;
        private String soldUnit;
        private Double conversionFactor;
        private Double baseQuantity;
        
        // Getters and Setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        
        public Double getQuantity() { return quantity; }
        public void setQuantity(Double quantity) { this.quantity = quantity; }
        
        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
        
        public Double getDiscountPercentage() { return discountPercentage; }
        public void setDiscountPercentage(Double discountPercentage) { this.discountPercentage = discountPercentage; }
        
        public String getPriceType() { return priceType; }
        public void setPriceType(String priceType) { this.priceType = priceType; }

        public String getSoldUnit() { return soldUnit; }
        public void setSoldUnit(String soldUnit) { this.soldUnit = soldUnit; }

        public Double getConversionFactor() { return conversionFactor; }
        public void setConversionFactor(Double conversionFactor) { this.conversionFactor = conversionFactor; }

        public Double getBaseQuantity() { return baseQuantity; }
        public void setBaseQuantity(Double baseQuantity) { this.baseQuantity = baseQuantity; }
    }
}
