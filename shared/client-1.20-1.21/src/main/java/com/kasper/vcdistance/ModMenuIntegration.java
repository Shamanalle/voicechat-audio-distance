package com.kasper.vcdistance;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu integration for VoiceChat Audio Distance Addon.
 * Allows accessing settings directly from Mod Menu's mod list.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AudioDistanceScreen::new;
    }
}
