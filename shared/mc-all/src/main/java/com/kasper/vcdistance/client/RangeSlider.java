package com.kasper.vcdistance.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * Slider over an arbitrary range with snapping, bound to a getter/setter pair.
 */
public class RangeSlider extends AbstractSliderButton {

    private final double min;
    private final double max;
    private final double step;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final DoubleFunction<Component> label;

    public RangeSlider(int x, int y, int width, int height, double min, double max, double step,
                       DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<Component> label) {
        super(x, y, width, height, Component.empty(), toPosition(getter.getAsDouble(), min, max));
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.label = label;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        if (label != null) {
            setMessage(label.apply(current()));
        }
    }

    @Override
    protected void applyValue() {
        setter.accept(current());
    }

    /** Re-reads the bound value, e.g. after a preset changed it. */
    public void sync() {
        this.value = toPosition(getter.getAsDouble(), min, max);
        updateMessage();
    }

    private double current() {
        double v = min + this.value * (max - min);
        if (step > 0.0) {
            v = Math.round(v / step) * step;
        }
        return Math.max(min, Math.min(max, v));
    }

    private static double toPosition(double v, double min, double max) {
        if (max <= min) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (v - min) / (max - min)));
    }
}
