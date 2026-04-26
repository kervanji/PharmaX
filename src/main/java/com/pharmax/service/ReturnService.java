package com.pharmax.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class ReturnService {
    private static final Logger logger = LoggerFactory.getLogger(ReturnService.class);

    private final SaleReturnRepository returnRepository;
    private final InventoryService inventoryService;

    public ReturnService() {
        this.returnRepository = new SaleReturnRepository();
        this.inventoryService = new InventoryService();
    }

    public SaleReturn createReturn(Sale sale, List<ReturnItem> items, String reason, String processedBy) {
        try {
            SaleReturn saleReturn = new SaleReturn();
            saleReturn.setReturnCode(returnRepository.generateReturnCode());
            saleReturn.setSale(sale);
            saleReturn.setCustomer(sale.getCustomer());
            saleReturn.setReturnDate(LocalDateTime.now());
            saleReturn.setReturnReason(reason);
            saleReturn.setProcessedBy(processedBy);
            saleReturn.setReturnStatus("COMPLETED");

            double totalReturnAmount = 0.0;
            List<ReturnItem> returnItems = new ArrayList<>();

            for (ReturnItem item : items) {
                item.setSaleReturn(saleReturn);
                item.setTotalPrice(item.getQuantity() * item.getUnitPrice());
                totalReturnAmount += item.getTotalPrice();
                returnItems.add(item);

                // Update inventory - add returned items back to stock
                if ("GOOD".equals(item.getConditionStatus())) {
                    inventoryService.addStock(item.getProduct().getId(), item.getQuantity());
                }
            }

            saleReturn.setTotalReturnAmount(totalReturnAmount);
            saleReturn.setReturnItems(returnItems);

            SaleReturn savedReturn = returnRepository.save(saleReturn);
            logger.info("Created return: {} with amount: {}", savedReturn.getReturnCode(), totalReturnAmount);
            return savedReturn;
        } catch (Exception e) {
            logger.error("Failed to create return", e);
            throw new RuntimeException("Failed to create return: " + e.getMessage(), e);
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

        double originalTotal = sale.getFinalAmount() != null ? sale.getFinalAmount() : 0.0;
        double originalPaid = sale.getPaidAmount() != null ? sale.getPaidAmount() : 0.0;

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
