package combat_tracker.detection;

/**
 * Tracks what the selected hotbar slot is supposed to be, and reports when it
 * moves without anything accounting for the move.
 *
 * <p>Deliberately free of any Minecraft import. This is the part of the hotbar
 * check that has been wrong twice — once masking a cheat's switch behind a
 * legitimate scroll in the same tick, once reporting a switch on every respawn —
 * so it is separated out to be tested directly rather than reasoned about.</p>
 *
 * <p>Two ways the slot can move, and the ledger has to see both:</p>
 *
 * <ul>
 *   <li>Through {@code Inventory.setSelectedSlot}, which is every vanilla path and
 *       most cheats. {@link #accountFor} handles these and knows whether an input
 *       path was responsible.</li>
 *   <li>By writing the private {@code selected} field directly, which no vanilla
 *       code does and the setter never sees. {@link #observe} catches these by
 *       comparing the live value against the ledger.</li>
 * </ul>
 *
 * <p>{@link #observe} is the reason this is not simply a tick-boundary comparison.
 * A cheat that writes the field, attacks, and writes it back within one tick leaves
 * the slot exactly where it started, so sampling once per tick sees nothing at all.
 * Observing at the moment of the attack catches the discrepancy while it is live.</p>
 */
public final class SlotLedger {
    private int last = -1;
    private boolean seeded;

    /**
     * Adopt a slot without judging it.
     *
     * <p>Used on first sight and whenever the player object is replaced. A respawn
     * builds a new player holding slot 0 with no setter call anywhere, which is a
     * new baseline rather than a switch.</p>
     */
    public void reseed(int slot) {
        last = slot;
        seeded = true;
    }

    public boolean isSeeded() {
        return seeded;
    }

    public int last() {
        return last;
    }

    /** Forgets everything, so the next observation reseeds instead of flagging. */
    public void clear() {
        last = -1;
        seeded = false;
    }

    /**
     * A call to {@code setSelectedSlot}.
     *
     * @param attributed whether a vanilla input path was responsible
     * @return true if this change should be flagged
     */
    public boolean accountFor(int newSlot, boolean attributed) {
        if (!seeded) {
            reseed(newSlot);
            return false;
        }
        if (newSlot == last) {
            // Re-asserting the current slot moves nothing, so there is nothing to
            // report however it arrived.
            return false;
        }
        last = newSlot;
        return !attributed;
    }

    /**
     * A direct look at the live slot.
     *
     * @return true if it moved without any setter call accounting for it
     */
    public boolean observe(int slot) {
        if (!seeded) {
            reseed(slot);
            return false;
        }
        if (slot == last) {
            return false;
        }
        last = slot;
        return true;
    }
}
