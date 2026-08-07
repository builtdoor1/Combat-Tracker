package combat_tracker.detection;

/**
 * Tracks which vanilla input path the client is currently executing inside.
 *
 * <p>Vanilla drives combat and hotbar selection from a very small number of
 * places. Verified against the 1.21.11 Mojang-mapped client:</p>
 *
 * <ul>
 *   <li>{@code Minecraft.startAttack()} and {@code Minecraft.startUseItem()} are
 *       referenced by exactly one class, {@code Minecraft}, and called from exactly
 *       one method, {@code handleKeybinds()}.</li>
 *   <li>{@code Inventory.setSelectedSlot(int)} is called from exactly three places
 *       on the client: {@code Minecraft.handleKeybinds()} (number keys),
 *       {@code MouseHandler.onScroll(...)} (the wheel), and
 *       {@code ClientPacketListener.handleSetHeldSlot(...)} (the server moving your
 *       hand for you).</li>
 * </ul>
 *
 * <p>So an attack, a use, or a slot change that happens while none of these are on
 * the stack did not come from the keyboard or mouse. That is the whole idea: rather
 * than guessing at timing signatures, ask where the call came from. A macro that
 * swaps your hotbar, a killaura that calls the attack method directly, or a shield
 * disabler that swaps and uses in the same tick all arrive outside these windows,
 * because the vanilla input methods are not what invoked them.</p>
 *
 * <p><b>Scope, honestly.</b> This observes the process it runs in. It sees other
 * mods acting on the game, and cheat clients that were not written with this mod in
 * mind will not be hiding from it. It cannot see a cheat that patches this class,
 * refuses to load it, or drives the real mouse from outside the game — and a
 * hardware macro clicking a physical button is indistinguishable from a hand,
 * because at that point it genuinely is ordinary input. See the README on what
 * these reports are worth.</p>
 *
 * <p>Everything here is client-thread only. {@code handleKeybinds}, {@code onScroll}
 * and packet handlers all run on the render thread, so plain fields are enough and
 * a {@code ThreadLocal} would only add cost. Depth counters rather than booleans
 * because a stuck flag is a false negative that hides real detections, and
 * {@link #resetForTick()} clears the state every tick so an exception unwinding
 * past a RETURN injector cannot leave a window pinned open.</p>
 */
public final class InputContext {

    /** Where a hotbar change came from. */
    public enum Source {
        /** Number keys, inside {@code handleKeybinds}. */
        KEYBIND,
        /** The scroll wheel, inside {@code MouseHandler.onScroll}. */
        SCROLL,
        /** The server told the client to change slot. Always legitimate. */
        SERVER,
        /** None of the vanilla input paths were executing. */
        NONE
    }

    private static int keybindDepth;
    private static int scrollDepth;
    private static int serverSlotDepth;
    private static int physicalDepth;
    private static int housekeepingDepth;

    private InputContext() {
    }

    public static void enterKeybinds() {
        keybindDepth++;
    }

    public static void exitKeybinds() {
        if (keybindDepth > 0) {
            keybindDepth--;
        }
    }

    public static void enterScroll() {
        scrollDepth++;
    }

    public static void exitScroll() {
        if (scrollDepth > 0) {
            scrollDepth--;
        }
    }

    public static void enterServerSlot() {
        serverSlotDepth++;
    }

    public static void exitServerSlot() {
        if (serverSlotDepth > 0) {
            serverSlotDepth--;
        }
    }

    /**
     * A real GLFW callback is on the stack: {@code KeyboardHandler.keyPress} or
     * {@code MouseHandler.onButton}. These are the only two places a physical
     * keystroke or mouse button enters the game.
     */
    public static void enterPhysicalInput() {
        physicalDepth++;
    }

    public static void exitPhysicalInput() {
        if (physicalDepth > 0) {
            physicalDepth--;
        }
    }

    /**
     * Vanilla housekeeping that legitimately rewrites keybind state without anyone
     * touching the keyboard: {@code Minecraft.setScreen} releasing every key,
     * {@code MouseHandler.grabMouse} restoring them, and the toggle-key restore
     * paths. Verified callers as of 1.21.11.
     */
    public static void enterHousekeeping() {
        housekeepingDepth++;
    }

    public static void exitHousekeeping() {
        if (housekeepingDepth > 0) {
            housekeepingDepth--;
        }
    }

    /**
     * True when a keybind's state may legitimately change right now.
     *
     * <p>{@code KeyMapping.set} routes through {@code setDown} for every real key
     * press, so without the physical window every genuine keystroke would be
     * reported as synthetic.</p>
     */
    public static boolean trustedInput() {
        return physicalDepth > 0 || housekeepingDepth > 0;
    }

    /** True while the client is inside {@code Minecraft.handleKeybinds()}. */
    public static boolean inKeybinds() {
        return keybindDepth > 0;
    }

    /** The vanilla path responsible for a hotbar change happening right now. */
    public static Source currentSource() {
        if (keybindDepth > 0) {
            return Source.KEYBIND;
        }
        if (scrollDepth > 0) {
            return Source.SCROLL;
        }
        return serverSlotDepth > 0 ? Source.SERVER : Source.NONE;
    }

    /**
     * Clears every window at the tick boundary.
     *
     * <p>None of these methods span a tick in vanilla, so anything still open here
     * is leftover state — most likely an exception that unwound past the injector
     * meant to close it. Clearing beats leaving a window open, which would silently
     * excuse every subsequent detection.</p>
     */
    public static void resetForTick() {
        keybindDepth = 0;
        scrollDepth = 0;
        serverSlotDepth = 0;
        physicalDepth = 0;
        housekeepingDepth = 0;
    }
}
