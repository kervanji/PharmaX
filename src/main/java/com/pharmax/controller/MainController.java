package com.pharmax.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;

import javafx.scene.input.TransferMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.pharmax.util.DashboardAccessService;
import com.pharmax.util.DashboardLayoutService;
import com.pharmax.util.SessionManager;
import com.pharmax.util.TabManager;
import com.pharmax.MainApp;
import com.pharmax.update.AppVersion;
import com.pharmax.update.UpdateCheckResult;
import com.pharmax.update.UpdateInstallerLauncher;
import com.pharmax.update.UpdateService;
import com.pharmax.model.Product;
import com.pharmax.model.Sale;
import com.pharmax.model.UserRole;
import com.pharmax.model.VoucherType;
import com.pharmax.service.CustomerService;
import com.pharmax.service.CashboxService;
import com.pharmax.service.InventoryService;
import com.pharmax.service.ProductBatchService;
import com.pharmax.service.SalesService;
import com.pharmax.service.VoucherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import com.pharmax.model.Installment;
import javafx.scene.control.TextInputDialog;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private static final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    @FXML
    private BorderPane mainLayout;
    @FXML
    private TabPane mainTabPane;
    @FXML
    private Tab dashboardTab;
    @FXML
    private Label todaySalesCountLabel;
    @FXML
    private Label todaySalesAmountLabel;
    @FXML
    private Label lowStockDescLabel;
    @FXML
    private Label lowStockCountLabel;
    @FXML
    private Label expiryAlertSummaryLabel;
    @FXML
    private Label dataQualityAlertSummaryLabel;
    @FXML
    private Label cashboxClosingSummaryLabel;
    @FXML
    private Label pendingPaymentsDescLabel;
    @FXML
    private Label pendingPaymentsLabel;
    @FXML
    private Label totalCustomersLabel;
    @FXML
    private Label totalProductsLabel;
    @FXML
    private Label totalSalesLabel;
    @FXML
    private Label inventoryValueLabel;
    @FXML
    private Label companyNameLabel;
    @FXML
    private Label currentUserLabel;
    @FXML
    private Label currentRoleLabel;
    @FXML
    private Button lockButton;
    @FXML
    private Button logoutButton;
    @FXML
    private Label updateStatusLabel;
    @FXML
    private ProgressIndicator updateProgress;
    @FXML
    private Button updateButton;
    @FXML
    private Button checkUpdateButton;
    @FXML
    private VBox userManagementTile;
    @FXML
    private VBox salesReportTile;
    @FXML
    private VBox settingsTile;
    @FXML
    private FlowPane tilesFlowPane;
    @FXML
    private HBox layoutEditControls;
    @FXML
    private Button editLayoutBtn;
    @FXML
    private Button sellerLayoutBtn;
    @FXML
    private Button resetLayoutBtn;
    @FXML
    private Button saveDefaultLayoutBtn;
    @FXML
    private HBox lowStockAlertCard;
    @FXML
    private HBox expiryAlertCard;
    @FXML
    private HBox dataQualityAlertCard;
    @FXML
    private HBox cashboxAlertCard;
    @FXML
    private HBox installmentAlertCard;
    @FXML
    private Label installmentReminderDaysLabel;
    @FXML
    private Label installmentAlertDescLabel;
    @FXML
    private Label installmentAlertLabel;
    @FXML
    private Label driveStatusIndicator;
    @FXML
    private Label driveStatusLabel;
    @FXML
    private Button connectDriveButton;

    private static final String PREF_COMPANY_NAME = "company.name";
    private static final String PREF_INSTALLMENT_REMINDER_DAYS = "installment.reminder.days";
    private static final int DEFAULT_REMINDER_DAYS = 3;
    private static final DataFormat TILE_DATA_FORMAT = new DataFormat("application/x-PharmaX-tile-id");

    private boolean editMode = false;
    private boolean sellerEditMode = false;
    private VBox dragSource = null;
    private Set<String> hiddenTileIds = new HashSet<>(); // hidden from everyone
    private Set<String> sellerHiddenTileIds = new HashSet<>(); // hidden from sellers only

    private MainApp mainApp;
    @SuppressWarnings("unused")
    private final CustomerService customerService = new CustomerService();
    private final InventoryService inventoryService = new InventoryService();
    private final ProductBatchService productBatchService = new ProductBatchService();
    private final SalesService salesService = new SalesService();
    private final VoucherService voucherService = new VoucherService();

    private final UpdateService updateService = new UpdateService();
    private volatile UpdateCheckResult availableUpdate;

    /**
     * Represents a dashboard tile definition.
     */
    private static class TileDef {
        final String id;
        final String icon; // Fallback emoji or text
        final String iconFile; // PNG filename
        final String label;
        final String style;
        final String handlerMethod;
        final boolean adminOnly;
        final boolean reportOnly;
        final boolean settingsOnly;

        TileDef(String id, String icon, String iconFile, String label, String style, String handlerMethod,
                boolean adminOnly, boolean reportOnly, boolean settingsOnly) {
            this.id = id;
            this.icon = icon;
            this.iconFile = iconFile;
            this.label = label;
            this.style = style;
            this.handlerMethod = handlerMethod;
            this.adminOnly = adminOnly;
            this.reportOnly = reportOnly;
            this.settingsOnly = settingsOnly;
        }

        TileDef(String id, String icon, String iconFile, String label, String style, String handlerMethod) {
            this(id, icon, iconFile, label, style, handlerMethod, false, false, false);
        }
    }

    private void addVisibilityToggle(VBox tile) {
        String tileId = tile.getId();
        boolean isHidden = hiddenTileIds.contains(tileId);
        boolean isSellerHidden = sellerHiddenTileIds.contains(tileId);

        Button toggleBtn = new Button(isHidden ? "🚫" : (isSellerHidden ? "🙈" : "👁"));
        toggleBtn.setId("visibility-toggle");
        toggleBtn.setStyle("-fx-background-color: "
                + (isHidden ? "rgba(239,68,68,0.7)" : isSellerHidden ? "rgba(245,158,11,0.8)" : "rgba(76,175,80,0.7)")
                + "; " +
                "-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 2 6; -fx-background-radius: 6; -fx-cursor: hand;");

        toggleBtn.setOnAction(e -> {
            if (sellerEditMode) {
                // Seller edit mode: only toggle visible <-> seller-hidden
                if (sellerHiddenTileIds.contains(tileId)) {
                    sellerHiddenTileIds.remove(tileId);
                    tile.setOpacity(1.0);
                    tile.getStyleClass().remove("tile-hidden");
                    toggleBtn.setText("👁");
                    toggleBtn.setStyle("-fx-background-color: rgba(76,175,80,0.7); " +
                            "-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 2 6; -fx-background-radius: 6; -fx-cursor: hand;");
                } else {
                    sellerHiddenTileIds.add(tileId);
                    tile.setOpacity(0.35);
                    tile.getStyleClass().add("tile-hidden");
                    toggleBtn.setText("🙈");
                    toggleBtn.setStyle("-fx-background-color: rgba(245,158,11,0.8); " +
                            "-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 2 6; -fx-background-radius: 6; -fx-cursor: hand;");
                }
                hiddenTileIds.remove(tileId); // ensure not fully hidden in seller-only mode
                return;
            }

            // Default mode: Toggle visible <-> hidden (Admin/Global view)
            if (hiddenTileIds.contains(tileId)) {
                hiddenTileIds.remove(tileId);
                tile.setOpacity(1.0);
                tile.getStyleClass().remove("tile-hidden");

                // Restore visual state based on whether it's hidden for sellers
                if (sellerHiddenTileIds.contains(tileId)) {
                    toggleBtn.setText("�");
                    toggleBtn.setStyle("-fx-background-color: rgba(245,158,11,0.8); " +
                            "-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 2 6; -fx-background-radius: 6; -fx-cursor: hand;");
                } else {
                    toggleBtn.setText("�");
                    toggleBtn.setStyle("-fx-background-color: rgba(76,175,80,0.7); " +
                            "-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 2 6; -fx-background-radius: 6; -fx-cursor: hand;");
                }
            } else {
                hiddenTileIds.add(tileId);
                tile.setOpacity(0.35);
                tile.getStyleClass().add("tile-hidden");
                toggleBtn.setText("�");
                toggleBtn.setStyle("-fx-background-color: rgba(239,68,68,0.7); " +
                        "-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 2 6; -fx-background-radius: 6; -fx-cursor: hand;");
            }
        });

        tile.getChildren().add(toggleBtn);
    }

    private void removeVisibilityToggle(VBox tile) {
        tile.getChildren().removeIf(n -> "visibility-toggle".equals(n.getId()));
    }

    private void disableEditMode() {
        if (editLayoutBtn != null) {
            editLayoutBtn.setText("✏️ تعديل الواجهة");
            editLayoutBtn.setStyle("-fx-background-color: rgba(255,193,7,0.15); -fx-text-fill: -fx-warning-text;");
        }
        if (sellerLayoutBtn != null) {
            sellerLayoutBtn.setText("🛍️ تعديل واجهة البائع");
            sellerLayoutBtn.setStyle("-fx-background-color: -fx-accent-bg; -fx-text-fill: -fx-accent-text;");
        }
        if (resetLayoutBtn != null) {
            resetLayoutBtn.setVisible(false);
            resetLayoutBtn.setManaged(false);
        }

        // Remove drag-and-drop and visual feedback
        if (tilesFlowPane != null) {
            for (Node node : tilesFlowPane.getChildren()) {
                if (node instanceof VBox tile) {
                    removeDragAndDrop(tile);
                    removeVisibilityToggle(tile);
                    tile.getStyleClass().remove("tile-edit-mode");
                    tile.getStyleClass().remove("tile-hidden");

                    // Re-apply hidden state in normal mode
                    String tileId = tile.getId();
                    boolean isHidden = hiddenTileIds.contains(tileId);
                    boolean isSellerHidden = sellerHiddenTileIds.contains(tileId);
                    boolean hideForSeller = isSellerHidden
                            && SessionManager.getInstance().getCurrentRole() == UserRole.SELLER;
                    boolean shouldHide = isHidden || hideForSeller;
                    tile.setVisible(!shouldHide);
                    tile.setManaged(!shouldHide);
                    tile.setOpacity(1.0);
                }
            }
        }
    }

    private final List<TileDef> defaultTileDefinitions = List.of(
            new TileDef("pos", "🛒", "point_of_sale.png", "نقطة بيع",
                    "linear-gradient(to bottom right, #2563eb, #1d4ed8)", "handleNewSale"),
            new TileDef("view-sales", "📄", "sales_reports.png", "عرض المبيعات",
                    "linear-gradient(to bottom right, #0f766e, #0d9488)", "handleViewSales"),
            new TileDef("view-inventory", "📦", "inventory_view.png", "عرض المخزون",
                    "linear-gradient(to bottom right, #d97706, #f59e0b)", "handleViewInventory"),
            new TileDef("new-product", "➕", "add_product.png", "إضافة منتج",
                    "linear-gradient(to bottom right, #ea580c, #f97316)", "handleNewProduct"),
            new TileDef("product-return", "↩️", "returns.png", "إرجاع مواد",
                    "linear-gradient(to bottom right, #db2777, #ec4899)", "handleProductReturn"),
            new TileDef("low-stock", "⚠️", "low_stock_alert.png", "منخفض المخزون",
                    "linear-gradient(to bottom right, #b91c1c, #dc2626)", "handleLowStock"),
            new TileDef("expiry-alerts", "⏰", "low_stock_alert.png", "تنبيهات الانتهاء",
                    "linear-gradient(to bottom right, #c2410c, #ea580c)", "handleExpiryAlerts"),
            new TileDef("purchase", "🛍️", "purchases.png", "المشتريات",
                    "linear-gradient(to bottom right, #6d28d9, #7c3aed)", "handlePurchase"),
            new TileDef("purchase-return", "↩️", "returns.png", "مرتجع شراء",
                    "linear-gradient(to bottom right, #a16207, #ca8a04)", "handlePurchaseReturn"),
            new TileDef("payment-voucher", "📤", "payment_voucher.svg", "سند دفع",
                    "linear-gradient(to bottom right, #dc2626, #ef4444)", "handlePaymentVoucher"),
            new TileDef("cashbox", "💵", "settings.png", "الصندوق",
                    "linear-gradient(to bottom right, #15803d, #16a34a)", "handleCashbox"),
            new TileDef("barcode-print", "▥", "barcode_printing.png", "طباعة باركود",
                    "linear-gradient(to bottom right, #475569, #64748b)", "handleBarcodePrint"),
            new TileDef("user-management", "👤", "user_management.png", "إدارة المستخدمين",
                    "linear-gradient(to bottom right, #0f766e, #14b8a6)", "handleUserManagement",
                    true, false, false),
            new TileDef("pharmacy-reports", "📊", "sales_reports.png", "التقارير",
                    "linear-gradient(to bottom right, #0f766e, #14b8a6)", "handlePharmacyReports",
                    false, true, false),
            new TileDef("settings", "⚙️", "settings.png", "الإعدادات",
                    "linear-gradient(to bottom right, #334155, #475569)", "handleSettings",
                    false, false, true),
            new TileDef("about", "ℹ️", "about_system.png", "عن البرنامج",
                    "linear-gradient(to bottom right, #1e293b, #334155)", "handleAbout"));

    private Map<String, TileDef> tileDefMap;

    @FXML
    private void initialize() {
        DashboardLayoutService.ensureDefaultLayoutFiles();

        // Build tile definition map
        tileDefMap = new LinkedHashMap<>();
        for (TileDef def : defaultTileDefinitions) {
            tileDefMap.put(def.id, def);
        }

        loadCompanyName();
        loadCurrentUserInfo();
        applyRolePermissions();
        buildDashboardTiles();
        refreshDashboard();
        loadInstallmentReminderDays();
        refreshInstallmentAlerts();
        initUpdateUi();
        checkForUpdatesInBackground();
        updateDriveStatusIndicator();
    }

    private void initUpdateUi() {
        if (updateStatusLabel != null) {
            updateStatusLabel.setText("");
        }
        if (updateProgress != null) {
            updateProgress.setVisible(false);
        }
        if (updateButton != null) {
            updateButton.setVisible(false);
            updateButton.setDisable(false);
        }
    }

    private void checkForUpdatesInBackground() {
        String currentVersion = AppVersion.current();
        if (updateStatusLabel != null) {
            updateStatusLabel.setText("فحص التحديثات...");
        }
        updateService.checkForUpdateAsync(currentVersion).whenComplete((result, err) -> {
            Platform.runLater(() -> {
                if (err != null) {
                    logger.warn("Update check failed", err);
                    if (updateStatusLabel != null) {
                        updateStatusLabel.setText("تعذر فحص التحديثات");
                    }
                    if (updateButton != null) {
                        updateButton.setVisible(false);
                    }
                    return;
                }

                if (result != null && result.isUpdateAvailable()) {
                    availableUpdate = result;
                    if (updateStatusLabel != null) {
                        updateStatusLabel.setText("يوجد تحديث v" + result.getLatestVersion());
                    }
                    if (updateButton != null) {
                        updateButton.setVisible(true);
                        updateButton.setDisable(false);
                    }
                } else {
                    if (updateStatusLabel != null) {
                        updateStatusLabel.setText("");
                    }
                    if (updateButton != null) {
                        updateButton.setVisible(false);
                    }
                }
            });
        });
    }

    private void loadCurrentUserInfo() {
        SessionManager session = SessionManager.getInstance();
        if (session.isLoggedIn()) {
            if (currentUserLabel != null) {
                currentUserLabel.setText(session.getCurrentDisplayName());
            }
            if (currentRoleLabel != null) {
                currentRoleLabel.setText(session.getCurrentRole().getDisplayName());
            }
        }
    }

    private void applyRolePermissions() {
        SessionManager session = SessionManager.getInstance();

        // Show layout edit controls for admins only
        if (layoutEditControls != null) {
            boolean isAdmin = session.getCurrentRole() == UserRole.ADMIN;
            layoutEditControls.setVisible(isAdmin);
            layoutEditControls.setManaged(isAdmin);
        }
        if (saveDefaultLayoutBtn != null) {
            boolean isAdmin = session.getCurrentRole() == UserRole.ADMIN;
            saveDefaultLayoutBtn.setVisible(isAdmin);
            saveDefaultLayoutBtn.setManaged(isAdmin);
        }
    }

    // ==================== Dashboard Tile Builder ====================

    private void buildDashboardTiles() {
        if (tilesFlowPane == null)
            return;

        // Clear existing FXML-defined tiles
        tilesFlowPane.getChildren().clear();

        SessionManager session = SessionManager.getInstance();
        String username = session.isLoggedIn() ? session.getCurrentUsername() : "default";

        // Load saved order from current user's layout, otherwise use default layout
        List<String> savedOrder = DashboardLayoutService.loadTileOrder(username);
        if (savedOrder.isEmpty()) {
            savedOrder = DashboardLayoutService.loadDefaultTileOrder();
        }
        hiddenTileIds = DashboardLayoutService.loadHiddenTiles(username);
        sellerHiddenTileIds = DashboardLayoutService.loadSellerHiddenTiles();
        List<TileDef> orderedDefs;

        if (!savedOrder.isEmpty()) {
            // Build ordered list from saved order, then append any new tiles not in saved
            // order
            orderedDefs = new ArrayList<>();
            for (String id : savedOrder) {
                TileDef def = tileDefMap.get(id);
                if (def != null) {
                    orderedDefs.add(def);
                }
            }
            // Add any tiles not in saved order (new tiles added after layout was saved)
            for (TileDef def : defaultTileDefinitions) {
                if (!savedOrder.contains(def.id)) {
                    orderedDefs.add(def);
                }
            }
        } else {
            orderedDefs = new ArrayList<>(defaultTileDefinitions);
        }

        // Build tile nodes
        for (TileDef def : orderedDefs) {
            // Check permissions
            if (def.adminOnly && !session.canManageUsers())
                continue;
            if (def.reportOnly && !session.canAccessReports())
                continue;
            if (def.settingsOnly && !session.canAccessSettings())
                continue;

            VBox tile = createTileNode(def);

            // Apply hidden state (hide in normal mode)
            boolean hideForAll = hiddenTileIds.contains(def.id);
            boolean hideForSeller = sellerHiddenTileIds.contains(def.id) && session.getCurrentRole() == UserRole.SELLER;
            if (hideForAll || hideForSeller) {
                tile.setVisible(false);
                tile.setManaged(false);
            }

            tilesFlowPane.getChildren().add(tile);
        }

        // Keep references for permission-based tiles
        updateTileReferences();
        refreshAlertWidgetAccess();
    }

    private void refreshAlertWidgetAccess() {
        setAlertCardAccessible(lowStockAlertCard, "low-stock");
        setAlertCardAccessible(expiryAlertCard, "expiry-alerts");
        setAlertCardAccessible(dataQualityAlertCard, "view-inventory");
        setAlertCardAccessible(cashboxAlertCard, "cashbox");
        setAlertCardAccessible(installmentAlertCard, "purchase");
    }

    private void setAlertCardAccessible(HBox card, String tileId) {
        if (card == null) {
            return;
        }
        boolean accessible = DashboardAccessService.canAccessTile(tileId);
        card.setVisible(accessible);
        card.setManaged(accessible);
        card.setDisable(!accessible);
    }

    private boolean guardTileAccess(String tileId) {
        if (DashboardAccessService.canAccessTile(tileId)) {
            return true;
        }
        showError("غير مسموح", "لا تملك صلاحية الوصول لهذه الواجهة");
        return false;
    }

    private VBox createTileNode(TileDef def) {
        VBox tile = new VBox();
        tile.setAlignment(javafx.geometry.Pos.CENTER);
        tile.setSpacing(8);
        tile.getStyleClass().add("dashboard-tile");
        tile.setId(def.id);

        // Apply custom style if defined
        if (def.style != null) {
            tile.setStyle("-fx-background-color: " + def.style + ";");
        }

        // Icon
        Node iconNode;
        String icon = def.icon;
        String iconFile = def.iconFile;

        if (iconFile != null && !iconFile.isEmpty()) {
            Image iconImage = loadPngIcon(iconFile, 72, 72);
            if (iconImage != null) {
                ImageView imageView = new ImageView(iconImage);
                imageView.setFitWidth(60);
                imageView.setFitHeight(60);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.setCache(true);
                imageView.getStyleClass().add("tile-image-icon");

                StackPane iconShell = new StackPane(imageView);
                iconShell.getStyleClass().add("tile-icon-shell");
                iconNode = iconShell;
            } else {
                // Fallback to text emoji if PNG fails
                Label iconLabel = new Label(icon);
                iconLabel.getStyleClass().add("tile-icon");
                iconNode = iconLabel;
            }
        } else {
            // Fallback to text emoji
            Label iconLabel = new Label(icon);
            iconLabel.getStyleClass().add("tile-icon");
            iconNode = iconLabel;
        }

        // Text label
        Label textLabel = new Label(def.label);
        textLabel.getStyleClass().add("tile-label");
        // Force white text if using a colored gradient background
        if (def.style != null) {
            textLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        }

        tile.getChildren().addAll(iconNode, textLabel);

        // Set click handler
        tile.setOnMouseClicked(event -> {
            if (editMode)
                return;
            if (!guardTileAccess(def.id))
                return;
            invokeTileHandler(def.handlerMethod);
        });

        return tile;
    }

    private Image loadPngIcon(String iconFile, double width, double height) {
        String resourcePath = "/images/icons/" + iconFile;
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                logger.warn("Dashboard icon resource not found: {}", resourcePath);
                return null;
            }

            Image image = new Image(inputStream, width, height, true, true);
            if (image.isError()) {
                logger.warn("Failed to load dashboard icon: {}", resourcePath, image.getException());
                return null;
            }
            return image;
        } catch (Exception e) {
            logger.warn("Failed to load dashboard icon: {}", resourcePath, e);
            return null;
        }
    }

    private void invokeTileHandler(String methodName) {
        try {
            java.lang.reflect.Method method = this.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception e) {
            logger.error("Failed to invoke tile handler: {}", methodName, e);
        }
    }

    private void updateTileReferences() {
        // Update fx:id references for tiles that have them
        if (tilesFlowPane == null)
            return;
        for (Node node : tilesFlowPane.getChildren()) {
            if (node instanceof VBox vbox) {
                String id = vbox.getId();
                if ("user-management".equals(id))
                    userManagementTile = vbox;
                else if ("sales-report".equals(id))
                    salesReportTile = vbox;
                else if ("settings".equals(id))
                    settingsTile = vbox;
            }
        }
    }

    // ==================== Edit Mode & Drag-and-Drop ====================

    @FXML
    private void handleToggleEditMode() {
        editMode = !editMode;
        sellerEditMode = false;
        if (sellerLayoutBtn != null) {
            sellerLayoutBtn.setText("🛍️ تعديل واجهة البائع");
            sellerLayoutBtn.setStyle("-fx-background-color: -fx-accent-bg; -fx-text-fill: -fx-accent-text;");
        }

        if (editMode) {
            enableEditMode();
        } else {
            disableEditMode();
            saveTileOrder();
        }
    }

    @FXML
    private void handleToggleSellerEditMode() {
        sellerEditMode = !sellerEditMode;
        editMode = sellerEditMode; // seller edit mode implies edit mode
        if (sellerEditMode) {
            if (editLayoutBtn != null) {
                editLayoutBtn.setText("✏️ تعديل الواجهة");
                editLayoutBtn.setStyle("-fx-background-color: rgba(255,193,7,0.15); -fx-text-fill: -fx-warning-text;");
            }
            if (sellerLayoutBtn != null) {
                sellerLayoutBtn.setText("✅ حفظ واجهة البائع");
                sellerLayoutBtn
                        .setStyle("-fx-background-color: rgba(76,175,80,0.25); -fx-text-fill: -fx-badge-success-text;");
            }
            enableEditMode();
        } else {
            if (sellerLayoutBtn != null) {
                sellerLayoutBtn.setText("🛍️ تعديل واجهة البائع");
                sellerLayoutBtn.setStyle("-fx-background-color: -fx-accent-bg; -fx-text-fill: -fx-accent-text;");
            }
            disableEditMode();
            saveTileOrder();
        }
    }

    private void enableEditMode() {
        if (editLayoutBtn != null) {
            editLayoutBtn.setText("✅ حفظ الترتيب");
            editLayoutBtn
                    .setStyle("-fx-background-color: rgba(76,175,80,0.25); -fx-text-fill: -fx-badge-success-text;");
        }
        if (resetLayoutBtn != null) {
            resetLayoutBtn.setVisible(true);
            resetLayoutBtn.setManaged(true);
        }

        // Add drag-and-drop to all tiles and visual feedback
        if (tilesFlowPane != null) {
            for (Node node : tilesFlowPane.getChildren()) {
                if (node instanceof VBox tile) {
                    // Show hidden tiles in edit mode (faded)
                    tile.setVisible(true);
                    tile.setManaged(true);

                    String tileId = tile.getId();
                    boolean isHidden = hiddenTileIds.contains(tileId);
                    boolean isSellerHidden = sellerHiddenTileIds.contains(tileId);

                    // Determine if should be faded based on edit mode
                    boolean shouldFade;
                    if (sellerEditMode) {
                        // In seller edit mode, fade if hidden for seller
                        shouldFade = isSellerHidden;
                    } else {
                        // In normal (admin) edit mode, only fade if hidden for admin
                        // Seller-hidden tiles remain opaque (but show monkey icon)
                        shouldFade = isHidden;
                    }

                    if (shouldFade) {
                        tile.setOpacity(0.35);
                        tile.getStyleClass().add("tile-hidden");
                    } else {
                        tile.setOpacity(1.0); // Ensure full opacity if not hidden in this mode
                    }

                    setupDragAndDrop(tile);
                    tile.getStyleClass().add("tile-edit-mode");
                    addVisibilityToggle(tile);
                }
            }
        }
    }

    private void setupDragAndDrop(VBox tile) {
        tile.setOnDragDetected(event -> {
            if (!editMode)
                return;
            dragSource = tile;
            Dragboard db = tile.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(TILE_DATA_FORMAT, tile.getId());
            db.setContent(content);

            // Visual feedback - make source semi-transparent
            tile.setOpacity(0.5);
            event.consume();
        });

        tile.setOnDragOver(event -> {
            if (!editMode)
                return;
            if (event.getGestureSource() != tile && event.getDragboard().hasContent(TILE_DATA_FORMAT)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        tile.setOnDragEntered(event -> {
            if (!editMode)
                return;
            if (event.getGestureSource() != tile && event.getDragboard().hasContent(TILE_DATA_FORMAT)) {
                tile.getStyleClass().add("tile-drag-over");
            }
            event.consume();
        });

        tile.setOnDragExited(event -> {
            tile.getStyleClass().remove("tile-drag-over");
            event.consume();
        });

        tile.setOnDragDropped(event -> {
            if (!editMode)
                return;
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasContent(TILE_DATA_FORMAT) && dragSource != null) {
                // Swap positions in the FlowPane
                int sourceIndex = tilesFlowPane.getChildren().indexOf(dragSource);
                int targetIndex = tilesFlowPane.getChildren().indexOf(tile);

                if (sourceIndex >= 0 && targetIndex >= 0 && sourceIndex != targetIndex) {
                    // Remove source, insert at target position
                    tilesFlowPane.getChildren().remove(dragSource);
                    if (targetIndex > tilesFlowPane.getChildren().size()) {
                        targetIndex = tilesFlowPane.getChildren().size();
                    }
                    tilesFlowPane.getChildren().add(targetIndex, dragSource);
                    success = true;
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });

        tile.setOnDragDone(event -> {
            if (dragSource != null) {
                dragSource.setOpacity(1.0);
                dragSource = null;
            }
            event.consume();
        });
    }

    private void removeDragAndDrop(VBox tile) {
        tile.setOnDragDetected(null);
        tile.setOnDragOver(null);
        tile.setOnDragEntered(null);
        tile.setOnDragExited(null);
        tile.setOnDragDropped(null);
        tile.setOnDragDone(null);
        tile.setOpacity(1.0);

        // Re-set the click handler
        String tileId = tile.getId();
        TileDef def = tileDefMap.get(tileId);
        if (def != null) {
            tile.setOnMouseClicked(event -> {
                if (!guardTileAccess(def.id)) {
                    return;
                }
                invokeTileHandler(def.handlerMethod);
            });
        }
    }

    private void saveTileOrder() {
        if (tilesFlowPane == null)
            return;

        SessionManager session = SessionManager.getInstance();
        String username = session.isLoggedIn() ? session.getCurrentUsername() : "default";

        List<String> tileIds = tilesFlowPane.getChildren().stream()
                .filter(n -> n instanceof VBox)
                .map(Node::getId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toList());

        DashboardLayoutService.saveTileLayout(username, tileIds, hiddenTileIds, Collections.emptySet());
        DashboardLayoutService.saveSellerLayout(tileIds, sellerHiddenTileIds);
        if (sellerEditMode) {
            showInfo("تم الحفظ", "تم حفظ واجهة البائع بنجاح");
        } else {
            showInfo("تم الحفظ", "تم حفظ ترتيب الواجهة بنجاح");
        }
    }

    @FXML
    private void handleSaveAsDefaultLayout() {
        if (tilesFlowPane == null) {
            return;
        }
        List<String> tileIds = tilesFlowPane.getChildren().stream()
                .filter(n -> n instanceof VBox)
                .map(Node::getId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toList());
        DashboardLayoutService.saveDefaultLayout(tileIds, hiddenTileIds, sellerHiddenTileIds);
        showInfo("تم الحفظ", "تم حفظ الترتيب الحالي كافتراضي للنظام");
    }

    @FXML
    private void handleResetLayout() {
        SessionManager session = SessionManager.getInstance();
        String username = session.isLoggedIn() ? session.getCurrentUsername() : "default";

        DashboardLayoutService.resetLayout(username);
        hiddenTileIds.clear();
        sellerHiddenTileIds = DashboardLayoutService.loadSellerHiddenTiles();

        editMode = false;
        sellerEditMode = false;
        disableEditMode();
        buildDashboardTiles();

        showInfo("تم إعادة الترتيب", "تم إعادة ترتيب الواجهة إلى الوضع الافتراضي");
    }

    private void loadCompanyName() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(MainController.class);
            String companyName = prefs.get(PREF_COMPANY_NAME, "");
            if (companyNameLabel != null && !companyName.isEmpty()) {
                companyNameLabel.setText(companyName);
            }
        } catch (Exception e) {
            logger.warn("Failed to load company name", e);
        }
    }

    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
        if (mainTabPane != null && dashboardTab != null) {
            TabManager.getInstance().initialize(mainTabPane, dashboardTab, mainApp);
            TabManager.getInstance().setDashboardRefreshCallback(this::refreshDashboard);
        }
        refreshDashboard();
        // Update drive status after mainApp is set (delayed to allow background token
        // reconnect)
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            Platform.runLater(this::updateDriveStatusIndicator);
        }).start();
    }

    private void refreshDashboard() {
        try {
            // Total products
            int productsCount = inventoryService.getAllProducts().size();
            if (totalProductsLabel != null) {
                totalProductsLabel.setText(String.valueOf(productsCount));
            }

            // Total sales count
            List<Sale> allSales = salesService.getAllSales();
            if (totalSalesLabel != null) {
                totalSalesLabel.setText(String.valueOf(allSales.size()));
            }

            // Today's sales
            LocalDate today = LocalDate.now();
            List<Sale> todaySales = allSales.stream()
                    .filter(s -> s.getSaleDate() != null && s.getSaleDate().toLocalDate().equals(today))
                    .toList();
            double todayAmount = todaySales.stream()
                    .mapToDouble(s -> s.getFinalAmount() != null ? s.getFinalAmount() : 0).sum();

            if (todaySalesCountLabel != null) {
                todaySalesCountLabel.setText("عدد المبيعات: " + todaySales.size());
            }
            if (todaySalesAmountLabel != null) {
                todaySalesAmountLabel.setText(currencyFormat.format(todayAmount) + " د.ع");
            }

            // Low stock products
            List<Product> lowStockProducts = inventoryService.getLowStockProducts();
            if (lowStockCountLabel != null) {
                if (lowStockProducts.isEmpty()) {
                    lowStockCountLabel.setText("لا توجد تنبيهات");
                    lowStockCountLabel.setStyle(
                            "-fx-font-size: 12px; -fx-text-fill: -fx-success-text; -fx-background-color: -fx-success-bg; -fx-padding: 6 10; -fx-background-radius: 8;");
                } else {
                    lowStockCountLabel.setText(lowStockProducts.size() + " منتج منخفض");
                    lowStockCountLabel.setStyle(
                            "-fx-font-size: 12px; -fx-text-fill: -fx-danger-text; -fx-background-color: -fx-badge-danger-bg; -fx-padding: 6 10; -fx-background-radius: 8;");
                }
            }

            int expiryAlerts = productBatchService.getExpiryAlertBatches(today).size();
            if (expiryAlertSummaryLabel != null) {
                if (expiryAlerts == 0) {
                    expiryAlertSummaryLabel.setText("لا توجد تنبيهات انتهاء");
                    expiryAlertSummaryLabel.setStyle(
                            "-fx-font-size: 12px; -fx-text-fill: -fx-success-text; -fx-background-color: -fx-success-bg; -fx-padding: 6 10; -fx-background-radius: 8;");
                } else {
                    expiryAlertSummaryLabel.setText(expiryAlerts + " دفعة تحتاج مراجعة");
                    expiryAlertSummaryLabel.setStyle(
                            "-fx-font-size: 12px; -fx-text-fill: -fx-warning-text; -fx-background-color: -fx-badge-warning-bg; -fx-padding: 6 10; -fx-background-radius: 8;");
                }
            }

            int qualityAlerts = inventoryService.getDataQualityAlerts().size();
            if (dataQualityAlertSummaryLabel != null) {
                if (qualityAlerts == 0) {
                    dataQualityAlertSummaryLabel.setText("البيانات سليمة");
                    dataQualityAlertSummaryLabel.setStyle(
                            "-fx-font-size: 12px; -fx-text-fill: -fx-success-text; -fx-background-color: -fx-success-bg; -fx-padding: 6 10; -fx-background-radius: 8;");
                } else {
                    dataQualityAlertSummaryLabel.setText(qualityAlerts + " ملاحظة بيانات");
                    dataQualityAlertSummaryLabel.setStyle(
                            "-fx-font-size: 12px; -fx-text-fill: -fx-warning-text; -fx-background-color: -fx-badge-warning-bg; -fx-padding: 6 10; -fx-background-radius: 8;");
                }
            }

            if (cashboxClosingSummaryLabel != null) {
                boolean closedToday = new CashboxService().getClosingByDate(today).isPresent();
                cashboxClosingSummaryLabel.setText(closedToday ? "تم إقفال اليوم" : "اليوم غير مقفل");
                cashboxClosingSummaryLabel.setStyle(closedToday
                        ? "-fx-font-size: 12px; -fx-text-fill: -fx-success-text; -fx-background-color: -fx-success-bg; -fx-padding: 6 10; -fx-background-radius: 8;"
                        : "-fx-font-size: 12px; -fx-text-fill: -fx-warning-text; -fx-background-color: -fx-badge-warning-bg; -fx-padding: 6 10; -fx-background-radius: 8;");
            }

            // Inventory value
            double inventoryValue = inventoryService.getTotalInventoryValue();
            if (inventoryValueLabel != null) {
                inventoryValueLabel.setText(currencyFormat.format(inventoryValue) + " د.ع");
            }

        } catch (Exception e) {
            logger.error("Failed to refresh dashboard", e);
        }
    }

    private int getInstallmentReminderDays() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(MainController.class);
            return prefs.getInt(PREF_INSTALLMENT_REMINDER_DAYS, DEFAULT_REMINDER_DAYS);
        } catch (Exception e) {
            return DEFAULT_REMINDER_DAYS;
        }
    }

    private void loadInstallmentReminderDays() {
        int days = getInstallmentReminderDays();
        if (installmentReminderDaysLabel != null) {
            installmentReminderDaysLabel.setText("تنبيه قبل: " + days + " أيام");
        }
    }

    @FXML
    private void handleChangeReminderDays(javafx.scene.input.MouseEvent event) {
        if (event != null) {
            event.consume();
        }
        int currentDays = getInstallmentReminderDays();
        TextInputDialog dialog = new TextInputDialog(String.valueOf(currentDays));
        dialog.setTitle("إعداد التنبيه");
        dialog.setHeaderText("تنبيه الأقساط");
        dialog.setContentText("عدد الأيام قبل موعد القسط للتنبيه:");
        dialog.getEditor().setStyle("-fx-alignment: center;");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(value -> {
            try {
                int days = Integer.parseInt(value.trim());
                if (days < 1)
                    days = 1;
                if (days > 365)
                    days = 365;
                Preferences prefs = Preferences.userNodeForPackage(MainController.class);
                prefs.putInt(PREF_INSTALLMENT_REMINDER_DAYS, days);
                loadInstallmentReminderDays();
                refreshInstallmentAlerts();
            } catch (NumberFormatException e) {
                showError("خطأ", "يرجى إدخال رقم صحيح");
            }
        });
    }

    private void refreshInstallmentAlerts() {
        try {
            int reminderDays = getInstallmentReminderDays();
            List<Installment> overdueInstallments = voucherService.getDueInstallments();
            List<Installment> upcomingInstallments = voucherService.getUpcomingInstallments(reminderDays);

            int overdueCount = overdueInstallments.size();
            int upcomingCount = upcomingInstallments.size();
            int totalAlerts = overdueCount + upcomingCount;

            if (installmentAlertLabel != null) {
                if (totalAlerts == 0) {
                    installmentAlertLabel.setText("لا توجد أقساط قريبة");
                    installmentAlertLabel.setStyle(
                            "-fx-font-size: 12px; -fx-text-fill: -fx-success-text; -fx-background-color: -fx-success-bg; -fx-padding: 6 10; -fx-background-radius: 8;");
                } else {
                    StringBuilder text = new StringBuilder();
                    if (overdueCount > 0) {
                        text.append(overdueCount).append(" متأخر");
                    }
                    if (upcomingCount > 0) {
                        if (text.length() > 0)
                            text.append(" | ");
                        text.append(upcomingCount).append(" قادم");
                    }
                    installmentAlertLabel.setText(text.toString());

                    if (overdueCount > 0) {
                        installmentAlertLabel.setStyle(
                                "-fx-font-size: 12px; -fx-text-fill: -fx-danger-text; -fx-background-color: -fx-badge-danger-bg; -fx-padding: 6 10; -fx-background-radius: 8;");
                    } else {
                        installmentAlertLabel.setStyle(
                                "-fx-font-size: 12px; -fx-text-fill: -fx-warning-text; -fx-background-color: -fx-badge-warning-bg; -fx-padding: 6 10; -fx-background-radius: 8;");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to refresh installment alerts", e);
        }
    }

    @FXML
    private void handleInstallmentAlertClick() {
        if (!guardTileAccess("purchase")) return;
        try {
            int reminderDays = getInstallmentReminderDays();
            List<Installment> overdueInstallments = voucherService.getDueInstallments();
            List<Installment> upcomingInstallments = voucherService.getUpcomingInstallments(reminderDays);

            if (overdueInstallments.isEmpty() && upcomingInstallments.isEmpty()) {
                showInfo("تنبيهات الأقساط", "لا توجد أقساط مستحقة أو قادمة.");
                return;
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            StringBuilder msg = new StringBuilder();

            if (!overdueInstallments.isEmpty()) {
                msg.append("⛔ أقساط متأخرة (").append(overdueInstallments.size()).append("):\n");
                for (Installment inst : overdueInstallments) {
                    String customerName = inst.getParentVoucher().getCustomer() != null
                            ? inst.getParentVoucher().getCustomer().getName()
                            : "-";
                    msg.append("  • ").append(customerName)
                            .append(" - سند ").append(inst.getParentVoucher().getVoucherNumber())
                            .append(" - القسط ").append(inst.getInstallmentNumber())
                            .append("/").append(inst.getParentVoucher().getTotalInstallments())
                            .append(" - ").append(currencyFormat.format(inst.getAmount()))
                            .append(" - مستحق: ").append(inst.getDueDate().format(fmt))
                            .append("\n");
                }
            }

            if (!upcomingInstallments.isEmpty()) {
                if (msg.length() > 0)
                    msg.append("\n");
                msg.append("⚠ أقساط قادمة خلال ").append(reminderDays).append(" أيام (")
                        .append(upcomingInstallments.size()).append("):\n");
                for (Installment inst : upcomingInstallments) {
                    String customerName = inst.getParentVoucher().getCustomer() != null
                            ? inst.getParentVoucher().getCustomer().getName()
                            : "-";
                    msg.append("  • ").append(customerName)
                            .append(" - سند ").append(inst.getParentVoucher().getVoucherNumber())
                            .append(" - القسط ").append(inst.getInstallmentNumber())
                            .append("/").append(inst.getParentVoucher().getTotalInstallments())
                            .append(" - ").append(currencyFormat.format(inst.getAmount()))
                            .append(" - مستحق: ").append(inst.getDueDate().format(fmt))
                            .append("\n");
                }
            }

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("تنبيهات الأقساط");
            alert.setHeaderText("الأقساط المستحقة والقادمة");
            alert.setContentText(msg.toString());
            alert.getDialogPane().setMinWidth(500);
            alert.showAndWait();
        } catch (Exception e) {
            logger.error("Failed to show installment alerts", e);
            showError("خطأ", "فشل في عرض تنبيهات الأقساط");
        }
    }

    @SuppressWarnings("unused")
    private void showInstallmentStartupAlert() {
        Platform.runLater(() -> {
            try {
                int reminderDays = getInstallmentReminderDays();
                List<Installment> overdueInstallments = voucherService.getDueInstallments();
                List<Installment> upcomingInstallments = voucherService.getUpcomingInstallments(reminderDays);

                if (overdueInstallments.isEmpty() && upcomingInstallments.isEmpty()) {
                    return;
                }

                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                StringBuilder msg = new StringBuilder();

                if (!overdueInstallments.isEmpty()) {
                    msg.append("⛔ لديك ").append(overdueInstallments.size()).append(" قسط متأخر!\n\n");
                    for (Installment inst : overdueInstallments) {
                        String customerName = inst.getParentVoucher().getCustomer() != null
                                ? inst.getParentVoucher().getCustomer().getName()
                                : "-";
                        msg.append("  • ").append(customerName)
                                .append(" - ").append(currencyFormat.format(inst.getAmount()))
                                .append(" - مستحق: ").append(inst.getDueDate().format(fmt))
                                .append("\n");
                    }
                }

                if (!upcomingInstallments.isEmpty()) {
                    if (msg.length() > 0)
                        msg.append("\n");
                    msg.append("⚠ لديك ").append(upcomingInstallments.size())
                            .append(" قسط خلال ").append(reminderDays).append(" أيام:\n\n");
                    for (Installment inst : upcomingInstallments) {
                        String customerName = inst.getParentVoucher().getCustomer() != null
                                ? inst.getParentVoucher().getCustomer().getName()
                                : "-";
                        msg.append("  • ").append(customerName)
                                .append(" - ").append(currencyFormat.format(inst.getAmount()))
                                .append(" - مستحق: ").append(inst.getDueDate().format(fmt))
                                .append("\n");
                    }
                }

                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("تنبيه الأقساط");
                alert.setHeaderText("تنبيه: لديك أقساط تحتاج انتباهك!");
                alert.setContentText(msg.toString());
                alert.getDialogPane().setMinWidth(500);
                alert.showAndWait();
            } catch (Exception e) {
                logger.error("Failed to show installment startup alert", e);
            }
        });
    }

    @FXML
    private void handlePendingPaymentsAlertClick() {
        try {
            List<Sale> pendingPayments = salesService.getPendingPayments();
            if (pendingPayments.isEmpty()) {
                showInfo("المدفوعات المعلقة", "لا توجد فواتير معلقة.");
                return;
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            StringBuilder msg = new StringBuilder();
            msg.append("الفواتير المعلقة (").append(pendingPayments.size()).append("):\n\n");

            for (Sale sale : pendingPayments) {
                String customerName = sale.getCustomer() != null ? sale.getCustomer().getName() : "-";
                double finalAmt = sale.getFinalAmount() != null ? sale.getFinalAmount() : 0;
                double paidAmt = sale.getPaidAmount() != null ? sale.getPaidAmount() : 0;
                double remaining = finalAmt - paidAmt;
                String currency = sale.getCurrency() != null ? sale.getCurrency() : "";

                msg.append("• فاتورة ").append(sale.getSaleCode())
                        .append(" - ").append(customerName)
                        .append(" - المبلغ: ").append(currencyFormat.format(finalAmt)).append(" ").append(currency)
                        .append(" - المتبقي: ").append(currencyFormat.format(remaining)).append(" ").append(currency);
                if (sale.getSaleDate() != null) {
                    msg.append(" - ").append(sale.getSaleDate().toLocalDate().format(fmt));
                }
                msg.append("\n");

            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("المدفوعات المعلقة");
            alert.setHeaderText("فواتير تنتظر الدفع");
            alert.setContentText(msg.toString());
            alert.getDialogPane().setMinWidth(550);
            alert.showAndWait();
        } catch (Exception e) {
            logger.error("Failed to show pending payments", e);
            showError("خطأ", "فشل في عرض المدفوعات المعلقة");
        }
    }

    @FXML
    private void handleLowStockAlertClick() {
        try {
            List<Product> lowStockProducts = inventoryService.getLowStockProducts();
            if (lowStockProducts.isEmpty()) {
                showInfo("تنبيهات المخزون", "لا توجد منتجات منخفضة المخزون.");
                return;
            }

            StringBuilder msg = new StringBuilder();
            msg.append("المنتجات منخفضة المخزون (").append(lowStockProducts.size()).append("):\n\n");

            for (Product product : lowStockProducts) {
                double qty = product.getQuantityInStock() != null ? product.getQuantityInStock() : 0;
                double minStock = product.getMinimumStock() != null ? product.getMinimumStock() : 0;
                String unit = product.getUnitOfMeasure() != null ? product.getUnitOfMeasure() : "";

                msg.append("• ").append(product.getName());
                if (product.getProductCode() != null) {
                    msg.append(" (").append(product.getProductCode()).append(")");
                }
                msg.append("\n   الكمية الحالية: ").append(currencyFormat.format(qty)).append(" ").append(unit)
                        .append(" | الحد الأدنى: ").append(currencyFormat.format(minStock)).append(" ").append(unit)
                        .append("\n");
            }

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("تنبيهات المخزون");
            alert.setHeaderText("منتجات منخفضة المخزون");
            alert.setContentText(msg.toString());
            alert.getDialogPane().setMinWidth(500);
            alert.showAndWait();
        } catch (Exception e) {
            logger.error("Failed to show low stock alerts", e);
            showError("خطأ", "فشل في عرض تنبيهات المخزون");
        }
    }

    @FXML
    private void handleNewCustomer() {
        handleAccounts();
    }

    @FXML
    private void handleNewProduct() {
        if (!guardTileAccess("new-product")) return;
        TabManager.getInstance().openTab(
                "new-product",
                "منتج جديد",
                "add_product.svg",
                "/views/ProductForm.fxml",
                (ProductController controller) -> controller.setTabMode(true));
    }

    @FXML
    private void handleNewSale() {
        if (!guardTileAccess("pos")) return;
        TabManager.getInstance().openTab(
                "new-sale",
                "بيع جديد",
                "sales_invoice.svg",
                "/views/SaleForm.fxml",
                (SaleFormController controller) -> {
                    controller.setTabMode(true);
                    controller.setMainApp(mainApp);
                });
    }

    @FXML
    private void handleViewInventory() {
        if (!guardTileAccess("view-inventory")) return;
        TabManager.getInstance().openTab(
                "inventory",
                "المخزون",
                "view_inventory.svg",
                "/views/InventoryList.fxml",
                null);
    }

    @FXML
    private void handleLowStock() {
        if (!guardTileAccess("low-stock")) return;
        TabManager.getInstance().openTab(
                "low-stock",
                "منخفض المخزون",
                "low_stock.svg",
                "/views/LowStockList.fxml",
                null);
    }

    @FXML
    private void handleExpiryAlerts() {
        if (!guardTileAccess("expiry-alerts")) return;
        TabManager.getInstance().openTab(
                "expiry-alerts",
                "تنبيهات الانتهاء",
                "low_stock.svg",
                "/views/ExpiryAlerts.fxml",
                null);
    }

    @FXML
    private void handleAddStock() {
        TabManager.getInstance().openTab(
                "add-stock",
                "إضافة مخزون",
                "add_stock.svg",
                "/views/AddStockDialog.fxml",
                (AddStockController controller) -> controller.setTabMode(true));
    }

    @FXML
    private void handleBarcodePrint() {
        if (!guardTileAccess("barcode-print")) return;
        TabManager.getInstance().openTab(
                "barcode-print",
                "طباعة باركود",
                "/views/BarcodePrint.fxml",
                null);
    }

    @FXML
    private void handleManageCategories() {
        TabManager.getInstance().openTab(
                "categories",
                "الفئات",
                "categories_management.svg",
                "/views/CategoryManager.fxml",
                (CategoryController controller) -> controller.setTabMode(true));
    }

    @FXML
    private void handleViewSales() {
        if (!guardTileAccess("view-sales")) return;
        TabManager.getInstance().openTab(
                "sales",
                "المبيعات",
                "view_sales.svg",
                "/views/SaleList.fxml",
                (SaleListController controller) -> {
                    controller.setMainApp(mainApp);
                    controller.setTabMode(true);
                    controller.setTabId("sales");
                });
    }

    @FXML
    private void handleSalesReport() {
        if (!SessionManager.getInstance().canAccessReports()) {
            showError("غير مسموح", "ليس لديك صلاحية الوصول للتقارير");
            return;
        }
        TabManager.getInstance().openTab(
                "sales-report",
                "تقارير المبيعات",
                "sales_reports.svg",
                "/views/SalesReport.fxml",
                null);
    }

    @FXML
    private void handlePharmacyReports() {
        if (!SessionManager.getInstance().canAccessReports()) {
            showError("غير مسموح", "ليس لديك صلاحية الوصول للتقارير");
            return;
        }
        TabManager.getInstance().openTab(
                "pharmacy-reports",
                "التقارير",
                "sales_reports.svg",
                "/views/PharmacyReports.fxml",
                (PharmacyReportsController controller) -> {
                    controller.setTabMode(true);
                    controller.setTabId("pharmacy-reports");
                });
    }

    @FXML
    private void handleAccounts() {
        TabManager.getInstance().openTab(
                "accounts",
                "حسابات",
                "statement.svg",
                "/views/Accounts.fxml",
                (AccountsController controller) -> {
                    controller.setMainApp(mainApp);
                    controller.setTabMode(true);
                    controller.setTabId("accounts");
                });
    }

    @FXML
    private void handlePendingPayments() {
        handleAccounts();
    }

    @FXML
    private void handleProductReturn() {
        if (!guardTileAccess("product-return")) return;
        TabManager.getInstance().openTab(
                "product-return",
                "إرجاع مواد",
                "return_items.svg",
                "/views/ReturnForm.fxml",
                (ReturnController controller) -> controller.setTabMode(true));
    }

    @FXML
    private void handleSettings() {
        if (!SessionManager.getInstance().canAccessSettings()) {
            showError("غير مسموح", "ليس لديك صلاحية الوصول للإعدادات");
            return;
        }
        TabManager.getInstance().openTab(
                "settings",
                "الإعدادات",
                "settings.svg",
                "/views/Settings.fxml",
                (SettingsController controller) -> {
                });
    }

    @FXML
    private void handleUserManagement() {
        if (!SessionManager.getInstance().canManageUsers()) {
            showError("غير مسموح", "ليس لديك صلاحية إدارة المستخدمين");
            return;
        }
        TabManager.getInstance().openTab(
                "user-management",
                "إدارة المستخدمين",
                "user_management.svg",
                "/views/UserManagement.fxml",
                (UserManagementController controller) -> controller.setTabMode(true));
    }

    @FXML
    private void handleLogout() {
        if (mainApp != null) {
            mainApp.logout();
        }
    }

    @FXML
    private void handleLock() {
        // Lock the app - go back to login but keep user remembered
        if (mainApp != null) {
            mainApp.lock();
        }
    }

    @FXML
    private void handleUpdateNow() {
        UpdateCheckResult update = availableUpdate;
        if (update == null || update.getDownloadUrl() == null || update.getDownloadUrl().isBlank()) {
            return;
        }

        if (updateProgress != null) {
            updateProgress.setVisible(true);
        }
        if (updateButton != null) {
            updateButton.setDisable(true);
        }
        if (updateStatusLabel != null) {
            updateStatusLabel.setText("جاري تنزيل التحديث...");
        }

        String fileName = "PharmaX-Setup-" + update.getLatestVersion() + ".exe";
        updateService.downloadInstallerAsync(update.getDownloadUrl(), fileName).whenComplete((path, err) -> {
            Platform.runLater(() -> {
                if (err != null) {
                    logger.error("Update download failed", err);
                    if (updateProgress != null) {
                        updateProgress.setVisible(false);
                    }
                    if (updateButton != null) {
                        updateButton.setDisable(false);
                    }
                    if (updateStatusLabel != null) {
                        updateStatusLabel.setText("فشل تنزيل التحديث");
                    }
                    return;
                }

                if (path == null) {
                    if (updateProgress != null) {
                        updateProgress.setVisible(false);
                    }
                    if (updateButton != null) {
                        updateButton.setDisable(false);
                    }
                    if (updateStatusLabel != null) {
                        updateStatusLabel.setText("فشل تنزيل التحديث");
                    }
                    return;
                }

                if (updateStatusLabel != null) {
                    updateStatusLabel.setText("جاري تثبيت التحديث...");
                }
                if (updateProgress != null) {
                    updateProgress.setVisible(false);
                }

                try {
                    // Application will close automatically after launching installer
                    UpdateInstallerLauncher.launchInstaller(path);
                    // Code below won't execute as System.exit(0) is called
                } catch (Exception e) {
                    logger.error("Failed to launch installer", e);
                    if (updateButton != null) {
                        updateButton.setDisable(false);
                    }
                    if (updateStatusLabel != null) {
                        updateStatusLabel.setText("فشل تشغيل التحديث");
                    }
                    showError("فشل التحديث", "تعذر تشغيل مثبت التحديث. حاول مرة أخرى.");
                }
            });
        });
    }

    public void refreshAfterLogin() {
        loadCurrentUserInfo();
        applyRolePermissions();
        buildDashboardTiles();
        refreshDashboard();
    }

    @FXML
    private void handleCheckForUpdates() {
        if (checkUpdateButton != null) {
            checkUpdateButton.setDisable(true);
        }
        if (updateStatusLabel != null) {
            updateStatusLabel.setText("جاري فحص التحديثات...");
        }
        if (updateProgress != null) {
            updateProgress.setVisible(true);
        }
        if (updateButton != null) {
            updateButton.setVisible(false);
        }

        String currentVersion = AppVersion.current();
        updateService.checkForUpdateAsync(currentVersion).whenComplete((result, err) -> {
            Platform.runLater(() -> {
                if (checkUpdateButton != null) {
                    checkUpdateButton.setDisable(false);
                }
                if (updateProgress != null) {
                    updateProgress.setVisible(false);
                }

                if (err != null) {
                    logger.warn("Manual update check failed", err);
                    if (updateStatusLabel != null) {
                        updateStatusLabel.setText("تعذر فحص التحديثات");
                    }
                    if (updateButton != null) {
                        updateButton.setVisible(false);
                    }
                    showError("فشل فحص التحديثات", "تعذر الاتصال بخادم التحديثات. تأكد من اتصالك بالإنترنت.");
                    return;
                }

                if (result != null && result.isUpdateAvailable()) {
                    availableUpdate = result;
                    if (updateStatusLabel != null) {
                        updateStatusLabel.setText("يوجد تحديث v" + result.getLatestVersion());
                    }
                    if (updateButton != null) {
                        updateButton.setVisible(true);
                        updateButton.setDisable(false);
                    }
                    showInfo("تحديث متوفر",
                            "يوجد إصدار جديد v" + result.getLatestVersion() + "\n\nاضغط على زر 'تحديث الآن' للتحديث.");
                } else {
                    if (updateStatusLabel != null) {
                        updateStatusLabel.setText("لا توجد تحديثات");
                    }
                    if (updateButton != null) {
                        updateButton.setVisible(false);
                    }
                    showInfo("لا توجد تحديثات", "أنت تستخدم أحدث إصدار من البرنامج (v" + currentVersion + ")");
                }
            });
        });
    }

    @FXML
    private void handleFirebaseSync() {
        // TODO: Implement Firebase sync
        showInfo("قريباً", "ميزة المزامنة مع فايربيس قيد التطوير");
    }

    private void updateDriveStatusIndicator() {
        com.pharmax.service.drive.BackupService bs = mainApp != null ? mainApp.getBackupService() : null;
        boolean connected = bs != null && bs.isDriveConnected();

        Platform.runLater(() -> {
            if (connected) {
                if (driveStatusIndicator != null) {
                    driveStatusIndicator.setText("●");
                    driveStatusIndicator.setStyle("-fx-font-size: 10px; -fx-text-fill: -fx-success-text;");
                }
                if (driveStatusLabel != null) {
                    driveStatusLabel.setText("Google Drive: متصل");
                    driveStatusLabel
                            .setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-success-text; -fx-font-weight: bold;");
                }
                if (connectDriveButton != null) {
                    connectDriveButton.setText("متصل ✓");
                    connectDriveButton.setDisable(true);
                    connectDriveButton.setStyle(
                            "-fx-background-color: rgba(16,185,129,0.2); -fx-text-fill: -fx-success-text; -fx-font-size: 10px; -fx-padding: 3 10; -fx-background-radius: 8;");
                }
            } else {
                if (driveStatusIndicator != null) {
                    driveStatusIndicator.setText("●");
                    driveStatusIndicator.setStyle("-fx-font-size: 10px; -fx-text-fill: -fx-danger-text;");
                }
                if (driveStatusLabel != null) {
                    driveStatusLabel.setText("Google Drive: غير متصل");
                    driveStatusLabel
                            .setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-text-hint; -fx-font-weight: 600;");
                }
                if (connectDriveButton != null) {
                    connectDriveButton.setText("ربط Google Drive");
                    connectDriveButton.setDisable(false);
                    connectDriveButton.setStyle(
                            "-fx-background-color: -fx-accent-bg; -fx-text-fill: -fx-accent-text; -fx-font-size: 10px; -fx-padding: 3 10; -fx-background-radius: 8;");
                }
            }
        });
    }

    @FXML
    private void handleConnectGoogleDrive() {
        if (mainApp == null) {
            showError("خطأ", "التطبيق غير جاهز");
            return;
        }

        com.pharmax.service.drive.BackupService bs = mainApp.getBackupService();
        com.pharmax.service.drive.GoogleDriveService driveService = mainApp.getGoogleDriveService();

        if (bs != null && bs.isDriveConnected()) {
            showInfo("Google Drive", "الحساب متصل بالفعل.");
            return;
        }

        if (driveService == null) {
            showError("خطأ", "خدمة Google Drive غير متوفرة");
            return;
        }

        Platform.runLater(() -> {
            if (driveStatusIndicator != null) {
                driveStatusIndicator.setStyle("-fx-font-size: 10px; -fx-text-fill: -fx-warning-text;");
            }
            if (driveStatusLabel != null) {
                driveStatusLabel.setText("Google Drive: جارِ الاتصال...");
                driveStatusLabel
                        .setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-warning-text; -fx-font-weight: 600;");
            }
            if (connectDriveButton != null) {
                connectDriveButton.setDisable(true);
                connectDriveButton.setText("جارِ الاتصال...");
            }
        });

        new Thread(() -> {
            try {
                driveService.initialize();
                if (bs != null) {
                    bs.startHourlyBackup();
                }
                logger.info("Google Drive connected successfully via dashboard button");
                Platform.runLater(() -> {
                    updateDriveStatusIndicator();
                    showInfo("Google Drive", "تم الاتصال بـ Google Drive بنجاح!");
                });
            } catch (Exception e) {
                logger.error("Failed to connect to Google Drive", e);
                Platform.runLater(() -> {
                    updateDriveStatusIndicator();
                    showError("خطأ", "فشل الاتصال بـ Google Drive: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void handleReceiptVoucher() {
        try {
            TabManager.getInstance().openTab(
                    "receipt-voucher",
                    "📥 سند قبض",
                    "/views/ReceiptVoucher.fxml",
                    (ReceiptVoucherController controller) -> {
                        controller.setTabMode(true);
                        controller.setTabId("receipt-voucher");
                    });
        } catch (Exception e) {
            logger.error("Failed to open receipt voucher", e);
            showError("خطأ", "فشل في فتح سند القبض: " + e.getMessage());
        }
    }

    @FXML
    private void handlePaymentVoucher() {
        if (!guardTileAccess("payment-voucher")) return;
        try {
            TabManager.getInstance().openTab(
                    "payment-voucher",
                    "📤 سند دفع",
                    "/views/PaymentVoucher.fxml",
                    (PaymentVoucherController controller) -> {
                        controller.setTabMode(true);
                        controller.setTabId("payment-voucher");
                    });
        } catch (Exception e) {
            logger.error("Failed to open payment voucher", e);
            showError("خطأ", "فشل في فتح سند الدفع: " + e.getMessage());
        }
    }

    @FXML
    private void handlePurchase() {
        if (!guardTileAccess("purchase")) return;
        try {
            TabManager.getInstance().openTab(
                    "purchase",
                    "🛍️ المشتريات",
                    "/views/Purchase.fxml",
                    (PurchaseController controller) -> {
                        controller.setTabMode(true);
                        controller.setTabId("purchase");
                    });
        } catch (Exception e) {
            logger.error("Failed to open purchase", e);
            showError("خطأ", "فشل في فتح المشتريات: " + e.getMessage());
        }
    }

    @FXML
    private void handlePurchaseReturn() {
        if (!guardTileAccess("purchase-return")) return;
        try {
            TabManager.getInstance().openTab(
                    "purchase-return",
                    "↩️ مرتجع شراء",
                    "/views/PurchaseReturn.fxml",
                    (PurchaseReturnController controller) -> {
                        controller.setTabMode(true);
                        controller.setTabId("purchase-return");
                    });
        } catch (Exception e) {
            logger.error("Failed to open purchase return", e);
            showError("خطأ", "فشل في فتح مرتجع الشراء: " + e.getMessage());
        }
    }

    @FXML
    private void handleCashbox() {
        if (!guardTileAccess("cashbox")) return;
        try {
            TabManager.getInstance().openTab(
                    "cashbox",
                    "💵 الصندوق",
                    "/views/Cashbox.fxml",
                    (CashboxController controller) -> {
                        controller.setTabMode(true);
                        controller.setTabId("cashbox");
                    });
        } catch (Exception e) {
            logger.error("Failed to open cashbox", e);
            showError("خطأ", "فشل في فتح شاشة الصندوق: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewReceiptVouchers() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/VoucherList.fxml"));
            Parent root = loader.load();

            VoucherListController controller = loader.getController();
            controller.setVoucherType(VoucherType.RECEIPT);

            Stage stage = new Stage();
            stage.setTitle("سندات القبض");
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open receipt vouchers list", e);
            showError("خطأ", "فشل في فتح قائمة سندات القبض");
        }
    }

    @FXML
    private void handleViewPaymentVouchers() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/VoucherList.fxml"));
            Parent root = loader.load();

            VoucherListController controller = loader.getController();
            controller.setVoucherType(VoucherType.PAYMENT);

            Stage stage = new Stage();
            stage.setTitle("سندات الدفع");
            Scene scene = new Scene(root);
            com.pharmax.util.ThemeManager.getInstance().applyTheme(scene);
            com.pharmax.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            com.pharmax.util.ThemeManager.getInstance().registerStage(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open payment vouchers list", e);
            showError("خطأ", "فشل في فتح قائمة سندات الدفع");
        }
    }

    @FXML
    private void handleDueInstallments() {
        handleInstallmentAlertClick();
    }

    @FXML
    private void handleAbout() {
        if (!guardTileAccess("about")) return;
        showInfo("عن البرنامج",
                "PharmaX v1.3.0\n\n" +
                        "من تطوير: KervanjiHolding\n" +
                        "الموقع: Kervanjiholding.com\n\n" +
                        "نظام متكامل لإدارة الصيدليات\n\n" +
                        "المميزات:\n" +
                        "• إدارة الأدوية والمخزون وتاريخ الصلاحية\n" +
                        "• نظام المبيعات والوصفات الطبية\n" +
                        "• إدارة العملاء والموردين\n" +
                        "• إصدار الإيصالات الفورية\n" +
                        "• تخزين البيانات محلياً\n" +
                        "• دعم المزامنة السحابية (قريباً)\n\n" +
                        "للدعم الفني: 07730199732\n\n" +
                        "© 2025 KervanjiHolding. جميع الحقوق محفوظة.");
    }

    @FXML
    private void handleExit() {
        System.exit(0);
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
