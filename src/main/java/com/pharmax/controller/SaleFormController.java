package com.pharmax.controller;

import com.pharmax.model.*;
import com.pharmax.service.*;
import com.pharmax.database.Repository.*;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import javafx.application.Platform;
import com.pharmax.util.AppConfigStore;
import com.pharmax.util.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;

public class SaleFormController {
    private static final Logger logger = LoggerFactory.getLogger(SaleFormController.class);
    private static final String IQD_CURRENCY = "دينار";
    private static final String DEFAULT_SALE_CUSTOMER_CODE = "CASH";
    private static final String DEFAULT_SALE_CUSTOMER_NAME = "زبون نقدي";
    private static final String DEFAULT_PRICE_TYPE = "مفرد";

    @FXML
    private VBox root;
    @FXML
    private ComboBox<Customer> customerComboBox;
    @FXML
    private ComboBox<String> projectLocationComboBox;
    @FXML
    private TextField newProjectLocationField;
    @FXML
    private Button addProjectLocationBtn;
    private FilteredList<Customer> filteredCustomers;
    private String customerSearchQuery = "";
    @FXML
    private ComboBox<String> categoryFilterComboBox;
    @FXML
    private ComboBox<Product> productComboBox;
    @FXML
    private ComboBox<ProductUnit> unitComboBox;
    @FXML
    private ComboBox<String> priceTypeComboBox;
    @FXML
    private TextField quantityField;
    @FXML
    private Label stockLabel;
    @FXML
    private Label priceLabel;
    @FXML
    private Label productAvailabilitySummaryLabel;
    @FXML
    private Button medicineDetailsButton;
    @FXML
    private FlowPane quickSaleButtonsPane;
    @FXML
    private TableView<SaleItemRow> itemsTable;
    @FXML
    private TableColumn<SaleItemRow, String> productNameColumn;
    @FXML
    private TableColumn<SaleItemRow, Double> quantityColumn;
    @FXML
    private TableColumn<SaleItemRow, String> soldUnitColumn;
    @FXML
    private TableColumn<SaleItemRow, Double> conversionFactorColumn;
    @FXML
    private TableColumn<SaleItemRow, Double> baseQuantityColumn;
    @FXML
    private TableColumn<SaleItemRow, String> batchPreviewColumn;
    @FXML
    private TableColumn<SaleItemRow, Double> unitPriceColumn;
    @FXML
    private TableColumn<SaleItemRow, String> stripPriceColumn;
    @FXML
    private TableColumn<SaleItemRow, String> boxPriceColumn;
    @FXML
    private TableColumn<SaleItemRow, Double> discountColumn;
    @FXML
    private TableColumn<SaleItemRow, Double> totalColumn;
    @FXML
    private TableColumn<SaleItemRow, Void> editColumn;
    @FXML
    private TableColumn<SaleItemRow, Void> actionColumn;
    @FXML
    private RadioButton cashRadio;
    @FXML
    private RadioButton creditRadio;
    @FXML
    private ToggleGroup paymentGroup;
    @FXML
    private ComboBox<String> currencyComboBox;
    @FXML
    private VBox exchangeRateBox;
    @FXML
    private TextField exchangeRateField;
    @FXML
    private TextField additionalDiscountField;
    @FXML
    private HBox paymentControlsBox;
    @FXML
    private Separator paymentControlsSeparator;
    @FXML
    private VBox additionalDiscountBox;
    @FXML
    private Label additionalDiscountCurrencyLabel;
    @FXML
    private Label subtotalLabel;
    @FXML
    private Label discountLabel;
    @FXML
    private Label finalTotalLabel;
    @FXML
    private TextField paidAmountField;
    @FXML
    private VBox paidAmountBox;
    @FXML
    private Label paidAmountCurrencyLabel;
    @FXML
    private Label balanceLabel;
    @FXML
    private Label balanceStatusLabel;
    @FXML
    private TextArea notesArea;
    @FXML
    private CheckBox printCheckbox;
    @FXML
    private CheckBox quickSaleCheckbox;

    private static final String QUICK_SALE_ENABLED_KEY = "sale.quick.enabled";
    private static final String QUICK_SALE_DEFAULT_KEY = "sale.quick.default";
    private final AppConfigStore configStore = new AppConfigStore();

    private Stage dialogStage;
    private final SalesService salesService;
    private final ReceiptService receiptService;
    private final ProductUnitService productUnitService;
    private final ProductBatchService productBatchService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ObservableList<SaleItemRow> saleItems = FXCollections.observableArrayList();
    private FilteredList<Product> filteredProducts;
    private String productSearchQuery = "";
    private Product selectedProduct = null;
    private ProductUnit selectedUnit = null;
    private ProductUnit pendingBarcodeUnit = null;
    private final Map<Long, List<ProductUnit>> unitsByProduct = new HashMap<>();
    private final DecimalFormat numberFormatter;
    @SuppressWarnings("unused") private com.pharmax.MainApp mainApp;
    private boolean tabMode = false;

    public void setMainApp(com.pharmax.MainApp mainApp) {
        this.mainApp = mainApp;
    }

    public SaleFormController() {
        this.salesService = new SalesService();
        this.receiptService = new ReceiptService();
        this.productUnitService = new ProductUnitService();
        this.productBatchService = new ProductBatchService();
        this.customerRepository = new CustomerRepository();
        this.productRepository = new ProductRepository();

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        this.numberFormatter = new DecimalFormat("#,##0.00", symbols);
    }

    @FXML
    private void initialize() {
        setupPaymentToggleGroup();
        setupCurrencyComboBox();
        setupCustomerComboBox();
        setupProductComboBox();
        setupUnitComboBox();
        setupPriceTypeComboBox();
        setupQuickSaleButtons();
        setupProductPreviewPanel();
        setupItemsTable();
        setupDefaults();
        setupSaleOptions();
        selectDefaultSaleCustomer();
        applyRoleRestrictions();

        Platform.runLater(() -> {
            if (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage s) {
                dialogStage = s;
            }
        });
    }

    private void setupCurrencyComboBox() {
        if (currencyComboBox == null) {
            return;
        }

        currencyComboBox.setItems(FXCollections.observableArrayList(IQD_CURRENCY));
        currencyComboBox.setValue(IQD_CURRENCY);
        currencyComboBox.setDisable(true);
        if (exchangeRateField != null
                && (exchangeRateField.getText() == null || exchangeRateField.getText().trim().isEmpty())) {
            exchangeRateField.setText("1500");
        }
        updateExchangeRateVisibility(IQD_CURRENCY);
        updateCurrencyLabels(IQD_CURRENCY);
        currencyComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!IQD_CURRENCY.equals(newVal)) {
                Platform.runLater(() -> currencyComboBox.setValue(IQD_CURRENCY));
                return;
            }
            updateExchangeRateVisibility(IQD_CURRENCY);
            updateCurrencyLabels(IQD_CURRENCY);
            updateSelectedProductPriceLabel();
            updateTotals();
        });
    }

    @FXML
    private void handleExchangeRateChange() {
        updateTotals();
        updateSelectedProductPriceLabel();
    }

    private void setupPriceTypeComboBox() {
        if (priceTypeComboBox != null) {
            priceTypeComboBox.setItems(FXCollections.observableArrayList("مفرد", "جملة", "خاص"));
            priceTypeComboBox.setValue(DEFAULT_PRICE_TYPE);
            priceTypeComboBox.setDisable(true);
            priceTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (!DEFAULT_PRICE_TYPE.equals(newVal)) {
                    Platform.runLater(() -> priceTypeComboBox.setValue(DEFAULT_PRICE_TYPE));
                    return;
                }
                updateSelectedProductPriceLabel();
            });
        }
    }

    private void setupSaleOptions() {
        boolean allowQuickSale = isQuickSaleAllowed();
        boolean quickSaleDefault = isQuickSaleDefaultEnabled();

        if (printCheckbox != null) {
            printCheckbox.setSelected(true);
        }
        if (quickSaleCheckbox != null) {
            quickSaleCheckbox.setVisible(allowQuickSale);
            quickSaleCheckbox.setManaged(allowQuickSale);
            quickSaleCheckbox.setSelected(allowQuickSale && quickSaleDefault);
            quickSaleCheckbox.selectedProperty().addListener((obs, oldVal, quickSale) -> updateQuickSaleState());
        }
        updateQuickSaleState();
    }

    private void updateQuickSaleState() {
        boolean quickSale = quickSaleCheckbox != null && quickSaleCheckbox.isVisible() && quickSaleCheckbox.isSelected();
        if (printCheckbox != null) {
            if (quickSale) {
                printCheckbox.setSelected(false);
            }
            printCheckbox.setDisable(quickSale);
        }
    }

    private boolean isQuickSaleAllowed() {
        return Boolean.parseBoolean(configStore.load().getProperty(QUICK_SALE_ENABLED_KEY, "false"));
    }

    private boolean isQuickSaleDefaultEnabled() {
        return Boolean.parseBoolean(configStore.load().getProperty(QUICK_SALE_DEFAULT_KEY, "false"));
    }

    private void setupUnitComboBox() {
        if (unitComboBox == null) {
            return;
        }
        unitComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ProductUnit unit) {
                if (unit == null) {
                    return "";
                }
                String name = unit.getUnitName() != null ? unit.getUnitName() : "وحدة";
                double factor = unit.getEffectiveConversionFactor();
                return factor == 1.0 ? name : name + " × " + numberFormatter.format(factor);
            }

            @Override
            public ProductUnit fromString(String string) {
                return null;
            }
        });
        unitComboBox.valueProperty().addListener((obs, oldUnit, newUnit) -> {
            selectedUnit = newUnit;
            updateSelectedProductStockLabel();
            updateSelectedProductPriceLabel();
            updateProductPreviewPanel();
        });
    }

    private void setupProductPreviewPanel() {
        if (quantityField != null) {
            quantityField.textProperty().addListener((obs, oldValue, newValue) -> updateProductPreviewPanel());
        }
        clearProductPreviewPanel();
    }

    private void updateExchangeRateVisibility(String currency) {
        boolean isUsd = false;
        if (exchangeRateBox != null) {
            exchangeRateBox.setVisible(isUsd);
            exchangeRateBox.setManaged(isUsd);
        }
    }

    private double getExchangeRateOrDefault() {
        double rate = 1500.0;
        if (exchangeRateField == null || exchangeRateField.getText() == null) {
            return rate;
        }

        try {
            String text = exchangeRateField.getText().trim().replace(",", "");
            if (!text.isEmpty()) {
                rate = Double.parseDouble(text);
            }
        } catch (NumberFormatException ignored) {
        }

        if (rate <= 0) {
            rate = 1500.0;
        }
        return rate;
    }

    private void updateCurrencyLabels(String currency) {
        String label = IQD_CURRENCY;
        if (additionalDiscountCurrencyLabel != null) {
            additionalDiscountCurrencyLabel.setText(label);
        }
        if (paidAmountCurrencyLabel != null) {
            paidAmountCurrencyLabel.setText(label);
        }
    }

    private void setupPaymentToggleGroup() {
        if (paymentGroup == null) {
            paymentGroup = new ToggleGroup();
        }

        cashRadio.setToggleGroup(paymentGroup);
        creditRadio.setToggleGroup(paymentGroup);

        if (paymentGroup.getSelectedToggle() == null) {
            cashRadio.setSelected(true);
        }

        if (paidAmountField != null) {
            paidAmountField.setDisable(true);
        }

        paymentGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }

            if (SessionManager.getInstance().isSeller() && creditRadio.isSelected()) {
                cashRadio.setSelected(true);
                return;
            }

            syncPaidAmountWithPayment(calculateFinalTotal());
            handlePaidAmountChange();
        });
    }

    private void applyRoleRestrictions() {
        boolean seller = SessionManager.getInstance().isSeller();
        boolean allowQuickSale = isQuickSaleAllowed();

        if (currencyComboBox != null) {
            currencyComboBox.setValue(IQD_CURRENCY);
            currencyComboBox.setDisable(true);
        }

        if (creditRadio != null) {
            creditRadio.setVisible(!seller);
            creditRadio.setManaged(!seller);
            creditRadio.setDisable(seller);
        }
        if (cashRadio != null && seller) {
            cashRadio.setSelected(true);
            cashRadio.setDisable(true);
        }

        if (additionalDiscountBox != null) {
            additionalDiscountBox.setVisible(!seller);
            additionalDiscountBox.setManaged(!seller);
        }
        if (paymentControlsBox != null) {
            paymentControlsBox.setVisible(!seller);
            paymentControlsBox.setManaged(!seller);
        }
        if (paymentControlsSeparator != null) {
            paymentControlsSeparator.setVisible(!seller);
            paymentControlsSeparator.setManaged(!seller);
        }
        if (seller && additionalDiscountField != null) {
            additionalDiscountField.setText("0");
        }

        if (paidAmountBox != null) {
            paidAmountBox.setVisible(false);
            paidAmountBox.setManaged(false);
        }
        if (paidAmountField != null) {
            paidAmountField.setDisable(true);
            paidAmountField.setVisible(false);
            paidAmountField.setManaged(false);
        }
        if (quickSaleCheckbox != null) {
            quickSaleCheckbox.setVisible(allowQuickSale);
            quickSaleCheckbox.setManaged(allowQuickSale);
            if (!allowQuickSale) {
                quickSaleCheckbox.setSelected(false);
            }
        }
        updateQuickSaleState();
    }

    private void setupCategoryFilter(List<Product> products) {
        if (categoryFilterComboBox == null) {
            return;
        }

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
            if (!productComboBox.isShowing()) {
                productComboBox.show();
            }
        });
    }

    private void applyProductFilters() {
        String selectedCategory = categoryFilterComboBox != null ? categoryFilterComboBox.getValue() : null;
        boolean allCategories = selectedCategory == null || selectedCategory.equals("كل الفئات");
        String query = productSearchQuery == null ? "" : productSearchQuery.trim();

        filteredProducts.setPredicate(p -> {
            if (!allCategories) {
                String cat = p.getCategory();
                if (cat == null || !cat.equals(selectedCategory)) {
                    return false;
                }
            }

            if (query.isEmpty()) {
                return true;
            }

            String name = p.getName() != null ? p.getName().toLowerCase() : "";
            String code = p.getProductCode() != null ? p.getProductCode().toLowerCase() : "";
            String barcode = p.getBarcode() != null ? p.getBarcode().toLowerCase() : "";
            boolean matchesUnit = unitsByProduct.getOrDefault(p.getId(), List.of()).stream()
                    .anyMatch(unit -> {
                        String unitName = unit.getUnitName() != null ? unit.getUnitName().toLowerCase() : "";
                        String unitBarcode = unit.getBarcode() != null ? unit.getBarcode().toLowerCase() : "";
                        return unitName.contains(query) || unitBarcode.contains(query);
                    });
            return name.contains(query) || code.contains(query) || barcode.contains(query) || matchesUnit;
        });
    }

    private void setupCustomerComboBox() {
        List<Customer> customers = customerRepository.findAll();
        filteredCustomers = new FilteredList<>(FXCollections.observableArrayList(customers), c -> true);
        customerComboBox.setItems(filteredCustomers);
        customerComboBox.setEditable(true);

        customerComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Customer customer) {
                return customer != null ? customer.getName() + " (" + customer.getCustomerCode() + ")" : "";
            }

            @Override
            public Customer fromString(String s) {
                if (s == null || s.isEmpty())
                    return null;
                return filteredCustomers.getSource().stream()
                        .filter(c -> (c.getName() + " (" + c.getCustomerCode() + ")").equals(s))
                        .findFirst()
                        .orElse(null);
            }
        });

        // Enable search in customer ComboBox
        if (customerComboBox.getEditor() != null) {
            customerComboBox.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                if (customerComboBox.getValue() != null) {
                    String rendered = customerComboBox.getConverter().toString(customerComboBox.getValue());
                    if (rendered.equals(newText)) {
                        return;
                    }
                }

                // Handle case where selection updates text but valueProperty hasn't fired yet
                if (newText != null) {
                    boolean isSelection = filteredCustomers.getSource().stream()
                            .anyMatch(c -> {
                                String s = customerComboBox.getConverter().toString(c);
                                return s != null && s.equals(newText);
                            });
                    if (isSelection) {
                        return;
                    }
                }

                customerSearchQuery = newText == null ? "" : newText.trim().toLowerCase();
                filteredCustomers.setPredicate(c -> {
                    if (customerSearchQuery.isEmpty())
                        return true;

                    String fullString = (c.getName() + " (" + c.getCustomerCode() + ")").toLowerCase();
                    String name = c.getName() != null ? c.getName().toLowerCase() : "";
                    String code = c.getCustomerCode() != null ? c.getCustomerCode().toLowerCase() : "";
                    String phone = c.getPhoneNumber() != null ? c.getPhoneNumber().toLowerCase() : "";

                    return fullString.contains(customerSearchQuery) ||
                            name.contains(customerSearchQuery) ||
                            code.contains(customerSearchQuery) ||
                            phone.contains(customerSearchQuery);
                });

                if (!customerComboBox.isShowing()) {
                    customerComboBox.show();
                }
            });
        }

        customerComboBox.valueProperty().addListener((obs, oldCustomer, newCustomer) -> {
            updateProjectLocations(newCustomer);
        });

        updateProjectLocations(customerComboBox.getValue());
    }

    private void selectDefaultSaleCustomer() {
        Customer defaultCustomer = getOrCreateDefaultSaleCustomer();
        if (defaultCustomer == null || customerComboBox == null) {
            return;
        }

        customerComboBox.setValue(defaultCustomer);
        updateProjectLocations(defaultCustomer);
    }

    private Customer getOrCreateDefaultSaleCustomer() {
        return customerRepository.findByCustomerCode(DEFAULT_SALE_CUSTOMER_CODE)
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setCustomerCode(DEFAULT_SALE_CUSTOMER_CODE);
                    customer.setName(DEFAULT_SALE_CUSTOMER_NAME);
                    customer.setProjectLocation("");
                    return customerRepository.save(customer);
                });
    }

    private void updateProjectLocations(Customer customer) {
        if (projectLocationComboBox == null) {
            return;
        }

        projectLocationComboBox.getItems().clear();
        projectLocationComboBox.setValue(null);

        if (customer == null) {
            projectLocationComboBox.setDisable(true);
            return;
        }

        // Enable ComboBox when customer is selected
        projectLocationComboBox.setDisable(false);

        String locationsText = customer.getProjectLocation();
        if (locationsText == null || locationsText.trim().isEmpty()) {
            // No locations yet, but keep enabled so user can add new ones
            return;
        }

        List<String> locations = locationsText.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        projectLocationComboBox.setItems(FXCollections.observableArrayList(locations));

        if (locations.size() == 1) {
            projectLocationComboBox.setValue(locations.get(0));
        }
    }

    private void setupProductComboBox() {
        // Show all active products including out of stock ones
        List<Product> products = productRepository.findAll().stream()
                .filter(Product::getIsActive)
                .toList();
        unitsByProduct.clear();
        for (Product product : products) {
            unitsByProduct.put(product.getId(), productUnitService.getUnitsForProductOrDefault(product));
        }

        filteredProducts = new FilteredList<>(FXCollections.observableArrayList(products), p -> true);
        productComboBox.setItems(filteredProducts);
        productComboBox.setEditable(true);
        setupQuickSaleButtons();

        setupCategoryFilter(products);

        productComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product product) {
                return product != null ? product.getName() : "";
            }

            @Override
            public Product fromString(String s) {
                return null;
            }
        });

        if (productComboBox.getEditor() != null) {
            productComboBox.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                if (productComboBox.getValue() != null) {
                    String rendered = productComboBox.getConverter().toString(productComboBox.getValue());
                    if (rendered.equals(newText)) {
                        return;
                    }
                }

                productSearchQuery = newText == null ? "" : newText.trim().toLowerCase();
                applyProductFilters();

                if (!productComboBox.isShowing()) {
                    productComboBox.show();
                }
            });
            productComboBox.getEditor().setOnAction(e -> handleProductBarcodeLookup(productComboBox.getEditor().getText()));
        }

        productComboBox.setOnAction(e -> {
            Product selected = productComboBox.getValue();
            if (selected != null) {
                handleProductSelection(selected);
            }
        });
    }

    private void setupQuickSaleButtons() {
        if (quickSaleButtonsPane == null) {
            return;
        }

        List<Product> quickSaleProducts = productRepository.findAll().stream()
                .filter(product -> Boolean.TRUE.equals(product.getIsActive()))
                .filter(product -> Boolean.TRUE.equals(product.getIsQuickSale()))
                .sorted((left, right) -> {
                    String leftName = left != null && left.getName() != null ? left.getName() : "";
                    String rightName = right != null && right.getName() != null ? right.getName() : "";
                    return leftName.compareToIgnoreCase(rightName);
                })
                .toList();

        quickSaleButtonsPane.getChildren().clear();
        quickSaleButtonsPane.setVisible(!quickSaleProducts.isEmpty());
        quickSaleButtonsPane.setManaged(!quickSaleProducts.isEmpty());

        for (Product product : quickSaleProducts) {
            Button quickSaleButton = new Button(product.getName());
            quickSaleButton.setMnemonicParsing(false);
            quickSaleButton.setWrapText(true);
            quickSaleButton.setFocusTraversable(false);
            quickSaleButton.setPrefWidth(150);
            quickSaleButton.setPrefHeight(42);
            quickSaleButton.setStyle(
                    "-fx-background-color: -fx-bg-surface; " +
                    "-fx-text-fill: -fx-form-label; " +
                    "-fx-border-color: -fx-border-input; " +
                    "-fx-border-radius: 10; " +
                    "-fx-background-radius: 10; " +
                    "-fx-font-weight: bold; " +
                    "-fx-padding: 8 12;");
            quickSaleButton.setOnAction(event -> handleQuickSaleProduct(product));
            quickSaleButtonsPane.getChildren().add(quickSaleButton);
        }
    }

    private void handleQuickSaleProduct(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }

        Product freshProduct = productRepository.findById(product.getId()).orElse(product);
        ProductUnit saleUnit = resolveQuickSaleUnit(freshProduct);
        addProductToSale(freshProduct, 1.0, saleUnit);
    }

    private ProductUnit resolveQuickSaleUnit(Product product) {
        if (product == null || product.getId() == null) {
            return null;
        }

        List<ProductUnit> units = unitsByProduct.computeIfAbsent(product.getId(),
                id -> productUnitService.getUnitsForProductOrDefault(product));

        return units.stream()
                .filter(unit -> Boolean.TRUE.equals(unit.getIsActive()))
                .filter(unit -> Boolean.TRUE.equals(unit.getIsDefault()))
                .findFirst()
                .orElseGet(() -> units.stream()
                        .filter(unit -> Boolean.TRUE.equals(unit.getIsActive()))
                        .findFirst()
                        .orElse(null));
    }

    private void handleProductBarcodeLookup(String barcodeText) {
        if (barcodeText == null || barcodeText.trim().isEmpty()) {
            return;
        }
        productUnitService.findProductOrUnitByBarcode(barcodeText.trim()).ifPresent(result -> {
            pendingBarcodeUnit = result.getProductUnit();
            Product product = result.getProduct();
            selectedProduct = product;
            productComboBox.setValue(product);
            handleProductSelection(product);
            productComboBox.hide();
        });
    }

    private void handleProductSelection(Product product) {
        selectedProduct = product;
        List<ProductUnit> units = unitsByProduct.computeIfAbsent(product.getId(),
                id -> productUnitService.getUnitsForProductOrDefault(product));
        if (unitComboBox != null) {
            unitComboBox.setItems(FXCollections.observableArrayList(units));
            ProductUnit barcodeUnit = pendingBarcodeUnit != null && pendingBarcodeUnit.getId() != null
                    ? units.stream()
                            .filter(unit -> pendingBarcodeUnit.getId().equals(unit.getId()))
                            .findFirst()
                            .orElse(pendingBarcodeUnit)
                    : pendingBarcodeUnit;
            ProductUnit unitToSelect = barcodeUnit != null
                    && pendingBarcodeUnit.getProduct() != null
                    && pendingBarcodeUnit.getProduct().getId().equals(product.getId())
                            ? barcodeUnit
                            : units.stream().filter(unit -> Boolean.TRUE.equals(unit.getIsDefault())).findFirst()
                                    .orElse(units.isEmpty() ? null : units.get(0));
            pendingBarcodeUnit = null;
            unitComboBox.setValue(unitToSelect);
            selectedUnit = unitToSelect;
        } else {
            selectedUnit = units.isEmpty() ? null : units.get(0);
        }
        updateSelectedProductStockLabel();
        updateSelectedProductPriceLabel();
        updateProductPreviewPanel();
    }

    private void updateProductPreviewPanel() {
        Product product = selectedProduct;
        if (product == null) {
            clearProductPreviewPanel();
            return;
        }

        ProductUnit unit = getSelectedSaleUnit();
        double factor = unit != null ? unit.getEffectiveConversionFactor() : 1.0;
        String baseUnit = productUnitService.resolveBaseUnit(product);
        String saleUnit = unit != null && unit.getUnitName() != null ? unit.getUnitName() : baseUnit;
        try {
            List<ProductBatch> batches = productBatchService.getAvailableBatches(product.getId());
            double availableBase = batches.stream().mapToDouble(ProductBatch::getQuantity).sum();
            double availableSelected = factor > 0 ? availableBase / factor : availableBase;
            String nearestExpiry = batches.isEmpty() || batches.get(0).getExpiryDate() == null
                    ? "-"
                    : batches.get(0).getExpiryDate().toString();
            setPreviewText(productAvailabilitySummaryLabel,
                    "المتاح: " + numberFormatter.format(availableBase) + " " + baseUnit
                            + " / " + numberFormatter.format(availableSelected) + " " + saleUnit
                            + " | أقرب انتهاء: " + nearestExpiry);
        } catch (Exception e) {
            logger.warn("Could not load batch preview for product {}", product.getId(), e);
            setPreviewText(productAvailabilitySummaryLabel, "المتاح: - | أقرب انتهاء: -");
        }
        if (medicineDetailsButton != null) {
            medicineDetailsButton.setDisable(false);
        }
    }

    private void clearProductPreviewPanel() {
        setPreviewText(productAvailabilitySummaryLabel, "المتاح: - | أقرب انتهاء: -");
        if (medicineDetailsButton != null) {
            medicineDetailsButton.setDisable(true);
        }
    }

    @FXML
    private void handleOpenProductBatchDetails() {
        Product product = selectedProduct != null ? selectedProduct : productComboBox.getValue();
        if (product == null) {
            showInfo("تفاصيل الدواء والدفعات", "اختر منتجاً أولاً لعرض التفاصيل");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/views/ProductBatchAvailabilityDialog.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent content = loader.load();

            ProductBatchAvailabilityDialogController controller = loader.getController();
            ProductUnit saleUnit = getSelectedSaleUnit();
            Double salePrice = getSelectedPrice(product, saleUnit, IQD_CURRENCY);
            controller.setProduct(product, saleUnit, salePrice, IQD_CURRENCY);

            Stage stage = new Stage();
            stage.setTitle("تفاصيل الدواء والدفعات");
            stage.initModality(Modality.WINDOW_MODAL);
            if (root != null && root.getScene() != null) {
                stage.initOwner(root.getScene().getWindow());
            } else if (dialogStage != null) {
                stage.initOwner(dialogStage);
            }
            Scene scene = new Scene(content);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.showAndWait();
        } catch (Exception e) {
            logger.error("Failed to open product batch availability dialog", e);
            showError("خطأ", "تعذر فتح تفاصيل الدواء والدفعات: " + e.getMessage());
        }
    }

    private void setPreviewText(Label label, String value) {
        if (label != null) {
            label.setText(safeText(value));
        }
    }

    private String safeText(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private void updateSelectedProductStockLabel() {
        if (selectedProduct == null || stockLabel == null) {
            return;
        }

        ProductUnit unit = getSelectedSaleUnit();
        String baseUnit = productUnitService.resolveBaseUnit(selectedProduct);
        String saleUnit = unit != null && unit.getUnitName() != null ? unit.getUnitName() : baseUnit;
        double factor = unit != null ? unit.getEffectiveConversionFactor() : 1.0;
        double stock = selectedProduct.getQuantityInStock() != null ? selectedProduct.getQuantityInStock() : 0.0;
        double availableByUnit = factor > 0 ? stock / factor : stock;

        if (stock <= 0) {
            stockLabel.setText("المخزون المتاح: 0 " + baseUnit + " (نفذ المخزون)");
            stockLabel.setStyle("-fx-text-fill: -fx-danger-text; -fx-font-weight: bold;");
        } else {
            stockLabel.setText("المخزون المتاح: " + numberFormatter.format(stock) + " " + baseUnit
                    + " / " + numberFormatter.format(availableByUnit) + " " + saleUnit);
            stockLabel.setStyle("-fx-text-fill: -fx-text-hint;");
        }
    }

    private void updateSelectedProductPriceLabel() {
        if (priceLabel == null) {
            return;
        }

        if (selectedProduct == null) {
            priceLabel.setText("السعر: -");
            return;
        }

        ProductUnit saleUnit = getSelectedSaleUnit();
        String saleCurrency = resolveProductCurrency(selectedProduct, saleUnit);
        Double price = getSelectedPrice(saleCurrency);
        if (price == null || price <= 0) {
            priceLabel.setText("السعر: غير محدد");
            return;
        }

        priceLabel.setText("السعر: " + formatCurrencyAmount(price, saleCurrency));
    }

    private Double getSelectedPrice(String currency) {
        if (selectedProduct == null)
            return null;
        return getSelectedPrice(selectedProduct, getSelectedSaleUnit(), currency);
    }

    @SuppressWarnings("unused")
    private Double getSelectedPrice(Product product, String currency) {
        return getSelectedPrice(product, product == selectedProduct ? getSelectedSaleUnit() : null, currency);
    }

    private Double getSelectedPrice(Product product, ProductUnit saleUnit, String currency) {
        String priceType = priceTypeComboBox != null ? priceTypeComboBox.getValue() : "مفرد";
        return getSelectedPrice(product, saleUnit, currency, priceType);
    }

    private Double getSelectedPrice(Product product, ProductUnit saleUnit, String currency, String selectedPriceType) {
        if (product == null)
            return null;
        String priceType = priceTypeComboBox != null ? priceTypeComboBox.getValue() : "مفرد";
        double conversionFactor = saleUnit != null ? saleUnit.getEffectiveConversionFactor() : 1.0;

        if (isRetailPriceType(priceType) && saleUnit != null) {
            Double unitPrice = "دولار".equals(currency) ? saleUnit.getSalePriceUsd() : saleUnit.getSalePrice();
            if (hasPositiveValue(unitPrice)) {
                return unitPrice;
            }
        }

        if ("دولار".equals(currency)) {
            if ("جملة".equals(priceType) && hasPositiveValue(product.getWholesalePriceUsd())) {
                return product.getWholesalePriceUsd() * conversionFactor;
            } else if ("خاص".equals(priceType) && hasPositiveValue(product.getSpecialPriceUsd())) {
                return product.getSpecialPriceUsd() * conversionFactor;
            }
            return hasPositiveValue(product.getUnitPriceUsd()) ? product.getUnitPriceUsd() * conversionFactor : null;
        }

        if ("جملة".equals(priceType) && hasPositiveValue(product.getWholesalePrice())) {
            return product.getWholesalePrice() * conversionFactor;
        } else if ("خاص".equals(priceType) && hasPositiveValue(product.getSpecialPrice())) {
            return product.getSpecialPrice() * conversionFactor;
        }
        return hasPositiveValue(product.getUnitPrice()) ? product.getUnitPrice() * conversionFactor : null;
    }

    private ProductUnit getSelectedSaleUnit() {
        if (unitComboBox != null && unitComboBox.getValue() != null) {
            return unitComboBox.getValue();
        }
        return selectedUnit;
    }

    private ProductUnit findUnitForRow(SaleItemRow row, String unitName) {
        if (row == null || row.getProductId() == null || unitName == null) {
            return null;
        }
        List<ProductUnit> units = unitsByProduct.getOrDefault(row.getProductId(), List.of());
        return units.stream()
                .filter(unit -> unitName.equals(unit.getUnitName()))
                .findFirst()
                .orElse(null);
    }

    private void applyUnitChangeToRow(SaleItemRow row, ProductUnit newUnit) {
        if (row == null || row.getProductId() == null || newUnit == null) {
            return;
        }

        Product product = productRepository.findById(row.getProductId()).orElse(null);
        if (product == null) {
            showError("خطأ", "تعذر العثور على المنتج لتغيير الوحدة");
            return;
        }

        Double unitPriceIqd = getSelectedPrice(product, newUnit, IQD_CURRENCY);
        if (unitPriceIqd == null || unitPriceIqd <= 0) {
            showError("خطأ", "لا يوجد سعر معروف للوحدة المحددة");
            return;
        }

        row.setSoldUnit(newUnit.getUnitName());
        row.setConversionFactor(newUnit.getEffectiveConversionFactor());
        row.setUnitPricing(unitPriceIqd, getSelectedPrice(product, newUnit, "دولار"));
        populateUnitPriceDisplays(row, product);
        row.recalculate();
        refreshCartBatchPreviews();
        if (itemsTable != null) {
            itemsTable.refresh();
        }
        updateTotals();
    }

    private boolean isRetailPriceType(String priceType) {
        return priceType == null || "مفرد".equals(priceType);
    }

    @SuppressWarnings("unused")
    private String resolveProductCurrency(Product product) {
        return resolveProductCurrency(product, product == selectedProduct ? getSelectedSaleUnit() : null);
    }

    private String resolveProductCurrency(Product product, ProductUnit saleUnit) {
        return IQD_CURRENCY;
    }

    private boolean hasPositiveValue(Double value) {
        return value != null && value > 0;
    }

    private String formatCurrencyAmount(double amount, String currency) {
        return numberFormatter.format(amount) + " " + currencySymbol(currency);
    }

    private String currencySymbol(String currency) {
        return "د.ع";
    }

    private SaleItemRow getCellRow(TableCell<SaleItemRow, ?> cell) {
        if (cell == null || cell.getTableView() == null || cell.getIndex() < 0
                || cell.getIndex() >= cell.getTableView().getItems().size()) {
            return null;
        }
        return cell.getTableView().getItems().get(cell.getIndex());
    }

    private void setupItemsTable() {
        productNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        productNameColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                SaleItemRow row = getCellRow(this);
                String currency = row != null ? row.getCurrency() : "دينار";
                boolean stockInsufficient = row != null && isStockInsufficient(row);
                setText(item + "  " + currencySymbol(currency) + (stockInsufficient ? "  (غير متوفر)" : ""));
                if (stockInsufficient) {
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #ff4d4d;");
                } else {
                    setStyle("دولار".equals(currency)
                            ? "-fx-font-weight: bold; -fx-text-fill: #64b5ff;"
                            : "-fx-font-weight: bold; -fx-text-fill: #45d483;");
                }
            }
        });
        quantityColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getQuantity()).asObject());
        if (soldUnitColumn != null) {
            soldUnitColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSoldUnit()));
            soldUnitColumn.setCellFactory(col -> new TableCell<>() {
                private final ComboBox<ProductUnit> comboBox = new ComboBox<>();
                private boolean updating = false;

                {
                    comboBox.setMaxWidth(Double.MAX_VALUE);
                    comboBox.setConverter(new StringConverter<>() {
                        @Override
                        public String toString(ProductUnit unit) {
                            return unit != null ? unit.getUnitName() : "";
                        }

                        @Override
                        public ProductUnit fromString(String string) {
                            return null;
                        }
                    });
                    comboBox.setOnAction(e -> {
                        if (updating) {
                            return;
                        }
                        SaleItemRow row = getCellRow(this);
                        ProductUnit selected = comboBox.getValue();
                        if (row != null && selected != null) {
                            applyUnitChangeToRow(row, selected);
                        }
                    });
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                        return;
                    }
                    SaleItemRow row = getCellRow(this);
                    if (row == null) {
                        setGraphic(null);
                        return;
                    }
                    List<ProductUnit> units = unitsByProduct.getOrDefault(row.getProductId(), List.of());
                    updating = true;
                    comboBox.setItems(FXCollections.observableArrayList(units));
                    comboBox.setValue(findUnitForRow(row, row.getSoldUnit()));
                    updating = false;
                    setGraphic(comboBox);
                }
            });
        }
        if (conversionFactorColumn != null) {
            conversionFactorColumn
                    .setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getConversionFactor()).asObject());
        }
        if (baseQuantityColumn != null) {
            baseQuantityColumn
                    .setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getBaseQuantity()).asObject());
        }
        if (batchPreviewColumn != null) {
            batchPreviewColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBatchPreview()));
        }
        unitPriceColumn
                .setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getUnitPrice()).asObject());
        if (stripPriceColumn != null) {
            stripPriceColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStripPriceDisplay()));
            stripPriceColumn.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item);
                        setStyle("-fx-text-fill: #64b5ff; -fx-font-size: 12px;");
                    }
                }
            });
        }
        if (boxPriceColumn != null) {
            boxPriceColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBoxPriceDisplay()));
            boxPriceColumn.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item);
                        setStyle("-fx-text-fill: #a78bfa; -fx-font-size: 12px;");
                    }
                }
            });
        }
        discountColumn
                .setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getDiscountAmount()).asObject());
        totalColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getTotalPrice()).asObject());

        // Enable double-click editing for quantity column
        quantityColumn.setCellFactory(col -> new TableCell<>() {
            private TextField textField;

            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(String.valueOf(item));
                    setGraphic(null);
                }
            }

            @Override
            public void startEdit() {
                super.startEdit();
                if (textField == null) {
                    textField = new TextField();
                    textField.setOnAction(e -> commitEdit(Double.parseDouble(textField.getText())));
                    textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                        if (!isNowFocused) {
                            try {
                                commitEdit(Double.parseDouble(textField.getText()));
                            } catch (NumberFormatException ex) {
                                cancelEdit();
                            }
                        }
                    });
                }
                textField.setText(String.valueOf(getItem()));
                setText(null);
                setGraphic(textField);
                textField.selectAll();
                textField.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(String.valueOf(getItem()));
                setGraphic(null);
            }

            @Override
            public void commitEdit(Double newValue) {
                super.commitEdit(newValue);
                SaleItemRow row = getTableView().getItems().get(getIndex());
                if (newValue <= 0) {
                    showError("خطأ", "الكمية يجب أن تكون أكبر من صفر");
                    cancelEdit();
                    return;
                }
                row.setQuantity(newValue);
                row.recalculate();
                refreshCartBatchPreviews();
                itemsTable.refresh();
                updateTotals();
            }
        });

        // Enable double-click editing for unit price column
        unitPriceColumn.setCellFactory(col -> new TableCell<>() {
            private TextField textField;

            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    SaleItemRow row = getCellRow(this);
                    setText(formatCurrencyAmount(item, row != null ? row.getCurrency() : "دينار"));
                    setGraphic(null);
                }
            }

            @Override
            public void startEdit() {
                super.startEdit();
                if (textField == null) {
                    textField = new TextField();
                    textField.setOnAction(e -> {
                        try {
                            commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                        } catch (NumberFormatException ex) {
                            cancelEdit();
                        }
                    });
                    textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                        if (!isNowFocused) {
                            try {
                                commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                            } catch (NumberFormatException ex) {
                                cancelEdit();
                            }
                        }
                    });
                }
                textField.setText(String.valueOf(getItem()));
                setText(null);
                setGraphic(textField);
                textField.selectAll();
                textField.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                SaleItemRow row = getCellRow(this);
                setText(formatCurrencyAmount(getItem() != null ? getItem() : 0.0,
                        row != null ? row.getCurrency() : "دينار"));
                setGraphic(null);
            }

            @Override
            public void commitEdit(Double newValue) {
                super.commitEdit(newValue);
                if (newValue < 0) {
                    showError("خطأ", "السعر لا يمكن أن يكون سالب");
                    cancelEdit();
                    return;
                }
                SaleItemRow row = getTableView().getItems().get(getIndex());
                row.setUnitPrice(newValue);
                row.recalculate();
                itemsTable.refresh();
                updateTotals();
            }
        });

        // Enable editing for Discount Column
        discountColumn.setCellFactory(col -> new TableCell<>() {
            private TextField textField;

            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                SaleItemRow row = getCellRow(this);
                setText(empty || item == null ? null : formatCurrencyAmount(item, row != null ? row.getCurrency() : "دينار"));
                setGraphic(null);
            }

            @Override
            public void startEdit() {
                super.startEdit();
                if (textField == null) {
                    textField = new TextField();
                    textField.setOnAction(e -> {
                        try {
                            commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                        } catch (NumberFormatException ex) {
                            cancelEdit();
                        }
                    });
                    textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                        if (!isNowFocused) {
                            try {
                                commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                            } catch (NumberFormatException ex) {
                                cancelEdit();
                            }
                        }
                    });
                }
                SaleItemRow row = getTableView().getItems().get(getIndex());
                textField.setText(String.valueOf(row.getDiscountAmount()));
                setText(null);
                setGraphic(textField);
                textField.selectAll();
                textField.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setGraphic(null);
                SaleItemRow row = getCellRow(this);
                setText(formatCurrencyAmount(getItem() != null ? getItem() : 0.0,
                        row != null ? row.getCurrency() : "دينار"));
            }

            @Override
            public void commitEdit(Double newDiscountAmount) {
                super.commitEdit(newDiscountAmount);
                // Removed negative check to allow negative discount (increasing price)
                SaleItemRow row = getTableView().getItems().get(getIndex());
                double gross = row.getUnitPrice() * row.getQuantity();
                if (gross == 0)
                    return;

                double newPercent = (newDiscountAmount / gross) * 100.0;
                row.setDiscountPercent(newPercent);
                row.recalculate();
                itemsTable.refresh();
                updateTotals();
            }
        });

        // Enable editing for Total Column
        totalColumn.setCellFactory(col -> new TableCell<>() {
            private TextField textField;

            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                SaleItemRow row = getCellRow(this);
                setText(empty || item == null ? null : formatCurrencyAmount(item, row != null ? row.getCurrency() : "دينار"));
                setGraphic(null);
            }

            @Override
            public void startEdit() {
                super.startEdit();
                if (textField == null) {
                    textField = new TextField();
                    textField.setOnAction(e -> {
                        try {
                            commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                        } catch (NumberFormatException ex) {
                            cancelEdit();
                        }
                    });
                    textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                        if (!isNowFocused) {
                            try {
                                commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                            } catch (NumberFormatException ex) {
                                cancelEdit();
                            }
                        }
                    });
                }
                textField.setText(String.valueOf(getItem()));
                setText(null);
                setGraphic(textField);
                textField.selectAll();
                textField.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setGraphic(null);
                SaleItemRow row = getCellRow(this);
                setText(formatCurrencyAmount(getItem() != null ? getItem() : 0.0,
                        row != null ? row.getCurrency() : "دينار"));
            }

            @Override
            public void commitEdit(Double newTotal) {
                super.commitEdit(newTotal);
                if (newTotal < 0) {
                    showError("خطأ", "الإجمالي لا يمكن أن يكون سالب");
                    cancelEdit();
                    return;
                }
                SaleItemRow row = getTableView().getItems().get(getIndex());

                // Update Unit Price based on new Total
                // Total = (UnitPrice * Quantity) - DiscountAmount
                // We want to keep DiscountAmount same (or 0?) and update UnitPrice.
                // Or simply: UnitPrice = (Total + DiscountAmount) / Quantity

                double currentDiscount = row.getDiscountAmount();
                double newGross = newTotal + currentDiscount;

                if (row.getQuantity() != 0) {
                    double newUnitPrice = newGross / row.getQuantity();
                    row.setUnitPrice(newUnitPrice);
                    // Discount percent might change relative to new unit price, let's recalculate
                    // it or keep amount fixed?
                    // row.recalculate() calculates discount amount from percent.
                    // If we want to keep discount AMOUNT fixed:
                    // newDiscountPercent = (currentDiscount / newGross) * 100
                    if (newGross != 0) {
                        row.setDiscountPercent((currentDiscount / newGross) * 100.0);
                    } else {
                        row.setDiscountPercent(0);
                    }
                }

                row.recalculate();
                itemsTable.refresh();
                updateTotals();
            }
        });

        // Edit button column
        editColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("✏");

            {
                editBtn.getStyleClass().add("action-btn-edit");
                editBtn.setOnAction(e -> {
                    SaleItemRow row = getTableView().getItems().get(getIndex());
                    openProductEditForm(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editBtn);
            }
        });

        actionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("🗑");

            {
                deleteBtn.getStyleClass().add("action-btn-delete");
                deleteBtn.setOnAction(e -> {
                    SaleItemRow item = getTableView().getItems().get(getIndex());
                    saleItems.remove(item);
                    refreshCartBatchPreviews();
                    updateTotals();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });

        itemsTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(SaleItemRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else if (isStockInsufficient(item)) {
                    setStyle("-fx-background-color: rgba(255, 64, 64, 0.24);");
                } else if ("دولار".equals(item.getCurrency())) {
                    setStyle("-fx-background-color: rgba(42, 140, 255, 0.16);");
                } else {
                    setStyle("-fx-background-color: rgba(45, 212, 131, 0.12);");
                }
            }
        });

        itemsTable.setItems(saleItems);
        itemsTable.setEditable(true);
    }

    private void openProductEditForm(SaleItemRow row) {
        try {
            Product product = productRepository.findById(row.getProductId()).orElse(null);
            if (product == null) {
                showError("خطأ", "لم يتم العثور على المنتج");
                return;
            }

            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/views/ProductForm.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("تعديل منتج");
            stage.initModality(Modality.WINDOW_MODAL);
            if (dialogStage != null) {
                stage.initOwner(dialogStage);
            }
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);

            ProductController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setProduct(product);

            stage.showAndWait();

            // Refresh table to reflect any changes
            itemsTable.refresh();
            updateTotals();

            // Refresh selected product info if needed
            if (selectedProduct != null && selectedProduct.getId().equals(product.getId())) {
                Product updated = productRepository.findById(product.getId()).orElse(null);
                if (updated != null) {
                    selectedProduct = updated;
                    // Update labels
                    String unit = updated.getUnitOfMeasure();
                    if (unit == null || unit.trim().isEmpty())
                        unit = "وحدة";
                    double stock = updated.getQuantityInStock();
                    if (stock <= 0) {
                        stockLabel.setText("المخزون المتاح: " + stock + " " + unit + " (نفذ المخزون)");
                        stockLabel.setStyle("-fx-text-fill: -fx-danger-text; -fx-font-weight: bold;");
                    } else {
                        stockLabel.setText("المخزون المتاح: " + stock + " " + unit);
                        stockLabel.setStyle("-fx-text-fill: -fx-text-hint;");
                    }
                    priceLabel.setText("السعر: " + numberFormatter.format(updated.getUnitPrice()) + " دينار");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to open product form", e);
            showError("خطأ", "فشل في فتح نافذة تعديل المنتج");
        }
    }

    @FXML
    private void handleAddProjectLocation() {
        if (newProjectLocationField == null)
            return;

        String newLocation = newProjectLocationField.getText().trim();
        if (newLocation.isEmpty()) {
            showError("خطأ", "الرجاء إدخال اسم موقع المشروع");
            return;
        }

        Customer customer = customerComboBox.getValue();
        if (customer == null) {
            showError("خطأ", "الرجاء اختيار العميل أولاً");
            return;
        }

        // Add new location to customer's project locations
        String existingLocations = customer.getProjectLocation();
        String updatedLocations;
        if (existingLocations == null || existingLocations.trim().isEmpty()) {
            updatedLocations = newLocation;
        } else {
            updatedLocations = existingLocations + "\n" + newLocation;
        }
        customer.setProjectLocation(updatedLocations);

        // Save customer with new location
        try {
            customerRepository.save(customer);
            updateProjectLocations(customer);
            projectLocationComboBox.setValue(newLocation);
            newProjectLocationField.clear();
            showSuccess("تم", "تمت إضافة موقع المشروع بنجاح");
        } catch (Exception e) {
            logger.error("Failed to save project location", e);
            showError("خطأ", "فشل في حفظ موقع المشروع");
        }
    }

    private void setupDefaults() {
        cashRadio.setSelected(true);
        quantityField.setText("1");
        additionalDiscountField.setText("0");
        if (paidAmountField != null) {
            paidAmountField.setText("0");
            paidAmountField.setDisable(true);
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    public void setSelectedCustomer(Customer customer) {
        if (customer != null) {
            customerComboBox.setValue(customer);
            updateProjectLocations(customer);
        }
    }

    @FXML
    private void handleNewCustomer() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/views/CustomerForm.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("إضافة عميل جديد");
            stage.initModality(Modality.WINDOW_MODAL);
            if (dialogStage != null) {
                stage.initOwner(dialogStage);
            }
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.setMaximized(false);

            CustomerController controller = loader.getController();
            controller.setDialogStage(stage);

            stage.showAndWait();

            if (controller.isSaved()) {
                refreshCustomersAndSelectLast();
            }
        } catch (Exception e) {
            logger.error("Failed to open customer form", e);
            showError("خطأ", "فشل في فتح نافذة إضافة عميل جديد");
        }
    }

    private void refreshCustomersAndSelectLast() {
        List<Customer> customers = customerRepository.findAll();
        customerComboBox.setItems(FXCollections.observableArrayList(customers));

        Customer last = customers.stream()
                .max((a, b) -> Long.compare(a.getId() != null ? a.getId() : 0L, b.getId() != null ? b.getId() : 0L))
                .orElse(null);

        if (last != null) {
            customerComboBox.setValue(last);
            updateProjectLocations(last);
        }
    }

    @FXML
    private void handleAddItem() {
        Product product = selectedProduct != null ? selectedProduct : productComboBox.getValue();
        if (product == null) {
            showError("خطأ", "الرجاء اختيار منتج");
            return;
        }

        double quantity;
        try {
            quantity = Double.parseDouble(quantityField.getText().trim());
            if (quantity <= 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("خطأ", "الرجاء إدخال كمية صحيحة");
            return;
        }

        addProductToSale(product, quantity, getSelectedSaleUnit());
    }

    private void addProductToSale(Product product, double quantity, ProductUnit saleUnit) {
        product = productRepository.findById(product.getId()).orElse(product);
        selectedProduct = product;

        double conversionFactor = saleUnit != null ? saleUnit.getEffectiveConversionFactor() : 1.0;
        String soldUnit = saleUnit != null && saleUnit.getUnitName() != null
                ? saleUnit.getUnitName()
                : productUnitService.resolveBaseUnit(product);
        double baseQuantity = quantity * conversionFactor;
        double discountPercent = 0;
        String itemCurrency = resolveProductCurrency(product, saleUnit);
        Double itemPrice = getSelectedPrice(product, saleUnit, itemCurrency);
        if (itemPrice == null || itemPrice <= 0) {
            showError("خطأ", "لا يوجد سعر لهذا المنتج بالدينار");
            return;
        }
        String priceType = priceTypeComboBox != null ? priceTypeComboBox.getValue() : "مفرد";
        Long productId = product.getId();

        SaleItemRow existingItem = saleItems.stream()
                .filter(item -> item.getProductId().equals(productId))
                .filter(item -> item.getCurrency().equals(itemCurrency))
                .filter(item -> item.getPriceType().equals(priceType))
                .filter(item -> item.getSoldUnit().equals(soldUnit))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            double newQty = existingItem.getQuantity() + quantity;
            existingItem.setQuantity(newQty);
            existingItem.recalculate();
        } else {
            SaleItemRow newItem = new SaleItemRow(
                    product.getId(),
                    product.getName(),
                    quantity,
                    getSelectedPrice(product, saleUnit, "دينار") != null ? getSelectedPrice(product, saleUnit, "دينار") : 0.0,
                    discountPercent,
                    priceType,
                    soldUnit,
                    conversionFactor);
            newItem.setSavedUnitPriceUsd(getSelectedPrice(product, saleUnit, "دولار"));
            newItem.setBaseQuantity(baseQuantity);
            newItem.setCurrencyAndRate(IQD_CURRENCY, getExchangeRateOrDefault());
            populateUnitPriceDisplays(newItem, product);
            newItem.recalculate();
            saleItems.add(newItem);
        }

        refreshCartBatchPreviews();
        updateTotals();
        clearProductSelection();
    }

    private void clearProductSelection() {
        selectedProduct = null;
        selectedUnit = null;
        productComboBox.setValue(null);
        productComboBox.getEditor().clear();
        if (unitComboBox != null) {
            unitComboBox.getItems().clear();
            unitComboBox.setValue(null);
        }
        quantityField.setText("1");
        stockLabel.setText("المخزون المتاح: -");
        priceLabel.setText("السعر: -");
        clearProductPreviewPanel();
    }

    private void populateUnitPriceDisplays(SaleItemRow item, Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        List<ProductUnit> units = unitsByProduct.getOrDefault(product.getId(), List.of());
        for (ProductUnit unit : units) {
            if (unit.getUnitName() == null || !Boolean.TRUE.equals(unit.getIsActive())) {
                continue;
            }
            String name = unit.getUnitName().trim();
            Double price = unit.getSalePrice();
            String display = price != null && price > 0
                    ? numberFormatter.format(price) + " د.ع"
                    : "-";
            if ("شريط".equals(name)) {
                item.setStripPriceDisplay(display);
            } else if ("علبة".equals(name)) {
                item.setBoxPriceDisplay(display);
            }
        }

    }

    private void refreshCartBatchPreviews() {
        Map<Long, List<ProductBatch>> batchesByProductId = new HashMap<>();
        Map<Long, Double> remainingByBatchId = new HashMap<>();

        for (SaleItemRow row : saleItems) {
            row.setBatchPreview(buildCumulativeBatchPreviewText(row, batchesByProductId, remainingByBatchId));
        }

        if (itemsTable != null) {
            itemsTable.refresh();
        }
    }

    private String buildCumulativeBatchPreviewText(SaleItemRow row,
                                                   Map<Long, List<ProductBatch>> batchesByProductId,
                                                   Map<Long, Double> remainingByBatchId) {
        if (row == null || row.getProductId() == null) {
            return "-";
        }

        try {
            List<ProductBatch> batches = batchesByProductId.computeIfAbsent(row.getProductId(),
                    productBatchService::getAvailableBatches);
            if (batches.isEmpty()) {
                return "لا توجد دفعات صالحة";
            }

            double remainingRequired = Math.max(0.0, row.getBaseQuantity());
            List<String> parts = new ArrayList<>();
            for (ProductBatch batch : batches) {
                if (remainingRequired <= 1e-9) {
                    break;
                }
                Long batchId = batch.getId();
                double remainingInBatch = remainingByBatchId.computeIfAbsent(batchId,
                        id -> batch.getQuantity() != null ? batch.getQuantity() : 0.0);
                if (remainingInBatch <= 1e-9) {
                    continue;
                }

                double used = Math.min(remainingRequired, remainingInBatch);
                String expiry = batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : "-";
                parts.add(safeText(batch.getBatchNumber()) + " / " + expiry + " / " + numberFormatter.format(used));
                remainingByBatchId.put(batchId, remainingInBatch - used);
                remainingRequired -= used;
            }

            if (remainingRequired > 1e-9) {
                if (parts.isEmpty()) {
                    return "غير كافٍ حسب الدفعات الصالحة";
                }
                parts.add("غير كافٍ حسب الدفعات الصالحة");
            }

            if (parts.isEmpty()) {
                ProductBatch first = batches.get(0);
                String expiry = first.getExpiryDate() != null ? first.getExpiryDate().toString() : "-";
                return safeText(first.getBatchNumber()) + " / " + expiry;
            }
            return String.join("، ", parts);
        } catch (Exception e) {
            logger.warn("Could not build cumulative batch preview for product {}", row.getProductId(), e);
            return "-";
        }
    }

    private boolean isStockInsufficient(SaleItemRow row) {
        if (row == null || row.getProductId() == null) {
            return false;
        }

        Product product = productRepository.findById(row.getProductId()).orElse(null);
        double availableStock = product != null && product.getQuantityInStock() != null ? product.getQuantityInStock() : 0.0;
        return getRequiredQuantityForProduct(row.getProductId()) > availableStock + 1e-9;
    }

    private boolean validateAllStockAvailable() {
        Map<Long, Double> requiredByProduct = new LinkedHashMap<>();
        for (SaleItemRow row : saleItems) {
            requiredByProduct.merge(row.getProductId(), row.getBaseQuantity(), (a, b) -> a + b);
        }

        for (Map.Entry<Long, Double> entry : requiredByProduct.entrySet()) {
            Product product = productRepository.findById(entry.getKey()).orElse(null);
            if (product == null) {
                showError("خطأ", "لم يتم العثور على أحد المنتجات في الفاتورة");
                return false;
            }
            if (!validateStockAvailable(product, entry.getValue())) {
                return false;
            }
        }

        return true;
    }

    private double getRequiredQuantityForProduct(Long productId) {
        return saleItems.stream()
                .filter(row -> row.getProductId() != null && row.getProductId().equals(productId))
                .mapToDouble(SaleItemRow::getBaseQuantity)
                .sum();
    }

    private boolean validateStockAvailable(Product product, double requestedQuantity) {
        double availableStock = product.getQuantityInStock() != null ? product.getQuantityInStock() : 0.0;
        if (requestedQuantity > availableStock + 1e-9) {
            showError("خطأ",
                    "الكمية غير متوفرة للمنتج: " + product.getName()
                            + "\nالمتوفر: " + numberFormatter.format(availableStock)
                            + "\nالمطلوب: " + numberFormatter.format(requestedQuantity));
            return false;
        }
        return true;
    }

    @FXML
    private void handleDiscountChange() {
        updateTotals();
    }

    private double calculateFinalTotal() {
        Map<String, CurrencyTotals> totals = buildCurrencyTotals();
        if (totals.isEmpty()) {
            return 0;
        }

        String paymentCurrency = getPaymentCurrencyForTotals(totals);
        CurrencyTotals summary = totals.get(paymentCurrency);
        return summary != null ? summary.finalTotal(getAllowedAdditionalDiscount()) : 0;
    }

    private void updateTotals() {
        Map<String, CurrencyTotals> totals = buildCurrencyTotals();
        double additionalDiscount = getAllowedAdditionalDiscount();
        String paymentCurrency = getPaymentCurrencyForTotals(totals);
        updateCurrencyLabels(paymentCurrency);

        subtotalLabel.setText(formatCurrencyTotals(totals, (currency, summary) -> summary.grossAmount));
        discountLabel.setText(formatCurrencyTotals(totals,
                (currency, summary) -> summary.itemDiscount + (currency.equals(paymentCurrency) ? additionalDiscount : 0)));
        finalTotalLabel.setText(formatCurrencyTotals(totals,
                (currency, summary) -> summary.finalTotal(currency.equals(paymentCurrency) ? additionalDiscount : 0)));

        double finalTotal = 0;
        CurrencyTotals paymentSummary = totals.get(paymentCurrency);
        if (paymentSummary != null) {
            finalTotal = paymentSummary.finalTotal(additionalDiscount);
        }

        syncPaidAmountWithPayment(finalTotal);

        updateBalance(finalTotal, paymentCurrency, totals.size() > 1);
    }

    private double getAllowedAdditionalDiscount() {
        if (SessionManager.getInstance().isSeller()) {
            return 0;
        }
        return parseAmount(additionalDiscountField);
    }

    private void syncPaidAmountWithPayment(double finalTotal) {
        if (paidAmountField == null) {
            return;
        }
        double paidAmount = cashRadio != null && cashRadio.isSelected() ? finalTotal : 0.0;
        paidAmountField.setText(String.valueOf(paidAmount));
    }

    @FXML
    private void handlePaidAmountChange() {
        Map<String, CurrencyTotals> totals = buildCurrencyTotals();
        String paymentCurrency = getPaymentCurrencyForTotals(totals);
        updateBalance(calculateFinalTotal(), paymentCurrency, totals.size() > 1);
    }

    private Map<String, CurrencyTotals> buildCurrencyTotals() {
        Map<String, CurrencyTotals> totals = new LinkedHashMap<>();
        for (SaleItemRow row : saleItems) {
            String currency = row.getCurrency();
            CurrencyTotals summary = totals.computeIfAbsent(currency, c -> new CurrencyTotals());
            double gross = row.getUnitPrice() * row.getQuantity();
            summary.grossAmount += gross;
            summary.itemDiscount += row.getDiscountAmount();
            summary.netAmount += row.getTotalPrice();
        }
        return totals;
    }

    private String getPaymentCurrencyForTotals(Map<String, CurrencyTotals> totals) {
        return IQD_CURRENCY;
    }

    private double parseAmount(TextField field) {
        if (field == null || field.getText() == null) {
            return 0;
        }
        try {
            String text = field.getText().trim().replace(",", "");
            return text.isEmpty() ? 0 : Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatCurrencyTotals(Map<String, CurrencyTotals> totals, CurrencyValueProvider provider) {
        if (totals.isEmpty()) {
            return formatCurrencyAmount(0, IQD_CURRENCY);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, CurrencyTotals> entry : totals.entrySet()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(formatCurrencyAmount(provider.value(entry.getKey(), entry.getValue()), entry.getKey()));
        }
        return sb.toString();
    }

    private void updateBalance(double finalTotal, String currency, boolean splitReceipts) {
        double paidAmount = parseAmount(paidAmountField);

        double balance = paidAmount - finalTotal;
        String splitNote = splitReceipts ? "\nسيتم إنشاء وصولات منفصلة حسب العملة" : "";

        if (balance > 0) {
            balanceLabel.setText(formatCurrencyAmount(balance, currency));
            balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -fx-success-text;");
            balanceStatusLabel.setText("✅ العميل دفع زيادة - نحن مدينون له بهذا المبلغ" + splitNote);
            balanceStatusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -fx-success-text;");
        } else if (balance < 0) {
            balanceLabel.setText(formatCurrencyAmount(Math.abs(balance), currency));
            balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -fx-danger-text;");
            balanceStatusLabel.setText("⚠️ العميل مدين لنا بهذا المبلغ" + splitNote);
            balanceStatusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -fx-danger-text;");
        } else {
            balanceLabel.setText(formatCurrencyAmount(0, currency));
            balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -fx-text-main;");
            balanceStatusLabel.setText("✔️ تم الدفع بالكامل" + splitNote);
            balanceStatusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -fx-text-main;");
        }
    }

    @FXML
    private void handleSaveAndPrint(javafx.event.ActionEvent e) {
        List<Sale> sales = createSales();
        if (!sales.isEmpty()) {
            try {
                boolean quickSale = quickSaleCheckbox != null && quickSaleCheckbox.isVisible() && quickSaleCheckbox.isSelected();
                boolean printAfterSave = printCheckbox != null && printCheckbox.isSelected();

                if (!quickSale) {
                    for (Sale sale : sales) {
                        Receipt receipt = receiptService.generateReceipt(sale.getId(), "DEFAULT", "System");
                        if (!printAfterSave || receipt.getFilePath() == null) {
                            continue;
                        }

                        File pdfFile = new File(receipt.getFilePath());
                        if (pdfFile.exists()) {
                            printReceiptPdf(pdfFile);
                        }
                    }
                }
                resetSaleForm();
            } catch (Exception ex) {
                logger.error("Failed to generate receipt", ex);
                showError("خطأ", "تم حفظ البيع لكن فشل إرسال الفاتورة إلى الطابعة: " + ex.getMessage());
            }
        }
    }

    private void printReceiptPdf(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) {
            throw new IllegalArgumentException("ملف الفاتورة غير موجود");
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintable(new PDFPrintable(document, Scaling.SHRINK_TO_FIT));
            job.print();
        } catch (PrinterException e) {
            throw new RuntimeException("فشل الاتصال بالطابعة الافتراضية", e);
        } catch (Exception e) {
            throw new RuntimeException("فشل تجهيز ملف الفاتورة للطباعة", e);
        }
    }

    private List<Sale> createSales() {
        selectDefaultSaleCustomer();
        if (customerComboBox.getValue() == null) {
            showError("خطأ", "تعذر تجهيز عميل البيع الافتراضي");
            return List.of();
        }

        if (saleItems.isEmpty()) {
            showError("خطأ", "الرجاء إضافة منتج واحد على الأقل");
            return List.of();
        }

        if (!validateAllStockAvailable()) {
            return List.of();
        }

        String paymentMethod = "CASH";
        if (SessionManager.getInstance().isSeller()) {
            cashRadio.setSelected(true);
        } else if (creditRadio.isSelected()) {
            paymentMethod = "DEBT";
        }

        try {
            Map<String, List<SaleItemRow>> rowsByCurrency = groupRowsByCurrency();
            String paymentCurrency = getPaymentCurrencyForTotals(buildCurrencyTotals());
            double additionalDiscount = getAllowedAdditionalDiscount();
            String createdBy = SessionManager.getInstance().getCurrentDisplayName();
            String creator = createdBy != null ? createdBy : "System";
            List<Sale> createdSales = new ArrayList<>();

            for (Map.Entry<String, List<SaleItemRow>> entry : rowsByCurrency.entrySet()) {
                String saleCurrency = entry.getKey();
                SalesService.SaleRequest request = new SalesService.SaleRequest();
                request.setCustomerId(customerComboBox.getValue().getId());
                request.setProjectLocation("");
                request.setPaymentMethod(paymentMethod);
                request.setCurrency(IQD_CURRENCY);
                request.setNotes(notesArea != null ? notesArea.getText() : "");
                request.setCreatedBy(creator);

                double saleAdditionalDiscount = saleCurrency.equals(paymentCurrency) ? additionalDiscount : 0;
                request.setAdditionalDiscount(saleAdditionalDiscount);

                List<SalesService.SaleItemRequest> items = new ArrayList<>();
                double groupTotal = 0;
                for (SaleItemRow row : entry.getValue()) {
                    SalesService.SaleItemRequest itemRequest = new SalesService.SaleItemRequest();
                    itemRequest.setProductId(row.getProductId());
                    itemRequest.setQuantity(row.getQuantity());
                    itemRequest.setUnitPrice(row.getUnitPrice());
                    itemRequest.setDiscountPercentage(row.getDiscountPercent());
                    itemRequest.setPriceType(row.getPriceType());
                    itemRequest.setSoldUnit(row.getSoldUnit());
                    itemRequest.setConversionFactor(row.getConversionFactor());
                    itemRequest.setBaseQuantity(row.getBaseQuantity());
                    items.add(itemRequest);
                    groupTotal += row.getTotalPrice();
                }
                request.setItems(items);

                double finalTotal = groupTotal - saleAdditionalDiscount;
                if (cashRadio.isSelected()) {
                    request.setPaidAmount(finalTotal);
                } else {
                    request.setPaidAmount(0.0);
                }

                createdSales.add(salesService.createSale(request));
            }

            return createdSales;

        } catch (Exception e) {
            logger.error("Failed to create sale", e);
            showError("خطأ", "فشل في إنشاء الفاتورة: " + e.getMessage());
            return List.of();
        }
    }

    private Map<String, List<SaleItemRow>> groupRowsByCurrency() {
        Map<String, List<SaleItemRow>> rowsByCurrency = new LinkedHashMap<>();
        for (SaleItemRow row : saleItems) {
            rowsByCurrency.computeIfAbsent(row.getCurrency(), c -> new ArrayList<>()).add(row);
        }
        return rowsByCurrency;
    }

    @FXML
    private void handleCancel() {
        if (tabMode) {
            com.pharmax.util.TabManager.getInstance().closeTab("new-sale");
        } else if (dialogStage != null) {
            dialogStage.close();
        } else {
            resetSaleForm();
        }
    }

    private void resetSaleForm() {
        customerComboBox.setValue(null);
        projectLocationComboBox.getSelectionModel().clearSelection();
        newProjectLocationField.clear();
        categoryFilterComboBox.getSelectionModel().clearSelection();
        productComboBox.getSelectionModel().clearSelection();
        quantityField.clear();
        stockLabel.setText("");
        priceLabel.setText("");
        saleItems.clear();
        itemsTable.refresh();
        additionalDiscountField.setText("0");
        additionalDiscountCurrencyLabel.setText(IQD_CURRENCY);
        subtotalLabel.setText("0");
        discountLabel.setText("0");
        finalTotalLabel.setText(formatCurrencyAmount(0, IQD_CURRENCY));
        paidAmountField.setText("0");
        paidAmountCurrencyLabel.setText(IQD_CURRENCY);
        balanceLabel.setText("0");
        balanceStatusLabel.setText("");
        if (printCheckbox != null) {
            printCheckbox.setSelected(true);
            printCheckbox.setDisable(false);
        }
        if (quickSaleCheckbox != null) {
            boolean allowQuickSale = isQuickSaleAllowed();
            quickSaleCheckbox.setVisible(allowQuickSale);
            quickSaleCheckbox.setManaged(allowQuickSale);
            quickSaleCheckbox.setSelected(allowQuickSale && isQuickSaleDefaultEnabled());
        }
        if (notesArea != null) {
            notesArea.clear();
        }
        if (paymentGroup != null) {
            paymentGroup.selectToggle(cashRadio);
        }
        selectDefaultSaleCustomer();
        applyRoleRestrictions();
        updateQuickSaleState();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @SuppressWarnings("unused")
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private interface CurrencyValueProvider {
        double value(String currency, CurrencyTotals totals);
    }

    private static class CurrencyTotals {
        private double grossAmount;
        private double itemDiscount;
        private double netAmount;

        private double finalTotal(double additionalDiscount) {
            return netAmount - additionalDiscount;
        }
    }

    public static class SaleItemRow {
        private Long productId;
        private String productName;
        private double quantity;
        private double baseUnitPriceIqd;
        private double discountPercent;
        private double discountAmount;
        private double totalPrice;
        private String priceType;
        private String soldUnit;
        private double conversionFactor = 1.0;
        private double baseQuantity;
        private String batchPreview = "-";
        private String stripPriceDisplay = "-";
        private String boxPriceDisplay = "-";

        private String currency = "دينار";
        private double exchangeRate = 1500.0;
        private Double savedUnitPriceUsd = null;

        public SaleItemRow(Long productId, String productName, double quantity, double unitPriceIqd,
                double discountPercent, String priceType, String soldUnit, double conversionFactor) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.baseUnitPriceIqd = unitPriceIqd;
            this.discountPercent = discountPercent;
            this.priceType = priceType;
            this.soldUnit = soldUnit != null ? soldUnit : "وحدة";
            this.conversionFactor = conversionFactor > 0 ? conversionFactor : 1.0;
            this.baseQuantity = quantity * this.conversionFactor;
            recalculate();
        }

        public void setSavedUnitPriceUsd(Double savedUnitPriceUsd) {
            this.savedUnitPriceUsd = savedUnitPriceUsd;
        }
        public void setUnitPricing(Double unitPriceIqd, Double unitPriceUsd) {
            this.baseUnitPriceIqd = unitPriceIqd != null ? unitPriceIqd : 0.0;
            this.savedUnitPriceUsd = unitPriceUsd;
        }

        public void setCurrencyAndRate(String currency, double exchangeRate) {
            this.currency = currency != null ? currency : "دينار";
            if (exchangeRate > 0) {
                this.exchangeRate = exchangeRate;
            }
        }

        public String getCurrency() {
            return currency != null ? currency : "دينار";
        }

        public void recalculate() {
            double unitPrice = getUnitPrice();
            this.baseQuantity = quantity * conversionFactor;
            double gross = unitPrice * quantity;
            this.discountAmount = gross * (discountPercent / 100.0);
            this.totalPrice = gross - discountAmount;
        }

        public Long getProductId() {
            return productId;
        }

        public String getProductName() {
            return productName + " (" + getSoldUnit() + ")";
        }

        public double getQuantity() {
            return quantity;
        }

        public void setQuantity(double quantity) {
            this.quantity = quantity;
            this.baseQuantity = quantity * conversionFactor;
        }

        public void setSoldUnit(String soldUnit) {
            this.soldUnit = soldUnit != null ? soldUnit : "ÙˆØ­Ø¯Ø©";
        }

        public void setConversionFactor(double conversionFactor) {
            this.conversionFactor = conversionFactor > 0 ? conversionFactor : 1.0;
            this.baseQuantity = quantity * this.conversionFactor;
        }

        public String getPriceType() {
            return priceType;
        }

        public String getSoldUnit() {
            return soldUnit != null ? soldUnit : "وحدة";
        }

        public double getConversionFactor() {
            return conversionFactor;
        }

        public double getBaseQuantity() {
            return baseQuantity;
        }

        public void setBaseQuantity(double baseQuantity) {
            this.baseQuantity = baseQuantity;
        }

        public String getBatchPreview() {
            return batchPreview != null ? batchPreview : "-";
        }

        public void setBatchPreview(String batchPreview) {
            this.batchPreview = batchPreview;
        }

        public String getStripPriceDisplay() {
            return stripPriceDisplay != null ? stripPriceDisplay : "-";
        }

        public void setStripPriceDisplay(String stripPriceDisplay) {
            this.stripPriceDisplay = stripPriceDisplay;
        }

        public String getBoxPriceDisplay() {
            return boxPriceDisplay != null ? boxPriceDisplay : "-";
        }

        public void setBoxPriceDisplay(String boxPriceDisplay) {
            this.boxPriceDisplay = boxPriceDisplay;
        }

        public double getUnitPrice() {
            if ("دولار".equals(currency)) {
                if (savedUnitPriceUsd != null && savedUnitPriceUsd > 0) {
                    return savedUnitPriceUsd;
                }
                return baseUnitPriceIqd / exchangeRate;
            }
            return baseUnitPriceIqd;
        }

        public void setUnitPrice(double unitPrice) {
            if (unitPrice < 0) {
                return;
            }
            if ("دولار".equals(currency)) {
                this.savedUnitPriceUsd = unitPrice;
                this.baseUnitPriceIqd = unitPrice * exchangeRate;
            } else {
                this.baseUnitPriceIqd = unitPrice;
                this.savedUnitPriceUsd = unitPrice / exchangeRate;
            }
        }

        public double getDiscountPercent() {
            return discountPercent;
        }

        public void setDiscountPercent(double discountPercent) {
            this.discountPercent = discountPercent;
        }

        public double getDiscountAmount() {
            return discountAmount;
        }

        public double getTotalPrice() {
            return totalPrice;
        }
    }
}
