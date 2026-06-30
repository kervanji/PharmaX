package com.pharmax.controller;

import com.pharmax.model.*;
import com.pharmax.service.CustomerService;
import com.pharmax.service.VoucherService;
import com.pharmax.util.SessionManager;
import com.pharmax.util.TabManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class PaymentVoucherController implements Initializable {
    
    @FXML private TextField voucherNumberField;
    @FXML private DatePicker voucherDatePicker;
    @FXML private ComboBox<Customer> customerCombo;
    @FXML private TextField amountField;
    @FXML private ComboBox<String> amountCurrencyCombo;
    @FXML private TextField discountPercentField;
    @FXML private TextField discountAmountField;
    @FXML private Label amountInWordsLabel;
    @FXML private TextField descriptionField;
    @FXML private TextArea notesArea;
    @FXML private TextField purchaseInvoiceSearchField;
    @FXML private Label previousBalanceLabel;
    @FXML private Label currentBalanceLabel;
    @FXML private Label balanceIqdLabel;
    @FXML private Label balanceUsdLabel;
    @FXML private CheckBox printCheckbox;
    @FXML private Button saveBtn;
    @FXML private VBox otherCurrenciesBox;
    
    // Installment fields
    @FXML private CheckBox installmentCheckbox;
    @FXML private HBox installmentOptionsBox;
    @FXML private TextField installmentCountField;
    @FXML private DatePicker firstInstallmentDatePicker;

    // Previous vouchers table
    @FXML private TableView<PreviousVoucherRow> previousVouchersTable;
    @FXML private TableColumn<PreviousVoucherRow, String> pvNumberColumn;
    @FXML private TableColumn<PreviousVoucherRow, String> pvSupplierInvoiceColumn;
    @FXML private TableColumn<PreviousVoucherRow, String> pvDateColumn;
    @FXML private TableColumn<PreviousVoucherRow, String> pvAmountColumn;
    @FXML private TableColumn<PreviousVoucherRow, String> pvRemainingColumn;
    @FXML private TableColumn<PreviousVoucherRow, String> pvStatusColumn;
    
    private final VoucherService voucherService = new VoucherService();
    private final CustomerService customerService = new CustomerService();
    private final DecimalFormat numberFormat = new DecimalFormat("#,###.##");
    private static final String PAID_STATUS_TEXT = "\u0645\u062f\u0641\u0648\u0639";
    private static final String UNPAID_STATUS_TEXT = "\u063a\u064a\u0631 \u0645\u062f\u0641\u0648\u0639";
    private static final String DEFAULT_CASH_ACCOUNT = "صندوق 181";
    
    private ObservableList<Customer> customers;
    private Customer selectedCustomer;
    private ObservableList<PreviousVoucherRow> previousVoucherSource;
    private FilteredList<PreviousVoucherRow> previousVoucherRows;
    private Long selectedPurchaseVoucherId;
    private String selectedPurchaseVoucherNumber;
    private boolean suppressTableSelectionEvents;

    private boolean tabMode = false;
    private String tabId;

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
        setupPreviousVouchersTable();
        setupListeners();
        resetForm(false);
    }

    private void setupPreviousVouchersTable() {
        if (previousVouchersTable == null) return;
        pvNumberColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().voucherNumber));
        if (pvSupplierInvoiceColumn != null) {
            pvSupplierInvoiceColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().supplierInvoiceNumber));
        }
        pvDateColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().dateText));
        pvAmountColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().amountText));
        pvRemainingColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().remainingText));
        if (pvStatusColumn != null) {
            pvStatusColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().statusText));
            pvStatusColumn.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String status, boolean empty) {
                    super.updateItem(status, empty);
                    if (empty || status == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(status);
                        setStyle(status.startsWith(PAID_STATUS_TEXT)
                                ? "-fx-text-fill: -fx-success-text; -fx-font-weight: bold;"
                                : "-fx-text-fill: -fx-warning-text; -fx-font-weight: bold;");
                    }
                }
            });
        }
        previousVoucherSource = FXCollections.observableArrayList();
        previousVoucherRows = new FilteredList<>(previousVoucherSource, r -> true);
        previousVouchersTable.setItems(previousVoucherRows);
        previousVouchersTable.setRowFactory(tv -> {
            TableRow<PreviousVoucherRow> row = new TableRow<>() {
                @Override
                protected void updateItem(PreviousVoucherRow item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setDisable(false);
                        setStyle("");
                    } else if (item.paid) {
                        setDisable(true);
                        setStyle("-fx-opacity: 0.55; -fx-cursor: default;");
                    } else {
                        setDisable(false);
                        setStyle("");
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && row.getItem() != null && row.getItem().paid) {
                    event.consume();
                    clearTableSelectionSafely();
                    showAlert(Alert.AlertType.INFORMATION, "معلومة", "هذا الوصل مدفوع مسبقاً ولا يمكن دفعه مرة أخرى");
                }
            });
            return row;
        });
        previousVouchersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, row) -> {
            if (suppressTableSelectionEvents || row == null) {
                return;
            }
            applyPurchaseInvoiceToPayment(row);
        });
    }

    private void loadPreviousVouchers() {
        if (previousVoucherSource == null) return;
        previousVoucherSource.clear();
        if (selectedCustomer == null || selectedCustomer.getId() == null) return;

        String currency = amountCurrencyCombo != null ? amountCurrencyCombo.getValue() : "دينار";
        boolean isUsd = "دولار".equals(currency);
        double currentBalance = isUsd ? selectedCustomer.getBalanceUsd() : selectedCustomer.getBalanceIqd();

        List<Voucher> vouchers = voucherService.getVouchersByCustomerAndType(selectedCustomer.getId(), VoucherType.PURCHASE);
        if (vouchers == null || vouchers.isEmpty()) return;

        vouchers = vouchers.stream()
                .filter(v -> v != null && currency.equals(v.getCurrency()))
                .toList();

        double running = currentBalance;
        int displayCount = 0;
        
        for (Voucher v : vouchers) {
            String dateText = v.getVoucherDate() != null ? v.getVoucherDate().toLocalDate().toString() : "-";
            double netAmount = v.getNetAmount() != null ? v.getNetAmount() : 0.0;
            boolean paid = voucherService.isPurchaseVoucherPaid(v.getId());
            Voucher paymentVoucher = paid ? voucherService.findPaymentForPurchaseVoucher(v.getId()).orElse(null) : null;
            String statusText = paid
                    ? PAID_STATUS_TEXT + (paymentVoucher != null && paymentVoucher.getVoucherNumber() != null
                    ? " - " + paymentVoucher.getVoucherNumber()
                    : "")
                    : UNPAID_STATUS_TEXT;
            String amountText = numberFormat.format(netAmount) + (isUsd ? " $" : " د.ع");
            String remainingText = numberFormat.format(running) + (isUsd ? " $" : " د.ع");

            if (displayCount < 50) {
                previousVoucherSource.add(new PreviousVoucherRow(
                        v.getId(),
                        v.getVoucherNumber() != null ? v.getVoucherNumber() : "-",
                        safe(v.getSupplierInvoiceNumber()),
                        dateText, amountText, remainingText, netAmount, v.getCurrency(), paid, statusText));
                displayCount++;
            }

            running += netAmount;
        }
        applyPurchaseInvoiceFilter();
        updateSaveButtonState();
    }
    
    private void setupForm() {
        voucherDatePicker.setValue(LocalDate.now());
        
        // تعبئة القوائم المنسدلة
        amountCurrencyCombo.setItems(FXCollections.observableArrayList("دينار", "دولار"));
        amountCurrencyCombo.setValue("دينار");
        firstInstallmentDatePicker.setValue(LocalDate.now().plusMonths(1));

        if (amountField != null) {
            amountField.setEditable(false);
        }
        
        customerCombo.setConverter(new StringConverter<Customer>() {
            @Override
            public String toString(Customer customer) {
                if (customer == null) return "";
                return customer.getCustomerCode() + " - " + customer.getName();
            }
            
            @Override
            public Customer fromString(String s) {
                if (s == null || s.isEmpty()) return null;
                return customers.stream()
                    .filter(c -> (c.getCustomerCode() + " - " + c.getName()).equals(s) || c.getName().contains(s))
                    .findFirst().orElse(null);
            }
        });
    }
    
    private void loadCustomers() {
        customers = FXCollections.observableArrayList(customerService.getSuppliers());
        customerCombo.setItems(customers);
    }
    
    private void setupListeners() {
        customerCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            selectedCustomer = newVal;
            clearPurchaseInvoiceSelection();
            updateCustomerBalanceDisplay();
            updateDescription();
            loadPreviousVouchers();
        });
        
        amountField.textProperty().addListener((obs, oldVal, newVal) -> {
            calculateNetAmount();
            updateAmountInWords();
        });
        
        discountPercentField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty()) {
                try {
                    double percent = Double.parseDouble(newVal);
                    double amount = parseAmount(amountField.getText());
                    double discountAmount = amount * percent / 100;
                    discountAmountField.setText(numberFormat.format(discountAmount));
                    calculateNetAmount();
                } catch (NumberFormatException ignored) {}
            }
        });
        
        discountAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
            calculateNetAmount();
            updateAmountInWords();
        });

        amountCurrencyCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateAmountInWords();
            calculateNetAmount();
            loadPreviousVouchers();
        });

        if (purchaseInvoiceSearchField != null) {
            purchaseInvoiceSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyPurchaseInvoiceFilter());
        }
    }
    
    
    private void updateCustomerBalanceDisplay() {
        if (selectedCustomer != null) {
            double balanceIqd = selectedCustomer.getBalanceIqd();
            double balanceUsd = selectedCustomer.getBalanceUsd();
            
            previousBalanceLabel.setText(numberFormat.format(balanceIqd));
            currentBalanceLabel.setText(numberFormat.format(balanceIqd) + " د.ع");
            balanceIqdLabel.setText(numberFormat.format(balanceIqd));
            balanceUsdLabel.setText(numberFormat.format(balanceUsd));
        } else {
            previousBalanceLabel.setText("0");
            currentBalanceLabel.setText("0 د.ع");
            balanceIqdLabel.setText("0");
            balanceUsdLabel.setText("0");
        }
    }
    
    private void updateDescription() {
        if (selectedCustomer != null) {
            descriptionField.setText("دفع إلى مذخر .. " + selectedCustomer.getName());
        } else {
            descriptionField.setText("");
        }
    }
    
    private void calculateNetAmount() {
        try {
            double amount = parseAmount(amountField.getText());
            double discount = parseAmount(discountAmountField.getText());
            double netAmount = amount - discount;
            
            if (selectedCustomer != null) {
                String currency = amountCurrencyCombo.getValue();
                double currentBalance = "دولار".equals(currency) ? 
                    selectedCustomer.getBalanceUsd() : selectedCustomer.getBalanceIqd();
                double newBalance = currentBalance + netAmount;
                currentBalanceLabel.setText(numberFormat.format(newBalance) + 
                    ("دولار".equals(currency) ? " $" : " د.ع"));
            }
        } catch (Exception ignored) {}
    }
    
    private void updateAmountInWords() {
        try {
            double amount = parseAmount(amountField.getText());
            double discount = parseAmount(discountAmountField.getText());
            double netAmount = amount - discount;
            String currency = amountCurrencyCombo.getValue();
            
            String words = convertToWords(netAmount, currency);
            amountInWordsLabel.setText(words);
        } catch (Exception e) {
            amountInWordsLabel.setText("صفر");
        }
    }
    
    private double parseAmount(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private String convertToWords(double amount, String currency) {
        if (amount == 0) return "صفر";
        
        long wholeNumber = (long) amount;
        String currencyName = "دينار".equals(currency) ? "دينار عراقي" : "دولار أمريكي";
        
        return convertNumberToArabic(wholeNumber) + " " + currencyName + " لا غير";
    }
    
    private String convertNumberToArabic(long number) {
        if (number == 0) return "صفر";
        
        String[] ones = {"", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة",
                "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر"};
        String[] tens = {"", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون"};
        
        if (number < 20) return ones[(int) number];
        if (number < 100) {
            int remainder = (int) (number % 10);
            if (remainder == 0) return tens[(int) (number / 10)];
            return ones[remainder] + " و" + tens[(int) (number / 10)];
        }
        if (number < 1000) {
            int hundreds = (int) (number / 100);
            int remainder = (int) (number % 100);
            String hundredWord = hundreds == 1 ? "مائة" : hundreds == 2 ? "مائتان" : ones[hundreds] + " مائة";
            if (remainder == 0) return hundredWord;
            return hundredWord + " و" + convertNumberToArabic(remainder);
        }
        if (number < 1000000) {
            int thousands = (int) (number / 1000);
            int remainder = (int) (number % 1000);
            String thousandWord;
            if (thousands == 1) thousandWord = "ألف";
            else if (thousands == 2) thousandWord = "ألفان";
            else if (thousands <= 10) thousandWord = ones[thousands] + " آلاف";
            else thousandWord = convertNumberToArabic(thousands) + " ألف";
            
            if (remainder == 0) return thousandWord;
            return thousandWord + " و" + convertNumberToArabic(remainder);
        }
        
        return String.valueOf(number);
    }
    
    @FXML
    private void toggleInstallmentOptions() {
        boolean show = installmentCheckbox.isSelected();
        installmentOptionsBox.setVisible(show);
        installmentOptionsBox.setManaged(show);
    }
    
    
    @FXML
    private void handleSave() {
        try {
            if (selectedCustomer == null) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار المذخر");
                return;
            }

            if (selectedPurchaseVoucherId == null) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار فاتورة مشتريات غير مدفوعة من القائمة");
                return;
            }

            if (voucherService.isPurchaseVoucherPaid(selectedPurchaseVoucherId)) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "تم دفع هذا الوصل مسبقاً ولا يمكن دفعه مرة أخرى");
                clearPurchaseInvoiceSelection();
                loadPreviousVouchers();
                return;
            }
            
            double amount = parseAmount(amountField.getText());
            if (amount <= 0) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار فاتورة مشتريات صحيحة من القائمة");
                return;
            }

            // Create voucher
            Voucher voucher = new Voucher();
            voucher.setVoucherType(VoucherType.PAYMENT);
            voucher.setVoucherNumber(voucherNumberField.getText());
            voucher.setVoucherDate(voucherDatePicker.getValue().atStartOfDay());
            voucher.setCurrency(amountCurrencyCombo.getValue());
            voucher.setExchangeRate(1.0);
            voucher.setCustomer(selectedCustomer);
            voucher.setCashAccount(DEFAULT_CASH_ACCOUNT);
            voucher.setAmount(amount);
            voucher.setDiscountPercentage(parseAmount(discountPercentField.getText()));
            voucher.setDiscountAmount(parseAmount(discountAmountField.getText()));
            voucher.setNetAmount(amount - parseAmount(discountAmountField.getText()));
            voucher.setAmountInWords(amountInWordsLabel.getText());
            voucher.setDescription(descriptionField.getText());
            voucher.setParentVoucherId(selectedPurchaseVoucherId);
            voucher.setReferenceNumber(selectedPurchaseVoucherNumber);

            if (notesArea != null && notesArea.getText() != null && !notesArea.getText().isEmpty()) {
                voucher.setNotes(notesArea.getText());
            }
            voucher.setCreatedBy(SessionManager.getInstance().getCurrentUser() != null ? 
                SessionManager.getInstance().getCurrentUser().getDisplayName() : "System");
            
            // Save with or without installments
            if (installmentCheckbox.isSelected()) {
                int installmentCount = Integer.parseInt(installmentCountField.getText());
                LocalDate firstDate = firstInstallmentDatePicker.getValue();
                voucher = voucherService.saveVoucherWithInstallments(voucher, installmentCount, firstDate);
            } else {
                voucher = voucherService.saveVoucher(voucher);
            }
            
            showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم حفظ سند الدفع بنجاح: " + voucher.getVoucherNumber());
            
            if (printCheckbox.isSelected()) {
                File pdfFile = voucherService.generateVoucherReceiptPdf(voucher.getId(), SessionManager.getInstance().getCurrentDisplayName());
                if (pdfFile != null && pdfFile.exists()) {
                    showPdfPreview(pdfFile);
                }
            }
            
            resetForm(true);
            loadCustomers();
            
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("تم دفع هذا الوصل مسبقاً")) {
                clearPurchaseInvoiceSelection();
                loadPreviousVouchers();
            }
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE") && e.getMessage().contains("voucher_number")) {
                voucherNumberField.setText(voucherService.generateVoucherNumber(VoucherType.PAYMENT));
            }
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في حفظ السند: " + e.getMessage());
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
    
    private void resetForm(boolean keepCustomer) {
        voucherNumberField.setText(voucherService.generateVoucherNumber(VoucherType.PAYMENT));
        voucherDatePicker.setValue(LocalDate.now());
        amountField.setText("");
        discountPercentField.setText("0");
        discountAmountField.setText("0");
        if (notesArea != null) {
            notesArea.setText("");
        }
        amountInWordsLabel.setText("صفر");
        printCheckbox.setSelected(false);
        installmentCheckbox.setSelected(false);
        installmentOptionsBox.setVisible(false);
        installmentOptionsBox.setManaged(false);
        installmentCountField.setText("");
        firstInstallmentDatePicker.setValue(LocalDate.now().plusMonths(1));

        clearPurchaseInvoiceSelection();

        if (keepCustomer && selectedCustomer != null) {
            updateDescription();
            updateCustomerBalanceDisplay();
            loadPreviousVouchers();
        } else {
            customerCombo.setValue(null);
            selectedCustomer = null;
            descriptionField.setText("");
            updateCustomerBalanceDisplay();
            if (purchaseInvoiceSearchField != null) {
                purchaseInvoiceSearchField.clear();
            }
            if (previousVoucherSource != null) {
                previousVoucherSource.clear();
            }
        }
        updateSaveButtonState();
    }

    private void applyPurchaseInvoiceFilter() {
        if (previousVoucherRows == null) {
            return;
        }
        String query = purchaseInvoiceSearchField != null && purchaseInvoiceSearchField.getText() != null
                ? purchaseInvoiceSearchField.getText().trim().toLowerCase()
                : "";
        previousVoucherRows.setPredicate(row -> {
            if (query.isEmpty()) {
                return true;
            }
            return contains(row.voucherNumber, query)
                    || contains(row.supplierInvoiceNumber, query)
                    || contains(row.dateText, query)
                    || contains(row.amountText, query)
                    || contains(row.statusText, query);
        });
    }

    private void applyPurchaseInvoiceToPayment(PreviousVoucherRow row) {
        if (row.paid || voucherService.isPurchaseVoucherPaid(row.voucherId)) {
            clearPurchaseInvoiceFormFields();
            clearTableSelectionSafely();
            showAlert(Alert.AlertType.INFORMATION, "معلومة", "هذا الوصل مدفوع مسبقاً ولا يمكن دفعه مرة أخرى");
            return;
        }

        selectedPurchaseVoucherId = row.voucherId;
        selectedPurchaseVoucherNumber = row.voucherNumber;
        amountField.setText(numberFormat.format(row.amount));
        if (amountCurrencyCombo != null && row.currency != null && !row.currency.isBlank()) {
            amountCurrencyCombo.setValue(row.currency);
        }
        String listNo = row.supplierInvoiceNumber != null && !row.supplierInvoiceNumber.isBlank()
                ? " / قائمة المذخر " + row.supplierInvoiceNumber
                : "";
        descriptionField.setText("دفع إلى مذخر .. "
                + (selectedCustomer != null ? selectedCustomer.getName() : "")
                + " عن مشتريات " + row.voucherNumber + listNo);
        calculateNetAmount();
        updateAmountInWords();
        updateSaveButtonState();
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private void clearSelectedPurchaseInvoice() {
        selectedPurchaseVoucherId = null;
        selectedPurchaseVoucherNumber = null;
    }

    private void clearPurchaseInvoiceSelection() {
        clearPurchaseInvoiceFormFields();
        clearTableSelectionSafely();
    }

    private void clearPurchaseInvoiceFormFields() {
        clearSelectedPurchaseInvoice();
        amountField.setText("");
        discountPercentField.setText("0");
        discountAmountField.setText("0");
        amountInWordsLabel.setText("صفر");
        if (selectedCustomer != null) {
            updateDescription();
            calculateNetAmount();
        }
        updateSaveButtonState();
    }

    private void clearTableSelectionSafely() {
        if (previousVouchersTable == null) {
            return;
        }
        suppressTableSelectionEvents = true;
        Platform.runLater(() -> {
            try {
                previousVouchersTable.getSelectionModel().clearSelection();
            } finally {
                suppressTableSelectionEvents = false;
            }
        });
    }

    private void updateSaveButtonState() {
        if (saveBtn == null) {
            return;
        }
        boolean canSave = selectedCustomer != null
                && selectedPurchaseVoucherId != null
                && !voucherService.isPurchaseVoucherPaid(selectedPurchaseVoucherId);
        saveBtn.setDisable(!canSave);
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
            CustomerController controller = loader.getController();
            controller.setSupplierMode();
            
            Stage stage = new Stage();
            stage.setTitle("إضافة مذخر جديد");
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
            
            loadCustomers();
            
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في فتح نافذة إضافة المذخر");
        }
    }
    
    @FXML
    private void showPreviousVouchers() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/VoucherList.fxml"));
            Parent root = loader.load();
            
            VoucherListController controller = loader.getController();
            controller.setVoucherType(VoucherType.PAYMENT);
            
            Stage stage = new Stage();
            stage.setTitle("سندات الدفع السابقة");
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
            
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في فتح قائمة السندات");
        }
    }
    
    @FXML
    private void showCustomerStatement() {
        openCustomerStatement("دينار");
    }
    
    @FXML
    private void showCustomerStatementUsd() {
        openCustomerStatement("دولار");
    }

    private void openCustomerStatement(String currency) {
        if (selectedCustomer == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار المذخر أولاً");
            return;
        }

        try {
            String tabId = "accounts-payment-statement-" + System.nanoTime();
            String title = "كشف حساب " + currency + " - " + selectedCustomer.getName();
            AccountsController controller = TabManager.getInstance().openTab(
                    tabId,
                    title,
                    "statement.svg",
                    "/views/Accounts.fxml",
                    (AccountsController accountsController) -> {
                        accountsController.setTabMode(true);
                        accountsController.setTabId(tabId);
                        accountsController.applyInitialFilters(selectedCustomer, currency);
                    });

            if (controller == null) {
                showCustomerStatementFallback(currency);
            }
        } catch (Exception e) {
            showCustomerStatementFallback(currency);
        }
    }

    private void showCustomerStatementFallback(String currency) {
        boolean usd = "دولار".equals(currency) || "USD".equalsIgnoreCase(currency);
        double balance = usd ? selectedCustomer.getBalanceUsd() : selectedCustomer.getBalanceIqd();
        String suffix = usd ? " $" : " د.ع";
        showAlert(Alert.AlertType.INFORMATION, "كشف حساب",
                "تعذر فتح شاشة كشف الحساب.\n"
                        + "كشف حساب " + selectedCustomer.getName() + " ب" + (usd ? "الدولار" : "الدينار")
                        + "\nالرصيد: " + numberFormat.format(balance) + suffix);
    }
    
    private static class PreviousVoucherRow {
        final Long voucherId;
        final String voucherNumber;
        final String supplierInvoiceNumber;
        final String dateText;
        final String amountText;
        final String remainingText;
        final double amount;
        final String currency;
        final boolean paid;
        final String statusText;

        private PreviousVoucherRow(Long voucherId, String voucherNumber, String supplierInvoiceNumber, String dateText,
                                   String amountText, String remainingText, double amount, String currency,
                                   boolean paid, String statusText) {
            this.voucherId = voucherId;
            this.voucherNumber = voucherNumber;
            this.supplierInvoiceNumber = supplierInvoiceNumber;
            this.dateText = dateText;
            this.amountText = amountText;
            this.remainingText = remainingText;
            this.amount = amount;
            this.currency = currency;
            this.paid = paid;
            this.statusText = statusText;
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
