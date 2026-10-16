package com.dynamicwinds.integration;

import com.dynamicwinds.core.StormKind;
import net.minecraft.util.StatCollector;

/** Locale-aware player-facing labels; the core physics remains Minecraft-free. */
public final class StormLanguage {
    private StormLanguage() {}

    public static String name(int kind) {
        String key = "storm.dynamicwinds." + slug(kind) + ".name";
        String value = StatCollector.translateToLocal(key);
        return value.equals(key) ? StormKind.name(kind, true) : value;
    }

    public static String category(int kind) {
        String key = "storm.dynamicwinds." + slug(kind) + ".level";
        String value = StatCollector.translateToLocal(key);
        return value.equals(key) ? StormKind.category(kind) : value;
    }

    public static String text(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("storm.dynamicwinds." + key, args);
    }

    private static String slug(int kind) {
        switch (kind) {
            case StormKind.TROPICAL_STORM: return "tropical";
            case StormKind.HURRICANE: return "hurricane";
            case StormKind.MAJOR_HURRICANE: return "major";
            default: return "vortex";
        }
    }
}
