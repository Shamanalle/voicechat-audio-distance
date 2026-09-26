package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ProfileCodeTest {

    private static DistanceConfig config() {
        return new DistanceConfig(Path.of("unused.properties"));
    }

    @Test
    @DisplayName("A profile survives the trip through a code, and the code is short enough to paste")
    void roundTrip() {
        DistanceConfig a = config();
        Preset.ATMOSPHERIC.apply(a, 48);
        a.setMaterialWeight(AcousticMaterial.METAL, 2.25);
        a.setReverbEnabled(false);
        a.setHudScale(1.4); // interface settings are not part of a profile
        String code = ProfileCode.encode(a);
        assertTrue(code.startsWith(ProfileCode.PREFIX));
        assertTrue(code.length() < 600, "code length " + code.length());
        assertTrue(code.matches("VP1:[A-Za-z0-9_-]+"), code);

        DistanceConfig b = config();
        assertTrue(ProfileCode.decode("  " + code.substring(0, 30) + "\n" + code.substring(30) + " ", b));
        assertEquals(AttenuationModel.EXPONENTIAL, b.getModel());
        assertEquals(a.getOpenalReferenceRatio(), b.getOpenalReferenceRatio(), 1e-4);
        assertEquals(2.25, b.getMaterialWeight(AcousticMaterial.METAL), 1e-4);
        assertFalse(b.isReverbEnabled());
        assertEquals(DistanceConfig.DEFAULT_HUD_SCALE, b.getHudScale(), 1e-9);
        assertTrue(Preset.ATMOSPHERIC.matches(b, 48));
    }

    @Test
    @DisplayName("Anything that is not a code is refused and changes nothing")
    void rejectsGarbage() {
        DistanceConfig c = config();
        c.setAttenuationFactor(0.42);
        for (String bad : new String[]{null, "", "hello", "VP1:", "VP1:!!!!", "VP2:abc", "VP1:AAAA",
                ProfileCode.encode(c).substring(0, 20)}) {
            assertFalse(ProfileCode.decode(bad, c), String.valueOf(bad));
            assertFalse(ProfileCode.isValid(bad));
        }
        assertEquals(0.42, c.getAttenuationFactor(), 1e-9);
    }

    @Test
    @DisplayName("The load meter averages milliseconds per tick and flags a busy client")
    void perfMeter() {
        PerfMeter m = new PerfMeter();
        for (int i = 0; i < 400; i++) {
            m.add(1_000_000); // 1 ms
            m.add(2_000_000); // + 2 ms in the same tick
            m.endTick();
        }
        assertEquals(3.0, m.averageMs(), 0.01);
        assertTrue(m.isBusy());
        for (int i = 0; i < 400; i++) {
            m.endTick();
        }
        assertEquals(0.0, m.averageMs(), 0.01);
        assertFalse(m.isBusy());
    }
}
