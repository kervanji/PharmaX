package com.pharmax.controller;

import com.pharmax.MainApp;
import com.pharmax.model.*;
import com.pharmax.model.dto.StatementItem;
import com.pharmax.service.*;
import com.pharmax.util.SessionManager;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AccountsController {
    private static final Logger logger = LoggerFactory.getLogger(AccountsController.class);
    private static final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ===== Statement =====
    @FXML
    private ComboBox<Customer> customerCombo;
    @FXML
    private ComboBox<String> projectCombo;
    @FXML
    private ComboBox<String> currencyCombo;
    @FXML
    private DatePicker fromDate;
    @FXML
    private DatePicker toDate;
    @FXML
    private Button generateBtn;
    @FXML
    private Button printBtn;

    @FXML
    private TableView<StatementItem> statementTable;
    @FXML
    private TableColumn<StatementItem, String> colDate;
    @FXML
    private TableColumn<StatementItem, String> colType;
    @FXML
    private TableColumn<StatementItem, String> colRef;
    @FXML
    private TableColumn<StatementItem, String> colDesc;
    @FXML
    private TableColumn<StatementItem, Double> colDebit;
    @FXML
    private TableColumn<StatementItem, Double> colCredit;
    @FXML
    private TableColumn<StatementItem, Double> colBalance;
    @FXML
    private TableColumn<StatementItem, Void> colActions;

    @FXML
    private Label totalDebitLabel;
    @FXML
    private Label totalCreditLabel;
    @FXML
    private Label finalBalanceLabel;
    @FXML
    private Label totalCountLabel;

    @FXML
    private TableColumn<StatementItem, String> colPayStatus;
    @FXML
    private Label pendingTotalLabel;
    @FXML
    private Label pendingCountLabel;
    @FXML
    private CheckBox showDetailsCheck;

    private final StatementService statementService = new StatementService();
    private final CustomerService customerService = new CustomerService();
    private final ReceiptService receiptService = new ReceiptService();
    private final VoucherService voucherService = new VoucherService();
    private final SalesService salesService = new SalesService();
    private final ReturnService returnService = new ReturnService();
    private final AuthService authService = new AuthService();

    private MainApp mainApp;
    private boolean tabMode = false;
    private String tabId;
    private ObservableList<Customer> customers = FXCollections.observableArrayList();

    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
    }

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    public void setTabId(String tabId) {
        this.tabId = tabId;
    }

    public void selectCustomerAndGenerate(Customer customer) {
        if (customer == null)
            return;
        javafx.application.Platform.runLater(() -> {
            customerCombo.setValue(customer);
            generateStatement();
        });
    }

    @FXML
    public void initialize() {
        setupCombos();
        setupTable();
        generateBtn.setOnAction(e -> generateStatement());
        printBtn.setOnAction(e -> printStatement());
        // Re-generate statement and adjust layout whenever the details checkbox is
        // toggled
        showDetailsCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            applyDetailsTableLayout(newVal);
            if (!statementTable.getItems().isEmpty()) {
                generateStatement();
            }
        });
    }

    /**
     * Dynamically reshapes the table when switching to/from details mode.
     *
     * Details mode column layout:
     * النوع(hidden/empty for detail) | رقم الفاتورة | اسم المادة | الكمية |
     * س.الوحدة | المجموع | الرصيد | الإجراءات
     *
     * colDate → hidden
     * colType → "النوع" : parent=type label, detail=empty
     * colRef → "رقم الفاتورة" : parent=invoice#, detail=empty
     * colDesc → "اسم المادة" : parent=notes, detail=productName (colored)
     * colPayStatus → "الكمية" : parent=empty, detail=qty
     * colDebit → "س. الوحدة" : parent=debit amount, detail=unitPrice
     * colCredit → "المجموع" : parent=credit amount, detail=itemTotal
     * colBalance → "الرصيد" : parent=running balance, detail=empty (kept visible)
     * colActions → unchanged
     */
    private void applyDetailsTableLayout(boolean showDetails) {
        // Reset all columns to visible first
        for (TableColumn<StatementItem, ?> col : java.util.List.of(
                colDate, colType, colRef, colDesc, colDebit, colCredit, colBalance, colPayStatus, colActions)) {
            col.setVisible(true);
        }

        if (showDetails) {
            // Details mode: hide date, reorder, rename columns
            statementTable.getColumns().setAll(colDate, colType, colRef, colDesc, colPayStatus, colDebit, colCredit,
                    colBalance, colActions);
            colDate.setVisible(false);
            colRef.setText("رقم الفاتورة");
            colDesc.setText("اسم المادة");
            colPayStatus.setText("الكمية");
            colDebit.setText("س. الوحدة");
            colCredit.setText("المجموع");
        } else {
            // Normal mode: show all, restore text
            statementTable.getColumns().setAll(colDate, colType, colRef, colDesc, colDebit, colCredit, colBalance,
                    colPayStatus, colActions);
            colRef.setText("رقم المرجع");
            colDesc.setText("البيان");
            colPayStatus.setText("حالة الدفع");
            colDebit.setText("مدين (لنا)");
            colCredit.setText("دائن (دفعة)");
        }
    }

    // ========== Customer Actions ==========

    @FXML
    private void handleAddCustomer() {
        openCustomerForm(null);
    }

    @FXML
    private void handleViewSelectedCustomer() {
        Customer customer = customerCombo.getValue();
        if (customer == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار العميل أولاً");
            return;
        }
        StringBuilder details = new StringBuilder();
        details.append("كود العميل: ").append(customer.getCustomerCode()).append("\n\n");
        details.append("الهاتف: ").append(customer.getPhoneNumber() != null ? customer.getPhoneNumber() : "-")
                .append("\n");
        details.append("العنوان: ").append(customer.getAddress() != null ? customer.getAddress() : "-").append("\n");
        details.append("مواقع المشاريع:\n")
                .append(customer.getProjectLocation() != null ? customer.getProjectLocation() : "-").append("\n\n");
        details.append("رصيد الدينار: ").append(currencyFormat.format(customer.getBalanceIqd())).append(" د.ع\n");
        details.append("رصيد الدولار: ").append(currencyFormat.format(customer.getBalanceUsd())).append(" $");
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("تفاصيل العميل");
        alert.setHeaderText(customer.getName());
        alert.setContentText(details.toString());
        alert.showAndWait();
    }

    @FXML
    private void handleEditSelectedCustomer() {
        Customer customer = customerCombo.getValue();
        if (customer == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار العميل أولاً");
            return;
        }
        openCustomerForm(customer);
    }

    @FXML
    private void handleDeleteSelectedCustomer() {
        Customer customer = customerCombo.getValue();
        if (customer == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار العميل أولاً");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الحذف");
        confirm.setHeaderText(null);
        confirm.setContentText("هل أنت متأكد من حذف العميل: " + customer.getName() + "؟");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    customerService.deleteCustomer(customer);
                    refreshCustomerCombo();
                    customerCombo.setValue(null);
                    showAlert(Alert.AlertType.INFORMATION, "تم", "تم حذف العميل بنجاح");
                } catch (Exception e) {
                    logger.error("Failed to delete customer", e);
                    showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في حذف العميل: " + e.getMessage());
                }
            }
        });
    }

    private void openCustomerForm(Customer customer) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/CustomerForm.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle(customer == null ? "إضافة عميل جديد" : "تعديل بيانات العميل");
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);

            CustomerController controller = loader.getController();
            controller.setDialogStage(stage);
            if (customer != null) {
                controller.setCustomer(customer);
            }

            stage.showAndWait();

            if (controller.isSaved()) {
                refreshCustomerCombo();
            }
        } catch (IOException e) {
            logger.error("Failed to open customer form", e);
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في فتح نموذج العميل");
        }
    }

    private void refreshCustomerCombo() {
        Customer selected = customerCombo.getValue();
        List<Customer> list = customerService.getAllCustomers();
        customers.setAll(list);
        customerCombo.setItems(customers);
        if (selected != null) {
            customers.stream().filter(c -> c.getId().equals(selected.getId())).findFirst()
                    .ifPresent(customerCombo::setValue);
        }
    }

    private void handleMarkAsPaid(Sale sale) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("تأكيد الدفع");
        alert.setHeaderText("هل تريد تسجيل الدفع لهذه الفاتورة؟");
        alert.setContentText("رقم الفاتورة: " + sale.getSaleCode() + "\nالمبلغ: " +
                currencyFormat.format(sale.getFinalAmount()));
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    salesService.updatePaymentStatus(sale.getId(), "PAID");
                    generateStatement();
                    showAlert(Alert.AlertType.INFORMATION, "تم", "تم تسجيل الدفع بنجاح");
                } catch (Exception e) {
                    logger.error("Failed to update payment status", e);
                    showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في تسجيل الدفع: " + e.getMessage());
                }
            }
        });
    }

    // ========== Statement Combos ==========

    private void setupCombos() {
        // Customers
        List<Customer> customerList = customerService.getAllCustomers();
        customers.setAll(customerList);
        customerCombo.setItems(customers);
        customerCombo.setEditable(true);
        customerCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Customer c) {
                return c != null ? c.getName() + " (" + c.getCustomerCode() + ")" : "";
            }

            @Override
            public Customer fromString(String s) {
                if (s == null || s.isEmpty())
                    return null;
                return customers.stream()
                        .filter(c -> {
                            String label = toString(c);
                            return label.equals(s) || (c.getName() != null && c.getName().contains(s));
                        })
                        .findFirst().orElse(null);
            }
        });

        // Filter customers as user types
        if (customerCombo.getEditor() != null) {
            customerCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                if (customerCombo.getValue() != null) {
                    String rendered = customerCombo.getConverter().toString(customerCombo.getValue());
                    if (rendered.equals(newText))
                        return;
                }
                if (newText != null) {
                    boolean isSelection = customers.stream()
                            .anyMatch(c -> {
                                String label = customerCombo.getConverter().toString(c);
                                return label != null && label.equals(newText);
                            });
                    if (isSelection)
                        return;
                }
                String query = newText == null ? "" : newText.trim().toLowerCase();
                ObservableList<Customer> filtered = customers.filtered(c -> {
                    if (query.isEmpty())
                        return true;
                    String name = c.getName() != null ? c.getName().toLowerCase() : "";
                    String code = c.getCustomerCode() != null ? c.getCustomerCode().toLowerCase() : "";
                    return name.contains(query) || code.contains(query);
                });
                customerCombo.setItems(filtered);
                if (!customerCombo.isShowing())
                    customerCombo.show();
            });
        }

        customerCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateProjectLocations(newVal));

        // Currencies
        currencyCombo.setItems(FXCollections.observableArrayList("دينار", "دولار", "الكل"));
        currencyCombo.getSelectionModel().selectFirst();

        // Projects
        projectCombo.setEditable(true);
        projectCombo.setDisable(true);
    }

    private void updateProjectLocations(Customer customer) {
        projectCombo.getItems().clear();
        projectCombo.setValue(null);
        if (customer == null) {
            projectCombo.setDisable(true);
            return;
        }
        projectCombo.setDisable(false);
        String locationsText = customer.getProjectLocation();
        if (locationsText == null || locationsText.trim().isEmpty())
            return;
        List<String> locations = locationsText.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        projectCombo.setItems(FXCollections.observableArrayList(locations));
        if (locations.size() == 1)
            projectCombo.setValue(locations.get(0));
    }

    private void setupTable() {
        colDate.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getDate() != null ? cell.getValue().getDate().format(dateTimeFormatter) : ""));

        colType.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isDetailRow()
                        ? cell.getValue().getProductName()
                        : cell.getValue().getType()));

        colType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    StatementItem si = getTableRow() != null ? getTableRow().getItem() : null;
                    // Detail rows: show empty in the النوع column (product name now in اسم المادة)
                    if (si != null && si.isDetailRow()) {
                        setText("");
                        setStyle("");
                        return;
                    }
                    setText(item);
                    switch (item) {
                        case "فاتورة مبيع" -> setStyle("-fx-text-fill: -fx-danger-text; -fx-font-weight: bold;");
                        case "تسديد فاتورة" -> setStyle("-fx-text-fill: -fx-success-text; -fx-font-weight: bold;");
                        case "سند قبض" -> setStyle("-fx-text-fill: -fx-success-text; -fx-font-weight: bold;");
                        case "سند صرف" -> setStyle("-fx-text-fill: -fx-warning-text; -fx-font-weight: bold;");
                        case "مرتجع مبيعات" -> setStyle("-fx-text-fill: #8b5cf6; -fx-font-weight: bold;");
                        case "رصيد سابق" -> setStyle("-fx-text-fill: -fx-text-hint; -fx-font-style: italic;");
                        default -> setStyle("");
                    }
                }
            }
        });

        // colRef: shows invoice/ref number for parent rows, empty for detail rows
        colRef.setCellValueFactory(cell -> {
            StatementItem si = cell.getValue();
            if (si.isDetailRow())
                return new SimpleStringProperty("");
            return new SimpleStringProperty(si.getReferenceNumber() != null ? si.getReferenceNumber() : "");
        });

        colDate.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    StatementItem si = getTableRow() != null ? getTableRow().getItem() : null;
                    if (si != null && si.isDetailRow()) {
                        setText("");
                        setStyle("");
                    } else {
                        setText(item);
                        setStyle("");
                    }
                }
            }
        });

        // colDesc: shows description for parent rows, productName (colored) for detail
        // rows
        colDesc.setCellValueFactory(cell -> {
            StatementItem si = cell.getValue();
            if (si.isDetailRow())
                return new SimpleStringProperty(si.getProductName() != null ? si.getProductName() : "");
            return new SimpleStringProperty(si.getDescription() != null ? si.getDescription() : "");
        });
        colDesc.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    StatementItem si = getTableRow() != null ? getTableRow().getItem() : null;
                    if (si != null && si.isDetailRow()) {
                        boolean isSale = "مادة مبيعة".equals(si.getType());
                        String color = isSale ? "#ef4444" : "#8b5cf6";
                        setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // colPayStatus: shows quantity for detail rows, payment status for parent rows
        colPayStatus.setCellValueFactory(cell -> {
            StatementItem si = cell.getValue();
            if (si.isDetailRow()) {
                Double qty = si.getItemQty();
                return new SimpleStringProperty(qty != null ? String.format("%.0f", qty) : "");
            }
            // Original payment status logic for parent rows
            if (si.getSourceObject() instanceof Sale sale) {
                String status = sale.getPaymentStatus();
                return new SimpleStringProperty("PAID".equals(status) ? "مدفوع" : "معلق");
            }
            return new SimpleStringProperty("");
        });

        colDebit.setCellValueFactory(cell -> {
            StatementItem si = cell.getValue();
            if (si.isDetailRow())
                return new SimpleObjectProperty<>(si.getItemUnitPrice());
            return new SimpleObjectProperty<>(si.getDebit());
        });
        colDebit.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                StatementItem si = getTableRow() != null ? getTableRow().getItem() : null;
                if (empty || item == null || item == 0) {
                    setText("");
                    setStyle("");
                } else if (si != null && si.isDetailRow()) {
                    // Show unit price with currency
                    String sym = "دولار".equals(si.getCurrency()) ? " $" : " د.ع";
                    boolean isSale = "مادة مبيعة".equals(si.getType());
                    String color = isSale ? "#ef4444" : "#8b5cf6";
                    setText(currencyFormat.format(item) + sym);
                    setStyle("-fx-text-fill: " + color + ";");
                } else {
                    setText(currencyFormat.format(item));
                    setStyle("-fx-text-fill: -fx-danger-text;");
                }
            }
        });

        colCredit.setCellValueFactory(cell -> {
            StatementItem si = cell.getValue();
            if (si.isDetailRow())
                return new SimpleObjectProperty<>(si.getItemTotal());
            return new SimpleObjectProperty<>(si.getCredit());
        });
        colCredit.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                StatementItem si = getTableRow() != null ? getTableRow().getItem() : null;
                if (empty || item == null || item == 0) {
                    setText("");
                    setStyle("");
                } else if (si != null && si.isDetailRow()) {
                    // Show item total with currency
                    String sym = "دولار".equals(si.getCurrency()) ? " $" : " د.ع";
                    boolean isSale = "مادة مبيعة".equals(si.getType());
                    String color = isSale ? "#ef4444" : "#8b5cf6";
                    setText(currencyFormat.format(item) + sym);
                    setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                } else {
                    setText(currencyFormat.format(item));
                    setStyle("-fx-text-fill: -fx-success-text;");
                }
            }
        });

        colBalance.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getBalance()));
        colBalance.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                StatementItem rowItem = getTableRow() != null ? getTableRow().getItem() : null;
                // Detail rows: no running balance shown
                if (empty || item == null || (rowItem != null && rowItem.isDetailRow())) {
                    setText("");
                    setStyle("");
                    return;
                }
                String currencySymbol = "دولار".equals(rowItem.getCurrency()) ? " $" : " د.ع";
                setText(currencyFormat.format(item) + currencySymbol);
                if (item > 0) {
                    setStyle("-fx-text-fill: -fx-danger-text; -fx-font-weight: bold;");
                } else if (item < 0) {
                    setStyle("-fx-text-fill: -fx-success-text; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });

        // colPayStatus cell styling (setCellValueFactory is defined above, alongside
        // colDesc)
        colPayStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "معلق" -> setStyle("-fx-text-fill: -fx-danger-text; -fx-font-weight: bold;");
                        case "مدفوع" -> setStyle("-fx-text-fill: -fx-success-text; -fx-font-weight: bold;");
                        default -> setStyle(""); // quantity — no special color
                    }
                }
            }
        });

        // Actions column
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("👁");
            private final Button deleteBtn = new Button("🗑");
            private final Button receiptBtn = new Button("🧾");
            private final Button payBtn = new Button("💰");
            private final HBox box = new HBox(4);

            {
                viewBtn.setStyle(
                        "-fx-background-color: -fx-accent-light; -fx-text-fill: white; -fx-padding: 2 6; -fx-cursor: hand;");
                deleteBtn.setStyle(
                        "-fx-background-color: -fx-danger; -fx-text-fill: white; -fx-padding: 2 6; -fx-cursor: hand;");
                receiptBtn.setStyle(
                        "-fx-background-color: #8b5cf6; -fx-text-fill: white; -fx-padding: 2 6; -fx-cursor: hand;");

                payBtn.setStyle(
                        "-fx-background-color: -fx-success; -fx-text-fill: white; -fx-padding: 2 6; -fx-cursor: hand;");

                viewBtn.setTooltip(new Tooltip("عرض"));
                deleteBtn.setTooltip(new Tooltip("حذف / إلغاء"));
                receiptBtn.setTooltip(new Tooltip("إنشاء إيصال"));
                payBtn.setTooltip(new Tooltip("تسديد"));

                viewBtn.setOnAction(e -> {
                    StatementItem item = getTableView().getItems().get(getIndex());
                    handleView(item);
                });
                deleteBtn.setOnAction(e -> {
                    StatementItem item = getTableView().getItems().get(getIndex());
                    handleDelete(item);
                });
                receiptBtn.setOnAction(e -> {
                    StatementItem item = getTableView().getItems().get(getIndex());
                    handleCreateReceipt(item);
                });
                payBtn.setOnAction(e -> {
                    StatementItem item = getTableView().getItems().get(getIndex());
                    Object src = item.getSourceObject();
                    if (src instanceof Sale sale)
                        handleMarkAsPaid(sale);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                StatementItem si = getTableView().getItems().get(getIndex());
                Object src = si.getSourceObject();
                box.getChildren().clear();

                if (src == null || si.isDetailRow()) {
                    // Opening balance row or detail sub-row — no actions
                    setGraphic(null);
                    return;
                }

                if ("تسديد فاتورة".equals(si.getType())) {
                    box.getChildren().add(viewBtn);
                    setGraphic(box);
                    return;
                }

                box.getChildren().add(viewBtn);
                box.getChildren().add(deleteBtn);

                if (src instanceof Sale || src instanceof SaleReturn || src instanceof Voucher) {
                    box.getChildren().add(receiptBtn);
                }

                setGraphic(box);
            }
        });

        // Row styling
        statementTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(StatementItem item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.isDetailRow()) {
                    boolean isSaleDetail = "مادة مبيعة".equals(item.getType());
                    String bg = isSaleDetail
                            ? "-fx-background-color: rgba(239,68,68,0.05);"
                            : "-fx-background-color: rgba(139,92,246,0.05);";
                    setStyle(bg + " -fx-font-size: 11px;");
                } else if ("رصيد سابق".equals(item.getType())) {
                    setStyle("-fx-background-color: rgba(100,116,139,0.1);");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private void generateStatement() {
        Customer customer = customerCombo.getValue();
        if (customer == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار العميل");
            return;
        }

        String currency = currencyCombo.getValue();
        if (currency == null || currency.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار العملة");
            return;
        }

        String project = projectCombo.getValue();
        if (project != null && project.isBlank())
            project = null;

        LocalDateTime from = fromDate.getValue() != null ? fromDate.getValue().atStartOfDay() : null;
        LocalDateTime to = toDate.getValue() != null ? toDate.getValue().atTime(23, 59, 59) : null;

        try {
            List<StatementItem> items;
            if (showDetailsCheck != null && showDetailsCheck.isSelected()) {
                items = statementService.getStatementWithDetails(customer.getId(), project, currency, from, to);
            } else {
                items = statementService.getStatement(customer.getId(), project, currency, from, to);
            }
            statementTable.setItems(FXCollections.observableArrayList(items));
            updateSummary(items);
        } catch (Exception e) {
            logger.error("Failed to generate statement", e);
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في توليد كشف الحساب: " + e.getMessage());
        }
    }

    private void updateSummary(List<StatementItem> items) {
        double totalDebitIqd = 0, totalCreditIqd = 0;
        double totalDebitUsd = 0, totalCreditUsd = 0;
        double finalBalanceIqd = 0, finalBalanceUsd = 0;
        int count = 0;

        for (StatementItem item : items) {
            if ("رصيد سابق".equals(item.getType()) || item.isDetailRow()) {
                // Don't count opening balance or detail sub-rows in totals
                continue;
            }
            double d = item.getDebit() != null ? item.getDebit() : 0;
            double c = item.getCredit() != null ? item.getCredit() : 0;
            String curr = item.getCurrency();

            if ("دولار".equals(curr)) {
                totalDebitUsd += d;
                totalCreditUsd += c;
            } else {
                totalDebitIqd += d;
                totalCreditIqd += c;
            }
            count++;
        }

        // Final balance is the last non-detail row's balance for each currency
        boolean foundIqd = false, foundUsd = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            StatementItem it = items.get(i);
            if (!it.isDetailRow() && it.getBalance() != null) {
                String curr = it.getCurrency();
                if ("دولار".equals(curr) && !foundUsd) {
                    finalBalanceUsd = it.getBalance();
                    foundUsd = true;
                } else if (!"دولار".equals(curr) && !foundIqd) {
                    finalBalanceIqd = it.getBalance();
                    foundIqd = true;
                }
            }
            if (foundIqd && foundUsd)
                break;
        }

        totalDebitLabel.setText(formatDualCurrency(totalDebitIqd, totalDebitUsd));
        totalCreditLabel.setText(formatDualCurrency(totalCreditIqd, totalCreditUsd));
        finalBalanceLabel.setText(formatDualCurrency(finalBalanceIqd, finalBalanceUsd));
        totalCountLabel.setText(String.valueOf(count));

        // Pending payments summary
        double pendingTotalIqd = 0;
        double pendingTotalUsd = 0;
        int pendingCount = 0;
        for (StatementItem item : items) {
            Object src = item.getSourceObject();
            if (src instanceof Sale sale && "PENDING".equals(sale.getPaymentStatus())) {
                if ("دولار".equals(sale.getCurrency())) {
                    pendingTotalUsd += sale.getFinalAmount();
                } else {
                    pendingTotalIqd += sale.getFinalAmount();
                }
                pendingCount++;
            }
        }
        pendingTotalLabel.setText(formatDualCurrency(pendingTotalIqd, pendingTotalUsd));
        pendingCountLabel.setText(String.valueOf(pendingCount));

        // We keep the logic simple, removing the color-based balance because it's mixed
        // currency and styling a combined label is complex.
        finalBalanceLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -fx-form-label;");
    }

    private String formatDualCurrency(double iqd, double usd) {
        StringBuilder sb = new StringBuilder();
        if (iqd != 0 || usd == 0) {
            sb.append(currencyFormat.format(iqd)).append(" د.ع");
        }
        if (usd != 0) {
            if (sb.length() > 0)
                sb.append(" | ");
            sb.append(currencyFormat.format(usd)).append(" $");
        }
        return sb.toString();
    }

    // ========== Action Handlers ==========

    private void handleView(StatementItem item) {
        Object src = item.getSourceObject();
        if (src == null)
            return;

        StringBuilder details = new StringBuilder();

        if (src instanceof Sale sale) {
            details.append("نوع: فاتورة مبيع\n");
            details.append("رقم الفاتورة: ").append(sale.getSaleCode()).append("\n");
            details.append("التاريخ: ")
                    .append(sale.getSaleDate() != null ? sale.getSaleDate().format(dateTimeFormatter) : "-")
                    .append("\n");
            details.append("العميل: ").append(sale.getCustomer() != null ? sale.getCustomer().getName() : "-")
                    .append("\n");
            details.append("المشروع: ").append(sale.getProjectLocation() != null ? sale.getProjectLocation() : "-")
                    .append("\n");
            details.append("المبلغ: ").append(currencyFormat.format(sale.getFinalAmount())).append(" ")
                    .append(sale.getCurrency()).append("\n");
            details.append("حالة الدفع: ").append("PAID".equals(sale.getPaymentStatus()) ? "مدفوع" : "معلق")
                    .append("\n");
            details.append("ملاحظات: ").append(sale.getNotes() != null ? sale.getNotes() : "-").append("\n");
        } else if (src instanceof Voucher voucher) {
            details.append("نوع: ").append(voucher.getVoucherType().getArabicName()).append("\n");
            details.append("رقم السند: ").append(voucher.getVoucherNumber()).append("\n");
            details.append("التاريخ: ").append(voucher.getVoucherDate().toLocalDate()).append("\n");
            details.append("الحساب: ").append(voucher.getCustomer() != null ? voucher.getCustomer().getName() : "نقدي")
                    .append("\n");
            details.append("المشروع: ").append(voucher.getProjectName() != null ? voucher.getProjectName() : "-")
                    .append("\n");
            details.append("المبلغ: ").append(currencyFormat.format(voucher.getAmount())).append(" ")
                    .append(voucher.getCurrency()).append("\n");
            details.append("الخصم: ").append(currencyFormat.format(voucher.getDiscountAmount())).append("\n");
            details.append("الصافي: ").append(currencyFormat.format(voucher.getNetAmount())).append("\n");
            details.append("المبلغ كتابةً: ").append(voucher.getAmountInWords()).append("\n");
            details.append("البيان: ").append(voucher.getDescription()).append("\n");
            details.append("بواسطة: ").append(voucher.getCreatedBy()).append("\n");
            if (voucher.getIsCancelled()) {
                details.append("\n*** ملغي ***\n");
                details.append("سبب الإلغاء: ").append(voucher.getCancelReason()).append("\n");
                details.append("ألغي بواسطة: ").append(voucher.getCancelledBy()).append("\n");
            }
        } else if (src instanceof SaleReturn ret) {
            details.append("نوع: مرتجع مبيعات\n");
            details.append("رقم المرتجع: ").append(ret.getReturnCode()).append("\n");
            details.append("التاريخ: ")
                    .append(ret.getReturnDate() != null ? ret.getReturnDate().format(dateTimeFormatter) : "-")
                    .append("\n");
            details.append("العميل: ").append(ret.getCustomer() != null ? ret.getCustomer().getName() : "-")
                    .append("\n");
            details.append("الفاتورة: ").append(ret.getSale() != null ? ret.getSale().getSaleCode() : "-").append("\n");
            details.append("المبلغ: ").append(currencyFormat.format(ret.getTotalReturnAmount())).append("\n");
            details.append("السبب: ").append(ret.getReturnReason() != null ? ret.getReturnReason() : "-").append("\n");
            details.append("الحالة: ").append(ret.getReturnStatus()).append("\n");
            details.append("بواسطة: ").append(ret.getProcessedBy() != null ? ret.getProcessedBy() : "-").append("\n");
        }

        showAlert(Alert.AlertType.INFORMATION, "تفاصيل الحركة", details.toString());
    }

    private void handleDelete(StatementItem item) {
        Object src = item.getSourceObject();
        if (src == null)
            return;

        // Admin PIN confirmation
        String pin = promptAdminPin();
        if (pin == null)
            return;
        if (!authService.verifyAdminPin(pin)) {
            showAlert(Alert.AlertType.ERROR, "غير مسموح", "رمز الأدمن غير صحيح");
            return;
        }

        if (src instanceof Sale sale) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("تأكيد الحذف");
            confirm.setHeaderText("هل تريد حذف الفاتورة؟");
            confirm.setContentText("رقم الفاتورة: " + sale.getSaleCode());
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        salesService.deleteSale(sale.getId());
                        showAlert(Alert.AlertType.INFORMATION, "تم", "تم حذف الفاتورة بنجاح");
                        generateStatement();
                    } catch (Exception e) {
                        logger.error("Failed to delete sale", e);
                        showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في حذف الفاتورة: " + e.getMessage());
                    }
                }
            });
        } else if (src instanceof Voucher voucher) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("تأكيد الإلغاء");
            confirm.setHeaderText("هل تريد إلغاء السند؟");
            confirm.setContentText("رقم السند: " + voucher.getVoucherNumber());

            // Ask for cancel reason
            TextInputDialog reasonDialog = new TextInputDialog();
            reasonDialog.setTitle("سبب الإلغاء");
            reasonDialog.setHeaderText("أدخل سبب إلغاء السند");
            reasonDialog.setContentText("السبب:");

            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    reasonDialog.showAndWait().ifPresent(reason -> {
                        try {
                            String cancelledBy = SessionManager.getInstance().getCurrentDisplayName();
                            voucherService.cancelVoucher(voucher.getId(), cancelledBy, reason);
                            showAlert(Alert.AlertType.INFORMATION, "تم", "تم إلغاء السند بنجاح");
                            generateStatement();
                        } catch (Exception e) {
                            logger.error("Failed to cancel voucher", e);
                            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في إلغاء السند: " + e.getMessage());
                        }
                    });
                }
            });
        } else if (src instanceof SaleReturn ret) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("تأكيد الحذف");
            confirm.setHeaderText("هل تريد حذف المرتجع؟");
            confirm.setContentText("رقم المرتجع: " + ret.getReturnCode());
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        returnService.deleteReturn(ret.getId());
                        showAlert(Alert.AlertType.INFORMATION, "تم", "تم حذف المرتجع بنجاح");
                        generateStatement();
                    } catch (Exception e) {
                        logger.error("Failed to delete return", e);
                        showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في حذف المرتجع: " + e.getMessage());
                    }
                }
            });
        }
    }

    private void handleCreateReceipt(StatementItem item) {
        Object src = item.getSourceObject();

        if (src instanceof Sale sale) {
            handleCreateSaleReceipt(sale);
        } else if (src instanceof SaleReturn ret) {
            handleCreateReturnReceipt(ret);
        } else if (src instanceof Voucher voucher) {
            handleCreateVoucherReceipt(voucher);
        }
    }

    private void handleCreateVoucherReceipt(Voucher voucher) {
        String typeName = voucher.getVoucherType() == VoucherType.RECEIPT ? "سند قبض" : "سند الدفع";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("إنشاء إيصال");
        confirm.setHeaderText("إنشاء إيصال لـ " + typeName + ": " + voucher.getVoucherNumber());
        confirm.setContentText("هل تريد إنشاء إيصال PDF لهذا السند؟");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    String printedBy = SessionManager.getInstance().getCurrentDisplayName();
                    File pdfFile = voucherService.generateVoucherReceiptPdf(voucher.getId(), printedBy);
                    if (pdfFile != null && pdfFile.exists()) {
                        showAlert(Alert.AlertType.INFORMATION, "تم بنجاح", "تم إنشاء إيصال السند بنجاح");
                        if (mainApp != null) {
                            mainApp.showPdfPreview(pdfFile);
                        } else if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(pdfFile);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to create voucher receipt", e);
                    showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في إنشاء إيصال السند: " + e.getMessage());
                }
            }
        });
    }

    private void handleCreateSaleReceipt(Sale sale) {
        boolean hasReceipt = receiptService.hasReceiptForSale(sale.getId());

        if (hasReceipt) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("إيصال موجود");
            confirm.setHeaderText("هذه الفاتورة لديها إيصال بالفعل");
            confirm.setContentText("هل تريد إنشاء إيصال جديد؟");
            var result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK)
                return;
        }

        // Create receipt dialog
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("إنشاء إيصال");
        dialog.setHeaderText("إنشاء إيصال للفاتورة: " + sale.getSaleCode());

        try {
            dialog.getDialogPane().getStylesheets().addAll(
                    getClass().getResource(com.pharmax.util.ThemeManager.getInstance().getCurrentTheme().getCssPath())
                            .toExternalForm(),
                    getClass().getResource("/styles/main.css").toExternalForm());
        } catch (Exception e) {
        }
        dialog.getDialogPane().setStyle("-fx-font-family: 'Geeza Pro', 'SF Arabic', 'Arial', 'Tahoma', sans-serif;");
        dialog.getDialogPane().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);

        ButtonType createBtn = new ButtonType("إنشاء", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        ComboBox<String> templateCombo = new ComboBox<>(
                FXCollections.observableArrayList("DEFAULT", "DETAILED", "SIMPLE"));
        templateCombo.setValue("DEFAULT");

        TextField notesField = new TextField();
        notesField.setPromptText("ملاحظات إضافية (اختياري)");
        notesField.setPrefWidth(300);

        CheckBox printCheck = new CheckBox("فتح بعد الإنشاء");
        printCheck.setSelected(true);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("القالب:"), 0, 0);
        grid.add(templateCombo, 1, 0);
        grid.add(new Label("ملاحظات:"), 0, 1);
        grid.add(notesField, 1, 1);
        grid.add(printCheck, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> btn == createBtn ? templateCombo.getValue() : null);

        dialog.showAndWait().ifPresent(template -> {
            try {
                Receipt receipt = receiptService.generateReceipt(sale.getId(), template, "System");
                String notes = notesField.getText();
                if (notes != null && !notes.trim().isEmpty()) {
                    receipt.setNotes(notes);
                }

                showAlert(Alert.AlertType.INFORMATION, "تم بنجاح",
                        "تم إنشاء الإيصال رقم: " + receipt.getReceiptNumber());

                if (printCheck.isSelected() && receipt.getFilePath() != null) {
                    File pdfFile = new File(receipt.getFilePath());
                    if (pdfFile.exists()) {
                        if (mainApp != null) {
                            mainApp.showPdfPreview(pdfFile);
                        } else if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(pdfFile);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to create receipt", e);
                showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في إنشاء الإيصال: " + e.getMessage());
            }
        });
    }

    private void handleCreateReturnReceipt(SaleReturn ret) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("إنشاء إيصال مرتجع");
        confirm.setHeaderText("إنشاء إيصال للمرتجع: " + ret.getReturnCode());
        confirm.setContentText("هل تريد إنشاء إيصال PDF لهذا المرتجع؟");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    File pdfFile = returnService.generateReturnReceiptPdf(ret);
                    if (pdfFile != null && pdfFile.exists()) {
                        showAlert(Alert.AlertType.INFORMATION, "تم بنجاح", "تم إنشاء إيصال المرتجع بنجاح");
                        if (mainApp != null) {
                            mainApp.showPdfPreview(pdfFile);
                        } else if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(pdfFile);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to create return receipt", e);
                    showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في إنشاء إيصال المرتجع: " + e.getMessage());
                }
            }
        });
    }

    private void printStatement() {
        Customer customer = customerCombo.getValue();
        if (customer == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار العميل أولاً");
            return;
        }

        String currency = currencyCombo.getValue();
        String project = projectCombo.getValue();
        if (project != null && project.isBlank())
            project = null;

        LocalDate fromDt = fromDate.getValue();
        LocalDate toDt = toDate.getValue();

        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("حفظ كشف الحساب");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            String customerName = customer.getName() != null ? customer.getName() : "customer";
            fileChooser.setInitialFileName("statement_" + customerName + ".pdf");

            Stage owner = (Stage) statementTable.getScene().getWindow();
            File selectedFile = fileChooser.showSaveDialog(owner);
            if (selectedFile == null)
                return;

            File pdfFile;
            boolean withDetails = showDetailsCheck != null && showDetailsCheck.isSelected();
            if (withDetails) {
                // Use the detailed PDF that renders item sub-rows (use the already-expanded
                // table items)
                List<StatementItem> currentItems = new java.util.ArrayList<>(statementTable.getItems());
                pdfFile = receiptService.generateDetailedStatementPdf(customer, currentItems, currency,
                        fromDt, toDt, selectedFile, project);
            } else {
                pdfFile = receiptService.generateAccountStatementPdf(customer, project, fromDt, toDt, false, currency,
                        selectedFile);
            }

            if (pdfFile != null && pdfFile.exists()) {
                if (mainApp != null) {
                    mainApp.showPdfPreview(pdfFile);
                } else if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(pdfFile);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to print statement", e);
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في طباعة كشف الحساب: " + e.getMessage());
        }
    }

    // ========== Helpers ==========

    private String promptAdminPin() {
        Dialog<String> pinDialog = new Dialog<>();
        pinDialog.setTitle("تأكيد الأدمن");
        pinDialog.setHeaderText("أدخل رمز PIN الأدمن لتأكيد العملية");

        try {
            pinDialog.getDialogPane().getStylesheets().addAll(
                    getClass().getResource(com.pharmax.util.ThemeManager.getInstance().getCurrentTheme().getCssPath())
                            .toExternalForm(),
                    getClass().getResource("/styles/main.css").toExternalForm());
        } catch (Exception e) {
        }
        pinDialog.getDialogPane().setStyle("-fx-font-family: 'Geeza Pro', 'SF Arabic', 'Arial', 'Tahoma', sans-serif;");
        pinDialog.getDialogPane().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        ButtonType okBtn = new ButtonType("تأكيد", ButtonBar.ButtonData.OK_DONE);
        pinDialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        PasswordField pinField = new PasswordField();
        pinField.setPromptText("PIN");
        pinDialog.getDialogPane().setContent(pinField);
        pinDialog.setResultConverter(btn -> btn == okBtn ? pinField.getText() : null);

        return pinDialog.showAndWait().orElse(null);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
