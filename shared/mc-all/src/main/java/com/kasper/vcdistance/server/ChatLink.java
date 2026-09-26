package com.kasper.vcdistance.server;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat text whose links open in the browser when clicked.
 * <p>
 * The click event changed shape in 1.21.5 (a class with an action and a value became a record per
 * action), and one jar serves both sides of that change under obfuscated names, so the event is
 * made by shape: the nested record holding a {@link URI}, or the constructor taking an action and a
 * text with the action whose name is "open_url". When neither works the link stays plain text.
 */
public final class ChatLink {

    private static final Pattern URL = Pattern.compile("https?://\\S+[^\\s.,;:!?)\\]]");
    private static volatile boolean failed;

    private ChatLink() {
    }

    public static Component of(String text) {
        Matcher m = URL.matcher(text);
        if (failed || !m.find()) {
            return Component.literal(text);
        }
        MutableComponent out = Component.literal("");
        int last = 0;
        do {
            out.append(Component.literal(text.substring(last, m.start())));
            String url = m.group();
            ClickEvent click = openUrl(url);
            MutableComponent link = Component.literal(url);
            if (click != null) {
                link = link.withStyle(Style.EMPTY.withClickEvent(click).withUnderlined(true));
            }
            out.append(link);
            last = m.end();
        } while (m.find());
        out.append(Component.literal(text.substring(last)));
        return out;
    }

    private static ClickEvent openUrl(String url) {
        try {
            if (ClickEvent.class.isInterface()) {
                // 1.21.5 and newer: ClickEvent.OpenUrl(URI)
                for (Class<?> nested : ClickEvent.class.getDeclaredClasses()) {
                    RecordComponent[] parts = nested.isRecord() ? nested.getRecordComponents() : null;
                    if (parts != null && parts.length == 1 && parts[0].getType() == URI.class) {
                        Constructor<?> c = nested.getDeclaredConstructor(URI.class);
                        c.setAccessible(true);
                        return (ClickEvent) c.newInstance(URI.create(url));
                    }
                }
            } else {
                // Before 1.21.5: new ClickEvent(ClickEvent.Action.OPEN_URL, url)
                for (Constructor<?> c : ClickEvent.class.getDeclaredConstructors()) {
                    Class<?>[] types = c.getParameterTypes();
                    if (types.length == 2 && types[0].isEnum() && types[1] == String.class) {
                        Object action = actionNamed(types[0], "open_url");
                        if (action != null) {
                            c.setAccessible(true);
                            return (ClickEvent) c.newInstance(action, url);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // falls through to plain text
        }
        failed = true;
        return null;
    }

    /** The enum constant whose serialized name (any no-argument String method) is {@code name}. */
    private static Object actionNamed(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                return constant;
            }
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == String.class) {
                    try {
                        if (name.equals(method.invoke(constant))) {
                            return constant;
                        }
                    } catch (Throwable ignored) {
                        // not this one
                    }
                }
            }
        }
        return null;
    }
}
