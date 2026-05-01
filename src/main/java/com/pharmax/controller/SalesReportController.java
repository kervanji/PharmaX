package com.pharmax.controller;

import com.pharmax.model.Sale;
import com.pharmax.model.SaleItem;
import com.pharmax.service.SalesService;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.apache.poi.ss.usermodel.FillPatternType;

import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class SalesReportController {
    @FXML private ComboBox<String> periodComboBox;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Label totalSalesLabel;
    @FXML private Label invoiceCountLabel;
    @FXML private Label avgSaleLabel;
    @FXML private Label totalDiscountLabel;
    @FXML private VBox paymentBreakdownBox;
    @FXML private TableView<ProductStat> topProductsTable;
    @FXML private TableColumn<ProductStat, String> productNameColumn;
    @FXML private TableColumn<ProductStat, Double> quantitySoldColumn;
    @FXML private TableColumn<ProductStat, Double> productTotalColumn;
    @FXML private TableView<CustomerStat> topCustomersTable;
    @FXML private TableColumn<CustomerStat, String> customerNameColumn;
    @FXML private TableColumn<CustomerStat, Double> customerTotalColumn;

    private final SalesService salesService;
    private List<Sale> reportData;

    public SalesReportController() {
        this.salesService = new SalesService();
    }

    @FXML
    private void initialize() {
        setupTables();
        setupDefaults();
        handleGenerateReport();
    }

    private void setupTables() {
        productNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        quantitySoldColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getQuantitySold()).asObject());
        productTotalColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getTotalAmount()).asObject());
        quantitySoldColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatNumber(value));
            }
        });
        productTotalColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatNumber(value));
            }
        });

        customerNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCustomerName()));
        customerTotalColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getTotalAmount()).asObject());
        customerTotalColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatNumber(value));
            }
        });
    }

    private String formatNumber(double value) {
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        return df.format(value);
    }

    private void setupDefaults() {
        periodComboBox.setValue("الشهر");
        LocalDate now = LocalDate.now();
        fromDatePicker.setValue(now.withDayOfMonth(1));
        toDatePicker.setValue(now);
    }

    @FXML
    private void handlePeriodChange() {
        String period = periodComboBox.getValue();
        LocalDate now = LocalDate.now();

        switch (period) {
            case "اليوم" -> {
                fromDatePicker.setValue(now);
                toDatePicker.setValue(now);
            }
            case "الأسبوع" -> {
                fromDatePicker.setValue(now.minusWeeks(1));
                toDatePicker.setValue(now);
            }
            case "الشهر" -> {
                fromDatePicker.setValue(now.withDayOfMonth(1));
                toDatePicker.setValue(now);
            }
            case "السنة" -> {
                fromDatePicker.setValue(now.withDayOfYear(1));
                toDatePicker.setValue(now);
            }
            case "مخصص" -> {
                // Keep current values, let user customize
            }
        }

        if (!"مخصص".equals(period)) {
            handleGenerateReport();
        }
    }

    @FXML
    private void handleDateChange() {
        periodComboBox.setValue("مخصص");
    }

    @FXML
    private void handleGenerateReport() {
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();

        if (fromDate == null || toDate == null) {
            showError("خطأ", "الرجاء تحديد فترة التقرير");
            return;
        }

        if (fromDate.isAfter(toDate)) {
            showError("خطأ", "تاريخ البداية يجب أن يكون قبل تاريخ النهاية");
            return;
        }

        LocalDateTime startDateTime = fromDate.atStartOfDay();
        LocalDateTime endDateTime = toDate.atTime(23, 59, 59);

        reportData = salesService.getAllSales().stream()
                .filter(sale -> sale.getSaleDate() != null &&
                        !sale.getSaleDate().isBefore(startDateTime) &&
                        !sale.getSaleDate().isAfter(endDateTime))
                .toList();

        updateSummary();
        updatePaymentBreakdown();
        updateTopProducts();
        updateTopCustomers();
    }

    private void updateSummary() {
        double totalSales = reportData.stream().mapToDouble(Sale::getFinalAmount).sum();
        int invoiceCount = reportData.size();
        double avgSale = invoiceCount > 0 ? totalSales / invoiceCount : 0;
        double totalDiscount = reportData.stream().mapToDouble(Sale::getDiscountAmount).sum();
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        totalSalesLabel.setText(df.format(totalSales));
        invoiceCountLabel.setText(String.valueOf(invoiceCount));
        avgSaleLabel.setText(df.format(avgSale));
        totalDiscountLabel.setText(df.format(totalDiscount));
    }

    private void updatePaymentBreakdown() {
        paymentBreakdownBox.getChildren().clear();

        Map<String, Double> paymentTotals = reportData.stream()
                .collect(Collectors.groupingBy(
                        sale -> getPaymentMethodArabic(sale.getPaymentMethod()),
                        Collectors.summingDouble(Sale::getFinalAmount)
                ));

        double total = reportData.stream().mapToDouble(Sale::getFinalAmount).sum();

        for (Map.Entry<String, Double> entry : paymentTotals.entrySet()) {
            double percentage = total > 0 ? (entry.getValue() / total) * 100 : 0;

            HBox row = new HBox(10);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label methodLabel = new Label(entry.getKey());
            methodLabel.setPrefWidth(80);
            methodLabel.setStyle("-fx-font-weight: bold;");

            ProgressBar progressBar = new ProgressBar(percentage / 100);
            progressBar.setPrefWidth(120);
            progressBar.setStyle("-fx-accent: " + getColorForMethod(entry.getKey()) + ";");

            java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
            Label valueLabel = new Label(df.format(entry.getValue()) + " دينار (" + String.format("%.1f%%", percentage) + ")");
            valueLabel.setStyle("-fx-text-fill: -fx-text-hint;");

            row.getChildren().addAll(methodLabel, progressBar, valueLabel);
            paymentBreakdownBox.getChildren().add(row);
        }

        if (paymentTotals.isEmpty()) {
            Label noDataLabel = new Label("لا توجد بيانات");
            noDataLabel.setStyle("-fx-text-fill: -fx-text-muted;");
            paymentBreakdownBox.getChildren().add(noDataLabel);
        }
    }

    private void updateTopProducts() {
        Map<String, ProductStat> productStats = new HashMap<>();

        for (Sale sale : reportData) {
            if (sale.getSaleItems() != null) {
                for (SaleItem item : sale.getSaleItems()) {
                    String productName = item.getProduct() != null ? item.getProduct().getName() : "غير معروف";
                    productStats.computeIfAbsent(productName, k -> new ProductStat(productName))
                            .addSale(item.getQuantity(), item.getTotalPrice());
                }
            }
        }

        List<ProductStat> topProducts = productStats.values().stream()
                .sorted(Comparator.comparingDouble(ProductStat::getTotalAmount).reversed())
                .limit(10)
                .toList();

        topProductsTable.setItems(FXCollections.observableArrayList(topProducts));
    }

    private void updateTopCustomers() {
        Map<String, CustomerStat> customerStats = new HashMap<>();

        for (Sale sale : reportData) {
            String customerName = sale.getCustomer() != null ? sale.getCustomer().getName() : "غير معروف";
            customerStats.computeIfAbsent(customerName, k -> new CustomerStat(customerName))
                    .addSale(sale.getFinalAmount());
        }

        List<CustomerStat> topCustomers = customerStats.values().stream()
                .sorted(Comparator.comparingDouble(CustomerStat::getTotalAmount).reversed())
                .limit(10)
                .toList();

        topCustomersTable.setItems(FXCollections.observableArrayList(topCustomers));
    }

    private String getPaymentMethodArabic(String method) {
        if (method == null) return "غير محدد";
        return switch (method) {
            case "CASH" -> "نقدي";
            case "DEBT" -> "دين";
            case "CARD" -> "بطاقة";
            default -> method;
        };
    }

    private String getColorForMethod(String method) {
        return switch (method) {
            case "نقدي" -> "-fx-success-text";
            case "دين" -> "-fx-danger-text";
            case "بطاقة" -> "-fx-accent-text";
            default -> "-fx-text-muted";
        };
    }

    @FXML
    private void handleExportPDF() {
        try {
            if (reportData == null) {
                showError("خطأ", "الرجاء إنشاء التقرير أولاً");
                return;
            }

            File selectedFile = showSaveDialog("حفظ تقرير المبيعات (PDF)", "sales_report.pdf", "PDF", "*.pdf");
            if (selectedFile == null) {
                return;
            }

            generateSalesReportPdf(selectedFile);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(selectedFile);
            }
            showInfo("تم", "تم تصدير تقرير المبيعات (PDF):\n" + selectedFile.getAbsolutePath());
        } catch (Exception e) {
            showError("خطأ", "فشل في تصدير PDF: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportExcel() {
        try {
            if (reportData == null) {
                showError("خطأ", "الرجاء إنشاء التقرير أولاً");
                return;
            }

            File selectedFile = showSaveDialog("حفظ تقرير المبيعات (Excel)", "sales_report.xlsx", "Excel", "*.xlsx");
            if (selectedFile == null) {
                return;
            }

            generateSalesReportExcel(selectedFile);
            showInfo("تم", "تم تصدير تقرير المبيعات (Excel):\n" + selectedFile.getAbsolutePath());
        } catch (Exception e) {
            showError("خطأ", "فشل في تصدير Excel: " + e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private String generateTextReport() {
        StringBuilder report = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        report.append("=== تقرير المبيعات ===\n\n");
        report.append("الفترة: ").append(fromDatePicker.getValue().format(formatter))
                .append(" إلى ").append(toDatePicker.getValue().format(formatter)).append("\n\n");

        report.append("--- الملخص ---\n");
        report.append("إجمالي المبيعات: ").append(totalSalesLabel.getText()).append(" دينار\n");
        report.append("عدد الفواتير: ").append(invoiceCountLabel.getText()).append("\n");
        report.append("متوسط الفاتورة: ").append(avgSaleLabel.getText()).append(" دينار\n");
        report.append("إجمالي الخصومات: ").append(totalDiscountLabel.getText()).append(" دينار\n");

        return report.toString();
    }

    private File showSaveDialog(String title, String initialFileName, String filterName, String filterPattern) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterName, filterPattern));
        fileChooser.setInitialFileName(initialFileName);

        Stage owner = (Stage) topProductsTable.getScene().getWindow();
        return fileChooser.showSaveDialog(owner);
    }

    private void generateSalesReportPdf(File outputFile) throws Exception {
        if (outputFile == null) {
            throw new IllegalArgumentException("مسار الملف غير صحيح");
        }

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        PdfWriter.getInstance(document, new FileOutputStream(outputFile));
        document.open();

        BaseFont baseFont = loadArabicBaseFont();
        Font titleFont = new Font(baseFont, 16, Font.BOLD);
        Font headerFont = new Font(baseFont, 11, Font.BOLD);
        Font bodyFont = new Font(baseFont, 10, Font.NORMAL);

        addCenteredRtlText(document, "تقرير المبيعات", titleFont, 4f, 10f);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        addCenteredRtlText(
                document,
                "الفترة: " + fromDatePicker.getValue().format(formatter) + " إلى " + toDatePicker.getValue().format(formatter),
                bodyFont,
                0f,
                10f
        );

        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(60);
        summary.setHorizontalAlignment(Element.ALIGN_CENTER);
        summary.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        summary.setSpacingAfter(10f);
        addSummaryRow(summary, "إجمالي المبيعات:", totalSalesLabel.getText() + " دينار", headerFont, bodyFont);
        addSummaryRow(summary, "عدد الفواتير:", invoiceCountLabel.getText(), headerFont, bodyFont);
        addSummaryRow(summary, "متوسط الفاتورة:", avgSaleLabel.getText() + " دينار", headerFont, bodyFont);
        addSummaryRow(summary, "إجمالي الخصومات:", totalDiscountLabel.getText() + " دينار", headerFont, bodyFont);
        document.add(summary);

        PdfPTable topProducts = new PdfPTable(4);
        topProducts.setWidthPercentage(100);
        topProducts.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        topProducts.setSpacingBefore(5f);
        topProducts.setSpacingAfter(10f);
        topProducts.setWidths(new float[]{0.4f, 2.0f, 0.8f, 1.0f});

        addTableHeader(topProducts, "ت", headerFont);
        addTableHeader(topProducts, "المنتج", headerFont);
        addTableHeader(topProducts, "الكمية", headerFont);
        addTableHeader(topProducts, "الإجمالي", headerFont);

        List<ProductStat> products = topProductsTable.getItems() != null ? topProductsTable.getItems() : FXCollections.observableArrayList();
        int i = 1;
        for (ProductStat p : products) {
            topProducts.addCell(createBodyCell(String.valueOf(i++), bodyFont, Element.ALIGN_CENTER));
            topProducts.addCell(createBodyCell(p.getProductName(), bodyFont, Element.ALIGN_RIGHT));
            topProducts.addCell(createBodyCell(formatNumber(p.getQuantitySold()), bodyFont, Element.ALIGN_CENTER));
            topProducts.addCell(createBodyCell(formatNumber(p.getTotalAmount()), bodyFont, Element.ALIGN_CENTER));
        }
        addCenteredRtlText(document, "المنتجات الأكثر مبيعاً", headerFont, 6f, 6f);
        document.add(topProducts);

        PdfPTable topCustomers = new PdfPTable(3);
        topCustomers.setWidthPercentage(100);
        topCustomers.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        topCustomers.setSpacingBefore(5f);
        topCustomers.setSpacingAfter(10f);
        topCustomers.setWidths(new float[]{0.4f, 2.0f, 1.0f});

        addTableHeader(topCustomers, "ت", headerFont);
        addTableHeader(topCustomers, "العميل", headerFont);
        addTableHeader(topCustomers, "الإجمالي", headerFont);

        List<CustomerStat> customers = topCustomersTable.getItems() != null ? topCustomersTable.getItems() : FXCollections.observableArrayList();
        int j = 1;
        for (CustomerStat c : customers) {
            topCustomers.addCell(createBodyCell(String.valueOf(j++), bodyFont, Element.ALIGN_CENTER));
            topCustomers.addCell(createBodyCell(c.getCustomerName(), bodyFont, Element.ALIGN_RIGHT));
            topCustomers.addCell(createBodyCell(formatNumber(c.getTotalAmount()), bodyFont, Element.ALIGN_CENTER));
        }
        addCenteredRtlText(document, "أفضل العملاء", headerFont, 6f, 6f);
        document.add(topCustomers);

        document.close();
    }

    private void addCenteredRtlText(Document document, String text, Font font, float spacingBefore, float spacingAfter) throws Exception {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingBefore(spacingBefore);
        paragraph.setSpacingAfter(spacingAfter);

        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        wrapper.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.addElement(paragraph);
        wrapper.addCell(cell);

        document.add(wrapper);
    }

    private void generateSalesReportExcel(File outputFile) throws Exception {
        if (outputFile == null) {
            throw new IllegalArgumentException("مسار الملف غير صحيح");
        }

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Sales Report");

            XSSFFont titleFont = (XSSFFont) workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle titleStyle = (XSSFCellStyle) workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            XSSFFont headerFont = (XSSFFont) workbook.createFont();
            headerFont.setBold(true);

            XSSFCellStyle headerStyle = (XSSFCellStyle) workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowIdx = 0;
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.createCell(0).setCellValue("تقرير المبيعات");
            titleRow.getCell(0).setCellStyle(titleStyle);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            Row periodRow = sheet.createRow(rowIdx++);
            periodRow.createCell(0).setCellValue("الفترة: " + fromDatePicker.getValue().format(formatter) + " إلى " + toDatePicker.getValue().format(formatter));

            rowIdx++;

            Row summaryHeader = sheet.createRow(rowIdx++);
            summaryHeader.createCell(0).setCellValue("الملخص");
            summaryHeader.getCell(0).setCellStyle(headerStyle);

            rowIdx = writeKeyValue(sheet, rowIdx, "إجمالي المبيعات", totalSalesLabel.getText() + " دينار");
            rowIdx = writeKeyValue(sheet, rowIdx, "عدد الفواتير", invoiceCountLabel.getText());
            rowIdx = writeKeyValue(sheet, rowIdx, "متوسط الفاتورة", avgSaleLabel.getText() + " دينار");
            rowIdx = writeKeyValue(sheet, rowIdx, "إجمالي الخصومات", totalDiscountLabel.getText() + " دينار");

            rowIdx++;

            Row prodTitle = sheet.createRow(rowIdx++);
            prodTitle.createCell(0).setCellValue("المنتجات الأكثر مبيعاً");
            prodTitle.getCell(0).setCellStyle(headerStyle);

            Row prodHeader = sheet.createRow(rowIdx++);
            String[] prodCols = new String[]{"#", "المنتج", "الكمية", "الإجمالي"};
            for (int c = 0; c < prodCols.length; c++) {
                prodHeader.createCell(c).setCellValue(prodCols[c]);
                prodHeader.getCell(c).setCellStyle(headerStyle);
            }

            List<ProductStat> products = topProductsTable.getItems() != null ? topProductsTable.getItems() : FXCollections.observableArrayList();
            int i = 1;
            for (ProductStat p : products) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(i++);
                r.createCell(1).setCellValue(p.getProductName());
                r.createCell(2).setCellValue(p.getQuantitySold());
                r.createCell(3).setCellValue(p.getTotalAmount());
            }

            rowIdx++;

            Row custTitle = sheet.createRow(rowIdx++);
            custTitle.createCell(0).setCellValue("أفضل العملاء");
            custTitle.getCell(0).setCellStyle(headerStyle);

            Row custHeader = sheet.createRow(rowIdx++);
            String[] custCols = new String[]{"#", "العميل", "الإجمالي"};
            for (int c = 0; c < custCols.length; c++) {
                custHeader.createCell(c).setCellValue(custCols[c]);
                custHeader.getCell(c).setCellStyle(headerStyle);
            }

            List<CustomerStat> customers = topCustomersTable.getItems() != null ? topCustomersTable.getItems() : FXCollections.observableArrayList();
            int j = 1;
            for (CustomerStat c : customers) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(j++);
                r.createCell(1).setCellValue(c.getCustomerName());
                r.createCell(2).setCellValue(c.getTotalAmount());
            }

            for (int col = 0; col < 6; col++) {
                sheet.autoSizeColumn(col);
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }
        }
    }

    private int writeKeyValue(org.apache.poi.ss.usermodel.Sheet sheet, int rowIdx, String key, String value) {
        Row r = sheet.createRow(rowIdx++);
        r.createCell(0).setCellValue(key);
        r.createCell(1).setCellValue(value);
        return rowIdx;
    }

    private BaseFont loadArabicBaseFont() throws Exception {
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

        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
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
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setPadding(4f);
        return cell;
    }

    private void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        labelCell.setPadding(5f);
        labelCell.setBorder(PdfPCell.NO_BORDER);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        valueCell.setPadding(5f);
        valueCell.setBorder(PdfPCell.NO_BORDER);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) topProductsTable.getScene().getWindow();
        stage.close();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleAlert(alert);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleAlert(alert);
        alert.showAndWait();
    }

    private void styleAlert(Alert alert) {
        if (alert == null || alert.getDialogPane() == null) {
            return;
        }

        String css = getClass().getResource("/styles/main.css") != null
                ? getClass().getResource("/styles/main.css").toExternalForm()
                : null;
        if (css != null && !alert.getDialogPane().getStylesheets().contains(css)) {
            alert.getDialogPane().getStylesheets().add(css);
        }
        alert.getDialogPane().setStyle(
                "-fx-font-family: 'Geeza Pro', 'SF Arabic', 'Arial', 'Tahoma';"
        );
    }

    public static class ProductStat {
        private final String productName;
        private double quantitySold = 0;
        private double totalAmount = 0;

        public ProductStat(String productName) {
            this.productName = productName;
        }

        public void addSale(double quantity, double amount) {
            this.quantitySold += quantity;
            this.totalAmount += amount;
        }

        public String getProductName() { return productName; }
        public double getQuantitySold() { return quantitySold; }
        public double getTotalAmount() { return totalAmount; }
    }

    public static class CustomerStat {
        private final String customerName;
        private double totalAmount = 0;

        public CustomerStat(String customerName) {
            this.customerName = customerName;
        }

        public void addSale(double amount) {
            this.totalAmount += amount;
        }

        public String getCustomerName() { return customerName; }
        public double getTotalAmount() { return totalAmount; }
    }
}
