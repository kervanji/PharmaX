package com.pharmax.service;

import com.pharmax.database.DatabaseManager;
import com.pharmax.model.*;
import com.itextpdf.text.*;
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
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class VoucherService {
    private static final Logger logger = LoggerFactory.getLogger(VoucherService.class);
    private final CustomerService customerService;
    private final InventoryService inventoryService;
    
    public VoucherService() {
        this.customerService = new CustomerService();
        this.inventoryService = new InventoryService();
    }

    public File generateVoucherReceiptPdf(Long voucherId, String printedBy) {
        if (voucherId == null) {
            throw new IllegalArgumentException("السند غير موجود");
        }

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Voucher voucher = session.get(Voucher.class, voucherId);
            if (voucher == null) {
                throw new IllegalArgumentException("السند غير موجود");
            }

            byte[] pdfData = generateVoucherPdf(voucher);

            File receiptsDir = new File("voucher_receipts");
            if (!receiptsDir.exists()) {
                receiptsDir.mkdirs();
            }

            String safeNumber = voucher.getVoucherNumber() != null ? voucher.getVoucherNumber() : String.valueOf(voucherId);
            String fileName = "voucher_" + safeNumber + ".pdf";
            File outFile = new File(receiptsDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(pdfData);
            }

            return outFile;
        } catch (Exception e) {
            logger.error("Failed to generate voucher receipt pdf", e);
            throw new RuntimeException("فشل في إنشاء إيصال السند", e);
        }
    }

    private byte[] generateVoucherPdf(Voucher voucher) throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        float bannerTargetHeight = 60f;
        Rectangle pageSize = PageSize.A5;
        Document document = new Document(pageSize, 12, 12, 10 + bannerTargetHeight, 10);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        BaseFont baseFont = loadArabicBaseFont();
        Font arabicFont = new Font(baseFont, 8, Font.NORMAL);
        Font arabicBoldFont = new Font(baseFont, 9, Font.BOLD);
        Font titleFont = new Font(baseFont, 11, Font.BOLD);
        Font smallFont = new Font(baseFont, 7, Font.NORMAL);

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
            logger.warn("Failed to add voucher banner", e);
        }

        addVoucherLayout(document, voucher, arabicFont, arabicBoldFont, titleFont, smallFont);

        document.close();
        return baos.toByteArray();
    }

    private void addVoucherLayout(Document document,
                                   Voucher voucher,
                                   Font arabicFont,
                                   Font arabicBoldFont,
                                   Font titleFont,
                                   Font smallFont) throws DocumentException {
        boolean isReceipt = voucher.getVoucherType() == VoucherType.RECEIPT;
        boolean isPurchase = voucher.getVoucherType() == VoucherType.PURCHASE;
        String voucherTitle = isReceipt ? "سند قبض" : isPurchase ? "فاتورة مشتريات" : "سند دفع";
        String personLabel = isReceipt ? "استلمنا من السيد" : isPurchase ? "مشتريات من" : "دفعنا إلى السيد";
        BaseColor metaBgColor = isReceipt ? new BaseColor(232, 245, 233) : isPurchase ? new BaseColor(254, 243, 199) : new BaseColor(254, 226, 226);

        PdfPTable titleTable = new PdfPTable(1);
        titleTable.setWidthPercentage(100);
        titleTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        PdfPCell titleCell = new PdfPCell(new Phrase(voucherTitle, titleFont));
        titleCell.setBorder(PdfPCell.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setPaddingBottom(3f);
        titleCell.setPaddingTop(0f);
        titleTable.addCell(titleCell);
        document.add(titleTable);

        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        info.setWidths(new float[]{1.1f, 0.9f});
        info.setSpacingAfter(3f);

        // Right block: voucher meta
        PdfPCell meta = new PdfPCell();
        meta.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        meta.setPadding(3f);
        meta.setBackgroundColor(metaBgColor);
        meta.addElement(new Phrase("رقم السند: " + safe(voucher.getVoucherNumber()), arabicFont));
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        meta.addElement(new Phrase("التاريخ: " + (voucher.getVoucherDate() != null ? voucher.getVoucherDate().format(dateFmt) : "-"), arabicFont));
        meta.addElement(new Phrase("الوقت: " + (voucher.getVoucherDate() != null ? voucher.getVoucherDate().format(timeFmt) : "-"), arabicFont));
        info.addCell(meta);

        // Left block: account info
        PdfPCell account = new PdfPCell();
        account.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        account.setPadding(3f);
        String accountName = voucher.getCustomer() != null && voucher.getCustomer().getName() != null ? voucher.getCustomer().getName() : "نقدي";
        String projectName = voucher.getProjectName();
        String accountWithProject = accountName + ((projectName != null && !projectName.trim().isEmpty()) ? " / " + projectName.trim() : "");
        account.addElement(new Phrase("الحساب: " + accountWithProject, arabicFont));
        account.addElement(new Phrase("العملة: " + safe(voucher.getCurrency()), arabicFont));
        info.addCell(account);

        document.add(info);

        double net = voucher.getNetAmount() != null ? voucher.getNetAmount() : 0.0;
        boolean isUsd = "دولار".equals(voucher.getCurrency()) || "USD".equalsIgnoreCase(voucher.getCurrency());
        double customerBalance = 0.0;
        if (voucher.getCustomer() != null) {
            customerBalance = isUsd
                    ? (voucher.getCustomer().getBalanceUsd() != null ? voucher.getCustomer().getBalanceUsd() : 0.0)
                    : (voucher.getCustomer().getBalanceIqd() != null ? voucher.getCustomer().getBalanceIqd() : 0.0);
        }
        double previousBalance = isReceipt ? customerBalance - net : customerBalance + net;

        PdfPTable body = new PdfPTable(2);
        body.setWidthPercentage(100);
        body.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        body.setWidths(new float[]{0.65f, 0.35f});
        body.setSpacingBefore(2f);

        double paidAmount = voucher.getAmount() != null ? voucher.getAmount() : 0.0;
        addLabeledRow(body, personLabel, accountWithProject, arabicFont, arabicBoldFont);
        addLabeledRow(body, "المدفوع", formatCurrency(paidAmount, voucher.getCurrency()), arabicFont, arabicBoldFont);
        addLabeledRow(body, "المبلغ كتابةً", safe(voucher.getAmountInWords()), arabicFont, arabicBoldFont);
        addLabeledRow(body, "البيان", safe(voucher.getDescription()), arabicFont, arabicBoldFont);
        addLabeledRow(body, "ملاحظات", safe(voucher.getNotes()), arabicFont, arabicBoldFont);
        addLabeledRow(body, "المبلغ السابق", formatCurrency(Math.abs(previousBalance), voucher.getCurrency()), arabicFont, arabicBoldFont);
        addLabeledRow(body, "المبلغ الحالي", formatCurrency(Math.abs(customerBalance), voucher.getCurrency()), arabicFont, arabicBoldFont);

        document.add(body);

        // جدول المواد (إن وجدت)
        List<VoucherItem> items = voucher.getItems() != null ? voucher.getItems() : Collections.emptyList();
        if (!items.isEmpty()) {
            PdfPTable itemsTable = new PdfPTable(6);
            itemsTable.setWidthPercentage(100);
            itemsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            itemsTable.setWidths(new float[]{0.4f, 2.0f, 0.7f, 0.7f, 0.8f, 0.8f});
            itemsTable.setSpacingBefore(3f);

            addHeaderCell(itemsTable, "ت", arabicBoldFont);
            addHeaderCell(itemsTable, "المادة", arabicBoldFont);
            addHeaderCell(itemsTable, "الكمية", arabicBoldFont);
            addHeaderCell(itemsTable, "الوحدة", arabicBoldFont);
            addHeaderCell(itemsTable, "سعر الوحدة", arabicBoldFont);
            addHeaderCell(itemsTable, "المجموع", arabicBoldFont);

            int rowNo = 1;
            for (VoucherItem item : items) {
                itemsTable.addCell(createBodyCell(String.valueOf(rowNo++), smallFont, Element.ALIGN_CENTER));
                itemsTable.addCell(createBodyCell(item.getProductName() != null ? item.getProductName() : "-", smallFont, Element.ALIGN_RIGHT));
                itemsTable.addCell(createBodyCell(item.getQuantity() != null ? formatAmount(item.getQuantity()) : "0", smallFont, Element.ALIGN_CENTER));
                itemsTable.addCell(createBodyCell(item.getUnitOfMeasure() != null ? item.getUnitOfMeasure() : "-", smallFont, Element.ALIGN_CENTER));
                itemsTable.addCell(createBodyCell(formatAmount(item.getUnitPrice()), smallFont, Element.ALIGN_CENTER));
                itemsTable.addCell(createBodyCell(formatAmount(item.getTotalPrice()), smallFont, Element.ALIGN_CENTER));
            }
            document.add(itemsTable);
        }
    }

    private void addLabeledRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        labelCell.setPadding(3f);
        labelCell.setBackgroundColor(new BaseColor(245, 245, 245));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "-", valueFont));
        valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        valueCell.setPadding(3f);
        valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(valueCell);
    }

    private PdfPCell signatureCell(String title, Font font) {
        PdfPCell cell = new PdfPCell();
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setPadding(10f);
        cell.setMinimumHeight(40f);
        cell.addElement(new Phrase(title + ":", font));
        cell.addElement(new Phrase("\n\n", font));
        return cell;
    }

    private String safe(String val) {
        return val != null && !val.trim().isEmpty() ? val : "-";
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(3f);
        cell.setBackgroundColor(new BaseColor(241, 245, 249));
        table.addCell(cell);
    }

    private PdfPCell createBodyCell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setHorizontalAlignment(align);
        cell.setPadding(3f);
        return cell;
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, labelFont));
        c1.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        c1.setPadding(6f);
        PdfPCell c2 = new PdfPCell(new Phrase(value, valueFont));
        c2.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        c2.setPadding(6f);
        c2.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(c1);
        table.addCell(c2);
    }

    private String formatAmount(Double amount) {
        if (amount == null) {
            return "0";
        }
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.##");
        return df.format(amount);
    }

    private String formatCurrency(Double amount, String currency) {
        String symbol = "دينار".equals(currency) ? "د.ع" : "$";
        return formatAmount(amount) + " " + symbol;
    }

    /**
     * مجموع مواد السند (total_price أو quantity * unit_price لكل مادة)
     */
    private double getItemsTotal(Voucher voucher) {
        if (voucher == null || voucher.getItems() == null) return 0.0;
        double sum = 0.0;
        for (VoucherItem item : voucher.getItems()) {
            if (item == null) continue;
            if (item.getTotalPrice() != null) {
                sum += item.getTotalPrice();
            } else if (item.getQuantity() != null && item.getUnitPrice() != null) {
                sum += item.getQuantity() * item.getUnitPrice();
            }
        }
        return sum;
    }

    /**
     * Determines the amount to show in PDF totals.
     * If voucher items exist, use their summed total_price; otherwise fall back to net amount, then amount.
     */
    private double getDisplayAmount(Voucher voucher) {
        if (voucher != null && voucher.getItems() != null && !voucher.getItems().isEmpty()) {
            double sum = 0.0;
            for (VoucherItem item : voucher.getItems()) {
                if (item == null) continue;
                if (item.getTotalPrice() != null) {
                    sum += item.getTotalPrice();
                } else if (item.getQuantity() != null && item.getUnitPrice() != null) {
                    sum += item.getQuantity() * item.getUnitPrice();
                }
            }
            if (sum > 0) return sum;
        }

        if (voucher != null) {
            if (voucher.getNetAmount() != null) {
                return voucher.getNetAmount();
            }
            if (voucher.getAmount() != null) {
                return voucher.getAmount();
            }
        }
        return 0.0;
    }

    private BaseFont loadArabicBaseFont() throws DocumentException, IOException {
        String[] fontCandidates = new String[]{
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
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(ReceiptService.class);
            String bannerPath = prefs.get("receipt.banner.path", null);
            if (bannerPath != null && !bannerPath.trim().isEmpty()) {
                File f = new File(bannerPath);
                if (f.exists() && f.isFile()) {
                    return Image.getInstance(f.getAbsolutePath());
                }
            }
            // Fallback to default logo
            URL logoUrl = VoucherService.class.getResource("/templates/PharmaX.png");
            if (logoUrl != null) {
                return Image.getInstance(logoUrl);
            }
        } catch (Exception e) {
            logger.warn("Failed to load voucher banner image", e);
        }
        return null;
    }
    
    // توليد رقم السند - تسلسل رقمي موحّد لجميع أنواع السندات
    public String generateVoucherNumber(VoucherType type) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<String> query = session.createQuery(
                "SELECT v.voucherNumber FROM Voucher v",
                String.class
            );
            List<String> allNumbers = query.list();

            long max = 0L;
            if (allNumbers != null) {
                for (String num : allNumbers) {
                    if (num == null) continue;
                    String trimmed = num.trim();
                    // إزالة البادئة القديمة (RV/PV) إن وجدت
                    if (trimmed.startsWith("RV") || trimmed.startsWith("PV")) {
                        trimmed = trimmed.substring(2);
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
            return String.valueOf(max + 1L);
        }
    }
    
    // حفظ سند جديد
    public Voucher saveVoucher(Voucher voucher) {
        logger.info("Saving voucher: {} - {}", voucher.getVoucherType(), voucher.getVoucherNumber());
        
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            
            // توليد رقم السند إذا لم يكن موجوداً
            if (voucher.getVoucherNumber() == null || voucher.getVoucherNumber().isEmpty()) {
                voucher.setVoucherNumber(generateVoucherNumber(voucher.getVoucherType()));
            }
            
            // حساب المبلغ الصافي
            calculateNetAmount(voucher);
            
            // تحويل المبلغ إلى كتابة
            voucher.setAmountInWords(convertAmountToWords(voucher.getNetAmount(), voucher.getCurrency()));
            
            // توليد البيان التلقائي
            if (voucher.getDescription() == null || voucher.getDescription().isEmpty()) {
                voucher.setDescription(generateDescription(voucher));
            }
            
            session.saveOrUpdate(voucher);
            
            // تحديث رصيد العميل (داخل نفس Session/Transaction لتجنب SQLITE_BUSY)
            if (voucher.getCustomer() != null && voucher.getCustomer().getId() != null) {
                updateCustomerBalanceInSession(session, voucher);
            }

            // إضافة المواد للمخزون (داخل نفس Session/Transaction لتجنب SQLITE_BUSY)
            if ((voucher.getVoucherType() == VoucherType.PAYMENT || voucher.getVoucherType() == VoucherType.PURCHASE) && voucher.getItems() != null) {
                for (VoucherItem item : voucher.getItems()) {
                    if (Boolean.TRUE.equals(item.getAddToInventory()) && item.getProduct() != null && item.getProduct().getId() != null) {
                        addStockInSession(session, item.getProduct().getId(), item.getQuantity());
                    }
                }
            }
            
            transaction.commit();
            logger.info("Voucher saved successfully: {}", voucher.getVoucherNumber());
            return voucher;
            
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            logger.error("Error saving voucher", e);
            throw new RuntimeException("فشل في حفظ السند: " + e.getMessage(), e);
        }
    }
    
    // إنشاء سند بأقساط
    public Voucher saveVoucherWithInstallments(Voucher voucher, int numberOfInstallments, LocalDate firstDueDate) {
        logger.info("Creating voucher with {} installments", numberOfInstallments);
        
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            
            // توليد رقم السند
            if (voucher.getVoucherNumber() == null || voucher.getVoucherNumber().isEmpty()) {
                voucher.setVoucherNumber(generateVoucherNumber(voucher.getVoucherType()));
            }
            
            calculateNetAmount(voucher);
            voucher.setAmountInWords(convertAmountToWords(voucher.getNetAmount(), voucher.getCurrency()));
            
            if (voucher.getDescription() == null || voucher.getDescription().isEmpty()) {
                voucher.setDescription(generateDescription(voucher));
            }
            
            voucher.setIsInstallment(true);
            voucher.setTotalInstallments(numberOfInstallments);
            
            session.saveOrUpdate(voucher);
            
            // إنشاء الأقساط
            double installmentAmount = voucher.getNetAmount() / numberOfInstallments;
            LocalDate dueDate = firstDueDate;
            
            for (int i = 1; i <= numberOfInstallments; i++) {
                Installment installment = new Installment();
                installment.setParentVoucher(voucher);
                installment.setInstallmentNumber(i);
                installment.setAmount(installmentAmount);
                installment.setDueDate(dueDate);
                installment.setIsPaid(false);
                
                session.save(installment);
                voucher.getInstallments().add(installment);
                
                // القسط التالي بعد شهر
                dueDate = dueDate.plusMonths(1);
            }

            // تحديث رصيد العميل (داخل نفس Session/Transaction لتجنب SQLITE_BUSY)
            if (voucher.getCustomer() != null && voucher.getCustomer().getId() != null) {
                updateCustomerBalanceInSession(session, voucher);
            }

            // إضافة المواد للمخزون (داخل نفس Session/Transaction لتجنب SQLITE_BUSY)
            if ((voucher.getVoucherType() == VoucherType.PAYMENT || voucher.getVoucherType() == VoucherType.PURCHASE) && voucher.getItems() != null) {
                for (VoucherItem item : voucher.getItems()) {
                    if (Boolean.TRUE.equals(item.getAddToInventory()) && item.getProduct() != null && item.getProduct().getId() != null) {
                        addStockInSession(session, item.getProduct().getId(), item.getQuantity());
                    }
                }
            }
            
            transaction.commit();
            logger.info("Voucher with installments saved: {}", voucher.getVoucherNumber());
            return voucher;
            
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            logger.error("Error saving voucher with installments", e);
            throw new RuntimeException("فشل في حفظ السند بالأقساط: " + e.getMessage(), e);
        }
    }
    
    // دفع قسط
    public Installment payInstallment(Long installmentId, Double paidAmount, String paymentVoucherNumber) {
        logger.info("Paying installment: {}", installmentId);
        
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            
            Installment installment = session.get(Installment.class, installmentId);
            if (installment == null) {
                throw new IllegalArgumentException("القسط غير موجود");
            }
            
            installment.setPaidAmount(installment.getPaidAmount() + paidAmount);
            installment.setPaidDate(LocalDate.now());
            
            if (installment.getPaidAmount() >= installment.getAmount()) {
                installment.setIsPaid(true);
            }
            
            session.update(installment);
            transaction.commit();
            
            return installment;
            
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            logger.error("Error paying installment", e);
            throw new RuntimeException("فشل في دفع القسط: " + e.getMessage(), e);
        }
    }
    
    // الحصول على السندات حسب النوع
    public List<Voucher> getVouchersByType(VoucherType type) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<Voucher> query = session.createQuery(
                "FROM Voucher v WHERE v.voucherType = :type AND v.isCancelled = false ORDER BY v.createdAt DESC", 
                Voucher.class);
            query.setParameter("type", type);
            return query.list();
        }
    }
    
    // الحصول على السندات حسب العميل
    public List<Voucher> getVouchersByCustomer(Long customerId) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<Voucher> query = session.createQuery(
                "FROM Voucher v WHERE v.customer.id = :customerId AND v.isCancelled = false ORDER BY v.createdAt DESC", 
                Voucher.class);
            query.setParameter("customerId", customerId);
            return query.list();
        }
    }
    
    // الحصول على السندات حسب العميل والنوع
    public List<Voucher> getVouchersByCustomerAndType(Long customerId, VoucherType type) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<Voucher> query = session.createQuery(
                "FROM Voucher v WHERE v.customer.id = :customerId AND v.voucherType = :type AND v.isCancelled = false ORDER BY v.createdAt DESC", 
                Voucher.class);
            query.setParameter("customerId", customerId);
            query.setParameter("type", type);
            return query.list();
        }
    }

    // الحصول على أسماء المشاريع المميزة
    public List<String> getDistinctProjectNames() {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<String> query = session.createQuery(
                "SELECT DISTINCT v.projectName FROM Voucher v WHERE v.projectName IS NOT NULL AND v.projectName <> '' ORDER BY v.projectName",
                String.class);
            return query.list();
        } catch (Exception e) {
            logger.error("Failed to get distinct project names", e);
            return Collections.emptyList();
        }
    }
    
    // الحصول على سند بالرقم
    public Optional<Voucher> getVoucherByNumber(String voucherNumber) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<Voucher> query = session.createQuery(
                "FROM Voucher v WHERE v.voucherNumber = :number", Voucher.class);
            query.setParameter("number", voucherNumber);
            return Optional.ofNullable(query.uniqueResult());
        }
    }
    
    // الحصول على سند بالمعرف
    public Optional<Voucher> getVoucherById(Long id) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            return Optional.ofNullable(session.get(Voucher.class, id));
        }
    }
    
    // الحصول على الأقساط المستحقة
    public List<Installment> getDueInstallments() {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<Installment> query = session.createQuery(
                "FROM Installment i WHERE i.isPaid = false AND i.dueDate <= :today ORDER BY i.dueDate", 
                Installment.class);
            query.setParameter("today", LocalDate.now());
            return query.list();
        }
    }
    
    // الحصول على الأقساط القادمة
    public List<Installment> getUpcomingInstallments(int days) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<Installment> query = session.createQuery(
                "FROM Installment i WHERE i.isPaid = false AND i.dueDate BETWEEN :today AND :future ORDER BY i.dueDate", 
                Installment.class);
            query.setParameter("today", LocalDate.now());
            query.setParameter("future", LocalDate.now().plusDays(days));
            return query.list();
        }
    }
    
    // الحصول على أقساط سند معين
    public List<Installment> getInstallmentsByVoucher(Long voucherId) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Query<Installment> query = session.createQuery(
                "FROM Installment i WHERE i.parentVoucher.id = :voucherId ORDER BY i.installmentNumber", 
                Installment.class);
            query.setParameter("voucherId", voucherId);
            return query.list();
        }
    }
    
    // إلغاء سند
    public Voucher cancelVoucher(Long voucherId, String cancelledBy, String reason) {
        logger.info("Cancelling voucher: {}", voucherId);
        
        Transaction transaction = null;
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            
            Voucher voucher = session.get(Voucher.class, voucherId);
            if (voucher == null) {
                throw new IllegalArgumentException("السند غير موجود");
            }
            
            voucher.setIsCancelled(true);
            voucher.setCancelledAt(LocalDateTime.now());
            voucher.setCancelledBy(cancelledBy);
            voucher.setCancelReason(reason);
            
            // عكس تأثير السند على رصيد العميل
            if (voucher.getCustomer() != null) {
                reverseCustomerBalanceInSession(session, voucher);
            }
            
            session.update(voucher);
            transaction.commit();
            
            return voucher;
            
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            logger.error("Error cancelling voucher", e);
            throw new RuntimeException("فشل في إلغاء السند: " + e.getMessage(), e);
        }
    }
    
    // حساب إحصائيات السندات
    public VoucherStats getVoucherStats(LocalDateTime from, LocalDateTime to) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            VoucherStats stats = new VoucherStats();
            
            // سندات القبض
            Query<Object[]> receiptQuery = session.createQuery(
                "SELECT COUNT(v), COALESCE(SUM(v.netAmount), 0) FROM Voucher v " +
                "WHERE v.voucherType = :type AND v.isCancelled = false " +
                "AND v.voucherDate BETWEEN :from AND :to", Object[].class);
            receiptQuery.setParameter("type", VoucherType.RECEIPT);
            receiptQuery.setParameter("from", from);
            receiptQuery.setParameter("to", to);
            Object[] receiptResult = receiptQuery.uniqueResult();
            stats.setReceiptCount(((Long) receiptResult[0]).intValue());
            stats.setReceiptTotal((Double) receiptResult[1]);
            
            // سندات الدفع
            Query<Object[]> paymentQuery = session.createQuery(
                "SELECT COUNT(v), COALESCE(SUM(v.netAmount), 0) FROM Voucher v " +
                "WHERE v.voucherType = :type AND v.isCancelled = false " +
                "AND v.voucherDate BETWEEN :from AND :to", Object[].class);
            paymentQuery.setParameter("type", VoucherType.PAYMENT);
            paymentQuery.setParameter("from", from);
            paymentQuery.setParameter("to", to);
            Object[] paymentResult = paymentQuery.uniqueResult();
            stats.setPaymentCount(((Long) paymentResult[0]).intValue());
            stats.setPaymentTotal((Double) paymentResult[1]);
            
            return stats;
        }
    }
    
    // البحث في السندات
    public List<Voucher> searchVouchers(String searchTerm, VoucherType type, LocalDateTime from, LocalDateTime to) {
        return searchVouchers(searchTerm, type, from, to, null, null);
    }

    public List<Voucher> searchVouchers(String searchTerm, VoucherType type, LocalDateTime from, LocalDateTime to,
                                        String projectName, Long customerId) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder("FROM Voucher v WHERE v.isCancelled = false ");
            
            if (type != null) {
                hql.append("AND v.voucherType = :type ");
            }
            if (from != null && to != null) {
                hql.append("AND v.voucherDate BETWEEN :from AND :to ");
            }
            if (searchTerm != null && !searchTerm.isEmpty()) {
                hql.append("AND (v.voucherNumber LIKE :search OR v.description LIKE :search OR v.customer.name LIKE :search) ");
            }
            if (projectName != null && !projectName.isBlank()) {
                hql.append("AND v.projectName LIKE :projectName ");
            }
            if (customerId != null) {
                hql.append("AND v.customer.id = :customerId ");
            }
            hql.append("ORDER BY v.createdAt DESC");
            
            Query<Voucher> query = session.createQuery(hql.toString(), Voucher.class);
            
            if (type != null) {
                query.setParameter("type", type);
            }
            if (from != null && to != null) {
                query.setParameter("from", from);
                query.setParameter("to", to);
            }
            if (searchTerm != null && !searchTerm.isEmpty()) {
                query.setParameter("search", "%" + searchTerm + "%");
            }
            if (projectName != null && !projectName.isBlank()) {
                query.setParameter("projectName", "%" + projectName.trim() + "%");
            }
            if (customerId != null) {
                query.setParameter("customerId", customerId);
            }
            
            return query.list();
        }
    }
    
    // ========== Helper Methods ==========
    
    private void calculateNetAmount(Voucher voucher) {
        double itemsTotal = getItemsTotal(voucher);
        double base = itemsTotal > 0 ? itemsTotal : (voucher.getAmount() != null ? voucher.getAmount() : 0.0);

        double discountPercent = voucher.getDiscountPercentage() != null ? voucher.getDiscountPercentage() : 0;
        double discountAmount = voucher.getDiscountAmount() != null ? voucher.getDiscountAmount() : 0;

        if (discountPercent > 0) {
            discountAmount = base * discountPercent / 100;
            voucher.setDiscountAmount(discountAmount);
        }

        double net = base - discountAmount;
        voucher.setNetAmount(net);
        // لا نغير amount هنا؛ يبقى مدفوع المستخدم (مثلاً دفعة جزئية)
    }
    
    private void updateCustomerBalance(Voucher voucher) {
        if (voucher.getCustomer() == null) return;
        
        double amount = voucher.getNetAmount();
        String currency = voucher.getCurrency();
        
        // سند قبض = العميل دفع لنا = تقليل ديونه علينا أو زيادة رصيده الدائن
        // سند دفع = نحن دفعنا للعميل/المورد = زيادة ديونه علينا أو تقليل رصيده الدائن
        if (voucher.getVoucherType() == VoucherType.RECEIPT) {
            // قبض من العميل - يقلل ما له عندنا
            customerService.updateCustomerBalanceByCurrency(voucher.getCustomer().getId(), amount, currency);
        } else {
            // دفع للعميل/المورد - يزيد ما له عندنا
            customerService.updateCustomerBalanceByCurrency(voucher.getCustomer().getId(), -amount, currency);
        }
    }
    
    private void reverseCustomerBalance(Voucher voucher) {
        if (voucher.getCustomer() == null) return;
        
        double amount = voucher.getNetAmount();
        String currency = voucher.getCurrency();
        
        // عكس التأثير
        if (voucher.getVoucherType() == VoucherType.RECEIPT) {
            customerService.updateCustomerBalanceByCurrency(voucher.getCustomer().getId(), -amount, currency);
        } else {
            customerService.updateCustomerBalanceByCurrency(voucher.getCustomer().getId(), amount, currency);
        }
    }

    private void updateCustomerBalanceInSession(Session session, Voucher voucher) {
        if (voucher.getCustomer() == null || voucher.getCustomer().getId() == null) {
            return;
        }

        Customer customer = session.get(Customer.class, voucher.getCustomer().getId());
        if (customer == null) {
            throw new IllegalArgumentException("العميل غير موجود");
        }

        double amount = voucher.getNetAmount() != null ? voucher.getNetAmount() : 0.0;
        String currency = voucher.getCurrency();

        // سند قبض = العميل دفع لنا
        // سند دفع = نحن دفعنا للعميل/المورد
        boolean isUsd = "دولار".equals(currency) || "USD".equalsIgnoreCase(currency);

        if (voucher.getVoucherType() == VoucherType.RECEIPT) {
            if (isUsd) {
                customer.setBalanceUsd(customer.getBalanceUsd() + amount);
            } else {
                customer.setBalanceIqd(customer.getBalanceIqd() + amount);
                customer.setCurrentBalance(customer.getCurrentBalance() + amount);
            }
        } else {
            // PAYMENT و PURCHASE كلاهما يخصم من رصيد العميل/المورد
            if (isUsd) {
                customer.setBalanceUsd(customer.getBalanceUsd() - amount);
            } else {
                customer.setBalanceIqd(customer.getBalanceIqd() - amount);
                customer.setCurrentBalance(customer.getCurrentBalance() - amount);
            }
        }

        session.saveOrUpdate(customer);
    }

    private void reverseCustomerBalanceInSession(Session session, Voucher voucher) {
        if (voucher.getCustomer() == null || voucher.getCustomer().getId() == null) {
            return;
        }

        Customer customer = session.get(Customer.class, voucher.getCustomer().getId());
        if (customer == null) {
            throw new IllegalArgumentException("العميل غير موجود");
        }

        double amount = voucher.getNetAmount() != null ? voucher.getNetAmount() : 0.0;
        String currency = voucher.getCurrency();
        boolean isUsd = "دولار".equals(currency) || "USD".equalsIgnoreCase(currency);

        // عكس تأثير السند
        if (voucher.getVoucherType() == VoucherType.RECEIPT) {
            if (isUsd) {
                customer.setBalanceUsd(customer.getBalanceUsd() - amount);
            } else {
                customer.setBalanceIqd(customer.getBalanceIqd() - amount);
                customer.setCurrentBalance(customer.getCurrentBalance() - amount);
            }
        } else {
            if (isUsd) {
                customer.setBalanceUsd(customer.getBalanceUsd() + amount);
            } else {
                customer.setBalanceIqd(customer.getBalanceIqd() + amount);
                customer.setCurrentBalance(customer.getCurrentBalance() + amount);
            }
        }

        session.saveOrUpdate(customer);
    }

    private void addStockInSession(Session session, Long productId, Double quantity) {
        if (quantity == null || quantity <= 0) {
            return;
        }
        Product product = session.get(Product.class, productId);
        if (product == null) {
            throw new IllegalArgumentException("المنتج غير موجود");
        }
        double current = product.getQuantityInStock() == null ? 0.0 : product.getQuantityInStock();
        product.setQuantityInStock(current + quantity);
        session.saveOrUpdate(product);
    }
    
    private String generateDescription(Voucher voucher) {
        String typeName;
        if (voucher.getVoucherType() == VoucherType.RECEIPT) {
            typeName = "قبض من حساب";
        } else if (voucher.getVoucherType() == VoucherType.PURCHASE) {
            typeName = "مشتريات من";
        } else {
            typeName = "دفع لحساب";
        }
        String customerName = voucher.getCustomer() != null ? voucher.getCustomer().getName() : "نقدي";
        return typeName + " .. " + customerName;
    }
    
    private String convertAmountToWords(Double amount, String currency) {
        if (amount == null || amount == 0) return "صفر";
        
        long wholeNumber = amount.longValue();
        String currencyName = "دينار".equals(currency) ? "دينار عراقي" : "دولار أمريكي";
        
        return convertNumberToArabicWords(wholeNumber) + " " + currencyName + " لا غير";
    }
    
    private String convertNumberToArabicWords(long number) {
        if (number == 0) return "صفر";
        if (number < 0) return "سالب " + convertNumberToArabicWords(-number);
        
        String[] ones = {"", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة",
                "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر"};
        String[] tens = {"", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون"};
        
        if (number < 20) return ones[(int) number];
        if (number < 100) {
            int remainder = (int) (number % 10);
            if (remainder == 0) return tens[(int) (number / 10)];
            return ones[remainder] + " و" + tens[(int) (number / 10)];
        }
        if (number < 1000) {
            int hundreds = (int) (number / 100);
            int remainder = (int) (number % 100);
            String hundredWord = hundreds == 1 ? "مائة" : hundreds == 2 ? "مائتان" : ones[hundreds] + " مائة";
            if (remainder == 0) return hundredWord;
            return hundredWord + " و" + convertNumberToArabicWords(remainder);
        }
        if (number < 1000000) {
            int thousands = (int) (number / 1000);
            int remainder = (int) (number % 1000);
            String thousandWord;
            if (thousands == 1) thousandWord = "ألف";
            else if (thousands == 2) thousandWord = "ألفان";
            else if (thousands <= 10) thousandWord = ones[thousands] + " آلاف";
            else thousandWord = convertNumberToArabicWords(thousands) + " ألف";
            
            if (remainder == 0) return thousandWord;
            return thousandWord + " و" + convertNumberToArabicWords(remainder);
        }
        if (number < 1000000000) {
            int millions = (int) (number / 1000000);
            int remainder = (int) (number % 1000000);
            String millionWord;
            if (millions == 1) millionWord = "مليون";
            else if (millions == 2) millionWord = "مليونان";
            else if (millions <= 10) millionWord = ones[millions] + " ملايين";
            else millionWord = convertNumberToArabicWords(millions) + " مليون";
            
            if (remainder == 0) return millionWord;
            return millionWord + " و" + convertNumberToArabicWords(remainder);
        }
        
        return String.valueOf(number);
    }
    
    // ========== Inner class for stats ==========
    public static class VoucherStats {
        private int receiptCount;
        private double receiptTotal;
        private int paymentCount;
        private double paymentTotal;
        
        public int getReceiptCount() { return receiptCount; }
        public void setReceiptCount(int receiptCount) { this.receiptCount = receiptCount; }
        
        public double getReceiptTotal() { return receiptTotal; }
        public void setReceiptTotal(double receiptTotal) { this.receiptTotal = receiptTotal; }
        
        public int getPaymentCount() { return paymentCount; }
        public void setPaymentCount(int paymentCount) { this.paymentCount = paymentCount; }
        
        public double getPaymentTotal() { return paymentTotal; }
        public void setPaymentTotal(double paymentTotal) { this.paymentTotal = paymentTotal; }
        
        public double getNetFlow() { return receiptTotal - paymentTotal; }
    }
}
