package com.pharmax.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.geometry.Pos;
import com.pharmax.MainApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * مدير التبويبات - يتحكم في فتح وإغلاق التبويبات في الواجهة الرئيسية
 */
public class TabManager {
    private static final Logger logger = LoggerFactory.getLogger(TabManager.class);

    private static TabManager instance;
    private TabPane tabPane;
    private Tab dashboardTab;
    private MainApp mainApp;
    private final Map<String, Tab> openTabs = new HashMap<>();
    private Runnable dashboardRefreshCallback;

    private TabManager() {
    }

    public static TabManager getInstance() {
        if (instance == null) {
            instance = new TabManager();
        }
        return instance;
    }

    public void initialize(TabPane tabPane, Tab dashboardTab, MainApp mainApp) {
        this.tabPane = tabPane;
        this.dashboardTab = dashboardTab;
        this.mainApp = mainApp;
        // Ensure we don't keep stale tabs between sessions / layouts
        this.openTabs.clear();
    }

    public void reset() {
        this.openTabs.clear();
        this.tabPane = null;
        this.dashboardTab = null;
        this.mainApp = null;
        this.dashboardRefreshCallback = null;
    }

    public void setDashboardRefreshCallback(Runnable callback) {
        this.dashboardRefreshCallback = callback;
    }

    public MainApp getMainApp() {
        return mainApp;
    }

    /**
     * فتح تبويب جديد أو التبديل إليه إذا كان مفتوحاً
     */
    public <T> T openTab(String tabId, String title, String fxmlPath) {
        return openTab(tabId, title, fxmlPath, null);
    }

    /**
     * فتح تبويب جديد مع تهيئة الكنترولر
     */
    /**
     * فتح تبويب جديد مع تهيئة الكنترولر وأيقونة SVG
     */
    public <T> T openTab(String tabId, String title, String iconPath, String fxmlPath,
            Consumer<T> controllerInitializer) {
        // التحقق إذا كان التبويب مفتوحاً بالفعل
        if (openTabs.containsKey(tabId)) {
            Tab existingTab = openTabs.get(tabId);
            if (tabPane != null && tabPane.getTabs().contains(existingTab)) {
                tabPane.getSelectionModel().select(existingTab);
                return null;
            }
            // Stale cached tab from previous session/layout
            openTabs.remove(tabId);
        }

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource(fxmlPath));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent content = loader.load();

            T controller = loader.getController();

            // تهيئة الكنترولر إذا كان هناك initializer
            if (controllerInitializer != null && controller != null) {
                controllerInitializer.accept(controller);
            }

            // Apply theme CSS to loaded content's scene (if available)
            // ThemeManager handles this via scene listener, but we ensure stylesheets are on the content
            if (content.getScene() != null) {
                ThemeManager.getInstance().applyTheme(content.getScene());
            }

            // Apply font size scaling to the loaded content
            if (content instanceof javafx.scene.Parent) {
                int fontSize = com.pharmax.util.SessionManager.getInstance().getUiFontSize();
                if (fontSize != 13) {
                    com.pharmax.MainApp.applyFontSizeRecursive((javafx.scene.Parent) content, fontSize);
                }
            }

            // إنشاء التبويب
            Tab tab = createTab(tabId, title, iconPath, content);

            // إضافة التبويب وتحديده
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            openTabs.put(tabId, tab);

            return controller;

        } catch (IOException e) {
            logger.error("Failed to open tab: " + tabId, e);
            return null;
        }
    }

    /**
     * فتح تبويب جديد مع تهيئة الكنترولر (للخلفية)
     */
    public <T> T openTab(String tabId, String title, String fxmlPath, Consumer<T> controllerInitializer) {
        return openTab(tabId, title, null, fxmlPath, controllerInitializer);
    }

    /**
     * إنشاء تبويب مع زر إغلاق وأيقونة SVG اختيارية
     */
    private Tab createTab(String tabId, String title, String iconPath, Parent content) {
        Tab tab = new Tab();
        tab.setContent(content);
        tab.setClosable(true);

        // إنشاء عنوان التبويب مع زر إغلاق
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER);

        // Icon
        if (iconPath != null) {
            javafx.scene.image.Image iconImage = SvgImageLoader.loadSvgImage("/icons/" + iconPath, 20, 20);
            if (iconImage != null) {
                javafx.scene.image.ImageView iconView = new javafx.scene.image.ImageView(iconImage);
                iconView.setFitWidth(20);
                iconView.setFitHeight(20);
                iconView.setPreserveRatio(true);

                // Colorize to white only if in dark theme, otherwise use primary color (dark blue/green)
                if (ThemeManager.getInstance().isDarkTheme()) {
                    javafx.scene.effect.ColorAdjust colorAdjust = new javafx.scene.effect.ColorAdjust();
                    colorAdjust.setBrightness(1.0); // Make it fully white
                    iconView.setEffect(colorAdjust);
                } else {
                    // For light theme, we want a dark color. The original SVGs might be black or white.
                    // We'll apply a color adjust to make them visible.
                    javafx.scene.effect.ColorAdjust colorAdjust = new javafx.scene.effect.ColorAdjust();
                    colorAdjust.setBrightness(-1.0); // Make it black/dark
                    iconView.setEffect(colorAdjust);
                }

                header.getChildren().add(iconView);
            }
        }

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600;");

        Button closeBtn = new Button("×");
        closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: -fx-text-hint; -fx-font-size: 14px; -fx-padding: 0 4; -fx-cursor: hand;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
                "-fx-background-color: -fx-badge-danger-bg; -fx-text-fill: -fx-danger-text; -fx-font-size: 14px; -fx-padding: 0 4; -fx-cursor: hand; -fx-background-radius: 4;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: -fx-text-hint; -fx-font-size: 14px; -fx-padding: 0 4; -fx-cursor: hand;"));
        closeBtn.setOnAction(e -> closeTab(tabId));

        header.getChildren().addAll(titleLabel, closeBtn);
        tab.setGraphic(header);
        tab.setText(null);

        // عند إغلاق التبويب
        tab.setOnClosed(e -> {
            openTabs.remove(tabId);
            refreshDashboard();
        });

        return tab;
    }

    /**
     * إغلاق تبويب محدد
     */
    public void closeTab(String tabId) {
        Tab tab = openTabs.get(tabId);
        if (tab != null) {
            tabPane.getTabs().remove(tab);
            openTabs.remove(tabId);
            refreshDashboard();
        }
    }

    /**
     * إغلاق جميع التبويبات ما عدا لوحة التحكم
     */
    public void closeAllTabs() {
        openTabs.values().forEach(tab -> tabPane.getTabs().remove(tab));
        openTabs.clear();
        tabPane.getSelectionModel().select(dashboardTab);
        refreshDashboard();
    }

    /**
     * العودة إلى لوحة التحكم
     */
    public void goToDashboard() {
        tabPane.getSelectionModel().select(dashboardTab);
    }

    /**
     * تحديث لوحة التحكم
     */
    public void refreshDashboard() {
        if (dashboardRefreshCallback != null) {
            dashboardRefreshCallback.run();
        }
    }

    /**
     * التحقق إذا كان التبويب مفتوحاً
     */
    public boolean isTabOpen(String tabId) {
        return openTabs.containsKey(tabId);
    }

    /**
     * الحصول على TabPane
     */
    public TabPane getTabPane() {
        return tabPane;
    }
}
