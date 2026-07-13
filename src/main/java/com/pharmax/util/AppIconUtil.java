package com.pharmax.util;

import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AppIconUtil {
    private static final Logger logger = LoggerFactory.getLogger(AppIconUtil.class);

    private static final String PRIMARY_ICON = "/templates/PharmaX_transparent.png";
    private static final String FALLBACK_ICON = "/templates/PharmaX.png";
    private static final String[] ICON_RESOURCES = {PRIMARY_ICON, FALLBACK_ICON};

    private static List<Image> stageIcons;
    private static boolean taskbarIconApplied;

    private AppIconUtil() {
    }

    public static void applyToStage(Stage stage) {
        if (stage == null) {
            return;
        }

        List<Image> icons = loadStageIcons();
        if (!icons.isEmpty()) {
            stage.getIcons().setAll(icons);
        }
    }

    public static void applyToApplicationTaskbar() {
        if (taskbarIconApplied) {
            return;
        }
        taskbarIconApplied = true;

        try {
            if (!Taskbar.isTaskbarSupported()) {
                return;
            }

            Taskbar taskbar = Taskbar.getTaskbar();
            if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                return;
            }

            URL iconUrl = AppIconUtil.class.getResource(PRIMARY_ICON);
            if (iconUrl == null) {
                iconUrl = AppIconUtil.class.getResource(FALLBACK_ICON);
            }
            if (iconUrl == null) {
                logger.warn("Application icon resource was not found");
                return;
            }

            BufferedImage image = ImageIO.read(iconUrl);
            if (image != null) {
                taskbar.setIconImage(image);
            }
        } catch (Exception e) {
            logger.debug("Unable to set native taskbar icon", e);
        }
    }

    private static List<Image> loadStageIcons() {
        if (stageIcons != null) {
            return stageIcons;
        }

        List<Image> icons = new ArrayList<>();
        for (String resource : ICON_RESOURCES) {
            URL url = AppIconUtil.class.getResource(resource);
            if (url == null) {
                logger.warn("Stage icon resource was not found: {}", resource);
                continue;
            }

            Image image = new Image(url.toExternalForm());
            if (image.isError()) {
                logger.warn("Failed to load stage icon: {}", resource, image.getException());
                continue;
            }
            icons.add(image);
        }

        stageIcons = Collections.unmodifiableList(icons);
        return stageIcons;
    }
}
