package com.kasper.vcdistance.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends chat text whose links open in the browser when clicked, through Paper's Adventure API.
 * Kept in its own class so that on Spigot, which has no Adventure, only this class fails to load
 * and the caller sends plain text instead.
 */
final class ChatLink {

    private static final Pattern URL = Pattern.compile("https?://\\S+[^\\s.,;:!?)\\]]");

    private ChatLink() {
    }

    static void send(Player player, String text) {
        Matcher m = URL.matcher(text);
        TextComponent.Builder out = Component.text();
        int last = 0;
        while (m.find()) {
            out.append(Component.text(text.substring(last, m.start())));
            out.append(Component.text(m.group())
                    .clickEvent(ClickEvent.openUrl(m.group()))
                    .decorate(TextDecoration.UNDERLINED));
            last = m.end();
        }
        out.append(Component.text(text.substring(last)));
        player.sendMessage(out.build());
    }
}
