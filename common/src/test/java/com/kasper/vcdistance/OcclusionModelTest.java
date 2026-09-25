package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OcclusionModelTest {

    @Test
    @DisplayName("No wall or zero strength means no effect")
    void noEffect() {
        assertEquals(0.0, OcclusionModel.muffle(0.0, 1.0), 1e-9);
        assertEquals(0.0, OcclusionModel.lossDb(0.0, 1.0), 1e-9);
        assertEquals(0.0, OcclusionModel.muffle(3.0, 0.0), 1e-9);
        assertEquals(0.0, OcclusionModel.lossDb(3.0, 0.0), 1e-9);
        assertEquals(0.0, OcclusionModel.muffle(Double.NaN, 1.0), 1e-9);
    }

    @Test
    @DisplayName("Thicker walls are always more muffled and quieter, but saturate")
    void monotonicAndSaturating() {
        double prevMuffle = 0.0;
        double prevLoss = 0.0;
        for (double t = 0.25; t <= 20.0; t += 0.25) {
            double m = OcclusionModel.muffle(t, 0.6);
            double l = OcclusionModel.lossDb(t, 0.6);
            assertTrue(m > prevMuffle && m < 1.0);
            assertTrue(l > prevLoss && l < OcclusionModel.MAX_LOSS_DB);
            prevMuffle = m;
            prevLoss = l;
        }
    }

    @Test
    @DisplayName("Default strength: one stone wall is a clear, not total, muffle")
    void defaultWall() {
        double s = DistanceConfig.DEFAULT_OCCLUSION_STRENGTH;
        double cutoff = OcclusionModel.cutoffHz(OcclusionModel.muffle(1.0, s));
        double loss = OcclusionModel.lossDb(1.0, s);
        assertTrue(cutoff > 1500 && cutoff < 4000, "cutoff " + cutoff);
        assertTrue(loss > 5 && loss < 12, "loss " + loss);
    }

    @Test
    @DisplayName("Cutoff sweeps from open to closed")
    void cutoffRange() {
        assertEquals(OcclusionModel.OPEN_CUTOFF_HZ, OcclusionModel.cutoffHz(0.0), 1e-6);
        assertEquals(OcclusionModel.MIN_CUTOFF_HZ, OcclusionModel.cutoffHz(1.0), 1e-6);
        assertEquals(OcclusionModel.MIN_CUTOFF_HZ, OcclusionModel.cutoffHz(5.0), 1e-6);
    }
}
