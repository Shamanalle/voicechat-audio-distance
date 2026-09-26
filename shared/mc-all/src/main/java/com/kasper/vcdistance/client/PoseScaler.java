package com.kasper.vcdistance.client;

import java.lang.reflect.Method;

/**
 * Scales what is drawn next, through the GUI's transform stack. The stack changed type in 1.21.6
 * ({@code PoseStack} before, JOML's {@code Matrix3x2fStack} since), and the 1.20/1.21 jars run with
 * intermediary names, so it is reached by reflection: JOML keeps its names everywhere, and
 * {@code PoseStack} is looked up by its intermediary names first, then by its Mojang names
 * (development, 26.x). When nothing fits the HUD is simply drawn at its normal size.
 */
final class PoseScaler {

    private static volatile Method pose;
    private static volatile Method push;
    private static volatile Method scale;
    private static volatile Method pop;
    private static volatile boolean threeArgs;
    private static volatile boolean unavailable;

    private PoseScaler() {
    }

    /** Pushes a scale on {@code graphics}' stack; {@code false} (nothing pushed) when that is not possible. */
    static boolean push(Object graphics, float factor) {
        if (unavailable || graphics == null) {
            return false;
        }
        try {
            if (pose == null) {
                resolve(graphics);
                if (unavailable) {
                    return false;
                }
            }
            Object stack = pose.invoke(graphics);
            push.invoke(stack);
            if (threeArgs) {
                scale.invoke(stack, factor, factor, 1.0F);
            } else {
                scale.invoke(stack, factor, factor);
            }
            return true;
        } catch (Throwable t) {
            unavailable = true;
            return false;
        }
    }

    static void pop(Object graphics) {
        try {
            pop.invoke(pose.invoke(graphics));
        } catch (Throwable t) {
            unavailable = true;
        }
    }

    private static void resolve(Object graphics) {
        Method p = find(graphics.getClass(), new String[]{"method_51448", "pose"});
        if (p == null) {
            unavailable = true;
            return;
        }
        try {
            p.setAccessible(true);
            Object stack = p.invoke(graphics);
            Class<?> type = stack.getClass();
            Method jomlPush = find(type, new String[]{"pushMatrix"});
            if (jomlPush != null) {
                push = jomlPush;
                scale = type.getMethod("scale", float.class, float.class);
                pop = type.getMethod("popMatrix");
                threeArgs = false;
            } else {
                push = find(type, new String[]{"method_22903", "pushPose"});
                pop = find(type, new String[]{"method_22909", "popPose"});
                scale = findScale3(type);
                threeArgs = true;
            }
            if (push == null || pop == null || scale == null) {
                unavailable = true;
                return;
            }
            pose = p;
        } catch (Throwable t) {
            unavailable = true;
        }
    }

    private static Method find(Class<?> type, String[] names) {
        for (String n : names) {
            try {
                return type.getMethod(n);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Method findScale3(Class<?> type) {
        for (String n : new String[]{"method_22905", "scale"}) {
            try {
                return type.getMethod(n, float.class, float.class, float.class);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
