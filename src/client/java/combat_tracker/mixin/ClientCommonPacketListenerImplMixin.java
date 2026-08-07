package combat_tracker.mixin;

import combat_tracker.detection.IntegrityMonitor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches a hotbar switch that never touches the hotbar.
 *
 * <p>The other slot checks watch {@code Inventory.selected} on this client. A
 * "silent" or "packet" switch never writes it: the cheat constructs a
 * {@link ServerboundSetCarriedItemPacket} — public constructor — and sends it
 * straight down the connection. The server moves its {@code ServerPlayer} to the
 * axe, the hit lands with the axe, and the client's own slot never moved, so there
 * is nothing for {@code InventoryMixin} or the tick sweep to see.</p>
 *
 * <p>Nor does vanilla correct the desync. {@code MultiPlayerGameMode
 * .ensureHasSentCarriedItem} only re-sends when its cached {@code carriedIndex}
 * differs from {@code getSelectedSlot()}, and the cheat disturbed neither, so the
 * server keeps the wrong item indefinitely.</p>
 *
 * <p>The test here is exact rather than heuristic. Vanilla emits this packet from
 * exactly one place, immediately after setting {@code carriedIndex =
 * getSelectedSlot()}, so a legitimate send always carries the slot the client is
 * actually on. A packet whose slot disagrees with the client's own is therefore
 * something no vanilla code path can produce.</p>
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerImplMixin {

    @Inject(method = "send", at = @At("HEAD"))
    private void combatTracker$onSend(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ServerboundSetCarriedItemPacket carried)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // Deliberately not gated on the client thread. A cheat sending from its own
        // thread is exactly the case worth catching, and the comparison is a plain
        // int read that cannot fail — a torn read at worst misattributes one event.
        if (carried.getSlot() != player.getInventory().getSelectedSlot()) {
            IntegrityMonitor.get().onSilentSlotPacket();
        }
    }
}
