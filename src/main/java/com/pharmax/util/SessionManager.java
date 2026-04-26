package com.pharmax.util;

import com.pharmax.model.User;
import com.pharmax.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.util.Properties;

public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static SessionManager instance;

    private static final String CONFIG_FILE = "app_config.properties";
    private static final String LAST_USERNAME_KEY = "last_username";
    private static final String REMEMBER_USER_KEY = "remember_user";
    private static final String UI_FONT_SIZE_KEY = "ui_font_size";

    private User currentUser;
    private LocalDateTime sessionStartTime;
    private Runnable onLogoutCallback;

    private SessionManager() {
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void startSession(User user, boolean rememberUser) {
        this.currentUser = user;
        this.sessionStartTime = LocalDateTime.now();

        if (rememberUser) {
            saveLastUsername(user.getUsername());
        }

        logger.info("Session started for user: {} at {}", user.getUsername(), sessionStartTime);
    }

    public void endSession() {
        if (currentUser != null) {
            logger.info("Session ended for user: {}", currentUser.getUsername());
        }
        this.currentUser = null;
        this.sessionStartTime = null;

        if (onLogoutCallback != null) {
            onLogoutCallback.run();
        }
    }

    public void lockSession() {
        if (currentUser != null) {
            logger.info("Session locked for user: {}", currentUser.getUsername());
        }
        this.currentUser = null;
        this.sessionStartTime = null;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public UserRole getCurrentRole() {
        return currentUser != null ? currentUser.getRole() : null;
    }

    public String getCurrentUsername() {
        return currentUser != null ? currentUser.getUsername() : null;
    }

    public String getCurrentDisplayName() {
        return currentUser != null ? currentUser.getDisplayName() : null;
    }

    public LocalDateTime getSessionStartTime() {
        return sessionStartTime;
    }

    public void setOnLogoutCallback(Runnable callback) {
        this.onLogoutCallback = callback;
    }

    // Permission checks
    public boolean canSeeCost() {
        return currentUser != null && currentUser.getRole().canSeeCost();
    }

    public boolean canSeeProfit() {
        return currentUser != null && currentUser.getRole().canSeeProfit();
    }

    public boolean canEditProducts() {
        return currentUser != null && currentUser.getRole().canEditProducts();
    }

    public boolean canDeleteInvoices() {
        return currentUser != null && currentUser.getRole().canDeleteInvoices();
    }

    public boolean canManageUsers() {
        return currentUser != null && currentUser.getRole().canManageUsers();
    }

    public boolean canAccessReports() {
        return currentUser != null && currentUser.getRole().canAccessReports();
    }

    public boolean canAccessSettings() {
        return currentUser != null && currentUser.getRole().canAccessSettings();
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == UserRole.ADMIN;
    }

    public boolean isSeller() {
        return currentUser != null && currentUser.getRole() == UserRole.SELLER;
    }

    // Local storage for remembering user
    public void saveLastUsername(String username) {
        try {
            Properties props = loadProperties();
            props.setProperty(LAST_USERNAME_KEY, username);
            props.setProperty(REMEMBER_USER_KEY, "true");
            saveProperties(props);
            logger.info("Saved last username: {}", username);
        } catch (Exception e) {
            logger.error("Failed to save last username", e);
        }
    }

    public String getLastUsername() {
        try {
            Properties props = loadProperties();
            String rememberUser = props.getProperty(REMEMBER_USER_KEY, "false");
            if ("true".equals(rememberUser)) {
                return props.getProperty(LAST_USERNAME_KEY, "");
            }
        } catch (Exception e) {
            logger.error("Failed to load last username", e);
        }
        return "";
    }

    public void clearLastUsername() {
        try {
            Properties props = loadProperties();
            props.remove(LAST_USERNAME_KEY);
            props.setProperty(REMEMBER_USER_KEY, "false");
            saveProperties(props);
            logger.info("Cleared last username");
        } catch (Exception e) {
            logger.error("Failed to clear last username", e);
        }
    }

    public int getUiFontSize() {
        try {
            Properties props = loadProperties();
            String value = props.getProperty(UI_FONT_SIZE_KEY, "13");
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            logger.error("Failed to load UI font size", e);
            return 13;
        }
    }

    public void setUiFontSize(int fontSizePx) {
        try {
            int clamped = Math.max(10, Math.min(24, fontSizePx));
            Properties props = loadProperties();
            props.setProperty(UI_FONT_SIZE_KEY, String.valueOf(clamped));
            saveProperties(props);
            logger.info("Saved UI font size: {}", clamped);
        } catch (Exception e) {
            logger.error("Failed to save UI font size", e);
        }
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException e) {
                logger.error("Failed to load config file", e);
            }
        }
        return props;
    }

    private void saveProperties(Properties props) {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "PharmaX Application Configuration");
        } catch (IOException e) {
            logger.error("Failed to save config file", e);
        }
    }
}
