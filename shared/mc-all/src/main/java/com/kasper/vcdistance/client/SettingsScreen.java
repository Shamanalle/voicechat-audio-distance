package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.AttenuationModel;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.AudioPhysics;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.NearbyPlayers;
import com.kasper.vcdistance.OcclusionModel;
import com.kasper.vcdistance.Preset;
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

    public enum Tab {
        DISTANCE("tab.distance"),
        WALLS("tab.walls"),
        MATERIALS("tab.materials"),
        MONITOR("tab.monitor");

        private final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private record Example(String key, AcousticMaterial material, int blocks) {
    }

    private static final Example[] EXAMPLES = {
            new Example("glass", AcousticMaterial.GLASS, 1),
            new Example("wood", AcousticMaterial.WOOD, 1),
            new Example("stone1", AcousticMaterial.STONE, 1),
            new Example("stone2", AcousticMaterial.STONE, 2),
            new Example("stone3", AcousticMaterial.STONE, 3),
            new Example("wool", AcousticMaterial.WOOL, 1),
            new Example("leaves", AcousticMaterial.LEAVES, 3)
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
    /** Widgets that change settings; disabled while the server enforces its profile. */
    private final List<AbstractWidget> editWidgets = new ArrayList<>();
    private boolean serverChip;

    protected SettingsScreen(Screen parent) {
        super(Component.translatable(K + "title"));
        this.parent = parent;
        this.config.ensureLoaded();
        this.snapshot = config.copy();
        this.tab = lastTab;
    }

    /** Shows another screen (the API for this differs between versions). */
    protected abstract void openScreen(Screen screen);

    protected abstract boolean inWorld();

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
        serverChip = false;

        int w = Math.min(this.width - 16, MAX_WIDTH);
        left = (this.width - w) / 2;
        right = left + w;

        Tab[] tabs = Tab.values();
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
            case MONITOR -> {
            }
        }

        initServerChip(w);
        if (AudioDistancePlugin.LINK.isEnforced()) {
            for (AbstractWidget widget : editWidgets) {
                widget.active = false;
            }
        }
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
                p.apply(config);
                rebuild();
            }).bounds(x, contentTop, pw, 20).tooltip(Tooltip.create(Component.translatable(p.getTooltipKey()))).build();
            presetButtons.add(b);
            presetOrder.add(p);
            presetBounds.add(new int[]{x, x + pw, contentTop + 20});
            edit(b);
        }
        refreshPresetButtons();

        int rows = contentBottom - ROW * 3 + GAP;
        graphTop = contentTop + ROW + 2;
        graphBottom = rows - 6;

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
        int colW = (w - GAP) / 2;
        int top = contentTop + 14;
        AcousticMaterial[] materials = AcousticMaterial.values();
        for (int i = 0; i < materials.length; i++) {
            AcousticMaterial m = materials[i];
            int x = i % 2 == 0 ? left : right - colW;
            int y = top + (i / 2) * ROW;
            RangeSlider slider = new RangeSlider(x, y, colW, 20, 0.0, AcousticMaterial.MAX_WEIGHT, 0.05,
                    () -> shown().getMaterialWeight(m), v -> config.setMaterialWeight(m, v),
                    v -> tr("material.value", Component.translatable(m.getTranslationKey()), pct(v)));
            slider.setTooltip(Tooltip.create(Component.translatable(m.getTooltipKey())));
            edit(slider);
        }
        int resetY = top + ((materials.length + 1) / 2) * ROW;
        if (resetY + 20 <= contentBottom) {
            edit(Button.builder(tr("materials.reset"), b -> {
                config.resetMaterials();
                rebuild();
            }).bounds(left, resetY, colW, 20).build());
        }
    }

    private void switchTab(Tab t) {
        tab = t;
        lastTab = t;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void refreshPresetButtons() {
        boolean enforced = AudioDistancePlugin.LINK.isEnforced();
        for (int i = 0; i < presetButtons.size(); i++) {
            presetButtons.get(i).active = !enforced && !presetOrder.get(i).matches(config);
        }
    }

    // =========================================================================
    // Closing
    // =========================================================================

    private void cancel() {
        config.copyFrom(snapshot);
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
        refreshPresetButtons();
        if (serverChip) {
            c.text(this.title, left, 8, Palette.TEXT);
            if (AudioDistancePlugin.LINK.isEnforced()) {
                c.right(tr("server.enforced"), right, 8, Palette.WARN);
            }
        } else {
            c.centered(this.title, this.width / 2, 8, Palette.TEXT);
        }
        c.fill(activeTabX1 + 2, tabsBottom + 1, activeTabX2 - 2, tabsBottom + 3, Palette.ACCENT);
        for (int i = 0; i < presetBounds.size(); i++) {
            if (presetOrder.get(i).matches(shown())) {
                int[] b = presetBounds.get(i);
                c.fill(b[0] + 2, b[2] + 1, b[1] - 2, b[2] + 3, Palette.ACCENT);
            }
        }
        switch (tab) {
            case DISTANCE -> paintDistance(c, mouseX, mouseY);
            case WALLS -> paintWalls(c);
            case MATERIALS -> c.text(fit(c, tr("materials.hint"), right - left), left, contentTop, Palette.TEXT_MUTED);
            case MONITOR -> paintMonitor(c);
        }
    }

    // ---- Distance -----------------------------------------------------------

    private void paintDistance(Canvas c, int mouseX, int mouseY) {
        int x1 = left;
        int x2 = right;
        int y1 = graphTop;
        int y2 = graphBottom;
        if (y2 - y1 < 44) {
            return;
        }
        c.frame(x1, y1, x2, y2, Palette.PANEL, Palette.PANEL_BORDER);

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
        int headerY = y1 + 5;
        boolean legend = c.width(summary) + legendW + 20 <= x2 - x1;
        c.text(fit(c, summary, x2 - x1 - 12), x1 + 6, headerY, Palette.TEXT_DIM);
        if (legend) {
            int lx = x2 - 6 - c.width(whisper);
            c.text(whisper, lx, headerY, Palette.TEXT_DIM);
            c.fill(lx - 11, headerY + 3, lx - 4, headerY + 5, Palette.WHISPER);
            lx -= 16 + c.width(voice);
            c.text(voice, lx, headerY, Palette.TEXT_DIM);
            c.fill(lx - 11, headerY + 3, lx - 4, headerY + 5, Palette.ACCENT_LINE);
        }

        int px1 = x1 + 8;
        int px2 = x2 - 8;
        int py1 = y1 + 18;
        int py2 = y2 - 13;
        int pw = px2 - px1;
        int ph = py2 - py1;
        if (pw < 40 || ph < 14) {
            return;
        }

        for (int i = 1; i < 4; i++) {
            c.hLine(px1, px2, py2 - ph * i / 4, Palette.GRID);
            c.vLine(px1 + pw * i / 4, py1, py2, Palette.GRID);
        }
        int refX = px1 + (int) Math.round(ref * pw);
        c.fill(px1, py1, refX, py2, Palette.ACCENT_ZONE);
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

        // Whisper curve (dashed) over the whisper range
        double whisperRolloff = AudioDistancePlugin.effectiveRolloff(true);
        int whisperEnd = Math.max(1, (int) Math.round(AudioDistancePlugin.LINK.whisperShare() * pw));
        prevY = -1;
        for (int i = 0; i <= whisperEnd; i++) {
            double g = AudioPhysics.calculateGain((double) i / whisperEnd, model, whisperRolloff, floor, ref);
            int y = py2 - (int) Math.round(g * ph);
            if ((i / 3) % 2 == 0) {
                int top = prevY < 0 ? y : Math.min(prevY, y);
                int bottom = prevY < 0 ? y : Math.max(prevY, y);
                c.fill(px1 + i, top, px1 + i + 1, bottom + 1, Palette.WHISPER);
            }
            prevY = y;
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
        for (SpeakerRegistry.Speaker s : AudioDistancePlugin.SPEAKERS.active(System.nanoTime())) {
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
                    hoverX, hoverY - 17 >= y1 + 15 ? hoverY - 17 : hoverY + 5);
        } else if (mouseX >= px1 && mouseX <= px2 && mouseY >= py1 && mouseY <= py2) {
            double f = (double) (mouseX - px1) / pw;
            double g = AudioPhysics.calculateGain(f, model, rolloff, floor, ref);
            int gy = py2 - (int) Math.round(g * ph);
            c.vLine(mouseX, py1, py2, 0x66FFFFFF);
            c.fill(mouseX - 1, gy - 1, mouseX + 2, gy + 2, 0xFFFFFFFF);
            int badgeY = gy - 17 >= y1 + 15 ? gy - 17 : Math.min(py2 - 12, gy + 5);
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
        c.frame(left, y, right, contentBottom, Palette.PANEL, Palette.PANEL_BORDER);
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
            if (rowY + 10 > contentBottom - 4) {
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
        if (rowY + 22 <= contentBottom - 4) {
            c.text(fit(c, tr("walls.hint"), right - left - 16), left + 8, contentBottom - 14, Palette.TEXT_MUTED);
        }
    }

    // ---- Monitor ------------------------------------------------------------

    private void paintMonitor(Canvas c) {
        c.frame(left, contentTop, right, contentBottom, Palette.PANEL, Palette.PANEL_BORDER);
        int midX = (left + right) / 2;
        if (!inWorld()) {
            c.centered(tr("monitor.no_world"), midX, (contentTop + contentBottom) / 2 - 4, Palette.TEXT_DIM);
            return;
        }

        AudioDistancePlugin.OcclusionStatus status = AudioDistancePlugin.occlusionStatus();
        boolean wallsActive = status == AudioDistancePlugin.OcclusionStatus.ACTIVE;
        double maxDist = AudioDistancePlugin.getServerMaxDistance();
        int y = contentTop + 6;
        c.text(tr("monitor.walls", tr("monitor.status." + status.name().toLowerCase(Locale.ROOT))), left + 6, y,
                wallsActive ? Palette.GOOD : Palette.TEXT_MUTED);
        c.right(tr("monitor.range", blocks(maxDist)), right - 6, y, Palette.TEXT_MUTED);
        LinkProtocol.ServerProfile serverProfile = AudioDistancePlugin.LINK.profile();
        Component serverLine = serverProfile == null
                ? tr("monitor.server.none")
                : tr("monitor.server.addon", tr("monitor.server.mode." + serverProfile.mode().getId()));
        y += 11;
        c.text(fit(c, serverLine, right - left - 12), left + 6, y, Palette.TEXT_MUTED);

        long now = System.nanoTime();
        List<NearbyPlayers.Row> rows = NearbyPlayers.rows(AudioDistancePlugin.SPEAKERS.active(now),
                AudioDistancePlugin.NEARBY.players(), id -> AudioDistancePlugin.LINK.voiceState(id, now));
        if (rows.isEmpty()) {
            int cy = (contentTop + contentBottom) / 2 - 8;
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
        int distW = Math.max(c.width(tr("monitor.col.distance")), c.width(tr("blocks", "000")));
        int nameLeft = left + 16;
        int nameW = distRight - distW - 10 - nameLeft;

        int hy = y + 16;
        c.text(tr("monitor.col.name"), nameLeft, hy, Palette.TEXT_MUTED);
        c.right(tr("monitor.col.distance"), distRight, hy, Palette.TEXT_MUTED);
        c.text(tr("monitor.col.loudness"), loudLeft, hy, Palette.TEXT_MUTED);
        c.right(tr("monitor.col.walls"), wallsRight, hy, Palette.TEXT_MUTED);
        c.hLine(left + 6, right - 6, hy + 11, Palette.PANEL_BORDER);

        // Without the addon on the server nobody's voice chat state is known, only who is talking
        int footer = AudioDistancePlugin.LINK.hasVoiceStates(now) ? 0 : 12;
        int rowY = hy + 16;
        int shown = 0;
        for (NearbyPlayers.Row row : rows) {
            if (rowY + 10 > contentBottom - 4 - footer) {
                c.text(tr("monitor.more", rows.size() - shown), nameLeft, rowY - 2, Palette.TEXT_MUTED);
                break;
            }
            if (row.isTalking()) {
                paintTalkingRow(c, row, rowY, wallsActive, nameLeft, nameW, distRight, loudLeft, loudRight, wallsRight);
            } else {
                paintSilentRow(c, row, rowY, nameLeft, nameW, distRight, loudLeft, wallsRight);
            }
            rowY += 13;
            shown++;
        }
        if (footer > 0) {
            c.text(fit(c, tr("monitor.states_unknown"), right - left - 12), left + 6, contentBottom - 13, Palette.TEXT_MUTED);
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
        double gain = s.getDistance() >= 0.0
                ? AudioDistancePlugin.curveGain(s.getDistance(), s.getMaxDistance(), s.isWhispering()) * OcclusionModel.dbToGain(-lossDb)
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
