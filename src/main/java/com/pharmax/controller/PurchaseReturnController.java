package com.pharmax.controller;

import com.pharmax.model.Customer;
import com.pharmax.model.ProductBatch;
import com.pharmax.model.PurchaseReturn;
import com.pharmax.model.Voucher;
import com.pharmax.service.CustomerService;
import com.pharmax.service.PurchaseReturnService;
import com.pharmax.util.SessionManager;
import com.pharmax.util.TabManager;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PurchaseReturnController {
    private final CustomerService customerService = new CustomerService();
    private final PurchaseReturnService purchaseReturnService = new PurchaseReturnService();
    private final DecimalFormat numberFormat = new DecimalFormat("#,##0.##");

    @FXML private ComboBox<Customer> supplierComboBox;
    @FXML private ComboBox<Voucher> purchaseVoucherComboBox;
    @FXML private TableView<ReturnablePurchaseRow> purchaseItemsTable;
    @FXML private TableColumn<ReturnablePurchaseRow, String> productColumn;
    @FXML private TableColumn<ReturnablePurchaseRow, Double> purchasedQtyColumn;
    @FXML private TableColumn<ReturnablePurchaseRow, Double> returnedQtyColumn;
    @FXML private TableColumn<ReturnablePurchaseRow, Double> availableQtyColumn;
    @FXML private TableColumn<ReturnablePurchaseRow, Double> currentBatchQtyColumn;
    @FXML private TableColumn<ReturnablePurchaseRow, String> batchColumn;
    @FXML private TableColumn<ReturnablePurchaseRow, String> expiryColumn;
    @FXML private TableColumn<ReturnablePurchaseRow, Double> unitCostColumn;
    @FXML private TableColumn<ReturnablePurchaseRow, Double> returnQtyColumn;
    @FXML private TextArea reasonArea;
    @FXML private TextArea notesArea;
    @FXML private Label totalReturnLabel;
    @FXML private Label statusLabel;
    @FXML private Button saveButton;

    private boolean tabMode = false;
    private String tabId;
    private final ObservableList<ReturnablePurchaseRow> rows = FXCollections.observableArrayList();

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    public void setTabId(String tabId) {
        this.tabId = tabId;
    }

    @FXML
    private void initialize() {
        setupCombos();
        setupTable();
        loadSuppliers();
        updateTotals();
    }

    private void setupCombos() {
        supplierComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Customer customer) {
                if (customer == null) {
                    return "";
                }
                return customer.getCustomerCode() + " - " + customer.getName();
            }

            @Override
            public Customer fromString(String string) {
                return null;
            }
        });

        purchaseVoucherComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Voucher voucher) {
                if (voucher == null) {
                    return "";
                }
                String date = voucher.getVoucherDate() != null
                        ? voucher.getVoucherDate().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        : "-";
                return voucher.getVoucherNumber() + " - " + date;
            }

            @Override
            public Voucher fromString(String string) {
                return null;
            }
        });
    }

    private void setupTable() {
        productColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        purchasedQtyColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getPurchasedQuantity()));
        returnedQtyColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getPreviouslyReturnedQuantity()));
        availableQtyColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getAvailableToReturnQuantity()));
        currentBatchQtyColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getCurrentBatchQuantity()));
        batchColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBatchNumber()));
        expiryColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getExpirationDate()));
        unitCostColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getUnitCost()));
        returnQtyColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getReturnQuantity()));
        returnQtyColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        returnQtyColumn.setOnEditCommit(event -> {
            ReturnablePurchaseRow row = event.getRowValue();
            double value = event.getNewValue() != null ? event.getNewValue() : 0.0;
            row.setReturnQuantity(Math.max(0.0, value));
            purchaseItemsTable.refresh();
            updateTotals();
        });
        purchaseItemsTable.setEditable(true);
        purchaseItemsTable.setItems(rows);
        purchaseItemsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> updateTotals());
    }

    private void loadSuppliers() {
        supplierComboBox.setItems(FXCollections.observableArrayList(customerService.getAllCustomers()));
    }

    @FXML
    private void handleSupplierChange() {
        rows.clear();
        updateTotals();
        Customer supplier = supplierComboBox.getValue();
        if (supplier == null) {
            purchaseVoucherComboBox.setItems(FXCollections.emptyObservableList());
            statusLabel.setText("اختر المورد/الحساب لعرض فواتير المشتريات");
            return;
        }
        List<Voucher> vouchers = purchaseReturnService.getPurchaseVouchersBySupplier(supplier.getId());
        purchaseVoucherComboBox.setItems(FXCollections.observableArrayList(vouchers));
        purchaseVoucherComboBox.setValue(null);
        statusLabel.setText(vouchers.isEmpty() ? "لا توجد فواتير مشتريات لهذا المورد" : "اختر فاتورة مشتريات");
    }

    @FXML
    private void handleVoucherChange() {
        rows.clear();
        updateTotals();
        Voucher voucher = purchaseVoucherComboBox.getValue();
        if (voucher == null) {
            statusLabel.setText("اختر فاتورة مشتريات لعرض المواد");
            return;
        }
        List<PurchaseReturnService.PurchaseReturnableItem> items = purchaseReturnService.getReturnableItemsForVoucher(voucher.getId());
        for (PurchaseReturnService.PurchaseReturnableItem item : items) {
            rows.add(new ReturnablePurchaseRow(item));
        }
        statusLabel.setText(rows.isEmpty()
                ? "لا توجد مواد قابلة لمرتجع شراء آمن في هذه الفاتورة"
                : "اختر سطراً واحداً وحدد كمية المرتجع");
    }

    @FXML
    private void handleSave() {
        try {
            Customer supplier = supplierComboBox.getValue();
            Voucher voucher = purchaseVoucherComboBox.getValue();
            ReturnablePurchaseRow row = purchaseItemsTable.getSelectionModel().getSelectedItem();

            if (supplier == null) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار المورد/الحساب");
                return;
            }
            if (voucher == null) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار فاتورة شراء");
                return;
            }
            if (row == null) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار سطر مشتريات واحد");
                return;
            }
            if (row.getReturnQuantity() <= 0) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى إدخال كمية مرتجع أكبر من صفر");
                return;
            }

            PurchaseReturn purchaseReturn = purchaseReturnService.createPurchaseReturn(
                    supplier.getId(),
                    voucher.getId(),
                    row.getSourceVoucherItemId(),
                    row.getReturnQuantity(),
                    reasonArea != null ? reasonArea.getText() : null,
                    notesArea != null ? notesArea.getText() : null,
                    SessionManager.getInstance().getCurrentDisplayName()
            );

            showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم حفظ مرتجع الشراء بنجاح رقم " + purchaseReturn.getId());
            reasonArea.clear();
            notesArea.clear();
            handleVoucherChange();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", e.getMessage());
        }
    }

    @FXML
    private void handleNew() {
        purchaseItemsTable.getSelectionModel().clearSelection();
        rows.forEach(row -> row.setReturnQuantity(0.0));
        if (reasonArea != null) {
            reasonArea.clear();
        }
        if (notesArea != null) {
            notesArea.clear();
        }
        purchaseItemsTable.refresh();
        updateTotals();
        statusLabel.setText("تمت إعادة تهيئة الشاشة");
    }

    @FXML
    private void handleClose() {
        if (tabMode && tabId != null && !tabId.isBlank()) {
            TabManager.getInstance().closeTab(tabId);
            return;
        }
        Stage stage = (Stage) supplierComboBox.getScene().getWindow();
        stage.close();
    }

    private void updateTotals() {
        ReturnablePurchaseRow row = purchaseItemsTable.getSelectionModel().getSelectedItem();
        double total = row != null ? row.getReturnQuantity() * row.getUnitCost() : 0.0;
        totalReturnLabel.setText(numberFormat.format(total) + " د.ع");
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class ReturnablePurchaseRow {
        private final PurchaseReturnService.PurchaseReturnableItem item;
        private double returnQuantity;

        public ReturnablePurchaseRow(PurchaseReturnService.PurchaseReturnableItem item) {
            this.item = item;
            this.returnQuantity = 0.0;
        }

        public Long getSourceVoucherItemId() {
            return item.getSourceVoucherItem().getId();
        }

        public String getProductName() {
            return item.getProduct() != null ? item.getProduct().getName() : item.getSourceVoucherItem().getProductName();
        }

        public double getPurchasedQuantity() {
            return item.getPurchasedQuantity();
        }

        public double getPreviouslyReturnedQuantity() {
            return item.getPreviouslyReturnedQuantity();
        }

        public double getAvailableToReturnQuantity() {
            return item.getAvailableToReturnQuantity();
        }

        public double getCurrentBatchQuantity() {
            return item.getCurrentBatchQuantity();
        }

        public String getBatchNumber() {
            ProductBatch batch = item.getBatch();
            return batch != null ? batch.getBatchNumber() : "-";
        }

        public String getExpirationDate() {
            ProductBatch batch = item.getBatch();
            if (batch != null && batch.getExpiryDate() != null) {
                return batch.getExpiryDate().toString();
            }
            return item.getSourceVoucherItem().getExpirationDate() != null ? item.getSourceVoucherItem().getExpirationDate() : "-";
        }

        public double getUnitCost() {
            return item.getSourceVoucherItem().getUnitPrice() != null ? item.getSourceVoucherItem().getUnitPrice() : 0.0;
        }

        public double getReturnQuantity() {
            return returnQuantity;
        }

        public void setReturnQuantity(double returnQuantity) {
            double maxAllowed = Math.min(getAvailableToReturnQuantity(), getCurrentBatchQuantity());
            this.returnQuantity = Math.max(0.0, Math.min(returnQuantity, maxAllowed));
        }
    }
}
