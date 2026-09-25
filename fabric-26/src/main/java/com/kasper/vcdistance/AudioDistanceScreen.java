package com.kasper.vcdistance;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Interactive Audio Distance Configuration GUI for Minecraft 26.3.
 * Supports live real-time preview curve, preset buttons, and occlusion controls.
 */
public class AudioDistanceScreen extends Screen {

    private final Screen parent;

    // Snapshot of config when entering screen (for cancel action)
    private final AttenuationModel initialModel;
    private final double initialAttenuation;
    private final double initialMinVolume;
    private final double initialRefRatio;
    private final double initialWhisperMult;
    private final boolean initialOcclusion;
    private final double initialOcclusionStrength;

    public AudioDistanceScreen(Screen parent) {
        super(Component.translatable("gui.vc-audio-distance.title"));
        this.parent = parent;
        this.initialModel             = AudioDistancePlugin.CONFIG.model;
        this.initialAttenuation       = AudioDistancePlugin.CONFIG.attenuationFactor;
        this.initialMinVolume         = AudioDistancePlugin.CONFIG.minVolumeFraction;
        this.initialRefRatio          = AudioDistancePlugin.CONFIG.openalReferenceRatio;
        this.initialWhisperMult       = AudioDistancePlugin.CONFIG.whisperMultiplier;
        this.initialOcclusion         = AudioDistancePlugin.CONFIG.occlusionEnabled;
        this.initialOcclusionStrength = AudioDistancePlugin.CONFIG.occlusionStrength;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int totalWidth = 320;
        int totalContentHeight = 248;
        int startX = (this.width - totalWidth) / 2;
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
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int pct = (int) Math.round(this.value * 100);
                setMessage(Component.translatable("gui.vc-audio-distance.attenuation.label", pct));
            }

            @Override
            protected void applyValue() {
                AudioDistancePlugin.CONFIG.attenuationFactor = Math.round(this.value * 100.0) / 100.0;
            }
        };
        attenuationSlider.setTooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.attenuation.tooltip")));
        addRenderableWidget(attenuationSlider);
        y += 22;

        // ── 3. Reference Distance Ratio Slider (10% - 100%) ──────────────────
        double refNorm = (AudioDistancePlugin.CONFIG.openalReferenceRatio - 0.10) / 0.90;
        AbstractSliderButton refDistanceSlider = new AbstractSliderButton(
                startX, y, totalWidth, 20,
                Component.empty(),
                Math.max(0.0, Math.min(1.0, refNorm))
        ) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                double ratio = 0.10 + this.value * 0.90;
                int pct = (int) Math.round(ratio * 100);
                int blocks = (int) Math.round(ratio * AudioDistancePlugin.getServerMaxDistance());
                setMessage(Component.translatable("gui.vc-audio-distance.ref_ratio.label", pct, blocks));
            }

            @Override
            protected void applyValue() {
                double ratio = 0.10 + this.value * 0.90;
                AudioDistancePlugin.CONFIG.openalReferenceRatio = Math.round(ratio * 100.0) / 100.0;
            }
        };
        refDistanceSlider.setTooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.ref_ratio.tooltip")));
        addRenderableWidget(refDistanceSlider);
        y += 22;

        // ── 4. Minimum Volume Floor Slider (0% - 50%) ────────────────────────
        double minVolNorm = AudioDistancePlugin.CONFIG.minVolumeFraction / 0.50;
        AbstractSliderButton minVolumeSlider = new AbstractSliderButton(
                startX, y, totalWidth, 20,
                Component.empty(),
                Math.max(0.0, Math.min(1.0, minVolNorm))
        ) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int pct = (int) Math.round(this.value * 50);
                setMessage(Component.translatable("gui.vc-audio-distance.min_volume.label", pct));
            }

            @Override
            protected void applyValue() {
                double val = this.value * 0.50;
                AudioDistancePlugin.CONFIG.minVolumeFraction = Math.round(val * 100.0) / 100.0;
            }
        };
        minVolumeSlider.setTooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.min_volume.tooltip")));
        addRenderableWidget(minVolumeSlider);
        y += 22;

        // ── 5. Whisper Multiplier Slider (0.5x - 2.0x) ───────────────────────
        double whisperNorm = (AudioDistancePlugin.CONFIG.whisperMultiplier - 0.50) / 1.50;
        AbstractSliderButton whisperSlider = new AbstractSliderButton(
                startX, y, totalWidth, 20,
                Component.empty(),
                Math.max(0.0, Math.min(1.0, whisperNorm))
        ) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                double mult = 0.50 + this.value * 1.50;
                setMessage(Component.translatable("gui.vc-audio-distance.whisper_mult.label", String.format("%.2fx", mult)));
            }

            @Override
            protected void applyValue() {
                double mult = 0.50 + this.value * 1.50;
                AudioDistancePlugin.CONFIG.whisperMultiplier = Math.round(mult * 100.0) / 100.0;
            }
        };
        whisperSlider.setTooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.whisper_mult.tooltip")));
        addRenderableWidget(whisperSlider);
        y += 22;

        // ── 6. Sound Occlusion Controls ──────────────────────────────────────
        int toggleWidth = (totalWidth - 4) / 2;
        int sliderWidth = totalWidth - toggleWidth - 4;

        Button occlusionToggle = Button.builder(
                Component.translatable(
                        "gui.vc-audio-distance.occlusion.toggle",
                        AudioDistancePlugin.CONFIG.occlusionEnabled
                                ? Component.translatable("gui.vc-audio-distance.enabled").getString()
                                : Component.translatable("gui.vc-audio-distance.disabled").getString()
                ),
                btn -> {
                    AudioDistancePlugin.CONFIG.occlusionEnabled = !AudioDistancePlugin.CONFIG.occlusionEnabled;
                    refreshScreen();
                }
        )
        .bounds(startX, y, toggleWidth, 20)
        .tooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.occlusion.tooltip")))
        .build();
        addRenderableWidget(occlusionToggle);

        AbstractSliderButton occlusionSlider = new AbstractSliderButton(
                startX + toggleWidth + 4, y, sliderWidth, 20,
                Component.empty(),
                AudioDistancePlugin.CONFIG.occlusionStrength
        ) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int pct = (int) Math.round(this.value * 100);
                setMessage(Component.translatable("gui.vc-audio-distance.occlusion_strength.label", pct));
            }

            @Override
            protected void applyValue() {
                AudioDistancePlugin.CONFIG.occlusionStrength = Math.round(this.value * 100.0) / 100.0;
            }
        };
        occlusionSlider.setTooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.occlusion_strength.tooltip")));
        occlusionSlider.active = AudioDistancePlugin.CONFIG.occlusionEnabled;
        addRenderableWidget(occlusionSlider);
        y += 22;

        // ── 7. Preset Quick-Select Buttons ────────────────────────────────────
        int gap = 3;
        int presetBtnWidth = (totalWidth - (gap * 3)) / 4;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.vc-audio-distance.preset.default"),
                btn -> applyPreset(() -> AudioDistancePlugin.CONFIG.applyDefault())
        )
        .bounds(startX, y, presetBtnWidth, 18)
        .tooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.preset.default.tooltip")))
        .build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.vc-audio-distance.preset.realistic"),
                btn -> applyPreset(() -> AudioDistancePlugin.CONFIG.applyRealistic())
        )
        .bounds(startX + (presetBtnWidth + gap), y, presetBtnWidth, 18)
        .tooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.preset.realistic.tooltip")))
        .build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.vc-audio-distance.preset.high_audibility"),
                btn -> applyPreset(() -> AudioDistancePlugin.CONFIG.applyHighAudibility())
        )
        .bounds(startX + (presetBtnWidth + gap) * 2, y, presetBtnWidth, 18)
        .tooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.preset.high_audibility.tooltip")))
        .build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.vc-audio-distance.preset.atmospheric"),
                btn -> applyPreset(() -> AudioDistancePlugin.CONFIG.applyAtmospheric())
        )
        .bounds(startX + (presetBtnWidth + gap) * 3, y, presetBtnWidth, 18)
        .tooltip(Tooltip.create(Component.translatable("gui.vc-audio-distance.preset.atmospheric.tooltip")))
        .build());
        y += 22;

        // ── 8. Action Buttons (Reset, Cancel, Save) ───────────────────────────
        int actionBtnWidth = (totalWidth - (gap * 2)) / 3;

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
        AudioDistancePlugin.CONFIG.occlusionEnabled     = initialOcclusion;
        AudioDistancePlugin.CONFIG.occlusionStrength    = initialOcclusionStrength;
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    private void saveAndClose() {
        AudioDistancePlugin.CONFIG.save();
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        cancelAndClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);

        int totalWidth = 320;
        int totalContentHeight = 248;
        int startX = (this.width - totalWidth) / 2;
        int startY = Math.max(6, (this.height - totalContentHeight) / 2);

        // Screen title
        Component titleComponent = Component.translatable("gui.vc-audio-distance.title");
        guiGraphics.centeredText(this.font, titleComponent, this.width / 2, startY, 0xFFFFFF);

        // Graph and summary cards
        renderCurveGraph(guiGraphics, startX, startY + 12, totalWidth);
    }

    private void renderCurveGraph(GuiGraphicsExtractor guiGraphics, int startX, int startY, int totalWidth) {
        int graphHeight = 32;

        // Background box and subtle border
        guiGraphics.fill(startX - 1, startY - 1, startX + totalWidth + 1, startY + graphHeight + 1, 0xFF3D444D);
        guiGraphics.fill(startX, startY, startX + totalWidth, startY + graphHeight, 0xF00D1117);

        // Reference 100% volume ceiling line
        guiGraphics.fill(startX, startY, startX + totalWidth, startY + 1, 0x40FFFFFF);

        AttenuationModel model = AudioDistancePlugin.CONFIG.model;
        double rolloff = AudioDistancePlugin.CONFIG.attenuationFactor;
        double minVol  = AudioDistancePlugin.CONFIG.minVolumeFraction;
        double refRatio = AudioDistancePlugin.CONFIG.openalReferenceRatio;
        double maxDistance = AudioDistancePlugin.getServerMaxDistance();
        int maxBlocks = (int) Math.round(maxDistance);

        int refX = startX + (int) Math.round(refRatio * totalWidth);

        // Highlight 100% audibility reference distance zone
        if (refX > startX && refX < startX + totalWidth) {
            guiGraphics.fill(startX, startY, refX, startY + graphHeight, 0x1855FF55);
            guiGraphics.fill(refX, startY, refX + 1, startY + graphHeight, 0xAA55FF55);
        }

        // Draw minimum volume floor line if configured
        if (minVol > 0.0) {
            int floorY = startY + graphHeight - (int) Math.round(minVol * (graphHeight - 2));
            guiGraphics.fill(startX, floorY, startX + totalWidth, floorY + 1, 0x77FFD700);
        }

        // Draw volume curve columns across all pixels
        for (int px = 0; px < totalWidth; px++) {
            double distFraction = (double) px / (double) totalWidth;
            double gain = calculateGain(distFraction, model, rolloff, minVol, refRatio);

            int barHeight = Math.max(1, (int) Math.round(gain * (graphHeight - 2)));
            int barY = startY + graphHeight - barHeight;

            int color;
            if (distFraction <= refRatio) {
                color = 0xFF55FF55; // Solid green inside 100% reference zone
            } else if (gain > 0.65) {
                color = 0xFF88FF55; // Yellow-green
            } else if (gain > 0.35) {
                color = 0xFFFFDD44; // Yellow
            } else if (gain > 0.10) {
                color = 0xFFFF8833; // Orange
            } else {
                color = 0xFFFF4444; // Red
            }

            guiGraphics.fill(startX + px, barY, startX + px + 1, barY + 2, color);
        }

        // Graph corner labels
        guiGraphics.text(this.font, Component.literal("100%"), startX + 3, startY + 2, 0x77AAAAAA, false);
        guiGraphics.text(this.font, Component.literal("0%"), startX + 3, startY + graphHeight - 9, 0x77AAAAAA, false);
        guiGraphics.text(this.font, Component.literal(maxBlocks + "m"), startX + totalWidth - 24, startY + graphHeight - 9, 0x77AAAAAA, false);

        // ── Real-Time Acoustic Propagation Summary Card ──────────────────────
        int summaryY = startY + graphHeight + 4;
        int summaryHeight = 18;

        guiGraphics.fill(startX - 1, summaryY - 1, startX + totalWidth + 1, summaryY + summaryHeight + 1, 0xFF3D444D);
        guiGraphics.fill(startX, summaryY, startX + totalWidth, summaryY + summaryHeight, 0xF0181D24);

        int startBlock = (int) Math.round(refRatio * maxDistance);
        int finalPct = (int) Math.round(calculateGain(1.0, model, rolloff, minVol, refRatio) * 100);

        if (rolloff <= 0.001) {
            Component flatText = Component.translatable("gui.vc-audio-distance.summary.flat", maxBlocks);
            guiGraphics.text(this.font, flatText, startX + 5, summaryY + 7, 0x55FF55, false);
        } else {
            Component zoneText = Component.translatable("gui.vc-audio-distance.summary.zone100", startBlock);
            guiGraphics.text(this.font, zoneText, startX + 5, summaryY + 2, 0x55FF55, false);

            Component falloffText = Component.translatable("gui.vc-audio-distance.summary.falloff", finalPct, maxBlocks);
            guiGraphics.text(this.font, falloffText, startX + 5, summaryY + 12, 0x55FFFF, false);
        }
    }

    public static double calculateGain(double distFraction, AttenuationModel model,
                                      double rolloff, double minVol, double refRatio) {
        return AudioPhysics.calculateGain(distFraction, model, rolloff, minVol, refRatio);
    }
}
