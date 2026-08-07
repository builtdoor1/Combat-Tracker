package combat_tracker.mixin;

import combat_tracker.detection.InputContext;
import combat_tracker.detection.IntegrityMonitor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attributes every hotbar change to the input path that caused it.
 *
 * <p>{@code setSelectedSlot} is called from exactly three places on a 1.21.11
 * client — {@code handleKeybinds}, {@code MouseHandler.onScroll} and
 * {@code ClientPacketListener.handleSetHeldSlot} — so the source recorded here is
 * {@link InputContext.Source#NONE} only when something outside vanilla moved the
 * slot.</p>
 *
 * <p>Filtered to the local player's inventory. Other players' inventories are not
 * simulated on this client, but the check costs nothing and keeps a future change
 * to that from quietly turning into noise.</p>
 */
@Mixin(Inventory.class)
public class InventoryMixin {

    @Inject(method = "setSelectedSlot(I)V", at = @At("HEAD"))
    private void combatTracker$onSetSelectedSlot(int slot, CallbackInfo ci) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null || self.getInventory() != (Object) this) {
            return;
        }
        IntegrityMonitor.get().noteSetterCall(slot, InputContext.currentSource());
    }
}
