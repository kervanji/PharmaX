package com.pharmax.controller;

import com.pharmax.model.*;
import com.pharmax.service.CustomerService;
import com.pharmax.service.InventoryService;
import com.pharmax.service.VoucherService;
import com.pharmax.util.SessionManager;
import com.pharmax.util.TabManager;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ResourceBundle;
import javafx.stage.Popup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;
import javafx.geometry.Insets;

public class PurchaseController implements Initializable {

    @FXML
    private TextField voucherNumberField;
    @FXML
    private DatePicker voucherDatePicker;
    @FXML
    private ComboBox<Customer> customerCombo;
    @FXML
    private TextField discountPercentField;
    @FXML
    private TextField discountAmountField;
    @FXML
    private TextField descriptionField;
    @FXML
    private TextArea notesArea;
    @FXML
    private Label previousBalanceLabel;
    @FXML
    private Label currentBalanceLabel;
    @FXML
    private Label itemsTotalLabel;
    @FXML
    private Label totalAmountLabel;
    @FXML
    private CheckBox printCheckbox;

    // Items table
    @FXML
    private TableView<PurchaseItemRow> itemsTable;
    @FXML
    private TableColumn<PurchaseItemRow, String> itemProductColumn;
    @FXML
    private TableColumn<PurchaseItemRow, Double> itemQuantityColumn;
    @FXML
    private TableColumn<PurchaseItemRow, String> itemUnitColumn;
    @FXML
    private TableColumn<PurchaseItemRow, Double> itemPriceColumn;
    @FXML
    private TableColumn<PurchaseItemRow, Double> itemSalePriceColumn;
    @FXML
    private TableColumn<PurchaseItemRow, Double> itemTotalColumn;
    @FXML
    private TableColumn<PurchaseItemRow, Void> itemEditColumn;
    @FXML
    private TableColumn<PurchaseItemRow, Void> itemDeleteColumn;

    private final VoucherService voucherService = new VoucherService();
    private final CustomerService customerService = new CustomerService();
    private final InventoryService inventoryService = new InventoryService();
    private final DecimalFormat numberFormat = new DecimalFormat("#,###.##");
    private static final String DEFAULT_CASH_ACCOUNT = "صندوق 181";
    private static final String DEFAULT_CURRENCY = "دينار";

    private ObservableList<Customer> customers;
    private ObservableList<Product> products;
    private Customer selectedCustomer;
    private ObservableList<PurchaseItemRow> itemRows = FXCollections.observableArrayList();

    private boolean tabMode = false;
    private String tabId;
    private Popup historyPopup;

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    public void setTabId(String tabId) {
        this.tabId = tabId;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupForm();
        loadCustomers();
        loadProducts();
        setupItemsTable();
        setupListeners();
        handleNew();
    }

    private void setupForm() {
        voucherDatePicker.setValue(LocalDate.now());

        customerCombo.setConverter(new StringConverter<Customer>() {
            @Override
            public String toString(Customer customer) {
                if (customer == null)
                    return "";
                return customer.getCustomerCode() + " - " + customer.getName();
            }

            @Override
            public Customer fromString(String s) {
                if (s == null || s.isEmpty())
                    return null;
                return customers.stream()
                        .filter(c -> (c.getCustomerCode() + " - " + c.getName()).equals(s) || c.getName().contains(s))
                        .findFirst().orElse(null);
            }
        });
    }

    private void loadCustomers() {
        customers = FXCollections.observableArrayList(customerService.getAllCustomers());
        customerCombo.setItems(customers);
    }

    private void loadProducts() {
        products = FXCollections.observableArrayList(inventoryService.getActiveProducts());
    }

    private void setupItemsTable() {
        itemProductColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProductName()));
        itemQuantityColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getQuantity()).asObject());
        itemUnitColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUnitOfMeasure()));
        itemPriceColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getUnitPrice()).asObject());
        itemSalePriceColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getSalePrice()).asObject());
        itemTotalColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getTotalPrice()).asObject());

        // Inline editing
        itemProductColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        itemProductColumn.setOnEditCommit(ev -> {
            ev.getRowValue().setProductName(ev.getNewValue());
            recalculateTotal();
        });

        itemUnitColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        itemUnitColumn.setOnEditCommit(ev -> ev.getRowValue().setUnitOfMeasure(ev.getNewValue()));

        itemQuantityColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        itemQuantityColumn.setOnEditCommit(ev -> {
            double v = ev.getNewValue() != null ? ev.getNewValue() : 0;
            ev.getRowValue().setQuantity(v > 0 ? v : 1);
            ev.getRowValue().recalcTotal();
            recalculateTotal();
        });

        itemPriceColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        itemPriceColumn.setOnEditCommit(ev -> {
            double v = ev.getNewValue() != null ? ev.getNewValue() : 0;
            ev.getRowValue().setUnitPrice(v);
            ev.getRowValue().recalcTotal();
            recalculateTotal();
        });

        itemSalePriceColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        itemSalePriceColumn.setOnEditCommit(ev -> {
            double v = ev.getNewValue() != null ? ev.getNewValue() : 0;
            ev.getRowValue().setSalePrice(v);
        });

        itemTotalColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        itemTotalColumn.setOnEditCommit(ev -> {
            double v = ev.getNewValue() != null ? ev.getNewValue() : 0;
            ev.getRowValue().setTotalPrice(v);
            recalculateTotal();
        });

        // Edit button column
        itemEditColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("✎");
            {
                editBtn.setStyle(
                        "-fx-background-color: -fx-accent; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 6; -fx-cursor: hand;");
                editBtn.setOnAction(e -> {
                    PurchaseItemRow row = getTableView().getItems().get(getIndex());
                    editItem(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editBtn);
            }
        });

        // Delete button column
        itemDeleteColumn.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("✕");
            {
                deleteBtn.setStyle(
                        "-fx-background-color: -fx-danger; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 6; -fx-cursor: hand;");
                deleteBtn.setOnAction(e -> {
                    PurchaseItemRow row = getTableView().getItems().get(getIndex());
                    itemRows.remove(row);
                    recalculateTotal();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });

        itemsTable.setItems(itemRows);
        itemsTable.setEditable(true);
    }

    private void setupListeners() {
        customerCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            selectedCustomer = newVal;
            updateCustomerBalanceDisplay();
            updateDescription();
            showSmartHistoryPanel();
        });

        discountPercentField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty()) {
                try {
                    double percent = Double.parseDouble(newVal);
                    double total = calculateItemsTotal();
                    double discountAmount = total * percent / 100;
                    discountAmountField.setText(numberFormat.format(discountAmount));
                    recalculateTotal();
                } catch (NumberFormatException ignored) {
                }
            }
        });

        discountAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
            recalculateTotal();
        });

    }

    private void updateCustomerBalanceDisplay() {
        if (selectedCustomer != null) {
            double balance = selectedCustomer.getBalanceIqd();
            previousBalanceLabel.setText(numberFormat.format(balance));
            currentBalanceLabel.setText(numberFormat.format(balance) + " د.ع");
        } else {
            previousBalanceLabel.setText("0");
            currentBalanceLabel.setText("0 د.ع");
        }
    }

    private void updateDescription() {
        if (selectedCustomer != null) {
            descriptionField.setText("مشتريات من .. " + selectedCustomer.getName());
        } else {
            descriptionField.setText("");
        }
    }

    private double calculateItemsTotal() {
        double total = 0;
        for (PurchaseItemRow row : itemRows) {
            total += row.getTotalPrice();
        }
        return total;
    }

    private void showSmartHistoryPanel() {
        if (historyPopup != null && historyPopup.isShowing()) {
            historyPopup.hide();
        }

        if (selectedCustomer == null)
            return;

        List<Voucher> vouchers = voucherService.getVouchersByCustomerAndType(selectedCustomer.getId(),
                VoucherType.PURCHASE);
        if (vouchers == null || vouchers.isEmpty())
            return;

        Map<String, VoucherItem> latestItems = new LinkedHashMap<>();
        Map<String, LocalDate> latestDates = new HashMap<>();

        for (Voucher v : vouchers) {
            if (v.getItems() != null) {
                for (VoucherItem vi : v.getItems()) {
                    String pName = vi.getProductName();
                    if (pName != null && !pName.isEmpty() && !latestItems.containsKey(pName)) {
                        latestItems.put(pName, vi);
                        LocalDate date = v.getVoucherDate() != null ? v.getVoucherDate().toLocalDate()
                                : (v.getCreatedAt() != null ? v.getCreatedAt().toLocalDate() : null);
                        latestDates.put(pName, date);
                    }
                }
            }
        }

        if (latestItems.isEmpty())
            return;

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        root.setPrefWidth(400);
        root.setMaxHeight(300);
        root.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);

        Label titleLbl = new Label("آخر مواد مشتراة من " + selectedCustomer.getName());
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");

        VBox itemsBox = new VBox(8);
        for (Map.Entry<String, VoucherItem> entry : latestItems.entrySet()) {
            String pName = entry.getKey();
            VoucherItem vi = entry.getValue();
            LocalDate date = latestDates.get(pName);

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle(
                    "-fx-padding: 8; -fx-background-color: #f8fafc; -fx-background-radius: 6; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");

            VBox infoBox = new VBox(4);
            Label nameLbl = new Label(pName);
            nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");

            String priceStr = numberFormat.format(vi.getUnitPrice()) + " " + DEFAULT_CURRENCY;
            String dateStr = date != null ? date.toString() : "-";

            Label detailsLbl = new Label("آخر سعر: " + priceStr + " | التاريخ: " + dateStr);
            detailsLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

            infoBox.getChildren().addAll(nameLbl, detailsLbl);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button addBtn = new Button("+");
            addBtn.setStyle(
                    "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand;");
            addBtn.setOnAction(e -> {
                addLatestItemToTable(vi);
                addBtn.setStyle(
                        "-fx-background-color: #22c55e; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand;");
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                        javafx.util.Duration.seconds(0.5));
                pause.setOnFinished(ev -> addBtn.setStyle(
                        "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand;"));
                pause.play();
            });

            row.getChildren().addAll(infoBox, spacer, addBtn);
            itemsBox.getChildren().add(row);
        }

        ScrollPane scrollPane = new ScrollPane(itemsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");

        root.getChildren().addAll(titleLbl, scrollPane);

        historyPopup = new Popup();
        historyPopup.getContent().add(root);
        historyPopup.setAutoHide(true);

        javafx.geometry.Bounds bounds = customerCombo.localToScreen(customerCombo.getBoundsInLocal());
        if (bounds != null) {
            historyPopup.show(customerCombo, bounds.getMinX(), bounds.getMaxY() + 5);
        }
    }

    private void addLatestItemToTable(VoucherItem vi) {
        Product product = vi.getProduct();
        String productName = vi.getProductName();
        double unitPrice = vi.getUnitPrice() != null ? vi.getUnitPrice() : 0.0;
        double qty = 1.0;
        String unit = vi.getUnitOfMeasure() != null ? vi.getUnitOfMeasure() : "";
        double salePrice = (product != null && product.getUnitPrice() != null) ? product.getUnitPrice() : unitPrice;
        double wholesalePrice = (product != null && product.getWholesalePrice() != null) ? product.getWholesalePrice()
                : salePrice;
        double specialPrice = (product != null && product.getSpecialPrice() != null) ? product.getSpecialPrice()
                : salePrice;
        String category = (product != null) ? product.getCategory() : "";

        PurchaseItemRow newRow = new PurchaseItemRow(product, productName, qty, unit, unitPrice, salePrice,
                wholesalePrice, specialPrice, category);
        itemRows.add(newRow);
        recalculateTotal();
    }

    private void recalculateTotal() {
        double itemsTotal = calculateItemsTotal();
        double discount = parseAmount(discountAmountField.getText());
        double netTotal = itemsTotal - discount;
        String currency = DEFAULT_CURRENCY;
        String suffix = "دولار".equals(currency) ? " $" : " د.ع";

        itemsTotalLabel.setText(numberFormat.format(itemsTotal) + suffix);
        totalAmountLabel.setText(numberFormat.format(netTotal) + suffix);

        // Update current balance preview
        if (selectedCustomer != null) {
            double currentBalance = selectedCustomer.getBalanceIqd();
            double newBalance = currentBalance - netTotal;
            currentBalanceLabel.setText(numberFormat.format(newBalance) + suffix);
        }
    }

    @FXML
    private void handleAddItem() {
        Dialog<PurchaseItemRow> dialog = new Dialog<>();
        dialog.setTitle("إضافة مادة");
        dialog.setHeaderText("أضف مادة جديدة للمشتريات");

        // Apply theme and main styles to the dialog pane
        try {
            dialog.getDialogPane().getStylesheets().addAll(
                    getClass().getResource(com.pharmax.util.ThemeManager.getInstance().getCurrentTheme().getCssPath())
                            .toExternalForm(),
                    getClass().getResource("/styles/main.css").toExternalForm());
        } catch (Exception e) {
        }
        dialog.getDialogPane().setStyle("-fx-font-family: 'Geeza Pro', 'SF Arabic', 'Arial', 'Tahoma', sans-serif;");
        dialog.getDialogPane().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);

        ButtonType addButtonType = new ButtonType("إضافة", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Form layout
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        grid.setPadding(new javafx.geometry.Insets(20, 20, 10, 20));

        ComboBox<Product> productCombo = new ComboBox<>(FXCollections.observableArrayList(products));
        productCombo.setEditable(true);
        productCombo.setPrefWidth(250);
        productCombo.setPromptText("اختر المادة أو اكتب اسمها");
        productCombo.setConverter(new StringConverter<Product>() {
            @Override
            public String toString(Product p) {
                if (p == null)
                    return "";
                return p.getProductCode() + " - " + p.getName();
            }

            @Override
            public Product fromString(String s) {
                if (s == null || s.isEmpty())
                    return null;
                return products.stream()
                        .filter(p -> (p.getProductCode() + " - " + p.getName()).equals(s) || p.getName().contains(s))
                        .findFirst().orElse(null);
            }
        });

        TextField quantityField = new TextField("1");
        quantityField.setPrefWidth(80);

        TextField unitField = new TextField();
        unitField.setPrefWidth(80);
        unitField.setPromptText("(اختياري)");

        TextField priceField = new TextField("0");
        priceField.setPrefWidth(120);

        TextField salePriceField = new TextField("0");
        salePriceField.setPrefWidth(120);

        TextField wholesalePriceField = new TextField("0");
        wholesalePriceField.setPrefWidth(120);

        TextField specialPriceField = new TextField("0");
        specialPriceField.setPrefWidth(120);

        Label marginSaleLabel = new Label("");
        marginSaleLabel.setStyle("-fx-text-fill: -fx-badge-success-text; -fx-font-weight: bold;");

        Label marginWholesaleLabel = new Label("");
        marginWholesaleLabel.setStyle("-fx-text-fill: -fx-badge-success-text; -fx-font-weight: bold;");

        Label marginSpecialLabel = new Label("");
        marginSpecialLabel.setStyle("-fx-text-fill: -fx-badge-success-text; -fx-font-weight: bold;");

        ComboBox<String> categoryCombo = new ComboBox<>(
                FXCollections.observableArrayList(inventoryService.getAllCategories()));
        categoryCombo.setEditable(true);
        categoryCombo.setPrefWidth(250);
        categoryCombo.setPromptText("اختر الفئة");

        ComboBox<String> currencyCombo = new ComboBox<>(FXCollections.observableArrayList("دينار", "دولار"));
        currencyCombo.setValue("دينار");
        currencyCombo.setPrefWidth(250);

        // Auto-fill from product selection
        productCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                boolean isUsdCurrency = "دولار"
                        .equals(currencyCombo != null ? currencyCombo.getValue() : DEFAULT_CURRENCY);
                if (isUsdCurrency) {
                    if (newVal.getCostPriceUsd() != null && newVal.getCostPriceUsd() > 0) {
                        priceField.setText(String.valueOf(newVal.getCostPriceUsd()));
                    } else if (newVal.getUnitPriceUsd() != null) {
                        priceField.setText(String.valueOf(newVal.getUnitPriceUsd()));
                    }
                    if (newVal.getUnitPriceUsd() != null) {
                        salePriceField.setText(String.valueOf(newVal.getUnitPriceUsd()));
                    }
                    if (newVal.getWholesalePriceUsd() != null) {
                        wholesalePriceField.setText(String.valueOf(newVal.getWholesalePriceUsd()));
                    }
                    if (newVal.getSpecialPriceUsd() != null) {
                        specialPriceField.setText(String.valueOf(newVal.getSpecialPriceUsd()));
                    }
                } else {
                    if (newVal.getCostPrice() != null && newVal.getCostPrice() > 0) {
                        priceField.setText(String.valueOf(newVal.getCostPrice()));
                    } else if (newVal.getUnitPrice() != null) {
                        priceField.setText(String.valueOf(newVal.getUnitPrice()));
                    }
                    if (newVal.getUnitPrice() != null) {
                        salePriceField.setText(String.valueOf(newVal.getUnitPrice()));
                    }
                    if (newVal.getWholesalePrice() != null) {
                        wholesalePriceField.setText(String.valueOf(newVal.getWholesalePrice()));
                    }
                    if (newVal.getSpecialPrice() != null) {
                        specialPriceField.setText(String.valueOf(newVal.getSpecialPrice()));
                    }
                }
                if (newVal.getUnitOfMeasure() != null) {
                    unitField.setText(newVal.getUnitOfMeasure());
                }
                if (newVal.getCategory() != null && !newVal.getCategory().isEmpty()) {
                    categoryCombo.setValue(newVal.getCategory());
                }
            }
        });

        currencyCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            Product selectedGroup = productCombo.getValue();
            if (selectedGroup != null) {
                boolean isUsdCurrency = "دولار".equals(newVal);
                if (isUsdCurrency) {
                    if (selectedGroup.getCostPriceUsd() != null && selectedGroup.getCostPriceUsd() > 0) {
                        priceField.setText(String.valueOf(selectedGroup.getCostPriceUsd()));
                    } else if (selectedGroup.getUnitPriceUsd() != null) {
                        priceField.setText(String.valueOf(selectedGroup.getUnitPriceUsd()));
                    }
                    if (selectedGroup.getUnitPriceUsd() != null) {
                        salePriceField.setText(String.valueOf(selectedGroup.getUnitPriceUsd()));
                    }
                    if (selectedGroup.getWholesalePriceUsd() != null) {
                        wholesalePriceField.setText(String.valueOf(selectedGroup.getWholesalePriceUsd()));
                    }
                    if (selectedGroup.getSpecialPriceUsd() != null) {
                        specialPriceField.setText(String.valueOf(selectedGroup.getSpecialPriceUsd()));
                    }
                } else {
                    if (selectedGroup.getCostPrice() != null && selectedGroup.getCostPrice() > 0) {
                        priceField.setText(String.valueOf(selectedGroup.getCostPrice()));
                    } else if (selectedGroup.getUnitPrice() != null) {
                        priceField.setText(String.valueOf(selectedGroup.getUnitPrice()));
                    }
                    if (selectedGroup.getUnitPrice() != null) {
                        salePriceField.setText(String.valueOf(selectedGroup.getUnitPrice()));
                    }
                    if (selectedGroup.getWholesalePrice() != null) {
                        wholesalePriceField.setText(String.valueOf(selectedGroup.getWholesalePrice()));
                    }
                    if (selectedGroup.getSpecialPrice() != null) {
                        specialPriceField.setText(String.valueOf(selectedGroup.getSpecialPrice()));
                    }
                }
            }
        });

        // margin update
        Runnable updateMargin = () -> {
            double cost = parseAmount(priceField.getText());
            if (cost <= 0) {
                marginSaleLabel.setText("");
                marginWholesaleLabel.setText("");
                marginSpecialLabel.setText("");
                return;
            }

            double sale = parseAmount(salePriceField.getText());
            if (sale > 0) {
                double margin = (sale - cost) / cost * 100.0;
                marginSaleLabel.setText(String.format("هامش الربح: %.2f%%", margin));
            } else {
                marginSaleLabel.setText("");
            }

            double wholesale = parseAmount(wholesalePriceField.getText());
            if (wholesale > 0) {
                double margin = (wholesale - cost) / cost * 100.0;
                marginWholesaleLabel.setText(String.format("هامش الربح: %.2f%%", margin));
            } else {
                marginWholesaleLabel.setText("");
            }

            double special = parseAmount(specialPriceField.getText());
            if (special > 0) {
                double margin = (special - cost) / cost * 100.0;
                marginSpecialLabel.setText(String.format("هامش الربح: %.2f%%", margin));
            } else {
                marginSpecialLabel.setText("");
            }
        };
        priceField.textProperty().addListener((o, a, b) -> updateMargin.run());
        salePriceField.textProperty().addListener((o, a, b) -> updateMargin.run());
        wholesalePriceField.textProperty().addListener((o, a, b) -> updateMargin.run());
        specialPriceField.textProperty().addListener((o, a, b) -> updateMargin.run());

        grid.add(new Label("المادة"), 0, 0);
        grid.add(productCombo, 1, 0);
        grid.add(new Label("الفئة"), 0, 1);
        grid.add(categoryCombo, 1, 1);
        grid.add(new Label("الكمية"), 0, 2);
        grid.add(quantityField, 1, 2);
        grid.add(new Label("الوحدة"), 0, 3);
        grid.add(unitField, 1, 3);
        grid.add(new Label("العملة"), 0, 4);
        grid.add(currencyCombo, 1, 4);
        grid.add(new Label("سعر الوحدة"), 0, 5);
        grid.add(priceField, 1, 5);
        grid.add(new Label("سعر البيع (مفرد)"), 0, 6);
        grid.add(salePriceField, 1, 6);
        grid.add(marginSaleLabel, 2, 6);
        grid.add(new Label("سعر الجملة"), 0, 7);
        grid.add(wholesalePriceField, 1, 7);
        grid.add(marginWholesaleLabel, 2, 7);
        grid.add(new Label("سعر خاص"), 0, 8);
        grid.add(specialPriceField, 1, 8);
        grid.add(marginSpecialLabel, 2, 8);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(550);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                Product selectedProduct = productCombo.getValue();
                String productName;
                if (selectedProduct != null) {
                    productName = selectedProduct.getName();
                } else {
                    // Allow manual product name entry
                    String editorText = productCombo.getEditor().getText();
                    if (editorText == null || editorText.trim().isEmpty()) {
                        return null;
                    }
                    productName = editorText.trim();
                }

                double qty = parseAmount(quantityField.getText());
                if (qty <= 0)
                    qty = 1;
                double price = parseAmount(priceField.getText());
                double salePrice = parseAmount(salePriceField.getText());
                double wholesalePrice = parseAmount(wholesalePriceField.getText());
                double specialPrice = parseAmount(specialPriceField.getText());
                String unit = unitField.getText() != null ? unitField.getText().trim() : "";
                String category = categoryCombo.getValue() != null ? categoryCombo.getValue().trim()
                        : (categoryCombo.getEditor().getText() != null ? categoryCombo.getEditor().getText().trim()
                                : "");
                String cur = currencyCombo.getValue() != null ? currencyCombo.getValue() : "دينار";
                PurchaseItemRow row = new PurchaseItemRow(
                        selectedProduct, productName, qty, unit, price, salePrice, wholesalePrice, specialPrice,
                        category, cur);
                return row;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(row -> {
            itemRows.add(row);
            recalculateTotal();
        });
    }

    private void editItem(PurchaseItemRow existingRow) {
        Dialog<PurchaseItemRow> dialog = new Dialog<>();
        dialog.setTitle("تعديل مادة");
        dialog.setHeaderText("تعديل تفاصيل المادة");

        try {
            dialog.getDialogPane().getStylesheets().addAll(
                    getClass().getResource(com.pharmax.util.ThemeManager.getInstance().getCurrentTheme().getCssPath())
                            .toExternalForm(),
                    getClass().getResource("/styles/main.css").toExternalForm());
        } catch (Exception e) {
        }
        dialog.getDialogPane().setStyle("-fx-font-family: 'Geeza Pro', 'SF Arabic', 'Arial', 'Tahoma', sans-serif;");
        dialog.getDialogPane().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);

        ButtonType addButtonType = new ButtonType("حفظ تعديل", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        grid.setPadding(new javafx.geometry.Insets(20, 20, 10, 20));

        ComboBox<Product> productCombo = new ComboBox<>(FXCollections.observableArrayList(products));
        productCombo.setEditable(true);
        productCombo.setPrefWidth(250);

        productCombo.setConverter(new StringConverter<Product>() {
            @Override
            public String toString(Product p) {
                if (p == null)
                    return "";
                return p.getProductCode() + " - " + p.getName();
            }

            @Override
            public Product fromString(String s) {
                if (s == null || s.isEmpty())
                    return null;
                return products.stream()
                        .filter(p -> (p.getProductCode() + " - " + p.getName()).equals(s) || p.getName().contains(s))
                        .findFirst().orElse(null);
            }
        });

        if (existingRow.getProduct() != null) {
            productCombo.setValue(existingRow.getProduct());
        } else {
            productCombo.getEditor().setText(existingRow.getProductName());
        }

        TextField quantityField = new TextField(String.valueOf(existingRow.getQuantity()));
        quantityField.setPrefWidth(80);

        TextField unitField = new TextField(
                existingRow.getUnitOfMeasure() != null ? existingRow.getUnitOfMeasure() : "");
        unitField.setPrefWidth(80);

        TextField priceField = new TextField(String.valueOf(existingRow.getUnitPrice()));
        priceField.setPrefWidth(120);

        TextField salePriceField = new TextField(String.valueOf(existingRow.getSalePrice()));
        salePriceField.setPrefWidth(120);

        TextField wholesalePriceField = new TextField(String.valueOf(existingRow.getWholesalePrice()));
        wholesalePriceField.setPrefWidth(120);

        TextField specialPriceField = new TextField(String.valueOf(existingRow.getSpecialPrice()));
        specialPriceField.setPrefWidth(120);

        Label marginSaleLabel = new Label("");
        marginSaleLabel.setStyle("-fx-text-fill: -fx-badge-success-text; -fx-font-weight: bold;");

        Label marginWholesaleLabel = new Label("");
        marginWholesaleLabel.setStyle("-fx-text-fill: -fx-badge-success-text; -fx-font-weight: bold;");

        Label marginSpecialLabel = new Label("");
        marginSpecialLabel.setStyle("-fx-text-fill: -fx-badge-success-text; -fx-font-weight: bold;");

        ComboBox<String> categoryCombo = new ComboBox<>(
                FXCollections.observableArrayList(inventoryService.getAllCategories()));
        categoryCombo.setEditable(true);
        categoryCombo.setPrefWidth(250);
        if (existingRow.getCategory() != null && !existingRow.getCategory().isEmpty()) {
            categoryCombo.setValue(existingRow.getCategory());
        }

        Runnable updateMargin = () -> {
            double cost = parseAmount(priceField.getText());
            if (cost <= 0) {
                marginSaleLabel.setText("");
                marginWholesaleLabel.setText("");
                marginSpecialLabel.setText("");
                return;
            }

            double sale = parseAmount(salePriceField.getText());
            if (sale > 0) {
                double margin = (sale - cost) / cost * 100.0;
                marginSaleLabel.setText(String.format("هامش الربح: %.2f%%", margin));
            } else {
                marginSaleLabel.setText("");
            }

            double wholesale = parseAmount(wholesalePriceField.getText());
            if (wholesale > 0) {
                double margin = (wholesale - cost) / cost * 100.0;
                marginWholesaleLabel.setText(String.format("هامش الربح: %.2f%%", margin));
            } else {
                marginWholesaleLabel.setText("");
            }

            double special = parseAmount(specialPriceField.getText());
            if (special > 0) {
                double margin = (special - cost) / cost * 100.0;
                marginSpecialLabel.setText(String.format("هامش الربح: %.2f%%", margin));
            } else {
                marginSpecialLabel.setText("");
            }
        };

        priceField.textProperty().addListener((o, a, b) -> updateMargin.run());
        salePriceField.textProperty().addListener((o, a, b) -> updateMargin.run());
        wholesalePriceField.textProperty().addListener((o, a, b) -> updateMargin.run());
        specialPriceField.textProperty().addListener((o, a, b) -> updateMargin.run());
        updateMargin.run();

        ComboBox<String> currencyCombo = new ComboBox<>(FXCollections.observableArrayList("دينار", "دولار"));
        currencyCombo.setValue(existingRow.getCurrency() != null ? existingRow.getCurrency() : "دينار");
        currencyCombo.setPrefWidth(250);

        productCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                boolean isUsdCurrency = "دولار"
                        .equals(currencyCombo != null ? currencyCombo.getValue() : DEFAULT_CURRENCY);
                if (isUsdCurrency) {
                    if (newVal.getCostPriceUsd() != null && newVal.getCostPriceUsd() > 0) {
                        priceField.setText(String.valueOf(newVal.getCostPriceUsd()));
                    } else if (newVal.getUnitPriceUsd() != null) {
                        priceField.setText(String.valueOf(newVal.getUnitPriceUsd()));
                    }
                    if (newVal.getUnitPriceUsd() != null) {
                        salePriceField.setText(String.valueOf(newVal.getUnitPriceUsd()));
                    }
                    if (newVal.getWholesalePriceUsd() != null) {
                        wholesalePriceField.setText(String.valueOf(newVal.getWholesalePriceUsd()));
                    }
                    if (newVal.getSpecialPriceUsd() != null) {
                        specialPriceField.setText(String.valueOf(newVal.getSpecialPriceUsd()));
                    }
                } else {
                    if (newVal.getCostPrice() != null && newVal.getCostPrice() > 0) {
                        priceField.setText(String.valueOf(newVal.getCostPrice()));
                    } else if (newVal.getUnitPrice() != null) {
                        priceField.setText(String.valueOf(newVal.getUnitPrice()));
                    }
                    if (newVal.getUnitPrice() != null) {
                        salePriceField.setText(String.valueOf(newVal.getUnitPrice()));
                    }
                    if (newVal.getWholesalePrice() != null) {
                        wholesalePriceField.setText(String.valueOf(newVal.getWholesalePrice()));
                    }
                    if (newVal.getSpecialPrice() != null) {
                        specialPriceField.setText(String.valueOf(newVal.getSpecialPrice()));
                    }
                }
                if (newVal.getUnitOfMeasure() != null) {
                    unitField.setText(newVal.getUnitOfMeasure());
                }
                if (newVal.getCategory() != null && !newVal.getCategory().isEmpty()) {
                    categoryCombo.setValue(newVal.getCategory());
                }
            }
        });

        currencyCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            Product selectedGroup = productCombo.getValue();
            if (selectedGroup != null) {
                boolean isUsdCurrency = "دولار".equals(newVal);
                if (isUsdCurrency) {
                    if (selectedGroup.getCostPriceUsd() != null && selectedGroup.getCostPriceUsd() > 0) {
                        priceField.setText(String.valueOf(selectedGroup.getCostPriceUsd()));
                    } else if (selectedGroup.getUnitPriceUsd() != null) {
                        priceField.setText(String.valueOf(selectedGroup.getUnitPriceUsd()));
                    }
                    if (selectedGroup.getUnitPriceUsd() != null) {
                        salePriceField.setText(String.valueOf(selectedGroup.getUnitPriceUsd()));
                    }
                    if (selectedGroup.getWholesalePriceUsd() != null) {
                        wholesalePriceField.setText(String.valueOf(selectedGroup.getWholesalePriceUsd()));
                    }
                    if (selectedGroup.getSpecialPriceUsd() != null) {
                        specialPriceField.setText(String.valueOf(selectedGroup.getSpecialPriceUsd()));
                    }
                } else {
                    if (selectedGroup.getCostPrice() != null && selectedGroup.getCostPrice() > 0) {
                        priceField.setText(String.valueOf(selectedGroup.getCostPrice()));
                    } else if (selectedGroup.getUnitPrice() != null) {
                        priceField.setText(String.valueOf(selectedGroup.getUnitPrice()));
                    }
                    if (selectedGroup.getUnitPrice() != null) {
                        salePriceField.setText(String.valueOf(selectedGroup.getUnitPrice()));
                    }
                    if (selectedGroup.getWholesalePrice() != null) {
                        wholesalePriceField.setText(String.valueOf(selectedGroup.getWholesalePrice()));
                    }
                    if (selectedGroup.getSpecialPrice() != null) {
                        specialPriceField.setText(String.valueOf(selectedGroup.getSpecialPrice()));
                    }
                }
            }
        });

        grid.add(new Label("المادة"), 0, 0);
        grid.add(productCombo, 1, 0);
        grid.add(new Label("الفئة"), 0, 1);
        grid.add(categoryCombo, 1, 1);
        grid.add(new Label("الكمية"), 0, 2);
        grid.add(quantityField, 1, 2);
        grid.add(new Label("الوحدة"), 0, 3);
        grid.add(unitField, 1, 3);
        grid.add(new Label("العملة"), 0, 4);
        grid.add(currencyCombo, 1, 4);
        grid.add(new Label("سعر الوحدة"), 0, 5);
        grid.add(priceField, 1, 5);
        grid.add(new Label("سعر البيع (مفرد)"), 0, 6);
        grid.add(salePriceField, 1, 6);
        grid.add(marginSaleLabel, 2, 6);
        grid.add(new Label("سعر الجملة"), 0, 7);
        grid.add(wholesalePriceField, 1, 7);
        grid.add(marginWholesaleLabel, 2, 7);
        grid.add(new Label("سعر خاص"), 0, 8);
        grid.add(specialPriceField, 1, 8);
        grid.add(marginSpecialLabel, 2, 8);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(550);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                Product selectedProduct = productCombo.getValue();
                String productName;
                if (selectedProduct != null) {
                    productName = selectedProduct.getName();
                } else {
                    String editorText = productCombo.getEditor().getText();
                    if (editorText == null || editorText.trim().isEmpty()) {
                        return null;
                    }
                    productName = editorText.trim();
                }

                double qty = parseAmount(quantityField.getText());
                if (qty <= 0)
                    qty = 1;
                double price = parseAmount(priceField.getText());
                double salePrice = parseAmount(salePriceField.getText());
                double wholesalePrice = parseAmount(wholesalePriceField.getText());
                double specialPrice = parseAmount(specialPriceField.getText());
                String unit = unitField.getText() != null ? unitField.getText().trim() : "";
                String category = categoryCombo.getValue() != null ? categoryCombo.getValue().trim()
                        : (categoryCombo.getEditor().getText() != null ? categoryCombo.getEditor().getText().trim()
                                : "");

                existingRow.product = selectedProduct;
                existingRow.setProductName(productName);
                existingRow.setQuantity(qty);
                existingRow.setUnitOfMeasure(unit);
                existingRow.setUnitPrice(price);
                existingRow.setSalePrice(salePrice);
                existingRow.setWholesalePrice(wholesalePrice);
                existingRow.setSpecialPrice(specialPrice);
                existingRow.category = category;
                existingRow.setCurrency(currencyCombo.getValue() != null ? currencyCombo.getValue() : "دينار");
                existingRow.recalcTotal();

                return existingRow;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(row -> {
            itemsTable.refresh();
            recalculateTotal();
        });
    }

    @FXML
    private void handleSave() {
        try {
            if (selectedCustomer == null) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار المورد/الحساب");
                return;
            }

            if (itemRows.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى إضافة مادة واحدة على الأقل");
                return;
            }

            double itemsTotal = calculateItemsTotal();
            double discount = parseAmount(discountAmountField.getText());
            double netAmount = itemsTotal - discount;

            if (netAmount <= 0) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "المبلغ الصافي يجب أن يكون أكبر من صفر");
                return;
            }

            String selectedCurrency = DEFAULT_CURRENCY;
            double exchangeRate = 1.0;

            // Create voucher
            Voucher voucher = new Voucher();
            voucher.setVoucherType(VoucherType.PURCHASE);
            voucher.setVoucherNumber(voucherNumberField.getText());
            voucher.setVoucherDate(voucherDatePicker.getValue().atStartOfDay());
            voucher.setCurrency(selectedCurrency);
            voucher.setExchangeRate(exchangeRate);
            voucher.setCustomer(selectedCustomer);
            voucher.setCashAccount(DEFAULT_CASH_ACCOUNT);
            voucher.setAmount(itemsTotal);
            voucher.setDiscountPercentage(parseAmount(discountPercentField.getText()));
            voucher.setDiscountAmount(discount);
            voucher.setNetAmount(netAmount);
            voucher.setDescription(descriptionField.getText());
            if (notesArea != null && notesArea.getText() != null && !notesArea.getText().isEmpty()) {
                voucher.setNotes(notesArea.getText());
            }
            voucher.setCreatedBy(SessionManager.getInstance().getCurrentUser() != null
                    ? SessionManager.getInstance().getCurrentUser().getDisplayName()
                    : "System");

            // Add items - create new products if needed
            for (PurchaseItemRow row : itemRows) {
                Product product = row.getProduct();
                boolean isUsdCurrency = "دولار".equals(row.getCurrency());

                // If product doesn't exist, create it
                if (product == null) {
                    Product newProduct = new Product();
                    newProduct.setName(row.getProductName());
                    newProduct.setCategory(row.getCategory());
                    newProduct.setUnitOfMeasure(row.getUnitOfMeasure());
                    if (isUsdCurrency) {
                        newProduct.setCostPriceUsd(row.getUnitPrice());
                        newProduct.setUnitPriceUsd(row.getSalePrice() > 0 ? row.getSalePrice() : row.getUnitPrice());
                        newProduct.setWholesalePriceUsd(
                                row.getWholesalePrice() > 0 ? row.getWholesalePrice() : newProduct.getUnitPriceUsd());
                        newProduct.setSpecialPriceUsd(
                                row.getSpecialPrice() > 0 ? row.getSpecialPrice() : newProduct.getUnitPriceUsd());
                    } else {
                        newProduct.setCostPrice(row.getUnitPrice());
                        newProduct.setUnitPrice(row.getSalePrice() > 0 ? row.getSalePrice() : row.getUnitPrice());
                        newProduct.setWholesalePrice(
                                row.getWholesalePrice() > 0 ? row.getWholesalePrice() : newProduct.getUnitPrice());
                        newProduct.setSpecialPrice(
                                row.getSpecialPrice() > 0 ? row.getSpecialPrice() : newProduct.getUnitPrice());
                    }
                    newProduct.setQuantityInStock(0.0);
                    newProduct.setMinimumStock(0.0);
                    newProduct.setIsActive(true);
                    product = inventoryService.createProduct(newProduct);
                } else {
                    boolean needsUpdate = false;
                    if (isUsdCurrency) {
                        if (row.getSalePrice() > 0 && (product.getUnitPriceUsd() == null
                                || Double.compare(product.getUnitPriceUsd(), row.getSalePrice()) != 0)) {
                            product.setUnitPriceUsd(row.getSalePrice());
                            needsUpdate = true;
                        }
                        if (row.getWholesalePrice() > 0 && (product.getWholesalePriceUsd() == null
                                || Double.compare(product.getWholesalePriceUsd(), row.getWholesalePrice()) != 0)) {
                            product.setWholesalePriceUsd(row.getWholesalePrice());
                            needsUpdate = true;
                        }
                        if (row.getSpecialPrice() > 0 && (product.getSpecialPriceUsd() == null
                                || Double.compare(product.getSpecialPriceUsd(), row.getSpecialPrice()) != 0)) {
                            product.setSpecialPriceUsd(row.getSpecialPrice());
                            needsUpdate = true;
                        }
                        if (row.getUnitPrice() > 0 && (product.getCostPriceUsd() == null
                                || Double.compare(product.getCostPriceUsd(), row.getUnitPrice()) != 0)) {
                            product.setCostPriceUsd(row.getUnitPrice());
                            needsUpdate = true;
                        }
                    } else {
                        if (row.getSalePrice() > 0 && (product.getUnitPrice() == null
                                || Double.compare(product.getUnitPrice(), row.getSalePrice()) != 0)) {
                            product.setUnitPrice(row.getSalePrice());
                            needsUpdate = true;
                        }
                        if (row.getWholesalePrice() > 0 && (product.getWholesalePrice() == null
                                || Double.compare(product.getWholesalePrice(), row.getWholesalePrice()) != 0)) {
                            product.setWholesalePrice(row.getWholesalePrice());
                            needsUpdate = true;
                        }
                        if (row.getSpecialPrice() > 0 && (product.getSpecialPrice() == null
                                || Double.compare(product.getSpecialPrice(), row.getSpecialPrice()) != 0)) {
                            product.setSpecialPrice(row.getSpecialPrice());
                            needsUpdate = true;
                        }
                        if (row.getUnitPrice() > 0 && (product.getCostPrice() == null
                                || Double.compare(product.getCostPrice(), row.getUnitPrice()) != 0)) {
                            product.setCostPrice(row.getUnitPrice());
                            needsUpdate = true;
                        }
                    }
                    if (needsUpdate) {
                        product = inventoryService.updateProduct(product);
                    }
                }

                VoucherItem item = new VoucherItem();
                item.setProduct(product);
                item.setProductName(row.getProductName());
                item.setQuantity(row.getQuantity());
                item.setUnitPrice(row.getUnitPrice());
                item.setTotalPrice(row.getTotalPrice());
                item.setUnitOfMeasure(row.getUnitOfMeasure());
                item.setAddToInventory(true);
                voucher.addItem(item);
            }

            voucher = voucherService.saveVoucher(voucher);

            showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم حفظ المشتريات بنجاح: " + voucher.getVoucherNumber());

            if (printCheckbox.isSelected()) {
                File pdfFile = voucherService.generateVoucherReceiptPdf(voucher.getId(),
                        SessionManager.getInstance().getCurrentDisplayName());
                if (pdfFile != null && pdfFile.exists()) {
                    showPdfPreview(pdfFile);
                }
            }

            handleNew();
            loadCustomers();
            loadProducts();

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")
                    && e.getMessage().contains("voucher_number")) {
                voucherNumberField.setText(voucherService.generateVoucherNumber(VoucherType.PURCHASE));
            }
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في حفظ المشتريات: " + e.getMessage());
        }
    }

    private void showPdfPreview(File pdfFile) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/views/PdfPreview.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("معاينة الطباعة");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(voucherNumberField.getScene().getWindow());
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            PdfPreviewController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setPdfFile(pdfFile);

            dialogStage.showAndWait();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في عرض معاينة الطباعة: " + e.getMessage());
        }
    }

    @FXML
    private void handleNew() {
        voucherNumberField.setText(voucherService.generateVoucherNumber(VoucherType.PURCHASE));
        voucherDatePicker.setValue(LocalDate.now());
        customerCombo.setValue(null);
        discountPercentField.setText("0");
        discountAmountField.setText("0");
        descriptionField.setText("");
        if (notesArea != null) {
            notesArea.setText("");
        }
        printCheckbox.setSelected(false);
        itemRows.clear();
        itemsTotalLabel.setText("0");
        totalAmountLabel.setText("0");

        selectedCustomer = null;
        updateCustomerBalanceDisplay();
    }

    @FXML
    private void handleClose() {
        if (tabMode && tabId != null && !tabId.isBlank()) {
            TabManager.getInstance().closeTab(tabId);
            return;
        }
        Stage stage = (Stage) voucherNumberField.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void addNewCustomer() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/CustomerForm.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("إضافة مورد/حساب جديد");
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            loadCustomers();

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في فتح نافذة إضافة الحساب");
        }
    }

    @FXML
    private void showPreviousPurchases() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/VoucherList.fxml"));
            Parent root = loader.load();

            VoucherListController controller = loader.getController();
            controller.setVoucherType(VoucherType.PURCHASE);

            Stage stage = new Stage();
            stage.setTitle("المشتريات السابقة");
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في فتح قائمة المشتريات");
        }
    }

    private double parseAmount(String text) {
        if (text == null || text.isEmpty())
            return 0;
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ========== Inner class for table row ==========
    public static class PurchaseItemRow {
        private Product product;
        private String productName;
        private double quantity;
        private String unitOfMeasure;
        private double unitPrice;
        private double salePrice;
        private double wholesalePrice;
        private double specialPrice;
        private double totalPrice;
        private String category;
        private String currency = "دينار";

        public PurchaseItemRow(Product product, String productName, double quantity,
                String unitOfMeasure, double unitPrice, double salePrice,
                double wholesalePrice, double specialPrice, String category, String currency) {
            this.product = product;
            this.productName = productName;
            this.quantity = quantity;
            this.unitOfMeasure = unitOfMeasure;
            this.unitPrice = unitPrice;
            this.salePrice = salePrice;
            this.wholesalePrice = wholesalePrice;
            this.specialPrice = specialPrice;
            this.totalPrice = quantity * unitPrice;
            this.category = category;
            this.currency = currency != null ? currency : "دينار";
        }

        public PurchaseItemRow(Product product, String productName, double quantity,
                String unitOfMeasure, double unitPrice, double salePrice,
                double wholesalePrice, double specialPrice, String category) {
            this(product, productName, quantity, unitOfMeasure, unitPrice, salePrice,
                    wholesalePrice, specialPrice, category, "دينار");
        }

        public void setProductName(String name) {
            this.productName = name;
        }

        public void setQuantity(double q) {
            this.quantity = q;
        }

        public void setUnitOfMeasure(String unit) {
            this.unitOfMeasure = unit;
        }

        public void setUnitPrice(double price) {
            this.unitPrice = price;
        }

        public void setSalePrice(double price) {
            this.salePrice = price;
        }

        public void setWholesalePrice(double price) {
            this.wholesalePrice = price;
        }

        public void setSpecialPrice(double price) {
            this.specialPrice = price;
        }

        public void setTotalPrice(double total) {
            this.totalPrice = total;
        }

        public void recalcTotal() {
            this.totalPrice = this.quantity * this.unitPrice;
        }

        public Product getProduct() {
            return product;
        }

        public String getProductName() {
            return productName;
        }

        public double getQuantity() {
            return quantity;
        }

        public String getUnitOfMeasure() {
            return unitOfMeasure;
        }

        public double getUnitPrice() {
            return unitPrice;
        }

        public double getSalePrice() {
            return salePrice;
        }

        public double getWholesalePrice() {
            return wholesalePrice;
        }

        public double getSpecialPrice() {
            return specialPrice;
        }

        public double getTotalPrice() {
            return totalPrice;
        }

        public String getCategory() {
            return category;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String c) {
            this.currency = c;
        }
    }
}
