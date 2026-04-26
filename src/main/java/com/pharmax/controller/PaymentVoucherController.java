package com.pharmax.controller;

import com.pharmax.model.*;
import com.pharmax.service.CustomerService;
import com.pharmax.service.VoucherService;
import com.pharmax.util.SessionManager;
import com.pharmax.util.TabManager;
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
    @FXML private ComboBox<String> projectNameField;
    @FXML private TextArea notesArea;
    @FXML private Label previousBalanceLabel;
    @FXML private Label currentBalanceLabel;
    @FXML private Label balanceIqdLabel;
    @FXML private Label balanceUsdLabel;
    @FXML private CheckBox printCheckbox;
    @FXML private VBox otherCurrenciesBox;
    
    // Installment fields
    @FXML private CheckBox installmentCheckbox;
    @FXML private HBox installmentOptionsBox;
    @FXML private TextField installmentCountField;
    @FXML private DatePicker firstInstallmentDatePicker;

    // Previous vouchers table
    @FXML private TableView<PreviousVoucherRow> previousVouchersTable;
    @FXML private TableColumn<PreviousVoucherRow, String> pvNumberColumn;
    @FXML private TableColumn<PreviousVoucherRow, String> pvDateColumn;
    @FXML private TableColumn<PreviousVoucherRow, String> pvAmountColumn;
    @FXML private TableColumn<PreviousVoucherRow, String> pvProjectColumn;
    @FXML private TableColumn<PreviousVoucherRow, String> pvRemainingColumn;
    
    private final VoucherService voucherService = new VoucherService();
    private final CustomerService customerService = new CustomerService();
    private final DecimalFormat numberFormat = new DecimalFormat("#,###.##");
    private static final String DEFAULT_CASH_ACCOUNT = "صندوق 181";
    
    private ObservableList<Customer> customers;
    private Customer selectedCustomer;
    private ObservableList<PreviousVoucherRow> previousVoucherSource;
    private FilteredList<PreviousVoucherRow> previousVoucherRows;

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
        handleNew();
    }

    private void setupPreviousVouchersTable() {
        if (previousVouchersTable == null) return;
        pvNumberColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().voucherNumber));
        pvDateColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().dateText));
        pvAmountColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().amountText));
        pvProjectColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().projectText));
        pvRemainingColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().remainingText));
        previousVoucherSource = FXCollections.observableArrayList();
        previousVoucherRows = new FilteredList<>(previousVoucherSource, r -> true);
        previousVouchersTable.setItems(previousVoucherRows);
    }

    private void loadPreviousVouchers() {
        if (previousVoucherSource == null) return;
        previousVoucherSource.clear();
        if (selectedCustomer == null || selectedCustomer.getId() == null) return;

        String currency = amountCurrencyCombo != null ? amountCurrencyCombo.getValue() : "دينار";
        boolean isUsd = "دولار".equals(currency);
        double currentBalance = isUsd ? selectedCustomer.getBalanceUsd() : selectedCustomer.getBalanceIqd();

        List<Voucher> vouchers = voucherService.getVouchersByCustomerAndType(selectedCustomer.getId(), VoucherType.PAYMENT);
        if (vouchers == null || vouchers.isEmpty()) return;

        vouchers = vouchers.stream()
                .filter(v -> v != null && currency.equals(v.getCurrency()))
                .toList();

        String selectedProject = null;
        if (projectNameField != null) {
            selectedProject = projectNameField.getValue();
            if ((selectedProject == null || selectedProject.isBlank()) && projectNameField.getEditor() != null) {
                selectedProject = projectNameField.getEditor().getText();
            }
        }
        final String projectFilter = (selectedProject != null && !selectedProject.isBlank()) ? selectedProject.trim() : null;

        double running = currentBalance;
        int displayCount = 0;
        
        for (Voucher v : vouchers) {
            String dateText = v.getVoucherDate() != null ? v.getVoucherDate().toLocalDate().toString() : "-";
            String amountText = numberFormat.format(v.getNetAmount() != null ? v.getNetAmount() : 0.0) + (isUsd ? " $" : " د.ع");
            String remainingText = numberFormat.format(running) + (isUsd ? " $" : " د.ع");
            String projectText = v.getProjectName() != null ? v.getProjectName() : "";

            boolean matchesProject = (projectFilter == null) || projectText.trim().equals(projectFilter);

            if (matchesProject && displayCount < 50) {
                previousVoucherSource.add(new PreviousVoucherRow(
                        v.getVoucherNumber() != null ? v.getVoucherNumber() : "-",
                        dateText, amountText, projectText, remainingText));
                displayCount++;
            }

            double net = v.getNetAmount() != null ? v.getNetAmount() : 0.0;
            running += net;
        }
    }
    
    private void setupForm() {
        voucherDatePicker.setValue(LocalDate.now());
        
        // تعبئة القوائم المنسدلة
        amountCurrencyCombo.setItems(FXCollections.observableArrayList("دينار", "دولار"));
        amountCurrencyCombo.setValue("دينار");
        firstInstallmentDatePicker.setValue(LocalDate.now().plusMonths(1));
        
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
        customers = FXCollections.observableArrayList(customerService.getAllCustomers());
        customerCombo.setItems(customers);
    }
    
    private void setupListeners() {
        customerCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            selectedCustomer = newVal;
            updateCustomerBalanceDisplay();
            updateProjectDropdown();
            updateDescription();
            loadPreviousVouchers();
        });

        if (projectNameField != null) {
            projectNameField.valueProperty().addListener((obs, o, n) -> {
                updateDescription();
                loadPreviousVouchers();
            });
            if (projectNameField.getEditor() != null) {
                projectNameField.getEditor().textProperty().addListener((obs, o, n) -> {
                    updateDescription();
                    loadPreviousVouchers();
                });
            }
        }
        
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
        });
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
            String project = null;
            if (projectNameField != null) {
                project = projectNameField.getValue();
                if ((project == null || project.isBlank()) && projectNameField.getEditor() != null) {
                    project = projectNameField.getEditor().getText();
                }
            }
            String projectPart = project != null && !project.trim().isEmpty() ? " / " + project.trim() : "";
            descriptionField.setText("دفع لحساب .. " + selectedCustomer.getName() + projectPart);
        } else {
            descriptionField.setText("");
        }
    }

    private void updateProjectDropdown() {
        if (projectNameField == null) {
            return;
        }
        projectNameField.getItems().clear();

        if (selectedCustomer == null) {
            projectNameField.setValue(null);
            return;
        }

        String locationsText = selectedCustomer.getProjectLocation();
        if (locationsText == null || locationsText.trim().isEmpty()) {
            projectNameField.setValue(null);
            return;
        }

        List<String> locations = locationsText.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        projectNameField.setItems(FXCollections.observableArrayList(locations));
        if (locations.size() == 1) {
            projectNameField.setValue(locations.get(0));
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
                double newBalance = currentBalance - netAmount;
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
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار الحساب/المورد");
                return;
            }
            
            double amount = parseAmount(amountField.getText());
            if (amount <= 0) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى إدخال مبلغ صحيح");
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

            String projectName = null;
            if (projectNameField != null) {
                projectName = projectNameField.getValue();
                if ((projectName == null || projectName.isBlank()) && projectNameField.getEditor() != null) {
                    projectName = projectNameField.getEditor().getText();
                }
            }
            voucher.setProjectName(projectName);

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
            
            handleNew();
            loadCustomers();
            
        } catch (Exception e) {
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
    
    @FXML
    private void handleNew() {
        voucherNumberField.setText(voucherService.generateVoucherNumber(VoucherType.PAYMENT));
        voucherDatePicker.setValue(LocalDate.now());
        customerCombo.setValue(null);
        amountField.setText("");
        discountPercentField.setText("0");
        discountAmountField.setText("0");
        descriptionField.setText("");
        if (projectNameField != null) {
            projectNameField.setValue(null);
            if (projectNameField.getEditor() != null) {
                projectNameField.getEditor().setText("");
            }
        }
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
            stage.setTitle("إضافة حساب/مورد جديد");
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
        if (selectedCustomer != null) {
            // TODO: Show customer statement for IQD
            showAlert(Alert.AlertType.INFORMATION, "كشف حساب", 
                "كشف حساب " + selectedCustomer.getName() + " بالدينار\nالرصيد: " + 
                numberFormat.format(selectedCustomer.getBalanceIqd()) + " د.ع");
        }
    }
    
    @FXML
    private void showCustomerStatementUsd() {
        if (selectedCustomer != null) {
            showAlert(Alert.AlertType.INFORMATION, "كشف حساب", 
                "كشف حساب " + selectedCustomer.getName() + " بالدولار\nالرصيد: " + 
                numberFormat.format(selectedCustomer.getBalanceUsd()) + " $");
        }
    }
    
    private static class PreviousVoucherRow {
        final String voucherNumber;
        final String dateText;
        final String amountText;
        final String projectText;
        final String remainingText;

        private PreviousVoucherRow(String voucherNumber, String dateText, String amountText,
                                   String projectText, String remainingText) {
            this.voucherNumber = voucherNumber;
            this.dateText = dateText;
            this.amountText = amountText;
            this.projectText = projectText;
            this.remainingText = remainingText;
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
