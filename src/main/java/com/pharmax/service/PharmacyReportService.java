package com.pharmax.service;

import com.pharmax.database.DatabaseManager;
import com.pharmax.model.Customer;
import com.pharmax.model.DailyClosing;
import com.pharmax.model.InventoryMovement;
import com.pharmax.model.Product;
import com.pharmax.model.ProductBatch;
import com.pharmax.model.SaleItem;
import com.pharmax.model.Voucher;
import com.pharmax.model.VoucherItem;
import com.pharmax.model.VoucherType;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PharmacyReportService {
    private final ProductBatchService productBatchService = new ProductBatchService();
    private final CashboxService cashboxService = new CashboxService();

    public List<StockByBatchRow> getStockByBatchRows(String searchTerm, String expiryFilter) {
        LocalDate today = LocalDate.now();
        String search = normalize(searchTerm);
        String filter = expiryFilter != null ? expiryFilter : "ALL";

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<ProductBatch> query = session.createQuery(
                    "FROM ProductBatch b " +
                            "WHERE COALESCE(b.quantity, 0) > 0 " +
                            "ORDER BY b.product.name ASC, " +
                            "CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, " +
                            "b.expiryDate ASC, b.batchNumber ASC",
                    ProductBatch.class);
            List<ProductBatch> batches = query.list().stream()
                    .filter(batch -> matchesExpiryFilter(batch, filter, today))
                    .filter(batch -> matchesBatchSearch(batch, search))
                    .toList();

            Map<Long, String> sourceReferenceByBatch = resolveSourceReferences(session, batches);
            List<StockByBatchRow> rows = new ArrayList<>();
            for (ProductBatch batch : batches) {
                rows.add(new StockByBatchRow(
                        batch.getProduct().getName(),
                        batch.getProduct().getProductCode(),
                        batch.getProduct().getBarcode(),
                        batch.getBatchNumber(),
                        batch.getExpiryDate(),
                        batch.getQuantity(),
                        batch.getUnitCost(),
                        batch.getProduct().getUnitPrice(),
                        batch.getStatus(),
                        sourceReferenceByBatch.getOrDefault(batch.getId(), batch.getIsOpeningBatch() ? "رصيد افتتاحي" : "-"),
                        batch.getCreatedAt()
                ));
            }
            return rows;
        }
    }

    public List<ExpiryReportRow> getExpiryRows(String filter) {
        LocalDate today = LocalDate.now();
        String effectiveFilter = filter != null ? filter : "ALL";
        List<ProductBatch> batches;

        if ("EXPIRED".equalsIgnoreCase(effectiveFilter)) {
            batches = productBatchService.getExpiredBatches(today);
        } else if ("30".equals(effectiveFilter)) {
            batches = productBatchService.getExpiringBatchesWithinDays(30, today);
        } else if ("60".equals(effectiveFilter)) {
            batches = productBatchService.getExpiringBatchesWithinDays(60, today);
        } else if ("90".equals(effectiveFilter)) {
            batches = productBatchService.getExpiringBatchesWithinDays(90, today);
        } else {
            batches = productBatchService.getExpiryAlertBatches(today);
        }

        return batches.stream()
                .map(batch -> new ExpiryReportRow(
                        batch.getProduct().getName(),
                        batch.getProduct().getProductCode(),
                        batch.getProduct().getBarcode(),
                        batch.getBatchNumber(),
                        batch.getExpiryDate(),
                        batch.getExpiryDate() != null ? ChronoUnit.DAYS.between(today, batch.getExpiryDate()) : null,
                        batch.getQuantity(),
                        classifyExpiry(batch.getExpiryDate(), today)))
                .toList();
    }

    public List<MovementReportRow> getMovementRows(LocalDate fromDate,
                                                   LocalDate toDate,
                                                   String searchTerm,
                                                   String movementType) {
        LocalDateTime start = fromDate != null ? fromDate.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = toDate != null ? toDate.atTime(23, 59, 59) : LocalDate.now().atTime(23, 59, 59);
        String search = normalize(searchTerm);
        String effectiveType = movementType != null ? movementType : "ALL";

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<InventoryMovement> query = session.createQuery(
                    "FROM InventoryMovement m " +
                            "WHERE m.createdAt BETWEEN :start AND :end " +
                            "ORDER BY m.createdAt DESC, m.id DESC",
                    InventoryMovement.class);
            query.setParameter("start", start);
            query.setParameter("end", end);
            return query.list().stream()
                    .filter(movement -> "ALL".equalsIgnoreCase(effectiveType)
                            || effectiveType.equalsIgnoreCase(movement.getMovementType()))
                    .filter(movement -> matchesMovementSearch(movement, search))
                    .map(movement -> new MovementReportRow(
                            movement.getCreatedAt(),
                            movement.getProduct() != null ? movement.getProduct().getName() : "-",
                            movement.getProduct() != null ? movement.getProduct().getProductCode() : "-",
                            movement.getBatch() != null ? movement.getBatch().getBatchNumber() : "-",
                            movement.getMovementType(),
                            movement.getQuantityBefore(),
                            movement.getQuantityDelta(),
                            movement.getQuantityAfter(),
                            movement.getReferenceType(),
                            movement.getReferenceId(),
                            movement.getNote()))
                    .toList();
        }
    }

    public List<PurchaseReportRow> getPurchaseRows(LocalDate fromDate,
                                                   LocalDate toDate,
                                                   Long supplierId,
                                                   String searchTerm) {
        LocalDateTime start = fromDate != null ? fromDate.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = toDate != null ? toDate.atTime(23, 59, 59) : LocalDate.now().atTime(23, 59, 59);
        String search = normalize(searchTerm);

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<VoucherItem> query = session.createQuery(
                    "SELECT vi FROM VoucherItem vi " +
                            "JOIN FETCH vi.voucher v " +
                            "LEFT JOIN FETCH vi.product p " +
                            "LEFT JOIN FETCH v.customer c " +
                            "WHERE v.voucherType = :voucherType " +
                            "AND COALESCE(v.isCancelled, false) = false " +
                            "AND v.voucherDate BETWEEN :start AND :end " +
                            "ORDER BY v.voucherDate DESC, v.id DESC, vi.id ASC",
                    VoucherItem.class);
            query.setParameter("voucherType", VoucherType.PURCHASE);
            query.setParameter("start", start);
            query.setParameter("end", end);

            return query.list().stream()
                    .filter(item -> supplierId == null
                            || (item.getVoucher().getCustomer() != null
                            && supplierId.equals(item.getVoucher().getCustomer().getId())))
                    .filter(item -> matchesPurchaseSearch(item, search))
                    .map(item -> new PurchaseReportRow(
                            item.getVoucher().getId(),
                            item.getVoucher().getVoucherNumber(),
                            item.getVoucher().getVoucherDate(),
                            item.getVoucher().getCustomer() != null ? item.getVoucher().getCustomer().getName() : "-",
                            item.getProductName(),
                            item.getQuantity(),
                            item.getTotalPrice(),
                            item.getBatchNumber(),
                            item.getParsedExpirationDate()))
                    .toList();
        }
    }

    public List<Customer> getPurchaseSuppliers() {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<Customer> query = session.createQuery(
                    "FROM Customer c " +
                            "WHERE COALESCE(c.accountType, :defaultType) IN (:supplierTypes) " +
                            "ORDER BY c.name ASC",
                    Customer.class);
            query.setParameter("defaultType", Customer.TYPE_CUSTOMER);
            query.setParameterList("supplierTypes", List.of(Customer.TYPE_SUPPLIER, Customer.TYPE_BOTH));
            return query.list();
        }
    }

    public ProfitReportStatus evaluateProfitReportReliability() {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            List<SaleItem> saleItems = session.createQuery(
                    "FROM SaleItem si " +
                            "LEFT JOIN FETCH si.batchAllocations ba " +
                            "LEFT JOIN FETCH ba.batch " +
                            "ORDER BY si.id ASC",
                    SaleItem.class).list();

            long missingSnapshots = saleItems.stream()
                    .filter(item -> !hasReliableCostSnapshot(item))
                    .count();

            if (saleItems.isEmpty()) {
                return new ProfitReportStatus(false, "تقرير الربح مؤجل حالياً لأن قاعدة البيانات لا تحتوي بعد على بيانات مبيعات كافية.");
            }
            if (missingSnapshots > 0) {
                return new ProfitReportStatus(
                        false,
                        "تقرير الربح مؤجل لأن بعض بنود المبيعات لا تحتوي على لقطات تكلفة موثوقة لجميع السجلات التاريخية."
                );
            }
            return new ProfitReportStatus(true, "يمكن إنشاء تقرير الربح لأن جميع بنود البيع الحالية تحتوي على لقطات تكلفة.");
        }
    }

    public List<CashboxReportRow> getCashboxRows(LocalDate fromDate, LocalDate toDate) {
        LocalDate start = fromDate != null ? fromDate : LocalDate.now().minusDays(30);
        LocalDate end = toDate != null ? toDate : LocalDate.now();
        if (end.isBefore(start)) {
            return List.of();
        }

        List<CashboxReportRow> rows = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            CashboxService.CashTotals totals = cashboxService.calculateTotals(cursor);
            Optional<DailyClosing> closingOpt = cashboxService.getClosingByDate(cursor);
            String status = closingOpt.map(DailyClosing::getStatus).orElse("OPEN");
            Double actualCash = closingOpt.map(DailyClosing::getActualCash).orElse(null);
            Double difference = closingOpt.map(DailyClosing::getDifferenceAmount).orElse(null);
            rows.add(new CashboxReportRow(
                    cursor,
                    totals.openingCash(),
                    totals.totalIn(),
                    totals.totalOut(),
                    totals.expectedCash(),
                    actualCash,
                    difference,
                    status
            ));
            cursor = cursor.plusDays(1);
        }
        return rows;
    }

    public List<SalesVelocityRow> getBestSellingRows(LocalDate fromDate, LocalDate toDate, String searchTerm) {
        LocalDateTime start = fromDate != null ? fromDate.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = toDate != null ? toDate.atTime(23, 59, 59) : LocalDate.now().atTime(23, 59, 59);
        String search = normalize(searchTerm);

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            List<SaleItem> items = session.createQuery(
                    "SELECT si FROM SaleItem si " +
                            "JOIN FETCH si.sale s " +
                            "JOIN FETCH si.product p " +
                            "WHERE s.saleDate BETWEEN :start AND :end",
                    SaleItem.class)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .list();

            Map<Long, SalesVelocityAccumulator> byProduct = new LinkedHashMap<>();
            for (SaleItem item : items) {
                Product product = item.getProduct();
                if (product == null || product.getId() == null || !matchesProductSearch(product, search)) {
                    continue;
                }
                SalesVelocityAccumulator accumulator = byProduct.computeIfAbsent(product.getId(),
                        id -> new SalesVelocityAccumulator(product));
                accumulator.addSale(
                        item.getEffectiveBaseQuantity(),
                        item.getTotalPrice() != null ? item.getTotalPrice() : 0.0,
                        item.getSale() != null ? item.getSale().getSaleDate() : null
                );
            }

            return byProduct.values().stream()
                    .map(SalesVelocityAccumulator::toRow)
                    .sorted(Comparator.comparing(SalesVelocityRow::quantitySold, Comparator.nullsLast(Double::compareTo)).reversed()
                            .thenComparing(Comparator.comparing(SalesVelocityRow::revenue, Comparator.nullsLast(Double::compareTo)).reversed())
                            .thenComparing(SalesVelocityRow::productName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    public List<SalesVelocityRow> getSlowMovingRows(LocalDate fromDate, LocalDate toDate, String searchTerm) {
        LocalDateTime start = fromDate != null ? fromDate.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = toDate != null ? toDate.atTime(23, 59, 59) : LocalDate.now().atTime(23, 59, 59);
        String search = normalize(searchTerm);

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            List<Product> products = session.createQuery(
                    "FROM Product p WHERE p.isActive = true ORDER BY p.name ASC",
                    Product.class).list();
            Map<Long, SalesVelocityAccumulator> byProduct = products.stream()
                    .filter(product -> product.getId() != null)
                    .filter(product -> matchesProductSearch(product, search))
                    .collect(Collectors.toMap(
                            Product::getId,
                            SalesVelocityAccumulator::new,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));

            if (byProduct.isEmpty()) {
                return List.of();
            }

            List<SaleItem> items = session.createQuery(
                    "SELECT si FROM SaleItem si " +
                            "JOIN FETCH si.sale s " +
                            "JOIN FETCH si.product p " +
                            "WHERE s.saleDate BETWEEN :start AND :end",
                    SaleItem.class)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .list();

            for (SaleItem item : items) {
                Product product = item.getProduct();
                if (product == null || product.getId() == null) {
                    continue;
                }
                SalesVelocityAccumulator accumulator = byProduct.get(product.getId());
                if (accumulator != null) {
                    accumulator.addSale(
                            item.getEffectiveBaseQuantity(),
                            item.getTotalPrice() != null ? item.getTotalPrice() : 0.0,
                            item.getSale() != null ? item.getSale().getSaleDate() : null
                    );
                }
            }

            return byProduct.values().stream()
                    .map(SalesVelocityAccumulator::toRow)
                    .sorted(Comparator.comparing(SalesVelocityRow::quantitySold, Comparator.nullsLast(Double::compareTo))
                            .thenComparing(row -> row.lastSaleDate() != null ? row.lastSaleDate() : LocalDateTime.MIN)
                            .thenComparing(SalesVelocityRow::productName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    private boolean hasReliableCostSnapshot(SaleItem item) {
        if (item == null) {
            return false;
        }
        if (item.getBatchAllocations() != null && !item.getBatchAllocations().isEmpty()) {
            return item.getBatchAllocations().stream()
                    .allMatch(allocation -> allocation.getUnitCostSnapshot() != null);
        }
        return item.getUnitCostSnapshot() != null;
    }

    private Map<Long, String> resolveSourceReferences(Session session, List<ProductBatch> batches) {
        Set<Long> batchIds = batches.stream()
                .map(ProductBatch::getId)
                .collect(Collectors.toSet());
        if (batchIds.isEmpty()) {
            return Map.of();
        }

        Query<InventoryMovement> movementQuery = session.createQuery(
                "FROM InventoryMovement m " +
                        "LEFT JOIN FETCH m.batch b " +
                        "WHERE m.batch.id IN :batchIds " +
                        "ORDER BY m.createdAt ASC, m.id ASC",
                InventoryMovement.class);
        movementQuery.setParameter("batchIds", batchIds);

        Map<Long, String> sourceByBatch = new HashMap<>();
        for (InventoryMovement movement : movementQuery.list()) {
            if (movement.getBatch() == null || movement.getBatch().getId() == null) {
                continue;
            }
            sourceByBatch.putIfAbsent(
                    movement.getBatch().getId(),
                    formatSourceReference(movement.getReferenceType(), movement.getReferenceId())
            );
        }
        return sourceByBatch;
    }

    private String formatSourceReference(String referenceType, Long referenceId) {
        if (referenceType == null || referenceType.isBlank()) {
            return "-";
        }
        if ("voucher_purchase".equalsIgnoreCase(referenceType) && referenceId != null) {
            return "فاتورة شراء #" + referenceId;
        }
        if ("migration_opening_balance".equalsIgnoreCase(referenceType)) {
            return "رصيد افتتاحي";
        }
        if (referenceId != null) {
            return referenceType + " #" + referenceId;
        }
        return referenceType;
    }

    private boolean matchesExpiryFilter(ProductBatch batch, String filter, LocalDate today) {
        if (batch == null) {
            return false;
        }
        if ("EXPIRED".equalsIgnoreCase(filter)) {
            return batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today);
        }
        if ("NON_EXPIRED".equalsIgnoreCase(filter)) {
            return batch.getExpiryDate() == null || !batch.getExpiryDate().isBefore(today);
        }
        return true;
    }

    private boolean matchesBatchSearch(ProductBatch batch, String search) {
        if (search.isBlank()) {
            return true;
        }
        return contains(batch.getProduct().getName(), search)
                || contains(batch.getProduct().getProductCode(), search)
                || contains(batch.getProduct().getBarcode(), search)
                || contains(batch.getBatchNumber(), search);
    }

    private boolean matchesMovementSearch(InventoryMovement movement, String search) {
        if (search.isBlank()) {
            return true;
        }
        return contains(movement.getProduct() != null ? movement.getProduct().getName() : null, search)
                || contains(movement.getProduct() != null ? movement.getProduct().getProductCode() : null, search)
                || contains(movement.getBatch() != null ? movement.getBatch().getBatchNumber() : null, search)
                || contains(movement.getReferenceType(), search)
                || contains(movement.getNote(), search);
    }

    private boolean matchesPurchaseSearch(VoucherItem item, String search) {
        if (search.isBlank()) {
            return true;
        }
        return contains(item.getProductName(), search)
                || contains(item.getVoucher().getVoucherNumber(), search)
                || contains(item.getVoucher().getCustomer() != null ? item.getVoucher().getCustomer().getName() : null, search)
                || contains(item.getBatchNumber(), search);
    }

    private boolean matchesProductSearch(Product product, String search) {
        if (search.isBlank()) {
            return true;
        }
        return contains(product.getName(), search)
                || contains(product.getProductCode(), search)
                || contains(product.getBarcode(), search)
                || contains(product.getCategory(), search);
    }

    private String classifyExpiry(LocalDate expiryDate, LocalDate today) {
        if (expiryDate == null) {
            return "-";
        }
        if (expiryDate.isBefore(today)) {
            return "منتهي";
        }
        long days = ChronoUnit.DAYS.between(today, expiryDate);
        if (days <= 30) {
            return "خلال 30 يوم";
        }
        if (days <= 60) {
            return "خلال 60 يوم";
        }
        return "خلال 90 يوم";
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public record StockByBatchRow(String productName,
                                  String productCode,
                                  String barcode,
                                  String batchNumber,
                                  LocalDate expiryDate,
                                  Double quantity,
                                  Double unitCost,
                                  Double salePrice,
                                  String status,
                                  String sourceReference,
                                  LocalDateTime createdAt) {}

    public record ExpiryReportRow(String productName,
                                  String productCode,
                                  String barcode,
                                  String batchNumber,
                                  LocalDate expiryDate,
                                  Long daysRemaining,
                                  Double quantity,
                                  String status) {}

    public record MovementReportRow(LocalDateTime movementDate,
                                    String productName,
                                    String productCode,
                                    String batchNumber,
                                    String movementType,
                                    Double quantityBefore,
                                    Double quantityChanged,
                                    Double quantityAfter,
                                    String referenceType,
                                    Long referenceId,
                                    String note) {}

    public record PurchaseReportRow(Long voucherId,
                                    String voucherNumber,
                                    LocalDateTime voucherDate,
                                    String supplierName,
                                    String productName,
                                    Double quantity,
                                    Double amount,
                                    String batchNumber,
                                    LocalDate expirationDate) {}

    public record ProfitReportStatus(boolean reliable, String message) {}

    public record CashboxReportRow(LocalDate date,
                                   Double openingCash,
                                   Double totalIn,
                                   Double totalOut,
                                   Double expectedCash,
                                   Double actualCash,
                                   Double difference,
                                   String status) {}

    public record SalesVelocityRow(String productName,
                                   String productCode,
                                   String barcode,
                                   String category,
                                   Double quantitySold,
                                   Double revenue,
                                   LocalDateTime lastSaleDate,
                                   Double currentStock) {}

    private static class SalesVelocityAccumulator {
        private final Product product;
        private double quantitySold;
        private double revenue;
        private LocalDateTime lastSaleDate;

        private SalesVelocityAccumulator(Product product) {
            this.product = product;
        }

        private void addSale(double quantity, double amount, LocalDateTime saleDate) {
            quantitySold += quantity;
            revenue += amount;
            if (saleDate != null && (lastSaleDate == null || saleDate.isAfter(lastSaleDate))) {
                lastSaleDate = saleDate;
            }
        }

        private SalesVelocityRow toRow() {
            return new SalesVelocityRow(
                    product.getName(),
                    product.getProductCode(),
                    product.getBarcode(),
                    product.getCategory(),
                    quantitySold,
                    revenue,
                    lastSaleDate,
                    product.getQuantityInStock()
            );
        }
    }
}
