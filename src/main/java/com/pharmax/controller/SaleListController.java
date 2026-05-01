package com.pharmax.controller;

import com.pharmax.model.Sale;
import com.pharmax.model.SaleItem;
import com.pharmax.service.ReceiptService;
import com.pharmax.service.SalesService;
import com.pharmax.util.TabManager;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SaleListController {
    private static final Logger logger = LoggerFactory.getLogger(SaleListController.class);

    @FXML private TextField searchField;
    @FXML private ComboBox<String> periodComboBox;
    @FXML private javafx.scene.control.DatePicker fromDatePicker;
    @FXML private javafx.scene.control.DatePicker toDatePicker;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private ComboBox<String> paymentMethodComboBox;
    @FXML private TextField minAmountField;
    @FXML private TextField maxAmountField;
    @FXML private TableView<Sale> salesTable;
    @FXML private TableColumn<Sale, String> saleCodeColumn;
    @FXML private TableColumn<Sale, String> customerColumn;
    @FXML private TableColumn<Sale, String> dateColumn;
    @FXML private TableColumn<Sale, Double> totalColumn;
    @FXML private TableColumn<Sale, Double> paidColumn;
    @FXML private TableColumn<Sale, Double> remainingColumn;
    @FXML private TableColumn<Sale, Integer> itemsCountColumn;
    @FXML private TableColumn<Sale, String> paymentMethodColumn;
    @FXML private TableColumn<Sale, String> statusColumn;
    @FXML private TableColumn<Sale, String> createdByColumn;
    @FXML private TableColumn<Sale, Void> actionsColumn;
    @FXML private Label totalSalesLabel;
    @FXML private Label totalAmountLabel;
    @FXML private Label paidAmountLabel;
    @FXML private Label pendingAmountLabel;
    @FXML private Label avgSaleLabel;
    @FXML private Label itemsCountLabel;

    private final SalesService salesService;
    private final ReceiptService receiptService;
    private ObservableList<Sale> allSales;
    private List<Sale> filteredSales = List.of();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private com.pharmax.MainApp mainApp;
    private boolean tabMode = false;
    private String tabId;

    public void setMainApp(com.pharmax.MainApp mainApp) {
        this.mainApp = mainApp;
    }

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    public void setTabId(String tabId) {
        this.tabId = tabId;
    }

    public SaleListController() {
        this.salesService = new SalesService();
        this.receiptService = new ReceiptService();
    }

    @FXML
    private void initialize() {
        setupTable();
        setupFilters();
        loadSales();
    }

    private void setupTable() {
        saleCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSaleCode()));
        customerColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCustomer() != null ? data.getValue().getCustomer().getName() : "-"));
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSaleDate() != null ? data.getValue().getSaleDate().format(dateFormatter) : "-"));
        totalColumn.setCellValueFactory(data -> new SimpleDoubleProperty(safeDouble(data.getValue().getFinalAmount())).asObject());
        paidColumn.setCellValueFactory(data -> new SimpleDoubleProperty(safeDouble(data.getValue().getPaidAmount())).asObject());
        remainingColumn.setCellValueFactory(data -> new SimpleDoubleProperty(getRemainingAmount(data.getValue())).asObject());
        itemsCountColumn.setCellValueFactory(data -> new SimpleIntegerProperty(getItemCount(data.getValue())).asObject());
        paymentMethodColumn.setCellValueFactory(data -> new SimpleStringProperty(
                getPaymentMethodArabic(data.getValue().getPaymentMethod())));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                getStatusArabic(data.getValue().getPaymentStatus())));
        createdByColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCreatedBy() != null && !data.getValue().getCreatedBy().isBlank()
                        ? data.getValue().getCreatedBy()
                        : "-"));

        setupCurrencyColumn(totalColumn);
        setupCurrencyColumn(paidColumn);
        setupCurrencyColumn(remainingColumn);

        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    switch (status) {
                        case "مدفوع" -> setStyle("-fx-text-fill: -fx-success-text; -fx-font-weight: bold;");
                        case "معلق" -> setStyle("-fx-text-fill: -fx-warning-text; -fx-font-weight: bold;");
                        case "متأخر" -> setStyle("-fx-text-fill: -fx-danger-text; -fx-font-weight: bold;");
                        default -> setStyle("");
                    }
                }
            }
        });

        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("👁");
            private final Button receiptBtn = new Button("🧾");
            private final Button payBtn = new Button("💰");
            private final HBox hbox = new HBox(5, viewBtn, receiptBtn, payBtn);

            {
                viewBtn.getStyleClass().add("action-btn-view");
                receiptBtn.getStyleClass().add("action-btn-purple");
                payBtn.getStyleClass().add("action-btn-success");

                viewBtn.setTooltip(new Tooltip("عرض التفاصيل"));
                receiptBtn.setTooltip(new Tooltip("طباعة الإيصال"));
                payBtn.setTooltip(new Tooltip("تحديث الدفع"));

                viewBtn.setOnAction(e -> handleViewSale(getTableView().getItems().get(getIndex())));
                receiptBtn.setOnAction(e -> handlePrintReceipt(getTableView().getItems().get(getIndex())));
                payBtn.setOnAction(e -> handleUpdatePayment(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : hbox);
            }
        });
    }

    private void setupFilters() {
        if (periodComboBox != null) {
            periodComboBox.setValue("هذا الشهر");
        }
        if (statusComboBox != null) {
            statusComboBox.setValue("الكل");
        }
        if (paymentMethodComboBox != null) {
            paymentMethodComboBox.setValue("الكل");
        }
        if (fromDatePicker != null) {
            fromDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        }
        if (toDatePicker != null) {
            toDatePicker.setValue(LocalDate.now());
        }
    }

    private void loadSales() {
        List<Sale> sales = salesService.getAllSales();
        allSales = FXCollections.observableArrayList(sales);
        applyFilters();
    }

    @FXML
    private void handleSearch() {
        applyFilters();
    }

    @FXML
    private void handlePeriodFilter() {
        String period = periodComboBox != null ? periodComboBox.getValue() : null;
        LocalDate now = LocalDate.now();
        if (period == null) {
            applyFilters();
            return;
        }

        switch (period) {
            case "اليوم" -> {
                fromDatePicker.setValue(now);
                toDatePicker.setValue(now);
            }
            case "هذا الأسبوع" -> {
                fromDatePicker.setValue(now.minusDays(6));
                toDatePicker.setValue(now);
            }
            case "هذا الشهر" -> {
                fromDatePicker.setValue(now.withDayOfMonth(1));
                toDatePicker.setValue(now);
            }
            case "هذه السنة" -> {
                fromDatePicker.setValue(now.withDayOfYear(1));
                toDatePicker.setValue(now);
            }
            case "كل الفترات" -> {
                fromDatePicker.setValue(null);
                toDatePicker.setValue(null);
            }
            case "مخصص" -> {
            }
            default -> {
            }
        }

        applyFilters();
    }

    @FXML
    private void handleDateFilter() {
        if (periodComboBox != null) {
            periodComboBox.setValue("مخصص");
        }
        applyFilters();
    }

    @FXML
    private void handleStatusFilter() {
        applyFilters();
    }

    @FXML
    private void handlePaymentMethodFilter() {
        applyFilters();
    }

    @FXML
    private void handleAmountFilter() {
        applyFilters();
    }

    private void applyFilters() {
        String searchText = safeLower(searchField != null ? searchField.getText() : "").trim();
        String statusFilter = statusComboBox != null ? statusComboBox.getValue() : null;
        String paymentMethodFilter = paymentMethodComboBox != null ? paymentMethodComboBox.getValue() : null;
        LocalDate fromDate = fromDatePicker != null ? fromDatePicker.getValue() : null;
        LocalDate toDate = toDatePicker != null ? toDatePicker.getValue() : null;
        Double minAmount = parseOptionalDouble(minAmountField != null ? minAmountField.getText() : null);
        Double maxAmount = parseOptionalDouble(maxAmountField != null ? maxAmountField.getText() : null);

        filteredSales = allSales.stream()
                .filter(sale -> {
                    if (!searchText.isEmpty()) {
                        boolean matchesCode = safeLower(sale.getSaleCode()).contains(searchText);
                        boolean matchesNotes = safeLower(sale.getNotes()).contains(searchText);
                        boolean matchesCreator = safeLower(sale.getCreatedBy()).contains(searchText);
                        if (!matchesCode && !matchesNotes && !matchesCreator) {
                            return false;
                        }
                    }

                    if (statusFilter != null && !"الكل".equals(statusFilter)) {
                        if (!statusFilter.equals(getStatusArabic(sale.getPaymentStatus()))) {
                            return false;
                        }
                    }

                    if (paymentMethodFilter != null && !"الكل".equals(paymentMethodFilter)) {
                        if (!paymentMethodFilter.equals(getPaymentMethodArabic(sale.getPaymentMethod()))) {
                            return false;
                        }
                    }

                    if (fromDate != null && sale.getSaleDate() != null
                            && sale.getSaleDate().toLocalDate().isBefore(fromDate)) {
                        return false;
                    }

                    if (toDate != null && sale.getSaleDate() != null
                            && sale.getSaleDate().toLocalDate().isAfter(toDate)) {
                        return false;
                    }

                    double finalAmount = safeDouble(sale.getFinalAmount());
                    if (minAmount != null && finalAmount < minAmount) {
                        return false;
                    }
                    if (maxAmount != null && finalAmount > maxAmount) {
                        return false;
                    }

                    return true;
                })
                .toList();

        salesTable.setItems(FXCollections.observableArrayList(filteredSales));
        updateSummaryForFiltered(filteredSales);
    }

    @FXML
    private void handleRefresh() {
        loadSales();
    }

    private void updateSummaryForFiltered(List<Sale> sales) {
        int totalCount = sales.size();
        double totalAmount = sales.stream().mapToDouble(s -> safeDouble(s.getFinalAmount())).sum();
        double paidAmount = sales.stream().mapToDouble(s -> safeDouble(s.getPaidAmount())).sum();
        double pendingAmount = sales.stream().mapToDouble(this::getRemainingAmount).sum();
        int totalItems = sales.stream().mapToInt(this::getItemCount).sum();
        double averageAmount = totalCount > 0 ? totalAmount / totalCount : 0;

        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        totalSalesLabel.setText(String.valueOf(totalCount));
        totalAmountLabel.setText(df.format(totalAmount) + " دينار");
        paidAmountLabel.setText(df.format(paidAmount) + " دينار");
        pendingAmountLabel.setText(df.format(pendingAmount) + " دينار");
        avgSaleLabel.setText(df.format(averageAmount) + " دينار");
        itemsCountLabel.setText(String.valueOf(totalItems));
    }

    private void handleViewSale(Sale sale) {
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        StringBuilder details = new StringBuilder();
        details.append("رقم الفاتورة: ").append(sale.getSaleCode()).append("\n");
        details.append("العميل: ").append(sale.getCustomer() != null ? sale.getCustomer().getName() : "-").append("\n");
        details.append("التاريخ: ").append(sale.getSaleDate() != null ? sale.getSaleDate().format(dateFormatter) : "-").append("\n");
        details.append("بواسطة: ").append(sale.getCreatedBy() != null ? sale.getCreatedBy() : "-").append("\n");
        details.append("طريقة الدفع: ").append(getPaymentMethodArabic(sale.getPaymentMethod())).append("\n");
        details.append("الحالة: ").append(getStatusArabic(sale.getPaymentStatus())).append("\n\n");
        details.append("المجموع: ").append(df.format(safeDouble(sale.getTotalAmount()))).append(" دينار\n");
        details.append("الخصم: ").append(df.format(safeDouble(sale.getDiscountAmount()))).append(" دينار\n");
        details.append("الإجمالي النهائي: ").append(df.format(safeDouble(sale.getFinalAmount()))).append(" دينار\n");
        details.append("المدفوع: ").append(df.format(safeDouble(sale.getPaidAmount()))).append(" دينار\n");
        details.append("المتبقي: ").append(df.format(getRemainingAmount(sale))).append(" دينار\n");
        details.append("عدد الأصناف: ").append(getItemCount(sale));

        if (sale.getSaleItems() != null && !sale.getSaleItems().isEmpty()) {
            details.append("\n\nالأصناف:\n");
            int index = 1;
            for (SaleItem item : sale.getSaleItems()) {
                String productName = item.getProduct() != null ? item.getProduct().getName() : "-";
                String soldUnit = item.getSoldUnit() != null && !item.getSoldUnit().isBlank() ? item.getSoldUnit() : "";
                details.append(index++).append(". ")
                        .append(productName)
                        .append(" | الكمية: ").append(df.format(safeDouble(item.getQuantity())))
                        .append(soldUnit.isBlank() ? "" : " " + soldUnit)
                        .append(" | السعر: ").append(df.format(safeDouble(item.getUnitPrice())))
                        .append(" | الإجمالي: ").append(df.format(safeDouble(item.getTotalPrice())))
                        .append("\n");
            }
        }

        if (sale.getNotes() != null && !sale.getNotes().isEmpty()) {
            details.append("\n\nملاحظات: ").append(sale.getNotes());
        }

        showInfo("تفاصيل الفاتورة", details.toString());
    }

    private void handlePrintReceipt(Sale sale) {
        try {
            var receipt = receiptService.generateReceipt(sale.getId(), "DEFAULT", "System");
            showSuccess("تم بنجاح", "تم إنشاء الإيصال بنجاح\nرقم الإيصال: " + receipt.getReceiptNumber());

            if (receipt.getFilePath() != null) {
                File pdfFile = new File(receipt.getFilePath());
                if (pdfFile.exists()) {
                    if (mainApp != null) {
                        mainApp.showPdfPreview(pdfFile);
                    } else if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(pdfFile);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to generate receipt", e);
            showError("خطأ", "فشل في إنشاء الإيصال: " + e.getMessage());
        }
    }

    private void handleUpdatePayment(Sale sale) {
        if ("PAID".equals(sale.getPaymentStatus())) {
            showInfo("معلومة", "هذه الفاتورة مدفوعة بالفعل");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("تأكيد الدفع");
        alert.setHeaderText("هل تريد تحديث حالة الدفع إلى 'مدفوع'؟");
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        alert.setContentText("رقم الفاتورة: " + sale.getSaleCode() + "\nالمبلغ: "
                + df.format(safeDouble(sale.getFinalAmount())) + " دينار");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    salesService.updatePaymentStatus(sale.getId(), "PAID");
                    loadSales();
                    showSuccess("تم بنجاح", "تم تحديث حالة الدفع بنجاح");
                } catch (Exception e) {
                    logger.error("Failed to update payment status", e);
                    showError("خطأ", "فشل في تحديث حالة الدفع");
                }
            }
        });
    }

    @FXML
    private void handleSalesReport() {
        Stage stage = new Stage();
        stage.setTitle("تقرير المبيعات التفصيلي");

        TextArea reportArea = new TextArea(buildDetailedReport());
        reportArea.setEditable(false);
        reportArea.setWrapText(true);
        reportArea.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        VBox.setVgrow(reportArea, Priority.ALWAYS);

        Button closeBtn = new Button("إغلاق");
        closeBtn.setOnAction(e -> stage.close());
        closeBtn.setStyle("-fx-background-color: -fx-scrollbar-thumb-hover; -fx-text-fill: white;");

        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12, reportArea, footer);
        root.setPadding(new Insets(14));
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        Scene scene = new Scene(root, 820, 640);
        stage.setScene(scene);
        stage.show();
    }

    @FXML
    private void handleExportExcel() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("تصدير عرض المبيعات إلى Excel");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
            fileChooser.setInitialFileName("sales_view_report.xlsx");
            File selectedFile = fileChooser.showSaveDialog(salesTable.getScene().getWindow());
            if (selectedFile == null) {
                return;
            }

            exportFilteredSalesToExcel(selectedFile);
            showSuccess("تم", "تم تصدير البيانات إلى:\n" + selectedFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to export sales Excel", e);
            showError("خطأ", "فشل في تصدير Excel: " + e.getMessage());
        }
    }

    @FXML
    private void handleClose() {
        if (tabMode && tabId != null && !tabId.isBlank()) {
            TabManager.getInstance().closeTab(tabId);
            return;
        }
        Stage stage = (Stage) salesTable.getScene().getWindow();
        stage.close();
    }

    private String getPaymentMethodArabic(String method) {
        if (method == null) {
            return "-";
        }
        return switch (method) {
            case "CASH" -> "نقدي";
            case "DEBT" -> "دين";
            case "CARD" -> "بطاقة";
            default -> method;
        };
    }

    private String getStatusArabic(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case "PAID" -> "مدفوع";
            case "PENDING" -> "معلق";
            case "OVERDUE" -> "متأخر";
            default -> status;
        };
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void setupCurrencyColumn(TableColumn<Sale, Double> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
                    setText(df.format(value));
                }
            }
        });
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private String safeLower(String value) {
        return value != null ? value.toLowerCase() : "";
    }

    private Double parseOptionalDouble(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double getRemainingAmount(Sale sale) {
        return Math.max(0, safeDouble(sale.getFinalAmount()) - safeDouble(sale.getPaidAmount()));
    }

    private int getItemCount(Sale sale) {
        return sale.getSaleItems() != null ? sale.getSaleItems().size() : 0;
    }

    private String buildDetailedReport() {
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        List<Sale> reportSource = filteredSales != null ? filteredSales : List.of();

        double totalAmount = reportSource.stream().mapToDouble(s -> safeDouble(s.getFinalAmount())).sum();
        double totalPaid = reportSource.stream().mapToDouble(s -> safeDouble(s.getPaidAmount())).sum();
        double totalRemaining = reportSource.stream().mapToDouble(this::getRemainingAmount).sum();
        double totalDiscount = reportSource.stream().mapToDouble(s -> safeDouble(s.getDiscountAmount())).sum();
        int invoices = reportSource.size();
        int items = reportSource.stream().mapToInt(this::getItemCount).sum();

        Map<String, Long> byStatus = reportSource.stream()
                .collect(Collectors.groupingBy(s -> getStatusArabic(s.getPaymentStatus()), LinkedHashMap::new, Collectors.counting()));
        Map<String, Double> byPaymentMethod = reportSource.stream()
                .collect(Collectors.groupingBy(s -> getPaymentMethodArabic(s.getPaymentMethod()), LinkedHashMap::new,
                        Collectors.summingDouble(s -> safeDouble(s.getFinalAmount()))));
        Map<String, Double> byCreator = reportSource.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getCreatedBy() != null && !s.getCreatedBy().isBlank() ? s.getCreatedBy() : "-",
                        LinkedHashMap::new,
                        Collectors.summingDouble(s -> safeDouble(s.getFinalAmount()))));

        Map<String, ProductAggregate> topProducts = new LinkedHashMap<>();
        for (Sale sale : reportSource) {
            if (sale.getSaleItems() == null) {
                continue;
            }
            for (SaleItem item : sale.getSaleItems()) {
                String productName = item.getProduct() != null ? item.getProduct().getName() : "غير معروف";
                topProducts.computeIfAbsent(productName, ignored -> new ProductAggregate())
                        .add(safeDouble(item.getQuantity()), safeDouble(item.getTotalPrice()));
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("تقرير عرض المبيعات\n");
        report.append("================================\n\n");
        report.append("عدد الفواتير: ").append(invoices).append("\n");
        report.append("إجمالي المبلغ: ").append(df.format(totalAmount)).append(" دينار\n");
        report.append("المدفوع: ").append(df.format(totalPaid)).append(" دينار\n");
        report.append("المتبقي: ").append(df.format(totalRemaining)).append(" دينار\n");
        report.append("الخصومات: ").append(df.format(totalDiscount)).append(" دينار\n");
        report.append("عدد الأصناف المباعة: ").append(items).append("\n\n");

        report.append("الفلاتر الحالية\n");
        report.append("--------------------------------\n");
        report.append("الفترة: ").append(periodComboBox != null ? periodComboBox.getValue() : "-").append("\n");
        report.append("الحالة: ").append(statusComboBox != null ? statusComboBox.getValue() : "-").append("\n");
        report.append("طريقة الدفع: ").append(paymentMethodComboBox != null ? paymentMethodComboBox.getValue() : "-").append("\n");
        report.append("البحث: ").append(searchField != null && !searchField.getText().isBlank() ? searchField.getText() : "-").append("\n\n");

        report.append("التوزيع حسب الحالة\n");
        report.append("--------------------------------\n");
        byStatus.forEach((status, count) -> report.append(status).append(": ").append(count).append("\n"));
        report.append("\n");

        report.append("التوزيع حسب طريقة الدفع\n");
        report.append("--------------------------------\n");
        byPaymentMethod.forEach((method, amount) -> report.append(method).append(": ").append(df.format(amount)).append(" دينار\n"));
        report.append("\n");

        report.append("الأكثر مبيعًا\n");
        report.append("--------------------------------\n");
        topProducts.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().amount, a.getValue().amount))
                .limit(10)
                .forEach(entry -> report.append(entry.getKey())
                        .append(" | كمية: ").append(df.format(entry.getValue().quantity))
                        .append(" | إجمالي: ").append(df.format(entry.getValue().amount)).append(" دينار\n"));
        report.append("\n");

        report.append("المبيعات حسب المستخدم\n");
        report.append("--------------------------------\n");
        byCreator.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> report.append(entry.getKey())
                        .append(": ").append(df.format(entry.getValue())).append(" دينار\n"));
        report.append("\n");

        report.append("أعلى الفواتير\n");
        report.append("--------------------------------\n");
        reportSource.stream()
                .sorted(Comparator.comparingDouble((Sale s) -> safeDouble(s.getFinalAmount())).reversed())
                .limit(10)
                .forEach(sale -> report.append(sale.getSaleCode())
                        .append(" | ").append(sale.getSaleDate() != null ? sale.getSaleDate().format(dateFormatter) : "-")
                        .append(" | ").append(df.format(safeDouble(sale.getFinalAmount()))).append(" دينار")
                        .append(" | ").append(getStatusArabic(sale.getPaymentStatus()))
                        .append("\n"));

        return report.toString();
    }

    private void exportFilteredSalesToExcel(File file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream out = new FileOutputStream(file)) {
            Sheet sheet = workbook.createSheet("Sales");
            String[] headers = {
                    "رقم الفاتورة", "التاريخ", "العميل", "طريقة الدفع", "الحالة",
                    "الإجمالي", "المدفوع", "المتبقي", "الخصم", "عدد الأصناف", "بواسطة", "ملاحظات"
            };

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            List<Sale> exportSales = filteredSales != null ? filteredSales : List.of();
            for (int i = 0; i < exportSales.size(); i++) {
                Sale sale = exportSales.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(sale.getSaleCode() != null ? sale.getSaleCode() : "");
                row.createCell(1).setCellValue(sale.getSaleDate() != null ? sale.getSaleDate().format(dateFormatter) : "");
                row.createCell(2).setCellValue(sale.getCustomer() != null ? sale.getCustomer().getName() : "-");
                row.createCell(3).setCellValue(getPaymentMethodArabic(sale.getPaymentMethod()));
                row.createCell(4).setCellValue(getStatusArabic(sale.getPaymentStatus()));
                row.createCell(5).setCellValue(safeDouble(sale.getFinalAmount()));
                row.createCell(6).setCellValue(safeDouble(sale.getPaidAmount()));
                row.createCell(7).setCellValue(getRemainingAmount(sale));
                row.createCell(8).setCellValue(safeDouble(sale.getDiscountAmount()));
                row.createCell(9).setCellValue(getItemCount(sale));
                row.createCell(10).setCellValue(sale.getCreatedBy() != null ? sale.getCreatedBy() : "");
                row.createCell(11).setCellValue(sale.getNotes() != null ? sale.getNotes() : "");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
        }
    }

    private static class ProductAggregate {
        private double quantity;
        private double amount;

        void add(double quantity, double amount) {
            this.quantity += quantity;
            this.amount += amount;
        }
    }
}
