package com.pharmax.controller;

import com.pharmax.model.Customer;
import com.pharmax.service.PharmacyReportService;
import com.pharmax.util.TabManager;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

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

    private final PharmacyReportService reportService = new PharmacyReportService();
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

        loadPurchaseSuppliers();
        handleRefreshStock();
        handleRefreshExpiry();
        handleRefreshMovements();
        handleRefreshPurchases();
        handleRefreshCashbox();
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
}
