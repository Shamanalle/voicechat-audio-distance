package com.kasper.vcdistance;

import java.util.function.DoubleUnaryOperator;

/**
 * A voice behind a wall reaches the listener two ways at once: straight through the wall, muffled,
 * and round it through an opening, further but clearer. Both add up, so the voice never flips from
 * one to the other: as the way round gets clearer it takes over the sound, the distance the volume
 * follows and, through {@link #pathShare}, the direction the voice seems to come from.
 *
 * @param pathShare share of the sound that comes round, 0 - 1
 * @param direct    straight-line distance, in blocks
 * @param distance  distance the volume curve should use, in blocks
 * @param muffle    muffle of the mix, 0 - 1
 * @param lossDb    loss of the mix on top of the volume curve at {@code distance}, in dB
 */
public record SoundBlend(double pathShare, double direct, double distance, double muffle, double lossDb) {

    /**
     * @param direct        straight-line distance, in blocks
     * @param wall          wall thickness on the straight line, in stone blocks
     * @param pathLength    length of the way round, in blocks, or NaN when there is none
     * @param pathThickness muffling of the way round, in stone blocks
     * @param strength      the player's wall strength, 0 - 1
     * @param curve         volume curve: gain at a distance in blocks
     */
    public static SoundBlend of(double direct, double wall, double pathLength, double pathThickness,
                                double strength, DoubleUnaryOperator curve) {
        double directMuffle = OcclusionModel.muffle(wall, strength);
        double directLoss = OcclusionModel.lossDb(wall, strength);
        if (Double.isNaN(pathLength) || Double.isNaN(pathThickness) || !(direct >= 0.0)) {
            return new SoundBlend(0.0, direct, direct, directMuffle, directLoss);
        }
        double length = Math.max(direct, pathLength);
        double pathMuffle = OcclusionModel.muffle(pathThickness, strength);
        double pathLoss = OcclusionModel.lossDb(pathThickness, strength);
        // A dull voice sounds quieter than a clear one at the same level
        double wallGain = OcclusionModel.dbToGain(-directLoss) * (1.0 - 0.5 * directMuffle);
        double roundGain = OcclusionModel.dbToGain(-pathLoss) * (1.0 - 0.5 * pathMuffle);
        double a = curve.applyAsDouble(direct) * wallGain;
        double b = curve.applyAsDouble(length) * roundGain;
        double share;
        if (a * a + b * b > 1e-12) {
            share = b * b / (a * a + b * b);
        } else {
            share = roundGain * roundGain / (wallGain * wallGain + roundGain * roundGain);
        }
        double distance = direct + (length - direct) * share;
        double muffle = directMuffle + (pathMuffle - directMuffle) * share;

        // Loud as both ways together, after the curve has already turned the voice down to its distance
        double total = Math.sqrt(sq(curve.applyAsDouble(direct) * OcclusionModel.dbToGain(-directLoss))
                + sq(curve.applyAsDouble(length) * OcclusionModel.dbToGain(-pathLoss)));
        double atDistance = curve.applyAsDouble(distance);
        double loss;
        if (atDistance > 1e-6 && total > 1e-9) {
            loss = Math.max(0.0, -20.0 * Math.log10(Math.min(1.0, total / atDistance)));
        } else {
            loss = directLoss + (pathLoss - directLoss) * share;
        }
        return new SoundBlend(share, direct, distance, muffle, Math.min(OcclusionModel.MAX_LOSS_DB, loss));
    }

    /** How much further the voice sounds than it is, in blocks. */
    public double extraDistance() {
        return Math.max(0.0, distance - direct);
    }

    /** {@code true} when most of the voice comes round the wall. */
    public boolean mostlyRound() {
        return pathShare > 0.5;
    }

    private static double sq(double v) {
        return v * v;
    }
}
