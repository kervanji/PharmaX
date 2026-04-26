package com.pharmax.controller;

import com.pharmax.model.Product;
import com.pharmax.service.InventoryService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.List;

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
    private Label newStockLabel;

    private Stage dialogStage;
    private final InventoryService inventoryService = new InventoryService();
    private boolean tabMode = false;
    private FilteredList<Product> filteredProducts;
    private ObservableList<Product> allProducts;

    @FXML
    private void initialize() {
        loadProducts();
        setupProductComboBox();
        setupQuantityListener();
    }

    private void loadProducts() {
        List<Product> products = inventoryService.getActiveProducts();
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
                applyProductFilters();
            });
        }

        productComboBox.setOnAction(e -> updateProductInfo());
    }

    private void setupQuantityListener() {
        quantityField.textProperty().addListener((obs, oldVal, newVal) -> updateNewStock());
    }

    private void updateProductInfo() {
        Product selected = productComboBox.getValue();
        if (selected != null) {
            currentStockLabel.setText(String.valueOf(selected.getQuantityInStock()));
            minimumStockLabel.setText(String.valueOf(selected.getMinimumStock()));
            updateNewStock();
        } else {
            currentStockLabel.setText("0");
            minimumStockLabel.setText("0");
            newStockLabel.setText("0");
        }
    }

    private void updateNewStock() {
        Product selected = productComboBox.getValue();
        if (selected != null) {
            try {
                double quantity = Double.parseDouble(quantityField.getText().trim());
                double newStock = selected.getQuantityInStock() + quantity;
                newStockLabel.setText(String.valueOf(newStock));

                if (newStock > selected.getMinimumStock()) {
                    newStockLabel
                            .setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -fx-success-dark;");
                } else {
                    newStockLabel
                            .setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -fx-warning-text;");
                }
            } catch (NumberFormatException e) {
                newStockLabel.setText(String.valueOf(selected.getQuantityInStock()));
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
            double quantity = Double.parseDouble(quantityText);
            if (quantity <= 0) {
                showError("خطأ", "الكمية يجب أن تكون أكبر من صفر");
                return;
            }

            inventoryService.addStock(selected.getId(), quantity);
            showInfo("تم", "تمت إضافة " + quantity + " وحدة إلى مخزون " + selected.getName());
            closeForm();

        } catch (NumberFormatException e) {
            showError("خطأ", "الكمية يجب أن تكون رقماً");
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
