package com.pharmax.controller;

import com.pharmax.model.Category;
import com.pharmax.model.Product;
import com.pharmax.model.ProductBatch;
import com.pharmax.model.ProductUnit;
import com.pharmax.service.CategoryService;
import com.pharmax.service.InventoryMovementService;
import com.pharmax.service.InventoryService;
import com.pharmax.service.ProductBatchService;
import com.pharmax.service.ProductUnitService;
import com.pharmax.util.SessionManager;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ProductController {
    private static final String STRIP_UNIT = "شريط";
    private static final String BOX_UNIT = "علبة";

    @FXML
    private TextField productCodeField;
    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<String> categoryComboBox;
    @FXML
    private TextField barcodeField;
    @FXML
    private TextField costPriceField;
    @FXML
    private TextField costPriceUsdField;
    @FXML
    private TextField unitPriceField;
    @FXML
    private TextField unitPriceUsdField;
    @FXML
    private TextField wholesalePriceField;
    @FXML
    private TextField wholesalePriceUsdField;
    @FXML
    private TextField specialPriceField;
    @FXML
    private TextField specialPriceUsdField;
    @FXML
    private TextField profitPercentageField;
    @FXML
    private RadioButton manualPriceRadio;
    @FXML
    private RadioButton percentagePriceRadio;
    @FXML
    private Label profitMarginRetailLabel;
    @FXML
    private Label calculatedRetailPriceLabel;
    @FXML
    private Label profitMarginWholesaleLabel;
    @FXML
    private Label profitMarginSpecialLabel;
    @FXML
    private TextField quantityField;
    @FXML
    private Label stockBreakdownLabel;
    @FXML
    private TextField minimumStockField;
    @FXML
    private TextField maximumStockField;
    @FXML
    private ComboBox<String> unitOfMeasureComboBox;
    @FXML
    private DatePicker expiryDatePicker;
    @FXML
    private TextField stripsPerBoxField;
    @FXML
    private Label packagingSummaryLabel;
    @FXML
    private ComboBox<String> baseUnitComboBox;
    @FXML
    private TableView<ProductUnitRow> packagingTable;
    @FXML
    private GridPane packagingControlsGrid;
    @FXML
    private Button packagingExpandButton;
    @FXML
    private TableColumn<ProductUnitRow, String> packagingUnitColumn;
    @FXML
    private TableColumn<ProductUnitRow, String> packagingBarcodeColumn;
    @FXML
    private TableColumn<ProductUnitRow, Double> packagingConversionColumn;
    @FXML
    private TableColumn<ProductUnitRow, Double> packagingPriceColumn;
    @FXML
    private TableColumn<ProductUnitRow, Double> packagingPriceUsdColumn;
    @FXML
    private TableColumn<ProductUnitRow, Boolean> packagingDefaultColumn;
    @FXML
    private TableColumn<ProductUnitRow, Boolean> packagingActiveColumn;
    @FXML
    private ComboBox<String> packagingUnitComboBox;
    @FXML
    private TextField packagingBarcodeField;
    @FXML
    private TextField packagingConversionField;
    @FXML
    private TextField packagingPriceField;
    @FXML
    private TextField packagingPriceUsdField;
    @FXML
    private CheckBox packagingDefaultCheckBox;
    @FXML
    private CheckBox packagingActiveCheckBox;
    @FXML
    private CheckBox isActiveCheckBox;
    @FXML
    private CheckBox quickSaleProductCheckBox;
    @FXML
    private Button deleteButton;
    @FXML
    private VBox openingBatchSection;
    @FXML
    private CheckBox openingBatchCheckBox;
    @FXML
    private javafx.scene.layout.GridPane openingBatchGrid;
    @FXML
    private TextField openingBatchNumberField;
    @FXML
    private TextField openingBatchQuantityField;
    @FXML
    private Label openingBatchBreakdownLabel;
    @FXML
    private DatePicker openingBatchProductionDatePicker;
    @FXML
    private DatePicker openingBatchExpiryDatePicker;
    @FXML
    private TextField openingBatchNotesField;

    private Stage dialogStage;
    private Product product;
    private boolean isEditMode = false;
    private boolean tabMode = false;
    private String tabId = "new-product";
    private Runnable afterSaveCallback;
    private boolean packagingTableExpanded = false;
    private Double originalCostPrice = null; // Store original cost for sellers who can't see it
    private Double originalUnitPrice = null; // Store original selling price for sellers who can't edit it
    private final InventoryService inventoryService = new InventoryService();
    private final CategoryService categoryService = new CategoryService();
    private final ProductBatchService productBatchService = new ProductBatchService();
    private final ProductUnitService productUnitService = new ProductUnitService();
    private final InventoryMovementService inventoryMovementService = new InventoryMovementService();
    private final ObservableList<ProductUnitRow> packagingRows = FXCollections.observableArrayList();
    private final DecimalFormat numberFormat;

    public ProductController() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        numberFormat = new DecimalFormat("#,##0.00", symbols);
    }

    @FXML
    private void initialize() {
        loadCategories();
        loadUnitsOfMeasure();
        setupPackagingSection();
        setupUnitBreakdownHelpers();
        setupPriceListeners();
        setupPricingModeToggle();
        setupOpeningBatchToggle();
        applyRoleRestrictions();
    }

    private void applyRoleRestrictions() {
        boolean canSeeCost = SessionManager.getInstance().canSeeCost();
        boolean canManageQuickSaleProducts = SessionManager.getInstance().isAdmin();

        if (!canSeeCost) {
            // Hide cost price - show ****** instead
            costPriceField.setEditable(false);
            costPriceField.setPromptText("******");
            costPriceUsdField.setEditable(false);
            costPriceUsdField.setPromptText("******");

            // Hide pricing mode options (they depend on cost)
            manualPriceRadio.setVisible(false);
            manualPriceRadio.setManaged(false);
            percentagePriceRadio.setVisible(false);
            percentagePriceRadio.setManaged(false);

            // Hide profit percentage field
            profitPercentageField.setVisible(false);
            profitPercentageField.setManaged(false);

            // Hide profit margin labels
            profitMarginRetailLabel.setVisible(false);
            profitMarginRetailLabel.setManaged(false);
            profitMarginWholesaleLabel.setVisible(false);
            profitMarginWholesaleLabel.setManaged(false);
            profitMarginSpecialLabel.setVisible(false);
            profitMarginSpecialLabel.setManaged(false);
        }

        if (quickSaleProductCheckBox != null) {
            quickSaleProductCheckBox.setVisible(canManageQuickSaleProducts);
            quickSaleProductCheckBox.setManaged(canManageQuickSaleProducts);
            quickSaleProductCheckBox.setDisable(!canManageQuickSaleProducts);
            if (!canManageQuickSaleProducts) {
                quickSaleProductCheckBox.setSelected(false);
            }
        }

        applySellingPriceEditRestriction();
    }

    private void applySellingPriceEditRestriction() {
        boolean lockSellingPrice = SessionManager.getInstance().isSeller() && isEditMode;
        unitPriceField.setEditable(!lockSellingPrice);
        unitPriceField.setDisable(lockSellingPrice);
        unitPriceUsdField.setEditable(!lockSellingPrice);
        unitPriceUsdField.setDisable(lockSellingPrice);
    }

    private void loadCategories() {
        List<String> categories = categoryService.getActiveCategories().stream()
                .map(Category::getName)
                .distinct()
                .sorted()
                .toList();
        categoryComboBox.setItems(FXCollections.observableArrayList(categories));
    }

    private void loadUnitsOfMeasure() {
        // Full list kept for base unit and legacy compatibility
        List<String> allUnits = Arrays.asList(
                "حبة", "قرص", "كبسولة", "شريط", "علبة", "كرتون",
                "أمبولة", "فيال", "قارورة", "عبوة", "أنبوب", "كيس",
                "حقنة", "مل", "جرام", "قطعة");
        // Only these units are offered as new selectable packaging/sale units
        List<String> selectablePackagingUnits = Arrays.asList("شريط", "علبة");

        if (unitOfMeasureComboBox != null) {
            unitOfMeasureComboBox.setItems(FXCollections.observableArrayList(allUnits));
        }
        if (baseUnitComboBox != null) {
            baseUnitComboBox.setItems(FXCollections.observableArrayList(allUnits));
            baseUnitComboBox.setValue("حبة");
        }
        if (packagingUnitComboBox != null) {
            packagingUnitComboBox.setItems(FXCollections.observableArrayList(selectablePackagingUnits));
        }
        if (unitOfMeasureComboBox != null && unitOfMeasureComboBox.getValue() == null) {
            unitOfMeasureComboBox.setValue(STRIP_UNIT);
        }
    }

    private void setupPackagingSection() {
        if (packagingTable == null) {
            return;
        }

        packagingUnitColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUnitName()));
        packagingBarcodeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBarcode()));
        packagingConversionColumn
                .setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getConversionFactor()).asObject());
        packagingPriceColumn
                .setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getSalePrice()).asObject());
        packagingPriceUsdColumn
                .setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getSalePriceUsd()).asObject());
        packagingDefaultColumn
                .setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().isDefaultUnit()));
        if (packagingActiveColumn != null) {
            packagingActiveColumn
                    .setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().isActiveUnit()));
        }
        packagingTable.setItems(packagingRows);
        applyPackagingTableSize();
        if (packagingConversionField != null
                && (packagingConversionField.getText() == null || packagingConversionField.getText().isBlank())) {
            packagingConversionField.setText("1");
        }

        packagingTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, row) -> {
            if (row == null) {
                return;
            }
            packagingUnitComboBox.setValue(row.getUnitName());
            packagingBarcodeField.setText(row.getBarcode());
            packagingConversionField.setText(String.valueOf(row.getConversionFactor()));
            packagingPriceField.setText(row.getSalePrice() > 0 ? String.valueOf(row.getSalePrice()) : "");
            packagingPriceUsdField.setText(row.getSalePriceUsd() > 0 ? String.valueOf(row.getSalePriceUsd()) : "");
            packagingDefaultCheckBox.setSelected(row.isDefaultUnit());
            if (packagingActiveCheckBox != null) {
                packagingActiveCheckBox.setSelected(row.isActiveUnit());
            }
            if (BOX_UNIT.equals(row.getUnitName()) && stripsPerBoxField != null && row.getConversionFactor() > 0) {
                stripsPerBoxField.setText(formatQuantity(row.getConversionFactor()));
            }
            refreshUnitBreakdownLabels();
        });
    }

    private void setupUnitBreakdownHelpers() {
        if (quantityField != null) {
            quantityField.textProperty().addListener((obs, oldVal, newVal) -> refreshUnitBreakdownLabels());
        }
        if (openingBatchQuantityField != null) {
            openingBatchQuantityField.textProperty().addListener((obs, oldVal, newVal) -> refreshUnitBreakdownLabels());
        }
        if (stripsPerBoxField != null) {
            stripsPerBoxField.textProperty().addListener((obs, oldVal, newVal) -> {
                syncPackagingEditorWithStripBox();
                syncStripBoxRowFromShortcut();
                refreshUnitBreakdownLabels();
            });
        }
        if (baseUnitComboBox != null) {
            baseUnitComboBox.valueProperty().addListener((obs, oldVal, newVal) -> refreshUnitBreakdownLabels());
        }
        if (packagingUnitComboBox != null) {
            packagingUnitComboBox.valueProperty().addListener((obs, oldVal, newVal) -> refreshUnitBreakdownLabels());
        }
        if (packagingConversionField != null) {
            packagingConversionField.textProperty().addListener((obs, oldVal, newVal) -> refreshUnitBreakdownLabels());
        }
        refreshUnitBreakdownLabels();
    }

    private void setupPriceListeners() {
        // Cost price changes trigger all profit margin recalculations
        costPriceField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (percentagePriceRadio.isSelected()) {
                calculatePriceFromPercentage();
            }
            updateAllProfitMargins();
        });
        costPriceUsdField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (percentagePriceRadio.isSelected()) {
                calculatePriceFromPercentage();
            }
            updateAllProfitMargins();
        });

        // The main selling price is the box price; strip price is derived from it.
        unitPriceField.textProperty().addListener((obs, oldVal, newVal) -> {
            syncStripBoxRowFromShortcut();
            updateAllProfitMargins();
        });
        unitPriceUsdField.textProperty().addListener((obs, oldVal, newVal) -> {
            syncStripBoxRowFromShortcut();
            updateAllProfitMargins();
        });
        wholesalePriceField.textProperty().addListener((obs, oldVal, newVal) -> updateAllProfitMargins());
        wholesalePriceUsdField.textProperty().addListener((obs, oldVal, newVal) -> updateAllProfitMargins());
        specialPriceField.textProperty().addListener((obs, oldVal, newVal) -> updateAllProfitMargins());
        specialPriceUsdField.textProperty().addListener((obs, oldVal, newVal) -> updateAllProfitMargins());

        profitPercentageField.textProperty().addListener((obs, oldVal, newVal) -> calculatePriceFromPercentage());
    }

    private void setupPricingModeToggle() {
        ToggleGroup pricingGroup = new ToggleGroup();
        manualPriceRadio.setToggleGroup(pricingGroup);
        percentagePriceRadio.setToggleGroup(pricingGroup);

        manualPriceRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                unitPriceField.setVisible(true);
                unitPriceField.setManaged(true);
                profitPercentageField.setVisible(false);
                profitPercentageField.setManaged(false);
                calculatedRetailPriceLabel.setVisible(false);
                calculatedRetailPriceLabel.setManaged(false);
            }
        });

        percentagePriceRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                unitPriceField.setVisible(false);
                unitPriceField.setManaged(false);
                profitPercentageField.setVisible(true);
                profitPercentageField.setManaged(true);
                calculatedRetailPriceLabel.setVisible(true);
                calculatedRetailPriceLabel.setManaged(true);
                calculatePriceFromPercentage();
            }
        });
    }

    private void calculatePriceFromPercentage() {
        try {
            double cost = getEffectiveCostPrice();
            double percentage = parseDouble(profitPercentageField.getText());
            if (cost > 0 && percentage >= 0) {
                double price = cost * (1 + percentage / 100);
                unitPriceField.setText(String.valueOf(price));
                updateCalculatedRetailPriceLabel(price);
            } else {
                updateCalculatedRetailPriceLabel(0);
            }
        } catch (Exception e) {
            // Ignore parsing errors during input
            updateCalculatedRetailPriceLabel(0);
        }
    }

    private void updateCalculatedRetailPriceLabel(double price) {
        if (calculatedRetailPriceLabel == null) {
            return;
        }
        if (!percentagePriceRadio.isSelected() || price <= 0) {
            calculatedRetailPriceLabel.setText("سعر بيع العلبة المحسوب: --");
            return;
        }

        double costIqd = parseDouble(costPriceField.getText());
        String currency = costIqd > 0 ? "د.ع" : "$";
        calculatedRetailPriceLabel.setText("سعر بيع العلبة المحسوب: " + numberFormat.format(price) + " " + currency);
    }

    /**
     * Gets the effective cost price in IQD.
     * Uses the IQD field if filled, otherwise falls back to USD field (stored as-is
     * for now).
     */
    private double getEffectiveCostPrice() {
        double costIqd = parseDouble(costPriceField.getText());
        if (costIqd > 0) {
            return costIqd;
        }
        // If IQD is empty, use USD value as-is (the user enters the cost, we just
        // calculate margin)
        return parseDouble(costPriceUsdField.getText());
    }

    /**
     * Gets the effective selling price for a given IQD/USD pair.
     */
    @SuppressWarnings("unused")
    private double getEffectiveSellingPrice(TextField iqdField, TextField usdField) {
        double priceIqd = parseDouble(iqdField.getText());
        if (priceIqd > 0) {
            return priceIqd;
        }
        return parseDouble(usdField.getText());
    }

    private void updateAllProfitMargins() {
        if (!SessionManager.getInstance().canSeeCost()) {
            profitMarginRetailLabel.setText("");
            profitMarginWholesaleLabel.setText("");
            profitMarginSpecialLabel.setText("");
            return;
        }

        // Determine which currency the cost price is in
        double costIqd = parseDouble(costPriceField.getText());
        double costUsd = parseDouble(costPriceUsdField.getText());
        boolean costInIqd = costIqd > 0;
        String currency = costInIqd ? "د.ع" : "$";
        double costValue = costInIqd ? costIqd : costUsd;

        // Retail profit margin
        updateSingleProfitMargin(profitMarginRetailLabel,
                costValue, costInIqd,
                parseDouble(unitPriceField.getText()),
                parseDouble(unitPriceUsdField.getText()),
                currency);

        // Wholesale profit margin
        updateSingleProfitMargin(profitMarginWholesaleLabel,
                costValue, costInIqd,
                parseDouble(wholesalePriceField.getText()),
                parseDouble(wholesalePriceUsdField.getText()),
                currency);

        // Special profit margin
        updateSingleProfitMargin(profitMarginSpecialLabel,
                costValue, costInIqd,
                parseDouble(specialPriceField.getText()),
                parseDouble(specialPriceUsdField.getText()),
                currency);
    }

    private void updateSingleProfitMargin(Label label, double costValue, boolean costInIqd,
            double priceIqd, double priceUsd, String currency) {
        try {
            if (costValue <= 0) {
                label.setText("--");
                return;
            }

            // Use price in the same currency as cost for accurate comparison
            double sellingPrice;
            if (costInIqd) {
                sellingPrice = priceIqd > 0 ? priceIqd : 0;
            } else {
                sellingPrice = priceUsd > 0 ? priceUsd : 0;
            }

            if (sellingPrice <= 0) {
                label.setText("--");
                return;
            }

            double profit = sellingPrice - costValue;
            double percentage = (profit / costValue) * 100;

            String color = profit >= 0 ? "-fx-success-text" : "-fx-danger-text";
            String marginLabel = label == profitMarginRetailLabel ? "هامش العلبة" : "هامش";
            label.setText(String.format("%s: %s %s (%.1f%%)", marginLabel, numberFormat.format(profit), currency, percentage));
            label.setStyle("-fx-font-size: 12px; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
        } catch (Exception e) {
            label.setText("--");
        }
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    public void setTabId(String tabId) {
        if (tabId != null && !tabId.isBlank()) {
            this.tabId = tabId;
        }
    }

    public void setAfterSaveCallback(Runnable afterSaveCallback) {
        this.afterSaveCallback = afterSaveCallback;
    }

    public void setProduct(Product product) {
        this.product = product;
        this.isEditMode = true;
        deleteButton.setVisible(true);

        productCodeField.setText(product.getProductCode());
        productCodeField.setEditable(false);
        nameField.setText(product.getName());
        categoryComboBox.setValue(product.getCategory());
        barcodeField.setText(product.getBarcode());
        // Store original cost price for sellers
        originalCostPrice = product.getCostPrice();
        originalUnitPrice = product.getUnitPrice();

        // Show cost price only if user has permission
        if (SessionManager.getInstance().canSeeCost()) {
            costPriceField.setText(product.getCostPrice() != null ? String.valueOf(product.getCostPrice()) : "");
            costPriceUsdField
                    .setText(product.getCostPriceUsd() != null ? String.valueOf(product.getCostPriceUsd()) : "");
        } else {
            costPriceField.setText("******");
            costPriceUsdField.setText("******");
        }
        unitPriceField.setText(product.getUnitPrice() != null ? String.valueOf(product.getUnitPrice()) : "");
        unitPriceUsdField.setText(product.getUnitPriceUsd() != null ? String.valueOf(product.getUnitPriceUsd()) : "");
        wholesalePriceField
                .setText(product.getWholesalePrice() != null ? String.valueOf(product.getWholesalePrice()) : "");
        wholesalePriceUsdField
                .setText(product.getWholesalePriceUsd() != null ? String.valueOf(product.getWholesalePriceUsd()) : "");
        specialPriceField.setText(product.getSpecialPrice() != null ? String.valueOf(product.getSpecialPrice()) : "");
        specialPriceUsdField
                .setText(product.getSpecialPriceUsd() != null ? String.valueOf(product.getSpecialPriceUsd()) : "");
        quantityField.setText(String.valueOf(product.getQuantityInStock()));
        minimumStockField.setText(product.getMinimumStock() != null ? String.valueOf(product.getMinimumStock()) : "");
        maximumStockField.setText(product.getMaximumStock() != null ? String.valueOf(product.getMaximumStock()) : "");
        if (unitOfMeasureComboBox != null) {
            unitOfMeasureComboBox.setValue(product.getUnitOfMeasure());
        }
        loadBatchFields(product);
        loadStripsPerBox(product);
        if (baseUnitComboBox != null) {
            String baseUnit = product.getBaseUnit() != null ? product.getBaseUnit() : product.getUnitOfMeasure();
            baseUnitComboBox.setValue(baseUnit != null ? baseUnit : STRIP_UNIT);
        }
        isActiveCheckBox.setSelected(product.getIsActive());
        if (quickSaleProductCheckBox != null) {
            quickSaleProductCheckBox.setSelected(Boolean.TRUE.equals(product.getIsQuickSale()));
        }
        loadPackagingRows(product);
        loadBoxSellingPriceFields(product);
        syncPackagingEditorWithStripBox();
        syncStripBoxRowFromShortcut();

        // Hide opening batch section in edit mode
        hideOpeningBatchSection();

        applySellingPriceEditRestriction();
        updateAllProfitMargins();
        refreshUnitBreakdownLabels();
    }

    private void loadBatchFields(Product product) {
        if (expiryDatePicker == null || product == null || product.getId() == null) {
            return;
        }

        findPreferredBatch(product).map(ProductBatch::getExpiryDate).ifPresent(expiryDatePicker::setValue);
    }

    private void loadStripsPerBox(Product product) {
        if (stripsPerBoxField == null || product == null || product.getId() == null) {
            return;
        }

        productUnitService.getUnitsForProductOrDefault(product).stream()
                .filter(unit -> unit.getUnitName() != null && unit.getUnitName().trim().equals(BOX_UNIT))
                .filter(unit -> unit.getEffectiveConversionFactor() > 1)
                .findFirst()
                .ifPresent(unit -> stripsPerBoxField.setText(formatQuantity(unit.getEffectiveConversionFactor())));
    }

    private void loadPackagingRows(Product product) {
        packagingRows.clear();
        List<ProductUnit> units = productUnitService.getUnitsForProductOrDefault(product);
        for (ProductUnit unit : units) {
            packagingRows.add(ProductUnitRow.fromUnit(unit));
        }
        refreshUnitBreakdownLabels();
    }

    private void loadBoxSellingPriceFields(Product product) {
        ProductUnitRow boxRow = findPackagingRow(BOX_UNIT);
        if (boxRow != null) {
            if (boxRow.getSalePrice() > 0) {
                unitPriceField.setText(String.valueOf(boxRow.getSalePrice()));
            }
            if (boxRow.getSalePriceUsd() > 0) {
                unitPriceUsdField.setText(String.valueOf(boxRow.getSalePriceUsd()));
            }
        } else if (product != null) {
            unitPriceField.setText(product.getUnitPrice() != null ? String.valueOf(product.getUnitPrice()) : "");
            unitPriceUsdField.setText(product.getUnitPriceUsd() != null ? String.valueOf(product.getUnitPriceUsd()) : "");
        }
    }

    @FXML
    private void handleAddPackagingUnit() {
        String unitName = safeTrim(packagingUnitComboBox);
        if (unitName.isEmpty()) {
            showError("خطأ", "اختر وحدة التعبئة");
            packagingUnitComboBox.requestFocus();
            return;
        }

        double conversionFactor = parseDouble(packagingConversionField.getText());
        if (conversionFactor <= 0) {
            showError("خطأ", "عامل التحويل يجب أن يكون أكبر من صفر");
            packagingConversionField.requestFocus();
            return;
        }

        ProductUnitRow row = new ProductUnitRow(
                unitName,
                safeTrim(packagingBarcodeField),
                conversionFactor,
                parseDouble(packagingPriceField.getText()),
                parseDouble(packagingPriceUsdField.getText()),
                packagingDefaultCheckBox.isSelected(),
                packagingActiveCheckBox == null || packagingActiveCheckBox.isSelected()
        );

        ProductUnitRow selected = packagingTable != null ? packagingTable.getSelectionModel().getSelectedItem() : null;
        ProductUnitRow existing = packagingRows.stream()
                .filter(item -> item != selected)
                .filter(item -> item.getUnitName().equalsIgnoreCase(unitName))
                .findFirst()
                .orElse(null);

        if (selected != null && existing != null) {
            showError("خطأ", "وحدة التعبئة مسجلة مسبقاً: " + unitName);
            packagingUnitComboBox.requestFocus();
            return;
        }

        if (row.isDefaultUnit()) {
            packagingRows.forEach(item -> item.setDefaultUnit(false));
        }

        if (selected != null) {
            int index = packagingRows.indexOf(selected);
            packagingRows.set(index, row);
        } else if (existing != null) {
            int index = packagingRows.indexOf(existing);
            packagingRows.set(index, row);
        } else {
            packagingRows.add(row);
        }
        packagingTable.refresh();
        if (BOX_UNIT.equals(unitName)) {
            if (stripsPerBoxField != null) {
                stripsPerBoxField.setText(formatQuantity(conversionFactor));
            }
            if (row.getSalePrice() > 0) {
                unitPriceField.setText(String.valueOf(row.getSalePrice()));
            }
            if (row.getSalePriceUsd() > 0) {
                unitPriceUsdField.setText(String.valueOf(row.getSalePriceUsd()));
            }
        }
        clearPackagingInputs();
        refreshUnitBreakdownLabels();
    }

    @FXML
    private void handleRemovePackagingUnit() {
        ProductUnitRow selected = packagingTable != null ? packagingTable.getSelectionModel().getSelectedItem() : null;
        if (selected != null) {
            packagingRows.remove(selected);
            packagingTable.refresh();
            clearPackagingInputs();
            refreshUnitBreakdownLabels();
        }
    }

    private void clearPackagingInputs() {
        packagingUnitComboBox.setValue(null);
        packagingBarcodeField.clear();
        packagingConversionField.setText("1");
        packagingPriceField.clear();
        packagingPriceUsdField.clear();
        packagingDefaultCheckBox.setSelected(false);
        if (packagingActiveCheckBox != null) {
            packagingActiveCheckBox.setSelected(true);
        }
        if (packagingTable != null) {
            packagingTable.getSelectionModel().clearSelection();
        }
    }

    @FXML
    private void handleAddCategory() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("إضافة فئة جديدة");
        dialog.setHeaderText(null);
        dialog.setContentText("اسم الفئة:");

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                String trimmed = name.trim();
                try {
                    categoryService.createCategory(new Category(trimmed));
                } catch (Exception e) {
                    showError("خطأ", e.getMessage());
                    return;
                }
                loadCategories();
                categoryComboBox.setValue(trimmed);
            }
        });
    }

    @FXML
    private void handleSave() {
        if (!validateInput()) {
            return;
        }

        try {
            if (product == null) {
                product = new Product();
            }

            String productCode = safeTrim(productCodeField);
            if (productCode.isEmpty()) {
                productCode = generateProductCode();
            }
            product.setProductCode(productCode);
            product.setName(safeTrim(nameField));
            product.setCategory(categoryComboBox.getValue());
            product.setBarcode(safeTrim(barcodeField));
            // Only update cost price if user can see it, otherwise keep original
            if (SessionManager.getInstance().canSeeCost()) {
                product.setCostPrice(parseDoubleOrNull(costPriceField.getText()));
                product.setCostPriceUsd(parseDoubleOrNull(costPriceUsdField.getText()));
            } else if (originalCostPrice != null) {
                product.setCostPrice(originalCostPrice);
            }

            // Sellers can't change selling price when editing; keep original value.
            if (SessionManager.getInstance().isSeller() && isEditMode && originalUnitPrice != null) {
                product.setUnitPrice(originalUnitPrice);
            } else {
                product.setUnitPrice(parseDoubleOrNull(unitPriceField.getText()));
                product.setUnitPriceUsd(parseDoubleOrNull(unitPriceUsdField.getText()));
                product.setWholesalePrice(parseDoubleOrNull(wholesalePriceField.getText()));
                product.setWholesalePriceUsd(parseDoubleOrNull(wholesalePriceUsdField.getText()));
                product.setSpecialPrice(parseDoubleOrNull(specialPriceField.getText()));
                product.setSpecialPriceUsd(parseDoubleOrNull(specialPriceUsdField.getText()));
            }
            double requestedStockQuantity = parseDouble(quantityField.getText());
            product.setQuantityInStock(requestedStockQuantity);
            product.setMinimumStock(parseDouble(minimumStockField.getText()));
            product.setMaximumStock(parseDoubleOrNull(maximumStockField.getText()));
            String baseUnit = baseUnitComboBox != null && baseUnitComboBox.getValue() != null
                    ? baseUnitComboBox.getValue()
                    : unitOfMeasureComboBox != null ? unitOfMeasureComboBox.getValue() : null;
            product.setBaseUnit(baseUnit);
            product.setUnitOfMeasure(baseUnit);
            product.setIsActive(isActiveCheckBox.isSelected());
            if (SessionManager.getInstance().isAdmin()) {
                product.setIsQuickSale(quickSaleProductCheckBox != null && quickSaleProductCheckBox.isSelected());
            } else if (product.getIsQuickSale() == null) {
                product.setIsQuickSale(false);
            }

            syncStripBoxRowFromShortcut();
            Product savedProduct;
            if (isEditMode) {
                savedProduct = inventoryService.updateProduct(product);
                productUnitService.replaceUnitsForProduct(savedProduct, buildProductUnits(savedProduct));
                syncProductStockState(savedProduct, requestedStockQuantity);
                showInfo("تم التحديث", "تم تحديث المنتج بنجاح");
            } else {
                savedProduct = inventoryService.createProduct(product);
                productUnitService.replaceUnitsForProduct(savedProduct, buildProductUnits(savedProduct));
                syncProductStockState(savedProduct, requestedStockQuantity);
                showInfo("تم الإضافة", "تم إضافة المنتج بنجاح");
            }

            if (afterSaveCallback != null) {
                afterSaveCallback.run();
            }
            closeForm();
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeForm();
    }

    private void closeForm() {
        if (tabMode) {
            com.pharmax.util.TabManager.getInstance().closeTab(tabId);
        } else if (dialogStage != null) {
            dialogStage.close();
        }
    }

    @FXML
    private void handleTogglePackagingTableSize() {
        packagingTableExpanded = !packagingTableExpanded;
        applyPackagingTableSize();
    }

    private void applyPackagingTableSize() {
        if (packagingTable == null) {
            return;
        }

        if (packagingTableExpanded) {
            packagingTable.setPrefHeight(520);
            packagingTable.setMinHeight(420);
        } else {
            packagingTable.setPrefHeight(150);
            packagingTable.setMinHeight(120);
        }

        if (packagingControlsGrid != null) {
            packagingControlsGrid.setVisible(!packagingTableExpanded);
            packagingControlsGrid.setManaged(!packagingTableExpanded);
        }
        if (packagingExpandButton != null) {
            packagingExpandButton.setText(packagingTableExpanded ? "تصغير الجدول" : "تكبير الجدول");
        }
    }

    private List<ProductUnit> buildProductUnits(Product savedProduct) {
        List<ProductUnitRow> rows = new ArrayList<>(packagingRows);
        return rows.stream()
                .map(row -> row.toProductUnit(savedProduct))
                .toList();
    }

    private void syncProductStockState(Product savedProduct, double requestedStockQuantity) {
        if (savedProduct == null || savedProduct.getId() == null) {
            return;
        }

        boolean explicitOpeningBatch = openingBatchCheckBox != null && openingBatchCheckBox.isSelected();
        List<ProductBatch> existingBatches = productBatchService.getAllBatches(savedProduct.getId());
        boolean needsImplicitOpeningBatch = requestedStockQuantity > 0 && existingBatches.isEmpty();

        if (!explicitOpeningBatch && !needsImplicitOpeningBatch) {
            if (!existingBatches.isEmpty()) {
                double currentBatchTotal = productBatchService.getTotalBatchQuantity(savedProduct.getId());
                double difference = requestedStockQuantity - currentBatchTotal;
                
                if (difference > 1e-9) {
                    String actor = SessionManager.getInstance().getCurrentUsername();
                    Double unitCost = savedProduct.getCostPrice() != null ? savedProduct.getCostPrice() : savedProduct.getCostPriceUsd();
                    String currency = savedProduct.getCostPrice() != null ? "دينار" : "دولار";
                    
                    ProductBatch adjustmentBatch = productBatchService.createOrUpdateBatch(
                            savedProduct,
                            "ADJ-" + System.currentTimeMillis(),
                            null,
                            null,
                            difference,
                            unitCost,
                            currency,
                            null,
                            false
                    );
                    
                    if (adjustmentBatch != null && adjustmentBatch.getId() != null) {
                        boolean alreadyRecorded = inventoryMovementService.existsByReference(
                                "manual_stock_add", "stock_adjustment", savedProduct.getId(), adjustmentBatch.getId());
                        if (!alreadyRecorded) {
                            inventoryMovementService.recordMovement(
                                    savedProduct,
                                    adjustmentBatch,
                                    "manual_stock_add",
                                    "stock_adjustment",
                                    savedProduct.getId(),
                                    adjustmentBatch.getId(),
                                    difference,
                                    unitCost,
                                    "تعديل كمية يدوي من واجهة المنتج",
                                    actor);
                        }
                    }
                }
                
                productBatchService.syncProductSummaryQuantity(savedProduct.getId());
            }
            return;
        }

        double quantity = explicitOpeningBatch
                ? parseDouble(openingBatchQuantityField.getText())
                : requestedStockQuantity;
        if (quantity <= 0) {
            return;
        }

        String batchNumber = explicitOpeningBatch ? safeTrim(openingBatchNumberField) : "";
        if (batchNumber.isEmpty()) {
            batchNumber = "OPENING-" + savedProduct.getId();
        }

        // Duplicate prevention: check if an opening batch with this number already exists
        java.util.Optional<ProductBatch> existingBatch =
                productBatchService.findByProductIdAndBatchNumber(savedProduct.getId(), batchNumber);
        if (existingBatch.isPresent()) {
            // Already created (retry scenario) - skip to avoid duplication
            return;
        }

        LocalDate expiryDate = explicitOpeningBatch
                ? (openingBatchExpiryDatePicker != null ? openingBatchExpiryDatePicker.getValue() : null)
                : (expiryDatePicker != null ? expiryDatePicker.getValue() : null);
        LocalDate productionDate = explicitOpeningBatch
                ? (openingBatchProductionDatePicker != null ? openingBatchProductionDatePicker.getValue() : null)
                : null;
        String notes = explicitOpeningBatch ? safeTrim(openingBatchNotesField) : "مخزون افتتاحي تلقائي";

        Double unitCost = savedProduct.getCostPrice() != null ? savedProduct.getCostPrice() : savedProduct.getCostPriceUsd();
        String currency = savedProduct.getCostPrice() != null ? "دينار" : "دولار";

        // Create the batch
        ProductBatch batch = productBatchService.createOrUpdateBatch(
                savedProduct,
                batchNumber,
                expiryDate,
                productionDate,
                quantity,
                unitCost,
                currency,
                null,
                true);

        // Record inventory movement for the opening batch
        if (batch != null && batch.getId() != null) {
            String actor = SessionManager.getInstance().getCurrentUsername();
            String movementNote = "دفعة افتتاحية";
            if (notes != null && !notes.isEmpty()) {
                movementNote += " - " + notes;
            }

            // Use existsByReference to prevent duplicate movement
            boolean alreadyRecorded = inventoryMovementService.existsByReference(
                    "manual_stock_add", "opening_batch", savedProduct.getId(), batch.getId());
            if (!alreadyRecorded) {
                inventoryMovementService.recordMovement(
                        savedProduct,
                        batch,
                        "manual_stock_add",
                        "opening_batch",
                        savedProduct.getId(),
                        batch.getId(),
                        quantity,
                        unitCost,
                        movementNote,
                        actor);
            }
        }

        // Sync product quantity from batch totals
        productBatchService.syncProductSummaryQuantity(savedProduct.getId());
    }

    private void setupOpeningBatchToggle() {
        if (openingBatchCheckBox == null || openingBatchGrid == null) {
            return;
        }
        openingBatchCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            openingBatchGrid.setDisable(!newVal);
            openingBatchGrid.setOpacity(newVal ? 1.0 : 0.5);
        });
    }

    private void hideOpeningBatchSection() {
        if (openingBatchSection != null) {
            openingBatchSection.setVisible(false);
            openingBatchSection.setManaged(false);
        }
    }

    @FXML
    private void handleDelete() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الحذف");
        confirm.setHeaderText(null);
        confirm.setContentText("هل أنت متأكد من حذف هذا المنتج؟");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    inventoryService.deleteProduct(product);
                    showInfo("تم الحذف", "تم حذف المنتج بنجاح");
                    if (afterSaveCallback != null) {
                        afterSaveCallback.run();
                    }
                    closeForm();
                } catch (Exception e) {
                    showError("خطأ", "فشل في حذف المنتج: " + e.getMessage());
                }
            }
        });
    }

    private boolean validateInput() {
        if (safeTrim(nameField).isEmpty()) {
            showError("خطأ", "اسم المنتج مطلوب");
            nameField.requestFocus();
            return false;
        }

        if (SessionManager.getInstance().canSeeCost()
                && !validateRequiredCurrencyPair("سعر التكلفة", costPriceField, costPriceUsdField)) {
            return false;
        }

        if (!validateRequiredCurrencyPair("سعر بيع العلبة", unitPriceField, unitPriceUsdField)) {
            return false;
        }

        if (!validateOptionalCurrencyPair("سعر الجملة", wholesalePriceField, wholesalePriceUsdField)) {
            return false;
        }

        if (!validateOptionalCurrencyPair("السعر الخاص", specialPriceField, specialPriceUsdField)) {
            return false;
        }

        if (expiryDatePicker != null && expiryDatePicker.getValue() != null && parseDouble(quantityField.getText()) <= 0) {
            showError("خطأ", "تاريخ الانتهاء يحتاج كمية افتتاحية أكبر من صفر");
            quantityField.requestFocus();
            return false;
        }

        // Validate opening batch fields if enabled
        if (openingBatchCheckBox != null && openingBatchCheckBox.isSelected()) {
            double openingQty = parseDouble(openingBatchQuantityField.getText());
            if (openingQty <= 0) {
                showError("خطأ", "الكمية الأولية للدفعة الافتتاحية يجب أن تكون أكبر من صفر");
                openingBatchQuantityField.requestFocus();
                return false;
            }

            LocalDate prodDate = openingBatchProductionDatePicker != null ? openingBatchProductionDatePicker.getValue() : null;
            LocalDate expDate = openingBatchExpiryDatePicker != null ? openingBatchExpiryDatePicker.getValue() : null;
            if (prodDate != null && expDate != null && !expDate.isAfter(prodDate)) {
                showError("خطأ", "تاريخ الانتهاء يجب أن يكون بعد تاريخ الإنتاج");
                openingBatchExpiryDatePicker.requestFocus();
                return false;
            }
        }

        if (isEditMode && product != null && product.getId() != null) {
            double requestedStockQuantity = parseDouble(quantityField.getText());
            double currentBatchTotal = productBatchService.getTotalBatchQuantity(product.getId());
            if (requestedStockQuantity < currentBatchTotal - 1e-9) {
                showError("خطأ", "لا يمكن تقليل الكمية يدوياً لأنها أقل من إجمالي الدفعات الحالية (" + numberFormat.format(currentBatchTotal) + "). يرجى استخدام شاشة البيع أو التسوية.");
                quantityField.requestFocus();
                return false;
            }
        }

        return true;
    }

    private java.util.Optional<ProductBatch> findPreferredBatch(Product product) {
        if (product == null || product.getId() == null) {
            return java.util.Optional.empty();
        }

        List<ProductBatch> batches = productBatchService.getAllBatches(product.getId());
        return batches.stream()
                .filter(batch -> Boolean.TRUE.equals(batch.getIsOpeningBatch()))
                .findFirst()
                .or(() -> batches.stream()
                        .filter(batch -> batch.getExpiryDate() != null)
                        .findFirst());
    }

    private boolean validateRequiredCurrencyPair(String fieldName, TextField iqdField, TextField usdField) {
        return validateCurrencyPair(fieldName, iqdField, usdField, true);
    }

    private boolean validateOptionalCurrencyPair(String fieldName, TextField iqdField, TextField usdField) {
        return validateCurrencyPair(fieldName, iqdField, usdField, false);
    }

    private boolean validateCurrencyPair(String fieldName, TextField iqdField, TextField usdField, boolean required) {
        Double iqdValue = parsePositiveDoubleOrNull(iqdField.getText());
        Double usdValue = parsePositiveDoubleOrNull(usdField.getText());

        if (hasText(iqdField) && iqdValue == null) {
            showError("خطأ", fieldName + " بالدينار يجب أن يكون رقماً أكبر من صفر");
            iqdField.requestFocus();
            return false;
        }

        if (hasText(usdField) && usdValue == null) {
            showError("خطأ", fieldName + " بالدولار يجب أن يكون رقماً أكبر من صفر");
            usdField.requestFocus();
            return false;
        }

        if (required && iqdValue == null && usdValue == null) {
            showError("خطأ", "يجب إدخال " + fieldName + " بالدينار أو بالدولار أو كليهما");
            iqdField.requestFocus();
            return false;
        }

        return true;
    }

    private boolean hasText(TextField field) {
        return field.getText() != null && !field.getText().trim().isEmpty();
    }

    private void syncPackagingEditorWithStripBox() {
        double stripsPerBox = parseDouble(stripsPerBoxField != null ? stripsPerBoxField.getText() : null);
        if (stripsPerBox <= 0) {
            return;
        }
        if (baseUnitComboBox != null && !STRIP_UNIT.equals(baseUnitComboBox.getValue())) {
            baseUnitComboBox.setValue(STRIP_UNIT);
        }
        if (packagingUnitComboBox != null
                && (packagingUnitComboBox.getValue() == null || packagingUnitComboBox.getValue().isBlank())) {
            packagingUnitComboBox.setValue(BOX_UNIT);
        }
        if (packagingUnitComboBox != null
                && BOX_UNIT.equals(packagingUnitComboBox.getValue())
                && packagingConversionField != null) {
            packagingConversionField.setText(formatQuantity(stripsPerBox));
        }
    }

    private void syncStripBoxRowFromShortcut() {
        double stripsPerBox = parseDouble(stripsPerBoxField != null ? stripsPerBoxField.getText() : null);
        if (stripsPerBox <= 0) {
            return;
        }

        ProductUnitRow existingStrip = findPackagingRow(STRIP_UNIT);
        ProductUnitRow existingBox = findPackagingRow(BOX_UNIT);

        String stripBarcode = existingStrip != null ? existingStrip.getBarcode() : "";
        boolean stripActive = existingStrip == null || existingStrip.isActiveUnit();
        String boxBarcode = existingBox != null ? existingBox.getBarcode() : "";
        boolean boxActive = existingBox == null || existingBox.isActiveUnit();

        if (packagingUnitComboBox != null && STRIP_UNIT.equals(packagingUnitComboBox.getValue())) {
            stripBarcode = safeTrim(packagingBarcodeField);
            stripActive = packagingActiveCheckBox == null || packagingActiveCheckBox.isSelected();
        } else if (packagingUnitComboBox != null && BOX_UNIT.equals(packagingUnitComboBox.getValue())) {
            boxBarcode = safeTrim(packagingBarcodeField);
            boxActive = packagingActiveCheckBox == null || packagingActiveCheckBox.isSelected();
        }

        double boxPrice = parseDouble(unitPriceField != null ? unitPriceField.getText() : null);
        double boxPriceUsd = parseDouble(unitPriceUsdField != null ? unitPriceUsdField.getText() : null);
        double stripPrice = boxPrice > 0 ? boxPrice / stripsPerBox : 0;
        double stripPriceUsd = boxPriceUsd > 0 ? boxPriceUsd / stripsPerBox : 0;

        ProductUnitRow updatedStrip = new ProductUnitRow(
                STRIP_UNIT,
                stripBarcode,
                1,
                stripPrice,
                stripPriceUsd,
                true,
                stripActive);
        ProductUnitRow updatedBox = new ProductUnitRow(
                BOX_UNIT,
                boxBarcode,
                stripsPerBox,
                boxPrice,
                boxPriceUsd,
                false,
                boxActive);

        packagingRows.forEach(row -> row.setDefaultUnit(false));
        upsertPackagingRow(updatedStrip);
        upsertPackagingRow(updatedBox);
        syncSelectedPackagingEditorPrices(stripPrice, stripPriceUsd, boxPrice, boxPriceUsd);

        if (packagingTable != null) {
            packagingTable.refresh();
        }
    }

    private ProductUnitRow findPackagingRow(String unitName) {
        if (unitName == null) {
            return null;
        }
        return packagingRows.stream()
                .filter(row -> unitName.equalsIgnoreCase(row.getUnitName()))
                .findFirst()
                .orElse(null);
    }

    private void upsertPackagingRow(ProductUnitRow row) {
        ProductUnitRow existing = findPackagingRow(row.getUnitName());
        if (existing != null) {
            packagingRows.set(packagingRows.indexOf(existing), row);
        } else {
            packagingRows.add(row);
        }
    }

    private void syncSelectedPackagingEditorPrices(double stripPrice, double stripPriceUsd,
            double boxPrice, double boxPriceUsd) {
        if (packagingUnitComboBox == null || packagingPriceField == null || packagingPriceUsdField == null) {
            return;
        }
        String selectedUnit = packagingUnitComboBox.getValue();
        if (STRIP_UNIT.equals(selectedUnit)) {
            packagingPriceField.setText(stripPrice > 0 ? String.valueOf(stripPrice) : "");
            packagingPriceUsdField.setText(stripPriceUsd > 0 ? String.valueOf(stripPriceUsd) : "");
        } else if (BOX_UNIT.equals(selectedUnit)) {
            packagingPriceField.setText(boxPrice > 0 ? String.valueOf(boxPrice) : "");
            packagingPriceUsdField.setText(boxPriceUsd > 0 ? String.valueOf(boxPriceUsd) : "");
        }
    }

    private void refreshUnitBreakdownLabels() {
        updateBreakdownLabel(stockBreakdownLabel, parseDouble(quantityField != null ? quantityField.getText() : null), false);
        updateBreakdownLabel(openingBatchBreakdownLabel,
                parseDouble(openingBatchQuantityField != null ? openingBatchQuantityField.getText() : null), true);
        updatePackagingSummaryLabel();
    }

    private void updateBreakdownLabel(Label label, double quantity, boolean openingBatch) {
        if (label == null) {
            return;
        }
        if (quantity <= 0) {
            label.setText(openingBatch
                    ? "أدخل كمية ابتدائية ليظهر تحويلها بين الشريط والعلبة."
                    : "أدخل الكمية الحالية ليظهر مجموعها بالشرائط والعلب.");
            return;
        }
        label.setText(formatUnitBreakdown(quantity));
    }

    private void updatePackagingSummaryLabel() {
        if (packagingSummaryLabel == null) {
            return;
        }
        double stripsPerBox = parseDouble(stripsPerBoxField != null ? stripsPerBoxField.getText() : null);
        String baseUnit = baseUnitComboBox != null && baseUnitComboBox.getValue() != null
                ? baseUnitComboBox.getValue()
                : "شريط";

        if (stripsPerBox <= 0) {
            packagingSummaryLabel.setText("حدد عدد الشرائط داخل العلبة ليتم تجهيز التحويل تلقائيًا.");
            return;
        }

        double stockQuantity = parseDouble(quantityField != null ? quantityField.getText() : null);
        StringBuilder summary = new StringBuilder();
        summary.append("سيُحسب المخزون على أساس ").append(baseUnit)
                .append(". كل 1 علبة = ").append(formatQuantity(stripsPerBox)).append(" شريط.");
        if (stockQuantity > 0) {
            summary.append(" المجموع الحالي: ").append(formatUnitBreakdown(stockQuantity));
        }
        packagingSummaryLabel.setText(summary.toString());
    }

    private String formatUnitBreakdown(double baseQuantity) {
        String baseUnit = baseUnitComboBox != null && baseUnitComboBox.getValue() != null
                ? baseUnitComboBox.getValue()
                : "شريط";
        double stripsPerBox = parseDouble(stripsPerBoxField != null ? stripsPerBoxField.getText() : null);

        if (!"شريط".equals(baseUnit) || stripsPerBox <= 0) {
            return numberFormat.format(baseQuantity) + " " + baseUnit;
        }

        double boxes = baseQuantity / stripsPerBox;
        return numberFormat.format(baseQuantity) + " شريط"
                + " = " + numberFormat.format(boxes) + " علبة";
    }

    private String safeTrim(TextField field) {
        return field != null && field.getText() != null ? field.getText().trim() : "";
    }

    private String safeTrim(ComboBox<String> comboBox) {
        String value = comboBox != null ? comboBox.getValue() : null;
        return value != null ? value.trim() : "";
    }

    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty())
            return 0.0;
        // Remove commas for parsing
        try {
            return Double.parseDouble(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Double parseDoubleOrNull(String value) {
        return parsePositiveDoubleOrNull(value);
    }

    private Double parsePositiveDoubleOrNull(String value) {
        if (value == null || value.trim().isEmpty())
            return null;
        try {
            double v = Double.parseDouble(value.trim().replace(",", ""));
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String generateProductCode() {
        long timestamp = System.currentTimeMillis();
        return "PRD" + timestamp;
    }

    private String formatQuantity(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.valueOf(value);
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

    public static class ProductUnitRow {
        private String unitName;
        private String barcode;
        private double conversionFactor;
        private double salePrice;
        private double salePriceUsd;
        private boolean defaultUnit;
        private boolean activeUnit;

        public ProductUnitRow(String unitName, String barcode, double conversionFactor,
                double salePrice, double salePriceUsd, boolean defaultUnit, boolean activeUnit) {
            this.unitName = unitName;
            this.barcode = barcode;
            this.conversionFactor = conversionFactor;
            this.salePrice = salePrice;
            this.salePriceUsd = salePriceUsd;
            this.defaultUnit = defaultUnit;
            this.activeUnit = activeUnit;
        }

        public static ProductUnitRow fromUnit(ProductUnit unit) {
            return new ProductUnitRow(
                    unit.getUnitName(),
                    unit.getBarcode(),
                    unit.getEffectiveConversionFactor(),
                    unit.getSalePrice() != null ? unit.getSalePrice() : 0,
                    unit.getSalePriceUsd() != null ? unit.getSalePriceUsd() : 0,
                    Boolean.TRUE.equals(unit.getIsDefault()),
                    !Boolean.FALSE.equals(unit.getIsActive())
            );
        }

        public ProductUnit toProductUnit(Product product) {
            ProductUnit unit = new ProductUnit();
            unit.setProduct(product);
            unit.setUnitName(unitName);
            unit.setBarcode(barcode != null && !barcode.trim().isEmpty() ? barcode.trim() : null);
            unit.setConversionFactor(conversionFactor);
            unit.setSalePrice(salePrice > 0 ? salePrice : null);
            unit.setSalePriceUsd(salePriceUsd > 0 ? salePriceUsd : null);
            unit.setIsDefault(defaultUnit);
            unit.setIsActive(activeUnit);
            return unit;
        }

        public String getUnitName() {
            return unitName != null ? unitName : "";
        }

        public String getBarcode() {
            return barcode != null ? barcode : "";
        }

        public double getConversionFactor() {
            return conversionFactor;
        }

        public double getSalePrice() {
            return salePrice;
        }

        public double getSalePriceUsd() {
            return salePriceUsd;
        }

        public boolean isDefaultUnit() {
            return defaultUnit;
        }

        public void setDefaultUnit(boolean defaultUnit) {
            this.defaultUnit = defaultUnit;
        }

        public boolean isActiveUnit() {
            return activeUnit;
        }
    }
}
