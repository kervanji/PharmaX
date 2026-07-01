package com.pharmax.controller;

import com.pharmax.MainApp;
import com.pharmax.model.Customer;
import com.pharmax.model.Product;
import com.pharmax.model.Sale;
import com.pharmax.service.CustomerService;
import com.pharmax.service.BackupRestoreService;
import com.pharmax.service.InventoryService;
import com.pharmax.service.ReceiptService;
import com.pharmax.service.SalesService;
import com.pharmax.util.AppConfigStore;
import com.pharmax.util.SessionManager;
import com.pharmax.util.TabManager;
import com.pharmax.util.ThemeManager;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;

import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.concurrent.Task;
import java.util.List;
import com.pharmax.service.drive.GoogleDriveService.BackupFile;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;

import java.util.prefs.Preferences;

public class SettingsController {
    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);
    private static final String PREF_BANNER_PATH = "receipt.banner.path";
    private static final String PREF_COMPANY_NAME = "company.name";

    @FXML
    private TextField backupPathField;
    @FXML
    private TextField restorePathField;
    @FXML
    private TextField bannerPathField;
    @FXML
    private TextField receiptsPathField;
    @FXML
    private TextField companyNameField;
    @FXML
    private Slider fontSizeSlider;
    @FXML
    private Label fontSizeValueLabel;
    @FXML
    private Label backupStatusLabel;
    @FXML
    private Label customersCountLabel;
    @FXML
    private Label productsCountLabel;
    @FXML
    private Label salesCountLabel;
    @FXML
    private Label receiptsCountLabel;
    @FXML
    private Label dbSizeLabel;
    @FXML
    private Label driveStatusLabel;
    @FXML
    private Label lastBackupLabel;
    @FXML
    private ProgressBar backupProgressBar;
    @FXML
    private ComboBox<ThemeManager.ThemeType> themeComboBox;
    @FXML
    private CheckBox allowQuickSaleCheckBox;
    @FXML
    private CheckBox defaultQuickSaleCheckBox;

    private com.pharmax.service.drive.BackupService backupService;
    private final CustomerService customerService = new CustomerService();
    private final InventoryService inventoryService = new InventoryService();
    private final SalesService salesService = new SalesService();
    private final ReceiptService receiptService = new ReceiptService();
    private final BackupRestoreService backupRestoreService = new BackupRestoreService();
    private final AppConfigStore configStore = new AppConfigStore();
    private Stage dialogStage;
    private static final String QUICK_SALE_ENABLED_KEY = "sale.quick.enabled";
    private static final String QUICK_SALE_DEFAULT_KEY = "sale.quick.default";

    @FXML
    private void initialize() {
        // Set default backup path
        backupPathField.setText(System.getProperty("user.home") + File.separator + "PharmaX_Backups");

        // Load preferences
        Preferences prefs = Preferences.userNodeForPackage(ReceiptService.class);
        String bannerPath = prefs.get(PREF_BANNER_PATH, "");
        bannerPathField.setText(bannerPath);

        // Load company name
        String companyName = prefs.get(PREF_COMPANY_NAME, "");
        if (companyNameField != null) {
            companyNameField.setText(companyName);
        }

        // Load statistics
        handleRefreshStats();

        // Load UI font size
        if (fontSizeSlider != null && fontSizeValueLabel != null) {
            int size = SessionManager.getInstance().getUiFontSize();
            fontSizeSlider.setValue(size);
            fontSizeValueLabel.setText(size + "px");
            fontSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                int rounded = (int) Math.round(newVal.doubleValue());
                fontSizeValueLabel.setText(rounded + "px");
                SessionManager.getInstance().setUiFontSize(rounded);

                com.pharmax.MainApp app = TabManager.getInstance().getMainApp();
                if (app != null) {
                    app.applyUiFontSizeToAllWindows(rounded);
                } else if (dialogStage != null && dialogStage.getScene() != null) {
                    dialogStage.getScene().getRoot().setStyle(
                            com.pharmax.MainApp.upsertFontSizeStyle(dialogStage.getScene().getRoot().getStyle(),
                                    rounded));
                }
            });
        }

        // Initialize Theme ComboBox
        if (themeComboBox != null) {
            ThemeManager tm = ThemeManager.getInstance();
            themeComboBox.getItems().addAll(tm.getAvailableThemes());
            themeComboBox.setValue(tm.getCurrentTheme());
            themeComboBox.setOnAction(e -> {
                ThemeManager.ThemeType selected = themeComboBox.getValue();
                if (selected != null) {
                    tm.setTheme(selected);
                }
            });
        }

        loadSaleOptions();

        // Initialize Drive Services
        com.pharmax.MainApp app = TabManager.getInstance().getMainApp();
        if (app != null) {
            this.backupService = app.getBackupService();
            updateDriveStatus();
        }
    }

    private void loadSaleOptions() {
        var props = configStore.load();
        boolean allowQuickSale = Boolean.parseBoolean(props.getProperty(QUICK_SALE_ENABLED_KEY, "false"));
        boolean defaultQuickSale = Boolean.parseBoolean(props.getProperty(QUICK_SALE_DEFAULT_KEY, "false"));

        if (allowQuickSaleCheckBox != null) {
            allowQuickSaleCheckBox.setSelected(allowQuickSale);
            allowQuickSaleCheckBox.selectedProperty().addListener((obs, oldVal, selected) -> {
                if (!selected && defaultQuickSaleCheckBox != null) {
                    defaultQuickSaleCheckBox.setSelected(false);
                }
                if (defaultQuickSaleCheckBox != null) {
                    defaultQuickSaleCheckBox.setDisable(!selected);
                }
            });
        }
        if (defaultQuickSaleCheckBox != null) {
            defaultQuickSaleCheckBox.setSelected(allowQuickSale && defaultQuickSale);
            defaultQuickSaleCheckBox.setDisable(!allowQuickSale);
        }
    }

    private void updateDriveStatus() {
        if (backupService == null)
            return;

        boolean connected = backupService.isDriveConnected();

        javafx.application.Platform.runLater(() -> {
            if (connected) {
                driveStatusLabel.setText("متصل (Google Drive)");
                driveStatusLabel.setStyle("-fx-text-fill: -fx-success-text; -fx-font-weight: bold;"); // Green
            } else {
                driveStatusLabel.setText("غير متصل");
                driveStatusLabel.setStyle("-fx-text-fill: -fx-danger-text; -fx-font-weight: bold;"); // Red
            }
        });
    }

    @FXML
    private void handleConnectGoogleDrive() {
        if (backupService == null) {
            showError("خطأ", "خدمة النسخ الاحتياطي غير متوفرة");
            return;
        }

        if (backupService.isDriveConnected()) {
            showInfo("Google Drive", "الحساب متصل بالفعل.");
            return;
        }

        com.pharmax.MainApp app = TabManager.getInstance().getMainApp();
        com.pharmax.service.drive.GoogleDriveService driveService = app != null ? app.getGoogleDriveService() : null;
        if (driveService == null) {
            showError("خطأ", "خدمة Google Drive غير متوفرة");
            return;
        }

        javafx.application.Platform.runLater(() -> {
            driveStatusLabel.setText("جارِ الاتصال... سيتم فتح المتصفح");
            driveStatusLabel.setStyle("-fx-text-fill: -fx-warning-text;"); // Orange
        });

        new Thread(() -> {
            try {
                driveService.initialize();
                backupService.startHourlyBackup();
                logger.info("Google Drive connected successfully via button");

                javafx.application.Platform.runLater(() -> {
                    driveStatusLabel.setText("متصل (Google Drive)");
                    driveStatusLabel.setStyle("-fx-text-fill: -fx-success-text; -fx-font-weight: bold;");
                    showInfo("Google Drive", "تم الاتصال بـ Google Drive بنجاح!");
                });
            } catch (Exception e) {
                logger.error("Failed to connect to Google Drive", e);
                javafx.application.Platform.runLater(() -> {
                    driveStatusLabel.setText("غير متصل");
                    driveStatusLabel.setStyle("-fx-text-fill: -fx-danger-text; -fx-font-weight: bold;");
                    showError("خطأ", "فشل الاتصال بـ Google Drive: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void handleBackupNow() {
        if (backupService == null)
            return;

        if (!backupService.isDriveConnected()) {
            showError("خطأ", "يجب الاتصال بـ Google Drive أولاً");
            return;
        }

        if (backupProgressBar != null)
            backupProgressBar.setVisible(true);
        if (lastBackupLabel != null)
            lastBackupLabel.setText("جارِ النسخ الاحتياطي...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                backupService.performBackup();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            if (backupProgressBar != null)
                backupProgressBar.setVisible(false);
            if (lastBackupLabel != null)
                lastBackupLabel.setText("آخر نسخة احتياطية: " +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            showInfo("تم بنجاح", "تم الانتهاء من النسخ الاحتياطي السحابي");
        });

        task.setOnFailed(e -> {
            if (backupProgressBar != null)
                backupProgressBar.setVisible(false);
            if (lastBackupLabel != null)
                lastBackupLabel.setText("فشل النسخ الاحتياطي");
            showError("خطأ", "فشل النسخ الاحتياطي: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private static final String BACKUP_DOWNLOAD_DIR = "C:\\PharmaX";

    @FXML
    private void handleRestoreFromCloud() {
        if (backupService == null || !backupService.isDriveConnected()) {
            showError("خطأ", "يجب الاتصال بـ Google Drive أولاً");
            return;
        }

        if (backupProgressBar != null)
            backupProgressBar.setVisible(true);
        if (lastBackupLabel != null)
            lastBackupLabel.setText("جارِ جلب قائمة النسخ الاحتياطية...");

        Task<List<BackupFile>> listTask = new Task<>() {
            @Override
            protected List<BackupFile> call() throws Exception {
                return backupService.listCloudBackups();
            }
        };

        listTask.setOnSucceeded(e -> {
            if (backupProgressBar != null)
                backupProgressBar.setVisible(false);
            if (lastBackupLabel != null)
                lastBackupLabel.setText("");
            List<BackupFile> backups = listTask.getValue();
            if (backups.isEmpty()) {
                showInfo("Google Drive", "لا توجد نسخ احتياطية متاحة في Google Drive.");
                return;
            }
            showBackupSelectionDialog(backups);
        });

        listTask.setOnFailed(e -> {
            if (backupProgressBar != null)
                backupProgressBar.setVisible(false);
            if (lastBackupLabel != null)
                lastBackupLabel.setText("");
            showError("خطأ", "فشل جلب قائمة النسخ الاحتياطية: " + listTask.getException().getMessage());
        });

        new Thread(listTask).start();
    }

    private void showBackupSelectionDialog(List<BackupFile> backups) {
        // Ensure download directory exists
        File downloadDir = new File(BACKUP_DOWNLOAD_DIR);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        Dialog<File> dialog = new Dialog<>();
        dialog.setTitle("استعادة من السحابة");
        dialog.setHeaderText("النسخ الاحتياطية المتاحة في Google Drive\nاختر نسخة لتنزيلها ثم استعادتها");
        dialog.getDialogPane().setPrefWidth(620);
        dialog.getDialogPane().setPrefHeight(500);
        applyDialogTheme(dialog);

        ButtonType restoreButtonType = new ButtonType("استعادة النسخة المحددة", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(restoreButtonType, ButtonType.CANCEL);

        Node restoreBtn = dialog.getDialogPane().lookupButton(restoreButtonType);
        restoreBtn.setDisable(true);

        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 10;");

        Label infoLabel = new Label("سيتم تنزيل النسخة إلى: " + BACKUP_DOWNLOAD_DIR);
        infoLabel.getStyleClass().add("info-hint");
        content.getChildren().add(infoLabel);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(350);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox backupListBox = new VBox(6);
        backupListBox.setStyle("-fx-padding: 5;");

        final File[] selectedDbFile = { null };
        final HBox[] selectedRow = { null };
        ToggleGroup restoreGroup = new ToggleGroup();

        DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss");

        for (BackupFile backup : backups) {
            HBox row = new HBox(10);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.getStyleClass().add("backup-row");

            RadioButton selectRadio = new RadioButton();
            selectRadio.setToggleGroup(restoreGroup);
            selectRadio.setFocusTraversable(false);

            Label statusIcon = new Label("☁");
            statusIcon.setMinWidth(24);
            statusIcon.getStyleClass().add("backup-status-icon");

            VBox infoBox = new VBox(2);
            HBox.setHgrow(infoBox, Priority.ALWAYS);

            Label nameLabel = new Label(backup.getTimestamp().format(displayFmt));
            nameLabel.getStyleClass().addAll("backup-row-title", "text-subtitle");

            String sizeText = backup.getSize() > 0 ? String.format("%.1f KB", backup.getSize() / 1024.0) : "";
            Label detailLabel = new Label(backup.getName() + (sizeText.isEmpty() ? "" : "  •  " + sizeText));
            detailLabel.getStyleClass().addAll("backup-row-detail", "text-small");

            infoBox.getChildren().addAll(nameLabel, detailLabel);

            Button downloadBtn = new Button("تنزيل");
            downloadBtn.getStyleClass().add("button-primary");

            String expectedZipName = backup.getName();
            String expectedDbName = expectedZipName.replace(".zip", ".db");
            File existingDb = new File(BACKUP_DOWNLOAD_DIR, expectedDbName);
            if (existingDb.exists()) {
                statusIcon.setText("✓");
                statusIcon.getStyleClass().add("status-success");
                downloadBtn.setText("تم التنزيل ✓");
                downloadBtn.getStyleClass().add("button-success");
            }

            Runnable updateRestoreSelection = () -> {
                if (selectRadio.isSelected()) {
                    File dbFile = new File(BACKUP_DOWNLOAD_DIR, expectedDbName);
                    if (dbFile.exists()) {
                        selectedDbFile[0] = dbFile;
                        restoreBtn.setDisable(false);
                    } else {
                        selectedDbFile[0] = null;
                        restoreBtn.setDisable(true);
                    }
                }
            };

            downloadBtn.setOnAction(ev -> {
                downloadBtn.setDisable(true);
                downloadBtn.setText("جارِ التنزيل...");
                statusIcon.setText("⏳");

                Task<File> dlTask = new Task<>() {
                    @Override
                    protected File call() throws Exception {
                        return downloadAndExtractBackup(backup);
                    }
                };

                dlTask.setOnSucceeded(ev2 -> {
                    statusIcon.setText("✓");
                    statusIcon.getStyleClass().add("status-success");
                    downloadBtn.setText("تم التنزيل ✓");
                    downloadBtn.getStyleClass().add("button-success");
                    downloadBtn.setDisable(false);
                    updateRestoreSelection.run();
                });

                dlTask.setOnFailed(ev2 -> {
                    statusIcon.setText("✗");
                    statusIcon.getStyleClass().add("status-danger");
                    downloadBtn.setText("فشل - إعادة");
                    downloadBtn.getStyleClass().add("button-danger");
                    downloadBtn.setDisable(false);
                    logger.error("Failed to download backup", dlTask.getException());
                });

                new Thread(dlTask).start();
            });

            selectRadio.selectedProperty().addListener((obs, oldVal, selected) -> {
                if (selected) {
                    if (selectedRow[0] != null) {
                        selectedRow[0].getStyleClass().remove("backup-row-selected");
                    }
                    row.getStyleClass().add("backup-row-selected");
                    selectedRow[0] = row;
                    updateRestoreSelection.run();
                }
            });

            row.setOnMouseClicked(ev -> selectRadio.setSelected(true));

            row.getChildren().addAll(selectRadio, statusIcon, infoBox, downloadBtn);
            backupListBox.getChildren().add(row);
        }

        scrollPane.setContent(backupListBox);
        content.getChildren().add(scrollPane);

        Label hintLabel = new Label("💡 قم بتنزيل النسخة أولاً ثم حدّدها ثم اضغط 'استعادة'");
        hintLabel.getStyleClass().add("warning-hint");
        content.getChildren().add(hintLabel);

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == restoreButtonType) {
                return selectedDbFile[0];
            }
            return null;
        });

        dialog.showAndWait().ifPresent(dbFile -> {
            if (dbFile != null && dbFile.exists()) {
                performLocalRestore(dbFile);
            }
        });
    }

    private void applyDialogTheme(Dialog<?> dialog) {
        dialog.setOnShown(event -> {
            Node owner = driveStatusLabel != null ? driveStatusLabel : backupStatusLabel;
            if (owner == null || owner.getScene() == null) {
                return;
            }
            Scene ownerScene = owner.getScene();
            dialog.getDialogPane().getStylesheets().setAll(ownerScene.getStylesheets());
            Scene dialogScene = dialog.getDialogPane().getScene();
            if (dialogScene != null) {
                ThemeManager.getInstance().applyTheme(dialogScene);
                int fontSize = SessionManager.getInstance().getUiFontSize();
                if (fontSize != 13) {
                    MainApp.applyFontSizeRecursive(dialog.getDialogPane(), fontSize);
                }
            }
        });
    }

    private File downloadAndExtractBackup(BackupFile backup) throws Exception {
        com.pharmax.MainApp app = TabManager.getInstance().getMainApp();
        com.pharmax.service.drive.GoogleDriveService driveService = app != null ? app.getGoogleDriveService() : null;
        if (driveService == null) {
            throw new IOException("خدمة Google Drive غير متوفرة");
        }

        File downloadDir = new File(BACKUP_DOWNLOAD_DIR);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        // Download zip
        File zipFile = new File(downloadDir, backup.getName());
        driveService.downloadFile(backup.getId(), zipFile);
        logger.info("Downloaded backup to: " + zipFile.getAbsolutePath());

        // Extract zip
        String dbName = backup.getName().replace(".zip", ".db");
        File dbFile = new File(downloadDir, dbName);

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            boolean extracted = false;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".db")) {
                    java.nio.file.Files.copy(zis, dbFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Extracted backup to: " + dbFile.getAbsolutePath());
                    extracted = true;
                    break;
                }
            }
            if (!extracted) {
                throw new IOException("ملف ZIP لا يحتوي على قاعدة بيانات صالحة");
            }
        }

        // Optionally delete zip after extraction
        zipFile.delete();

        return dbFile;
    }

    private void performLocalRestore(File dbFile) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الاستعادة");
        confirm.setHeaderText("هل أنت متأكد من استعادة البيانات؟");
        confirm.setContentText("سيتم استبدال قاعدة البيانات الحالية بـ:\n" + dbFile.getName()
                + "\n\nسيتم إنشاء نسخة احتياطية وقائية تلقائياً قبل الاستعادة.\nقد تحتاج إلى إعادة تشغيل البرنامج بعد اكتمال العملية.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    backupRestoreService.restoreLocalBackup(dbFile);

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("تمت الاستعادة");
                    alert.setHeaderText(null);
                    alert.setContentText("تمت استعادة النسخة الاحتياطية بنجاح.\n"
                            + "يرجى إعادة تشغيل البرنامج لضمان إعادة فتح قاعدة البيانات المحدثة.");
                    alert.showAndWait();
                } catch (Exception e) {
                    logger.error("Failed to restore backup file", e);
                    showError("خطأ", "فشل في استعادة النسخة الاحتياطية: " + e.getMessage());
                }
            }
        });
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleBrowseBackupPath() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("اختر مجلد النسخ الاحتياطي");
        File dir = chooser.showDialog(dialogStage);
        if (dir != null) {
            backupPathField.setText(dir.getAbsolutePath());
        }
    }

    @FXML
    private void handleBrowseRestorePath() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("اختر ملف النسخة الاحتياطية");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Database Files", "*.db"));
        File file = chooser.showOpenDialog(dialogStage);
        if (file != null) {
            restorePathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void handleCreateBackup() {
        String backupDir = backupPathField.getText().trim();
        if (backupDir.isEmpty()) {
            showError("خطأ", "الرجاء تحديد مجلد النسخ الاحتياطي");
            return;
        }

        try {
            // Create backup directory if not exists
            File dir = new File(backupDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Source database file
            File sourceDb = new File("pharmax.db");
            if (!sourceDb.exists()) {
                showError("خطأ", "ملف قاعدة البيانات غير موجود");
                return;
            }

            // Create backup with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "PharmaX_backup_" + timestamp + ".db";
            File backupFile = new File(dir, backupFileName);

            Files.copy(sourceDb.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            backupRestoreService.writeBackupMetadata(backupFile, "manual");

            backupStatusLabel.setText("✓ تم إنشاء النسخة الاحتياطية: " + backupFileName);
            backupStatusLabel.setStyle("-fx-text-fill: -fx-success-text;");

            showSuccess("تم بنجاح", "تم إنشاء النسخة الاحتياطية بنجاح\n" + backupFile.getAbsolutePath());

        } catch (Exception e) {
            logger.error("Failed to create backup", e);
            backupStatusLabel.setText("✗ فشل في إنشاء النسخة الاحتياطية");
            backupStatusLabel.setStyle("-fx-text-fill: -fx-danger-text;");
            showError("خطأ", "فشل في إنشاء النسخة الاحتياطية: " + e.getMessage());
        }
    }

    @FXML
    private void handleRestoreBackup() {
        String restorePath = restorePathField.getText().trim();
        if (restorePath.isEmpty()) {
            showError("خطأ", "الرجاء تحديد ملف النسخة الاحتياطية");
            return;
        }

        File backupFile = new File(restorePath);
        if (!backupFile.exists()) {
            showError("خطأ", "ملف النسخة الاحتياطية غير موجود");
            return;
        }

        // Confirm restore
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الاستعادة");
        confirm.setHeaderText("هل أنت متأكد من استعادة البيانات؟");
        confirm.setContentText(
                "سيتم استبدال جميع البيانات الحالية بالبيانات من النسخة الاحتياطية.\nهذا الإجراء لا يمكن التراجع عنه!");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    backupRestoreService.restoreLocalBackup(backupFile);

                    backupStatusLabel.setText("✓ تم استعادة البيانات بنجاح - يرجى إعادة تشغيل البرنامج");
                    backupStatusLabel.setStyle("-fx-text-fill: -fx-success-text;");

                    showSuccess("تم بنجاح", "تم استعادة البيانات بنجاح!\nيرجى إعادة تشغيل البرنامج لتطبيق التغييرات.");

                } catch (Exception e) {
                    logger.error("Failed to restore backup", e);
                    backupStatusLabel.setText("✗ فشل في استعادة البيانات");
                    backupStatusLabel.setStyle("-fx-text-fill: -fx-danger-text;");
                    showError("خطأ", "فشل في استعادة البيانات: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleSaveSaleOptions() {
        boolean allowQuickSale = allowQuickSaleCheckBox != null && allowQuickSaleCheckBox.isSelected();
        boolean defaultQuickSale = allowQuickSale && defaultQuickSaleCheckBox != null && defaultQuickSaleCheckBox.isSelected();

        var props = configStore.load();
        props.setProperty(QUICK_SALE_ENABLED_KEY, String.valueOf(allowQuickSale));
        props.setProperty(QUICK_SALE_DEFAULT_KEY, String.valueOf(defaultQuickSale));
        configStore.save(props);

        if (defaultQuickSaleCheckBox != null) {
            defaultQuickSaleCheckBox.setDisable(!allowQuickSale);
        }
        showSuccess("تم", "تم حفظ خيارات شاشة البيع بنجاح");
    }

    @FXML
    private void handleSaveCompanyName() {
        String companyName = companyNameField.getText().trim();
        Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);

        if (companyName.isEmpty()) {
            prefs.remove(PREF_COMPANY_NAME);
            showSuccess("تم", "تم إزالة اسم الشركة");
        } else {
            prefs.put(PREF_COMPANY_NAME, companyName);
            showSuccess("تم", "تم حفظ اسم الشركة بنجاح\nسيظهر عند إعادة تشغيل البرنامج");
        }
    }

    @FXML
    private void handleBrowseBannerPath() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("اختر صورة الشعار");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(dialogStage);
        if (file != null) {
            bannerPathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void handleSaveBanner() {
        String bannerPath = bannerPathField.getText().trim();
        Preferences prefs = Preferences.userNodeForPackage(ReceiptService.class);

        if (bannerPath.isEmpty()) {
            prefs.remove(PREF_BANNER_PATH);
            showSuccess("تم", "تم إزالة الشعار");
        } else {
            File file = new File(bannerPath);
            if (!file.exists()) {
                showError("خطأ", "ملف الصورة غير موجود");
                return;
            }
            prefs.put(PREF_BANNER_PATH, bannerPath);
            showSuccess("تم", "تم حفظ الشعار بنجاح");
        }
    }

    @FXML
    private void handleRemoveBanner() {
        Preferences prefs = Preferences.userNodeForPackage(ReceiptService.class);
        prefs.remove(PREF_BANNER_PATH);
        bannerPathField.clear();
        showSuccess("تم", "تم إزالة الشعار");
    }

    @FXML
    private void handleOpenReceiptsFolder() {
        try {
            File receiptsDir = new File(receiptsPathField.getText().trim());
            if (!receiptsDir.exists()) {
                receiptsDir.mkdirs();
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(receiptsDir);
            }
        } catch (Exception e) {
            logger.error("Failed to open receipts folder", e);
            showError("خطأ", "فشل في فتح مجلد الإيصالات");
        }
    }

    @FXML
    private void handleRefreshStats() {
        try {
            int customersCount = customerService.getAllCustomers().size();
            int productsCount = inventoryService.getAllProducts().size();
            int salesCount = salesService.getAllSales().size();
            int receiptsCount = receiptService.getAllReceipts().size();

            customersCountLabel.setText("عدد العملاء: " + customersCount);
            productsCountLabel.setText("عدد المنتجات: " + productsCount);
            salesCountLabel.setText("عدد المبيعات: " + salesCount);
            receiptsCountLabel.setText("عدد الإيصالات: " + receiptsCount);

            File dbFile = new File("pharmax.db");
            if (dbFile.exists()) {
                long sizeKB = dbFile.length() / 1024;
                dbSizeLabel.setText("حجم قاعدة البيانات: " + sizeKB + " KB");
            }
        } catch (Exception e) {
            logger.error("Failed to refresh stats", e);
        }
    }

    @FXML
    private void handleResetDatabase() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد إعادة التعيين");
        confirm.setHeaderText("⚠️ تحذير خطير!");
        confirm.setContentText("سيتم حذف جميع البيانات نهائياً!\nهل أنت متأكد تماماً؟");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Second confirmation
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("تأكيد نهائي");
                dialog.setHeaderText("للتأكيد، اكتب 'حذف' في الحقل أدناه");
                dialog.setContentText("اكتب 'حذف':");

                dialog.showAndWait().ifPresent(text -> {
                    if ("حذف".equals(text.trim())) {
                        try {
                            // Create backup before reset
                            File currentDb = new File("pharmax.db");
                            if (currentDb.exists()) {
                                String timestamp = LocalDateTime.now()
                                        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                                File preResetBackup = new File("PharmaX_pre_reset_" + timestamp + ".db");
                                Files.copy(currentDb.toPath(), preResetBackup.toPath(),
                                        StandardCopyOption.REPLACE_EXISTING);
                            }

                            // Delete database
                            com.pharmax.database.DatabaseManager.shutdown();
                            if (currentDb.exists()) {
                                currentDb.delete();
                            }

                            showSuccess("تم", "تم إعادة تعيين قاعدة البيانات.\nيرجى إعادة تشغيل البرنامج.");
                        } catch (Exception e) {
                            logger.error("Failed to reset database", e);
                            showError("خطأ", "فشل في إعادة تعيين قاعدة البيانات");
                        }
                    } else {
                        showInfo("إلغاء", "تم إلغاء العملية");
                    }
                });
            }
        });
    }

    @FXML
    private void handleExportCustomers() {
        exportToCSV("customers", () -> {
            List<Customer> customers = customerService.getAllCustomers();
            StringBuilder sb = new StringBuilder();
            sb.append("الكود,الاسم,الهاتف,العنوان,الرصيد\n");
            for (Customer c : customers) {
                sb.append(escape(c.getCustomerCode())).append(",");
                sb.append(escape(c.getName())).append(",");
                sb.append(escape(c.getPhoneNumber())).append(",");
                sb.append(escape(c.getAddress())).append(",");
                sb.append(c.getCurrentBalance() != null ? c.getCurrentBalance() : 0).append("\n");
            }
            return sb.toString();
        });
    }

    @FXML
    private void handleExportProducts() {
        exportToCSV("products", () -> {
            List<Product> products = inventoryService.getAllProducts();
            StringBuilder sb = new StringBuilder();
            sb.append("الكود,الاسم,الفئة,السعر,التكلفة,الكمية,الحد الأدنى\n");
            for (Product p : products) {
                sb.append(escape(p.getProductCode())).append(",");
                sb.append(escape(p.getName())).append(",");
                sb.append(escape(p.getCategory())).append(",");
                sb.append(p.getUnitPrice() != null ? p.getUnitPrice() : 0).append(",");
                sb.append(p.getCostPrice() != null ? p.getCostPrice() : 0).append(",");
                sb.append(p.getQuantityInStock() != null ? p.getQuantityInStock() : 0).append(",");
                sb.append(p.getMinimumStock() != null ? p.getMinimumStock() : 0).append("\n");
            }
            return sb.toString();
        });
    }

    @FXML
    private void handleExportSales() {
        exportToCSV("sales", () -> {
            List<Sale> sales = salesService.getAllSales();
            StringBuilder sb = new StringBuilder();
            sb.append("رقم الفاتورة,العميل,التاريخ,الإجمالي,المدفوع,الحالة\n");
            for (Sale s : sales) {
                sb.append(escape(s.getSaleCode())).append(",");
                sb.append(escape(s.getCustomer() != null ? s.getCustomer().getName() : "-")).append(",");
                sb.append(s.getSaleDate() != null
                        ? s.getSaleDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : "-").append(",");
                sb.append(s.getFinalAmount() != null ? s.getFinalAmount() : 0).append(",");
                sb.append(s.getPaidAmount() != null ? s.getPaidAmount() : 0).append(",");
                sb.append(escape(s.getPaymentStatus())).append("\n");
            }
            return sb.toString();
        });
    }

    private void exportToCSV(String name, java.util.function.Supplier<String> dataSupplier) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("حفظ ملف CSV");
        chooser.setInitialFileName(name + "_export.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(dialogStage);

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                // Add BOM for Excel compatibility
                writer.write('\ufeff');
                writer.write(dataSupplier.get());
                showSuccess("تم", "تم تصدير البيانات بنجاح إلى:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Failed to export to CSV", e);
                showError("خطأ", "فشل في تصدير البيانات");
            }
        }
    }

    private String escape(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
