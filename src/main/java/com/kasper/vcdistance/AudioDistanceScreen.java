package com.kasper.vcdistance;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Modern, interactive settings screen for VoiceChat Audio Distance Addon.
 * Includes real-time acoustic preview curve, model cycling, tooltips, presets, and action buttons.
 */
public class AudioDistanceScreen extends Screen {

    private static final Component TITLE = Component.translatable("gui.vc-audio-distance.title");

    private final Screen parent;
    private final AttenuationModel initialModel;
    private final double initialAttenuation;
    private final double initialMinVolume;
    private final double initialRefRatio;
    private final double initialWhisperMult;

    public AudioDistanceScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
        this.initialModel       = AudioDistancePlugin.CONFIG.model;
        this.initialAttenuation = AudioDistancePlugin.CONFIG.attenuationFactor;
        this.initialMinVolume   = AudioDistancePlugin.CONFIG.minVolumeFraction;
        this.initialRefRatio    = AudioDistancePlugin.CONFIG.openalReferenceRatio;
        this.initialWhisperMult = AudioDistancePlugin.CONFIG.whisperMultiplier;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int totalWidth = 220;
        int startX = centerX - totalWidth / 2;
        int y = this.height / 2 - 65;

        // ── 1. Attenuation Model Selector Button ──────────────────────────────
        Button modelButton = Button.builder(
                Component.translatable("gui.vc-audio-distance.model.label", AudioDistancePlugin.CONFIG.model.getDisplayName()),
                btn -> {
                    AudioDistancePlugin.CONFIG.model = AudioDistancePlugin.CONFIG.model.next();
                    refreshScreen();
                }
        )
        .bounds(startX, y, totalWidth, 20)
        .tooltip(Tooltip.create(AudioDistancePlugin.CONFIG.model.getTooltip()))
        .build();
        addRenderableWidget(modelButton);
        y += 23;

        // ── 2. Attenuation Factor / Rolloff Slider (0% - 100%) ────────────────
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
        y += 23;

        // ── 3. Minimum Volume Floor Slider (0% - 100%) ───────────────────────
        AbstractSliderButton minVolumeSlider = new AbstractSliderButton(
                startX, y, totalWidth, 20,
                Component.empty(),
                AudioDistancePlugin.CONFIG.minVolumeFraction
        ) {
            {
                setTooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.min_volume.tooltip")));
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
        y += 23;

        // ── 4. Attenuation Start Ratio Slider (10% - 100%) ───────────────────
        // Mapped from normalized value (0.0 - 1.0) to ratio (0.10 - 1.00)
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
                setMessage(Component.translatable("gui.vc-audio-distance.attenuation_start", pct));
            }

            @Override
            protected void applyValue() {
                AudioDistancePlugin.CONFIG.openalReferenceRatio = 0.10 + value * 0.90;
            }
        };
        addRenderableWidget(refDistanceSlider);
        y += 26;

        // ── 5. Presets Row (4 buttons) ───────────────────────────────────────
        int gap = 2;
        int presetBtnWidth = (totalWidth - gap * 3) / 4; // ~53px each

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

        y += 25;

        // ── 6. Bottom Action Row: Reset, Cancel, Save ────────────────────────
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
        renderTransparentBackground(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int totalWidth = 220;
        int startX = centerX - totalWidth / 2;

        // Title
        guiGraphics.drawCenteredString(this.font, this.title, centerX, this.height / 2 - 105, 0xFFFFFF);

        // ── Real-Time Acoustic Curve Visualizer ──────────────────────────────
        int previewY = this.height / 2 - 91;
        int previewHeight = 20;

        // Box border & dark translucent background
        guiGraphics.fill(startX - 1, previewY - 1, startX + totalWidth + 1, previewY + previewHeight + 1, 0xFF555555);
        guiGraphics.fill(startX, previewY, startX + totalWidth, previewY + previewHeight, 0xEE121212);

        // Render physics curve according to selected OpenAL model
        AttenuationModel model = AudioDistancePlugin.CONFIG.model;
        double rolloff  = AudioDistancePlugin.CONFIG.attenuationFactor;
        double minVol   = AudioDistancePlugin.CONFIG.minVolumeFraction;
        double refRatio = AudioDistancePlugin.CONFIG.openalReferenceRatio;

        for (int i = 0; i < totalWidth; i++) {
            double distFraction = (double) i / (double) totalWidth;
            double gain;

            if (distFraction <= refRatio) {
                gain = 1.0;
            } else {
                double excess = distFraction - refRatio;
                double remaining = Math.max(0.001, 1.0 - refRatio);

                switch (model) {
                    case REALISTIC_INVERSE -> {
                        // OpenAL Inverse Distance Model formula
                        gain = refRatio / (refRatio + rolloff * excess);
                    }
                    case EXPONENTIAL -> {
                        // OpenAL Exponential Distance Model formula
                        gain = Math.pow(Math.max(0.0001, distFraction / Math.max(0.01, refRatio)), -rolloff * 1.5);
                    }
                    default -> {
                        // Standard Linear Distance Model
                        gain = 1.0 - rolloff * (excess / remaining);
                    }
                }
            }

            // Apply hardware min gain floor and clamp [0.0, 1.0]
            gain = Math.max(minVol, Math.max(0.0, Math.min(1.0, gain)));

            int barHeight = (int) Math.round(gain * (previewHeight - 2));
            if (barHeight > 0) {
                // Color ramp: Bright Lime (100%) -> Cyan / Yellow -> Soft Orange at low volume
                int red   = (int) (Math.max(0.0, 1.0 - gain) * 220);
                int green = (int) (160 + gain * 95);
                int blue  = (int) (gain * 110 + (1.0 - gain) * 40);
                int color = 0xFF000000 | (red << 16) | (green << 8) | blue;

                guiGraphics.fill(startX + i, previewY + previewHeight - 1 - barHeight,
                        startX + i + 1, previewY + previewHeight - 1, color);
            }
        }

        // Distance text indicators
        guiGraphics.drawString(this.font, Component.translatable("gui.vc-audio-distance.curve_close"),
                startX + 4, previewY + 6, 0xEEEEEE, true);
        Component farText = Component.translatable("gui.vc-audio-distance.curve_far");
        int farWidth = this.font.width(farText);
        guiGraphics.drawString(this.font, farText,
                startX + totalWidth - farWidth - 4, previewY + 6, 0xEEEEEE, true);
    }
}
