package com.kasper.vcdistance.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Creates the "open settings" key mapping on every 1.20 - 1.21.x release.
 * <p>
 * Minecraft 1.21.9 replaced the {@code String} category of {@link KeyMapping} with a
 * {@code KeyMapping.Category} object, and 1.21.11 dropped the four-argument constructor entirely.
 * This jar is compiled against the old constructor; on newer versions it falls back to
 * {@code KeyMapping(String, int, Category)} via reflection and files the key under "Miscellaneous".
 */
public final class KeyMappings {

    private KeyMappings() {
    }

    public static KeyMapping create(String name, String legacyCategory) {
        try {
            return new KeyMapping(name, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, legacyCategory);
        } catch (LinkageError e) {
            return createWithCategoryObject(name);
        }
    }

    private static KeyMapping createWithCategoryObject(String name) {
        try {
            for (Constructor<?> ctor : KeyMapping.class.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 3 && p[0] == String.class && p[1] == int.class && p[2] != String.class) {
                    Object category = findCategory(p[2]);
                    if (category != null) {
                        return (KeyMapping) ctor.newInstance(name, GLFW.GLFW_KEY_UNKNOWN, category);
                    }
                }
            }
            for (Constructor<?> ctor : KeyMapping.class.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 4 && p[0] == String.class && p[1] == InputConstants.Type.class
                        && p[2] == int.class && p[3] != String.class) {
                    Object category = findCategory(p[3]);
                    if (category != null) {
                        return (KeyMapping) ctor.newInstance(name, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }

    private static Object findCategory(Class<?> categoryType) throws IllegalAccessException {
        Object first = null;
        for (Field field : categoryType.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == categoryType) {
                Object value = field.get(null);
                if (value != null && String.valueOf(value).contains("misc")) {
                    return value;
                }
                if (first == null) {
                    first = value;
                }
            }
        }
        return first;
    }
}
