package combat_tracker.detection;

import combat_tracker.CombatTrackerClient;
import combat_tracker.record.SessionRecorder;
import combat_tracker.stats.ComboStatsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Tracks combos of sprint hits against another player and records the interval
 * between consecutive hits.
 *
 * <p>A combo is a chain of >= 2 sprint hits on the <em>same</em> player with no
 * self-damage in between and no gap longer than {@code maxComboGapMs}. Only
 * attacks made while sprinting count (true sprint-reset combo).</p>
 */
public class ComboTracker {
    private static final ComboTracker INSTANCE = new ComboTracker();

    public static ComboTracker get() {
        return INSTANCE;
    }

    /**
     * A combo ends if no hit lands within this gap. Derived from the sword attack
     * cooldown (~0.625s to fully recharge) plus a small grace window: if you don't
     * swing shortly after your sword is charged, the combo is over. Hardcoded so a
     * stale config.json can't override it.
     */
    private static final long COMBO_GAP_NANO = 700L * 1_000_000L;

    private int currentTargetId = -1;
    private long lastHitNano = 0L;
    private int comboHits = 0;
    private int prevHurtTime = 0;

    /** Called from the attack mixin whenever the local player attacks an entity. */
    public void onAttack(Entity target) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null || target == self || !(target instanceof Player)) {
            return;
        }
        if (!self.isSprinting()) {
            return; // sprint hits only
        }

        // Time from the mouse press, not from the tick this attack was processed
        // on. Attack processing is tick-bound, so tick timing rounds every interval
        // to a 50ms boundary and the variation between a human's clicks, which is
        // the whole signal against an autoclicker, disappears before it is measured.
        // 0 means no click was behind this attack, so fall back to the tick.
        long clickNano = CombatTrackerClient.clickNano;
        long nano = clickNano != 0L ? clickNano : System.nanoTime();
        long maxGapNano = COMBO_GAP_NANO;

        if (target.getId() == currentTargetId && comboHits >= 1 && (nano - lastHitNano) <= maxGapNano) {
            long intervalMs = (nano - lastHitNano) / 1_000_000L;
            comboHits++;
            boolean newCombo = comboHits == 2; // the first interval means a real combo just formed
            ComboStatsTracker.get().record(intervalMs, newCombo);
            ComboStatsTracker.get().save();
            SessionRecorder.get().recordCombo(intervalMs, newCombo);
        } else {
            currentTargetId = target.getId();
            comboHits = 1;
        }
        lastHitNano = nano;
    }

    /** Called every client tick to break combos on self-damage or a long gap. */
    public void tick(LocalPlayer player) {
        if (prevHurtTime == 0 && player.hurtTime > 0) {
            breakCombo();
        }
        prevHurtTime = player.hurtTime;

        if (currentTargetId != -1) {
            long maxGapNano = COMBO_GAP_NANO;
            if (System.nanoTime() - lastHitNano > maxGapNano) {
                breakCombo();
            }
        }
    }

    /** Live hit count of the current combo (0 when not comboing). */
    public int currentCombo() {
        return comboHits;
    }

    private void breakCombo() {
        currentTargetId = -1;
        comboHits = 0;
    }
}
