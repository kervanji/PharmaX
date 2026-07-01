package com.pharmax.controller;

import com.pharmax.service.LicenseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class ActivationController {
    @FXML private Label activationTitleLabel;
    @FXML private Label activationHintLabel;
    @FXML private TextField activationCodeField;
    @FXML private Label deviceIdLabel;
    @FXML private Label errorLabel;
    @FXML private Label statusLabel;
    @FXML private Button activateButton;

    private final LicenseService licenseService = new LicenseService();
    private Runnable onActivationSuccess;

    @FXML
    private void initialize() {
        if (deviceIdLabel != null) {
            deviceIdLabel.setText(licenseService.getDeviceFingerprintShort());
        }

        if (licenseService.isFirstInstall()) {
            if (activationTitleLabel != null) {
                activationTitleLabel.setText("تفعيل البرنامج");
                activationTitleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -fx-accent-color;");
            }
            if (activationHintLabel != null) {
                activationHintLabel.setText("مرحباً! أدخل كود التفعيل للبدء. لن يُطلب منك مرة أخرى بعد التفعيل.");
            }
        } else {
            if (activationTitleLabel != null) {
                activationTitleLabel.setText("انتهت الفترة التجريبية");
                activationTitleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -fx-danger-text;");
            }
            if (activationHintLabel != null) {
                activationHintLabel.setText("يرجى التواصل مع المطور للحصول على كود تفعيل دائم.");
            }
        }

        if (activationCodeField != null) {
            activationCodeField.setOnAction(e -> handleActivate());
        }

        Platform.runLater(() -> {
            if (activationCodeField != null) {
                activationCodeField.requestFocus();
            }
        });
    }

    @FXML
    private void handleActivate() {
        clearError();
        if (activateButton != null) {
            activateButton.setDisable(true);
        }
        if (statusLabel != null) {
            statusLabel.setText("جاري التحقق...");
        }

        String code = activationCodeField != null ? activationCodeField.getText() : "";
        LicenseService.ActivationResult result = licenseService.activate(code);

        if (result.success) {
            if (statusLabel != null) {
                statusLabel.setText(result.message);
            }
            if (onActivationSuccess != null) {
                Platform.runLater(onActivationSuccess);
            }
        } else {
            if (activateButton != null) {
                activateButton.setDisable(false);
            }
            if (statusLabel != null) {
                statusLabel.setText("");
            }
            showError(result.message);
        }
    }

    @FXML
    private void handleCopyDeviceCode() {
        if (deviceIdLabel != null && !deviceIdLabel.getText().isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(deviceIdLabel.getText());
            clipboard.setContent(content);
            if (statusLabel != null) {
                statusLabel.setText("تم نسخ كود الجهاز بنجاح");
            }
        }
    }

    @FXML
    private void handleExit() {
        Platform.exit();
        System.exit(0);
    }

    private void showError(String message) {
        if (errorLabel == null) {
            return;
        }
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        if (errorLabel == null) {
            return;
        }
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    public void setOnActivationSuccess(Runnable callback) {
        this.onActivationSuccess = callback;
    }
}
