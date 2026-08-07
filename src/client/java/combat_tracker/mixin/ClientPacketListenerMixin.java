package combat_tracker.mixin;

import combat_tracker.detection.InputContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the window in which the server is moving the player's hand.
 *
 * <p>Servers change your held slot legitimately and often — on respawn, when a kit
 * is given, when a plugin builds an inventory. Without this window every one of
 * those would be reported as an unattributed switch, which is the fastest way to
 * make a detector worth ignoring.</p>
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    /**
     * Guarded on the thread, which matters more than it looks.
     *
     * <p>Packet handlers begin with {@code PacketUtils.ensureRunningOnSameThread},
     * which on the Netty thread reschedules the packet onto the client thread and
     * <em>throws</em>. A HEAD injector runs before that, so opening the window
     * unconditionally would open it on the Netty thread and never close it — the
     * RETURN injector is never reached on the throwing path. That leaves an amnesty
     * window standing for the rest of the tick, during which any unattributed hotbar
     * change is silently excused. It also writes a non-volatile static from a second
     * thread, which the rest of this design assumes never happens.</p>
     *
     * <p>Skipping the Netty pass costs nothing: the packet is immediately re-run on
     * the client thread, and that pass opens and closes the window properly.</p>
     */
    @Inject(method = "handleSetHeldSlot", at = @At("HEAD"))
    private void combatTracker$onHeldSlotStart(ClientboundSetHeldSlotPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            InputContext.enterServerSlot();
        }
    }

    @Inject(method = "handleSetHeldSlot", at = @At("RETURN"))
    private void combatTracker$onHeldSlotEnd(ClientboundSetHeldSlotPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            InputContext.exitServerSlot();
        }
    }
}
