package com.kasper.vcdistance.client;

import com.kasper.vcdistance.DistanceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Opens and reads the current screen on every 26.x release. 26.3 moved screen handling from
 * {@link Minecraft} ({@code setScreen}, field {@code screen}) to {@link Gui} ({@code setScreen},
 * {@code screen()}); 26.x is unobfuscated, so the names are the same at runtime.
 */
public final class ScreenSwitch {

    private static final Method GUI_SET = method(Gui.class, "setScreen", Screen.class);
    private static final Method GUI_GET = method(Gui.class, "screen");
    private static final Method MINECRAFT_SET = method(Minecraft.class, "setScreen", Screen.class);
    private static final Field MINECRAFT_SCREEN = field(Minecraft.class, "screen");

    private ScreenSwitch() {
    }

    public static void open(Minecraft client, Screen screen) {
        try {
            if (GUI_SET != null && client.gui != null) {
                GUI_SET.invoke(client.gui, screen);
            } else if (MINECRAFT_SET != null) {
                MINECRAFT_SET.invoke(client, screen);
            } else {
                DistanceConfig.LOGGER.warn("Cannot open a screen: no setScreen in this Minecraft version");
            }
        } catch (ReflectiveOperationException e) {
            DistanceConfig.LOGGER.warn("Cannot open a screen: {}", e.toString());
        }
    }

    public static Screen current(Minecraft client) {
        try {
            if (GUI_GET != null && client.gui != null) {
                return (Screen) GUI_GET.invoke(client.gui);
            }
            if (MINECRAFT_SCREEN != null) {
                return (Screen) MINECRAFT_SCREEN.get(client);
            }
        } catch (ReflectiveOperationException e) {
            DistanceConfig.LOGGER.debug("Cannot read the current screen: {}", e.toString());
        }
        return null;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
