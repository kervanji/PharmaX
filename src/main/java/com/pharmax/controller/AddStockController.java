package com.pharmax.controller;

import com.pharmax.model.Product;
import com.pharmax.model.ProductUnit;
import com.pharmax.service.InventoryService;
import com.pharmax.service.ProductUnitService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AddStockController {
    @FXML
    private ComboBox<Product> productComboBox;
    @FXML
    private ComboBox<String> categoryFilterComboBox;
    @FXML
    private Label currentStockLabel;
    @FXML
    private Label minimumStockLabel;
    @FXML
    private TextField quantityField;
    @FXML
    private ComboBox<String> quantityUnitComboBox;
    @FXML
    private Label quantityBreakdownLabel;
    @FXML
    private Label newStockLabel;

    private Stage dialogStage;
    private final InventoryService inventoryService = new InventoryService();
    private final ProductUnitService productUnitService = new ProductUnitService();
    private final DecimalFormat numberFormat;
    private boolean tabMode = false;
    private FilteredList<Product> filteredProducts;
    private ObservableList<Product> allProducts;

    public AddStockController() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        numberFormat = new DecimalFormat("#,##0.##", symbols);
    }

    @FXML
    private void initialize() {
        loadProducts();
        setupProductComboBox();
        setupQuantityListener();
    }

    private void loadProducts() {
        List<Product> products = inventoryService.getActiveProducts().stream()
                .filter(product -> !Boolean.TRUE.equals(product.getIsUnlimitedStock()))
                .toList();
        allProducts = FXCollections.observableArrayList(products);
        filteredProducts = new FilteredList<>(allProducts, p -> true);
        productComboBox.setItems(filteredProducts);
        productComboBox.setEditable(true);
        setupCategoryFilter(products);
    }

    private void setupCategoryFilter(List<Product> products) {
        if (categoryFilterComboBox == null)
            return;
        List<String> categories = products.stream()
                .map(Product::getCategory)
                .filter(c -> c != null && !c.trim().isEmpty())
                .distinct()
                .sorted()
                .toList();

        ObservableList<String> items = FXCollections.observableArrayList();
        items.add("كل الفئات");
        items.addAll(categories);
        categoryFilterComboBox.setItems(items);
        categoryFilterComboBox.setValue("كل الفئات");

        categoryFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            applyProductFilters();
        });
    }

    private void applyProductFilters() {
        String selectedCategory = categoryFilterComboBox != null ? categoryFilterComboBox.getValue() : null;
        boolean allCategories = selectedCategory == null || selectedCategory.equals("كل الفئات");
        String queryText = productComboBox.getEditor().getText();
        String query = queryText == null ? "" : queryText.trim().toLowerCase();

        filteredProducts.setPredicate(p -> {
            if (!allCategories) {
                String cat = p.getCategory();
                if (cat == null || !cat.equals(selectedCategory)) {
                    return false;
                }
            }
            if (query.isEmpty())
                return true;
            String name = p.getName() != null ? p.getName().toLowerCase() : "";
            return name.contains(query);
        });

        if (!productComboBox.isShowing() && productComboBox.getScene() != null && productComboBox.isFocused()) {
            productComboBox.show();
        }
    }

    private void setupProductComboBox() {
        productComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product product) {
                if (product == null)
                    return null;
                return product.getName();
            }

            @Override
            public Product fromString(String string) {
                if (string == null || string.isEmpty())
                    return null;
                return allProducts.stream()
                        .filter(p -> p.getName() != null && p.getName().equals(string))
                        .findFirst()
                        .orElse(null);
            }
        });

        if (productComboBox.getEditor() != null) {
            productComboBox.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                if (productComboBox.getValue() != null) {
                    String rendered = productComboBox.getConverter().toString(productComboBox.getValue());
                    if (rendered.equals(newText))
                        return;
                }
                if (isProductSelectionText(newText)) {
                    return;
                }
                applyProductFilters();
            });
        }

        productComboBox.setOnAction(e -> updateProductInfo());
    }

    private void setupQuantityListener() {
        quantityField.textProperty().addListener((obs, oldVal, newVal) -> updateNewStock());
        if (quantityUnitComboBox != null) {
            quantityUnitComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateNewStock());
        }
    }

    private void updateProductInfo() {
        Product selected = productComboBox.getValue();
        if (selected != null) {
            refreshQuantityUnitOptions(selected);
            currentStockLabel.setText(formatInventoryQuantity(selected, safe(selected.getQuantityInStock())));
            minimumStockLabel.setText(formatInventoryQuantity(selected, safe(selected.getMinimumStock())));
            updateNewStock();
        } else {
            if (quantityUnitComboBox != null) {
                quantityUnitComboBox.hide();
                quantityUnitComboBox.getItems().clear();
            }
            currentStockLabel.setText("0");
            minimumStockLabel.setText("0");
            newStockLabel.setText("0");
            if (quantityBreakdownLabel != null) {
                quantityBreakdownLabel.setText("اختر المنتج ثم أدخل الكمية ووحدتها");
            }
        }
    }

    private boolean isProductSelectionText(String text) {
        if (text == null || allProducts == null || productComboBox == null || productComboBox.getConverter() == null) {
            return false;
        }

        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        return allProducts.stream()
                .anyMatch(product -> normalized.equals(productComboBox.getConverter().toString(product)));
    }

    private void updateNewStock() {
        Product selected = productComboBox.getValue();
        if (selected != null) {
            try {
                double enteredQuantity = Double.parseDouble(quantityField.getText().trim());
                double baseQuantity = convertQuantityToBase(selected, enteredQuantity, getSelectedInputUnit(selected));
                double newStock = safe(selected.getQuantityInStock()) + baseQuantity;
                newStockLabel.setText(formatInventoryQuantity(selected, newStock));
                updateQuantityBreakdown(selected, enteredQuantity, baseQuantity);

                if (newStock > safe(selected.getMinimumStock())) {
                    newStockLabel
                            .setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -fx-success-dark;");
                } else {
                    newStockLabel
                            .setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -fx-warning-text;");
                }
            } catch (NumberFormatException e) {
                newStockLabel.setText(formatInventoryQuantity(selected, safe(selected.getQuantityInStock())));
                if (quantityBreakdownLabel != null) {
                    quantityBreakdownLabel.setText("أدخل الكمية ليظهر التحويل قبل الإضافة");
                }
            }
        }
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    public void setProduct(Product product) {
        productComboBox.setValue(product);
        productComboBox.setDisable(true);
        updateProductInfo();
    }

    @FXML
    private void handleAdd() {
        Product selected = productComboBox.getValue();

        if (selected == null) {
            showError("خطأ", "يرجى اختيار المنتج");
            return;
        }

        String quantityText = quantityField.getText().trim();
        if (quantityText.isEmpty()) {
            showError("خطأ", "يرجى إدخال الكمية");
            quantityField.requestFocus();
            return;
        }

        try {
            double enteredQuantity = Double.parseDouble(quantityText);
            if (enteredQuantity <= 0) {
                showError("خطأ", "الكمية يجب أن تكون أكبر من صفر");
                return;
            }

            String inputUnit = getSelectedInputUnit(selected);
            double baseQuantity = convertQuantityToBase(selected, enteredQuantity, inputUnit);
            inventoryService.addStock(selected.getId(), baseQuantity);
            showInfo("تم", "تمت إضافة " + numberFormat.format(enteredQuantity) + " " + inputUnit
                    + " (" + formatInventoryQuantity(selected, baseQuantity) + ") إلى مخزون " + selected.getName());
            closeForm();

        } catch (NumberFormatException e) {
            showError("خطأ", "الكمية يجب أن تكون رقماً");
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    private void refreshQuantityUnitOptions(Product product) {
        if (quantityUnitComboBox == null || product == null) {
            return;
        }
        String currentValue = quantityUnitComboBox.getValue();
        String baseUnit = productUnitService.resolveBaseUnit(product);
        Set<String> units = new LinkedHashSet<>();
        addUnitOption(units, baseUnit);
        for (ProductUnit unit : productUnitService.getUnitsForProductOrDefault(product)) {
            if (unit == null || Boolean.FALSE.equals(unit.getIsActive())) {
                continue;
            }
            addUnitOption(units, unit.getUnitName());
        }

        ObservableList<String> options = FXCollections.observableArrayList(units);
        quantityUnitComboBox.hide();
        quantityUnitComboBox.setItems(options);
        if (currentValue != null && options.contains(currentValue)) {
            quantityUnitComboBox.setValue(currentValue);
        } else if (options.contains(baseUnit)) {
            quantityUnitComboBox.setValue(baseUnit);
        } else if (!options.isEmpty()) {
            quantityUnitComboBox.setValue(options.get(0));
        }
    }

    private void addUnitOption(Set<String> units, String unitName) {
        if (unitName == null) {
            return;
        }
        String trimmed = unitName.trim();
        if (!trimmed.isEmpty()) {
            units.add(trimmed);
        }
    }

    private String getSelectedInputUnit(Product product) {
        String selectedUnit = quantityUnitComboBox != null ? quantityUnitComboBox.getValue() : null;
        if (selectedUnit != null && !selectedUnit.trim().isEmpty()) {
            return selectedUnit.trim();
        }
        return productUnitService.resolveBaseUnit(product);
    }

    private double convertQuantityToBase(Product product, double quantity, String unitName) {
        if (quantity <= 0) {
            return quantity;
        }
        return quantity * resolveConversionFactor(product, unitName);
    }

    private double resolveConversionFactor(Product product, String unitName) {
        String baseUnit = productUnitService.resolveBaseUnit(product);
        if (unitName == null || unitName.trim().isEmpty() || unitName.equals(baseUnit)) {
            return 1.0;
        }
        return productUnitService.getUnitsForProductOrDefault(product).stream()
                .filter(unit -> unit.getUnitName() != null && unit.getUnitName().equals(unitName))
                .findFirst()
                .map(ProductUnit::getEffectiveConversionFactor)
                .orElse(1.0);
    }

    private void updateQuantityBreakdown(Product product, double enteredQuantity, double baseQuantity) {
        if (quantityBreakdownLabel == null) {
            return;
        }
        String inputUnit = getSelectedInputUnit(product);
        String baseUnit = productUnitService.resolveBaseUnit(product);
        if (inputUnit.equals(baseUnit)) {
            quantityBreakdownLabel.setText("الإضافة: " + formatInventoryQuantity(product, baseQuantity));
        } else {
            quantityBreakdownLabel.setText("أدخلت " + numberFormat.format(enteredQuantity) + " " + inputUnit
                    + "، ستُضاف داخلياً: " + formatInventoryQuantity(product, baseQuantity));
        }
    }

    private String formatInventoryQuantity(Product product, double quantity) {
        String baseUnit = productUnitService.resolveBaseUnit(product);
        String baseDisplay = numberFormat.format(quantity) + " " + baseUnit;
        ProductUnit largestUnit = productUnitService.getUnitsForProductOrDefault(product).stream()
                .filter(unit -> unit != null && !Boolean.FALSE.equals(unit.getIsActive()))
                .filter(unit -> unit.getUnitName() != null && !unit.getUnitName().equals(baseUnit))
                .filter(unit -> unit.getEffectiveConversionFactor() > 1.0)
                .max((left, right) -> Double.compare(left.getEffectiveConversionFactor(), right.getEffectiveConversionFactor()))
                .orElse(null);
        if (largestUnit == null) {
            return baseDisplay;
        }
        return baseDisplay + " = "
                + numberFormat.format(quantity / largestUnit.getEffectiveConversionFactor())
                + " " + largestUnit.getUnitName();
    }

    private double safe(Double value) {
        return value != null ? value : 0.0;
    }

    @FXML
    private void handleCancel() {
        closeForm();
    }

    private void closeForm() {
        if (tabMode) {
            com.pharmax.util.TabManager.getInstance().closeTab("add-stock");
        } else if (dialogStage != null) {
            dialogStage.close();
        }
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
