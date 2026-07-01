package com.pharmax.util;

import com.pharmax.MainApp;

import java.io.File;
import java.net.URI;
import java.net.URL;

/**
 * Resolves the installed application directory (where PharmaX.jar / PharmaX.exe live).
 */
public final class AppInstallPaths {
    private AppInstallPaths() {
    }

    public static File getInstallDirectory() {
        File fromCodeSource = resolveFromCodeSource();
        if (fromCodeSource != null) {
            return fromCodeSource;
        }

        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            return new File(userDir);
        }

        return new File(".");
    }

    public static File getBundledCredentialsFile() {
        return new File(getInstallDirectory(), "credentials.json");
    }

    private static File resolveFromCodeSource() {
        for (Class<?> anchor : new Class<?>[] { MainApp.class, AppInstallPaths.class }) {
            File dir = resolveFromClass(anchor);
            if (dir != null) {
                return dir;
            }
        }
        return null;
    }

    private static File resolveFromClass(Class<?> anchor) {
        try {
            URL location = anchor.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }
            URI uri = location.toURI();
            File code = new File(uri);
            if (code.isFile()) {
                File parent = code.getParentFile();
                return parent != null ? parent : code;
            }
            if (code.isDirectory()) {
                return code;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
