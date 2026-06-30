package com.pharmax.controller;

import com.pharmax.model.*;
import com.pharmax.service.CustomerService;
import com.pharmax.service.VoucherService;
import com.pharmax.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import javafx.collections.transformation.FilteredList;
import javafx.util.StringConverter;

public class VoucherListController implements Initializable {
    
    @FXML private Label titleLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<Customer> customerFilterCombo;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private TableView<Voucher> vouchersTable;
    @FXML private TableColumn<Voucher, String> voucherNumberCol;
    @FXML private TableColumn<Voucher, String> supplierInvoiceNumberCol;
    @FXML private TableColumn<Voucher, String> dateCol;
    @FXML private TableColumn<Voucher, String> customerCol;
    @FXML private TableColumn<Voucher, String> amountCol;
    @FXML private TableColumn<Voucher, String> currencyCol;
    @FXML private TableColumn<Voucher, String> descriptionCol;
    @FXML private TableColumn<Voucher, String> createdByCol;
    @FXML private Label totalCountLabel;
    @FXML private Label totalIqdLabel;
    @FXML private Label totalUsdLabel;
    
    private final VoucherService voucherService = new VoucherService();
    private final CustomerService customerService = new CustomerService();
    private final DecimalFormat numberFormat = new DecimalFormat("#,###.##");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    
    private VoucherType voucherType = VoucherType.RECEIPT;
    private ObservableList<Voucher> vouchers = FXCollections.observableArrayList();
    private ObservableList<Customer> customers = FXCollections.observableArrayList();
    private FilteredList<Customer> filteredCustomers;
    private Customer selectedCustomer;
    private String customerSearchQuery;
    private String initialSearchTerm;
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupDatePickers();
        setupCustomerFilter();
        loadCustomers();
    }
    
    public void setVoucherType(VoucherType type) {
        this.voucherType = type;
        updateTitle();
        loadCustomers();
        loadVouchers();
    }

    public void setInitialSearchTerm(String term) {
        this.initialSearchTerm = term;
        if (searchField != null) {
            searchField.setText(term != null ? term : "");
        }
        if (term != null && !term.isBlank()) {
            selectCustomerByName(term.trim());
        }
        handleSearch();
    }

    private void setupCustomerFilter() {
        if (customerFilterCombo == null) {
            return;
        }
        filteredCustomers = new FilteredList<>(customers, c -> true);
        customerFilterCombo.setItems(filteredCustomers);
        customerFilterCombo.setEditable(true);
        customerFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Customer customer) {
                if (customer == null) return "";
                String code = customer.getCustomerCode() != null ? customer.getCustomerCode() : "";
                return (code.isEmpty() ? "" : code + " - ") + customer.getName();
            }

            @Override
            public Customer fromString(String s) {
                if (s == null || s.isEmpty()) return null;
                return filteredCustomers.getSource().stream()
                        .filter(c -> {
                            String label = toString(c);
                            return label.equals(s) || (c.getName() != null && c.getName().contains(s));
                        })
                        .findFirst()
                        .orElse(null);
            }
        });

        if (customerFilterCombo.getEditor() != null) {
            customerFilterCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                if (customerFilterCombo.getValue() != null) {
                    String rendered = customerFilterCombo.getConverter().toString(customerFilterCombo.getValue());
                    if (rendered.equals(newText)) {
                        return;
                    }
                }

                if (newText != null) {
                    boolean isSelection = filteredCustomers.getSource().stream()
                            .anyMatch(c -> {
                                String label = customerFilterCombo.getConverter().toString(c);
                                return label != null && label.equals(newText);
                            });
                    if (isSelection) {
                        return;
                    }
                }

                customerSearchQuery = newText == null ? "" : newText.trim().toLowerCase();
                filteredCustomers.setPredicate(c -> {
                    if (customerSearchQuery.isEmpty()) return true;
                    String name = c.getName() != null ? c.getName().toLowerCase() : "";
                    String code = c.getCustomerCode() != null ? c.getCustomerCode().toLowerCase() : "";
                    String full = (code + " - " + name).toLowerCase();
                    return name.contains(customerSearchQuery) || code.contains(customerSearchQuery) || full.contains(customerSearchQuery);
                });

                if (!customerFilterCombo.isShowing()) {
                    customerFilterCombo.show();
                }
            });
        }

        customerFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            selectedCustomer = newVal;
        });
    }

    private void loadCustomers() {
        customers.setAll(voucherType == VoucherType.RECEIPT
                ? customerService.getSaleCustomers()
                : customerService.getSuppliers());
        if (filteredCustomers != null) {
            filteredCustomers.setPredicate(c -> true);
        }
        if (initialSearchTerm != null && !initialSearchTerm.isBlank()) {
            selectCustomerByName(initialSearchTerm.trim());
        }
    }

    private void selectCustomerByName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        Customer match = customers.stream()
                .filter(c -> c.getName() != null && c.getName().contains(name))
                .findFirst()
                .orElse(null);
        if (match != null && customerFilterCombo != null) {
            customerFilterCombo.setValue(match);
        }
    }

    private void setupTable() {
        voucherNumberCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getVoucherNumber()));
        if (supplierInvoiceNumberCol != null) {
            supplierInvoiceNumberCol.setCellValueFactory(data ->
                    new SimpleStringProperty(safe(data.getValue().getSupplierInvoiceNumber())));
        }
        
        dateCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getVoucherDate().toLocalDate().format(dateFormatter)));
        
        customerCol.setCellValueFactory(data -> {
            Customer customer = data.getValue().getCustomer();
            return new SimpleStringProperty(customer != null ? customer.getName() : "نقدي");
        });
        
        amountCol.setCellValueFactory(data -> 
            new SimpleStringProperty(numberFormat.format(data.getValue().getNetAmount())));
        
        currencyCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getCurrency()));
        
        descriptionCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getDescription()));
        
        createdByCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getCreatedBy()));
        
        vouchersTable.setItems(vouchers);
        
        // Row styling for cancelled vouchers
        vouchersTable.setRowFactory(tv -> new TableRow<Voucher>() {
            @Override
            protected void updateItem(Voucher voucher, boolean empty) {
                super.updateItem(voucher, empty);
                if (voucher == null || empty) {
                    setStyle("");
                } else if (voucher.getIsCancelled()) {
                    setStyle("-fx-background-color: -fx-badge-danger-bg; -fx-text-fill: -fx-danger-text;");
                } else {
                    setStyle("");
                }
            }
        });
    }
    
    private void setupDatePickers() {
        fromDatePicker.setValue(LocalDate.now().minusMonths(1));
        toDatePicker.setValue(LocalDate.now());
    }
    
    private void updateTitle() {
        if (voucherType == VoucherType.RECEIPT) {
            titleLabel.setText("سندات القبض");
            setSupplierInvoiceColumnVisible(false);
            updateAccountLabels("اختر العميل", "العميل");
            titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -fx-success-text;");
        } else if (voucherType == VoucherType.PURCHASE) {
            titleLabel.setText("المشتريات");
            setSupplierInvoiceColumnVisible(true);
            updateAccountLabels("اختر المذخر", "المذخر");
            titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -fx-warning-text;");
        } else {
            titleLabel.setText("سندات الدفع");
            setSupplierInvoiceColumnVisible(false);
            updateAccountLabels("اختر المذخر", "المذخر");
            titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -fx-danger-text;");
        }
    }

    private void setSupplierInvoiceColumnVisible(boolean visible) {
        if (supplierInvoiceNumberCol != null) {
            supplierInvoiceNumberCol.setVisible(visible);
        }
    }

    private void updateAccountLabels(String promptText, String columnText) {
        if (customerFilterCombo != null) {
            customerFilterCombo.setPromptText(promptText);
        }
        if (customerCol != null) {
            customerCol.setText(columnText);
        }
    }
    
    private void loadVouchers() {
        List<Voucher> voucherList = voucherService.getVouchersByType(voucherType);
        vouchers.setAll(voucherList);
        updateSummary();
    }
    
    @FXML
    private void handleSearch() {
        String searchTerm = searchField != null ? searchField.getText() : null;
        Long customerId = selectedCustomer != null ? selectedCustomer.getId() : null;
        LocalDateTime from = fromDatePicker.getValue() != null ? 
            fromDatePicker.getValue().atStartOfDay() : null;
        LocalDateTime to = toDatePicker.getValue() != null ? 
            toDatePicker.getValue().atTime(23, 59, 59) : null;
        
        List<Voucher> results = voucherService.searchVouchers(searchTerm, voucherType, from, to, customerId);
        vouchers.setAll(results);
        updateSummary();
    }
    
    private void updateSummary() {
        int count = vouchers.size();
        double totalIqd = vouchers.stream()
            .filter(v -> "دينار".equals(v.getCurrency()))
            .mapToDouble(Voucher::getNetAmount)
            .sum();
        double totalUsd = vouchers.stream()
            .filter(v -> "دولار".equals(v.getCurrency()))
            .mapToDouble(Voucher::getNetAmount)
            .sum();
        
        totalCountLabel.setText(String.valueOf(count));
        totalIqdLabel.setText(numberFormat.format(totalIqd) + " د.ع");
        totalUsdLabel.setText(numberFormat.format(totalUsd) + " $");
    }
    
    @FXML
    private void viewVoucherDetails() {
        Voucher selected = vouchersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار سند");
            return;
        }
        
        StringBuilder details = new StringBuilder();
        details.append("رقم السند: ").append(selected.getVoucherNumber()).append("\n");
        if (selected.getSupplierInvoiceNumber() != null && !selected.getSupplierInvoiceNumber().isBlank()) {
            details.append("رقم قائمة المذخر: ").append(selected.getSupplierInvoiceNumber()).append("\n");
        }
        details.append("التاريخ: ").append(selected.getVoucherDate().toLocalDate()).append("\n");
        details.append(voucherType == VoucherType.RECEIPT ? "العميل: " : "المذخر: ")
                .append(selected.getCustomer() != null ? selected.getCustomer().getName() : "نقدي").append("\n");
        details.append("المبلغ: ").append(numberFormat.format(selected.getAmount())).append(" ").append(selected.getCurrency()).append("\n");
        details.append("الخصم: ").append(numberFormat.format(selected.getDiscountAmount())).append("\n");
        details.append("الصافي: ").append(numberFormat.format(selected.getNetAmount())).append("\n");
        details.append("المبلغ كتابةً: ").append(selected.getAmountInWords()).append("\n");
        details.append("البيان: ").append(selected.getDescription()).append("\n");
        details.append("بواسطة: ").append(selected.getCreatedBy()).append("\n");
        
        if (selected.getIsCancelled()) {
            details.append("\n*** ملغي ***\n");
            details.append("سبب الإلغاء: ").append(selected.getCancelReason()).append("\n");
            details.append("ألغي بواسطة: ").append(selected.getCancelledBy()).append("\n");
        }
        
        showAlert(Alert.AlertType.INFORMATION, "تفاصيل السند", details.toString());
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
    
    @FXML
    private void printVoucher() {
        Voucher selected = vouchersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار سند للطباعة");
            return;
        }

        try {
            File pdfFile = voucherService.generateVoucherReceiptPdf(selected.getId(), SessionManager.getInstance().getCurrentDisplayName());
            if (pdfFile != null && pdfFile.exists()) {
                showPdfPreview(pdfFile);
            } else {
                showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في إنشاء ملف الإيصال");
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في طباعة السند: " + e.getMessage());
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
            dialogStage.initOwner(vouchersTable.getScene().getWindow());
            Scene scene = new Scene(page);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
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
    private void handleClose() {
        Stage stage = (Stage) vouchersTable.getScene().getWindow();
        stage.close();
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
