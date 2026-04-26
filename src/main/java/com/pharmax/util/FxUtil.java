package com.pharmax.util;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Utility helpers for JavaFX window operations. */
public final class FxUtil {
    private FxUtil() {}

    /** Close window using any Node inside it (Button, Pane, etc.). */
    public static void closeWindow(Node anyNodeInWindow) {
        if (anyNodeInWindow == null) return;
        Window w = anyNodeInWindow.getScene() != null ? anyNodeInWindow.getScene().getWindow() : null;
        if (w instanceof Stage s) s.close();
    }

    /** Close window using ActionEvent source (recommended for button handlers). */
    public static void closeWindow(ActionEvent e) {
        if (e == null) return;
        Object src = e.getSource();
        if (src instanceof Node node) closeWindow(node);
    }
}
