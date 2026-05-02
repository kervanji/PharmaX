package com.pharmax.controller;

import com.pharmax.model.Category;
import com.pharmax.model.Product;
import com.pharmax.model.ProductBatch;
import com.pharmax.model.ProductUnit;
import com.pharmax.service.CategoryService;
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
import javafx.stage.Stage;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ProductController {
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
    private Label profitMarginWholesaleLabel;
    @FXML
    private Label profitMarginSpecialLabel;
    @FXML
    private TextField quantityField;
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
    private ComboBox<String> baseUnitComboBox;
    @FXML
    private TableView<ProductUnitRow> packagingTable;
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
    private TextField packagingUnitNameField;
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
    private CheckBox isActiveCheckBox;
    @FXML
    private Button deleteButton;

    private Stage dialogStage;
    private Product product;
    private boolean isEditMode = false;
    private boolean tabMode = false;
    private Double originalCostPrice = null; // Store original cost for sellers who can't see it
    private Double originalUnitPrice = null; // Store original selling price for sellers who can't edit it
    private final InventoryService inventoryService = new InventoryService();
    private final CategoryService categoryService = new CategoryService();
    private final ProductBatchService productBatchService = new ProductBatchService();
    private final ProductUnitService productUnitService = new ProductUnitService();
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
        setupPriceListeners();
        setupPricingModeToggle();
        applyRoleRestrictions();
    }

    private void applyRoleRestrictions() {
        boolean canSeeCost = SessionManager.getInstance().canSeeCost();

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
        List<String> units = Arrays.asList(
                "حبة", "قرص", "كبسولة", "شريط", "علبة", "كرتون",
                "أمبولة", "فيال", "قارورة", "عبوة", "أنبوب", "كيس",
                "حقنة", "مل", "جرام", "قطعة");
        unitOfMeasureComboBox.setItems(FXCollections.observableArrayList(units));
        if (baseUnitComboBox != null) {
            baseUnitComboBox.setItems(FXCollections.observableArrayList(units));
            baseUnitComboBox.setValue("شريط");
        }
        if (unitOfMeasureComboBox.getValue() == null) {
            unitOfMeasureComboBox.setValue("شريط");
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
        packagingTable.setItems(packagingRows);
        if (packagingConversionField != null
                && (packagingConversionField.getText() == null || packagingConversionField.getText().isBlank())) {
            packagingConversionField.setText("1");
        }

        packagingTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, row) -> {
            if (row == null) {
                return;
            }
            packagingUnitNameField.setText(row.getUnitName());
            packagingBarcodeField.setText(row.getBarcode());
            packagingConversionField.setText(String.valueOf(row.getConversionFactor()));
            packagingPriceField.setText(row.getSalePrice() > 0 ? String.valueOf(row.getSalePrice()) : "");
            packagingPriceUsdField.setText(row.getSalePriceUsd() > 0 ? String.valueOf(row.getSalePriceUsd()) : "");
            packagingDefaultCheckBox.setSelected(row.isDefaultUnit());
        });
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

        // Selling price changes trigger their respective profit margin
        unitPriceField.textProperty().addListener((obs, oldVal, newVal) -> updateAllProfitMargins());
        unitPriceUsdField.textProperty().addListener((obs, oldVal, newVal) -> updateAllProfitMargins());
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
            }
        });

        percentagePriceRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                unitPriceField.setVisible(false);
                unitPriceField.setManaged(false);
                profitPercentageField.setVisible(true);
                profitPercentageField.setManaged(true);
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
            }
        } catch (Exception e) {
            // Ignore parsing errors during input
        }
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
            label.setText(String.format("هامش: %s %s (%.1f%%)", numberFormat.format(profit), currency, percentage));
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
        unitOfMeasureComboBox.setValue(product.getUnitOfMeasure());
        loadBatchFields(product);
        loadStripsPerBox(product);
        if (baseUnitComboBox != null) {
            String baseUnit = product.getBaseUnit() != null ? product.getBaseUnit() : product.getUnitOfMeasure();
            baseUnitComboBox.setValue(baseUnit != null ? baseUnit : "شريط");
        }
        isActiveCheckBox.setSelected(product.getIsActive());
        loadPackagingRows(product);

        applySellingPriceEditRestriction();
        updateAllProfitMargins();
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
                .filter(unit -> unit.getUnitName() != null && unit.getUnitName().trim().equals("علبة"))
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
    }

    @FXML
    private void handleAddPackagingUnit() {
        String unitName = safeTrim(packagingUnitNameField);
        if (unitName.isEmpty()) {
            showError("خطأ", "اسم وحدة التعبئة مطلوب");
            packagingUnitNameField.requestFocus();
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
                packagingDefaultCheckBox.isSelected()
        );

        ProductUnitRow selected = packagingTable != null ? packagingTable.getSelectionModel().getSelectedItem() : null;
        ProductUnitRow existing = packagingRows.stream()
                .filter(item -> item != selected)
                .filter(item -> item.getUnitName().equalsIgnoreCase(unitName))
                .findFirst()
                .orElse(null);

        if (selected != null && existing != null) {
            showError("Ø®Ø·Ø£", "ÙˆØ­Ø¯Ø© Ø§Ù„ØªØ¹Ø¨Ø¦Ø© Ù…Ø³Ø¬Ù„Ø© Ù…Ø³Ø¨Ù‚Ø§Ù‹: " + unitName);
            packagingUnitNameField.requestFocus();
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
        clearPackagingInputs();
    }

    @FXML
    private void handleRemovePackagingUnit() {
        ProductUnitRow selected = packagingTable != null ? packagingTable.getSelectionModel().getSelectedItem() : null;
        if (selected != null) {
            packagingRows.remove(selected);
            packagingTable.refresh();
            clearPackagingInputs();
        }
    }

    private void clearPackagingInputs() {
        packagingUnitNameField.clear();
        packagingBarcodeField.clear();
        packagingConversionField.setText("1");
        packagingPriceField.clear();
        packagingPriceUsdField.clear();
        packagingDefaultCheckBox.setSelected(false);
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
            product.setQuantityInStock(parseDouble(quantityField.getText()));
            product.setMinimumStock(parseDouble(minimumStockField.getText()));
            product.setMaximumStock(parseDoubleOrNull(maximumStockField.getText()));
            String baseUnit = baseUnitComboBox != null && baseUnitComboBox.getValue() != null
                    ? baseUnitComboBox.getValue()
                    : unitOfMeasureComboBox.getValue();
            product.setBaseUnit(baseUnit);
            product.setUnitOfMeasure(baseUnit);
            product.setIsActive(isActiveCheckBox.isSelected());

            Product savedProduct;
            if (isEditMode) {
                savedProduct = inventoryService.updateProduct(product);
                productUnitService.replaceUnitsForProduct(savedProduct, buildProductUnits(savedProduct));
                syncOpeningBatch(savedProduct);
                showInfo("تم التحديث", "تم تحديث المنتج بنجاح");
            } else {
                savedProduct = inventoryService.createProduct(product);
                productUnitService.replaceUnitsForProduct(savedProduct, buildProductUnits(savedProduct));
                syncOpeningBatch(savedProduct);
                showInfo("تم الإضافة", "تم إضافة المنتج بنجاح");
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
            com.pharmax.util.TabManager.getInstance().closeTab("new-product");
        } else if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private List<ProductUnit> buildProductUnits(Product savedProduct) {
        List<ProductUnitRow> rows = new ArrayList<>(packagingRows);
        double stripsPerBox = stripsPerBoxField != null ? parseDouble(stripsPerBoxField.getText()) : 0.0;
        if (stripsPerBox > 0) {
            rows.removeIf(row -> "علبة".equals(row.getUnitName()));
            rows.add(new ProductUnitRow("علبة", "", stripsPerBox, 0, 0, false));
        }

        return rows.stream()
                .map(row -> row.toProductUnit(savedProduct))
                .toList();
    }

    private void syncOpeningBatch(Product savedProduct) {
        if (savedProduct == null || savedProduct.getId() == null) {
            return;
        }

        double quantity = parseDouble(quantityField.getText());
        LocalDate expiryDate = expiryDatePicker != null ? expiryDatePicker.getValue() : null;
        ProductBatch openingBatch = findPreferredBatch(savedProduct).orElse(null);
        if (quantity <= 0 && openingBatch == null) {
            return;
        }

        String batchNumber = openingBatch != null ? openingBatch.getBatchNumber() : "OPENING-" + savedProduct.getProductCode();
        double currentOpeningQuantity = openingBatch != null ? openingBatch.getQuantity() : 0.0;
        double quantityDelta = quantity - currentOpeningQuantity;
        Double unitCost = savedProduct.getCostPrice() != null ? savedProduct.getCostPrice() : savedProduct.getCostPriceUsd();
        String currency = savedProduct.getCostPrice() != null ? "دينار" : "دولار";

        if (Math.abs(quantityDelta) > 1e-9) {
            productBatchService.createOrUpdateBatch(
                    savedProduct,
                    batchNumber,
                    expiryDate,
                    quantityDelta,
                    unitCost,
                    currency,
                    null,
                    true);
        } else if (openingBatch != null && !java.util.Objects.equals(expiryDate, openingBatch.getExpiryDate())) {
            productBatchService.updateBatchExpiry(openingBatch, expiryDate);
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

        if (!validateRequiredCurrencyPair("السعر المفرد", unitPriceField, unitPriceUsdField)) {
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

        if (stripsPerBoxField != null && hasText(stripsPerBoxField)) {
            Double stripsPerBox = parsePositiveDoubleOrNull(stripsPerBoxField.getText());
            if (stripsPerBox == null) {
                showError("خطأ", "عدد الشرائط في العلبة يجب أن يكون رقماً أكبر من صفر");
                stripsPerBoxField.requestFocus();
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

    private String safeTrim(TextField field) {
        return field != null && field.getText() != null ? field.getText().trim() : "";
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

        public ProductUnitRow(String unitName, String barcode, double conversionFactor,
                double salePrice, double salePriceUsd, boolean defaultUnit) {
            this.unitName = unitName;
            this.barcode = barcode;
            this.conversionFactor = conversionFactor;
            this.salePrice = salePrice;
            this.salePriceUsd = salePriceUsd;
            this.defaultUnit = defaultUnit;
        }

        public static ProductUnitRow fromUnit(ProductUnit unit) {
            return new ProductUnitRow(
                    unit.getUnitName(),
                    unit.getBarcode(),
                    unit.getEffectiveConversionFactor(),
                    unit.getSalePrice() != null ? unit.getSalePrice() : 0,
                    unit.getSalePriceUsd() != null ? unit.getSalePriceUsd() : 0,
                    Boolean.TRUE.equals(unit.getIsDefault())
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
            unit.setIsActive(true);
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
    }
}
