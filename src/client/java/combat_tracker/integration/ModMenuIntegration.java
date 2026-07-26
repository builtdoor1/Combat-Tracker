package combat_tracker.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import combat_tracker.screen.ConfigScreen;

/**
 * Registers the config screen with Mod Menu. This class is only ever loaded when
 * Mod Menu is present (it queries the "modmenu" entrypoint), so the mod runs
 * fine without Mod Menu installed.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
