package com.pharmax.controller;

import com.pharmax.model.Product;
import com.pharmax.model.VoucherItem;
import com.pharmax.service.InventoryService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.List;

public class VoucherItemFormController {
    @FXML private Label titleLabel;
    @FXML private ComboBox<Product> productCombo;
    @FXML private TextField productNameField;
    @FXML private TextField quantityField;
    @FXML private TextField unitPriceField;
    @FXML private ComboBox<String> unitOfMeasureCombo;
    @FXML private TextArea notesArea;
    @FXML private CheckBox addToInventoryCheckBox;

    private final InventoryService inventoryService = new InventoryService();

    private Stage dialogStage;
    private VoucherItem voucherItem;
    private boolean saved = false;

    @FXML
    private void initialize() {
        loadProducts();
        loadUnitsOfMeasure();
        setupProductConverter();
        setupProductSelectionSync();
    }

    private void loadProducts() {
        List<Product> products = inventoryService.getAllProducts();
        productCombo.setItems(FXCollections.observableArrayList(products));
    }

    private void loadUnitsOfMeasure() {
        List<String> units = Arrays.asList("قطعة", "كيلو", "متر", "لتر", "علبة", "كرتون", "طن", "جرام");
        unitOfMeasureCombo.setItems(FXCollections.observableArrayList(units));
    }

    private void setupProductConverter() {
        productCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product product) {
                if (product == null) return "";
                return product.getProductCode() + " - " + product.getName();
            }

            @Override
            public Product fromString(String s) {
                if (s == null || s.isBlank()) return null;
                return productCombo.getItems().stream()
                        .filter(p -> (p.getProductCode() + " - " + p.getName()).equals(s) || p.getName().contains(s))
                        .findFirst().orElse(null);
            }
        });
    }

    private void setupProductSelectionSync() {
        productCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                productNameField.setText(newVal.getName());
                if (newVal.getUnitOfMeasure() != null && !newVal.getUnitOfMeasure().isBlank()) {
                    unitOfMeasureCombo.setValue(newVal.getUnitOfMeasure());
                }
                if (newVal.getCostPrice() != null) {
                    unitPriceField.setText(String.valueOf(newVal.getCostPrice()));
                }
            }
        });
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setDefaultAddToInventory(boolean defaultValue) {
        addToInventoryCheckBox.setSelected(defaultValue);
    }

    public void setVoucherItem(VoucherItem voucherItem) {
        this.voucherItem = voucherItem;

        if (voucherItem.getProduct() != null) {
            productCombo.setValue(voucherItem.getProduct());
            productNameField.setText(voucherItem.getProduct().getName());
        } else {
            productNameField.setText(voucherItem.getProductName());
        }

        quantityField.setText(voucherItem.getQuantity() != null ? String.valueOf(voucherItem.getQuantity()) : "1");
        unitPriceField.setText(voucherItem.getUnitPrice() != null ? String.valueOf(voucherItem.getUnitPrice()) : "0");
        unitOfMeasureCombo.setValue(voucherItem.getUnitOfMeasure());
        notesArea.setText(voucherItem.getNotes());
        addToInventoryCheckBox.setSelected(Boolean.TRUE.equals(voucherItem.getAddToInventory()));
    }

    public VoucherItem getVoucherItem() {
        return voucherItem;
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void handleSave() {
        if (!validateInput()) {
            return;
        }

        if (voucherItem == null) {
            voucherItem = new VoucherItem();
        }

        Product selectedProduct = productCombo.getValue();
        if (selectedProduct != null) {
            voucherItem.setProduct(selectedProduct);
            voucherItem.setProductName(selectedProduct.getName());
        } else {
            voucherItem.setProduct(null);
            voucherItem.setProductName(productNameField.getText().trim());
        }

        voucherItem.setQuantity(parseDouble(quantityField.getText()));
        voucherItem.setUnitPrice(parseDouble(unitPriceField.getText()));
        voucherItem.setUnitOfMeasure(unitOfMeasureCombo.getValue());
        voucherItem.setNotes(notesArea.getText());
        voucherItem.setAddToInventory(addToInventoryCheckBox.isSelected());
        voucherItem.calculateTotal();

        saved = true;
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    @FXML
    private void handleCancel() {
        saved = false;
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private boolean validateInput() {
        Product selectedProduct = productCombo.getValue();
        String manualName = productNameField.getText() != null ? productNameField.getText().trim() : "";

        if (selectedProduct == null && manualName.isEmpty()) {
            showError("خطأ", "يرجى اختيار مادة من المخزون أو إدخال اسم المادة يدوياً");
            productNameField.requestFocus();
            return false;
        }

        double qty;
        try {
            qty = parseDouble(quantityField.getText());
        } catch (Exception e) {
            showError("خطأ", "الكمية يجب أن تكون رقماً");
            quantityField.requestFocus();
            return false;
        }
        if (qty <= 0) {
            showError("خطأ", "الكمية يجب أن تكون أكبر من صفر");
            quantityField.requestFocus();
            return false;
        }

        double price;
        try {
            price = parseDouble(unitPriceField.getText());
        } catch (Exception e) {
            showError("خطأ", "سعر الوحدة يجب أن يكون رقماً");
            unitPriceField.requestFocus();
            return false;
        }
        if (price < 0) {
            showError("خطأ", "سعر الوحدة لا يمكن أن يكون سالباً");
            unitPriceField.requestFocus();
            return false;
        }

        return true;
    }

    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) return 0.0;
        return Double.parseDouble(value.trim().replace(",", ""));
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
