package combat_tracker;

import combat_tracker.config.CtConfig;
import combat_tracker.detection.ClickTimestamps;
import combat_tracker.detection.ComboTracker;
import combat_tracker.detection.InputContext;
import combat_tracker.detection.IntegrityMonitor;
import combat_tracker.detection.JumpResetTracker;
import combat_tracker.detection.LatencyEstimator;
import combat_tracker.detection.WebhookNotifier;
import combat_tracker.record.SessionRecorder;
import combat_tracker.stats.ComboStatsTracker;
import combat_tracker.stats.StatsTracker;
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
public class CombatTrackerClient implements ClientModInitializer {
    public static final String MOD_ID = "combat_tracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Set by {@code LivingEntityMixin} to {@code System.nanoTime()} when the local
     * player actually jumps ({@code jumpFromGround}). Read and cleared by the
     * tracker each tick via {@link #consumeJumpNano()}. 0 means "no jump".
     */
    public static volatile long jumpNano = 0L;

    /**
     * Set by {@code LivingEntityMixin} to {@code System.nanoTime()} when the local
     * player registers a hit ({@code handleDamageEvent}). Gives a precise hit time
     * instead of one quantized to the end of the tick. 0 means "none yet".
     */
    public static volatile long hitNano = 0L;

    /**
     * Time of the mouse press behind the attack currently being processed, set by
     * {@code MinecraftMixin} at the top of {@code startAttack}. 0 means the attack
     * had no click behind it, in which case timing falls back to the tick.
     *
     * <p>Combo intervals are measured from this rather than from the tick the
     * attack was processed on. See {@link combat_tracker.detection.ClickTimestamps}.</p>
     */
    public static volatile long clickNano = 0L;

    /** Returns the pending jump nano-timestamp (or 0) and clears it. */
    public static long consumeJumpNano() {
        long v = jumpNano;
        jumpNano = 0L;
        return v;
    }

    private final JumpResetTracker tracker = new JumpResetTracker();
    private KeyMapping toggleHudKey;
    private KeyMapping toggleRecordKey;

    @Override
    public void onInitializeClient() {
        // Load persisted state up front.
        CtConfig.get();
        StatsTracker.get();
        ComboStatsTracker.get();

        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.combat_tracker.toggle_hud",
                GLFW.GLFW_KEY_J,
                "key.categories.misc"
        ));
        toggleRecordKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.combat_tracker.toggle_record",
                GLFW.GLFW_KEY_UNKNOWN, // unbound by default
                "key.categories.misc"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);

        LOGGER.info("Combat Tracker initialized (client-side, observational only)");
    }

    private void onEndTick(Minecraft client) {
        while (toggleHudKey.consumeClick()) {
            CtConfig config = CtConfig.get();
            config.hudEnabled = !config.hudEnabled;
            CtConfig.save();
        }
        while (toggleRecordKey.consumeClick()) {
            SessionRecorder.get().toggle();
        }

        LocalPlayer player = client.player;
        if (player == null) {
            // Disconnected or in a menu with no world: drop any click that never
            // became an attack, so it can't pair with a swing after reconnecting.
            ClickTimestamps.clear();
        }
        if (player != null && client.level != null) {
            tracker.tick(client);
            ComboTracker.get().tick(player);
            // Resolves any hotbar change seen this tick, including one that never
            // went through setSelectedSlot at all.
            IntegrityMonitor.get().tick(player);
            WebhookNotifier.get().tick();
            // Ping and identity are both captured while playing rather than at the
            // moment the report is written, when the player may already be gone.
            SessionRecorder.get().samplePing(LatencyEstimator.get().currentMs());
            SessionRecorder.get().noteIdentity(player.getName().getString(), player.getUUID().toString());
        }

        // Last thing in the tick: none of the vanilla input windows span a tick, so
        // anything still open is leftover state that would otherwise excuse the next
        // detection. See InputContext.resetForTick().
        InputContext.resetForTick();
    }
}
