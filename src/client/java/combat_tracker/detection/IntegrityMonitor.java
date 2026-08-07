package combat_tracker.detection;

import combat_tracker.record.SessionRecorder;
import net.minecraft.client.Minecraft;
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
    private final SlotLedger slots = new SlotLedger();

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
        slots.clear();
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
        if (slots.accountFor(newSlot, source != InputContext.Source.NONE)) {
            hotbarFlags++;
            record(Kind.HOTBAR);
        }
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
        resolve(player);
    }

    /**
     * Checks the slot at the moment an action is taken, not only at the tick
     * boundary.
     *
     * <p>This is what closes the write-act-revert hole. A cheat that writes the
     * private {@code selected} field, swings, and writes it straight back finishes
     * the tick on the slot it started on, so an end-of-tick comparison sees nothing
     * whatsoever. At the moment of the swing the slot is somewhere the ledger never
     * agreed to, and that is when this looks.</p>
     */
    public void checkSlotNow() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            resolve(player);
        }
    }

    /**
     * Compares the live slot against the ledger.
     *
     * <p>A replaced player is a new baseline, not a switch: {@code handleRespawn}
     * builds a fresh {@code LocalPlayer} on slot 0 with no setter call, so without
     * this every death from a non-zero slot would be reported.</p>
     */
    private void resolve(LocalPlayer player) {
        if (lastPlayer == null || lastPlayer.get() != player) {
            lastPlayer = new WeakReference<>(player);
            slots.reseed(player.getInventory().getSelectedSlot());
            return;
        }
        if (slots.observe(player.getInventory().getSelectedSlot())) {
            hotbarFlags++;
            record(Kind.HOTBAR);
        }
    }

    private void record(Kind kind) {
        SessionRecorder.get().recordFlag(kind, MAX_EVENTS);
        WebhookNotifier.get().onFlag(kind);
    }
}
