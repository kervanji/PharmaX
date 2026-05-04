package com.pharmax.controller;

import com.pharmax.model.Product;
import com.pharmax.model.ProductBatch;
import com.pharmax.service.ProductBatchService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

public class ExpiryAlertsController {
    @FXML private ComboBox<String> filterComboBox;
    @FXML private TableView<ExpiryAlertRow> expiryTable;
    @FXML private TableColumn<ExpiryAlertRow, String> productNameColumn;
    @FXML private TableColumn<ExpiryAlertRow, String> productCodeColumn;
    @FXML private TableColumn<ExpiryAlertRow, String> barcodeColumn;
    @FXML private TableColumn<ExpiryAlertRow, String> batchNumberColumn;
    @FXML private TableColumn<ExpiryAlertRow, String> expirationDateColumn;
    @FXML private TableColumn<ExpiryAlertRow, String> daysRemainingColumn;
    @FXML private TableColumn<ExpiryAlertRow, String> quantityColumn;
    @FXML private TableColumn<ExpiryAlertRow, String> statusColumn;
    @FXML private Label expiredCountLabel;
    @FXML private Label expiringSoonCountLabel;
    @FXML private Label alertWindowLabel;
    @FXML private Label lastUpdateLabel;

    private final ProductBatchService productBatchService = new ProductBatchService();
    private final DecimalFormat numberFormat;
    private final ObservableList<ExpiryAlertRow> rows = FXCollections.observableArrayList();

    public ExpiryAlertsController() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        numberFormat = new DecimalFormat("#,##0.00", symbols);
    }

    @FXML
    private void initialize() {
        setupFilter();
        setupTable();
        loadAlerts();
    }

    private void setupFilter() {
        filterComboBox.setItems(FXCollections.observableArrayList(
                "الكل",
                "منتهي",
                "30 يوم",
                "60 يوم",
                "90 يوم"));
        filterComboBox.setValue("الكل");
        filterComboBox.setOnAction(event -> loadAlerts());
    }

    private void setupTable() {
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        productCodeColumn.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        barcodeColumn.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        batchNumberColumn.setCellValueFactory(new PropertyValueFactory<>("batchNumber"));
        expirationDateColumn.setCellValueFactory(new PropertyValueFactory<>("expirationDate"));
        daysRemainingColumn.setCellValueFactory(new PropertyValueFactory<>("daysRemaining"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        expiryTable.setItems(rows);
    }

    private void loadAlerts() {
        LocalDate today = LocalDate.now();
        String filter = filterComboBox.getValue() != null ? filterComboBox.getValue() : "الكل";

        List<ProductBatch> batches = switch (filter) {
            case "منتهي" -> productBatchService.getExpiredBatches(today);
            case "30 يوم" -> productBatchService.getExpiringBatchesWithinDays(30, today);
            case "60 يوم" -> productBatchService.getExpiringBatchesWithinDays(60, today);
            case "90 يوم" -> productBatchService.getExpiringBatchesWithinDays(90, today);
            default -> productBatchService.getExpiryAlertBatches(today);
        };

        rows.setAll(batches.stream()
                .map(batch -> ExpiryAlertRow.from(batch, today, numberFormat))
                .toList());

        int expiredCount = productBatchService.getExpiredBatches(today).size();
        int expiringSoonCount = productBatchService.getExpiringBatchesWithinDays(30, today).size();
        expiredCountLabel.setText(String.valueOf(expiredCount));
        expiringSoonCountLabel.setText(String.valueOf(expiringSoonCount));
        alertWindowLabel.setText(String.valueOf(rows.size()));
        lastUpdateLabel.setText("آخر تحديث: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")));
    }

    @FXML
    private void handleRefresh() {
        loadAlerts();
    }

    public static class ExpiryAlertRow {
        private final String productName;
        private final String productCode;
        private final String barcode;
        private final String batchNumber;
        private final String expirationDate;
        private final String daysRemaining;
        private final String quantity;
        private final String status;

        private ExpiryAlertRow(String productName,
                               String productCode,
                               String barcode,
                               String batchNumber,
                               String expirationDate,
                               String daysRemaining,
                               String quantity,
                               String status) {
            this.productName = productName;
            this.productCode = productCode;
            this.barcode = barcode;
            this.batchNumber = batchNumber;
            this.expirationDate = expirationDate;
            this.daysRemaining = daysRemaining;
            this.quantity = quantity;
            this.status = status;
        }

        public static ExpiryAlertRow from(ProductBatch batch, LocalDate today, DecimalFormat format) {
            Product product = batch.getProduct();
            long days = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
            String status;
            if (days < 0) {
                status = "Expired";
            } else if (days <= 30) {
                status = "Expiring within 30 days";
            } else if (days <= 60) {
                status = "Expiring within 60 days";
            } else {
                status = "Expiring within 90 days";
            }
            return new ExpiryAlertRow(
                    product != null && product.getName() != null ? product.getName() : "-",
                    product != null && product.getProductCode() != null ? product.getProductCode() : "-",
                    product != null && product.getBarcode() != null ? product.getBarcode() : "-",
                    batch.getBatchNumber() != null ? batch.getBatchNumber() : "-",
                    batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : "-",
                    days < 0 ? "منتهي منذ " + Math.abs(days) + " يوم" : String.valueOf(days),
                    format.format(batch.getQuantity() != null ? batch.getQuantity() : 0.0),
                    status);
        }

        public String getProductName() { return productName; }
        public String getProductCode() { return productCode; }
        public String getBarcode() { return barcode; }
        public String getBatchNumber() { return batchNumber; }
        public String getExpirationDate() { return expirationDate; }
        public String getDaysRemaining() { return daysRemaining; }
        public String getQuantity() { return quantity; }
        public String getStatus() { return status; }
    }
}
