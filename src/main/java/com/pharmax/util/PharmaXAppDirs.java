package com.pharmax.util;

import java.io.File;

/**
 * Persistent application data directory (survives app updates/reinstalls).
 */
public final class PharmaXAppDirs {
    private PharmaXAppDirs() {
    }

    public static File getAppDataDir() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase();
        File appDataDir;

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                appDataDir = new File(appData, "PharmaX");
            } else {
                appDataDir = new File(userHome, "AppData\\Roaming\\PharmaX");
            }
        } else if (os.contains("mac")) {
            appDataDir = new File(userHome, "Library/Application Support/PharmaX");
        } else {
            appDataDir = new File(userHome, ".pharmax");
        }

        if (!appDataDir.exists()) {
            appDataDir.mkdirs();
        }
        return appDataDir;
    }

    public static File getLicenseFile() {
        return new File(getAppDataDir(), "license.dat");
    }

    public static File getCredentialsFile() {
        return new File(getAppDataDir(), "credentials.json");
    }

    public static File getDriveTokensDir() {
        return new File(getAppDataDir(), "drive_tokens");
    }
}
