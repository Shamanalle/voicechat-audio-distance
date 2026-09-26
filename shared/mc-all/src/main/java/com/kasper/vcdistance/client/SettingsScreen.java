package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.AttenuationModel;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.AudioPhysics;
import com.kasper.vcdistance.Bearing;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.EnvironmentEffects;
import com.kasper.vcdistance.ListenerEnvironment;
import com.kasper.vcdistance.RoomEstimate;
import com.kasper.vcdistance.SoundBlend;
import com.kasper.vcdistance.HudMode;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.NearbyPlayers;
import com.kasper.vcdistance.OcclusionModel;
import com.kasper.vcdistance.Preset;
import com.kasper.vcdistance.ProfileCode;
import com.kasper.vcdistance.SpeakerRegistry;
import com.kasper.vcdistance.VoiceState;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings screen, shared by every Minecraft version.
 * <p>
 * Four tabs: the distance curve with a live graph, wall muffling with an audible preview, per-material
 * absorption, and a live monitor of the players within voice range and the voices you are hearing. Every change is heard
 * immediately; "Done" (or Esc) saves, "Cancel" restores what was there when the screen opened.
 * <p>
 * Version subclasses only forward rendering through a {@link Canvas} and switch screens.
 */
public abstract class SettingsScreen extends Screen {

    private static final String K = "gui.vc-audio-distance.";
    private static final int MAX_WIDTH = 420;
    private static final int ROW = 24;
    private static final int GAP = 4;

    private static Tab lastTab = Tab.DISTANCE;
    /** Monitor as a list (false) or a radar seen from above (true); kept while the game runs. */
    private static boolean radarView;

    /** Walk-away preview: where the voice is at each step, as a share of the range. */
    private static final double[] PREVIEW_STEPS = {0.05, 0.25, 0.45, 0.65, 0.85, 1.0};
    private static final int PREVIEW_STEP_TICKS = 14;

    public enum Tab {
        DISTANCE("tab.distance"),
        WALLS("tab.walls"),
        MATERIALS("tab.materials"),
        EFFECTS("tab.effects"),
        MONITOR("tab.monitor"),
        /** Only for server admins, when the server has the addon. */
        SERVER("tab.server");

        private final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private record Example(String key, AcousticMaterial material, int blocks) {
    }

    private static final Example[] EXAMPLES = {
            // Roughly from the weakest to the strongest with the default weights
            new Example("glass", AcousticMaterial.GLASS, 1),
            new Example("leaves", AcousticMaterial.LEAVES, 3),
            new Example("wood", AcousticMaterial.WOOD, 1),
            new Example("earth", AcousticMaterial.EARTH, 1),
            new Example("stone1", AcousticMaterial.STONE, 1),
            new Example("metal", AcousticMaterial.METAL, 1),
            new Example("wool", AcousticMaterial.WOOL, 1),
            new Example("stone2", AcousticMaterial.STONE, 2),
            new Example("stone3", AcousticMaterial.STONE, 3)
    };

    protected final Screen parent;
    private final DistanceConfig config = AudioDistancePlugin.CONFIG;
    private final DistanceConfig snapshot;
    private Tab tab;

    private int left;
    private int right;
    private int contentTop;
    private int contentBottom;
    private int graphTop;
    private int graphBottom;
    private int panelTop;

    private final List<Button> presetButtons = new ArrayList<>();
    private final List<Preset> presetOrder = new ArrayList<>();
    /** x1, x2, bottom of each preset button, to mark the one that matches the current sound. */
    private final List<int[]> presetBounds = new ArrayList<>();
    private int activeTabX1;
    private int activeTabX2;
    private int tabsBottom;
    private RangeSlider strengthSlider;
    private RangeSlider reverbSlider;
    /** Widgets that change settings; disabled while the server enforces its profile. */
    private final List<AbstractWidget> editWidgets = new ArrayList<>();
    private boolean serverChip;
    /** Current walk-away preview step, or -1 when it is not playing. */
    private int previewStep = -1;
    private int previewTicks;
    private Button listenButton;
    private Button copyButton;
    private Button pasteButton;
    /** Short-lived result shown on the profile code buttons ("copied", "pasted", "not a code"). */
    private String copyFeedback;
    private String pasteFeedback;
    private int codeFeedbackTicks;
    /** Server tab: the zone picked in the list, and the last reply shown. */
    private static String selectedZone;
    private int seenAdminReplies = -1;
    private int serverListTop;
    private int monitorTop;

    protected SettingsScreen(Screen parent) {
        super(Component.translatable(K + "title"));
        this.parent = parent;
        this.config.ensureLoaded();
        this.snapshot = config.copy();
        this.tab = lastTab;
    }

    /** Shows another screen (the API for this differs between versions). */
    protected abstract void openScreen(Screen screen);

    /** Plays the preview voice (a villager's "hmm") at {@code volume}, 0 - 1. */
    protected abstract void playPreview(float volume);

    protected abstract boolean inWorld();

    /** Text on the system clipboard, or "" (the API for this differs between versions). */
    protected abstract String readClipboard();

    protected abstract void writeClipboard(String text);

    // =========================================================================
    // Layout
    // =========================================================================

    @Override
    protected void init() {
        presetButtons.clear();
        presetOrder.clear();
        presetBounds.clear();
        editWidgets.clear();
        strengthSlider = null;
        reverbSlider = null;
        listenButton = null;
        copyButton = null;
        pasteButton = null;
        serverChip = false;

        int w = Math.min(this.width - 16, MAX_WIDTH);
        left = (this.width - w) / 2;
        right = left + w;

        // The Server tab only for admins of a server with the addon
        boolean admin = AudioDistancePlugin.LINK.isAdmin();
        if (tab == Tab.SERVER && !admin) {
            tab = Tab.DISTANCE;
        }
        Tab[] tabs = admin ? Tab.values() : java.util.Arrays.copyOf(Tab.values(), Tab.values().length - 1);
        int tabW = (w - GAP * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = i == tabs.length - 1 ? right - tabW : left + i * (tabW + GAP);
            Button b = Button.builder(tr(t.key), btn -> switchTab(t)).bounds(x, 22, tabW, 20).build();
            b.active = t != tab;
            addRenderableWidget(b);
            if (t == tab) {
                activeTabX1 = x;
                activeTabX2 = x + tabW;
            }
        }
        tabsBottom = 22 + 20;
        contentTop = tabsBottom + 8;

        int footerY = this.height - 26;
        contentBottom = footerY - 6;
        int fw = (w - GAP * 2) / 3;
        edit(Button.builder(tr("reset"), b -> {
            config.applyDefaults();
            rebuild();
        }).bounds(left, footerY, fw, 20).tooltip(tip("reset.tooltip")).build());
        addRenderableWidget(Button.builder(tr("cancel"), b -> cancel())
                .bounds(left + fw + GAP, footerY, fw, 20).tooltip(tip("cancel.tooltip")).build());
        addRenderableWidget(Button.builder(tr("done"), b -> saveAndClose())
                .bounds(right - fw, footerY, fw, 20).build());

        switch (tab) {
            case DISTANCE -> initDistance();
            case WALLS -> initWalls();
            case MATERIALS -> initMaterials();
            case EFFECTS -> initEffects();
            case MONITOR -> initMonitor();
            case SERVER -> initServer();
        }

        initServerChip(w);
        DistanceConfig.Part part = lockedPartOf(tab);
        if (part != null && AudioDistancePlugin.LINK.isLocked(part)) {
            for (AbstractWidget widget : editWidgets) {
                widget.active = false;
            }
        }
    }

    /** The part of the sound settings a tab changes, which the server may lock; {@code null} for the others. */
    private static DistanceConfig.Part lockedPartOf(Tab tab) {
        return switch (tab) {
            case DISTANCE -> DistanceConfig.Part.CURVE;
            case WALLS -> DistanceConfig.Part.WALLS;
            case MATERIALS -> DistanceConfig.Part.MATERIALS;
            case EFFECTS -> DistanceConfig.Part.EFFECTS;
            default -> null;
        };
    }

    /** Top-right corner: what the server offers, when it has the addon. */
    private void initServerChip(int w) {
        LinkProtocol.ServerProfile profile = AudioDistancePlugin.LINK.profile();
        if (profile == null || profile.mode() == com.kasper.vcdistance.ServerSettings.ProfileMode.OFF) {
            return;
        }
        serverChip = true;
        if (AudioDistancePlugin.LINK.isSuggested()) {
            Component label = tr("server.apply");
            int bw = Math.min(this.font.width(label) + 16, w / 2);
            addRenderableWidget(Button.builder(label, b -> {
                config.copyFrom(profile.config());
                rebuild();
            }).bounds(right - bw, 2, bw, 16).tooltip(tip("server.apply.tooltip")).build());
        }
    }

    private <T extends AbstractWidget> T edit(T widget) {
        editWidgets.add(widget);
        addRenderableWidget(widget);
        return widget;
    }

    /** What is heard right now: the server's profile while it is enforced, otherwise the player's settings. */
    private static DistanceConfig shown() {
        return AudioDistancePlugin.config();
    }

    private void initDistance() {
        int w = right - left;
        int colW = (w - GAP) / 2;
        int col2 = right - colW;

        Preset[] presets = Preset.values();
        int pw = (w - GAP * (presets.length - 1)) / presets.length;
        for (int i = 0; i < presets.length; i++) {
            Preset p = presets[i];
            int x = i == presets.length - 1 ? right - pw : left + i * (pw + GAP);
            Button b = Button.builder(Component.translatable(p.getTranslationKey()), btn -> {
                double range = AudioDistancePlugin.getServerMaxDistance();
                p.apply(config, range);
                config.setChosenPreset(p, range);
                rebuild();
            }).bounds(x, contentTop, pw, 20).tooltip(Tooltip.create(Component.translatable(p.getTooltipKey()))).build();
            presetButtons.add(b);
            presetOrder.add(p);
            presetBounds.add(new int[]{x, x + pw, contentTop + 20});
            edit(b);
        }
        refreshPresetButtons();

        // The graph keeps a readable shape instead of filling tall windows; the sliders follow it
        graphTop = contentTop + ROW + 2;
        int room = contentBottom - graphTop - 6 - (ROW * 4 - GAP);
        graphBottom = graphTop + Math.min(room, Math.max(120, w * 2 / 5));
        int rows = graphBottom + 6;

        // Not an edit widget: listening is allowed while the server enforces its profile.
        // It sits in the header strip above the graph panel, with one width for both labels so it
        // does not jump when it toggles.
        int lw = Math.max(this.font.width(tr("listen")), this.font.width(tr("listen.stop"))) + 12;
        listenButton = Button.builder(previewStep >= 0 ? tr("listen.stop") : tr("listen"), b -> togglePreview())
                .bounds(right - lw, graphTop, lw, 14).tooltip(tip("listen.tooltip")).build();
        addRenderableWidget(listenButton);

        edit(Button.builder(modelLabel(), b -> {
            config.setModel(config.getModel().next());
            b.setMessage(modelLabel());
            b.setTooltip(Tooltip.create(Component.translatable(config.getModel().getTooltipKey())));
        }).bounds(left, rows, colW, 20).tooltip(Tooltip.create(Component.translatable(shown().getModel().getTooltipKey()))).build());

        edit(withTip(new RangeSlider(col2, rows, colW, 20,
                DistanceConfig.ROLLOFF_MIN, DistanceConfig.ROLLOFF_MAX, 0.01,
                () -> shown().getAttenuationFactor(), config::setAttenuationFactor,
                v -> tr("falloff", pct(v))), "falloff.tooltip"));

        edit(withTip(new RangeSlider(left, rows + ROW, colW, 20,
                DistanceConfig.REFERENCE_MIN, DistanceConfig.REFERENCE_MAX, 0.01,
                () -> shown().getOpenalReferenceRatio(), config::setOpenalReferenceRatio,
                v -> tr("reference", blocks(v * AudioDistancePlugin.getServerMaxDistance()))), "reference.tooltip"));

        edit(withTip(new RangeSlider(col2, rows + ROW, colW, 20,
                DistanceConfig.MIN_VOLUME_MIN, DistanceConfig.MIN_VOLUME_MAX, 0.01,
                () -> shown().getMinVolumeFraction(), config::setMinVolumeFraction,
                v -> tr("floor", pct(v))), "floor.tooltip"));

        edit(withTip(new RangeSlider(left, rows + ROW * 2, w, 20,
                DistanceConfig.WHISPER_MIN, DistanceConfig.WHISPER_MAX, 0.05,
                () -> shown().getWhisperMultiplier(), config::setWhisperMultiplier,
                v -> tr("whisper", String.format(Locale.ROOT, "×%.2f", v))), "whisper.tooltip"));

        // Share the profile as a line of text. Copying works while a server enforces its profile
        // (it copies the server's), pasting does not.
        int codeY = rows + ROW * 3;
        if (codeY + 20 <= contentBottom) {
            copyButton = Button.builder(tr(copyFeedback != null ? copyFeedback : "code.copy"), b -> copyCode())
                    .bounds(left, codeY, colW, 20).tooltip(tip("code.copy.tooltip")).build();
            addRenderableWidget(copyButton);
            pasteButton = edit(Button.builder(tr(pasteFeedback != null ? pasteFeedback : "code.paste"), b -> pasteCode())
                    .bounds(col2, codeY, colW, 20).tooltip(tip("code.paste.tooltip")).build());
        }
    }

    private void initWalls() {
        int w = right - left;
        int colW = (w - GAP) / 2;

        edit(Button.builder(wallsLabel(), b -> {
            config.setOcclusionEnabled(!config.isOcclusionEnabled());
            b.setMessage(wallsLabel());
            if (strengthSlider != null) {
                strengthSlider.active = config.isOcclusionEnabled();
            }
        }).bounds(left, contentTop, colW, 20).tooltip(tip("walls.toggle.tooltip")).build());

        strengthSlider = new RangeSlider(right - colW, contentTop, colW, 20,
                DistanceConfig.STRENGTH_MIN, DistanceConfig.STRENGTH_MAX, 0.01,
                () -> shown().getOcclusionStrength(), config::setOcclusionStrength,
                v -> tr("strength", pct(v)));
        strengthSlider.active = shown().isOcclusionEnabled();
        edit(withTip(strengthSlider, "strength.tooltip"));

        panelTop = contentTop + ROW + 2;
    }

    private void initMaterials() {
        int w = right - left;
        int top = contentTop + 25;
        AcousticMaterial[] materials = AcousticMaterial.values();
        // Materials fill the grid; the reset button takes the cell after the last one
        int cells = materials.length + 1;
        int cols = 2;
        if (top + ((cells + 1) / 2) * ROW - GAP > contentBottom && (w - GAP * 2) / 3 >= 110) {
            cols = 3;
        }
        int colW = (w - GAP * (cols - 1)) / cols;
        for (int i = 0; i < cells; i++) {
            int col = i % cols;
            int x = col == cols - 1 ? right - colW : left + col * (colW + GAP);
            int y = top + (i / cols) * ROW;
            if (y + 20 > contentBottom) {
                break;
            }
            if (i == materials.length) {
                edit(Button.builder(tr("materials.reset"), b -> {
                    config.resetMaterials();
                    rebuild();
                }).bounds(x, y, colW, 20).build());
                break;
            }
            AcousticMaterial m = materials[i];
            RangeSlider slider = new RangeSlider(x, y, colW, 20, 0.0, AcousticMaterial.MAX_WEIGHT, 0.05,
                    () -> shown().getMaterialWeight(m), v -> config.setMaterialWeight(m, v),
                    v -> tr("material.value", Component.translatable(m.getTranslationKey()), pct(v)));
            slider.setTooltip(Tooltip.create(Component.translatable(m.getTooltipKey())));
            edit(slider);
        }
    }

    private void initEffects() {
        int w = right - left;
        int colW = (w - GAP) / 2;
        edit(Button.builder(onOff("effects.reverb", shown().isReverbEnabled()), b -> {
            config.setReverbEnabled(!config.isReverbEnabled());
            b.setMessage(onOff("effects.reverb", config.isReverbEnabled()));
            if (reverbSlider != null) {
                reverbSlider.active = config.isReverbEnabled();
            }
        }).bounds(left, contentTop, colW, 20).tooltip(tip("effects.reverb.tooltip")).build());
        reverbSlider = new RangeSlider(right - colW, contentTop, colW, 20,
                DistanceConfig.REVERB_MIN, DistanceConfig.REVERB_MAX, 0.01,
                () -> shown().getReverbStrength(), config::setReverbStrength,
                v -> tr("effects.reverb.strength", pct(v)));
        reverbSlider.active = shown().isReverbEnabled();
        edit(withTip(reverbSlider, "effects.reverb.strength.tooltip"));

        edit(Button.builder(onOff("effects.water", shown().isUnderwaterEnabled()), b -> {
            config.setUnderwaterEnabled(!config.isUnderwaterEnabled());
            b.setMessage(onOff("effects.water", config.isUnderwaterEnabled()));
        }).bounds(left, contentTop + ROW, colW, 20).tooltip(tip("effects.water.tooltip")).build());
        edit(Button.builder(onOff("effects.weather", shown().isWeatherEnabled()), b -> {
            config.setWeatherEnabled(!config.isWeatherEnabled());
            b.setMessage(onOff("effects.weather", config.isWeatherEnabled()));
        }).bounds(right - colW, contentTop + ROW, colW, 20).tooltip(tip("effects.weather.tooltip")).build());
        edit(Button.builder(onOff("effects.corners", shown().isDiffractionEnabled()), b -> {
            config.setDiffractionEnabled(!config.isDiffractionEnabled());
            b.setMessage(onOff("effects.corners", config.isDiffractionEnabled()));
        }).bounds(left, contentTop + ROW * 2, colW, 20).tooltip(tip("effects.corners.tooltip")).build());
        panelTop = contentTop + ROW * 3 + 2;
    }

    private static Component onOff(String key, boolean on) {
        return tr(key, tr(on ? "on" : "off"));
    }

    private void initMonitor() {
        int w = right - left;
        int bw = (w - GAP * 2) / 3;
        DistanceConfig prefs = config;
        addRenderableWidget(Button.builder(HudOverlay.shortModeLabel(prefs.getHudMode()), b -> {
            prefs.setHudMode(prefs.getHudMode().next());
            b.setMessage(HudOverlay.shortModeLabel(prefs.getHudMode()));
        }).bounds(left, contentTop, bw, 20).tooltip(tip("hud.mode.tooltip")).build());
        addRenderableWidget(Button.builder(HudOverlay.shortCornerLabel(prefs.getHudCorner()), b -> {
            prefs.setHudCorner(prefs.getHudCorner().next());
            b.setMessage(HudOverlay.shortCornerLabel(prefs.getHudCorner()));
        }).bounds(left + bw + GAP, contentTop, bw, 20).tooltip(tip("hud.corner.tooltip")).build());
        addRenderableWidget(Button.builder(viewLabel(), b -> {
            radarView = !radarView;
            b.setMessage(viewLabel());
        }).bounds(right - bw, contentTop, bw, 20).tooltip(tip("monitor.view.tooltip")).build());

        // HUD look: size, background, compact, colors
        int qw = (w - GAP * 3) / 4;
        int row2 = contentTop + ROW;
        addRenderableWidget(withTip(new RangeSlider(left, row2, qw, 20,
                DistanceConfig.HUD_SCALE_MIN, DistanceConfig.HUD_SCALE_MAX, 0.05,
                prefs::getHudScale, prefs::setHudScale, v -> tr("hud.scale", pct(v))), "hud.scale.tooltip"));
        addRenderableWidget(withTip(new RangeSlider(left + qw + GAP, row2, qw, 20, 0.0, 1.0, 0.05,
                prefs::getHudBackground, prefs::setHudBackground, v -> tr("hud.background", pct(v))), "hud.background.tooltip"));
        addRenderableWidget(Button.builder(onOff("hud.compact", prefs.isHudCompact()), b -> {
            prefs.setHudCompact(!prefs.isHudCompact());
            b.setMessage(onOff("hud.compact", prefs.isHudCompact()));
        }).bounds(left + (qw + GAP) * 2, row2, qw, 20).tooltip(tip("hud.compact.tooltip")).build());
        addRenderableWidget(Button.builder(colorsLabel(), b -> {
            prefs.setColorblind(!prefs.isColorblind());
            b.setMessage(colorsLabel());
        }).bounds(right - qw, row2, qw, 20).tooltip(tip("colors.tooltip")).build());
        monitorTop = contentTop + ROW * 2 + 2;
    }

    private Component colorsLabel() {
        return tr("colors", tr(config.isColorblind() ? "colors.colorblind" : "colors.normal"));
    }

    private static Component viewLabel() {
        return tr("monitor.view", tr(radarView ? "monitor.view.radar" : "monitor.view.list"));
    }

    // =========================================================================
    // Server tab (admins)
    // =========================================================================

    private static final String[] SERVER_MODES = {"off", "suggest", "enforce"};
    private static final String[] SERVER_PRESETS = {"custom", "vanilla", "realistic", "clear", "stealth"};
    private static final String[] SERVER_REQUIRE = {"off", "suggest", "warn", "kick"};
    private static final String[] SERVER_SNEAK = {"1", "0.7", "0.5", "0.3"};
    private static final String[] ZONE_RANGE = {"-", "0.4", "2", "3"};
    private static final String[] ZONE_WALLS = {"-", "0", "0.5", "1"};
    private static final String[] ZONE_ECHO = {"-", "off", "0.5", "0.9"};
    private static final String MEGAPHONE = "minecraft:goat_horn";

    private static java.util.Properties serverState() {
        LinkProtocol.AdminReply reply = AudioDistancePlugin.LINK.adminReply();
        return reply == null ? null : reply.state();
    }

    /** The value after {@code current} in {@code values}, comparing numbers as numbers. */
    private static String next(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (same(values[i], current)) {
                return values[(i + 1) % values.length];
            }
        }
        // A value set elsewhere (0.85 in the file): go on to the next step above it
        try {
            double v = Double.parseDouble(current);
            for (String value : values) {
                if (Double.parseDouble(value) > v) {
                    return value;
                }
            }
        } catch (NumberFormatException | NullPointerException ignored) {
            // not a number list
        }
        return values[0];
    }

    private static boolean same(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        try {
            return Math.abs(Double.parseDouble(a) - Double.parseDouble(b)) < 0.005;
        } catch (NumberFormatException e) {
            return a.equalsIgnoreCase(b);
        }
    }

    private Button serverButton(Component label, String tooltip, int x, int y, int w, String command) {
        Button b = Button.builder(label, btn -> AudioDistancePlugin.LINK.sendAdmin(command)).bounds(x, y, w, 20).build();
        if (tooltip != null) {
            b.setTooltip(tip(tooltip));
        }
        addRenderableWidget(b);
        return b;
    }

    private static Component presetName(String name) {
        return switch (name == null ? "custom" : name) {
            case "vanilla" -> Component.translatable(Preset.VANILLA.getTranslationKey());
            case "realistic" -> Component.translatable(Preset.REALISTIC.getTranslationKey());
            case "clear" -> Component.translatable(Preset.CLEAR.getTranslationKey());
            case "stealth" -> Component.translatable(Preset.ATMOSPHERIC.getTranslationKey());
            default -> tr("server.preset.custom");
        };
    }

    /** What {@code /vcd lock} cycles through on the Server tab. */
    private static final String[] SERVER_LOCKS = {"all", "curve,walls", "curve", "none"};

    private static Component lockedName(String locked) {
        return switch (locked) {
            case "all" -> tr("server.locked.all");
            case "none" -> tr("server.locked.none");
            case "curve" -> tr("server.locked.curve");
            case "curve,walls" -> tr("server.locked.curve_walls");
            default -> Component.literal(locked);
        };
    }

    private static Component yesNo(String bool) {
        boolean on = Boolean.parseBoolean(bool);
        return tr(on ? "on" : "off");
    }

    private void initServer() {
        java.util.Properties st = serverState();
        int w = right - left;
        int third = (w - GAP * 2) / 3;
        int x2 = left + third + GAP;
        int x3 = right - third;
        int y = contentTop;
        if (st == null) {
            serverListTop = y;
            return;
        }
        String mode = st.getProperty("profile_mode", "off");
        String preset = st.getProperty("profile_preset", "custom");
        String walls = st.getProperty("walls_strength", "0");
        serverButton(tr("server.profile", tr("server.mode." + mode)), "server.profile.tooltip", left, y, third,
                "profile " + next(SERVER_MODES, mode));
        serverButton(tr("server.preset", presetName(preset)), "server.preset.tooltip", x2, y, third,
                "preset " + next(SERVER_PRESETS, preset));
        // Walls in 5% steps: − and + either side of the value
        int wallsPct = (int) Math.round(parse(walls) * 100.0);
        int down = Math.max(0, (wallsPct + 4) / 5 * 5 - 5);
        int up = Math.min(100, wallsPct / 5 * 5 + 5);
        serverButton(Component.literal("−"), "server.walls.tooltip", x3, y, 20, down == 0 ? "walls off" : "walls " + down)
                .active = wallsPct > 0;
        serverButton(tr("server.walls", wallsPct == 0 ? tr("off") : Component.literal(wallsPct + "%")), "server.walls.tooltip",
                x3 + 22, y, third - 44, "walls " + up).active = wallsPct < 100;
        serverButton(Component.literal("+"), "server.walls.tooltip", right - 20, y, 20, "walls " + up)
                .active = wallsPct < 100;

        y += ROW;
        boolean serverWalls = "true".equalsIgnoreCase(st.getProperty("server_walls"));
        String require = st.getProperty("require_addon", "off");
        String megaphone = st.getProperty("megaphone_item", "");
        serverButton(tr("server.server_walls", yesNo(String.valueOf(serverWalls))), "server.server_walls.tooltip", left, y, third,
                "serverwalls " + (serverWalls ? "off" : "on"));
        serverButton(tr("server.require", tr("server.require." + require)), "server.require.tooltip", x2, y, third,
                "require " + next(SERVER_REQUIRE, require));
        serverButton(tr("server.megaphone", megaphone.isEmpty() ? tr("off") : Component.translatable("item.minecraft.goat_horn")),
                "server.megaphone.tooltip", x3, y, third, "rule megaphone " + (megaphone.isEmpty() ? MEGAPHONE : "off"));

        y += ROW;
        String sneak = st.getProperty("sneak_range_multiplier", "1");
        String dead = st.getProperty("dead_players_silent", "false");
        String spectators = st.getProperty("spectators_hear_only_spectators", "false");
        serverButton(tr("server.sneak", pct(parse(sneak))), "server.sneak.tooltip", left, y, third,
                "rule sneak " + next(SERVER_SNEAK, sneak));
        serverButton(tr("server.dead", yesNo(dead)), "server.dead.tooltip", x2, y, third,
                "rule dead " + ("true".equalsIgnoreCase(dead) ? "off" : "on"));
        serverButton(tr("server.spectators", yesNo(spectators)), "server.spectators.tooltip", x3, y, third,
                "rule spectators " + ("true".equalsIgnoreCase(spectators) ? "off" : "on"));

        y += ROW;
        String locked = st.getProperty("profile_locked", "all");
        String monitor = st.getProperty("allow_monitor", "true");
        serverButton(tr("server.locked", lockedName(locked)), "server.locked.tooltip", left, y, third,
                "lock " + next(SERVER_LOCKS, locked));
        serverButton(tr("server.monitor", yesNo(monitor)), "server.monitor.tooltip", x2, y, third,
                "monitor " + ("true".equalsIgnoreCase(monitor) ? "off" : "on"));
        String openRange = st.getProperty("open_group_range", "true");
        serverButton(tr("server.group.open_range", yesNo(openRange)), "server.group.open_range.tooltip", x3, y, third,
                "group open_range " + ("true".equalsIgnoreCase(openRange) ? "off" : "on"));

        // Rules inside Simple Voice Chat groups
        y += ROW;
        String groupDead = st.getProperty("group_dead_silent", "false");
        String groupSpectators = st.getProperty("group_spectators_apart", "false");
        String groupZones = st.getProperty("group_isolated_zones", "false");
        serverButton(tr("server.group.dead", yesNo(groupDead)), "server.group.dead.tooltip", left, y, third,
                "group dead " + ("true".equalsIgnoreCase(groupDead) ? "off" : "on"));
        serverButton(tr("server.group.spectators", yesNo(groupSpectators)), "server.group.spectators.tooltip", x2, y, third,
                "group spectators " + ("true".equalsIgnoreCase(groupSpectators) ? "off" : "on"));
        serverButton(tr("server.group.zones", yesNo(groupZones)), "server.group.zones.tooltip", x3, y, third,
                "group zones " + ("true".equalsIgnoreCase(groupZones) ? "off" : "on"));

        // Zones: a list to pick from, the picked zone's settings under it
        y += ROW + 4;
        java.util.List<String[]> zones = new java.util.ArrayList<>();
        for (int i = 0; st.getProperty("zone." + i) != null; i++) {
            zones.add(st.getProperty("zone." + i).split("\\|", -1));
        }
        int used = zones.size() + 1;
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String[] z : zones) {
            names.add(z[1]);
        }
        while (names.contains("zone-" + used)) {
            used++;
        }
        int createW = this.font.width(tr("server.zone.create")) + 16;
        serverButton(tr("server.zone.create"), "server.zone.create.tooltip", right - createW, y, createW,
                "zone create zone-" + used + " 8");
        y += ROW;
        serverListTop = y;

        String[] picked = null;
        for (String[] z : zones) {
            if (z.length >= 11 && z[1].equals(selectedZone)) {
                picked = z;
            }
        }
        int controlsY = contentBottom - 20 - 12;
        int listBottom = picked != null ? controlsY - 4 : contentBottom - 12;
        for (String[] z : zones) {
            if (z.length < 11 || y + 18 > listBottom) {
                break;
            }
            String name = z[1];
            Button b = Button.builder(zoneLabel(z), btn -> {
                selectedZone = name;
                rebuild();
            }).bounds(left, y, w, 18).build();
            b.active = !name.equals(selectedZone);
            addRenderableWidget(b);
            y += 20;
        }
        if (picked != null) {
            String name = picked[1];
            int fifth = (w - GAP * 4) / 5;
            int x = left;
            serverButton(tr("server.zone.range", "-".equals(picked[6]) ? tr("server.default") : Component.literal("×" + picked[6])),
                    "server.zone.range.tooltip", x, controlsY, fifth,
                    "zone set " + name + " range_multiplier " + zoneValue(next(ZONE_RANGE, picked[6])));
            x += fifth + GAP;
            serverButton(tr("server.zone.walls", "-".equals(picked[7]) ? tr("server.default") : Component.literal(pct(parse(picked[7])))),
                    "server.zone.walls.tooltip", x, controlsY, fifth,
                    "zone set " + name + " walls " + zoneValue(next(ZONE_WALLS, picked[7])));
            x += fifth + GAP;
            serverButton(tr("server.zone.echo", "-".equals(picked[8]) ? tr("server.default")
                            : "off".equals(picked[8]) ? tr("off") : Component.literal(pct(parse(picked[8])))),
                    "server.zone.echo.tooltip", x, controlsY, fifth,
                    "zone set " + name + " echo " + zoneValue(next(ZONE_ECHO, picked[8])));
            x += fifth + GAP;
            boolean isolated = "true".equalsIgnoreCase(picked[9]);
            serverButton(tr("server.zone.isolated", yesNo(picked[9])), "server.zone.isolated.tooltip", x, controlsY, fifth,
                    "zone set " + name + " isolated " + (isolated ? "off" : "on"));
            serverButton(tr("server.zone.delete"), null, right - fifth, controlsY, fifth, "zone delete " + name);
        }
    }

    private static String zoneValue(String v) {
        return "-".equals(v) ? "default" : v;
    }

    private static double parse(String v) {
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException | NullPointerException e) {
            return 0.0;
        }
    }

    /** "Box stage · range ×2 · isolated" for the zone list. */
    private static Component zoneLabel(String[] z) {
        java.util.List<Component> parts = new java.util.ArrayList<>();
        if (!"-".equals(z[6])) {
            parts.add(tr("server.zone.range", Component.literal("×" + z[6])));
        }
        if (!"-".equals(z[4])) {
            parts.add(tr("server.zone.voice", z[4]));
        }
        if (!"-".equals(z[7])) {
            parts.add(tr("server.zone.walls", Component.literal(pct(parse(z[7])))));
        }
        if (!"-".equals(z[8])) {
            parts.add(tr("server.zone.echo", "off".equals(z[8]) ? tr("off") : Component.literal(pct(parse(z[8])))));
        }
        if ("true".equalsIgnoreCase(z[9])) {
            parts.add(tr("server.zone.isolated_short"));
        }
        if (!"-".equals(z[3])) {
            parts.add(presetName(z[3]));
        }
        Component label = Component.empty().append(tr("server.kind." + z[0])).append(Component.literal(" " + z[1]));
        for (Component p : parts) {
            label = Component.empty().append(label).append(Component.literal(" · ")).append(p);
        }
        return label;
    }

    private void paintServer(Canvas c) {
        LinkProtocol.AdminReply reply = AudioDistancePlugin.LINK.adminReply();
        if (reply == null) {
            c.centered(tr("server.loading"), (left + right) / 2, (contentTop + contentBottom) / 2 - 4, Palette.TEXT_DIM);
            return;
        }
        java.util.Properties st = reply.state();
        c.text(tr("server.zones"), left, serverListTop - ROW + 6, Palette.TEXT_DIM);
        if (st.getProperty("zone.0") == null) {
            c.text(fit(c, tr("server.zones.none"), right - left), left, serverListTop + 4, Palette.TEXT_MUTED);
        }
        // The server's answer to the last change ("Saved ..."), or a hint
        java.util.List<String> lines = reply.lines();
        Component status = lines.isEmpty() || lines.get(0).startsWith("Voice Physics")
                ? tr("server.hint") : Component.literal(lines.get(lines.size() - 1));
        c.text(fit(c, status, right - left), left, contentBottom - 9, Palette.TEXT_MUTED);
    }

    // =========================================================================
    // Profile code
    // =========================================================================

    private void copyCode() {
        try {
            writeClipboard(ProfileCode.encode(shown()));
            showCodeFeedback("code.copied", null);
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Could not copy the profile code: {}", t.toString());
            showCodeFeedback("code.failed", null);
        }
    }

    private void pasteCode() {
        String text;
        try {
            text = readClipboard();
        } catch (Throwable t) {
            text = "";
        }
        if (ProfileCode.decode(text, config)) {
            config.setChosenPreset(null, 0.0);
            showCodeFeedback(null, "code.pasted");
            rebuild();
        } else {
            showCodeFeedback(null, "code.invalid");
        }
    }

    private void showCodeFeedback(String copy, String paste) {
        copyFeedback = copy;
        pasteFeedback = paste;
        codeFeedbackTicks = 40;
        if (copyButton != null) {
            copyButton.setMessage(tr(copy != null ? copy : "code.copy"));
        }
        if (pasteButton != null) {
            pasteButton.setMessage(tr(paste != null ? paste : "code.paste"));
        }
    }

    // =========================================================================
    // Walk-away preview
    // =========================================================================

    private void togglePreview() {
        if (previewStep >= 0) {
            stopPreview();
            return;
        }
        previewStep = 0;
        previewTicks = 0;
        playPreviewStep();
        if (listenButton != null) {
            listenButton.setMessage(tr("listen.stop"));
        }
    }

    private void stopPreview() {
        previewStep = -1;
        if (listenButton != null) {
            listenButton.setMessage(tr("listen"));
        }
    }

    private void playPreviewStep() {
        DistanceConfig shown = shown();
        double gain = AudioPhysics.calculateGain(PREVIEW_STEPS[previewStep], shown.getModel(),
                shown.getAttenuationFactor(), shown.getMinVolumeFraction(), shown.getOpenalReferenceRatio());
        if (gain > 0.005) {
            try {
                playPreview((float) gain);
            } catch (Throwable t) {
                DistanceConfig.LOGGER.debug("Preview sound failed: {}", t.toString());
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (tab == Tab.SERVER && AudioDistancePlugin.LINK.adminReplyCount() != seenAdminReplies) {
            seenAdminReplies = AudioDistancePlugin.LINK.adminReplyCount();
            rebuild();
        }
        if (codeFeedbackTicks > 0 && --codeFeedbackTicks == 0) {
            showCodeFeedback(null, null);
            codeFeedbackTicks = 0;
        }
        if (previewStep < 0) {
            return;
        }
        if (++previewTicks >= PREVIEW_STEP_TICKS) {
            previewTicks = 0;
            previewStep++;
            if (previewStep >= PREVIEW_STEPS.length) {
                stopPreview();
            } else {
                playPreviewStep();
            }
        }
    }

    private void switchTab(Tab t) {
        previewStep = -1;
        tab = t;
        lastTab = t;
        if (t == Tab.SERVER) {
            // Fresh settings from the server; the tab fills in when the reply arrives
            AudioDistancePlugin.LINK.sendAdmin("status");
        }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void refreshPresetButtons() {
        boolean enforced = AudioDistancePlugin.LINK.isLocked(DistanceConfig.Part.CURVE);
        for (int i = 0; i < presetButtons.size(); i++) {
            presetButtons.get(i).active = !enforced && !presetOrder.get(i).matches(config, AudioDistancePlugin.getServerMaxDistance());
        }
    }

    // =========================================================================
    // Closing
    // =========================================================================

    private void cancel() {
        config.copyFrom(snapshot);
        config.copyInterfaceFrom(snapshot);
        openScreen(parent);
    }

    private void saveAndClose() {
        config.save();
        openScreen(parent);
    }

    @Override
    public void onClose() {
        // Esc keeps the changes, like vanilla option screens
        saveAndClose();
    }

    // =========================================================================
    // Painting
    // =========================================================================

    /** Draws everything that is not a widget. Called by the version subclass after the widgets. */
    protected void paint(Canvas c, int mouseX, int mouseY) {
        Palette.useColorblind(config.isColorblind());
        refreshPresetButtons();
        if (serverChip) {
            c.text(this.title, left, 8, Palette.TEXT);
            if (AudioDistancePlugin.LINK.isEnforced()) {
                DistanceConfig.Part part = lockedPartOf(tab);
                boolean lockedHere = part == null || AudioDistancePlugin.LINK.isLocked(part);
                c.right(tr(lockedHere ? "server.enforced" : "server.enforced.free"), right, 8,
                        lockedHere ? Palette.WARN : Palette.TEXT_MUTED);
            }
        } else {
            c.centered(this.title, this.width / 2, 8, Palette.TEXT);
        }
        c.fill(activeTabX1 + 2, tabsBottom + 1, activeTabX2 - 2, tabsBottom + 3, Palette.ACCENT);
        for (int i = 0; i < presetBounds.size(); i++) {
            if (presetOrder.get(i).matches(shown(), AudioDistancePlugin.getServerMaxDistance())) {
                int[] b = presetBounds.get(i);
                c.fill(b[0] + 2, b[2] + 1, b[1] - 2, b[2] + 3, Palette.ACCENT);
            }
        }
        switch (tab) {
            case DISTANCE -> paintDistance(c, mouseX, mouseY);
            case WALLS -> paintWalls(c);
            case MATERIALS -> {
                c.text(fit(c, tr("materials.hint"), right - left), left, contentTop, Palette.TEXT_DIM);
                c.text(fit(c, tr("materials.hint_other"), right - left), left, contentTop + 11, Palette.TEXT_MUTED);
            }
            case EFFECTS -> paintEffects(c);
            case MONITOR -> paintMonitor(c, mouseX, mouseY);
            case SERVER -> paintServer(c);
        }
    }

    // ---- Distance -----------------------------------------------------------

    private void paintDistance(Canvas c, int mouseX, int mouseY) {
        int x1 = left;
        int x2 = right;
        // Header strip (summary, legend, listen button) above the panel
        int headerY = graphTop + 3;
        int y1 = graphTop + 17;
        int y2 = graphBottom;
        if (y2 - y1 < 40) {
            return;
        }

        double maxDist = AudioDistancePlugin.getServerMaxDistance();
        DistanceConfig shown = shown();
        AttenuationModel model = shown.getModel();
        double rolloff = shown.getAttenuationFactor();
        double floor = shown.getMinVolumeFraction();
        double ref = shown.getOpenalReferenceRatio();

        // Header: plain-language summary + legend. Every curve ends at the edge volume, so the
        // summary names the loudness halfway through the fade, where the curves differ.
        double middle = ref + (1.0 - ref) / 2.0;
        Component summary = rolloff <= 0.001
                ? tr("summary.flat", blocks(maxDist))
                : tr("summary.curve", blocks(ref * maxDist), pct(AudioPhysics.calculateGain(middle, model, rolloff, floor, ref)), blocks(middle * maxDist));
        Component voice = tr("legend.voice");
        Component whisper = tr("legend.whisper");
        int legendW = c.width(voice) + c.width(whisper) + 36;
        int headRight = listenButton != null ? listenButton.getX() - 8 : x2;
        boolean legend = c.width(summary) + legendW + 14 <= headRight - x1;
        c.text(fit(c, summary, (legend ? headRight - legendW : headRight) - x1 - 2), x1 + 1, headerY, Palette.TEXT_DIM);
        if (legend) {
            int lx = headRight - c.width(whisper);
            c.text(whisper, lx, headerY, Palette.TEXT_DIM);
            c.dashedHLine(lx - 12, lx - 3, headerY + 4, 2, Palette.WHISPER);
            lx -= 16 + c.width(voice);
            c.text(voice, lx, headerY, Palette.TEXT_DIM);
            c.fill(lx - 12, headerY + 3, lx - 3, headerY + 5, Palette.ACCENT_LINE);
        }

        c.frame(x1, y1, x2, y2, Palette.PANEL, Palette.PANEL_BORDER);
        // Volume scale on the left, distance scale along the bottom
        int scaleW = c.width(Component.literal("100%")) + 4;
        int px1 = x1 + 4 + scaleW;
        int px2 = x2 - 8;
        int py1 = y1 + 8;
        int py2 = y2 - 13;
        int pw = px2 - px1;
        int ph = py2 - py1;
        if (pw < 40 || ph < 14) {
            return;
        }

        for (int i = 1; i <= 4; i++) {
            int gy = py2 - ph * i / 4;
            if (i < 4) {
                c.hLine(px1, px2, gy, Palette.GRID);
                c.vLine(px1 + pw * i / 4, py1, py2, Palette.GRID);
            }
            if (i % 2 == 0 || ph >= 60) {
                c.right(Component.literal((25 * i) + "%"), px1 - 3, gy - 3, Palette.TEXT_MUTED);
            }
        }
        // Full-volume zone: shaded, with its edge marked
        int refX = px1 + (int) Math.round(ref * pw);
        c.fill(px1, py1, refX, py2, Palette.ACCENT_ZONE);
        if (ref > 0.0 && refX < px2) {
            c.dashedVLine(refX, py1, py2, 2, Palette.withAlpha(Palette.ACCENT, 0x70));
        }
        c.hLine(px1, px2 + 1, py2, Palette.PANEL_BORDER);

        // Voice curve: filled area + line
        int prevY = -1;
        for (int i = 0; i <= pw; i++) {
            double g = AudioPhysics.calculateGain((double) i / pw, model, rolloff, floor, ref);
            int y = py2 - (int) Math.round(g * ph);
            int x = px1 + i;
            if (i < pw) {
                c.fill(x, y, x + 1, py2, Palette.ACCENT_AREA);
            }
            int top = prevY < 0 ? y : Math.min(prevY, y);
            int bottom = prevY < 0 ? y : Math.max(prevY, y);
            c.fill(x, top, x + 1, bottom + 1, Palette.ACCENT_LINE);
            prevY = y;
        }

        // Whisper curve over the whisper range, dashed along its length so steep parts stay dashed too
        double whisperRolloff = AudioDistancePlugin.effectiveRolloff(true);
        int whisperEnd = Math.max(1, (int) Math.round(AudioDistancePlugin.LINK.whisperShare() * pw));
        prevY = -1;
        int run = 0;
        for (int i = 0; i <= whisperEnd; i++) {
            double g = AudioPhysics.calculateGain((double) i / whisperEnd, model, whisperRolloff, floor, ref);
            int y = py2 - (int) Math.round(g * ph);
            int x = px1 + i;
            int from = prevY < 0 ? y : prevY;
            int dir = y >= from ? 1 : -1;
            for (int yy = from; ; yy += dir) {
                if ((run++ / 4) % 2 == 0) {
                    c.fill(x, yy, x + 1, yy + 1, Palette.WHISPER);
                }
                if (yy == y) {
                    break;
                }
            }
            prevY = y;
        }
        // Where the whisper range ends, on the distance scale
        c.fill(px1 + whisperEnd, py2 + 1, px1 + whisperEnd + 1, py2 + 4, Palette.WHISPER);

        // Walk-away preview: where the voice is now
        if (previewStep >= 0) {
            double f = PREVIEW_STEPS[previewStep];
            double g = AudioPhysics.calculateGain(f, model, rolloff, floor, ref);
            int sx = px1 + (int) Math.round(f * pw);
            int sy = py2 - (int) Math.round(g * ph);
            c.vLine(sx, py1, py2, Palette.withAlpha(Palette.ACCENT, 0xA0));
            c.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFF000000);
            c.fill(sx - 1, sy - 1, sx + 2, sy + 2, Palette.ACCENT_LINE);
            badge(c, tr("listen.at", blocks(f * maxDist), pct(g)), sx, sy - 17 >= y1 + 2 ? sy - 17 : sy + 5);
        }

        if (floor > 0.0) {
            int fy = py2 - (int) Math.round(floor * ph);
            c.dashedHLine(px1, px2, fy, 3, Palette.withAlpha(Palette.FLOOR, 0xB0));
            Component label = tr("floor_label", pct(floor));
            c.text(label, px2 - c.width(label) - 2, Math.max(py1, fy - 10), Palette.FLOOR);
        }

        // Axis in blocks
        for (int i = 0; i <= 4; i++) {
            int x = px1 + pw * i / 4;
            int ly = py2 + 3;
            if (i == 0) {
                c.text(Component.literal("0"), x, ly, Palette.TEXT_MUTED);
            } else if (i == 4) {
                c.right(tr("blocks", blocks(maxDist)), x + 1, ly, Palette.TEXT_MUTED);
            } else {
                c.centered(Component.literal(blocks(maxDist * i / 4.0)), x, ly, Palette.TEXT_MUTED);
            }
        }

        // People you hear right now, as dots on the curve
        SpeakerRegistry.Speaker hovered = null;
        int hoverX = 0;
        int hoverY = 0;
        List<SpeakerRegistry.Speaker> dots = AudioDistancePlugin.LINK.isMonitorAllowed()
                ? AudioDistancePlugin.SPEAKERS.active(System.nanoTime()) : List.of();
        for (SpeakerRegistry.Speaker s : dots) {
            if (s.getDistance() < 0.0) {
                continue;
            }
            double f = Math.min(1.0, s.getDistance() / maxDist);
            double g = AudioDistancePlugin.curveGain(s.getDistance(), s.getMaxDistance(), s.isWhispering());
            int sx = px1 + (int) Math.round(f * pw);
            int sy = py2 - (int) Math.round(g * ph);
            int color = s.isWhispering() ? Palette.WHISPER
                    : (s.getFilter().getDisplayLossDb() > 1.0F ? Palette.MUFFLED : Palette.TEXT);
            c.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFF000000);
            c.fill(sx - 1, sy - 1, sx + 2, sy + 2, color);
            if (Math.abs(mouseX - sx) <= 4 && Math.abs(mouseY - sy) <= 4) {
                hovered = s;
                hoverX = sx;
                hoverY = sy;
            }
        }

        if (hovered != null) {
            double g = AudioDistancePlugin.curveGain(hovered.getDistance(), hovered.getMaxDistance(), hovered.isWhispering());
            badge(c, tr("inspect.speaker", speakerName(hovered), blocks(hovered.getDistance()), pct(g)),
                    hoverX, hoverY - 17 >= y1 + 2 ? hoverY - 17 : hoverY + 5);
        } else if (mouseX >= px1 && mouseX <= px2 && mouseY >= py1 && mouseY <= py2) {
            double f = (double) (mouseX - px1) / pw;
            double g = AudioPhysics.calculateGain(f, model, rolloff, floor, ref);
            int gy = py2 - (int) Math.round(g * ph);
            c.vLine(mouseX, py1, py2, 0x66FFFFFF);
            c.fill(mouseX - 1, gy - 1, mouseX + 2, gy + 2, 0xFFFFFFFF);
            int badgeY = gy - 17 >= y1 + 2 ? gy - 17 : Math.min(py2 - 12, gy + 5);
            badge(c, tr("inspect", blocks(f * maxDist), pct(g), db(g)), mouseX, badgeY);
        }
    }

    // ---- Walls --------------------------------------------------------------

    private void paintWalls(Canvas c) {
        AudioDistancePlugin.OcclusionStatus status = AudioDistancePlugin.occlusionStatus();
        int y = panelTop;
        if (status == AudioDistancePlugin.OcclusionStatus.SOUND_PHYSICS || status == AudioDistancePlugin.OcclusionStatus.UNAVAILABLE) {
            String key = status == AudioDistancePlugin.OcclusionStatus.SOUND_PHYSICS ? "status.sound_physics" : "status.unavailable";
            c.frame(left, y, right, y + 26, 0x30F6C453, Palette.withAlpha(Palette.WARN, 0x90));
            c.text(fit(c, tr(key), right - left - 12), left + 6, y + 4, Palette.WARN);
            c.text(fit(c, tr(key + ".detail"), right - left - 12), left + 6, y + 14, Palette.TEXT_DIM);
            y += 30;
        }
        if (contentBottom - y < 40) {
            return;
        }

        DistanceConfig shown = shown();
        boolean on = shown.isOcclusionEnabled();
        // The panel is as tall as its rows, not the whole window
        int panelBottom = Math.min(contentBottom, y + 20 + EXAMPLES.length * 14 + 18);
        c.frame(left, y, right, panelBottom, Palette.PANEL, Palette.PANEL_BORDER);
        Component header = on ? tr("walls.preview") : tr("walls.preview_off");
        c.text(fit(c, header, right - left - 12), left + 6, y + 5, on ? Palette.TEXT_DIM : Palette.TEXT_MUTED);

        int labelW = 0;
        for (Example e : EXAMPLES) {
            labelW = Math.max(labelW, c.width(tr("walls.example." + e.key)));
        }
        labelW = Math.min(labelW, (right - left) * 2 / 5);
        int valueW = c.width(tr("walls.value", "−00.0", tr("khz", "00.0"))) + 4;
        int barX1 = left + 8 + labelW + 8;
        int barX2 = right - 8 - valueW;
        if (barX2 - barX1 < 30) {
            barX2 = right - 8;
            valueW = 0;
        }

        double strength = shown.getOcclusionStrength();
        int rowY = y + 20;
        for (Example e : EXAMPLES) {
            if (rowY + 10 > panelBottom - 4) {
                break;
            }
            double thickness = e.blocks * shown.getMaterialWeight(e.material);
            double muffle = OcclusionModel.muffle(thickness, strength);
            double loss = OcclusionModel.lossDb(thickness, strength);
            double gain = OcclusionModel.dbToGain(-loss);
            int alpha = on ? 0xFF : 0x70;

            c.text(fit(c, tr("walls.example." + e.key), labelW), left + 8, rowY, Palette.withAlpha(Palette.TEXT, alpha));
            c.fill(barX1, rowY + 1, barX2, rowY + 8, 0x22FFFFFF);
            int filled = barX1 + (int) Math.round(gain * (barX2 - barX1));
            c.fill(barX1, rowY + 1, filled, rowY + 8, Palette.withAlpha(Palette.mix(Palette.ACCENT, Palette.MUFFLED, muffle), alpha));
            if (valueW > 0) {
                c.right(tr("walls.value", String.format(Locale.ROOT, "−%.1f", loss), frequency(OcclusionModel.cutoffHz(muffle))),
                        right - 8, rowY, Palette.withAlpha(Palette.TEXT_DIM, alpha));
            }
            rowY += 14;
        }
        if (rowY + 18 <= panelBottom) {
            c.text(fit(c, tr("walls.hint"), right - left - 16), left + 8, panelBottom - 14, Palette.TEXT_MUTED);
        }
    }

    // ---- Effects ------------------------------------------------------------

    /** What the surroundings do to voices right now. */
    private void paintEffects(Canvas c) {
        int y = panelTop;
        AudioDistancePlugin.OcclusionStatus status = AudioDistancePlugin.occlusionStatus();
        if (status == AudioDistancePlugin.OcclusionStatus.SOUND_PHYSICS || status == AudioDistancePlugin.OcclusionStatus.UNAVAILABLE) {
            String key = status == AudioDistancePlugin.OcclusionStatus.SOUND_PHYSICS ? "effects.sound_physics" : "status.unavailable";
            c.frame(left, y, right, y + 26, 0x30F6C453, Palette.withAlpha(Palette.WARN, 0x90));
            c.text(fit(c, tr(key), right - left - 12), left + 6, y + 4, Palette.WARN);
            c.text(fit(c, tr(key + ".detail"), right - left - 12), left + 6, y + 14, Palette.TEXT_DIM);
            y += 30;
        }
        if (contentBottom - y < 40) {
            return;
        }
        c.frame(left, y, right, contentBottom, Palette.PANEL, Palette.PANEL_BORDER);
        int x = left + 8;
        int w = right - left - 16;
        if (!inWorld()) {
            c.centered(tr("effects.no_world"), (left + right) / 2, (y + contentBottom) / 2 - 4, Palette.TEXT_DIM);
            return;
        }
        DistanceConfig shown = shown();
        ListenerEnvironment env = AudioDistancePlugin.ENVIRONMENT;
        c.text(tr("effects.now"), x, y + 5, Palette.TEXT_DIM);
        int rowY = y + 20;

        // Echo: what kind of place you are in and what it does to voices
        RoomEstimate room = env.room();
        boolean reverbOn = shown.isReverbEnabled() && status != AudioDistancePlugin.OcclusionStatus.SOUND_PHYSICS;
        double level = (room.wet() > 0.0 ? room.wet() : room.echoes().loudest()) * shown.getReverbStrength();
        c.text(fit(c, Component.translatable(room.kind().getTranslationKey()), w), x, rowY,
                reverbOn && room.isAudible() ? Palette.TEXT : Palette.TEXT_MUTED);
        rowY += 12;
        Component detail = null;
        if (!room.echoes().isEmpty()) {
            detail = tr("effects.room.repeat", String.format(Locale.ROOT, "%.2f", room.echoes().delays()[0]));
        } else if (room.wet() > 0.0) {
            detail = tr("effects.room.detail", String.format(Locale.ROOT, "%.1f", room.decaySeconds()), blocks(room.meanFree()));
        }
        if (detail != null && contentBottom - rowY > 72) {
            c.text(fit(c, detail, w), x, rowY, Palette.TEXT_DIM);
            rowY += 12;
        }
        int barRight = right - 8 - c.width(Component.literal("100%")) - 4;
        c.fill(x, rowY + 1, barRight, rowY + 7, 0x22FFFFFF);
        c.fill(x, rowY + 1, x + (int) Math.round(Math.min(1.0, reverbOn ? level : 0.0) * (barRight - x)), rowY + 7,
                Palette.withAlpha(Palette.ACCENT, reverbOn ? 0xFF : 0x70));
        c.right(Component.literal(pct(reverbOn ? level : 0.0)), right - 8, rowY, Palette.TEXT_DIM);
        rowY += 16;

        // Water
        boolean waterOn = shown.isUnderwaterEnabled() && status != AudioDistancePlugin.OcclusionStatus.SOUND_PHYSICS;
        c.text(fit(c, tr(env.isUnderWater() ? "effects.water.under" : "effects.water.dry"), w), x, rowY,
                env.isUnderWater() && waterOn ? Palette.ACCENT_LINE : Palette.TEXT_MUTED);
        rowY += 14;

        // Weather
        EnvironmentEffects.Weather weather = env.weather();
        String weatherKey = "effects.weather." + weather.name().toLowerCase(Locale.ROOT);
        c.text(fit(c, tr(weatherKey), w), x, rowY,
                weather != EnvironmentEffects.Weather.CLEAR && shown.isWeatherEnabled() ? Palette.WARN : Palette.TEXT_MUTED);
        rowY += 14;

        // Corners: voices that come round a wall right now
        int round = 0;
        for (SpeakerRegistry.Speaker sp : AudioDistancePlugin.SPEAKERS.active(System.nanoTime())) {
            if (sp.isHeardRound()) {
                round++;
            }
        }
        boolean cornersOn = shown.isDiffractionEnabled() && status == AudioDistancePlugin.OcclusionStatus.ACTIVE;
        if (rowY + 10 <= contentBottom - 4) {
            c.text(fit(c, round > 0 ? tr("effects.corners.now", round) : tr("effects.corners.none"), w), x, rowY,
                    round > 0 && cornersOn ? Palette.ACCENT_LINE : Palette.TEXT_MUTED);
            rowY += 14;
        }

        if (rowY + 22 <= contentBottom - 4) {
            c.text(fit(c, tr("effects.hint"), w), x, contentBottom - 14, Palette.TEXT_MUTED);
        }
    }

    // ---- Monitor ------------------------------------------------------------

    private void paintMonitor(Canvas c, int mouseX, int mouseY) {
        int top = monitorTop;
        c.frame(left, top, right, contentBottom, Palette.PANEL, Palette.PANEL_BORDER);
        int midX = (left + right) / 2;
        if (!inWorld()) {
            c.centered(tr("monitor.no_world"), midX, (top + contentBottom) / 2 - 4, Palette.TEXT_DIM);
            return;
        }

        AudioDistancePlugin.OcclusionStatus status = AudioDistancePlugin.occlusionStatus();
        boolean wallsActive = status == AudioDistancePlugin.OcclusionStatus.ACTIVE;
        double maxDist = AudioDistancePlugin.getServerMaxDistance();
        int y = top + 6;
        c.text(tr("monitor.walls", tr("monitor.status." + status.name().toLowerCase(Locale.ROOT))), left + 6, y,
                wallsActive ? Palette.GOOD : Palette.TEXT_MUTED);
        c.right(tr("monitor.range", blocks(maxDist)), right - 6, y, Palette.TEXT_MUTED);
        LinkProtocol.ServerProfile serverProfile = AudioDistancePlugin.LINK.profile();
        Component serverLine = serverProfile == null
                ? tr("monitor.server.none")
                : tr("monitor.server.addon", tr("monitor.server.mode." + serverProfile.mode().getId()));
        y += 11;
        // The addon's own cost per tick; it spaces its work out above PerfMeter.BUSY_MS
        double ms = AudioDistancePlugin.CLIENT_PERF.averageMs();
        Component perf = tr("monitor.perf", String.format(Locale.ROOT, "%.2f", ms));
        c.right(perf, right - 6, y, AudioDistancePlugin.CLIENT_PERF.isBusy() ? Palette.WARN : Palette.TEXT_MUTED);
        c.text(fit(c, serverLine, right - left - 18 - c.width(perf)), left + 6, y, Palette.TEXT_MUTED);

        if (!AudioDistancePlugin.LINK.isMonitorAllowed()) {
            c.centered(tr("monitor.off_by_server"), midX, (y + contentBottom) / 2, Palette.TEXT_DIM);
            return;
        }
        long now = System.nanoTime();
        List<NearbyPlayers.Row> rows = NearbyPlayers.rows(AudioDistancePlugin.SPEAKERS.active(now),
                AudioDistancePlugin.NEARBY.players(), id -> AudioDistancePlugin.voiceState(id, now));
        // Without any source of voice chat states only who is talking is known
        int footer = AudioDistancePlugin.hasVoiceStates(now) ? 0 : 12;
        if (footer > 0) {
            c.text(fit(c, tr("monitor.states_unknown"), right - left - 12), left + 6, contentBottom - 13, Palette.TEXT_MUTED);
        }
        if (radarView) {
            paintRadar(c, rows, y + 14, contentBottom - 4 - footer, maxDist, wallsActive, mouseX, mouseY);
            return;
        }
        if (rows.isEmpty()) {
            int cy = (y + contentBottom) / 2 - 4;
            c.centered(tr("monitor.empty"), midX, cy, Palette.TEXT_DIM);
            c.centered(fit(c, tr("monitor.empty_hint"), right - left - 12), midX, cy + 12, Palette.TEXT_MUTED);
            return;
        }

        int wallsRight = right - 8;
        int wallsW = Math.max(c.width(tr("monitor.col.walls")), c.width(tr("db", "−00.0")));
        int loudRight = wallsRight - wallsW - 10;
        int loudW = Math.max(60, Math.min(120, (right - left) / 4));
        int loudLeft = loudRight - loudW;
        int distRight = loudLeft - 10;
        int arrowW = c.width(Component.literal("↗")) + 3;
        int distW = Math.max(c.width(tr("monitor.col.distance")), c.width(tr("blocks", "000")) + arrowW);
        int nameLeft = left + 16;
        int nameW = distRight - distW - 10 - nameLeft;

        int hy = y + 16;
        c.text(tr("monitor.col.name"), nameLeft, hy, Palette.TEXT_MUTED);
        c.right(tr("monitor.col.distance"), distRight, hy, Palette.TEXT_MUTED);
        c.text(tr("monitor.col.loudness"), loudLeft, hy, Palette.TEXT_MUTED);
        c.right(tr("monitor.col.walls"), wallsRight, hy, Palette.TEXT_MUTED);
        c.hLine(left + 6, right - 6, hy + 11, Palette.PANEL_BORDER);

        int rowY = hy + 16;
        int shown = 0;
        for (NearbyPlayers.Row row : rows) {
            if (rowY + 10 > contentBottom - 4 - footer) {
                c.text(tr("monitor.more", rows.size() - shown), nameLeft, rowY - 2, Palette.TEXT_MUTED);
                break;
            }
            // Distance and, after it, an arrow towards the player
            String arrow = Bearing.arrow(row.bearing());
            if (!arrow.isEmpty()) {
                c.right(Component.literal(arrow), distRight, rowY, Palette.TEXT_DIM);
            }
            int numRight = distRight - arrowW;
            if (row.isTalking()) {
                paintTalkingRow(c, row, rowY, wallsActive, nameLeft, nameW, numRight, loudLeft, loudRight, wallsRight);
            } else {
                paintSilentRow(c, row, rowY, nameLeft, nameW, numRight, loudLeft, wallsRight);
            }
            rowY += 13;
            shown++;
        }
    }

    /** Top-down view: you in the middle looking up, the voice and whisper ranges as rings. */
    private void paintRadar(Canvas c, List<NearbyPlayers.Row> rows, int y1, int y2, double maxDist,
                            boolean wallsActive, int mouseX, int mouseY) {
        int size = Math.min(right - left - 16, y2 - y1 - 4);
        if (size < 40) {
            return;
        }
        int radius = size / 2 - 2;
        int cx = (left + right) / 2;
        int cy = y1 + size / 2 + 2;

        ring(c, cx, cy, radius, Palette.withAlpha(Palette.ACCENT, 0x90));
        ring(c, cx, cy, (int) Math.round(radius * AudioDistancePlugin.LINK.whisperShare()), Palette.withAlpha(Palette.WHISPER, 0x70));
        c.hLine(cx - radius, cx + radius + 1, cy, Palette.GRID);
        c.vLine(cx, cy - radius, cy + radius + 1, Palette.GRID);
        c.right(tr("blocks", blocks(maxDist)), cx + radius, cy - radius, Palette.TEXT_MUTED);
        // You, facing up
        c.fill(cx - 2, cy - 2, cx + 3, cy + 3, Palette.TEXT);
        c.centered(Component.literal("↑"), cx, cy - 12, Palette.TEXT_DIM);

        NearbyPlayers.Row hovered = null;
        int hoverX = 0;
        int hoverY = 0;
        for (NearbyPlayers.Row row : rows) {
            if (row.distance() < 0.0 || Double.isNaN(row.bearing())) {
                continue;
            }
            double r = Math.min(1.0, row.distance() / maxDist) * radius;
            double a = Math.toRadians(row.bearing());
            int px = cx + (int) Math.round(Math.sin(a) * r);
            int py = cy - (int) Math.round(Math.cos(a) * r);
            int color;
            if (row.isTalking()) {
                // Shape as well as color: square talking, cross whispering, diamond behind a wall
                SpeakerRegistry.Speaker s = row.speaker();
                boolean muffled = wallsActive && s.getFilter().getDisplayLossDb() > 1.0F;
                color = s.isWhispering() ? Palette.WHISPER : (muffled ? Palette.MUFFLED : Palette.GOOD);
                if (s.isWhispering()) {
                    c.fill(px - 4, py - 1, px + 5, py + 2, 0xFF000000);
                    c.fill(px - 1, py - 4, px + 2, py + 5, 0xFF000000);
                    c.fill(px - 3, py, px + 4, py + 1, color);
                    c.fill(px, py - 3, px + 1, py + 4, color);
                } else if (muffled) {
                    for (int d = -3; d <= 3; d++) {
                        int half = 3 - Math.abs(d);
                        c.fill(px - half - 1, py + d, px + half + 2, py + d + 1, 0xFF000000);
                    }
                    for (int d = -2; d <= 2; d++) {
                        int half = 2 - Math.abs(d);
                        c.fill(px - half, py + d, px + half + 1, py + d + 1, color);
                    }
                } else {
                    c.fill(px - 3, py - 3, px + 4, py + 4, 0xFF000000);
                    c.fill(px - 2, py - 2, px + 3, py + 3, color);
                }
            } else {
                color = stateColor(row.state());
                c.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
                c.frame(px - 2, py - 2, px + 3, py + 3, 0x00000000, color);
            }
            Component name = row.isTalking() ? speakerName(row.speaker())
                    : Component.literal(row.name() != null ? row.name() : "?");
            c.text(fit(c, name, 70), px + 5, py - 4, row.isTalking() ? Palette.TEXT : Palette.TEXT_MUTED);
            if (Math.abs(mouseX - px) <= 4 && Math.abs(mouseY - py) <= 4) {
                hovered = row;
                hoverX = px;
                hoverY = py;
            }
        }
        if (hovered != null) {
            Component state = hovered.isTalking()
                    ? tr(hovered.speaker().isWhispering() ? "monitor.whisper" : "monitor.talking")
                    : tr("monitor.state." + (hovered.state() == null ? "silent" : hovered.state().getTranslationKey()));
            Component name = hovered.isTalking() ? speakerName(hovered.speaker())
                    : Component.literal(hovered.name() != null ? hovered.name() : "?");
            badge(c, tr("monitor.radar.badge", name, blocks(hovered.distance()), state), hoverX, hoverY - 17);
        }
        if (rows.isEmpty()) {
            c.centered(tr("monitor.empty"), cx, cy + radius / 2, Palette.TEXT_DIM);
        }
        paintRadarLegend(c, left + 8, cx - radius - 8, y2);
    }

    /** What the marks mean, in the free space left of the radar (skipped when there is none). */
    private void paintRadarLegend(Canvas c, int x, int maxX, int bottom) {
        int w = maxX - x;
        if (w < 50) {
            return;
        }
        Component[] labels = {tr("monitor.talking"), tr("monitor.whisper"), tr("hud.walls"),
                tr("monitor.state.silent"), tr("monitor.legend.voice_ring"), tr("monitor.legend.whisper_ring")};
        int[] colors = {Palette.GOOD, Palette.WHISPER, Palette.MUFFLED, Palette.TEXT_MUTED,
                Palette.ACCENT, Palette.WHISPER};
        int y = bottom - labels.length * 11;
        for (int i = 0; i < labels.length; i++) {
            // The same shapes as on the radar
            int mx = x + 2;
            int my = y + 4;
            if (i == 1) {
                c.fill(mx - 2, my, mx + 3, my + 1, colors[i]);
                c.fill(mx, my - 2, mx + 1, my + 3, colors[i]);
            } else if (i == 2) {
                for (int d = -2; d <= 2; d++) {
                    int half = 2 - Math.abs(d);
                    c.fill(mx - half, my + d, mx + half + 1, my + d + 1, colors[i]);
                }
            } else if (i == 3) {
                c.frame(x, y + 2, x + 5, y + 7, 0x00000000, colors[i]);
            } else if (i >= 4) {
                c.hLine(x, x + 6, y + 4, Palette.withAlpha(colors[i], 0xC0));
            } else {
                c.fill(x, y + 2, x + 5, y + 7, colors[i]);
            }
            c.text(fit(c, labels[i], w - 9), x + 9, y, Palette.TEXT_DIM);
            y += 11;
        }
    }

    private static void ring(Canvas c, int cx, int cy, int radius, int argb) {
        if (radius < 2) {
            return;
        }
        int steps = Math.max(24, (int) (radius * 6.3));
        for (int i = 0; i < steps; i++) {
            double a = 2.0 * Math.PI * i / steps;
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int y = cy + (int) Math.round(Math.sin(a) * radius);
            c.fill(x, y, x + 1, y + 1, argb);
        }
    }

    private void paintTalkingRow(Canvas c, NearbyPlayers.Row row, int rowY, boolean wallsActive, int nameLeft, int nameW,
                                 int distRight, int loudLeft, int loudRight, int wallsRight) {
        SpeakerRegistry.Speaker s = row.speaker();
        boolean talking = s.getLevelDb() > -50.0F;
        c.fill(left + 7, rowY + 2, left + 11, rowY + 6, talking ? Palette.GOOD : Palette.withAlpha(Palette.TEXT_MUTED, 0x80));

        // A tag after the name: whispering, or a state that means they will not hear you back
        Component tag = null;
        int tagColor = Palette.WHISPER;
        if (s.isWhispering()) {
            tag = tr("monitor.whisper");
        } else if (row.state() != null && row.state().isProblem()) {
            tag = tr("monitor.state." + row.state().getTranslationKey());
            tagColor = Palette.WARN;
        }
        Component name = speakerName(s);
        if (tag != null) {
            int tagW = c.width(tag) + 4;
            Component fitted = fit(c, name, nameW - tagW);
            c.text(fitted, nameLeft, rowY, Palette.TEXT);
            c.text(tag, nameLeft + c.width(fitted) + 4, rowY, tagColor);
        } else {
            c.text(fit(c, name, nameW), nameLeft, rowY, Palette.TEXT);
        }

        c.right(s.getDistance() >= 0.0 ? tr("blocks", blocks(s.getDistance())) : Component.literal("—"),
                distRight, rowY, Palette.TEXT_DIM);

        float lossDb = wallsActive ? s.getFilter().getDisplayLossDb() : 0.0F;
        // Round a wall the voice is as loud as the longer way round
        SoundBlend blend = wallsActive ? s.getBlend() : null;
        double heardAt = blend != null ? s.getDistance() + blend.extraDistance() : s.getDistance();
        double gain = s.getDistance() >= 0.0
                ? AudioDistancePlugin.curveGain(heardAt, s.getMaxDistance(), s.isWhispering()) * OcclusionModel.dbToGain(-lossDb)
                : 0.0;
        Component pctText = Component.literal(pct(gain));
        int barRight = loudRight - c.width(Component.literal("100%")) - 4;
        c.fill(loudLeft, rowY + 1, barRight, rowY + 7, 0x22FFFFFF);
        int fill = loudLeft + (int) Math.round(Math.min(1.0, gain) * (barRight - loudLeft));
        double muffle = wallsActive ? s.getFilter().getDisplayMuffle() : 0.0;
        c.fill(loudLeft, rowY + 1, fill, rowY + 7, Palette.mix(Palette.ACCENT, Palette.MUFFLED, muffle));
        c.right(pctText, loudRight, rowY, Palette.TEXT_DIM);

        if (lossDb > 0.5F) {
            c.right(tr("db", String.format(Locale.ROOT, "−%.1f", lossDb)), wallsRight, rowY, Palette.MUFFLED);
        } else {
            c.right(Component.literal("—"), wallsRight, rowY, Palette.TEXT_MUTED);
        }
    }

    /** A nearby player who is not talking: their voice chat state takes the loudness and walls columns. */
    private void paintSilentRow(Canvas c, NearbyPlayers.Row row, int rowY, int nameLeft, int nameW,
                                int distRight, int loudLeft, int wallsRight) {
        VoiceState state = row.state();
        int color = stateColor(state);
        // Hollow marker: in range, not talking
        c.frame(left + 7, rowY + 2, left + 11, rowY + 6, 0x00000000, Palette.withAlpha(color, 0xC0));
        String name = row.name();
        c.text(fit(c, Component.literal(name != null ? name : row.playerId().toString().substring(0, 8)), nameW),
                nameLeft, rowY, Palette.TEXT_DIM);
        c.right(tr("blocks", blocks(row.distance())), distRight, rowY, Palette.TEXT_DIM);
        Component text = tr("monitor.state." + (state == null ? "silent" : state.getTranslationKey()));
        c.text(fit(c, text, wallsRight - loudLeft), loudLeft, rowY, color);
    }

    private static int stateColor(VoiceState state) {
        if (state == null || state == VoiceState.CONNECTED) {
            return Palette.TEXT_MUTED;
        }
        return switch (state) {
            case GROUP -> Palette.WHISPER;
            case NO_VOICE_CHAT -> Palette.BAD;
            default -> Palette.WARN;
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void badge(Canvas c, Component text, int centerX, int y) {
        int w = c.width(text) + 8;
        int x = Math.max(left + 2, Math.min(right - 2 - w, centerX - w / 2));
        c.frame(x, y, x + w, y + 12, Palette.BADGE, Palette.BADGE_BORDER);
        c.text(text, x + 4, y + 2, Palette.TEXT);
    }

    private Component modelLabel() {
        return tr("model.label", Component.translatable(shown().getModel().getTranslationKey()));
    }

    private Component wallsLabel() {
        return tr("walls.toggle", tr(shown().isOcclusionEnabled() ? "on" : "off"));
    }

    private static Component speakerName(SpeakerRegistry.Speaker s) {
        String name = s.getDisplayName();
        if (name != null && !name.isEmpty()) {
            return Component.literal(name);
        }
        if (s.getKind() == SpeakerRegistry.Kind.LOCATIONAL) {
            return tr("monitor.source");
        }
        String id = s.getEntityId() != null ? s.getEntityId().toString() : s.getChannelId().toString();
        return Component.literal(id.substring(0, 8));
    }

    /** Shortens a text with an ellipsis so it fits into {@code maxWidth} pixels. */
    private static Component fit(Canvas c, Component text, int maxWidth) {
        if (maxWidth <= 0) {
            return Component.empty();
        }
        if (c.width(text) <= maxWidth) {
            return text;
        }
        String s = text.getString();
        for (int len = s.length() - 1; len > 0; len--) {
            Component candidate = Component.literal(s.substring(0, len).trim() + "…");
            if (c.width(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return Component.literal("…");
    }

    private static <T extends AbstractWidget> T withTip(T widget, String key) {
        widget.setTooltip(tip(key));
        return widget;
    }

    private static Tooltip tip(String key) {
        return Tooltip.create(tr(key));
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    private static String pct(double v) {
        return Math.round(v * 100.0) + "%";
    }

    private static String blocks(double v) {
        return v < 10.0 && Math.abs(v - Math.rint(v)) > 0.05
                ? String.format(Locale.ROOT, "%.1f", v)
                : String.valueOf(Math.round(v));
    }

    private static Component db(double gain) {
        if (gain <= 0.0001) {
            return tr("db", "−∞");
        }
        double d = 20.0 * Math.log10(gain);
        return tr("db", Math.abs(d) < 0.05 ? "0" : String.format(Locale.ROOT, "%.1f", d).replace('-', '−'));
    }

    private static Component frequency(double hz) {
        if (hz >= 1000.0) {
            return tr("khz", String.format(Locale.ROOT, "%.1f", hz / 1000.0));
        }
        return tr("hz", String.valueOf(Math.round(hz / 10.0) * 10));
    }
}
