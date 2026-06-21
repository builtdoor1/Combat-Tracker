package jump_reset_tracker;

import jump_reset_tracker.config.JrtConfig;
import jump_reset_tracker.detection.AttemptCorrelator;
import jump_reset_tracker.detection.JumpDetector;
import jump_reset_tracker.stats.StatsTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. Wires up config/stats loading, the HUD-toggle keybind, and
 * the per-tick jump detection. Hit detection and HUD rendering happen via mixins.
 */
public class JumpResetTrackerClient implements ClientModInitializer {
    public static final String MOD_ID = "jump_reset_tracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final JumpDetector jumpDetector = new JumpDetector();
    private KeyMapping toggleHudKey;

    @Override
    public void onInitializeClient() {
        // Load persisted config + stats up front.
        JrtConfig.get();
        StatsTracker.get();

        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.jump_reset_tracker.toggle_hud",
                GLFW.GLFW_KEY_J,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);

        LOGGER.info("Jump Reset Tracker initialized (client-side, observational only)");
    }

    private void onEndTick(Minecraft client) {
        while (toggleHudKey.consumeClick()) {
            JrtConfig config = JrtConfig.get();
            config.hudEnabled = !config.hudEnabled;
            JrtConfig.save();
        }

        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        if (jumpDetector.detectJump(player, client.options)) {
            AttemptCorrelator.get().onJump(System.currentTimeMillis());
        }
    }
}
