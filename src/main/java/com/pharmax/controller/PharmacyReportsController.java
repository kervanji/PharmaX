package com.pharmax.controller;

import com.pharmax.model.Customer;
import com.pharmax.service.PharmacyReportExportService;
import com.pharmax.service.PharmacyReportService;
import com.pharmax.util.TabManager;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PharmacyReportsController {
    @FXML private TextField stockSearchField;
    @FXML private ComboBox<String> stockExpiryFilterComboBox;
    @FXML private TableView<PharmacyReportService.StockByBatchRow> stockByBatchTable;
    @FXML private TableColumn<PharmacyReportService.StockByBatchRow, String> stockProductColumn;
    @FXML private TableColumn<PharmacyReportService.StockByBatchRow, String> stockCodeColumn;
    @FXML private TableColumn<PharmacyReportService.StockByBatchRow, String> stockBarcodeColumn;
    @FXML private TableColumn<PharmacyReportService.StockByBatchRow, String> stockBatchColumn;
    @FXML private TableColumn<PharmacyReportService.StockByBatchRow, String> stockExpiryColumn;
    @FXML private TableColumn<PharmacyReportService.StockByBatchRow, Double> stockQtyColumn;
    @FXML private TableColumn<PharmacyReportService.StockByBatchRow, Double> stockCostColumn;
    @FXML private TableColumn<PharmacyReportService.StockByBatchRow, Double> stockSalePriceColumn;
    @FXML private TableColumn<PharmacyReportService.StockByBatchRow, String> stockStatusColumn;
    @FXML private TableColumn<PharmacyReportService.StockByBatchRow, String> stockSourceColumn;
    @FXML private Label stockCountLabel;

    @FXML private ComboBox<String> expiryFilterComboBox;
    @FXML private TableView<PharmacyReportService.ExpiryReportRow> expiryTable;
    @FXML private TableColumn<PharmacyReportService.ExpiryReportRow, String> expiryProductColumn;
    @FXML private TableColumn<PharmacyReportService.ExpiryReportRow, String> expiryCodeColumn;
    @FXML private TableColumn<PharmacyReportService.ExpiryReportRow, String> expiryBatchColumn;
    @FXML private TableColumn<PharmacyReportService.ExpiryReportRow, String> expiryDateColumn;
    @FXML private TableColumn<PharmacyReportService.ExpiryReportRow, Long> expiryDaysColumn;
    @FXML private TableColumn<PharmacyReportService.ExpiryReportRow, Double> expiryQtyColumn;
    @FXML private TableColumn<PharmacyReportService.ExpiryReportRow, String> expiryStatusColumn;
    @FXML private Label expiryCountLabel;

    @FXML private DatePicker movementFromDatePicker;
    @FXML private DatePicker movementToDatePicker;
    @FXML private TextField movementSearchField;
    @FXML private ComboBox<String> movementTypeComboBox;
    @FXML private TableView<PharmacyReportService.MovementReportRow> movementsTable;
    @FXML private TableColumn<PharmacyReportService.MovementReportRow, String> movementDateColumn;
    @FXML private TableColumn<PharmacyReportService.MovementReportRow, String> movementProductColumn;
    @FXML private TableColumn<PharmacyReportService.MovementReportRow, String> movementBatchColumn;
    @FXML private TableColumn<PharmacyReportService.MovementReportRow, String> movementTypeColumn;
    @FXML private TableColumn<PharmacyReportService.MovementReportRow, Double> movementBeforeColumn;
    @FXML private TableColumn<PharmacyReportService.MovementReportRow, Double> movementChangedColumn;
    @FXML private TableColumn<PharmacyReportService.MovementReportRow, Double> movementAfterColumn;
    @FXML private TableColumn<PharmacyReportService.MovementReportRow, String> movementRefTypeColumn;
    @FXML private TableColumn<PharmacyReportService.MovementReportRow, Long> movementRefIdColumn;
    @FXML private TableColumn<PharmacyReportService.MovementReportRow, String> movementNoteColumn;
    @FXML private Label movementCountLabel;

    @FXML private DatePicker purchaseFromDatePicker;
    @FXML private DatePicker purchaseToDatePicker;
    @FXML private ComboBox<Customer> purchaseSupplierComboBox;
    @FXML private TextField purchaseSearchField;
    @FXML private TableView<PharmacyReportService.PurchaseReportRow> purchasesTable;
    @FXML private TableColumn<PharmacyReportService.PurchaseReportRow, String> purchaseVoucherColumn;
    @FXML private TableColumn<PharmacyReportService.PurchaseReportRow, String> purchaseDateColumn;
    @FXML private TableColumn<PharmacyReportService.PurchaseReportRow, String> purchaseSupplierColumn;
    @FXML private TableColumn<PharmacyReportService.PurchaseReportRow, String> purchaseProductColumn;
    @FXML private TableColumn<PharmacyReportService.PurchaseReportRow, Double> purchaseQtyColumn;
    @FXML private TableColumn<PharmacyReportService.PurchaseReportRow, Double> purchaseAmountColumn;
    @FXML private TableColumn<PharmacyReportService.PurchaseReportRow, String> purchaseBatchColumn;
    @FXML private TableColumn<PharmacyReportService.PurchaseReportRow, String> purchaseExpiryColumn;
    @FXML private Label purchaseCountLabel;

    @FXML private Label profitStatusLabel;

    @FXML private DatePicker cashboxFromDatePicker;
    @FXML private DatePicker cashboxToDatePicker;
    @FXML private TableView<PharmacyReportService.CashboxReportRow> cashboxTable;
    @FXML private TableColumn<PharmacyReportService.CashboxReportRow, String> cashboxDateColumn;
    @FXML private TableColumn<PharmacyReportService.CashboxReportRow, Double> cashboxOpeningColumn;
    @FXML private TableColumn<PharmacyReportService.CashboxReportRow, Double> cashboxInColumn;
    @FXML private TableColumn<PharmacyReportService.CashboxReportRow, Double> cashboxOutColumn;
    @FXML private TableColumn<PharmacyReportService.CashboxReportRow, Double> cashboxExpectedColumn;
    @FXML private TableColumn<PharmacyReportService.CashboxReportRow, Double> cashboxActualColumn;
    @FXML private TableColumn<PharmacyReportService.CashboxReportRow, Double> cashboxDifferenceColumn;
    @FXML private TableColumn<PharmacyReportService.CashboxReportRow, String> cashboxStatusColumn;
    @FXML private Label cashboxCountLabel;

    @FXML private DatePicker velocityFromDatePicker;
    @FXML private DatePicker velocityToDatePicker;
    @FXML private TextField velocitySearchField;
    @FXML private TableView<PharmacyReportService.SalesVelocityRow> bestSellingTable;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, String> bestProductColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, String> bestCodeColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, String> bestCategoryColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, Double> bestQtyColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, Double> bestRevenueColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, String> bestLastSaleColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, Double> bestStockColumn;
    @FXML private Label bestSellingCountLabel;

    @FXML private TableView<PharmacyReportService.SalesVelocityRow> slowMovingTable;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, String> slowProductColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, String> slowCodeColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, String> slowCategoryColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, Double> slowQtyColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, Double> slowRevenueColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, String> slowLastSaleColumn;
    @FXML private TableColumn<PharmacyReportService.SalesVelocityRow, Double> slowStockColumn;
    @FXML private Label slowMovingCountLabel;

    private final PharmacyReportService reportService = new PharmacyReportService();
    private final PharmacyReportExportService exportService = new PharmacyReportExportService();
    private final DecimalFormat numberFormat = new DecimalFormat("#,##0.##");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private boolean tabMode = false;
    private String tabId;

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    public void setTabId(String tabId) {
        this.tabId = tabId;
    }

    @FXML
    private void initialize() {
        setupStockTab();
        setupExpiryTab();
        setupMovementTab();
        setupPurchaseTab();
        setupProfitTab();
        setupCashboxTab();
        setupVelocityTabs();

        loadPurchaseSuppliers();
        handleRefreshStock();
        handleRefreshExpiry();
        handleRefreshMovements();
        handleRefreshPurchases();
        handleRefreshCashbox();
        handleRefreshVelocity();
    }

    private void setupStockTab() {
        stockExpiryFilterComboBox.setItems(FXCollections.observableArrayList("ALL", "NON_EXPIRED", "EXPIRED"));
        stockExpiryFilterComboBox.setValue("ALL");

        stockProductColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().productName()));
        stockCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().productCode()));
        stockBarcodeColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().barcode())));
        stockBatchColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().batchNumber()));
        stockExpiryColumn.setCellValueFactory(data -> new SimpleStringProperty(formatDate(data.getValue().expiryDate())));
        stockQtyColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().quantity()));
        stockCostColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().unitCost()));
        stockSalePriceColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().salePrice()));
        stockStatusColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().status())));
        stockSourceColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().sourceReference())));
        setupNumberColumn(stockQtyColumn);
        setupNumberColumn(stockCostColumn);
        setupNumberColumn(stockSalePriceColumn);
    }

    private void setupExpiryTab() {
        expiryFilterComboBox.setItems(FXCollections.observableArrayList("ALL", "EXPIRED", "30", "60", "90"));
        expiryFilterComboBox.setValue("ALL");

        expiryProductColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().productName()));
        expiryCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().productCode()));
        expiryBatchColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().batchNumber()));
        expiryDateColumn.setCellValueFactory(data -> new SimpleStringProperty(formatDate(data.getValue().expiryDate())));
        expiryDaysColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().daysRemaining()));
        expiryQtyColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().quantity()));
        expiryStatusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status()));
        setupNumberColumn(expiryQtyColumn);
    }

    private void setupMovementTab() {
        movementFromDatePicker.setValue(LocalDate.now().minusDays(30));
        movementToDatePicker.setValue(LocalDate.now());
        movementTypeComboBox.setItems(FXCollections.observableArrayList(
                "ALL", "purchase", "sale", "sale_return", "purchase_return", "migration_opening_balance"));
        movementTypeComboBox.setValue("ALL");

        movementDateColumn.setCellValueFactory(data -> new SimpleStringProperty(formatDateTime(data.getValue().movementDate())));
        movementProductColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().productName()));
        movementBatchColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().batchNumber())));
        movementTypeColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().movementType())));
        movementBeforeColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().quantityBefore()));
        movementChangedColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().quantityChanged()));
        movementAfterColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().quantityAfter()));
        movementRefTypeColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().referenceType())));
        movementRefIdColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().referenceId()));
        movementNoteColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().note())));
        setupNumberColumn(movementBeforeColumn);
        setupNumberColumn(movementChangedColumn);
        setupNumberColumn(movementAfterColumn);
    }

    private void setupPurchaseTab() {
        purchaseFromDatePicker.setValue(LocalDate.now().minusDays(30));
        purchaseToDatePicker.setValue(LocalDate.now());

        purchaseSupplierComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Customer customer) {
                if (customer == null) {
                    return "الكل";
                }
                return customer.getCustomerCode() + " - " + customer.getName();
            }

            @Override
            public Customer fromString(String string) {
                return null;
            }
        });

        purchaseVoucherColumn.setCellValueFactory(data -> new SimpleStringProperty(
                safe(data.getValue().voucherNumber()) + " (#" + data.getValue().voucherId() + ")"));
        purchaseDateColumn.setCellValueFactory(data -> new SimpleStringProperty(formatDateTime(data.getValue().voucherDate())));
        purchaseSupplierColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().supplierName()));
        purchaseProductColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().productName()));
        purchaseQtyColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().quantity()));
        purchaseAmountColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().amount()));
        purchaseBatchColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().batchNumber())));
        purchaseExpiryColumn.setCellValueFactory(data -> new SimpleStringProperty(formatDate(data.getValue().expirationDate())));
        setupNumberColumn(purchaseQtyColumn);
        setupNumberColumn(purchaseAmountColumn);
    }

    private void setupProfitTab() {
        PharmacyReportService.ProfitReportStatus status = reportService.evaluateProfitReportReliability();
        profitStatusLabel.setText(status.message());
        profitStatusLabel.setStyle(status.reliable()
                ? "-fx-text-fill: -fx-success-text; -fx-font-size: 14px;"
                : "-fx-text-fill: -fx-warning-dark; -fx-font-size: 14px;");
    }

    private void setupCashboxTab() {
        cashboxFromDatePicker.setValue(LocalDate.now().minusDays(30));
        cashboxToDatePicker.setValue(LocalDate.now());

        cashboxDateColumn.setCellValueFactory(data -> new SimpleStringProperty(formatDate(data.getValue().date())));
        cashboxOpeningColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().openingCash()));
        cashboxInColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().totalIn()));
        cashboxOutColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().totalOut()));
        cashboxExpectedColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().expectedCash()));
        cashboxActualColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().actualCash()));
        cashboxDifferenceColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().difference()));
        cashboxStatusColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().status())));
        setupNumberColumn(cashboxOpeningColumn);
        setupNumberColumn(cashboxInColumn);
        setupNumberColumn(cashboxOutColumn);
        setupNumberColumn(cashboxExpectedColumn);
        setupNumberColumn(cashboxActualColumn);
        setupNumberColumn(cashboxDifferenceColumn);
    }

    private void setupVelocityTabs() {
        velocityFromDatePicker.setValue(LocalDate.now().minusDays(30));
        velocityToDatePicker.setValue(LocalDate.now());

        bestProductColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().productName()));
        bestCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().productCode())));
        bestCategoryColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().category())));
        bestQtyColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().quantitySold()));
        bestRevenueColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().revenue()));
        bestLastSaleColumn.setCellValueFactory(data -> new SimpleStringProperty(formatDateTime(data.getValue().lastSaleDate())));
        bestStockColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().currentStock()));
        setupNumberColumn(bestQtyColumn);
        setupNumberColumn(bestRevenueColumn);
        setupNumberColumn(bestStockColumn);

        slowProductColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().productName()));
        slowCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().productCode())));
        slowCategoryColumn.setCellValueFactory(data -> new SimpleStringProperty(safe(data.getValue().category())));
        slowQtyColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().quantitySold()));
        slowRevenueColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().revenue()));
        slowLastSaleColumn.setCellValueFactory(data -> new SimpleStringProperty(formatDateTime(data.getValue().lastSaleDate())));
        slowStockColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().currentStock()));
        setupNumberColumn(slowQtyColumn);
        setupNumberColumn(slowRevenueColumn);
        setupNumberColumn(slowStockColumn);
    }

    private void loadPurchaseSuppliers() {
        List<Customer> suppliers = reportService.getPurchaseSuppliers();
        purchaseSupplierComboBox.setItems(FXCollections.observableArrayList(suppliers));
    }

    @FXML
    private void handleRefreshStock() {
        List<PharmacyReportService.StockByBatchRow> rows = reportService.getStockByBatchRows(
                stockSearchField.getText(),
                stockExpiryFilterComboBox.getValue());
        stockByBatchTable.setItems(FXCollections.observableArrayList(rows));
        stockCountLabel.setText(String.valueOf(rows.size()));
    }

    @FXML
    private void handleRefreshExpiry() {
        List<PharmacyReportService.ExpiryReportRow> rows = reportService.getExpiryRows(expiryFilterComboBox.getValue());
        expiryTable.setItems(FXCollections.observableArrayList(rows));
        expiryCountLabel.setText(String.valueOf(rows.size()));
    }

    @FXML
    private void handleRefreshMovements() {
        List<PharmacyReportService.MovementReportRow> rows = reportService.getMovementRows(
                movementFromDatePicker.getValue(),
                movementToDatePicker.getValue(),
                movementSearchField.getText(),
                movementTypeComboBox.getValue());
        movementsTable.setItems(FXCollections.observableArrayList(rows));
        movementCountLabel.setText(String.valueOf(rows.size()));
    }

    @FXML
    private void handleRefreshPurchases() {
        Customer supplier = purchaseSupplierComboBox.getValue();
        List<PharmacyReportService.PurchaseReportRow> rows = reportService.getPurchaseRows(
                purchaseFromDatePicker.getValue(),
                purchaseToDatePicker.getValue(),
                supplier != null ? supplier.getId() : null,
                purchaseSearchField.getText());
        purchasesTable.setItems(FXCollections.observableArrayList(rows));
        purchaseCountLabel.setText(String.valueOf(rows.size()));
    }

    @FXML
    private void handleRefreshCashbox() {
        List<PharmacyReportService.CashboxReportRow> rows = reportService.getCashboxRows(
                cashboxFromDatePicker.getValue(),
                cashboxToDatePicker.getValue());
        cashboxTable.setItems(FXCollections.observableArrayList(rows));
        cashboxCountLabel.setText(String.valueOf(rows.size()));
    }

    @FXML
    private void handleRefreshVelocity() {
        List<PharmacyReportService.SalesVelocityRow> bestRows = reportService.getBestSellingRows(
                velocityFromDatePicker.getValue(),
                velocityToDatePicker.getValue(),
                velocitySearchField.getText());
        List<PharmacyReportService.SalesVelocityRow> slowRows = reportService.getSlowMovingRows(
                velocityFromDatePicker.getValue(),
                velocityToDatePicker.getValue(),
                velocitySearchField.getText());
        bestSellingTable.setItems(FXCollections.observableArrayList(bestRows));
        slowMovingTable.setItems(FXCollections.observableArrayList(slowRows));
        bestSellingCountLabel.setText(String.valueOf(bestRows.size()));
        slowMovingCountLabel.setText(String.valueOf(slowRows.size()));
    }

    @FXML private void handleExportStockExcel() { exportStock("xlsx"); }
    @FXML private void handleExportStockPdf() { exportStock("pdf"); }
    @FXML private void handleExportExpiryExcel() { exportExpiry("xlsx"); }
    @FXML private void handleExportExpiryPdf() { exportExpiry("pdf"); }
    @FXML private void handleExportMovementsExcel() { exportMovements("xlsx"); }
    @FXML private void handleExportMovementsPdf() { exportMovements("pdf"); }
    @FXML private void handleExportPurchasesExcel() { exportPurchases("xlsx"); }
    @FXML private void handleExportPurchasesPdf() { exportPurchases("pdf"); }
    @FXML private void handleExportProfitExcel() { exportProfit("xlsx"); }
    @FXML private void handleExportProfitPdf() { exportProfit("pdf"); }
    @FXML private void handleExportCashboxExcel() { exportCashbox("xlsx"); }
    @FXML private void handleExportCashboxPdf() { exportCashbox("pdf"); }
    @FXML private void handleExportBestSellingExcel() { exportVelocity("xlsx", true); }
    @FXML private void handleExportBestSellingPdf() { exportVelocity("pdf", true); }
    @FXML private void handleExportSlowMovingExcel() { exportVelocity("xlsx", false); }
    @FXML private void handleExportSlowMovingPdf() { exportVelocity("pdf", false); }

    private void exportStock(String extension) {
        export("المخزون حسب الدفعة", "stock_by_batch", extension, stockByBatchTable.getItems(), List.of(
                new PharmacyReportExportService.ReportColumn<>("المنتج", PharmacyReportService.StockByBatchRow::productName),
                new PharmacyReportExportService.ReportColumn<>("الكود", PharmacyReportService.StockByBatchRow::productCode),
                new PharmacyReportExportService.ReportColumn<>("الباركود", PharmacyReportService.StockByBatchRow::barcode),
                new PharmacyReportExportService.ReportColumn<>("الدفعة", PharmacyReportService.StockByBatchRow::batchNumber),
                new PharmacyReportExportService.ReportColumn<>("الصلاحية", PharmacyReportService.StockByBatchRow::expiryDate),
                new PharmacyReportExportService.ReportColumn<>("الكمية", PharmacyReportService.StockByBatchRow::quantity),
                new PharmacyReportExportService.ReportColumn<>("التكلفة", PharmacyReportService.StockByBatchRow::unitCost),
                new PharmacyReportExportService.ReportColumn<>("سعر البيع", PharmacyReportService.StockByBatchRow::salePrice),
                new PharmacyReportExportService.ReportColumn<>("الحالة", PharmacyReportService.StockByBatchRow::status),
                new PharmacyReportExportService.ReportColumn<>("المصدر", PharmacyReportService.StockByBatchRow::sourceReference)
        ));
    }

    private void exportExpiry(String extension) {
        export("تقرير الانتهاء", "expiry_report", extension, expiryTable.getItems(), List.of(
                new PharmacyReportExportService.ReportColumn<>("المنتج", PharmacyReportService.ExpiryReportRow::productName),
                new PharmacyReportExportService.ReportColumn<>("الكود", PharmacyReportService.ExpiryReportRow::productCode),
                new PharmacyReportExportService.ReportColumn<>("الباركود", PharmacyReportService.ExpiryReportRow::barcode),
                new PharmacyReportExportService.ReportColumn<>("الدفعة", PharmacyReportService.ExpiryReportRow::batchNumber),
                new PharmacyReportExportService.ReportColumn<>("الصلاحية", PharmacyReportService.ExpiryReportRow::expiryDate),
                new PharmacyReportExportService.ReportColumn<>("الأيام المتبقية", PharmacyReportService.ExpiryReportRow::daysRemaining),
                new PharmacyReportExportService.ReportColumn<>("الكمية", PharmacyReportService.ExpiryReportRow::quantity),
                new PharmacyReportExportService.ReportColumn<>("الحالة", PharmacyReportService.ExpiryReportRow::status)
        ));
    }

    private void exportMovements(String extension) {
        export("حركات المخزون", "inventory_movements", extension, movementsTable.getItems(), List.of(
                new PharmacyReportExportService.ReportColumn<>("التاريخ", PharmacyReportService.MovementReportRow::movementDate),
                new PharmacyReportExportService.ReportColumn<>("المنتج", PharmacyReportService.MovementReportRow::productName),
                new PharmacyReportExportService.ReportColumn<>("الكود", PharmacyReportService.MovementReportRow::productCode),
                new PharmacyReportExportService.ReportColumn<>("الدفعة", PharmacyReportService.MovementReportRow::batchNumber),
                new PharmacyReportExportService.ReportColumn<>("النوع", PharmacyReportService.MovementReportRow::movementType),
                new PharmacyReportExportService.ReportColumn<>("قبل", PharmacyReportService.MovementReportRow::quantityBefore),
                new PharmacyReportExportService.ReportColumn<>("التغير", PharmacyReportService.MovementReportRow::quantityChanged),
                new PharmacyReportExportService.ReportColumn<>("بعد", PharmacyReportService.MovementReportRow::quantityAfter),
                new PharmacyReportExportService.ReportColumn<>("المرجع", PharmacyReportService.MovementReportRow::referenceType),
                new PharmacyReportExportService.ReportColumn<>("رقم المرجع", PharmacyReportService.MovementReportRow::referenceId),
                new PharmacyReportExportService.ReportColumn<>("ملاحظات", PharmacyReportService.MovementReportRow::note)
        ));
    }

    private void exportPurchases(String extension) {
        export("تقرير المشتريات", "purchase_report", extension, purchasesTable.getItems(), List.of(
                new PharmacyReportExportService.ReportColumn<>("الفاتورة", PharmacyReportService.PurchaseReportRow::voucherNumber),
                new PharmacyReportExportService.ReportColumn<>("رقم الفاتورة", PharmacyReportService.PurchaseReportRow::voucherId),
                new PharmacyReportExportService.ReportColumn<>("التاريخ", PharmacyReportService.PurchaseReportRow::voucherDate),
                new PharmacyReportExportService.ReportColumn<>("المورد", PharmacyReportService.PurchaseReportRow::supplierName),
                new PharmacyReportExportService.ReportColumn<>("المنتج", PharmacyReportService.PurchaseReportRow::productName),
                new PharmacyReportExportService.ReportColumn<>("الكمية", PharmacyReportService.PurchaseReportRow::quantity),
                new PharmacyReportExportService.ReportColumn<>("المبلغ", PharmacyReportService.PurchaseReportRow::amount),
                new PharmacyReportExportService.ReportColumn<>("الدفعة", PharmacyReportService.PurchaseReportRow::batchNumber),
                new PharmacyReportExportService.ReportColumn<>("الصلاحية", PharmacyReportService.PurchaseReportRow::expirationDate)
        ));
    }

    private void exportCashbox(String extension) {
        export("تقرير الصندوق", "cashbox_report", extension, cashboxTable.getItems(), cashboxColumns());
    }

    private void exportProfit(String extension) {
        export("تقرير الربح", "profit_report", extension, List.of(profitStatusLabel.getText()), List.of(
                new PharmacyReportExportService.ReportColumn<>("حالة التقرير", value -> value)
        ));
    }

    private void exportVelocity(String extension, boolean bestSelling) {
        TableView<PharmacyReportService.SalesVelocityRow> table = bestSelling ? bestSellingTable : slowMovingTable;
        export(bestSelling ? "المنتجات الأكثر مبيعاً" : "المنتجات بطيئة الحركة",
                bestSelling ? "best_selling_products" : "slow_moving_products",
                extension,
                table.getItems(),
                velocityColumns());
    }

    private List<PharmacyReportExportService.ReportColumn<PharmacyReportService.CashboxReportRow>> cashboxColumns() {
        return List.of(
                new PharmacyReportExportService.ReportColumn<>("التاريخ", PharmacyReportService.CashboxReportRow::date),
                new PharmacyReportExportService.ReportColumn<>("الرصيد الافتتاحي", PharmacyReportService.CashboxReportRow::openingCash),
                new PharmacyReportExportService.ReportColumn<>("الداخل", PharmacyReportService.CashboxReportRow::totalIn),
                new PharmacyReportExportService.ReportColumn<>("الخارج", PharmacyReportService.CashboxReportRow::totalOut),
                new PharmacyReportExportService.ReportColumn<>("الرصيد المتوقع", PharmacyReportService.CashboxReportRow::expectedCash),
                new PharmacyReportExportService.ReportColumn<>("الرصيد الفعلي", PharmacyReportService.CashboxReportRow::actualCash),
                new PharmacyReportExportService.ReportColumn<>("الفرق", PharmacyReportService.CashboxReportRow::difference),
                new PharmacyReportExportService.ReportColumn<>("الحالة", PharmacyReportService.CashboxReportRow::status)
        );
    }

    private List<PharmacyReportExportService.ReportColumn<PharmacyReportService.SalesVelocityRow>> velocityColumns() {
        return List.of(
                new PharmacyReportExportService.ReportColumn<>("المنتج", PharmacyReportService.SalesVelocityRow::productName),
                new PharmacyReportExportService.ReportColumn<>("الكود", PharmacyReportService.SalesVelocityRow::productCode),
                new PharmacyReportExportService.ReportColumn<>("الباركود", PharmacyReportService.SalesVelocityRow::barcode),
                new PharmacyReportExportService.ReportColumn<>("الفئة", PharmacyReportService.SalesVelocityRow::category),
                new PharmacyReportExportService.ReportColumn<>("الكمية المباعة", PharmacyReportService.SalesVelocityRow::quantitySold),
                new PharmacyReportExportService.ReportColumn<>("الإيراد", PharmacyReportService.SalesVelocityRow::revenue),
                new PharmacyReportExportService.ReportColumn<>("آخر بيع", PharmacyReportService.SalesVelocityRow::lastSaleDate),
                new PharmacyReportExportService.ReportColumn<>("المخزون الحالي", PharmacyReportService.SalesVelocityRow::currentStock)
        );
    }

    private <T> void export(String title,
                            String baseFileName,
                            String extension,
                            List<T> rows,
                            List<PharmacyReportExportService.ReportColumn<T>> columns) {
        try {
            File selected = showSaveDialog(title, baseFileName, extension);
            if (selected == null) {
                return;
            }
            if ("pdf".equalsIgnoreCase(extension)) {
                exportService.exportPdf(selected, title, columns, rows);
            } else {
                exportService.exportExcel(selected, title, columns, rows);
            }
            showInfo("تم", "تم تصدير التقرير:\n" + selected.getAbsolutePath());
        } catch (Exception e) {
            showError("خطأ", "فشل تصدير التقرير: " + e.getMessage());
        }
    }

    private File showSaveDialog(String title, String baseFileName, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ " + title);
        fileChooser.setInitialFileName(baseFileName + "." + extension);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "pdf".equalsIgnoreCase(extension) ? "PDF" : "Excel",
                "*." + extension
        ));
        Stage owner = (Stage) stockByBatchTable.getScene().getWindow();
        return fileChooser.showSaveDialog(owner);
    }

    @FXML
    private void handleClose() {
        if (tabMode && tabId != null && !tabId.isBlank()) {
            TabManager.getInstance().closeTab(tabId);
            return;
        }
        Stage stage = (Stage) stockByBatchTable.getScene().getWindow();
        stage.close();
    }

    private <S, T extends Number> void setupNumberColumn(TableColumn<S, T> column) {
        column.setCellFactory(col -> new TableCell<S, T>() {
            @Override
            protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? "-" : numberFormat.format(value.doubleValue()));
            }
        });
    }

    private String formatDate(LocalDate value) {
        return value == null ? "-" : value.format(dateFormatter);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(dateTimeFormatter);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
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
}
