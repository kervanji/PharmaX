package com.pharmax.controller;

import com.pharmax.model.Customer;
import com.pharmax.service.CustomerService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomerController {
    private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);
    
    @FXML private Label formTitleLabel;
    @FXML private TextField customerCodeField;
    @FXML private TextField customerNameField;
    @FXML private TextField phoneNumberField;
    @FXML private TextField addressField;
    @FXML private TextArea projectLocationsArea;
    @FXML private Button saveButton;
    
    private Stage dialogStage;
    private final CustomerService customerService = new CustomerService();
    private Customer editingCustomer = null;
    private boolean saved = false;
    private boolean tabMode = false;
    
    @FXML
    private void initialize() {
        // Set up phone number field validation
        phoneNumberField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                phoneNumberField.setText(oldVal);
            }
        });

        // Show next code in form for new customer
        try {
            customerCodeField.setText(customerService.previewNextCustomerCode());
        } catch (Exception e) {
            logger.warn("Failed to preview next customer code", e);
        }
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }
    
    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }
    
    public void setCustomer(Customer customer) {
        this.editingCustomer = customer;
        if (customer != null) {
            formTitleLabel.setText("تعديل بيانات العميل");
            saveButton.setText("تحديث");
            
            customerCodeField.setText(customer.getCustomerCode());
            customerNameField.setText(customer.getName());
            phoneNumberField.setText(customer.getPhoneNumber() != null ? customer.getPhoneNumber() : "");
            addressField.setText(customer.getAddress() != null ? customer.getAddress() : "");
            projectLocationsArea.setText(customer.getProjectLocation() != null ? customer.getProjectLocation() : "");
        }
    }
    
    public boolean isSaved() {
        return saved;
    }
    
    @FXML
    private void handleSave() {
        String name = customerNameField.getText().trim();
        String phone = phoneNumberField.getText().trim();
        String projectLocations = projectLocationsArea.getText().trim();
        
        if (name.isEmpty()) {
            showError("خطأ", "اسم العميل مطلوب");
            customerNameField.requestFocus();
            return;
        }

        if (phone.isEmpty()) {
            showError("خطأ", "رقم الهاتف مطلوب");
            phoneNumberField.requestFocus();
            return;
        }

        if (!phone.matches("^07\\d{9}$")) {
            showError("خطأ", "رقم الهاتف غير صالح (يجب أن يبدأ بـ 07 ويكون 11 رقم)");
            phoneNumberField.requestFocus();
            return;
        }
        
        if (projectLocations.isEmpty()) {
            showError("خطأ", "مواقع المشاريع مطلوبة (أدخل موقعاً واحداً على الأقل)");
            projectLocationsArea.requestFocus();
            return;
        }
        
        try {
            Customer customer = editingCustomer != null ? editingCustomer : new Customer();
            
            customer.setName(name);
            customer.setPhoneNumber(phone);
            customer.setAddress(addressField.getText().trim().isEmpty() ? null : addressField.getText().trim());
            customer.setProjectLocation(projectLocations);

            // Ensure customer code is set for new customers
            if (editingCustomer == null) {
                customer.setCustomerCode(customerCodeField.getText() != null ? customerCodeField.getText().trim() : null);
            }
            
            if (editingCustomer != null) {
                customerService.updateCustomer(customer);
                showInfo("تم", "تم تحديث بيانات العميل بنجاح");
                logger.info("Customer updated: {}", customer.getCustomerCode());
            } else {
                customerService.createCustomer(customer);
                showInfo("تم", "تم إضافة العميل بنجاح\nكود العميل: " + customer.getCustomerCode());
                logger.info("New customer created: {}", customer.getCustomerCode());
            }
            
            saved = true;
            closeForm();
            
        } catch (IllegalArgumentException e) {
            showError("خطأ في البيانات", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to save customer", e);
            showError("خطأ", "فشل في حفظ بيانات العميل: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleCancel() {
        closeForm();
    }
    
    private void closeForm() {
        if (tabMode) {
            com.pharmax.util.TabManager.getInstance().closeTab("new-customer");
        } else if (dialogStage != null) {
            dialogStage.close();
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
