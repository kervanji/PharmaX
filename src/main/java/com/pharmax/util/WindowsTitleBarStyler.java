package com.pharmax.util;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the current app theme to the native Windows title bar while keeping
 * the standard window controls and resize behavior.
 */
public final class WindowsTitleBarStyler {
    private static final Logger logger = LoggerFactory.getLogger(WindowsTitleBarStyler.class);

    private static final String WINDOWS_OS_PREFIX = "win";
    private static final String LISTENER_INSTALLED_KEY = "PharmaX.titleBarListenerInstalled";

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19;
    private static final int DWMWA_WINDOW_BORDER_COLOR = 34;
    private static final int DWMWA_CAPTION_COLOR = 35;
    private static final int DWMWA_TEXT_COLOR = 36;

    private static final int COLOR_DARK_BLUE_BG = rgb(0x0f, 0x1b, 0x2d);
    private static final int COLOR_DARK_BLUE_TEXT = rgb(0xe8, 0xed, 0xf4);
    private static final int COLOR_DARK_GREEN_BG = rgb(0x0d, 0x1f, 0x17);
    private static final int COLOR_DARK_GREEN_TEXT = rgb(0xe8, 0xf5, 0xee);
    private static final int COLOR_LIGHT_BG = rgb(0xff, 0xff, 0xff);
    private static final int COLOR_LIGHT_TEXT = rgb(0x00, 0x00, 0x00);
    private static final int COLOR_LIGHT_BORDER = rgb(0xe2, 0xe8, 0xf0);
    private static final int COLOR_LIGHT_BLUE_BG = rgb(0xf0, 0xf8, 0xff);
    private static final int COLOR_LIGHT_BLUE_BORDER = rgb(0xdb, 0xea, 0xfe);
    private static final int COLOR_LIGHT_WARM_BG = rgb(0xff, 0xfd, 0xfa);
    private static final int COLOR_LIGHT_WARM_BORDER = rgb(0xfa, 0xec, 0xd8);

    private static volatile boolean nativeApiAvailable = true;

    private WindowsTitleBarStyler() {
    }

    public static void install(Stage stage, ThemeManager.ThemeType theme) {
        if (!isWindows() || stage == null) {
            return;
        }

        if (stage.getProperties().putIfAbsent(LISTENER_INSTALLED_KEY, Boolean.TRUE) == null) {
            stage.showingProperty().addListener((obs, wasShowing, isShowing) -> {
                if (isShowing) {
                    applyDeferred(ThemeManager.getInstance().getCurrentTheme());
                }
            });
            stage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (isFocused) {
                    applyDeferred(ThemeManager.getInstance().getCurrentTheme());
                }
            });
        }

        if (stage.isShowing()) {
            applyDeferred(theme);
        }
    }

    public static void applyToCurrentProcessWindows(ThemeManager.ThemeType theme) {
        if (!isWindows() || !nativeApiAvailable) {
            return;
        }

        try {
            TitleBarPalette palette = paletteFor(theme);
            for (Pointer hwnd : findCurrentProcessWindows()) {
                applyPalette(hwnd, palette);
            }
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            nativeApiAvailable = false;
            logger.warn("Windows title bar styling is unavailable on this system", e);
        } catch (Exception e) {
            logger.debug("Unable to apply Windows title bar styling", e);
        }
    }

    private static void applyDeferred(ThemeManager.ThemeType theme) {
        if (Platform.isFxApplicationThread()) {
            PauseTransition delay = new PauseTransition(Duration.millis(80));
            delay.setOnFinished(event -> applyToCurrentProcessWindows(theme));
            delay.play();
            return;
        }

        Platform.runLater(() -> applyDeferred(theme));
    }

    private static List<Pointer> findCurrentProcessWindows() {
        int currentPid = Math.toIntExact(ProcessHandle.current().pid());
        List<Pointer> handles = new ArrayList<>();

        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (hwnd == null || Pointer.nativeValue(hwnd) == 0 || !User32.INSTANCE.IsWindowVisible(hwnd)) {
                return true;
            }

            IntByReference windowPid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, windowPid);
            if (windowPid.getValue() == currentPid && hasWindowTitle(hwnd)) {
                handles.add(hwnd);
            }

            return true;
        }, null);

        return handles;
    }

    private static boolean hasWindowTitle(Pointer hwnd) {
        int length = User32.INSTANCE.GetWindowTextLengthW(hwnd);
        if (length <= 0) {
            return false;
        }

        char[] buffer = new char[length + 1];
        int copied = User32.INSTANCE.GetWindowTextW(hwnd, buffer, buffer.length);
        return copied > 0 && Native.toString(buffer).trim().length() > 0;
    }

    private static void applyPalette(Pointer hwnd, TitleBarPalette palette) {
        setIntAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, palette.darkMode ? 1 : 0);
        setIntAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, palette.darkMode ? 1 : 0);
        setIntAttribute(hwnd, DWMWA_CAPTION_COLOR, palette.captionColor);
        setIntAttribute(hwnd, DWMWA_TEXT_COLOR, palette.textColor);
        setIntAttribute(hwnd, DWMWA_WINDOW_BORDER_COLOR, palette.borderColor);
    }

    private static void setIntAttribute(Pointer hwnd, int attribute, int value) {
        IntByReference ref = new IntByReference(value);
        DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, ref, Integer.BYTES);
    }

    private static TitleBarPalette paletteFor(ThemeManager.ThemeType theme) {
        if (theme == ThemeManager.ThemeType.DARK_GREEN) {
            return new TitleBarPalette(true, COLOR_DARK_GREEN_BG, COLOR_DARK_GREEN_TEXT, COLOR_DARK_GREEN_BG);
        }
        if (theme == ThemeManager.ThemeType.LIGHT_BLUE) {
            return new TitleBarPalette(false, COLOR_LIGHT_BLUE_BG, COLOR_LIGHT_TEXT, COLOR_LIGHT_BLUE_BORDER);
        }
        if (theme == ThemeManager.ThemeType.LIGHT_WARM) {
            return new TitleBarPalette(false, COLOR_LIGHT_WARM_BG, COLOR_LIGHT_TEXT, COLOR_LIGHT_WARM_BORDER);
        }
        if (theme == ThemeManager.ThemeType.LIGHT) {
            return new TitleBarPalette(false, COLOR_LIGHT_BG, COLOR_LIGHT_TEXT, COLOR_LIGHT_BORDER);
        }
        return new TitleBarPalette(true, COLOR_DARK_BLUE_BG, COLOR_DARK_BLUE_TEXT, COLOR_DARK_BLUE_BG);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith(WINDOWS_OS_PREFIX);
    }

    private static int rgb(int red, int green, int blue) {
        return (blue << 16) | (green << 8) | red;
    }

    private record TitleBarPalette(boolean darkMode, int captionColor, int textColor, int borderColor) {
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean EnumWindows(WndEnumProc callback, Pointer data);

        boolean IsWindowVisible(Pointer hwnd);

        int GetWindowThreadProcessId(Pointer hwnd, IntByReference processId);

        int GetWindowTextLengthW(Pointer hwnd);

        int GetWindowTextW(Pointer hwnd, char[] text, int maxCount);
    }

    private interface WndEnumProc extends StdCallLibrary.StdCallCallback {
        boolean callback(Pointer hwnd, Pointer data);
    }

    private interface DwmApi extends StdCallLibrary {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

        int DwmSetWindowAttribute(Pointer hwnd, int attribute, IntByReference attributeValue, int attributeSize);
    }
}
