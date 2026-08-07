package combat_tracker.detection;

import combat_tracker.record.SessionRecorder;
import net.minecraft.client.player.LocalPlayer;

import java.lang.ref.WeakReference;

/**
 * Watches for combat actions that no vanilla input path asked for.
 *
 * <p>Three signals, all of them call-site provenance rather than timing. See
 * {@link InputContext} for why the vanilla call graph makes this a clean test.</p>
 *
 * <ul>
 *   <li><b>Hotbar</b> — the selected slot changed with none of the three legitimate
 *       paths on the stack. Catches auto-swap macros, including the swap half of a
 *       shield disabler and anything that switches to a weapon for you.</li>
 *   <li><b>Use</b> — {@code startUseItem()} ran outside {@code handleKeybinds()}.</li>
 *   <li><b>Attack</b> — {@code startAttack()} ran outside {@code handleKeybinds()}.</li>
 * </ul>
 *
 * <p>The hotbar check runs twice over. The mixin on {@code setSelectedSlot} attributes
 * a change the moment it happens, which is precise and names the source. The tick
 * sweep then compares the slot against what was last seen, which catches a change
 * that never went through the setter at all — a cheat writing the private
 * {@code selected} field, whether by reflection or its own mixin, moves the slot
 * without the setter ever running. Neither check alone covers both.</p>
 *
 * <p><b>A flag is not a conviction.</b> Any mod that legitimately swaps your hotbar
 * or triggers a use — an auto-tool, a keybind helper, a fishing bot — is doing the
 * same observable thing as a cheat, and this cannot tell them apart. That is why the
 * report shows counts and times rather than a verdict.</p>
 */
public final class IntegrityMonitor {
    /**
     * Kept small deliberately. A cheat that fires every tick would otherwise grow
     * this list without limit for a session that is already comprehensively flagged
     * by the first hundred entries. Counts stay exact past the cap.
     */
    private static final int MAX_EVENTS = 200;

    /** What tripped. Ordinal is written into the share link, so do not reorder. */
    public enum Kind {
        HOTBAR,
        USE,
        ATTACK,
        /** A keybind was pressed or held by code rather than by a key. */
        KEYBIND
    }

    private static final IntegrityMonitor INSTANCE = new IntegrityMonitor();

    public static IntegrityMonitor get() {
        return INSTANCE;
    }

    /**
     * The slot as last accounted for, updated by the setter and by the tick sweep.
     *
     * <p>Kept in step with {@code setSelectedSlot} rather than sampled once a tick,
     * so that a swap and an immediate swap-back inside one tick still leaves a trace.
     * Comparing only at tick boundaries would see the slot end where it started and
     * conclude nothing happened — which is exactly the shape of an auto-disable that
     * switches to an axe, hits, and switches back.</p>
     */
    private int lastSlot = -1;

    /** Weak so a stale player can never be what keeps a world in memory. */
    private WeakReference<LocalPlayer> lastPlayer;

    private int hotbarFlags;
    private int useFlags;
    private int attackFlags;
    private int keybindFlags;

    private IntegrityMonitor() {
    }

    public int hotbarFlags() {
        return hotbarFlags;
    }

    public int useFlags() {
        return useFlags;
    }

    public int attackFlags() {
        return attackFlags;
    }

    public int keybindFlags() {
        return keybindFlags;
    }

    public int totalFlags() {
        return hotbarFlags + useFlags + attackFlags + keybindFlags;
    }

    /** Clears counters. Used by the stats reset in settings. */
    public void reset() {
        hotbarFlags = 0;
        useFlags = 0;
        attackFlags = 0;
        keybindFlags = 0;
        lastSlot = -1;
        lastPlayer = null;
    }

    /**
     * A keybind's pressed state changed with no physical event behind it.
     *
     * <p>Closes the gap that the other three checks leave wide open: a cheat that
     * calls {@code KeyMapping.setDown(true)} or {@code KeyMapping.click(...)} instead
     * of invoking the action directly gets vanilla's own {@code handleKeybinds} to do
     * the work, so the attack or the swap arrives from inside the trusted window and
     * looks exactly like a hand. Watching the keybind state itself is a layer below
     * where that trick operates.</p>
     */
    public void onSyntheticKeybind() {
        keybindFlags++;
        record(Kind.KEYBIND);
    }

    /**
     * Called from the {@code Inventory.setSelectedSlot} mixin for the local player.
     *
     * <p>Judged here and now rather than deferred to the tick. Deferring lost two
     * cases: a switch and a switch-back inside one tick cancelled out to no visible
     * change, and a legitimate scroll landing in the same tick as a cheat's switch
     * overwrote the recorded source and excused it. Every setter call is its own
     * event, so every one gets its own verdict.</p>
     */
    public void noteSetterCall(int newSlot, InputContext.Source source) {
        // A "switch" to the slot already held moves nothing. Counting it would add
        // noise proportional to how often something re-asserts the current slot.
        if (newSlot == lastSlot) {
            return;
        }
        if (source == InputContext.Source.NONE) {
            hotbarFlags++;
            record(Kind.HOTBAR);
        }
        // Accounted for either way: the tick sweep only needs to catch changes that
        // never came through here at all.
        lastSlot = newSlot;
    }

    /**
     * An outbound held-slot packet disagreeing with the slot this client is on.
     *
     * <p>Counted as a hotbar event because that is what it is — the hand changed
     * without anyone moving it — even though the client-side slot never moved and
     * none of the other hotbar checks can see it. See
     * {@code ClientCommonPacketListenerImplMixin}.</p>
     */
    public void onSilentSlotPacket() {
        hotbarFlags++;
        record(Kind.HOTBAR);
    }

    /** Called from the {@code startUseItem} mixin. */
    public void onUseItem() {
        if (!InputContext.inKeybinds()) {
            useFlags++;
            record(Kind.USE);
        }
    }

    /** Called from the {@code startAttack} mixin. */
    public void onAttack() {
        if (!InputContext.inKeybinds()) {
            attackFlags++;
            record(Kind.ATTACK);
        }
    }

    /**
     * End-of-tick sweep. Resolves any hotbar change seen since the last tick.
     *
     * <p>A first observation only seeds {@link #lastSlot}: joining a world, or the
     * server placing you on a slot before this class has ever looked, is not a
     * detection.</p>
     */
    public void tick(LocalPlayer player) {
        int slot = player.getInventory().getSelectedSlot();

        // A respawn does not move the slot — it replaces the player. handleRespawn
        // builds a brand-new LocalPlayer, so the inventory is new and selected is 0,
        // and nothing calls the setter on the way. Without this the first tick after
        // every death that happened on a non-zero slot reports a switch that never
        // occurred, which in a PvP session means one bogus flag per death. Dimension
        // changes and joining a world take the same path.
        if (lastPlayer == null || lastPlayer.get() != player) {
            lastPlayer = new WeakReference<>(player);
            lastSlot = slot;
            return;
        }

        if (lastSlot == -1) {
            lastSlot = slot;
            return;
        }

        // Every route through setSelectedSlot has already updated lastSlot, so a
        // difference here means the private field was written behind the setter's
        // back — reflection, an access widener, or another mixin. Vanilla never
        // does that.
        if (slot != lastSlot) {
            hotbarFlags++;
            record(Kind.HOTBAR);
            lastSlot = slot;
        }
    }

    private void record(Kind kind) {
        SessionRecorder.get().recordFlag(kind, MAX_EVENTS);
        WebhookNotifier.get().onFlag(kind);
    }
}
