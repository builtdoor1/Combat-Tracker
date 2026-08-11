package combat_tracker.detection;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * The keybinds the synthetic-press check actually cares about.
 *
 * <p>The check exists to close one specific bypass. A cheat that calls
 * {@code startAttack()} or {@code setSelectedSlot()} directly is caught by the
 * other three checks, so the way around them is to not call the action at all:
 * press the <em>keybind</em> instead and let vanilla's own {@code handleKeybinds}
 * perform the action a tick later, from inside the trusted window where it looks
 * exactly like a hand.</p>
 *
 * <p>That bypass only works through the handful of binds vanilla consults to drive
 * something this mod measures: attack, use, the nine hotbar slots, the offhand
 * swap, and jump — jump-reset timing being one of the four measurements.</p>
 *
 * <p><b>Why the rest are ignored.</b> Watching every keybind was the original
 * design and it does not survive contact with a real client. Sprint, sneak and the
 * movement keys are held down by software constantly and legitimately: vanilla's
 * own toggle sprint and toggle sneak, third-party toggle mods, controller support
 * that translates a stick into key presses, accessibility software that latches a
 * key. One innocent session on a public server produced 116 flags and not one of
 * them was a cheat. A detector that fires that often on ordinary play is not a
 * quiet detector with a few false positives — it is noise deep enough to bury the
 * flags that matter, which is worse than not looking.</p>
 *
 * <p>Nothing is lost by narrowing. Pressing sprint by script does not fake a
 * jump reset, a reach measurement, a combo interval or an aim placement, so a
 * synthetic sprint was never evidence about the things this mod reports.</p>
 *
 * <p>Comparison is by object identity against the live {@link Options} fields
 * rather than by name, because only the exact mapping objects vanilla reads can
 * drive a vanilla action. A mod that makes its own {@code KeyMapping} called
 * {@code key.attack} and presses it does not attack anyone.</p>
 */
public final class WatchedKeys {

    private WatchedKeys() {
    }

    /** True if vanilla consults this mapping to drive something this mod measures. */
    public static boolean watched(KeyMapping mapping) {
        if (mapping == null) {
            return false;
        }
        Options o = options();
        if (o == null) {
            // Before options exist there is no game to cheat at, and no mapping can
            // be one of vanilla's. Saying "not watched" keeps startup silent.
            return false;
        }
        if (mapping == o.keyAttack || mapping == o.keyUse
                || mapping == o.keyJump || mapping == o.keySwapOffhand) {
            return true;
        }
        for (KeyMapping slot : o.keyHotbarSlots) {
            if (mapping == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * The same question for the static {@code KeyMapping.click(Key)} path, which
     * names a physical key rather than a mapping and bumps the click count of every
     * mapping bound to it.
     *
     * <p>Matched on {@code saveString()}, which is the bound key's name, so a
     * rebound attack key is still followed. Unbound mappings are skipped: they all
     * report {@code key.keyboard.unknown} and would otherwise all match each
     * other.</p>
     */
    public static boolean boundToWatched(InputConstants.Key key) {
        if (key == null) {
            return false;
        }
        Options o = options();
        if (o == null) {
            return false;
        }
        String name = key.getName();
        if (matches(o.keyAttack, name) || matches(o.keyUse, name)
                || matches(o.keyJump, name) || matches(o.keySwapOffhand, name)) {
            return true;
        }
        for (KeyMapping slot : o.keyHotbarSlots) {
            if (matches(slot, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(KeyMapping mapping, String keyName) {
        return mapping != null && !mapping.isUnbound() && mapping.saveString().equals(keyName);
    }

    private static Options options() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.options;
    }
}
