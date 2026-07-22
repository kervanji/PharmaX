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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import javafx.util.Duration;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import com.pharmax.util.AppConfigStore;
import com.pharmax.util.MedicalEmojiIcons;
import com.pharmax.util.SessionManager;
import com.pharmax.util.TabManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.ByteArrayInputStream;
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
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.prefs.Preferences;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;

public class SaleFormController {
    private static final Logger logger = LoggerFactory.getLogger(SaleFormController.class);
    private static final String IQD_CURRENCY = "دينار";
    private static final String DEFAULT_SALE_CUSTOMER_CODE = "CASH";
    private static final String DEFAULT_SALE_CUSTOMER_NAME = "زبون نقدي";
    private static final String DEFAULT_PRICE_TYPE = "مفرد";
    private static final String PREF_SALE_TABLE_HIDDEN_COLUMNS = "saleForm.itemsTable.hiddenColumns";

    @FXML
    private VBox root;
    @FXML
    private StackPane saleRoot;
    @FXML
    private VBox customerSelectionBox;
    @FXML
    private ComboBox<Customer> customerComboBox;
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
    private FlowPane inlineQuickSaleButtonsPane;
    @FXML
    private VBox quickSaleGroupsPane;
    @FXML
    private HBox quickSaleDrawer;
    @FXML
    private VBox quickSaleDrawerPanel;
    @FXML
    private Button quickSaleDrawerToggle;
    @FXML
    private ToggleButton quickSalePinButton;
    @FXML
    private Button quickSaleManageButton;
    @FXML
    private Label quickSaleEmptyLabel;
    @FXML
    private TableView<SaleItemRow> itemsTable;
    @FXML
    private Label selectedItemsTotalLabel;
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
    private final QuickSaleService quickSaleService;
    private final ObservableList<SaleItemRow> saleItems = FXCollections.observableArrayList();
    private FilteredList<Product> filteredProducts;
    private String productSearchQuery = "";
    private Customer lastSelectedDebtCustomer;
    private boolean globalProductSearchInstalled = false;
    private boolean suppressProductAutoAdd = false;
    private boolean categoryFilterListenerInstalled = false;
    private boolean quickSaleDrawerInitialized = false;
    private boolean quickSaleDrawerOpen = false;
    private Long selectedQuickSaleGroupId;
    private PauseTransition quickSaleHideDelay;
    private TranslateTransition quickSaleDrawerAnimation;
    private boolean refreshingComboData = false;
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
        this.quickSaleService = new QuickSaleService();

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
        updateCustomerSelectionVisibility();

        Platform.runLater(() -> {
            if (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage s) {
                dialogStage = s;
            }
            installGlobalProductSearch();
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

    private void installGlobalProductSearch() {
        if (globalProductSearchInstalled || root == null || productComboBox == null || productComboBox.getEditor() == null) {
            return;
        }
        if (root.getScene() == null) {
            root.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    installGlobalProductSearch();
                }
            });
            return;
        }

        Scene scene = root.getScene();
        scene.addEventFilter(KeyEvent.KEY_TYPED, this::handleGlobalProductSearchTyped);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalProductSearchPressed);
        globalProductSearchInstalled = true;
    }

    private void handleGlobalProductSearchTyped(KeyEvent event) {
        if (shouldIgnoreGlobalProductKey(event)) {
            return;
        }

        String character = event.getCharacter();
        if (character == null || character.isEmpty() || character.chars().allMatch(Character::isISOControl)) {
            return;
        }

        TextField editor = productComboBox.getEditor();
        if (editor == null) {
            return;
        }
        if (character.isBlank() && (editor.getText() == null || editor.getText().isBlank())) {
            return;
        }

        focusProductSearchEditor();
        editor.appendText(character);
        if (!productComboBox.isShowing()) {
            productComboBox.show();
        }
        event.consume();
    }

    private void handleGlobalProductSearchPressed(KeyEvent event) {
        if (event.getCode() != KeyCode.BACK_SPACE || shouldIgnoreGlobalProductKey(event)) {
            return;
        }

        TextField editor = productComboBox.getEditor();
        if (editor == null || editor.getText() == null || editor.getText().isEmpty()) {
            return;
        }

        focusProductSearchEditor();
        int caret = editor.getCaretPosition();
        if (caret > 0) {
            editor.deleteText(caret - 1, caret);
        }
        if (!productComboBox.isShowing()) {
            productComboBox.show();
        }
        event.consume();
    }

    private void focusProductSearchEditor() {
        productComboBox.requestFocus();
        TextField editor = productComboBox.getEditor();
        if (editor != null) {
            editor.requestFocus();
            editor.positionCaret(editor.getText() != null ? editor.getText().length() : 0);
        }
    }

    private boolean shouldIgnoreGlobalProductKey(KeyEvent event) {
        if (root == null || root.getScene() == null) {
            return true;
        }

        if (!isEventInsideSaleForm(event)) {
            return true;
        }

        Node focusOwner = root.getScene().getFocusOwner();
        if (focusOwner == null) {
            return false;
        }
        if (isDescendantOf(focusOwner, productComboBox)) {
            return true;
        }
        return focusOwner instanceof TextInputControl || isDescendantOf(focusOwner, customerComboBox);
    }

    private boolean isEventInsideSaleForm(KeyEvent event) {
        if (event != null && event.getTarget() instanceof Node targetNode) {
            return isDescendantOf(targetNode, root);
        }

        Node focusOwner = root != null && root.getScene() != null ? root.getScene().getFocusOwner() : null;
        return focusOwner != null && isDescendantOf(focusOwner, root);
    }

    private boolean isDescendantOf(Node node, Node parent) {
        Node current = node;
        while (current != null) {
            if (current == parent) {
                return true;
            }
            current = current.getParent();
        }
        return false;
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

            updateCustomerSelectionVisibility();
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
        updateCustomerSelectionVisibility();
        updateQuickSaleState();
    }

    private void updateCustomerSelectionVisibility() {
        boolean debtSale = !SessionManager.getInstance().isSeller()
                && creditRadio != null
                && creditRadio.isSelected();

        if (customerSelectionBox != null) {
            customerSelectionBox.setVisible(debtSale);
            customerSelectionBox.setManaged(debtSale);
        }

        if (debtSale) {
            if (isDefaultSaleCustomer(customerComboBox != null ? customerComboBox.getValue() : null)) {
                clearSelectedCustomer();
            }
            if (customerComboBox != null) {
                customerComboBox.setDisable(false);
                Platform.runLater(() -> customerComboBox.requestFocus());
            }
            return;
        }

        if (customerComboBox != null) {
            customerComboBox.hide();
            customerComboBox.setDisable(true);
        }
        selectDefaultSaleCustomer();
    }

    private boolean isDebtCustomerSelectionVisible() {
        return customerSelectionBox != null
                && customerSelectionBox.isVisible()
                && !SessionManager.getInstance().isSeller()
                && creditRadio != null
                && creditRadio.isSelected();
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

        if (!categoryFilterListenerInstalled) {
            categoryFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (refreshingComboData) {
                    return;
                }
                applyProductFilters();
                if (productSearchQuery != null && !productSearchQuery.isBlank() && !productComboBox.isShowing()) {
                    productComboBox.show();
                }
            });
            categoryFilterListenerInstalled = true;
        }
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
        List<Customer> customers = customerRepository.findSaleCustomers();
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

        customerComboBox.valueProperty().addListener((obs, oldCustomer, newCustomer) -> {
            if (newCustomer != null && !isDefaultSaleCustomer(newCustomer)) {
                lastSelectedDebtCustomer = newCustomer;
            }
        });

        // Enable search in customer ComboBox
        if (customerComboBox.getEditor() != null) {
            customerComboBox.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                if (refreshingComboData) {
                    return;
                }
                if (!isDebtCustomerSelectionVisible()) {
                    return;
                }

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
                Customer exactCustomer = findCustomerByText(newText);
                if (exactCustomer != null && !isDefaultSaleCustomer(exactCustomer)) {
                    lastSelectedDebtCustomer = exactCustomer;
                }
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

    }

    private void selectDefaultSaleCustomer() {
        Customer defaultCustomer = getOrCreateDefaultSaleCustomer();
        if (defaultCustomer == null || customerComboBox == null) {
            return;
        }

        customerComboBox.setValue(defaultCustomer);
    }

    private Customer getOrCreateDefaultSaleCustomer() {
        return customerRepository.findByCustomerCode(DEFAULT_SALE_CUSTOMER_CODE)
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setCustomerCode(DEFAULT_SALE_CUSTOMER_CODE);
                    customer.setName(DEFAULT_SALE_CUSTOMER_NAME);
                    return customerRepository.save(customer);
                });
    }

    private Customer resolveSelectedCustomer() {
        if (customerComboBox == null) {
            return null;
        }

        Customer selectedCustomer = customerComboBox.getValue();
        if (selectedCustomer != null && !isDefaultSaleCustomer(selectedCustomer)) {
            return selectedCustomer;
        }

        if (customerComboBox.getEditor() == null || filteredCustomers == null) {
            return null;
        }

        String typedText = customerComboBox.getEditor().getText();
        if (typedText == null || typedText.trim().isEmpty()) {
            return null;
        }

        String normalizedText = typedText.trim();
        Customer typedCustomer = findCustomerByText(normalizedText);
        if (typedCustomer != null && !isDefaultSaleCustomer(typedCustomer)) {
            return typedCustomer;
        }

        if (filteredCustomers.size() == 1 && !isDefaultSaleCustomer(filteredCustomers.get(0))) {
            return filteredCustomers.get(0);
        }

        if (lastSelectedDebtCustomer != null && customerMatchesText(lastSelectedDebtCustomer, normalizedText)) {
            return lastSelectedDebtCustomer;
        }

        return null;
    }

    private Customer findCustomerByText(String text) {
        if (text == null || filteredCustomers == null) {
            return null;
        }

        String normalizedText = text.trim();
        if (normalizedText.isEmpty()) {
            return null;
        }

        return filteredCustomers.getSource().stream()
                .filter(customer -> customerMatchesText(customer, normalizedText))
                .findFirst()
                .orElse(null);
    }

    private boolean customerMatchesText(Customer customer, String text) {
        if (customer == null || text == null) {
            return false;
        }

        String normalizedText = text.trim();
        String rendered = customerComboBox != null && customerComboBox.getConverter() != null
                ? customerComboBox.getConverter().toString(customer)
                : "";
        String name = customer.getName() != null ? customer.getName().trim() : "";
        String code = customer.getCustomerCode() != null ? customer.getCustomerCode().trim() : "";
        String phone = customer.getPhoneNumber() != null ? customer.getPhoneNumber().trim() : "";

        return normalizedText.equalsIgnoreCase(rendered)
                || normalizedText.equalsIgnoreCase(name)
                || normalizedText.equalsIgnoreCase(code)
                || normalizedText.equalsIgnoreCase(phone);
    }

    private boolean isDefaultSaleCustomer(Customer customer) {
        return customer != null
                && customer.getCustomerCode() != null
                && DEFAULT_SALE_CUSTOMER_CODE.equalsIgnoreCase(customer.getCustomerCode().trim());
    }

    private void clearSelectedCustomer() {
        if (customerComboBox == null) {
            return;
        }

        customerComboBox.hide();
        customerComboBox.setValue(null);
        if (customerComboBox.getEditor() != null) {
            customerComboBox.getEditor().clear();
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
                if (refreshingComboData) {
                    return;
                }
                if (productComboBox.getValue() != null) {
                    String rendered = productComboBox.getConverter().toString(productComboBox.getValue());
                    if (rendered.equals(newText)) {
                        return;
                    }
                }
                if (isProductSelectionText(newText)) {
                    return;
                }

                productSearchQuery = newText == null ? "" : newText.trim().toLowerCase();
                applyProductFilters();

                if (productSearchQuery.isEmpty()) {
                    productComboBox.hide();
                    return;
                }

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
                if (!suppressProductAutoAdd) {
                    addSelectedProductWithSingleQuantity(selected);
                }
            }
        });
    }

    private boolean isProductSelectionText(String text) {
        if (text == null || filteredProducts == null || productComboBox == null || productComboBox.getConverter() == null) {
            return false;
        }

        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        return filteredProducts.getSource().stream()
                .anyMatch(product -> normalized.equals(productComboBox.getConverter().toString(product)));
    }

    private void refreshData() {
        Customer selectedCustomer = lastSelectedDebtCustomer != null ? lastSelectedDebtCustomer : customerComboBox.getValue();

        refreshingComboData = true;
        suppressProductAutoAdd = true;
        try {
            hideComboPopup(customerComboBox);
            hideComboPopup(categoryFilterComboBox);
            hideComboPopup(productComboBox);

            if (productComboBox != null) {
                productComboBox.getSelectionModel().clearSelection();
                productComboBox.setValue(null);
                if (productComboBox.getEditor() != null) {
                    productComboBox.getEditor().clear();
                }
            }
            productSearchQuery = "";
            selectedProduct = null;
            selectedUnit = null;
            if (unitComboBox != null) {
                unitComboBox.getItems().clear();
                unitComboBox.setValue(null);
            }

            List<Customer> customers = customerRepository.findSaleCustomers();
            filteredCustomers = new FilteredList<>(FXCollections.observableArrayList(customers), c -> true);
            customerComboBox.setItems(filteredCustomers);
            if (selectedCustomer != null && selectedCustomer.getId() != null && !isDefaultSaleCustomer(selectedCustomer)) {
                customers.stream()
                        .filter(customer -> selectedCustomer.getId().equals(customer.getId()))
                        .findFirst()
                        .ifPresent(customer -> {
                            lastSelectedDebtCustomer = customer;
                            if (isDebtCustomerSelectionVisible()) {
                                customerComboBox.setValue(customer);
                            }
                        });
            } else if (!isDebtCustomerSelectionVisible()) {
                selectDefaultSaleCustomer();
            }

            List<Product> products = productRepository.findAll().stream()
                    .filter(Product::getIsActive)
                    .toList();
            unitsByProduct.clear();
            for (Product product : products) {
                unitsByProduct.put(product.getId(), productUnitService.getUnitsForProductOrDefault(product));
            }

            filteredProducts = new FilteredList<>(FXCollections.observableArrayList(products), p -> true);
            productComboBox.setItems(filteredProducts);
            setupCategoryFilter(products);
        } finally {
            suppressProductAutoAdd = false;
            refreshingComboData = false;
        }

        refreshCartBatchPreviews();
        updateTotals();
    }

    private void hideComboPopup(ComboBox<?> comboBox) {
        if (comboBox != null) {
            comboBox.hide();
        }
    }

    private void addSelectedProductWithSingleQuantity(Product product) {
        if (product == null) {
            return;
        }
        addProductToSale(product, 1.0, getSelectedSaleUnit());
    }

    private void setupQuickSaleButtons() {
        List<Product> quickSaleProducts = productRepository.findAll().stream()
                .filter(product -> Boolean.TRUE.equals(product.getIsActive()))
                .filter(product -> Boolean.TRUE.equals(product.getIsQuickSale()))
                .sorted((left, right) -> {
                    String leftName = left != null && left.getName() != null ? left.getName() : "";
                    String rightName = right != null && right.getName() != null ? right.getName() : "";
                    return leftName.compareToIgnoreCase(rightName);
                })
                .toList();

        if (inlineQuickSaleButtonsPane != null) {
            inlineQuickSaleButtonsPane.getChildren().clear();
            inlineQuickSaleButtonsPane.setVisible(!quickSaleProducts.isEmpty());
            inlineQuickSaleButtonsPane.setManaged(!quickSaleProducts.isEmpty());
        }

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
            if (inlineQuickSaleButtonsPane != null) inlineQuickSaleButtonsPane.getChildren().add(quickSaleButton);
        }

        if (quickSaleButtonsPane != null && quickSaleGroupsPane != null && quickSaleDrawer != null) {
            initializeQuickSaleDrawerInteraction();
            loadQuickSaleGroups();
        }
    }

    private void initializeQuickSaleDrawerInteraction() {
        if (quickSaleDrawerInitialized) return;
        quickSaleDrawerInitialized = true;
        quickSaleHideDelay = new PauseTransition(Duration.seconds(3));
        quickSaleHideDelay.setOnFinished(event -> {
            if (quickSalePinButton == null || !quickSalePinButton.isSelected()) closeQuickSaleDrawer(true);
        });
        quickSaleDrawer.setOnMouseEntered(event -> {
            quickSaleHideDelay.stop();
            openQuickSaleDrawer(true);
        });
        quickSaleDrawer.setOnMouseExited(event -> {
            if (quickSalePinButton == null || !quickSalePinButton.isSelected()) quickSaleHideDelay.playFromStart();
        });
        if (quickSaleManageButton != null) {
            boolean admin = SessionManager.getInstance().isAdmin();
            quickSaleManageButton.setVisible(admin);
            quickSaleManageButton.setManaged(admin);
        }
        Platform.runLater(() -> closeQuickSaleDrawer(false));
    }

    private void loadQuickSaleGroups() {
        try {
            List<QuickSaleGroup> groups = quickSaleService.getGroups(true);
            quickSaleGroupsPane.getChildren().clear();
            if (groups.isEmpty()) {
                selectedQuickSaleGroupId = null;
                renderQuickSaleItems(null);
                return;
            }
            if (selectedQuickSaleGroupId == null || groups.stream().noneMatch(g -> g.getId().equals(selectedQuickSaleGroupId))) {
                selectedQuickSaleGroupId = groups.get(0).getId();
            }
            for (QuickSaleGroup group : groups) {
                ImageView coloredIcon = MedicalEmojiIcons.createView(group.getIconKey(), 22);
                Button button = new Button(coloredIcon == null ? group.toString() : group.getName());
                button.setGraphic(coloredIcon);
                button.setGraphicTextGap(7);
                button.setWrapText(true);
                button.setMaxWidth(Double.MAX_VALUE);
                button.setFocusTraversable(false);
                button.getStyleClass().add("quick-sale-group-button");
                if (group.getId().equals(selectedQuickSaleGroupId)) button.getStyleClass().add("selected");
                button.setOnAction(event -> {
                    selectedQuickSaleGroupId = group.getId();
                    loadQuickSaleGroups();
                });
                quickSaleGroupsPane.getChildren().add(button);
            }
            QuickSaleGroup selected = groups.stream().filter(g -> g.getId().equals(selectedQuickSaleGroupId))
                    .findFirst().orElse(groups.get(0));
            renderQuickSaleItems(selected);
        } catch (Exception e) {
            logger.error("Failed to load quick sale drawer", e);
            quickSaleButtonsPane.getChildren().clear();
            quickSaleEmptyLabel.setText("تعذر تحميل المنتجات السريعة");
            quickSaleEmptyLabel.setVisible(true);
            quickSaleEmptyLabel.setManaged(true);
        }
    }

    private void renderQuickSaleItems(QuickSaleGroup group) {
        List<QuickSaleItem> items = group == null ? List.of() : quickSaleService.getItems(group.getId());
        quickSaleButtonsPane.getChildren().clear();
        boolean empty = items.isEmpty();
        quickSaleEmptyLabel.setText(group == null ? "لا توجد مجموعات مفعلة" : "لا توجد منتجات في هذه المجموعة");
        quickSaleEmptyLabel.setVisible(empty);
        quickSaleEmptyLabel.setManaged(empty);
        for (QuickSaleItem item : items) quickSaleButtonsPane.getChildren().add(createQuickSaleCard(item));
        resizeQuickSaleDrawer(items.size());
        Platform.runLater(() -> resizeQuickSaleDrawer(items.size()));
    }

    private void resizeQuickSaleDrawer(int itemCount) {
        int rows = Math.max(1, (itemCount + 1) / 2);
        double cardsHeight = 76 + rows * 184.0;
        double groupsHeight = 76 + Math.max(1, quickSaleGroupsPane.getChildren().size()) * 46.0;
        double desiredHeight = Math.max(270, Math.max(cardsHeight, groupsHeight));
        double sceneHeight = quickSaleDrawer.getScene() == null ? 720 : quickSaleDrawer.getScene().getHeight();
        double availableHeight = Math.max(320, sceneHeight - 16);
        double drawerHeight = Math.min(desiredHeight, availableHeight);
        quickSaleDrawer.setMinHeight(Region.USE_PREF_SIZE);
        quickSaleDrawer.setPrefHeight(drawerHeight);
        quickSaleDrawer.setMaxHeight(Region.USE_PREF_SIZE);
        quickSaleDrawerPanel.setPrefHeight(drawerHeight);
    }

    private Button createQuickSaleCard(QuickSaleItem item) {
        Product product = item.getProduct();
        ProductUnit unit = item.getProductUnit() != null ? item.getProductUnit() : resolveQuickSaleUnit(product);
        VBox content = new VBox(4);
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setFillWidth(true);

        if (item.getImageData() != null && item.getImageData().length > 0) {
            ImageView image = new ImageView(new Image(new ByteArrayInputStream(item.getImageData())));
            image.setFitWidth(82); image.setFitHeight(64); image.setPreserveRatio(true); image.setSmooth(true);
            content.getChildren().add(image);
        } else {
            Label placeholder = new Label(product.getName() == null || product.getName().isBlank()
                    ? "💊" : product.getName().substring(0, 1));
            placeholder.getStyleClass().add("quick-sale-card-placeholder");
            content.getChildren().add(placeholder);
        }

        Label name = new Label(product.getName());
        name.setWrapText(true); name.setMaxWidth(112); name.getStyleClass().add("quick-sale-card-name");
        Double price = unit != null && unit.getSalePrice() != null ? unit.getSalePrice() : product.getUnitPrice();
        String unitName = unit == null ? productUnitService.resolveBaseUnit(product) : unit.getUnitName();
        Label priceLabel = new Label((price == null ? "—" : numberFormatter.format(price) + " د.ع") + " • " + unitName);
        priceLabel.getStyleClass().add("quick-sale-card-price");
        boolean unlimitedStock = Boolean.TRUE.equals(product.getIsUnlimitedStock());
        double stock = product.getQuantityInStock() == null ? 0 : product.getQuantityInStock();
        Label stockLabel = new Label(unlimitedStock ? "الكمية: غير محدودة"
                : stock > 0 ? "المتوفر: " + numberFormatter.format(stock) : "غير متوفر");
        stockLabel.getStyleClass().add(unlimitedStock || stock > 0 ? "quick-sale-card-stock" : "quick-sale-card-out");
        double inCart = saleItems.stream().filter(row -> Objects.equals(row.getProductId(), product.getId()))
                .mapToDouble(SaleItemRow::getQuantity).sum();
        content.getChildren().addAll(name, priceLabel, stockLabel);
        if (inCart > 0) {
            Label badge = new Label("في الفاتورة: " + numberFormatter.format(inCart));
            badge.getStyleClass().add("quick-sale-card-badge"); content.getChildren().add(badge);
        }

        Button card = new Button();
        card.setGraphic(content); card.setMnemonicParsing(false); card.setFocusTraversable(false);
        card.setPrefWidth(126); card.setMinWidth(126); card.setMaxWidth(126);
        card.setPrefHeight(176); card.setMinHeight(176); card.setMaxHeight(176);
        card.getStyleClass().add("quick-sale-product-card");
        if (item.getAccentColor() != null && !item.getAccentColor().isBlank()) {
            card.getStyleClass().add("accent-" + item.getAccentColor());
        }
        double requiredStock = unit == null ? 1.0 : unit.getEffectiveConversionFactor();
        boolean available = Boolean.TRUE.equals(product.getIsActive())
                && (unlimitedStock || stock + 1e-9 >= requiredStock);
        card.setDisable(!available);
        card.setTooltip(new Tooltip(available ? "إضافة وحدة واحدة إلى الفاتورة" : "المنتج غير متوفر للبيع"));
        card.setOnAction(event -> {
            Product fresh = productRepository.findById(product.getId()).orElse(product);
            ProductUnit selectedUnit = item.getProductUnit() != null ? item.getProductUnit() : resolveQuickSaleUnit(fresh);
            addProductToSale(fresh, 1.0, selectedUnit);
            renderQuickSaleItems(groupForSelectedId());
        });
        return card;
    }

    private QuickSaleGroup groupForSelectedId() {
        if (selectedQuickSaleGroupId == null) return null;
        return quickSaleService.getGroups(true).stream()
                .filter(g -> g.getId().equals(selectedQuickSaleGroupId)).findFirst().orElse(null);
    }

    @FXML
    private void handleToggleQuickSaleDrawer() {
        if (quickSaleDrawerOpen) closeQuickSaleDrawer(true); else openQuickSaleDrawer(true);
    }

    @FXML
    private void handleToggleQuickSalePin() {
        if (quickSalePinButton.isSelected()) {
            quickSaleHideDelay.stop(); openQuickSaleDrawer(true);
        } else if (!quickSaleDrawer.isHover()) {
            quickSaleHideDelay.playFromStart();
        }
    }

    private void openQuickSaleDrawer(boolean animated) { animateQuickSaleDrawer(0, animated, true); }
    private void closeQuickSaleDrawer(boolean animated) {
        double hiddenOffset = -(quickSaleDrawerPanel == null ? 402 : quickSaleDrawerPanel.getWidth());
        if (hiddenOffset >= 0) hiddenOffset = -402;
        animateQuickSaleDrawer(hiddenOffset, animated, false);
    }

    private void animateQuickSaleDrawer(double targetX, boolean animated, boolean open) {
        if (quickSaleDrawerAnimation != null) quickSaleDrawerAnimation.stop();
        quickSaleDrawerOpen = open;
        if (quickSaleDrawerToggle != null) quickSaleDrawerToggle.setText(open ? "❮" : "❯");
        if (!animated) { quickSaleDrawer.setTranslateX(targetX); return; }
        quickSaleDrawerAnimation = new TranslateTransition(Duration.millis(260), quickSaleDrawer);
        quickSaleDrawerAnimation.setToX(targetX);
        quickSaleDrawerAnimation.setInterpolator(javafx.animation.Interpolator.EASE_BOTH);
        quickSaleDrawerAnimation.play();
    }

    @FXML
    private void handleManageQuickSales() {
        if (!SessionManager.getInstance().isAdmin()) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/QuickSaleManager.fxml"));
            Parent view = loader.load();
            QuickSaleManagerController controller = loader.getController();
            controller.setOnChanged(this::loadQuickSaleGroups);
            Stage stage = new Stage();
            stage.setTitle("إدارة المنتجات السريعة");
            stage.initModality(Modality.WINDOW_MODAL);
            if (quickSaleDrawer.getScene() != null) stage.initOwner(quickSaleDrawer.getScene().getWindow());
            stage.setScene(new Scene(view));
            stage.setMinWidth(860); stage.setMinHeight(580);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.showAndWait();
            loadQuickSaleGroups();
        } catch (Exception e) {
            logger.error("Failed to open quick sale manager", e);
            showError("خطأ", "تعذر فتح إدارة المنتجات السريعة: " + e.getMessage());
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
            suppressProductAutoAdd = true;
            try {
                selectedProduct = product;
                productComboBox.setValue(product);
            } finally {
                suppressProductAutoAdd = false;
            }
            handleProductSelection(product);
            addSelectedProductWithSingleQuantity(product);
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
        updateMedicineDetailsButtonState();
    }

    @FXML
    private void handleOpenProductBatchDetails() {
        SaleItemRow selectedRow = getSelectedSaleItemForDetails();
        Product product = selectedRow != null
                ? resolveProductForSaleRow(selectedRow)
                : selectedProduct != null ? selectedProduct : productComboBox.getValue();
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
            ProductUnit saleUnit = selectedRow != null
                    ? resolveSaleUnitForRow(product, selectedRow)
                    : getSelectedSaleUnit();
            String currency = selectedRow != null ? selectedRow.getCurrency() : IQD_CURRENCY;
            Double salePrice = selectedRow != null
                    ? selectedRow.getUnitPrice()
                    : getSelectedPrice(product, saleUnit, currency);
            controller.setProduct(product, saleUnit, salePrice, currency);

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

    private void updateMedicineDetailsButtonState() {
        if (medicineDetailsButton == null) {
            return;
        }
        boolean hasSelectedCartRow = getSelectedSaleItemForDetails() != null;
        boolean hasSelectedInputProduct = selectedProduct != null || (productComboBox != null && productComboBox.getValue() != null);
        medicineDetailsButton.setDisable(!hasSelectedCartRow && !hasSelectedInputProduct);
    }

    private SaleItemRow getSelectedSaleItemForDetails() {
        if (itemsTable == null || itemsTable.getSelectionModel() == null) {
            return null;
        }
        SaleItemRow selectedRow = itemsTable.getSelectionModel().getSelectedItem();
        if (selectedRow != null) {
            return selectedRow;
        }
        ObservableList<SaleItemRow> selectedRows = itemsTable.getSelectionModel().getSelectedItems();
        return selectedRows == null || selectedRows.isEmpty() ? null : selectedRows.get(0);
    }

    private Product resolveProductForSaleRow(SaleItemRow row) {
        if (row == null || row.getProductId() == null) {
            return null;
        }
        if (selectedProduct != null && row.getProductId().equals(selectedProduct.getId())) {
            return selectedProduct;
        }
        return productRepository.findById(row.getProductId()).orElse(null);
    }

    private ProductUnit resolveSaleUnitForRow(Product product, SaleItemRow row) {
        if (product == null || row == null) {
            return null;
        }
        String soldUnit = row.getSoldUnit();
        List<ProductUnit> units = unitsByProduct.computeIfAbsent(product.getId(),
                id -> productUnitService.getUnitsForProductOrDefault(product));
        return units.stream()
                .filter(unit -> unit.getUnitName() != null && unit.getUnitName().equals(soldUnit))
                .findFirst()
                .orElseGet(() -> new ProductUnit(product, soldUnit, row.getConversionFactor()));
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
        if (Boolean.TRUE.equals(selectedProduct.getIsUnlimitedStock())) {
            stockLabel.setText("المخزون المتاح: كمية غير محدودة");
            stockLabel.setStyle("-fx-text-fill: -fx-success-text; -fx-font-weight: bold;");
            return;
        }
        String baseUnit = productUnitService.resolveBaseUnit(selectedProduct);
        String saleUnit = unit != null && unit.getUnitName() != null ? unit.getUnitName() : baseUnit;
        double factor = unit != null ? unit.getEffectiveConversionFactor() : 1.0;
        double stock = getSellableStockQuantity(selectedProduct);
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
        itemsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        itemsTable.getSelectionModel().getSelectedItems()
                .addListener((javafx.collections.ListChangeListener<SaleItemRow>) change -> {
                    updateSelectedItemsTotal();
                    updateMedicineDetailsButtonState();
                });
        itemsTable.setEditable(true);
        updateSelectedItemsTotal();
        updateMedicineDetailsButtonState();
        setupItemsTableColumnMenu();
    }

    private void setupItemsTableColumnMenu() {
        if (itemsTable == null) {
            return;
        }

        Map<String, TableColumn<SaleItemRow, ?>> columnsById = new LinkedHashMap<>();
        putColumnIfPresent(columnsById, "productName", productNameColumn, "المنتج");
        putColumnIfPresent(columnsById, "stripPrice", stripPriceColumn, "سعر الشريط");
        putColumnIfPresent(columnsById, "boxPrice", boxPriceColumn, "سعر العلبة");
        putColumnIfPresent(columnsById, "quantity", quantityColumn, "الكمية");
        putColumnIfPresent(columnsById, "soldUnit", soldUnitColumn, "الوحدة");
        putColumnIfPresent(columnsById, "conversionFactor", conversionFactorColumn, "التحويل");
        putColumnIfPresent(columnsById, "baseQuantity", baseQuantityColumn, "كمية الأساس");
        putColumnIfPresent(columnsById, "batchPreview", batchPreviewColumn, "دفعات FEFO");
        putColumnIfPresent(columnsById, "unitPrice", unitPriceColumn, "السعر");
        putColumnIfPresent(columnsById, "discount", discountColumn, "الخصم");
        putColumnIfPresent(columnsById, "total", totalColumn, "الإجمالي");
        putColumnIfPresent(columnsById, "edit", editColumn, "تعديل");
        putColumnIfPresent(columnsById, "action", actionColumn, "حذف");

        Set<String> hiddenColumns = loadHiddenSaleTableColumns();
        for (Map.Entry<String, TableColumn<SaleItemRow, ?>> entry : columnsById.entrySet()) {
            entry.getValue().setVisible(!hiddenColumns.contains(entry.getKey()));
        }

        ContextMenu menu = new ContextMenu();
        List<CheckMenuItem> columnMenuItems = new ArrayList<>();
        MenuItem showAllItem = new MenuItem("إظهار كل الأعمدة");
        showAllItem.setOnAction(event -> {
            hiddenColumns.clear();
            columnsById.values().forEach(column -> column.setVisible(true));
            columnMenuItems.forEach(item -> item.setSelected(true));
            saveHiddenSaleTableColumns(hiddenColumns);
        });
        menu.getItems().add(showAllItem);
        menu.getItems().add(new SeparatorMenuItem());

        for (Map.Entry<String, TableColumn<SaleItemRow, ?>> entry : columnsById.entrySet()) {
            String columnId = entry.getKey();
            TableColumn<SaleItemRow, ?> column = entry.getValue();
            String label = column.getUserData() instanceof String text ? text : columnId;
            CheckMenuItem item = new CheckMenuItem(label);
            item.setSelected(column.isVisible());
            item.selectedProperty().addListener((obs, oldVal, visible) -> {
                column.setVisible(visible);
                if (visible) {
                    hiddenColumns.remove(columnId);
                } else {
                    hiddenColumns.add(columnId);
                }
                saveHiddenSaleTableColumns(hiddenColumns);
            });
            columnMenuItems.add(item);
            menu.getItems().add(item);
        }
        itemsTable.setContextMenu(menu);
    }

    private void putColumnIfPresent(Map<String, TableColumn<SaleItemRow, ?>> columnsById,
                                    String id,
                                    TableColumn<SaleItemRow, ?> column,
                                    String label) {
        if (column != null) {
            column.setUserData(label);
            columnsById.put(id, column);
        }
    }

    private Set<String> loadHiddenSaleTableColumns() {
        Set<String> hidden = new HashSet<>();
        try {
            String username = SessionManager.getInstance().getCurrentUsername();
            String raw = Preferences.userNodeForPackage(SaleFormController.class)
                    .get(PREF_SALE_TABLE_HIDDEN_COLUMNS + "." + username, "");
            if (raw != null && !raw.isBlank()) {
                for (String part : raw.split(",")) {
                    if (!part.isBlank()) {
                        hidden.add(part.trim());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load sale table column preferences", e);
        }
        return hidden;
    }

    private void saveHiddenSaleTableColumns(Set<String> hiddenColumns) {
        try {
            String username = SessionManager.getInstance().getCurrentUsername();
            String raw = hiddenColumns == null || hiddenColumns.isEmpty()
                    ? ""
                    : String.join(",", hiddenColumns);
            Preferences.userNodeForPackage(SaleFormController.class)
                    .put(PREF_SALE_TABLE_HIDDEN_COLUMNS + "." + username, raw);
        } catch (Exception e) {
            logger.warn("Failed to save sale table column preferences", e);
        }
    }

    private void updateSelectedItemsTotal() {
        if (selectedItemsTotalLabel == null || itemsTable == null) {
            return;
        }

        ObservableList<SaleItemRow> selectedRows = itemsTable.getSelectionModel().getSelectedItems();
        if (selectedRows == null || selectedRows.isEmpty()) {
            selectedItemsTotalLabel.setText("المحدد: 0 د.ع");
            return;
        }

        Map<String, CurrencyTotals> selectedTotals = new LinkedHashMap<>();
        for (SaleItemRow row : selectedRows) {
            String currency = row.getCurrency();
            CurrencyTotals summary = selectedTotals.computeIfAbsent(currency, c -> new CurrencyTotals());
            summary.netAmount += row.getTotalPrice();
        }

        selectedItemsTotalLabel.setText("المحدد: " + selectedRows.size() + " | "
                + formatCurrencyTotals(selectedTotals, (currency, summary) -> summary.netAmount));
    }

    private void openProductEditForm(SaleItemRow row) {
        try {
            Product product = productRepository.findById(row.getProductId()).orElse(null);
            if (product == null) {
                showError("خطأ", "لم يتم العثور على المنتج");
                return;
            }

            String tabId = "product-edit-" + product.getId();
            ProductController controller = TabManager.getInstance().openTab(
                    tabId,
                    "تعديل منتج - " + product.getName(),
                    "add_product.svg",
                    "/views/ProductForm.fxml",
                    (ProductController productController) -> {
                        productController.setTabMode(true);
                        productController.setTabId(tabId);
                        productController.setAfterSaveCallback(() -> refreshSaleAfterProductEdit(product.getId()));
                        productController.setProduct(product);
                    });
            if (controller == null && !TabManager.getInstance().isTabOpen(tabId)) {
                showError("خطأ", "فشل في فتح تاب تعديل المنتج");
            }
        } catch (Exception e) {
            logger.error("Failed to open product form", e);
            showError("خطأ", "فشل في فتح تاب تعديل المنتج");
        }
    }

    private void refreshSaleAfterProductEdit(Long productId) {
        boolean restoreSelectedProduct = productId != null
                && selectedProduct != null
                && productId.equals(selectedProduct.getId());
        refreshData();
        itemsTable.refresh();
        updateTotals();

        if (restoreSelectedProduct) {
            Product updated = productRepository.findById(productId).orElse(null);
            if (updated != null) {
                selectedProduct = updated;
                if (productComboBox != null) {
                    suppressProductAutoAdd = true;
                    try {
                        productComboBox.setValue(updated);
                    } finally {
                        suppressProductAutoAdd = false;
                    }
                }
                unitsByProduct.put(productId, productUnitService.getUnitsForProductOrDefault(updated));
                updateSelectedProductStockLabel();
                updateSelectedProductPriceLabel();
                updateProductPreviewPanel();
            }
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
            if (!isDefaultSaleCustomer(customer)) {
                lastSelectedDebtCustomer = customer;
            }
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
        List<Customer> customers = customerRepository.findSaleCustomers();
        filteredCustomers = new FilteredList<>(FXCollections.observableArrayList(customers), c -> true);
        customerComboBox.setItems(filteredCustomers);

        Customer last = customers.stream()
                .max((a, b) -> Long.compare(a.getId() != null ? a.getId() : 0L, b.getId() != null ? b.getId() : 0L))
                .orElse(null);

        if (last != null) {
            customerComboBox.setValue(last);
            if (!isDefaultSaleCustomer(last)) {
                lastSelectedDebtCustomer = last;
            }
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

        Product previewProduct = productRepository.findById(row.getProductId()).orElse(null);
        if (previewProduct != null && Boolean.TRUE.equals(previewProduct.getIsUnlimitedStock())) {
            return "كمية غير محدودة — لا تخصم من المخزون";
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
        if (product != null && Boolean.TRUE.equals(product.getIsUnlimitedStock())) {
            return false;
        }
        double availableStock = getSellableStockQuantity(product);
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
        if (product != null && Boolean.TRUE.equals(product.getIsUnlimitedStock())) {
            return true;
        }
        double availableStock = getSellableStockQuantity(product);
        if (requestedQuantity > availableStock + 1e-9) {
            showError("خطأ",
                    "الكمية غير متوفرة للمنتج: " + product.getName()
                            + "\nالمتوفر: " + numberFormatter.format(availableStock)
                            + "\nالمطلوب: " + numberFormatter.format(requestedQuantity));
            return false;
        }
        return true;
    }

    private double getSellableStockQuantity(Product product) {
        if (product == null || product.getId() == null) {
            return 0.0;
        }
        if (Boolean.TRUE.equals(product.getIsUnlimitedStock())) {
            return Double.POSITIVE_INFINITY;
        }

        try {
            return productBatchService.getAvailableBatches(product.getId()).stream()
                    .map(ProductBatch::getQuantity)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();
        } catch (Exception e) {
            logger.warn("Could not resolve sellable stock for product {}", product.getId(), e);
            return product.getQuantityInStock() != null ? product.getQuantityInStock() : 0.0;
        }
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
        updateSelectedItemsTotal();
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
                        Receipt receipt = receiptService.generateReceipt(sale.getId(), ReceiptService.TEMPLATE_THERMAL_80MM, "System");
                        if (!printAfterSave || receipt.getFilePath() == null) {
                            continue;
                        }

                        File pdfFile = new File(receipt.getFilePath());
                        if (pdfFile.exists()) {
                            try {
                                printReceiptPdf(pdfFile);
                            } catch (Exception printEx) {
                                logger.warn("Receipt print failed; opening preview fallback", printEx);
                                openReceiptPreview(pdfFile);
                                showWarning("تنبيه", "تم حفظ البيع لكن فشلت الطباعة. تم فتح معاينة الفاتورة.");
                            }
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

        if (PrinterJob.lookupPrintServices().length == 0) {
            openReceiptPreview(pdfFile);
            showWarning("تنبيه", "لا توجد طابعة متاحة. تم فتح معاينة الفاتورة بدلاً من الطباعة.");
            return;
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

    private void openReceiptPreview(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) {
            return;
        }
        if (mainApp != null) {
            mainApp.showPdfPreview(pdfFile, "معاينة فاتورة حرارية");
        }
    }

    private List<Sale> createSales() {
        if (saleItems.isEmpty()) {
            showError("خطأ", "الرجاء إضافة منتج واحد على الأقل");
            return List.of();
        }

        String paymentMethod = "CASH";
        if (SessionManager.getInstance().isSeller()) {
            cashRadio.setSelected(true);
        } else if (creditRadio.isSelected()) {
            paymentMethod = "DEBT";
        }

        Customer selectedCustomer;
        if ("DEBT".equals(paymentMethod)) {
            selectedCustomer = resolveSelectedCustomer();
            if (selectedCustomer == null || selectedCustomer.getId() == null || isDefaultSaleCustomer(selectedCustomer)) {
                updateCustomerSelectionVisibility();
                showError("خطأ", "الرجاء اختيار اسم العميل قبل حفظ بيع الدين");
                return List.of();
            }
            customerComboBox.setValue(selectedCustomer);
        } else {
            selectDefaultSaleCustomer();
            selectedCustomer = customerComboBox.getValue();
            if (selectedCustomer == null || selectedCustomer.getId() == null) {
                showError("خطأ", "تعذر تجهيز عميل البيع الافتراضي");
                return List.of();
            }
        }

        if (!validateAllStockAvailable()) {
            return List.of();
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
                request.setCustomerId(selectedCustomer.getId());
                request.setPaymentMethod(paymentMethod);
                request.setCurrency(saleCurrency);
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
                if ("CASH".equals(paymentMethod)) {
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
        categoryFilterComboBox.getSelectionModel().clearSelection();
        productComboBox.getSelectionModel().clearSelection();
        quantityField.clear();
        stockLabel.setText("");
        priceLabel.setText("");
        saleItems.clear();
        itemsTable.refresh();
        updateSelectedItemsTotal();
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

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
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
