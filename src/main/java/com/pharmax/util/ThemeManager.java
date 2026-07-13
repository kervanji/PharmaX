package com.pharmax.util;

import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * مدير الثيم الموحّد - Singleton
 * مسؤول عن تطبيق وتبديل الثيم على كل النوافذ والمشاهد.
 */
public class ThemeManager {
    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    private static final String PREF_THEME = "PharmaX.theme";

    private static ThemeManager instance;

    /** أنواع الثيمات المتاحة */
    public enum ThemeType {
        DARK_BLUE("داكن أزرق", "/styles/theme.css"),
        DARK_GREEN("داكن أخضر", "/styles/theme-dark-green.css"),
        LIGHT("فاتح", "/styles/theme-light.css"),
        LIGHT_BLUE("فاتح أزرق", "/styles/theme-light-blue.css"),
        LIGHT_WARM("فاتح دافئ", "/styles/theme-light-warm.css");

        private final String displayName;
        private final String cssPath;

        ThemeType(String displayName, String cssPath) {
            this.displayName = displayName;
            this.cssPath = cssPath;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getCssPath() {
            return cssPath;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final String MAIN_CSS_PATH = "/styles/main.css";

    private ThemeType currentTheme;
    private final List<Stage> managedStages = new ArrayList<>();

    private ThemeManager() {
        // Load saved theme preference
        Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);
        String savedTheme = prefs.get(PREF_THEME, ThemeType.DARK_BLUE.name());
        try {
            currentTheme = ThemeType.valueOf(savedTheme);
        } catch (IllegalArgumentException e) {
            currentTheme = ThemeType.DARK_BLUE;
        }
    }

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    /**
     * تسجيل Stage لإدارته (يتم إزالته تلقائياً عند إغلاقه)
     */
    public void registerStage(Stage stage) {
        if (stage == null || managedStages.contains(stage)) {
            return;
        }
        managedStages.add(stage);
        AppIconUtil.applyToStage(stage);
        stage.setOnHidden(e -> managedStages.remove(stage));

        // Apply theme to current scene if exists
        if (stage.getScene() != null) {
            applyTheme(stage.getScene());
        }
        WindowsTitleBarStyler.install(stage, currentTheme);

        // Auto-apply theme when scene changes
        stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                applyTheme(newScene);
                WindowsTitleBarStyler.applyToCurrentProcessWindows(currentTheme);
            }
        });
    }

    /**
     * تطبيق الثيم الحالي على Scene
     */
    public void applyTheme(Scene scene) {
        if (scene == null) {
            return;
        }

        ObservableList<String> stylesheets = scene.getStylesheets();

        // Resolve URLs
        String themeUrl = resolveUrl(currentTheme.getCssPath());
        String mainUrl = resolveUrl(MAIN_CSS_PATH);

        // Remove all old theme CSS files
        List<String> toRemove = new ArrayList<>();
        for (String url : stylesheets) {
            for (ThemeType t : ThemeType.values()) {
                String tUrl = resolveUrl(t.getCssPath());
                if (tUrl != null && url.equals(tUrl)) {
                    toRemove.add(url);
                }
            }
        }
        stylesheets.removeAll(toRemove);

        // Add theme.css first (variables), then main.css (rules)
        if (themeUrl != null && !stylesheets.contains(themeUrl)) {
            stylesheets.add(0, themeUrl);
        }
        if (mainUrl != null && !stylesheets.contains(mainUrl)) {
            // main.css should come after theme.css
            int idx = themeUrl != null ? stylesheets.indexOf(themeUrl) + 1 : 0;
            stylesheets.add(idx, mainUrl);
        }
    }

    /**
     * تغيير الثيم وتطبيقه فوراً على كل النوافذ
     */
    public void setTheme(ThemeType theme) {
        if (theme == null) {
            return;
        }
        this.currentTheme = theme;

        // Save preference
        Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);
        prefs.put(PREF_THEME, theme.name());

        // Refresh all open stages
        refreshAllStages();

        logger.info("Theme changed to: {}", theme.getDisplayName());
    }

    /**
     * إعادة تطبيق الثيم على جميع النوافذ المفتوحة
     */
    public void refreshAllStages() {
        for (Stage stage : new ArrayList<>(managedStages)) {
            if (stage != null && stage.getScene() != null) {
                applyTheme(stage.getScene());
                WindowsTitleBarStyler.install(stage, currentTheme);
            }
        }
        WindowsTitleBarStyler.applyToCurrentProcessWindows(currentTheme);
    }

    public boolean isDarkTheme() {
        return currentTheme == ThemeType.DARK_BLUE || currentTheme == ThemeType.DARK_GREEN;
    }

    /**
     * الحصول على الثيم الحالي
     */
    public ThemeType getCurrentTheme() {
        return currentTheme;
    }

    /**
     * الحصول على كل أنواع الثيمات
     */
    public ThemeType[] getAvailableThemes() {
        return ThemeType.values();
    }

    /**
     * تحويل مسار مورد إلى URL خارجي
     */
    private String resolveUrl(String resourcePath) {
        URL url = ThemeManager.class.getResource(resourcePath);
        if (url != null) {
            return url.toExternalForm();
        }
        logger.warn("Could not resolve theme resource: {}", resourcePath);
        return null;
    }
}
