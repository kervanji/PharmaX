package com.pharmax.service;

import com.pharmax.database.DatabaseManager;
import com.pharmax.database.Repository.SaleReturnRepository;
import com.pharmax.model.*;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

public class ReturnService {
    private static final Logger logger = LoggerFactory.getLogger(ReturnService.class);
    private static final double EPSILON = 1e-9;

    private final SaleReturnRepository returnRepository;
    @SuppressWarnings("unused") private final InventoryService inventoryService;
    private final ProductBatchService productBatchService;
    private final InventoryMovementService inventoryMovementService;

    public ReturnService() {
        this.returnRepository = new SaleReturnRepository();
        this.inventoryService = new InventoryService();
        this.productBatchService = new ProductBatchService();
        this.inventoryMovementService = new InventoryMovementService();
    }

    public SaleReturn createReturn(Sale sale, List<ReturnItem> items, String reason, String processedBy) {
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Sale managedSale = session.get(Sale.class, sale != null ? sale.getId() : null);
            if (managedSale == null) {
                throw new IllegalArgumentException("الفاتورة الأصلية غير موجودة");
            }
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("لا توجد عناصر للإرجاع");
            }

            List<PreparedReturnItem> preparedItems = new ArrayList<>();
            double totalReturnAmount = 0.0;
            for (ReturnItem item : items) {
                SaleItem originalSaleItem = session.get(
                        SaleItem.class,
                        item.getOriginalSaleItem() != null ? item.getOriginalSaleItem().getId() : null);
                if (originalSaleItem == null) {
                    throw new IllegalArgumentException("العنصر الأصلي غير موجود");
                }

                validateReturnRequest(session, managedSale, originalSaleItem, item);
                List<BatchRestorePlan> restorePlans = List.of();
                boolean restoreToStock = "GOOD".equalsIgnoreCase(item.getConditionStatus());
                if (restoreToStock) {
                    restorePlans = buildRestorePlan(session, originalSaleItem, item.getQuantity());
                }
                double unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : originalSaleItem.getUnitPrice();
                totalReturnAmount += safe(item.getQuantity()) * safe(unitPrice);
                preparedItems.add(new PreparedReturnItem(item, originalSaleItem, restoreToStock, restorePlans, unitPrice));
            }

            SaleReturn saleReturn = new SaleReturn();
            saleReturn.setReturnCode(returnRepository.generateReturnCode());
            saleReturn.setSale(managedSale);
            saleReturn.setCustomer(managedSale.getCustomer());
            saleReturn.setReturnDate(LocalDateTime.now());
            saleReturn.setReturnReason(reason);
            saleReturn.setProcessedBy(processedBy);
            saleReturn.setReturnStatus("COMPLETED");
            saleReturn.setTotalReturnAmount(0.0);

            transaction = session.beginTransaction();
            session.save(saleReturn);
            session.flush();

            List<ReturnItem> returnItems = new ArrayList<>();

            for (PreparedReturnItem prepared : preparedItems) {
                ReturnItem managedReturnItem = new ReturnItem();
                managedReturnItem.setSaleReturn(saleReturn);
                managedReturnItem.setProduct(prepared.originalSaleItem.getProduct());
                managedReturnItem.setOriginalSaleItem(prepared.originalSaleItem);
                managedReturnItem.setQuantity(prepared.requestItem.getQuantity());
                managedReturnItem.setUnitPrice(prepared.unitPrice);
                managedReturnItem.setReturnReason(prepared.requestItem.getReturnReason());
                managedReturnItem.setConditionStatus(prepared.requestItem.getConditionStatus());
                managedReturnItem.setTotalPrice(managedReturnItem.getQuantity() * managedReturnItem.getUnitPrice());

                if (prepared.restoreToStock) {
                    for (BatchRestorePlan plan : prepared.restorePlans) {
                        if (plan.batch.getId() == null) {
                            session.save(plan.batch);
                            session.flush();
                        }
                    }
                    applyReturnSnapshot(managedReturnItem, prepared.restorePlans);
                }

                session.save(managedReturnItem);
                session.flush();

                if (prepared.restoreToStock) {
                    for (BatchRestorePlan plan : prepared.restorePlans) {
                        double quantityBefore = safe(plan.batch.getQuantity());
                        double quantityAfter = quantityBefore + plan.quantityReturned;
                        plan.batch.setQuantity(quantityAfter);
                        plan.batch.setStatus("ACTIVE");
                        plan.batch.setUpdatedAt(LocalDateTime.now());
                        session.saveOrUpdate(plan.batch);

                        ReturnItemBatch restoration = new ReturnItemBatch();
                        restoration.setReturnItem(managedReturnItem);
                        restoration.setSaleItemBatch(plan.saleItemBatch);
                        restoration.setBatch(plan.batch);
                        restoration.setQuantityReturned(plan.quantityReturned);
                        restoration.setBatchNumberSnapshot(plan.batch.getBatchNumber());
                        restoration.setExpirationDateSnapshot(plan.batch.getExpiryDate() != null
                                ? plan.batch.getExpiryDate().toString()
                                : null);
                        restoration.setQuantityBefore(quantityBefore);
                        restoration.setQuantityAfter(quantityAfter);
                        session.save(restoration);

                        inventoryMovementService.recordMovement(
                                session,
                                managedReturnItem.getProduct(),
                                plan.batch,
                                "sale_return",
                                "sale_return",
                                saleReturn.getId(),
                                managedReturnItem.getId(),
                                plan.quantityReturned,
                                quantityBefore,
                                quantityAfter,
                                plan.batch.getUnitCost(),
                                buildReturnMovementNote(saleReturn, managedReturnItem, plan),
                                processedBy);
                    }

                    productBatchService.syncProductSummaryQuantity(session, managedReturnItem.getProduct().getId());
                }

                returnItems.add(managedReturnItem);
            }

            saleReturn.setTotalReturnAmount(totalReturnAmount);
            saleReturn.setReturnItems(returnItems);
            session.saveOrUpdate(saleReturn);

            // Returns reverse the sale's balance effect. For unpaid sales this reduces debt
            // (moves the balance toward zero). For fully paid sales it creates a customer credit
            // until the cash refund is settled elsewhere.
            applyCustomerBalanceCorrection(managedSale.getCustomer(), totalReturnAmount, managedSale.getCurrency());

            transaction.commit();
            logger.info("Created return: {} with amount: {}", saleReturn.getReturnCode(), totalReturnAmount);
            return saleReturn;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    logger.error("Failed to rollback return transaction", rollbackEx);
                }
            }
            logger.error("Failed to create return", e);
            throw new RuntimeException("Failed to create return: " + e.getMessage(), e);
        }
    }

    private void validateReturnRequest(Session session, Sale sale, SaleItem originalSaleItem, ReturnItem requestItem) {
        if (originalSaleItem.getSale() == null || !originalSaleItem.getSale().getId().equals(sale.getId())) {
            throw new IllegalArgumentException("عنصر الإرجاع لا ينتمي إلى الفاتورة المحددة");
        }
        double requestedQty = safe(requestItem.getQuantity());
        if (requestedQty <= EPSILON) {
            throw new IllegalArgumentException("كمية الإرجاع يجب أن تكون أكبر من صفر");
        }
        double soldQty = safe(originalSaleItem.getQuantity());
        double previouslyReturned = getPreviouslyReturnedQuantity(session, originalSaleItem.getId());
        double remaining = soldQty - previouslyReturned;
        if (requestedQty - remaining > EPSILON) {
            throw new IllegalArgumentException("لا يمكن إرجاع كمية أكبر من الكمية المباعة المتبقية");
        }
    }

    private List<BatchRestorePlan> buildRestorePlan(Session session, SaleItem originalSaleItem, Double returnQuantity) {
        double conversionFactor = originalSaleItem.getEffectiveConversionFactor();
        double requiredBaseQuantity = safe(returnQuantity) * conversionFactor;
        if (requiredBaseQuantity <= EPSILON) {
            return List.of();
        }

        List<SaleItemBatch> originalAllocations = new ArrayList<>(originalSaleItem.getBatchAllocations() != null
                ? originalSaleItem.getBatchAllocations()
                : List.of());
        originalAllocations.sort(Comparator
                .comparing((SaleItemBatch allocation) -> allocation.getCreatedAt() != null ? allocation.getCreatedAt() : LocalDateTime.MIN)
                .thenComparing(allocation -> allocation.getId() != null ? allocation.getId() : Long.MAX_VALUE));

        if (!originalAllocations.isEmpty()) {
            return allocateAcrossOriginalBatches(session, originalSaleItem, originalAllocations, requiredBaseQuantity);
        }

        ProductBatch directBatch = resolveLegacyBatch(session, originalSaleItem);
        return List.of(new BatchRestorePlan(null, directBatch, requiredBaseQuantity));
    }

    private List<BatchRestorePlan> allocateAcrossOriginalBatches(Session session,
                                                                 SaleItem originalSaleItem,
                                                                 List<SaleItemBatch> originalAllocations,
                                                                 double requiredBaseQuantity) {
        Map<Long, Double> alreadyReturnedBySaleItemBatch = getAttributedReturnedBySaleItemBatch(session, originalSaleItem.getId());
        double attributedTotal = alreadyReturnedBySaleItemBatch.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalReturnedBase = getPreviouslyReturnedQuantity(session, originalSaleItem.getId()) * originalSaleItem.getEffectiveConversionFactor();
        double unattributedLegacyBase = Math.max(0.0, totalReturnedBase - attributedTotal);

        List<BatchRestorePlan> plans = new ArrayList<>();
        double remainingToRestore = requiredBaseQuantity;

        for (SaleItemBatch allocation : originalAllocations) {
            if (remainingToRestore <= EPSILON) {
                break;
            }

            double alreadyReturned = alreadyReturnedBySaleItemBatch.getOrDefault(allocation.getId(), 0.0);
            double allocSold = safe(allocation.getQuantitySold());
            double legacyShare = Math.min(Math.max(0.0, allocSold - alreadyReturned), unattributedLegacyBase);
            alreadyReturned += legacyShare;
            unattributedLegacyBase -= legacyShare;

            double restorable = allocSold - alreadyReturned;
            if (restorable <= EPSILON) {
                continue;
            }

            double quantityToRestore = Math.min(remainingToRestore, restorable);
            plans.add(new BatchRestorePlan(allocation, allocation.getBatch(), quantityToRestore));
            remainingToRestore -= quantityToRestore;
        }

        if (remainingToRestore > EPSILON) {
            throw new IllegalArgumentException("لا يمكن توزيع الكمية المرتجعة على دفعات البيع الأصلية");
        }

        return plans;
    }

    private ProductBatch resolveLegacyBatch(Session session, SaleItem originalSaleItem) {
        if (originalSaleItem.getBatch() != null) {
            return session.get(ProductBatch.class, originalSaleItem.getBatch().getId());
        }

        String batchNumber = originalSaleItem.getBatchNumberSnapshot();
        if (batchNumber != null && !batchNumber.isBlank() && !"MULTI".equalsIgnoreCase(batchNumber)) {
            Optional<ProductBatch> batchOpt = productBatchService.findByProductIdAndBatchNumber(
                    session,
                    originalSaleItem.getProduct().getId(),
                    batchNumber.trim());
            if (batchOpt.isPresent()) {
                return batchOpt.get();
            }
        }

        ProductBatch batch = new ProductBatch();
        batch.setProduct(originalSaleItem.getProduct());
        batch.setBatchNumber("LEGACY-RETURN-SALEITEM-" + originalSaleItem.getId());
        batch.setExpiryDate(parseSnapshotDate(originalSaleItem.getExpirationDateSnapshot()));
        batch.setUnitCost(originalSaleItem.getUnitCostSnapshot());
        batch.setCurrency(originalSaleItem.getSale() != null ? originalSaleItem.getSale().getCurrency() : "دينار");
        batch.setQuantity(0.0);
        batch.setOriginalQuantity(0.0);
        batch.setStatus("ACTIVE");
        batch.setIsOpeningBatch(false);
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        return batch;
    }

    private void applyReturnSnapshot(ReturnItem returnItem, List<BatchRestorePlan> restorePlans) {
        if (restorePlans.size() == 1) {
            BatchRestorePlan plan = restorePlans.get(0);
            returnItem.setBatch(plan.batch);
            returnItem.setSaleItemBatch(plan.saleItemBatch);
            returnItem.setBatchNumberSnapshot(plan.batch.getBatchNumber());
            returnItem.setExpirationDateSnapshot(plan.batch.getExpiryDate() != null ? plan.batch.getExpiryDate().toString() : null);
            return;
        }

        returnItem.setBatch(null);
        returnItem.setSaleItemBatch(null);
        returnItem.setBatchNumberSnapshot("MULTI");
        returnItem.setExpirationDateSnapshot(null);
    }

    private double getPreviouslyReturnedQuantity(Session session, Long saleItemId) {
        Query<Double> query = session.createQuery(
                "SELECT COALESCE(SUM(ri.quantity), 0) FROM ReturnItem ri " +
                        "WHERE ri.originalSaleItem.id = :saleItemId " +
                        "AND ri.saleReturn.returnStatus = :status",
                Double.class);
        query.setParameter("saleItemId", saleItemId);
        query.setParameter("status", "COMPLETED");
        Double returned = query.uniqueResult();
        return returned != null ? returned : 0.0;
    }

    private Map<Long, Double> getAttributedReturnedBySaleItemBatch(Session session, Long saleItemId) {
        Query<Object[]> query = session.createQuery(
                "SELECT rib.saleItemBatch.id, COALESCE(SUM(rib.quantityReturned), 0) " +
                        "FROM ReturnItemBatch rib " +
                        "WHERE rib.returnItem.originalSaleItem.id = :saleItemId " +
                        "GROUP BY rib.saleItemBatch.id",
                Object[].class);
        query.setParameter("saleItemId", saleItemId);

        Map<Long, Double> returned = new HashMap<>();
        for (Object[] row : query.list()) {
            if (row[0] != null) {
                returned.put((Long) row[0], row[1] != null ? ((Number) row[1]).doubleValue() : 0.0);
            }
        }
        return returned;
    }

    private void applyCustomerBalanceCorrection(Customer customer, double amount, String currency) {
        if ("دولار".equals(currency) || "USD".equalsIgnoreCase(currency)) {
            customer.setBalanceUsd(customer.getBalanceUsd() + amount);
        } else {
            customer.setBalanceIqd(customer.getBalanceIqd() + amount);
            customer.setCurrentBalance(customer.getCurrentBalance() + amount);
        }
    }

    private String buildReturnMovementNote(SaleReturn saleReturn, ReturnItem returnItem, BatchRestorePlan plan) {
        String productName = returnItem.getProduct() != null ? returnItem.getProduct().getName() : "-";
        String batchNumber = plan.batch != null ? plan.batch.getBatchNumber() : "-";
        return "Sale return " + saleReturn.getReturnCode() + " - " + productName + " - batch " + batchNumber;
    }

    private java.time.LocalDate parseSnapshotDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private double safe(Double value) {
        return value != null ? value : 0.0;
    }

    private static class BatchRestorePlan {
        private final SaleItemBatch saleItemBatch;
        private final ProductBatch batch;
        private final double quantityReturned;

        private BatchRestorePlan(SaleItemBatch saleItemBatch, ProductBatch batch, double quantityReturned) {
            this.saleItemBatch = saleItemBatch;
            this.batch = batch;
            this.quantityReturned = quantityReturned;
        }
    }

    private static class PreparedReturnItem {
        private final ReturnItem requestItem;
        private final SaleItem originalSaleItem;
        private final boolean restoreToStock;
        private final List<BatchRestorePlan> restorePlans;
        private final double unitPrice;

        private PreparedReturnItem(ReturnItem requestItem,
                                   SaleItem originalSaleItem,
                                   boolean restoreToStock,
                                   List<BatchRestorePlan> restorePlans,
                                   double unitPrice) {
            this.requestItem = requestItem;
            this.originalSaleItem = originalSaleItem;
            this.restoreToStock = restoreToStock;
            this.restorePlans = restorePlans;
            this.unitPrice = unitPrice;
        }
    }

    public List<SaleReturn> getAllReturns() {
        return returnRepository.findAllWithDetails();
    }

    public List<SaleReturn> getReturnsBySale(Long saleId) {
        return returnRepository.findBySaleId(saleId);
    }

    public List<SaleReturn> getReturnsByCustomer(Long customerId) {
        return returnRepository.findByCustomerId(customerId);
    }

    public Double getTotalReturnsByCustomerAndProject(Long customerId, String projectLocation) {
        return returnRepository.getTotalReturnsByCustomerAndProject(customerId, projectLocation);
    }

    public SaleReturn getReturnById(Long id) {
        return returnRepository.findById(id).orElse(null);
    }

    public void updateReturnStatus(Long returnId, String status) {
        SaleReturn saleReturn = returnRepository.findById(returnId).orElse(null);
        if (saleReturn != null) {
            saleReturn.setReturnStatus(status);
            saleReturn.setUpdatedAt(LocalDateTime.now());
            returnRepository.save(saleReturn);
        }
    }

    public void deleteReturn(Long returnId) {
        SaleReturn saleReturn = returnRepository.findById(returnId).orElse(null);
        if (saleReturn != null) {
            returnRepository.delete(saleReturn);
        }
    }

    public File generateReturnReceiptPdf(SaleReturn saleReturn) {
        return generateReturnReceiptPdf(saleReturn, null);
    }

    public File generateReturnReceiptPdf(SaleReturn saleReturn, File outputFile) {
        if (saleReturn == null) {
            throw new IllegalArgumentException("المرتجع غير موجود");
        }

        try {
            byte[] pdfData = generateReturnReceiptPDF(saleReturn);

            File out = outputFile;
            if (out == null) {
                File dir = new File("receipts");
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String fileName = "return_" + saleReturn.getReturnCode() + "_" + datePart + ".pdf";
                out = new File(dir, fileName);
            }

            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(pdfData);
            }

            return out;
        } catch (Exception e) {
            logger.error("Failed to generate return receipt PDF", e);
            throw new RuntimeException("فشل في إنشاء إيصال المرتجع", e);
        }
    }

    public File generateAllReturnsReceiptPdf(Sale sale) {
        if (sale == null) {
            throw new IllegalArgumentException("الفاتورة غير موجودة");
        }

        List<SaleReturn> returns = returnRepository.findBySaleId(sale.getId());
        if (returns.isEmpty()) {
            throw new IllegalArgumentException("لا توجد مرتجعات لهذه الفاتورة");
        }

        try {
            byte[] pdfData = generateAllReturnsReceiptPDF(sale, returns);

            File dir = new File("receipts");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "returns_" + sale.getSaleCode() + "_" + datePart + ".pdf";
            File out = new File(dir, fileName);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(pdfData);
            }

            return out;
        } catch (Exception e) {
            logger.error("Failed to generate all returns receipt PDF", e);
            throw new RuntimeException("فشل في إنشاء إيصال المرتجعات", e);
        }
    }

    private byte[] generateReturnReceiptPDF(SaleReturn saleReturn) throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final float bannerTargetHeight = 120f;
        Document document = new Document(PageSize.A4, 30, 30, 30 + bannerTargetHeight, 30);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        BaseFont baseFont = loadArabicBaseFont();
        Font arabicFont = new Font(baseFont, 10, Font.NORMAL);
        Font arabicBoldFont = new Font(baseFont, 11, Font.BOLD);
        Font sectionTitleFont = new Font(baseFont, 14, Font.BOLD);
        Font smallFont = new Font(baseFont, 9, Font.NORMAL);

        try {
            Image banner = loadBannerImage();
            if (banner != null) {
                float pageWidth = document.getPageSize().getWidth();
                float pageHeight = document.getPageSize().getHeight();
                banner.scaleAbsolute(pageWidth, bannerTargetHeight);
                banner.setAbsolutePosition(0f, pageHeight - bannerTargetHeight);
                writer.getDirectContent().addImage(banner);
            }
        } catch (Exception e) {
            logger.warn("Failed to add banner", e);
        }

        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        header.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        PdfPCell titleCell = new PdfPCell(new Phrase("إيصال مرتجع", sectionTitleFont));
        titleCell.setBorder(PdfPCell.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        titleCell.setPaddingBottom(8f);
        titleCell.setBackgroundColor(new BaseColor(255, 230, 230));
        header.addCell(titleCell);

        document.add(header);

        Sale sale = saleReturn.getSale();
        Customer customer = saleReturn.getCustomer();

        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        info.setWidths(new float[] { 1f, 1f });
        info.setSpacingBefore(10f);
        info.setSpacingAfter(10f);

        PdfPCell leftInfo = new PdfPCell();
        leftInfo.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        leftInfo.setPadding(8f);
        leftInfo.addElement(
                new Phrase("رقم المرتجع: " + (saleReturn.getReturnCode() != null ? saleReturn.getReturnCode() : "-"),
                        arabicBoldFont));
        leftInfo.addElement(new Phrase(
                "رقم الفاتورة الأصلية: " + (sale != null && sale.getSaleCode() != null ? sale.getSaleCode() : "-"),
                arabicFont));
        leftInfo.addElement(new Phrase("تاريخ الإرجاع: " + (saleReturn.getReturnDate() != null
                ? saleReturn.getReturnDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : "-"), arabicFont));
        info.addCell(leftInfo);

        PdfPCell rightInfo = new PdfPCell();
        rightInfo.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        rightInfo.setPadding(8f);
        rightInfo.addElement(
                new Phrase("العميل: " + (customer != null && customer.getName() != null ? customer.getName() : "-"),
                        arabicBoldFont));
        if (customer != null && customer.getPhoneNumber() != null && !customer.getPhoneNumber().trim().isEmpty()) {
            rightInfo.addElement(new Phrase("الهاتف: " + customer.getPhoneNumber(), arabicFont));
        }
        if (sale != null && sale.getProjectLocation() != null && !sale.getProjectLocation().trim().isEmpty()) {
            rightInfo.addElement(new Phrase("المشروع: " + sale.getProjectLocation(), arabicFont));
        }
        info.addCell(rightInfo);

        document.add(info);

        PdfPTable itemsTable = new PdfPTable(7);
        itemsTable.setWidthPercentage(100);
        itemsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        itemsTable.setSpacingBefore(5f);
        itemsTable.setSpacingAfter(10f);
        itemsTable.setWidths(new float[] { 1.2f, 2f, 0.9f, 0.9f, 0.9f, 1f, 0.4f });

        addTableHeader(itemsTable, "ت", arabicBoldFont);
        addTableHeader(itemsTable, "المادة", arabicBoldFont);
        addTableHeader(itemsTable, "الكمية الأصلية", arabicBoldFont);
        addTableHeader(itemsTable, "المرتجع", arabicBoldFont);
        addTableHeader(itemsTable, "المتبقي", arabicBoldFont);
        addTableHeader(itemsTable, "سعر الوحدة", arabicBoldFont);
        addTableHeader(itemsTable, "مبلغ المرتجع", arabicBoldFont);

        List<ReturnItem> items = saleReturn.getReturnItems();
        int row = 1;
        double totalOriginalQty = 0;
        double totalReturnedQty = 0;
        double totalRemainingQty = 0;

        List<SaleReturn> allReturnsForSale = returnRepository.findBySaleId(sale != null ? sale.getId() : null);
        allReturnsForSale.sort((r1, r2) -> r1.getReturnDate().compareTo(r2.getReturnDate()));

        if (items != null) {
            for (ReturnItem item : items) {
                String productName = item.getProduct() != null && item.getProduct().getName() != null
                        ? item.getProduct().getName()
                        : "-";
                double returnedQty = item.getQuantity() != null ? item.getQuantity() : 0;
                double originalQty = 0;
                if (item.getOriginalSaleItem() != null && item.getOriginalSaleItem().getQuantity() != null) {
                    originalQty = item.getOriginalSaleItem().getQuantity();
                }

                double totalReturnedBeforeThis = 0;
                if (item.getOriginalSaleItem() != null) {
                    Long saleItemId = item.getOriginalSaleItem().getId();
                    for (SaleReturn ret : allReturnsForSale) {
                        if (ret.getReturnDate().isBefore(saleReturn.getReturnDate()) ||
                                (ret.getReturnDate().equals(saleReturn.getReturnDate())
                                        && ret.getId() < saleReturn.getId())) {
                            if (ret.getReturnItems() != null) {
                                for (ReturnItem retItem : ret.getReturnItems()) {
                                    if (retItem.getOriginalSaleItem() != null &&
                                            retItem.getOriginalSaleItem().getId().equals(saleItemId)) {
                                        totalReturnedBeforeThis += retItem.getQuantity() != null ? retItem.getQuantity()
                                                : 0;
                                    }
                                }
                            }
                        }
                    }
                }

                double remainingQty = originalQty - totalReturnedBeforeThis - returnedQty;
                double unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : 0.0;
                double total = item.getTotalPrice() != null ? item.getTotalPrice() : 0.0;

                totalOriginalQty += originalQty;
                totalReturnedQty += returnedQty;
                totalRemainingQty += remainingQty;

                itemsTable.addCell(createBodyCell(String.valueOf(row), arabicFont, Element.ALIGN_CENTER));
                itemsTable.addCell(createBodyCell(productName, arabicFont, Element.ALIGN_RIGHT));
                itemsTable.addCell(createBodyCell(String.valueOf(originalQty), arabicFont, Element.ALIGN_CENTER));
                itemsTable.addCell(createBodyCell(String.valueOf(returnedQty), arabicFont, Element.ALIGN_CENTER));
                itemsTable.addCell(createBodyCell(String.valueOf(remainingQty), arabicFont, Element.ALIGN_CENTER));
                String currency = sale != null && sale.getCurrency() != null ? sale.getCurrency() : "د.ع";
                itemsTable
                        .addCell(createBodyCell(formatCurrency(unitPrice, currency), arabicFont, Element.ALIGN_CENTER));
                itemsTable.addCell(createBodyCell(formatCurrency(total, currency), arabicFont, Element.ALIGN_CENTER));
                row++;
            }
        }

        PdfPCell totalLabelCell = new PdfPCell(new Phrase("الإجمالي", arabicBoldFont));
        totalLabelCell.setColspan(2);
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalLabelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        totalLabelCell.setPadding(5f);
        totalLabelCell.setBackgroundColor(new BaseColor(240, 240, 240));
        itemsTable.addCell(totalLabelCell);

        PdfPCell totalOrigQtyCell = new PdfPCell(new Phrase(String.valueOf(totalOriginalQty), arabicBoldFont));
        totalOrigQtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalOrigQtyCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        totalOrigQtyCell.setPadding(5f);
        totalOrigQtyCell.setBackgroundColor(new BaseColor(240, 240, 240));
        itemsTable.addCell(totalOrigQtyCell);

        PdfPCell totalRetQtyCell = new PdfPCell(new Phrase(String.valueOf(totalReturnedQty), arabicBoldFont));
        totalRetQtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalRetQtyCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        totalRetQtyCell.setPadding(5f);
        totalRetQtyCell.setBackgroundColor(new BaseColor(240, 240, 240));
        itemsTable.addCell(totalRetQtyCell);

        PdfPCell totalRemQtyCell = new PdfPCell(new Phrase(String.valueOf(totalRemainingQty), arabicBoldFont));
        totalRemQtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalRemQtyCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        totalRemQtyCell.setPadding(5f);
        totalRemQtyCell.setBackgroundColor(new BaseColor(230, 255, 230));
        itemsTable.addCell(totalRemQtyCell);

        String currency = sale != null && sale.getCurrency() != null ? sale.getCurrency() : "د.ع";
        PdfPCell totalAmountCell = new PdfPCell(
                new Phrase(formatCurrency(saleReturn.getTotalReturnAmount(), currency), arabicBoldFont));
        totalAmountCell.setColspan(2);
        totalAmountCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalAmountCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        totalAmountCell.setPadding(5f);
        totalAmountCell.setBackgroundColor(new BaseColor(255, 245, 245));
        itemsTable.addCell(totalAmountCell);

        document.add(itemsTable);

        double originalTotal = sale != null && sale.getFinalAmount() != null ? sale.getFinalAmount() : 0.0;
        double originalPaid = sale != null && sale.getPaidAmount() != null ? sale.getPaidAmount() : 0.0;

        double totalReturnsUpToThis = 0.0;
        if (sale != null) {
            for (SaleReturn ret : allReturnsForSale) {
                if (ret.getReturnDate().isBefore(saleReturn.getReturnDate()) ||
                        (ret.getReturnDate().equals(saleReturn.getReturnDate()) && ret.getId() <= saleReturn.getId())) {
                    totalReturnsUpToThis += ret.getTotalReturnAmount() != null ? ret.getTotalReturnAmount() : 0.0;
                }
            }
        }

        double remainingAfterReturn = originalTotal - originalPaid - totalReturnsUpToThis;

        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(80);
        summary.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        summary.setSpacingBefore(10f);
        summary.setSpacingAfter(10f);
        summary.setHorizontalAlignment(Element.ALIGN_CENTER);
        summary.setWidths(new float[] { 1.5f, 1f });

        addSummaryRow(summary, "إجمالي الفاتورة الأصلية:", formatCurrency(originalTotal, currency), arabicFont,
                arabicBoldFont);
        addSummaryRow(summary, "المدفوع مسبقاً:", formatCurrency(originalPaid, currency), arabicFont, arabicBoldFont);
        addSummaryRow(summary, "إجمالي المرتجعات:", formatCurrency(totalReturnsUpToThis, currency), arabicFont,
                arabicBoldFont, new BaseColor(255, 230, 230));
        addSummaryRow(summary, "المتبقي على العميل:", formatCurrency(remainingAfterReturn, currency), arabicFont,
                arabicBoldFont, new BaseColor(230, 255, 230));

        document.add(summary);

        if (saleReturn.getReturnReason() != null && !saleReturn.getReturnReason().trim().isEmpty()) {
            PdfPTable reasonTable = new PdfPTable(1);
            reasonTable.setWidthPercentage(100);
            reasonTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            reasonTable.setSpacingBefore(10f);

            PdfPCell reasonCell = new PdfPCell(new Phrase("سبب الإرجاع: " + saleReturn.getReturnReason(), arabicFont));
            reasonCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            reasonCell.setPadding(8f);
            reasonCell.setBackgroundColor(new BaseColor(245, 245, 245));
            reasonTable.addCell(reasonCell);

            document.add(reasonTable);
        }

        addUnifiedFooter(document, arabicBoldFont, smallFont);

        document.close();
        return baos.toByteArray();
    }

    private byte[] generateAllReturnsReceiptPDF(Sale sale, List<SaleReturn> returns)
            throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final float bannerTargetHeight = 120f;
        Document document = new Document(PageSize.A4, 30, 30, 30 + bannerTargetHeight, 30);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        BaseFont baseFont = loadArabicBaseFont();
        Font arabicFont = new Font(baseFont, 10, Font.NORMAL);
        Font arabicBoldFont = new Font(baseFont, 11, Font.BOLD);
        Font sectionTitleFont = new Font(baseFont, 14, Font.BOLD);
        Font smallFont = new Font(baseFont, 9, Font.NORMAL);

        try {
            Image banner = loadBannerImage();
            if (banner != null) {
                float pageWidth = document.getPageSize().getWidth();
                float pageHeight = document.getPageSize().getHeight();
                banner.scaleAbsolute(pageWidth, bannerTargetHeight);
                banner.setAbsolutePosition(0f, pageHeight - bannerTargetHeight);
                writer.getDirectContent().addImage(banner);
            }
        } catch (Exception e) {
            logger.warn("Failed to add banner", e);
        }

        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        header.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        PdfPCell titleCell = new PdfPCell(new Phrase("إيصال مرتجعات", sectionTitleFont));
        titleCell.setBorder(PdfPCell.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        titleCell.setPaddingBottom(8f);
        titleCell.setBackgroundColor(new BaseColor(255, 230, 230));
        header.addCell(titleCell);

        document.add(header);

        Customer customer = sale.getCustomer();

        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        info.setWidths(new float[] { 1f, 1f });
        info.setSpacingBefore(10f);
        info.setSpacingAfter(10f);

        PdfPCell leftInfo = new PdfPCell();
        leftInfo.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        leftInfo.setPadding(8f);
        leftInfo.addElement(new Phrase(
                "رقم الفاتورة الأصلية: " + (sale.getSaleCode() != null ? sale.getSaleCode() : "-"), arabicBoldFont));
        leftInfo.addElement(new Phrase("عدد المرتجعات: " + returns.size(), arabicFont));
        leftInfo.addElement(new Phrase(
                "تاريخ الإصدار: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                arabicFont));
        info.addCell(leftInfo);

        PdfPCell rightInfo = new PdfPCell();
        rightInfo.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        rightInfo.setPadding(8f);
        rightInfo.addElement(
                new Phrase("العميل: " + (customer != null && customer.getName() != null ? customer.getName() : "-"),
                        arabicBoldFont));
        if (customer != null && customer.getPhoneNumber() != null && !customer.getPhoneNumber().trim().isEmpty()) {
            rightInfo.addElement(new Phrase("الهاتف: " + customer.getPhoneNumber(), arabicFont));
        }
        if (sale.getProjectLocation() != null && !sale.getProjectLocation().trim().isEmpty()) {
            rightInfo.addElement(new Phrase("المشروع: " + sale.getProjectLocation(), arabicFont));
        }
        info.addCell(rightInfo);

        document.add(info);

        for (int i = 0; i < returns.size(); i++) {
            SaleReturn saleReturn = returns.get(i);

            if (i > 0) {
                document.add(new com.itextpdf.text.Paragraph("\n"));
            }

            PdfPTable returnHeader = new PdfPTable(1);
            returnHeader.setWidthPercentage(100);
            returnHeader.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            returnHeader.setSpacingBefore(10f);
            returnHeader.setSpacingAfter(5f);

            PdfPCell returnTitleCell = new PdfPCell(
                    new Phrase("مرتجع رقم " + (i + 1) + " - " + saleReturn.getReturnCode() + " - " +
                            (saleReturn.getReturnDate() != null
                                    ? saleReturn.getReturnDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                    : ""),
                            arabicBoldFont));
            returnTitleCell.setBorder(PdfPCell.NO_BORDER);
            returnTitleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            returnTitleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            returnTitleCell.setPadding(5f);
            returnTitleCell.setBackgroundColor(new BaseColor(245, 245, 245));
            returnHeader.addCell(returnTitleCell);
            document.add(returnHeader);

            PdfPTable itemsTable = new PdfPTable(7);
            itemsTable.setWidthPercentage(100);
            itemsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            itemsTable.setSpacingBefore(5f);
            itemsTable.setSpacingAfter(5f);
            itemsTable.setWidths(new float[] { 1.2f, 2f, 0.9f, 0.9f, 0.9f, 1f, 0.4f });

            addTableHeader(itemsTable, "ت", arabicBoldFont);
            addTableHeader(itemsTable, "المادة", arabicBoldFont);
            addTableHeader(itemsTable, "الكمية الأصلية", arabicBoldFont);
            addTableHeader(itemsTable, "المرتجع", arabicBoldFont);
            addTableHeader(itemsTable, "المتبقي", arabicBoldFont);
            addTableHeader(itemsTable, "سعر الوحدة", arabicBoldFont);
            addTableHeader(itemsTable, "مبلغ المرتجع", arabicBoldFont);

            List<ReturnItem> items = saleReturn.getReturnItems();
            int row = 1;
            double totalOriginalQty = 0;
            double totalReturnedQty = 0;
            double totalRemainingQty = 0;
            if (items != null) {
                for (ReturnItem item : items) {
                    String productName = item.getProduct() != null && item.getProduct().getName() != null
                            ? item.getProduct().getName()
                            : "-";
                    double returnedQty = item.getQuantity() != null ? item.getQuantity() : 0;
                    double originalQty = 0;
                    if (item.getOriginalSaleItem() != null && item.getOriginalSaleItem().getQuantity() != null) {
                        originalQty = item.getOriginalSaleItem().getQuantity();
                    }

                    double totalReturnedBeforeThis = 0;
                    if (item.getOriginalSaleItem() != null) {
                        Long saleItemId = item.getOriginalSaleItem().getId();
                        for (SaleReturn ret : returns) {
                            if (ret.getReturnDate().isBefore(saleReturn.getReturnDate()) ||
                                    (ret.getReturnDate().equals(saleReturn.getReturnDate())
                                            && ret.getId() < saleReturn.getId())) {
                                if (ret.getReturnItems() != null) {
                                    for (ReturnItem retItem : ret.getReturnItems()) {
                                        if (retItem.getOriginalSaleItem() != null &&
                                                retItem.getOriginalSaleItem().getId().equals(saleItemId)) {
                                            totalReturnedBeforeThis += retItem.getQuantity() != null
                                                    ? retItem.getQuantity()
                                                    : 0;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    double remainingQty = originalQty - totalReturnedBeforeThis - returnedQty;
                    double unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : 0.0;
                    double total = item.getTotalPrice() != null ? item.getTotalPrice() : 0.0;

                    totalOriginalQty += originalQty;
                    totalReturnedQty += returnedQty;
                    totalRemainingQty += remainingQty;

                    itemsTable.addCell(createBodyCell(String.valueOf(row), arabicFont, Element.ALIGN_CENTER));
                    itemsTable.addCell(createBodyCell(productName, arabicFont, Element.ALIGN_RIGHT));
                    itemsTable.addCell(createBodyCell(String.valueOf(originalQty), arabicFont, Element.ALIGN_CENTER));
                    itemsTable.addCell(createBodyCell(String.valueOf(returnedQty), arabicFont, Element.ALIGN_CENTER));
                    itemsTable.addCell(createBodyCell(String.valueOf(remainingQty), arabicFont, Element.ALIGN_CENTER));
                    String currency = sale != null && sale.getCurrency() != null ? sale.getCurrency() : "د.ع";
                    itemsTable.addCell(
                            createBodyCell(formatCurrency(unitPrice, currency), arabicFont, Element.ALIGN_CENTER));
                    itemsTable
                            .addCell(createBodyCell(formatCurrency(total, currency), arabicFont, Element.ALIGN_CENTER));
                    row++;
                }
            }

            PdfPCell totalLabelCell = new PdfPCell(new Phrase("الإجمالي", arabicBoldFont));
            totalLabelCell.setColspan(2);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            totalLabelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            totalLabelCell.setPadding(5f);
            totalLabelCell.setBackgroundColor(new BaseColor(240, 240, 240));
            itemsTable.addCell(totalLabelCell);

            PdfPCell totalOrigQtyCell = new PdfPCell(new Phrase(String.valueOf(totalOriginalQty), arabicBoldFont));
            totalOrigQtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            totalOrigQtyCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            totalOrigQtyCell.setPadding(5f);
            totalOrigQtyCell.setBackgroundColor(new BaseColor(240, 240, 240));
            itemsTable.addCell(totalOrigQtyCell);

            PdfPCell totalRetQtyCell = new PdfPCell(new Phrase(String.valueOf(totalReturnedQty), arabicBoldFont));
            totalRetQtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            totalRetQtyCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            totalRetQtyCell.setPadding(5f);
            totalRetQtyCell.setBackgroundColor(new BaseColor(240, 240, 240));
            itemsTable.addCell(totalRetQtyCell);

            PdfPCell totalRemQtyCell = new PdfPCell(new Phrase(String.valueOf(totalRemainingQty), arabicBoldFont));
            totalRemQtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            totalRemQtyCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            totalRemQtyCell.setPadding(5f);
            totalRemQtyCell.setBackgroundColor(new BaseColor(230, 255, 230));
            itemsTable.addCell(totalRemQtyCell);

            String currency = sale != null && sale.getCurrency() != null ? sale.getCurrency() : "د.ع";
            PdfPCell totalAmountCell = new PdfPCell(
                    new Phrase(formatCurrency(saleReturn.getTotalReturnAmount(), currency), arabicBoldFont));
            totalAmountCell.setColspan(2);
            totalAmountCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            totalAmountCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            totalAmountCell.setPadding(5f);
            totalAmountCell.setBackgroundColor(new BaseColor(255, 245, 245));
            itemsTable.addCell(totalAmountCell);

            document.add(itemsTable);

            if (saleReturn.getReturnReason() != null && !saleReturn.getReturnReason().trim().isEmpty()) {
                PdfPTable reasonTable = new PdfPTable(1);
                reasonTable.setWidthPercentage(100);
                reasonTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                reasonTable.setSpacingBefore(5f);

                PdfPCell reasonCell = new PdfPCell(new Phrase("السبب: " + saleReturn.getReturnReason(), smallFont));
                reasonCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                reasonCell.setPadding(5f);
                reasonCell.setBackgroundColor(new BaseColor(250, 250, 250));
                reasonTable.addCell(reasonCell);

                document.add(reasonTable);
            }
        }

        double originalTotal = sale != null && sale.getFinalAmount() != null ? sale.getFinalAmount() : 0.0;
        double originalPaid = sale != null && sale.getPaidAmount() != null ? sale.getPaidAmount() : 0.0;

        double totalReturnsUpToLast = 0.0;
        for (SaleReturn ret : returns) {
            totalReturnsUpToLast += ret.getTotalReturnAmount() != null ? ret.getTotalReturnAmount() : 0.0;
        }

        double remainingAfterReturn = originalTotal - originalPaid - totalReturnsUpToLast;

        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(80);
        summary.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        summary.setSpacingBefore(15f);
        summary.setSpacingAfter(10f);
        summary.setHorizontalAlignment(Element.ALIGN_CENTER);
        summary.setWidths(new float[] { 1.5f, 1f });

        String currency = sale != null && sale.getCurrency() != null ? sale.getCurrency() : "د.ع";
        addSummaryRow(summary, "إجمالي الفاتورة الأصلية:", formatCurrency(originalTotal, currency), arabicFont,
                arabicBoldFont);
        addSummaryRow(summary, "المدفوع مسبقاً:", formatCurrency(originalPaid, currency), arabicFont, arabicBoldFont);
        addSummaryRow(summary, "إجمالي المرتجعات:", formatCurrency(totalReturnsUpToLast, currency), arabicFont,
                arabicBoldFont, new BaseColor(255, 230, 230));
        addSummaryRow(summary, "المتبقي على العميل:", formatCurrency(remainingAfterReturn, currency), arabicFont,
                arabicBoldFont, new BaseColor(230, 255, 230));

        document.add(summary);

        addUnifiedFooter(document, arabicBoldFont, smallFont);

        document.close();
        return baos.toByteArray();
    }

    @SuppressWarnings("unused")
    private Double getTotalReturnsBySale(Long saleId) {
        if (saleId == null)
            return 0.0;
        List<SaleReturn> returns = returnRepository.findBySaleId(saleId);
        double total = 0.0;
        for (SaleReturn r : returns) {
            if (r.getTotalReturnAmount() != null) {
                total += r.getTotalReturnAmount();
            }
        }
        return total;
    }

    private BaseFont loadArabicBaseFont() throws DocumentException, IOException {
        String[] fontCandidates = new String[] {
                "C:\\Windows\\Fonts\\arial.ttf",
                "C:\\Windows\\Fonts\\tahoma.ttf",
                "C:\\Windows\\Fonts\\arialuni.ttf",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                "/System/Library/Fonts/Supplemental/Diwan Kufi.ttc,0",
                "/System/Library/Fonts/Supplemental/Damascus.ttc,0",
                "/System/Library/Fonts/Supplemental/DecoTypeNaskh.ttc,0",
                "/System/Library/Fonts/Supplemental/KufiStandardGK.ttc,0",
                "/Library/Fonts/Arial Unicode.ttf",
                "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf",
                "/usr/share/fonts/truetype/noto/NotoSansArabic-Regular.ttf",
                "/usr/share/fonts/opentype/noto/NotoNaskhArabic-Regular.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        };

        for (String fontPath : fontCandidates) {
            try {
                return BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ignored) {
            }
        }

        logger.warn("Arabic font not found on system. Falling back to Helvetica.");
        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    }

    private Image loadBannerImage() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(ReceiptService.class);
            String bannerPath = prefs.get("receipt.banner.path", null);
            if (bannerPath != null && !bannerPath.trim().isEmpty()) {
                File bannerFile = new File(bannerPath);
                if (bannerFile.exists() && bannerFile.isFile()) {
                    return Image.getInstance(bannerFile.getAbsolutePath());
                }
            }

            java.net.URL logoUrl = ReturnService.class.getResource("/templates/PharmaX.png");
            if (logoUrl != null) {
                return Image.getInstance(logoUrl);
            }
        } catch (Exception e) {
            logger.warn("Failed to load banner image", e);
        }
        return null;
    }

    private void addTableHeader(PdfPTable table, String header, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(header, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private PdfPCell createBodyCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setPadding(4f);
        return cell;
    }

    private void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        addSummaryRow(table, label, value, labelFont, valueFont, null);
    }

    private void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont,
            BaseColor bgColor) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        labelCell.setPadding(5f);
        if (bgColor != null) {
            labelCell.setBackgroundColor(bgColor);
        }

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        valueCell.setPadding(5f);
        if (bgColor != null) {
            valueCell.setBackgroundColor(bgColor);
        }

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private String formatCurrency(Double value, String currency) {
        double v = value != null ? value : 0.0;
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        String c = currency != null && "دولار".equals(currency) ? "$" : "د.ع";
        return df.format(v) + " " + c;
    }

    private void addUnifiedFooter(Document document, Font boldFont, Font smallFont) throws DocumentException {
        PdfPTable footerTable = new PdfPTable(1);
        footerTable.setWidthPercentage(100);
        footerTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        footerTable.setSpacingBefore(15f);

        PdfPCell thankYouCell = new PdfPCell(new Phrase("شكراً لتعاملكم معنا", boldFont));
        thankYouCell.setBorder(PdfPCell.NO_BORDER);
        thankYouCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        thankYouCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        thankYouCell.setPaddingBottom(8f);
        footerTable.addCell(thankYouCell);

        PdfPCell appInfoCell = new PdfPCell(new Phrase("PharmaX | Kervanjiholding.com", smallFont));
        appInfoCell.setBorder(PdfPCell.NO_BORDER);
        appInfoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        appInfoCell.setRunDirection(PdfWriter.RUN_DIRECTION_LTR);
        appInfoCell.setPaddingTop(5f);
        appInfoCell.setBackgroundColor(new BaseColor(245, 245, 245));
        footerTable.addCell(appInfoCell);

        document.add(footerTable);
    }
}
