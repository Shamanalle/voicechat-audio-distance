package com.kasper.vcdistance;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Ultra-clear, interactive configuration screen for VoiceChat Audio Distance Addon.
 * Displays concrete distances in blocks, real-time audio physics curve with hover inspector,
 * human-readable live scenario summary, presets, and tooltips.
 */
public class AudioDistanceScreen extends Screen {

    private static final Component TITLE = Component.translatable("gui.vc-audio-distance.title");

    private final Screen parent;
    private final double maxDistance;
    private final AttenuationModel initialModel;
    private final double initialAttenuation;
    private final double initialMinVolume;
    private final double initialRefRatio;
    private final double initialWhisperMult;
    private final boolean initialOcclusionEnabled;
    private final double initialOcclusionStrength;

    // Track mouse coordinates for the live curve hover inspector
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    public AudioDistanceScreen(Screen parent) {
        super(TITLE);
        this.parent                   = parent;
        this.maxDistance              = AudioDistancePlugin.getServerMaxDistance();
        this.initialModel             = AudioDistancePlugin.CONFIG.model;
        this.initialAttenuation       = AudioDistancePlugin.CONFIG.attenuationFactor;
        this.initialMinVolume         = AudioDistancePlugin.CONFIG.minVolumeFraction;
        this.initialRefRatio          = AudioDistancePlugin.CONFIG.openalReferenceRatio;
        this.initialWhisperMult       = AudioDistancePlugin.CONFIG.whisperMultiplier;
        this.initialOcclusionEnabled  = AudioDistancePlugin.CONFIG.occlusionEnabled;
        this.initialOcclusionStrength = AudioDistancePlugin.CONFIG.occlusionStrength;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int totalWidth = 230;
        int startX = centerX - totalWidth / 2;

        int totalContentHeight = 246;
        int startY = Math.max(6, (this.height - totalContentHeight) / 2);

        // ── 1. Model Selector Button ─────────────────────────────────────────
        int y = startY + 68;
        Button modelButton = Button.builder(
                Component.translatable("gui.vc-audio-distance.model.label", Component.translatable(AudioDistancePlugin.CONFIG.model.getTranslationKey())),
                btn -> {
                    AudioDistancePlugin.CONFIG.model = AudioDistancePlugin.CONFIG.model.next();
                    refreshScreen();
                }
        )
        .bounds(startX, y, totalWidth, 20)
        .tooltip(Tooltip.create(Component.translatable(AudioDistancePlugin.CONFIG.model.getTooltipKey())))
        .build();
        addRenderableWidget(modelButton);
        y += 22;

        // ── 2. Attenuation / Decay Rate Slider (0% - 100%) ───────────────────
        AbstractSliderButton attenuationSlider = new AbstractSliderButton(
                startX, y, totalWidth, 20,
                Component.empty(),
                AudioDistancePlugin.CONFIG.attenuationFactor
        ) {
            {
                setTooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.attenuation.tooltip")));
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int pct = (int) Math.round(value * 100);
                setMessage(Component.translatable("gui.vc-audio-distance.attenuation", pct));
            }

            @Override
            protected void applyValue() {
                AudioDistancePlugin.CONFIG.attenuationFactor = value;
            }
        };
        addRenderableWidget(attenuationSlider);
        y += 22;

        // ── 3. Far Volume Floor Slider (0% - 100%) ───────────────────────────
        AbstractSliderButton minVolumeSlider = new AbstractSliderButton(
                startX, y, totalWidth, 20,
                Component.empty(),
                AudioDistancePlugin.CONFIG.minVolumeFraction
        ) {
            {
                int maxBlocks = (int) Math.round(maxDistance);
                setTooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.min_volume.tooltip", maxBlocks)));
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int pct = (int) Math.round(value * 100);
                setMessage(Component.translatable("gui.vc-audio-distance.min_volume", pct));
            }

            @Override
            protected void applyValue() {
                AudioDistancePlugin.CONFIG.minVolumeFraction = value;
            }
        };
        addRenderableWidget(minVolumeSlider);
        y += 22;

        // ── 4. Falloff Start Ratio Slider (10% - 100%) ───────────────────────
        double initialRatioNorm = (AudioDistancePlugin.CONFIG.openalReferenceRatio - 0.10) / 0.90;
        AbstractSliderButton refDistanceSlider = new AbstractSliderButton(
                startX, y, totalWidth, 20,
                Component.empty(),
                Math.max(0.0, Math.min(1.0, initialRatioNorm))
        ) {
            {
                setTooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.attenuation_start.tooltip")));
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                double ratio = 0.10 + value * 0.90;
                int pct = (int) Math.round(ratio * 100);
                int blocks = (int) Math.round(ratio * maxDistance);
                setMessage(Component.translatable("gui.vc-audio-distance.attenuation_start", blocks, pct));
            }

            @Override
            protected void applyValue() {
                AudioDistancePlugin.CONFIG.openalReferenceRatio = 0.10 + value * 0.90;
            }
        };
        addRenderableWidget(refDistanceSlider);
        y += 22;

        // ── 5. Whisper Decay Multiplier Slider (0.50x - 2.00x) ───────────────
        double initialWhisperNorm = (AudioDistancePlugin.CONFIG.whisperMultiplier - 0.50) / 1.50;
        AbstractSliderButton whisperSlider = new AbstractSliderButton(
                startX, y, totalWidth, 20,
                Component.empty(),
                Math.max(0.0, Math.min(1.0, initialWhisperNorm))
        ) {
            {
                setTooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.whisper_multiplier.tooltip")));
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                double mult = 0.50 + value * 1.50;
                setMessage(Component.translatable("gui.vc-audio-distance.whisper_multiplier", String.format(java.util.Locale.ROOT, "%.2fx", mult)));
            }

            @Override
            protected void applyValue() {
                AudioDistancePlugin.CONFIG.whisperMultiplier = 0.50 + value * 1.50;
            }
        };
        addRenderableWidget(whisperSlider);
        y += 22;

        // ── 6. Sound Occlusion Row (Toggle + Strength Slider) ────────────────
        int halfGap = 2;
        int halfWidth = (totalWidth - halfGap) / 2;

        boolean occEnabled = AudioDistancePlugin.CONFIG.occlusionEnabled;
        Component toggleText = Component.translatable(
                occEnabled ? "gui.vc-audio-distance.occlusion.enabled" : "gui.vc-audio-distance.occlusion.disabled"
        );
        Button occlusionToggle = Button.builder(
                toggleText,
                btn -> {
                    AudioDistancePlugin.CONFIG.occlusionEnabled = !AudioDistancePlugin.CONFIG.occlusionEnabled;
                    refreshScreen();
                }
        )
        .bounds(startX, y, halfWidth, 20)
        .tooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.occlusion.toggle.tooltip")))
        .build();
        addRenderableWidget(occlusionToggle);

        AbstractSliderButton occlusionSlider = new AbstractSliderButton(
                startX + halfWidth + halfGap, y, halfWidth, 20,
                Component.empty(),
                AudioDistancePlugin.CONFIG.occlusionStrength
        ) {
            {
                active = AudioDistancePlugin.CONFIG.occlusionEnabled;
                setTooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.occlusion.strength.tooltip")));
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int pct = (int) Math.round(value * 100);
                setMessage(Component.translatable("gui.vc-audio-distance.occlusion.strength", pct));
            }

            @Override
            protected void applyValue() {
                AudioDistancePlugin.CONFIG.occlusionStrength = value;
            }
        };
        addRenderableWidget(occlusionSlider);
        y += 23;

        // ── 7. Presets Row (4 buttons) ───────────────────────────────────────
        int gap = 2;
        int presetBtnWidth = (totalWidth - gap * 3) / 4;

        // Preset: Vanilla Default
        addRenderableWidget(Button.builder(
                Component.translatable("gui.vc-audio-distance.preset.default"),
                btn -> applyPreset(() -> AudioDistancePlugin.CONFIG.applyDefault())
        )
        .bounds(startX, y, presetBtnWidth, 20)
        .tooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.preset.default.tooltip")))
        .build());

        // Preset: Realistic
        addRenderableWidget(Button.builder(
                Component.translatable("gui.vc-audio-distance.preset.realistic"),
                btn -> applyPreset(() -> AudioDistancePlugin.CONFIG.applyRealistic())
        )
        .bounds(startX + (presetBtnWidth + gap), y, presetBtnWidth, 20)
        .tooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.preset.realistic.tooltip")))
        .build());

        // Preset: High Audibility
        addRenderableWidget(Button.builder(
                Component.translatable("gui.vc-audio-distance.preset.audible"),
                btn -> applyPreset(() -> AudioDistancePlugin.CONFIG.applyHighAudibility())
        )
        .bounds(startX + (presetBtnWidth + gap) * 2, y, presetBtnWidth, 20)
        .tooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.preset.audible.tooltip")))
        .build());

        // Preset: Atmospheric / Stealth
        addRenderableWidget(Button.builder(
                Component.translatable("gui.vc-audio-distance.preset.atmospheric"),
                btn -> applyPreset(() -> AudioDistancePlugin.CONFIG.applyAtmospheric())
        )
        .bounds(startX + (presetBtnWidth + gap) * 3, y, presetBtnWidth, 20)
        .tooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.preset.atmospheric.tooltip")))
        .build());

        y += 24;

        // ── 8. Bottom Actions: Reset, Cancel, Save ───────────────────────────
        int actionBtnWidth = (totalWidth - gap * 2) / 3;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.vc-audio-distance.reset"),
                btn -> applyPreset(() -> AudioDistancePlugin.CONFIG.applyDefault())
        )
        .bounds(startX, y, actionBtnWidth, 20)
        .tooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.reset.tooltip")))
        .build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.vc-audio-distance.cancel"),
                btn -> cancelAndClose()
        )
        .bounds(startX + (actionBtnWidth + gap), y, actionBtnWidth, 20)
        .build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.vc-audio-distance.save"),
                btn -> saveAndClose()
        )
        .bounds(startX + (actionBtnWidth + gap) * 2, y, actionBtnWidth, 20)
        .build());
    }

    private void applyPreset(Runnable presetAction) {
        presetAction.run();
        refreshScreen();
    }

    private void refreshScreen() {
        this.clearWidgets();
        this.init();
    }

    private void cancelAndClose() {
        AudioDistancePlugin.CONFIG.model                = initialModel;
        AudioDistancePlugin.CONFIG.attenuationFactor    = initialAttenuation;
        AudioDistancePlugin.CONFIG.minVolumeFraction    = initialMinVolume;
        AudioDistancePlugin.CONFIG.openalReferenceRatio = initialRefRatio;
        AudioDistancePlugin.CONFIG.whisperMultiplier    = initialWhisperMult;
        AudioDistancePlugin.CONFIG.occlusionEnabled     = initialOcclusionEnabled;
        AudioDistancePlugin.CONFIG.occlusionStrength    = initialOcclusionStrength;
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void saveAndClose() {
        AudioDistancePlugin.CONFIG.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        cancelAndClose();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft != null && this.minecraft.level != null) {
            renderTransparentBackground(guiGraphics);
        } else {
            renderPanorama(guiGraphics, delta);
            renderBlurredBackground(guiGraphics);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        super.render(guiGraphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int totalWidth = 230;
        int startX = centerX - totalWidth / 2;

        int totalContentHeight = 246;
        int startY = Math.max(6, (this.height - totalContentHeight) / 2);

        // Title
        guiGraphics.drawCenteredString(this.font, this.title, centerX, startY, 0xFFFFFF);

        // ── 1. Interactive Acoustic Curve Box ────────────────────────────────
        int previewY = startY + 14;
        int previewHeight = 26;

        // Border and background
        guiGraphics.fill(startX - 1, previewY - 1, startX + totalWidth + 1, previewY + previewHeight + 1, 0xFF666666);
        guiGraphics.fill(startX, previewY, startX + totalWidth, previewY + previewHeight, 0xF0101014);

        AttenuationModel model = AudioDistancePlugin.CONFIG.model;
        double rolloff  = AudioDistancePlugin.CONFIG.attenuationFactor;
        double minVol   = AudioDistancePlugin.CONFIG.minVolumeFraction;
        double refRatio = AudioDistancePlugin.CONFIG.openalReferenceRatio;

        // Draw volume fill columns across distance
        for (int i = 0; i < totalWidth; i++) {
            double distFraction = (double) i / (double) totalWidth;
            double gain = calculateGain(distFraction, model, rolloff, minVol, refRatio);

            int barHeight = (int) Math.round(gain * (previewHeight - 2));
            if (barHeight > 0) {
                int red   = (int) (Math.max(0.0, 1.0 - gain) * 220);
                int green = (int) (160 + gain * 95);
                int blue  = (int) (gain * 110 + (1.0 - gain) * 40);
                int color = 0xFF000000 | (red << 16) | (green << 8) | blue;

                guiGraphics.fill(startX + i, previewY + previewHeight - 1 - barHeight,
                        startX + i + 1, previewY + previewHeight - 1, color);
            }
        }

        // Reference distance vertical marker (where decay starts)
        int refPixelX = startX + (int) Math.round(refRatio * totalWidth);
        if (refPixelX > startX + 5 && refPixelX < startX + totalWidth - 5) {
            guiGraphics.fill(refPixelX, previewY + 1, refPixelX + 1, previewY + previewHeight - 1, 0xAAFFFFFF);
        }

        // Horizontal min volume floor line (if > 0)
        if (minVol > 0.0) {
            int floorY = previewY + previewHeight - 1 - (int) Math.round(minVol * (previewHeight - 2));
            guiGraphics.fill(startX, floorY, startX + totalWidth, floorY + 1, 0x8800FFFF);
        }

        // Distance text labels on the curve
        guiGraphics.drawString(this.font, Component.translatable("gui.vc-audio-distance.curve_close"),
                startX + 4, previewY + 3, 0xFFEEEE, true);

        int maxBlocks = (int) Math.round(maxDistance);
        Component farText = Component.translatable("gui.vc-audio-distance.curve_far", maxBlocks);
        int farWidth = this.font.width(farText);
        guiGraphics.drawString(this.font, farText,
                startX + totalWidth - farWidth - 4, previewY + 3, 0xFFEEEE, true);

        // Hover Inspector on the curve
        boolean isHoveringCurve = (mouseX >= startX && mouseX < startX + totalWidth
                && mouseY >= previewY && mouseY <= previewY + previewHeight);

        if (isHoveringCurve) {
            // Draw inspector cursor line
            guiGraphics.fill(mouseX, previewY, mouseX + 1, previewY + previewHeight, 0xFFFFFFFF);

            double inspectDistFraction = (double) (mouseX - startX) / (double) totalWidth;
            int inspectBlock = (int) Math.round(inspectDistFraction * maxDistance);
            double inspectGain = calculateGain(inspectDistFraction, model, rolloff, minVol, refRatio);
            int inspectPct = (int) Math.round(inspectGain * 100);

            Component inspectText = Component.translatable("gui.vc-audio-distance.curve_inspect", inspectBlock, inspectPct);
            int badgeWidth = this.font.width(inspectText) + 8;
            int badgeX = Math.max(startX + 2, Math.min(startX + totalWidth - badgeWidth - 2, mouseX - badgeWidth / 2));
            int badgeY = previewY + 2;

            guiGraphics.fill(badgeX - 1, badgeY - 1, badgeX + badgeWidth + 1, badgeY + 11, 0xFF333333);
            guiGraphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + 10, 0xF0141418);
            guiGraphics.drawString(this.font, inspectText, badgeX + 4, badgeY + 1, 0xFFFF55, false);
        }

        // ── 2. Live Human-Readable Scenario Summary ──────────────────────────
        int summaryY = startY + 43;
        int summaryHeight = 22;

        guiGraphics.fill(startX - 1, summaryY - 1, startX + totalWidth + 1, summaryY + summaryHeight + 1, 0xFF3D444D);
        guiGraphics.fill(startX, summaryY, startX + totalWidth, summaryY + summaryHeight, 0xF0181D24);

        int startBlock = (int) Math.round(refRatio * maxDistance);
        int finalPct = (int) Math.round(calculateGain(1.0, model, rolloff, minVol, refRatio) * 100);

        if (rolloff <= 0.001) {
            Component flatText = Component.translatable("gui.vc-audio-distance.summary.flat", maxBlocks);
            guiGraphics.drawString(this.font, flatText, startX + 5, summaryY + 7, 0x55FF55, false);
        } else {
            Component zoneText = Component.translatable("gui.vc-audio-distance.summary.zone100", startBlock);
            guiGraphics.drawString(this.font, zoneText, startX + 5, summaryY + 2, 0x55FF55, false);

            Component falloffText = Component.translatable("gui.vc-audio-distance.summary.falloff", finalPct, maxBlocks);
            guiGraphics.drawString(this.font, falloffText, startX + 5, summaryY + 12, 0x55FFFF, false);
        }
    }

    /**
     * Calculates the normalized gain (0.0 - 1.0) according to OpenAL physical attenuation formulas.
     */
    public static double calculateGain(double distFraction, AttenuationModel model,
                                      double rolloff, double minVol, double refRatio) {
        return AudioPhysics.calculateGain(distFraction, model, rolloff, minVol, refRatio);
    }
}
