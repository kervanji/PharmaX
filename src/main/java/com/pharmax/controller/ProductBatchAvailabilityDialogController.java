package com.pharmax.controller;

import com.pharmax.model.Product;
import com.pharmax.model.ProductBatch;
import com.pharmax.model.ProductUnit;
import com.pharmax.service.ProductBatchService;
import com.pharmax.service.ProductUnitService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductBatchAvailabilityDialogController {
    @FXML
    private VBox detailsRoot;
    @FXML
    private Label productNameLabel;
    @FXML
    private Label barcodeLabel;
    @FXML
    private Label scientificNameLabel;
    @FXML
    private Label manufacturerLabel;
    @FXML
    private Label dosageFormLabel;
    @FXML
    private Label categoryLabel;
    @FXML
    private Label storageLocationLabel;
    @FXML
    private Label salePriceLabel;
    @FXML
    private Label totalAvailableLabel;
    @FXML
    private TableView<BatchRow> batchesTable;
    @FXML
    private TableColumn<BatchRow, String> fefoOrderColumn;
    @FXML
    private TableColumn<BatchRow, String> batchNumberColumn;
    @FXML
    private TableColumn<BatchRow, String> expiryDateColumn;
    @FXML
    private TableColumn<BatchRow, Number> daysRemainingColumn;
    @FXML
    private TableColumn<BatchRow, Number> baseQuantityColumn;
    @FXML
    private TableColumn<BatchRow, Number> selectedUnitQuantityColumn;
    @FXML
    private TableColumn<BatchRow, String> statusColumn;

    private final ProductBatchService productBatchService = new ProductBatchService();
    private final ProductUnitService productUnitService = new ProductUnitService();
    private final DecimalFormat numberFormat;

    public ProductBatchAvailabilityDialogController() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        numberFormat = new DecimalFormat("#,##0.00", symbols);
    }

    @FXML
    private void initialize() {
        fefoOrderColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFefoOrder()));
        batchNumberColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBatchNumber()));
        expiryDateColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getExpiryDate()));
        daysRemainingColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getDaysRemaining()));
        baseQuantityColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getBaseQuantity()));
        selectedUnitQuantityColumn
                .setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getSelectedUnitQuantity()));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
    }

    public void setProduct(Product product, ProductUnit selectedUnit, Double salePrice, String currency) {
        if (product == null) {
            return;
        }

        productNameLabel.setText(safe(product.getName()));
        barcodeLabel.setText(safe(product.getBarcode()));
        scientificNameLabel.setText("-");
        manufacturerLabel.setText("-");
        dosageFormLabel.setText("-");
        categoryLabel.setText(safe(product.getCategory()));
        storageLocationLabel.setText("-");
        List<ProductBatch> availableBatches = productBatchService.getAvailableBatches(product.getId());
        double totalAvailableBase = availableBatches.stream().mapToDouble(ProductBatch::getQuantity).sum();
        String baseUnit = productUnitService.resolveBaseUnit(product);
        String saleUnit = selectedUnit != null && selectedUnit.getUnitName() != null
                ? selectedUnit.getUnitName()
                : baseUnit;
        salePriceLabel.setText(salePrice != null && salePrice > 0
                ? numberFormat.format(salePrice) + " " + safe(currency) + " / " + saleUnit
                : "-");

        ProductUnit displayUnit = resolveDisplayUnit(product, selectedUnit, baseUnit);
        String displayUnitName = displayUnit != null && displayUnit.getUnitName() != null
                ? displayUnit.getUnitName()
                : baseUnit;
        double displayFactor = displayUnit != null ? displayUnit.getEffectiveConversionFactor() : 1.0;
        double totalAvailableDisplay = displayFactor > 0 ? totalAvailableBase / displayFactor : totalAvailableBase;
        if (displayUnitName.equals(baseUnit)) {
            totalAvailableLabel.setText(numberFormat.format(totalAvailableBase) + " " + baseUnit);
        } else {
            totalAvailableLabel.setText(numberFormat.format(totalAvailableBase) + " " + baseUnit
                    + " = " + numberFormat.format(totalAvailableDisplay) + " " + displayUnitName);
        }
        if (baseQuantityColumn != null) {
            baseQuantityColumn.setText("كمية الأساس (" + baseUnit + ")");
        }
        if (selectedUnitQuantityColumn != null) {
            selectedUnitQuantityColumn.setText("كمية " + displayUnitName);
        }

        Map<Long, Integer> fefoOrderByBatchId = new HashMap<>();
        for (int i = 0; i < availableBatches.size(); i++) {
            ProductBatch batch = availableBatches.get(i);
            if (batch.getId() != null) {
                fefoOrderByBatchId.put(batch.getId(), i + 1);
            }
        }

        LocalDate today = LocalDate.now();
        List<BatchRow> rows = productBatchService.getAllBatches(product.getId()).stream()
                .filter(batch -> batch.getQuantity() > 0)
                .sorted(Comparator
                        .comparing(ProductBatch::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductBatch::getBatchNumber, Comparator.nullsLast(String::compareTo)))
                .map(batch -> toRow(batch, fefoOrderByBatchId, today, displayFactor))
                .toList();
        batchesTable.setItems(FXCollections.observableArrayList(rows));
    }

    private ProductUnit resolveDisplayUnit(Product product, ProductUnit selectedUnit, String baseUnit) {
        if (selectedUnit != null && selectedUnit.getEffectiveConversionFactor() > 1.0) {
            return selectedUnit;
        }
        return productUnitService.getUnitsForProductOrDefault(product).stream()
                .filter(unit -> unit != null && !Boolean.FALSE.equals(unit.getIsActive()))
                .filter(unit -> unit.getUnitName() != null && !unit.getUnitName().equals(baseUnit))
                .filter(unit -> unit.getEffectiveConversionFactor() > 1.0)
                .max(Comparator.comparingDouble(ProductUnit::getEffectiveConversionFactor))
                .orElse(selectedUnit);
    }

    private BatchRow toRow(ProductBatch batch, Map<Long, Integer> fefoOrderByBatchId, LocalDate today, double factor) {
        LocalDate expiry = batch.getExpiryDate();
        long daysRemaining = expiry != null ? ChronoUnit.DAYS.between(today, expiry) : -1;
        double baseQuantity = batch.getQuantity();
        double selectedQuantity = factor > 0 ? baseQuantity / factor : baseQuantity;
        int fefoOrder = batch.getId() != null ? fefoOrderByBatchId.getOrDefault(batch.getId(), 0) : 0;
        return new BatchRow(
                fefoOrder,
                safe(batch.getBatchNumber()),
                expiry != null ? expiry.toString() : "-",
                daysRemaining,
                baseQuantity,
                selectedQuantity,
                resolveStatus(expiry, today));
    }

    private String resolveStatus(LocalDate expiry, LocalDate today) {
        if (expiry == null) {
            return "صالح";
        }
        if (expiry.isBefore(today)) {
            return "منتهي";
        }
        if (!expiry.isAfter(today.plusDays(30))) {
            return "قريب الانتهاء";
        }
        return "صالح";
    }

    @FXML
    private void handleClose() {
        if (detailsRoot != null && detailsRoot.getScene() != null
                && detailsRoot.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    public static class BatchRow {
        private final int fefoOrder;
        private final String batchNumber;
        private final String expiryDate;
        private final long daysRemaining;
        private final double baseQuantity;
        private final double selectedUnitQuantity;
        private final String status;

        public BatchRow(int fefoOrder, String batchNumber, String expiryDate, long daysRemaining,
                double baseQuantity, double selectedUnitQuantity, String status) {
            this.fefoOrder = fefoOrder;
            this.batchNumber = batchNumber;
            this.expiryDate = expiryDate;
            this.daysRemaining = daysRemaining;
            this.baseQuantity = baseQuantity;
            this.selectedUnitQuantity = selectedUnitQuantity;
            this.status = status;
        }

        public String getFefoOrder() {
            return fefoOrder > 0 ? String.valueOf(fefoOrder) : "-";
        }

        public String getBatchNumber() {
            return batchNumber;
        }

        public String getExpiryDate() {
            return expiryDate;
        }

        public long getDaysRemaining() {
            return daysRemaining;
        }

        public double getBaseQuantity() {
            return baseQuantity;
        }

        public double getSelectedUnitQuantity() {
            return selectedUnitQuantity;
        }

        public String getStatus() {
            return status;
        }
    }
}
