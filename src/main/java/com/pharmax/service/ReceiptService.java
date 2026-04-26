package com.pharmax.service;

import com.pharmax.database.Repository.ReceiptRepository;
import com.pharmax.database.Repository.SaleRepository;
import com.pharmax.database.Repository.SaleReturnRepository;
import com.pharmax.model.Customer;
import com.pharmax.model.Receipt;
import com.pharmax.model.Sale;
import com.pharmax.model.SaleItem;
import com.pharmax.model.SaleReturn;
import com.pharmax.model.Voucher;
import com.pharmax.model.VoucherType;
import com.pharmax.model.dto.StatementItem;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
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
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

public class ReceiptService {
    private static final Logger logger = LoggerFactory.getLogger(ReceiptService.class);
    private final ReceiptRepository receiptRepository;
    private final SaleRepository saleRepository;
    private final SaleReturnRepository returnRepository;

    private static final String PREF_BANNER_PATH = "receipt.banner.path";
    private static final String PREF_LAST_RECEIPT_NUMBER = "receipt.last.number";

    // Company information
    private static final String APP_NAME = "PharmaX";
    private static final String COMPANY_WEBSITE = "Kervanjiholding.com";

    public ReceiptService() {
        this.receiptRepository = new ReceiptRepository();
        this.saleRepository = new SaleRepository();
        this.returnRepository = new SaleReturnRepository();
    }

    public File generateAccountStatementPdf(Customer customer,
            String projectLocation,
            LocalDate from,
            LocalDate to,
            boolean includeItems) {
        return generateAccountStatementPdf(customer, projectLocation, from, to, includeItems, null);
    }

    public File generateAccountStatementPdf(Customer customer,
            String projectLocation,
            LocalDate from,
            LocalDate to,
            boolean includeItems,
            String currency) {
        return generateAccountStatementPdf(customer, projectLocation, from, to, includeItems, currency, null);
    }

    public File generateAccountStatementPdf(Customer customer,
            String projectLocation,
            LocalDate from,
            LocalDate to,
            boolean includeItems,
            String currency,
            File outputFile) {
        if (customer == null || customer.getId() == null) {
            throw new IllegalArgumentException("العميل غير موجود");
        }

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.atTime(23, 59, 59) : null;
        List<Sale> sales;
        List<SaleReturn> returns;
        List<Voucher> vouchers;
        try {
            sales = saleRepository.findForAccountStatement(customer.getId(), projectLocation, fromDt, toDt,
                    includeItems);
            returns = returnRepository.findForAccountStatement(customer.getId(), projectLocation, fromDt, toDt);
            VoucherService voucherService = new VoucherService();
            vouchers = voucherService.getVouchersByCustomer(customer.getId());
            if (currency != null && !currency.isEmpty() && !"الكل".equals(currency)) {
                String selectedCurrency = currency;
                sales = sales.stream()
                        .filter(sale -> selectedCurrency.equals(sale.getCurrency()))
                        .toList();
                returns = returns.stream()
                        .filter(ret -> ret.getSale() != null && selectedCurrency.equals(ret.getSale().getCurrency()))
                        .toList();
                vouchers = vouchers.stream()
                        .filter(v -> selectedCurrency.equals(v.getCurrency()))
                        .toList();
            }
            vouchers = vouchers.stream()
                    .filter(v -> !Boolean.TRUE.equals(v.getIsCancelled()))
                    .toList();
        } catch (Exception e) {
            logger.error("Failed to load sales/returns/vouchers for statement", e);
            throw e;
        }

        try {
            byte[] pdfData = generateAccountStatementPDF(customer, projectLocation, from, to, sales, returns, vouchers,
                    includeItems, currency);

            File out = outputFile;
            if (out == null) {
                File dir = new File("statements");
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String safeCustomer = customer.getName() != null
                        ? customer.getName().replaceAll("[^\\p{L}\\p{N}]+", "_")
                        : "customer";
                String fileName = "statement_" + safeCustomer + "_" + datePart + ".pdf";
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
            logger.error("Failed to generate account statement PDF", e);
            throw new RuntimeException("فشل في إنشاء كشف الحساب", e);
        }
    }

    public File generateDetailedStatementPdf(Customer customer,
            List<StatementItem> items,
            String currency,
            LocalDate from,
            LocalDate to,
            File outputFile,
            String projectLocation) {
        if (customer == null || customer.getId() == null) {
            throw new IllegalArgumentException("العميل غير موجود");
        }

        try {
            byte[] pdfData = generateDetailedStatementPDFBytes(customer, items, currency, from, to, projectLocation);

            File out = outputFile;
            if (out == null) {
                File dir = new File("statements");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String safeCustomer = customer.getName() != null
                        ? customer.getName().replaceAll("[^\\p{L}\\p{N}]+", "_")
                        : "customer";
                String fileName = "statement_detailed_" + safeCustomer + "_" + datePart + ".pdf";
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
            logger.error("Failed to generate detailed statement PDF", e);
            throw new RuntimeException("فشل في إنشاء كشف الحساب التفصيلي", e);
        }
    }

    private byte[] generateDetailedStatementPDFBytes(Customer customer,
            List<StatementItem> items,
            String currency,
            LocalDate from,
            LocalDate to,
            String projectLocation) throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final float bannerTargetHeight = 120f;
        Document document = new Document(PageSize.A4.rotate(), 30, 30, 30 + bannerTargetHeight, 30);
        PdfWriter writer = PdfWriter.getInstance(document, baos);

        // Banner on every page
        try {
            final Image banner = loadBannerImage();
            if (banner != null) {
                writer.setPageEvent(new com.itextpdf.text.pdf.PdfPageEventHelper() {
                    @Override
                    public void onEndPage(PdfWriter w, Document d) {
                        try {
                            float pw = d.getPageSize().getWidth();
                            float ph = d.getPageSize().getHeight();
                            banner.scaleAbsolute(pw, bannerTargetHeight);
                            banner.setAbsolutePosition(0f, ph - bannerTargetHeight);
                            w.getDirectContent().addImage(banner);
                        } catch (Exception e) {}
                    }
                });
            }
        } catch (Exception e) {
            logger.warn("Failed to set banner event", e);
        }

        document.open();

        BaseFont baseFont = loadArabicBaseFont();
        Font arabicFont = new Font(baseFont, 9, Font.NORMAL);
        Font arabicBoldFont = new Font(baseFont, 10, Font.BOLD);
        Font sectionTitleFont = new Font(baseFont, 13, Font.BOLD);
        Font smallFont = new Font(baseFont, 8, Font.NORMAL);

        // Title
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        header.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        PdfPCell titleCell = new PdfPCell(new Phrase("كشف حساب تفصيلي", sectionTitleFont));
        titleCell.setBorder(PdfPCell.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        titleCell.setPaddingBottom(4f);
        header.addCell(titleCell);
        document.add(header);

        // Customer info
        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        info.setWidths(new float[] { 1.4f, 1f });
        info.setSpacingBefore(6f);
        info.setSpacingAfter(8f);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        leftCell.setPadding(8f);
        leftCell.addElement(
                new Phrase("اسم العميل: " + (customer.getName() != null ? customer.getName() : "-"), arabicFont));
        if (customer.getPhoneNumber() != null && !customer.getPhoneNumber().trim().isEmpty()) {
            leftCell.addElement(new Phrase("الهاتف: " + customer.getPhoneNumber(), arabicFont));
        }
        if (customer.getAddress() != null && !customer.getAddress().trim().isEmpty()) {
            leftCell.addElement(new Phrase("العنوان: " + customer.getAddress(), arabicFont));
        }
        if (projectLocation != null && !projectLocation.trim().isEmpty()) {
            leftCell.addElement(new Phrase("المشروع: " + projectLocation, arabicFont));
        } else {
            leftCell.addElement(new Phrase("المشروع: كل المشاريع", arabicFont));
        }
        info.addCell(leftCell);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        rightCell.setPadding(8f);
        String period;
        if (from != null && to != null) {
            period = from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " إلى "
                    + to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (from != null) {
            period = "من " + from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (to != null) {
            period = "إلى " + to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            period = "كل الفترات";
        }
        rightCell.addElement(new Phrase("الفترة: " + period, arabicFont));
        rightCell.addElement(new Phrase(
                "تاريخ الإصدار: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                arabicFont));
        if (currency != null && !currency.isEmpty()) {
            rightCell.addElement(new Phrase("العملة: " + currency, arabicFont));
        }
        info.addCell(rightCell);
        document.add(info);

        // Statement table — matches app details-mode columns
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.setSpacingBefore(5f);
        table.setSpacingAfter(10f);
        table.setWidths(new float[] { 1f, 0.8f, 1.6f, 0.7f, 1f, 1f, 1f });

        addTableHeader(table, "النوع", arabicBoldFont);
        addTableHeader(table, "رقم الفاتورة", arabicBoldFont);
        addTableHeader(table, "اسم المادة", arabicBoldFont);
        addTableHeader(table, "الكمية", arabicBoldFont);
        addTableHeader(table, "سعر القطعة", arabicBoldFont);
        addTableHeader(table, "المجموع", arabicBoldFont);
        addTableHeader(table, "مجموع الوصل", arabicBoldFont);

        double totalDebitIqd = 0, totalDebitUsd = 0;
        double totalCreditIqd = 0, totalCreditUsd = 0;
        double finalBalanceIqd = 0, finalBalanceUsd = 0;

        BaseColor saleColor = new BaseColor(239, 68, 68);
        BaseColor receiptColor = new BaseColor(34, 197, 94);
        BaseColor paymentColor = new BaseColor(245, 158, 11);
        BaseColor returnColor = new BaseColor(139, 92, 246);
        BaseColor openingColor = new BaseColor(100, 116, 139);
        BaseColor openingBg = new BaseColor(240, 242, 245);

        for (StatementItem item : items) {
            String type = item.getType() != null ? item.getType() : "";
            String ref = item.getReferenceNumber() != null ? item.getReferenceNumber() : "";
            String desc = item.getDescription() != null ? item.getDescription() : "";
            double debit = item.getDebit() != null ? item.getDebit() : 0;
            double credit = item.getCredit() != null ? item.getCredit() : 0;
            double balance = item.getBalance() != null ? item.getBalance() : 0;

            boolean isOpening = "رصيد سابق".equals(type);
            boolean isDetail = item.isDetailRow();

            // ── Detail (product sub-row) ──────────────────────────────────────
            if (isDetail) {
                boolean isSaleDetail = "مادة مبيعة".equals(type);
                BaseColor detailBg = isSaleDetail
                        ? new BaseColor(254, 242, 242) // very light red
                        : new BaseColor(245, 240, 255); // very light purple
                BaseColor detailFg = isSaleDetail
                        ? new BaseColor(220, 38, 38)
                        : new BaseColor(109, 40, 217);

                if (isSaleDetail && item.getSourceObject() instanceof com.pharmax.model.SaleItem saleItem) {
                    if (saleItem.getSale() != null && "PAID".equals(saleItem.getSale().getPaymentStatus())) {
                        detailBg = new BaseColor(240, 253, 244); // light green
                        detailFg = new BaseColor(34, 197, 94); // green
                    }
                }

                Font detailFont = new Font(baseFont, 8, Font.ITALIC, detailFg);
                Font detailBoldFont = new Font(baseFont, 8, Font.BOLDITALIC, detailFg);

                String productName = item.getProductName() != null ? item.getProductName() : "";
                Double qty = item.getItemQty();
                Double unitPrice = item.getItemUnitPrice();
                Double itemTotal = item.getItemTotal();
                String currSym = "دولار".equals(item.getCurrency()) ? " $" : " د.ع";

                // col0: النوع — empty for detail
                PdfPCell c0 = createBodyCell("", smallFont, Element.ALIGN_CENTER);
                c0.setBackgroundColor(detailBg);
                table.addCell(c0);
                // col1: رقم الفاتورة — empty for detail
                PdfPCell c1 = createBodyCell("", smallFont, Element.ALIGN_CENTER);
                c1.setBackgroundColor(detailBg);
                table.addCell(c1);
                // col2: اسم المادة
                PdfPCell c2 = createBodyCell(productName, detailFont, Element.ALIGN_CENTER);
                c2.setBackgroundColor(detailBg);
                table.addCell(c2);
                // col3: الكمية
                PdfPCell c3 = createBodyCell(qty != null ? String.format("%.0f", qty) : "", detailFont,
                        Element.ALIGN_CENTER);
                c3.setBackgroundColor(detailBg);
                table.addCell(c3);
                // col4: س. الوحدة
                PdfPCell c4 = createBodyCell(
                        unitPrice != null && unitPrice > 0 ? formatAmount(unitPrice) + currSym : "", detailFont,
                        Element.ALIGN_CENTER);
                c4.setBackgroundColor(detailBg);
                table.addCell(c4);
                // col5: المجموع
                PdfPCell c5 = createBodyCell(
                        itemTotal != null && itemTotal > 0 ? formatAmount(itemTotal) + currSym : "", detailBoldFont,
                        Element.ALIGN_CENTER);
                c5.setBackgroundColor(detailBg);
                table.addCell(c5);
                // col6: الرصيد — empty for detail
                PdfPCell c6 = createBodyCell("", smallFont, Element.ALIGN_CENTER);
                c6.setBackgroundColor(detailBg);
                table.addCell(c6);

                continue;
            }
            BaseColor typeColor;
            switch (type) {
                case "فاتورة مبيع" -> typeColor = saleColor;
                case "تسديد فاتورة" -> typeColor = receiptColor;
                case "سند قبض" -> typeColor = receiptColor;
                case "سند صرف" -> typeColor = paymentColor;
                case "مرتجع مبيعات" -> typeColor = returnColor;
                case "رصيد سابق" -> typeColor = openingColor;
                default -> typeColor = BaseColor.BLACK;
            }

            BaseColor specificRowColor = null;
            if (item.getSourceObject() instanceof com.pharmax.model.Sale sale) {
                if ("PAID".equals(sale.getPaymentStatus())) {
                    specificRowColor = receiptColor; // Green
                } else {
                    specificRowColor = saleColor; // Red
                }
            }

            Font typeFont = specificRowColor != null ? new Font(baseFont, 9, Font.BOLD, specificRowColor) : new Font(baseFont, 9, Font.BOLD, typeColor);
            Font regularRowFont = specificRowColor != null ? new Font(baseFont, 9, Font.NORMAL, specificRowColor) : arabicFont;

            // col0: النوع
            PdfPCell typeCell = new PdfPCell(new Phrase(type, typeFont));
            typeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            typeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            typeCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            typeCell.setPadding(4f);
            if (isOpening)
                typeCell.setBackgroundColor(openingBg);
            table.addCell(typeCell);

            // col1: رقم الفاتورة
            PdfPCell refCell = createBodyCell(ref, regularRowFont, Element.ALIGN_CENTER);
            if (isOpening)
                refCell.setBackgroundColor(openingBg);
            table.addCell(refCell);

            // col2: اسم المادة — shows description/notes for parent rows
            PdfPCell descCell = createBodyCell(desc, regularRowFont, Element.ALIGN_CENTER);
            if (isOpening)
                descCell.setBackgroundColor(openingBg);
            table.addCell(descCell);

            // col3: الكمية — shows payment status for parent rows
            String payStatusText = "";
            PdfPCell qtyCell = createBodyCell(payStatusText, regularRowFont, Element.ALIGN_CENTER);
            if (isOpening)
                qtyCell.setBackgroundColor(openingBg);
            table.addCell(qtyCell);

            // col4: س. الوحدة — shows debit for parent rows (سعر القطعة)
            PdfPCell debitCell;
            if (debit > 0) {
                Font debitFont = specificRowColor != null ? regularRowFont : new Font(baseFont, 9, Font.NORMAL, saleColor);
                debitCell = new PdfPCell(new Phrase(formatAmount(debit), debitFont));
            } else {
                debitCell = new PdfPCell(new Phrase("", regularRowFont));
            }
            debitCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            debitCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            debitCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            debitCell.setPadding(4f);
            if (isOpening)
                debitCell.setBackgroundColor(openingBg);
            table.addCell(debitCell);

            // col5: المجموع — shows credit for parent rows
            PdfPCell creditCell;
            if (credit > 0) {
                Font creditFont = specificRowColor != null ? regularRowFont : new Font(baseFont, 9, Font.NORMAL, receiptColor);
                creditCell = new PdfPCell(new Phrase(formatAmount(credit), creditFont));
            } else {
                creditCell = new PdfPCell(new Phrase("", regularRowFont));
            }
            creditCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            creditCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            creditCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            creditCell.setPadding(4f);
            if (isOpening)
                creditCell.setBackgroundColor(openingBg);
            table.addCell(creditCell);

            // col6: الرصيد
            BaseColor balColor = balance > 0 ? saleColor : (balance < 0 ? receiptColor : BaseColor.BLACK);
            Font balFont = specificRowColor != null ? new Font(baseFont, 9, Font.BOLD, specificRowColor) : new Font(baseFont, 9, Font.BOLD, balColor);
            PdfPCell balCell = new PdfPCell(new Phrase(formatAmount(balance), balFont));
            balCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            balCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            balCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            balCell.setPadding(4f);
            if (isOpening)
                balCell.setBackgroundColor(openingBg);
            table.addCell(balCell);

            // Accumulate totals (skip opening balance)
            if (!isOpening) {
                if ("دولار".equals(item.getCurrency())) {
                    totalDebitUsd += debit;
                    totalCreditUsd += credit;
                } else {
                    totalDebitIqd += debit;
                    totalCreditIqd += credit;
                }
            }
            if ("دولار".equals(item.getCurrency())) {
                finalBalanceUsd = balance;
            } else {
                finalBalanceIqd = balance;
            }
        }

        document.add(table);

        // Summary footer (3 columns — no عدد الحركات)
        PdfPTable summaryTable = new PdfPTable(3);
        summaryTable.setWidthPercentage(100);
        summaryTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        summaryTable.setSpacingBefore(6f);
        summaryTable.setSpacingAfter(10f);

        PdfPCell sdCell = new PdfPCell(
                new Phrase("إجمالي مدين\n" + formatDualCurrencyStr(totalDebitIqd, totalDebitUsd), arabicBoldFont));
        sdCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        sdCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        sdCell.setPadding(8f);
        sdCell.setBackgroundColor(new BaseColor(254, 226, 226));
        summaryTable.addCell(sdCell);

        PdfPCell scCell = new PdfPCell(
                new Phrase("اجمالي المدفوع\n" + formatDualCurrencyStr(totalCreditIqd, totalCreditUsd), arabicBoldFont));
        scCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        scCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        scCell.setPadding(8f);
        scCell.setBackgroundColor(new BaseColor(220, 252, 231));
        summaryTable.addCell(scCell);

        PdfPCell sbCell = new PdfPCell(
                new Phrase("الرصيد النهائي\n" + formatDualCurrencyStr(finalBalanceIqd, finalBalanceUsd),
                        arabicBoldFont));
        sbCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        sbCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        sbCell.setPadding(8f);
        summaryTable.addCell(sbCell);

        document.add(summaryTable);

        addUnifiedFooter(document, arabicBoldFont, smallFont);

        document.close();
        return baos.toByteArray();
    }

    private String formatDualCurrencyStr(double iqd, double usd) {
        StringBuilder sb = new StringBuilder();
        if (iqd != 0 || usd == 0) {
            sb.append(formatAmount(iqd)).append(" د.ع");
        }
        if (usd != 0) {
            if (sb.length() > 0)
                sb.append(" | ");
            sb.append(formatAmount(usd)).append(" $");
        }
        return sb.toString();
    }

    private Receipt prepareReceiptForSale(Sale sale, String template, String printedBy) {
        List<Receipt> existingReceipts = receiptRepository.findBySaleId(sale.getId());
        Receipt receipt;

        if (existingReceipts.isEmpty()) {
            receipt = new Receipt();
            receipt.setReceiptNumber(generateReceiptNumber());
            receipt.setSale(sale);
        } else {
            // keep the latest receipt and delete older duplicates
            receipt = existingReceipts.get(existingReceipts.size() - 1);
            for (int i = 0; i < existingReceipts.size() - 1; i++) {
                Receipt duplicate = existingReceipts.get(i);
                deleteReceiptSafely(duplicate);
            }
        }

        receipt.setTemplate(template != null ? template : "DEFAULT");
        receipt.setPrintedBy(printedBy);
        receipt.setReceiptDate(LocalDateTime.now());
        receipt.setUpdatedAt(LocalDateTime.now());
        return receipt;
    }

    public void ensureSingleReceiptPerSale() {
        List<Long> saleIds = receiptRepository.findSaleIdsWithMultipleReceipts();
        for (Long saleId : saleIds) {
            List<Receipt> receipts = receiptRepository.findBySaleId(saleId);
            if (receipts.isEmpty()) {
                continue;
            }
            Receipt keep = receipts.get(receipts.size() - 1);
            for (int i = 0; i < receipts.size() - 1; i++) {
                Receipt duplicate = receipts.get(i);
                if (!duplicate.getId().equals(keep.getId())) {
                    deleteReceiptSafely(duplicate);
                }
            }
        }
    }

    private void deleteReceiptSafely(Receipt receipt) {
        try {
            if (receipt.getFilePath() != null) {
                java.io.File pdf = new java.io.File(receipt.getFilePath());
                if (pdf.exists()) {
                    pdf.delete();
                }
            }
            receiptRepository.delete(receipt);
        } catch (Exception e) {
            logger.warn("Failed to delete duplicate receipt {}", receipt.getId(), e);
        }
    }

    public Receipt generateReceipt(Long saleId, String template, String printedBy) {
        logger.info("Generating receipt for sale: {}", saleId);

        Optional<Sale> saleOpt = saleRepository.findByIdWithDetails(saleId);
        if (saleOpt.isEmpty()) {
            throw new IllegalArgumentException("البيع غير موجود");
        }

        Sale sale = saleOpt.get();
        Receipt receipt = prepareReceiptForSale(sale, template, printedBy);

        // Generate PDF
        try {
            byte[] pdfData = generateReceiptPDF(sale, receipt.getTemplate());

            // Ensure receipts directory exists
            java.io.File receiptsDir = new java.io.File("receipts");
            if (!receiptsDir.exists()) {
                receiptsDir.mkdirs();
            }

            // Save PDF file
            String fileName = "receipt_" + receipt.getReceiptNumber() + ".pdf";
            String filePath = "receipts/" + fileName;

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(pdfData);
            }

            receipt.setFilePath(filePath);
            receipt.setIsPrinted(true);
            receipt.setPrintedAt(LocalDateTime.now());

            Receipt savedReceipt = receiptRepository.save(receipt);
            logger.info("Receipt generated successfully: {}", savedReceipt.getReceiptNumber());

            return savedReceipt;

        } catch (Exception e) {
            logger.error("Failed to generate receipt PDF", e);
            throw new RuntimeException("فشل في إنشاء الإيصال", e);
        }
    }

    public Receipt regenerateReceiptPdf(Long receiptId, String printedBy) {
        Optional<Receipt> receiptOpt = receiptRepository.findByIdWithDetails(receiptId);
        if (receiptOpt.isEmpty()) {
            throw new IllegalArgumentException("الوصل غير موجود");
        }

        Receipt receipt = receiptOpt.get();
        if (receipt.getSale() == null) {
            throw new IllegalStateException("لا توجد فاتورة مرتبطة بهذا الوصل");
        }

        try {
            byte[] pdfData = generateReceiptPDF(receipt.getSale(), receipt.getTemplate());

            java.io.File receiptsDir = new java.io.File("receipts");
            if (!receiptsDir.exists()) {
                receiptsDir.mkdirs();
            }

            String fileName = "receipt_" + receipt.getReceiptNumber() + ".pdf";
            String filePath = "receipts/" + fileName;

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(pdfData);
            }

            receipt.setFilePath(filePath);
            receipt.setIsPrinted(true);
            receipt.setPrintedAt(LocalDateTime.now());
            receipt.setPrintedBy(printedBy);

            Receipt savedReceipt = receiptRepository.save(receipt);
            logger.info("Receipt PDF regenerated successfully: {}", savedReceipt.getReceiptNumber());
            return savedReceipt;
        } catch (Exception e) {
            logger.error("Failed to regenerate receipt PDF", e);
            throw new RuntimeException("فشل في إعادة إنشاء الوصل", e);
        }
    }

    private byte[] generateReceiptPDF(Sale sale, String template) throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final float bannerTargetHeight = 120f;
        Document document = new Document(PageSize.A4, 30, 30, 30 + bannerTargetHeight, 30);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        BaseFont baseFont = loadArabicBaseFont();
        Font arabicFont = new Font(baseFont, 10, Font.NORMAL);
        Font arabicBoldFont = new Font(baseFont, 11, Font.BOLD);
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

        document.add(new Paragraph(" ", arabicFont));

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        infoTable.setWidths(new float[] { 1.5f, 1 });
        infoTable.setSpacingBefore(5f);
        infoTable.setSpacingAfter(10f);

        String customerName = sale.getCustomer() != null && sale.getCustomer().getName() != null
                ? sale.getCustomer().getName()
                : "-";
        String customerPhone = sale.getCustomer() != null ? sale.getCustomer().getPhoneNumber() : null;
        String customerAddress = sale.getCustomer() != null ? sale.getCustomer().getAddress() : null;

        PdfPCell customerCell = new PdfPCell();
        customerCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        customerCell.setPadding(8f);
        customerCell.addElement(new Phrase("اسم العميل: " + customerName, arabicFont));
        if (customerPhone != null && !customerPhone.trim().isEmpty()) {
            customerCell.addElement(new Phrase("الهاتف: " + customerPhone, arabicFont));
        }
        if (customerAddress != null && !customerAddress.trim().isEmpty()) {
            customerCell.addElement(new Phrase("العنوان: " + customerAddress, arabicFont));
        }
        if (sale.getProjectLocation() != null && !sale.getProjectLocation().trim().isEmpty()) {
            customerCell.addElement(new Phrase("موقع المشروع: " + sale.getProjectLocation(), arabicFont));
        }
        infoTable.addCell(customerCell);

        PdfPCell invoiceCell = new PdfPCell();
        invoiceCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        invoiceCell.setPadding(8f);
        invoiceCell.setBackgroundColor(new BaseColor(255, 182, 193));
        invoiceCell.addElement(new Phrase("فاتورة بيع/أجل", arabicBoldFont));
        invoiceCell.addElement(new Phrase(" ", smallFont));
        invoiceCell.addElement(
                new Phrase("رقم الفاتورة: " + (sale.getSaleCode() != null ? sale.getSaleCode() : "-"), arabicFont));
        invoiceCell.addElement(new Phrase(
                "التاريخ والوقت: " + (sale.getSaleDate() != null
                        ? sale.getSaleDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : "-"),
                arabicFont));
        infoTable.addCell(invoiceCell);

        document.add(infoTable);

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.setWidths(new float[] { 1f, 1f, 1f, 0.6f, 1f, 0.8f, 0.8f, 0.2f });
        table.setSpacingBefore(5f);
        table.setSpacingAfter(10f);

        addTableHeader(table, "ت", arabicBoldFont);
        addTableHeader(table, "المادة", arabicBoldFont);
        addTableHeader(table, "العدد", arabicBoldFont);
        addTableHeader(table, "التعبئة", arabicBoldFont);
        addTableHeader(table, "السعر", arabicBoldFont);
        addTableHeader(table, "نوع السعر", arabicBoldFont);
        addTableHeader(table, "السعر بعد الخصم", arabicBoldFont);
        addTableHeader(table, "المجموع ع", arabicBoldFont);

        String saleCurrency = sale.getCurrency() != null ? sale.getCurrency() : "دينار";
        List<SaleItem> items = sale.getSaleItems() != null ? sale.getSaleItems() : Collections.emptyList();
        int rowNo = 1;
        for (SaleItem item : items) {
            String productName = item.getProduct() != null && item.getProduct().getName() != null
                    ? item.getProduct().getName()
                    : "-";
            String unitOfMeasure = item.getProduct() != null && item.getProduct().getUnitOfMeasure() != null
                    ? item.getProduct().getUnitOfMeasure()
                    : "-";

            table.addCell(createBodyCell(String.valueOf(rowNo), arabicFont, Element.ALIGN_CENTER));
            table.addCell(createBodyCell(productName, arabicFont, Element.ALIGN_RIGHT));
            table.addCell(createBodyCell(item.getQuantity() != null ? String.valueOf(item.getQuantity()) : "0",
                    arabicFont, Element.ALIGN_CENTER));
            table.addCell(createBodyCell(unitOfMeasure, arabicFont, Element.ALIGN_CENTER));
            table.addCell(createBodyCell(formatCurrency(item.getUnitPrice(), saleCurrency), arabicFont, Element.ALIGN_CENTER));
            table.addCell(createBodyCell(item.getPriceType() != null ? item.getPriceType() : "مفرد", arabicFont,
                    Element.ALIGN_CENTER));
            Double quantity = item.getQuantity() != null ? item.getQuantity() : 0.0;
            Double discountAmount = item.getDiscountAmount() != null ? item.getDiscountAmount() : 0.0;
            double discountPerUnit = quantity > 0 ? discountAmount / quantity : 0.0;
            double unitPriceAfterDiscount = (item.getUnitPrice() != null ? item.getUnitPrice() : 0.0) - discountPerUnit;
            table.addCell(createBodyCell(formatCurrency(unitPriceAfterDiscount, saleCurrency), arabicFont, Element.ALIGN_CENTER));
            table.addCell(createBodyCell(formatCurrency(item.getTotalPrice(), saleCurrency), arabicFont, Element.ALIGN_CENTER));
            rowNo++;
        }

        document.add(table);

        PdfPTable totalsTable = new PdfPTable(2);
        totalsTable.setWidthPercentage(100);
        totalsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        totalsTable.setWidths(new float[] { 1, 1 });
        totalsTable.setSpacingBefore(5f);
        totalsTable.setSpacingAfter(10f);

        PdfPCell notesCell = new PdfPCell();
        notesCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        notesCell.setPadding(8f);
        notesCell.setMinimumHeight(60f);
        notesCell.addElement(new Phrase("عدد المواد: " + (sale.getSaleItems() != null ? sale.getSaleItems().size() : 0),
                arabicFont));
        if (sale.getNotes() != null && !sale.getNotes().trim().isEmpty()) {
            notesCell.addElement(new Phrase("ملاحظات: " + sale.getNotes(), arabicFont));
        }
        totalsTable.addCell(notesCell);

        PdfPCell summaryCell = new PdfPCell();
        summaryCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        summaryCell.setPadding(8f);

        PdfPTable innerTotals = new PdfPTable(2);
        innerTotals.setWidthPercentage(100);
        innerTotals.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        addTotalRowBordered(innerTotals, "المجموع", formatCurrency(sale.getTotalAmount(), saleCurrency), arabicFont,
                arabicBoldFont);
        double itemsDiscount = items.stream()
                .mapToDouble(item -> item.getDiscountAmount() != null ? item.getDiscountAmount() : 0.0)
                .sum();
        if (itemsDiscount > 0) {
            addTotalRowBordered(innerTotals, "خصم المواد", formatCurrency(itemsDiscount, saleCurrency), arabicFont,
                    arabicBoldFont);
        }
        if (sale.getDiscountAmount() != null && sale.getDiscountAmount() > 0) {
            addTotalRowBordered(innerTotals, "خصم إضافي", formatCurrency(sale.getDiscountAmount(), saleCurrency),
                    arabicFont, arabicBoldFont);
        }
        addTotalRowBordered(innerTotals, "الإجمالي", formatCurrency(sale.getFinalAmount(), saleCurrency),
                arabicBoldFont, arabicBoldFont);

        Double paid = sale.getPaidAmount() != null ? sale.getPaidAmount() : 0.0;
        if (paid > 0) {
            addTotalRowBordered(innerTotals, "المدفوع", formatCurrency(paid, saleCurrency), arabicFont, arabicBoldFont);
            Double finalAmount = sale.getFinalAmount() != null ? sale.getFinalAmount() : 0.0;
            addTotalRowBordered(innerTotals, "المتبقي", formatCurrency(finalAmount - paid, saleCurrency), arabicFont,
                    arabicBoldFont);
        }

        summaryCell.addElement(innerTotals);
        totalsTable.addCell(summaryCell);

        document.add(totalsTable);

        PdfPTable signatureTable = new PdfPTable(2);
        signatureTable.setWidthPercentage(100);
        signatureTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signatureTable.setSpacingBefore(10f);
        signatureTable.setSpacingAfter(10f);

        PdfPCell paymentCell = new PdfPCell();
        paymentCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        paymentCell.setPadding(8f);
        paymentCell.setMinimumHeight(50f);
        paymentCell
                .addElement(new Phrase("طريقة الدفع: " + getPaymentMethodArabic(sale.getPaymentMethod()), arabicFont));
        paymentCell.addElement(new Phrase("الحالة: " + getPaymentStatusArabic(sale.getPaymentStatus()), arabicFont));
        signatureTable.addCell(paymentCell);

        PdfPCell signCell = new PdfPCell();
        signCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signCell.setPadding(8f);
        signCell.setMinimumHeight(50f);
        signCell.addElement(new Phrase("التوقيع: ", arabicFont));
        signatureTable.addCell(signCell);

        document.add(signatureTable);

        addUnifiedFooter(document, arabicBoldFont, smallFont);

        document.close();

        return baos.toByteArray();
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
            String bannerPath = prefs.get(PREF_BANNER_PATH, null);
            if (bannerPath != null && !bannerPath.trim().isEmpty()) {
                java.io.File f = new java.io.File(bannerPath);
                if (f.exists() && f.isFile()) {
                    return Image.getInstance(f.getAbsolutePath());
                }
            }

            URL logoUrl = ReceiptService.class.getResource("/templates/PharmaX.png");
            if (logoUrl != null) {
                return Image.getInstance(logoUrl);
            }
        } catch (Exception e) {
            logger.warn("Failed to load banner image", e);
        }
        return null;
    }

    private byte[] generateAccountStatementPDF(Customer customer,
            String projectLocation,
            LocalDate from,
            LocalDate to,
            List<Sale> sales,
            List<SaleReturn> returns,
            List<Voucher> vouchers,
            boolean includeItems,
            String currency) throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final float bannerTargetHeight = 120f;
        Document document = new Document(PageSize.A4, 30, 30, 30 + bannerTargetHeight, 30);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        BaseFont baseFont = loadArabicBaseFont();
        Font arabicFont = new Font(baseFont, 10, Font.NORMAL);
        Font arabicBoldFont = new Font(baseFont, 11, Font.BOLD);
        Font sectionTitleFont = new Font(baseFont, 12, Font.BOLD);
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

        PdfPCell titleCell = new PdfPCell(new Phrase("كشف حساب", sectionTitleFont));
        titleCell.setBorder(PdfPCell.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        titleCell.setPaddingBottom(4f);
        header.addCell(titleCell);

        document.add(header);

        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        info.setWidths(new float[] { 1.4f, 1f });
        info.setSpacingBefore(6f);
        info.setSpacingAfter(8f);

        PdfPCell left = new PdfPCell();
        left.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        left.setPadding(8f);
        left.addElement(
                new Phrase("اسم العميل: " + (customer.getName() != null ? customer.getName() : "-"), arabicFont));
        if (customer.getPhoneNumber() != null && !customer.getPhoneNumber().trim().isEmpty()) {
            left.addElement(new Phrase("الهاتف: " + customer.getPhoneNumber(), arabicFont));
        }
        if (projectLocation != null && !projectLocation.trim().isEmpty()) {
            left.addElement(new Phrase("المشروع: " + projectLocation, arabicFont));
        }
        info.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        right.setPadding(8f);
        String period;
        if (from != null && to != null) {
            period = from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " إلى "
                    + to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (from != null) {
            period = "من " + from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (to != null) {
            period = "إلى " + to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            period = "كل الفترات";
        }
        right.addElement(new Phrase("الفترة: " + period, arabicFont));
        right.addElement(new Phrase(
                "تاريخ الإصدار: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                arabicFont));
        if (currency != null && !currency.isEmpty()) {
            right.addElement(new Phrase("العملة: " + currency, arabicFont));
        }
        info.addCell(right);

        document.add(info);

        double totalFinal = 0.0;
        double totalPaid = 0.0;
        double totalReturns = 0.0;
        double totalFinalIqd = 0.0;
        double totalPaidIqd = 0.0;
        double totalReturnsIqd = 0.0;
        double totalFinalUsd = 0.0;
        double totalPaidUsd = 0.0;
        double totalReturnsUsd = 0.0;
        for (Sale s : sales) {
            double finalAmount = s.getFinalAmount() != null ? s.getFinalAmount() : 0.0;
            double paidAmount = s.getPaidAmount() != null ? s.getPaidAmount() : 0.0;
            totalFinal += finalAmount;
            totalPaid += paidAmount;
            if (currency == null || currency.isEmpty() || "الكل".equals(currency)) {
                if ("دولار".equals(s.getCurrency())) {
                    totalFinalUsd += finalAmount;
                    totalPaidUsd += paidAmount;
                } else {
                    totalFinalIqd += finalAmount;
                    totalPaidIqd += paidAmount;
                }
            }
        }
        for (SaleReturn r : returns) {
            double returnAmount = r.getTotalReturnAmount() != null ? r.getTotalReturnAmount() : 0.0;
            totalReturns += returnAmount;
            if (currency == null || currency.isEmpty() || "الكل".equals(currency)) {
                String retCurrency = r.getSale() != null ? r.getSale().getCurrency() : null;
                if ("دولار".equals(retCurrency)) {
                    totalReturnsUsd += returnAmount;
                } else {
                    totalReturnsIqd += returnAmount;
                }
            }
        }
        // Include receipt vouchers in total paid
        for (Voucher v : vouchers) {
            if (v.getVoucherType() == VoucherType.RECEIPT) {
                double vAmount = v.getAmount() != null ? v.getAmount() : 0.0;
                totalPaid += vAmount;
                if (currency == null || currency.isEmpty() || "الكل".equals(currency)) {
                    if ("دولار".equals(v.getCurrency())) {
                        totalPaidUsd += vAmount;
                    } else {
                        totalPaidIqd += vAmount;
                    }
                }
            }
        }
        double totalRemaining = totalFinal - totalPaid - totalReturns;
        double totalRemainingIqd = totalFinalIqd - totalPaidIqd - totalReturnsIqd;
        double totalRemainingUsd = totalFinalUsd - totalPaidUsd - totalReturnsUsd;

        double netSales = totalFinal - totalReturns;
        double netSalesIqd = totalFinalIqd - totalReturnsIqd;
        double netSalesUsd = totalFinalUsd - totalReturnsUsd;

        Map<Long, Double> returnsBySaleId = new HashMap<>();
        for (SaleReturn r : returns) {
            if (r.getSale() == null || r.getSale().getId() == null) {
                continue;
            }
            double amount = r.getTotalReturnAmount() != null ? r.getTotalReturnAmount() : 0.0;
            returnsBySaleId.merge(r.getSale().getId(), amount, (a, b) -> (a != null ? a : 0.0) + (b != null ? b : 0.0));
        }

        PdfPTable summary = new PdfPTable(3);
        summary.setWidthPercentage(100);
        summary.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        summary.setSpacingBefore(6f);
        summary.setSpacingAfter(10f);
        summary.setWidths(new float[] { 1f, 1f, 1f });

        String s1Text = (currency == null || currency.isEmpty() || "الكل".equals(currency))
                ? formatDualCurrencyStr(netSalesIqd, netSalesUsd)
                : formatCurrency(netSales, currency);
        PdfPCell s1 = new PdfPCell(new Phrase("إجمالي البيع\n" + s1Text, arabicBoldFont));
        s1.setHorizontalAlignment(Element.ALIGN_CENTER);
        s1.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        s1.setPadding(8f);
        summary.addCell(s1);

        String s2Text = (currency == null || currency.isEmpty() || "الكل".equals(currency))
                ? formatDualCurrencyStr(totalPaidIqd, totalPaidUsd)
                : formatCurrency(totalPaid, currency);
        PdfPCell s2 = new PdfPCell(new Phrase("إجمالي المدفوع\n" + s2Text, arabicBoldFont));
        s2.setHorizontalAlignment(Element.ALIGN_CENTER);
        s2.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        s2.setPadding(8f);
        summary.addCell(s2);

        String s3Text = (currency == null || currency.isEmpty() || "الكل".equals(currency))
                ? formatDualCurrencyStr(totalRemainingIqd, totalRemainingUsd)
                : formatCurrency(totalRemaining, currency);
        PdfPCell s3 = new PdfPCell(new Phrase("المطلوب للدفع لهذا المشروع\n" + s3Text, arabicBoldFont));
        s3.setHorizontalAlignment(Element.ALIGN_CENTER);
        s3.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        s3.setPadding(8f);
        summary.addCell(s3);

        document.add(summary);

        // ===== Sales Table =====
        PdfPTable salesTable = new PdfPTable(6);
        salesTable.setWidthPercentage(100);
        salesTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        salesTable.setSpacingBefore(5f);
        salesTable.setSpacingAfter(10f);
        salesTable.setWidths(new float[] { 1f, 1f, 1.2f, 1f, 1f, 0.2f });

        addTableHeader(salesTable, "ت", arabicBoldFont);
        addTableHeader(salesTable, "رقم الفاتورة", arabicBoldFont);
        addTableHeader(salesTable, "التاريخ", arabicBoldFont);
        addTableHeader(salesTable, "المشروع", arabicBoldFont);
        addTableHeader(salesTable, "الإجمالي", arabicBoldFont);
        addTableHeader(salesTable, "المدفوع/الدين", arabicBoldFont);

        Font detailFont = new Font(baseFont, 8, Font.NORMAL, new BaseColor(80, 80, 80));
        BaseColor detailBg = new BaseColor(245, 247, 250);

        int row = 1;
        for (Sale s : sales) {
            String code = s.getSaleCode() != null ? s.getSaleCode() : "-";
            String date = s.getSaleDate() != null
                    ? s.getSaleDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    : "-";
            String proj = s.getProjectLocation() != null ? s.getProjectLocation() : "-";
            double fin = s.getFinalAmount() != null ? s.getFinalAmount() : 0.0;
            double paid = s.getPaidAmount() != null ? s.getPaidAmount() : 0.0;
            double returnsForSale = returnsBySaleId.getOrDefault(s.getId(), 0.0);
            double rem = fin - paid - returnsForSale;
            String rowCurrency = (currency == null || currency.isEmpty() || "الكل".equals(currency))
                    ? ("دولار".equals(s.getCurrency()) ? "دولار" : "دينار")
                    : currency;
            String paidDebtDisplay = rem != 0
                    ? formatCurrency(paid, rowCurrency) + " / " + formatCurrency(rem, rowCurrency)
                    : formatCurrency(paid, rowCurrency);

            salesTable.addCell(createBodyCell(String.valueOf(row), arabicFont, Element.ALIGN_CENTER));
            salesTable.addCell(createBodyCell(code, arabicFont, Element.ALIGN_CENTER));
            salesTable.addCell(createBodyCell(date, arabicFont, Element.ALIGN_CENTER));
            salesTable.addCell(createBodyCell(proj, arabicFont, Element.ALIGN_CENTER));
            salesTable.addCell(createBodyCell(formatCurrency(fin, rowCurrency), arabicFont, Element.ALIGN_CENTER));
            salesTable.addCell(createBodyCell(paidDebtDisplay, arabicFont, Element.ALIGN_CENTER));

            // Detail sub-rows when includeItems is true
            if (includeItems) {
                StringBuilder detailText = new StringBuilder();

                // Sale items
                List<SaleItem> saleItemsList = s.getSaleItems() != null ? s.getSaleItems() : Collections.emptyList();
                if (!saleItemsList.isEmpty()) {
                    detailText.append("━━ المواد ━━\n");
                    int idx = 1;
                    for (SaleItem si : saleItemsList) {
                        String productName = si.getProduct() != null && si.getProduct().getName() != null
                                ? si.getProduct().getName()
                                : "منتج";
                        detailText.append(idx++).append(". ").append(productName);
                        detailText.append("  |  الكمية: ").append(formatAmount(si.getQuantity()));
                        detailText.append(" × ").append(formatAmount(si.getUnitPrice()));
                        detailText.append(" = ").append(formatAmount(si.getTotalPrice()));
                        if (si.getDiscountAmount() != null && si.getDiscountAmount() > 0) {
                            detailText.append("  (خصم: ").append(formatAmount(si.getDiscountAmount())).append(")");
                        }
                        detailText.append("\n");
                    }
                }

                // Financial summary
                detailText.append("━━ ملخص مالي ━━\n");
                detailText.append("المجموع: ").append(formatCurrency(s.getTotalAmount(), rowCurrency));
                if (s.getDiscountAmount() != null && s.getDiscountAmount() > 0) {
                    detailText.append("  |  الخصم: ").append(formatCurrency(s.getDiscountAmount(), rowCurrency));
                }
                if (s.getTaxAmount() != null && s.getTaxAmount() > 0) {
                    detailText.append("  |  الضريبة: ").append(formatCurrency(s.getTaxAmount(), rowCurrency));
                }
                detailText.append("  |  الصافي: ").append(formatCurrency(s.getFinalAmount(), rowCurrency)).append("\n");

                // Payment info
                detailText.append("━━ الدفع ━━\n");
                detailText.append("الحالة: ").append("PAID".equals(s.getPaymentStatus()) ? "مدفوع ✓" : "قبل دفع ✗");
                if (s.getPaidAmount() != null && s.getPaidAmount() > 0) {
                    detailText.append("  |  المدفوع: ").append(formatCurrency(s.getPaidAmount(), rowCurrency));
                    double remaining = fin - s.getPaidAmount();
                    if (remaining > 0) {
                        detailText.append("  |  المتبقي: ").append(formatCurrency(remaining, rowCurrency));
                    }
                }
                if (s.getPaymentMethod() != null && !s.getPaymentMethod().isEmpty()) {
                    detailText.append("  |  طريقة الدفع: ").append(getPaymentMethodArabic(s.getPaymentMethod()));
                }
                detailText.append("\n");

                if (s.getCreatedBy() != null) {
                    detailText.append("بواسطة: ").append(s.getCreatedBy());
                }
                if (s.getNotes() != null && !s.getNotes().isEmpty()) {
                    detailText.append("  |  ملاحظات: ").append(s.getNotes());
                }

                PdfPCell detailCell = new PdfPCell(new Phrase(detailText.toString().trim(), detailFont));
                detailCell.setColspan(6);
                detailCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                detailCell.setPadding(6f);
                detailCell.setBackgroundColor(detailBg);
                detailCell.setBorderWidthTop(0f);
                salesTable.addCell(detailCell);
            }

            row++;
        }

        document.add(salesTable);

        // ===== Returns Section =====
        if (!returns.isEmpty()) {
            PdfPTable returnsHeader = new PdfPTable(1);
            returnsHeader.setWidthPercentage(100);
            returnsHeader.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            returnsHeader.setSpacingBefore(10f);

            PdfPCell returnsTitleCell = new PdfPCell(new Phrase("المرتجعات", sectionTitleFont));
            returnsTitleCell.setBorder(PdfPCell.NO_BORDER);
            returnsTitleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            returnsTitleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            returnsTitleCell.setPaddingBottom(4f);
            returnsTitleCell.setBackgroundColor(new BaseColor(255, 230, 230));
            returnsHeader.addCell(returnsTitleCell);
            document.add(returnsHeader);

            PdfPTable returnsTable = new PdfPTable(6);
            returnsTable.setWidthPercentage(100);
            returnsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            returnsTable.setSpacingBefore(5f);
            returnsTable.setSpacingAfter(10f);
            returnsTable.setWidths(new float[] { 1f, 1f, 1.2f, 1f, 1f, 0.2f });

            addTableHeader(returnsTable, "ت", arabicBoldFont);
            addTableHeader(returnsTable, "رقم الفاتورة", arabicBoldFont);
            addTableHeader(returnsTable, "التاريخ", arabicBoldFont);
            addTableHeader(returnsTable, "المشروع", arabicBoldFont);
            addTableHeader(returnsTable, "الإجمالي", arabicBoldFont);
            addTableHeader(returnsTable, "السبب", arabicBoldFont);

            int retRow = 1;
            for (SaleReturn r : returns) {
                String saleCode = r.getSale() != null && r.getSale().getSaleCode() != null ? r.getSale().getSaleCode()
                        : "-";
                String retDate = r.getReturnDate() != null
                        ? r.getReturnDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : "-";
                String projectName = r.getSale() != null && r.getSale().getProjectLocation() != null
                        ? r.getSale().getProjectLocation()
                        : "-";
                double retAmount = r.getTotalReturnAmount() != null ? r.getTotalReturnAmount() : 0.0;
                String returnCurrency = (currency == null || currency.isEmpty() || "الكل".equals(currency))
                        ? ("دولار".equals(r.getSale() != null ? r.getSale().getCurrency() : null) ? "دولار" : "دينار")
                        : currency;
                String reason = r.getReturnReason() != null ? r.getReturnReason() : "-";

                returnsTable.addCell(createBodyCell(String.valueOf(retRow), arabicFont, Element.ALIGN_CENTER));
                returnsTable.addCell(createBodyCell(saleCode, arabicFont, Element.ALIGN_CENTER));
                returnsTable.addCell(createBodyCell(retDate, arabicFont, Element.ALIGN_CENTER));
                returnsTable.addCell(createBodyCell(projectName, arabicFont, Element.ALIGN_CENTER));
                returnsTable.addCell(
                        createBodyCell(formatCurrency(retAmount, returnCurrency), arabicFont, Element.ALIGN_CENTER));
                returnsTable.addCell(createBodyCell(reason, arabicFont, Element.ALIGN_CENTER));

                retRow++;
            }

            document.add(returnsTable);
        }

        // ===== Receipts (سندات القبض) =====
        List<Voucher> receipts = vouchers.stream().filter(v -> v.getVoucherType() == VoucherType.RECEIPT).toList();
        if (!receipts.isEmpty()) {
            PdfPTable receiptsHeader = new PdfPTable(1);
            receiptsHeader.setWidthPercentage(100);
            receiptsHeader.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            receiptsHeader.setSpacingBefore(10f);

            PdfPCell receiptsTitleCell = new PdfPCell(new Phrase("سندات القبض", sectionTitleFont));
            receiptsTitleCell.setBorder(PdfPCell.NO_BORDER);
            receiptsTitleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            receiptsTitleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            receiptsTitleCell.setPaddingBottom(4f);
            receiptsTitleCell.setBackgroundColor(new BaseColor(220, 252, 231));
            receiptsHeader.addCell(receiptsTitleCell);
            document.add(receiptsHeader);

            PdfPTable receiptsTable = new PdfPTable(6);
            receiptsTable.setWidthPercentage(100);
            receiptsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            receiptsTable.setSpacingBefore(5f);
            receiptsTable.setSpacingAfter(10f);
            receiptsTable.setWidths(new float[] { 1f, 1f, 1.2f, 1f, 1f, 0.2f });

            addTableHeader(receiptsTable, "ت", arabicBoldFont);
            addTableHeader(receiptsTable, "رقم السند", arabicBoldFont);
            addTableHeader(receiptsTable, "التاريخ", arabicBoldFont);
            addTableHeader(receiptsTable, "المشروع", arabicBoldFont);
            addTableHeader(receiptsTable, "المبلغ", arabicBoldFont);
            addTableHeader(receiptsTable, "البيان", arabicBoldFont);

            int rcptRow = 1;
            for (Voucher v : receipts) {
                String vNumber = v.getVoucherNumber() != null ? v.getVoucherNumber() : "-";
                String vDate = v.getVoucherDate() != null
                        ? v.getVoucherDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : "-";
                String vProject = v.getProjectName() != null ? v.getProjectName() : "-";
                double vAmount = v.getAmount() != null ? v.getAmount() : 0.0;
                String vCurrency = (currency == null || currency.isEmpty() || "الكل".equals(currency))
                        ? ("دولار".equals(v.getCurrency()) ? "دولار" : "دينار")
                        : currency;
                String vDesc = v.getDescription() != null ? v.getDescription() : "-";

                receiptsTable.addCell(createBodyCell(String.valueOf(rcptRow), arabicFont, Element.ALIGN_CENTER));
                receiptsTable.addCell(createBodyCell(vNumber, arabicFont, Element.ALIGN_CENTER));
                receiptsTable.addCell(createBodyCell(vDate, arabicFont, Element.ALIGN_CENTER));
                receiptsTable.addCell(createBodyCell(vProject, arabicFont, Element.ALIGN_CENTER));
                receiptsTable
                        .addCell(createBodyCell(formatCurrency(vAmount, vCurrency), arabicFont, Element.ALIGN_CENTER));
                receiptsTable.addCell(createBodyCell(vDesc, arabicFont, Element.ALIGN_CENTER));

                rcptRow++;
            }

            document.add(receiptsTable);
        }

        // ===== Payments (سندات الدفع) =====
        List<Voucher> payments = vouchers.stream().filter(v -> v.getVoucherType() == VoucherType.PAYMENT).toList();
        if (!payments.isEmpty()) {
            PdfPTable paymentsHeader = new PdfPTable(1);
            paymentsHeader.setWidthPercentage(100);
            paymentsHeader.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            paymentsHeader.setSpacingBefore(10f);

            PdfPCell paymentsTitleCell = new PdfPCell(new Phrase("سندات الدفع", sectionTitleFont));
            paymentsTitleCell.setBorder(PdfPCell.NO_BORDER);
            paymentsTitleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            paymentsTitleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            paymentsTitleCell.setPaddingBottom(4f);
            paymentsTitleCell.setBackgroundColor(new BaseColor(254, 243, 199));
            paymentsHeader.addCell(paymentsTitleCell);
            document.add(paymentsHeader);

            PdfPTable paymentsTable = new PdfPTable(6);
            paymentsTable.setWidthPercentage(100);
            paymentsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            paymentsTable.setSpacingBefore(5f);
            paymentsTable.setSpacingAfter(10f);
            paymentsTable.setWidths(new float[] { 1f, 1f, 1.2f, 1f, 1f, 0.2f });

            addTableHeader(paymentsTable, "ت", arabicBoldFont);
            addTableHeader(paymentsTable, "رقم السند", arabicBoldFont);
            addTableHeader(paymentsTable, "التاريخ", arabicBoldFont);
            addTableHeader(paymentsTable, "المشروع", arabicBoldFont);
            addTableHeader(paymentsTable, "المبلغ", arabicBoldFont);
            addTableHeader(paymentsTable, "البيان", arabicBoldFont);

            int payRow = 1;
            for (Voucher v : payments) {
                String vNumber = v.getVoucherNumber() != null ? v.getVoucherNumber() : "-";
                String vDate = v.getVoucherDate() != null
                        ? v.getVoucherDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : "-";
                String vProject = v.getProjectName() != null ? v.getProjectName() : "-";
                double vAmount = v.getAmount() != null ? v.getAmount() : 0.0;
                String vCurrency = (currency == null || currency.isEmpty() || "الكل".equals(currency))
                        ? ("دولار".equals(v.getCurrency()) ? "دولار" : "دينار")
                        : currency;
                String vDesc = v.getDescription() != null ? v.getDescription() : "-";

                paymentsTable.addCell(createBodyCell(String.valueOf(payRow), arabicFont, Element.ALIGN_CENTER));
                paymentsTable.addCell(createBodyCell(vNumber, arabicFont, Element.ALIGN_CENTER));
                paymentsTable.addCell(createBodyCell(vDate, arabicFont, Element.ALIGN_CENTER));
                paymentsTable.addCell(createBodyCell(vProject, arabicFont, Element.ALIGN_CENTER));
                paymentsTable
                        .addCell(createBodyCell(formatCurrency(vAmount, vCurrency), arabicFont, Element.ALIGN_CENTER));
                paymentsTable.addCell(createBodyCell(vDesc, arabicFont, Element.ALIGN_CENTER));

                payRow++;
            }

            document.add(paymentsTable);
        }

        addUnifiedFooter(document, arabicBoldFont, smallFont);

        document.close();
        return baos.toByteArray();
    }

    private PdfPCell createBodyCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setPadding(4f);
        return cell;
    }

    private void addTotalRowBordered(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        labelCell.setPadding(5f);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        valueCell.setPadding(5f);

        table.addCell(labelCell);
        table.addCell(valueCell);
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

    private String formatAmount(Double value) {
        double v = value != null ? value : 0.0;
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        return df.format(v);
    }

    private String formatCurrency(Double value) {
        return formatCurrency(value, "دينار");
    }

    private String formatCurrency(Double value, String currency) {
        String label = currency != null && !currency.isBlank() ? currency : "د.ع";
        if ("دولار".equals(label)) {
            label = "$";
        } else if ("دينار".equals(label)) {
            label = "د.ع";
        } else if ("الكل".equals(label)) {
            label = "الكل";
        }
        return formatAmount(value) + " " + label;
    }

    private String generateReceiptNumber() {
        Preferences prefs = Preferences.userNodeForPackage(ReceiptService.class);
        long lastUsed = prefs.getLong(PREF_LAST_RECEIPT_NUMBER, 0L);

        long dbNext = receiptRepository.getNextReceiptNumberNumeric();
        long next = Math.max(lastUsed + 1L, dbNext);

        prefs.putLong(PREF_LAST_RECEIPT_NUMBER, next);
        return String.valueOf(next);
    }

    private String getPaymentMethodArabic(String method) {
        if (method == null)
            return "-";
        switch (method) {
            case "CASH":
                return "نقدي";
            case "DEBT":
                return "دين";
            case "CARD":
                return "بطاقة";
            default:
                return method;
        }
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

        PdfPCell appInfoCell = new PdfPCell(new Phrase(APP_NAME + " | " + COMPANY_WEBSITE, smallFont));
        appInfoCell.setBorder(PdfPCell.NO_BORDER);
        appInfoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        appInfoCell.setRunDirection(PdfWriter.RUN_DIRECTION_LTR);
        appInfoCell.setPaddingTop(5f);
        appInfoCell.setBackgroundColor(new BaseColor(245, 245, 245));
        footerTable.addCell(appInfoCell);

        document.add(footerTable);
    }

    private String getPaymentStatusArabic(String status) {
        if (status == null)
            return "-";
        switch (status) {
            case "PAID":
                return "مدفوع";
            case "PENDING":
                return "معلق";
            case "OVERDUE":
                return "متأخر";
            default:
                return status;
        }
    }

    public Optional<Receipt> getReceiptById(Long id) {
        return receiptRepository.findById(id);
    }

    public Optional<Receipt> getReceiptByNumber(String receiptNumber) {
        return receiptRepository.findByReceiptNumber(receiptNumber);
    }

    public List<Receipt> getAllReceipts() {
        return receiptRepository.findAllWithDetails();
    }

    public void deleteReceipt(Long id) {
        if (id == null) {
            return;
        }

        try {
            Optional<Receipt> receiptOpt = receiptRepository.findById(id);
            if (receiptOpt.isPresent()) {
                Receipt receipt = receiptOpt.get();
                if (receipt.getFilePath() != null) {
                    File pdf = new File(receipt.getFilePath());
                    if (pdf.exists()) {
                        pdf.delete();
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to delete receipt PDF before DB delete: {}", id, e);
        }

        receiptRepository.deleteByIdDirect(id);
    }

    public boolean hasReceiptForSale(Long saleId) {
        if (saleId == null)
            return false;
        return receiptRepository.findAll().stream()
                .anyMatch(r -> r.getSale() != null && saleId.equals(r.getSale().getId()));
    }
}
