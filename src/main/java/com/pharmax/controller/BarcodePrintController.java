package com.pharmax.controller;

import com.pharmax.database.Repository.ProductRepository;
import com.pharmax.model.Product;
import com.pharmax.model.ProductUnit;
import com.pharmax.service.BarcodeLabelPrintService;
import com.pharmax.service.BarcodeService;
import com.pharmax.service.InventoryService;
import com.pharmax.service.ProductUnitService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.embed.swing.SwingFXUtils;
import javafx.util.StringConverter;

import java.awt.image.BufferedImage;
import java.util.List;

public class BarcodePrintController {
    @FXML
    private ComboBox<Product> productComboBox;
    @FXML
    private ComboBox<ProductUnit> unitComboBox;
    @FXML
    private ComboBox<String> printerComboBox;
    @FXML
    private Spinner<Integer> copiesSpinner;
    @FXML
    private TextField widthField;
    @FXML
    private TextField heightField;
    @FXML
    private CheckBox barcodeOnlyCheckBox;
    @FXML
    private CheckBox productNameCheckBox;
    @FXML
    private CheckBox priceCheckBox;
    @FXML
    private ImageView previewImageView;

    private final ProductRepository productRepository = new ProductRepository();
    private final InventoryService inventoryService = new InventoryService();
    private final ProductUnitService productUnitService = new ProductUnitService();
    private final BarcodeService barcodeService = new BarcodeService();
    private final BarcodeLabelPrintService printService = new BarcodeLabelPrintService();

    @FXML
    private void initialize() {
        setupProductComboBox();
        setupUnitComboBox();
        setupPrinterComboBox();
        setupOptions();
        updatePreview();
    }

    private void setupProductComboBox() {
        List<Product> products = productRepository.findAll().stream()
                .filter(product -> Boolean.TRUE.equals(product.getIsActive()))
                .toList();
        productComboBox.setItems(FXCollections.observableArrayList(products));
        productComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product product) {
                return product != null ? product.getName() : "";
            }

            @Override
            public Product fromString(String string) {
                return null;
            }
        });
        productComboBox.valueProperty().addListener((obs, oldProduct, product) -> {
            loadUnits(product);
            updatePreview();
        });
    }

    private void setupUnitComboBox() {
        unitComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ProductUnit unit) {
                if (unit == null) {
                    return "";
                }
                String name = unit.getUnitName() != null ? unit.getUnitName() : "وحدة";
                double factor = unit.getEffectiveConversionFactor();
                return factor == 1.0 ? name : name + " × " + factor;
            }

            @Override
            public ProductUnit fromString(String string) {
                return null;
            }
        });
        unitComboBox.valueProperty().addListener((obs, oldUnit, unit) -> updatePreview());
    }

    private void setupPrinterComboBox() {
        printerComboBox.setItems(FXCollections.observableArrayList(printService.getPrinterNames()));
        if (!printerComboBox.getItems().isEmpty()) {
            printerComboBox.getSelectionModel().selectFirst();
        }
    }

    private void setupOptions() {
        copiesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));
        widthField.setText("50");
        heightField.setText("30");
        productNameCheckBox.setSelected(true);
        priceCheckBox.setSelected(true);

        barcodeOnlyCheckBox.selectedProperty().addListener((obs, oldVal, selected) -> {
            productNameCheckBox.setDisable(selected);
            priceCheckBox.setDisable(selected);
            if (selected) {
                productNameCheckBox.setSelected(false);
                priceCheckBox.setSelected(false);
            }
            updatePreview();
        });
        productNameCheckBox.selectedProperty().addListener((obs, oldVal, selected) -> updatePreview());
        priceCheckBox.selectedProperty().addListener((obs, oldVal, selected) -> updatePreview());
        widthField.textProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        heightField.textProperty().addListener((obs, oldVal, newVal) -> updatePreview());
    }

    private void loadUnits(Product product) {
        unitComboBox.getItems().clear();
        if (product == null) {
            return;
        }
        List<ProductUnit> units = productUnitService.getUnitsForProductOrDefault(product);
        unitComboBox.setItems(FXCollections.observableArrayList(units));
        units.stream()
                .filter(unit -> Boolean.TRUE.equals(unit.getIsDefault()))
                .findFirst()
                .ifPresentOrElse(unitComboBox::setValue, () -> {
                    if (!units.isEmpty()) {
                        unitComboBox.setValue(units.get(0));
                    }
                });
    }

    @FXML
    private void handleGenerateBarcode() {
        Product product = productComboBox.getValue();
        if (product == null) {
            showError("خطأ", "اختر منتجاً أولاً");
            return;
        }

        ProductUnit unit = unitComboBox.getValue();
        String barcode = barcodeService.createBarcodeValue(product, unit);
        try {
            if (unit != null && unit.getId() != null) {
                unit.setBarcode(barcode);
                productUnitService.saveUnit(unit);
            } else {
                product.setBarcode(barcode);
                inventoryService.updateProduct(product);
            }
            loadUnits(product);
            showInfo("تم", "تم توليد الباركود: " + barcode);
            updatePreview();
        } catch (Exception e) {
            showError("خطأ", "فشل توليد الباركود: " + e.getMessage());
        }
    }

    @FXML
    private void handlePrint() {
        try {
            BarcodeLabelPrintService.BarcodeLabelRequest request = buildPrintRequest();
            printService.printLabels(request);
            showInfo("تم", "تم إرسال الملصقات إلى الطابعة");
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }

    @FXML
    private void handleRefreshPreview() {
        updatePreview();
    }

    private BarcodeLabelPrintService.BarcodeLabelRequest buildPrintRequest() {
        Product product = productComboBox.getValue();
        ProductUnit unit = unitComboBox.getValue();
        if (product == null) {
            throw new IllegalArgumentException("اختر منتجاً أولاً");
        }

        String barcode = resolveBarcode(product, unit);
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("لا يوجد باركود لهذا المنتج أو الوحدة. استخدم زر توليد باركود أولاً.");
        }

        BarcodeService.LabelOptions options = buildOptions();
        BarcodeLabelPrintService.BarcodeLabelRequest request = new BarcodeLabelPrintService.BarcodeLabelRequest();
        request.setBarcodeText(barcode);
        request.setProductName(product.getName());
        request.setPriceText(resolvePriceText(product, unit));
        request.setCopies(copiesSpinner.getValue());
        request.setPrinterName(printerComboBox.getValue());
        request.setOptions(options);
        return request;
    }

    private BarcodeService.LabelOptions buildOptions() {
        BarcodeService.LabelOptions options = new BarcodeService.LabelOptions();
        options.setBarcodeOnly(barcodeOnlyCheckBox.isSelected());
        options.setShowProductName(productNameCheckBox.isSelected());
        options.setShowPrice(priceCheckBox.isSelected());
        options.setLabelWidthMm(parsePositive(widthField.getText(), 50.0));
        options.setLabelHeightMm(parsePositive(heightField.getText(), 30.0));
        return options;
    }

    private void updatePreview() {
        if (previewImageView == null) {
            return;
        }
        try {
            Product product = productComboBox != null ? productComboBox.getValue() : null;
            ProductUnit unit = unitComboBox != null ? unitComboBox.getValue() : null;
            String barcode = resolveBarcode(product, unit);
            if (barcode == null || barcode.isBlank()) {
                previewImageView.setImage(null);
                return;
            }
            BufferedImage image = barcodeService.renderLabel(
                    barcode,
                    product != null ? product.getName() : "",
                    resolvePriceText(product, unit),
                    buildOptions()
            );
            previewImageView.setImage(SwingFXUtils.toFXImage(image, null));
        } catch (Exception ignored) {
            previewImageView.setImage(null);
        }
    }

    private String resolveBarcode(Product product, ProductUnit unit) {
        if (unit != null && unit.getBarcode() != null && !unit.getBarcode().isBlank()) {
            return unit.getBarcode().trim();
        }
        if (product != null && product.getBarcode() != null && !product.getBarcode().isBlank()) {
            return product.getBarcode().trim();
        }
        return null;
    }

    private String resolvePriceText(Product product, ProductUnit unit) {
        if (unit != null && unit.getSalePrice() != null && unit.getSalePrice() > 0) {
            return barcodeService.formatPrice(unit.getSalePrice(), "دينار");
        }
        if (unit != null && unit.getSalePriceUsd() != null && unit.getSalePriceUsd() > 0) {
            return barcodeService.formatPrice(unit.getSalePriceUsd(), "دولار");
        }
        if (product != null && product.getUnitPrice() != null && product.getUnitPrice() > 0) {
            return barcodeService.formatPrice(product.getUnitPrice(), "دينار");
        }
        if (product != null && product.getUnitPriceUsd() != null && product.getUnitPriceUsd() > 0) {
            return barcodeService.formatPrice(product.getUnitPriceUsd(), "دولار");
        }
        return "";
    }

    private double parsePositive(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value.trim().replace(",", ""));
            return parsed > 0 ? parsed : fallback;
        } catch (Exception e) {
            return fallback;
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
