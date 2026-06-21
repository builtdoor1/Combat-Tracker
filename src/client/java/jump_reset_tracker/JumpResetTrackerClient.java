package jump_reset_tracker;

import jump_reset_tracker.config.JrtConfig;
import jump_reset_tracker.detection.ComboTracker;
import jump_reset_tracker.detection.JumpResetTracker;
import jump_reset_tracker.record.SessionRecorder;
import jump_reset_tracker.stats.ComboStatsTracker;
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
 * Client entrypoint. Loads config/stats, registers keybinds, and drives the
 * per-tick detection. Jump impulse capture, HUD rendering and outgoing-attack
 * detection happen via mixins.
 */
public class JumpResetTrackerClient implements ClientModInitializer {
    public static final String MOD_ID = "jump_reset_tracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Pre-physics vertical velocity captured by {@code LocalPlayerMixin} at the
     * HEAD of {@code move()}; read by the tracker in END_CLIENT_TICK.
     */
    public static volatile double preMoveVelocityY = 0.0;

    private final JumpResetTracker tracker = new JumpResetTracker();
    private KeyMapping toggleHudKey;
    private KeyMapping toggleRecordKey;

    @Override
    public void onInitializeClient() {
        // Load persisted state up front.
        JrtConfig.get();
        StatsTracker.get();
        ComboStatsTracker.get();

        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.jump_reset_tracker.toggle_hud",
                GLFW.GLFW_KEY_J,
                KeyMapping.Category.MISC
        ));
        toggleRecordKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.jump_reset_tracker.toggle_record",
                GLFW.GLFW_KEY_UNKNOWN, // unbound by default
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);

        LOGGER.info("Combat Tracker initialized (client-side, observational only)");
    }

    private void onEndTick(Minecraft client) {
        while (toggleHudKey.consumeClick()) {
            JrtConfig config = JrtConfig.get();
            config.hudEnabled = !config.hudEnabled;
            JrtConfig.save();
        }
        while (toggleRecordKey.consumeClick()) {
            SessionRecorder.get().toggle();
        }

        LocalPlayer player = client.player;
        if (player != null && client.level != null) {
            tracker.tick(client);
            ComboTracker.get().tick(player);
        }
    }
}
