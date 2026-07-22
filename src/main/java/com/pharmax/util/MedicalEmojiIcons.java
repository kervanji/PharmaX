package com.pharmax.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Colored medical emoji images used where JavaFX 17 cannot render color emoji fonts. */
public final class MedicalEmojiIcons {
    private static final List<String> ICONS = List.of(
            "💊", "💉", "🩺", "🩹", "🧪", "🧬", "🩸", "🫀", "🫁", "🦷",
            "🧴", "⚕️", "🏥", "🚑", "🌡️", "🌿", "🧫", "🩻"
    );

    private static final Map<String, String> RESOURCES = Map.ofEntries(
            Map.entry("💊", "pill.png"),
            Map.entry("💉", "syringe.png"),
            Map.entry("🩺", "stethoscope.png"),
            Map.entry("🩹", "adhesive_bandage.png"),
            Map.entry("🧪", "test_tube.png"),
            Map.entry("🧬", "dna.png"),
            Map.entry("🩸", "drop_of_blood.png"),
            Map.entry("🫀", "anatomical_heart.png"),
            Map.entry("🫁", "lungs.png"),
            Map.entry("🦷", "tooth.png"),
            Map.entry("🧴", "lotion_bottle.png"),
            Map.entry("⚕", "medical_symbol.png"),
            Map.entry("🏥", "hospital.png"),
            Map.entry("🚑", "ambulance.png"),
            Map.entry("🌡", "thermometer.png"),
            Map.entry("🌿", "herb.png"),
            Map.entry("🧫", "petri_dish.png"),
            Map.entry("🩻", "x_ray.png")
    );

    private static final Map<String, Image> IMAGE_CACHE = new HashMap<>();

    private MedicalEmojiIcons() {}

    public static List<String> supportedIcons() {
        return ICONS;
    }

    public static boolean supports(String icon) {
        return resourceName(icon) != null;
    }

    public static ImageView createView(String icon, double size) {
        String resourceName = resourceName(icon);
        if (resourceName == null) return null;
        Image image = IMAGE_CACHE.computeIfAbsent(resourceName, MedicalEmojiIcons::loadImage);
        if (image == null || image.isError()) return null;
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private static String resourceName(String icon) {
        if (icon == null || icon.isBlank()) return null;
        return RESOURCES.get(icon.trim().replace("\uFE0F", ""));
    }

    private static Image loadImage(String resourceName) {
        URL resource = MedicalEmojiIcons.class.getResource("/images/medical-emoji/" + resourceName);
        return resource == null ? null : new Image(resource.toExternalForm(), false);
    }
}
